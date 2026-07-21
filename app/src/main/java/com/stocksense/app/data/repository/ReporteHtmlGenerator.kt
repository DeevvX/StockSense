package com.stocksense.app.data.repository

import com.stocksense.app.data.model.Alerta
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.model.Producto

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt


object ReporteHtmlGenerator {

    private const val PAGE_WIDTH = 794
    private const val PAGE_HEIGHT = 1123

    private const val NAVY = "#0B1220"
    private const val NAVY_2 = "#111C30"
    private const val CYAN = "#06B6D4"
    private const val BLUE = "#2563EB"
    private const val GREEN = "#10B981"
    private const val RED = "#EF4444"
    private const val AMBER = "#F59E0B"
    private const val SLATE = "#64748B"
    private const val BORDER = "#DCE4EE"
    private const val SOFT = "#F6F8FB"

    private val locale = Locale("es", "MX")
    private val integerFormat = NumberFormat.getIntegerInstance(locale)

    fun generar(
        productos: List<Producto>,
        movimientos: List<Movimiento>,
        alertas: List<Alerta>,
        analisisIa: String,
        fechaGeneracion: Long = System.currentTimeMillis()
    ): String {
        val sortedProducts = productos.sortedBy { it.nombre.lowercase(locale) }
        val recentMovements = movimientos.sortedByDescending { it.timestamp }
        val predictions = calculatePredictions(
            productos = sortedProducts,
            movimientos = movimientos,
            now = fechaGeneracion
        )

        val pages = mutableListOf<PageSpec>()

        pages += PageSpec(
            title = "Resumen ejecutivo",
            subtitle = "Panorama general del inventario",
            body = overviewPage(
                productos = sortedProducts,
                alertas = alertas,
                analisisIa = analisisIa,
                fecha = fechaGeneracion
            ),
            showHeader = false
        )

        pages += PageSpec(
            title = "Analítica de inventario",
            subtitle = "Stock, rotación y comportamiento reciente",
            body = analyticsPage(
                productos = sortedProducts,
                movimientos = movimientos,
                fecha = fechaGeneracion
            )
        )

        pages += PageSpec(
            title = "Pronóstico y categorías",
            subtitle = "Distribución y proyección de existencias",
            body = forecastPage(
                productos = sortedProducts,
                predictions = predictions
            )
        )

        sortedProducts.chunked(12).forEachIndexed { index, chunk ->
            pages += PageSpec(
                title = "Inventario",
                subtitle = if (sortedProducts.size > 12) {
                    "Detalle de productos · ${index + 1} de " +
                            ceil(sortedProducts.size / 12.0).toInt()
                } else {
                    "Detalle de productos registrados"
                },
                body = inventoryTable(chunk)
            )
        }

        predictions.chunked(11).forEachIndexed { index, chunk ->
            pages += PageSpec(
                title = "Riesgo de agotamiento",
                subtitle = if (predictions.size > 11) {
                    "Pronóstico de consumo · ${index + 1} de " +
                            ceil(predictions.size / 11.0).toInt()
                } else {
                    "Estimación basada en salidas de los últimos 30 días"
                },
                body = predictionTable(chunk)
            )
        }

        recentMovements.take(24).chunked(10).forEachIndexed { index, chunk ->
            pages += PageSpec(
                title = "Movimientos recientes",
                subtitle = if (recentMovements.size > 10) {
                    "Últimas operaciones · ${index + 1} de " +
                            ceil(minOf(recentMovements.size, 24) / 10.0).toInt()
                } else {
                    "Últimas operaciones registradas"
                },
                body = movementsAndAlerts(
                    movements = chunk,
                    alerts = if (index == 0) alertas.take(5) else emptyList()
                )
            )
        }

        if (recentMovements.isEmpty()) {
            pages += PageSpec(
                title = "Movimientos recientes",
                subtitle = "Últimas operaciones registradas",
                body = movementsAndAlerts(emptyList(), alertas.take(5))
            )
        }

        val totalPages = pages.size

        return buildString {
            append("<!DOCTYPE html><html lang=\"es\"><head>")
            append("<meta charset=\"UTF-8\">")
            append(
                "<meta name=\"viewport\" content=\"width=$PAGE_WIDTH," +
                        " initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
            )
            append("<style>${css()}</style>")
            append("</head><body><div id=\"pdf-root\">")

            pages.forEachIndexed { index, page ->
                append(
                    renderPage(
                        spec = page,
                        pageNumber = index + 1,
                        totalPages = totalPages,
                        generatedAt = fechaGeneracion
                    )
                )
            }

            append("</div></body></html>")
        }
    }

