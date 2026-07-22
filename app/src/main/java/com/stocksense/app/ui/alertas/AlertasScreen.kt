package com.stocksense.app.ui.alertas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stocksense.app.data.model.Alerta
import com.stocksense.app.data.model.Producto
import com.stocksense.app.ui.login.SSColors
import com.stocksense.app.ui.login.bgGradient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertasScreen(
    onBack: () -> Unit,
    viewModel: AlertasViewModel = viewModel()
) {
    val productosEnAlerta by viewModel.productosEnAlerta.collectAsState()
    val historialAlertas by viewModel.historialAlertas.collectAsState()
    val sugerencias by viewModel.sugerencias.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(mensaje) {
        mensaje?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.limpiarMensaje()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AlertasTopBar(onBack = onBack)

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SSColors.Cyan, strokeWidth = 2.dp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    AlertasActivasCard(
                        productos = productosEnAlerta,
                        sugerencias = sugerencias,
                        onActualizarUmbral = { productoId, nuevoUmbral ->
                            viewModel.actualizarUmbral(productoId, nuevoUmbral)
                        }
                    )

                    HistorialAlertasCard(alertas = historialAlertas)

                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
fun AlertasTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080E1A))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1F2E))
                .border(1.dp, SSColors.CardBorder, RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = SSColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        Text(
            text = "ALERTAS",
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = SSColors.Cyan,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun AlertasActivasCard(
    productos: List<Producto>,
    sugerencias: Map<String, Int>,
    onActualizarUmbral: (String, Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SSColors.Card)
            .border(1.dp, SSColors.CardBorder, RoundedCornerShape(14.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF3B5C), CircleShape))
                Text(
                    text = "ALERTAS ACTIVAS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SSColors.Text,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${productos.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF3B5C)
                )
            }

            HorizontalDivider(color = SSColors.CardBorder)

            if (productos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SSColors.Green,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Sin alertas activas\nTodo el inventario está en niveles normales",
                            fontSize = 12.sp,
                            color = SSColors.TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                productos.forEachIndexed { index, producto ->
                    AlertaProductoRow(
                        producto = producto,
                        sugerencia = sugerencias[producto.id] ?: producto.stockMinimo,
                        onActualizarUmbral = { nuevoUmbral ->
                            onActualizarUmbral(producto.id, nuevoUmbral)
                        }
                    )
                    if (index < productos.size - 1) HorizontalDivider(color = SSColors.CardBorder)
                }
            }
        }
    }
}

@Composable
fun AlertaProductoRow(
    producto: Producto,
    sugerencia: Int,
    onActualizarUmbral: (Int) -> Unit
) {
    var editandoUmbral by remember { mutableStateOf(false) }
    var umbralTexto by remember(producto.stockMinimo) { mutableStateOf(producto.stockMinimo.toString()) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF3B5C),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = producto.nombre,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF3B5C),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${producto.stock} ${producto.unidad}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF3B5C)
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SSColors.CyanDim)
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = "SUGERENCIA DE REABASTO",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = SSColors.Cyan,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Reabastecer $sugerencia ${producto.unidad} (según consumo reciente)",
                    fontSize = 11.sp,
                    color = SSColors.Text
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Umbral mínimo:",
                fontSize = 10.sp,
                color = SSColors.TextMuted
            )

            if (editandoUmbral) {
                OutlinedTextField(
                    value = umbralTexto,
                    onValueChange = { nuevoValor ->
                        if (nuevoValor.all { it.isDigit() }) umbralTexto = nuevoValor
                    },
                    modifier = Modifier.width(70.dp).height(48.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = SSColors.Text),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                TextButton(onClick = {
                    val nuevoUmbral = umbralTexto.toIntOrNull()
                    if (nuevoUmbral != null && nuevoUmbral >= 0) {
                        onActualizarUmbral(nuevoUmbral)
                    }
                    editandoUmbral = false
                    focusManager.clearFocus()
                }) {
                    Text("Guardar", fontSize = 11.sp, color = SSColors.Cyan)
                }
            } else {
                Text(
                    text = "${producto.stockMinimo} ${producto.unidad}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SSColors.Text
                )
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Editar umbral",
                    tint = SSColors.TextMuted,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { editandoUmbral = true }
                )
            }
        }
    }
}

@Composable
fun HistorialAlertasCard(alertas: List<Alerta>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SSColors.Card)
            .border(1.dp, SSColors.CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = "HISTORIAL DE ALERTAS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SSColors.Text,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (alertas.isEmpty()) {
                Text(
                    text = "Sin alertas registradas todavía",
                    fontSize = 12.sp,
                    color = SSColors.TextMuted
                )
            } else {
                alertas.take(10).forEachIndexed { index, alerta ->
                    HistorialAlertaRow(alerta = alerta)
                    if (index < alertas.take(10).size - 1) HorizontalDivider(color = SSColors.CardBorder)
                }
            }
        }
    }
}

@Composable
fun HistorialAlertaRow(alerta: Alerta) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alerta.productoNombre,
                fontSize = 11.sp,
                color = SSColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatearFechaHora(alerta.timestamp)} · stock: ${alerta.stockAlMomento}",
                fontSize = 9.sp,
                color = SSColors.TextMuted
            )
        }
        Text(
            text = "+${alerta.sugerenciaReabasto}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = SSColors.Cyan
        )
    }
}

private fun formatearFechaHora(timestamp: Long): String {
    return try {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        "--/-- --:--"
    }
}