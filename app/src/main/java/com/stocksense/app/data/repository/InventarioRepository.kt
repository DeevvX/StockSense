package com.stocksense.app.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.model.Producto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class InventarioRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    private val rootRef = database.reference
    private val productosRef = rootRef.child("productos")
    private val movimientosRef = rootRef.child("movimientos")

    fun obtenerProductos(): Flow<List<Producto>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val productos = snapshot.children.mapNotNull { child ->
                    child.getValue(Producto::class.java)?.copy(
                        id = child.key ?: ""
                    )
                }

                trySend(productos)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        productosRef.addValueEventListener(listener)

        awaitClose {
            productosRef.removeEventListener(listener)
        }
    }

    fun obtenerMovimientos(): Flow<List<Movimiento>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val movimientos = snapshot.children.mapNotNull { child ->
                    child.getValue(Movimiento::class.java)?.copy(
                        id = child.key ?: ""
                    )
                }.sortedByDescending { it.timestamp }

                trySend(movimientos)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        movimientosRef.addValueEventListener(listener)

        awaitClose {
            movimientosRef.removeEventListener(listener)
        }
    }



    suspend fun buscarProductoPorNombre(nombre: String): Producto? {
        return try {
            if (nombre.isBlank()) return null

            val nombreBuscado = normalizar(nombre)

            val snapshot = productosRef.get().await()
            val productos = snapshot.children.mapNotNull { child ->
                child.getValue(Producto::class.java)?.copy(id = child.key ?: "")
            }

            // 1 coincidencia exacta normalizada
            productos.firstOrNull { normalizar(it.nombre) == nombreBuscado }
            // 2 coincidencia parcial (uno contiene al otro)
                ?: productos.firstOrNull {
                    val n = normalizar(it.nombre)
                    n.contains(nombreBuscado) || nombreBuscado.contains(n)
                }
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizar(texto: String): String =
        texto.trim().lowercase()

    suspend fun agregarProducto(producto: Producto): Boolean {
        return try {
            val id = producto.id.ifBlank {
                productosRef.push().key ?: return false
            }

            val productoFinal = producto.copy(id = id)

            productosRef
                .child(id)
                .setValue(productoFinal)
                .await()

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun actualizarStock(
        productoId: String,
        nuevoStock: Int
    ): Boolean {
        return try {
            if (productoId.isBlank()) return false

            productosRef
                .child(productoId)
                .child("stock")
                .setValue(nuevoStock.coerceAtLeast(0))
                .await()

            true
        } catch (e: Exception) {
            false
        }
    }


    suspend fun actualizarStockMinimo(
        productoId: String,
        nuevoStockMinimo: Int
    ): Boolean {
        return try {
            if (productoId.isBlank()) return false

            productosRef
                .child(productoId)
                .child("stockMinimo")
                .setValue(nuevoStockMinimo.coerceAtLeast(0))
                .await()

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun registrarMovimiento(movimiento: Movimiento): Boolean {
        return try {
            if (movimiento.productoId.isBlank()) return false
            if (movimiento.cantidad <= 0) return false

            val productoSnapshot = productosRef
                .child(movimiento.productoId)
                .get()
                .await()

            val producto = productoSnapshot.getValue(Producto::class.java)
                ?: return false

            val tipoNormalizado = movimiento.tipo.lowercase().trim()

            val nuevoStock = when (tipoNormalizado) {
                "entrada" -> producto.stock + movimiento.cantidad
                "salida" -> producto.stock - movimiento.cantidad
                else -> return false
            }

            if (nuevoStock < 0) return false

            val movimientoId = movimiento.id.ifBlank {
                movimientosRef.push().key ?: return false
            }

            val movimientoFinal = movimiento.copy(
                id = movimientoId,
                productoNombre = movimiento.productoNombre.ifBlank {
                    producto.nombre
                },
                tipo = tipoNormalizado,
                timestamp = if (movimiento.timestamp == 0L) {
                    System.currentTimeMillis()
                } else {
                    movimiento.timestamp
                }
            )

            val updates = mapOf<String, Any>(
                "productos/${movimiento.productoId}/stock" to nuevoStock,
                "movimientos/$movimientoId" to movimientoFinal
            )

            rootRef
                .updateChildren(updates)
                .await()

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun eliminarProducto(productoId: String): Boolean {
        return try {
            if (productoId.isBlank()) return false

            productosRef
                .child(productoId)
                .removeValue()
                .await()

            true
        } catch (e: Exception) {
            false
        }
    }
}