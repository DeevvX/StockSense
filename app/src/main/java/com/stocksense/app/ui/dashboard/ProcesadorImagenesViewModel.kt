package com.stocksense.app.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.stocksense.app.data.model.ImagenPendiente
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.repository.InventarioRepository
import com.stocksense.app.data.repository.OpenAIVisionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcesadorImagenesViewModel(
    private val openAiApiKey: String,
    private val repository: InventarioRepository = InventarioRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "ProcesadorImagenesVM"
    }

    private val db = FirebaseDatabase.getInstance()
    private val imagenesRef = db.getReference("imagenes_pendientes")
    private val visionService = OpenAIVisionService(openAiApiKey)

    private val _procesando = MutableStateFlow(false)
    val procesando: StateFlow<Boolean> = _procesando.asStateFlow()

    private val _ultimoResultado = MutableStateFlow<String?>(null)
    val ultimoResultado: StateFlow<String?> = _ultimoResultado.asStateFlow()


    private val idsEnProceso = mutableSetOf<String>()

    init {
        escucharImagenesPendientes()
    }

    private fun escucharImagenesPendientes() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                snapshot.children.forEach { child ->
                    procesarHijoSeguro(child)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error escuchando Firebase: ${error.message}")
                _ultimoResultado.value = "Error escuchando Firebase: ${error.message}"
            }
        }
        imagenesRef.addValueEventListener(listener)
    }

    private fun procesarHijoSeguro(child: DataSnapshot) {
        val key = child.key ?: return

        val imagen: ImagenPendiente? = try {
            child.getValue(ImagenPendiente::class.java)?.copy(id = key)
        } catch (e: Exception) {

            Log.w(TAG, "Nodo '$key' en imagenes_pendientes no se pudo convertir a ImagenPendiente: ${e.message}")
            null
        }

        if (imagen == null) return
        if (imagen.procesada) return
        if (imagen.imagenBase64.isBlank()) {
            Log.w(TAG, "Nodo '$key' no tiene imagenBase64 válido, se omite")
            return
        }
        if (!idsEnProceso.add(imagen.id)) {
            return
        }

        procesarImagen(imagen)
    }

    private fun procesarImagen(imagen: ImagenPendiente) {
        viewModelScope.launch {
            _procesando.value = true

            try {
                val deteccion = withContext(Dispatchers.IO) {
                    visionService.identificarProducto(imagen.imagenBase64)
                }

                if (deteccion != null && deteccion.confianza >= 0.5) {

                    val productoReal = repository.buscarProductoPorNombre(deteccion.producto)

                    if (productoReal == null) {
                        Log.w(TAG, "OpenAI detectó '${deteccion.producto}' pero no existe ningún producto con ese nombre en el catálogo")
                        _ultimoResultado.value =
                            "Producto detectado (\"${deteccion.producto}\") no existe en el catálogo. Agrégalo en Configuración."
                    } else {

                        val movimiento = Movimiento(
                            productoId = productoReal.id,
                            productoNombre = productoReal.nombre,
                            tipo = "salida",
                            cantidad = deteccion.cantidad
                        )

                        val registrado = repository.registrarMovimiento(movimiento)

                        _ultimoResultado.value = if (registrado) {
                            "Salida registrada: ${productoReal.nombre} - confianza ${(deteccion.confianza * 100).toInt()}%"
                        } else {
                            "Se detectó ${productoReal.nombre} pero no se pudo registrar la salida (revisa stock disponible)"
                        }
                    }
                } else {
                    _ultimoResultado.value = "No se pudo identificar el producto con suficiente confianza"
                }

                imagenesRef.child(imagen.id).child("procesada").setValue(true)

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando imagen ${imagen.id}: ${e.message}", e)
                _ultimoResultado.value = "Error procesando imagen: ${e.message}"
            } finally {
                idsEnProceso.remove(imagen.id)
                _procesando.value = false
            }
        }
    }
}