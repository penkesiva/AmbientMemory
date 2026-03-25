package com.ambientmemory.timeline.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.ambientmemory.timeline.data.ImageStorage
import com.ambientmemory.timeline.data.db.AppDatabase
import com.ambientmemory.timeline.data.db.CaptureSessionEntity
import com.ambientmemory.timeline.data.db.InferredEventEntity
import com.ambientmemory.timeline.data.db.RawCaptureEventEntity
import com.ambientmemory.timeline.data.db.SceneUnderstandingResultEntity
import com.ambientmemory.timeline.data.db.TimelineSessionEntity
import com.ambientmemory.timeline.data.db.UserInsightEntity
import com.ambientmemory.timeline.data.prefs.AppPreferenceDefaults
import com.ambientmemory.timeline.data.prefs.AppPreferenceKeys
import com.ambientmemory.timeline.data.prefs.appDataStore
import com.ambientmemory.timeline.diagnostics.CaptureEventLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class MemoryRepository(
    context: Context,
    val db: AppDatabase,
    private val imageStorage: ImageStorage = ImageStorage(context),
) {
    private val appContext = context.applicationContext
    private val dao = db.memoryDao()
    private val dataStore = appContext.appDataStore

    val timelineSessions: Flow<List<TimelineSessionEntity>> = dao.observeTimelineSessionsLatestFirst()

    val activeCaptureSession: Flow<CaptureSessionEntity?> = dao.observeActiveCaptureSession()
    val insights: Flow<List<UserInsightEntity>> = dao.observeInsights()

    fun observeInferredForTimeline(timelineId: Long): Flow<List<InferredEventEntity>> =
        dao.observeInferredForTimeline(timelineId)

    suspend fun getCaptureSession(id: Long): CaptureSessionEntity? = dao.getCaptureSession(id)

    suspend fun getActiveCaptureSessionOrNull(): CaptureSessionEntity? = dao.getActiveCaptureSession()

    suspend fun startCaptureSession(): Long {
        val entity =
            CaptureSessionEntity(
                startedAtMillis = System.currentTimeMillis(),
                endedAtMillis = null,
                state = "RUNNING",
            )
        return dao.insertCaptureSession(entity)
    }

    suspend fun pauseCaptureSession(id: Long) {
        val s = dao.getCaptureSession(id) ?: return
        if (s.state == "STOPPED") return
        dao.updateCaptureSession(s.copy(state = "PAUSED"))
    }

    suspend fun resumeCaptureSession(id: Long) {
        val s = dao.getCaptureSession(id) ?: return
        if (s.state == "STOPPED") return
        dao.updateCaptureSession(s.copy(state = "RUNNING"))
    }

    suspend fun stopCaptureSession(id: Long) {
        val s = dao.getCaptureSession(id) ?: return
        val now = System.currentTimeMillis()
        dao.updateCaptureSession(
            s.copy(
                state = "STOPPED",
                endedAtMillis = now,
            ),
        )
    }

    suspend fun insertRawCapture(row: RawCaptureEventEntity): Long = dao.insertRawCapture(row)

    suspend fun updateRawCapture(row: RawCaptureEventEntity) = dao.updateRawCapture(row)

    suspend fun getRawCapture(id: Long): RawCaptureEventEntity? = dao.getRawCapture(id)

    suspend fun getLastAcceptedRawCapture(sessionId: Long): RawCaptureEventEntity? =
        dao.getLastAcceptedRawCapture(sessionId)

    suspend fun insertScene(row: SceneUnderstandingResultEntity) = dao.insertScene(row)

    suspend fun getSceneForCapture(captureId: Long): SceneUnderstandingResultEntity? =
        dao.getSceneForCapture(captureId)

    suspend fun insertInferred(row: InferredEventEntity): Long = dao.insertInferred(row)

    suspend fun updateInferred(row: InferredEventEntity) = dao.updateInferred(row)

    suspend fun getInferredByRawCapture(rawId: Long): InferredEventEntity? =
        dao.getInferredByRawCapture(rawId)

    suspend fun getInferredForCaptureSession(captureSessionId: Long): List<InferredEventEntity> =
        dao.getInferredForCaptureSession(captureSessionId)

    suspend fun deleteTimelineForRegroup(captureSessionId: Long) {
        dao.deleteTimelineSessionsForCapture(captureSessionId)
        dao.clearTimelineIdsForCaptureSession(captureSessionId)
    }

    suspend fun insertTimelineSession(entity: TimelineSessionEntity): Long =
        dao.insertTimelineSession(entity)

    suspend fun updateTimelineSession(entity: TimelineSessionEntity) = dao.updateTimelineSession(entity)

    suspend fun linkInferredToTimeline(inferredId: Long, timelineId: Long?) =
        dao.linkInferredToTimeline(inferredId, timelineId)

    suspend fun getAllQueuedRawCaptures(): List<RawCaptureEventEntity> = dao.getAllQueuedRawCaptures()
    suspend fun getRecentInferred(limit: Int): List<InferredEventEntity> = dao.getRecentInferred(limit)
    suspend fun upsertInsight(entity: UserInsightEntity) = dao.upsertInsight(entity)
    suspend fun getConfirmedInsights(): List<UserInsightEntity> = dao.getConfirmedInsights()

    suspend fun mergeInsightCandidate(candidate: UserInsightEntity) {
        val existing = dao.getInsightByKey(candidate.insightKey)
        val now = System.currentTimeMillis()
        when (existing?.status) {
            "dismissed" -> return
            "confirmed" ->
                dao.updateInsightEvidence(
                    id = existing.id,
                    evidenceCount = candidate.evidenceCount,
                    confidence = candidate.confidence,
                    reasonJson = candidate.reasonJson,
                    updatedAt = now,
                )
            null -> dao.insertInsight(candidate)
            else ->
                dao.upsertInsight(
                    candidate.copy(
                        id = existing.id,
                        createdAtMillis = existing.createdAtMillis,
                    ),
                )
        }
    }

    suspend fun updateInsightStatus(
        id: Long,
        status: String,
    ) = dao.updateInsightStatus(id, status, System.currentTimeMillis())

    suspend fun deleteAllUserData() {
        imageStorage.deleteAll()
        dao.deleteAllScenes()
        dao.deleteAllInferred()
        dao.deleteAllInsights()
        dao.deleteAllTimelineSessions()
        dao.deleteAllRawCaptures()
        dao.deleteAllCaptureSessions()
        CaptureEventLog.clear()
    }

    fun newCaptureFile(captureSessionId: Long) = imageStorage.newImageFile(captureSessionId)

    val settingsFlow: Flow<AmbientSettings> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { p ->
                AmbientSettings(
                    captureEnabled = p[AppPreferenceKeys.captureEnabled] ?: AppPreferenceDefaults.CAPTURE_ENABLED,
                    captureIntervalSeconds =
                        p[AppPreferenceKeys.captureIntervalSeconds]
                            ?: AppPreferenceDefaults.CAPTURE_INTERVAL_SECONDS,
                    onlyDuringActiveSessions =
                        p[AppPreferenceKeys.onlyDuringActiveSessions] ?: AppPreferenceDefaults.ONLY_ACTIVE,
                    wifiOnlyProcessing = p[AppPreferenceKeys.wifiOnlyProcessing] ?: AppPreferenceDefaults.WIFI_ONLY,
                    jpegQuality = p[AppPreferenceKeys.jpegQuality] ?: AppPreferenceDefaults.JPEG_QUALITY,
                    ruleEngineEnabled = p[AppPreferenceKeys.ruleEngineEnabled] ?: AppPreferenceDefaults.RULE_ENGINE,
                    onDeviceLlmEnabled = p[AppPreferenceKeys.onDeviceLlmEnabled] ?: AppPreferenceDefaults.ON_DEVICE_LLM,
                    perceptronCaptioningEnabled =
                        p[AppPreferenceKeys.perceptronCaptioningEnabled] ?: AppPreferenceDefaults.PERCEPTRON_CAPTIONING,
                    llmUnavailableFallbackRules =
                        p[AppPreferenceKeys.llmUnavailableFallbackRules]
                            ?: AppPreferenceDefaults.LLM_FALLBACK_RULES,
                    llmConfidenceThreshold =
                        p[AppPreferenceKeys.llmConfidenceThreshold]
                            ?: AppPreferenceDefaults.LLM_CONFIDENCE_THRESHOLD,
                    sessionGapMinutes =
                        p[AppPreferenceKeys.sessionGapMinutes] ?: AppPreferenceDefaults.SESSION_GAP_MINUTES,
                    dedupeHashThreshold =
                        p[AppPreferenceKeys.dedupeHashThreshold] ?: AppPreferenceDefaults.DEDUPE_HASH_THRESHOLD,
                    dedupeTimeWindowSeconds =
                        p[AppPreferenceKeys.dedupeTimeWindowSeconds]
                            ?: AppPreferenceDefaults.DEDUPE_TIME_WINDOW_SEC,
                    maxEventsPerSessionDisplay =
                        p[AppPreferenceKeys.maxEventsPerSessionDisplay]
                            ?: AppPreferenceDefaults.MAX_EVENTS_PER_SESSION,
                    localOnlyStorage = p[AppPreferenceKeys.localOnlyStorage] ?: AppPreferenceDefaults.LOCAL_ONLY,
                    blurSensitiveInUi = p[AppPreferenceKeys.blurSensitiveInUi] ?: AppPreferenceDefaults.BLUR_SENSITIVE,
                    insightPriorsEnabled =
                        p[AppPreferenceKeys.insightPriorsEnabled] ?: AppPreferenceDefaults.INSIGHT_PRIORS,
                )
            }

    suspend fun readSettings(): AmbientSettings = settingsFlow.first()

    suspend fun updateLastActivityState(state: String) {
        dataStore.edit { prefs ->
            prefs[AppPreferenceKeys.lastActivityState] = state
            prefs[AppPreferenceKeys.lastActivityUpdatedMillis] = System.currentTimeMillis()
        }
    }

    suspend fun getLastActivityState(): String =
        dataStore.data.first()[AppPreferenceKeys.lastActivityState]
            ?: AppPreferenceDefaults.UNKNOWN_ACTIVITY

    suspend fun writeSettings(transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { transform(it) }
    }
}

data class AmbientSettings(
    val captureEnabled: Boolean,
    val captureIntervalSeconds: Long,
    val onlyDuringActiveSessions: Boolean,
    val wifiOnlyProcessing: Boolean,
    val jpegQuality: Int,
    val ruleEngineEnabled: Boolean,
    val onDeviceLlmEnabled: Boolean,
    val perceptronCaptioningEnabled: Boolean,
    val llmUnavailableFallbackRules: Boolean,
    val llmConfidenceThreshold: Float,
    val sessionGapMinutes: Long,
    val dedupeHashThreshold: Int,
    val dedupeTimeWindowSeconds: Long,
    val maxEventsPerSessionDisplay: Int,
    val localOnlyStorage: Boolean,
    val blurSensitiveInUi: Boolean,
    val insightPriorsEnabled: Boolean,
)
