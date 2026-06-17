package com.stocksense.app.data.model

data class Movimiento(
    val id: String = "",
    val productoId: String = "",
    val productoNombre: String = "",
    val tipo: String = "",
    val cantidad: Int = 0,
    val timestamp: Long = 0L,
    val imagenUrl: String = ""
) {
    val esEntrada: Boolean
        get() = tipo.lowercase() == "entrada"
}