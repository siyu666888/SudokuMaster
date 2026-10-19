package com.example.sudokumaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.sudokumaster.domain.model.AppSettings
import com.example.sudokumaster.nav.AppNavHost
import com.example.sudokumaster.ui.theme.SudokuMasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as SudokuApplication
            val settings by app.container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            SudokuMasterTheme(settings = settings) {
                val navController = rememberNavController()
                AppNavHost(navController)
            }
        }
    }
}
