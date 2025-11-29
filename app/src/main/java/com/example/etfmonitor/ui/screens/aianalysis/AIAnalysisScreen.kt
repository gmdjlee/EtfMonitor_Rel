package com.etfmonitor.ui.screens.aianalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.etfmonitor.ai.*
import com.etfmonitor.database.entities.StockPrediction
import com.etfmonitor.database.entities.TrainingResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAnalysisScreen(
    navController: NavHostController,
    viewModel: AIAnalysisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val isApiKeyConfigured by viewModel.isApiKeyConfigured.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 시장 분석") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "설정")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API 키 상태 카드
            APIKeyStatusCard(
                isConfigured = isApiKeyConfigured,
                onRefresh = { viewModel.refreshApiKeyStatus() },
                onTest = { viewModel.testApiConnection() },
                onNavigateToSettings = { navController.navigate("settings") }
            )

            // 시장 선택
            MarketSelector(
                selectedMarket = selectedMarket,
                onMarketSelected = { viewModel.selectMarket(it) }
            )

            // 분석 버튼들
            if (isApiKeyConfigured) {
                AnalysisButtons(
                    onAnalyze = { viewModel.analyzeLatestMarket() },
                    onQuickSignal = { viewModel.generateQuickSignal() },
                    onBacktest = { /* TODO: Show date picker */ },
                    onPrediction = { viewModel.runStockPrediction() }
                )
            } else {
                // API 키가 없어도 ML 예측은 가능
                PredictionOnlyButton(
                    onPrediction = { viewModel.runStockPrediction() }
                )
            }

            // 결과 표시
            when (state) {
                is AIAnalysisState.Loading -> {
                    LoadingCard("시장 분석 중...")
                }
                is AIAnalysisState.LoadingQuick -> {
                    LoadingCard("빠른 신호 생성 중...")
                }
                is AIAnalysisState.LoadingBacktest -> {
                    LoadingCard("백테스트 실행 중...")
                }
                is AIAnalysisState.LoadingPrediction -> {
                    LoadingCard("ML 모델 학습 및 예측 중...")
                }
                is AIAnalysisState.Success -> {
                    val response = (state as AIAnalysisState.Success).response
                    SignalResultCard(response.signal)
                }
                is AIAnalysisState.QuickSignal -> {
                    val signal = (state as AIAnalysisState.QuickSignal).signal
                    QuickSignalCard(signal)
                }
                is AIAnalysisState.BacktestComplete -> {
                    val result = (state as AIAnalysisState.BacktestComplete).result
                    BacktestResultCard(result)
                }
                is AIAnalysisState.PredictionComplete -> {
                    val predState = state as AIAnalysisState.PredictionComplete
                    PredictionResultCard(
                        predictions = predState.predictions,
                        trainingResult = predState.trainingResult,
                        totalAnalyzed = predState.totalAnalyzed,
                        predictedCount = predState.predictedCount
                    )
                }
                is AIAnalysisState.ApiTestSuccess -> {
                    SuccessCard("API 연결 성공!")
                }
                is AIAnalysisState.Error -> {
                    ErrorCard(
                        message = (state as AIAnalysisState.Error).message,
                        onDismiss = { viewModel.clearError() }
                    )
                }
                AIAnalysisState.Idle -> {
                    IdleCard()
                }
            }
        }
    }
}

@Composable
fun APIKeyStatusCard(
    isConfigured: Boolean,
    onRefresh: () -> Unit,
    onTest: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfigured)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isConfigured)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConfigured) "API 키 설정됨" else "API 키 미설정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "새로고침")
                }
            }

            if (!isConfigured) {
                Text(
                    text = "Claude API 키를 설정해야 AI 분석을 사용할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("설정하기")
                }
            } else {
                Button(
                    onClick = onTest,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("연결 테스트")
                }
            }
        }
    }
}

@Composable
fun MarketSelector(
    selectedMarket: String,
    onMarketSelected: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "시장 선택",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedMarket == "KOSPI",
                    onClick = { onMarketSelected("KOSPI") },
                    label = { Text("KOSPI") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedMarket == "KOSDAQ",
                    onClick = { onMarketSelected("KOSDAQ") },
                    label = { Text("KOSDAQ") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AnalysisButtons(
    onAnalyze: () -> Unit,
    onQuickSignal: () -> Unit,
    onBacktest: () -> Unit,
    onPrediction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "분석 실행",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Analytics, null)
                Spacer(Modifier.width(8.dp))
                Text("종합 분석 (최신 데이터)")
            }

            OutlinedButton(
                onClick = onQuickSignal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Speed, null)
                Spacer(Modifier.width(8.dp))
                Text("빠른 신호")
            }

            OutlinedButton(
                onClick = onPrediction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.TrendingUp, null)
                Spacer(Modifier.width(8.dp))
                Text("ML 주가 상승 예측")
            }

            /* Backtest 기능은 추후 구현
            OutlinedButton(
                onClick = onBacktest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.History, null)
                Spacer(Modifier.width(8.dp))
                Text("백테스트")
            }
            */
        }
    }
}

