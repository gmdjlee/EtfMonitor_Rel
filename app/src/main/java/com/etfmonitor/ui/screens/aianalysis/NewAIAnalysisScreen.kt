package com.etfmonitor.ui.screens.aianalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import com.etfmonitor.database.entities.SearchHistory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.core.analysis.AIStockIndicatorInterpretation
import com.etfmonitor.core.analysis.FullStockIndicatorCorrelationResult
import com.etfmonitor.core.analysis.IndicatorStockCorrelation
import com.etfmonitor.core.analysis.MarketIndicatorType
import com.etfmonitor.core.analysis.SignalType
import com.etfmonitor.core.analysis.StockIndicatorCorrelationResult
import com.etfmonitor.core.analysis.StockMetricType
import com.etfmonitor.database.entities.AIChatMessage
import com.etfmonitor.database.entities.Stock
import com.etfmonitor.database.entities.AIChatSession
import com.etfmonitor.database.entities.CorrelationAnalysisResult
import com.etfmonitor.database.entities.StockIndicatorAIResult
import com.etfmonitor.repository.FullAnalysisResult
import com.etfmonitor.ui.components.*
import com.etfmonitor.core.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 분석 탭 종류
 */
enum class AnalysisTab(val title: String) {
    CORRELATION("상관관계"),
    STOCK_INDICATOR("종목-지표")
}

