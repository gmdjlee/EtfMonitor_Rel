package com.etfmonitor.feature.settings.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.etfmonitor.R
import com.etfmonitor.core.ui.theme.*

/**
 * Settings Screen - Keyword Tab Card Components
 * Contains ThemeCard and ExclusionCard for managing ETF filter keywords
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeCard(
    themes: List<String>,
    onAddTheme: (String) -> Unit,
    onRemoveTheme: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var newTheme by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(stringResource(R.string.settings_include_theme), style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.settings_add))
                }
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_include_theme_desc),
                style = MaterialTheme.typography.bodySmall
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themes.forEach { theme ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveTheme(theme) },
                        label = { Text(theme, maxLines = 1) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
    }

    if (showDialog) {
        val keyboardController = LocalSoftwareKeyboardController.current
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.settings_add_theme)) },
            text = {
                OutlinedTextField(
                    value = newTheme,
                    onValueChange = { newTheme = it },
                    label = {
                        Text(
                            stringResource(R.string.settings_keyword),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(R.string.settings_keyword_example),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = MaterialTheme.extendedShapes.searchBar,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddTheme(newTheme)
                        newTheme = ""
                        showDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExclusionCard(
    exclusions: List<String>,
    onAddExclusion: (String) -> Unit,
    onRemoveExclusion: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var newExclusion by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(stringResource(R.string.settings_exclude_keyword), style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.settings_add))
                }
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_exclude_keyword_desc),
                style = MaterialTheme.typography.bodySmall
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                exclusions.forEach { exclusion ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveExclusion(exclusion) },
                        label = { Text(exclusion, maxLines = 1) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
            }
        }
    }

    if (showDialog) {
        val keyboardController = LocalSoftwareKeyboardController.current
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.settings_add_exclusion)) },
            text = {
                OutlinedTextField(
                    value = newExclusion,
                    onValueChange = { newExclusion = it },
                    label = {
                        Text(
                            stringResource(R.string.settings_keyword),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(R.string.settings_exclude_example),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = MaterialTheme.extendedShapes.searchBar,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddExclusion(newExclusion)
                        newExclusion = ""
                        showDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
