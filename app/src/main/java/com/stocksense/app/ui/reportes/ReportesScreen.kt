package com.stocksense.app.ui.reportes

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stocksense.app.ui.login.SSColors
import com.stocksense.app.ui.login.bgGradient
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG_DESCARGA = "StockSense_Descarga"
private const val CANAL_DESCARGAS_ID = "stocksense_descargas"
private const val NOTIF_ID_DESCARGA = 2001


@Composable
fun ReportesScreen(
    onBack: () -> Unit,
    viewModel: ReportesViewModel = viewModel()
) {
    val estado by viewModel.estado.collectAsState()
    val mesSeleccionado by viewModel.mesSeleccionado.collectAsState()
    val historial by viewModel.historialReportes.collectAsState()
    val reporteVistaPrevia by viewModel.reporteVistaPrevia.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(estado) {
        if (estado is ReporteEstado.Error) {
            snackbarHostState.showSnackbar((estado as ReporteEstado.Error).mensaje)
            viewModel.resetEstado()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgGradient)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TopBar
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF080E1A))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1F2E)).border(1.dp, SSColors.CardBorder, RoundedCornerShape(10.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = SSColors.TextMuted, modifier = Modifier.size(16.dp))
                }
                Text(text = "REPORTES", fontSize = 14.sp, fontWeight = FontWeight.Black, color = SSColors.Cyan, letterSpacing = 1.sp)
            }

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // ── Tarjeta principal de generación ─────────────────
                TarjetaGenerarReporte(
                    mesSeleccionado = mesSeleccionado,
                    estado = estado,
                    onGenerar = { viewModel.generarReporte(context) },
                    onReset = { viewModel.resetEstado() }
                )

                // ── Botones de acción cuando el PDF está listo ───────
                if (estado is ReporteEstado.Listo) {
                    val archivo = (estado as ReporteEstado.Listo).archivo
                    TarjetaAcciones(
                        archivo = archivo,
                        snackbarHostState = snackbarHostState,
                        onCompartir = { viewModel.compartirPDF(context, archivo) },
                        onReset = { viewModel.resetEstado() }
                    )
                }

                // ── Vista previa del reporte activo ──────────────────
                reporteVistaPrevia?.let { preview ->
                    key(preview.selectionId) {
                        VisorPDF(
                            archivo = preview.archivo,
                            metadata = preview.metadata,
                            selectionId = preview.selectionId
                        )
                    }
                }

                // ── Historial de reportes anteriores ─────────────────
                if (historial.isNotEmpty()) {
                    HistorialReportes(
                        historial = historial,
                        reporteActivo = reporteVistaPrevia?.metadata,
                        onSeleccionar = { metadata ->
                            viewModel.seleccionarReporteParaVer(context, metadata)
                        }
                    )
                }

                // ── Info del reporte ─────────────────────────────────
                InfoReporte()

                Spacer(Modifier.height(16.dp))
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
fun TarjetaGenerarReporte(
    mesSeleccionado: String,
    estado: ReporteEstado,
    onGenerar: () -> Unit,
    onReset: () -> Unit
) {
    val isGenerando = estado is ReporteEstado.CargandoDatos ||
            estado is ReporteEstado.GenerandoAnalisis ||
            estado is ReporteEstado.GenerandoPDF

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0D1F35), Color(0xFF111827))))
            .border(1.dp, SSColors.CyanGlow, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
                    .background(SSColors.CyanDim).border(1.dp, SSColors.CyanGlow, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) { Text(text = "📄", fontSize = 28.sp) }

            Text(text = "REPORTE MENSUAL", fontSize = 14.sp, fontWeight = FontWeight.Black, color = SSColors.Text, letterSpacing = 1.sp, textAlign = TextAlign.Center)
            Text(text = mesSeleccionado, fontSize = 12.sp, color = SSColors.Cyan, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                text = "Genera el reporte completo del mes con análisis\nautomático de IA, movimientos, alertas y stock.",
                fontSize = 11.sp, color = SSColors.TextMuted, textAlign = TextAlign.Center, lineHeight = 16.sp
            )

            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isGenerando) Brush.horizontalGradient(listOf(SSColors.Green, SSColors.Green))
                        else Brush.horizontalGradient(listOf(SSColors.Cyan, SSColors.Purple))
                    )
                    .clickable(
                        enabled = !isGenerando && estado !is ReporteEstado.Listo,
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { onGenerar() },
                contentAlignment = Alignment.Center
            ) {
                if (isGenerando) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = SSColors.BgDeep, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text(
                            text = when (estado) {
                                is ReporteEstado.CargandoDatos -> "Cargando datos..."
                                is ReporteEstado.GenerandoAnalisis -> "Analizando con IA..."
                                is ReporteEstado.GenerandoPDF -> "Generando PDF..."
                                else -> "Generando..."
                            },
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSColors.BgDeep
                        )
                    }
                } else {
                    Text(
                        text = if (estado is ReporteEstado.Listo) "✓ Reporte generado" else "Generar Reporte PDF",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSColors.BgDeep
                    )
                }
            }
        }
    }
}

