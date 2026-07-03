package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val builtConfigJson = checkIfBuiltApp()

        if (builtConfigJson != null) {
            // Standalone Player Mode: directly boot into the built user app
            setContent {
                MyApplicationTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        DynamicLayoutRenderer(configJson = builtConfigJson)
                    }
                }
            }
        } else {
            // IDE Editor/Compiler Mode
            val db = AppDatabase.getDatabase(applicationContext)
            val repository = ProjectRepository(db.projectDao())
            val viewModel = ViewModelProvider(this, ProjectViewModelFactory(repository))[ProjectViewModel::class.java]

            setContent {
                MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        var currentScreen by remember { mutableStateOf("dashboard") }

                        if (currentScreen == "dashboard") {
                            DashboardScreen(
                                viewModel = viewModel,
                                onProjectSelected = { currentScreen = "workspace" }
                            )
                        } else {
                            WorkspaceScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = "dashboard" }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkIfBuiltApp(): String? {
        return try {
            assets.open("is_built_app.txt").use { stream ->
                val text = stream.bufferedReader().readText().trim()
                if (text == "true") {
                    assets.open("config.json").use { configStream ->
                        configStream.bufferedReader().readText()
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
