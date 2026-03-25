package com.ambientmemory.timeline.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_insights",
    indices = [
        Index("insight_key", unique = true),
        Index("status"),
        Index("updated_at_millis"),
    ],
)
data class UserInsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "insight_key")
    val insightKey: String,
    val category: String,
    val text: String,
    val status: String,
    val confidence: Float,
    @ColumnInfo(name = "evidence_count")
    val evidenceCount: Int,
    @ColumnInfo(name = "reason_json")
    val reasonJson: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long,
)
