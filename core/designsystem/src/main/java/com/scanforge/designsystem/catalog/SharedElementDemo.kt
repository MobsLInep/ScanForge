package com.scanforge.designsystem.catalog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scanforge.designsystem.theme.ScanForgeTheme

/**
 * Demonstrates the grid → detail **shared-element** transition. Tapping the thumbnail morphs it
 * (and its title) into the detail view and back. This is the reusable pattern the document grid
 * will use to open a document. Experimental Compose shared-transition API.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedElementDemo(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val sheetShape = ScanForgeTheme.shapesExt.card

    SharedTransitionLayout(modifier = modifier.fillMaxWidth()) {
        val sharedScope = this
        AnimatedContent(
            targetState = expanded,
            label = "doc-shared-element",
        ) { isExpanded ->
            with(sharedScope) {
                if (!isExpanded) {
                    Surface(
                        shape = sheetShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .sharedElement(
                                rememberSharedContentState(key = "doc-tile"),
                                animatedVisibilityScope = this@AnimatedContent,
                            )
                            .size(120.dp)
                            .clickable { expanded = true },
                    ) {
                        Box(Modifier.padding(12.dp), contentAlignment = Alignment.BottomStart) {
                            Text(
                                "Invoice.pdf",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.sharedElement(
                                    rememberSharedContentState(key = "doc-title"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                ),
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = sheetShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .sharedElement(
                                rememberSharedContentState(key = "doc-tile"),
                                animatedVisibilityScope = this@AnimatedContent,
                            )
                            .fillMaxWidth()
                            .height(220.dp)
                            .clickable { expanded = false },
                    ) {
                        Box(Modifier.padding(16.dp), contentAlignment = Alignment.TopStart) {
                            Text(
                                "Invoice.pdf",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.sharedElement(
                                    rememberSharedContentState(key = "doc-title"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
