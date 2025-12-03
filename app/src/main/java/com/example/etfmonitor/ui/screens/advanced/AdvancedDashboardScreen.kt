package com.etfmonitor.ui.screens.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.etfmonitor.database.entities.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedDashboardScreen(
    navController: NavHostController,
    viewModel: AdvancedDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("고급 분석 대시보드") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val currentState = state) {
                is AdvancedDashboardState.Loading -> {
                    LoadingContent()
                }
                is AdvancedDashboardState.Error -> {
                    ErrorContent(
                        message = currentState.message,
                        onRetry = { viewModel.loadDashboard() }
                    )
                }
                is AdvancedDashboardState.Success -> {
                    DashboardContent(
                        data = currentState.data,
                        onRefresh = { viewModel.refresh() }
                    )
                }
            }

            // 로딩 오버레이
            if (isRefreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("분석 데이터 로드 중...")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("다시 시도")
            }
        }
    }
}

@Composable
private fun DashboardContent(
    data: AdvancedDashboardData,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 날짜 및 종합 신호
        item {
            OverallSignalCard(
                date = data.date,
                signal = data.overallSignal
            )
        }

        // 핵심 지표 요약
        item {
            KeyMetricsRow(
                marketCapFlow = data.marketCapFlow,
                divergence = data.divergenceSummary,
                liquidity = data.liquidityAnalysis
            )
        }

        // 섹터 심리 요약
        if (data.topGreedSectors.isNotEmpty() || data.topFearSectors.isNotEmpty()) {
            item {
                SectorSentimentCard(
                    greedSectors = data.topGreedSectors,
                    fearSectors = data.topFearSectors
                )
            }
        }

        // 섹터 로테이션 신호
        if (data.sectorRotationSignals.isNotEmpty()) {
            item {
                SectorRotationCard(signals = data.sectorRotationSignals)
            }
        }

        // 시총 가중 흐름 상세
        data.marketCapFlow?.let { flow ->
            item {
                MarketCapFlowCard(flow = flow)
            }
        }

        // 수급 Divergence 상세
        data.divergenceSummary?.let { divergence ->
            item {
                DivergenceCard(divergence = divergence)
            }
        }

        // 유동성 분석 상세
        data.liquidityAnalysis?.let { liquidity ->
            item {
                LiquidityCard(liquidity = liquidity)
            }
        }

        // ETF 중복 경고
        if (data.highOverlapEtfs.isNotEmpty()) {
            item {
                EtfOverlapWarningCard(overlaps = data.highOverlapEtfs.take(5))
            }
        }
    }
}

