package com.scanforge.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanforge.designsystem.motion.processingPulse
import com.scanforge.designsystem.motion.scanBeamSweep
import com.scanforge.designsystem.motion.shimmer
import com.scanforge.designsystem.theme.ScanForgeTheme

/** A single shimmering placeholder block. Caller controls size; shape defaults to 8dp. */
@Composable
fun SfShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(modifier.clip(shape).shimmer())
}

/** A document-row skeleton (thumbnail + two text lines), used as the `Loading` state of a list. */
@Composable
fun SfLoadingShimmer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfShimmerBox(
            modifier = Modifier.size(56.dp),
            shape = ScanForgeTheme.shapesExt.card,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SfShimmerBox(Modifier.fillMaxWidth(0.6f).height(14.dp))
            SfShimmerBox(Modifier.fillMaxWidth(0.4f).height(12.dp))
        }
    }
}

/**
 * A page thumbnail being processed: combines a [shimmer] base with a teal [processingPulse] and
 * the amber [scanBeamSweep]. Drop a real thumbnail in via [content] and set [showShimmerBase] to
 * false; effects render on top while [active].
 */
@Composable
fun SfProcessingTile(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    showShimmerBase: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(ScanForgeTheme.shapesExt.card)
            .then(if (showShimmerBase) Modifier.shimmer() else Modifier)
            .processingPulse(active)
            .scanBeamSweep(active),
        content = content,
    )
}

@Preview(name = "Shimmer / Processing · Dark")
@Composable
private fun SfLoadingShimmerDarkPreview() = SfPreviewSurface(dark = true) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SfLoadingShimmer()
        SfProcessingTile(modifier = Modifier.size(120.dp))
    }
}
