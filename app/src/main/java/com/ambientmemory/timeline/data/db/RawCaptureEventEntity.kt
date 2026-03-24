package com.ambientmemory.timeline.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_capture_events",
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
        Index(value = ["timestamp_millis"]),
    ],
)
data class RawCaptureEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "capture_session_id")
    val captureSessionId: Long,
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long,
    @ColumnInfo(name = "image_uri")
    val imageUri: String,
    @ColumnInfo(name = "image_hash")
    val imageHash: String?,
    /** Last known activity at capture time, e.g. STILL, WALKING */
    @ColumnInfo(name = "activity_state")
    val activityState: String,
    @ColumnInfo(name = "accepted_for_processing")
    val acceptedForProcessing: Boolean,
    @ColumnInfo(name = "dedupe_reason")
    val dedupeReason: String?,
    /** queued, processing, done, failed, skipped */
    @ColumnInfo(name = "processing_status")
    val processingStatus: String,
)
