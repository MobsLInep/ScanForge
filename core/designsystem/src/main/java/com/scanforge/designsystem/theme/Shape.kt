package com.scanforge.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * Shape scale. Buttons 12dp, cards 16dp, bottom sheets 24dp — the "machined tool" silhouette.
 * Material 3 component defaults map onto this scale (e.g. Card → medium, Button → small).
 */
val ScanForgeShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),     // buttons, chips
    medium = RoundedCornerShape(16.dp),    // cards
    large = RoundedCornerShape(24.dp),     // bottom sheets, large containers
    extraLarge = RoundedCornerShape(28.dp),
)

/** Named, intent-revealing shapes for ScanForge components. Reached via `ScanForgeTheme.shapesExt`. */
@Immutable
data class ScanForgeShapeTokens(
    val button: RoundedCornerShape = RoundedCornerShape(12.dp),
    val card: RoundedCornerShape = RoundedCornerShape(16.dp),
    val chip: RoundedCornerShape = RoundedCornerShape(10.dp),
    val textField: RoundedCornerShape = RoundedCornerShape(12.dp),
    val bottomSheet: RoundedCornerShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    val segmented: RoundedCornerShape = RoundedCornerShape(12.dp),
)

val ScanForgeShapeTokensDefault = ScanForgeShapeTokens()