/**
 * 새로운 AI 분석 화면
 * 상관관계 분석 + 종목-지표 상관관계 분석 + AI 해석 + 채팅 기능 통합
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAIAnalysisScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOscillator: ((String) -> Unit)? = null,
    viewModel: NewAIAnalysisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val isApiKeyConfigured by viewModel.isApiKeyConfigured.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val stockIndicatorCorrelationResult by viewModel.stockIndicatorCorrelationResult.collectAsState()
    val analysisPeriod by viewModel.analysisPeriod.collectAsState()
    val selectedStock by viewModel.selectedStock.collectAsState()
    val stockSearchResults by viewModel.stockSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isSendingMessage by viewModel.isSendingMessage.collectAsState()
    val chatSessions by viewModel.chatSessions.collectAsState(initial = emptyList())
    val stockIndicatorAIHistory by viewModel.stockIndicatorAIHistory.collectAsState(initial = emptyList())
    val searchHistory by viewModel.searchHistory.collectAsState(initial = emptyList())
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()

    var showProviderDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showStockIndicatorHistorySheet by remember { mutableStateOf(false) }
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val selectedTab = AnalysisTab.entries[selectedTabIndex]

    // 화면 진입 시 API 키 상태 새로고침 (설정에서 돌아왔을 때 반영)
    LaunchedEffect(Unit) {
        viewModel.refreshApiKeyState()
    }

    // FAB 표시 조건: 종목-지표 탭에서 종목이 선택되고 분석 결과가 있을 때
    val showFab = quickChartAnalysisEnabled &&
            onNavigateToOscillator != null &&
            selectedTab == AnalysisTab.STOCK_INDICATOR &&
            selectedStock != null &&
            stockIndicatorCorrelationResult?.correlationResult != null &&
            currentSession == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 분석") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSession != null) {
                            viewModel.closeChat()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    // 캐시 초기화 및 재분석 버튼
                    IconButton(onClick = { viewModel.clearCacheAndRefresh() }) {
                        Icon(Icons.Default.Refresh, "새로고침")
                    }

                    // AI 제공자 선택
                    TextButton(onClick = { showProviderDialog = true }) {
                        Text(
                            selectedProvider.name,
                            color = if (isApiKeyConfigured)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }

                    // 대화 이력
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(Icons.Default.History, "대화 이력")
                    }
                }
            )
        },
        floatingActionButton = {
            if (showFab && selectedStock != null) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToOscillator?.invoke(selectedStock!!.first) },
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                    text = { Text("차트 분석") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                currentSession != null -> {
                    // 채팅 화면
                    ChatScreen(
                        messages = chatMessages,
                        isSending = isSendingMessage,
                        onSendMessage = { viewModel.sendMessage(it) },
                        state = state
                    )
                }
                else -> {
                    Column {
                        // 탭 선택
                        TabRow(
                            selectedTabIndex = selectedTab.ordinal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AnalysisTab.entries.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { viewModel.selectTab(tab.ordinal) },
                                    text = { Text(tab.title) }
                                )
                            }
                        }

                        // 탭 내용
                        when (selectedTab) {
                            AnalysisTab.CORRELATION -> {
                                CorrelationAnalysisScreen(
                                    state = state,
                                    selectedMarket = selectedMarket,
                                    isApiKeyConfigured = isApiKeyConfigured,
                                    analysisResult = analysisResult,
                                    onMarketSelect = { viewModel.selectMarket(it) },
                                    onRunCorrelation = { viewModel.runCorrelationAnalysis() },
                                    onRunFullAnalysis = { viewModel.runFullAnalysis() },
                                    onInterpretWithAI = { viewModel.interpretWithAI(it) },
                                    onStartChat = { viewModel.startNewChat() },
                                    onClearError = { viewModel.clearError() }
                                )
                            }
                            AnalysisTab.STOCK_INDICATOR -> {
                                StockIndicatorCorrelationScreen(
                                    state = state,
                                    analysisPeriod = analysisPeriod,
                                    isApiKeyConfigured = isApiKeyConfigured,
                                    selectedStock = selectedStock,
                                    stockIndicatorCorrelationResult = stockIndicatorCorrelationResult,
                                    stockSearchResults = stockSearchResults,
                                    isSearching = isSearching,
                                    historyCount = stockIndicatorAIHistory.size,
                                    searchHistory = searchHistory,
                                    onPeriodChange = { viewModel.setAnalysisPeriod(it) },
                                    onSearchStock = { viewModel.searchStock(it) },
                                    onSelectStock = { ticker, name -> viewModel.selectStock(ticker, name) },
                                    onClearStock = { viewModel.clearSelectedStock() },
                                    onRunAnalysis = { viewModel.analyzeStockIndicatorCorrelation() },
                                    onRunFullAnalysis = { viewModel.runFullStockIndicatorCorrelationAnalysis() },
                                    onInterpretWithAI = { viewModel.interpretStockIndicatorCorrelationWithAI() },
                                    onStartChat = { viewModel.startNewChat() },
                                    onClearError = { viewModel.clearError() },
                                    onShowHistory = { showStockIndicatorHistorySheet = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // AI 제공자 선택 다이얼로그
    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text("AI 제공자 선택") },
            text = {
                Column {
                    viewModel.getAvailableProviders().forEach { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectProvider(provider)
                                    showProviderDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = provider == selectedProvider,
                                onClick = {
                                    viewModel.selectProvider(provider)
                                    showProviderDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(provider.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    // 대화 이력 바텀 시트
    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "대화 이력",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (chatSessions.isEmpty()) {
                    Text(
                        "저장된 대화가 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(chatSessions) { session ->
                            SessionItem(
                                session = session,
                                onClick = {
                                    viewModel.openSession(session.id)
                                    showHistorySheet = false
                                },
                                onDelete = { viewModel.deleteSession(session.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 종목-지표 분석 이력 바텀 시트
    if (showStockIndicatorHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showStockIndicatorHistorySheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "AI 분석 이력",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (stockIndicatorAIHistory.isEmpty()) {
                    Text(
                        "저장된 분석 결과가 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(stockIndicatorAIHistory) { historyItem ->
                            StockIndicatorAIHistoryItem(
                                item = historyItem,
                                onClick = {
                                    viewModel.loadFromHistory(historyItem)
                                    showStockIndicatorHistorySheet = false
                                },
                                onDelete = { viewModel.deleteHistoryItem(historyItem.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 종목-지표 AI 분석 히스토리 아이템
 */
