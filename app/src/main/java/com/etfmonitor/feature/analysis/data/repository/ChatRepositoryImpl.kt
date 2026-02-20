package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.core.database.AIChatDao
import com.etfmonitor.core.database.AIAnalysisDao
import com.etfmonitor.core.database.CorrelationAnalysisDao
import com.etfmonitor.core.database.entities.AIChatMessage as ChatMessageEntity
import com.etfmonitor.core.database.entities.AIChatSession as ChatSessionEntity
import com.etfmonitor.core.database.entities.CorrelationAnalysisResult as CorrelationEntity
import com.etfmonitor.core.database.entities.AIAnalysisResult as AIAnalysisEntity
import com.etfmonitor.core.network.ai.AIApiClientFactory
import com.etfmonitor.core.network.ai.ChatMessage as AIChatMessage
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.feature.analysis.data.mapper.toDomain
import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.ChatMessage
import com.etfmonitor.feature.analysis.domain.model.ChatSession
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 채팅 Repository 구현체
 * 분석 데이터 기반 대화형 Q&A 기능
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: AIChatDao,
    private val aiAnalysisDao: AIAnalysisDao,
    private val correlationAnalysisDao: CorrelationAnalysisDao,
    private val aiApiClientFactory: AIApiClientFactory
) : ChatRepository {

    companion object {
        private val logger = AppLogger.getLogger("ChatRepoImpl")
        private const val MAX_CONTEXT_MESSAGES = 10
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    override suspend fun createSession(
        market: String,
        title: String
    ): ChatSession = withContext(Dispatchers.IO) {
        val session = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            market = market,
            analysisDate = null,
            contextData = null
        )
        chatDao.insertSession(session)
        logger.d("Created new chat session: ${session.id}")
        session.toDomain()
    }

    override suspend fun createSessionWithAnalysis(
        correlationResult: CorrelationAnalysis,
        aiResult: AIAnalysis?
    ): ChatSession = withContext(Dispatchers.IO) {
        val correlationEntity = correlationResult.toEntity()
        val aiEntity = aiResult?.toEntity()

        val contextData = buildContextData(correlationEntity, aiEntity)
        val title = "${correlationResult.market} 분석 (${correlationResult.analysisDate})"

        val session = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            market = correlationResult.market,
            analysisDate = correlationResult.analysisDate,
            contextData = contextData
        )
        chatDao.insertSession(session)

        // 초기 시스템 메시지 추가
        val systemMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = session.id,
            role = "assistant",
            content = buildWelcomeMessage(correlationEntity, aiEntity),
            analysisResultId = aiResult?.id,
            aiProvider = null,
            aiModel = null,
            tokenCount = null
        )
        chatDao.insertMessage(systemMessage)
        chatDao.updateSessionMessageCount(session.id, 1)

        logger.d("Created analysis chat session: ${session.id}")
        session.toDomain()
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
            try {
                logger.d("Sending message in session $sessionId: ${content.take(50)}...")

                // 1. 사용자 메시지 저장
                val userMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "user",
                    content = content,
                    analysisResultId = null,
                    aiProvider = null,
                    aiModel = null,
                    tokenCount = null
                )
                chatDao.insertMessage(userMsg)

                // 2. 세션 정보 및 컨텍스트 가져오기
                val session = chatDao.getSessionById(sessionId)
                    ?: return@withContext Result.failure(Exception("세션을 찾을 수 없습니다"))

                // 3. 대화 이력 가져오기
                val recentMessages = chatDao.getRecentMessages(sessionId, MAX_CONTEXT_MESSAGES)
                    .reversed()

                // 4. 시스템 프롬프트 생성
                val systemPrompt = buildSystemPrompt(session)

                // 5. AI 호출용 메시지 구성
                val chatMessages = recentMessages.map { msg ->
                    AIChatMessage(role = msg.role, content = msg.content)
                } + AIChatMessage(role = "user", content = content)

                // 6. AI 응답 요청
                val client = aiApiClientFactory.getClient()
                val response = client.chat(chatMessages, systemPrompt, temperature = 0.7)

                if (response.isFailure) {
                    return@withContext Result.failure(
                        response.exceptionOrNull() ?: Exception("AI 응답 실패")
                    )
                }

                val aiResponse = response.getOrThrow()

                // 7. AI 응답 저장
                val assistantMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "assistant",
                    content = aiResponse,
                    analysisResultId = null,
                    aiProvider = client.provider.name,
                    aiModel = "default",
                    tokenCount = aiResponse.length / 4
                )
                chatDao.insertMessage(assistantMsg)

                // 8. 세션 업데이트
                val messageCount = chatDao.getMessageCount(sessionId)
                chatDao.updateSessionMessageCount(sessionId, messageCount)

                // 첫 메시지인 경우 제목 업데이트
                if (messageCount <= 3) {
                    val newTitle = generateSessionTitle(content)
                    chatDao.updateSessionTitle(sessionId, newTitle)
                }

                logger.d("AI response received, ${aiResponse.length} chars")
                Result.success(assistantMsg.toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Failed to send message", e)
                Result.failure(e)
            }
        }

    override fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesBySession(sessionId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteMessagesBySession(sessionId)
        chatDao.deleteSession(sessionId)
        logger.d("Deleted session: $sessionId")
    }

    override suspend fun askAboutAnalysis(
        sessionId: String,
        question: String
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        sendMessage(sessionId, question)
    }

    // ==================== Private Helpers ====================

    private fun buildContextData(
        correlationResult: CorrelationEntity,
        aiResult: AIAnalysisEntity?
    ): String {
        val contextMap = mutableMapOf<String, Any>()
        contextMap["market"] = correlationResult.market
        contextMap["analysisDate"] = correlationResult.analysisDate
        contextMap["periodDays"] = correlationResult.periodDays
        contextMap["compositeScore"] = correlationResult.compositeScore
        contextMap["signal"] = correlationResult.signal
        contextMap["confidence"] = correlationResult.confidence

        aiResult?.let {
            contextMap["aiSignal"] = it.signal
            contextMap["aiReasoning"] = it.reasoning
            contextMap["aiRecommendation"] = it.recommendation
        }

        return json.encodeToString(contextMap)
    }

    private fun buildWelcomeMessage(
        correlationResult: CorrelationEntity,
        aiResult: AIAnalysisEntity?
    ): String {
        return buildString {
            appendLine("안녕하세요! ${correlationResult.market} 시장 분석 결과에 대해 궁금한 점을 질문해주세요.")
            appendLine()
            appendLine("📊 분석 요약:")
            appendLine("- 분석일: ${correlationResult.analysisDate}")
            appendLine("- 분석 기간: ${correlationResult.periodDays}일")
            appendLine("- 종합 점수: ${String.format("%.3f", correlationResult.compositeScore)}")
            appendLine("- 신호: ${correlationResult.signal}")

            aiResult?.let {
                appendLine()
                appendLine("🤖 AI 분석:")
                appendLine("- 신호: ${it.signal}")
                appendLine("- 신뢰도: ${String.format("%.1f", it.confidence * 100)}%")
                appendLine("- 권장사항: ${it.recommendation}")
            }

            appendLine()
            appendLine("어떤 것에 대해 더 자세히 알고 싶으신가요?")
        }
    }

    private fun buildSystemPrompt(session: ChatSessionEntity): String {
        return buildString {
            appendLine("당신은 한국 주식 시장 분석 전문가입니다.")
            appendLine("사용자와 대화하면서 시장 분석 결과에 대해 설명합니다.")
            appendLine()

            session.contextData?.let { contextData ->
                appendLine("다음은 이 대화의 분석 컨텍스트입니다:")
                appendLine(contextData)
                appendLine()
            }

            appendLine("지침:")
            appendLine("- 간결하고 명확하게 답변하세요")
            appendLine("- 전문 용어는 쉽게 설명해주세요")
            appendLine("- 구체적인 수치가 있다면 언급해주세요")
            appendLine("- 투자 권유가 아닌 정보 제공임을 명시하세요")
        }
    }

    private fun generateSessionTitle(firstMessage: String): String {
        val words = firstMessage.take(30).split(" ").take(5)
        return if (words.isNotEmpty()) {
            words.joinToString(" ") + if (firstMessage.length > 30) "..." else ""
        } else {
            "새 대화"
        }
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
