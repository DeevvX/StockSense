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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.stocksense.app.ui.login.SSColors

// ── Modelos de datos de prueba ───────────────────────────────────────
data class ProductoStock(
    val nombre: String,
    val stock: Int,
    val minimo: Int,
    val categoria: String
)

data class Movimiento(
    val hora: String,
    val producto: String,
    val tipo: String,
    val cantidad: Int
)

val productosMuestra = listOf(
    ProductoStock("Shampoo 500ml",       8,  10, "Personal"),
    ProductoStock("Crema Hidratante",    23, 10, "Personal"),
    ProductoStock("Paracetamol 500mg",   3,  15, "Medicina"),
    ProductoStock("Alcohol 96°",         12, 10, "Limpieza"),
    ProductoStock("Gasas Estériles",     5,  20, "Medicina"),
)

val movimientosMuestra = listOf(
    Movimiento("09:14", "Shampoo 500ml",       "salida",  -2),
    Movimiento("08:52", "Crema Hidratante",    "entrada", +10),
    Movimiento("08:30", "Paracetamol 500mg",  "salida",  -5),
)

// ── Dashboard Screen ─────────────────────────────────────────────────
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onNavigateToReportes: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val userName = auth.currentUser?.displayName?.split(" ")?.firstOrNull() ?: "Usuario"

    val bgGradient = Brush.verticalGradient(
        colors = listOf(SSColors.BgDeep, SSColors.BgMid, SSColors.BgSurface)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Top Bar ──────────────────────────────────────────────
            TopBar(userName = userName, onLogout = onLogout, onNavigateToReportes = onNavigateToReportes)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Stats Row ────────────────────────────────────────
                StatsRow()

                // ── Stock en tiempo real ─────────────────────────────
                StockCard()

                // ── Últimos movimientos ──────────────────────────────
                MovimientosCard()

                // ── IoT Status ───────────────────────────────────────
                IoTStatusCard()

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────
@Composable
fun TopBar(userName: String, onLogout: () -> Unit, onNavigateToReportes: () -> Unit) {
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1A1F2E))
                    .border(1.dp, SSColors.CardBorder, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNavigateToReportes() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📄", fontSize = 16.sp)
            }

            // Botón logout
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
    }

    // Línea divisora con glow
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
fun StatsRow() {
    val stats = listOf(
        Triple("142", "Productos", SSColors.Cyan),
        Triple("3",   "Alertas",   Color(0xFFFF3B5C)),
        Triple("89",  "Hoy",       SSColors.Green),
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
                    Text(
                        text = valor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = color
                    )
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = SSColors.TextMuted
                    )
                }
            }
        }
    }
}

// ── Stock Card ────────────────────────────────────────────────────────
@Composable
fun StockCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SSColors.Card)
            .border(1.dp, SSColors.CardBorder, RoundedCornerShape(14.dp))
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFFF3B5C), CircleShape)
                )
                Text(
                    text = "STOCK EN TIEMPO REAL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SSColors.Text,
                    letterSpacing = 0.5.sp
                )
            }

            HorizontalDivider(color = SSColors.CardBorder)

            // Productos
            productosMuestra.forEachIndexed { index, producto ->
                ProductoRow(producto = producto)
                if (index < productosMuestra.size - 1) {
                    HorizontalDivider(color = SSColors.CardBorder)
                }
            }
        }
    }
}

@Composable
fun ProductoRow(producto: ProductoStock) {
    val isBajo = producto.stock <= producto.minimo
    val porcentaje = (producto.stock.toFloat() / (producto.minimo * 2f)).coerceIn(0f, 1f)
    val barColor = if (isBajo) Color(0xFFFF3B5C) else SSColors.Green
    val textColor = if (isBajo) Color(0xFFFF3B5C) else SSColors.Text

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
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
                fontWeight = if (isBajo) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${producto.stock} u",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }

        Spacer(Modifier.height(6.dp))

        // Barra de progreso
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SSColors.CardBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(porcentaje)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }

        if (isBajo) {
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
                    text = "STOCK BAJO — mínimo ${producto.minimo} u",
                    fontSize = 9.sp,
                    color = Color(0xFFFF3B5C)
                )
            }
        }
    }
}

// ── Movimientos Card ──────────────────────────────────────────────────
@Composable
fun MovimientosCard() {
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

            movimientosMuestra.forEachIndexed { index, mov ->
                val isEntrada = mov.tipo == "entrada"
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
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mov.producto,
                            fontSize = 11.sp,
                            color = SSColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${mov.hora} · ${mov.tipo}",
                            fontSize = 9.sp,
                            color = SSColors.TextMuted
                        )
                    }

                    Text(
                        text = "${if (mov.cantidad > 0) "+" else ""}${mov.cantidad}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }

                if (index < movimientosMuestra.size - 1) {
                    HorizontalDivider(color = SSColors.CardBorder)
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
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0D1F2D), Color(0xFF0A1628))
                )
            )
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
                Text(
                    text = "ESP32-CAM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SSColors.Text
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(SSColors.Green, CircleShape)
                    )
                    Text(
                        text = "Conectado — Almacén Principal",
                        fontSize = 9.sp,
                        color = SSColors.Green
                    )
                }
            }

            Text(
                text = "MQTT OK",
                fontSize = 9.sp,
                color = SSColors.TextMuted
            )
        }
    }
}
