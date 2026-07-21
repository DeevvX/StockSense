package com.stocksense.app.ui.reportes

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.stocksense.app.BuildConfig
import com.stocksense.app.data.model.Alerta
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.model.Producto
import com.stocksense.app.data.repository.PdfGenerator
import com.stocksense.app.data.repository.ReporteHtmlGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ReporteMetadata(
    val id: String = "",
    val mes: String = "",
    val fechaGeneracion: Long = 0L,
    val nombreArchivo: String = ""
)

data class ReporteVistaPrevia(
    val archivo: File,
    val metadata: ReporteMetadata,
    val selectionId: Long = System.nanoTime()
)

sealed class ReporteEstado {
    object Idle : ReporteEstado()
    object CargandoDatos : ReporteEstado()
    object GenerandoAnalisis : ReporteEstado()
    object GenerandoPDF : ReporteEstado()
    data class Listo(val archivo: File, val metadata: ReporteMetadata) : ReporteEstado()
    data class Error(val mensaje: String) : ReporteEstado()
}

class ReportesViewModel : ViewModel() {

    private val database = FirebaseDatabase.getInstance()
    private val reportesRef = database.reference.child("reportes")

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _estado = MutableStateFlow<ReporteEstado>(ReporteEstado.Idle)
    val estado: StateFlow<ReporteEstado> = _estado.asStateFlow()

    private val _mesSeleccionado = MutableStateFlow(obtenerMesActual())
    val mesSeleccionado: StateFlow<String> = _mesSeleccionado.asStateFlow()

    private val _historialReportes = MutableStateFlow<List<ReporteMetadata>>(emptyList())
    val historialReportes: StateFlow<List<ReporteMetadata>> = _historialReportes.asStateFlow()

    // Reporte actualmente visible en la vista previa
    private val _reporteVistaPrevia = MutableStateFlow<ReporteVistaPrevia?>(null)
    val reporteVistaPrevia: StateFlow<ReporteVistaPrevia?> = _reporteVistaPrevia.asStateFlow()

    init {
        escucharHistorial()
    }

