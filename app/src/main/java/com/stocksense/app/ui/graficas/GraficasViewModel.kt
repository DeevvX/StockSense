package com.stocksense.app.ui.graficas

import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.model.Producto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StockPorProducto(
    val nombre: String,
    val stock: Int,
    val color: Long
)

data class MovimientoPorDia(
    val dia: String,
    val entradas: Int,
    val salidas: Int
)

data class DistribucionCategoria(
    val categoria: String,
    val cantidad: Int,
    val porcentaje: Float,
    val color: Long
)

data class StockHistorico(
    val productoNombre: String,
    val color: Long,
    val puntos: List<Pair<String, Float>> // fecha -> stock en ese momento
)

class GraficasViewModel : ViewModel() {

    private val database = FirebaseDatabase.getInstance()

    private val coloresGrafica = listOf(
        0xFF00D4FF, 0xFF7C3AED, 0xFF00FF9C,
        0xFFFF6B35, 0xFFFF3B5C, 0xFFFFD60A,
        0xFF30D158, 0xFF64D2FF
    )

    private val _stockPorProducto = MutableStateFlow<List<StockPorProducto>>(emptyList())
    val stockPorProducto: StateFlow<List<StockPorProducto>> = _stockPorProducto.asStateFlow()

    private val _movimientosPorDia = MutableStateFlow<List<MovimientoPorDia>>(emptyList())
    val movimientosPorDia: StateFlow<List<MovimientoPorDia>> = _movimientosPorDia.asStateFlow()

    private val _distribucionCategorias = MutableStateFlow<List<DistribucionCategoria>>(emptyList())
    val distribucionCategorias: StateFlow<List<DistribucionCategoria>> = _distribucionCategorias.asStateFlow()

    private val _stockHistorico = MutableStateFlow<List<StockHistorico>>(emptyList())
    val stockHistorico: StateFlow<List<StockHistorico>> = _stockHistorico.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var productos: List<Producto> = emptyList()
    private var movimientos: List<Movimiento> = emptyList()

    init {
        escucharProductos()
        escucharMovimientos()
    }

    private fun escucharProductos() {
        database.reference.child("productos")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    productos = snapshot.children.mapNotNull { child ->
                        try { child.getValue(Producto::class.java)?.copy(id = child.key ?: "") }
                        catch (e: Exception) { null }
                    }
                    procesarDatos()
                    _isLoading.value = false
                }
                override fun onCancelled(error: DatabaseError) { _isLoading.value = false }
            })
    }

    private fun escucharMovimientos() {
        database.reference.child("movimientos")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    movimientos = snapshot.children.mapNotNull { child ->
                        try { child.getValue(Movimiento::class.java) }
                        catch (e: Exception) { null }
                    }
                    procesarDatos()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun procesarDatos() {
        procesarStockActual()
        procesarMovimientosPorDia()
        procesarDistribucion()
        procesarStockHistorico()
    }

    private fun procesarStockActual() {
        _stockPorProducto.value = productos.mapIndexed { index, producto ->
            StockPorProducto(
                nombre = producto.nombre.split(" ").first(), // primera palabra para que quepa
                stock = producto.stock,
                color = coloresGrafica[index % coloresGrafica.size]
            )
        }
    }

    private fun procesarMovimientosPorDia() {
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val ultimos7Dias = (6 downTo 0).map { diasAtras ->
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -diasAtras)
            val fechaInicio = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val fechaFin = fechaInicio + 86_400_000L - 1L
            val etiqueta = sdf.format(Date(fechaInicio))
            val movsDia = movimientos.filter { it.timestamp in fechaInicio..fechaFin }
            MovimientoPorDia(
                dia = etiqueta,
                entradas = movsDia.count { it.tipo.lowercase() == "entrada" },
                salidas = movsDia.count { it.tipo.lowercase() == "salida" }
            )
        }
        _movimientosPorDia.value = ultimos7Dias
    }

    private fun procesarDistribucion() {
        val totalStock = productos.sumOf { it.stock }.toFloat()
        if (totalStock <= 0) return
        val porCategoria = productos
            .groupBy { it.categoria.ifBlank { "Sin categoría" } }
            .map { (cat, prods) -> cat to prods.sumOf { it.stock } }
            .sortedByDescending { it.second }
        _distribucionCategorias.value = porCategoria.mapIndexed { index, (cat, cant) ->
            DistribucionCategoria(
                categoria = cat,
                cantidad = cant,
                porcentaje = (cant / totalStock) * 100f,
                color = coloresGrafica[index % coloresGrafica.size]
            )
        }
    }

    private fun procesarStockHistorico() {
        if (productos.isEmpty()) return
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        val calendar = Calendar.getInstance()

        _stockHistorico.value = productos.mapIndexed { index, producto ->
            // Reconstruimos el stock histórico partiendo del stock actual
            // y aplicando movimientos en reversa
            val movsProd = movimientos
                .filter { it.productoId == producto.id }
                .sortedByDescending { it.timestamp }

            val puntos = mutableListOf<Pair<String, Float>>()
            var stockReconstruido = producto.stock.toFloat()

            // Stock actual (hoy)
            val hoy = Calendar.getInstance()
            puntos.add(0, sdf.format(hoy.time) to stockReconstruido)

            // Últimos 6 días en reversa
            for (diasAtras in 1..6) {
                calendar.time = Date()
                calendar.add(Calendar.DAY_OF_YEAR, -diasAtras)
                val fechaInicio = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val fechaFin = fechaInicio + 86_400_000L - 1L

                // Deshacer los movimientos de ese día para reconstruir el stock anterior
                val movsDia = movsProd.filter { it.timestamp in fechaInicio..fechaFin }
                movsDia.forEach { mov ->
                    if (mov.tipo.lowercase() == "salida") {
                        stockReconstruido += mov.cantidad // deshacer salida = sumar
                    } else {
                        stockReconstruido -= mov.cantidad // deshacer entrada = restar
                    }
                }
                stockReconstruido = stockReconstruido.coerceAtLeast(0f)
                puntos.add(0, sdf.format(Date(fechaInicio)) to stockReconstruido)
            }

            StockHistorico(
                productoNombre = producto.nombre.split(" ").first(),
                color = coloresGrafica[index % coloresGrafica.size],
                puntos = puntos
            )
        }
    }
}