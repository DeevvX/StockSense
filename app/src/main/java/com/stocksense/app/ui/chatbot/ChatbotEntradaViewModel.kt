package com.stocksense.app.ui.chatbot

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.repository.InventarioRepository
import com.stocksense.app.data.repository.OpenAIEntradaVisionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Un producto detectado en la nota, editable antes de confirmar el registro.
 * `existente = false` significa que no se encontró en el catálogo — en ese
 * caso se muestran campos extra (categoría, unidad, stock mínimo) para que
 * el usuario los ajuste antes de crear el producto nuevo.
 */
data class ItemEntrada(
    val id: Long = System.nanoTime(),
    val nombre: String,
    val cantidad: Int,
    val incluido: Boolean = true,
    val existente: Boolean = true,
    val categoriaNueva: String = "General",
    val unidadNueva: String = "unidad",
    val stockMinimoNuevo: Int = 5
)

/** Resultado de intentar registrar un item como entrada en Firebase. */
data class ResultadoItemEntrada(
    val nombre: String,
    val cantidad: Int,
    val exito: Boolean,
    val productoNuevo: Boolean,
    val detalle: String
)

sealed class ChatbotEntradaEstado {
    data object Idle : ChatbotEntradaEstado()
    data object ProcesandoImagen : ChatbotEntradaEstado()
    data class ListoParaConfirmar(val items: List<ItemEntrada>) : ChatbotEntradaEstado()
    data object Registrando : ChatbotEntradaEstado()
    data class Completado(val resultados: List<ResultadoItemEntrada>) : ChatbotEntradaEstado()
    data class Error(val mensaje: String) : ChatbotEntradaEstado()
}

