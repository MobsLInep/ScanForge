package com.scanforge.core.domain.imaging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class EnhancementSettingsTest {

    @Test
    fun `rotate clockwise advances by 90 and wraps at 360`() {
        var s = EnhancementSettings(rotationDegrees = 0)
        s = s.rotatedClockwise(); assertEquals(90, s.rotationDegrees)
        s = s.rotatedClockwise(); assertEquals(180, s.rotationDegrees)
        s = s.rotatedClockwise(); assertEquals(270, s.rotationDegrees)
        s = s.rotatedClockwise(); assertEquals(0, s.rotationDegrees)
    }

    @Test
    fun `original preset disables auto-corrections`() {
        val original = EnhancementSettings.ORIGINAL
        assertEquals(EnhancementFilter.Original, original.filter)
        assertFalse(original.deskew)
        assertFalse(original.removeShadows)
    }

    @Test
    fun `default filter is Auto with neutral adjustments`() {
        val s = EnhancementSettings()
        assertEquals(EnhancementFilter.Auto, s.filter)
        assertEquals(0f, s.brightness)
        assertEquals(0f, s.contrast)
        assertEquals(0f, s.sharpness)
    }
}
