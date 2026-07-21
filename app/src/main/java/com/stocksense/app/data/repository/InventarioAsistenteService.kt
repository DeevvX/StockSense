package com.stocksense.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Un turno de la conversación, para mandarle historial al modelo. */
data class TurnoChat(val esUsuario: Boolean, val texto: String)

/**
 * Servicio de chat en lenguaje natural sobre el inventario. Recibe un
 * "contexto" ya armado (resumen de productos, stock, consumo) más la
 * pregunta del usuario, y responde basándose únicamente en esos datos —
 * no en conocimiento general — para evitar que invente cifras.
 */
class InventarioAsistenteService(
    private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun responder(
        pregunta: String,
        contextoInventario: String,
        historial: List<TurnoChat>
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val promptSistema = """
                    Eres el asistente de inventario de StockSense, una app de control de
                    almacén. Respondes preguntas del usuario ÚNICAMENTE con base en los
                    datos de inventario que se te dan a continuación — nunca inventes
                    cifras que no estén ahí.

                    DATOS ACTUALES DEL INVENTARIO:
                    $contextoInventario

                    Reglas:
                    - Responde en español, de forma breve, clara y directa (máximo 4-5 líneas).
                    - Si preguntan cuánto stock queda de un producto, da el número exacto.
                    - Si preguntan cuándo deben comprar/reabastecer algo, usa el consumo
                      diario estimado y los días restantes que se incluyen en los datos.
                    - Si el producto que preguntan no aparece en los datos, dilo claramente
                      en vez de inventar información.
                    - No uses markdown ni asteriscos, solo texto plano.
                """.trimIndent()

                val mensajes = JSONArray()
                mensajes.put(JSONObject().apply {
                    put("role", "system")
                    put("content", promptSistema)
                })
                historial.takeLast(10).forEach { turno ->
                    mensajes.put(JSONObject().apply {
                        put("role", if (turno.esUsuario) "user" else "assistant")
                        put("content", turno.texto)
                    })
                }
                mensajes.put(JSONObject().apply {
                    put("role", "user")
                    put("content", pregunta)
                })

                val bodyJson = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("max_tokens", 300)
                    put("messages", mensajes)
                }.toString()

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                    ?: return@withContext "No se pudo obtener respuesta del asistente."

                if (!response.isSuccessful) {
                    return@withContext "El asistente no está disponible en este momento (${response.code})."
                }

                val json = JSONObject(responseBody)
                json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
            } catch (e: Exception) {
                "Ocurrió un error al consultar el asistente: ${e.localizedMessage ?: "desconocido"}"
            }
        }
    }
}