@Composable
fun PredictionOnlyButton(
    onPrediction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ML 예측 (API 키 불필요)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onPrediction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.TrendingUp, null)
                Spacer(Modifier.width(8.dp))
                Text("ML 주가 상승 예측")
            }

            Text(
                "ETF 구성 변화 데이터로 학습하여 주가 상승 가능성이 높은 종목을 예측합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SignalResultCard(signal: MarketSignal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (signal.signal) {
                SignalType.STRONG_BUY, SignalType.BUY ->
                    MaterialTheme.colorScheme.primaryContainer
                SignalType.STRONG_SELL, SignalType.SELL ->
                    MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 신호 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${signal.signal.toEmoji()} ${signal.signal.toKorean()}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = signal.date,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 신뢰도
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("신뢰도", fontWeight = FontWeight.Bold)
                    Text("${(signal.confidence * 100).toInt()}%")
                }
                LinearProgressIndicator(
                    progress = { signal.confidence.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 확률
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("상승 확률", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${signal.upProbability.toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("하락 확률", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${signal.downProbability.toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Divider()

            // 분석 이유
            Column {
                Text("분석 근거", fontWeight = FontWeight.Bold)
                Text(
                    signal.reasoning,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 주요 요인
            if (signal.keyFactors.isNotEmpty()) {
                Column {
                    Text("주요 요인", fontWeight = FontWeight.Bold)
                    signal.keyFactors.forEach { factor ->
                        Row {
                            Text("• ", style = MaterialTheme.typography.bodyMedium)
                            Text(factor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Divider()

            // 권장사항
            Column {
                Text("권장사항", fontWeight = FontWeight.Bold)
                Text(
                    signal.recommendation,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 위험 수준
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("위험 수준", fontWeight = FontWeight.Bold)
                Text(
                    signal.riskLevel.toKorean(),
                    color = when (signal.riskLevel) {
                        RiskLevel.LOW -> MaterialTheme.colorScheme.primary
                        RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
                        RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
fun QuickSignalCard(signal: MarketSignal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "빠른 신호",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${signal.signal.toEmoji()} ${signal.signal.toKorean()}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(signal.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Text(
                signal.reasoning,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun BacktestResultCard(result: BacktestResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "백테스트 결과",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("기간: ${result.period}", style = MaterialTheme.typography.bodySmall)

            Divider()

            // 성과 지표
            MetricRow("총 신호", "${result.totalSignals}개")
            MetricRow("정확도", "${String.format("%.1f", result.accuracy)}%")
            MetricRow("승률", "${String.format("%.1f", result.winRate)}%")
            MetricRow("평균 수익률", "${String.format("%+.2f", result.averageReturn)}%")
            MetricRow("최대 낙폭", "${String.format("%.2f", result.maxDrawdown)}%")
            result.sharpeRatio?.let {
                MetricRow("샤프 비율", String.format("%.2f", it))
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LoadingCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}

@Composable
fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "오류",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("확인")
            }
        }
    }
}

@Composable
fun SuccessCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(message, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun IdleCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "AI 시장 분석",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Claude AI를 활용하여 시장을 분석하고\n매수/매도 신호를 생성합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PredictionResultCard(
    predictions: List<StockPrediction>,
    trainingResult: TrainingResult?,
    totalAnalyzed: Int,
    predictedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ML 주가 상승 예측 결과",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 요약 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$totalAnalyzed",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("분석 종목", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$predictedCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("상승 예측", style = MaterialTheme.typography.bodySmall)
                }
            }

            // 학습 결과 (있을 경우)
            trainingResult?.let { result ->
                Divider()
                Text(
                    "모델 성능",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${String.format("%.1f", result.accuracy * 100)}%",
                            fontWeight = FontWeight.Bold
                        )
                        Text("정확도", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${String.format("%.1f", result.precision * 100)}%",
                            fontWeight = FontWeight.Bold
                        )
                        Text("정밀도", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${String.format("%.1f", result.recall * 100)}%",
                            fontWeight = FontWeight.Bold
                        )
                        Text("재현율", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 예측 목록
            if (predictions.isNotEmpty()) {
                Divider()
                Text(
                    "상승 예측 종목 (신뢰도 순)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                predictions.take(10).forEach { prediction ->
                    PredictionItem(prediction)
                }

                if (predictions.size > 10) {
                    Text(
                        "외 ${predictions.size - 10}개 종목",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PredictionItem(prediction: StockPrediction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                prediction.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    prediction.ticker,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when (prediction.status) {
                        "NEW" -> "신규편입"
                        "INCREASED" -> "비중증가"
                        "DECREASED" -> "비중감소"
                        else -> prediction.status
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (prediction.status) {
                        "NEW" -> MaterialTheme.colorScheme.primary
                        "INCREASED" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${String.format("%.1f", prediction.confidence * 100)}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "신뢰도",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
