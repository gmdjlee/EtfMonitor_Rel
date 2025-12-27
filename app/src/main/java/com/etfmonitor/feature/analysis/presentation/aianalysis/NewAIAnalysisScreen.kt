package com.etfmonitor.feature.analysis.presentation.aianalysis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.etfmonitor.core.database.entities.SearchHistory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.core.database.entities.Stock
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullStockIndicatorAnalysis

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
            selectedStock?.let { stock ->
                if (showFab) {
                    ExtendedFloatingActionButton(
                        onClick = { onNavigateToOscillator?.invoke(stock.first) },
                        icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                        text = { Text("차트 분석") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
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
                                CorrelationAnalysisContent(
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
                                StockIndicatorCorrelationContent(
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
                        items(chatSessions, key = { it.id }) { session ->
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
                        items(stockIndicatorAIHistory, key = { it.id }) { historyItem ->
                            StockIndicatorAIHistoryItemCard(
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
 * 상관관계 분석 화면 콘텐츠
 */
@Composable
private fun CorrelationAnalysisContent(
    state: NewAIAnalysisState,
    selectedMarket: String,
    isApiKeyConfigured: Boolean,
    analysisResult: FullAnalysis?,
    onMarketSelect: (String) -> Unit,
    onRunCorrelation: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: (CorrelationAnalysis) -> Unit,
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
 * 종목-지표 상관관계 분석 화면 콘텐츠
 * 시장은 종목 티커에 따라 자동 감지됨 (KOSPI: 0,1,2,3으로 시작, 나머지: KOSDAQ)
 */
@Composable
private fun StockIndicatorCorrelationContent(
    state: NewAIAnalysisState,
    analysisPeriod: Int,
    isApiKeyConfigured: Boolean,
    selectedStock: Pair<String, String>?,
    stockIndicatorCorrelationResult: FullStockIndicatorAnalysis?,
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
