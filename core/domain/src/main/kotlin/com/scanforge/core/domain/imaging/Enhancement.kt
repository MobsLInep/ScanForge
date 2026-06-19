package com.scanforge.core.domain.imaging

import com.scanforge.core.domain.scanning.DetectedQuad
import kotlinx.serialization.Serializable

/**
 * A named document-enhancement preset. Each value maps to a distinct tone/colour treatment applied
 * by the imaging pipeline after the perspective warp. [Original] keeps the warped pixels untouched.
 */
enum class EnhancementFilter {
    /** No tonal change — the warped capture as-is. */
    Original,

    /** Adaptive threshold + local contrast: the default "scanned document" look. */
    Auto,

    /** Boosted saturation/contrast for colourful originals (receipts, magazines). */
    MagicColor,

    /** Desaturated, contrast-normalised greyscale. */
    Grayscale,

    /** Hard black-and-white (binarised) — smallest, crispest text. */
    BlackAndWhite,
}

/**
 * The full set of tunable enhancement parameters for a page. Held as a plain value object so it can
 * be (a) previewed live, (b) re-applied to the full-resolution original on export, and (c)
 * serialized into the page's stored edit recipe for non-destructive re-editing.
 *
 * Slider ranges are normalized so the UI and the pipeline agree without unit juggling:
 * [brightness]/[contrast] are `-1f..1f` (0 = unchanged), [sharpness] is `0f..1f` (0 = none).
 */
@Serializable
data class EnhancementSettings(
    val filter: EnhancementFilter = EnhancementFilter.Auto,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val sharpness: Float = 0f,
    /** Clockwise rotation applied after warp; normalized to one of 0/90/180/270. */
    val rotationDegrees: Int = 0,
    /** Straighten small residual skew from the warped page edges. */
    val deskew: Boolean = true,
    /** Flatten uneven lighting / page shadows via background division. */
    val removeShadows: Boolean = true,
    /** Suppress sensor/compression noise (slower; off by default). */
    val denoise: Boolean = false,
) {
    /** A copy with [rotationDegrees] advanced by 90° clockwise, normalized to `0..270`. */
    fun rotatedClockwise(): EnhancementSettings =
        copy(rotationDegrees = (rotationDegrees + 90).mod(360))

    companion object {
        /** Pristine pass-through: original tones, no adjustments, no auto-corrections. */
        val ORIGINAL = EnhancementSettings(
            filter = EnhancementFilter.Original,
            deskew = false,
            removeShadows = false,
        )
    }
}

/**
 * A page's complete, re-editable edit recipe: which region of the original to keep ([cropQuad]) and
 * how to enhance it ([enhancement]). Persisted alongside the processed image so any edit can be
 * reopened and changed without ever touching the archived original. A `null` [cropQuad] means "use
 * the whole image" (no perspective crop).
 */
@Serializable
data class PageProcessing(
    val cropQuad: DetectedQuad? = null,
    val enhancement: EnhancementSettings = EnhancementSettings(),
) {
    companion object {
        val DEFAULT = PageProcessing()
    }
}
