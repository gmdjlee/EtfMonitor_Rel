package com.etfmonitor.ui.screens.settings.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etfmonitor.R

/**
 * Settings Screen - Data Period Tab Card Components
 * Contains DefaultDaysCard, SearchHistoryLimitCard, DatabaseCard
 * and their related dialogs
 *
 * Note: ETF 데이터 관리 기능은 UpdateCards.kt의 EtfDataManagementCard에서 제공
 */

@Composable
fun DefaultDaysCard(
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.settings_etf_period), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_etf_period_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.settings_current_setting),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.settings_days_format, currentDays),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.settings_action_change))
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    stringResource(R.string.settings_etf_period_recommend),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        DaysSelectionDialog(
            currentDays = currentDays,
            onDismiss = { showDialog = false },
            onConfirm = { days, reinitialize ->
                onDaysChange(days, reinitialize)
                showDialog = false
            }
        )
    }
}

@Composable
fun SearchHistoryLimitCard(
    currentLimit: Int,
    onLimitChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.settings_search_history), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_search_history_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.settings_current_setting),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.settings_count_format, currentLimit),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.settings_action_change))
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    stringResource(R.string.settings_search_history_range),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        SearchHistoryLimitDialog(
            currentLimit = currentLimit,
            onDismiss = { showDialog = false },
            onConfirm = { limit ->
                onLimitChange(limit)
                showDialog = false
            }
        )
    }
}

@Composable
fun DatabaseCard(
    onReset: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(stringResource(R.string.settings_database), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_database_desc),
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_database_reset))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text(stringResource(R.string.settings_database_reset)) },
            text = { Text(stringResource(R.string.settings_database_reset_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        onReset()
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.action_reset))
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

// ==================== Dialogs ====================

@Composable
fun DaysSelectionDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit
) {
    val options = listOf(
        DaysOption(5, stringResource(R.string.option_days_5), stringResource(R.string.option_days_5_desc)),
        DaysOption(10, stringResource(R.string.option_days_10), stringResource(R.string.option_days_10_desc)),
        DaysOption(15, stringResource(R.string.option_days_15), stringResource(R.string.option_days_15_desc)),
        DaysOption(20, stringResource(R.string.option_days_20), stringResource(R.string.option_days_20_desc)),
        DaysOption(25, stringResource(R.string.option_days_25), stringResource(R.string.option_days_25_desc)),
        DaysOption(30, stringResource(R.string.option_days_30), stringResource(R.string.option_days_30_desc)),
        DaysOption(40, stringResource(R.string.option_days_40), stringResource(R.string.option_days_40_desc)),
        DaysOption(50, stringResource(R.string.option_days_50), stringResource(R.string.option_days_50_desc))
    )

    var selectedDays by remember { mutableIntStateOf(currentDays) }
    var reinitialize by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_period_change_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 즉시 적용 옵션
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = reinitialize,
                        onCheckedChange = { reinitialize = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "지금 데이터 재수집",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "선택한 기간으로 ETF 데이터를 즉시 재수집합니다 (시간 소요)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays, reinitialize) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun SearchHistoryLimitDialog(
    currentLimit: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedLimit by remember { mutableIntStateOf(currentLimit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_search_history_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.settings_search_history_select))

                Column {
                    Text(
                        stringResource(R.string.settings_count_format, selectedLimit),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = selectedLimit.toFloat(),
                        onValueChange = { selectedLimit = it.toInt() },
                        valueRange = 5f..30f,
                        steps = 24
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.settings_count_format, 5), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.settings_count_format, 30), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedLimit) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// ==================== Data Classes ====================

private data class DaysOption(
    val days: Int,
    val label: String,
    val description: String
)