class ChatbotEntradaViewModel(
    private val openAiApiKey: String,
    private val repository: InventarioRepository = InventarioRepository()
) : ViewModel() {

    private val visionService = OpenAIEntradaVisionService(openAiApiKey)

    private val _estado = MutableStateFlow<ChatbotEntradaEstado>(ChatbotEntradaEstado.Idle)
    val estado: StateFlow<ChatbotEntradaEstado> = _estado.asStateFlow()

    /**
     * Recibe la foto ya capturada/seleccionada (como Bitmap), la manda a
     * OpenAI Vision, verifica cuáles productos ya existen en el catálogo
     * (para marcar los nuevos), y arma la lista editable.
     */
    fun procesarImagen(bitmap: Bitmap) {
        procesarImagenes(listOf(bitmap))
    }

    /**
     * Igual que procesarImagen, pero para varias imágenes a la vez (por
     * ejemplo, las páginas de un PDF). Manda cada página a OpenAI Vision
     * por separado y fusiona los resultados: si el mismo producto aparece
     * en más de una página, suma las cantidades en vez de duplicarlo.
     */
    fun procesarImagenes(bitmaps: List<Bitmap>) {
        if (bitmaps.isEmpty()) {
            _estado.value = ChatbotEntradaEstado.Error("No se pudo leer ninguna página del archivo.")
            return
        }

        viewModelScope.launch {
            _estado.value = ChatbotEntradaEstado.ProcesandoImagen
            try {
                val detectadosPorPagina = withContext(Dispatchers.IO) {
                    bitmaps.map { bitmap ->
                        async {
                            val base64 = withContext(Dispatchers.Default) { bitmapABase64(bitmap) }
                            visionService.identificarProductosEnNota(base64)
                        }
                    }.awaitAll()
                }

                // Fusiona por nombre normalizado, sumando cantidades si se repite entre páginas.
                val fusionados = linkedMapOf<String, com.stocksense.app.data.repository.ProductoNotaEntrega>()
                detectadosPorPagina.flatten().forEach { detectado ->
                    val clave = detectado.nombre.trim().lowercase()
                    if (clave.isBlank()) return@forEach
                    val existente = fusionados[clave]
                    fusionados[clave] = if (existente != null) {
                        existente.copy(cantidad = existente.cantidad + detectado.cantidad)
                    } else {
                        detectado
                    }
                }

                if (fusionados.isEmpty()) {
                    _estado.value = ChatbotEntradaEstado.Error(
                        "No se detectó ningún producto en el documento. Intenta con una foto o archivo más claro."
                    )
                    return@launch
                }

                val items = withContext(Dispatchers.IO) {
                    fusionados.values.map { detectado ->
                        async {
                            val nombreLimpio = detectado.nombre.trim()
                            val existente = repository.buscarProductoPorNombre(nombreLimpio) != null
                            ItemEntrada(
                                nombre = nombreLimpio,
                                cantidad = detectado.cantidad.coerceAtLeast(1),
                                existente = existente
                            )
                        }
                    }.awaitAll()
                }

                _estado.value = ChatbotEntradaEstado.ListoParaConfirmar(items)
            } catch (e: Exception) {
                _estado.value = ChatbotEntradaEstado.Error(
                    e.localizedMessage ?: "Ocurrió un error al procesar el documento."
                )
            }
        }
    }

    /** Convierte un Bitmap a JPEG Base64, redimensionando si es muy grande. */
    private fun bitmapABase64(original: Bitmap): String {
        val maxDimension = 1280
        val ancho = original.width
        val alto = original.height
        val escala = if (max(ancho, alto) > maxDimension) {
            maxDimension.toFloat() / max(ancho, alto)
        } else {
            1f
        }

        val bitmap = if (escala < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (ancho * escala).toInt().coerceAtLeast(1),
                (alto * escala).toInt().coerceAtLeast(1),
                true
            )
        } else {
            original
        }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /** Actualiza el nombre de un item mientras el usuario lo edita. */
    fun actualizarNombre(id: Long, nuevoNombre: String) {
        actualizarLista { items ->
            items.map { if (it.id == id) it.copy(nombre = nuevoNombre) else it }
        }
    }

    /** Cambia la cantidad de un item, sin bajar de 1. */
    fun ajustarCantidad(id: Long, delta: Int) {
        actualizarLista { items ->
            items.map {
                if (it.id == id) it.copy(cantidad = (it.cantidad + delta).coerceAtLeast(1)) else it
            }
        }
    }

    /** Incluye/excluye un item de lo que se va a registrar. */
    fun alternarIncluido(id: Long) {
        actualizarLista { items ->
            items.map { if (it.id == id) it.copy(incluido = !it.incluido) else it }
        }
    }

    /** Quita un item por completo de la lista. */
    fun eliminarItem(id: Long) {
        actualizarLista { items -> items.filter { it.id != id } }
    }

    /** Actualiza la categoría de un producto nuevo (solo aplica si existente=false). */
    fun actualizarCategoriaNueva(id: Long, categoria: String) {
        actualizarLista { items ->
            items.map { if (it.id == id) it.copy(categoriaNueva = categoria) else it }
        }
    }

    /** Actualiza la unidad de un producto nuevo (solo aplica si existente=false). */
    fun actualizarUnidadNueva(id: Long, unidad: String) {
        actualizarLista { items ->
            items.map { if (it.id == id) it.copy(unidadNueva = unidad) else it }
        }
    }

    /** Ajusta el stock mínimo de un producto nuevo (solo aplica si existente=false). */
    fun ajustarStockMinimoNuevo(id: Long, delta: Int) {
        actualizarLista { items ->
            items.map {
                if (it.id == id) it.copy(stockMinimoNuevo = (it.stockMinimoNuevo + delta).coerceAtLeast(0)) else it
            }
        }
    }

    private fun actualizarLista(transform: (List<ItemEntrada>) -> List<ItemEntrada>) {
        val actual = _estado.value
        if (actual is ChatbotEntradaEstado.ListoParaConfirmar) {
            _estado.value = ChatbotEntradaEstado.ListoParaConfirmar(transform(actual.items))
        }
    }

    /**
     * Registra cada item marcado como incluido:
     * - Si ya existe en el catálogo, busca su id y registra el movimiento de entrada.
     * - Si NO existe, primero lo crea con los datos que el usuario ajustó
     *   (categoría, unidad, stock mínimo) y luego registra la entrada, para
     *   que el stock quede correcto desde el primer momento.
     */
    fun confirmarRegistro() {
        val actual = _estado.value
        if (actual !is ChatbotEntradaEstado.ListoParaConfirmar) return

        val items = actual.items.filter { it.incluido && it.nombre.isNotBlank() }
        if (items.isEmpty()) {
            _estado.value = ChatbotEntradaEstado.Error("No hay productos seleccionados para registrar.")
            return
        }

        viewModelScope.launch {
            _estado.value = ChatbotEntradaEstado.Registrando

            val resultados = items.map { item ->
                try {
                    val producto = if (item.existente) {
                        repository.buscarProductoPorNombre(item.nombre)
                    } else {
                        repository.agregarProductoNuevo(
                            nombre = item.nombre,
                            categoria = item.categoriaNueva,
                            stockMinimo = item.stockMinimoNuevo,
                            unidad = item.unidadNueva
                        )
                    }

                    if (producto == null) {
                        ResultadoItemEntrada(
                            nombre = item.nombre,
                            cantidad = item.cantidad,
                            exito = false,
                            productoNuevo = !item.existente,
                            detalle = if (item.existente) {
                                "No se encontró un producto con ese nombre en el catálogo."
                            } else {
                                "No se pudo crear el producto nuevo."
                            }
                        )
                    } else {
                        val movimiento = Movimiento(
                            productoId = producto.id,
                            productoNombre = producto.nombre,
                            tipo = "entrada",
                            cantidad = item.cantidad
                        )
                        val ok = repository.registrarMovimiento(movimiento)
                        ResultadoItemEntrada(
                            nombre = producto.nombre,
                            cantidad = item.cantidad,
                            exito = ok,
                            productoNuevo = !item.existente,
                            detalle = when {
                                !ok -> "No se pudo registrar el movimiento."
                                !item.existente -> "Producto creado · Stock inicial: ${item.cantidad}"
                                else -> "Stock actualizado: +${item.cantidad}"
                            }
                        )
                    }
                } catch (e: Exception) {
                    ResultadoItemEntrada(
                        nombre = item.nombre,
                        cantidad = item.cantidad,
                        exito = false,
                        productoNuevo = !item.existente,
                        detalle = e.localizedMessage ?: "Error inesperado."
                    )
                }
            }

            _estado.value = ChatbotEntradaEstado.Completado(resultados)
        }
    }

    fun resetEstado() {
        _estado.value = ChatbotEntradaEstado.Idle
    }
}