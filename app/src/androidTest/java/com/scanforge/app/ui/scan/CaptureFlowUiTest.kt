package com.scanforge.app.ui.scan

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.scanforge.core.domain.scanning.DetectedQuad
import com.scanforge.designsystem.theme.ScanForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the capture-flow surfaces that don't require a live camera: the batch
 * filmstrip (reorder/delete), the crop-review actions (confirm/retake + manual fallback), and the
 * shutter. They assert the rendered state machine the user actually touches; deeper transition
 * logic is covered by [ScanViewModelTest].
 */
class CaptureFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun page(id: Long) = CapturedPage(
        localId = id,
        originalImagePath = "/tmp/orig_$id.jpg",
        thumbnailPath = "/tmp/thumb_$id.jpg",
        quad = DetectedQuad.FULL_FRAME,
        edgesDetected = true,
    )

    @Test
    fun filmstrip_showsThumbnails_andDeleteFires() {
        var deleted: Long? = null
        composeRule.setContent {
            ScanForgeTheme(darkTheme = true) {
                BatchFilmstrip(
                    pages = listOf(page(1), page(2), page(3)),
                    onDelete = { deleted = it },
                    onMove = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithTag(TestTags.FILMSTRIP).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.pageThumb(1)).assertExists()
        composeRule.onNodeWithTag(TestTags.deletePage(2)).performClick()
        assertEquals(2L, deleted)
    }

    @Test
    fun filmstrip_moveEarlier_fires() {
        var moved: Pair<Long, Boolean>? = null
        composeRule.setContent {
            ScanForgeTheme(darkTheme = true) {
                BatchFilmstrip(
                    pages = listOf(page(1), page(2)),
                    onDelete = {},
                    onMove = { id, earlier -> moved = id to earlier },
                )
            }
        }
        // "Move page 2 earlier" is unique and enabled (page 2 is not first).
        composeRule.onNodeWithContentDescription("Move page 2 earlier").performClick()
        assertEquals(2L to true, moved)
    }

    @Test
    fun cropReview_confirm_passesQuad_and_retake_fires() {
        var confirmed: DetectedQuad? = null
        var retaken = false
        composeRule.setContent {
            ScanForgeTheme(darkTheme = true) {
                CropReviewScreen(
                    originalImagePath = "/tmp/none.jpg",
                    initialQuad = DetectedQuad.FULL_FRAME,
                    edgesDetected = true,
                    onConfirm = { confirmed = it },
                    onRetake = { retaken = true },
                )
            }
        }
        composeRule.onNodeWithTag(TestTags.CROP_CONFIRM).performClick()
        assertEquals(DetectedQuad.FULL_FRAME, confirmed)

        composeRule.onNodeWithTag(TestTags.CROP_RETAKE).performClick()
        assertTrue(retaken)
    }

    @Test
    fun cropReview_manualFallback_showsFourCornerHandles() {
        composeRule.setContent {
            ScanForgeTheme(darkTheme = true) {
                CropReviewScreen(
                    originalImagePath = "/tmp/none.jpg",
                    initialQuad = DetectedQuad.FULL_FRAME,
                    edgesDetected = false,
                    onConfirm = {},
                    onRetake = {},
                )
            }
        }
        listOf("TL", "TR", "BR", "BL").forEach {
            composeRule.onNodeWithTag("${TestTags.CROP_HANDLE_PREFIX}$it").assertExists()
        }
    }

    @Test
    fun captureButton_isClickable() {
        var clicked = false
        composeRule.setContent {
            ScanForgeTheme(darkTheme = true) {
                CaptureButton(onClick = { clicked = true }, ringColor = Color(0xFFF2A65A))
            }
        }
        composeRule.onNodeWithContentDescription("Capture page").performClick()
        assertTrue(clicked)
    }
}
