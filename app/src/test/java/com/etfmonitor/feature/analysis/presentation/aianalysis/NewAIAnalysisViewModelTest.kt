package com.etfmonitor.feature.analysis.presentation.aianalysis

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.SearchHistoryDao
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.database.entities.SearchHistoryType
import com.etfmonitor.core.network.ai.AIApiClientFactory
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.core.network.ai.ApiKeyProvider
import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.ChatMessage
import com.etfmonitor.feature.analysis.domain.model.ChatSession
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullAnalysis
import com.etfmonitor.feature.analysis.domain.repository.ChatRepository
import com.etfmonitor.feature.analysis.domain.repository.CorrelationAnalysisRepository
import com.etfmonitor.feature.analysis.domain.repository.StockIndicatorRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NewAIAnalysisViewModel 단위 테스트
 *
 * 테스트 범위:
 * - 초기 상태 (Idle/CorrelationComplete)
 * - API 키 체크 (isApiKeyConfigured)
 * - runCorrelationAnalysis() 성공/실패
 * - runFullAnalysis() API 키 없을 때 Error
 * - runFullAnalysis() 성공
 * - startNewChat() API 키 없을 때 Error
 * - startNewChat() 성공
 * - sendMessage() API 키 없을 때 ChatError
 * - sendMessage() 성공/실패
 * - selectMarket() 시장 변경
 * - selectProvider() 제공자 변경
 * - clearError() 동작
 * - closeChat() 동작
 * - deleteSession() 동작
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class NewAIAnalysisViewModelTest {

    private lateinit var correlationAnalysisRepository: CorrelationAnalysisRepository
    private lateinit var stockIndicatorRepository: StockIndicatorRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var apiKeyProvider: ApiKeyProvider
    private lateinit var aiApiClientFactory: AIApiClientFactory
    private lateinit var etfDao: EtfDao
    private lateinit var searchHistoryDao: SearchHistoryDao

    @BeforeEach
    fun setup() {
        correlationAnalysisRepository = mockk(relaxed = true)
        stockIndicatorRepository = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        apiKeyProvider = mockk(relaxed = true)
        aiApiClientFactory = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
        searchHistoryDao = mockk(relaxed = true)

        // Default: API key configured
        every { apiKeyProvider.getSelectedProvider() } returns AIProvider.CLAUDE
        every { apiKeyProvider.getApiKey(any()) } returns "test-api-key"

        // Default: no latest correlation result, so auto-run analysis
        coEvery { correlationAnalysisRepository.getLatestCorrelationResult(any()) } returns null
        coEvery { correlationAnalysisRepository.runLatestCorrelationAnalysis(any(), any()) } returns
            Result.success(makeCorrelationAnalysis())

        // Default: chat sessions and messages are empty
        every { chatRepository.getAllSessions() } returns flowOf(emptyList<ChatSession>())
        every { chatRepository.getMessages(any()) } returns flowOf(emptyList<ChatMessage>())

        // Default: stock indicator AI history empty
        every { stockIndicatorRepository.getStockIndicatorAIHistory(any()) } returns
            flowOf(emptyList())
        every { stockIndicatorRepository.getAllStockIndicatorAIHistory(any()) } returns
            flowOf(emptyList())

        // Default: search history
        every { searchHistoryDao.getRecentSearchesByType(SearchHistoryType.AI_ANALYSIS, any()) } returns
            flowOf(emptyList<SearchHistory>())

        // Default: no settings
        coEvery { etfDao.getSetting(any()) } returns null

        // Default: correlation results flow
        every { correlationAnalysisRepository.getCorrelationResults(any()) } returns
            flowOf(emptyList<CorrelationAnalysis>())

        // Default: available providers
        every { aiApiClientFactory.getAvailableProviders() } returns listOf(AIProvider.CLAUDE, AIProvider.GEMINI)
    }

    private fun createViewModel(): NewAIAnalysisViewModel = NewAIAnalysisViewModel(
        correlationAnalysisRepository = correlationAnalysisRepository,
        stockIndicatorRepository = stockIndicatorRepository,
        chatRepository = chatRepository,
        apiKeyProvider = apiKeyProvider,
        aiApiClientFactory = aiApiClientFactory,
        etfDao = etfDao,
        searchHistoryDao = searchHistoryDao
    )

    // --- helpers ---

    private fun makeCorrelationAnalysis(
        id: String = "corr-001",
        market: String = "KOSPI"
    ) = CorrelationAnalysis(
        id = id,
        market = market,
        analysisDate = "2025-01-15",
        periodDays = 30,
        etfNetFlowCorrelation = 0.65,
        etfNewStockCorrelation = 0.3,
        etfRemovedStockCorrelation = -0.2,
        etfIncreasedCorrelation = 0.45,
        etfDecreasedCorrelation = -0.35,
        cashDepositCorrelation = 0.55,
        marketDepositCorrelation = 0.4,
        creditBalanceCorrelation = -0.3,
        fearGreedCorrelation = 0.6,
        fearGreedLeadCorrelation = 0.5,
        oscillatorCorrelation = 0.7,
        oscillatorLeadCorrelation = 0.65,
        compositeScore = 0.58,
        signal = "매수",
        confidence = 0.72,
        upProbability = 0.65,
        downProbability = 0.35,
        analysisContext = "분석 컨텍스트"
    )

    private fun makeAIAnalysis(id: String = "ai-001") = AIAnalysis(
        id = id,
        market = "KOSPI",
        analysisDate = "2025-01-15",
        correlationResultId = "corr-001",
        aiProvider = "CLAUDE",
        aiModel = "claude-3-5-haiku-20241022",
        signal = "매수",
        confidence = 0.72,
        upProbability = 0.65,
        downProbability = 0.35,
        riskLevel = "중간",
        reasoning = "긍정적 지표들이 우세합니다",
        keyFactors = listOf("ETF 순유입 증가", "Fear&Greed 상승"),
        recommendation = "매수 추천",
        alternativeScenarios = null,
        processingTimeMs = 1500
    )

    private fun makeChatSession(id: String = "session-001") = ChatSession(
        id = id,
        title = "새 대화",
        market = "KOSPI",
        analysisDate = "2025-01-15",
        contextData = null,
        messageCount = 0,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    // ---------------------------------------------------------------
    // 초기 상태 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("API 키 설정 시 isApiKeyConfigured = true")
        fun apiKeyConfigured_isApiKeyConfiguredIsTrue() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns "valid-api-key"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.isApiKeyConfigured.test {
                assertTrue(awaitItem())
            }
        }

        @Test
        @DisplayName("API 키 미설정 시 isApiKeyConfigured = false")
        fun noApiKey_isApiKeyConfiguredIsFalse() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.isApiKeyConfigured.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("초기 selectedMarket 은 KOSPI")
        fun initialSelectedMarket_isKospi() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectedMarket.test {
                assertEquals("KOSPI", awaitItem())
            }
        }

        @Test
        @DisplayName("초기 selectedProvider 는 apiKeyProvider에서 로드")
        fun initialSelectedProvider_loadedFromApiKeyProvider() = runTest {
            every { apiKeyProvider.getSelectedProvider() } returns AIProvider.CLAUDE

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectedProvider.test {
                assertEquals(AIProvider.CLAUDE, awaitItem())
            }
        }

        @Test
        @DisplayName("최신 상관관계 결과 없을 때 자동 분석 실행")
        fun noLatestResult_autoRunsCorrelationAnalysis() = runTest {
            coEvery { correlationAnalysisRepository.getLatestCorrelationResult(any()) } returns null
            coEvery { correlationAnalysisRepository.runLatestCorrelationAnalysis(any(), any()) } returns
                Result.success(makeCorrelationAnalysis())

            val viewModel = createViewModel()
            advanceUntilIdle()

            coVerify { correlationAnalysisRepository.runLatestCorrelationAnalysis(any(), any()) }
        }

        @Test
        @DisplayName("최신 상관관계 결과 있을 때 CorrelationComplete 상태")
        fun hasLatestResult_stateIsCorrelationComplete() = runTest {
            val correlation = makeCorrelationAnalysis()
            coEvery { correlationAnalysisRepository.getLatestCorrelationResult("KOSPI") } returns correlation
            coEvery { correlationAnalysisRepository.getLatestAIResult("KOSPI") } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<NewAIAnalysisState.CorrelationComplete>(awaitItem())
            }
        }

        @Test
        @DisplayName("최신 상관관계 + AI 결과 있을 때 FullAnalysisComplete 상태")
        fun hasLatestResultAndAI_stateIsFullAnalysisComplete() = runTest {
            val correlation = makeCorrelationAnalysis()
            val aiAnalysis = makeAIAnalysis()
            coEvery { correlationAnalysisRepository.getLatestCorrelationResult("KOSPI") } returns correlation
            coEvery { correlationAnalysisRepository.getLatestAIResult("KOSPI") } returns aiAnalysis

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<NewAIAnalysisState.FullAnalysisComplete>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // runCorrelationAnalysis() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("runCorrelationAnalysis() 테스트")
    inner class RunCorrelationAnalysisTests {

        @Test
        @DisplayName("성공 시 CorrelationComplete 상태")
        fun success_producesCorrelationCompleteState() = runTest {
            val correlation = makeCorrelationAnalysis()
            coEvery { correlationAnalysisRepository.runLatestCorrelationAnalysis(any(), any()) } returns
                Result.success(correlation)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.runCorrelationAnalysis()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<NewAIAnalysisState.CorrelationComplete>(state)
                assertEquals(correlation.id, state.result.id)
            }
        }

        @Test
        @DisplayName("실패 시 Error 상태")
        fun failure_producesErrorState() = runTest {
            coEvery { correlationAnalysisRepository.runLatestCorrelationAnalysis(any(), any()) } returns
                Result.failure(RuntimeException("분석 실패"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.runCorrelationAnalysis()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<NewAIAnalysisState.Error>(state)
                assertTrue(state.message.contains("분석 실패"))
            }
        }

        @Test
        @DisplayName("실행 중 AnalyzingCorrelation 상태 설정")
        fun running_setsAnalyzingCorrelationState() = runTest {
            coEvery { correlationAnalysisRepository.runLatestCorrelationAnalysis(any(), any()) } coAnswers {
                kotlinx.coroutines.delay(100)
                Result.success(makeCorrelationAnalysis())
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test { cancelAndIgnoreRemainingEvents() }

            viewModel.runCorrelationAnalysis()

            viewModel.state.test {
                assertIs<NewAIAnalysisState.AnalyzingCorrelation>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ---------------------------------------------------------------
    // runFullAnalysis() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("runFullAnalysis() 테스트")
    inner class RunFullAnalysisTests {

        @Test
        @DisplayName("API 키 없을 때 Error 상태")
        fun noApiKey_producesErrorState() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.runFullAnalysis()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<NewAIAnalysisState.Error>(state)
                assertTrue(state.message.contains("API 키"))
            }
        }

        @Test
        @DisplayName("성공 시 FullAnalysisComplete 상태")
        fun success_producesFullAnalysisCompleteState() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns "valid-key"
            val fullAnalysis = FullAnalysis(makeCorrelationAnalysis(), makeAIAnalysis(), null)
            coEvery { correlationAnalysisRepository.runFullAnalysis(any(), any()) } returns
                Result.success(fullAnalysis)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.runFullAnalysis()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<NewAIAnalysisState.FullAnalysisComplete>(awaitItem())
            }
        }

        @Test
        @DisplayName("실패 시 Error 상태")
        fun failure_producesErrorState() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns "valid-key"
            coEvery { correlationAnalysisRepository.runFullAnalysis(any(), any()) } returns
                Result.failure(RuntimeException("AI 분석 실패"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.runFullAnalysis()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<NewAIAnalysisState.Error>(state)
                assertTrue(state.message.contains("AI 분석 실패"))
            }
        }
    }

    // ---------------------------------------------------------------
    // startNewChat() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("startNewChat() 테스트")
    inner class StartNewChatTests {

        @Test
        @DisplayName("API 키 없을 때 Error 상태")
        fun noApiKey_producesErrorState() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.startNewChat()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<NewAIAnalysisState.Error>(awaitItem())
            }
        }

        @Test
        @DisplayName("성공 시 ChatActive 상태")
        fun success_producesChatActiveState() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns "valid-key"
            val session = makeChatSession()
            coEvery { chatRepository.createSession(any(), any()) } returns session
            coEvery { chatRepository.createSessionWithAnalysis(any(), any()) } returns session

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.startNewChat()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<NewAIAnalysisState.ChatActive>(state)
                assertEquals(session.id, state.session.id)
            }
        }

        @Test
        @DisplayName("성공 시 currentSession 업데이트")
        fun success_updatesCurrentSession() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns "valid-key"
            val session = makeChatSession()
            coEvery { chatRepository.createSession(any(), any()) } returns session
            coEvery { chatRepository.createSessionWithAnalysis(any(), any()) } returns session

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.startNewChat()
            advanceUntilIdle()

            viewModel.currentSession.test {
                val current = awaitItem()
                assertNotNull(current)
                assertEquals(session.id, current.id)
            }
        }
    }

    // ---------------------------------------------------------------
    // sendMessage() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("sendMessage() 테스트")
    inner class SendMessageTests {

        @Test
        @DisplayName("세션 없을 때 sendMessage() 는 아무것도 하지 않음")
        fun noSession_sendMessageDoesNothing() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val stateBefore = viewModel.state.value

            viewModel.sendMessage("테스트 메시지")
            advanceUntilIdle()

            // State should not change when there's no session
            coVerify(exactly = 0) { chatRepository.sendMessage(any(), any()) }
        }

        @Test
        @DisplayName("API 키 없을 때 sendMessage() 는 ChatError 상태")
        fun noApiKey_sendMessage_producesChatError() = runTest {
            // Start with a valid key so startNewChat() succeeds
            every { apiKeyProvider.getApiKey(any()) } returns "valid-key"

            val session = makeChatSession()
            coEvery { chatRepository.createSession(any(), any()) } returns session
            coEvery { chatRepository.createSessionWithAnalysis(any(), any()) } returns session

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.startNewChat()
            advanceUntilIdle()

            // Revoke the API key, then refresh so _isApiKeyConfigured is updated to false
            every { apiKeyProvider.getApiKey(any()) } returns null
            viewModel.refreshApiKeyState()
            advanceUntilIdle()

            viewModel.sendMessage("테스트")
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<NewAIAnalysisState.ChatError>(awaitItem())
            }
        }

        @Test
        @DisplayName("sendMessage() 성공 시 isSendingMessage false로 리셋")
        fun success_resetsisSendingMessage() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns "valid-key"
            val session = makeChatSession()
            coEvery { chatRepository.createSession(any(), any()) } returns session
            coEvery { chatRepository.createSessionWithAnalysis(any(), any()) } returns session
            coEvery { chatRepository.sendMessage(any(), any()) } returns Result.success(
                ChatMessage(
                    id = "msg-001",
                    sessionId = session.id,
                    role = com.etfmonitor.feature.analysis.domain.model.MessageRole.ASSISTANT,
                    content = "AI 응답",
                    aiProvider = "CLAUDE",
                    aiModel = "claude-3-5-haiku-20241022",
                    tokenCount = 50,
                    timestamp = System.currentTimeMillis()
                )
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.startNewChat()
            advanceUntilIdle()

            viewModel.sendMessage("테스트 메시지")
            advanceUntilIdle()

            viewModel.isSendingMessage.test {
                assertFalse(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // selectMarket() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("selectMarket() 테스트")
    inner class SelectMarketTests {

        @Test
        @DisplayName("selectMarket() 호출 시 selectedMarket 업데이트")
        fun selectMarket_updatesSelectedMarket() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectMarket("KOSDAQ")
            advanceUntilIdle()

            viewModel.selectedMarket.test {
                assertEquals("KOSDAQ", awaitItem())
            }
        }

        @Test
        @DisplayName("selectMarket() 시 최신 결과 재로드")
        fun selectMarket_loadsLatestResults() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectMarket("KOSDAQ")
            advanceUntilIdle()

            coVerify { correlationAnalysisRepository.getLatestCorrelationResult("KOSDAQ") }
        }
    }

    // ---------------------------------------------------------------
    // selectProvider() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("selectProvider() 테스트")
    inner class SelectProviderTests {

        @Test
        @DisplayName("selectProvider() 호출 시 selectedProvider 업데이트")
        fun selectProvider_updatesSelectedProvider() = runTest {
            // Track the "stored" provider so getSelectedProvider() reflects setSelectedProvider() calls
            var storedProvider = AIProvider.CLAUDE
            every { apiKeyProvider.getSelectedProvider() } answers { storedProvider }
            every { apiKeyProvider.setSelectedProvider(any()) } answers {
                storedProvider = firstArg()
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectProvider(AIProvider.GEMINI)
            advanceUntilIdle()

            viewModel.selectedProvider.test {
                assertEquals(AIProvider.GEMINI, awaitItem())
            }
        }

        @Test
        @DisplayName("selectProvider() 시 apiKeyProvider.setSelectedProvider 호출")
        fun selectProvider_callsSetSelectedProvider() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectProvider(AIProvider.GEMINI)

            verify { apiKeyProvider.setSelectedProvider(AIProvider.GEMINI) }
        }
    }

    // ---------------------------------------------------------------
    // clearError() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("clearError() 테스트")
    inner class ClearErrorTests {

        @Test
        @DisplayName("Error 상태에서 clearError() 시 Idle 상태로 전환 (분석 결과 없을 때)")
        fun clearError_noResult_transitionsToIdle() = runTest {
            coEvery { correlationAnalysisRepository.runLatestCorrelationAnalysis(any(), any()) } returns
                Result.failure(RuntimeException("오류"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.runCorrelationAnalysis()
            advanceUntilIdle()

            viewModel.clearError()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<NewAIAnalysisState.Idle>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // closeChat() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("closeChat() 테스트")
    inner class CloseChatTests {

        @Test
        @DisplayName("closeChat() 시 currentSession null로 리셋")
        fun closeChat_resetsCurrentSession() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns "valid-key"
            val session = makeChatSession()
            coEvery { chatRepository.createSession(any(), any()) } returns session
            coEvery { chatRepository.createSessionWithAnalysis(any(), any()) } returns session

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.startNewChat()
            advanceUntilIdle()

            viewModel.closeChat()

            viewModel.currentSession.test {
                assertNull(awaitItem())
            }
        }

        @Test
        @DisplayName("closeChat() 시 chatMessages 초기화")
        fun closeChat_clearsChatMessages() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns "valid-key"
            val session = makeChatSession()
            coEvery { chatRepository.createSession(any(), any()) } returns session
            coEvery { chatRepository.createSessionWithAnalysis(any(), any()) } returns session

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.startNewChat()
            advanceUntilIdle()

            viewModel.closeChat()

            viewModel.chatMessages.test {
                assertTrue(awaitItem().isEmpty())
            }
        }
    }

    // ---------------------------------------------------------------
    // deleteSession() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("deleteSession() 테스트")
    inner class DeleteSessionTests {

        @Test
        @DisplayName("현재 세션 삭제 시 Idle 상태로 전환")
        fun deleteCurrentSession_transitionsToIdle() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns "valid-key"
            val session = makeChatSession("session-001")
            coEvery { chatRepository.createSession(any(), any()) } returns session
            coEvery { chatRepository.createSessionWithAnalysis(any(), any()) } returns session
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.startNewChat()
            advanceUntilIdle()

            viewModel.deleteSession("session-001")
            advanceUntilIdle()

            viewModel.currentSession.test {
                assertNull(awaitItem())
            }
        }

        @Test
        @DisplayName("다른 세션 삭제 시 현재 세션 유지")
        fun deleteOtherSession_currentSessionUnchanged() = runTest {
            every { apiKeyProvider.getApiKey(any()) } returns "valid-key"
            val currentSession = makeChatSession("current-session")
            coEvery { chatRepository.createSession(any(), any()) } returns currentSession
            coEvery { chatRepository.createSessionWithAnalysis(any(), any()) } returns currentSession

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.startNewChat()
            advanceUntilIdle()

            viewModel.deleteSession("other-session")
            advanceUntilIdle()

            viewModel.currentSession.test {
                val session = awaitItem()
                assertNotNull(session)
                assertEquals("current-session", session!!.id)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ---------------------------------------------------------------
    // selectTab() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("selectTab() 테스트")
    inner class SelectTabTests {

        @Test
        @DisplayName("selectTab() 호출 시 selectedTabIndex 업데이트")
        fun selectTab_updatesSelectedTabIndex() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectTab(1)

            viewModel.selectedTabIndex.test {
                assertEquals(1, awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // getAvailableProviders() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getAvailableProviders() 테스트")
    inner class GetAvailableProvidersTests {

        @Test
        @DisplayName("사용 가능한 AI 제공자 목록 반환")
        fun returnsAvailableProviders() = runTest {
            every { aiApiClientFactory.getAvailableProviders() } returns listOf(AIProvider.CLAUDE, AIProvider.GEMINI)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val providers = viewModel.getAvailableProviders()
            assertEquals(2, providers.size)
            assertTrue(providers.contains(AIProvider.CLAUDE))
        }
    }
}
