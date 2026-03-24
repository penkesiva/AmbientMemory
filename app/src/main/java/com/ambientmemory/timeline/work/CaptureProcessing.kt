package com.ambientmemory.timeline.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CaptureProcessing {
    fun enqueue(
        context: Context,
        rawCaptureId: Long,
        wifiOnlyProcessing: Boolean,
    ) {
        val constraints =
            Constraints.Builder()
                .apply {
                    if (wifiOnlyProcessing) {
                        setRequiredNetworkType(NetworkType.UNMETERED)
                    } else {
                        setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    }
                }.build()
        val input: Data =
            androidx.work.workDataOf(ProcessCaptureWorker.KEY_RAW_CAPTURE_ID to rawCaptureId)
        val request =
            OneTimeWorkRequestBuilder<ProcessCaptureWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "process_capture_$rawCaptureId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
