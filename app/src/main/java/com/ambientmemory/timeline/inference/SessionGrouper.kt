package com.ambientmemory.timeline.inference

import com.ambientmemory.timeline.data.db.CaptureSessionEntity
import com.ambientmemory.timeline.data.db.InferredEventEntity
import com.ambientmemory.timeline.data.db.TimelineSessionEntity
import com.ambientmemory.timeline.data.repo.MemoryRepository

class SessionGrouper(
    private val repository: MemoryRepository,
) {

    suspend fun regroupCaptureSession(
        captureSessionId: Long,
        captureSession: CaptureSessionEntity,
        gapThresholdMillis: Long,
    ) {
        val events = repository.getInferredForCaptureSession(captureSessionId).sortedBy { it.startTimeMillis }
        if (events.isEmpty()) return

        repository.deleteTimelineForRegroup(captureSessionId)

        val groups = mutableListOf<MutableList<InferredEventEntity>>()
        var current = mutableListOf<InferredEventEntity>()
        for (e in events) {
            if (current.isEmpty()) {
                current.add(e)
                continue
            }
            val last = current.last()
            val gap = e.startTimeMillis - last.startTimeMillis
            val sameKind = sameGroup(last, e)
            if (gap <= gapThresholdMillis && sameKind) {
                current.add(e)
            } else {
                groups.add(current)
                current = mutableListOf(e)
            }
        }
        groups.add(current)

        val isRunning = captureSession.state == "RUNNING"
        val isPaused = captureSession.state == "PAUSED"

        groups.forEachIndexed { index, list ->
            val start = list.first().startTimeMillis
            val end = list.last().endTimeMillis ?: list.last().startTimeMillis
            val isLast = index == groups.lastIndex
            val status =
                when {
                    isLast && isRunning -> "in_progress"
                    isLast && isPaused -> "paused"
                    else -> "completed"
                }
            val title = buildTitle(list)
            val summary = buildSummary(list)
            val timelineId =
                repository.insertTimelineSession(
                    TimelineSessionEntity(
                        captureSessionId = captureSessionId,
                        startTimeMillis = start,
                        endTimeMillis = if (status == "in_progress") null else end,
                        title = title,
                        summary = summary,
                        status = status,
                        eventCount = list.size,
                    ),
                )
            list.forEach { ev ->
                repository.updateInferred(
                    ev.copy(
                        timelineSessionId = timelineId,
                        endTimeMillis = ev.endTimeMillis ?: ev.startTimeMillis,
                    ),
                )
            }
        }
    }

    private fun sameGroup(
        a: InferredEventEntity,
        b: InferredEventEntity,
    ): Boolean {
        if (a.activity != b.activity) return false
        if (a.whereLabel != b.whereLabel) {
            val unk = setOf("unknown", "")
            if (a.whereLabel !in unk && b.whereLabel !in unk) return false
        }
        return true
    }

    private fun buildTitle(list: List<InferredEventEntity>): String {
        val dominant =
            list
                .asReversed()
                .firstOrNull { it.activity != "unknown" }
                ?.activity
                ?: "unknown"
        if (dominant == "unknown") {
            val ref = list.last()
            narrativePlaceTitle(ref.whereLabel)?.let { return it }
            val snippet = ref.whatSummary.trim().take(40)
            if (snippet.isNotBlank()) {
                return if (ref.whatSummary.length > 40) "$snippet…" else snippet
            }
            return "Moment"
        }
        return when (dominant) {
            "working" -> "Working session"
            "eating" -> "Meal"
            "meeting" -> "Meeting"
            "walking" -> "Walk"
            "commuting" -> "Commute"
            "shopping" -> "Shopping"
            "resting" -> "Rest"
            "relaxing" -> "Relaxing"
            "sitting" -> "Sitting"
            "exercising" -> "Exercise"
            "household" -> "Home tasks"
            "socializing" -> "Social"
            else -> "Moment"
        }
    }

    private fun narrativePlaceTitle(whereLabel: String): String? {
        val w = whereLabel.trim().lowercase()
        if (w.isBlank() || w == "unknown") return null
        return when (w) {
            "home" -> "At home"
            "office" -> "Workspace"
            "bathroom" -> "Bathroom"
            "hallway" -> "Hallway"
            "outdoors" -> "Outside"
            "vehicle" -> "On the go"
            "restaurant" -> "Dining out"
            else -> whereLabel.replaceFirstChar { it.uppercase() }
        }
    }

    private fun buildSummary(list: List<InferredEventEntity>): String {
        val newest = list.last()
        val whatNewest = newest.whatSummary.take(120).trim()
        if (whatNewest.isNotBlank()) return whatNewest
        val howNewest = newest.howSummary.trim()
        if (howNewest.isNotBlank()) return howNewest
        val first = list.first()
        val whatFirst = first.whatSummary.take(120).trim()
        if (whatFirst.isNotBlank()) return whatFirst
        val howFirst = first.howSummary.trim()
        if (howFirst.isNotBlank()) return howFirst
        return newest.activity
    }
}
