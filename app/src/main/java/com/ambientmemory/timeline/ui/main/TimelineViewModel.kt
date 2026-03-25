package com.ambientmemory.timeline.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ambientmemory.timeline.data.db.CaptureSessionEntity
import com.ambientmemory.timeline.data.db.TimelineSessionEntity
import com.ambientmemory.timeline.data.repo.AmbientSettings
import com.ambientmemory.timeline.data.repo.MemoryRepository
import com.ambientmemory.timeline.data.prefs.AppPreferenceDefaults
import com.ambientmemory.timeline.diagnostics.CaptureEventLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TimelineViewModel(
    private val repository: MemoryRepository,
) : ViewModel() {
    val timelineSessions: StateFlow<List<TimelineSessionEntity>> =
        repository.timelineSessions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val activeCaptureSession: StateFlow<CaptureSessionEntity?> =
        repository.activeCaptureSession.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    val settings: StateFlow<AmbientSettings> =
        repository.settingsFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue =
                AmbientSettings(
                    captureEnabled = AppPreferenceDefaults.CAPTURE_ENABLED,
                    captureIntervalSeconds = AppPreferenceDefaults.CAPTURE_INTERVAL_SECONDS,
                    onlyDuringActiveSessions = AppPreferenceDefaults.ONLY_ACTIVE,
                    wifiOnlyProcessing = AppPreferenceDefaults.WIFI_ONLY,
                    jpegQuality = AppPreferenceDefaults.JPEG_QUALITY,
                    ruleEngineEnabled = AppPreferenceDefaults.RULE_ENGINE,
                    onDeviceLlmEnabled = AppPreferenceDefaults.ON_DEVICE_LLM,
                    perceptronCaptioningEnabled = AppPreferenceDefaults.PERCEPTRON_CAPTIONING,
                    llmUnavailableFallbackRules = AppPreferenceDefaults.LLM_FALLBACK_RULES,
                    llmConfidenceThreshold = AppPreferenceDefaults.LLM_CONFIDENCE_THRESHOLD,
                    sessionGapMinutes = AppPreferenceDefaults.SESSION_GAP_MINUTES,
                    dedupeHashThreshold = AppPreferenceDefaults.DEDUPE_HASH_THRESHOLD,
                    dedupeTimeWindowSeconds = AppPreferenceDefaults.DEDUPE_TIME_WINDOW_SEC,
                    maxEventsPerSessionDisplay = AppPreferenceDefaults.MAX_EVENTS_PER_SESSION,
                    localOnlyStorage = AppPreferenceDefaults.LOCAL_ONLY,
                    blurSensitiveInUi = AppPreferenceDefaults.BLUR_SENSITIVE,
                    insightPriorsEnabled = AppPreferenceDefaults.INSIGHT_PRIORS,
                ),
        )

    val captureEvents: StateFlow<List<String>> = CaptureEventLog.events

    fun eventsForTimeline(timelineId: Long) = repository.observeInferredForTimeline(timelineId)

    companion object {
        fun factory(repository: MemoryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TimelineViewModel(repository) as T
            }
    }
}