    private fun escucharHistorial() {
        reportesRef.orderByChild("fechaGeneracion")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val reportes = snapshot.children.mapNotNull { child ->
                        child.getValue(ReporteMetadata::class.java)?.copy(id = child.key ?: "")
                    }.sortedByDescending { it.fechaGeneracion }
                    _historialReportes.value = reportes
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun generarReporte(context: Context) {
        viewModelScope.launch {
            try {
                _estado.value = ReporteEstado.CargandoDatos
                val productos = cargarProductos()
                val movimientos = cargarMovimientos()
                val alertas = cargarAlertas()

                _estado.value = ReporteEstado.GenerandoAnalisis
                val analisis = generarAnalisisOpenAI(productos, movimientos, alertas, _mesSeleccionado.value)

                _estado.value = ReporteEstado.GenerandoPDF
                val html = ReporteHtmlGenerator.generar(
                    productos = productos,
                    movimientos = movimientos,
                    alertas = alertas,
                    analisisIa = analisis
                )
                val archivo = withContext(kotlinx.coroutines.Dispatchers.Main) {
                    PdfGenerator.generatePdf(
                        context = context,
                        html = html,
                        fileName = "StockSense_Reporte_${_mesSeleccionado.value.replace(" ", "_")}.pdf"
                    )
                }

                // Guardar metadatos en Firebase
                val metadata = guardarEnFirebase(_mesSeleccionado.value, archivo.name)

                _estado.value = ReporteEstado.Listo(archivo, metadata)
                _reporteVistaPrevia.value = ReporteVistaPrevia(archivo, metadata)

            } catch (e: Exception) {
                _estado.value = ReporteEstado.Error(e.localizedMessage ?: "Error generando el reporte")
            }
        }
    }

    private suspend fun guardarEnFirebase(mes: String, nombreArchivo: String): ReporteMetadata {
        val id = reportesRef.push().key ?: System.currentTimeMillis().toString()
        val metadata = ReporteMetadata(
            id = id,
            mes = mes,
            fechaGeneracion = System.currentTimeMillis(),
            nombreArchivo = nombreArchivo
        )
        reportesRef.child(id).setValue(metadata).await()
        return metadata
    }

    fun seleccionarReporteParaVer(context: Context, metadata: ReporteMetadata) {
        val directory = context.getExternalFilesDir("reports") ?: return
        val archivo = File(directory, metadata.nombreArchivo)
        if (!archivo.exists() || !archivo.isFile) {
            _estado.value = ReporteEstado.Error("El archivo de este reporte ya no está en el dispositivo.")
            return
        }
        _reporteVistaPrevia.value = ReporteVistaPrevia(
            archivo = archivo,
            metadata = metadata,
            selectionId = System.nanoTime()
        )
    }

    fun compartirPDF(context: Context, archivo: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de Inventario StockSense — ${_mesSeleccionado.value}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
    }

    fun resetEstado() { _estado.value = ReporteEstado.Idle }

    private suspend fun cargarProductos(): List<Producto> {
        val snapshot = database.reference.child("productos").get().await()
        return snapshot.children.mapNotNull { child ->
            try { child.getValue(Producto::class.java)?.copy(id = child.key ?: "") }
            catch (e: Exception) { null }
        }
    }

    private suspend fun cargarMovimientos(): List<Movimiento> {
        val snapshot = database.reference.child("movimientos").get().await()
        return snapshot.children.mapNotNull { child ->
            try { child.getValue(Movimiento::class.java) }
            catch (e: Exception) { null }
        }.sortedByDescending { it.timestamp }
    }

    private suspend fun cargarAlertas(): List<Alerta> {
        val snapshot = database.reference.child("alertas").get().await()
        return snapshot.children.mapNotNull { child ->
            try { child.getValue(Alerta::class.java)?.copy(id = child.key ?: "") }
            catch (e: Exception) { null }
        }
    }

    private suspend fun generarAnalisisOpenAI(
        productos: List<Producto>,
        movimientos: List<Movimiento>,
        alertas: List<Alerta>,
        mes: String
    ): String {
        return try {
            val resumen = buildString {
                appendLine("Datos del inventario de $mes:")
                appendLine("- ${productos.size} productos en catálogo")
                appendLine("- ${movimientos.size} movimientos registrados")
                appendLine("- ${movimientos.count { it.tipo.lowercase() == "entrada" }} entradas, ${movimientos.count { it.tipo.lowercase() == "salida" }} salidas")
                appendLine("- ${alertas.size} alertas de stock bajo generadas")
                appendLine("- ${productos.count { it.stock == 0 }} productos agotados")
                appendLine()
                appendLine("Productos con stock bajo:")
                productos.filter { it.stockBajo }.forEach { p ->
                    appendLine("  • ${p.nombre}: ${p.stock} ${p.unidad} (mínimo: ${p.stockMinimo})")
                }
                appendLine()
                appendLine("Productos con mayor movimiento:")
                movimientos.groupBy { it.productoNombre }
                    .entries.sortedByDescending { it.value.size }.take(3)
                    .forEach { (nombre, movs) -> appendLine("  • $nombre: ${movs.size} movimientos") }
            }

            val prompt = """
                Eres el sistema de análisis de inventario de StockSense.
                Redacta un análisis profesional y conciso del inventario del mes de $mes
                basado en estos datos:

                $resumen

                El análisis debe:
                1. Destacar los hallazgos más importantes del mes
                2. Identificar productos en riesgo de agotarse
                3. Dar 2-3 recomendaciones concretas de reabasto
                4. Usar lenguaje claro y directo (sin tecnicismos)
                5. Tener máximo 150 palabras

                Redacta solo el análisis, sin títulos ni encabezados.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("max_tokens", 300)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()

        } catch (e: Exception) {
            "Análisis no disponible. Los datos del mes muestran ${productos.size} productos en catálogo con ${movimientos.size} movimientos registrados."
        }
    }

    private fun obtenerMesActual(): String {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("es", "MX"))
        return sdf.format(Date()).replaceFirstChar { it.uppercase() }
    }
}