@Composable
fun TarjetaAcciones(
    archivo: File,
    snackbarHostState: SnackbarHostState,
    onCompartir: () -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var resultadoDescarga by remember { mutableStateOf<Pair<Boolean, String>?>(null) }


    val permisoNotificacionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            mostrarNotificacionDescarga(context, archivo)
        } else {
            Log.d(TAG_DESCARGA, "Permiso de notificaciones no concedido, se omite la notificación.")
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(SSColors.Card).border(1.dp, SSColors.Green.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(8.dp).background(SSColors.Green, CircleShape))
                Text(text = "PDF LISTO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SSColors.Green, letterSpacing = 0.5.sp)
            }
            Text(text = archivo.name, fontSize = 11.sp, color = SSColors.TextMuted)

            // Botón descargar
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(SSColors.Cyan.copy(alpha = 0.15f))
                    .border(1.dp, SSColors.Cyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch {
                            val exito = withContext(Dispatchers.IO) {
                                descargarPDF(context, archivo)
                            }
                            Log.d(TAG_DESCARGA, "Descarga de ${archivo.name}: exito=$exito")
                            resultadoDescarga = exito to if (exito) {
                                "El reporte se guardó correctamente en la carpeta Descargas de tu dispositivo."
                            } else {
                                "No se pudo guardar el archivo. Intenta de nuevo o usa la opción de compartir."
                            }

                            if (exito) {
                                val tienePermiso = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED

                                if (tienePermiso) {
                                    mostrarNotificacionDescarga(context, archivo)
                                } else {
                                    permisoNotificacionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "⬇", fontSize = 14.sp, color = SSColors.Cyan)
                    Text(text = "Guardar en Descargas", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSColors.Cyan)
                }
            }

            // Botón compartir
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(SSColors.Green.copy(alpha = 0.15f))
                    .border(1.dp, SSColors.Green.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCompartir() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null, tint = SSColors.Green, modifier = Modifier.size(16.dp))
                    Text(text = "Compartir PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSColors.Green)
                }
            }

            TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Generar nuevo reporte", fontSize = 11.sp, color = SSColors.TextMuted)
            }
        }
    }

    resultadoDescarga?.let { (exito, mensaje) ->
        DialogoResultado(
            exito = exito,
            titulo = if (exito) "Descarga completa" else "No se pudo guardar",
            mensaje = mensaje,
            onDismiss = { resultadoDescarga = null }
        )
    }
}


