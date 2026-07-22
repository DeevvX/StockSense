package com.stocksense.app.ui.chatbot

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stocksense.app.ui.login.SSColors
import com.stocksense.app.ui.login.bgGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ModoChatbot { ENTRADA, PREGUNTAR }

/**
 * Envuelve el contenido del chatbot en un diálogo de pantalla completa.
 * Se abre desde el botón flotante del Dashboard — no es una ruta de
 * navegación, así que no deja rastro en el back stack ni cambia de pantalla.
 */
@Composable
fun ChatbotEntradaDialog(
    openAiApiKey: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ChatbotEntradaContenido(
                openAiApiKey = openAiApiKey,
                onCerrar = onDismiss
            )
        }
    }
}

@Composable
private fun ChatbotEntradaContenido(
    openAiApiKey: String,
    onCerrar: () -> Unit,
    viewModel: ChatbotEntradaViewModel = viewModel(
        factory = ChatbotEntradaViewModelFactory(openAiApiKey)
    ),
    asistenteViewModel: AsistenteChatViewModel = viewModel(
        factory = AsistenteChatViewModelFactory(openAiApiKey)
    )
) {
    var modo by remember { mutableStateOf(ModoChatbot.ENTRADA) }

    Box(
        modifier = Modifier.fillMaxSize().background(bgGradient)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0D1421), Color(0xFF080E1A))))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                                .background(Brush.linearGradient(listOf(SSColors.Cyan, SSColors.Purple))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🤖", fontSize = 18.sp)
                        }
                        Column {
                            Text(text = "Asistente StockSense", fontSize = 15.sp, fontWeight = FontWeight.Black, color = SSColors.Text, letterSpacing = 0.2.sp)
                            Text(text = "Entradas por foto · Consultas con IA", fontSize = 10.sp, color = SSColors.TextMuted)
                        }
                    }

                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                            .background(Color(0xFF1A1F2E))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCerrar() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 14.sp, color = SSColors.TextMuted)
                    }
                }

                // Segmented control
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF11182A))
                        .padding(3.dp)
                ) {
                    SegmentoTab(
                        texto = "Registrar entrada",
                        activo = modo == ModoChatbot.ENTRADA,
                        modifier = Modifier.weight(1f)
                    ) { modo = ModoChatbot.ENTRADA }
                    SegmentoTab(
                        texto = "Preguntar",
                        activo = modo == ModoChatbot.PREGUNTAR,
                        modifier = Modifier.weight(1f)
                    ) { modo = ModoChatbot.PREGUNTAR }
                }

                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, SSColors.CyanGlow, Color.Transparent))))
            }

            when (modo) {
                ModoChatbot.ENTRADA -> ContenidoEntrada(viewModel = viewModel, modifier = Modifier.weight(1f))
                ModoChatbot.PREGUNTAR -> ContenidoChat(viewModel = asistenteViewModel, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SegmentoTab(texto: String, activo: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxHeight().clip(RoundedCornerShape(9.dp))
            .background(if (activo) Brush.linearGradient(listOf(SSColors.Cyan, SSColors.Purple)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (activo) SSColors.BgDeep else SSColors.TextMuted)
    }
}

// ══════════════════════════════════════════════════════════════
//  MODO: REGISTRAR ENTRADA (foto / galería / PDF)
// ══════════════════════════════════════════════════════════════

@Composable
private fun ContenidoEntrada(viewModel: ChatbotEntradaViewModel, modifier: Modifier = Modifier) {
    val estado by viewModel.estado.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fotoActual by remember { mutableStateOf<Bitmap?>(null) }
    var cargandoPdf by remember { mutableStateOf(false) }

    val lanzadorCamara = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            fotoActual = bitmap
            viewModel.procesarImagen(bitmap)
        }
    }

    val lanzadorGaleria = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (e: Exception) {
                null
            }
            if (bitmap != null) {
                fotoActual = bitmap
                viewModel.procesarImagen(bitmap)
            }
        }
    }

    val lanzadorPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                cargandoPdf = true
                fotoActual = null
                val paginas = withContext(Dispatchers.IO) {
                    renderizarPaginasPdf(context, uri)
                }
                cargandoPdf = false
                if (paginas.isEmpty()) {
                    viewModel.procesarImagenes(emptyList())
                } else {
                    fotoActual = paginas.first()
                    viewModel.procesarImagenes(paginas)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (val estadoActual = estado) {
            is ChatbotEntradaEstado.Idle -> {
                PantallaInicial(
                    cargandoPdf = cargandoPdf,
                    onTomarFoto = { lanzadorCamara.launch(null) },
                    onElegirGaleria = {
                        lanzadorGaleria.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSubirPdf = { lanzadorPdf.launch(arrayOf("application/pdf")) }
                )
            }

            is ChatbotEntradaEstado.ProcesandoImagen -> {
                TarjetaCargando(fotoActual = fotoActual, mensaje = "Analizando la nota de entrega con IA...")
            }

            is ChatbotEntradaEstado.ListoParaConfirmar -> {
                ListaConfirmacion(
                    items = estadoActual.items,
                    onNombreCambiado = viewModel::actualizarNombre,
                    onAjustarCantidad = viewModel::ajustarCantidad,
                    onAlternarIncluido = viewModel::alternarIncluido,
                    onEliminar = viewModel::eliminarItem,
                    onCategoriaCambiada = viewModel::actualizarCategoriaNueva,
                    onUnidadCambiada = viewModel::actualizarUnidadNueva,
                    onAjustarStockMinimo = viewModel::ajustarStockMinimoNuevo,
                    onConfirmar = viewModel::confirmarRegistro,
                    onCancelar = {
                        fotoActual = null
                        viewModel.resetEstado()
                    }
                )
            }

            is ChatbotEntradaEstado.Registrando -> {
                TarjetaCargando(fotoActual = fotoActual, mensaje = "Registrando entradas en el inventario...")
            }

            is ChatbotEntradaEstado.Completado -> {
                TarjetaResultado(
                    resultados = estadoActual.resultados,
                    onNuevaFoto = {
                        fotoActual = null
                        viewModel.resetEstado()
                    },
                    onCerrar = {
                        fotoActual = null
                        viewModel.resetEstado()
                    }
                )
            }

            is ChatbotEntradaEstado.Error -> {
                TarjetaError(
                    mensaje = estadoActual.mensaje,
                    onReintentar = {
                        fotoActual = null
                        viewModel.resetEstado()
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Renderiza cada página de un PDF (máximo 5) a un Bitmap usando PdfRenderer. */
private fun renderizarPaginasPdf(context: android.content.Context, uri: android.net.Uri, maxPaginas: Int = 5): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val totalPaginas = minOf(renderer.pageCount, maxPaginas)
                for (i in 0 until totalPaginas) {
                    renderer.openPage(i).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps += bitmap
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Si falla, se regresa lo que se haya alcanzado a renderizar (puede ser vacío).
    }
    return bitmaps
}

@Composable
private fun PantallaInicial(
    cargandoPdf: Boolean,
    onTomarFoto: () -> Unit,
    onElegirGaleria: () -> Unit,
    onSubirPdf: () -> Unit
) {
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
            ) { Text(text = "📋", fontSize = 28.sp) }

            Text(text = "ENTRADA POR NOTA", fontSize = 14.sp, fontWeight = FontWeight.Black, color = SSColors.Text, letterSpacing = 1.sp, textAlign = TextAlign.Center)
            Text(
                text = "Toma una foto, elige una imagen o sube un PDF de tu\nnota de entrega, remito o factura. La IA identifica\nlos productos y cantidades automáticamente.",
                fontSize = 11.sp, color = SSColors.TextMuted, textAlign = TextAlign.Center, lineHeight = 16.sp
            )

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(SSColors.Cyan, SSColors.Purple)))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTomarFoto() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "📸", fontSize = 16.sp)
                    Text(text = "Tomar foto", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSColors.BgDeep)
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(SSColors.Cyan.copy(alpha = 0.15f))
                    .border(1.dp, SSColors.Cyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onElegirGaleria() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "🖼️", fontSize = 16.sp)
                    Text(text = "Elegir de galería", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSColors.Cyan)
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(SSColors.Purple.copy(alpha = 0.15f))
                    .border(1.dp, SSColors.Purple.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable(
                        enabled = !cargandoPdf,
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { onSubirPdf() },
                contentAlignment = Alignment.Center
            ) {
                if (cargandoPdf) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = SSColors.Purple, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text(text = "Leyendo PDF...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSColors.Purple)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "📄", fontSize = 16.sp)
                        Text(text = "Subir PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSColors.Purple)
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaCargando(fotoActual: Bitmap?, mensaje: String) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        fotoActual?.let { bitmap ->
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, SSColors.CardBorder, RoundedCornerShape(14.dp))
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto de la nota",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(SSColors.Card).border(1.dp, SSColors.CardBorder, RoundedCornerShape(14.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(color = SSColors.Cyan, strokeWidth = 2.dp)
                Text(text = mensaje, fontSize = 12.sp, color = SSColors.TextMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ListaConfirmacion(
    items: List<ItemEntrada>,
    onNombreCambiado: (Long, String) -> Unit,
    onAjustarCantidad: (Long, Int) -> Unit,
    onAlternarIncluido: (Long) -> Unit,
    onEliminar: (Long) -> Unit,
    onCategoriaCambiada: (Long, String) -> Unit,
    onUnidadCambiada: (Long, String) -> Unit,
    onAjustarStockMinimo: (Long, Int) -> Unit,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    val seleccionados = items.count { it.incluido }
    val nuevos = items.count { it.incluido && !it.existente }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(SSColors.CyanDim).border(1.dp, SSColors.CyanGlow, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "Se detectaron ${items.size} producto(s)." +
                        if (nuevos > 0) " $nuevos no está(n) en tu catálogo y se crearán como nuevos." else "",
                fontSize = 11.sp, color = SSColors.Cyan, lineHeight = 16.sp
            )
        }

        items.forEach { item ->
            FilaItemEntrada(
                item = item,
                onNombreCambiado = { nuevo -> onNombreCambiado(item.id, nuevo) },
                onAjustarCantidad = { delta -> onAjustarCantidad(item.id, delta) },
                onAlternarIncluido = { onAlternarIncluido(item.id) },
                onEliminar = { onEliminar(item.id) },
                onCategoriaCambiada = { cat -> onCategoriaCambiada(item.id, cat) },
                onUnidadCambiada = { unidad -> onUnidadCambiada(item.id, unidad) },
                onAjustarStockMinimo = { delta -> onAjustarStockMinimo(item.id, delta) }
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                .background(
                    if (seleccionados > 0) Brush.horizontalGradient(listOf(SSColors.Cyan, SSColors.Purple))
                    else Brush.horizontalGradient(listOf(SSColors.CardBorder, SSColors.CardBorder))
                )
                .clickable(
                    enabled = seleccionados > 0,
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { onConfirmar() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Registrar $seleccionados entrada(s)",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (seleccionados > 0) SSColors.BgDeep else SSColors.TextMuted
            )
        }

        TextButton(onClick = onCancelar, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Cancelar y empezar de nuevo", fontSize = 11.sp, color = SSColors.TextMuted)
        }
    }
}

@Composable
private fun FilaItemEntrada(
    item: ItemEntrada,
    onNombreCambiado: (String) -> Unit,
    onAjustarCantidad: (Int) -> Unit,
    onAlternarIncluido: () -> Unit,
    onEliminar: () -> Unit,
    onCategoriaCambiada: (String) -> Unit,
    onUnidadCambiada: (String) -> Unit,
    onAjustarStockMinimo: (Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(SSColors.Card)
            .border(
                1.dp,
                if (!item.existente) SSColors.Purple.copy(alpha = 0.5f)
                else if (item.incluido) SSColors.CardBorder else SSColors.CardBorder.copy(alpha = 0.4f),
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (item.incluido) SSColors.Green.copy(alpha = 0.2f) else Color(0xFF1A1F2E))
                        .border(1.dp, if (item.incluido) SSColors.Green else SSColors.CardBorder, RoundedCornerShape(6.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAlternarIncluido() },
                    contentAlignment = Alignment.Center
                ) {
                    if (item.incluido) {
                        Text(text = "✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SSColors.Green)
                    }
                }

                OutlinedTextField(
                    value = item.nombre,
                    onValueChange = onNombreCambiado,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = SSColors.Text),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SSColors.Cyan,
                        unfocusedBorderColor = SSColors.CardBorder,
                        focusedTextColor = SSColors.Text,
                        unfocusedTextColor = SSColors.Text
                    )
                )

                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1F2E))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onEliminar() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 13.sp, color = Color(0xFFFF5C5C))
                }
            }

            if (!item.existente) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(SSColors.Purple.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text = "PRODUCTO NUEVO — se agregará al catálogo", fontSize = 9.sp, color = SSColors.Purple, fontWeight = FontWeight.Bold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Cantidad:", fontSize = 11.sp, color = SSColors.TextMuted)

                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1F2E)).border(1.dp, SSColors.CardBorder, RoundedCornerShape(8.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAjustarCantidad(-1) },
                    contentAlignment = Alignment.Center
                ) { Text(text = "−", fontSize = 15.sp, color = SSColors.Text) }

                Text(
                    text = item.cantidad.toString(),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SSColors.Cyan,
                    modifier = Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1F2E)).border(1.dp, SSColors.CardBorder, RoundedCornerShape(8.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAjustarCantidad(1) },
                    contentAlignment = Alignment.Center
                ) { Text(text = "+", fontSize = 15.sp, color = SSColors.Text) }
            }

            // Campos extra solo para productos que no existen en el catálogo
            if (!item.existente) {
                HorizontalDivider(color = SSColors.CardBorder)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = item.categoriaNueva,
                        onValueChange = onCategoriaCambiada,
                        label = { Text("Categoría", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = SSColors.Text),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SSColors.Purple,
                            unfocusedBorderColor = SSColors.CardBorder,
                            focusedTextColor = SSColors.Text,
                            unfocusedTextColor = SSColors.Text
                        )
                    )
                    OutlinedTextField(
                        value = item.unidadNueva,
                        onValueChange = onUnidadCambiada,
                        label = { Text("Unidad", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = SSColors.Text),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SSColors.Purple,
                            unfocusedBorderColor = SSColors.CardBorder,
                            focusedTextColor = SSColors.Text,
                            unfocusedTextColor = SSColors.Text
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Stock mínimo (para alertas):", fontSize = 10.sp, color = SSColors.TextMuted)

                    Box(
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1F2E)).border(1.dp, SSColors.CardBorder, RoundedCornerShape(6.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAjustarStockMinimo(-1) },
                        contentAlignment = Alignment.Center
                    ) { Text(text = "−", fontSize = 13.sp, color = SSColors.Text) }

                    Text(
                        text = item.stockMinimoNuevo.toString(),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SSColors.Purple,
                        modifier = Modifier.widthIn(min = 20.dp), textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1F2E)).border(1.dp, SSColors.CardBorder, RoundedCornerShape(6.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAjustarStockMinimo(1) },
                        contentAlignment = Alignment.Center
                    ) { Text(text = "+", fontSize = 13.sp, color = SSColors.Text) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaResultado(
    resultados: List<ResultadoItemEntrada>,
    onNuevaFoto: () -> Unit,
    onCerrar: () -> Unit
) {
    val exitosos = resultados.count { it.exito }
    val fallidos = resultados.size - exitosos
    val nuevosCreados = resultados.count { it.exito && it.productoNuevo }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0D1F35), Color(0xFF111827))))
                .border(1.dp, if (fallidos == 0) SSColors.Green.copy(alpha = 0.5f) else Color(0xFFFF5C5C).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = if (fallidos == 0) "✓" else "!", fontSize = 30.sp, color = if (fallidos == 0) SSColors.Green else Color(0xFFFF5C5C))
                Text(
                    text = "$exitosos de ${resultados.size} entradas registradas",
                    fontSize = 14.sp, fontWeight = FontWeight.Black, color = SSColors.Text, textAlign = TextAlign.Center
                )
                if (nuevosCreados > 0) {
                    Text(
                        text = "$nuevosCreados producto(s) nuevo(s) agregado(s) al catálogo",
                        fontSize = 10.sp, color = SSColors.Purple, textAlign = TextAlign.Center
                    )
                }
            }
        }

        resultados.forEach { resultado ->
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(SSColors.Card)
                    .border(
                        1.dp,
                        if (resultado.exito) SSColors.Green.copy(alpha = 0.35f) else Color(0xFFFF5C5C).copy(alpha = 0.35f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.size(26.dp).clip(CircleShape)
                            .background((if (resultado.exito) SSColors.Green else Color(0xFFFF5C5C)).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (resultado.exito) "✓" else "✕", fontSize = 12.sp, color = if (resultado.exito) SSColors.Green else Color(0xFFFF5C5C))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "${resultado.nombre} (${resultado.cantidad})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SSColors.Text)
                            if (resultado.productoNuevo) {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(SSColors.Purple.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(text = "NUEVO", fontSize = 8.sp, color = SSColors.Purple, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(text = resultado.detalle, fontSize = 10.sp, color = SSColors.TextMuted)
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(SSColors.Cyan, SSColors.Purple)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNuevaFoto() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Registrar otra entrada", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSColors.BgDeep)
        }

        TextButton(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Listo", fontSize = 11.sp, color = SSColors.TextMuted)
        }
    }
}

@Composable
private fun TarjetaError(mensaje: String, onReintentar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(SSColors.Card).border(1.dp, Color(0xFFFF5C5C).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "!", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF5C5C))
                Text(text = mensaje, fontSize = 12.sp, color = SSColors.TextMuted, textAlign = TextAlign.Center, lineHeight = 17.sp)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(SSColors.Cyan, SSColors.Purple)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onReintentar() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Intentar de nuevo", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SSColors.BgDeep)
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  MODO: PREGUNTAR (chat conversacional)
// ══════════════════════════════════════════════════════════════

@Composable
private fun ContenidoChat(viewModel: AsistenteChatViewModel, modifier: Modifier = Modifier) {
    val mensajes by viewModel.mensajes.collectAsState()
    val procesando by viewModel.procesando.collectAsState()
    var textoInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(mensajes.size, procesando) {
        if (mensajes.isNotEmpty()) {
            listState.animateScrollToItem(mensajes.size - 1 + if (procesando) 1 else 0)
        }
    }

    fun enviar() {
        val texto = textoInput.trim()
        if (texto.isNotBlank() && !procesando) {
            viewModel.enviarPregunta(texto)
            textoInput = ""
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(mensajes) { mensaje ->
                BurbujaChat(mensaje)
            }
            if (procesando) {
                item { IndicadorEscribiendo() }
            }
        }

        // ── Sugerencias rápidas (solo si es la conversación inicial) ──
        if (mensajes.size <= 1 && !procesando) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("¿Qué productos tengo en stock?", "¿Qué está por agotarse?", "¿Cuándo debo reabastecer?").forEach { sugerencia ->
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(SSColors.Card).border(1.dp, SSColors.CardBorder, RoundedCornerShape(20.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                viewModel.enviarPregunta(sugerencia)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(text = sugerencia, fontSize = 11.sp, color = SSColors.Cyan)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SSColors.CardBorder))

        // ── Barra de entrada estilo "pill" ──────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1421))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp))
                    .background(Color(0xFF1A1F2E))
                    .border(1.dp, SSColors.CardBorder, RoundedCornerShape(23.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = textoInput,
                    onValueChange = { textoInput = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = SSColors.Text),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(SSColors.Cyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { enviar() }),
                    decorationBox = { innerTextField ->
                        if (textoInput.isEmpty()) {
                            Text("Pregunta sobre tu inventario...", fontSize = 13.sp, color = SSColors.TextMuted)
                        }
                        innerTextField()
                    }
                )
            }

            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape)
                    .background(
                        if (textoInput.isNotBlank() && !procesando) Brush.linearGradient(listOf(SSColors.Cyan, SSColors.Purple))
                        else Brush.linearGradient(listOf(Color(0xFF1A1F2E), Color(0xFF1A1F2E)))
                    )
                    .clickable(
                        enabled = textoInput.isNotBlank() && !procesando,
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { enviar() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "➤", fontSize = 17.sp, color = if (textoInput.isNotBlank() && !procesando) SSColors.BgDeep else SSColors.TextMuted)
            }
        }
    }
}

@Composable
private fun AvatarAsistente() {
    Box(
        modifier = Modifier.size(26.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(SSColors.Cyan, SSColors.Purple))),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "🤖", fontSize = 12.sp)
    }
}

@Composable
private fun IndicadorEscribiendo() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        AvatarAsistente()
        Box(
            modifier = Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(SSColors.Card).border(1.dp, SSColors.CardBorder, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            CircularProgressIndicator(color = SSColors.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun BurbujaChat(mensaje: MensajeChat) {
    if (mensaje.esUsuario) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier.widthIn(max = 270.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(Brush.linearGradient(listOf(SSColors.Cyan, SSColors.Purple)))
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            ) {
                Text(text = mensaje.texto, fontSize = 13.sp, color = SSColors.BgDeep, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            AvatarAsistente()
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.widthIn(max = 270.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(SSColors.Card)
                    .border(1.dp, SSColors.CardBorder, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .padding(horizontal = 15.dp, vertical = 12.dp)
            ) {
                Text(text = mensaje.texto, fontSize = 13.sp, color = SSColors.Text, lineHeight = 19.sp)
            }
        }
    }
}