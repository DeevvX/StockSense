package com.stocksense.app.data.model

data class Producto(
    val id: String = "",
    val nombre: String = "",
    val categoria: String = "",
    val stock: Int = 0,
    val stockMinimo: Int = 0,
    val unidad: String = ""
) {
    val stockBajo: Boolean
        get() = stock <= stockMinimo

    val porcentajeStock: Float
        get() {
            val maximoReferencia = (stockMinimo * 2).coerceAtLeast(1)
            return (stock.toFloat() / maximoReferencia.toFloat()).coerceIn(0f, 1f)
        }
}