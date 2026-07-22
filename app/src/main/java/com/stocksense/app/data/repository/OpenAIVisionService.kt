package com.stocksense.app.data.repository

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


@Serializable
data class ProductoDetectado(
    val producto: String = "",
    val cantidad: Int = 1,
    val confianza: Double = 0.0
)

class OpenAIVisionService(
    private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val promptSistema = """
        Eres un sistema de control de inventario. Analiza esta imagen de un almacén
        y determina: 1) ¿Qué producto aparece o fue movido? (nombre específico),
        2) ¿Cuántas unidades se movieron? Cada detección de movimiento frente a la
        cámara se interpreta como una salida de inventario.
        Responde ÚNICAMENTE en formato JSON sin texto adicional, sin markdown:
        {"producto": string, "cantidad": number, "confianza": number}
        La confianza debe ser un número entre 0 y 1.
    """.trimIndent()


    fun identificarProducto(imagenBase64: String): ProductoDetectado? {
        return try {
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
                  "max_tokens": 200
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                return null
            }

            val contenido = extraerContenido(responseBody) ?: return null
            val limpio = contenido
                .replace("```json", "")
                .replace("```", "")
                .trim()

            json.decodeFromString<ProductoDetectado>(limpio)
        } catch (e: Exception) {
            null
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