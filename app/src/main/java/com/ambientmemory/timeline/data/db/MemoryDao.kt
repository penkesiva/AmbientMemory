package com.ambientmemory.timeline.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert
    suspend fun insertCaptureSession(entity: CaptureSessionEntity): Long

    @Update
    suspend fun updateCaptureSession(entity: CaptureSessionEntity)

    @Query(
        "SELECT * FROM capture_sessions WHERE state IN ('RUNNING','PAUSED') ORDER BY started_at_millis DESC LIMIT 1",
    )
    suspend fun getActiveCaptureSession(): CaptureSessionEntity?

    @Query(
        "SELECT * FROM capture_sessions WHERE state IN ('RUNNING','PAUSED') ORDER BY started_at_millis DESC LIMIT 1",
    )
    fun observeActiveCaptureSession(): Flow<CaptureSessionEntity?>

    @Query("SELECT * FROM capture_sessions WHERE id = :id")
    suspend fun getCaptureSession(id: Long): CaptureSessionEntity?

    @Query("SELECT * FROM capture_sessions ORDER BY started_at_millis DESC LIMIT :limit")
    fun observeRecentCaptureSessions(limit: Int): Flow<List<CaptureSessionEntity>>

    @Insert
    suspend fun insertRawCapture(entity: RawCaptureEventEntity): Long

    @Update
    suspend fun updateRawCapture(entity: RawCaptureEventEntity)

    @Query("SELECT * FROM raw_capture_events WHERE id = :id")
    suspend fun getRawCapture(id: Long): RawCaptureEventEntity?

    @Query(
        """
        SELECT * FROM raw_capture_events
        WHERE capture_session_id = :sessionId AND accepted_for_processing = 1
        ORDER BY timestamp_millis DESC LIMIT 1
        """,
    )
    suspend fun getLastAcceptedRawCapture(sessionId: Long): RawCaptureEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScene(entity: SceneUnderstandingResultEntity)

    @Query("SELECT * FROM scene_understanding_results WHERE capture_id = :captureId")
    suspend fun getSceneForCapture(captureId: Long): SceneUnderstandingResultEntity?

    @Insert
    suspend fun insertInferred(entity: InferredEventEntity): Long

    @Update
    suspend fun updateInferred(entity: InferredEventEntity)

    @Query("UPDATE inferred_events SET timeline_session_id = :timelineId WHERE id = :inferredId")
    suspend fun linkInferredToTimeline(inferredId: Long, timelineId: Long?)

    @Query("SELECT * FROM inferred_events WHERE raw_capture_id = :rawId LIMIT 1")
    suspend fun getInferredByRawCapture(rawId: Long): InferredEventEntity?

    @Query(
        """
        SELECT * FROM inferred_events
        WHERE timeline_session_id = :timelineId
        ORDER BY start_time_millis ASC
        """,
    )
    fun observeInferredForTimeline(timelineId: Long): Flow<List<InferredEventEntity>>

    @Query(
        """
        SELECT * FROM inferred_events
        WHERE capture_session_id = :captureSessionId
        ORDER BY start_time_millis ASC
        """,
    )
    suspend fun getInferredForCaptureSession(captureSessionId: Long): List<InferredEventEntity>

    @Insert
    suspend fun insertTimelineSession(entity: TimelineSessionEntity): Long

    @Update
    suspend fun updateTimelineSession(entity: TimelineSessionEntity)

    @Query("DELETE FROM timeline_sessions WHERE capture_session_id = :captureSessionId")
    suspend fun deleteTimelineSessionsForCapture(captureSessionId: Long)

    @Query(
        "UPDATE inferred_events SET timeline_session_id = NULL WHERE capture_session_id = :captureSessionId",
    )
    suspend fun clearTimelineIdsForCaptureSession(captureSessionId: Long)

    @Query(
        """
        SELECT * FROM timeline_sessions
        ORDER BY start_time_millis DESC
        """,
    )
    fun observeAllTimelineSessions(): Flow<List<TimelineSessionEntity>>

    @Query(
        """
        SELECT * FROM timeline_sessions
        WHERE capture_session_id = :captureSessionId
        ORDER BY start_time_millis ASC
        """,
    )
    suspend fun getTimelineSessionsForCapture(captureSessionId: Long): List<TimelineSessionEntity>

    @Query(
        """
        SELECT * FROM timeline_sessions
        ORDER BY COALESCE(end_time_millis, start_time_millis) DESC, start_time_millis DESC
        """,
    )
    fun observeTimelineSessionsLatestFirst(): Flow<List<TimelineSessionEntity>>

    @Query("DELETE FROM raw_capture_events")
    suspend fun deleteAllRawCaptures()

    @Query("DELETE FROM scene_understanding_results")
    suspend fun deleteAllScenes()

    @Query("DELETE FROM inferred_events")
    suspend fun deleteAllInferred()

    @Query("DELETE FROM timeline_sessions")
    suspend fun deleteAllTimelineSessions()

    @Query("DELETE FROM capture_sessions")
    suspend fun deleteAllCaptureSessions()

    @Query(
        """
        SELECT * FROM raw_capture_events
        WHERE capture_session_id = :sessionId AND processing_status = 'queued' AND accepted_for_processing = 1
        """,
    )
    suspend fun getQueuedRawCaptures(sessionId: Long): List<RawCaptureEventEntity>

    @Query(
        """
        SELECT * FROM raw_capture_events
        WHERE processing_status = 'queued' AND accepted_for_processing = 1
        """,
    )
    suspend fun getAllQueuedRawCaptures(): List<RawCaptureEventEntity>
}
