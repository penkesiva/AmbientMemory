package com.ambientmemory.timeline.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "capture_sessions",
    indices = [Index(value = ["started_at_millis"])],
)
data class CaptureSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at_millis")
    val startedAtMillis: Long,
    @ColumnInfo(name = "ended_at_millis")
    val endedAtMillis: Long?,
    /** RUNNING, PAUSED, STOPPED */
    val state: String,
)
