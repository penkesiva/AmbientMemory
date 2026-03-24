package com.ambientmemory.timeline.inference

data class InferenceOutput(
    val activity: String,
    val confidence: Float,
    val whereLabel: String,
    val whatSummary: String,
    val howSummary: String,
    val whySummary: String?,
)

class RuleEngine {
    fun infer(scene: SceneUnderstandingResult, activityState: String): InferenceOutput {
        val tokens = scene.objects.map { it.lowercase() }.toSet()
        val people = scene.peopleCount
        val place = scene.placeCategory
        val act = activityState.uppercase()
        val vehicleVisual =
            tokens.intersects("vehicle", "car", "bus", "train")
        val roadLike =
            tokens.intersects("road", "traffic", "highway")
        val indoorLike =
            tokens.intersects("room", "door", "wall", "socket", "plug", "couch", "sofa", "bed") ||
                place == "home" ||
                place == "office"
        val likelyCommuting =
            vehicleVisual ||
                place == "vehicle" ||
                (act == "IN_VEHICLE" && !indoorLike && (roadLike || place == "outdoors" || place == "vehicle"))

        // High-signal commuting
        if (likelyCommuting) {
            return InferenceOutput(
                activity = "commuting",
                confidence = 0.78f,
                whereLabel = place,
                whatSummary = scene.rawSceneText.take(120),
                howSummary = "likely in or near a vehicle",
                whySummary = "travel",
            )
        }

        if (act == "WALKING" && (place == "outdoors" || tokens.intersects("tree", "road"))) {
            return InferenceOutput(
                activity = "walking",
                confidence = 0.76f,
                whereLabel = place,
                whatSummary = scene.rawSceneText.take(120),
                howSummary = "walking outdoors",
                whySummary = null,
            )
        }

        if (tokens.intersects("food", "meal", "plate", "dish") &&
            tokens.intersects("table", "chair")
        ) {
            return InferenceOutput(
                activity = "eating",
                confidence = 0.74f,
                whereLabel = place,
                whatSummary = scene.rawSceneText.take(120),
                howSummary = "seated with food visible",
                whySummary = "meal",
            )
        }

        if (people >= 2 && tokens.intersects("table", "chair") && place != "outdoors") {
            return InferenceOutput(
                activity = "meeting",
                confidence = 0.7f,
                whereLabel = place,
                whatSummary = scene.rawSceneText.take(120),
                howSummary = "multiple people around a table",
                whySummary = "likely group interaction",
            )
        }

        if (tokens.intersects("laptop", "computer", "monitor") &&
            (tokens.intersects("desk", "table") || place == "office") &&
            act == "STILL"
        ) {
            return InferenceOutput(
                activity = "working",
                confidence = 0.72f,
                whereLabel = place,
                whatSummary = scene.rawSceneText.take(120),
                howSummary = "seated with laptop or desk setup",
                whySummary = "likely working session",
            )
        }

        if (tokens.intersects("laptop", "computer") && people <= 1) {
            return InferenceOutput(
                activity = "working",
                confidence = 0.62f,
                whereLabel = place,
                whatSummary = scene.rawSceneText.take(120),
                howSummary = "likely focused screen work",
                whySummary = "likely working session",
            )
        }

        if (tokens.intersects("shop", "grocery", "cart", "store")) {
            return InferenceOutput(
                activity = "shopping",
                confidence = 0.6f,
                whereLabel = place,
                whatSummary = scene.rawSceneText.take(120),
                howSummary = "retail or shopping cues",
                whySummary = null,
            )
        }

        if (tokens.intersects("couch", "bed", "sofa") && act == "STILL") {
            return InferenceOutput(
                activity = "resting",
                confidence = 0.55f,
                whereLabel = place,
                whatSummary = scene.rawSceneText.take(120),
                howSummary = "stationary in a restful setting",
                whySummary = "downtime",
            )
        }

        if (act == "WALKING") {
            return InferenceOutput(
                activity = "walking",
                confidence = 0.55f,
                whereLabel = place,
                whatSummary = scene.rawSceneText.take(120),
                howSummary = "walking",
                whySummary = null,
            )
        }

        return InferenceOutput(
            activity = "unknown",
            confidence = 0.35f,
            whereLabel = place,
            whatSummary = scene.rawSceneText.take(120),
            howSummary = "insufficient cues",
            whySummary = null,
        )
    }

    private fun Set<String>.intersects(vararg terms: String): Boolean = terms.any { contains(it) }
}
