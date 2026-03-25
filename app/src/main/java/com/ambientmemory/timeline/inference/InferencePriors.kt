package com.ambientmemory.timeline.inference

import com.ambientmemory.timeline.data.db.UserInsightEntity
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/**
 * Applies only [UserInsightEntity] rows the user has **confirmed**.
 * Weak nudges: does not override non-unknown activities.
 */
object InferencePriors {
    fun computeSceneConfidence(scene: SceneUnderstandingResult): Float =
        when (scene.sceneSource) {
            "perceptron" -> {
                val len = scene.rawSceneText.length
                (0.76f + (len / 420f) * 0.14f).coerceIn(0.74f, 0.92f)
            }
            else -> {
                var c = 0.44f
                if (scene.placeCategory != "unknown") c += 0.12f
                c += scene.objects.size.coerceAtMost(14) * 0.014f
                c.coerceIn(0.38f, 0.74f)
            }
        }

    fun applyConfirmedInsights(
        inference: InferenceOutput,
        scene: SceneUnderstandingResult,
        eventTimeMillis: Long,
        confirmed: List<UserInsightEntity>,
    ): InferenceOutput {
        if (confirmed.isEmpty() || inference.activity != "unknown") return inference

        val keys = confirmed.map { it.insightKey }.toSet()
        val placeFeedback = parseFeedbackPlacePriors(keys)
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.timeInMillis = eventTimeMillis
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val weekday =
            cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.FRIDAY
        val tokens = scene.objects.map { it.lowercase() }.toSet()
        val workplaceLike =
            scene.placeCategory == "office" ||
                tokens.any {
                    it in
                        setOf(
                            "workspace",
                            "workstation",
                            "laptop",
                            "monitor",
                            "keyboard",
                            "mouse",
                            "desk",
                            "computer",
                        )
                }

        if ("habit_home_evening" in keys &&
            scene.placeCategory == "home" &&
            hour in 18..23
        ) {
            return inference.copy(
                activity = "relaxing",
                confidence = max(inference.confidence, 0.52f),
                howSummary =
                    mergeHow(
                        inference.howSummary,
                        "matches your confirmed evening-at-home pattern",
                    ),
                whySummary = inference.whySummary ?: "downtime",
            )
        }

        if ("habit_weekday_workspace" in keys && weekday && workplaceLike) {
            return inference.copy(
                activity = "working",
                confidence = max(inference.confidence, 0.54f),
                whereLabel =
                    if (inference.whereLabel == "unknown" && scene.placeCategory == "office") {
                        "office"
                    } else {
                        inference.whereLabel
                    },
                howSummary =
                    mergeHow(
                        inference.howSummary,
                        "matches your confirmed weekday workspace pattern",
                    ),
                whySummary = inference.whySummary ?: "likely focused work",
            )
        }

        val placeToMatch =
            when {
                scene.placeCategory != "unknown" -> scene.placeCategory
                inference.whereLabel != "unknown" -> inference.whereLabel
                else -> ""
            }
        val feedbackActivity = placeFeedback[placeToMatch]
        if (!feedbackActivity.isNullOrBlank()) {
            return inference.copy(
                activity = feedbackActivity,
                confidence = max(inference.confidence, 0.6f),
                whereLabel = if (placeToMatch.isBlank()) inference.whereLabel else placeToMatch,
                howSummary =
                    mergeHow(
                        inference.howSummary,
                        "matches your confirmed correction pattern",
                    ),
            )
        }

        return inference
    }

    private fun mergeHow(
        existing: String,
        note: String,
    ): String =
        listOf(existing.trim(), note)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
            .take(200)

    private fun parseFeedbackPlacePriors(keys: Set<String>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        keys.forEach { k ->
            if (!k.startsWith("feedback_place_")) return@forEach
            val marker = "_activity_"
            val idx = k.indexOf(marker)
            if (idx <= "feedback_place_".length) return@forEach
            val place = k.substring("feedback_place_".length, idx)
            val activity = k.substring(idx + marker.length)
            if (place.isBlank() || activity.isBlank()) return@forEach
            out[place] = activity
        }
        return out
    }
}
