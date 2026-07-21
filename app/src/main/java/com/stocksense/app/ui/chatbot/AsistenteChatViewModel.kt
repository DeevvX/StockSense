package com.stocksense.app.ui.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.model.Producto
import com.stocksense.app.data.repository.InventarioAsistenteService
import com.stocksense.app.data.repository.InventarioRepository
import com.stocksense.app.data.repository.TurnoChat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class MensajeChat(
    val id: Long = System.nanoTime(),
    val esUsuario: Boolean,
    val texto: String
)

class AsistenteChatViewModel(
    openAiApiKey: String,
    private val repository: InventarioRepository = InventarioRepository()
) : ViewModel() {

    private val servicio = InventarioAsistenteService(openAiApiKey)

    private val _mensajes = MutableStateFlow<List<MensajeChat>>(
        listOf(
            MensajeChat(
                esUsuario = false,
                texto = "Hola, soy el asistente de inventario de StockSense. Puedes preguntarme cosas como \"¿cuánto stock me queda de Shampoo?\" o \"¿cuándo debería comprar más Paracetamol?\"."
            )
        )
    )
    val mensajes: StateFlow<List<MensajeChat>> = _mensajes.asStateFlow()

    private val _procesando = MutableStateFlow(false)
    val procesando: StateFlow<Boolean> = _procesando.asStateFlow()

    fun enviarPregunta(pregunta: String) {
        val texto = pregunta.trim()
        if (texto.isBlank() || _procesando.value) return

        _mensajes.value = _mensajes.value + MensajeChat(esUsuario = true, texto = texto)
        _procesando.value = true

        viewModelScope.launch {
            try {
                val productos = repository.obtenerProductosSnapshot()
                val movimientos = repository.obtenerMovimientosSnapshot()
                val contexto = construirContexto(productos, movimientos)

                val historial = _mensajes.value.map { TurnoChat(it.esUsuario, it.texto) }
                val respuesta = servicio.responder(texto, contexto, historial)

                _mensajes.value = _mensajes.value + MensajeChat(esUsuario = false, texto = respuesta)
            } catch (e: Exception) {
                _mensajes.value = _mensajes.value + MensajeChat(
                    esUsuario = false,
                    texto = "Ocurrió un error al procesar tu pregunta. Intenta de nuevo."
                )
            } finally {
                _procesando.value = false
            }
        }
    }

    /**
     * Arma un resumen en texto plano del inventario actual: stock, mínimo,
     * consumo diario promedio de los últimos 30 días, y una estimación de
     * en cuántos días se agotaría cada producto. Esto es lo que le da al
     * modelo la información real para responder sin inventar cifras.
     */
    private fun construirContexto(productos: List<Producto>, movimientos: List<Movimiento>): String {
        if (productos.isEmpty()) {
            return "El catálogo de productos está vacío. No hay datos de inventario disponibles."
        }

        val ahora = System.currentTimeMillis()
        val hace30Dias = ahora - 30L * 86_400_000L

        val salidasPorProducto = movimientos
            .asSequence()
            .filter { !it.esEntrada && it.timestamp in hace30Dias..ahora }
            .groupBy { it.productoId }
            .mapValues { (_, lista) -> lista.sumOf { it.cantidad } }

        return buildString {
            appendLine("Fecha actual: ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("es", "MX")).format(java.util.Date(ahora))}")
            appendLine("Total de productos en catálogo: ${productos.size}")
            appendLine()
            productos.forEach { producto ->
                val totalSalidas30d = salidasPorProducto[producto.id] ?: 0
                val consumoDiario = totalSalidas30d / 30.0
                val diasRestantes = if (consumoDiario > 0.0) {
                    ceil(producto.stock / consumoDiario).toInt()
                } else null

                append("- ${producto.nombre}: stock actual ${producto.stock} ${producto.unidad}, ")
                append("mínimo configurado ${producto.stockMinimo} ${producto.unidad}, ")
                append("categoría ${producto.categoria.ifBlank { "sin categoría" }}, ")
                append("consumo últimos 30 días: $totalSalidas30d ${producto.unidad} ")
                append("(promedio ${formatearDecimal(consumoDiario)} ${producto.unidad}/día). ")
                if (diasRestantes != null) {
                    appendLine("A este ritmo, el stock se agotaría en aproximadamente $diasRestantes día(s).")
                } else {
                    appendLine("Sin salidas registradas recientemente, no se puede estimar cuándo se agotará.")
                }
            }
        }
    }

    private fun formatearDecimal(valor: Double): String =
        String.format(java.util.Locale("es", "MX"), "%.1f", valor)
}