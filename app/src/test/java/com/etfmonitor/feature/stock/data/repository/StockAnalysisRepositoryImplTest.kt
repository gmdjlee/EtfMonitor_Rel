package com.etfmonitor.feature.stock.data.repository

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.analysis.model.StockData
import com.etfmonitor.core.database.entities.StockAnalysisWithName
import com.etfmonitor.core.domain.repository.StockDataRepository
import com.etfmonitor.feature.stock.data.datasource.StockAnalysisLocalDataSource
import com.etfmonitor.feature.stock.data.datasource.StockLocalDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * StockAnalysisRepositoryImpl 캐시 TTL 테스트
 *
 * ## 테스트 범위
 * shouldUpdateData() 의 5가지 분기를 getStockAnalysis() 를 통해 간접 검증한다.
 *
 * 검증 전략:
 * - shouldUpdate == true  → stockDataRepository.getStockAnalysisData() 가 호출된다
 * - shouldUpdate == false → stockDataRepository.getStockAnalysisData() 가 호출되지 않는다
 *
 * ## 캐싱 정책 (StockAnalysisRepositoryImpl)
 * 1. 캐시 없음 → 업데이트
 * 2. 24시간 초과 → 업데이트
 * 3. dataEndDate != today → 업데이트
 * 4. dates.size < requestedDays * 0.8 → 업데이트
 * 5. 신선한 데이터, today 포함, 80% 이상 → 업데이트 안 함
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class StockAnalysisRepositoryImplTest {

    private lateinit var analysisLocalDataSource: StockAnalysisLocalDataSource
    private lateinit var stockLocalDataSource: StockLocalDataSource
    private lateinit var stockDataRepository: StockDataRepository

    private lateinit var repository: StockAnalysisRepositoryImpl

    /** 오늘 날짜 (테스트용 고정값) */
    private val today: String
        get() = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    @BeforeEach
    fun setup() {
        analysisLocalDataSource = mockk(relaxed = true)
        stockLocalDataSource = mockk(relaxed = true)
        stockDataRepository = mockk(relaxed = true)

        repository = StockAnalysisRepositoryImpl(
            analysisLocalDataSource = analysisLocalDataSource,
            stockLocalDataSource = stockLocalDataSource,
            stockDataRepository = stockDataRepository
        )
    }

    // ============================================================
    // shouldUpdateData 캐시 TTL 분기 테스트
    // ============================================================

    @Nested
    @DisplayName("캐시 없음 — 항상 업데이트")
    inner class NoCacheTests {

        @Test
        @DisplayName("캐시 없음 → stockDataRepository 호출됨")
        fun `getStockAnalysis_noCachedData_fetchesFromRemote`() = runTest {
            // Given: DB 에 캐시 없음
            coEvery { analysisLocalDataSource.getAnalysisDataWithName(any()) } returns null
            coEvery { stockDataRepository.getStockAnalysisData(any(), any()) } returns null

            // When
            repository.getStockAnalysis("005930", 30)

            // Then: 캐시가 없으면 반드시 원격 fetch 가 실행된다
            coVerify(exactly = 1) { stockDataRepository.getStockAnalysisData("005930", 30) }
        }

        @Test
        @DisplayName("캐시 없음 → 원격 데이터도 없으면 null 반환")
        fun `getStockAnalysis_noCacheAndRemoteFails_returnsNull`() = runTest {
            // Given
            coEvery { analysisLocalDataSource.getAnalysisDataWithName(any()) } returns null
            coEvery { stockDataRepository.getStockAnalysisData(any(), any()) } returns null

            // When
            val result = repository.getStockAnalysis("005930", 30)

            // Then
            assertNull(result, "캐시도 없고 원격 데이터도 없으면 null 을 반환해야 한다")
        }

        @Test
        @DisplayName("캐시 없음 → 원격 데이터 반환 시 StockData 를 반환")
        fun `getStockAnalysis_noCacheRemoteSucceeds_returnsStockData`() = runTest {
            // Given
            val remoteData = createTestStockData("005930", today, datesCount = 30)
            coEvery { analysisLocalDataSource.getAnalysisDataWithName(any()) } returns null
            coEvery { stockDataRepository.getStockAnalysisData(any(), any()) } returns remoteData
            coEvery { stockLocalDataSource.upsertFromHolding(any(), any(), any(), any()) } returns Unit

            // When
            val result = repository.getStockAnalysis("005930", 30)

            // Then
            assertNotNull(result, "원격 데이터가 있으면 StockData 를 반환해야 한다")
        }
    }

    @Nested
    @DisplayName("캐시 만료 — 24시간 초과 시 업데이트")
    inner class CacheExpiryTests {

        @Test
        @DisplayName("lastUpdated 가 25시간 전 → 24시간 초과로 fetch 호출")
        fun `getStockAnalysis_cacheOlderThan24h_fetchesFromRemote`() = runTest {
            // Given: 25시간 전 데이터 (DATA_EXPIRY_HOURS = 24)
            val twentyFiveHoursAgoMs = System.currentTimeMillis() - (25 * 60 * 60 * 1000L)
            val staleCache = createTestCachedData(
                ticker = "005930",
                lastUpdated = twentyFiveHoursAgoMs,
                dataEndDate = today,
                datesCount = 30
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("005930") } returns staleCache
            coEvery { stockDataRepository.getStockAnalysisData(any(), any()) } returns null

            // When
            repository.getStockAnalysis("005930", 30)

            // Then: 24시간 초과 → 원격 fetch 가 실행되어야 한다
            coVerify(exactly = 1) { stockDataRepository.getStockAnalysisData("005930", 30) }
        }

        @Test
        @DisplayName("lastUpdated 가 정확히 24시간 전 → 만료 경계값으로 fetch 호출")
        fun `getStockAnalysis_cacheExactly24hOld_fetchesFromRemote`() = runTest {
            // Given: 정확히 24시간 전 (>= 조건이므로 만료)
            val exactlyTwentyFourHoursAgoMs = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            val staleCache = createTestCachedData(
                ticker = "005930",
                lastUpdated = exactlyTwentyFourHoursAgoMs,
                dataEndDate = today,
                datesCount = 30
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("005930") } returns staleCache
            coEvery { stockDataRepository.getStockAnalysisData(any(), any()) } returns null

            // When
            repository.getStockAnalysis("005930", 30)

            // Then: hoursSinceUpdate >= DATA_EXPIRY_HOURS → 업데이트
            coVerify(exactly = 1) { stockDataRepository.getStockAnalysisData("005930", 30) }
        }

        @Test
        @DisplayName("lastUpdated 가 23시간 전 → 만료 미도달, 다른 조건 충족 시에만 fetch")
        fun `getStockAnalysis_cache23hOldWithFreshData_doesNotFetch`() = runTest {
            // Given: 23시간 전 (만료 미도달) + today 포함 + 충분한 데이터 (80% 이상)
            val twentyThreeHoursAgoMs = System.currentTimeMillis() - (23 * 60 * 60 * 1000L)
            val freshCache = createTestCachedData(
                ticker = "005930",
                lastUpdated = twentyThreeHoursAgoMs,
                dataEndDate = today,
                datesCount = 30  // requestedDays=30, 30 >= 30*0.8=24 → 충분
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("005930") } returns freshCache

            // When
            repository.getStockAnalysis("005930", 30)

            // Then: 모든 캐시 조건 충족 → 원격 fetch 없음
            coVerify(exactly = 0) { stockDataRepository.getStockAnalysisData(any(), any()) }
        }
    }

    @Nested
    @DisplayName("오늘 날짜 미포함 — dataEndDate != today 시 업데이트")
    inner class StaleEndDateTests {

        @Test
        @DisplayName("dataEndDate 가 어제 날짜 → today 불일치로 fetch 호출")
        fun `getStockAnalysis_dataEndDateIsYesterday_fetchesFromRemote`() = runTest {
            // Given: dataEndDate 가 어제 (오늘이 아님)
            val yesterday = java.time.LocalDate.now().minusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val recentCache = createTestCachedData(
                ticker = "005930",
                lastUpdated = System.currentTimeMillis() - (1 * 60 * 60 * 1000L), // 1시간 전 (만료 미도달)
                dataEndDate = yesterday,
                datesCount = 30
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("005930") } returns recentCache
            coEvery { stockDataRepository.getStockAnalysisData(any(), any()) } returns null

            // When
            repository.getStockAnalysis("005930", 30)

            // Then: dataEndDate != today → fetch 실행
            coVerify(exactly = 1) { stockDataRepository.getStockAnalysisData("005930", 30) }
        }

        @Test
        @DisplayName("dataEndDate 가 일주일 전 → today 불일치로 fetch 호출")
        fun `getStockAnalysis_dataEndDateOneWeekAgo_fetchesFromRemote`() = runTest {
            // Given
            val oneWeekAgo = java.time.LocalDate.now().minusDays(7)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val oldCache = createTestCachedData(
                ticker = "000660",
                lastUpdated = System.currentTimeMillis() - (2 * 60 * 60 * 1000L), // 2시간 전
                dataEndDate = oneWeekAgo,
                datesCount = 30
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("000660") } returns oldCache
            coEvery { stockDataRepository.getStockAnalysisData(any(), any()) } returns null

            // When
            repository.getStockAnalysis("000660", 30)

            // Then
            coVerify(exactly = 1) { stockDataRepository.getStockAnalysisData("000660", 30) }
        }
    }

    @Nested
    @DisplayName("데이터 부족 — 요청 일수의 80% 미만 시 업데이트")
    inner class InsufficientDataTests {

        @Test
        @DisplayName("dates.size = 23 (requestedDays=30의 80% 미만=24) → fetch 호출")
        fun `getStockAnalysis_datesCountBelowThreshold_fetchesFromRemote`() = runTest {
            // Given: 30일 요청, 23개 날짜 데이터 (30 * 0.8 = 24 → 23 < 24)
            val sparseCache = createTestCachedData(
                ticker = "005930",
                lastUpdated = System.currentTimeMillis() - (1 * 60 * 60 * 1000L),
                dataEndDate = today,
                datesCount = 23  // < 30 * 0.8 = 24
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("005930") } returns sparseCache
            coEvery { stockDataRepository.getStockAnalysisData(any(), any()) } returns null

            // When
            repository.getStockAnalysis("005930", 30)

            // Then: dates.size (23) < requestedDays * 0.8 (24) → fetch
            coVerify(exactly = 1) { stockDataRepository.getStockAnalysisData("005930", 30) }
        }

        @Test
        @DisplayName("dates.size = 24 (requestedDays=30의 80% 정확히) → fetch 안 함")
        fun `getStockAnalysis_datesCountExactlyAt80Percent_doesNotFetch`() = runTest {
            // Given: 30일 요청, 24개 날짜 데이터 (30 * 0.8 = 24 → 24 >= 24 → 충분)
            val adequateCache = createTestCachedData(
                ticker = "005930",
                lastUpdated = System.currentTimeMillis() - (1 * 60 * 60 * 1000L),
                dataEndDate = today,
                datesCount = 24  // == 30 * 0.8 = 24 (경계값, 충분)
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("005930") } returns adequateCache

            // When
            repository.getStockAnalysis("005930", 30)

            // Then: dates.size (24) >= requestedDays * 0.8 (24) → 업데이트 안 함
            coVerify(exactly = 0) { stockDataRepository.getStockAnalysisData(any(), any()) }
        }

        @Test
        @DisplayName("dates.size = 0 (빈 날짜 목록) → fetch 호출")
        fun `getStockAnalysis_emptyDates_fetchesFromRemote`() = runTest {
            // Given: 날짜 데이터가 아예 없음
            val emptyDatesCache = createTestCachedData(
                ticker = "035720",
                lastUpdated = System.currentTimeMillis() - (1 * 60 * 60 * 1000L),
                dataEndDate = today,
                datesCount = 0
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("035720") } returns emptyDatesCache
            coEvery { stockDataRepository.getStockAnalysisData(any(), any()) } returns null

            // When
            repository.getStockAnalysis("035720", 30)

            // Then
            coVerify(exactly = 1) { stockDataRepository.getStockAnalysisData("035720", 30) }
        }

        @Test
        @DisplayName("requestedDays 를 크게 늘리면 동일한 캐시가 80% 미달로 판정됨")
        fun `getStockAnalysis_largerRequestedDaysMakesCache80PercentInsufficient_fetches`() = runTest {
            // Given: 50개 날짜 데이터 보유
            // requestedDays=60 → 60 * 0.8 = 48 → 50 >= 48 → 충분
            val cache50 = createTestCachedData(
                ticker = "005930",
                lastUpdated = System.currentTimeMillis() - (1 * 60 * 60 * 1000L),
                dataEndDate = today,
                datesCount = 50
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("005930") } returns cache50

            // When: 60일 요청
            repository.getStockAnalysis("005930", 60)

            // Then: 50 >= 60 * 0.8 (48) → 충분 → fetch 안 함
            coVerify(exactly = 0) { stockDataRepository.getStockAnalysisData(any(), any()) }

            // 이번엔 requestedDays=70 으로 확대 → 70 * 0.8 = 56 → 50 < 56 → 부족
            repository.getStockAnalysis("005930", 70)

            // Then: 70일 요청에서 50 < 56 → fetch
            coVerify(exactly = 1) { stockDataRepository.getStockAnalysisData("005930", 70) }
        }
    }

    @Nested
    @DisplayName("캐시 유효 — 업데이트 안 함")
    inner class FreshCacheTests {

        @Test
        @DisplayName("신선 캐시 + today + 80% 이상 → fetch 안 함, 캐시 반환")
        fun `getStockAnalysis_freshCacheWithTodayAnd80Percent_returnsCachedData`() = runTest {
            // Given: 모든 캐시 조건 충족
            val freshCache = createTestCachedData(
                ticker = "005930",
                lastUpdated = System.currentTimeMillis() - (1 * 60 * 60 * 1000L), // 1시간 전
                dataEndDate = today,
                datesCount = 30  // 30 >= 30 * 0.8 = 24 → 충분
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("005930") } returns freshCache

            // When
            val result = repository.getStockAnalysis("005930", 30)

            // Then: 원격 fetch 없이 캐시 데이터 반환
            coVerify(exactly = 0) { stockDataRepository.getStockAnalysisData(any(), any()) }
            assertNotNull(result, "신선한 캐시가 있으면 캐시 데이터를 반환해야 한다")
        }

        @Test
        @DisplayName("캐시 ticker, name, dates 가 StockData 로 올바르게 변환된다")
        fun `getStockAnalysis_freshCache_mapsToCorrectStockData`() = runTest {
            // Given
            val expectedTicker = "005930"
            val expectedName = "삼성전자"
            val expectedDates = listOf(today, "2025-01-14", "2025-01-13")
            val freshCache = StockAnalysisWithName(
                ticker = expectedTicker,
                name = expectedName,
                dates = expectedDates,
                marketCap = listOf(400_000_000L, 395_000_000L, 390_000_000L),
                foreign5d = listOf(1000L, 900L, 800L),
                institution5d = listOf(500L, 450L, 400L),
                lastUpdated = System.currentTimeMillis() - (1 * 60 * 60 * 1000L),
                dataStartDate = "2025-01-13",
                dataEndDate = today
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName(expectedTicker) } returns freshCache

            // When
            val result = repository.getStockAnalysis(expectedTicker, 3)

            // Then
            assertNotNull(result)
            assert(result.ticker == expectedTicker) { "ticker 불일치: ${result.ticker}" }
            assert(result.name == expectedName) { "name 불일치: ${result.name}" }
            assert(result.dates == expectedDates) { "dates 불일치: ${result.dates}" }
        }

        @Test
        @DisplayName("requestedDays 보다 훨씬 많은 데이터 보유 시에도 캐시 반환")
        fun `getStockAnalysis_cacheHasMoreThanRequestedDays_returnsCachedData`() = runTest {
            // Given: 100개 날짜 데이터, 30일만 요청
            val richCache = createTestCachedData(
                ticker = "000660",
                lastUpdated = System.currentTimeMillis() - (30 * 60 * 1000L), // 30분 전
                dataEndDate = today,
                datesCount = 100
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("000660") } returns richCache

            // When
            repository.getStockAnalysis("000660", 30)

            // Then
            coVerify(exactly = 0) { stockDataRepository.getStockAnalysisData(any(), any()) }
        }
    }

    @Nested
    @DisplayName("원격 fetch 실패 시 폴백 동작")
    inner class FallbackTests {

        @Test
        @DisplayName("24시간 초과 캐시 + 원격 실패 → 기존 캐시 반환")
        fun `getStockAnalysis_staleCache_remoteFails_returnsStaleCachedData`() = runTest {
            // Given: 만료된 캐시 존재 + 원격 fetch 실패(null 반환)
            val staleCache = createTestCachedData(
                ticker = "005930",
                lastUpdated = System.currentTimeMillis() - (25 * 60 * 60 * 1000L),
                dataEndDate = today,
                datesCount = 30
            )
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("005930") } returns staleCache
            coEvery { stockDataRepository.getStockAnalysisData(any(), any()) } returns null

            // When
            val result = repository.getStockAnalysis("005930", 30)

            // Then: 원격 실패 시 만료 캐시라도 반환 (null 보다 낫다)
            assertNotNull(result, "원격 fetch 실패 시 만료 캐시를 폴백으로 반환해야 한다")
        }

        @Test
        @DisplayName("캐시 없음 + 원격 성공 → 새 데이터를 DB 에 저장 후 반환")
        fun `getStockAnalysis_noCacheRemoteSuccess_savesAndReturnsData`() = runTest {
            // Given
            val remoteData = createTestStockData("035720", today, datesCount = 30)
            coEvery { analysisLocalDataSource.getAnalysisDataWithName("035720") } returns null
            coEvery { stockDataRepository.getStockAnalysisData("035720", 30) } returns remoteData
            coEvery { stockLocalDataSource.upsertFromHolding(any(), any(), any(), any()) } returns Unit

            // When
            val result = repository.getStockAnalysis("035720", 30)

            // Then: DB 저장 + 종목명 동기화 호출 확인
            coVerify(exactly = 1) { analysisLocalDataSource.insertAnalysisData(any()) }
            coVerify(exactly = 1) { stockLocalDataSource.upsertFromHolding("035720", any(), any(), any()) }
            assertNotNull(result)
        }
    }

    // ============================================================
    // 헬퍼 함수
    // ============================================================

    /**
     * 테스트용 StockAnalysisWithName (캐시 데이터) 를 생성한다.
     */
    private fun createTestCachedData(
        ticker: String,
        lastUpdated: Long,
        dataEndDate: String,
        datesCount: Int,
        dataStartDate: String = "2025-01-01"
    ): StockAnalysisWithName {
        val dates = (0 until datesCount).map { offset ->
            java.time.LocalDate.now().minusDays(offset.toLong())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
        return StockAnalysisWithName(
            ticker = ticker,
            name = "${ticker}종목",
            dates = dates,
            marketCap = List(datesCount) { 400_000_000L - it * 1_000L },
            foreign5d = List(datesCount) { 1000L - it * 10L },
            institution5d = List(datesCount) { 500L - it * 5L },
            lastUpdated = lastUpdated,
            dataStartDate = dataStartDate,
            dataEndDate = dataEndDate
        )
    }

    /**
     * 테스트용 StockData (원격 데이터) 를 생성한다.
     */
    private fun createTestStockData(
        ticker: String,
        dataEndDate: String,
        datesCount: Int
    ): StockData {
        val dates = (0 until datesCount).map { offset ->
            java.time.LocalDate.now().minusDays(offset.toLong())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
        return StockData(
            ticker = ticker,
            name = "${ticker}종목",
            dates = dates,
            marketCap = List(datesCount) { 400_000_000L },
            foreign5d = List(datesCount) { 1000L },
            institution5d = List(datesCount) { 500L }
        )
    }
}
