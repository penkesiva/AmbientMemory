package com.ambientmemory.timeline.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CaptureSessionEntity::class,
        RawCaptureEventEntity::class,
        SceneUnderstandingResultEntity::class,
        InferredEventEntity::class,
        TimelineSessionEntity::class,
        UserInsightEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        private const val NAME = "ambient_memory.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
