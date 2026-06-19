package com.scanforge.designsystem.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Named haptic vocabulary so feedback stays consistent (and can later be muted from settings).
 *
 * `captureTick()` is the shutter "thunk"; `toggle()` is the lighter tick for segmented controls,
 * chips, and switches.
 */
class ScanForgeHaptics internal constructor(private val haptics: HapticFeedback) {
    fun captureTick() = haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    fun toggle() = haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

@Composable
fun rememberScanForgeHaptics(): ScanForgeHaptics {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { ScanForgeHaptics(haptics) }
}
