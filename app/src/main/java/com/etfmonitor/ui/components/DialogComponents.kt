package com.etfmonitor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.ui.theme.extendedShapes

/**
 * Dialog Components - Unified dialog templates
 *
 * This file contains reusable dialog composables:
 * - SelectionDialog: Generic radio button selection dialog
 * - ConfirmDialog: Simple confirmation dialog
 * - InfoSurface: Styled info box for dialogs
 */

/**
 * Data class for selection options used in dialogs.
 *
 * @param value The value to return when selected
 * @param label The primary display text
 * @param description Optional secondary description text
 */
data class SelectionOption<T>(
    val value: T,
    val label: String,
    val description: String = ""
)

/**
 * Generic selection dialog with radio buttons.
 * Supports any value type for options.
 *
 * @param title Dialog title
 * @param options List of selection options
 * @param defaultValue The initially selected value
 * @param onDismiss Callback when dialog is dismissed
 * @param onConfirm Callback when selection is confirmed (receives selected value)
 * @param description Optional description text shown above options
 * @param infoText Optional info text shown at the bottom
 * @param confirmText Text for confirm button (defaults to "Confirm")
 * @param dismissText Text for dismiss button (defaults to "Cancel")
 */
@Composable
fun <T> SelectionDialog(
    title: String,
    options: List<SelectionOption<T>>,
    defaultValue: T,
    onDismiss: () -> Unit,
    onConfirm: (T) -> Unit,
    description: String? = null,
    infoText: String? = null,
    confirmText: String = stringResource(R.string.action_confirm),
    dismissText: String = stringResource(R.string.action_cancel)
) {
    var selectedValue by remember { mutableStateOf(defaultValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                }

                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedValue == option.value),
                                onClick = { selectedValue = option.value }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedValue == option.value),
                            onClick = { selectedValue = option.value }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (option.description.isNotEmpty()) {
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (infoText != null) {
                    Spacer(Modifier.height(8.dp))
                    InfoSurface(text = infoText)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(selectedValue) },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

/**
 * Simple confirmation dialog with title, message, and two buttons.
 *
 * @param title Dialog title
 * @param message Dialog message
 * @param onDismiss Callback when dialog is dismissed or cancelled
 * @param onConfirm Callback when confirmed
 * @param confirmText Text for confirm button (defaults to "Confirm")
 * @param dismissText Text for dismiss button (defaults to "Cancel")
 * @param isDestructive Whether the confirm action is destructive (shows in error color)
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = stringResource(R.string.action_confirm),
    dismissText: String = stringResource(R.string.action_cancel),
    isDestructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            if (isDestructive) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = MaterialTheme.extendedShapes.button
                ) {
                    Text(confirmText)
                }
            } else {
                FilledTonalButton(
                    onClick = onConfirm,
                    shape = MaterialTheme.extendedShapes.button
                ) {
                    Text(confirmText)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

/**
 * Styled info surface for displaying notes or tips in dialogs.
 * Uses secondaryContainer color scheme.
 *
 * @param text The info text to display
 * @param modifier Modifier for the surface
 */
@Composable
fun InfoSurface(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.extendedShapes.card
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * Styled highlighted surface for important info in dialogs.
 * Uses tertiaryContainer color scheme.
 *
 * @param text The info text to display
 * @param modifier Modifier for the surface
 */
@Composable
fun HighlightedInfoSurface(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.extendedShapes.card
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
