package com.etfmonitor.feature.analysis.data.mapper

import com.etfmonitor.core.database.entities.AIAnalysisResult as AIAnalysisEntity
import com.etfmonitor.core.database.entities.AIChatMessage as ChatMessageEntity
import com.etfmonitor.core.database.entities.AIChatSession as ChatSessionEntity
import com.etfmonitor.core.database.entities.CorrelationAnalysisResult as CorrelationEntity
import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.ChatMessage
import com.etfmonitor.feature.analysis.domain.model.ChatSession
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.model.MessageRole
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AnalysisMapper 단위 테스트
 *
 * 테스트 범위:
 * - AIAnalysisEntity → AIAnalysis 변환 (keyFactors JSON 파싱 포함)
 * - CorrelationAnalysisEntity → CorrelationAnalysis 변환
 * - ChatSessionEntity → ChatSession 변환
 * - ChatMessageEntity → ChatMessage 변환 (role 매핑 포함)
 *
 * 주의: 복잡한 타입 변환 (MarketCapFlow, DivergenceAnalysis 등)은
 * TypeConverter에 의존하여 Room 런타임이 필요하므로 제외합니다.
 */
@DisplayName("AnalysisMapper 테스트")
class AnalysisMapperTest {

    // ========== AIAnalysisResult → AIAnalysis ==========

    @Nested
    @DisplayName("AIAnalysisResult Entity → Domain 변환")
    inner class AIAnalysisTests {

        @Test
        @DisplayName("toDomain()은 기본 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsBasicFieldsCorrectly`() {
            // Given
            val entity = createTestAIAnalysisEntity(
                id = "test-uuid-001",
                market = "KOSPI",
                analysisDate = "2025-01-15",
                signal = "BUY",
                confidence = 0.75
            )

            // When
            val domain: AIAnalysis = entity.toDomain()

            // Then
            assertEquals("test-uuid-001", domain.id)
            assertEquals("KOSPI", domain.market)
            assertEquals("2025-01-15", domain.analysisDate)
            assertEquals("BUY", domain.signal)
            assertEquals(0.75, domain.confidence)
        }

        @Test
        @DisplayName("toDomain()은 keyFactors JSON 배열을 List<String>으로 파싱한다")
        fun `toDomain_withValidKeyFactorsJson_parsesJsonToList`() {
            // Given
            val entity = createTestAIAnalysisEntity(
                keyFactors = """["ETF 순증가", "Fear & Greed 탐욕", "외국인 순매수"]"""
            )

            // When
            val domain: AIAnalysis = entity.toDomain()

            // Then
            assertEquals(3, domain.keyFactors.size)
            assertEquals("ETF 순증가", domain.keyFactors[0])
            assertEquals("Fear & Greed 탐욕", domain.keyFactors[1])
            assertEquals("외국인 순매수", domain.keyFactors[2])
        }

        @Test
        @DisplayName("toDomain()은 잘못된 keyFactors JSON에 대해 빈 리스트를 반환한다")
        fun `toDomain_withInvalidKeyFactorsJson_returnsEmptyList`() {
            // Given
            val entity = createTestAIAnalysisEntity(keyFactors = "not-valid-json")

            // When
            val domain: AIAnalysis = entity.toDomain()

            // Then
            assertTrue(domain.keyFactors.isEmpty(), "Invalid JSON should result in empty keyFactors list")
        }

        @Test
        @DisplayName("toDomain()은 빈 keyFactors JSON 배열에 대해 빈 리스트를 반환한다")
        fun `toDomain_withEmptyKeyFactorsJson_returnsEmptyList`() {
            // Given
            val entity = createTestAIAnalysisEntity(keyFactors = "[]")

            // When
            val domain: AIAnalysis = entity.toDomain()

            // Then
            assertTrue(domain.keyFactors.isEmpty())
        }

        @Test
        @DisplayName("toDomain()은 correlationResultId가 null이면 null을 유지한다")
        fun `toDomain_withNullCorrelationResultId_preservesNull`() {
            // Given
            val entity = createTestAIAnalysisEntity(correlationResultId = null)

            // When
            val domain: AIAnalysis = entity.toDomain()

            // Then
            assertNull(domain.correlationResultId)
        }

        @Test
        @DisplayName("toDomain()은 alternativeScenarios가 null이면 null을 유지한다")
        fun `toDomain_withNullAlternativeScenarios_preservesNull`() {
            // Given
            val entity = createTestAIAnalysisEntity(alternativeScenarios = null)

            // When
            val domain: AIAnalysis = entity.toDomain()

            // Then
            assertNull(domain.alternativeScenarios)
        }

        @Test
        @DisplayName("toDomain()은 다양한 신호 값을 올바르게 전달한다")
        fun `toDomain_withVariousSignals_preservesSignalValue`() {
            val signalValues = listOf("STRONG_BUY", "BUY", "NEUTRAL", "SELL", "STRONG_SELL")
            signalValues.forEach { signal ->
                val entity = createTestAIAnalysisEntity(signal = signal)
                val domain: AIAnalysis = entity.toDomain()
                assertEquals(signal, domain.signal, "Signal $signal was not preserved")
            }
        }

        @Test
        @DisplayName("toDomain()은 aiProvider와 aiModel을 올바르게 변환한다")
        fun `toDomain_withClaudeProvider_mapsProviderAndModelCorrectly`() {
            // Given
            val entity = createTestAIAnalysisEntity(
                aiProvider = "CLAUDE",
                aiModel = "claude-sonnet-4-6"
            )

            // When
            val domain: AIAnalysis = entity.toDomain()

            // Then
            assertEquals("CLAUDE", domain.aiProvider)
            assertEquals("claude-sonnet-4-6", domain.aiModel)
        }

        @Test
        @DisplayName("toDomain()은 확률 필드를 올바르게 변환한다")
        fun `toDomain_withProbabilityFields_mapsProbabilitiesCorrectly`() {
            // Given
            val entity = createTestAIAnalysisEntity(
                upProbability = 65.0,
                downProbability = 35.0
            )

            // When
            val domain: AIAnalysis = entity.toDomain()

            // Then
            assertEquals(65.0, domain.upProbability)
            assertEquals(35.0, domain.downProbability)
        }
    }

