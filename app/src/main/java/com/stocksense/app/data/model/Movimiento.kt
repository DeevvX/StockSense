package com.stocksense.app.data.model

import com.google.firebase.database.Exclude

data class Movimiento(
    val id: String = "",
    val productoId: String = "",
    val productoNombre: String = "",
    val tipo: String = "",
    val cantidad: Int = 0,
    val timestamp: Long = 0L,
    val imagenUrl: String = ""
) {

    @get:Exclude
    val esEntrada: Boolean
        get() = tipo.lowercase() == "entrada"
}