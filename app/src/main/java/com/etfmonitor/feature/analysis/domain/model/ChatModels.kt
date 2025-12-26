package com.etfmonitor.feature.analysis.domain.model

/**
 * AI 채팅 세션 도메인 모델
 */
data class ChatSession(
    val id: String,
    val title: String,
    val market: String?,
    val analysisDate: String?,
    val contextData: String?,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * AI 채팅 메시지 도메인 모델
 */
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val aiProvider: String?,
    val aiModel: String?,
    val tokenCount: Int?,
    val timestamp: Long
)

/**
 * 메시지 역할
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * 분석 컨텍스트 (ChatSession에 포함되는 데이터)
 */
data class AnalysisContext(
    val currentIndex: Double,
    val indexChangeRate: Double,
    val etfSummary: EtfSummary?,
    val fearGreedValue: Double?,
    val oscillatorValue: Double?
)

/**
 * ETF 요약 정보
 */
data class EtfSummary(
    val newStocks: Int,
    val removedStocks: Int,
    val increasedStocks: Int,
    val decreasedStocks: Int,
    val cashDepositChange: Double
)
