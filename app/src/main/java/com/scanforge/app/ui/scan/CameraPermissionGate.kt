package com.scanforge.app.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.scanforge.app.R
import com.scanforge.designsystem.components.SfButton
import com.scanforge.designsystem.components.SfButtonVariant

private enum class CamPermState { Rationale, Denied, Granted }

private fun Context.hasCamera(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Gates [content] behind the CAMERA runtime permission with a polished, privacy-forward rationale
 * screen (shown *before* the system dialog) and a denied state that deep-links to app settings and
 * still offers the import path. Re-checks on resume so returning from Settings updates the UI.
 */
@Composable
fun CameraPermissionGate(
    onImportInstead: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var state by remember {
        mutableStateOf(if (context.hasCamera()) CamPermState.Granted else CamPermState.Rationale)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state = if (granted) CamPermState.Granted else CamPermState.Denied
    }

    // Re-evaluate when the user comes back from system Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && context.hasCamera()) state = CamPermState.Granted
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (state) {
        CamPermState.Granted -> content()
        CamPermState.Rationale -> PermissionPrompt(
            modifier = modifier,
            title = stringResource(R.string.perm_rationale_title),
            body = stringResource(R.string.perm_rationale_body),
            primaryLabel = stringResource(R.string.perm_rationale_grant),
            onPrimary = { launcher.launch(Manifest.permission.CAMERA) },
            onImportInstead = onImportInstead,
        )
        CamPermState.Denied -> PermissionPrompt(
            modifier = modifier,
            title = stringResource(R.string.perm_denied_title),
            body = stringResource(R.string.perm_denied_body),
            primaryLabel = stringResource(R.string.perm_open_settings),
            onPrimary = { context.openAppSettings() },
            onImportInstead = onImportInstead,
        )
    }
}

@Composable
private fun PermissionPrompt(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onImportInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
        )
        SfButton(primaryLabel, onPrimary)
        SfButton(
            stringResource(R.string.perm_import_instead),
            onImportInstead,
            variant = SfButtonVariant.Ghost,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
