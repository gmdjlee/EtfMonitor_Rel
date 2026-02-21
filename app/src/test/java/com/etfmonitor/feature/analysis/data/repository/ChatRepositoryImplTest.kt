package com.etfmonitor.feature.analysis.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.AIAnalysisDao
import com.etfmonitor.core.database.AIChatDao
import com.etfmonitor.core.database.CorrelationAnalysisDao
import com.etfmonitor.core.database.entities.AIChatMessage as ChatMessageEntity
import com.etfmonitor.core.database.entities.AIChatSession as ChatSessionEntity
import com.etfmonitor.core.network.ai.AIApiClient
import com.etfmonitor.core.network.ai.AIApiClientFactory
import com.etfmonitor.core.network.ai.AIProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ChatRepositoryImpl 테스트
 *
 * 테스트 범위:
 * - createSession — 세션 생성 및 DB 저장
 * - getSession — 세션 조회 성공/실패
 * - getAllSessions / getSessionsByMarket — Flow 반환
 * - sendMessage — 성공 경로, 세션 없음 실패, AI 실패
 * - getMessages — Flow 반환
 * - deleteSession — 메시지 + 세션 삭제
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class ChatRepositoryImplTest {

    private lateinit var chatDao: AIChatDao
    private lateinit var aiAnalysisDao: AIAnalysisDao
    private lateinit var correlationAnalysisDao: CorrelationAnalysisDao
    private lateinit var aiApiClientFactory: AIApiClientFactory
    private lateinit var aiApiClient: AIApiClient

    private lateinit var repository: ChatRepositoryImpl

    @BeforeEach
    fun setup() {
        chatDao = mockk(relaxed = true)
        aiAnalysisDao = mockk(relaxed = true)
        correlationAnalysisDao = mockk(relaxed = true)
        aiApiClientFactory = mockk(relaxed = true)
        aiApiClient = mockk(relaxed = true)

        every { aiApiClientFactory.getClient() } returns aiApiClient
        every { aiApiClient.provider } returns AIProvider.CLAUDE

        repository = ChatRepositoryImpl(
            chatDao = chatDao,
            aiAnalysisDao = aiAnalysisDao,
            correlationAnalysisDao = correlationAnalysisDao,
            aiApiClientFactory = aiApiClientFactory
        )
    }

    // ========== createSession 테스트 ==========

    @Nested
    @DisplayName("createSession 테스트")
    inner class CreateSessionTests {

        @Test
        @DisplayName("createSession — DB에 세션 삽입 후 도메인 모델 반환")
        fun createSession_insertsSessionAndReturnsDomain() = runTest {
            val sessionSlot = slot<ChatSessionEntity>()
            coEvery { chatDao.insertSession(capture(sessionSlot)) } returns Unit

            val result = repository.createSession("KOSPI", "시장 분석 Q&A")

            assertNotNull(result)
            assertEquals("KOSPI", result.market)
            assertEquals("시장 분석 Q&A", result.title)
            coVerify(exactly = 1) { chatDao.insertSession(any()) }

            val captured = sessionSlot.captured
            assertEquals("KOSPI", captured.market)
            assertEquals("시장 분석 Q&A", captured.title)
        }

        @Test
        @DisplayName("createSession — 반환된 세션 ID가 UUID 형식")
        fun createSession_returnsSessionWithUuidId() = runTest {
            val result = repository.createSession("KOSDAQ", "테스트 세션")

            // UUID 형식 확인 (길이 36, 대시 포함)
            assertTrue(result.id.isNotBlank())
            assertEquals(36, result.id.length)
        }

        @Test
        @DisplayName("createSession — contextData는 null (분석 없는 단순 세션)")
        fun createSession_simpleSession_contextDataIsNull() = runTest {
            val sessionSlot = slot<ChatSessionEntity>()
            coEvery { chatDao.insertSession(capture(sessionSlot)) } returns Unit

            repository.createSession("KOSPI", "단순 대화")

            assertNull(sessionSlot.captured.contextData)
        }
    }

    // ========== getSession 테스트 ==========

    @Nested
    @DisplayName("getSession 테스트")
    inner class GetSessionTests {

        @Test
        @DisplayName("getSession — 존재하는 세션 반환")
        fun getSession_existingSession_returnsDomain() = runTest {
            val sessionId = "test-session-id"
            val entity = createChatSessionEntity(sessionId, "KOSPI", "테스트 세션")
            coEvery { chatDao.getSessionById(sessionId) } returns entity

            val result = repository.getSession(sessionId)

            assertNotNull(result)
            assertEquals(sessionId, result.id)
            assertEquals("KOSPI", result.market)
        }

        @Test
        @DisplayName("getSession — 없는 세션 → null 반환")
        fun getSession_notFound_returnsNull() = runTest {
            coEvery { chatDao.getSessionById(any()) } returns null

            assertNull(repository.getSession("nonexistent-id"))
        }
    }

    // ========== getAllSessions / getSessionsByMarket 테스트 ==========

    @Nested
    @DisplayName("세션 목록 조회 테스트")
    inner class SessionListTests {

        @Test
        @DisplayName("getAllSessions — 모든 세션을 도메인 모델 Flow로 반환")
        fun getAllSessions_returnsMappedSessionFlow() = runTest {
            val entities = listOf(
                createChatSessionEntity("id-1", "KOSPI", "세션 1"),
                createChatSessionEntity("id-2", "KOSDAQ", "세션 2")
            )
            every { chatDao.getAllSessions() } returns flowOf(entities)

            repository.getAllSessions().test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertEquals("id-1", result[0].id)
                assertEquals("id-2", result[1].id)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getSessionsByMarket — 특정 시장 세션 반환")
        fun getSessionsByMarket_returnsMarketFilteredSessions() = runTest {
            val entities = listOf(createChatSessionEntity("id-1", "KOSPI", "KOSPI 세션"))
            every { chatDao.getSessionsByMarket("KOSPI") } returns flowOf(entities)

            repository.getSessionsByMarket("KOSPI").test {
                val result = awaitItem()
                assertEquals(1, result.size)
                assertEquals("KOSPI", result[0].market)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getAllSessions — 빈 목록 처리")
        fun getAllSessions_emptyList_returnsEmptyFlow() = runTest {
            every { chatDao.getAllSessions() } returns flowOf(emptyList())

            repository.getAllSessions().test {
                val result = awaitItem()
                assertTrue(result.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ========== sendMessage 테스트 ==========

    @Nested
    @DisplayName("sendMessage 테스트")
    inner class SendMessageTests {

        @Test
        @DisplayName("sendMessage — 성공 경로: 사용자 메시지 저장 후 AI 응답 저장")
        fun sendMessage_success_savesUserAndAssistantMessages() = runTest {
            val sessionId = "test-session-id"
            val session = createChatSessionEntity(sessionId, "KOSPI", "시장 분석")
            coEvery { chatDao.getSessionById(sessionId) } returns session
            coEvery { chatDao.getRecentMessages(sessionId, any()) } returns emptyList()
            coEvery { chatDao.getMessageCount(sessionId) } returns 2
            coEvery { aiApiClient.chat(any(), any(), any()) } returns Result.success("AI 분석 응답입니다.")

            val result = repository.sendMessage(sessionId, "오늘 시장은 어때요?")

            assertTrue(result.isSuccess)
            // user message + assistant message = 2 insertMessage calls
            coVerify(exactly = 2) { chatDao.insertMessage(any()) }
            coVerify(exactly = 1) { aiApiClient.chat(any(), any(), any()) }
        }

        @Test
        @DisplayName("sendMessage — 세션 없으면 Result.failure")
        fun sendMessage_sessionNotFound_returnsFailure() = runTest {
            coEvery { chatDao.getSessionById(any()) } returns null
            // user message is inserted before session lookup
            coEvery { chatDao.insertMessage(any()) } returns Unit

            val result = repository.sendMessage("nonexistent-session", "질문")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("sendMessage — AI 응답 실패 시 Result.failure")
        fun sendMessage_aiFailure_returnsFailure() = runTest {
            val sessionId = "test-session-id"
            val session = createChatSessionEntity(sessionId, "KOSPI", "세션")
            coEvery { chatDao.getSessionById(sessionId) } returns session
            coEvery { chatDao.getRecentMessages(sessionId, any()) } returns emptyList()
            coEvery { aiApiClient.chat(any(), any(), any()) } returns Result.failure(Exception("AI 서비스 오류"))

            val result = repository.sendMessage(sessionId, "질문")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("sendMessage — 첫 3개 메시지 이내에는 세션 제목 업데이트")
        fun sendMessage_firstFewMessages_updatesSessionTitle() = runTest {
            val sessionId = "test-session-id"
            val session = createChatSessionEntity(sessionId, "KOSPI", "초기 제목")
            coEvery { chatDao.getSessionById(sessionId) } returns session
            coEvery { chatDao.getRecentMessages(sessionId, any()) } returns emptyList()
            coEvery { chatDao.getMessageCount(sessionId) } returns 2 // <= 3
            coEvery { aiApiClient.chat(any(), any(), any()) } returns Result.success("AI 응답")

            repository.sendMessage(sessionId, "오늘 코스피 전망")

            coVerify(exactly = 1) { chatDao.updateSessionTitle(sessionId, any(), any()) }
        }
    }

    // ========== getMessages 테스트 ==========

    @Nested
    @DisplayName("getMessages 테스트")
    inner class GetMessagesTests {

        @Test
        @DisplayName("getMessages — 세션 메시지를 도메인 모델 Flow로 반환")
        fun getMessages_returnsMappedMessageFlow() = runTest {
            val sessionId = "test-session-id"
            val entities = listOf(
                createChatMessageEntity("msg-1", sessionId, "user", "안녕하세요"),
                createChatMessageEntity("msg-2", sessionId, "assistant", "안녕하세요! 도움이 필요하신가요?")
            )
            every { chatDao.getMessagesBySession(sessionId) } returns flowOf(entities)

            repository.getMessages(sessionId).test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertEquals(com.etfmonitor.feature.analysis.domain.model.MessageRole.USER, result[0].role)
                assertEquals(com.etfmonitor.feature.analysis.domain.model.MessageRole.ASSISTANT, result[1].role)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ========== deleteSession 테스트 ==========

    @Nested
    @DisplayName("deleteSession 테스트")
    inner class DeleteSessionTests {

        @Test
        @DisplayName("deleteSession — 메시지 먼저 삭제 후 세션 삭제")
        fun deleteSession_deletesMessagesAndSession() = runTest {
            val sessionId = "test-session-id"
            coEvery { chatDao.deleteMessagesBySession(sessionId) } returns Unit
            coEvery { chatDao.deleteSession(sessionId) } returns Unit

            repository.deleteSession(sessionId)

            coVerify { chatDao.deleteMessagesBySession(sessionId) }
            coVerify { chatDao.deleteSession(sessionId) }
        }
    }

    // ========== askAboutAnalysis 테스트 ==========

    @Test
    @DisplayName("askAboutAnalysis — sendMessage로 위임")
    fun askAboutAnalysis_delegatesToSendMessage() = runTest {
        val sessionId = "test-session-id"
        val session = createChatSessionEntity(sessionId, "KOSPI", "분석 세션")
        coEvery { chatDao.getSessionById(sessionId) } returns session
        coEvery { chatDao.getRecentMessages(sessionId, any()) } returns emptyList()
        coEvery { chatDao.getMessageCount(sessionId) } returns 5
        coEvery { aiApiClient.chat(any(), any(), any()) } returns Result.success("분석 결과 설명")

        val result = repository.askAboutAnalysis(sessionId, "어떤 매수 신호인가요?")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { aiApiClient.chat(any(), any(), any()) }
    }

    // ========== Helpers ==========

    private fun createChatSessionEntity(
        id: String,
        market: String,
        title: String
    ): ChatSessionEntity = ChatSessionEntity(
        id = id,
        title = title,
        market = market,
        analysisDate = null,
        contextData = null,
        messageCount = 0,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private fun createChatMessageEntity(
        id: String,
        sessionId: String,
        role: String,
        content: String
    ): ChatMessageEntity = ChatMessageEntity(
        id = id,
        sessionId = sessionId,
        role = role,
        content = content,
        analysisResultId = null,
        aiProvider = if (role == "assistant") "CLAUDE" else null,
        aiModel = if (role == "assistant") "claude-3-5-sonnet" else null,
        tokenCount = if (role == "assistant") content.length / 4 else null,
        timestamp = System.currentTimeMillis()
    )
}
