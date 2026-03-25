package com.ambientmemory.timeline.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ambientmemory.timeline.AmbientMemoryApp
import com.ambientmemory.timeline.data.db.InferredEventEntity
import com.ambientmemory.timeline.data.db.UserInsightEntity
import com.ambientmemory.timeline.diagnostics.CaptureEventLog
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

class InsightAggregationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AmbientMemoryApp
        val events = app.graph.repository.getRecentInferred(400)
        if (events.size < 20) return Result.success()

        val candidates = buildCandidates(events)
        if (candidates.isEmpty()) return Result.success()

        candidates.forEach { app.graph.repository.mergeInsightCandidate(it) }
        CaptureEventLog.add("Insights updated: ${candidates.size} candidates")
        return Result.success()
    }

    private fun buildCandidates(events: List<InferredEventEntity>): List<UserInsightEntity> {
        val now = System.currentTimeMillis()
        val total = events.size.toFloat()
        val out = mutableListOf<UserInsightEntity>()

        val homeEvening =
            events.count {
                it.whereLabel == "home" && eventHour(it.startTimeMillis) in 18..23
            }
        val homeEveningRatio = homeEvening / total
        if (homeEvening >= 8 && homeEveningRatio >= 0.25f) {
            out +=
                makeCandidate(
                    key = "habit_home_evening",
                    category = "habit",
                    text = "Often spends evenings at home.",
                    confidence = (0.45f + homeEveningRatio).coerceIn(0f, 0.92f),
                    evidenceCount = homeEvening,
                    reason =
                        JSONObject()
                            .put("home_evening_count", homeEvening)
                            .put("sample_size", events.size)
                            .toString(),
                    now = now,
                )
        }

        val workplaceMoments =
            events.count {
                (it.whereLabel == "office" || it.activity == "working") && isWeekday(it.startTimeMillis)
            }
        val workRatio = workplaceMoments / total
        if (workplaceMoments >= 8 && workRatio >= 0.2f) {
            out +=
                makeCandidate(
                    key = "habit_weekday_workspace",
                    category = "habit",
                    text = "Frequently has weekday workspace moments.",
                    confidence = (0.45f + workRatio).coerceIn(0f, 0.9f),
                    evidenceCount = workplaceMoments,
                    reason =
                        JSONObject()
                            .put("weekday_workspace_count", workplaceMoments)
                            .put("sample_size", events.size)
                            .toString(),
                    now = now,
                )
        }

        val unknown = events.count { it.activity == "unknown" }
        val unknownRatio = unknown / total
        if (unknown >= 10 && unknownRatio >= 0.35f) {
            out +=
                makeCandidate(
                    key = "context_low_activity_confidence",
                    category = "context",
                    text = "Many moments are hard to classify; consider feedback for better personalization.",
                    confidence = (0.4f + unknownRatio * 0.5f).coerceIn(0f, 0.85f),
                    evidenceCount = unknown,
                    reason =
                        JSONObject()
                            .put("unknown_count", unknown)
                            .put("sample_size", events.size)
                            .toString(),
                    now = now,
                )
        }

        return out
    }

    private fun makeCandidate(
        key: String,
        category: String,
        text: String,
        confidence: Float,
        evidenceCount: Int,
        reason: String,
        now: Long,
    ): UserInsightEntity =
        UserInsightEntity(
            insightKey = key,
            category = category,
            text = text,
            status = "candidate",
            confidence = confidence,
            evidenceCount = evidenceCount,
            reasonJson = reason,
            source = "inferred",
            createdAtMillis = now,
            updatedAtMillis = now,
        )

    private fun eventHour(millis: Long): Int {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.timeInMillis = millis
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun isWeekday(millis: Long): Boolean {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.timeInMillis = millis
        return cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.FRIDAY
    }
}
