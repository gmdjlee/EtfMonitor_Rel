package com.etfmonitor.feature.stock.data.repository.financial

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.FinancialCacheDao
import com.etfmonitor.core.database.entities.FinancialCache
import com.etfmonitor.core.network.kis.KisApiKeyConfig
import com.etfmonitor.core.network.kis.KisApiKeyProvider
import com.etfmonitor.core.network.kis.InvestmentMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FinancialRepositoryImpl 테스트
 *
 * 테스트 전략:
 * - KIS API 키 미설정 → 즉시 Result.failure (네트워크 불필요)
 * - 캐시 히트 경로 — DAO에서 신선한 캐시 반환 → API 미호출
 * - 캐시 만료 (24시간 초과) → 네트워크 재호출 시도
 * - useCache=false → 캐시 DAO 미조회
 * - clearCache / clearExpiredCache — DAO 위임 및 TTL 검증
 * - OAuth2 토큰 캐시 — 두 번 호출 시 kisApiKeyProvider 재조회 확인
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class FinancialRepositoryImplTest {

    private lateinit var financialCacheDao: FinancialCacheDao
    private lateinit var kisApiKeyProvider: KisApiKeyProvider
    private lateinit var httpClient: OkHttpClient
    private lateinit var json: Json

    private lateinit var repository: FinancialRepositoryImpl

    @BeforeEach
    fun setup() {
        financialCacheDao = mockk(relaxed = true)
        kisApiKeyProvider = mockk(relaxed = true)

        // 짧은 타임아웃으로 실제 네트워크 호출을 빠르게 실패시킴
        httpClient = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()

        json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

        repository = FinancialRepositoryImpl(
            financialCacheDao = financialCacheDao,
            kisApiKeyProvider = kisApiKeyProvider,
            json = json,
            httpClient = httpClient
        )
    }

    // ========== KIS API 키 미설정 테스트 ==========

    @Nested
    @DisplayName("KIS API 키 미설정 테스트")
    inner class ApiKeyNotConfiguredTests {

        @Test
        @DisplayName("getFinancialData — API 키 없으면 즉시 Result.failure (IllegalStateException)")
        fun getFinancialData_noApiKey_returnsIllegalStateException() = runTest {
            every { kisApiKeyProvider.getConfig() } returns KisApiKeyConfig("", "", InvestmentMode.MOCK)

            val result = repository.getFinancialData("005930", "삼성전자", useCache = false)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

        @Test
        @DisplayName("refreshFinancialData — API 키 없으면 즉시 Result.failure")
        fun refreshFinancialData_noApiKey_returnsFailure() = runTest {
            every { kisApiKeyProvider.getConfig() } returns KisApiKeyConfig("", "", InvestmentMode.MOCK)

            val result = repository.refreshFinancialData("005930", "삼성전자")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

        @Test
        @DisplayName("appKey 있지만 appSecret 없으면 isValid() == false → 즉시 실패")
        fun getFinancialData_appKeyOnlyNoSecret_returnsFailure() = runTest {
            every { kisApiKeyProvider.getConfig() } returns KisApiKeyConfig("test-app-key", "", InvestmentMode.MOCK)

            val result = repository.getFinancialData("005930", "삼성전자", useCache = false)

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("appSecret 있지만 appKey 없으면 → 즉시 실패")
        fun getFinancialData_noAppKeyWithSecret_returnsFailure() = runTest {
            every { kisApiKeyProvider.getConfig() } returns KisApiKeyConfig("", "test-app-secret", InvestmentMode.MOCK)

            val result = repository.getFinancialData("005930", "삼성전자", useCache = false)

            assertTrue(result.isFailure)
        }
    }

    // ========== 캐시 히트 테스트 ==========

    @Nested
    @DisplayName("캐시 히트 테스트")
    inner class CacheHitTests {

        @Test
        @DisplayName("유효한 캐시 존재 + useCache=true — API 미호출, 캐시 데이터 반환")
        fun getFinancialData_validCache_returnsCachedDataWithoutNetworkCall() = runTest {
            val validConfig = KisApiKeyConfig("test-app-key", "test-app-secret", InvestmentMode.MOCK)
            every { kisApiKeyProvider.getConfig() } returns validConfig

            val freshCachedAt = System.currentTimeMillis() - 1_000L // 1초 전 (24시간 미만)
            val cache = FinancialCache(
                ticker = "005930",
                name = "삼성전자",
                data = createValidCacheJson(),
                cachedAt = freshCachedAt
            )
            coEvery { financialCacheDao.get("005930") } returns cache

            val result = repository.getFinancialData("005930", "삼성전자", useCache = true)

            // 캐시 JSON 파싱 성공 → Result.success
            assertTrue(result.isSuccess)
            // DAO.get() 호출 확인
            coVerify(exactly = 1) { financialCacheDao.get("005930") }
        }

        @Test
        @DisplayName("캐시 없을 때 useCache=true — DAO.get() 호출 후 네트워크 시도")
        fun getFinancialData_noCache_callsDaoAndProceedsToNetwork() = runTest {
            val validConfig = KisApiKeyConfig("test-app-key", "test-app-secret", InvestmentMode.MOCK)
            every { kisApiKeyProvider.getConfig() } returns validConfig
            coEvery { financialCacheDao.get("005930") } returns null

            // 네트워크 없어서 실패 예상
            repository.getFinancialData("005930", "삼성전자", useCache = true)

            coVerify(exactly = 1) { financialCacheDao.get("005930") }
        }

        @Test
        @DisplayName("만료된 캐시 (25시간 초과) — 네트워크 재호출 시도")
        fun getFinancialData_expiredCache_attemptsNetworkRefresh() = runTest {
            val validConfig = KisApiKeyConfig("test-app-key", "test-app-secret", InvestmentMode.MOCK)
            every { kisApiKeyProvider.getConfig() } returns validConfig

            val expiredCachedAt = System.currentTimeMillis() - (25 * 60 * 60 * 1000L)
            val cache = FinancialCache(
                ticker = "005930",
                name = "삼성전자",
                data = createValidCacheJson(),
                cachedAt = expiredCachedAt
            )
            coEvery { financialCacheDao.get("005930") } returns cache

            // 만료된 캐시 → refreshFinancialData 호출 → 실제 네트워크 필요 → 실패 예상
            val result = repository.getFinancialData("005930", "삼성전자", useCache = true)

            // 만료된 캐시 → 네트워크 시도 → 실패
            assertTrue(result.isFailure)
            // DAO.get() 은 호출됨
            coVerify(exactly = 1) { financialCacheDao.get("005930") }
        }

        @Test
        @DisplayName("useCache=false — DAO.get() 호출 완전 스킵")
        fun getFinancialData_useCacheFalse_doesNotQueryDaoCache() = runTest {
            val validConfig = KisApiKeyConfig("test-app-key", "test-app-secret", InvestmentMode.MOCK)
            every { kisApiKeyProvider.getConfig() } returns validConfig

            // 네트워크 없어서 실패 예상
            repository.getFinancialData("005930", "삼성전자", useCache = false)

            // useCache=false이면 DAO.get() 호출 없음
            coVerify(exactly = 0) { financialCacheDao.get(any()) }
        }

        @Test
        @DisplayName("캐시 JSON 파싱 실패 — 네트워크 재호출 시도 (fallback)")
        fun getFinancialData_cachedDataCorrupted_fallsBackToNetwork() = runTest {
            val validConfig = KisApiKeyConfig("test-app-key", "test-app-secret", InvestmentMode.MOCK)
            every { kisApiKeyProvider.getConfig() } returns validConfig

            val corruptedCache = FinancialCache(
                ticker = "005930",
                name = "삼성전자",
                data = "{ invalid json {{{}",
                cachedAt = System.currentTimeMillis() - 1_000L
            )
            coEvery { financialCacheDao.get("005930") } returns corruptedCache

            // JSON 파싱 실패 → 네트워크 재시도 → 연결 실패 → Result.failure
            val result = repository.getFinancialData("005930", "삼성전자", useCache = true)

            // 파싱 실패 후 네트워크 시도 → 타임아웃으로 실패
            assertTrue(result.isFailure)
        }
    }

    // ========== clearCache 테스트 ==========

    @Nested
    @DisplayName("캐시 삭제 테스트")
    inner class CacheDeleteTests {

        @Test
        @DisplayName("clearCache(ticker) — DAO.delete(ticker) 위임")
        fun clearCache_delegatesToDaoDelete() = runTest {
            coEvery { financialCacheDao.delete("005930") } returns Unit

            repository.clearCache("005930")

            coVerify(exactly = 1) { financialCacheDao.delete("005930") }
        }

        @Test
        @DisplayName("clearCache — 다른 ticker에 영향 없음")
        fun clearCache_doesNotAffectOtherTickers() = runTest {
            repository.clearCache("005930")

            coVerify(exactly = 0) { financialCacheDao.delete("000660") }
        }

        @Test
        @DisplayName("clearExpiredCache — DAO.deleteExpired(threshold) 위임, 24시간 TTL")
        fun clearExpiredCache_delegatesToDaoWithCorrect24hThreshold() = runTest {
            val thresholdSlot = slot<Long>()
            coEvery { financialCacheDao.deleteExpired(capture(thresholdSlot)) } returns Unit

            val beforeCall = System.currentTimeMillis()
            repository.clearExpiredCache()
            val afterCall = System.currentTimeMillis()

            coVerify(exactly = 1) { financialCacheDao.deleteExpired(any()) }

            // threshold = now - 24h, 오차 ±1초 허용
            val cache24h = 24L * 60 * 60 * 1000L
            val expectedMin = beforeCall - cache24h - 1_000L
            val expectedMax = afterCall - cache24h + 1_000L
            assertTrue(
                thresholdSlot.captured in expectedMin..expectedMax,
                "Expected threshold ~${beforeCall - cache24h}, actual=${thresholdSlot.captured}"
            )
        }
    }

    // ========== OAuth2 설정 검증 테스트 ==========

    @Nested
    @DisplayName("OAuth2 설정 및 PRODUCTION URL 테스트")
    inner class OAuth2ConfigTests {

        @Test
        @DisplayName("PRODUCTION 모드 config — isValid() true 인데 네트워크 없으면 실패")
        fun getFinancialData_productionMode_noNetwork_returnsFailure() = runTest {
            every { kisApiKeyProvider.getConfig() } returns KisApiKeyConfig(
                "prod-key", "prod-secret", InvestmentMode.PRODUCTION
            )
            coEvery { financialCacheDao.get(any()) } returns null

            val result = repository.getFinancialData("005930", "삼성전자", useCache = true)

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("두 번 연속 호출 시 kisApiKeyProvider.getConfig() 각 호출에서 조회")
        fun getFinancialData_calledTwice_configRetrievedEachTime() = runTest {
            every { kisApiKeyProvider.getConfig() } returns KisApiKeyConfig("", "", InvestmentMode.MOCK)

            repository.getFinancialData("005930", "삼성전자", useCache = false)
            repository.getFinancialData("000660", "SK하이닉스", useCache = false)

            // 각 호출에서 설정 조회
            coVerify(atLeast = 2) { kisApiKeyProvider.getConfig() }
        }
    }

    // ========== Helpers ==========

    /**
     * JSON 파싱이 가능한 유효한 FinancialDataCache JSON 생성
     * FinancialRepositoryImpl.getFinancialData() 의 캐시 파싱 경로와 호환
     *
     * FinancialDataCache 의 각 필드는 List<...Cache> 타입이므로 JSON 배열([])이어야 한다.
     * {} (object) 를 사용하면 kotlinx.serialization 이 파싱 실패 → 캐시 미사용 경로로 빠짐.
     */
    private fun createValidCacheJson(): String {
        // FinancialDataCache 형식에 맞는 최소 JSON — 모든 컬렉션 필드는 빈 배열
        return """{"ticker":"005930","name":"삼성전자","periods":[],"balanceSheets":[],"incomeStatements":[],"profitabilityRatios":[],"stabilityRatios":[],"growthRatios":[]}"""
    }
}
