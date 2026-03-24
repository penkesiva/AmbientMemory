package com.ambientmemory.timeline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientmemory.timeline.AmbientMemoryApp
import com.ambientmemory.timeline.R
import com.ambientmemory.timeline.data.prefs.AppPreferenceDefaults
import com.ambientmemory.timeline.data.prefs.AppPreferenceKeys
import com.ambientmemory.timeline.data.prefs.appDataStore
import com.ambientmemory.timeline.data.repo.AmbientSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    app: AmbientMemoryApp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val dataStore = app.applicationContext.appDataStore
    val settings by app.graph.repository.settingsFlow.collectAsStateWithLifecycle(
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
            ),
    )

    var intervalText by remember(settings.captureIntervalSeconds) {
        mutableStateOf(settings.captureIntervalSeconds.toString())
    }
    var gapText by remember(settings.sessionGapMinutes) {
        mutableStateOf(settings.sessionGapMinutes.toString())
    }
    var hashThresh by remember(settings.dedupeHashThreshold) {
        mutableStateOf(settings.dedupeHashThreshold.toString())
    }
    var dedupeWindow by remember(settings.dedupeTimeWindowSeconds) {
        mutableStateOf(settings.dedupeTimeWindowSeconds.toString())
    }
    var maxEvents by remember(settings.maxEventsPerSessionDisplay) {
        mutableStateOf(settings.maxEventsPerSessionDisplay.toString())
    }
    var llmThresh by remember(settings.llmConfidenceThreshold) {
        mutableStateOf(settings.llmConfidenceThreshold.toString())
    }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Capture", style = MaterialTheme.typography.titleMedium)
            SettingsSwitchRow(
                title = "Enable memory capture",
                checked = settings.captureEnabled,
                onChecked = { v -> scope.launch { dataStore.edit { it[AppPreferenceKeys.captureEnabled] = v } } },
            )
            OutlinedTextField(
                value = intervalText,
                onValueChange = { intervalText = it },
                label = { Text("Capture interval (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            SettingsSwitchRow(
                title = "Only during active sessions",
                checked = settings.onlyDuringActiveSessions,
                onChecked = { v -> scope.launch { dataStore.edit { it[AppPreferenceKeys.onlyDuringActiveSessions] = v } } },
            )
            SettingsSwitchRow(
                title = "Wi‑Fi only processing",
                checked = settings.wifiOnlyProcessing,
                onChecked = { v -> scope.launch { dataStore.edit { it[AppPreferenceKeys.wifiOnlyProcessing] = v } } },
            )
            OutlinedTextField(
                value = settings.jpegQuality.toString(),
                onValueChange = { t ->
                    t.toIntOrNull()?.let { q ->
                        scope.launch {
                            dataStore.edit { prefs ->
                                prefs[AppPreferenceKeys.jpegQuality] = q.coerceIn(50, 100)
                            }
                        }
                    }
                },
                label = { Text("JPEG quality (50–100)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Inference", style = MaterialTheme.typography.titleMedium)
            SettingsSwitchRow(
                title = "Enable rule engine",
                checked = settings.ruleEngineEnabled,
                onChecked = { v -> scope.launch { dataStore.edit { it[AppPreferenceKeys.ruleEngineEnabled] = v } } },
            )
            SettingsSwitchRow(
                title = "On-device LLM (when supported)",
                checked = settings.onDeviceLlmEnabled,
                onChecked = { v -> scope.launch { dataStore.edit { it[AppPreferenceKeys.onDeviceLlmEnabled] = v } } },
            )
            SettingsSwitchRow(
                title = "Perceptron cloud captioning",
                checked = settings.perceptronCaptioningEnabled,
                onChecked = { v ->
                    scope.launch {
                        dataStore.edit { it[AppPreferenceKeys.perceptronCaptioningEnabled] = v }
                    }
                },
            )
            SettingsSwitchRow(
                title = "Fallback to rules if LLM unavailable",
                checked = settings.llmUnavailableFallbackRules,
                onChecked = { v ->
                    scope.launch {
                        dataStore.edit { it[AppPreferenceKeys.llmUnavailableFallbackRules] = v }
                    }
                },
            )
            OutlinedTextField(
                value = llmThresh,
                onValueChange = { llmThresh = it },
                label = { Text("LLM fallback confidence threshold") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Sessions & dedupe", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = gapText,
                onValueChange = { gapText = it },
                label = { Text("Session gap (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = hashThresh,
                onValueChange = { hashThresh = it },
                label = { Text("Dedupe hash threshold (lower = stricter)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = dedupeWindow,
                onValueChange = { dedupeWindow = it },
                label = { Text("Dedupe time window (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = maxEvents,
                onValueChange = { maxEvents = it },
                label = { Text("Max events shown per session (UI)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            SettingsSwitchRow(
                title = "Local-only storage mode",
                checked = settings.localOnlyStorage,
                onChecked = { v -> scope.launch { dataStore.edit { it[AppPreferenceKeys.localOnlyStorage] = v } } },
            )
            SettingsSwitchRow(
                title = "Blur sensitive cues in UI",
                checked = settings.blurSensitiveInUi,
                onChecked = { v -> scope.launch { dataStore.edit { it[AppPreferenceKeys.blurSensitiveInUi] = v } } },
            )

            Button(
                onClick = {
                    scope.launch {
                        dataStore.edit { prefs ->
                            intervalText.toLongOrNull()?.let { prefs[AppPreferenceKeys.captureIntervalSeconds] = it.coerceAtLeast(10L) }
                            gapText.toLongOrNull()?.let { prefs[AppPreferenceKeys.sessionGapMinutes] = it.coerceAtLeast(1L) }
                            hashThresh.toIntOrNull()?.let { prefs[AppPreferenceKeys.dedupeHashThreshold] = it.coerceIn(0, 64) }
                            dedupeWindow.toLongOrNull()?.let { prefs[AppPreferenceKeys.dedupeTimeWindowSeconds] = it.coerceAtLeast(5L) }
                            maxEvents.toIntOrNull()?.let { prefs[AppPreferenceKeys.maxEventsPerSessionDisplay] = it.coerceIn(5, 500) }
                            llmThresh.toFloatOrNull()?.let { prefs[AppPreferenceKeys.llmConfidenceThreshold] = it.coerceIn(0f, 1f) }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply numeric settings")
            }

            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.clear_database))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.clear_database)) },
            text = { Text(stringResource(R.string.clear_database_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch { app.graph.repository.deleteAllUserData() }
                    },
                ) {
                    Text(stringResource(R.string.clear_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
