package com.ambientmemory.timeline

import android.app.Application
import androidx.work.Configuration
import com.ambientmemory.timeline.BuildConfig
import com.ambientmemory.timeline.capture.DedupeManager
import com.ambientmemory.timeline.data.db.AppDatabase
import com.ambientmemory.timeline.data.repo.MemoryRepository
import com.ambientmemory.timeline.inference.GenAiInferenceAdapter
import com.ambientmemory.timeline.inference.PerceptronCaptionClient
import com.ambientmemory.timeline.inference.RuleEngine
import com.ambientmemory.timeline.inference.SceneUnderstandingAdapter
import com.ambientmemory.timeline.inference.SessionGrouper

class AppGraph(
    context: Application,
) {
    val db: AppDatabase = AppDatabase.create(context)
    val repository = MemoryRepository(context, db)
    val dedupeManager = DedupeManager(repository)
    val perceptronCaptionClient =
        PerceptronCaptionClient(
            apiKey = BuildConfig.PERCEPTRON_API_KEY,
            model = BuildConfig.PERCEPTRON_MODEL,
        )
    val sceneAdapter = SceneUnderstandingAdapter(perceptronCaptionClient)
    val ruleEngine = RuleEngine()
    val genAiAdapter = GenAiInferenceAdapter()
    val sessionGrouper = SessionGrouper(repository)
}

class AmbientMemoryApp :
    Application(),
    Configuration.Provider {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()
}