@Composable
fun DialogoResultado(
    exito: Boolean,
    titulo: String,
    mensaje: String,
    onDismiss: () -> Unit
) {
    val colorAcento = if (exito) SSColors.Green else Color(0xFFFF5C5C)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0D1F35), Color(0xFF111827))))
                .border(1.dp, colorAcento.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                        .background(colorAcento.copy(alpha = 0.15f))
                        .border(1.dp, colorAcento.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (exito) "✓" else "!", fontSize = 26.sp, fontWeight = FontWeight.Black, color = colorAcento)
                }

                Text(
                    text = titulo,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = SSColors.Text,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.3.sp
                )

                Text(
                    text = mensaje,
                    fontSize = 12.sp,
                    color = SSColors.TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Box(
                    modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
                        .background(
                            if (exito) Brush.horizontalGradient(listOf(SSColors.Cyan, SSColors.Purple))
                            else Brush.horizontalGradient(listOf(colorAcento, colorAcento))
                        )
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "OK", fontSize = 13.sp, fontWeight = FontWeight.Black, color = SSColors.BgDeep, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

private data class PdfRenderKey(
    val absolutePath: String,
    val length: Long,
    val lastModified: Long,
    val selectionId: Long
)

private sealed interface PdfPreviewState {
    data object Loading : PdfPreviewState
    data class Success(val pages: List<Bitmap>) : PdfPreviewState
    data class Error(val message: String) : PdfPreviewState
}

@Composable
fun VisorPDF(archivo: File, metadata: ReporteMetadata, selectionId: Long) {
    val renderKey = PdfRenderKey(
        absolutePath = archivo.absolutePath,
        length = archivo.length(),
        lastModified = archivo.lastModified(),
        selectionId = selectionId
    )

    var previewState by remember(renderKey) {
        mutableStateOf<PdfPreviewState>(PdfPreviewState.Loading)
    }

    LaunchedEffect(renderKey) {
        previewState = PdfPreviewState.Loading
        val result = runCatching {
            withContext(Dispatchers.IO) {
                renderizarPdfCancelable(archivo)
            }
        }
        coroutineContext.ensureActive()
        previewState = result.fold(
            onSuccess = { PdfPreviewState.Success(it) },
            onFailure = { PdfPreviewState.Error(it.message ?: "No fue posible abrir el PDF.") }
        )
    }

    DisposableEffect(renderKey, previewState) {
        val currentPages = (previewState as? PdfPreviewState.Success)?.pages.orEmpty()
        onDispose {
            currentPages.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val fechaFormateada = sdf.format(Date(metadata.fechaGeneracion))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Encabezado identificador del reporte
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(SSColors.CyanDim).border(1.dp, SSColors.CyanGlow, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "VISTA PREVIA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = SSColors.Cyan, letterSpacing = 1.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Reporte: ${metadata.mes}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SSColors.Text)
                    Text(text = "Generado el $fechaFormateada", fontSize = 9.sp, color = SSColors.TextMuted)
                }
            }
        }

        when (val state = previewState) {
            PdfPreviewState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = SSColors.Cyan, strokeWidth = 2.dp)
                        Text(text = "Cargando reporte...", fontSize = 11.sp, color = SSColors.TextMuted)
                    }
                }
            }
            is PdfPreviewState.Error -> {
                Text(text = state.message, fontSize = 11.sp, color = SSColors.TextMuted, modifier = Modifier.padding(16.dp))
            }
            is PdfPreviewState.Success -> {
                state.pages.forEachIndexed { index, bitmap ->
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, SSColors.CardBorder, RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Página ${index + 1}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                    if (index < state.pages.size - 1) {
                        Text(
                            text = "Página ${index + 2} de ${state.pages.size}",
                            fontSize = 9.sp, color = SSColors.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private suspend fun renderizarPdfCancelable(archivo: File): List<Bitmap> {
    require(archivo.exists()) { "El archivo no existe: ${archivo.absolutePath}" }
    val bitmaps = mutableListOf<Bitmap>()
    try {
        ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    coroutineContext.ensureActive()
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        try {
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            coroutineContext.ensureActive()
                            bitmaps += bmp
                        } catch (e: Throwable) {
                            if (!bmp.isRecycled) bmp.recycle()
                            throw e
                        }
                    }
                }
            }
        }
        return bitmaps
    } catch (e: Throwable) {
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        throw e
    }
}


@Composable
fun HistorialReportes(
    historial: List<ReporteMetadata>,
    reporteActivo: ReporteMetadata?,
    onSeleccionar: (ReporteMetadata) -> Unit
) {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(SSColors.Card).border(1.dp, SSColors.CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                text = "REPORTES GENERADOS",
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = SSColors.Text, letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            HorizontalDivider(color = SSColors.CardBorder)

            historial.forEachIndexed { index, reporte ->
                val isActivo = reporteActivo?.id == reporte.id

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onSeleccionar(reporte)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ícono
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (isActivo) SSColors.CyanDim else Color(0xFF1A1F2E))
                            .border(1.dp, if (isActivo) SSColors.Cyan else SSColors.CardBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(text = "📄", fontSize = 16.sp) }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reporte.mes,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActivo) SSColors.Cyan else SSColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Generado el ${sdf.format(Date(reporte.fechaGeneracion))}",
                            fontSize = 9.sp,
                            color = SSColors.TextMuted
                        )
                    }

                    // Badge "Viendo" si es el activo
                    if (isActivo) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(SSColors.CyanDim)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(text = "Viendo", fontSize = 9.sp, color = SSColors.Cyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (index < historial.size - 1) HorizontalDivider(color = SSColors.CardBorder)
            }
        }
    }
}

