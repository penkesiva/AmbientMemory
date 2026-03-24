package com.ambientmemory.timeline.activity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.tasks.await

class ActivityTransitionSubscription(
    private val context: Context,
) {
    private val client = ActivityRecognition.getClient(context)

    @SuppressLint("MissingPermission")
    suspend fun register() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        val granted =
            ContextCompat.checkSelfPermission(
                context,
                ACTIVITY_RECOGNITION_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val pendingIntent = createPendingIntentOrNull() ?: return
        val transitions = buildTransitions()
        val request = ActivityTransitionRequest(transitions)
        try {
            client.requestActivityTransitionUpdates(request, pendingIntent).await()
        } catch (_: SecurityException) {
            // Permission might be revoked while app is running.
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun unregister() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val granted =
                ContextCompat.checkSelfPermission(
                    context,
                    ACTIVITY_RECOGNITION_PERMISSION,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val pendingIntent = createPendingIntentOrNull() ?: return
        try {
            client.removeActivityTransitionUpdates(pendingIntent).await()
        } catch (_: SecurityException) {
            // Permission might be revoked while app is running.
        }
    }

    private fun createPendingIntentOrNull(): PendingIntent? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val granted =
                ContextCompat.checkSelfPermission(
                    context,
                    ACTIVITY_RECOGNITION_PERMISSION,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return null
        }
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return try {
            PendingIntent.getBroadcast(context, PI_CODE_TRANSITIONS, intent, flags)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun buildTransitions(): List<ActivityTransition> {
        val types =
            listOf(
                DetectedActivity.WALKING,
                DetectedActivity.STILL,
                DetectedActivity.IN_VEHICLE,
            )
        val out = ArrayList<ActivityTransition>()
        for (type in types) {
            out.add(
                ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
            )
            out.add(
                ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
            )
        }
        return out
    }

    companion object {
        private const val ACTIVITY_RECOGNITION_PERMISSION = "android.permission.ACTIVITY_RECOGNITION"
        private const val PI_CODE_TRANSITIONS = 9101
    }
}
