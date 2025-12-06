package com.etfmonitor.repository

import android.util.Log
import com.etfmonitor.ai.*
import com.etfmonitor.database.AIChatDao
import com.etfmonitor.database.AIAnalysisDao
import com.etfmonitor.database.CorrelationAnalysisDao
import com.etfmonitor.database.entities.AIChatMessage
import com.etfmonitor.database.entities.AIChatSession
import com.etfmonitor.database.entities.AIAnalysisResult
import com.etfmonitor.database.entities.CorrelationAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 채팅 Repository
 * 분석 데이터 기반 대화형 Q&A 기능
 */
@Singleton
class AIChatRepository @Inject constructor(
    private val chatDao: AIChatDao,
    private val aiAnalysisDao: AIAnalysisDao,
    private val correlationAnalysisDao: CorrelationAnalysisDao,
    private val aiApiClientFactory: AIApiClientFactory
) {
    companion object {
        private const val TAG = "AIChatRepository"
        private const val MAX_CONTEXT_MESSAGES = 10 // 최대 컨텍스트 메시지 수
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // ========== 세션 관리 ==========

    /**
     * 새 채팅 세션 생성
     */
    suspend fun createSession(
        market: String? = null,
        analysisDate: String? = null,
        title: String = "새 대화"
    ): AIChatSession = withContext(Dispatchers.IO) {
        val session = AIChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            market = market,
            analysisDate = analysisDate,
            contextData = null
        )
        chatDao.insertSession(session)
        Log.d(TAG, "Created new chat session: ${session.id}")
        session
    }

    /**
     * 분석 결과 기반 채팅 세션 생성
     */
    suspend fun createSessionWithAnalysis(
        correlationResult: CorrelationAnalysisResult,
        aiResult: AIAnalysisResult? = null
    ): AIChatSession = withContext(Dispatchers.IO) {
        val contextData = buildContextData(correlationResult, aiResult)
        val title = "${correlationResult.market} 분석 (${correlationResult.analysisDate})"

        val session = AIChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            market = correlationResult.market,
            analysisDate = correlationResult.analysisDate,
            contextData = contextData
        )
        chatDao.insertSession(session)

        // 초기 시스템 메시지 추가
        val systemMessage = AIChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = session.id,
            role = "assistant",
            content = buildWelcomeMessage(correlationResult, aiResult),
            analysisResultId = aiResult?.id,
            aiProvider = null,
            aiModel = null,
            tokenCount = null
        )
        chatDao.insertMessage(systemMessage)
        chatDao.updateSessionMessageCount(session.id, 1)

        Log.d(TAG, "Created analysis chat session: ${session.id}")
        session
    }

    /**
     * 모든 세션 조회
     */
    fun getAllSessions(): Flow<List<AIChatSession>> =
        chatDao.getAllSessions().flowOn(Dispatchers.IO)

    /**
     * 세션 조회
     */
    suspend fun getSession(sessionId: String): AIChatSession? = withContext(Dispatchers.IO) {
        chatDao.getSessionById(sessionId)
    }

    /**
     * 세션 삭제
     */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteMessagesBySession(sessionId)
        chatDao.deleteSession(sessionId)
        Log.d(TAG, "Deleted session: $sessionId")
    }

    // ========== 메시지 관리 ==========

    /**
     * 세션의 메시지 조회
     */
    fun getMessages(sessionId: String): Flow<List<AIChatMessage>> =
        chatDao.getMessagesBySession(sessionId).flowOn(Dispatchers.IO)

    /**
     * 메시지 전송 및 AI 응답 받기
     */
    suspend fun sendMessage(
        sessionId: String,
        userMessage: String
    ): Result<AIChatMessage> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending message in session $sessionId: ${userMessage.take(50)}...")

            // 1. 사용자 메시지 저장
            val userMsg = AIChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "user",
                content = userMessage,
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
                .reversed() // 시간순 정렬

            // 4. 시스템 프롬프트 생성
            val systemPrompt = buildSystemPrompt(session)

            // 5. AI 호출용 메시지 구성
            val chatMessages = recentMessages.map { msg ->
                ChatMessage(role = msg.role, content = msg.content)
            } + ChatMessage(role = "user", content = userMessage)

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
            val assistantMsg = AIChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "assistant",
                content = aiResponse,
                analysisResultId = null,
                aiProvider = client.provider.name,
                aiModel = "default",
                tokenCount = aiResponse.length / 4 // 대략적인 토큰 수
            )
            chatDao.insertMessage(assistantMsg)

            // 8. 세션 업데이트
            val messageCount = chatDao.getMessageCount(sessionId)
            chatDao.updateSessionMessageCount(sessionId, messageCount)

            // 첫 메시지인 경우 제목 업데이트
            if (messageCount <= 3) {
                val newTitle = generateSessionTitle(userMessage)
                chatDao.updateSessionTitle(sessionId, newTitle)
            }

            Log.d(TAG, "AI response received: ${aiResponse.take(100)}...")
            Result.success(assistantMsg)

        } catch (e: Exception) {
            Log.e(TAG, "Send message failed", e)
            Result.failure(e)
        }
    }

    /**
     * 특정 분석에 대해 질문하기
     */
    suspend fun askAboutAnalysis(
        sessionId: String,
        question: String,
        correlationResultId: String
    ): Result<AIChatMessage> = withContext(Dispatchers.IO) {
        try {
            // 상관관계 결과 가져오기
            val correlationResult = correlationAnalysisDao.getById(correlationResultId)
            val aiResult = correlationResult?.let {
                aiAnalysisDao.getByCorrelationId(it.id)
            }

            // 질문에 분석 컨텍스트 추가
            val enhancedQuestion = buildAnalysisQuestion(question, correlationResult, aiResult)

            // 일반 메시지 전송
            sendMessage(sessionId, enhancedQuestion)
        } catch (e: Exception) {
            Log.e(TAG, "Ask about analysis failed", e)
            Result.failure(e)
        }
    }

    // ========== Private Helpers ==========

    /**
     * 시스템 프롬프트 생성
     */
    private suspend fun buildSystemPrompt(session: AIChatSession): String {
        return buildString {
            appendLine("당신은 한국 주식 시장 전문 AI 애널리스트입니다.")
            appendLine("사용자의 투자 관련 질문에 친절하고 정확하게 답변해주세요.")
            appendLine()
            appendLine("## 역할")
            appendLine("- ETF 분석 및 시장 동향 설명")
            appendLine("- 상관관계 분석 결과 해석")
            appendLine("- 투자 전략 및 위험 관리 조언")
            appendLine()

            // 세션 컨텍스트 추가
            session.market?.let {
                appendLine("## 현재 분석 대상")
                appendLine("- 시장: $it")
            }
            session.analysisDate?.let {
                appendLine("- 분석 날짜: $it")
            }

            // 분석 데이터 컨텍스트
            session.contextData?.let { context ->
                appendLine()
                appendLine("## 분석 데이터 요약")
                appendLine(context)
            }

            appendLine()
            appendLine("## 응답 지침")
            appendLine("1. 한국어로 답변")
            appendLine("2. 구체적인 수치와 근거 제시")
            appendLine("3. 투자 결정은 개인의 책임임을 상기")
            appendLine("4. 불확실한 경우 솔직하게 표현")
        }
    }

    /**
     * 컨텍스트 데이터 생성
     */
    private fun buildContextData(
        correlationResult: CorrelationAnalysisResult,
        aiResult: AIAnalysisResult?
    ): String {
        return buildString {
            appendLine("### 상관관계 분석 결과")
            appendLine("- 분석 기간: ${correlationResult.periodDays}일")
            appendLine("- ETF 순편입 상관관계: ${String.format("%.3f", correlationResult.etfNetFlowCorrelation)}")
            appendLine("- 원화예금 상관관계: ${String.format("%.3f", correlationResult.cashDepositCorrelation)}")
            correlationResult.fearGreedCorrelation?.let {
                appendLine("- Fear&Greed 상관관계: ${String.format("%.3f", it)}")
            }
            correlationResult.oscillatorCorrelation?.let {
                appendLine("- Oscillator 상관관계: ${String.format("%.3f", it)}")
            }
            appendLine("- 종합 점수: ${String.format("%.3f", correlationResult.compositeScore)}")
            appendLine("- 신호: ${correlationResult.signal}")

            aiResult?.let {
                appendLine()
                appendLine("### AI 분석 결과")
                appendLine("- 신호: ${it.signal}")
                appendLine("- 신뢰도: ${String.format("%.1f", it.confidence * 100)}%")
                appendLine("- 상승 확률: ${String.format("%.1f", it.upProbability)}%")
                appendLine("- 핵심 분석: ${it.reasoning.take(200)}")
            }
        }
    }

    /**
     * 환영 메시지 생성
     */
    private fun buildWelcomeMessage(
        correlationResult: CorrelationAnalysisResult,
        aiResult: AIAnalysisResult?
    ): String {
        return buildString {
            appendLine("${correlationResult.market} 시장 분석 결과를 바탕으로 대화를 시작합니다.")
            appendLine()
            appendLine("**분석 요약 (${correlationResult.analysisDate})**")
            appendLine("- 분석 기간: ${correlationResult.periodDays}일")
            appendLine("- 신호: ${correlationResult.signal}")
            appendLine("- 상승 확률: ${String.format("%.1f", correlationResult.upProbability)}%")
            appendLine()

            aiResult?.let {
                appendLine("**AI 분석 의견**")
                appendLine(it.reasoning.take(300))
                if (it.reasoning.length > 300) appendLine("...")
                appendLine()
            }

            appendLine("분석 결과에 대해 궁금한 점이 있으시면 질문해주세요!")
        }
    }

    /**
     * 분석 질문 강화
     */
    private fun buildAnalysisQuestion(
        question: String,
        correlationResult: CorrelationAnalysisResult?,
        aiResult: AIAnalysisResult?
    ): String {
        if (correlationResult == null) return question

        return buildString {
            appendLine("[분석 데이터 참조]")
            appendLine("시장: ${correlationResult.market}, 날짜: ${correlationResult.analysisDate}")
            appendLine("현재 신호: ${correlationResult.signal}, 종합 점수: ${String.format("%.3f", correlationResult.compositeScore)}")
            aiResult?.let {
                appendLine("AI 의견: ${it.signal} (신뢰도 ${String.format("%.0f", it.confidence * 100)}%)")
            }
            appendLine()
            appendLine("[질문]")
            appendLine(question)
        }
    }

    /**
     * 세션 제목 생성
     */
    private fun generateSessionTitle(firstMessage: String): String {
        val cleaned = firstMessage
            .replace("\n", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        return if (cleaned.length > 30) {
            cleaned.take(27) + "..."
        } else {
            cleaned
        }
    }
}
