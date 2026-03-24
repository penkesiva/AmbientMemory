package com.ambientmemory.timeline.ui.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientmemory.timeline.R
import com.ambientmemory.timeline.data.db.TimelineSessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineRoute(
    viewModel: TimelineViewModel,
    onSettings: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    canRun: Boolean,
    modifier: Modifier = Modifier,
) {
    val sessions by viewModel.timelineSessions.collectAsStateWithLifecycle()
    val active by viewModel.activeCaptureSession.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val events by viewModel.captureEvents.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timeline_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    if (!canRun) {
                        Text(
                            "Camera, activity recognition, and notifications are required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (active == null) {
                            Button(
                                onClick = onStartSession,
                                enabled = canRun,
                            ) {
                                Text(stringResource(R.string.start_session))
                            }
                        } else {
                            Button(onClick = onStopSession) {
                                Text(stringResource(R.string.stop_session))
                            }
                        }
                    }
                    active?.let {
                        Text(
                            "Active capture session · ${it.state}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (events.isNotEmpty()) {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Capture log",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                events.takeLast(6).reversed().forEach { line ->
                                    Text(line, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            if (sessions.isEmpty()) {
                item {
                    Text(
                        "Start a session to build your memory timeline.",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    CollapsibleSessionCard(session, viewModel, settings.maxEventsPerSessionDisplay)
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSessionCard(
    session: TimelineSessionEntity,
    viewModel: TimelineViewModel,
    maxEvents: Int,
) {
    var expanded by remember(session.id) { mutableStateOf(false) }
    val events by viewModel.eventsForTimeline(session.id).collectAsStateWithLifecycle(emptyList())
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatRange(session, timeFmt, dateFmt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.events_label, session.eventCount),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (session.status == "in_progress") {
                            Text(
                                stringResource(R.string.in_progress),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        } else if (session.status == "paused") {
                            Text(
                                "Paused",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            Text(
                session.summary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.take(maxEvents).forEach { ev ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                val whenText =
                                    buildString {
                                        append(dateFmt.format(Date(ev.startTimeMillis)))
                                        append(" · ")
                                        append(timeFmt.format(Date(ev.startTimeMillis)))
                                    }
                                Text(
                                    timeFmt.format(Date(ev.startTimeMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Activity: ${ev.activity.replaceFirstChar { it.uppercase() }}",
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "Where: ${ev.whereLabel.ifBlank { "unknown" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    "What: ${ev.whatSummary.ifBlank { "No scene description" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "When: $whenText",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "Why: ${ev.whySummary?.takeIf { it.isNotBlank() } ?: "not inferred"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "How: ${ev.howSummary.ifBlank { "not inferred" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            val pct = (ev.confidence * 100).toInt().coerceIn(0, 100)
                            Text(
                                stringResource(R.string.confidence_label, pct),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { ev.confidence },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatRange(
    session: TimelineSessionEntity,
    timeFmt: SimpleDateFormat,
    dateFmt: SimpleDateFormat,
): String {
    val start = Date(session.startTimeMillis)
    val end = session.endTimeMillis?.let { Date(it) }
    val startStr = "${dateFmt.format(start)} · ${timeFmt.format(start)}"
    val endStr =
        if (end != null) {
            timeFmt.format(end)
        } else {
            "…"
        }
    return "$startStr – $endStr"
}
