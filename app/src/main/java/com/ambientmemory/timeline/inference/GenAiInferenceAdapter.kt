package com.ambientmemory.timeline.inference

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Optional Stage B refinement via ML Kit Prompt API / Gemini Nano when available.
 */
class GenAiInferenceAdapter {
    private val client = Generation.getClient()

    suspend fun isModelAvailable(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                client.checkStatus() == FeatureStatus.AVAILABLE
            }.getOrDefault(false)
        }

    suspend fun refineWithSceneJson(
        sceneJson: String,
        activityState: String,
        ruleOutput: InferenceOutput,
    ): InferenceOutput? =
        withContext(Dispatchers.IO) {
            if (client.checkStatus() != FeatureStatus.AVAILABLE) return@withContext null
            val prompt =
                """
                You classify a user's moment from scene JSON and device activity. Be conservative.
                Allowed activities: working, meeting, walking, eating, commuting, shopping, resting, unknown.
                Return ONLY compact JSON with keys:
                activity, confidence (0-1), short_reason, where_label, what_summary, how_summary, why_summary (empty if unsure).
                Scene JSON: $sceneJson
                Device activity state: $activityState
                Rule baseline: ${ruleOutput.activity} conf=${ruleOutput.confidence}
                """.trimIndent()
            val response =
                runCatching {
                    client.generateContent(
                        generateContentRequest(TextPart(prompt)) {
                            temperature = 0.2f
                            maxOutputTokens = 256
                        },
                    )
                }.getOrNull() ?: return@withContext null
            val text =
                response.candidates.firstOrNull()?.text?.trim()
                    ?: return@withContext null
            parseLlmJson(text, ruleOutput)
        }

    private fun parseLlmJson(text: String, fallback: InferenceOutput): InferenceOutput? {
        val jsonStr =
            text
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        return runCatching {
            val o = JSONObject(jsonStr)
            val activity = o.optString("activity", fallback.activity).lowercase()
            val conf = o.optDouble("confidence", fallback.confidence.toDouble()).toFloat().coerceIn(0f, 1f)
            InferenceOutput(
                activity = normalizeActivity(activity),
                confidence = conf,
                whereLabel = o.optString("where_label", fallback.whereLabel),
                whatSummary = o.optString("what_summary", fallback.whatSummary).take(200),
                howSummary = o.optString("how_summary", fallback.howSummary).take(200),
                whySummary = o.optString("why_summary", "").take(120).ifBlank { null },
            )
        }.getOrNull()
    }

    private fun normalizeActivity(a: String): String {
        val allowed =
            setOf(
                "working",
                "meeting",
                "walking",
                "eating",
                "commuting",
                "shopping",
                "resting",
                "unknown",
            )
        return if (a in allowed) a else "unknown"
    }
}
