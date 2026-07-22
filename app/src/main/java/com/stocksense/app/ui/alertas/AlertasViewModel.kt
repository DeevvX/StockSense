package com.stocksense.app.ui.alertas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.model.Alerta
import com.stocksense.app.data.model.Producto
import com.stocksense.app.data.repository.AlertasRepository
import com.stocksense.app.data.repository.InventarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlertasViewModel(
    private val inventarioRepository: InventarioRepository = InventarioRepository(),
    private val alertasRepository: AlertasRepository = AlertasRepository()
) : ViewModel() {

    private val _productosEnAlerta = MutableStateFlow<List<Producto>>(emptyList())
    val productosEnAlerta: StateFlow<List<Producto>> = _productosEnAlerta.asStateFlow()

    private val _historialAlertas = MutableStateFlow<List<Alerta>>(emptyList())
    val historialAlertas: StateFlow<List<Alerta>> = _historialAlertas.asStateFlow()

    private val _sugerencias = MutableStateFlow<Map<String, Int>>(emptyMap())
    val sugerencias: StateFlow<Map<String, Int>> = _sugerencias.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    init {
        escucharProductos()
        escucharHistorialAlertas()
    }

    private fun escucharProductos() {
        viewModelScope.launch {
            try {
                inventarioRepository.obtenerProductos().collect { productos ->
                    val enAlerta = productos.filter { it.stockBajo }
                    _productosEnAlerta.value = enAlerta
                    _isLoading.value = false

                    calcularSugerenciasPara(enAlerta)
                    registrarAlertasNuevas(enAlerta)
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _mensaje.value = "Error cargando productos: ${e.message}"
            }
        }
    }

    private fun escucharHistorialAlertas() {
        viewModelScope.launch {
            try {
                alertasRepository.obtenerHistorialAlertas().collect { alertas ->
                    _historialAlertas.value = alertas
                }
            } catch (e: Exception) {
                _mensaje.value = "Error cargando historial de alertas: ${e.message}"
            }
        }
    }

    private fun calcularSugerenciasPara(productos: List<Producto>) {
        viewModelScope.launch {
            val nuevasSugerencias = mutableMapOf<String, Int>()
            productos.forEach { producto ->
                val sugerencia = alertasRepository.calcularSugerenciaReabasto(producto)
                nuevasSugerencias[producto.id] = sugerencia
            }
            _sugerencias.value = nuevasSugerencias
        }
    }

    private fun registrarAlertasNuevas(productos: List<Producto>) {
        viewModelScope.launch {
            productos.forEach { producto ->
                alertasRepository.registrarAlertaSiCorresponde(producto)
            }
        }
    }

    /**
     * Actualiza el umbral de stock mínimo configurable de un producto
     */
    fun actualizarUmbral(productoId: String, nuevoUmbral: Int) {
        viewModelScope.launch {
            val exito = inventarioRepository.actualizarStockMinimo(productoId, nuevoUmbral)
            _mensaje.value = if (exito) {
                "Umbral actualizado correctamente"
            } else {
                "No se pudo actualizar el umbral"
            }
        }
    }

    fun limpiarMensaje() {
        _mensaje.value = null
    }
}