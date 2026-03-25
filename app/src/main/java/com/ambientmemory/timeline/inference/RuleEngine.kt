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
        val tokens =
            scene.objects
                .map { normalizeToken(it) }
                .filter { it.isNotBlank() }
                .toSet()
        val people = scene.peopleCount
        val place = scene.placeCategory
        val act = activityState.uppercase()
        val vehicleVisual =
            tokens.intersects("vehicle", "car", "bus", "train")
        val roadLike =
            tokens.intersects("road", "traffic", "highway")
        val bathroomContext = isBathroomContext(place, tokens, scene.rawSceneText)
        val indoorLike =
            tokens.intersects("room", "door", "wall", "socket", "plug", "couch", "sofa", "bed") ||
                place == "home" ||
                place == "office" ||
                place == "bathroom"
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

        if (!bathroomContext &&
            tokens.intersects("food", "meal", "plate", "dish") &&
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

        if (act == "UNKNOWN") {
            val workplaceCues =
                place == "office" ||
                    tokens.intersects("workspace", "workstation", "laptop", "monitor", "keyboard", "mouse", "desk", "computer", "notebook")
            val socialCues = people >= 2 || tokens.intersects("people", "person", "group", "conversation")
            val mealCuesStrong = tokens.intersects("food", "meal", "plate", "dish")
            val mealCuesWeakCup = tokens.intersects("cup", "mug") && !bathroomContext
            val mealCues = mealCuesStrong || mealCuesWeakCup
            val exerciseCues = tokens.intersects("yoga", "mat", "dumbbell", "workout", "exercise", "stretch")
            val householdCues = tokens.intersects("broom", "vacuum", "laundry", "sink", "kitchen", "cleaning")
            val relaxCues = tokens.intersects("sofa", "couch", "bed", "pillow", "blanket", "tv", "television")
            val indoorCues = place == "home" || place == "hallway" || tokens.intersects("room", "door", "wall", "mat", "rug")

            if (bathroomContext && !mealCuesStrong) {
                return InferenceOutput(
                    activity = "household",
                    confidence = 0.55f,
                    whereLabel = if (place == "unknown") "bathroom" else place,
                    whatSummary = scene.rawSceneText.take(120),
                    howSummary = "bathroom or hygiene context",
                    whySummary = "routine",
                )
            }

            if (workplaceCues && !mealCues && !exerciseCues) {
                return InferenceOutput(
                    activity = "working",
                    confidence = 0.58f,
                    whereLabel = if (place == "unknown") "office" else place,
                    whatSummary = scene.rawSceneText.take(120),
                    howSummary = "workspace cues visible",
                    whySummary = "likely focused work",
                )
            }
            if (mealCues) {
                return InferenceOutput(
                    activity = "eating",
                    confidence = 0.57f,
                    whereLabel = if (place == "unknown") "home" else place,
                    whatSummary = scene.rawSceneText.take(120),
                    howSummary = "food or tableware cues in scene",
                    whySummary = "meal",
                )
            }
            if (exerciseCues) {
                return InferenceOutput(
                    activity = "exercising",
                    confidence = 0.56f,
                    whereLabel = if (place == "unknown") "home" else place,
                    whatSummary = scene.rawSceneText.take(120),
                    howSummary = "fitness or movement-related indoor cues",
                    whySummary = "health or routine",
                )
            }
            if (householdCues) {
                return InferenceOutput(
                    activity = "household",
                    confidence = 0.54f,
                    whereLabel = if (place == "unknown") "home" else place,
                    whatSummary = scene.rawSceneText.take(120),
                    howSummary = "household object cues detected",
                    whySummary = "daily chores",
                )
            }
            if (socialCues && indoorCues) {
                return InferenceOutput(
                    activity = "socializing",
                    confidence = 0.55f,
                    whereLabel = if (place == "unknown") "home" else place,
                    whatSummary = scene.rawSceneText.take(120),
                    howSummary = "multiple-person indoor context",
                    whySummary = "social interaction",
                )
            }
            if (place == "home" && relaxCues) {
                return InferenceOutput(
                    activity = "relaxing",
                    confidence = 0.56f,
                    whereLabel = place,
                    whatSummary = scene.rawSceneText.take(120),
                    howSummary = "home comfort cues with no movement signal",
                    whySummary = "downtime",
                )
            }
            if (indoorCues) {
                return InferenceOutput(
                    activity = "sitting",
                    confidence = 0.5f,
                    whereLabel = if (place == "unknown") "home" else place,
                    whatSummary = scene.rawSceneText.take(120),
                    howSummary = "indoor stationary context",
                    whySummary = null,
                )
            }
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

    private fun isBathroomContext(
        place: String,
        tokens: Set<String>,
        rawSceneText: String,
    ): Boolean {
        if (place == "bathroom") return true
        if (tokens.intersects(
                "bathroom",
                "toilet",
                "vanity",
                "shower",
                "bathtub",
                "sink",
                "toothbrush",
                "toiletries",
                "faucet",
            )
        ) {
            return true
        }
        val t = rawSceneText.lowercase()
        return listOf(
            "bathroom",
            "toilet",
            "vanity",
            "shower",
            "bathtub",
            "restroom",
            "washroom",
            "toothbrush",
            "toiletries",
        ).any { t.contains(it) }
    }

    private fun normalizeToken(token: String): String {
        val t = token.lowercase().trim()
        return when (t) {
            "laptopm" -> "laptop"
            "balck" -> "black"
            else -> t
        }.removeSuffix("s")
    }
}