    private fun renderPage(
        spec: PageSpec,
        pageNumber: Int,
        totalPages: Int,
        generatedAt: Long
    ): String {
        return buildString {
            append("<section class=\"pdf-page\">")

            if (spec.showHeader) {
                append(
                    """
                    <table class="page-header" cellpadding="0" cellspacing="0">
                        <tr>
                            <td>
                                <div class="header-brand">StockSense</div>
                                <div class="header-caption">Reporte de inventario</div>
                            </td>
                            <td class="header-meta">
                                ${escape(formatDate(generatedAt))}
                            </td>
                        </tr>
                    </table>
                    """.trimIndent()
                )

                append(
                    """
                    <div class="page-title-block">
                        <div class="eyebrow">STOCKSENSE ANALYTICS</div>
                        <div class="page-title">${escape(spec.title)}</div>
                        <div class="page-subtitle">${escape(spec.subtitle)}</div>
                    </div>
                    """.trimIndent()
                )
            }

            append("<main class=\"page-content ${if (!spec.showHeader) "cover-content" else ""}\">")
            append(spec.body)
            append("</main>")

            append(
                """
                <table class="page-footer" cellpadding="0" cellspacing="0">
                    <tr>
                        <td>StockSense · Inteligencia para tu inventario</td>
                        <td class="page-number">Página $pageNumber de $totalPages</td>
                    </tr>
                </table>
                """.trimIndent()
            )

            append("</section>")
        }
    }

    private fun overviewPage(
        productos: List<Producto>,
        alertas: List<Alerta>,
        analisisIa: String,
        fecha: Long
    ): String {
        val totalUnits = productos.sumOf { it.stock }
        val lowStock = productos.count { it.stockBajo || it.stock <= it.stockMinimo }
        val healthy = (productos.size - lowStock).coerceAtLeast(0)
        val coverage = if (productos.isEmpty()) 0 else {
            (healthy * 100f / productos.size).roundToInt()
        }

        val topAlerts = alertas.sortedByDescending { it.timestamp }.take(3)

        return """
            <div class="cover-hero">
                <table class="cover-brand-table" cellpadding="0" cellspacing="0">
                    <tr>
                        <td>
                            <div class="cover-brand">StockSense</div>
                            <div class="cover-kicker">INVENTORY INTELLIGENCE</div>
                        </td>
                        <td class="cover-date">
                            REPORTE EJECUTIVO<br>
                            <strong>${escape(formatDate(fecha))}</strong>
                        </td>
                    </tr>
                </table>

                <div class="cover-title">Decisiones de inventario<br>con datos claros.</div>
                <div class="cover-description">
                    Estado actual, rotación, alertas y pronóstico de existencias
                    en un solo informe.
                </div>
            </div>

            <table class="kpi-grid" cellpadding="0" cellspacing="0">
                <tr>
                    ${kpi("Productos", productos.size.toString(), "SKUs activos", CYAN)}
                    ${kpi("Unidades", integerFormat.format(totalUnits), "Disponibles", BLUE)}
                </tr>
                <tr>
                    ${kpi("Stock bajo", lowStock.toString(), "Requieren atención", RED)}
                    ${kpi("Cobertura", "$coverage%", "Productos saludables", GREEN)}
                </tr>
            </table>

            <table class="summary-grid" cellpadding="0" cellspacing="0">
                <tr>
                    <td class="summary-main">
                        <div class="panel">
                            <div class="panel-label">ANÁLISIS IA</div>
                            <div class="ai-text">
                                ${nl2br(
            analisisIa.ifBlank {
                "No hay análisis disponible para este periodo. " +
                        "El reporte conserva el estado cuantitativo del inventario."
            }
        )}
                            </div>
                        </div>
                    </td>
                    <td class="summary-side">
                        <div class="panel alert-panel">
                            <div class="panel-label">ALERTAS PRIORITARIAS</div>
                            ${
            if (topAlerts.isEmpty()) {
                "<div class=\"empty-compact\">Sin alertas activas.</div>"
            } else {
                topAlerts.joinToString("") { alert ->
                    """
                                        <div class="alert-item">
                                            <div class="alert-product">${escape(shorten(alert.productoNombre, 23))}</div>
                                            <div class="alert-detail">
                                                Stock ${alert.stockAlMomento} · mínimo ${alert.stockMinimo}
                                            </div>
                                        </div>
                                        """.trimIndent()
                }
            }
        }
                        </div>
                    </td>
                </tr>
            </table>
        """.trimIndent()
    }

    private fun analyticsPage(
        productos: List<Producto>,
        movimientos: List<Movimiento>,
        fecha: Long
    ): String {
        return """
            <div class="section-card">
                <table class="card-heading" cellpadding="0" cellspacing="0">
                    <tr>
                        <td>
                            <div class="card-title">Stock por producto</div>
                            <div class="card-subtitle">Top 8 por unidades disponibles</div>
                        </td>
                        <td class="card-tag">UNIDADES</td>
                    </tr>
                </table>
                ${stockChart(productos)}
            </div>

            <div class="section-card lower-card">
                <table class="card-heading" cellpadding="0" cellspacing="0">
                    <tr>
                        <td>
                            <div class="card-title">Entradas y salidas</div>
                            <div class="card-subtitle">Comportamiento de los últimos 7 días</div>
                        </td>
                        <td class="card-tag">7 DÍAS</td>
                    </tr>
                </table>
                ${flowChart(movimientos, fecha)}
            </div>
        """.trimIndent()
    }