@Composable
private fun StockIndicatorAIHistoryItem(
    item: StockIndicatorAIResult,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 신호 아이콘
            val signalColor = when (item.signal) {
                "STRONG_BUY" -> Color(0xFF4CAF50)
                "BUY" -> Color(0xFF8BC34A)
                "NEUTRAL" -> Color(0xFFFFB300)
                "SELL" -> Color(0xFFFF9800)
                "STRONG_SELL" -> Color(0xFFF44336)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(signalColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.signal) {
                        "STRONG_BUY", "BUY" -> Icons.Default.TrendingUp
                        "STRONG_SELL", "SELL" -> Icons.Default.TrendingDown
                        else -> Icons.Default.TrendingFlat
                    },
                    contentDescription = null,
                    tint = signalColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${item.stockName} (${item.ticker})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${item.analysisDate} | ${item.market}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.signal.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = signalColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "신뢰도 ${(item.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("분석 결과 삭제") },
            text = { Text("${item.stockName}의 분석 결과를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun CorrelationAnalysisScreen(
    state: NewAIAnalysisState,
    selectedMarket: String,
    isApiKeyConfigured: Boolean,
    analysisResult: FullAnalysisResult?,
    onMarketSelect: (String) -> Unit,
    onRunCorrelation: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: (CorrelationAnalysisResult) -> Unit,
    onStartChat: () -> Unit,
    onClearError: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 시장 선택
        item {
            AnalysisMarketSelector(
                selectedMarket = selectedMarket,
                onMarketSelect = onMarketSelect
            )
        }

        // API 키 경고
        if (!isApiKeyConfigured) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI 분석을 위해 설정에서 API 키를 등록해주세요",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 분석 버튼
        item {
            AnalysisButtons(
                state = state,
                isApiKeyConfigured = isApiKeyConfigured,
                hasCorrelationResult = analysisResult?.correlationResult != null,
                onRunCorrelation = onRunCorrelation,
                onRunFullAnalysis = onRunFullAnalysis,
                onInterpretWithAI = {
                    analysisResult?.correlationResult?.let { onInterpretWithAI(it) }
                }
            )
        }

        // 에러 표시
        when (state) {
            is NewAIAnalysisState.Error -> {
                item {
                    AnalysisErrorCard(message = state.message, onDismiss = onClearError)
                }
            }
            else -> {}
        }

        // 분석 결과
        analysisResult?.let { result ->
            item {
                CorrelationResultCard(
                    result = result.correlationResult,
                    aiResult = result.aiResult
                )
            }

            // AI 해석 결과
            result.aiResult?.let { aiResult ->
                item {
                    AIInterpretationCard(
                        signal = aiResult.signal,
                        confidence = aiResult.confidence,
                        upProbability = aiResult.upProbability,
                        downProbability = aiResult.downProbability,
                        reasoning = aiResult.reasoning,
                        recommendation = aiResult.recommendation,
                        riskLevel = aiResult.riskLevel
                    )
                }
            }

            // 채팅 시작 버튼
            item {
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state !is NewAIAnalysisState.AnalyzingCorrelation &&
                            state !is NewAIAnalysisState.AnalyzingFull &&
                            state !is NewAIAnalysisState.InterpretingWithAI
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("분석 결과로 대화하기")
                }
            }
        }
    }
}

/**
 * 종목-지표 상관관계 분석 화면
 * 시장은 종목 티커에 따라 자동 감지됨 (KOSPI: 0,1,2,3으로 시작, 나머지: KOSDAQ)
 */
@Composable
private fun StockIndicatorCorrelationScreen(
    state: NewAIAnalysisState,
    analysisPeriod: Int,
    isApiKeyConfigured: Boolean,
    selectedStock: Pair<String, String>?,
    stockIndicatorCorrelationResult: FullStockIndicatorCorrelationResult?,
    stockSearchResults: List<Pair<String, String>>,
    isSearching: Boolean,
    historyCount: Int,
    searchHistory: List<SearchHistory>,
    onPeriodChange: (Int) -> Unit,
    onSearchStock: (String) -> Unit,
    onSelectStock: (String, String) -> Unit,
    onClearStock: () -> Unit,
    onRunAnalysis: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: () -> Unit,
    onStartChat: () -> Unit,
    onClearError: () -> Unit,
    onShowHistory: () -> Unit
) {
    val isLoading = state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelation ||
            state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelationFull ||
            state is NewAIAnalysisState.InterpretingStockIndicatorCorrelation

    var searchQuery by remember { mutableStateOf("") }

    // 선택된 종목의 시장 자동 감지
    val detectedMarket = selectedStock?.let { Stock.inferMarket(it.first) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 종목 검색
        item {
            StockSearchSection(
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    searchQuery = it
                    onSearchStock(it)
                },
                searchResults = stockSearchResults,
                isSearching = isSearching,
                selectedStock = selectedStock,
                detectedMarket = detectedMarket,
                searchHistory = searchHistory,
                onSelectStock = { ticker, name ->
                    onSelectStock(ticker, name)
                    searchQuery = ""
                },
                onClearStock = onClearStock
            )
        }

        // 분석 이력 버튼
        item {
            OutlinedCard(
                onClick = onShowHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "AI 분석 이력",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (historyCount > 0) "저장된 분석 결과 ${historyCount}개" else "저장된 분석 결과 없음",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 분석 기간 선택
        item {
            TimeSeriesPeriodSelector(
                period = analysisPeriod,
                onPeriodChange = onPeriodChange
            )
        }

        // API 키 경고
        if (!isApiKeyConfigured) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI 분석을 위해 설정에서 API 키를 등록해주세요",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 분석 버튼
        item {
            StockIndicatorCorrelationButtons(
                state = state,
                isApiKeyConfigured = isApiKeyConfigured,
                hasSelectedStock = selectedStock != null,
                hasCorrelationResult = stockIndicatorCorrelationResult?.correlationResult != null,
                onRunAnalysis = onRunAnalysis,
                onRunFullAnalysis = onRunFullAnalysis,
                onInterpretWithAI = onInterpretWithAI
            )
        }

        // 에러 표시
        when (state) {
            is NewAIAnalysisState.Error -> {
                item {
                    AnalysisErrorCard(message = state.message, onDismiss = onClearError)
                }
            }
            else -> {}
        }

        // 상관관계 분석 결과
        stockIndicatorCorrelationResult?.correlationResult?.let { result ->
            // 종목 정보 요약 카드
            item {
                StockIndicatorSummaryCard(result = result)
            }

            // Fear & Greed 상관관계 차트
            if (result.fearGreedCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "심리 지표 상관관계",
                        subtitle = "Fear & Greed, RSI, 모멘텀",
                        icon = Icons.Default.Psychology,
                        correlations = result.fearGreedCorrelations,
                        color = Color(0xFF6750A4)
                    )
                }
            }

            // Oscillator 상관관계 차트
            if (result.oscillatorCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "기술 지표 상관관계",
                        subtitle = "시장 과매수/과매도",
                        icon = Icons.Default.TrendingUp,
                        correlations = result.oscillatorCorrelations,
                        color = Color(0xFF1976D2)
                    )
                }
            }

            // 예탁금/신용 상관관계 차트
            if (result.depositCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "자금 동향 상관관계",
                        subtitle = "고객예탁금, 신용잔고",
                        icon = Icons.Default.AccountBalance,
                        correlations = result.depositCorrelations,
                        color = Color(0xFF388E3C)
                    )
                }
            }

            // ETF 수급 상관관계 차트
            if (result.etfCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "ETF 수급 상관관계",
                        subtitle = "ETF 편입/편출, 비중 변화",
                        icon = Icons.Default.ShowChart,
                        correlations = result.etfCorrelations,
                        color = Color(0xFFE64A19)
                    )
                }
            }

            // Top 상관관계 요약
            if (result.topPositiveCorrelations.isNotEmpty() || result.topNegativeCorrelations.isNotEmpty()) {
                item {
                    TopCorrelationsCard(
                        topPositive = result.topPositiveCorrelations,
                        topNegative = result.topNegativeCorrelations
                    )
                }
            }

            // AI 해석 결과
            stockIndicatorCorrelationResult.aiInterpretation?.let { aiResult ->
                item {
                    StockIndicatorAIInterpretationCard(interpretation = aiResult)
                }
            }

            // 채팅 시작 버튼
            item {
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("상관관계 분석 결과로 대화하기")
                }
            }
        }
    }
}

