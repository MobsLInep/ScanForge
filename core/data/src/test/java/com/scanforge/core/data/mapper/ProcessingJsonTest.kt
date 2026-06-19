package com.scanforge.core.data.mapper

import com.scanforge.core.domain.imaging.EnhancementFilter
import com.scanforge.core.domain.imaging.EnhancementSettings
import com.scanforge.core.domain.imaging.PageProcessing
import com.scanforge.core.domain.scanning.DetectedQuad
import com.scanforge.core.domain.scanning.NormalizedPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProcessingJsonTest {

    @Test
    fun `round-trips a full recipe including crop quad`() {
        val recipe = PageProcessing(
            cropQuad = DetectedQuad(
                topLeft = NormalizedPoint(0.05f, 0.06f),
                topRight = NormalizedPoint(0.95f, 0.07f),
                bottomRight = NormalizedPoint(0.94f, 0.93f),
                bottomLeft = NormalizedPoint(0.06f, 0.92f),
            ),
            enhancement = EnhancementSettings(
                filter = EnhancementFilter.MagicColor,
                brightness = 0.2f,
                contrast = -0.1f,
                sharpness = 0.5f,
                rotationDegrees = 90,
                deskew = false,
                removeShadows = true,
                denoise = true,
            ),
        )
        val decoded = ProcessingJson.decode(ProcessingJson.encode(recipe))
        assertEquals(recipe, decoded)
    }

    @Test
    fun `round-trips a recipe with no crop`() {
        val recipe = PageProcessing(cropQuad = null, enhancement = EnhancementSettings())
        assertEquals(recipe, ProcessingJson.decode(ProcessingJson.encode(recipe)))
    }

    @Test
    fun `null and malformed json decode to null`() {
        assertNull(ProcessingJson.decode(null))
        assertNull(ProcessingJson.decode("not json"))
    }
}
