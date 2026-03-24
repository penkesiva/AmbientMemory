package com.ambientmemory.timeline.inference

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.ambientmemory.timeline.diagnostics.CaptureEventLog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SceneUnderstandingResult(
    val placeCategory: String,
    val objects: List<String>,
    val peopleCount: Int,
    val rawSceneText: String,
    val privacyFlags: Map<String, Boolean>,
    val structuredTags: Map<String, String>,
)

class SceneUnderstandingAdapter(
    private val perceptronClient: PerceptronCaptionClient? = null,
) {
    private val labeler =
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.45f).build(),
        )

    private val objectDetector =
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build(),
        )

    suspend fun analyzeImageFile(
        file: File,
        usePerceptronCaptioning: Boolean = false,
    ): SceneUnderstandingResult {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            if (usePerceptronCaptioning) {
                if (perceptronClient?.isConfigured() != true) {
                    CaptureEventLog.add("Perceptron fallback: API key missing")
                } else {
                    CaptureEventLog.add("Perceptron request")
                }
                val caption =
                    runCatching { perceptronClient?.captionConcise(file) }
                        .getOrNull()
                        ?.trim()
                        .orEmpty()
                if (caption.isNotBlank()) {
                    CaptureEventLog.add("Perceptron response ok")
                    CaptureEventLog.add("Perceptron caption: ${caption.take(96)}")
                    return buildResultFromCaption(caption, bmp.width, bmp.height)
                }
                CaptureEventLog.add("Perceptron fallback: empty response")
                Log.i(TAG, "Perceptron caption unavailable, falling back to on-device scene analysis")
            }
            val image = InputImage.fromBitmap(bmp, 0)
            val labels = labeler.process(image).await()
            val objs = objectDetector.process(image).await()
            return buildResult(labels, objs, bmp.width, bmp.height)
        } finally {
            if (bmp.width > 1) bmp.recycle()
        }
    }

    private fun buildResultFromCaption(
        caption: String,
        width: Int,
        height: Int,
    ): SceneUnderstandingResult {
        val allTokens =
            caption
                .lowercase()
                .split(Regex("[^a-z0-9]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(40)
        val peopleCount =
            when {
                caption.contains("no people", ignoreCase = true) -> 0
                caption.contains("single person", ignoreCase = true) ||
                    caption.contains("one person", ignoreCase = true) -> 1
                caption.contains("two people", ignoreCase = true) -> 2
                allTokens.any { it == "people" || it == "person" } -> 1
                else -> 0
            }
        val place = inferPlace(allTokens)
        val privacy =
            mapOf(
                "screen_visible" to allTokens.any { it in setOf("computer", "laptop", "monitor", "screen", "television") },
                "sensitive_scene" to allTokens.any { it in setOf("document", "certificate", "id", "passport") },
                "faces_present" to (peopleCount > 0),
            )
        val tags =
            mapOf(
                "top_labels" to allTokens.take(5).joinToString("|"),
                "object_names" to allTokens.drop(5).take(5).joinToString("|"),
                "dims" to "${width}x$height",
            )
        return SceneUnderstandingResult(
            placeCategory = place,
            objects = allTokens,
            peopleCount = peopleCount,
            rawSceneText = caption.take(280),
            privacyFlags = privacy,
            structuredTags = tags,
        )
    }

    private fun buildResult(
        labels: List<ImageLabel>,
        objects: List<DetectedObject>,
        width: Int,
        height: Int,
    ): SceneUnderstandingResult {
        val labelTexts =
            labels
                .sortedByDescending { it.confidence }
                .take(12)
                .map { it.text.lowercase() }
        val objectLabels =
            objects.flatMap { obj ->
                obj.labels.map { it.text.lowercase() }
            }.distinct()

        val allTokens = (labelTexts + objectLabels).distinct()
        val rawSceneText = allTokens.joinToString(", ")

        var people = 0
        objects.forEach { o ->
            val hasPerson = o.labels.any { it.text.equals("Person", true) }
            if (hasPerson) people++
        }
        if (people == 0 && allTokens.any { it.contains("person") || it.contains("people") }) {
            people = 1
        }

        val place = inferPlace(allTokens)
        val privacy =
            mapOf(
                "screen_visible" to allTokens.any { it.contains("computer") || it.contains("laptop") || it.contains("monitor") || it.contains("television") },
                "sensitive_scene" to allTokens.any { it.contains("document") || it.contains("certificate") },
                "faces_present" to (people > 0),
            )

        val tags =
            mapOf(
                "top_labels" to labelTexts.take(5).joinToString("|"),
                "object_names" to objectLabels.take(5).joinToString("|"),
                "dims" to "${width}x$height",
            )

        return SceneUnderstandingResult(
            placeCategory = place,
            objects = allTokens,
            peopleCount = people,
            rawSceneText = rawSceneText,
            privacyFlags = privacy,
            structuredTags = tags,
        )
    }

    private fun inferPlace(tokens: List<String>): String {
        val tokenSet = tokens.map { it.lowercase() }.toSet()
        return when {
            tokenSet.intersects("vehicle", "car", "bus", "train") -> "vehicle"
            tokenSet.intersects("restaurant", "food", "meal", "dish") -> "restaurant"
            tokenSet.intersects("office", "desk", "computer") -> "office"
            tokenSet.intersects("sofa", "couch", "bed", "living", "room", "door", "wall", "socket", "plug") -> "home"
            tokenSet.intersects("tree", "grass", "road", "sky") -> "outdoors"
            tokenSet.intersects("hallway", "corridor", "stairs") -> "hallway"
            else -> "unknown"
        }
    }

    private fun Set<String>.intersects(vararg terms: String): Boolean = terms.any { contains(it) }

    fun toJsonPayload(scene: SceneUnderstandingResult): String {
        val o = JSONObject()
        o.put("place_category", scene.placeCategory)
        o.put("objects", JSONArray(scene.objects.take(20)))
        o.put("people_count", scene.peopleCount)
        o.put("raw_scene_text", scene.rawSceneText)
        val pf = JSONObject()
        scene.privacyFlags.forEach { (k, v) -> pf.put(k, v) }
        o.put("privacy_flags", pf)
        return o.toString()
    }

    companion object {
        private const val TAG = "SceneUnderstanding"
    }
}
