package com.ambientmemory.timeline.work

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ambientmemory.timeline.AmbientMemoryApp
import com.ambientmemory.timeline.data.db.InferredEventEntity
import com.ambientmemory.timeline.data.db.SceneUnderstandingResultEntity
import com.ambientmemory.timeline.diagnostics.CaptureEventLog
import com.ambientmemory.timeline.inference.InferenceOutput
import org.json.JSONArray
import org.json.JSONObject

class ProcessCaptureWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AmbientMemoryApp
        val g = app.graph
        val rawId = inputData.getLong(KEY_RAW_CAPTURE_ID, -1L)
        if (rawId < 0) return Result.failure()
        CaptureEventLog.add("Worker start (raw=$rawId)")

        val raw =
            g.repository.getRawCapture(rawId)
                ?: return Result.success()
        if (!raw.acceptedForProcessing) {
            return Result.success()
        }

        val settings = g.repository.readSettings()

        if (settings.wifiOnlyProcessing) {
            val cm = applicationContext.getSystemService(ConnectivityManager::class.java)
            if (cm.isActiveNetworkMetered) {
                CaptureEventLog.add("Worker retry: waiting for unmetered network")
                return Result.retry()
            }
        }

        if (g.repository.getInferredByRawCapture(rawId) != null) {
            return Result.success()
        }

        g.repository.updateRawCapture(raw.copy(processingStatus = "processing"))

        val file =
            try {
                java.io.File(raw.imageUri)
            } catch (_: Exception) {
                g.repository.updateRawCapture(raw.copy(processingStatus = "failed"))
                return Result.failure()
            }
        if (!file.exists()) {
            g.repository.updateRawCapture(raw.copy(processingStatus = "failed"))
            return Result.failure()
        }

        val sceneRaw =
            try {
                if (settings.perceptronCaptioningEnabled) {
                    CaptureEventLog.add("Scene path: perceptron enabled")
                } else {
                    CaptureEventLog.add("Scene path: perceptron disabled")
                }
                g.sceneAdapter.analyzeImageFile(
                    file = file,
                    usePerceptronCaptioning = settings.perceptronCaptioningEnabled,
                )
            } catch (_: Exception) {
                g.repository.updateRawCapture(raw.copy(processingStatus = "failed"))
                CaptureEventLog.add("Worker failed scene analysis (raw=$rawId)")
                return Result.retry()
            }

        val privacyJson =
            JSONObject().apply {
                sceneRaw.privacyFlags.forEach { (k, v) -> put(k, v) }
            }.toString()
        val tagsJson =
            JSONObject().apply {
                sceneRaw.structuredTags.forEach { (k, v) -> put(k, v) }
            }.toString()
        val sceneEntity =
            SceneUnderstandingResultEntity(
                captureId = rawId,
                placeCategory = sceneRaw.placeCategory,
                objectsJson = JSONArray(sceneRaw.objects).toString(),
                peopleCount = sceneRaw.peopleCount,
                rawSceneText = sceneRaw.rawSceneText,
                privacyFlagsJson = privacyJson,
                structuredTagsJson = tagsJson,
            )
        g.repository.insertScene(sceneEntity)

        var inference =
            if (settings.ruleEngineEnabled) {
                g.ruleEngine.infer(sceneRaw, raw.activityState)
            } else {
                InferenceOutput(
                    activity = "unknown",
                    confidence = 0.2f,
                    whereLabel = sceneRaw.placeCategory,
                    whatSummary = sceneRaw.rawSceneText.take(120),
                    howSummary = "rules disabled",
                    whySummary = null,
                )
            }
        CaptureEventLog.add(
            "INFER_V2 act=${raw.activityState} place=${sceneRaw.placeCategory} -> ${inference.activity} (${(inference.confidence * 100).toInt()}%)",
        )
        android.util.Log.i(
            "ProcessCaptureWorker",
            "INFER_V2 activityState=${raw.activityState} place=${sceneRaw.placeCategory} inferred=${inference.activity} confidence=${inference.confidence} scene=\"${sceneRaw.rawSceneText.take(120)}\"",
        )
        var source = "rules"

        val needsLlm =
            settings.onDeviceLlmEnabled &&
                (
                    inference.confidence < settings.llmConfidenceThreshold ||
                        inference.activity == "unknown"
                )

        val llmReady =
            needsLlm &&
                runCatching { g.genAiAdapter.isModelAvailable() }.getOrDefault(false)
        if (llmReady) {
            val json = g.sceneAdapter.toJsonPayload(sceneRaw)
            val refined =
                try {
                    g.genAiAdapter.refineWithSceneJson(json, raw.activityState, inference)
                } catch (_: Exception) {
                    null
                }
            if (refined != null) {
                source =
                    if (refined.activity != inference.activity || kotlin.math.abs(refined.confidence - inference.confidence) > 0.15f) {
                        "hybrid"
                    } else {
                        "llm"
                    }
                inference = refined
                CaptureEventLog.add("LLM refinement applied (raw=$rawId)")
            }
        }

        inference = applyIndoorSafetyOverride(inference, sceneRaw, raw.activityState)

        val now = raw.timestampMillis
        val inferredRow =
            InferredEventEntity(
                captureSessionId = raw.captureSessionId,
                timelineSessionId = null,
                rawCaptureId = rawId,
                startTimeMillis = now,
                endTimeMillis = now,
                activity = inference.activity,
                whereLabel = inference.whereLabel,
                whatSummary = inference.whatSummary,
                howSummary = inference.howSummary,
                whySummary = inference.whySummary,
                confidence = inference.confidence,
                inferenceSource = source,
            )

        g.repository.insertInferred(inferredRow)

        g.repository.updateRawCapture(raw.copy(processingStatus = "done"))
        CaptureEventLog.add("Worker done: ${inference.activity} (${(inference.confidence * 100).toInt()}%)")

        val cap = g.repository.getCaptureSession(raw.captureSessionId)
        if (cap != null) {
            val gapMs = settings.sessionGapMinutes * 60_000L
            g.sessionGrouper.regroupCaptureSession(cap.id, cap, gapMs)
        }

        return Result.success()
    }

    private fun applyIndoorSafetyOverride(
        inference: InferenceOutput,
        scene: com.ambientmemory.timeline.inference.SceneUnderstandingResult,
        activityState: String,
    ): InferenceOutput {
        if (inference.activity != "commuting") return inference

        val tokens = scene.objects.map { it.lowercase() }.toSet()
        val indoorByPlace = scene.placeCategory == "home" || scene.placeCategory == "office" || scene.placeCategory == "hallway"
        val indoorByObjects =
            tokens.any {
                it in
                    setOf(
                        "room",
                        "living",
                        "hallway",
                        "door",
                        "wall",
                        "socket",
                        "plug",
                        "carpet",
                        "rug",
                        "couch",
                        "sofa",
                        "bed",
                        "mirror",
                        "curtain",
                    )
            }
        val vehicleSignals = tokens.any { it in setOf("vehicle", "car", "bus", "train", "road", "traffic", "highway") }
        val forceIndoor = (indoorByPlace || indoorByObjects) && !vehicleSignals
        if (!forceIndoor) return inference

        CaptureEventLog.add("INFER_GUARD indoor override: commuting -> unknown")
        android.util.Log.i(
            "ProcessCaptureWorker",
            "INFER_GUARD override commuting->unknown place=${scene.placeCategory} activityState=$activityState tokens=${tokens.take(12)}",
        )
        return inference.copy(
            activity = "unknown",
            confidence = 0.48f,
            howSummary = "indoor scene conflicts with commuting",
            whySummary = null,
        )
    }

    companion object {
        const val KEY_RAW_CAPTURE_ID = "raw_capture_id"
    }
}
