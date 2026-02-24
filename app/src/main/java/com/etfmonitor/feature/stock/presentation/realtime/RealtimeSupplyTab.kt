package com.etfmonitor.feature.stock.presentation.realtime

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.feature.stock.domain.model.RealtimeSupplyData
import com.etfmonitor.feature.stock.domain.model.RealtimeSupplySignal
import com.etfmonitor.feature.stock.domain.model.RealtimeSupplySummary
import java.text.NumberFormat
import java.util.Locale

@Composable
fun RealtimeSupplyContent(
    ticker: String?,
    modifier: Modifier = Modifier,
    viewModel: RealtimeSupplyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsState()

    LaunchedEffect(ticker) {
        if (ticker != null) {
            viewModel.loadForStock(ticker)
        } else {
            viewModel.clearStock()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val currentState = state) {
            is RealtimeSupplyState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "종목을 선택해주세요.\n검색 화면에서 종목을 검색하고 선택하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is RealtimeSupplyState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "장중 수급 데이터를 불러오는 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is RealtimeSupplyState.Success -> {
                RealtimeSupplySuccessContent(
                    summary = currentState.summary,
                    isRefreshing = isRefreshing,
                    autoRefreshEnabled = autoRefreshEnabled,
                    onRefresh = { viewModel.refresh() },
                    onToggleAutoRefresh = { viewModel.toggleAutoRefresh() }
                )
            }

            is RealtimeSupplyState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.refresh() }) {
                            Text("다시 시도")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealtimeSupplySuccessContent(
    summary: RealtimeSupplySummary,
    isRefreshing: Boolean,
    autoRefreshEnabled: Boolean,
    onRefresh: () -> Unit,
    onToggleAutoRefresh: () -> Unit
) {
    val data = summary.data
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Trading hours indicator + refresh controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (summary.isTradingHours)
                        Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (summary.isTradingHours) "장중" else "장외",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (summary.isTradingHours)
                            Color(0xFF4CAF50)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "자동갱신",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = autoRefreshEnabled,
                    onCheckedChange = { onToggleAutoRefresh() },
                    modifier = Modifier.height(24.dp)
                )
            }

            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "새로고침",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Signal Card
        SignalCard(signal = summary.signal, description = summary.signalDescription)

        // Supply/Demand Metrics
        SupplyDemandCard(data = data, numberFormat = numberFormat)

        // Net Buy Details
        NetBuyDetailsCard(data = data, numberFormat = numberFormat)

        // Fetch time
        val fetchTime = remember(data.fetchedAt) {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", Locale.KOREA)
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
            sdf.format(java.util.Date(data.fetchedAt))
        }
        Text(
            text = "최종 갱신: $fetchTime",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SignalCard(signal: RealtimeSupplySignal, description: String) {
    val (signalText, signalColor) = when (signal) {
        RealtimeSupplySignal.STRONG_BUY -> "강력매수" to Color(0xFFD32F2F)  // Red = Buy (Korean)
        RealtimeSupplySignal.BUY -> "매수" to Color(0xFFE57373)
        RealtimeSupplySignal.NEUTRAL -> "중립" to Color(0xFF9E9E9E)
        RealtimeSupplySignal.SELL -> "매도" to Color(0xFF64B5F6)
        RealtimeSupplySignal.STRONG_SELL -> "강력매도" to Color(0xFF1976D2)  // Blue = Sell (Korean)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = signalColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "투자자별 수급 신호",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = signalColor.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = signalText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = signalColor
                )
            }
        }
    }
}

@Composable
private fun SupplyDemandCard(data: RealtimeSupplyData, numberFormat: NumberFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "매매 현황",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider()

            // Buy / Sell amounts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "매수",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatAmount(data.buyAmount, numberFormat),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F)  // Red = Buy
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "매도",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatAmount(data.sellAmount, numberFormat),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)  // Blue = Sell
                    )
                }
            }

            // Net buy bar
            val netColor = if (data.netBuyAmount >= 0) Color(0xFFD32F2F) else Color(0xFF1976D2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "순매수금액",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatSignedAmount(data.netBuyAmount, numberFormat),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = netColor
                )
            }
        }
    }
}

@Composable
private fun NetBuyDetailsCard(data: RealtimeSupplyData, numberFormat: NumberFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "상세 정보",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider()

            DetailRow("현재가", numberFormat.format(data.currentPrice) + "원")
            DetailRow("순매수수량", numberFormat.format(data.netBuyQuantity) + "주")
            DetailRow("누적거래량", numberFormat.format(data.accumulatedVolume) + "주")
            DetailRow("순매수비율", String.format("%.2f%%", data.netBuyRatio * 100))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatAmount(amount: Long, numberFormat: NumberFormat): String {
    val absAmount = kotlin.math.abs(amount)
    return when {
        absAmount >= 10000 -> String.format("%.1f억", amount / 10000.0)
        absAmount >= 100 -> numberFormat.format(amount) + "백만"
        else -> numberFormat.format(amount) + "백만"
    }
}

private fun formatSignedAmount(amount: Long, numberFormat: NumberFormat): String {
    val prefix = if (amount > 0) "+" else ""
    return prefix + formatAmount(amount, numberFormat)
}
