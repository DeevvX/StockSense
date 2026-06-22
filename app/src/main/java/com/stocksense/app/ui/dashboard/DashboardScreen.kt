package com.stocksense.app.ui.dashboard

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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.stocksense.app.BuildConfig
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.model.Producto
import com.stocksense.app.ui.login.SSColors
import com.stocksense.app.ui.login.bgGradient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val auth = FirebaseAuth.getInstance()
    val userName = auth.currentUser?.displayName?.split(" ")?.firstOrNull() ?: "Usuario"

    val productos by viewModel.productos.collectAsState()
    val movimientos by viewModel.movimientos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Procesador de imágenes IoT con OpenAI Vision
    val procesadorViewModel: ProcesadorImagenesViewModel = viewModel(
        factory = ProcesadorImagenesViewModelFactory(BuildConfig.OPENAI_API_KEY)
    )
    val procesandoImagen by procesadorViewModel.procesando.collectAsState()
    val ultimoResultadoIA by procesadorViewModel.ultimoResultado.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = SSColors.Cyan,
                    strokeWidth = 2.dp
                )
            }

            productos.isEmpty() && movimientos.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TopBar(userName = userName, onLogout = onLogout)
                    Spacer(Modifier.height(120.dp))
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = SSColors.TextMuted,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Sin datos aún\nAgrega productos en Firebase",
                        color = SSColors.TextMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    TopBar(userName = userName, onLogout = onLogout)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StatsRow(
                            totalProductos = viewModel.totalProductos,
                            totalAlertas = viewModel.totalAlertas,
                            movimientosHoy = viewModel.movimientosHoy
                        )
                        StockCard(productos = viewModel.todosLosProductos)
                        MovimientosCard(movimientos = viewModel.ultimosMovimientos)
                        IoTStatusCard()

                        if (procesandoImagen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SSColors.CyanDim)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = SSColors.Cyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Procesando imagen con IA...", fontSize = 11.sp, color = SSColors.Cyan)
                                }
                            }
                        }

                        ultimoResultadoIA?.let { resultado ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SSColors.Card)
                                    .border(1.dp, SSColors.CardBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(resultado, fontSize = 11.sp, color = SSColors.TextMuted)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────
@Composable
fun TopBar(userName: String, onLogout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080E1A))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SSColors.CyanDim)
                    .border(1.dp, SSColors.CyanGlow, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    tint = SSColors.Cyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "StockSense",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = SSColors.Cyan,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Hola, $userName",
                    fontSize = 10.sp,
                    color = SSColors.TextMuted
                )
            }
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1F2E))
                .border(1.dp, SSColors.CardBorder, RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onLogout() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ExitToApp,
                contentDescription = "Cerrar sesión",
                tint = SSColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, SSColors.CyanGlow, Color.Transparent)
                )
            )
    )

    Spacer(Modifier.height(14.dp))
}

// ── Stats Row ─────────────────────────────────────────────────────────
@Composable
fun StatsRow(totalProductos: Int, totalAlertas: Int, movimientosHoy: Int) {
    val stats = listOf(
        Triple(totalProductos.toString(), "Productos", SSColors.Cyan),
        Triple(totalAlertas.toString(), "Alertas", Color(0xFFFF3B5C)),
        Triple(movimientosHoy.toString(), "Hoy", SSColors.Green),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        stats.forEach { (valor, label, color) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SSColors.Card)
                    .border(1.dp, SSColors.CardBorder, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = valor, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color)
                    Text(text = label, fontSize = 10.sp, color = SSColors.TextMuted)
                }
            }
        }
    }
}

// ── Stock Card ────────────────────────────────────────────────────────
@Composable
fun StockCard(productos: List<Producto>) {
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
                    text = "STOCK EN TIEMPO REAL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SSColors.Text,
                    letterSpacing = 0.5.sp
                )
            }

            HorizontalDivider(color = SSColors.CardBorder)

            if (productos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin productos en el catálogo",
                        fontSize = 12.sp,
                        color = SSColors.TextMuted
                    )
                }
            } else {
                productos.forEachIndexed { index, producto ->
                    ProductoRow(producto = producto)
                    if (index < productos.size - 1) HorizontalDivider(color = SSColors.CardBorder)
                }
            }
        }
    }
}

@Composable
fun ProductoRow(producto: Producto) {
    val barColor = if (producto.stockBajo) Color(0xFFFF3B5C) else SSColors.Green
    val textColor = if (producto.stockBajo) Color(0xFFFF3B5C) else SSColors.Text

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = producto.nombre,
                fontSize = 11.sp,
                color = textColor,
                fontWeight = if (producto.stockBajo) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${producto.stock} ${producto.unidad}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SSColors.CardBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(producto.porcentajeStock)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }

        if (producto.stockBajo) {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF3B5C),
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = "STOCK BAJO — mínimo ${producto.stockMinimo} ${producto.unidad}",
                    fontSize = 9.sp,
                    color = Color(0xFFFF3B5C)
                )
            }
        }
    }
}

// ── Movimientos Card ──────────────────────────────────────────────────
@Composable
fun MovimientosCard(movimientos: List<Movimiento>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SSColors.Card)
            .border(1.dp, SSColors.CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                text = "ÚLTIMOS MOVIMIENTOS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SSColors.Text,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (movimientos.isEmpty()) {
                Text(
                    text = "Sin movimientos registrados",
                    fontSize = 12.sp,
                    color = SSColors.TextMuted
                )
            } else {
                movimientos.forEachIndexed { index, mov ->
                    val isEntrada = mov.esEntrada
                    val color = if (isEntrada) SSColors.Green else Color(0xFFFF3B5C)
                    val icon = if (isEntrada) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mov.productoNombre,
                                fontSize = 11.sp,
                                color = SSColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatearFecha(mov.timestamp)} · ${mov.tipo}",
                                fontSize = 9.sp,
                                color = SSColors.TextMuted
                            )
                        }

                        Text(
                            text = "${if (mov.cantidad > 0 && isEntrada) "+" else ""}${mov.cantidad}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }

                    if (index < movimientos.size - 1) HorizontalDivider(color = SSColors.CardBorder)
                }
            }
        }
    }
}

// ── IoT Status Card ───────────────────────────────────────────────────
@Composable
fun IoTStatusCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0D1F2D), Color(0xFF0A1628))))
            .border(1.dp, SSColors.CyanGlow, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SSColors.CyanDim),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = SSColors.Cyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "LOGITECH C920", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SSColors.Text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(6.dp).background(SSColors.Green, CircleShape))
                    Text(text = "Conectado — Almacén Principal", fontSize = 9.sp, color = SSColors.Green)
                }
            }

            Text(text = "MQTT OK", fontSize = 9.sp, color = SSColors.TextMuted)
        }
    }
}

private fun formatearFecha(timestamp: Long): String {
    return try {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        "--:--"
    }
}