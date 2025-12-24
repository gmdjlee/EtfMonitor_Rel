package com.etfmonitor.feature.analysis.domain.usecase

import com.etfmonitor.feature.analysis.domain.model.FullStockIndicatorAnalysis
import com.etfmonitor.feature.analysis.domain.model.StockIndicatorCorrelation
import com.etfmonitor.feature.analysis.domain.model.StockIndicatorInterpretation
import com.etfmonitor.feature.analysis.domain.model.StockIndicatorRequest
import com.etfmonitor.feature.analysis.domain.repository.StockIndicatorAIHistoryItem
import com.etfmonitor.feature.analysis.domain.repository.StockIndicatorRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 종목 검색 UseCase
 */
class SearchStockForAnalysisUseCase @Inject constructor(
    private val repository: StockIndicatorRepository
) {
    suspend operator fun invoke(query: String): Pair<String, String>? {
        return repository.searchStock(query)
    }
}

/**
 * 종목-지표 상관관계 분석 UseCase (로컬 계산)
 */
class AnalyzeStockIndicatorCorrelationUseCase @Inject constructor(
    private val repository: StockIndicatorRepository
) {
    suspend operator fun invoke(
        request: StockIndicatorRequest
    ): Result<StockIndicatorCorrelation> {
        return repository.analyzeStockIndicatorCorrelations(request)
    }
}

/**
 * 종목-지표 전체 분석 (상관관계 + AI 해석) UseCase
 */
class RunFullStockIndicatorAnalysisUseCase @Inject constructor(
    private val repository: StockIndicatorRepository
) {
    suspend operator fun invoke(
        ticker: String,
        name: String,
        market: String,
        periodDays: Int = 30
    ): Result<FullStockIndicatorAnalysis> {
        return repository.runFullStockIndicatorCorrelationAnalysis(
            ticker, name, market, periodDays
        )
    }
}

/**
 * 종목-지표 AI 해석 추가 UseCase
 */
class InterpretStockIndicatorWithAIUseCase @Inject constructor(
    private val repository: StockIndicatorRepository
) {
    suspend operator fun invoke(
        correlationResult: StockIndicatorCorrelation
    ): Result<StockIndicatorInterpretation> {
        return repository.interpretStockIndicatorCorrelationsWithAI(correlationResult)
    }
}

/**
 * 종목별 AI 분석 히스토리 조회 UseCase
 */
class GetStockIndicatorHistoryUseCase @Inject constructor(
    private val repository: StockIndicatorRepository
) {
    operator fun invoke(ticker: String): Flow<List<StockIndicatorAIHistoryItem>> {
        return repository.getStockIndicatorAIHistory(ticker)
    }
}

/**
 * 전체 AI 분석 히스토리 조회 UseCase
 */
class GetAllStockIndicatorHistoryUseCase @Inject constructor(
    private val repository: StockIndicatorRepository
) {
    operator fun invoke(limit: Int = 50): Flow<List<StockIndicatorAIHistoryItem>> {
        return repository.getAllStockIndicatorAIHistory(limit)
    }
}

/**
 * 종목-지표 히스토리 삭제 UseCase
 */
class DeleteStockIndicatorHistoryUseCase @Inject constructor(
    private val repository: StockIndicatorRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteStockIndicatorAIHistory(id)
    }
}
