package com.example.agriscout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agriscout.ui.navigation.AgriScoutApp
import com.example.agriscout.ui.theme.AgriScoutTheme
import com.example.agriscout.ui.viewmodel.AgriScoutViewModel
import com.example.agriscout.ui.viewmodel.AgriScoutViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as AgriScoutApplication).appContainer
        setContent {
            AgriScoutTheme {
                val viewModel: AgriScoutViewModel = viewModel(
                    factory = AgriScoutViewModelFactory(appContainer, applicationContext)
                )
                AgriScoutApp(viewModel)
            }
        }
    }
}