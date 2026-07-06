package com.stocksense.app.ui.graficas

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.core.chart.column.ColumnChart
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.stocksense.app.ui.login.SSColors
import com.stocksense.app.ui.login.bgGradient

@Composable
fun GraficasScreen(
    onBack: () -> Unit,
    viewModel: GraficasViewModel = viewModel()
) {
    val stockPorProducto by viewModel.stockPorProducto.collectAsState()
    val movimientosPorDia by viewModel.movimientosPorDia.collectAsState()
    val distribucionCategorias by viewModel.distribucionCategorias.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(bgGradient)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = SSColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "GRÁFICAS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = SSColors.Cyan,
                    letterSpacing = 1.sp
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    GraficaStockActual(stockPorProducto = stockPorProducto)
                    GraficaMovimientosPorDia(movimientosPorDia = movimientosPorDia)
                    GraficaDistribucionCategorias(distribucion = distribucionCategorias)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun GraficaStockActual(stockPorProducto: List<StockPorProducto>) {
    GraficaCard(titulo = "STOCK ACTUAL POR PRODUCTO") {
        if (stockPorProducto.isEmpty()) {
            EstadoVacio("Sin productos registrados")
            return@GraficaCard
        }

        val modelProducer = remember { ChartEntryModelProducer() }

        LaunchedEffect(stockPorProducto) {
            modelProducer.setEntries(
                stockPorProducto.mapIndexed { index, producto ->
                    listOf(entryOf(index.toFloat(), producto.stock.toFloat()))
                }
            )
        }

        val columnas = stockPorProducto.map { producto ->
            lineComponent(
                color = Color(producto.color),
                thickness = 20.dp,
                shape = Shapes.roundedCornerShape(allPercent = 25)
            )
        }

        Chart(
            chart = columnChart(
                columns = columnas,
                mergeMode = ColumnChart.MergeMode.Grouped
            ),
            chartModelProducer = modelProducer,
            startAxis = rememberStartAxis(
                label = textComponent(color = SSColors.TextMuted, textSize = 9.sp)
            ),
            bottomAxis = rememberBottomAxis(
                label = textComponent(color = SSColors.TextMuted, textSize = 9.sp),
                valueFormatter = { value, _ ->
                    stockPorProducto.getOrNull(value.toInt())?.nombre ?: ""
                }
            ),
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )

        Spacer(Modifier.height(8.dp))
        stockPorProducto.forEach { producto ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).background(Color(producto.color), CircleShape))
                Text(
                    text = "${producto.nombre}: ${producto.stock} uds.",
                    fontSize = 10.sp,
                    color = SSColors.TextMuted
                )
            }
        }
    }
}

@Composable
fun GraficaMovimientosPorDia(movimientosPorDia: List<MovimientoPorDia>) {
    GraficaCard(titulo = "MOVIMIENTOS ÚLTIMOS 7 DÍAS") {
        if (movimientosPorDia.all { it.entradas == 0 && it.salidas == 0 }) {
            EstadoVacio("Sin movimientos en los últimos 7 días")
            return@GraficaCard
        }

        val modelProducer = remember { ChartEntryModelProducer() }

        LaunchedEffect(movimientosPorDia) {
            modelProducer.setEntries(
                listOf(
                    movimientosPorDia.mapIndexed { index, dia ->
                        entryOf(index.toFloat(), dia.entradas.toFloat())
                    },
                    movimientosPorDia.mapIndexed { index, dia ->
                        entryOf(index.toFloat(), dia.salidas.toFloat())
                    }
                )
            )
        }

        Chart(
            chart = columnChart(
                columns = listOf(
                    lineComponent(
                        color = SSColors.Green,
                        thickness = 12.dp,
                        shape = Shapes.roundedCornerShape(allPercent = 25)
                    ),
                    lineComponent(
                        color = Color(0xFFFF3B5C),
                        thickness = 12.dp,
                        shape = Shapes.roundedCornerShape(allPercent = 25)
                    )
                ),
                mergeMode = ColumnChart.MergeMode.Grouped
            ),
            chartModelProducer = modelProducer,
            startAxis = rememberStartAxis(
                label = textComponent(color = SSColors.TextMuted, textSize = 9.sp)
            ),
            bottomAxis = rememberBottomAxis(
                label = textComponent(color = SSColors.TextMuted, textSize = 9.sp),
                valueFormatter = { value, _ ->
                    movimientosPorDia.getOrNull(value.toInt())?.dia ?: ""
                }
            ),
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(8.dp).background(SSColors.Green, CircleShape))
                Text("Entradas", fontSize = 10.sp, color = SSColors.TextMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF3B5C), CircleShape))
                Text("Salidas", fontSize = 10.sp, color = SSColors.TextMuted)
            }
        }
    }
}

@Composable
fun GraficaDistribucionCategorias(distribucion: List<DistribucionCategoria>) {
    GraficaCard(titulo = "DISTRIBUCIÓN POR CATEGORÍA") {
        if (distribucion.isEmpty()) {
            EstadoVacio("Sin datos de categorías")
            return@GraficaCard
        }

        distribucion.forEach { cat ->
            Column(modifier = Modifier.padding(vertical = 5.dp)) {
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
                        Box(modifier = Modifier.size(8.dp).background(Color(cat.color), CircleShape))
                        Text(text = cat.categoria, fontSize = 11.sp, color = SSColors.Text, maxLines = 1)
                    }
                    Text(
                        text = "${cat.cantidad} uds · ${"%.1f".format(cat.porcentaje)}%",
                        fontSize = 10.sp,
                        color = SSColors.TextMuted
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1A1F2E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((cat.porcentaje / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(cat.color))
                    )
                }
            }
        }
    }
}

@Composable
fun GraficaCard(titulo: String, content: @Composable ColumnScope.() -> Unit) {
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
                text = titulo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SSColors.Text,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun EstadoVacio(mensaje: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = mensaje, fontSize = 12.sp, color = SSColors.TextMuted, textAlign = TextAlign.Center)
    }
}