package com.etfmonitor.feature.analysis.domain.usecase

import com.etfmonitor.feature.analysis.domain.model.*
import com.etfmonitor.feature.analysis.domain.repository.AdvancedAnalysisRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 시총 가중 흐름 분석 UseCase
 */
class GetMarketCapFlowUseCase @Inject constructor(
    private val repository: AdvancedAnalysisRepository
) {
    suspend operator fun invoke(
        currentDate: String,
        previousDate: String,
        market: String = "ALL"
    ): MarketCapFlow {
        return repository.calculateMarketCapWeightedFlow(currentDate, previousDate, market)
    }
}


/**
 * 수급 Divergence 분석 UseCase
 */
class AnalyzeDivergenceUseCase @Inject constructor(
    private val repository: AdvancedAnalysisRepository
) {
    suspend operator fun invoke(
        date: String,
        market: String = "ALL"
    ): DivergenceAnalysis {
        return repository.analyzeSupplyDemandDivergence(date, market)
    }
}

/**
 * 유동성 분석 UseCase
 */
class GetLiquidityAnalysisUseCase @Inject constructor(
    private val repository: AdvancedAnalysisRepository
) {
    suspend operator fun invoke(date: String): LiquidityAnalysisData? {
        val latest = repository.getLatestLiquidityAnalysis()
        return latest ?: repository.calculateAndSaveLiquidityAnalysis(date)
    }
}

/**
 * 유동성 분석 강제 재계산 UseCase
 */
class CalculateLiquidityAnalysisUseCase @Inject constructor(
    private val repository: AdvancedAnalysisRepository
) {
    suspend operator fun invoke(date: String): LiquidityAnalysisData? {
        return repository.calculateAndSaveLiquidityAnalysis(date)
    }
}


/**
 * 섹터 분석 UseCase
 */
class GetSectorAnalysisUseCase @Inject constructor(
    private val repository: AdvancedAnalysisRepository
) {
    suspend operator fun invoke(
        currentDate: String,
        previousDate: String
    ): List<SectorAnalysisData> {
        val existing = repository.getSectorAnalysisByDate(currentDate)
        return if (existing.isEmpty()) {
            repository.calculateAndSaveSectorAnalysis(currentDate, previousDate)
        } else {
            existing
        }
    }
}

/**
 * 섹터 분석 강제 재계산 UseCase
 */
class CalculateSectorAnalysisUseCase @Inject constructor(
    private val repository: AdvancedAnalysisRepository
) {
    suspend operator fun invoke(
        currentDate: String,
        previousDate: String
    ): List<SectorAnalysisData> {
        return repository.calculateAndSaveSectorAnalysis(currentDate, previousDate)
    }
}

/**
 * 섹터 분석 조회 UseCase (Flow)
 */
class ObserveSectorAnalysisUseCase @Inject constructor(
    private val repository: AdvancedAnalysisRepository
) {
    operator fun invoke(date: String): Flow<List<SectorAnalysisData>> {
        return repository.observeSectorAnalysis(date)
    }
}

/**
 * 섹터 로테이션 감지 UseCase
 */
class DetectSectorRotationUseCase @Inject constructor(
    private val repository: AdvancedAnalysisRepository
) {
    suspend operator fun invoke(
        currentDate: String,
        previousDate: String
    ): List<SectorRotation> {
        return repository.detectSectorRotation(currentDate, previousDate)
    }
}

/**
 * ETF 상관관계 계산 UseCase
 */
class CalculateEtfCorrelationUseCase @Inject constructor(
    private val repository: AdvancedAnalysisRepository
) {
    suspend operator fun invoke(
        etf1Ticker: String,
        etf2Ticker: String,
        date: String
    ): EtfCorrelation? {
        return repository.calculateAndSaveEtfCorrelation(etf1Ticker, etf2Ticker, date)
    }
}

