package com.ambientmemory.timeline.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientmemory.timeline.AmbientMemoryApp
import com.ambientmemory.timeline.R
import com.ambientmemory.timeline.data.db.UserInsightEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsRoute(
    app: AmbientMemoryApp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val insights by app.graph.repository.insights.collectAsStateWithLifecycle(initialValue = emptyList())
    val candidates = insights.filter { it.status == "candidate" }
    val confirmed = insights.filter { it.status == "confirmed" }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Insights (beta)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Derived from repeated patterns over time. Review and correct to personalize safely.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { Text("Candidates", style = MaterialTheme.typography.titleMedium) }
            if (candidates.isEmpty()) {
                item { Text("No candidate insights yet.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(candidates, key = { it.id }) { insight ->
                    InsightCard(
                        insight = insight,
                        onConfirm = {
                            scope.launch { app.graph.repository.updateInsightStatus(insight.id, "confirmed") }
                        },
                        onDismiss = {
                            scope.launch { app.graph.repository.updateInsightStatus(insight.id, "dismissed") }
                        },
                        onDelete = {
                            scope.launch { app.graph.repository.deleteInsight(insight.id) }
                        },
                        onSaveEdit = { text ->
                            scope.launch { app.graph.repository.updateInsightText(insight.id, text) }
                        },
                    )
                }
            }

            item { Text("Confirmed", style = MaterialTheme.typography.titleMedium) }
            if (confirmed.isEmpty()) {
                item { Text("No confirmed insights yet.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(confirmed, key = { it.id }) { insight ->
                    InsightCard(
                        insight = insight,
                        onConfirm = null,
                        onDismiss = {
                            scope.launch { app.graph.repository.updateInsightStatus(insight.id, "dismissed") }
                        },
                        onDelete = {
                            scope.launch { app.graph.repository.deleteInsight(insight.id) }
                        },
                        onSaveEdit = { text ->
                            scope.launch { app.graph.repository.updateInsightText(insight.id, text) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCard(
    insight: UserInsightEntity,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSaveEdit: (String) -> Unit,
) {
    val updatedFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    var showEdit by remember(insight.id) { mutableStateOf(false) }
    var editText by remember(insight.id, insight.text) { mutableStateOf(insight.text) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(insight.category.replaceFirstChar { it.uppercase() }) },
                )
                Text("Confidence ${(insight.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Text(insight.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Evidence ${insight.evidenceCount} · Updated ${updatedFmt.format(Date(insight.updatedAtMillis))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onConfirm != null) {
                    Button(onClick = onConfirm) { Text("Confirm") }
                }
                TextButton(onClick = onDismiss) { Text("Not true") }
                TextButton(onClick = { showEdit = true }) { Text(stringResource(R.string.edit_action)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete_action)) }
            }
        }
    }
    if (showEdit) {
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text(stringResource(R.string.edit_insight_title)) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = editText.trim()
                        if (trimmed.isNotBlank()) {
                            onSaveEdit(trimmed)
                        }
                        showEdit = false
                    },
                ) {
                    Text(stringResource(R.string.save_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEdit = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }
}
