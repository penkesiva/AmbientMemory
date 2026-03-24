package com.ambientmemory.timeline.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "ambient_settings")

object AppPreferenceKeys {
    val captureEnabled = booleanPreferencesKey("capture_enabled")
    val captureIntervalSeconds = longPreferencesKey("capture_interval_seconds")
    val onlyDuringActiveSessions = booleanPreferencesKey("only_during_active_sessions")
    val wifiOnlyProcessing = booleanPreferencesKey("wifi_only_processing")
    /** 0-100 JPEG quality */
    val jpegQuality = intPreferencesKey("jpeg_quality")

    val ruleEngineEnabled = booleanPreferencesKey("rule_engine_enabled")
    val onDeviceLlmEnabled = booleanPreferencesKey("on_device_llm_enabled")
    val perceptronCaptioningEnabled = booleanPreferencesKey("perceptron_captioning_enabled")
    val llmUnavailableFallbackRules = booleanPreferencesKey("llm_unavailable_fallback_rules")
    val llmConfidenceThreshold = floatPreferencesKey("llm_confidence_threshold")

    val sessionGapMinutes = longPreferencesKey("session_gap_minutes")
    val dedupeHashThreshold = intPreferencesKey("dedupe_hash_threshold")
    val dedupeTimeWindowSeconds = longPreferencesKey("dedupe_time_window_seconds")
    val maxEventsPerSessionDisplay = intPreferencesKey("max_events_per_session_display")

    val localOnlyStorage = booleanPreferencesKey("local_only_storage")
    val blurSensitiveInUi = booleanPreferencesKey("blur_sensitive_in_ui")

    val lastActivityState = stringPreferencesKey("last_activity_state")
    val lastActivityUpdatedMillis = longPreferencesKey("last_activity_updated_millis")
}

object AppPreferenceDefaults {
    const val CAPTURE_ENABLED = true
    const val CAPTURE_INTERVAL_SECONDS = 60L
    const val ONLY_ACTIVE = true
    const val WIFI_ONLY = false
    const val JPEG_QUALITY = 85
    const val RULE_ENGINE = true
    const val ON_DEVICE_LLM = true
    const val PERCEPTRON_CAPTIONING = true
    const val LLM_FALLBACK_RULES = true
    const val LLM_CONFIDENCE_THRESHOLD = 0.55f
    const val SESSION_GAP_MINUTES = 25L
    const val DEDUPE_HASH_THRESHOLD = 8
    const val DEDUPE_TIME_WINDOW_SEC = 120L
    const val MAX_EVENTS_PER_SESSION = 50
    const val LOCAL_ONLY = true
    const val BLUR_SENSITIVE = true
    const val UNKNOWN_ACTIVITY = "UNKNOWN"
}
