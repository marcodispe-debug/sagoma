package com.sagoma.planimetria.reading

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.UUID

/**
 * [PlanReader] sul server della lettura (server/lettura-piantine): `POST <url>/leggi` con l'immagine in base64 e il codice del
 * dispositivo (per il limite al giorno); [appKey] va nell'intestazione `X-Sagoma-App` se c'è. La lettura può durare più di un
 * minuto: si aspetta fino a [readTimeoutMillis].
 */
class HttpPlanReader(
    baseUrl: String,
    private val device: String,
    private val appKey: String = "",
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 420_000,
) : PlanReader {
    private val url = baseUrl.trimEnd('/') + "/leggi"

    override suspend fun read(image: ByteArray, mediaType: String, width: Int, height: Int): PlanReaderResult = withContext(Dispatchers.IO) {
        val body = JsonObject(
            mapOf(
                "image" to JsonPrimitive(Base64.getEncoder().encodeToString(image)),
                "media_type" to JsonPrimitive(mediaType),
                "width" to JsonPrimitive(width),
                "height" to JsonPrimitive(height),
                "device" to JsonPrimitive(device),
            ),
        ).toString().toByteArray()
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = connectTimeoutMillis
                conn.readTimeout = readTimeoutMillis
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                if (appKey.isNotEmpty()) conn.setRequestProperty("X-Sagoma-App", appKey)
                conn.outputStream.use { it.write(body) }
                val status = conn.responseCode
                val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
                if (status in 200..299) {
                    runCatching { PlanReaderResult.Ok(PlanReading.decode(text)) }.getOrDefault(PlanReaderResult.Unreachable)
                } else {
                    val error = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                    val code = error?.get("error")?.jsonPrimitive?.contentOrNull
                    if (code == null) PlanReaderResult.Unreachable
                    else PlanReaderResult.Refused(code, error["message"]?.jsonPrimitive?.contentOrNull)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            PlanReaderResult.Unreachable
        } catch (e: IllegalArgumentException) {
            PlanReaderResult.Unreachable
        }
    }

    companion object {
        /**
         * Codice casuale del dispositivo, scritto la prima volta in [dir]: serve solo al limite di letture al giorno, non dice
         * niente di chi usa l'app.
         */
        fun deviceId(dir: File): String {
            val file = File(dir, "dispositivo.txt")
            runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.matches(Regex("[A-Za-z0-9-]{8,64}")) }?.let { return it }
            return UUID.randomUUID().toString().also { id -> runCatching { file.writeText(id) } }
        }
    }
}
