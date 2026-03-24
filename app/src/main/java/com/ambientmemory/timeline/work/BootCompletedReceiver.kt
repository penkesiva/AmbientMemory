package com.ambientmemory.timeline.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ambientmemory.timeline.AmbientMemoryApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val app = context.applicationContext as? AmbientMemoryApp
        if (app == null) {
            pendingResult.finish()
            return
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = app.graph.repository.readSettings()
                val queued = app.graph.repository.getAllQueuedRawCaptures()
                for (raw in queued) {
                    CaptureProcessing.enqueue(
                        context.applicationContext,
                        raw.id,
                        settings.wifiOnlyProcessing,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
