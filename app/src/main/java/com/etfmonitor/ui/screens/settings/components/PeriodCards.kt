package com.etfmonitor.ui.screens.settings.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etfmonitor.R

/**
 * 기간 옵션 데이터 클래스
 */
data class PeriodOption(
    val days: Int,
    val label: String,
    val description: String
)

/**
 * 기간 설정 카드 구성 데이터 클래스
 */
data class PeriodCardConfig(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val dialogTitle: String,
    val recommendationText: String
)

/**
 * 일 수를 표시 텍스트로 변환 (Composable)
 */
@Composable
private fun daysToDisplayText(days: Int): String = when (days) {
    180 -> stringResource(R.string.period_6_months).substringBefore(" ")
    365 -> stringResource(R.string.period_12_months).substringBefore(" ")
    540 -> stringResource(R.string.period_18_months)
    730 -> stringResource(R.string.period_24_months)
    else -> stringResource(R.string.settings_days_format, days)
}

/**
 * 공통 기간 설정 카드 컴포넌트
 */
@Composable
fun PeriodCard(
    config: PeriodCardConfig,
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    config.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(config.title, style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            // 설명
            Text(
                config.description,
                style = MaterialTheme.typography.bodyMedium
            )

            // 현재 설정 표시 및 변경 버튼
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
                        daysToDisplayText(currentDays),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.settings_action_change))
                }
            }

            // 권장 사항 표시
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    config.recommendationText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        PeriodSelectionDialog(
            title = config.dialogTitle,
            currentDays = currentDays,
            onDismiss = { showDialog = false },
            onConfirm = { days, reinitialize ->
                onDaysChange(days, reinitialize)
                showDialog = false
            }
        )
    }
}

/**
 * Fear & Greed Index 수집 기간 카드
 */
@Composable
fun FearGreedPeriodCard(
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit
) {
    val config = PeriodCardConfig(
        title = stringResource(R.string.settings_feargreed_period),
        icon = Icons.Default.BarChart,
        description = stringResource(R.string.settings_feargreed_period_desc),
        dialogTitle = stringResource(R.string.settings_feargreed_period_title),
        recommendationText = stringResource(R.string.settings_feargreed_period_recommend)
    )

    PeriodCard(
        config = config,
        currentDays = currentDays,
        onDaysChange = onDaysChange
    )
}

/**
 * 과매수/과매도 수집 기간 카드
 */
@Composable
fun MarketOscillatorPeriodCard(
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit
) {
    val config = PeriodCardConfig(
        title = stringResource(R.string.settings_oscillator_period),
        icon = Icons.Default.Leaderboard,
        description = stringResource(R.string.settings_oscillator_period_desc),
        dialogTitle = stringResource(R.string.settings_oscillator_period_title),
        recommendationText = stringResource(R.string.settings_oscillator_period_recommend)
    )

    PeriodCard(
        config = config,
        currentDays = currentDays,
        onDaysChange = onDaysChange
    )
}

/**
 * 시장 지수 수집 기간 카드
 */
@Composable
fun MarketIndexPeriodCard(
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit
) {
    val config = PeriodCardConfig(
        title = stringResource(R.string.settings_market_index_period),
        icon = Icons.Default.Analytics,
        description = stringResource(R.string.settings_market_index_period_desc),
        dialogTitle = stringResource(R.string.settings_market_index_period_title),
        recommendationText = stringResource(R.string.settings_market_index_period_recommend)
    )

    PeriodCard(
        config = config,
        currentDays = currentDays,
        onDaysChange = onDaysChange
    )
}

/**
 * 기간 선택 다이얼로그
 */
@Composable
fun PeriodSelectionDialog(
    title: String,
    currentDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit
) {
    val periodOptions = listOf(
        PeriodOption(180, stringResource(R.string.option_months_6), stringResource(R.string.option_months_6_desc)),
        PeriodOption(365, stringResource(R.string.option_months_12), stringResource(R.string.option_months_12_desc)),
        PeriodOption(540, stringResource(R.string.option_months_18), stringResource(R.string.option_months_18_desc)),
        PeriodOption(730, stringResource(R.string.option_months_24), stringResource(R.string.option_months_24_desc))
    )

    var selectedDays by remember { mutableIntStateOf(currentDays) }
    var reinitialize by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.settings_period_select),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                periodOptions.forEach { option ->
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
                            "선택한 기간으로 데이터를 즉시 재수집합니다 (시간 소요)",
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
