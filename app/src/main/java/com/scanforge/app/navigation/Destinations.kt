package com.scanforge.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.scanforge.app.R
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes. Each destination is a `@Serializable` type consumed by the
 * Navigation-Compose `composable<T>` / `toRoute<T>` APIs — no string route parsing.
 */
sealed interface ScanForgeRoute {
    @Serializable
    data object Home : ScanForgeRoute

    @Serializable
    data object Scan : ScanForgeRoute

    @Serializable
    data class DocumentDetail(val documentId: Long) : ScanForgeRoute

    /** Non-destructive page editor: warp/crop, enhancement filters, and adjustments. */
    @Serializable
    data class PageEditor(val pageId: Long) : ScanForgeRoute

    /** OCR results for a page: image/text views, edit/correction, confidence heatmap, re-run. */
    @Serializable
    data class OcrResult(val pageId: Long) : ScanForgeRoute

    /** In-app preview of an exported PDF (rendered with PdfRenderer) before sharing. */
    @Serializable
    data class PdfPreview(val filePath: String) : ScanForgeRoute

    /** Full-text search across titles, OCR text and tags. */
    @Serializable
    data object Search : ScanForgeRoute

    /** Recycle bin: restore or permanently delete soft-deleted documents. */
    @Serializable
    data object Trash : ScanForgeRoute

    @Serializable
    data object Settings : ScanForgeRoute
}

/** Destinations that appear in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: ScanForgeRoute,
    val icon: ImageVector,
    val labelRes: Int,
) {
    Home(ScanForgeRoute.Home, Icons.Outlined.Description, R.string.dest_home),
    Scan(ScanForgeRoute.Scan, Icons.Filled.DocumentScanner, R.string.dest_scan),
    Settings(ScanForgeRoute.Settings, Icons.Outlined.Settings, R.string.dest_settings),
}
