package com.ambientmemory.timeline.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ambientmemory.timeline.AmbientMemoryApp
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
        ) {
        if (intent == null) return
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val pendingResult = goAsync()
        val app = context.applicationContext as? AmbientMemoryApp
        if (app == null) {
            pendingResult.finish()
            return
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val events: List<ActivityTransitionEvent> = result.transitionEvents
                for (e in events) {
                    if (e.transitionType != ActivityTransition.ACTIVITY_TRANSITION_ENTER) continue
                    val label = mapActivity(e.activityType)
                    app.graph.repository.updateLastActivityState(label)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun mapActivity(type: Int): String =
        when (type) {
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.RUNNING -> "RUNNING"
            else -> "UNKNOWN"
        }
}
