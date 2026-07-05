package com.stocksense.app.ui.historial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.ui.login.SSColors
import com.stocksense.app.ui.login.bgGradient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistorialScreen(
    onBack: () -> Unit,
    viewModel: HistorialViewModel = viewModel()
) {
    val movimientos by viewModel.movimientosFiltrados.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val busqueda by viewModel.busqueda.collectAsState()
    val filtroTipo by viewModel.filtroTipo.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HistorialTopBar(onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // Buscador
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { viewModel.onBusquedaCambia(it) },
                    placeholder = {
                        Text(
                            text = "Buscar producto...",
                            fontSize = 12.sp,
                            color = SSColors.TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = SSColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SSColors.Cyan,
                        unfocusedBorderColor = SSColors.CardBorder,
                        focusedTextColor = SSColors.Text,
                        unfocusedTextColor = SSColors.Text,
                        cursorColor = SSColors.Cyan,
                        focusedContainerColor = SSColors.Card,
                        unfocusedContainerColor = SSColors.Card
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )

                // Filtros de tipo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FiltroTipo.entries.forEach { filtro ->
                        val seleccionado = filtroTipo == filtro
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (seleccionado) SSColors.Cyan else SSColors.Card)
                                .border(
                                    1.dp,
                                    if (seleccionado) SSColors.Cyan else SSColors.CardBorder,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { viewModel.onFiltroTipoCambia(filtro) }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filtro.etiqueta,
                                fontSize = 11.sp,
                                fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
                                color = if (seleccionado) Color(0xFF060D1F) else SSColors.TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SSColors.Cyan, strokeWidth = 2.dp)
                    }
                }

                movimientos.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (busqueda.isEmpty() && filtroTipo == FiltroTipo.TODOS)
                                "Sin movimientos registrados todavía"
                            else
                                "Sin resultados para \"$busqueda\"",
                            fontSize = 13.sp,
                            color = SSColors.TextMuted
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Text(
                                text = "${movimientos.size} movimiento(s)",
                                fontSize = 10.sp,
                                color = SSColors.TextMuted,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        items(movimientos, key = { it.id }) { movimiento ->
                            MovimientoItem(movimiento = movimiento)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistorialTopBar(onBack: () -> Unit) {
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
            text = "HISTORIAL DE MOVIMIENTOS",
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = SSColors.Cyan,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun MovimientoItem(movimiento: Movimiento) {
    val isEntrada = movimiento.tipo.lowercase() == "entrada"
    val color = if (isEntrada) SSColors.Green else Color(0xFFFF3B5C)
    val icon = if (isEntrada) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SSColors.Card)
            .border(1.dp, SSColors.CardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movimiento.productoNombre,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SSColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatearFechaCompleta(movimiento.timestamp)} · ${movimiento.tipo}",
                    fontSize = 10.sp,
                    color = SSColors.TextMuted
                )
            }

            Text(
                text = "${if (isEntrada) "+" else "-"}${movimiento.cantidad}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

private fun formatearFechaCompleta(timestamp: Long): String {
    return try {
        SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        "--/--/-- --:--"
    }
}