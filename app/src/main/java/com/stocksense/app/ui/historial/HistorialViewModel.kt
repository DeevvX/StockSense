package com.stocksense.app.ui.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.repository.InventarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistorialViewModel(
    private val repository: InventarioRepository = InventarioRepository()
) : ViewModel() {

    private val _todosLosMovimientos = MutableStateFlow<List<Movimiento>>(emptyList())

    private val _movimientosFiltrados = MutableStateFlow<List<Movimiento>>(emptyList())
    val movimientosFiltrados: StateFlow<List<Movimiento>> = _movimientosFiltrados.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda.asStateFlow()

    private val _filtroTipo = MutableStateFlow(FiltroTipo.TODOS)
    val filtroTipo: StateFlow<FiltroTipo> = _filtroTipo.asStateFlow()

    init {
        escucharMovimientos()
    }

    private fun escucharMovimientos() {
        viewModelScope.launch {
            try {
                repository.obtenerMovimientos().collect { movimientos ->
                    _todosLosMovimientos.value = movimientos
                    _isLoading.value = false
                    aplicarFiltros()
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    fun onBusquedaCambia(texto: String) {
        _busqueda.value = texto
        aplicarFiltros()
    }

    fun onFiltroTipoCambia(filtro: FiltroTipo) {
        _filtroTipo.value = filtro
        aplicarFiltros()
    }

    private fun aplicarFiltros() {
        val texto = _busqueda.value.trim().lowercase()
        val tipo = _filtroTipo.value

        _movimientosFiltrados.value = _todosLosMovimientos.value.filter { mov ->
            val coincideTexto = texto.isEmpty() || mov.productoNombre.lowercase().contains(texto)
            val coincideTipo = when (tipo) {
                FiltroTipo.TODOS -> true
                FiltroTipo.ENTRADAS -> mov.tipo.lowercase() == "entrada"
                FiltroTipo.SALIDAS -> mov.tipo.lowercase() == "salida"
            }
            coincideTexto && coincideTipo
        }
    }
}

enum class FiltroTipo(val etiqueta: String) {
    TODOS("Todos"),
    ENTRADAS("Entradas"),
    SALIDAS("Salidas")
}