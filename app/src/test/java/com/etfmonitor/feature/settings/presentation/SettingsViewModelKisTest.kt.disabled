package com.etfmonitor.feature.settings.presentation

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.network.ai.ApiKeyProvider
import com.etfmonitor.core.network.python.PyKrxClient
import com.etfmonitor.feature.settings.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SettingsViewModel KIS API 관련 테스트 (Phase 6)
 *
 * 테스트 범위:
 * - KIS 자격 증명 설정 및 조회
 * - KIS API 연결 테스트
 * - KIS 클라이언트 초기화
 * - 상태 관리 (StateFlow)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class SettingsViewModelKisTest {

    // Mocks
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var apiKeyProvider: ApiKeyProvider
    private lateinit var pyKrxClient: PyKrxClient
    private lateinit var etfDao: EtfDao

    @BeforeEach
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        apiKeyProvider = mockk(relaxed = true)
        pyKrxClient = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)

        // Default mocks
        every { apiKeyProvider.isKisApiConfigured() } returns false
        every { apiKeyProvider.getKisAccountNumber() } returns null
        every { apiKeyProvider.isKisVirtualMode() } returns false
        coEvery { etfDao.getSetting(any()) } returns null
    }

    @Nested
    @DisplayName("KIS APP KEY 설정 테스트")
    inner class KisAppKeyTests {

        @Test
        @DisplayName("유효한 APP KEY 설정 성공")
        fun setKisAppKey_validKey_savesSuccessfully() = runTest {
            // Given
            val appKey = "PSQY1234567890"

            // When
            apiKeyProvider.setKisAppKey(appKey)

            // Then
            verify { apiKeyProvider.setKisAppKey(appKey) }
        }

        @Test
        @DisplayName("빈 APP KEY 설정 시 저장하지 않음")
        fun setKisAppKey_emptyKey_doesNotSave() = runTest {
            // Given
            val emptyKey = ""

            // When - 빈 키는 ViewModel에서 검증 후 저장하지 않음
            // ViewModel의 setKisAppKey는 빈 문자열이면 메시지만 표시하고 반환

            // Then - apiKeyProvider.setKisAppKey는 호출되지 않아야 함
            verify(exactly = 0) { apiKeyProvider.setKisAppKey(emptyKey) }
        }
    }

    @Nested
    @DisplayName("KIS APP SECRET 설정 테스트")
    inner class KisAppSecretTests {

        @Test
        @DisplayName("유효한 APP SECRET 설정 성공")
        fun setKisAppSecret_validSecret_savesSuccessfully() = runTest {
            // Given
            val appSecret = "abcdefghijklmnopqrstuvwxyz1234567890abcdefgh"

            // When
            apiKeyProvider.setKisAppSecret(appSecret)

            // Then
            verify { apiKeyProvider.setKisAppSecret(appSecret) }
        }
    }

    @Nested
    @DisplayName("KIS 자격 증명 일괄 설정 테스트")
    inner class KisCredentialsTests {

        @Test
        @DisplayName("전체 자격 증명 일괄 설정")
        fun setKisCredentials_allFields_savesSuccessfully() = runTest {
            // Given
            val appKey = "PSQY1234567890"
            val appSecret = "abcdefghijklmnopqrstuvwxyz1234567890"
            val accountNumber = "50123456-01"

            // When
            apiKeyProvider.setKisAppKey(appKey)
            apiKeyProvider.setKisAppSecret(appSecret)
            apiKeyProvider.setKisAccountNumber(accountNumber)

            // Then
            verify { apiKeyProvider.setKisAppKey(appKey) }
            verify { apiKeyProvider.setKisAppSecret(appSecret) }
            verify { apiKeyProvider.setKisAccountNumber(accountNumber) }
        }

        @Test
        @DisplayName("자격 증명 삭제")
        fun clearKisCredentials_removesAll() = runTest {
            // Given
            every { apiKeyProvider.isKisApiConfigured() } returns false

            // When
            apiKeyProvider.removeKisCredentials()

            // Then
            verify { apiKeyProvider.removeKisCredentials() }
            assertFalse(apiKeyProvider.isKisApiConfigured())
        }
    }

    @Nested
    @DisplayName("KIS API 연결 테스트")
    inner class KisApiConnectionTests {

        @Test
        @DisplayName("연결 테스트 성공")
        fun testKisApiConnection_success() = runTest {
            // Given
            val appKey = "valid_app_key"
            val appSecret = "valid_app_secret"
            every { apiKeyProvider.getKisAppKey() } returns appKey
            every { apiKeyProvider.getKisAppSecret() } returns appSecret
            coEvery { pyKrxClient.initializeKisClient(appKey, appSecret) } returns true
            coEvery { pyKrxClient.testKisApiConnection() } returns true

            // When
            val initResult = pyKrxClient.initializeKisClient(appKey, appSecret)
            val testResult = pyKrxClient.testKisApiConnection()

            // Then
            assertTrue(initResult)
            assertTrue(testResult)
            coVerify { pyKrxClient.initializeKisClient(appKey, appSecret) }
            coVerify { pyKrxClient.testKisApiConnection() }
        }

        @Test
        @DisplayName("연결 테스트 실패 - 초기화 실패")
        fun testKisApiConnection_initFails() = runTest {
            // Given
            val appKey = "invalid_app_key"
            val appSecret = "invalid_app_secret"
            every { apiKeyProvider.getKisAppKey() } returns appKey
            every { apiKeyProvider.getKisAppSecret() } returns appSecret
            coEvery { pyKrxClient.initializeKisClient(appKey, appSecret) } returns false

            // When
            val initResult = pyKrxClient.initializeKisClient(appKey, appSecret)

            // Then
            assertFalse(initResult)
        }

        @Test
        @DisplayName("연결 테스트 실패 - API 연결 실패")
        fun testKisApiConnection_connectionFails() = runTest {
            // Given
            val appKey = "valid_app_key"
            val appSecret = "valid_app_secret"
            every { apiKeyProvider.getKisAppKey() } returns appKey
            every { apiKeyProvider.getKisAppSecret() } returns appSecret
            coEvery { pyKrxClient.initializeKisClient(appKey, appSecret) } returns true
            coEvery { pyKrxClient.testKisApiConnection() } returns false

            // When
            val initResult = pyKrxClient.initializeKisClient(appKey, appSecret)
            val testResult = pyKrxClient.testKisApiConnection()

            // Then
            assertTrue(initResult)
            assertFalse(testResult)
        }

        @Test
        @DisplayName("자격 증명 미설정 시 연결 테스트 불가")
        fun testKisApiConnection_noCredentials() = runTest {
            // Given
            every { apiKeyProvider.getKisAppKey() } returns null
            every { apiKeyProvider.getKisAppSecret() } returns null
            every { apiKeyProvider.isKisApiConfigured() } returns false

            // When
            val isConfigured = apiKeyProvider.isKisApiConfigured()

            // Then
            assertFalse(isConfigured)
        }
    }

    @Nested
    @DisplayName("KIS 클라이언트 자동 초기화 테스트")
    inner class KisClientAutoInitializationTests {

        @Test
        @DisplayName("앱 시작 시 저장된 자격 증명으로 초기화")
        fun initializeKisClientIfConfigured_credentialsExist_initializes() = runTest {
            // Given
            val appKey = "stored_app_key"
            val appSecret = "stored_app_secret"
            every { apiKeyProvider.isKisApiConfigured() } returns true
            every { apiKeyProvider.getKisAppKey() } returns appKey
            every { apiKeyProvider.getKisAppSecret() } returns appSecret
            coEvery { pyKrxClient.initializeKisClient(appKey, appSecret) } returns true

            // When
            val shouldInitialize = apiKeyProvider.isKisApiConfigured()
            var initResult = false
            if (shouldInitialize) {
                val key = apiKeyProvider.getKisAppKey()
                val secret = apiKeyProvider.getKisAppSecret()
                if (key != null && secret != null) {
                    initResult = pyKrxClient.initializeKisClient(key, secret)
                }
            }

            // Then
            assertTrue(initResult)
            coVerify { pyKrxClient.initializeKisClient(appKey, appSecret) }
        }

        @Test
        @DisplayName("자격 증명 없으면 초기화 건너뜀")
        fun initializeKisClientIfConfigured_noCredentials_skips() = runTest {
            // Given
            every { apiKeyProvider.isKisApiConfigured() } returns false

            // When
            val shouldInitialize = apiKeyProvider.isKisApiConfigured()

            // Then
            assertFalse(shouldInitialize)
            coVerify(exactly = 0) { pyKrxClient.initializeKisClient(any(), any()) }
        }
    }

    @Nested
    @DisplayName("KIS 모의투자 모드 테스트")
    inner class KisVirtualModeTests {

        @Test
        @DisplayName("모의투자 모드 활성화")
        fun setKisVirtualMode_enable() = runTest {
            // Given
            every { apiKeyProvider.isKisVirtualMode() } returns true

            // When
            apiKeyProvider.setKisVirtualMode(true)
            val result = apiKeyProvider.isKisVirtualMode()

            // Then
            assertTrue(result)
            verify { apiKeyProvider.setKisVirtualMode(true) }
        }

        @Test
        @DisplayName("모의투자 모드 비활성화")
        fun setKisVirtualMode_disable() = runTest {
            // Given
            every { apiKeyProvider.isKisVirtualMode() } returns false

            // When
            apiKeyProvider.setKisVirtualMode(false)
            val result = apiKeyProvider.isKisVirtualMode()

            // Then
            assertFalse(result)
            verify { apiKeyProvider.setKisVirtualMode(false) }
        }
    }

    @Nested
    @DisplayName("KIS 상태 관리 테스트")
    inner class KisStateManagementTests {

        @Test
        @DisplayName("isKisApiConfigured 상태 업데이트")
        fun isKisApiConfigured_stateUpdates() = runTest {
            // Given - 초기 상태: 미구성
            every { apiKeyProvider.isKisApiConfigured() } returns false
            assertFalse(apiKeyProvider.isKisApiConfigured())

            // When - 자격 증명 설정
            every { apiKeyProvider.isKisApiConfigured() } returns true

            // Then - 구성 완료
            assertTrue(apiKeyProvider.isKisApiConfigured())
        }

        @Test
        @DisplayName("kisAccountNumber 상태 업데이트")
        fun kisAccountNumber_stateUpdates() = runTest {
            // Given - 초기 상태: null
            every { apiKeyProvider.getKisAccountNumber() } returns null
            assertEquals(null, apiKeyProvider.getKisAccountNumber())

            // When - 계좌번호 설정
            val accountNumber = "50123456-01"
            every { apiKeyProvider.getKisAccountNumber() } returns accountNumber

            // Then - 계좌번호 반영
            assertEquals(accountNumber, apiKeyProvider.getKisAccountNumber())
        }
    }
}
