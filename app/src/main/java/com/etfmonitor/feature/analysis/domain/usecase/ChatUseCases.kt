package com.etfmonitor.feature.analysis.domain.usecase

import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.ChatMessage
import com.etfmonitor.feature.analysis.domain.model.ChatSession
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 새 채팅 세션 생성 UseCase
 */
class CreateChatSessionUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(
        market: String,
        title: String = "새 대화"
    ): ChatSession {
        return repository.createSession(market, title)
    }
}

/**
 * 분석 결과로 채팅 세션 생성 UseCase
 */
class CreateChatWithAnalysisUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(
        correlationResult: CorrelationAnalysis,
        aiResult: AIAnalysis? = null
    ): ChatSession {
        return repository.createSessionWithAnalysis(correlationResult, aiResult)
    }
}

/**
 * 채팅 세션 조회 UseCase
 */
class GetChatSessionUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(sessionId: String): ChatSession? {
        return repository.getSession(sessionId)
    }
}

/**
 * 모든 채팅 세션 조회 UseCase (Flow)
 */
class GetAllChatSessionsUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    operator fun invoke(): Flow<List<ChatSession>> {
        return repository.getAllSessions()
    }
}

/**
 * 특정 시장의 채팅 세션 조회 UseCase (Flow)
 */
class GetChatSessionsByMarketUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    operator fun invoke(market: String): Flow<List<ChatSession>> {
        return repository.getSessionsByMarket(market)
    }
}

/**
 * 메시지 전송 UseCase
 */
class SendChatMessageUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        content: String
    ): Result<ChatMessage> {
        return repository.sendMessage(sessionId, content)
    }
}

/**
 * 채팅 메시지 조회 UseCase (Flow)
 */
class GetChatMessagesUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    operator fun invoke(sessionId: String): Flow<List<ChatMessage>> {
        return repository.getMessages(sessionId)
    }
}

/**
 * 채팅 세션 삭제 UseCase
 */
class DeleteChatSessionUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(sessionId: String) {
        repository.deleteSession(sessionId)
    }
}

/**
 * 분석 질문 UseCase
 */
class AskAboutAnalysisUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        question: String
    ): Result<ChatMessage> {
        return repository.askAboutAnalysis(sessionId, question)
    }
}
