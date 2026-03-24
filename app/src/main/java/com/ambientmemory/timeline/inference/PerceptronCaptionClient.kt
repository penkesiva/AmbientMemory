package com.ambientmemory.timeline.inference

import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class PerceptronCaptionClient(
    private val apiKey: String,
    private val model: String,
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank()

    fun captionConcise(file: File): String? {
        if (!isConfigured() || !file.exists()) return null
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "Perceptron request start file=${file.name} bytes=${file.length()}")

        val mime = guessMimeType(file)
        val base64 = Base64.getEncoder().encodeToString(file.readBytes())
        val dataUrl = "data:$mime;base64,$base64"
        val payload = buildPayload(dataUrl)

        // Retry once to reduce transient network/server failures.
        repeat(MAX_ATTEMPTS) { attemptIndex ->
            val attempt = attemptIndex + 1
            val caption = runSingleAttempt(payload, attempt)
            if (!caption.isNullOrBlank()) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                Log.i(TAG, "Perceptron caption success in ${elapsed}ms attempts=$attempt")
                return caption
            }
        }

        val elapsed = SystemClock.elapsedRealtime() - startedAt
        Log.i(TAG, "Perceptron caption unavailable after ${elapsed}ms")
        return null
    }

    private fun runSingleAttempt(
        payload: String,
        attempt: Int,
    ): String? {
        val attemptStart = SystemClock.elapsedRealtime()
        val connection =
            (URL(CHAT_COMPLETIONS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

        return runCatching {
            connection.outputStream.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val elapsed = SystemClock.elapsedRealtime() - attemptStart
            Log.i(TAG, "Perceptron attempt=$attempt responseCode=$code elapsedMs=$elapsed")

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = parseErrorMessage(raw)
                if (message.isNotBlank()) {
                    Log.w(TAG, "Perceptron error attempt=$attempt message=${message.take(240)}")
                }
                return@runCatching null
            }

            val caption = parseCaption(raw)
            if (caption.isNullOrBlank()) {
                Log.i(TAG, "Perceptron response empty attempt=$attempt")
            } else {
                Log.i(TAG, "Perceptron caption=\"${caption.take(140)}\"")
            }
            caption
        }.onFailure {
            Log.w(TAG, "Perceptron call failed attempt=$attempt", it)
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun buildPayload(dataUrl: String): String =
        JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("type", "text")
                                        .put(
                                            "text",
                                            "Provide a concise, human-friendly caption for this scene. Focus on place, key objects, and people count cues.",
                                        ),
                                ).put(
                                    JSONObject()
                                        .put("type", "image_url")
                                        .put(
                                            "image_url",
                                            JSONObject().put("url", dataUrl),
                                        ),
                                ),
                        ),
                ),
            ).put("temperature", 0.1)
            .toString()

    private fun parseCaption(response: String): String? {
        if (response.isBlank()) return null
        val root = JSONObject(response)
        val firstChoice = root.optJSONArray("choices")?.optJSONObject(0)

        // OpenAI-compatible chat shape: choices[0].message.content
        val messageContent = firstChoice?.optJSONObject("message")?.opt("content")
        val fromMessage = parseContentField(messageContent)
        if (!fromMessage.isNullOrBlank()) return fromMessage

        // Some providers return choices[0].text.
        val fromChoiceText = firstChoice?.optString("text").orEmpty().trim()
        if (fromChoiceText.isNotBlank()) return fromChoiceText

        // Some providers return top-level output_text.
        val fromTopLevel = parseContentField(root.opt("output_text"))
        if (!fromTopLevel.isNullOrBlank()) return fromTopLevel

        return null
    }

    private fun parseContentField(content: Any?): String? =
        when (content) {
            is String -> content.trim().ifBlank { null }
            is JSONArray -> {
                val parts = mutableListOf<String>()
                for (i in 0 until content.length()) {
                    val part = content.opt(i)
                    when (part) {
                        is String -> {
                            val value = part.trim()
                            if (value.isNotBlank()) parts.add(value)
                        }

                        is JSONObject -> {
                            val text =
                                part.optString("text")
                                    .ifBlank { part.optString("output_text") }
                                    .trim()
                            if (text.isNotBlank()) parts.add(text)
                        }
                    }
                }
                parts.joinToString(" ").ifBlank { null }
            }

            else -> null
        }

    private fun parseErrorMessage(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            val root = JSONObject(raw)
            val err = root.opt("error")
            when (err) {
                is JSONObject ->
                    err.optString("message")
                        .ifBlank { err.toString() }

                is String -> err
                else -> raw
            }.trim()
        }.getOrElse {
            raw.trim()
        }
    }

    private fun guessMimeType(file: File): String {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".png") -> "image/png"
            name.endsWith(".webp") -> "image/webp"
            else -> "image/jpeg"
        }
    }

    companion object {
        private const val TAG = "PerceptronClient"
        private const val CHAT_COMPLETIONS_URL = "https://api.perceptron.inc/v1/chat/completions"
        private const val MAX_ATTEMPTS = 2
    }
}