    // ========== CorrelationAnalysisResult → CorrelationAnalysis ==========

    @Nested
    @DisplayName("CorrelationAnalysisResult Entity → Domain 변환")
    inner class CorrelationAnalysisTests {

        @Test
        @DisplayName("toDomain()은 기본 상관관계 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsBasicFieldsCorrectly`() {
            // Given
            val entity = createTestCorrelationEntity(
                id = "KOSPI-2025-01-15",
                market = "KOSPI",
                analysisDate = "2025-01-15"
            )

            // When
            val domain: CorrelationAnalysis = entity.toDomain()

            // Then
            assertEquals("KOSPI-2025-01-15", domain.id)
            assertEquals("KOSPI", domain.market)
            assertEquals("2025-01-15", domain.analysisDate)
        }

        @Test
        @DisplayName("toDomain()은 ETF 관련 상관관계 값을 올바르게 변환한다")
        fun `toDomain_withEtfCorrelations_mapsCorrelationsCorrectly`() {
            // Given
            val entity = createTestCorrelationEntity(
                etfNetFlowCorrelation = 0.65,
                etfNewStockCorrelation = 0.45,
                etfRemovedStockCorrelation = -0.30,
                etfIncreasedCorrelation = 0.55,
                etfDecreasedCorrelation = -0.40,
                cashDepositCorrelation = 0.20
            )

            // When
            val domain: CorrelationAnalysis = entity.toDomain()

            // Then
            assertEquals(0.65, domain.etfNetFlowCorrelation)
            assertEquals(0.45, domain.etfNewStockCorrelation)
            assertEquals(-0.30, domain.etfRemovedStockCorrelation)
            assertEquals(0.55, domain.etfIncreasedCorrelation)
            assertEquals(-0.40, domain.etfDecreasedCorrelation)
            assertEquals(0.20, domain.cashDepositCorrelation)
        }

        @Test
        @DisplayName("toDomain()은 nullable 상관관계 필드에서 null을 유지한다")
        fun `toDomain_withNullableFields_preservesNulls`() {
            // Given
            val entity = createTestCorrelationEntity(
                marketDepositCorrelation = null,
                creditBalanceCorrelation = null,
                fearGreedCorrelation = null,
                fearGreedLeadCorrelation = null,
                oscillatorCorrelation = null,
                oscillatorLeadCorrelation = null
            )

            // When
            val domain: CorrelationAnalysis = entity.toDomain()

            // Then
            assertNull(domain.marketDepositCorrelation)
            assertNull(domain.creditBalanceCorrelation)
            assertNull(domain.fearGreedCorrelation)
            assertNull(domain.fearGreedLeadCorrelation)
            assertNull(domain.oscillatorCorrelation)
            assertNull(domain.oscillatorLeadCorrelation)
        }

        @Test
        @DisplayName("toDomain()은 nullable 상관관계 필드에서 non-null 값을 올바르게 변환한다")
        fun `toDomain_withNonNullableOptionalFields_mapsValuesCorrectly`() {
            // Given
            val entity = createTestCorrelationEntity(
                marketDepositCorrelation = 0.35,
                creditBalanceCorrelation = -0.25,
                fearGreedCorrelation = 0.60,
                fearGreedLeadCorrelation = 0.55,
                oscillatorCorrelation = 0.70,
                oscillatorLeadCorrelation = 0.65
            )

            // When
            val domain: CorrelationAnalysis = entity.toDomain()

            // Then
            assertEquals(0.35, domain.marketDepositCorrelation)
            assertEquals(-0.25, domain.creditBalanceCorrelation)
            assertEquals(0.60, domain.fearGreedCorrelation)
            assertEquals(0.55, domain.fearGreedLeadCorrelation)
            assertEquals(0.70, domain.oscillatorCorrelation)
            assertEquals(0.65, domain.oscillatorLeadCorrelation)
        }

        @Test
        @DisplayName("toDomain()은 compositeScore와 신호 결과를 올바르게 변환한다")
        fun `toDomain_withCompositeScoreAndSignal_mapsCorrectly`() {
            // Given
            val entity = createTestCorrelationEntity(
                compositeScore = 0.42,
                signal = "BUY",
                confidence = 0.68,
                upProbability = 62.0,
                downProbability = 38.0
            )

            // When
            val domain: CorrelationAnalysis = entity.toDomain()

            // Then
            assertEquals(0.42, domain.compositeScore)
            assertEquals("BUY", domain.signal)
            assertEquals(0.68, domain.confidence)
            assertEquals(62.0, domain.upProbability)
            assertEquals(38.0, domain.downProbability)
        }

        @Test
        @DisplayName("toDomain()은 periodDays를 올바르게 변환한다")
        fun `toDomain_withPeriodDays_mapsCorrectly`() {
            // Given
            val entity = createTestCorrelationEntity(periodDays = 90)

            // When
            val domain: CorrelationAnalysis = entity.toDomain()

            // Then
            assertEquals(90, domain.periodDays)
        }
    }

