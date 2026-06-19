package com.scanforge.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.scanforge.app.navigation.ScanForgeRoute
import com.scanforge.app.navigation.TopLevelDestination
import com.scanforge.app.ui.detail.DocumentDetailScreen
import com.scanforge.app.ui.editor.PageEditorScreen
import com.scanforge.app.ui.export.PdfPreviewScreen
import com.scanforge.app.ui.home.HomeScreen
import com.scanforge.app.ui.ocr.OcrResultScreen
import com.scanforge.app.ui.scan.ScanScreen
import com.scanforge.app.ui.settings.SettingsScreen
import com.scanforge.app.ui.search.SearchScreen
import com.scanforge.app.ui.trash.TrashScreen

@Composable
fun ScanForgeApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val isTopLevel = TopLevelDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
    }
    // The camera capture screen is immersive and owns its own chrome, so suppress the bottom bar.
    val onScanScreen = currentDestination
        ?.hierarchy?.any { it.hasRoute(ScanForgeRoute.Scan::class) } == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isTopLevel && !onScanScreen) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.hasRoute(dest.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(ScanForgeRoute.Home) {
                                        saveState = true
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ScanForgeRoute.Home,
            // Only consume the bottom inset; each screen's own SfTopBar handles the top inset.
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable<ScanForgeRoute.Home> {
                HomeScreen(
                    onDocumentClick = { id -> navController.navigate(ScanForgeRoute.DocumentDetail(id)) },
                    onScanClick = { navController.navigate(ScanForgeRoute.Scan) },
                    onSearchClick = { navController.navigate(ScanForgeRoute.Search) },
                    onTrashClick = { navController.navigate(ScanForgeRoute.Trash) },
                )
            }
            composable<ScanForgeRoute.Search> {
                SearchScreen(
                    onBack = { navController.navigateUp() },
                    onResultClick = { id -> navController.navigate(ScanForgeRoute.DocumentDetail(id)) },
                    onJumpToPage = { pageId -> navController.navigate(ScanForgeRoute.OcrResult(pageId)) },
                )
            }
            composable<ScanForgeRoute.Trash> {
                TrashScreen(onBack = { navController.navigateUp() })
            }
            composable<ScanForgeRoute.Scan> {
                ScanScreen(
                    onClose = { navController.navigateUp() },
                    onDocumentCreated = { id ->
                        navController.navigate(ScanForgeRoute.DocumentDetail(id)) {
                            popUpTo(ScanForgeRoute.Scan) { inclusive = true }
                        }
                    },
                )
            }
            composable<ScanForgeRoute.Settings> { SettingsScreen() }
            composable<ScanForgeRoute.DocumentDetail> {
                DocumentDetailScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onPageClick = { pageId -> navController.navigate(ScanForgeRoute.PageEditor(pageId)) },
                    onPageOcrClick = { pageId -> navController.navigate(ScanForgeRoute.OcrResult(pageId)) },
                    onPreviewPdf = { path -> navController.navigate(ScanForgeRoute.PdfPreview(path)) },
                    onDocumentDuplicated = { id ->
                        navController.navigate(ScanForgeRoute.DocumentDetail(id)) {
                            popUpTo(ScanForgeRoute.DocumentDetail(id)) { inclusive = true }
                        }
                    },
                )
            }
            composable<ScanForgeRoute.PdfPreview> { entry ->
                val route = entry.toRoute<ScanForgeRoute.PdfPreview>()
                PdfPreviewScreen(filePath = route.filePath, onBack = { navController.navigateUp() })
            }
            composable<ScanForgeRoute.PageEditor> {
                PageEditorScreen(onBack = { navController.navigateUp() })
            }
            composable<ScanForgeRoute.OcrResult> {
                OcrResultScreen(onBack = { navController.navigateUp() })
            }
        }
    }
}
