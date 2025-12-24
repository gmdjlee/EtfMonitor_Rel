package com.etfmonitor.feature.analysis.domain.model

/**
 * 고급 분석 대시보드 통합 데이터
 */
data class AdvancedDashboard(
    val date: String,
    val marketCapFlow: MarketCapFlow?,
    val divergenceSummary: DivergenceAnalysis?,
    val liquidityAnalysis: LiquidityAnalysisData?,
    val allSectorAnalyses: List<SectorAnalysisData>,
    val topGreedSectors: List<SectorAnalysisData>,
    val topFearSectors: List<SectorAnalysisData>,
    val sectorRotationSignals: List<SectorRotation>,
    val highOverlapEtfs: List<EtfCorrelation>,
    val overallSignal: OverallSignal,
    val dataAvailability: DataAvailability
)

/**
 * 종합 신호
 */
data class OverallSignal(
    val direction: SignalDirection,
    val strength: Double,  // 0.0 ~ 1.0
    val factors: List<String>
)

/**
 * 신호 방향
 */
enum class SignalDirection {
    STRONG_BUY,
    BUY,
    NEUTRAL,
    SELL,
    STRONG_SELL
}

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
    val prediction: String,
    val actualResult: String,
    val actualChangeRate: Double,
    val isCorrect: Boolean
)

/**
 * 종합 분석 정확도 데이터
 */
data class OverallAccuracy(
    val marketCapFlowAccuracy: PredictionAccuracy?,
    val liquidityAccuracy: PredictionAccuracy?,
    val overallSignalAccuracy: PredictionAccuracy?,
    val sectorAccuracyMap: Map<String, PredictionAccuracy>
)

/**
 * 시총 가중 흐름 히스토리 아이템
 */
data class MarketCapFlowHistory(
    val date: String,
    val netFlow: Double,
    val inflow: Double,
    val outflow: Double
)

/**
 * 분석 타입
 */
enum class AnalysisType {
    MARKET_CAP_FLOW,
    DIVERGENCE,
    LIQUIDITY,
    SECTOR,
    ETF_CORRELATION
}
