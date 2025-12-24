package com.etfmonitor.feature.analysis.domain.usecase

import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullAnalysis
import com.etfmonitor.feature.analysis.domain.repository.CorrelationAnalysisRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 상관관계 분석 실행 UseCase
 */
class RunCorrelationAnalysisUseCase @Inject constructor(
    private val repository: CorrelationAnalysisRepository
) {
    suspend operator fun invoke(
        market: String,
        endDate: String? = null,
        periodDays: Int = 30
    ): Result<CorrelationAnalysis> {
        return if (endDate != null) {
            repository.runCorrelationAnalysis(market, endDate, periodDays)
        } else {
            repository.runLatestCorrelationAnalysis(market, periodDays)
        }
    }
}

/**
 * 전체 분석 (상관관계 + AI) 실행 UseCase
 */
class RunFullAnalysisUseCase @Inject constructor(
    private val repository: CorrelationAnalysisRepository
) {
    suspend operator fun invoke(
        market: String,
        endDate: String? = null,
        periodDays: Int = 30
    ): Result<FullAnalysis> {
        return repository.runFullAnalysis(market, endDate, periodDays)
    }
}

/**
 * AI 해석 추가 UseCase
 */
class InterpretWithAIUseCase @Inject constructor(
    private val repository: CorrelationAnalysisRepository
) {
    suspend operator fun invoke(
        correlationResult: CorrelationAnalysis
    ): Result<AIAnalysis> {
        return repository.interpretWithAI(correlationResult)
    }
}

/**
 * 상관관계 분석 결과 조회 UseCase
 */
class GetCorrelationResultsUseCase @Inject constructor(
    private val repository: CorrelationAnalysisRepository
) {
    operator fun invoke(market: String): Flow<List<CorrelationAnalysis>> {
        return repository.getCorrelationResults(market)
    }
}

/**
 * 최신 상관관계 분석 결과 조회 UseCase
 */
class GetLatestCorrelationResultUseCase @Inject constructor(
    private val repository: CorrelationAnalysisRepository
) {
    suspend operator fun invoke(market: String): CorrelationAnalysis? {
        return repository.getLatestCorrelationResult(market)
    }
}

/**
 * 최신 AI 분석 결과 조회 UseCase
 */
class GetLatestAIResultUseCase @Inject constructor(
    private val repository: CorrelationAnalysisRepository
) {
    suspend operator fun invoke(market: String): AIAnalysis? {
        return repository.getLatestAIResult(market)
    }
}
