package com.etfmonitor.ui.screens.advanced

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.database.*
import com.etfmonitor.database.entities.*
import com.etfmonitor.repository.AdvancedAnalysisRepository
import com.etfmonitor.utils.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 예측 정확도 데이터
 */
data class PredictionAccuracy(
    val totalPredictions: Int,
    val correctPredictions: Int,
    val hitRate: Double,  // 0.0 ~ 1.0
    val details: List<PredictionDetail>
)

/**
 * 개별 예측 상세
 */
data class PredictionDetail(
    val date: String,
    val prediction: String,      // 예측 신호 (BUY, SELL, NEUTRAL 등)
    val actualResult: String,    // 실제 결과 (UP, DOWN, FLAT)
    val actualChangeRate: Double,  // 실제 변동률 %
    val isCorrect: Boolean
)

/**
 * 신호-결과 매칭 결과
 */
data class SignalPerformance(
    val signalType: String,
    val totalCount: Int,
    val avgNextDayReturn: Double,
    val avgNext3DayReturn: Double,
    val positiveCount: Int,
    val negativeCount: Int
)

/**
 * 종합 분석 정확도 데이터
 */
data class OverallAccuracyData(
    val marketCapFlowAccuracy: PredictionAccuracy?,
    val liquidityAccuracy: PredictionAccuracy?,
    val overallSignalAccuracy: PredictionAccuracy?,
    val sectorAccuracyMap: Map<String, PredictionAccuracy>
)

/**
 * 고급 분석 대시보드 상태
 */
sealed class AdvancedDashboardState {
    object Loading : AdvancedDashboardState()
    data class Success(val data: AdvancedDashboardData) : AdvancedDashboardState()
    data class Error(val message: String) : AdvancedDashboardState()
}

/**
 * 대시보드 통합 데이터
 */
data class AdvancedDashboardData(
    val date: String,
    val marketCapFlow: MarketCapWeightedFlow?,
    val divergenceSummary: MarketDivergenceSummary?,
    val liquidityAnalysis: LiquidityAnalysis?,
    val allSectorAnalyses: List<SectorAnalysis>,
    val topGreedSectors: List<SectorAnalysis>,
    val topFearSectors: List<SectorAnalysis>,
    val sectorRotationSignals: List<SectorRotationSignal>,
    val highOverlapEtfs: List<EtfCorrelationCache>,
    val overallSignal: OverallSignal,
    val dataAvailability: DataAvailability
)

/**
 * 데이터 가용성 상태
 */
data class DataAvailability(
    val holdingsData: DataSourceStatus,
    val stockAnalysisData: DataSourceStatus,
    val marketDepositData: DataSourceStatus,
    val fearGreedData: DataSourceStatus,
    val etfData: DataSourceStatus
)

/**
 * 개별 데이터 소스 상태
 */
data class DataSourceStatus(
    val available: Boolean,
    val count: Int = 0,
    val latestDate: String? = null,
    val message: String = ""
)

/**
 * 종합 신호
 */
data class OverallSignal(
    val direction: SignalDirection,
    val strength: Double,  // 0.0 ~ 1.0
    val factors: List<String>
)

enum class SignalDirection {
    STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL
}