@Composable
fun InfoReporte() {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(SSColors.Card).border(1.dp, SSColors.CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "EL REPORTE INCLUYE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SSColors.Text, letterSpacing = 0.5.sp)
            HorizontalDivider(color = SSColors.CardBorder)
            listOf(
                "📊" to "Resumen ejecutivo del mes",
                "🤖" to "Análisis automático generado por IA",
                "📦" to "Inventario actual con estado de cada producto",
                "🔄" to "Historial de movimientos del mes",
                "⚠️" to "Alertas de stock bajo registradas",
                "📤" to "Compartible por WhatsApp, correo o Drive"
            ).forEach { (emoji, descripcion) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = emoji, fontSize = 14.sp)
                    Text(text = descripcion, fontSize = 11.sp, color = SSColors.TextMuted)
                }
            }
        }
    }
}



private fun descargarPDF(context: android.content.Context, archivo: File): Boolean {
    if (!archivo.exists()) {
        Log.e(TAG_DESCARGA, "El archivo origen no existe: ${archivo.absolutePath}")
        return false
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return try {
            val destino = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                archivo.name
            )
            archivo.copyTo(destino, overwrite = true)
            true
        } catch (e: Exception) {
            Log.e(TAG_DESCARGA, "Fallo al descargar PDF (legacy): ${e.message}", e)
            false
        }
    }


    val resolver = context.contentResolver
    var ultimoError: Exception? = null

    for (intento in 0..2) {
        val nombreIntento = if (intento == 0) archivo.name else nombreConSufijo(archivo.name, intento)
        try {
            guardarEnMediaStore(resolver, archivo, nombreIntento)
            Log.d(TAG_DESCARGA, "Descarga guardada como '$nombreIntento' (intento $intento)")
            return true
        } catch (e: Exception) {
            ultimoError = e
            Log.w(TAG_DESCARGA, "Intento $intento con nombre '$nombreIntento' falló: ${e.message}")
        }
    }

    Log.e(TAG_DESCARGA, "Fallo al descargar PDF tras varios intentos: ${ultimoError?.message}", ultimoError)
    return false
}


@RequiresApi(Build.VERSION_CODES.Q)
private fun guardarEnMediaStore(resolver: android.content.ContentResolver, archivo: File, nombreDestino: String) {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, nombreDestino)
        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("resolver.insert devolvió null para '$nombreDestino'")

    try {
        resolver.openOutputStream(uri, "wt")?.use { out ->
            FileInputStream(archivo).use { input -> input.copyTo(out) }
        } ?: error("openOutputStream devolvió null para uri=$uri")

        val valuesFinal = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        resolver.update(uri, valuesFinal, null, null)
    } catch (e: Exception) {
        // Limpieza: no dejar un registro "pending" huérfano ocupando el nombre.
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
}


private fun nombreConSufijo(nombreOriginal: String, n: Int): String {
    val sinExtension = nombreOriginal.removeSuffix(".pdf")
    return "$sinExtension ($n).pdf"
}

private fun crearCanalNotificacionesDescargas(context: android.content.Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CANAL_DESCARGAS_ID) == null) {
        val canal = NotificationChannel(
            CANAL_DESCARGAS_ID,
            "Reportes descargados",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisa cuando un reporte PDF de StockSense se guarda en Descargas"
        }
        manager.createNotificationChannel(canal)
    }
}


@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
private fun mostrarNotificacionDescarga(context: Context, archivo: File) {
    crearCanalNotificacionesDescargas(context)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", archivo)
    val intentAbrir = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        archivo.name.hashCode(),
        intentAbrir,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notificacion = NotificationCompat.Builder(context, CANAL_DESCARGAS_ID)
        .setSmallIcon(R.drawable.stat_sys_download_done)
        .setContentTitle("Reporte descargado")
        .setContentText("${archivo.name} • Toca para abrir")
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    try {
        NotificationManagerCompat.from(context).notify(NOTIF_ID_DESCARGA, notificacion)
    } catch (e: SecurityException) {
        // Puede pasar si el permiso se revocó justo entre la verificación y
        // este punto (caso raro, pero mejor no crashear la app por esto).
        Log.w(TAG_DESCARGA, "Sin permiso para mostrar la notificación: ${e.message}")
    }
}