package com.scanforge.app.ui.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanforge.app.R
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfButtonVariant

/**
 * Capture-flow host. Renders the current [ScanStep] as a deterministic state machine, wraps the
 * camera in [CameraPermissionGate], owns the SAF import pickers (images + PDF — no storage
 * permission), and reports the created document id up for navigation.
 */
@Composable
fun ScanScreen(
    onClose: () -> Unit,
    onDocumentCreated: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showImportChooser by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.onImportImages(uris.map { it.toString() }) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onImportPdf(uri.toString()) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (val step = state.step) {
            is ScanStep.Camera, is ScanStep.Processing, is ScanStep.Error -> {
                CameraPermissionGate(onImportInstead = { showImportChooser = true }) {
                    CameraCaptureContent(
                        state = state,
                        onCapture = viewModel::onImageCaptured,
                        onToggleFlash = viewModel::toggleFlash,
                        onToggleGrid = viewModel::toggleGrid,
                        onToggleMode = viewModel::toggleMode,
                        onImport = { showImportChooser = true },
                        onClose = onClose,
                        onDeletePage = viewModel::onDeletePage,
                        onMovePage = viewModel::onMovePage,
                        onDoneBatch = viewModel::onDoneBatch,
                    )
                }
                if (step is ScanStep.Processing) {
                    BlockingOverlay(stringResource(R.string.scan_processing), TestTags.PROCESSING)
                }
                if (step is ScanStep.Error) {
                    ErrorDialog(
                        message = stringResource(step.messageRes),
                        onDismiss = viewModel::consumeError,
                    )
                }
            }

            is ScanStep.Review -> CropReviewScreen(
                originalImagePath = step.page.originalImagePath,
                initialQuad = step.page.quad,
                edgesDetected = step.page.edgesDetected,
                onConfirm = viewModel::onConfirmReview,
                onRetake = viewModel::onRetake,
            )

            is ScanStep.Saving -> BlockingOverlay(stringResource(R.string.scan_saving), null)

            is ScanStep.Saved -> androidx.compose.runtime.LaunchedEffect(step.documentId) {
                onDocumentCreated(step.documentId)
            }
        }
    }

    if (showImportChooser) {
        ImportChooserDialog(
            onDismiss = { showImportChooser = false },
            onPickImages = { showImportChooser = false; imagePicker.launch("image/*") },
            onPickPdf = { showImportChooser = false; pdfPicker.launch(arrayOf("application/pdf")) },
        )
    }
}

@Composable
private fun BlockingOverlay(message: String, testTag: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ImportChooserDialog(
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
    onPickPdf: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_chooser_title)) },
        text = { Text(stringResource(R.string.import_chooser_body)) },
        confirmButton = { SfButton(stringResource(R.string.import_images), onPickImages) },
        dismissButton = {
            SfButton(stringResource(R.string.import_pdf), onPickPdf, variant = SfButtonVariant.Secondary)
        },
    )
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_error_title)) },
        text = { Text(message) },
        confirmButton = { SfButton(stringResource(R.string.action_retry), onDismiss) },
    )
}
