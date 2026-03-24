package com.ambientmemory.timeline.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inferred_events",
    foreignKeys = [
        ForeignKey(
            entity = CaptureSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["capture_session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RawCaptureEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["raw_capture_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TimelineSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["timeline_session_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("capture_session_id"),
        Index("raw_capture_id", unique = true),
        Index("timeline_session_id"),
        Index("start_time_millis"),
    ],
)
data class InferredEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "capture_session_id")
    val captureSessionId: Long,
    @ColumnInfo(name = "timeline_session_id")
    val timelineSessionId: Long?,
    @ColumnInfo(name = "raw_capture_id")
    val rawCaptureId: Long,
    @ColumnInfo(name = "start_time_millis")
    val startTimeMillis: Long,
    @ColumnInfo(name = "end_time_millis")
    val endTimeMillis: Long?,
    val activity: String,
    @ColumnInfo(name = "where_label")
    val whereLabel: String,
    @ColumnInfo(name = "what_summary")
    val whatSummary: String,
    @ColumnInfo(name = "how_summary")
    val howSummary: String,
    @ColumnInfo(name = "why_summary")
    val whySummary: String?,
    val confidence: Float,
    /** rules, llm, hybrid */
    @ColumnInfo(name = "inference_source")
    val inferenceSource: String,
)
