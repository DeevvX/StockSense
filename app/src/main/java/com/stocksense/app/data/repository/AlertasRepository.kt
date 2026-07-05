package com.stocksense.app.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.stocksense.app.data.model.Alerta
import com.stocksense.app.data.model.Movimiento
import com.stocksense.app.data.model.Producto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class AlertasRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    companion object {
        private const val DIAS_VENTANA_CONSUMO = 7
        private const val DIAS_COBERTURA_SUGERIDA = 14
    }

    private val rootRef = database.reference
    private val alertasRef = rootRef.child("alertas")
    private val movimientosRef = rootRef.child("movimientos")
    private val productosRef = rootRef.child("productos")

    fun obtenerHistorialAlertas(): Flow<List<Alerta>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alertas = snapshot.children.mapNotNull { child ->
                    child.getValue(Alerta::class.java)?.copy(id = child.key ?: "")
                }.sortedByDescending { it.timestamp }

                trySend(alertas)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        alertasRef.addValueEventListener(listener)

        awaitClose {
            alertasRef.removeEventListener(listener)
        }
    }

    /**
     * Calcula la sugerencia de cantidad a reabastecer para un producto,
     * basada en el promedio diario de salidas registradas en los
     * últimos DIAS_VENTANA_CONSUMO días.
     *
     * Si no hay suficiente historial de movimientos, devuelve una
     * sugerencia mínima (cubrir el stockMinimo configurado), en vez de
     * un valor inventado sin respaldo de datos.
     */
    suspend fun calcularSugerenciaReabasto(producto: Producto): Int {
        return try {
            val ahora = System.currentTimeMillis()
            val limiteVentana = ahora - TimeUnit.DAYS.toMillis(DIAS_VENTANA_CONSUMO.toLong())

            val snapshot = movimientosRef.get().await()
            val movimientosDelProducto = snapshot.children.mapNotNull { child ->
                child.getValue(Movimiento::class.java)
            }.filter {
                it.productoId == producto.id &&
                        it.tipo.lowercase() == "salida" &&
                        it.timestamp >= limiteVentana
            }

            if (movimientosDelProducto.isEmpty()) {
                // Sin historial de consumo reciente: sugerencia conservadora,
                // basada únicamente en el mínimo configurado por el negocio.
                return producto.stockMinimo.coerceAtLeast(1)
            }

            val totalUnidadesVendidas = movimientosDelProducto.sumOf { it.cantidad }
            val promedioDiario = totalUnidadesVendidas.toDouble() / DIAS_VENTANA_CONSUMO

            val sugerencia = (promedioDiario * DIAS_COBERTURA_SUGERIDA).toInt()

            // Nunca sugerir menos que el mínimo configurado, incluso si el
            // consumo reciente fue bajo.
            sugerencia.coerceAtLeast(producto.stockMinimo).coerceAtLeast(1)
        } catch (e: Exception) {
            producto.stockMinimo.coerceAtLeast(1)
        }
    }

    /**
     * Registra una nueva alerta en el historial si el producto está en
     * stock bajo y no se ha registrado ya una alerta reciente para él
     * (evita duplicar la misma alerta en cada lectura del Dashboard).
     */
    suspend fun registrarAlertaSiCorresponde(producto: Producto): Boolean {
        return try {
            if (!producto.stockBajo) return false

            val yaExisteAlertaReciente = existeAlertaActivaPara(producto.id, producto.stock)
            if (yaExisteAlertaReciente) return false

            val sugerencia = calcularSugerenciaReabasto(producto)

            val alertaId = alertasRef.push().key ?: return false

            val alerta = Alerta(
                id = alertaId,
                productoId = producto.id,
                productoNombre = producto.nombre,
                stockAlMomento = producto.stock,
                stockMinimo = producto.stockMinimo,
                sugerenciaReabasto = sugerencia,
                timestamp = System.currentTimeMillis()
            )

            alertasRef.child(alertaId).setValue(alerta).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Revisa si ya existe una alerta para este producto registrada en
     * las últimas 24 horas CON EL MISMO NIVEL DE STOCK. Esto evita
     * duplicar la misma alerta cada vez que el Dashboard se actualiza,
     * pero sí permite registrar una nueva entrada en el historial si
     * el stock sigue cambiando (por ejemplo, sigue bajando con más
     * salidas, o sube por una entrada administrativa y vuelve a caer).
     */
    private suspend fun existeAlertaActivaPara(productoId: String, stockActual: Int): Boolean {
        return try {
            val hace24Horas = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)

            val snapshot = alertasRef.get().await()
            snapshot.children.any { child ->
                val alerta = child.getValue(Alerta::class.java)
                alerta != null &&
                        alerta.productoId == productoId &&
                        alerta.timestamp >= hace24Horas &&
                        alerta.stockAlMomento == stockActual
            }
        } catch (e: Exception) {
            false
        }
    }
}