package com.scanforge.core.domain.export

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompressionPresetTest {

    @Test
    fun `smaller presets estimate smaller files for the same pages`() {
        val areas = listOf(2_000_000L, 3_000_000L)
        val edges = listOf(2000, 2500)
        val high = CompressionPreset.HighQuality.estimateBytes(areas, edges)
        val balanced = CompressionPreset.Balanced.estimateBytes(areas, edges)
        val small = CompressionPreset.Small.estimateBytes(areas, edges)
        assertTrue(high > balanced, "high=$high balanced=$balanced")
        assertTrue(balanced > small, "balanced=$balanced small=$small")
    }

    @Test
    fun `downscaling a large page caps the estimated area`() {
        // A huge 6000px-edge page should estimate less than its raw area implies, because Small caps
        // the longer edge at 1500px (area scales by the square of the ratio).
        val huge = CompressionPreset.Small.estimateBytes(listOf(36_000_000L), listOf(6000))
        val capped = CompressionPreset.Small.estimateBytes(listOf(2_250_000L), listOf(1500))
        assertTrue(huge < capped * 2, "downscaled huge=$huge should be near the 1500px estimate=$capped")
    }

    @Test
    fun `empty document is just the container overhead`() {
        assertTrue(CompressionPreset.Balanced.estimateBytes(emptyList(), emptyList()) in 1L..10_000L)
    }
}
