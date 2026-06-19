package com.scanforge.designsystem.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanforge.designsystem.theme.ScanForgeTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for [SfButton]. Requires a connected device/emulator:
 *   ./gradlew :designsystem:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SfButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun click_invokesCallback() {
        var clicked = false
        composeRule.setContent {
            ScanForgeTheme { SfButton(text = "Scan", onClick = { clicked = true }) }
        }

        composeRule.onNodeWithText("Scan").assertHasClickAction().performClick()

        assertTrue(clicked)
    }

    @Test
    fun loading_hidesLabelAndBlocksClicks() {
        var clicked = false
        composeRule.setContent {
            ScanForgeTheme { SfButton(text = "Exporting", onClick = { clicked = true }, loading = true) }
        }

        // Label is swapped for the spinner while loading…
        composeRule.onNodeWithText("Exporting").assertDoesNotExist()
        assertFalse(clicked)
    }

    @Test
    fun disabled_isNotEnabled() {
        composeRule.setContent {
            ScanForgeTheme { SfButton(text = "Disabled", onClick = {}, enabled = false) }
        }

        composeRule.onNodeWithText("Disabled").assertIsNotEnabled()
    }
}
