package com.etfmonitor.ui.screens.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.etfmonitor.database.entities.*
import kotlinx.coroutines.launch

// 색상 정의
private val GreenPositive = Color(0xFF4CAF50)
private val RedNegative = Color(0xFFF44336)
private val BlueAccent = Color(0xFF2196F3)
private val OrangeAccent = Color(0xFFFF9800)

// 탭 정의
private enum class AdvancedTab(val title: String, val icon: ImageVector) {
    DASHBOARD("대시보드", Icons.Default.Dashboard),
    MARKET_CAP_FLOW("시총가중", Icons.AutoMirrored.Filled.TrendingUp),
    DIVERGENCE("수급분석", Icons.Default.CompareArrows),
    LIQUIDITY("유동성", Icons.Default.AccountBalance),
    SECTOR_FG("섹터심리", Icons.Default.PieChart),
    ETF_CORRELATION("ETF상관", Icons.Default.GridView)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedDashboardScreen(
    navController: NavHostController,
    viewModel: AdvancedDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pagerState = rememberPagerState(pageCount = { AdvancedTab.entries.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("고급 분석") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 탭 바
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 8.dp,
                divider = {}
            ) {
                AdvancedTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(20.dp)) }
                    )
                }
            }

            // 컨텐츠
            Box(modifier = Modifier.fillMaxSize()) {
                when (val currentState = state) {
                    is AdvancedDashboardState.Loading -> LoadingContent()
                    is AdvancedDashboardState.Error -> ErrorContent(currentState.message) { viewModel.loadDashboard() }
                    is AdvancedDashboardState.Success -> {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (AdvancedTab.entries[page]) {
                                AdvancedTab.DASHBOARD -> DashboardTab(currentState.data)
                                AdvancedTab.MARKET_CAP_FLOW -> MarketCapFlowTab(currentState.data)
                                AdvancedTab.DIVERGENCE -> DivergenceTab(currentState.data)
                                AdvancedTab.LIQUIDITY -> LiquidityTab(currentState.data)
                                AdvancedTab.SECTOR_FG -> SectorFearGreedTab(currentState.data)
                                AdvancedTab.ETF_CORRELATION -> EtfCorrelationTab(currentState.data)
                            }
                        }
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
}

// ==================== 탭 1: 통합 대시보드 ====================

@Composable
private fun DashboardTab(data: AdvancedDashboardData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 데이터 가용성
        item { DataAvailabilityCard(data.dataAvailability) }

        // 종합 신호
        item { OverallSignalCard(data.date, data.overallSignal) }

        // 핵심 지표 3개
        item { KeyMetricsRow(data.marketCapFlow, data.divergenceSummary, data.liquidityAnalysis) }

        // 섹터 심리 요약
        if (data.topGreedSectors.isNotEmpty() || data.topFearSectors.isNotEmpty()) {
            item { SectorSentimentSummary(data.topGreedSectors, data.topFearSectors) }
        }

        // 섹터 로테이션
        if (data.sectorRotationSignals.isNotEmpty()) {
            item { SectorRotationCard(data.sectorRotationSignals) }
        }

        // ETF 중복 경고
        if (data.highOverlapEtfs.isNotEmpty()) {
            item { EtfOverlapWarningCard(data.highOverlapEtfs.take(3)) }
        }
    }
}

// ==================== 탭 2: 시총 가중 ETF 흐름 ====================

