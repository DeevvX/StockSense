package com.stocksense.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Producto identificado en una foto de nota de entrega / remito.
 * A diferencia de ProductoDetectado (usado por la cámara IoT), aquí no hay
 * ambigüedad de entrada/salida: todo lo que viene de una nota de entrega
 * es, por definición, una ENTRADA de mercancía al almacén.
 */
@Serializable
data class ProductoNotaEntrega(
    val nombre: String = "",
    val cantidad: Int = 1
)

@Serializable
private data class RespuestaNotaEntrega(
    val productos: List<ProductoNotaEntrega> = emptyList()
)

/**
 * Servicio independiente de OpenAIVisionService.kt (el de la cámara IoT).
 * Se creó aparte a propósito para no arriesgar el pipeline de cámara que
 * ya está en producción — este servicio es exclusivo del chatbot de
 * entradas por foto.
 */
class OpenAIEntradaVisionService(
    private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val promptSistema = """
        Eres un sistema de control de inventario. Esta imagen es una NOTA DE ENTREGA,
        REMITO o FACTURA de productos que están INGRESANDO al almacén (una compra o
        reabastecimiento). Identifica cada producto que aparece listado y la cantidad
        correspondiente.

        Reglas:
        - Si un producto aparece varias veces, súmalo en una sola entrada.
        - Si no puedes leer la cantidad con claridad, usa 1 como valor por defecto.
        - Ignora totales, subtotales, precios, impuestos y datos del proveedor.
        - Usa el nombre del producto tal como aparece escrito, sin inventar detalles.

        Responde ÚNICAMENTE en formato JSON, sin texto adicional y sin markdown:
        {"productos": [{"nombre": string, "cantidad": number}, ...]}

        Si no logras identificar ningún producto, responde: {"productos": []}
    """.trimIndent()

    /**
     * Envía la imagen en Base64 a GPT-4o Vision y devuelve la lista de
     * productos detectados en la nota de entrega. Lista vacía si no se
     * detectó nada o si algo falló (nunca lanza excepción hacia afuera).
     */
    suspend fun identificarProductosEnNota(imagenBase64: String): List<ProductoNotaEntrega> {
        return withContext(Dispatchers.IO) {
            try {
                val promptEscapado = json.encodeToString(
                    kotlinx.serialization.serializer<String>(),
                    promptSistema
                )
                val bodyJson = """
                    {
                      "model": "gpt-4o",
                      "messages": [
                        {
                          "role": "user",
                          "content": [
                            { "type": "text", "text": $promptEscapado },
                            { "type": "image_url", "image_url": { "url": "data:image/jpeg;base64,$imagenBase64" } }
                          ]
                        }
                      ],
                      "max_tokens": 800
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext emptyList()

                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }

                val contenido = extraerContenido(responseBody) ?: return@withContext emptyList()
                val limpio = contenido
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                json.decodeFromString<RespuestaNotaEntrega>(limpio).productos
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun extraerContenido(responseBody: String): String? {
        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            jsonElement
                .jsonObject["choices"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}