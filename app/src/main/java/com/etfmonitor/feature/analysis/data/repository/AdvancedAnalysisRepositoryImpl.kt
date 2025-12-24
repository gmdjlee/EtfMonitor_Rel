package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.database.EtfCorrelationDao
import com.etfmonitor.database.LiquidityAnalysisDao
import com.etfmonitor.database.SectorAnalysisDao
import com.etfmonitor.feature.analysis.data.mapper.toDomain
import com.etfmonitor.feature.analysis.domain.model.*
import com.etfmonitor.feature.analysis.domain.repository.AdvancedAnalysisRepository
import com.etfmonitor.repository.AdvancedAnalysisRepository as LegacyAdvancedRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 고급 분석 Repository 구현체
 * 기존 Repository를 래핑하여 Clean Architecture 인터페이스 제공
 */
@Singleton
class AdvancedAnalysisRepositoryImpl @Inject constructor(
    private val legacyRepository: LegacyAdvancedRepo,
    private val liquidityAnalysisDao: LiquidityAnalysisDao,
    private val sectorAnalysisDao: SectorAnalysisDao,
    private val etfCorrelationDao: EtfCorrelationDao
) : AdvancedAnalysisRepository {

    // ==================== 1. 시총 가중 ETF 흐름 분석 ====================

    override suspend fun calculateMarketCapWeightedFlow(
        currentDate: String,
        previousDate: String,
        market: String
    ): MarketCapFlow = withContext(Dispatchers.IO) {
        legacyRepository.calculateMarketCapWeightedFlow(currentDate, previousDate, market)
            .toDomain()
    }

    override fun observeMarketCapWeightedFlowHistory(
        days: Int,
        market: String
    ): Flow<List<MarketCapFlow>> {
        return legacyRepository.observeMarketCapWeightedFlowHistory(days, market)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    // ==================== 2. 외국인/기관 수급 Divergence 분석 ====================

    override suspend fun analyzeSupplyDemandDivergence(
        date: String,
        market: String
    ): DivergenceAnalysis = withContext(Dispatchers.IO) {
        legacyRepository.analyzeSupplyDemandDivergence(date, market).toDomain()
    }

    // ==================== 3. 예탁금/시총 비율 분석 (유동성) ====================

    override suspend fun calculateAndSaveLiquidityAnalysis(date: String): LiquidityAnalysisData? =
        withContext(Dispatchers.IO) {
            legacyRepository.calculateAndSaveLiquidityAnalysis(date)?.toDomain()
        }

    override suspend fun getLatestLiquidityAnalysis(): LiquidityAnalysisData? =
        withContext(Dispatchers.IO) {
            legacyRepository.getLatestLiquidityAnalysis()?.toDomain()
        }

    override fun observeLiquidityHistory(days: Int): Flow<List<LiquidityAnalysisData>> {
        return liquidityAnalysisDao.observeRecentHistory(days)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun analyzeLiquidityTrend(days: Int): LiquidityTrendData? =
        withContext(Dispatchers.IO) {
            legacyRepository.analyzeLiquidityTrend(days)?.toDomain()
        }

    // ==================== 4. 섹터별 Fear & Greed 분석 ====================

    override suspend fun calculateAndSaveSectorAnalysis(
        currentDate: String,
        previousDate: String
    ): List<SectorAnalysisData> = withContext(Dispatchers.IO) {
        legacyRepository.calculateAndSaveSectorAnalysis(currentDate, previousDate)
            .map { it.toDomain() }
    }

    override suspend fun getSectorAnalysisByDate(date: String): List<SectorAnalysisData> =
        withContext(Dispatchers.IO) {
            sectorAnalysisDao.getByDate(date).map { it.toDomain() }
        }

    override fun observeSectorAnalysis(date: String): Flow<List<SectorAnalysisData>> {
        return sectorAnalysisDao.observeByDate(date)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun detectSectorRotation(
        currentDate: String,
        previousDate: String
    ): List<SectorRotation> = withContext(Dispatchers.IO) {
        legacyRepository.detectSectorRotation(currentDate, previousDate)
            .map { it.toDomain() }
    }

    // ==================== 5. ETF 간 상관관계 분석 ====================

    override suspend fun calculateAndSaveEtfCorrelation(
        etf1Ticker: String,
        etf2Ticker: String,
        date: String
    ): EtfCorrelation? = withContext(Dispatchers.IO) {
        legacyRepository.calculateAndSaveEtfCorrelation(etf1Ticker, etf2Ticker, date)?.toDomain()
    }

    override suspend fun calculateAllEtfCorrelations(date: String): List<EtfCorrelation> =
        withContext(Dispatchers.IO) {
            legacyRepository.calculateAllEtfCorrelations(date).map { it.toDomain() }
        }

    override suspend fun getHighOverlapEtfPairs(
        date: String,
        threshold: Double
    ): List<EtfCorrelation> = withContext(Dispatchers.IO) {
        etfCorrelationDao.getHighOverlapPairs(date, threshold).map { it.toDomain() }
    }

    override suspend fun analyzePortfolioDiversification(
        etfTickers: List<String>,
        date: String
    ): PortfolioDiversificationResult = withContext(Dispatchers.IO) {
        legacyRepository.analyzePortfolioDiversification(etfTickers, date).toDomain()
    }
}