@Composable
private fun MarketCapFlowTab(data: AdvancedDashboardData) {
    val flow = data.marketCapFlow

    if (flow == null) {
        EmptyStateCard("ETF 보유종목 데이터가 필요합니다", Icons.Default.BarChart)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 핵심 지표 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (flow.netFlow >= 0) GreenPositive.copy(alpha = 0.1f)
                                    else RedNegative.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("순 자금 흐름", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${if (flow.netFlow >= 0) "+" else ""}${formatAmount(flow.netFlow)}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (flow.netFlow >= 0) GreenPositive else RedNegative
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FlowStatItem("유입", "+${formatAmount(flow.totalInflow)}", GreenPositive)
                        FlowStatItem("유출", "-${formatAmount(flow.totalOutflow)}", RedNegative)
                    }
                }
            }
        }

        // 시총 규모별 분포
        item {
            SectionCard("규모별 분포") {
                MarketCapSize.entries.forEach { size ->
                    val inflow = flow.inflowBySize[size] ?: 0L
                    val outflow = flow.outflowBySize[size] ?: 0L
                    val net = inflow - outflow
                    val maxValue = maxOf(flow.totalInflow, flow.totalOutflow).coerceAtLeast(1L)

                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(size.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${if (net >= 0) "+" else ""}${formatAmount(net)}",
                                fontWeight = FontWeight.Bold,
                                color = if (net >= 0) GreenPositive else RedNegative
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowProgressBar(inflow, outflow, maxValue)
                    }
                }
            }
        }

        // 상위 유입 종목
        if (flow.topInflowStocks.isNotEmpty()) {
            item {
                SectionCard("상위 유입 종목") {
                    flow.topInflowStocks.take(10).forEachIndexed { idx, stock ->
                        StockFlowRow(idx + 1, stock, true)
                    }
                }
            }
        }

        // 상위 유출 종목
        if (flow.topOutflowStocks.isNotEmpty()) {
            item {
                SectionCard("상위 유출 종목") {
                    flow.topOutflowStocks.take(10).forEachIndexed { idx, stock ->
                        StockFlowRow(idx + 1, stock, false)
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FlowProgressBar(inflow: Long, outflow: Long, maxValue: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (inflow > 0) {
            Box(
                modifier = Modifier
                    .weight((inflow.toFloat() / maxValue).coerceIn(0.01f, 1f))
                    .fillMaxHeight()
                    .background(GreenPositive)
            )
        }
        if (outflow > 0) {
            Box(
                modifier = Modifier
                    .weight((outflow.toFloat() / maxValue).coerceIn(0.01f, 1f))
                    .fillMaxHeight()
                    .background(RedNegative)
            )
        }
        // 남은 공간 채우기
        val remaining = 1f - ((inflow + outflow).toFloat() / maxValue).coerceIn(0f, 1f)
        if (remaining > 0.01f) {
            Spacer(modifier = Modifier.weight(remaining))
        }
    }
}

@Composable
private fun StockFlowRow(rank: Int, stock: StockFlow, isInflow: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(stock.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "시총 ${formatMarketCap(stock.marketCap)} | ETF ${stock.etfCount}개",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${if (isInflow) "+" else ""}${formatAmount(stock.flowAmount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isInflow) GreenPositive else RedNegative
        )
    }
}

// ==================== 탭 3: 수급 Divergence ====================

@Composable
private fun DivergenceTab(data: AdvancedDashboardData) {
    val divergence = data.divergenceSummary

    if (divergence == null) {
        EmptyStateCard("종목 수급 분석 데이터가 필요합니다", Icons.Default.CompareArrows)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 시장 심리 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("시장 심리", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        divergence.marketSentiment.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 분포 현황
        item {
            SectionCard("수급 분포") {
                val total = divergence.foreignBullishCount + divergence.institutionBullishCount +
                        divergence.alignedBullishCount + divergence.alignedBearishCount + divergence.neutralCount

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DivergenceStatItem("외국인↑", divergence.foreignBullishCount, BlueAccent)
                    DivergenceStatItem("기관↑", divergence.institutionBullishCount, OrangeAccent)
                    DivergenceStatItem("동반↑", divergence.alignedBullishCount, GreenPositive)
                    DivergenceStatItem("동반↓", divergence.alignedBearishCount, RedNegative)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 분포 막대
                Row(
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                ) {
                    val items = listOf(
                        divergence.foreignBullishCount to BlueAccent,
                        divergence.institutionBullishCount to OrangeAccent,
                        divergence.alignedBullishCount to GreenPositive,
                        divergence.alignedBearishCount to RedNegative,
                        divergence.neutralCount to Color.Gray
                    )
                    items.forEach { (count, color) ->
                        if (count > 0 && total > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(count.toFloat() / total)
                                    .fillMaxHeight()
                                    .background(color)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "총 $total 종목 분석",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }

        // 외국인 강세 종목
        if (divergence.topForeignBullish.isNotEmpty()) {
            item {
                SectionCard("외국인 강세 종목 (기관 매도)", BlueAccent) {
                    divergence.topForeignBullish.take(5).forEach { stock ->
                        DivergenceStockRow(stock)
                    }
                }
            }
        }

        // 기관 강세 종목
        if (divergence.topInstitutionBullish.isNotEmpty()) {
            item {
                SectionCard("기관 강세 종목 (외국인 매도)", OrangeAccent) {
                    divergence.topInstitutionBullish.take(5).forEach { stock ->
                        DivergenceStockRow(stock)
                    }
                }
            }
        }
    }
}

@Composable
private fun DivergenceStatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DivergenceStockRow(stock: SupplyDemandDivergence) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stock.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "외국인: ${formatAmount(stock.foreign5d / 100)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlueAccent
                )
                Text(
                    "기관: ${formatAmount(stock.institution5d / 100)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangeAccent
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                String.format("%.2f", stock.divergenceScore),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Divergence", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ==================== 탭 4: 유동성 분석 ====================

@Composable
private fun LiquidityTab(data: AdvancedDashboardData) {
    val liquidity = data.liquidityAnalysis

    if (liquidity == null) {
        EmptyStateCard("예탁금/시총 데이터가 필요합니다", Icons.Default.AccountBalance)
        return
    }

    val signal = try { LiquiditySignal.valueOf(liquidity.signal) } catch (e: Exception) { LiquiditySignal.NEUTRAL }
    val riskLevel = try { LeverageRiskLevel.valueOf(liquidity.riskLevel) } catch (e: Exception) { LeverageRiskLevel.MEDIUM }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 핵심 지표 카드
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidityMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "예탁금",
                    value = formatTrillion(liquidity.depositAmount),
                    change = liquidity.depositChange,
                    isPositiveGood = true
                )
                LiquidityMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "신용잔고",
                    value = formatTrillion(liquidity.creditAmount),
                    change = liquidity.creditChange,
                    isPositiveGood = false
                )
            }
        }

        // 신호 및 위험도
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SignalCard(
                    modifier = Modifier.weight(1f),
                    title = "유동성 신호",
                    value = signal.displayName,
                    color = when (signal) {
                        LiquiditySignal.BULLISH_LIQUIDITY -> GreenPositive
                        LiquiditySignal.BEARISH_LEVERAGE -> RedNegative
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                SignalCard(
                    modifier = Modifier.weight(1f),
                    title = "레버리지 위험",
                    value = riskLevel.displayName,
                    color = when (riskLevel) {
                        LeverageRiskLevel.LOW -> GreenPositive
                        LeverageRiskLevel.MEDIUM -> OrangeAccent
                        LeverageRiskLevel.HIGH -> RedNegative
                        LeverageRiskLevel.EXTREME -> Color(0xFFD32F2F)
                    }
                )
            }
        }

        // 비율 분석
        item {
            SectionCard("비율 분석") {
                // 예탁금/시총 비율
                RatioProgressItem(
                    title = "예탁금/시총 비율",
                    value = liquidity.depositToMarketCapRatio,
                    maxValue = 5.0,
                    suffix = "%",
                    color = GreenPositive
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 신용/예탁금 비율
                RatioProgressItem(
                    title = "신용/예탁금 비율",
                    value = liquidity.creditToDepositRatio,
                    maxValue = 100.0,
                    suffix = "%",
                    color = when {
                        liquidity.creditToDepositRatio > 50 -> RedNegative
                        liquidity.creditToDepositRatio > 30 -> OrangeAccent
                        else -> GreenPositive
                    },
                    thresholds = listOf(30.0 to "보통", 50.0 to "주의")
                )
            }
        }

        // 역사적 백분위
        item {
            SectionCard("역사적 위치") {
                val percentile = (100 - liquidity.historicalPercentile).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("현재 유동성 수준", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "상위 $percentile%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (percentile / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                    color = when {
                        percentile < 30 -> RedNegative
                        percentile < 70 -> OrangeAccent
                        else -> GreenPositive
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("낮음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("높음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 시장 시총
        item {
            SectionCard("시장 시가총액") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "${String.format("%.0f", liquidity.totalMarketCap / 10000.0)}조원",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidityMetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    change: Double,
    isPositiveGood: Boolean
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (change != 0.0) {
                val isGood = if (isPositiveGood) change > 0 else change < 0
                Text(
                    String.format("%+.0f억", change),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isGood) GreenPositive else RedNegative
                )
            }
        }
    }
}

@Composable
private fun SignalCard(modifier: Modifier, title: String, value: String, color: Color) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun RatioProgressItem(
    title: String,
    value: Double,
    maxValue: Double,
    suffix: String,
    color: Color,
    thresholds: List<Pair<Double, String>> = emptyList()
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${String.format("%.2f", value)}$suffix",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (value / maxValue).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ==================== 탭 5: 섹터별 Fear & Greed ====================

@Composable
private fun SectorFearGreedTab(data: AdvancedDashboardData) {
    val allSectors = data.allSectorAnalyses.sortedByDescending { it.fearGreedValue }

    if (allSectors.isEmpty()) {
        EmptyStateCard("섹터 분석 데이터가 필요합니다", Icons.Default.PieChart)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 전체 시장 심리
        item {
            val avgFearGreed = allSectors.map { it.fearGreedValue }.average()
            val sentiment = when {
                avgFearGreed > 0.8 -> "극도의 탐욕"
                avgFearGreed > 0.6 -> "탐욕"
                avgFearGreed > 0.4 -> "중립"
                avgFearGreed > 0.2 -> "공포"
                else -> "극도의 공포"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = getFearGreedColor(avgFearGreed).copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("시장 전체 심리", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        String.format("%.0f", avgFearGreed * 100),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = getFearGreedColor(avgFearGreed)
                    )
                    Text(sentiment, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // 섹터 히트맵
        item {
            SectionCard("섹터별 심리 지수") {
                allSectors.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { sector ->
                            SectorHeatmapItem(
                                modifier = Modifier.weight(1f),
                                sector = sector
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 탐욕 상위 섹터
        val greedSectors = allSectors.filter { it.fearGreedValue > 0.6 }
        if (greedSectors.isNotEmpty()) {
            item {
                SectionCard("탐욕 섹터", GreenPositive) {
                    greedSectors.take(5).forEachIndexed { idx, sector ->
                        SectorDetailRow(idx + 1, sector)
                    }
                }
            }
        }

        // 공포 상위 섹터
        val fearSectors = allSectors.filter { it.fearGreedValue < 0.4 }.sortedBy { it.fearGreedValue }
        if (fearSectors.isNotEmpty()) {
            item {
                SectionCard("공포 섹터", RedNegative) {
                    fearSectors.take(5).forEachIndexed { idx, sector ->
                        SectorDetailRow(idx + 1, sector)
                    }
                }
            }
        }

        // 섹터 로테이션 신호
        if (data.sectorRotationSignals.isNotEmpty()) {
            item {
                SectionCard("섹터 로테이션 신호") {
                    data.sectorRotationSignals.take(3).forEach { signal ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                signal.description,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(signal.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectorHeatmapItem(modifier: Modifier, sector: SectorAnalysis) {
    val color = getFearGreedColor(sector.fearGreedValue)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                sector.sectorName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                String.format("%.0f", sector.fearGreedValue * 100),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun SectorDetailRow(rank: Int, sector: SectorAnalysis) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(sector.sectorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "신규 +${sector.newEntries} | 제외 -${sector.removals}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            String.format("%.0f", sector.fearGreedValue * 100),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = getFearGreedColor(sector.fearGreedValue)
        )
    }
}

private fun getFearGreedColor(value: Double): Color = when {
    value > 0.8 -> Color(0xFF1B5E20)
    value > 0.6 -> GreenPositive
    value > 0.4 -> OrangeAccent
    value > 0.2 -> Color(0xFFE65100)
    else -> RedNegative
}

// ==================== 탭 6: ETF 상관관계 ====================

@Composable
private fun EtfCorrelationTab(data: AdvancedDashboardData) {
    val overlaps = data.highOverlapEtfs

    if (overlaps.isEmpty()) {
        EmptyStateCard("ETF 상관관계 분석 데이터가 필요합니다", Icons.Default.GridView)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 높은 중복률 ETF 쌍
        item {
            SectionCard("높은 중복률 ETF 쌍") {
                Text(
                    "중복률 70% 이상인 ETF 쌍은 분산 효과가 낮습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                overlaps.take(10).forEach { pair ->
                    EtfCorrelationRow(pair)
                }
            }
        }

        // 분산 투자 권고
        if (overlaps.any { it.overlapRatio > 0.8 }) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = OrangeAccent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "분산 투자 권고",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "80% 이상 중복되는 ETF가 있습니다. 동일 종목에 과도하게 투자될 수 있으니 포트폴리오 재검토를 권장합니다.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // 상관관계 범례
        item {
            SectionCard("상관관계 범례") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CorrelationLegendItem("높음 >70%", Color(0xFFD32F2F))
                    CorrelationLegendItem("보통 40-70%", OrangeAccent)
                    CorrelationLegendItem("낮음 <40%", GreenPositive)
                }
            }
        }
    }
}

@Composable
private fun EtfCorrelationRow(pair: EtfCorrelationCache) {
    val overlapColor = when {
        pair.overlapRatio > 0.7 -> Color(0xFFD32F2F)
        pair.overlapRatio > 0.4 -> OrangeAccent
        else -> GreenPositive
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = overlapColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pair.etf1Name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        pair.etf2Name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${(pair.overlapRatio * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = overlapColor
                )
                Text(
                    "공통 ${pair.commonStockCount}종목",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CorrelationLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ==================== 공통 컴포넌트 ====================

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("분석 데이터 로드 중...")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("다시 시도") }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String, icon: ImageVector) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (accentColor != null) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DataAvailabilityCard(dataAvailability: DataAvailability) {
    var expanded by remember { mutableStateOf(false) }

    val allAvailable = dataAvailability.holdingsData.available &&
            dataAvailability.stockAnalysisData.available &&
            dataAvailability.marketDepositData.available &&
            dataAvailability.fearGreedData.available

    val availableCount = listOf(
        dataAvailability.holdingsData.available,
        dataAvailability.stockAnalysisData.available,
        dataAvailability.marketDepositData.available,
        dataAvailability.fearGreedData.available,
        dataAvailability.etfData.available
    ).count { it }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allAvailable) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (allAvailable) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (allAvailable) GreenPositive else OrangeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("데이터 소스: $availableCount/5", style = MaterialTheme.typography.titleSmall)
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                DataSourceRow("ETF 보유종목", dataAvailability.holdingsData)
                DataSourceRow("종목 수급분석", dataAvailability.stockAnalysisData)
                DataSourceRow("시장 예탁금", dataAvailability.marketDepositData)
                DataSourceRow("Fear & Greed", dataAvailability.fearGreedData)
                DataSourceRow("ETF 목록", dataAvailability.etfData)
            }
        }
    }
}

@Composable
private fun DataSourceRow(name: String, status: DataSourceStatus) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (status.available) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (status.available) GreenPositive else RedNegative,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.bodySmall)
        }
        Text(status.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverallSignalCard(date: String, signal: OverallSignal) {
    val (backgroundColor, textColor, icon) = when (signal.direction) {
        SignalDirection.STRONG_BUY -> Triple(Color(0xFF1B5E20), Color.White, Icons.Default.TrendingUp)
        SignalDirection.BUY -> Triple(GreenPositive, Color.White, Icons.AutoMirrored.Filled.TrendingUp)
        SignalDirection.NEUTRAL -> Triple(Color(0xFF9E9E9E), Color.White, Icons.Default.TrendingFlat)
        SignalDirection.SELL -> Triple(OrangeAccent, Color.White, Icons.Default.TrendingDown)
        SignalDirection.STRONG_SELL -> Triple(Color(0xFFD32F2F), Color.White, Icons.Default.TrendingDown)
    }

    val signalText = when (signal.direction) {
        SignalDirection.STRONG_BUY -> "강력 매수"
        SignalDirection.BUY -> "매수 우위"
        SignalDirection.NEUTRAL -> "중립"
        SignalDirection.SELL -> "매도 우위"
        SignalDirection.STRONG_SELL -> "강력 매도"
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = textColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(signalText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = textColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { signal.strength.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = textColor,
                trackColor = textColor.copy(alpha = 0.3f)
            )
            if (signal.factors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(signal.factors.joinToString(" + "), style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun KeyMetricsRow(
    flow: MarketCapWeightedFlow?,
    divergence: MarketDivergenceSummary?,
    liquidity: LiquidityAnalysis?
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "시총가중",
            value = flow?.let { "${if (it.netFlow >= 0) "+" else ""}${it.netFlow}억" } ?: "-",
            isPositive = flow?.netFlow?.let { it >= 0 }
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "수급",
            value = divergence?.marketSentiment?.displayName ?: "-",
            isPositive = divergence?.marketSentiment?.let {
                it == MarketSentimentType.CONSENSUS_BULLISH || it == MarketSentimentType.STRONG_FOREIGN_LED || it == MarketSentimentType.STRONG_INSTITUTION_LED
            }
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "유동성",
            value = liquidity?.let { try { LiquiditySignal.valueOf(it.signal).displayName } catch (e: Exception) { "-" } } ?: "-",
            isPositive = liquidity?.signal?.let { it == LiquiditySignal.BULLISH_LIQUIDITY.name }
        )
    }
}

@Composable
private fun MetricCard(modifier: Modifier, title: String, value: String, isPositive: Boolean?) {
    val backgroundColor = when (isPositive) {
        true -> GreenPositive.copy(alpha = 0.1f)
        false -> RedNegative.copy(alpha = 0.1f)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (isPositive) {
        true -> GreenPositive
        false -> RedNegative
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectorSentimentSummary(greed: List<SectorAnalysis>, fear: List<SectorAnalysis>) {
    SectionCard("섹터 심리") {
        if (greed.isNotEmpty()) {
            Text("탐욕", style = MaterialTheme.typography.labelSmall, color = GreenPositive)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(greed) { sector ->
                    SectorChip(sector.sectorName, sector.fearGreedValue, true)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (fear.isNotEmpty()) {
            Text("공포", style = MaterialTheme.typography.labelSmall, color = RedNegative)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(fear) { sector ->
                    SectorChip(sector.sectorName, sector.fearGreedValue, false)
                }
            }
        }
    }
}

@Composable
private fun SectorChip(name: String, value: Double, isGreed: Boolean) {
    val color = if (isGreed) GreenPositive else RedNegative
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.15f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodySmall, color = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(String.format("%.0f", value * 100), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SectorRotationCard(signals: List<SectorRotationSignal>) {
    SectionCard("섹터 로테이션") {
        signals.take(3).forEach { signal ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(signal.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${(signal.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EtfOverlapWarningCard(overlaps: List<EtfCorrelationCache>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ETF 중복 경고", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            overlaps.forEach { o ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${o.etf1Name} ↔ ${o.etf2Name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(o.overlapRatio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = OrangeAccent)
                }
            }
        }
    }
}

// ==================== 유틸리티 ====================

private fun formatAmount(amount: Long): String = when {
    kotlin.math.abs(amount) >= 10000 -> String.format("%.1f조", amount / 10000.0)
    kotlin.math.abs(amount) >= 1000 -> String.format("%.0f억", amount.toDouble())
    else -> "${amount}억"
}

private fun formatMarketCap(cap: Long): String = when {
    cap >= 10000 -> String.format("%.0f조", cap / 10000.0)
    cap >= 1000 -> String.format("%.1f조", cap / 10000.0)
    else -> "${cap}억"
}

private fun formatTrillion(amount: Double): String = String.format("%.1f조", amount / 10000)
