package com.etfmonitor.feature.ranking.data.repository

import com.etfmonitor.core.network.kiwoom.KiwoomApiClient
import com.etfmonitor.core.network.kiwoom.KiwoomApiError
import com.etfmonitor.core.network.kiwoom.KiwoomApiKeyConfig
import com.etfmonitor.core.network.kiwoom.KiwoomApiKeyProvider
import com.etfmonitor.core.network.kiwoom.KiwoomInvestmentMode
import com.etfmonitor.feature.ranking.domain.model.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * RankingRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - API 키 미설정 시 NoApiKeyError 반환 (getApiConfig 실패 경로)
 * - API 클라이언트 성공 시 RankingResult 반환
 * - API 클라이언트 오류 전파
 * - 각 메서드가 올바른 apiId로 호출되는지 검증
 *   (getOrderBookSurge→ka10021, getVolumeSurge→ka10023,
 *    getDailyVolumeTop→ka10030, getCreditRatioTop→ka10033,
 *    getForeignInstitutionTop→ka90009)
 *
 * 주의: KiwoomApiClient.call()은 Android 환경 의존성이 있으므로
 *       RankingRepository 인터페이스를 직접 mock하여 계층 격리 테스트도 수행.
 */
@DisplayName("RankingRepositoryImpl 테스트")
class RankingRepositoryImplTest {

    private lateinit var apiClient: KiwoomApiClient
    private lateinit var kiwoomApiKeyProvider: KiwoomApiKeyProvider
    private lateinit var json: Json
    private lateinit var repository: RankingRepositoryImpl

