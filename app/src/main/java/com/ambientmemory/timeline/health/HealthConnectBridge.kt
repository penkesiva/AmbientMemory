package com.ambientmemory.timeline.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Reads a small Health Connect snapshot around capture time (watch / Samsung Health → HC).
 */
object HealthConnectBridge {
    private const val TAG = "HealthConnect"

    /** Google Play Health Connect APK / system provider. */
    const val PROVIDER_PACKAGE_NAME: String = "com.google.android.apps.healthdata"

    val READ_PERMISSIONS: Set<String> =
        setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
        )

    fun isSdkAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE_NAME) ==
            HealthConnectClient.SDK_AVAILABLE

    /** [ExerciseSessionRecord.exerciseType] is an Int (see HC EXERCISE_TYPE_*), not an enum. */
    private fun exerciseTypeLabel(type: Int): String =
        when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill run"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Biking"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Indoor cycling"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Open water swim"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Weightlifting"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing machine"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "Rowing"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE ->
                "Stair climbing"
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "Workout"
            else -> "Workout"
        }

    /**
     * One-line suffix for [com.ambientmemory.timeline.data.db.InferredEventEntity.howSummary].
     */
    fun formatHowSummaryLine(json: String?): String? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            val parts = mutableListOf<String>()
            if (o.has("hr") && !o.isNull("hr")) {
                parts.add("HR ${o.getInt("hr")} bpm")
            }
            if (o.optBoolean("inExercise", false)) {
                val title = o.optString("exerciseTitle", "").trim()
                parts.add(if (title.isNotEmpty()) title else "Exercise")
            }
            val steps = o.optLong("steps5m", -1L)
            if (steps >= 0) {
                parts.add("~$steps steps (5m)")
            }
            if (parts.isEmpty()) return null
            "Health: ${parts.joinToString(" · ")}"
        }.getOrElse {
            Log.w(TAG, "formatHowSummaryLine parse failed", it)
            null
        }
    }

    suspend fun readSnapshotJson(
        context: Context,
        atEpochMillis: Long,
    ): String? {
        if (!isSdkAvailable(context)) return null
        val client =
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrElse {
                Log.w(TAG, "getOrCreate failed", it)
                return null
            }
        val granted =
            runCatching { client.permissionController.getGrantedPermissions() }.getOrElse {
                Log.w(TAG, "getGrantedPermissions failed", it)
                return null
            }
        if (!granted.containsAll(READ_PERMISSIONS)) {
            return null
        }

        val at = Instant.ofEpochMilli(atEpochMillis)
        val windowStart = at.minus(Duration.ofMinutes(5))
        val windowEnd = at.plus(Duration.ofMinutes(2))
        val filter = TimeRangeFilter.between(windowStart, windowEnd)

        return runCatching {
            val o = JSONObject()

            val hrRequest =
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = filter,
                )
            val hrResponse = client.readRecords(hrRequest)
            var bestBpm: Long? = null
            var bestDt = Long.MAX_VALUE // millis distance to capture instant
            for (rec in hrResponse.records) {
                for (s in rec.samples) {
                    val dt = abs(Duration.between(s.time, at).toMillis())
                    if (dt < bestDt) {
                        bestDt = dt
                        bestBpm = s.beatsPerMinute
                    }
                }
            }
            bestBpm?.let { o.put("hr", it.toInt()) }

            val exRequest =
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = filter,
                )
            val exResponse = client.readRecords(exRequest)
            var active: ExerciseSessionRecord? = null
            for (rec in exResponse.records) {
                val start = rec.startTime
                val end = rec.endTime
                if (!start.isAfter(at) && !end.isBefore(at)) {
                    active = rec
                    break
                }
            }
            if (active != null) {
                o.put("inExercise", true)
                val title = active.title?.trim().orEmpty()
                if (title.isNotEmpty()) {
                    o.put("exerciseTitle", title)
                } else {
                    o.put("exerciseTitle", exerciseTypeLabel(active.exerciseType))
                }
            }

            val stepsRequest =
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = filter,
                )
            val stepsResponse = client.readRecords(stepsRequest)
            var stepSum = 0L
            for (rec in stepsResponse.records) {
                val rs = rec.startTime
                val re = rec.endTime
                if (rs.isBefore(windowEnd) && re.isAfter(windowStart)) {
                    stepSum += rec.count
                }
            }
            if (stepSum > 0) {
                o.put("steps5m", stepSum)
            }

            val hasSignal =
                o.has("hr") || o.optBoolean("inExercise", false) || o.has("steps5m")
            if (!hasSignal) null else o.toString()
        }.getOrElse {
            Log.w(TAG, "readSnapshotJson failed", it)
            null
        }
    }
}
