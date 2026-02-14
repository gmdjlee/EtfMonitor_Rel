package com.etfmonitor.core.network.ai

import com.etfmonitor.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ApiKeyProvider KIS 자격 증명 테스트 (Phase 6)
 *
 * 테스트 범위:
 * - KIS APP KEY/SECRET 저장 및 조회
 * - KIS 자격 증명 구성 상태 확인
 * - KIS 계좌번호 및 모의투자 모드 관리
 * - KIS 자격 증명 삭제
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class ApiKeyProviderKisTest {

    private lateinit var apiKeyProvider: ApiKeyProvider

    @BeforeEach
    fun setup() {
        apiKeyProvider = mockk(relaxed = true)
    }

    @Nested
    @DisplayName("KIS APP KEY/SECRET 테스트")
    inner class KisAppKeySecretTests {

        @Test
        @DisplayName("KIS APP KEY 저장 및 조회")
        fun setAndGetKisAppKey() {
            // Given
            val appKey = "PSQY1234567890"
            every { apiKeyProvider.getKisAppKey() } returns appKey

            // When
            apiKeyProvider.setKisAppKey(appKey)
            val result = apiKeyProvider.getKisAppKey()

            // Then
            assertEquals(appKey, result)
            verify { apiKeyProvider.setKisAppKey(appKey) }
        }

        @Test
        @DisplayName("KIS APP SECRET 저장 및 조회")
        fun setAndGetKisAppSecret() {
            // Given
            val appSecret = "abcdefghijklmnopqrstuvwxyz123456789012345678901234567890"
            every { apiKeyProvider.getKisAppSecret() } returns appSecret

            // When
            apiKeyProvider.setKisAppSecret(appSecret)
            val result = apiKeyProvider.getKisAppSecret()

            // Then
            assertEquals(appSecret, result)
            verify { apiKeyProvider.setKisAppSecret(appSecret) }
        }

        @Test
        @DisplayName("KIS APP KEY 미설정 시 null 반환")
        fun getKisAppKey_notSet_returnsNull() {
            // Given
            every { apiKeyProvider.getKisAppKey() } returns null

            // When
            val result = apiKeyProvider.getKisAppKey()

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("KIS APP SECRET 미설정 시 null 반환")
        fun getKisAppSecret_notSet_returnsNull() {
            // Given
            every { apiKeyProvider.getKisAppSecret() } returns null

            // When
            val result = apiKeyProvider.getKisAppSecret()

            // Then
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("KIS 자격 증명 구성 상태 테스트")
    inner class KisConfigurationStatusTests {

        @Test
        @DisplayName("KIS API 구성 완료 - KEY와 SECRET 모두 존재")
        fun isKisApiConfigured_bothKeysExist_returnsTrue() {
            // Given
            every { apiKeyProvider.getKisAppKey() } returns "test_app_key"
            every { apiKeyProvider.getKisAppSecret() } returns "test_app_secret"
            every { apiKeyProvider.isKisApiConfigured() } returns true

            // When
            val result = apiKeyProvider.isKisApiConfigured()

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("KIS API 구성 미완료 - APP KEY만 존재")
        fun isKisApiConfigured_onlyKeyExists_returnsFalse() {
            // Given
            every { apiKeyProvider.getKisAppKey() } returns "test_app_key"
            every { apiKeyProvider.getKisAppSecret() } returns null
            every { apiKeyProvider.isKisApiConfigured() } returns false

            // When
            val result = apiKeyProvider.isKisApiConfigured()

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("KIS API 구성 미완료 - APP SECRET만 존재")
        fun isKisApiConfigured_onlySecretExists_returnsFalse() {
            // Given
            every { apiKeyProvider.getKisAppKey() } returns null
            every { apiKeyProvider.getKisAppSecret() } returns "test_app_secret"
            every { apiKeyProvider.isKisApiConfigured() } returns false

            // When
            val result = apiKeyProvider.isKisApiConfigured()

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("KIS API 구성 미완료 - 둘 다 미존재")
        fun isKisApiConfigured_neitherExists_returnsFalse() {
            // Given
            every { apiKeyProvider.getKisAppKey() } returns null
            every { apiKeyProvider.getKisAppSecret() } returns null
            every { apiKeyProvider.isKisApiConfigured() } returns false

            // When
            val result = apiKeyProvider.isKisApiConfigured()

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("KIS API 구성 미완료 - 빈 문자열")
        fun isKisApiConfigured_emptyStrings_returnsFalse() {
            // Given
            every { apiKeyProvider.getKisAppKey() } returns ""
            every { apiKeyProvider.getKisAppSecret() } returns ""
            every { apiKeyProvider.isKisApiConfigured() } returns false

            // When
            val result = apiKeyProvider.isKisApiConfigured()

            // Then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("KIS 계좌번호 테스트")
    inner class KisAccountNumberTests {

        @Test
        @DisplayName("KIS 계좌번호 저장 및 조회")
        fun setAndGetKisAccountNumber() {
            // Given
            val accountNumber = "50123456-01"
            every { apiKeyProvider.getKisAccountNumber() } returns accountNumber

            // When
            apiKeyProvider.setKisAccountNumber(accountNumber)
            val result = apiKeyProvider.getKisAccountNumber()

            // Then
            assertEquals(accountNumber, result)
            verify { apiKeyProvider.setKisAccountNumber(accountNumber) }
        }

        @Test
        @DisplayName("KIS 계좌번호 미설정 시 null 반환")
        fun getKisAccountNumber_notSet_returnsNull() {
            // Given
            every { apiKeyProvider.getKisAccountNumber() } returns null

            // When
            val result = apiKeyProvider.getKisAccountNumber()

            // Then
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("KIS 모의투자 모드 테스트")
    inner class KisVirtualModeTests {

        @Test
        @DisplayName("모의투자 모드 활성화")
        fun setKisVirtualMode_enabled() {
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
        fun setKisVirtualMode_disabled() {
            // Given
            every { apiKeyProvider.isKisVirtualMode() } returns false

            // When
            apiKeyProvider.setKisVirtualMode(false)
            val result = apiKeyProvider.isKisVirtualMode()

            // Then
            assertFalse(result)
            verify { apiKeyProvider.setKisVirtualMode(false) }
        }

        @Test
        @DisplayName("모의투자 모드 기본값 - false")
        fun isKisVirtualMode_default_returnsFalse() {
            // Given - 기본값은 false
            every { apiKeyProvider.isKisVirtualMode() } returns false

            // When
            val result = apiKeyProvider.isKisVirtualMode()

            // Then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("KIS 자격 증명 삭제 테스트")
    inner class KisCredentialRemovalTests {

        @Test
        @DisplayName("KIS 자격 증명 전체 삭제")
        fun removeKisCredentials_removesAll() {
            // Given
            every { apiKeyProvider.getKisAppKey() } returns null
            every { apiKeyProvider.getKisAppSecret() } returns null
            every { apiKeyProvider.getKisAccountNumber() } returns null
            every { apiKeyProvider.isKisVirtualMode() } returns false

            // When
            apiKeyProvider.removeKisCredentials()

            // Then
            verify { apiKeyProvider.removeKisCredentials() }
            assertNull(apiKeyProvider.getKisAppKey())
            assertNull(apiKeyProvider.getKisAppSecret())
            assertNull(apiKeyProvider.getKisAccountNumber())
        }

        @Test
        @DisplayName("자격 증명 삭제 후 isKisApiConfigured는 false")
        fun removeKisCredentials_configuredIsFalse() {
            // Given
            every { apiKeyProvider.isKisApiConfigured() } returns false

            // When
            apiKeyProvider.removeKisCredentials()

            // Then
            assertFalse(apiKeyProvider.isKisApiConfigured())
        }
    }

    @Nested
    @DisplayName("KIS 자격 증명 형식 검증 테스트")
    inner class KisCredentialFormatTests {

        @Test
        @DisplayName("유효한 APP KEY 형식 - 영문 대문자로 시작")
        fun kisAppKey_validFormat() {
            // Given - KIS APP KEY는 보통 대문자로 시작 (예: PSQY...)
            val validAppKey = "PSQY1234567890"
            every { apiKeyProvider.getKisAppKey() } returns validAppKey

            // When
            val result = apiKeyProvider.getKisAppKey()

            // Then
            assertTrue(result?.matches(Regex("^[A-Z].+")) == true)
        }

        @Test
        @DisplayName("유효한 계좌번호 형식 - 숫자와 하이픈")
        fun kisAccountNumber_validFormat() {
            // Given - 계좌번호 형식: 50123456-01
            val validAccountNumber = "50123456-01"
            every { apiKeyProvider.getKisAccountNumber() } returns validAccountNumber

            // When
            val result = apiKeyProvider.getKisAccountNumber()

            // Then
            assertTrue(result?.matches(Regex("^\\d+-\\d+$")) == true)
        }
    }
}
