package com.kadhafi.aetherhop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kadhafi.aetherhop.core.theme.AetherHopTheme
import com.kadhafi.aetherhop.presentation.radar.MainRadarScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AetherHopTheme {
                MainRadarScreen()
            }
        }
    }
}
