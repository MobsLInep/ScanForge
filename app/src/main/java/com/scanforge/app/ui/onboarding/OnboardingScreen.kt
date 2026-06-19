@file:OptIn(ExperimentalLayoutApi::class)

package com.scanforge.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanforge.app.R
import com.scanforge.core.domain.ocr.OcrLanguage
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfChip
import com.scanforge.designsystem.theme.ScanForgeTheme
import com.scanforge.designsystem.theme.SpaceGrotesk
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 5

/**
 * First-launch flow: a 3-slide value carousel, a camera permission-priming page, and a language
 * picker. Completing it persists the language choice and flips the onboarding flag (see
 * [OnboardingViewModel]); the app root then swaps to the main UI.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Skip jumps to the final page rather than abandoning setup, so a language is still chosen.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AnimatedVisibility(visible = pager.currentPage < PAGE_COUNT - 1) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGE_COUNT - 1) } }) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> ValueSlide(
                        icon = Icons.Outlined.DocumentScanner,
                        title = stringResource(R.string.onboarding_1_title),
                        body = stringResource(R.string.onboarding_1_body),
                    )
                    1 -> ValueSlide(
                        icon = Icons.Outlined.TextFields,
                        title = stringResource(R.string.onboarding_2_title),
                        body = stringResource(R.string.onboarding_2_body),
                    )
                    2 -> ValueSlide(
                        icon = Icons.Outlined.IosShare,
                        title = stringResource(R.string.onboarding_3_title),
                        body = stringResource(R.string.onboarding_3_body),
                    )
                    3 -> PermissionSlide()
                    else -> LanguageSlide(
                        selected = state.selectedLanguages,
                        onToggle = viewModel::toggleLanguage,
                    )
                }
            }

            PageDots(count = PAGE_COUNT, current = pager.currentPage)
            Spacer(Modifier.height(20.dp))

            val isLast = pager.currentPage == PAGE_COUNT - 1
            SfButton(
                text = stringResource(if (isLast) R.string.onboarding_get_started else R.string.onboarding_next),
                onClick = {
                    if (isLast) viewModel.finish()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ValueSlide(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(112.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SpaceGrotesk),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionSlide() {
    var granted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(112.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.onboarding_perm_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SpaceGrotesk),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_perm_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ScanForgeTheme.colors.success)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.onboarding_perm_granted),
                    color = ScanForgeTheme.colors.success,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            SfButton(
                text = stringResource(R.string.onboarding_perm_grant),
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                leadingIcon = Icons.Outlined.PhotoCamera,
            )
        }
    }
}

@Composable
private fun LanguageSlide(selected: Set<String>, onToggle: (String) -> Unit) {
    val options = listOf(
        OcrLanguage.Latin.tag to R.string.ocr_lang_latin,
        OcrLanguage.Devanagari.tag to R.string.ocr_lang_devanagari,
        OcrLanguage.Chinese.tag to R.string.ocr_lang_chinese,
        OcrLanguage.Japanese.tag to R.string.ocr_lang_japanese,
        OcrLanguage.Korean.tag to R.string.ocr_lang_korean,
    )
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onboarding_lang_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SpaceGrotesk),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_lang_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (tag, labelRes) ->
                SfChip(
                    label = stringResource(labelRes),
                    selected = tag in selected,
                    onClick = { onToggle(tag) },
                )
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (active) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}
