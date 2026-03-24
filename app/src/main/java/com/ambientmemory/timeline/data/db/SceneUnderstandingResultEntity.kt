package com.ambientmemory.timeline.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scene_understanding_results",
    foreignKeys = [
        ForeignKey(
            entity = RawCaptureEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["capture_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("capture_id", unique = true)],
)
data class SceneUnderstandingResultEntity(
    @ColumnInfo(name = "capture_id")
    @PrimaryKey val captureId: Long,
    @ColumnInfo(name = "place_category")
    val placeCategory: String,
    @ColumnInfo(name = "objects_json")
    val objectsJson: String,
    @ColumnInfo(name = "people_count")
    val peopleCount: Int,
    @ColumnInfo(name = "raw_scene_text")
    val rawSceneText: String,
    @ColumnInfo(name = "privacy_flags_json")
    val privacyFlagsJson: String,
    @ColumnInfo(name = "structured_tags_json")
    val structuredTagsJson: String,
)
