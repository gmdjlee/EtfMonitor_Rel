package com.etfmonitor.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.ui.screens.settings.EtfUpdateSettings
import com.etfmonitor.ui.screens.settings.FearGreedUpdateSettings
import com.etfmonitor.ui.screens.settings.MarketDepositUpdateSettings
import com.etfmonitor.ui.screens.settings.MarketOscillatorUpdateSettings
import com.etfmonitor.ui.screens.settings.StockUpdateSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 통계 정보 항목
 */
data class StatItem(
    val label: String,
    val value: String
)

/**
 * 데이터 업데이트 카드 설정
 */
data class DataUpdateCardConfig(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val updateHour: Int,
    val updateMinute: Int,
    val lastUpdateTime: Long?,
    val isUpdating: Boolean,
    val stats: List<StatItem>
)

/**
 * 공통 데이터 업데이트 카드 컴포넌트
 */
@Composable
fun DataUpdateCard(
    config: DataUpdateCardConfig,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
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

            // 업데이트 시간 설정
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.settings_update_time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${String.format("%02d", config.updateHour)}:${String.format("%02d", config.updateMinute)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showTimePicker = true }) {
                    Text(stringResource(R.string.settings_action_change))
                }
            }

            // 통계 정보
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    config.stats.forEach { stat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stat.label,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stat.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    config.lastUpdateTime?.let { time ->
                        val dateStr = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            Locale.getDefault()
                        ).format(Date(time))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.settings_last_update),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                dateStr,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 업데이트 버튼
            Button(
                onClick = onUpdateNow,
                enabled = !config.isUpdating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (config.isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_updating))
                } else {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_update_now))
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            currentHour = config.updateHour,
            currentMinute = config.updateMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                onTimeChange(hour, minute)
                showTimePicker = false
            }
        )
    }
}

/**
 * 종목 DB 자동 업데이트 카드
 */
@Composable
fun StockUpdateCard(
    settings: StockUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_stock_update),
        icon = Icons.Default.Schedule,
        description = stringResource(R.string.settings_stock_update_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_stock_count), stringResource(R.string.label_etf_count_unit, settings.stockCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * 증시 자금 DB 자동 업데이트 카드
 */
@Composable
fun MarketDepositUpdateCard(
    settings: MarketDepositUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_deposit_update),
        icon = Icons.Default.TrendingUp,
        description = stringResource(R.string.settings_deposit_update_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_deposit_count), stringResource(R.string.label_etf_count_unit, settings.depositCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * Fear & Greed Index DB 자동 업데이트 카드
 */
@Composable
fun FearGreedUpdateCard(
    settings: FearGreedUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_feargreed_update),
        icon = Icons.Default.Psychology,
        description = stringResource(R.string.settings_feargreed_update_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_kospi_count), stringResource(R.string.label_etf_count_unit, settings.kospiCount)),
            StatItem(stringResource(R.string.settings_kosdaq_count), stringResource(R.string.label_etf_count_unit, settings.kosdaqCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * 시장 오실레이터 DB 자동 업데이트 카드
 */
@Composable
fun MarketOscillatorUpdateCard(
    settings: MarketOscillatorUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_oscillator_update),
        icon = Icons.Default.ShowChart,
        description = stringResource(R.string.settings_oscillator_update_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_kospi_count), stringResource(R.string.label_etf_count_unit, settings.kospiCount)),
            StatItem(stringResource(R.string.settings_kosdaq_count), stringResource(R.string.label_etf_count_unit, settings.kosdaqCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * ETF 데이터 자동 업데이트 카드
 * 다른 데이터 업데이트 카드와 동일한 형식
 * 참고: ETF 데이터 초기화는 DatabaseCard의 데이터베이스 초기화에서 지원됨
 */
@Composable
fun EtfDataManagementCard(
    settings: EtfUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_etf_data_management),
        icon = Icons.Default.CloudDownload,
        description = stringResource(R.string.settings_etf_data_management_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_etf_count), stringResource(R.string.label_etf_count_unit, settings.etfCount)),
            StatItem(stringResource(R.string.settings_holding_count), stringResource(R.string.label_etf_count_unit, settings.holdingCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * 시간 선택 다이얼로그
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    currentHour: Int,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = currentHour,
        initialMinute = currentMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_update_time_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
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
