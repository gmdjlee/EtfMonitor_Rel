package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.core.database.AIAnalysisDao
import com.etfmonitor.core.database.CorrelationAnalysisDao
import com.etfmonitor.core.database.entities.AIAnalysisResult as AIAnalysisEntity
import com.etfmonitor.core.database.entities.CorrelationAnalysisResult as CorrelationEntity
import com.etfmonitor.feature.analysis.data.mapper.toDomain
import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullAnalysis
import com.etfmonitor.feature.analysis.domain.repository.CorrelationAnalysisRepository
import com.etfmonitor.repository.CorrelationAnalysisRepository as LegacyCorrelationRepo
import com.etfmonitor.repository.FullAnalysisResult as LegacyFullResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 상관관계 분석 Repository 구현체
 * 기존 Repository를 래핑하여 Clean Architecture 인터페이스 제공
 */
@Singleton
class CorrelationAnalysisRepositoryImpl @Inject constructor(
    private val legacyRepository: LegacyCorrelationRepo,
    private val correlationAnalysisDao: CorrelationAnalysisDao,
    private val aiAnalysisDao: AIAnalysisDao
) : CorrelationAnalysisRepository {

    override suspend fun runCorrelationAnalysis(
        market: String,
        endDate: String,
        periodDays: Int
    ): Result<CorrelationAnalysis> = withContext(Dispatchers.IO) {
        legacyRepository.runCorrelationAnalysis(market, endDate, periodDays)
            .map { it.toDomain() }
    }

    override suspend fun runLatestCorrelationAnalysis(
        market: String,
        periodDays: Int
    ): Result<CorrelationAnalysis> = withContext(Dispatchers.IO) {
        legacyRepository.runLatestCorrelationAnalysis(market, periodDays)
            .map { it.toDomain() }
    }

    override suspend fun interpretWithAI(
        correlationResult: CorrelationAnalysis
    ): Result<AIAnalysis> = withContext(Dispatchers.IO) {
        // Domain -> Entity 변환
        val entity = correlationResult.toEntity()
        legacyRepository.interpretWithAI(entity)
            .map { it.toDomain() }
    }

    override suspend fun runFullAnalysis(
        market: String,
        endDate: String?,
        periodDays: Int
    ): Result<FullAnalysis> = withContext(Dispatchers.IO) {
        legacyRepository.runFullAnalysis(market, endDate, periodDays)
            .map { it.toDomain() }
    }

    override fun getCorrelationResults(market: String): Flow<List<CorrelationAnalysis>> {
        return correlationAnalysisDao.getAllByMarket(market)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getCorrelationResult(market: String, date: String): CorrelationAnalysis? =
        withContext(Dispatchers.IO) {
            correlationAnalysisDao.getByMarketAndDate(market, date)?.toDomain()
        }

    override suspend fun getLatestCorrelationResult(market: String): CorrelationAnalysis? =
        withContext(Dispatchers.IO) {
            correlationAnalysisDao.getLatestByMarket(market)?.toDomain()
        }

    override fun getAIAnalysisResults(market: String): Flow<List<AIAnalysis>> {
        return aiAnalysisDao.getAllByMarket(market)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getLatestAIResult(market: String): AIAnalysis? =
        withContext(Dispatchers.IO) {
            aiAnalysisDao.getLatestByMarket(market)?.toDomain()
        }

    // Domain -> Entity 변환 헬퍼
    private fun CorrelationAnalysis.toEntity(): CorrelationEntity = CorrelationEntity(
        id = id,
        market = market,
        analysisDate = analysisDate,
        periodDays = periodDays,
        etfNetFlowCorrelation = etfNetFlowCorrelation,
        etfNewStockCorrelation = etfNewStockCorrelation,
        etfRemovedStockCorrelation = etfRemovedStockCorrelation,
        etfIncreasedCorrelation = etfIncreasedCorrelation,
        etfDecreasedCorrelation = etfDecreasedCorrelation,
        cashDepositCorrelation = cashDepositCorrelation,
        marketDepositCorrelation = marketDepositCorrelation,
        creditBalanceCorrelation = creditBalanceCorrelation,
        fearGreedCorrelation = fearGreedCorrelation,
        fearGreedLeadCorrelation = fearGreedLeadCorrelation,
        oscillatorCorrelation = oscillatorCorrelation,
        oscillatorLeadCorrelation = oscillatorLeadCorrelation,
        compositeScore = compositeScore,
        signal = signal,
        confidence = confidence,
        upProbability = upProbability,
        downProbability = downProbability,
        analysisContext = analysisContext
    )

    private fun LegacyFullResult.toDomain(): FullAnalysis = FullAnalysis(
        correlationResult = correlationResult.toDomain(),
        aiResult = aiResult?.toDomain(),
        errorMessage = errorMessage
    )
}
