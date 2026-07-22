package com.stocksense.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.model.Producto
import com.stocksense.app.data.repository.InventarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardViewModel(
    private val repository: InventarioRepository = InventarioRepository()
) : ViewModel() {

    private val _productos = MutableStateFlow<List<Producto>>(emptyList())
    val productos: StateFlow<List<Producto>> = _productos.asStateFlow()

    private val _movimientos = MutableStateFlow<List<Movimiento>>(emptyList())
    val movimientos: StateFlow<List<Movimiento>> = _movimientos.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val totalProductos: Int
        get() = _productos.value.size

    val productosConStockBajo: List<Producto>
        get() = _productos.value.filter { it.stockBajo }

    val totalAlertas: Int
        get() = productosConStockBajo.size


    val todosLosProductos: List<Producto>
        get() = _productos.value.sortedWith(
            compareByDescending<Producto> { it.stockBajo }.thenBy { it.nombre }
        )

    val movimientosHoy: Int
        get() = _movimientos.value.count { movimiento ->
            esTimestampDeHoy(movimiento.timestamp)
        }

    val ultimosMovimientos: List<Movimiento>
        get() = _movimientos.value
            .sortedByDescending { it.timestamp }
            .take(5)

    private var productosCargados = false
    private var movimientosCargados = false

    init {
        escucharProductos()
        escucharMovimientos()
    }

    private fun escucharProductos() {
        viewModelScope.launch {
            try {
                repository.obtenerProductos().collect { listaProductos ->
                    _productos.value = listaProductos
                    productosCargados = true
                    actualizarLoading()
                }
            } catch (e: Exception) {
                productosCargados = true
                actualizarLoading()
            }
        }
    }

    private fun escucharMovimientos() {
        viewModelScope.launch {
            try {
                repository.obtenerMovimientos().collect { listaMovimientos ->
                    _movimientos.value = listaMovimientos
                    movimientosCargados = true
                    actualizarLoading()
                }
            } catch (e: Exception) {
                movimientosCargados = true
                actualizarLoading()
            }
        }
    }

    private fun actualizarLoading() {
        if (productosCargados && movimientosCargados) {
            _isLoading.value = false
        }
    }

    private fun esTimestampDeHoy(timestamp: Long): Boolean {
        val hoy = Calendar.getInstance()

        val fechaMovimiento = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }

        return hoy.get(Calendar.YEAR) == fechaMovimiento.get(Calendar.YEAR) &&
                hoy.get(Calendar.DAY_OF_YEAR) == fechaMovimiento.get(Calendar.DAY_OF_YEAR)
    }
}