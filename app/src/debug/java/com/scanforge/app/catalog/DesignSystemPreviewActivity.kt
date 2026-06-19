package com.scanforge.app.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scanforge.designsystem.catalog.DesignSystemPreviewScreen

/**
 * Debug-only entry point (separate launcher icon "ScanForge · Design System") that renders the
 * full component/token catalog. Not present in release builds — it lives in `src/debug`.
 */
class DesignSystemPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The screen manages its own Dark/Light theming via the in-catalog toggle.
        setContent { DesignSystemPreviewScreen() }
    }
}
