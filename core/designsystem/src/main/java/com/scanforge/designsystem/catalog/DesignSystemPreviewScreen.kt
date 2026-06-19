package com.scanforge.designsystem.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanforge.designsystem.components.SfBottomSheet
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfButtonVariant
import com.scanforge.designsystem.components.SfCard
import com.scanforge.designsystem.components.SfChip
import com.scanforge.designsystem.components.SfEmptyState
import com.scanforge.designsystem.components.SfFab
import com.scanforge.designsystem.components.SfLoadingShimmer
import com.scanforge.designsystem.components.SfProcessingTile
import com.scanforge.designsystem.components.SfSegmentedControl
import com.scanforge.designsystem.components.SfTag
import com.scanforge.designsystem.components.SfTextField
import com.scanforge.designsystem.components.SfTopBar
import com.scanforge.designsystem.motion.scanBeamSweep
import com.scanforge.designsystem.theme.ScanForgeTheme

/**
 * Debug-only catalog showing every ScanForge token and component. The Dark/Light segmented control
 * at the top re-themes the whole catalog so both themes can be inspected on-device. Hosted by the
 * debug-only `DesignSystemPreviewActivity` in `:app`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignSystemPreviewScreen() {
    var dark by remember { mutableStateOf(true) }

    ScanForgeTheme(darkTheme = dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                SfTopBar(
                    title = "Design System",
                    onNavigationClick = null,
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    },
                )
            },
            floatingActionButton = {
                SfFab(
                    icon = Icons.Filled.DocumentScanner,
                    contentDescription = "Scan document",
                    onClick = {},
                    text = "Scan",
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SfSegmentedControl(
                    options = listOf("Dark", "Light"),
                    selectedIndex = if (dark) 0 else 1,
                    onSelected = { dark = it == 0 },
                    modifier = Modifier.fillMaxWidth(),
                )

                Section("Color") { ColorsSection() }
                Section("Typography") { TypographySection() }
                Section("Shape") { ShapeSection() }
                Section("Buttons") { ButtonsSection() }
                Section("Text fields") { TextFieldsSection() }
                Section("Chips & tags") { ChipsSection() }
                Section("Cards") {
                    SfCard {
                        Text("Q2_Tax_Receipts.pdf", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("8 pages · 2.4 MB · OCR complete", style = ScanForgeTheme.mono.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Section("Empty state") {
                    SfEmptyState(
                        icon = Icons.Filled.DocumentScanner,
                        title = "No documents yet",
                        description = "Scan your first page to forge a crisp, searchable PDF.",
                        actionText = "Scan document",
                        onAction = {},
                    )
                }
                Section("Loading & processing") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SfLoadingShimmer()
                        Box(contentAlignment = Alignment.Center) {
                            SfProcessingTile(modifier = Modifier.size(140.dp))
                            Text("OCR…", style = ScanForgeTheme.mono.medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Section("Bottom sheet") { BottomSheetSection() }
                Section("Motion · scan beam") { ScanBeamSection() }
                Section("Motion · spring insertion") { SpringInsertionSection() }
                Section("Motion · shared element (tap)") { SharedElementDemo() }

                Box(Modifier.height(72.dp)) // breathing room above the FAB
            }
        }
    }
}

// ── Section scaffold ──────────────────────────────────────────────────────────────────────────
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = ScanForgeTheme.colors.scanBeam,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(color = ScanForgeTheme.colors.hairline)
        content()
    }
}

// ── Sections ────────────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorsSection() {
    val cs = MaterialTheme.colorScheme
    val ext = ScanForgeTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch("primary", cs.primary, cs.onPrimary)
        Swatch("secondary", cs.secondary, cs.onSecondary)
        Swatch("tertiary", cs.tertiary, cs.onTertiary)
        Swatch("error", cs.error, cs.onError)
        Swatch("surface", cs.surface, cs.onSurface)
        Swatch("surfaceHigh", cs.surfaceContainerHigh, cs.onSurface)
        Swatch("success", ext.success, ext.onSuccess)
        Swatch("warning", ext.warning, ext.onWarning)
        Swatch("scanBeam", ext.scanBeam, Color.Black)
        Swatch("brand", ext.brand, Color.White)
    }
}

@Composable
private fun Swatch(name: String, color: Color, onColor: Color) {
    Box(
        modifier = Modifier
            .size(width = 92.dp, height = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = onColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp),
        )
    }
}

@Composable
private fun TypographySection() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Display Small", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Text("Headline Medium", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Title Large — Space Grotesk vibe", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text("Body Medium — Inter is the UI workhorse for readable copy.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Label Large", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("mono · 0123456789 · 2.4 MB · ¥€\$", style = ScanForgeTheme.mono.medium, color = ScanForgeTheme.colors.scanBeam)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeSection() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShapeChip("button 12", ScanForgeTheme.shapesExt.button)
        ShapeChip("card 16", ScanForgeTheme.shapesExt.card)
        ShapeChip("sheet 24", RoundedCornerShape(24.dp))
    }
}

@Composable
private fun ShapeChip(label: String, shape: androidx.compose.ui.graphics.Shape) {
    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonsSection() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SfButton("Primary", {}, variant = SfButtonVariant.Primary)
        SfButton("Secondary", {}, variant = SfButtonVariant.Secondary)
        SfButton("Ghost", {}, variant = SfButtonVariant.Ghost)
        SfButton("Loading", {}, loading = true)
        SfButton("Disabled", {}, enabled = false)
        SfButton("With icon", {}, leadingIcon = Icons.Filled.Add)
    }
}

@Composable
private fun TextFieldsSection() {
    var name by remember { mutableStateOf("Q2 Tax Receipts") }
    var bad by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SfTextField(name, { name = it }, label = "Document name", supportingText = "Used for the PDF file name", leadingIcon = Icons.Filled.DocumentScanner)
        SfTextField(bad, { bad = it }, label = "Tag", isError = true, errorText = "Tag can't be empty")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsSection() {
    var selected by remember { mutableIntStateOf(0) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Receipts", "Work", "Personal").forEachIndexed { i, label ->
            SfChip(label, selected = selected == i, onClick = { selected = i })
        }
        SfTag("Tax", color = ScanForgeTheme.colors.warning)
        SfTag("Archived", onRemove = {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetSection() {
    var show by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    SfButton("Open bottom sheet", { show = true }, variant = SfButtonVariant.Secondary)
    if (show) {
        SfBottomSheet(onDismissRequest = { show = false }, sheetState = sheetState, title = "Export options") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SfButton("Searchable PDF", { show = false }, modifier = Modifier.fillMaxWidth())
                SfButton("Plain text (.txt)", { show = false }, variant = SfButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ScanBeamSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(ScanForgeTheme.shapesExt.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .scanBeamSweep(active = true),
        contentAlignment = Alignment.Center,
    ) {
        Text("SCANNING", style = ScanForgeTheme.mono.medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpringInsertionSection() {
    var pages by remember { mutableStateOf(listOf("Page 1", "Page 2")) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SfButton("Add page", { pages = pages + "Page ${pages.size + 1}" }, variant = SfButtonVariant.Secondary, leadingIcon = Icons.Filled.Add)
        pages.forEach { page ->
            key(page) {
                val state = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = state,
                    enter = fadeIn(ScanForgeTheme.motion.cardInsertionSpring()) +
                        scaleIn(initialScale = 0.85f, animationSpec = ScanForgeTheme.motion.cardInsertionSpring()),
                ) {
                    SfCard(modifier = Modifier.fillMaxWidth()) {
                        Text(page, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Preview(name = "Design System (Dark)", heightDp = 1600)
@Composable
private fun DesignSystemPreview() {
    DesignSystemPreviewScreen()
}