    @BeforeEach
    fun setUp() {
        apiClient = mockk()
        kiwoomApiKeyProvider = mockk()
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        repository = RankingRepositoryImpl(apiClient, kiwoomApiKeyProvider, json)
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private fun mockConfigured(mode: KiwoomInvestmentMode = KiwoomInvestmentMode.MOCK) {
        every { kiwoomApiKeyProvider.getConfig() } returns KiwoomApiKeyConfig(
            appKey = "testAppKey",
            secretKey = "testSecretKey",
            investmentMode = mode
        )
    }

    private fun mockNotConfigured() {
        every { kiwoomApiKeyProvider.getConfig() } returns KiwoomApiKeyConfig()
    }

    private fun makeSuccessResult(
        rankingType: RankingType = RankingType.DAILY_VOLUME_TOP
    ) = RankingResult(
        rankingType = rankingType,
        marketType = MarketType.KOSPI,
        exchangeType = ExchangeType.KRX_MOCK,
        items = listOf(
            RankingItem(
                rank = 1,
                ticker = "005930",
                name = "삼성전자",
                currentPrice = 70000L,
                priceChange = 1000L,
                priceChangeSign = "+",
                changeRate = 1.45,
                volume = 5_000_000L
            )
        )
    )

    /** Mock apiClient.call to return a successful result via the parser lambda. */
    private fun <T> mockApiClientSuccess(result: T) {
        coEvery {
            apiClient.call<T>(
                apiId = any(),
                url = any(),
                body = any(),
                appKey = any(),
                secretKey = any(),
                baseUrl = any(),
                parser = any()
            )
        } coAnswers {
            // Invoke the parser lambda with a minimal valid JSON string and return Result.success
            val parser = arg<(String) -> T>(6)
            Result.success(parser(MINIMAL_VALID_JSON))
        }
    }

    private fun mockApiClientFailure(error: Throwable) {
        coEvery {
            apiClient.call<Any>(
                apiId = any(),
                url = any(),
                body = any(),
                appKey = any(),
                secretKey = any(),
                baseUrl = any(),
                parser = any()
            )
        } returns Result.failure(error)
    }

    // ================================================================
    // API 키 미설정 테스트 (NoApiKeyError)
    // ================================================================

    @Nested
    @DisplayName("API 키 미설정 시 NoApiKeyError 반환")
    inner class NoApiKeyTests {

        @Test
        @DisplayName("getOrderBookSurge_whenNotConfigured_returnsNoApiKeyError")
        fun `getOrderBookSurge_whenNotConfigured_returnsNoApiKeyError`() = runTest {
            mockNotConfigured()

            val result = repository.getOrderBookSurge(
                OrderBookSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isFailure)
            assertIs<KiwoomApiError.NoApiKeyError>(result.exceptionOrNull())
        }

        @Test
        @DisplayName("getVolumeSurge_whenNotConfigured_returnsNoApiKeyError")
        fun `getVolumeSurge_whenNotConfigured_returnsNoApiKeyError`() = runTest {
            mockNotConfigured()

            val result = repository.getVolumeSurge(
                VolumeSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isFailure)
            assertIs<KiwoomApiError.NoApiKeyError>(result.exceptionOrNull())
        }

        @Test
        @DisplayName("getDailyVolumeTop_whenNotConfigured_returnsNoApiKeyError")
        fun `getDailyVolumeTop_whenNotConfigured_returnsNoApiKeyError`() = runTest {
            mockNotConfigured()

            val result = repository.getDailyVolumeTop(
                DailyVolumeTopParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isFailure)
            assertIs<KiwoomApiError.NoApiKeyError>(result.exceptionOrNull())
        }

        @Test
        @DisplayName("getCreditRatioTop_whenNotConfigured_returnsNoApiKeyError")
        fun `getCreditRatioTop_whenNotConfigured_returnsNoApiKeyError`() = runTest {
            mockNotConfigured()

            val result = repository.getCreditRatioTop(
                CreditRatioTopParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isFailure)
            assertIs<KiwoomApiError.NoApiKeyError>(result.exceptionOrNull())
        }

        @Test
        @DisplayName("getForeignInstitutionTop_whenNotConfigured_returnsNoApiKeyError")
        fun `getForeignInstitutionTop_whenNotConfigured_returnsNoApiKeyError`() = runTest {
            mockNotConfigured()

            val result = repository.getForeignInstitutionTop(
                ForeignInstitutionTopParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isFailure)
            assertIs<KiwoomApiError.NoApiKeyError>(result.exceptionOrNull())
        }
    }

    // ================================================================
    // API 클라이언트 호출 apiId 검증
    // ================================================================

    @Nested
    @DisplayName("API 클라이언트 호출 파라미터 검증")
    inner class ApiClientCallTests {

        @Test
        @DisplayName("getOrderBookSurge_whenConfigured_callsApiWithKa10021")
        fun `getOrderBookSurge_whenConfigured_callsApiWithKa10021`() = runTest {
            mockConfigured()
            val apiIdSlot = slot<String>()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = capture(apiIdSlot),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.success(makeSuccessResult(RankingType.ORDER_BOOK_SURGE))

            repository.getOrderBookSurge(
                OrderBookSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertEquals("ka10021", apiIdSlot.captured)
        }

        @Test
        @DisplayName("getVolumeSurge_whenConfigured_callsApiWithKa10023")
        fun `getVolumeSurge_whenConfigured_callsApiWithKa10023`() = runTest {
            mockConfigured()
            val apiIdSlot = slot<String>()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = capture(apiIdSlot),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.success(makeSuccessResult(RankingType.VOLUME_SURGE))

            repository.getVolumeSurge(
                VolumeSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertEquals("ka10023", apiIdSlot.captured)
        }

        @Test
        @DisplayName("getDailyVolumeTop_whenConfigured_callsApiWithKa10030")
        fun `getDailyVolumeTop_whenConfigured_callsApiWithKa10030`() = runTest {
            mockConfigured()
            val apiIdSlot = slot<String>()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = capture(apiIdSlot),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.success(makeSuccessResult(RankingType.DAILY_VOLUME_TOP))

            repository.getDailyVolumeTop(
                DailyVolumeTopParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertEquals("ka10030", apiIdSlot.captured)
        }

        @Test
        @DisplayName("getCreditRatioTop_whenConfigured_callsApiWithKa10033")
        fun `getCreditRatioTop_whenConfigured_callsApiWithKa10033`() = runTest {
            mockConfigured()
            val apiIdSlot = slot<String>()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = capture(apiIdSlot),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.success(makeSuccessResult(RankingType.CREDIT_RATIO_TOP))

            repository.getCreditRatioTop(
                CreditRatioTopParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertEquals("ka10033", apiIdSlot.captured)
        }

        @Test
        @DisplayName("getForeignInstitutionTop_whenConfigured_callsApiWithKa90009")
        fun `getForeignInstitutionTop_whenConfigured_callsApiWithKa90009`() = runTest {
            mockConfigured()
            val apiIdSlot = slot<String>()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = capture(apiIdSlot),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.success(makeSuccessResult(RankingType.FOREIGN_INSTITUTION_TOP))

            repository.getForeignInstitutionTop(
                ForeignInstitutionTopParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertEquals("ka90009", apiIdSlot.captured)
        }

        @Test
        @DisplayName("getOrderBookSurge_whenConfigured_callsRkInfoUrl")
        fun `getOrderBookSurge_whenConfigured_callsRkInfoUrl`() = runTest {
            mockConfigured()
            val urlSlot = slot<String>()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = capture(urlSlot),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.success(makeSuccessResult())

            repository.getOrderBookSurge(
                OrderBookSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertEquals("/api/dostk/rkinfo", urlSlot.captured)
        }

        @Test
        @DisplayName("getOrderBookSurge_whenConfigured_passesAppKeyToApiClient")
        fun `getOrderBookSurge_whenConfigured_passesAppKeyToApiClient`() = runTest {
            mockConfigured()
            val appKeySlot = slot<String>()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = any(),
                    body = any(),
                    appKey = capture(appKeySlot),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.success(makeSuccessResult())

            repository.getOrderBookSurge(
                OrderBookSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertEquals("testAppKey", appKeySlot.captured)
        }
    }

    // ================================================================
    // 오류 전파 테스트
    // ================================================================

    @Nested
    @DisplayName("API 오류 전파 테스트")
    inner class ErrorPropagationTests {

        @Test
        @DisplayName("getOrderBookSurge_whenApiClientReturnsNetworkError_propagatesError")
        fun `getOrderBookSurge_whenApiClientReturnsNetworkError_propagatesError`() = runTest {
            mockConfigured()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.failure(KiwoomApiError.NetworkError("네트워크 오류"))

            val result = repository.getOrderBookSurge(
                OrderBookSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isFailure)
            assertIs<KiwoomApiError.NetworkError>(result.exceptionOrNull())
        }

        @Test
        @DisplayName("getVolumeSurge_whenApiClientReturnsAuthError_propagatesError")
        fun `getVolumeSurge_whenApiClientReturnsAuthError_propagatesError`() = runTest {
            mockConfigured()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.failure(KiwoomApiError.AuthError("인증 실패"))

            val result = repository.getVolumeSurge(
                VolumeSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isFailure)
            assertIs<KiwoomApiError.AuthError>(result.exceptionOrNull())
        }

        @Test
        @DisplayName("getDailyVolumeTop_whenApiClientReturnsApiCallError_propagatesError")
        fun `getDailyVolumeTop_whenApiClientReturnsApiCallError_propagatesError`() = runTest {
            mockConfigured()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.failure(KiwoomApiError.ApiCallError(500, "서버 오류"))

            val result = repository.getDailyVolumeTop(
                DailyVolumeTopParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isFailure)
            assertIs<KiwoomApiError.ApiCallError>(result.exceptionOrNull())
        }

        @Test
        @DisplayName("getCreditRatioTop_whenApiClientReturnsTimeoutError_propagatesError")
        fun `getCreditRatioTop_whenApiClientReturnsTimeoutError_propagatesError`() = runTest {
            mockConfigured()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.failure(KiwoomApiError.TimeoutError("타임아웃"))

            val result = repository.getCreditRatioTop(
                CreditRatioTopParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isFailure)
            assertIs<KiwoomApiError.TimeoutError>(result.exceptionOrNull())
        }

        @Test
        @DisplayName("getForeignInstitutionTop_whenApiClientReturnsParseError_propagatesError")
        fun `getForeignInstitutionTop_whenApiClientReturnsParseError_propagatesError`() = runTest {
            mockConfigured()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.failure(KiwoomApiError.ParseError("파싱 오류"))

            val result = repository.getForeignInstitutionTop(
                ForeignInstitutionTopParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isFailure)
            assertIs<KiwoomApiError.ParseError>(result.exceptionOrNull())
        }
    }

    // ================================================================
    // 성공 결과 반환 테스트
    // ================================================================

    @Nested
    @DisplayName("API 성공 시 결과 반환 테스트")
    inner class SuccessResultTests {

        @Test
        @DisplayName("getOrderBookSurge_whenApiClientSucceeds_returnsSuccessResult")
        fun `getOrderBookSurge_whenApiClientSucceeds_returnsSuccessResult`() = runTest {
            mockConfigured()
            val expectedResult = makeSuccessResult(RankingType.ORDER_BOOK_SURGE)

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.success(expectedResult)

            val result = repository.getOrderBookSurge(
                OrderBookSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isSuccess)
            assertEquals(expectedResult, result.getOrNull())
        }

        @Test
        @DisplayName("getDailyVolumeTop_whenApiClientSucceeds_returnsSuccessResult")
        fun `getDailyVolumeTop_whenApiClientSucceeds_returnsSuccessResult`() = runTest {
            mockConfigured()
            val expectedResult = makeSuccessResult(RankingType.DAILY_VOLUME_TOP)

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = any(),
                    parser = any()
                )
            } returns Result.success(expectedResult)

            val result = repository.getDailyVolumeTop(
                DailyVolumeTopParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertTrue(result.isSuccess)
            assertEquals(RankingType.DAILY_VOLUME_TOP, result.getOrNull()?.rankingType)
        }
    }

    // ================================================================
    // PRODUCTION 모드 baseUrl 검증
    // ================================================================

    @Nested
    @DisplayName("투자 모드별 baseUrl 검증")
    inner class BaseUrlTests {

        @Test
        @DisplayName("getOrderBookSurge_inMockMode_usesMockBaseUrl")
        fun `getOrderBookSurge_inMockMode_usesMockBaseUrl`() = runTest {
            mockConfigured(KiwoomInvestmentMode.MOCK)
            val baseUrlSlot = slot<String>()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = capture(baseUrlSlot),
                    parser = any()
                )
            } returns Result.success(makeSuccessResult())

            repository.getOrderBookSurge(
                OrderBookSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX_MOCK
                )
            )

            assertEquals("https://mockapi.kiwoom.com", baseUrlSlot.captured)
        }

        @Test
        @DisplayName("getOrderBookSurge_inProductionMode_usesProductionBaseUrl")
        fun `getOrderBookSurge_inProductionMode_usesProductionBaseUrl`() = runTest {
            mockConfigured(KiwoomInvestmentMode.PRODUCTION)
            val baseUrlSlot = slot<String>()

            coEvery {
                apiClient.call<RankingResult>(
                    apiId = any(),
                    url = any(),
                    body = any(),
                    appKey = any(),
                    secretKey = any(),
                    baseUrl = capture(baseUrlSlot),
                    parser = any()
                )
            } returns Result.success(makeSuccessResult())

            repository.getOrderBookSurge(
                OrderBookSurgeParams(
                    marketType = MarketType.KOSPI,
                    exchangeType = ExchangeType.KRX
                )
            )

            assertEquals("https://api.kiwoom.com", baseUrlSlot.captured)
        }
    }

    companion object {
        /**
         * Minimal JSON that satisfies `findAndParseItemsArray` — it has no data arrays,
         * so the parser returns an empty list. Used when we need a parser to run.
         */
        private const val MINIMAL_VALID_JSON =
            """{"return_code":0,"return_msg":"정상처리"}"""
    }
}
