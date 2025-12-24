package com.etfmonitor.feature.analysis.domain.repository

import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.ChatMessage
import com.etfmonitor.feature.analysis.domain.model.ChatSession
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import kotlinx.coroutines.flow.Flow

/**
 * AI 채팅 Repository 인터페이스
 */
interface ChatRepository {

    /**
     * 새 채팅 세션 생성
     */
    suspend fun createSession(
        market: String,
        title: String = "새 대화"
    ): ChatSession

    /**
     * 분석 결과 컨텍스트로 세션 생성
     */
    suspend fun createSessionWithAnalysis(
        correlationResult: CorrelationAnalysis,
        aiResult: AIAnalysis? = null
    ): ChatSession

    /**
     * 세션 조회
     */
    suspend fun getSession(sessionId: String): ChatSession?

    /**
     * 모든 세션 조회 (Flow)
     */
    fun getAllSessions(): Flow<List<ChatSession>>

    /**
     * 특정 시장의 세션 조회 (Flow)
     */
    fun getSessionsByMarket(market: String): Flow<List<ChatSession>>

    /**
     * 메시지 전송
     */
    suspend fun sendMessage(sessionId: String, content: String): Result<ChatMessage>

    /**
     * 세션의 메시지 조회 (Flow)
     */
    fun getMessages(sessionId: String): Flow<List<ChatMessage>>

    /**
     * 세션 삭제
     */
    suspend fun deleteSession(sessionId: String)

    /**
     * 분석에 대해 질문하기
     */
    suspend fun askAboutAnalysis(
        sessionId: String,
        question: String
    ): Result<ChatMessage>
}
