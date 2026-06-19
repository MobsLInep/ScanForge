package com.scanforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.scanforge.app.ui.ScanForgeRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12 splash: install before super.onCreate so the system shows the forge mark, then
        // hands off to Theme.ScanForge (postSplashScreenTheme) for the Compose UI.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme (dark/accent) and the onboarding gate are driven by saved settings.
            ScanForgeRoot()
        }
    }
}