@Composable
private fun OverallSignalCard(
    date: String,
    signal: OverallSignal
) {
    val (backgroundColor, textColor, icon) = when (signal.direction) {
        SignalDirection.STRONG_BUY -> Triple(Color(0xFF1B5E20), Color.White, Icons.Default.TrendingUp)
        SignalDirection.BUY -> Triple(Color(0xFF4CAF50), Color.White, Icons.AutoMirrored.Filled.TrendingUp)
        SignalDirection.NEUTRAL -> Triple(Color(0xFF9E9E9E), Color.White, Icons.Default.TrendingFlat)
        SignalDirection.SELL -> Triple(Color(0xFFFF9800), Color.White, Icons.Default.TrendingDown)
        SignalDirection.STRONG_SELL -> Triple(Color(0xFFD32F2F), Color.White, Icons.Default.TrendingDown)
    }

    val signalText = when (signal.direction) {
        SignalDirection.STRONG_BUY -> "강력 매수"
        SignalDirection.BUY -> "매수 우위"
        SignalDirection.NEUTRAL -> "중립"
        SignalDirection.SELL -> "매도 우위"
        SignalDirection.STRONG_SELL -> "강력 매도"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = textColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = signalText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 신뢰도 바
            LinearProgressIndicator(
                progress = { signal.strength.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = textColor,
                trackColor = textColor.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 주요 요인
            if (signal.factors.isNotEmpty()) {
                Text(
                    text = signal.factors.joinToString(" + "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun KeyMetricsRow(
    marketCapFlow: MarketCapWeightedFlow?,
    divergence: MarketDivergenceSummary?,
    liquidity: LiquidityAnalysis?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 시총 가중 흐름
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "시총가중",
            value = marketCapFlow?.let { "${if (it.netFlow >= 0) "+" else ""}${it.netFlow}억" } ?: "-",
            isPositive = marketCapFlow?.netFlow?.let { it >= 0 }
        )

        // 수급 심리
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "수급",
            value = divergence?.marketSentiment?.displayName ?: "-",
            isPositive = divergence?.marketSentiment?.let {
                it == MarketSentimentType.CONSENSUS_BULLISH ||
                it == MarketSentimentType.STRONG_FOREIGN_LED ||
                it == MarketSentimentType.STRONG_INSTITUTION_LED
            }
        )

        // 유동성
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "유동성",
            value = liquidity?.let { LiquiditySignal.valueOf(it.signal).displayName } ?: "-",
            isPositive = liquidity?.signal?.let { it == LiquiditySignal.BULLISH_LIQUIDITY.name }
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    isPositive: Boolean?
) {
    val backgroundColor = when (isPositive) {
        true -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        false -> Color(0xFFF44336).copy(alpha = 0.1f)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when (isPositive) {
        true -> Color(0xFF4CAF50)
        false -> Color(0xFFF44336)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun SectorSentimentCard(
    greedSectors: List<SectorAnalysis>,
    fearSectors: List<SectorAnalysis>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "섹터 심리",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 탐욕 섹터
            if (greedSectors.isNotEmpty()) {
                Text(
                    text = "탐욕",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(greedSectors) { sector ->
                        SectorChip(
                            name = sector.sectorName,
                            value = sector.fearGreedValue,
                            isGreed = true
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 공포 섹터
            if (fearSectors.isNotEmpty()) {
                Text(
                    text = "공포",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF44336)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fearSectors) { sector ->
                        SectorChip(
                            name = sector.sectorName,
                            value = sector.fearGreedValue,
                            isGreed = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectorChip(
    name: String,
    value: Double,
    isGreed: Boolean
) {
    val backgroundColor = if (isGreed) Color(0xFF4CAF50) else Color(0xFFF44336)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = backgroundColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = String.format("%.0f", value * 100),
                style = MaterialTheme.typography.bodySmall,
                color = backgroundColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SectorRotationCard(signals: List<SectorRotationSignal>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "섹터 로테이션 신호",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            signals.take(3).forEach { signal ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = signal.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(signal.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketCapFlowCard(flow: MarketCapWeightedFlow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "시총 가중 ETF 흐름",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 요약
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("유입", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "+${flow.totalInflow}억",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4CAF50)
                    )
                }
                Column {
                    Text("유출", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "-${flow.totalOutflow}억",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFF44336)
                    )
                }
                Column {
                    Text("순흐름", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${if (flow.netFlow >= 0) "+" else ""}${flow.netFlow}억",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (flow.netFlow >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }

            // 규모별 분포
            if (flow.inflowBySize.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text("규모별 분포", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))

                MarketCapSize.entries.forEach { size ->
                    val inflow = flow.inflowBySize[size] ?: 0
                    val outflow = flow.outflowBySize[size] ?: 0
                    val net = inflow - outflow

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(size.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${if (net >= 0) "+" else ""}${net}억",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (net >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DivergenceCard(divergence: MarketDivergenceSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "외국인/기관 수급 Divergence",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 시장 심리
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("시장 심리: ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    divergence.marketSentiment.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 분포
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DivergenceCountItem("외국인↑", divergence.foreignBullishCount, Color(0xFF2196F3))
                DivergenceCountItem("기관↑", divergence.institutionBullishCount, Color(0xFFFF9800))
                DivergenceCountItem("동반↑", divergence.alignedBullishCount, Color(0xFF4CAF50))
                DivergenceCountItem("동반↓", divergence.alignedBearishCount, Color(0xFFF44336))
            }
        }
    }
}

@Composable
private fun DivergenceCountItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LiquidityCard(liquidity: LiquidityAnalysis) {
    val signal = try {
        LiquiditySignal.valueOf(liquidity.signal)
    } catch (e: Exception) {
        LiquiditySignal.NEUTRAL
    }

    val riskLevel = try {
        LeverageRiskLevel.valueOf(liquidity.riskLevel)
    } catch (e: Exception) {
        LeverageRiskLevel.MEDIUM
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "시장 유동성 분석",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 핵심 지표
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("예탁금", style = MaterialTheme.typography.bodySmall)
                    Text(
                        String.format("%.1f조", liquidity.depositAmount / 10000),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Column {
                    Text("신용잔고", style = MaterialTheme.typography.bodySmall)
                    Text(
                        String.format("%.1f조", liquidity.creditAmount / 10000),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 신호 및 위험도
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("신호: ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        signal.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (signal) {
                            LiquiditySignal.BULLISH_LIQUIDITY -> Color(0xFF4CAF50)
                            LiquiditySignal.BEARISH_LEVERAGE -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("레버리지: ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        riskLevel.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (riskLevel) {
                            LeverageRiskLevel.LOW -> Color(0xFF4CAF50)
                            LeverageRiskLevel.MEDIUM -> Color(0xFFFF9800)
                            LeverageRiskLevel.HIGH -> Color(0xFFF44336)
                            LeverageRiskLevel.EXTREME -> Color(0xFFD32F2F)
                        }
                    )
                }
            }

            // 백분위
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "예탁금/시총 비율: 역사적 상위 ${(100 - liquidity.historicalPercentile).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EtfOverlapWarningCard(overlaps: List<EtfCorrelationCache>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF9800)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ETF 중복 경고",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            overlaps.forEach { overlap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${overlap.etf1Name} ↔ ${overlap.etf2Name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(overlap.overlapRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        }
    }
}
