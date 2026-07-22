package com.stocksense.app.ui.graficas

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
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
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val puntos: List<Pair<String, Float>>
)

data class PrediccionProducto(
    val productoNombre: String,
    val productoId: String,
    val stockActual: Int,
    val unidadesPredictasSemana: Int,
    val diasHastaAgotarse: Int,   // -1 = no se agota en el periodo
    val confianza: Float,         // 0.0 a 1.0
    val color: Long
)

class GraficasViewModel : ViewModel() {

    private val database = FirebaseDatabase.getInstance()

    /** uid de la cuenta actual — las gráficas y predicciones se calculan
     * únicamente sobre el inventario propio de esta cuenta. */
    private val uid: String =
        FirebaseAuth.getInstance().currentUser?.uid ?: "sin_sesion"

    private val usuarioRef = database.reference.child("usuarios").child(uid)

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

    private val _predicciones = MutableStateFlow<List<PrediccionProducto>>(emptyList())
    val predicciones: StateFlow<List<PrediccionProducto>> = _predicciones.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var productos: List<Producto> = emptyList()
    private var movimientos: List<Movimiento> = emptyList()

    init {
        escucharProductos()
        escucharMovimientos()
    }

    private fun escucharProductos() {
        usuarioRef.child("productos")
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
        usuarioRef.child("movimientos")
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
        procesarPredicciones()
    }

    private fun procesarStockActual() {
        _stockPorProducto.value = productos.mapIndexed { index, producto ->
            StockPorProducto(
                nombre = producto.nombre.split(" ").first(),
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
            val movsProd = movimientos
                .filter { it.productoId == producto.id }
                .sortedByDescending { it.timestamp }

            val puntos = mutableListOf<Pair<String, Float>>()
            var stockReconstruido = producto.stock.toFloat()

            val hoy = Calendar.getInstance()
            puntos.add(0, sdf.format(hoy.time) to stockReconstruido)

            for (diasAtras in 1..6) {
                calendar.time = Date()
                calendar.add(Calendar.DAY_OF_YEAR, -diasAtras)
                val fechaInicio = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val fechaFin = fechaInicio + 86_400_000L - 1L

                val movsDia = movsProd.filter { it.timestamp in fechaInicio..fechaFin }
                movsDia.forEach { mov ->
                    if (mov.tipo.lowercase() == "salida") stockReconstruido += mov.cantidad
                    else stockReconstruido -= mov.cantidad
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

    /**
     * Predicción de demanda usando regresión lineal simple (mínimos cuadrados)
     * sobre los últimos 30 días de salidas por producto.
     *
     * La regresión lineal ajusta una línea y = a + b*x donde:
     * - x = día (0 a 29)
     * - y = unidades vendidas ese día
     * - b = pendiente (tendencia de consumo: positiva = crece, negativa = decrece)
     * - a = intercepto
     *
     * Con eso proyectamos las salidas esperadas para los próximos 7 días.
     * La confianza se basa en qué tan bien se ajusta la línea a los datos reales (R²).
     */
    private fun procesarPredicciones() {
        if (productos.isEmpty()) return

        val ahora = System.currentTimeMillis()
        val hace30Dias = ahora - (30L * 24 * 60 * 60 * 1000)

        _predicciones.value = productos.mapIndexed { index, producto ->
            val movsProd = movimientos.filter {
                it.productoId == producto.id &&
                        it.tipo.lowercase() == "salida" &&
                        it.timestamp >= hace30Dias
            }

            // Agrupar salidas por día (día 0 = hace 30 días, día 29 = hoy)
            val salidasPorDia = DoubleArray(30) { 0.0 }
            movsProd.forEach { mov ->
                val diasDesdeInicio = ((mov.timestamp - hace30Dias) / (24 * 60 * 60 * 1000)).toInt()
                if (diasDesdeInicio in 0..29) {
                    salidasPorDia[diasDesdeInicio] = salidasPorDia[diasDesdeInicio] + mov.cantidad
                }
            }

            val prediccion = if (movsProd.isEmpty()) {
                // Sin historial: predicción conservadora basada en el stock mínimo
                PrediccionProducto(
                    productoNombre = producto.nombre.split(" ").first(),
                    productoId = producto.id,
                    stockActual = producto.stock,
                    unidadesPredictasSemana = 0,
                    diasHastaAgotarse = -1,
                    confianza = 0f,
                    color = coloresGrafica[index % coloresGrafica.size]
                )
            } else {
                // Regresión lineal por mínimos cuadrados
                val n = 30.0
                val sumX = (0 until 30).sumOf { it.toDouble() }
                val sumY = salidasPorDia.sum()
                val sumXY = (0 until 30).sumOf { x -> x * salidasPorDia[x] }
                val sumX2 = (0 until 30).sumOf { x -> (x * x).toDouble() }

                val pendiente = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
                val intercepto = (sumY - pendiente * sumX) / n

                // Proyectar los próximos 7 días (días 30 a 36)
                val unidadesProxSemana = (30 until 37).sumOf { x ->
                    (intercepto + pendiente * x).coerceAtLeast(0.0)
                }.roundToInt()

                // Calcular R² (coeficiente de determinación) para la confianza
                val mediaY = sumY / n
                val ssTot = salidasPorDia.sumOf { y -> (y - mediaY) * (y - mediaY) }
                val ssRes = (0 until 30).sumOf { x ->
                    val yPred = intercepto + pendiente * x
                    val diff = salidasPorDia[x] - yPred
                    diff * diff
                }
                val r2 = if (ssTot > 0) (1 - ssRes / ssTot).coerceIn(0.0, 1.0) else 0.0

                // Días hasta agotarse con el ritmo de consumo actual
                val consumoDiario = (sumY / n).coerceAtLeast(0.0)
                val diasHastaAgotarse = if (consumoDiario > 0) {
                    (producto.stock / consumoDiario).roundToInt()
                } else -1

                PrediccionProducto(
                    productoNombre = producto.nombre.split(" ").first(),
                    productoId = producto.id,
                    stockActual = producto.stock,
                    unidadesPredictasSemana = unidadesProxSemana,
                    diasHastaAgotarse = diasHastaAgotarse,
                    confianza = r2.toFloat(),
                    color = coloresGrafica[index % coloresGrafica.size]
                )
            }
            prediccion
        }
    }
}