/**
 * 종목 검색 섹션
 */
@Composable
private fun StockSearchSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<Pair<String, String>>,
    isSearching: Boolean,
    selectedStock: Pair<String, String>?,
    detectedMarket: String? = null,
    searchHistory: List<SearchHistory> = emptyList(),
    onSelectStock: (String, String) -> Unit,
    onClearStock: () -> Unit,
    onSelectFromHistory: ((String, String) -> Unit)? = null
) {
    var textFieldValue by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column {
        Text(
            "종목 선택",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 선택된 종목 표시
        if (selectedStock != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selectedStock.second,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            // 감지된 시장 표시
                            if (detectedMarket != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (detectedMarket == "KOSPI")
                                        Color(0xFF1976D2).copy(alpha = 0.15f)
                                    else
                                        Color(0xFFE64A19).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = detectedMarket,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (detectedMarket == "KOSPI")
                                            Color(0xFF1976D2)
                                        else
                                            Color(0xFFE64A19),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "${selectedStock.first}${if (detectedMarket != null) " • ${detectedMarket} 지표로 분석" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onClearStock) {
                        Icon(Icons.Default.Close, contentDescription = "선택 해제")
                    }
                }
            }
        } else {
            // 검색 입력 - Box로 감싸서 드롭다운 오버레이
            Box(modifier = Modifier.fillMaxWidth()) {
                // 검색 필드 - AnalysisTab 스타일
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        onSearchQueryChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "종목명 또는 종목코드 입력",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // History 버튼
                            if (searchHistory.isNotEmpty() && textFieldValue.isEmpty()) {
                                IconButton(onClick = { showHistoryDialog = true }) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = "검색 히스토리",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // Clear 버튼
                            if (textFieldValue.isNotEmpty()) {
                                IconButton(onClick = {
                                    textFieldValue = ""
                                    onSearchQueryChange("")
                                }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "지우기",
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
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                )

                // 자동완성 드롭다운 - 오버레이
                if (searchResults.isNotEmpty() && textFieldValue.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp)
                            .heightIn(max = 300.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 8.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = MaterialTheme.extendedShapes.cardLarge
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(searchResults) { (ticker, name) ->
                                ListItem(
                                    headlineContent = { Text(name) },
                                    supportingContent = {
                                        Text(
                                            ticker,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        textFieldValue = name
                                        onSearchQueryChange("")
                                        onSelectStock(ticker, name)
                                    }
                                )
                                if (searchResults.last().first != ticker) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 검색 히스토리 다이얼로그
    if (showHistoryDialog && searchHistory.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
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
                    Text("최근 검색")
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
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
                                        textFieldValue = history.name
                                        onSelectFromHistory?.invoke(history.ticker, history.name)
                                            ?: onSelectStock(history.ticker, history.name)
                                        showHistoryDialog = false
                                    }
                                )
                                if (history != searchHistory.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }
}

/**
 * 종목-지표 상관관계 분석 버튼
 */
@Composable
private fun StockIndicatorCorrelationButtons(
    state: NewAIAnalysisState,
    isApiKeyConfigured: Boolean,
    hasSelectedStock: Boolean,
    hasCorrelationResult: Boolean,
    onRunAnalysis: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: () -> Unit
) {
    val isLoading = state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelation ||
            state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelationFull ||
            state is NewAIAnalysisState.InterpretingStockIndicatorCorrelation

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 상관관계 분석 (로컬만)
        OutlinedButton(
            onClick = onRunAnalysis,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && hasSelectedStock
        ) {
            if (state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelation) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Analytics, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("상관관계 분석 (로컬)")
        }

        // 전체 분석 (상관관계 + AI 해석)
        Button(
            onClick = onRunFullAnalysis,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && isApiKeyConfigured && hasSelectedStock
        ) {
            if (state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelationFull) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Psychology, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("전체 분석 (+ AI)")
        }

        // AI 해석 추가
        if (hasCorrelationResult && isApiKeyConfigured) {
            TextButton(
                onClick = onInterpretWithAI,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (state is NewAIAnalysisState.InterpretingStockIndicatorCorrelation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 해석 추가")
            }
        }
    }
}

/**
 * 종목-지표 상관관계 요약 카드
 */
@Composable
private fun StockIndicatorSummaryCard(
    result: StockIndicatorCorrelationResult
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "상관관계 분석 결과",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${result.stockName} (${result.ticker})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${result.market} | ${result.startDate} ~ ${result.endDate} | ${result.totalDataPoints}일",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 분석 요약
            Text(
                result.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 카테고리별 상관관계 카드 (차트 포함)
 */
@Composable
private fun CorrelationCategoryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    correlations: List<IndicatorStockCorrelation>,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 상관관계 바 차트
            correlations.forEach { correlation ->
                CorrelationBarItem(
                    label = getIndicatorDisplayName(correlation.indicatorType) +
                            " vs " + getMetricDisplayName(correlation.stockMetricType),
                    value = correlation.correlation,
                    leadLagDays = correlation.leadLagDays,
                    color = color
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 상관관계 바 아이템
 * leadLagDays: 양수면 지표가 주가보다 선행, 음수면 후행
 */
@Composable
private fun CorrelationBarItem(
    label: String,
    value: Double,
    leadLagDays: Int = 0,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 레이블과 선행/후행 표시
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall
                )
                // 선행/후행 배지
                if (leadLagDays != 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val isLeading = leadLagDays > 0
                    Surface(
                        color = if (isLeading)
                            Color(0xFF2196F3).copy(alpha = 0.15f)
                        else
                            Color(0xFFFF9800).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLeading)
                                    Icons.Default.TrendingUp
                                else
                                    Icons.Default.TrendingDown,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = if (isLeading) Color(0xFF2196F3) else Color(0xFFFF9800)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isLeading)
                                    "${leadLagDays}일 선행"
                                else
                                    "${-leadLagDays}일 후행",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLeading) Color(0xFF2196F3) else Color(0xFFFF9800),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
            // 상관계수 값
            Text(
                String.format("%+.3f", value),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = when {
                    value > 0.3 -> Color(0xFF4CAF50)
                    value < -0.3 -> Color(0xFFE53935)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 상관관계 바
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val barWidth = abs(value).coerceIn(0.0, 1.0).toFloat()
            val barColor = if (value >= 0) Color(0xFF4CAF50) else Color(0xFFE53935)

            Box(
                modifier = Modifier
                    .fillMaxWidth(barWidth)
                    .fillMaxHeight()
                    .align(if (value >= 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor.copy(alpha = 0.7f))
            )
        }
    }
}

/**
 * Top 상관관계 카드
 */
@Composable
private fun TopCorrelationsCard(
    topPositive: List<IndicatorStockCorrelation>,
    topNegative: List<IndicatorStockCorrelation>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFA726)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "주요 상관관계",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (topPositive.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "강한 양의 상관관계",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4CAF50)
                )
                topPositive.take(3).forEach { correlation ->
                    Text(
                        "- ${correlation.description}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (topNegative.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "강한 음의 상관관계",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE53935)
                )
                topNegative.take(3).forEach { correlation ->
                    Text(
                        "- ${correlation.description}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 종목-지표 상관관계 AI 해석 카드
 */
@Composable
private fun StockIndicatorAIInterpretationCard(
    interpretation: AIStockIndicatorInterpretation
) {
    val signalType = interpretation.signal.toSignalType()
    val signalColor = when (signalType) {
        SignalType.STRONG_BUY -> Color(0xFF1B5E20)
        SignalType.BUY -> Color(0xFF4CAF50)
        SignalType.NEUTRAL -> Color(0xFF757575)
        SignalType.SELL -> Color(0xFFE53935)
        SignalType.STRONG_SELL -> Color(0xFFB71C1C)
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "AI 상관관계 분석",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "${interpretation.name} (${interpretation.ticker}) | ${interpretation.period}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 신호 표시
            SignalIndicator(
                signal = interpretation.signal,
                confidence = interpretation.confidence,
                upProbability = interpretation.upProbability,
                downProbability = interpretation.downProbability
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 시장 심리 영향
            if (interpretation.marketSentimentImpact.isNotBlank()) {
                Text(
                    "시장 심리 영향",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6750A4)
                )
                Text(
                    interpretation.marketSentimentImpact,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 자금 흐름 영향
            if (interpretation.fundFlowImpact.isNotBlank()) {
                Text(
                    "자금 흐름 영향",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF388E3C)
                )
                Text(
                    interpretation.fundFlowImpact,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ETF 수급 영향
            if (interpretation.etfFlowImpact.isNotBlank()) {
                Text(
                    "ETF 수급 영향",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE64A19)
                )
                Text(
                    interpretation.etfFlowImpact,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 핵심 상관관계
            if (interpretation.keyCorrelations.isNotEmpty()) {
                Text(
                    "핵심 상관관계",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                interpretation.keyCorrelations.forEach { correlation ->
                    Text(
                        "- $correlation",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 분석 근거
            Text(
                "분석 근거",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                interpretation.reasoning,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 권장사항
            Text(
                "권장사항",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                interpretation.recommendation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 위험도
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val riskColor = when (interpretation.riskLevel) {
                    "LOW" -> Color(0xFF4CAF50)
                    "HIGH" -> Color(0xFFE53935)
                    else -> Color(0xFFFFA726)
                }
                Text(
                    "위험도: ${interpretation.riskLevel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor
                )
            }
        }
    }
}

/**
 * 지표 타입 표시 이름 변환
 */
private fun getIndicatorDisplayName(indicatorType: String): String {
    return try {
        MarketIndicatorType.valueOf(indicatorType).displayName
    } catch (e: Exception) {
        indicatorType
    }
}

/**
 * 종목 지표 타입 표시 이름 변환
 */
private fun getMetricDisplayName(metricType: String): String {
    return try {
        StockMetricType.valueOf(metricType).displayName
    } catch (e: Exception) {
        metricType
    }
}

/**
 * 시계열 분석 기간 선택
 */
@Composable
private fun TimeSeriesPeriodSelector(
    period: Int,
    onPeriodChange: (Int) -> Unit
) {
    val periodOptions = listOf(7, 14, 30, 60, 90, 180, 365)

    Column {
        Text(
            "분석 기간: ${period}일",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            periodOptions.forEach { days ->
                FilterChip(
                    selected = period == days,
                    onClick = { onPeriodChange(days) },
                    label = {
                        Text(
                            when (days) {
                                7 -> "1주"
                                14 -> "2주"
                                30 -> "1개월"
                                60 -> "2개월"
                                90 -> "3개월"
                                180 -> "6개월"
                                365 -> "1년"
                                else -> "${days}일"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AnalysisMarketSelector(
    selectedMarket: String,
    onMarketSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("KOSPI", "KOSDAQ").forEach { market ->
            FilterChip(
                selected = market == selectedMarket,
                onClick = { onMarketSelect(market) },
                label = { Text(market) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AnalysisButtons(
    state: NewAIAnalysisState,
    isApiKeyConfigured: Boolean,
    hasCorrelationResult: Boolean,
    onRunCorrelation: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: () -> Unit
) {
    val isLoading = state is NewAIAnalysisState.AnalyzingCorrelation ||
            state is NewAIAnalysisState.AnalyzingFull ||
            state is NewAIAnalysisState.InterpretingWithAI

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 상관관계 분석만
        OutlinedButton(
            onClick = onRunCorrelation,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (state is NewAIAnalysisState.AnalyzingCorrelation) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Analytics, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("상관관계 분석 (로컬)")
        }

        // 전체 분석 (상관관계 + AI)
        Button(
            onClick = onRunFullAnalysis,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && isApiKeyConfigured
        ) {
            if (state is NewAIAnalysisState.AnalyzingFull) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Psychology, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("전체 분석 (상관관계 + AI)")
        }

        // AI 해석 추가
        if (hasCorrelationResult && isApiKeyConfigured) {
            TextButton(
                onClick = onInterpretWithAI,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (state is NewAIAnalysisState.InterpretingWithAI) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 해석 추가")
            }
        }
    }
}

@Composable
private fun CorrelationResultCard(
    result: CorrelationAnalysisResult,
    aiResult: com.etfmonitor.database.entities.AIAnalysisResult?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "상관관계 분석 결과",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${result.market} | ${result.analysisDate} | ${result.periodDays}일간",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 신호 표시
            SignalIndicator(
                signal = if (aiResult != null) aiResult.signal else result.signal,
                confidence = if (aiResult != null) aiResult.confidence else result.confidence,
                upProbability = if (aiResult != null) aiResult.upProbability else result.upProbability,
                downProbability = if (aiResult != null) aiResult.downProbability else result.downProbability
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 주요 상관관계
            Text(
                "주요 상관관계",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            CorrelationItem("ETF 순편입 vs 지수", result.etfNetFlowCorrelation)
            CorrelationItem("원화예금 변화 vs 지수", result.cashDepositCorrelation)
            result.fearGreedCorrelation?.let {
                CorrelationItem("Fear&Greed vs 지수", it)
            }
            result.oscillatorCorrelation?.let {
                CorrelationItem("Oscillator vs 지수", it)
            }
        }
    }
}

@Composable
private fun CorrelationItem(label: String, value: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            String.format("%+.3f", value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = when {
                value > 0.3 -> Color(0xFF4CAF50)
                value < -0.3 -> Color(0xFFE53935)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun SignalIndicator(
    signal: String,
    confidence: Double,
    upProbability: Double,
    downProbability: Double
) {
    val signalType = signal.toSignalType()
    val signalColor = when (signalType) {
        SignalType.STRONG_BUY -> Color(0xFF1B5E20)
        SignalType.BUY -> Color(0xFF4CAF50)
        SignalType.NEUTRAL -> Color(0xFF757575)
        SignalType.SELL -> Color(0xFFE53935)
        SignalType.STRONG_SELL -> Color(0xFFB71C1C)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = signalColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                signalType.toKorean(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = signalColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "신뢰도",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${String.format("%.0f", confidence * 100)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "상승 확률",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        "${String.format("%.0f", upProbability)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "하락 확률",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE53935)
                    )
                    Text(
                        "${String.format("%.0f", downProbability)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                }
            }
        }
    }
}

@Composable
private fun AIInterpretationCard(
    signal: String,
    confidence: Double,
    upProbability: Double,
    downProbability: Double,
    reasoning: String,
    recommendation: String,
    riskLevel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "AI 분석",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "분석 근거",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                reasoning,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "권장사항",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                recommendation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val riskColor = when (riskLevel) {
                    "LOW" -> Color(0xFF4CAF50)
                    "HIGH" -> Color(0xFFE53935)
                    else -> Color(0xFFFFA726)
                }
                Text(
                    "위험도: $riskLevel",
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor
                )
            }
        }
    }
}

@Composable
private fun ChatScreen(
    messages: List<AIChatMessage>,
    isSending: Boolean,
    onSendMessage: (String) -> Unit,
    state: NewAIAnalysisState
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 새 메시지가 오면 스크롤
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 메시지 목록
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { message ->
                ChatMessageItem(message = message)
            }

            // 로딩 표시
            if (isSending) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI가 답변 중...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 에러 표시
        if (state is NewAIAnalysisState.ChatError) {
            Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // 입력 영역
        Surface(
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지를 입력하세요") },
                    maxLines = 3,
                    enabled = !isSending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            keyboardController?.hide()
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            keyboardController?.hide()
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isSending
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "전송",
                        tint = if (inputText.isNotBlank() && !isSending)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(message: AIChatMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SessionItem(
    session: AIChatSession,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${session.market ?: "일반"} | ${session.messageCount}개 메시지",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("대화 삭제") },
            text = { Text("이 대화를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun AnalysisErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "닫기"
                )
            }
        }
    }
}