    // ========== ChatSession ==========

    @Nested
    @DisplayName("AIChatSession Entity → Domain 변환")
    inner class ChatSessionTests {

        @Test
        @DisplayName("toDomain()은 모든 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val entity = ChatSessionEntity(
                id = "session-uuid-001",
                title = "KOSPI 분석 세션",
                market = "KOSPI",
                analysisDate = "2025-01-15",
                contextData = """{"fearGreedValue": 65.5}""",
                messageCount = 5,
                createdAt = now - 3600_000L,
                updatedAt = now
            )

            // When
            val domain: ChatSession = entity.toDomain()

            // Then
            assertEquals("session-uuid-001", domain.id)
            assertEquals("KOSPI 분석 세션", domain.title)
            assertEquals("KOSPI", domain.market)
            assertEquals("2025-01-15", domain.analysisDate)
            assertEquals("""{"fearGreedValue": 65.5}""", domain.contextData)
            assertEquals(5, domain.messageCount)
            assertEquals(now - 3600_000L, domain.createdAt)
            assertEquals(now, domain.updatedAt)
        }

        @Test
        @DisplayName("toDomain()은 market이 null인 세션을 올바르게 변환한다")
        fun `toDomain_withNullMarket_preservesNull`() {
            // Given
            val entity = ChatSessionEntity(
                id = "session-uuid-002",
                title = "일반 질문",
                market = null,
                analysisDate = null,
                contextData = null,
                messageCount = 1,
                createdAt = 0L,
                updatedAt = 0L
            )

            // When
            val domain: ChatSession = entity.toDomain()

            // Then
            assertNull(domain.market)
            assertNull(domain.analysisDate)
            assertNull(domain.contextData)
        }
    }

    // ========== ChatMessage ==========

    @Nested
    @DisplayName("AIChatMessage Entity → Domain 변환 (role 매핑 포함)")
    inner class ChatMessageTests {

        @Test
        @DisplayName("toDomain()은 role='user'를 MessageRole.USER로 변환한다")
        fun `toDomain_withUserRole_mapsToUserMessageRole`() {
            // Given
            val entity = createTestChatMessageEntity(role = "user")

            // When
            val domain: ChatMessage = entity.toDomain()

            // Then
            assertEquals(MessageRole.USER, domain.role)
        }

        @Test
        @DisplayName("toDomain()은 role='assistant'를 MessageRole.ASSISTANT로 변환한다")
        fun `toDomain_withAssistantRole_mapsToAssistantMessageRole`() {
            // Given
            val entity = createTestChatMessageEntity(
                role = "assistant",
                aiProvider = "CLAUDE",
                aiModel = "claude-sonnet-4-6",
                tokenCount = 150
            )

            // When
            val domain: ChatMessage = entity.toDomain()

            // Then
            assertEquals(MessageRole.ASSISTANT, domain.role)
            assertEquals("CLAUDE", domain.aiProvider)
            assertEquals("claude-sonnet-4-6", domain.aiModel)
            assertEquals(150, domain.tokenCount)
        }

        @Test
        @DisplayName("toDomain()은 알 수 없는 role을 MessageRole.SYSTEM으로 처리한다")
        fun `toDomain_withUnknownRole_defaultsToSystemRole`() {
            // Given
            val entity = createTestChatMessageEntity(role = "unknown_role")

            // When
            val domain: ChatMessage = entity.toDomain()

            // Then
            assertEquals(MessageRole.SYSTEM, domain.role)
        }

        @Test
        @DisplayName("toDomain()은 모든 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val entity = createTestChatMessageEntity(
                id = "msg-uuid-001",
                sessionId = "session-uuid-001",
                role = "user",
                content = "KOSPI 전망을 알려주세요",
                timestamp = now
            )

            // When
            val domain: ChatMessage = entity.toDomain()

            // Then
            assertEquals("msg-uuid-001", domain.id)
            assertEquals("session-uuid-001", domain.sessionId)
            assertEquals(MessageRole.USER, domain.role)
            assertEquals("KOSPI 전망을 알려주세요", domain.content)
            assertEquals(now, domain.timestamp)
        }

        @Test
        @DisplayName("toDomain()은 사용자 메시지에서 aiProvider가 null임을 올바르게 변환한다")
        fun `toDomain_withUserMessage_preservesNullAiFields`() {
            // Given
            val entity = createTestChatMessageEntity(
                role = "user",
                aiProvider = null,
                aiModel = null,
                tokenCount = null
            )

            // When
            val domain: ChatMessage = entity.toDomain()

            // Then
            assertNull(domain.aiProvider)
            assertNull(domain.aiModel)
            assertNull(domain.tokenCount)
        }

        @Test
        @DisplayName("toDomain()은 Gemini 제공자를 올바르게 변환한다")
        fun `toDomain_withGeminiProvider_mapsProviderCorrectly`() {
            // Given
            val entity = createTestChatMessageEntity(
                role = "assistant",
                aiProvider = "GEMINI",
                aiModel = "gemini-2.0-flash"
            )

            // When
            val domain: ChatMessage = entity.toDomain()

            // Then
            assertEquals("GEMINI", domain.aiProvider)
            assertEquals("gemini-2.0-flash", domain.aiModel)
        }
    }

    // ========== Helper Functions ==========

    private fun createTestAIAnalysisEntity(
        id: String = "test-uuid",
        market: String = "KOSPI",
        analysisDate: String = "2025-01-15",
        correlationResultId: String? = "corr-id",
        aiProvider: String = "CLAUDE",
        aiModel: String = "claude-sonnet-4-6",
        signal: String = "BUY",
        confidence: Double = 0.75,
        upProbability: Double = 65.0,
        downProbability: Double = 35.0,
        riskLevel: String = "MEDIUM",
        reasoning: String = "긍정적 수급 신호",
        keyFactors: String = """["ETF 순증가", "Fear & Greed 탐욕"]""",
        recommendation: String = "매수 고려",
        alternativeScenarios: String? = null,
        processingTimeMs: Long = 1500L
    ): AIAnalysisEntity = AIAnalysisEntity(
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
        keyFactors = keyFactors,
        recommendation = recommendation,
        alternativeScenarios = alternativeScenarios,
        promptUsed = "test prompt",
        rawResponse = "test raw response",
        processingTimeMs = processingTimeMs,
        createdAt = System.currentTimeMillis()
    )

    private fun createTestCorrelationEntity(
        id: String = "KOSPI-2025-01-15",
        market: String = "KOSPI",
        analysisDate: String = "2025-01-15",
        periodDays: Int = 30,
        etfNetFlowCorrelation: Double = 0.50,
        etfNewStockCorrelation: Double = 0.40,
        etfRemovedStockCorrelation: Double = -0.25,
        etfIncreasedCorrelation: Double = 0.45,
        etfDecreasedCorrelation: Double = -0.35,
        cashDepositCorrelation: Double = 0.15,
        marketDepositCorrelation: Double? = 0.30,
        creditBalanceCorrelation: Double? = -0.20,
        fearGreedCorrelation: Double? = 0.55,
        fearGreedLeadCorrelation: Double? = 0.50,
        oscillatorCorrelation: Double? = 0.60,
        oscillatorLeadCorrelation: Double? = 0.55,
        compositeScore: Double = 0.38,
        signal: String = "BUY",
        confidence: Double = 0.65,
        upProbability: Double = 60.0,
        downProbability: Double = 40.0,
        analysisContext: String = "{}"
    ): CorrelationEntity = CorrelationEntity(
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

    private fun createTestChatMessageEntity(
        id: String = "msg-uuid",
        sessionId: String = "session-uuid",
        role: String = "user",
        content: String = "테스트 메시지",
        aiProvider: String? = null,
        aiModel: String? = null,
        tokenCount: Int? = null,
        timestamp: Long = System.currentTimeMillis()
    ): ChatMessageEntity = ChatMessageEntity(
        id = id,
        sessionId = sessionId,
        role = role,
        content = content,
        analysisResultId = null,
        aiProvider = aiProvider,
        aiModel = aiModel,
        tokenCount = tokenCount,
        timestamp = timestamp
    )
}
