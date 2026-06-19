package com.scanforge.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Single source of truth for ScanForge motion — durations, easings, and spring specs.
 *
 * Everything animated in the app should pull from here so timing stays consistent and we can
 * later route it through a "reduce motion" accessibility switch in one place. Reached via
 * `ScanForgeTheme.motion`.
 */
object ScanForgeMotion {

    /** Durations in milliseconds. */
    object Duration {
        const val Quick = 120        // micro feedback: chip toggle, ripple-adjacent
        const val Standard = 240     // most enter/exit, state changes
        const val Emphasized = 400   // hero transitions, shared elements
        const val Deliberate = 600   // large surfaces, bottom sheets
        const val ScanSweep = 1500   // capture scan-line sweep cycle
        const val Shimmer = 1200     // loading shimmer cycle
        const val Pulse = 1400       // teal processing pulse cycle
    }

    /** Easings. */
    val Standard: Easing = FastOutSlowInEasing
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Linear: Easing = LinearEasing

    // ── Tween factories (generic so they work for Dp, Color, Float, Offset, …) ────────────────
    fun <T> tweenQuick(): TweenSpec<T> = tween(Duration.Quick, easing = Standard)
    fun <T> tweenStandard(): TweenSpec<T> = tween(Duration.Standard, easing = Standard)
    fun <T> tweenEmphasized(): TweenSpec<T> = tween(Duration.Emphasized, easing = Emphasized)
    fun <T> tweenDeliberate(): TweenSpec<T> = tween(Duration.Deliberate, easing = EmphasizedDecelerate)

    // ── Spring factories ──────────────────────────────────────────────────────────────────────
    /** Bouncy spring for "page added" card insertion. */
    fun <T> cardInsertionSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Subtle, controlled spring for size/position nudges. */
    fun <T> gentleSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Snappy, no-overshoot spring for press / toggle feedback. */
    fun <T> snappySpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )
}
