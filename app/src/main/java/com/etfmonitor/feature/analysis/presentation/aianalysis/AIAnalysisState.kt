package com.etfmonitor.feature.analysis.presentation.aianalysis

import com.etfmonitor.feature.analysis.domain.model.*

/**
 * AI 분석 화면 상태
 */
sealed class AIAnalysisState {
    object Idle : AIAnalysisState()

    // 상관관계 분석 진행 중
    object AnalyzingCorrelation : AIAnalysisState()
    object AnalyzingFull : AIAnalysisState()
    object InterpretingWithAI : AIAnalysisState()

    // 상관관계 분석 완료
    data class CorrelationComplete(val result: CorrelationAnalysis) : AIAnalysisState()
    data class FullAnalysisComplete(val result: FullAnalysis) : AIAnalysisState()
    data class AIInterpretationComplete(val result: AIAnalysis) : AIAnalysisState()

    // 종목-지표 상관관계 분석 진행 중
    object AnalyzingStockIndicatorCorrelation : AIAnalysisState()
    object AnalyzingStockIndicatorCorrelationFull : AIAnalysisState()
    object InterpretingStockIndicatorCorrelation : AIAnalysisState()

    // 종목-지표 상관관계 분석 완료
    data class StockIndicatorCorrelationComplete(val result: StockIndicatorCorrelation) : AIAnalysisState()
    data class StockIndicatorCorrelationAIComplete(val result: FullStockIndicatorAnalysis) : AIAnalysisState()

    // 채팅
    data class ChatActive(val session: ChatSession) : AIAnalysisState()
    data class ChatError(val message: String) : AIAnalysisState()

    // 에러
    data class Error(val message: String) : AIAnalysisState()
}
