package com.ambientmemory.timeline

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.activity.ComponentActivity
import com.ambientmemory.timeline.service.MemoryCaptureService
import com.ambientmemory.timeline.ui.insights.InsightsRoute
import com.ambientmemory.timeline.ui.main.TimelineRoute
import com.ambientmemory.timeline.ui.main.TimelineViewModel
import com.ambientmemory.timeline.ui.settings.SettingsRoute
import com.ambientmemory.timeline.ui.theme.AmbientMemoryTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val ACTIVITY_RECOGNITION_PERMISSION = "android.permission.ACTIVITY_RECOGNITION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AmbientMemoryApp
        setContent {
            AmbientMemoryTheme {
                val navController = rememberNavController()
                val vm: TimelineViewModel =
                    viewModel(factory = TimelineViewModel.factory(app.graph.repository))

                var canRun by remember { mutableStateOf(false) }
                val perms =
                    buildList {
                        add(Manifest.permission.CAMERA)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            add(ACTIVITY_RECOGNITION_PERMISSION)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()

                val launcher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) { result ->
                        canRun = result.values.all { it }
                    }

                LaunchedEffect(Unit) {
                    val granted =
                        perms.all { p ->
                            ContextCompat.checkSelfPermission(this@MainActivity, p) ==
                                PackageManager.PERMISSION_GRANTED
                        }
                    canRun = granted
                    if (!granted) {
                        launcher.launch(perms)
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "timeline",
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable("timeline") {
                        TimelineRoute(
                            viewModel = vm,
                            onSettings = { navController.navigate("settings") },
                            onInsights = { navController.navigate("insights") },
                            onStartSession = {
                                val hasAllPermissions =
                                    perms.all { p ->
                                        ContextCompat.checkSelfPermission(this@MainActivity, p) ==
                                            PackageManager.PERMISSION_GRANTED
                                    }
                                if (!hasAllPermissions) {
                                    launcher.launch(perms)
                                    return@TimelineRoute
                                }
                                val i =
                                    Intent(this@MainActivity, MemoryCaptureService::class.java).apply {
                                        action = MemoryCaptureService.ACTION_START
                                    }
                                try {
                                    ContextCompat.startForegroundService(this@MainActivity, i)
                                } catch (_: SecurityException) {
                                    canRun = false
                                    launcher.launch(perms)
                                }
                            },
                            onStopSession = {
                                val i =
                                    Intent(this@MainActivity, MemoryCaptureService::class.java).apply {
                                        action = MemoryCaptureService.ACTION_STOP
                                    }
                                startService(i)
                            },
                            canRun = canRun,
                        )
                    }
                    composable("settings") {
                        SettingsRoute(
                            app = app,
                            onBack = { navController.popBackStack() },
                            onInsights = { navController.navigate("insights") },
                        )
                    }
                    composable("insights") {
                        InsightsRoute(
                            app = app,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