    private fun forecastPage(
        productos: List<Producto>,
        predictions: List<Prediction>
    ): String {
        return """
            <div class="section-card compact-card">
                <table class="card-heading" cellpadding="0" cellspacing="0">
                    <tr>
                        <td>
                            <div class="card-title">Distribución por categoría</div>
                            <div class="card-subtitle">Participación sobre el stock total</div>
                        </td>
                        <td class="card-tag">CATEGORÍAS</td>
                    </tr>
                </table>
                ${categoryChart(productos)}
            </div>

            <div class="section-card forecast-card">
                <table class="card-heading" cellpadding="0" cellspacing="0">
                    <tr>
                        <td>
                            <div class="card-title">Proyección de existencias</div>
                            <div class="card-subtitle">Stock actual frente a estimación a 7 días</div>
                        </td>
                        <td class="card-tag">PRONÓSTICO</td>
                    </tr>
                </table>
                ${forecastChart(predictions)}
            </div>
        """.trimIndent()
    }

    private fun stockChart(productos: List<Producto>): String {
        if (productos.isEmpty()) return emptyChart("No hay productos para analizar.")

        val items = productos.sortedByDescending { it.stock }.take(8)
        val maxStock = max(items.maxOfOrNull { it.stock } ?: 1, 1)

        return buildString {
            append("<div class=\"chart-area\"><table class=\"bar-chart\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
            items.forEach { product ->
                val height = max(4, (product.stock * 190f / maxStock).roundToInt())
                val color = if (
                    product.stockBajo || product.stock <= product.stockMinimo
                ) RED else CYAN

                append("<td class=\"bar-cell\">")
                append("<div class=\"bar-number\">${product.stock}</div>")
                append("<div class=\"bar-vertical\" style=\"height:${height}px;background:$color\"></div>")
                append("<div class=\"bar-name\">${escape(shorten(product.nombre, 12))}</div>")
                append("</td>")
            }
            append("</tr></table></div>")
            append(
                """
                <div class="legend-line">
                    <span class="legend-chip"><i style="background:$CYAN"></i>Stock normal</span>
                    <span class="legend-chip"><i style="background:$RED"></i>Stock bajo</span>
                </div>
                """.trimIndent()
            )
        }
    }

    private fun flowChart(
        movimientos: List<Movimiento>,
        now: Long
    ): String {
        val days = lastSevenDays(movimientos, now)
        val maxValue = max(
            days.maxOfOrNull { max(it.entries, it.exits) } ?: 1,
            1
        )

        return buildString {
            append("<div class=\"chart-area short\"><table class=\"bar-chart grouped\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
            days.forEach { day ->
                val inHeight = if (day.entries == 0) 3 else {
                    max(4, (day.entries * 130f / maxValue).roundToInt())
                }
                val outHeight = if (day.exits == 0) 3 else {
                    max(4, (day.exits * 130f / maxValue).roundToInt())
                }

                append("<td class=\"bar-cell\">")
                append("<table class=\"pair-table\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
                append(
                    "<td><div class=\"bar-number small\">${day.entries}</div>" +
                            "<div class=\"bar-pair\" style=\"height:${inHeight}px;background:$GREEN\"></div></td>"
                )
                append(
                    "<td><div class=\"bar-number small\">${day.exits}</div>" +
                            "<div class=\"bar-pair\" style=\"height:${outHeight}px;background:$RED\"></div></td>"
                )
                append("</tr></table>")
                append("<div class=\"bar-name\">${escape(day.label)}</div>")
                append("</td>")
            }
            append("</tr></table></div>")
            append(
                """
                <div class="legend-line">
                    <span class="legend-chip"><i style="background:$GREEN"></i>Entradas</span>
                    <span class="legend-chip"><i style="background:$RED"></i>Salidas</span>
                </div>
                """.trimIndent()
            )
        }
    }

    private fun categoryChart(productos: List<Producto>): String {
        if (productos.isEmpty()) return emptyChart("No hay categorías registradas.")

        val categories = productos
            .groupBy { it.categoria.ifBlank { "Sin categoría" } }
            .mapValues { (_, values) -> values.sumOf { it.stock } }
            .toList()
            .sortedByDescending { it.second }
            .take(7)

        val total = max(categories.sumOf { it.second }, 1)

        return buildString {
            categories.forEachIndexed { index, (name, stock) ->
                val width = max(2, (stock * 100f / total).roundToInt())
                val color = chartColor(index)

                append(
                    """
                    <table class="category-row" cellpadding="0" cellspacing="0">
                        <tr>
                            <td class="category-name">${escape(shorten(name, 22))}</td>
                            <td class="category-track-cell">
                                <div class="category-track">
                                    <div class="category-fill"
                                         style="width:$width%;background:$color"></div>
                                </div>
                            </td>
                            <td class="category-value">$width%</td>
                        </tr>
                    </table>
                    """.trimIndent()
                )
            }
        }
    }

    private fun forecastChart(predictions: List<Prediction>): String {
        if (predictions.isEmpty()) return emptyChart("No hay datos para proyectar.")

        val items = predictions.sortedByDescending { it.stockActual }.take(6)
        val maxValue = max(
            items.maxOfOrNull {
                max(it.stockActual, it.stockProjected7Days)
            } ?: 1,
            1
        )

        return buildString {
            append("<div class=\"chart-area forecast\"><table class=\"bar-chart grouped\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
            items.forEach { item ->
                val currentHeight = max(
                    4,
                    (item.stockActual * 175f / maxValue).roundToInt()
                )
                val futureHeight = max(
                    4,
                    (item.stockProjected7Days * 175f / maxValue).roundToInt()
                )

                append("<td class=\"bar-cell\">")
                append("<table class=\"pair-table\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
                append(
                    "<td><div class=\"bar-number small\">${item.stockActual}</div>" +
                            "<div class=\"bar-pair\" style=\"height:${currentHeight}px;background:$BLUE\"></div></td>"
                )
                append(
                    "<td><div class=\"bar-number small\">${item.stockProjected7Days}</div>" +
                            "<div class=\"bar-pair predicted\" style=\"height:${futureHeight}px;background:$BLUE\"></div></td>"
                )
                append("</tr></table>")
                append("<div class=\"bar-name\">${escape(shorten(item.product.nombre, 11))}</div>")
                append("</td>")
            }
            append("</tr></table></div>")
            append(
                """
                <div class="legend-line">
                    <span class="legend-chip"><i style="background:$BLUE"></i>Actual</span>
                    <span class="legend-chip"><i class="soft-blue"></i>Proyección 7 días</span>
                </div>
                """.trimIndent()
            )
        }
    }

    private fun inventoryTable(items: List<Producto>): String {
        if (items.isEmpty()) return emptyState("No existen productos registrados.")

        return dataTable(
            headers = listOf("Producto", "Categoría", "Stock", "Mínimo", "Unidad", "Estado"),
            widths = listOf("26%", "19%", "11%", "11%", "13%", "20%"),
            rows = items.map { product ->
                listOf(
                    "<strong>${escape(product.nombre)}</strong>",
                    escape(product.categoria),
                    product.stock.toString(),
                    product.stockMinimo.toString(),
                    escape(product.unidad),
                    if (product.stockBajo || product.stock <= product.stockMinimo) {
                        badge("Stock bajo", "danger")
                    } else {
                        badge("Disponible", "success")
                    }
                )
            },
            numericColumns = setOf(2, 3)
        )
    }

    private fun predictionTable(items: List<Prediction>): String {
        if (items.isEmpty()) return emptyState("No hay predicciones disponibles.")

        return dataTable(
            headers = listOf(
                "Producto",
                "Stock",
                "Salida/día",
                "7 días",
                "Días restantes",
                "Nivel"
            ),
            widths = listOf("29%", "11%", "15%", "12%", "17%", "16%"),
            rows = items.map { item ->
                listOf(
                    "<strong>${escape(item.product.nombre)}</strong>",
                    item.stockActual.toString(),
                    formatDecimal(item.dailyOut),
                    item.stockProjected7Days.toString(),
                    item.daysUntilEmpty?.toString() ?: "Sin consumo",
                    when {
                        item.daysUntilEmpty == null -> badge("Estable", "neutral")
                        item.daysUntilEmpty <= 7 -> badge("Crítico", "danger")
                        item.daysUntilEmpty <= 14 -> badge("Atención", "warning")
                        else -> badge("Controlado", "success")
                    }
                )
            },
            numericColumns = setOf(1, 2, 3, 4)
        )
    }

    private fun movementsAndAlerts(
        movements: List<Movimiento>,
        alerts: List<Alerta>
    ): String {
        val movementTable = if (movements.isEmpty()) {
            emptyState("No se encontraron movimientos recientes.")
        } else {
            dataTable(
                headers = listOf("Fecha", "Producto", "Tipo", "Cantidad", "Movimiento"),
                widths = listOf("20%", "31%", "17%", "14%", "18%"),
                rows = movements.map { movement ->
                    listOf(
                        escape(formatDateTime(movement.timestamp)),
                        "<strong>${escape(movement.productoNombre)}</strong>",
                        escape(movement.tipo),
                        movement.cantidad.toString(),
                        if (movement.esEntrada) {
                            badge("Entrada", "success")
                        } else {
                            badge("Salida", "danger")
                        }
                    )
                },
                numericColumns = setOf(3)
            )
        }

        val alertBlock = if (alerts.isEmpty()) {
            ""
        } else {
            buildString {
                append("<div class=\"subsection-title\">Alertas activas</div>")
                append("<table class=\"alerts-table\" cellpadding=\"0\" cellspacing=\"0\">")
                alerts.forEach { alert ->
                    append(
                        """
                        <tr>
                            <td class="alert-dot-cell"><span class="alert-dot"></span></td>
                            <td>
                                <strong>${escape(alert.productoNombre)}</strong>
                                <div class="muted">
                                    Stock ${alert.stockAlMomento} · mínimo ${alert.stockMinimo}
                                </div>
                            </td>
                            <td class="alert-suggestion">
                                Reabasto sugerido<br>
                                <strong>${alert.sugerenciaReabasto}</strong>
                            </td>
                        </tr>
                        """.trimIndent()
                    )
                }
                append("</table>")
            }
        }

        return movementTable + alertBlock
    }

    private fun dataTable(
        headers: List<String>,
        widths: List<String>,
        rows: List<List<String>>,
        numericColumns: Set<Int>
    ): String {
        return buildString {
            append("<div class=\"table-shell\"><table class=\"data-table\" cellpadding=\"0\" cellspacing=\"0\">")
            append("<colgroup>")
            widths.forEach { width ->
                append("<col style=\"width:$width\">")
            }
            append("</colgroup><thead><tr>")

            headers.forEachIndexed { index, header ->
                append(
                    "<th class=\"${if (index in numericColumns) "num" else ""}\">" +
                            escape(header) +
                            "</th>"
                )
            }

            append("</tr></thead><tbody>")

            rows.forEachIndexed { rowIndex, row ->
                append("<tr class=\"${if (rowIndex % 2 == 0) "even" else "odd"}\">")
                row.forEachIndexed { columnIndex, cell ->
                    append(
                        "<td class=\"${if (columnIndex in numericColumns) "num" else ""}\">" +
                                cell +
                                "</td>"
                    )
                }
                append("</tr>")
            }

            append("</tbody></table></div>")
        }
    }

    private fun kpi(
        label: String,
        value: String,
        caption: String,
        accent: String
    ): String {
        return """
            <td class="kpi-cell">
                <div class="kpi-card">
                    <div class="kpi-accent" style="background:$accent"></div>
                    <div class="kpi-label">${escape(label)}</div>
                    <div class="kpi-value">${escape(value)}</div>
                    <div class="kpi-caption">${escape(caption)}</div>
                </div>
            </td>
        """.trimIndent()
    }

    private fun badge(text: String, type: String): String =
        "<span class=\"badge $type\">${escape(text)}</span>"

    private fun emptyChart(text: String): String =
        "<div class=\"empty-chart\">${escape(text)}</div>"

    private fun emptyState(text: String): String =
        "<div class=\"empty-state\">${escape(text)}</div>"

    private fun calculatePredictions(
        productos: List<Producto>,
        movimientos: List<Movimiento>,
        now: Long
    ): List<Prediction> {
        val from = now - 30L * 86_400_000L

        val exits = movimientos
            .asSequence()
            .filter { !it.esEntrada && it.timestamp in from..now }
            .groupBy { it.productoId }
            .mapValues { (_, values) -> values.sumOf { it.cantidad } }

        return productos.map { product ->
            val totalOut = exits[product.id] ?: 0
            val dailyOut = totalOut / 30.0
            val projected = max(
                0,
                (product.stock - dailyOut * 7.0).roundToInt()
            )
            val days = if (dailyOut > 0.0) {
                ceil(product.stock / dailyOut).toInt()
            } else {
                null
            }

            Prediction(
                product = product,
                stockActual = product.stock,
                dailyOut = dailyOut,
                stockProjected7Days = projected,
                daysUntilEmpty = days
            )
        }
    }

    private fun lastSevenDays(
        movements: List<Movimiento>,
        now: Long
    ): List<DayFlow> {
        val dayMs = 86_400_000L
        val dayFormat = SimpleDateFormat("EEE", locale)

        return (6 downTo 0).map { offset ->
            val start = startOfDay(now - offset * dayMs)
            val end = start + dayMs

            DayFlow(
                label = dayFormat.format(Date(start))
                    .replace(".", "")
                    .replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                    },
                entries = movements
                    .asSequence()
                    .filter {
                        it.esEntrada &&
                                it.timestamp >= start &&
                                it.timestamp < end
                    }
                    .sumOf { it.cantidad },
                exits = movements
                    .asSequence()
                    .filter {
                        !it.esEntrada &&
                                it.timestamp >= start &&
                                it.timestamp < end
                    }
                    .sumOf { it.cantidad }
            )
        }
    }

    private fun css(): String = """
        * {
            box-sizing: border-box;
            -webkit-box-sizing: border-box;
        }

        html,
        body {
            width: ${PAGE_WIDTH}px;
            margin: 0;
            padding: 0;
            background: #FFFFFF;
            color: #172033;
            font-family: Arial, Helvetica, sans-serif;
            font-size: 12px;
            -webkit-text-size-adjust: 100%;
        }

        #pdf-root {
            width: ${PAGE_WIDTH}px;
            margin: 0;
            padding: 0;
        }

        .pdf-page {
            position: relative;
            width: ${PAGE_WIDTH}px;
            height: ${PAGE_HEIGHT}px;
            min-height: ${PAGE_HEIGHT}px;
            max-height: ${PAGE_HEIGHT}px;
            overflow: hidden;
            background: #FFFFFF;
            page-break-after: always;
        }

        .page-header {
            position: absolute;
            left: 42px;
            top: 28px;
            width: 710px;
            height: 42px;
            table-layout: fixed;
            border-bottom: 1px solid $BORDER;
        }

        .header-brand {
            color: $CYAN;
            font-size: 19px;
            line-height: 20px;
            font-weight: 800;
            letter-spacing: -0.4px;
        }

        .header-caption {
            color: #8492A6;
            font-size: 9px;
            margin-top: 2px;
            text-transform: uppercase;
            letter-spacing: 1px;
        }

        .header-meta {
            width: 180px;
            text-align: right;
            color: #8492A6;
            font-size: 10px;
            vertical-align: top;
            padding-top: 4px;
        }

        .page-title-block {
            position: absolute;
            left: 42px;
            top: 92px;
            width: 710px;
        }

        .eyebrow {
            color: $CYAN;
            font-size: 9px;
            line-height: 12px;
            font-weight: 800;
            letter-spacing: 1.4px;
        }

        .page-title {
            color: $NAVY;
            font-size: 27px;
            line-height: 33px;
            font-weight: 800;
            letter-spacing: -0.5px;
            margin-top: 5px;
        }

        .page-subtitle {
            color: $SLATE;
            font-size: 11px;
            line-height: 16px;
            margin-top: 3px;
        }

        .page-content {
            position: absolute;
            left: 42px;
            top: 176px;
            width: 710px;
            height: 866px;
            overflow: hidden;
        }

        .page-content.cover-content {
            left: 0;
            top: 0;
            width: ${PAGE_WIDTH}px;
            height: 1068px;
        }

        .page-footer {
            position: absolute;
            left: 42px;
            bottom: 22px;
            width: 710px;
            height: 22px;
            border-top: 1px solid $BORDER;
            color: #8A97A8;
            font-size: 8px;
            table-layout: fixed;
            padding-top: 8px;
        }

        .page-number {
            text-align: right;
        }

        .cover-hero {
            height: 410px;
            padding: 44px 48px;
            background: $NAVY;
            color: #FFFFFF;
        }

        .cover-brand-table {
            width: 100%;
            table-layout: fixed;
        }

        .cover-brand {
            color: $CYAN;
            font-size: 28px;
            line-height: 32px;
            font-weight: 800;
            letter-spacing: -0.8px;
        }

        .cover-kicker {
            color: #718096;
            font-size: 9px;
            letter-spacing: 1.7px;
            margin-top: 4px;
        }

        .cover-date {
            width: 200px;
            text-align: right;
            color: #8190A6;
            font-size: 9px;
            line-height: 16px;
            letter-spacing: 0.8px;
            vertical-align: top;
        }

        .cover-date strong {
            color: #FFFFFF;
            font-size: 11px;
        }

        .cover-title {
            margin-top: 74px;
            color: #FFFFFF;
            font-size: 38px;
            line-height: 45px;
            font-weight: 800;
            letter-spacing: -1.1px;
        }

        .cover-description {
            width: 540px;
            margin-top: 17px;
            color: #9EADC1;
            font-size: 13px;
            line-height: 20px;
        }

        .kpi-grid {
            width: 710px;
            margin: -42px 42px 0 42px;
            table-layout: fixed;
            border-spacing: 12px;
        }

        .kpi-cell {
            width: 50%;
            padding: 0;
            vertical-align: top;
        }

        .kpi-card {
            position: relative;
            height: 126px;
            background: #FFFFFF;
            border: 1px solid $BORDER;
            border-radius: 10px;
            padding: 20px 22px;
            box-shadow: 0 4px 14px rgba(15, 23, 42, 0.07);
            overflow: hidden;
        }

        .kpi-accent {
            position: absolute;
            left: 0;
            top: 0;
            width: 5px;
            height: 126px;
        }

        .kpi-label {
            color: $SLATE;
            font-size: 9px;
            font-weight: 800;
            letter-spacing: 1.1px;
            text-transform: uppercase;
        }

        .kpi-value {
            color: $NAVY;
            font-size: 29px;
            line-height: 35px;
            font-weight: 800;
            margin-top: 7px;
        }

        .kpi-caption {
            color: #8A97A8;
            font-size: 10px;
            margin-top: 2px;
        }

        .summary-grid {
            width: 710px;
            margin: 18px 42px 0 42px;
            table-layout: fixed;
            border-spacing: 12px 0;
        }

        .summary-main {
            width: 64%;
            vertical-align: top;
        }

        .summary-side {
            width: 36%;
            vertical-align: top;
        }

        .panel {
            height: 242px;
            padding: 22px;
            background: $SOFT;
            border: 1px solid $BORDER;
            border-radius: 10px;
            overflow: hidden;
        }

        .panel-label {
            color: $CYAN;
            font-size: 9px;
            font-weight: 800;
            letter-spacing: 1.2px;
        }

        .ai-text {
            color: #344155;
            font-size: 12px;
            line-height: 19px;
            margin-top: 14px;
        }

        .alert-panel {
            background: #FFFFFF;
        }

        .alert-item {
            padding: 13px 0;
            border-bottom: 1px solid $BORDER;
        }

        .alert-item:last-child {
            border-bottom: 0;
        }

        .alert-product {
            color: $NAVY;
            font-size: 11px;
            font-weight: 800;
            line-height: 14px;
        }

        .alert-detail {
            color: $SLATE;
            font-size: 9px;
            margin-top: 4px;
        }

        .empty-compact {
            color: $SLATE;
            font-size: 10px;
            margin-top: 18px;
        }

        .section-card {
            height: 405px;
            padding: 20px 22px 15px 22px;
            border: 1px solid $BORDER;
            border-radius: 12px;
            background: #FFFFFF;
            overflow: hidden;
        }

        .section-card.lower-card {
            height: 405px;
            margin-top: 20px;
        }

        .section-card.compact-card {
            height: 312px;
        }

        .section-card.forecast-card {
            height: 498px;
            margin-top: 20px;
        }

        .card-heading {
            width: 100%;
            table-layout: fixed;
            border-bottom: 1px solid $BORDER;
            padding-bottom: 12px;
        }

        .card-title {
            color: $NAVY;
            font-size: 15px;
            line-height: 20px;
            font-weight: 800;
        }

        .card-subtitle {
            color: $SLATE;
            font-size: 9px;
            margin-top: 2px;
        }

        .card-tag {
            width: 100px;
            text-align: right;
            color: $CYAN;
            font-size: 8px;
            font-weight: 800;
            letter-spacing: 1px;
        }

        .chart-area {
            height: 262px;
            padding: 18px 6px 0 6px;
        }

        .chart-area.short {
            height: 207px;
        }

        .chart-area.forecast {
            height: 295px;
            padding-top: 20px;
        }

        .bar-chart {
            width: 100%;
            height: 238px;
            table-layout: fixed;
            border-collapse: collapse;
            border-bottom: 1px solid #C9D3DF;
        }

        .bar-chart.grouped {
            height: 190px;
        }

        .bar-cell {
            vertical-align: bottom;
            text-align: center;
            padding: 0 4px;
        }

        .bar-number {
            color: #4B5A70;
            font-size: 8px;
            font-weight: 800;
            margin-bottom: 4px;
        }

        .bar-number.small {
            font-size: 7px;
        }

        .bar-vertical {
            width: 66%;
            min-width: 8px;
            margin: 0 auto;
            border-radius: 4px 4px 0 0;
        }

        .bar-name {
            height: 31px;
            padding-top: 6px;
            color: #59677B;
            font-size: 7px;
            line-height: 10px;
            overflow: hidden;
            word-break: break-word;
        }

        .pair-table {
            width: 100%;
            table-layout: fixed;
            border-collapse: collapse;
        }

        .pair-table td {
            width: 50%;
            vertical-align: bottom;
            text-align: center;
            padding: 0 2px;
        }

        .bar-pair {
            width: 86%;
            min-width: 7px;
            margin: 0 auto;
            border-radius: 3px 3px 0 0;
        }

        .bar-pair.predicted {
            opacity: 0.32;
        }

        .legend-line {
            text-align: center;
            color: $SLATE;
            font-size: 8px;
            margin-top: 9px;
        }

        .legend-chip {
            display: inline-block;
            margin: 0 11px;
        }

        .legend-chip i {
            display: inline-block;
            width: 8px;
            height: 8px;
            margin-right: 5px;
            border-radius: 2px;
            vertical-align: -1px;
        }

        .legend-chip i.soft-blue {
            background: rgba(37, 99, 235, 0.32);
        }

        .category-row {
            width: 100%;
            height: 31px;
            table-layout: fixed;
            margin-top: 7px;
        }

        .category-name {
            width: 145px;
            color: #334155;
            font-size: 9px;
            font-weight: 700;
            padding-right: 12px;
        }

        .category-track-cell {
            vertical-align: middle;
        }

        .category-track {
            width: 100%;
            height: 12px;
            background: #E8EDF3;
            border-radius: 6px;
            overflow: hidden;
        }

        .category-fill {
            height: 12px;
            border-radius: 6px;
        }

        .category-value {
            width: 48px;
            text-align: right;
            color: $SLATE;
            font-size: 9px;
            font-weight: 700;
        }

        .table-shell {
            width: 100%;
            border: 1px solid $BORDER;
            border-radius: 10px;
            overflow: hidden;
        }

        .data-table {
            width: 100%;
            table-layout: fixed;
            border-collapse: collapse;
        }

        .data-table th {
            height: 42px;
            padding: 0 10px;
            background: $NAVY;
            color: #DCE5F1;
            font-size: 8px;
            font-weight: 800;
            letter-spacing: 0.65px;
            text-transform: uppercase;
            text-align: left;
            border-right: 1px solid #283750;
        }

        .data-table th:last-child {
            border-right: 0;
        }

        .data-table td {
            height: 61px;
            padding: 8px 10px;
            color: #3F4D61;
            font-size: 9px;
            line-height: 13px;
            vertical-align: middle;
            border-bottom: 1px solid $BORDER;
            word-wrap: break-word;
        }

        .data-table tr:last-child td {
            border-bottom: 0;
        }

        .data-table tr.even td {
            background: #FFFFFF;
        }

        .data-table tr.odd td {
            background: $SOFT;
        }

        .data-table .num {
            text-align: right;
        }

        .data-table strong {
            color: $NAVY;
            font-weight: 800;
        }

        .badge {
            display: inline-block;
            padding: 4px 7px;
            border-radius: 9px;
            font-size: 7px;
            font-weight: 800;
            white-space: nowrap;
        }

        .badge.success {
            color: #08765A;
            background: #D9F7EC;
        }

        .badge.danger {
            color: #B42318;
            background: #FEE4E2;
        }

        .badge.warning {
            color: #9A6700;
            background: #FFF0C2;
        }

        .badge.neutral {
            color: #526277;
            background: #E8EDF3;
        }

        .empty-chart,
        .empty-state {
            color: $SLATE;
            text-align: center;
            border: 1px dashed #CBD5E1;
            background: $SOFT;
            border-radius: 8px;
        }

        .empty-chart {
            margin-top: 18px;
            padding: 65px 20px;
        }

        .empty-state {
            padding: 42px 20px;
        }

        .subsection-title {
            color: $NAVY;
            font-size: 15px;
            font-weight: 800;
            margin-top: 22px;
            margin-bottom: 10px;
        }

        .alerts-table {
            width: 100%;
            table-layout: fixed;
            border: 1px solid $BORDER;
            border-radius: 10px;
            overflow: hidden;
            border-collapse: separate;
            border-spacing: 0;
        }

        .alerts-table td {
            height: 62px;
            padding: 10px;
            border-bottom: 1px solid $BORDER;
            color: #3F4D61;
            font-size: 9px;
            vertical-align: middle;
        }

        .alerts-table tr:last-child td {
            border-bottom: 0;
        }

        .alert-dot-cell {
            width: 34px;
            text-align: center;
        }

        .alert-dot {
            display: inline-block;
            width: 8px;
            height: 8px;
            background: $RED;
            border-radius: 4px;
        }

        .alert-suggestion {
            width: 145px;
            text-align: right;
            color: $SLATE !important;
        }

        .muted {
            color: $SLATE;
            font-size: 8px;
            margin-top: 3px;
        }

        @media print {
            .pdf-page {
                page-break-after: always;
            }
        }
    """.trimIndent()

    private fun chartColor(index: Int): String {
        val colors = listOf(
            CYAN,
            BLUE,
            GREEN,
            AMBER,
            "#8B5CF6",
            "#EC4899",
            "#14B8A6"
        )
        return colors[index % colors.size]
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("dd MMM yyyy", locale).format(Date(timestamp))

    private fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date(timestamp))

    private fun formatDecimal(value: Double): String =
        String.format(locale, "%.1f", value)

    private fun startOfDay(timestamp: Long): Long {
        val format = SimpleDateFormat("yyyy-MM-dd", locale)
        return runCatching {
            format.parse(format.format(Date(timestamp)))?.time
        }.getOrNull() ?: timestamp
    }

    private fun shorten(text: String, maxLength: Int): String {
        val clean = text.trim()
        return if (clean.length <= maxLength) clean
        else clean.take(maxLength - 1) + "…"
    }

    private fun nl2br(text: String): String =
        escape(text).replace("\n", "<br>")

    private fun escape(text: String): String = buildString {
        text.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> char
                }
            )
        }
    }

    private data class PageSpec(
        val title: String,
        val subtitle: String,
        val body: String,
        val showHeader: Boolean = true
    )

    private data class Prediction(
        val product: Producto,
        val stockActual: Int,
        val dailyOut: Double,
        val stockProjected7Days: Int,
        val daysUntilEmpty: Int?
    )

    private data class DayFlow(
        val label: String,
        val entries: Int,
        val exits: Int
    )
}