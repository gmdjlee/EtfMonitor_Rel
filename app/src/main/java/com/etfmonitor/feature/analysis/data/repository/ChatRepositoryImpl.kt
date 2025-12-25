package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.core.database.AIChatDao
import com.etfmonitor.core.database.entities.CorrelationAnalysisResult as CorrelationEntity
import com.etfmonitor.core.database.entities.AIAnalysisResult as AIAnalysisEntity
import com.etfmonitor.feature.analysis.data.mapper.toDomain
import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.ChatMessage
import com.etfmonitor.feature.analysis.domain.model.ChatSession
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.repository.ChatRepository
import com.etfmonitor.repository.AIChatRepository as LegacyChatRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 채팅 Repository 구현체
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val legacyRepository: LegacyChatRepo,
    private val chatDao: AIChatDao
) : ChatRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createSession(
        market: String,
        title: String
    ): ChatSession = withContext(Dispatchers.IO) {
        legacyRepository.createSession(market, title).toDomain()
    }

    override suspend fun createSessionWithAnalysis(
        correlationResult: CorrelationAnalysis,
        aiResult: AIAnalysis?
    ): ChatSession = withContext(Dispatchers.IO) {
        val correlationEntity = correlationResult.toEntity()
        val aiEntity = aiResult?.toEntity()
        legacyRepository.createSessionWithAnalysis(correlationEntity, aiEntity).toDomain()
    }

    override suspend fun getSession(sessionId: String): ChatSession? =
        withContext(Dispatchers.IO) {
            chatDao.getSessionById(sessionId)?.toDomain()
        }

    override fun getAllSessions(): Flow<List<ChatSession>> {
        return chatDao.getAllSessions()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getSessionsByMarket(market: String): Flow<List<ChatSession>> {
        return chatDao.getSessionsByMarket(market)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun sendMessage(sessionId: String, content: String): Result<ChatMessage> =
        withContext(Dispatchers.IO) {
            legacyRepository.sendMessage(sessionId, content)
                .map { it.toDomain() }
        }

    override fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesBySession(sessionId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        legacyRepository.deleteSession(sessionId)
    }

    override suspend fun askAboutAnalysis(
        sessionId: String,
        question: String
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        // 세션 컨텍스트를 활용하는 sendMessage 사용
        // (askAboutAnalysis는 correlationResultId가 필요하지만, 세션에 이미 컨텍스트가 저장되어 있음)
        legacyRepository.sendMessage(sessionId, question)
            .map { it.toDomain() }
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

    private fun AIAnalysis.toEntity(): AIAnalysisEntity = AIAnalysisEntity(
        id = id,
        market = market,
        analysisDate = analysisDate,
        correlationResultId = correlationResultId,
        aiProvider = aiProvider,
        aiModel = aiModel,
        signal = signal,
        confidence = confidence,
        upProbability = upProbability,
        downProbability = downProbability,
        riskLevel = riskLevel,
        reasoning = reasoning,
        keyFactors = json.encodeToString(keyFactors),
        recommendation = recommendation,
        alternativeScenarios = alternativeScenarios,
        promptUsed = "",
        rawResponse = reasoning,
        processingTimeMs = processingTimeMs
    )
}