@HiltViewModel
class AdvancedDashboardViewModel @Inject constructor(
    private val advancedRepository: AdvancedAnalysisRepository,
    private val etfDao: EtfDao,
    private val stockAnalysisDao: StockAnalysisDao,
    private val marketDepositDao: MarketDepositDao,
    private val fearGreedDao: FearGreedDao,
    private val liquidityAnalysisDao: LiquidityAnalysisDao,
    private val sectorAnalysisDao: SectorAnalysisDao,
    private val marketIndexDao: MarketIndexDao
) : ViewModel() {

    companion object {
        private val logger = AppLogger.getLogger("AdvancedDashboardVM")
        private const val HISTORY_DAYS = 30
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _state = MutableStateFlow<AdvancedDashboardState>(AdvancedDashboardState.Loading)
    val state: StateFlow<AdvancedDashboardState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // 개별 분석 결과 캐시
    private val _marketCapFlow = MutableStateFlow<MarketCapWeightedFlow?>(null)
    val marketCapFlow: StateFlow<MarketCapWeightedFlow?> = _marketCapFlow.asStateFlow()

    private val _divergenceSummary = MutableStateFlow<MarketDivergenceSummary?>(null)
    val divergenceSummary: StateFlow<MarketDivergenceSummary?> = _divergenceSummary.asStateFlow()

    private val _liquidityAnalysis = MutableStateFlow<LiquidityAnalysis?>(null)
    val liquidityAnalysis: StateFlow<LiquidityAnalysis?> = _liquidityAnalysis.asStateFlow()

    private val _sectorAnalyses = MutableStateFlow<List<SectorAnalysis>>(emptyList())
    val sectorAnalyses: StateFlow<List<SectorAnalysis>> = _sectorAnalyses.asStateFlow()

    // 히스토리 데이터
    private val _liquidityHistory = MutableStateFlow<List<LiquidityAnalysis>>(emptyList())
    val liquidityHistory: StateFlow<List<LiquidityAnalysis>> = _liquidityHistory.asStateFlow()

    private val _sectorHistory = MutableStateFlow<Map<String, List<SectorAnalysis>>>(emptyMap())
    val sectorHistory: StateFlow<Map<String, List<SectorAnalysis>>> = _sectorHistory.asStateFlow()

    private val _marketCapFlowHistory = MutableStateFlow<List<MarketCapFlowHistoryItem>>(emptyList())
    val marketCapFlowHistory: StateFlow<List<MarketCapFlowHistoryItem>> = _marketCapFlowHistory.asStateFlow()

    // 예측 정확도 데이터
    private val _accuracyData = MutableStateFlow<OverallAccuracyData?>(null)
    val accuracyData: StateFlow<OverallAccuracyData?> = _accuracyData.asStateFlow()

    private val _marketCapFlowAccuracy = MutableStateFlow<PredictionAccuracy?>(null)
    val marketCapFlowAccuracy: StateFlow<PredictionAccuracy?> = _marketCapFlowAccuracy.asStateFlow()

    private val _liquidityAccuracy = MutableStateFlow<PredictionAccuracy?>(null)
    val liquidityAccuracy: StateFlow<PredictionAccuracy?> = _liquidityAccuracy.asStateFlow()

    private val _sectorAccuracy = MutableStateFlow<Map<String, PredictionAccuracy>>(emptyMap())
    val sectorAccuracy: StateFlow<Map<String, PredictionAccuracy>> = _sectorAccuracy.asStateFlow()

    // ETF 상관관계 계산 상태
    private val _isCalculatingCorrelation = MutableStateFlow(false)
    val isCalculatingCorrelation: StateFlow<Boolean> = _isCalculatingCorrelation.asStateFlow()

    init {
        loadDashboard()
        loadHistoryData()
        loadAccuracyData()
    }

    /**
     * 히스토리 데이터 로드
     */
    private fun loadHistoryData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 유동성 히스토리 로드
                val liquidityHistoryData = liquidityAnalysisDao.getRecentHistory(HISTORY_DAYS)
                _liquidityHistory.value = liquidityHistoryData
                logger.d("Loaded ${liquidityHistoryData.size} liquidity history records")

                // 섹터별 히스토리 로드
                val allSectors = sectorAnalysisDao.getAllSectors()
                val sectorHistoryMap = mutableMapOf<String, List<SectorAnalysis>>()
                for (sector in allSectors) {
                    val history = sectorAnalysisDao.getBySector(sector, HISTORY_DAYS)
                    if (history.isNotEmpty()) {
                        sectorHistoryMap[sector] = history
                    }
                }
                _sectorHistory.value = sectorHistoryMap
                logger.d("Loaded sector history for ${sectorHistoryMap.size} sectors")

                // 시총가중 흐름 히스토리 계산
                loadMarketCapFlowHistory()
            } catch (e: Exception) {
                logger.e("Error loading history data", e)
            }
        }
    }

    /**
     * 시총 가중 흐름 히스토리 로드
     */
    private suspend fun loadMarketCapFlowHistory() {
        try {
            val dates = etfDao.getAllDistinctDates(HISTORY_DAYS + 1)
            if (dates.size < 2) return

            val historyItems = mutableListOf<MarketCapFlowHistoryItem>()
            for (i in 0 until minOf(dates.size - 1, HISTORY_DAYS)) {
                val currentDate = dates[i]
                val previousDate = dates[i + 1]

                val flow = advancedRepository.calculateMarketCapWeightedFlow(currentDate, previousDate)
                historyItems.add(
                    MarketCapFlowHistoryItem(
                        date = currentDate,
                        netFlow = flow.netFlow.toDouble(),
                        inflow = flow.totalInflow.toDouble(),
                        outflow = flow.totalOutflow.toDouble()
                    )
                )
            }
            _marketCapFlowHistory.value = historyItems.reversed()  // 오래된 순으로 정렬
            logger.d("Loaded ${historyItems.size} market cap flow history records")
        } catch (e: Exception) {
            logger.e("Error loading market cap flow history", e)
        }
    }

    /**
     * 예측 정확도 데이터 로드
     */
    private fun loadAccuracyData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 시장 지수 데이터 로드 (KOSPI)
                val marketIndexes = marketIndexDao.getByMarketAndDateRangeSuspend(
                    "KOSPI",
                    LocalDate.now().minusDays(HISTORY_DAYS.toLong() + 5).format(dateFormatter),
                    LocalDate.now().format(dateFormatter)
                ).sortedBy { it.date }

                if (marketIndexes.size < 2) {
                    logger.w("Insufficient market index data for accuracy calculation")
                    return@launch
                }

                // 시총 가중 흐름 정확도 계산
                calculateMarketCapFlowAccuracy(marketIndexes)

                // 유동성 신호 정확도 계산
                calculateLiquidityAccuracy(marketIndexes)

                logger.d("Accuracy data loaded successfully")
            } catch (e: Exception) {
                logger.e("Error loading accuracy data", e)
            }
        }
    }

    /**
     * 시총 가중 흐름 예측 정확도 계산
     *
     * 예측 규칙:
     * - 순유입(netFlow > 0) -> 다음날 시장 상승 예측
     * - 순유출(netFlow < 0) -> 다음날 시장 하락 예측
     */
    private suspend fun calculateMarketCapFlowAccuracy(marketIndexes: List<com.etfmonitor.database.entities.MarketIndex>) {
        try {
            val flowHistory = _marketCapFlowHistory.value
            if (flowHistory.isEmpty()) return

            val details = mutableListOf<PredictionDetail>()
            val indexByDate = marketIndexes.associateBy { it.date }

            for (flow in flowHistory) {
                val predictionDate = flow.date
                // 다음 거래일 찾기
                val nextDayIndex = findNextTradingDay(predictionDate, marketIndexes)
                    ?: continue

                val actualChangeRate = nextDayIndex.changeRate
                val prediction = when {
                    flow.netFlow > 100 -> "BUY"
                    flow.netFlow < -100 -> "SELL"
                    else -> "NEUTRAL"
                }
                val actualResult = when {
                    actualChangeRate > 0.3 -> "UP"
                    actualChangeRate < -0.3 -> "DOWN"
                    else -> "FLAT"
                }

                // 정확도 판정: 예측과 결과 방향이 일치하면 정확
                val isCorrect = when (prediction) {
                    "BUY" -> actualResult == "UP" || actualResult == "FLAT"
                    "SELL" -> actualResult == "DOWN" || actualResult == "FLAT"
                    "NEUTRAL" -> actualResult == "FLAT"
                    else -> false
                }

                details.add(
                    PredictionDetail(
                        date = predictionDate,
                        prediction = prediction,
                        actualResult = actualResult,
                        actualChangeRate = actualChangeRate,
                        isCorrect = isCorrect
                    )
                )
            }

            if (details.isNotEmpty()) {
                val correctCount = details.count { it.isCorrect }
                _marketCapFlowAccuracy.value = PredictionAccuracy(
                    totalPredictions = details.size,
                    correctPredictions = correctCount,
                    hitRate = correctCount.toDouble() / details.size,
                    details = details.sortedByDescending { it.date }
                )
                logger.d("Market cap flow accuracy: ${correctCount}/${details.size} (${String.format("%.1f", correctCount.toDouble() / details.size * 100)}%)")
            }
        } catch (e: Exception) {
            logger.e("Error calculating market cap flow accuracy", e)
        }
    }

    /**
     * 유동성 신호 예측 정확도 계산
     *
     * 예측 규칙:
     * - BULLISH_LIQUIDITY -> 시장 상승 예측
     * - BEARISH_LEVERAGE -> 시장 하락 예측
     */
    private suspend fun calculateLiquidityAccuracy(marketIndexes: List<com.etfmonitor.database.entities.MarketIndex>) {
        try {
            val liquidityHistory = _liquidityHistory.value
            if (liquidityHistory.isEmpty()) return

            val details = mutableListOf<PredictionDetail>()

            for (liquidity in liquidityHistory) {
                val predictionDate = liquidity.date
                // 다음 거래일 찾기
                val nextDayIndex = findNextTradingDay(predictionDate, marketIndexes)
                    ?: continue

                val actualChangeRate = nextDayIndex.changeRate
                val signal = try { LiquiditySignal.valueOf(liquidity.signal) } catch (e: Exception) { LiquiditySignal.NEUTRAL }

                val prediction = when (signal) {
                    LiquiditySignal.BULLISH_LIQUIDITY -> "BUY"
                    LiquiditySignal.BEARISH_LEVERAGE -> "SELL"
                    else -> "NEUTRAL"
                }
                val actualResult = when {
                    actualChangeRate > 0.3 -> "UP"
                    actualChangeRate < -0.3 -> "DOWN"
                    else -> "FLAT"
                }

                val isCorrect = when (prediction) {
                    "BUY" -> actualResult == "UP" || actualResult == "FLAT"
                    "SELL" -> actualResult == "DOWN" || actualResult == "FLAT"
                    "NEUTRAL" -> true  // 중립은 항상 정확으로 처리
                    else -> false
                }

                details.add(
                    PredictionDetail(
                        date = predictionDate,
                        prediction = prediction,
                        actualResult = actualResult,
                        actualChangeRate = actualChangeRate,
                        isCorrect = isCorrect
                    )
                )
            }

            if (details.isNotEmpty()) {
                val correctCount = details.count { it.isCorrect }
                _liquidityAccuracy.value = PredictionAccuracy(
                    totalPredictions = details.size,
                    correctPredictions = correctCount,
                    hitRate = correctCount.toDouble() / details.size,
                    details = details.sortedByDescending { it.date }
                )
                logger.d("Liquidity accuracy: ${correctCount}/${details.size} (${String.format("%.1f", correctCount.toDouble() / details.size * 100)}%)")
            }
        } catch (e: Exception) {
            logger.e("Error calculating liquidity accuracy", e)
        }
    }

    /**
     * 다음 거래일 찾기
     */
    private fun findNextTradingDay(
        date: String,
        marketIndexes: List<com.etfmonitor.database.entities.MarketIndex>
    ): com.etfmonitor.database.entities.MarketIndex? {
        val sortedIndexes = marketIndexes.sortedBy { it.date }
        val currentIndex = sortedIndexes.indexOfFirst { it.date == date }
        return if (currentIndex >= 0 && currentIndex < sortedIndexes.size - 1) {
            sortedIndexes[currentIndex + 1]
        } else {
            null
        }
    }

    /**
     * 대시보드 데이터 로드
     */
    fun loadDashboard() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = AdvancedDashboardState.Loading

            try {
                // 1. 데이터 가용성 체크
                val dataAvailability = checkDataAvailability()
                logger.d("Data availability checked: holdings=${dataAvailability.holdingsData.available}, stockAnalysis=${dataAvailability.stockAnalysisData.available}")

                // 날짜 설정
                val dates = etfDao.getAllDistinctDates()
                logger.d("Available dates count: ${dates.size}")
                if (dates.size < 2) {
                    logger.w("Insufficient date data: ${dates.size}")
                    _state.value = AdvancedDashboardState.Error("데이터가 부족합니다. ETF 데이터를 먼저 수집해 주세요.")
                    return@launch
                }

                val currentDate = dates.first()
                val previousDate = dates[1]
                logger.d("Analyzing dates: current=$currentDate, previous=$previousDate")

                // 병렬로 데이터 로드
                logger.d("Starting data analysis...")
                val marketCapFlow = advancedRepository.calculateMarketCapWeightedFlow(currentDate, previousDate)
                logger.d("Market cap flow: netFlow=${marketCapFlow.netFlow}")

                val divergenceSummary = advancedRepository.analyzeSupplyDemandDivergence(currentDate)
                logger.d("Divergence: total=${divergenceSummary.foreignBullishCount + divergenceSummary.institutionBullishCount + divergenceSummary.alignedBullishCount + divergenceSummary.alignedBearishCount + divergenceSummary.neutralCount}")

                val liquidityAnalysis = advancedRepository.getLatestLiquidityAnalysis()
                    ?: advancedRepository.calculateAndSaveLiquidityAnalysis(currentDate)
                logger.d("Liquidity analysis: ${liquidityAnalysis?.signal ?: "null"}")

                // 섹터 분석
                var sectorAnalyses = advancedRepository.getSectorAnalysisByDate(currentDate)
                if (sectorAnalyses.isEmpty()) {
                    sectorAnalyses = advancedRepository.calculateAndSaveSectorAnalysis(currentDate, previousDate)
                }

                val topGreed = sectorAnalyses.filter { it.fearGreedValue > 0.6 }.take(3)
                val topFear = sectorAnalyses.filter { it.fearGreedValue < 0.4 }.take(3)

                // 섹터 로테이션
                val rotationSignals = advancedRepository.detectSectorRotation(currentDate, previousDate)

                // ETF 상관관계
                val highOverlap = advancedRepository.getHighOverlapEtfPairs(currentDate)

                // 종합 신호 계산
                val overallSignal = calculateOverallSignal(marketCapFlow, divergenceSummary, liquidityAnalysis, sectorAnalyses)

                // 캐시 업데이트
                _marketCapFlow.value = marketCapFlow
                _divergenceSummary.value = divergenceSummary
                _liquidityAnalysis.value = liquidityAnalysis
                _sectorAnalyses.value = sectorAnalyses

                _state.value = AdvancedDashboardState.Success(
                    AdvancedDashboardData(
                        date = currentDate,
                        marketCapFlow = marketCapFlow,
                        divergenceSummary = divergenceSummary,
                        liquidityAnalysis = liquidityAnalysis,
                        allSectorAnalyses = sectorAnalyses,
                        topGreedSectors = topGreed,
                        topFearSectors = topFear,
                        sectorRotationSignals = rotationSignals,
                        highOverlapEtfs = highOverlap,
                        overallSignal = overallSignal,
                        dataAvailability = dataAvailability
                    )
                )
            } catch (e: Exception) {
                logger.e("Error loading dashboard", e)
                _state.value = AdvancedDashboardState.Error("데이터 로드 실패: ${e.message}")
            }
        }
    }

    /**
     * 데이터 가용성 체크
     */
    private suspend fun checkDataAvailability(): DataAvailability {
        // Holdings 데이터 체크
        val holdingCount = etfDao.getTotalHoldingCount().toInt()
        val holdingDates = etfDao.getAllDistinctDates(10)
        val holdingsStatus = DataSourceStatus(
            available = holdingCount > 0,
            count = holdingCount,
            latestDate = holdingDates.firstOrNull(),
            message = if (holdingCount > 0) "$holdingCount 건, ${holdingDates.size}일 데이터" else "ETF 보유종목 데이터 없음"
        )

        // Stock Analysis 데이터 체크
        val stockAnalysisCount = stockAnalysisDao.getCount()
        val stockAnalysisStatus = DataSourceStatus(
            available = stockAnalysisCount > 0,
            count = stockAnalysisCount,
            latestDate = null,
            message = if (stockAnalysisCount > 0) "$stockAnalysisCount 종목 분석 데이터" else "종목 수급 데이터 없음 (수급 분석 필요)"
        )

        // Market Deposit 데이터 체크
        val latestDeposit = marketDepositDao.getLatestDeposit()
        val marketDepositStatus = DataSourceStatus(
            available = latestDeposit != null,
            count = if (latestDeposit != null) 1 else 0,
            latestDate = latestDeposit?.date,
            message = if (latestDeposit != null) "최신: ${latestDeposit.date}" else "예탁금 데이터 없음"
        )

        // Fear & Greed 데이터 체크
        val latestFearGreedDate = fearGreedDao.getLatestDate("KOSPI")
        val fearGreedStatus = DataSourceStatus(
            available = latestFearGreedDate != null,
            count = 0,
            latestDate = latestFearGreedDate,
            message = if (latestFearGreedDate != null) "최신: $latestFearGreedDate" else "Fear & Greed 데이터 없음"
        )

        // ETF 데이터 체크
        val etfCount = etfDao.getEtfCount()
        val etfStatus = DataSourceStatus(
            available = etfCount > 0,
            count = etfCount,
            latestDate = null,
            message = if (etfCount > 0) "$etfCount 개 ETF" else "ETF 데이터 없음"
        )

        return DataAvailability(
            holdingsData = holdingsStatus,
            stockAnalysisData = stockAnalysisStatus,
            marketDepositData = marketDepositStatus,
            fearGreedData = fearGreedStatus,
            etfData = etfStatus
        )
    }

    /**
     * 새로고침 (캐시 사용)
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadDashboard()
            loadHistoryData()
            loadAccuracyData()
            _isRefreshing.value = false
        }
    }

    /**
     * 강제 재계산 (캐시 무시)
     * 데이터 수집 기간을 늘린 후 사용
     */
    fun forceRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            _state.value = AdvancedDashboardState.Loading

            try {
                val dates = etfDao.getAllDistinctDates()
                if (dates.size < 2) {
                    _state.value = AdvancedDashboardState.Error("데이터가 부족합니다. ETF 데이터를 먼저 수집해 주세요.")
                    _isRefreshing.value = false
                    return@launch
                }

                val currentDate = dates.first()
                val previousDate = dates[1]

                // 모든 분석 강제 재계산
                logger.d("Force recalculating all analyses for $currentDate")

                // 유동성 분석 재계산
                val liquidityAnalysis = advancedRepository.calculateAndSaveLiquidityAnalysis(currentDate)
                _liquidityAnalysis.value = liquidityAnalysis
                logger.d("Liquidity analysis recalculated")

                // 섹터 분석 재계산
                val sectorAnalyses = advancedRepository.calculateAndSaveSectorAnalysis(currentDate, previousDate)
                _sectorAnalyses.value = sectorAnalyses
                logger.d("Sector analysis recalculated: ${sectorAnalyses.size} sectors")

                // 히스토리 데이터 새로고침
                loadHistoryData()
                loadAccuracyData()

                // 대시보드 새로고침
                loadDashboard()

                logger.d("Force refresh completed")
            } catch (e: Exception) {
                logger.e("Force refresh failed", e)
                _state.value = AdvancedDashboardState.Error("재계산 실패: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * 특정 분석 강제 재계산
     */
    fun recalculateAnalysis(type: AnalysisType) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dates = etfDao.getAllDistinctDates()
                if (dates.size < 2) return@launch

                val currentDate = dates.first()
                val previousDate = dates[1]

                when (type) {
                    AnalysisType.MARKET_CAP_FLOW -> {
                        val result = advancedRepository.calculateMarketCapWeightedFlow(currentDate, previousDate)
                        _marketCapFlow.value = result
                    }
                    AnalysisType.DIVERGENCE -> {
                        val result = advancedRepository.analyzeSupplyDemandDivergence(currentDate)
                        _divergenceSummary.value = result
                    }
                    AnalysisType.LIQUIDITY -> {
                        val result = advancedRepository.calculateAndSaveLiquidityAnalysis(currentDate)
                        _liquidityAnalysis.value = result
                    }
                    AnalysisType.SECTOR -> {
                        val result = advancedRepository.calculateAndSaveSectorAnalysis(currentDate, previousDate)
                        _sectorAnalyses.value = result
                    }
                    AnalysisType.ETF_CORRELATION -> {
                        advancedRepository.calculateAllEtfCorrelations(currentDate)
                    }
                }

                // 대시보드 갱신
                loadDashboard()
            } catch (e: Exception) {
                logger.e("Error recalculating analysis", e)
            }
        }
    }

    /**
     * ETF 상관관계 계산 (UI에서 직접 호출용)
     */
    fun calculateEtfCorrelation() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isCalculatingCorrelation.value = true

                val dates = etfDao.getAllDistinctDates()
                if (dates.size < 2) {
                    logger.w("Insufficient date data for ETF correlation")
                    return@launch
                }

                val currentDate = dates.first()
                logger.d("Calculating ETF correlations for date: $currentDate")

                val results = advancedRepository.calculateAllEtfCorrelations(currentDate)
                logger.d("ETF correlation calculation returned ${results.size} results")

                // 대시보드 갱신
                loadDashboard()
            } catch (e: Exception) {
                logger.e("Error calculating ETF correlation", e)
            } finally {
                _isCalculatingCorrelation.value = false
            }
        }
    }

    /**
     * 종합 신호 계산
     */
    private fun calculateOverallSignal(
        flow: MarketCapWeightedFlow?,
        divergence: MarketDivergenceSummary?,
        liquidity: LiquidityAnalysis?,
        sectors: List<SectorAnalysis>
    ): OverallSignal {
        val factors = mutableListOf<String>()
        var score = 0.0
        var count = 0

        // 1. 시총 가중 흐름 신호 (40%)
        flow?.let {
            val flowScore = when {
                it.netFlow > 1000 -> 1.0
                it.netFlow > 500 -> 0.75
                it.netFlow > 0 -> 0.6
                it.netFlow > -500 -> 0.4
                it.netFlow > -1000 -> 0.25
                else -> 0.0
            }
            score += flowScore * 0.4
            count++

            if (it.netFlow > 500) factors.add("시총흐름(+)")
            else if (it.netFlow < -500) factors.add("시총흐름(-)")
        }

        // 2. 수급 Divergence 신호 (25%)
        divergence?.let {
            val divergenceScore = when (it.marketSentiment) {
                MarketSentimentType.CONSENSUS_BULLISH -> 0.9
                MarketSentimentType.STRONG_FOREIGN_LED -> 0.8
                MarketSentimentType.STRONG_INSTITUTION_LED -> 0.7
                MarketSentimentType.MIXED -> 0.5
                MarketSentimentType.CONSENSUS_BEARISH -> 0.2
            }
            score += divergenceScore * 0.25
            count++

            when (it.marketSentiment) {
                MarketSentimentType.CONSENSUS_BULLISH -> factors.add("컨센서스상승")
                MarketSentimentType.STRONG_FOREIGN_LED -> factors.add("외국인주도")
                MarketSentimentType.STRONG_INSTITUTION_LED -> factors.add("기관주도")
                MarketSentimentType.CONSENSUS_BEARISH -> factors.add("컨센서스하락")
                else -> {}
            }
        }

        // 3. 유동성 신호 (20%)
        liquidity?.let {
            val liquidityScore = when (LiquiditySignal.valueOf(it.signal)) {
                LiquiditySignal.BULLISH_LIQUIDITY -> 0.9
                LiquiditySignal.NEUTRAL -> 0.5
                LiquiditySignal.DELEVERAGING -> 0.4
                LiquiditySignal.BEARISH_LEVERAGE -> 0.2
            }
            score += liquidityScore * 0.2
            count++

            if (it.signal == LiquiditySignal.BULLISH_LIQUIDITY.name) factors.add("유동성(+)")
            else if (it.signal == LiquiditySignal.BEARISH_LEVERAGE.name) factors.add("레버리지위험")
        }

        // 4. 섹터 심리 신호 (15%)
        if (sectors.isNotEmpty()) {
            val avgFearGreed = sectors.map { it.fearGreedValue }.average()
            val sectorScore = avgFearGreed.coerceIn(0.0, 1.0)
            score += sectorScore * 0.15
            count++

            val greedSectors = sectors.filter { it.fearGreedValue > 0.7 }
            if (greedSectors.isNotEmpty()) {
                factors.add("${greedSectors.first().sectorName}탐욕")
            }
        }

        // 최종 점수 정규화
        val finalScore = if (count > 0) score / (count * 0.25 + 0.4 + 0.2 + 0.15) * count else 0.5

        val direction = when {
            finalScore >= 0.8 -> SignalDirection.STRONG_BUY
            finalScore >= 0.6 -> SignalDirection.BUY
            finalScore >= 0.4 -> SignalDirection.NEUTRAL
            finalScore >= 0.2 -> SignalDirection.SELL
            else -> SignalDirection.STRONG_SELL
        }

        return OverallSignal(
            direction = direction,
            strength = finalScore,
            factors = factors
        )
    }
}

enum class AnalysisType {
    MARKET_CAP_FLOW,
    DIVERGENCE,
    LIQUIDITY,
    SECTOR,
    ETF_CORRELATION
}

/**
 * 분석에 필요한 데이터 소스별 요구사항
 */
object AnalysisDataRequirements {
    val MARKET_CAP_FLOW = listOf("holdings")
    val DIVERGENCE = listOf("holdings", "stock_analysis")
    val LIQUIDITY = listOf("market_deposits", "stock_analysis")
    val SECTOR = listOf("holdings", "fear_greed")
    val ETF_CORRELATION = listOf("holdings", "etfs")
}

/**
 * 시총 가중 흐름 히스토리 아이템
 */
data class MarketCapFlowHistoryItem(
    val date: String,
    val netFlow: Double,
    val inflow: Double,
    val outflow: Double
)
