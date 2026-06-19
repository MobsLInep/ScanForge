package com.scanforge.core.domain.scanning

import kotlinx.serialization.Serializable

/**
 * A point expressed in normalized image coordinates: `x` and `y` are each in `0f..1f`, relative to
 * the source image's width and height. Normalizing keeps the domain free of any pixel/Android
 * dependency and lets the same quad map onto a downsized analysis frame, the full-res capture, or
 * an on-screen preview of any size.
 */
@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

/**
 * Four corners of a detected page, always in clockwise order starting top-left. Produced by an
 * [EdgeDetector] and consumed by the perspective-crop step. Corner ordering is part of the
 * contract so downstream code can warp without re-sorting.
 */
@Serializable
data class DetectedQuad(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint,
) {
    val corners: List<NormalizedPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        /** The whole frame — the manual-capture fallback when no document edge is found. */
        val FULL_FRAME = DetectedQuad(
            topLeft = NormalizedPoint(0f, 0f),
            topRight = NormalizedPoint(1f, 0f),
            bottomRight = NormalizedPoint(1f, 1f),
            bottomLeft = NormalizedPoint(0f, 1f),
        )
    }
}
