package com.stocksense.app.data.model

data class ImagenPendiente(
    val id: String = "",
    val nombreArchivo: String = "",
    val imagenBase64: String = "",
    val timestamp: Long = 0L,
    val procesada: Boolean = false
) {
    constructor() : this("", "", "", 0L, false)
}