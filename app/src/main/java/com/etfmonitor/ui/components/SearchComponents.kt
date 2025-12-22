package com.etfmonitor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.database.entities.SearchHistory
import com.etfmonitor.database.entities.Stock
import com.etfmonitor.ui.theme.extendedShapes

/**
 * Search Components - Unified search UI templates
 *
 * This file contains reusable search-related composables:
 * - SearchTextField: Standard search input with optional history/clear buttons
 * - SearchAutocompleteDropdown: Suggestion dropdown for stock search
 * - SearchHistoryDialog: Dialog showing recent search history
 */

/**
 * Standard search text field with consistent styling across the app.
 *
 * @param value Current search query value
 * @param onValueChange Callback when value changes
 * @param modifier Modifier for the text field
 * @param placeholder Placeholder text (defaults to search hint)
 * @param showLeadingIcon Whether to show search icon on the left
 * @param showHistoryButton Whether to show history button when field is empty
 * @param hasHistory Whether there is search history available
 * @param onHistoryClick Callback when history button is clicked
 * @param onClear Callback when clear button is clicked
 * @param onSearchDone Callback when search keyboard action is triggered
 */
@Composable
fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_hint),
    showLeadingIcon: Boolean = true,
    showHistoryButton: Boolean = true,
    hasHistory: Boolean = false,
    onHistoryClick: () -> Unit = {},
    onClear: () -> Unit = {},
    onSearchDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = if (showLeadingIcon) {
            {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // History button - show when empty and has history
                if (showHistoryButton && hasHistory && value.isEmpty()) {
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource(R.string.search_history),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Clear button - show when has text
                if (value.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.extendedShapes.searchBar,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchDone() })
    )
}

/**
 * Compact search text field variant without border (for header integration).
 * Uses transparent border and rounded corners.
 */
@Composable
fun SearchTextFieldCompact(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_hint),
    showLeadingIcon: Boolean = true,
    onClear: () -> Unit = {},
    onSearchDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = if (showLeadingIcon) {
            {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.action_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null,
        singleLine = true,
        shape = MaterialTheme.extendedShapes.searchBar,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchDone() })
    )
}

/**
 * Autocomplete dropdown for stock search suggestions.
 * Displays a list of matching stocks below the search field.
 *
 * @param suggestions List of stock suggestions to display
 * @param onSuggestionSelected Callback when a suggestion is selected (receives ticker)
 * @param modifier Modifier for the dropdown card
 * @param topPadding Top padding to position dropdown below search field
 */
@Composable
fun SearchAutocompleteDropdown(
    suggestions: List<Stock>,
    onSuggestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Int = 72
) {
    if (suggestions.isNotEmpty()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = topPadding.dp)
                .heightIn(max = 300.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(suggestions) { stock ->
                    ListItem(
                        headlineContent = { Text(stock.name) },
                        supportingContent = {
                            Text(
                                "${stock.ticker} • ${stock.market}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        modifier = Modifier.clickable {
                            onSuggestionSelected(stock.ticker)
                        }
                    )
                    if (stock != suggestions.last()) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * Dialog displaying recent search history.
 * Shows a list of previously searched stocks with tap-to-select.
 *
 * @param searchHistory List of search history entries
 * @param onDismiss Callback when dialog is dismissed
 * @param onSelectStock Callback when a stock is selected (receives ticker)
 */
@Composable
fun SearchHistoryDialog(
    searchHistory: List<SearchHistory>,
    onDismiss: () -> Unit,
    onSelectStock: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.recent_search))
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (searchHistory.isEmpty()) {
                    Text(
                        stringResource(R.string.search_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchHistory) { history ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(history.name) },
                                    supportingContent = {
                                        Text(
                                            "${history.ticker} • ${history.market}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        onSelectStock(history.ticker)
                                    }
                                )
                                if (history != searchHistory.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
