package com.ambientmemory.timeline.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object InsightAggregation {
    fun enqueue(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<InsightAggregationWorker>()
                .setInitialDelay(20, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build(),
                ).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "aggregate_user_insights",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
