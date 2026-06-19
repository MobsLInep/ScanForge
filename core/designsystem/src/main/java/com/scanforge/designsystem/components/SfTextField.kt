package com.scanforge.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.scanforge.designsystem.theme.ScanForgeTheme

/**
 * Brand-styled single/multi-line text field with label, supporting/error text, and optional
 * leading/trailing icons. Focused outline uses the amber primary; error state uses the brand red.
 */
@Composable
fun SfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconDescription: String? = null,
    trailingIcon: ImageVector? = null,
    trailingIconDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        shape = ScanForgeTheme.shapesExt.textField,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = leadingIconDescription) }
        },
        trailingIcon = trailingIcon?.let { icon ->
            {
                if (onTrailingClick != null) {
                    IconButton(onClick = onTrailingClick) {
                        Icon(icon, contentDescription = trailingIconDescription)
                    }
                } else {
                    Icon(icon, contentDescription = trailingIconDescription)
                }
            }
        },
        supportingText = when {
            isError && errorText != null -> {
                { Text(errorText, color = MaterialTheme.colorScheme.error) }
            }
            supportingText != null -> {
                { Text(supportingText) }
            }
            else -> null
        },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
            errorBorderColor = MaterialTheme.colorScheme.error,
        ),
    )
}

@Preview(name = "TextField · Dark")
@Composable
private fun SfTextFieldDarkPreview() = SfPreviewSurface(dark = true) {
    SfTextField(
        value = "Q2 Tax Receipts",
        onValueChange = {},
        label = "Document name",
        supportingText = "Used for the PDF file name",
    )
}

@Preview(name = "TextField · Error · Light")
@Composable
private fun SfTextFieldErrorPreview() = SfPreviewSurface(dark = false) {
    SfTextField(
        value = "",
        onValueChange = {},
        label = "Document name",
        isError = true,
        errorText = "Name can't be empty",
    )
}
