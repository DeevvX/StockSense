package com.stocksense.app.data.model


data class Alerta(
    val id: String = "",
    val productoId: String = "",
    val productoNombre: String = "",
    val stockAlMomento: Int = 0,
    val stockMinimo: Int = 0,
    val sugerenciaReabasto: Int = 0,
    val timestamp: Long = 0L
)