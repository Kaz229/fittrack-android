package com.kaz229.fittrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import com.kaz229.fittrack.ui.FitTrackApp
import com.kaz229.fittrack.ui.FitTrackViewModel
import com.kaz229.fittrack.ui.theme.FitTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Le thème est un réglage de l'utilisateur : le ViewModel est créé ici pour
            // que l'application entière soit recomposée quand il change.
            val viewModel: FitTrackViewModel = viewModel()
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            FitTrackTheme(choice = theme) {
                FitTrackApp(viewModel)
            }
        }
    }
}
