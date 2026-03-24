package com.ambientmemory.timeline.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timeline_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CaptureSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["capture_session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("capture_session_id"),
        Index(value = ["start_time_millis"]),
    ],
)
data class TimelineSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "capture_session_id")
    val captureSessionId: Long,
    @ColumnInfo(name = "start_time_millis")
    val startTimeMillis: Long,
    @ColumnInfo(name = "end_time_millis")
    val endTimeMillis: Long?,
    val title: String,
    val summary: String,
    /** completed, in_progress, paused */
    val status: String,
    @ColumnInfo(name = "event_count")
    val eventCount: Int,
)
