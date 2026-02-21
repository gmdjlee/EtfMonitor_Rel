package com.etfmonitor.feature.market.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.entities.MarketIndex as MarketIndexEntity
import com.etfmonitor.core.domain.usecase.krx.GetKrxIndexDataUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MarketIndexRepositoryImpl 테스트
 *
 * 테스트 범위:
 * - Flow 조회 메서드 (getAllByMarket, getRecentByMarket, getByMarketAndDateRange)
 * - 단건 조회 (getByMarketAndDate, getByDate)
 * - 카운트 / 날짜 / 업데이트 시간 조회
 * - initializeMarketIndex — 성공, 빈 데이터, kotlin_krx 오류
 * - updateMarketIndex — 성공, 빈 데이터
 * - deleteByMarket / deleteAll 위임 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class MarketIndexRepositoryImplTest {

    private lateinit var dao: MarketIndexDao
    private lateinit var getKrxIndexDataUseCase: GetKrxIndexDataUseCase

    private lateinit var repository: MarketIndexRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        getKrxIndexDataUseCase = mockk(relaxed = true)

        repository = MarketIndexRepositoryImpl(
            dao = dao,
            getKrxIndexDataUseCase = getKrxIndexDataUseCase
        )
    }

    // ========== Flow 조회 테스트 ==========

    @Nested
    @DisplayName("Flow 조회 테스트")
    inner class FlowQueryTests {

        @Test
        @DisplayName("getAllByMarket — 시장별 전체 데이터를 도메인 모델로 변환")
        fun getAllByMarket_returnsMappedDomainList() = runTest {
            val entities = listOf(
                createMarketIndexEntity("KOSPI", "2025-01-15", closePrice = 2800.0),
                createMarketIndexEntity("KOSPI", "2025-01-14", closePrice = 2780.0)
            )
            every { dao.getAllByMarket("KOSPI") } returns flowOf(entities)

            repository.getAllByMarket("KOSPI").test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertEquals("KOSPI", result[0].market)
                assertEquals("2025-01-15", result[0].date)
                assertEquals(2800.0, result[0].closePrice, 0.01)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getRecentByMarket — 최근 N개 데이터 반환")
        fun getRecentByMarket_returnsLimitedData() = runTest {
            val limit = 5
            val entities = (1..limit).map { i ->
                createMarketIndexEntity("KOSDAQ", "2025-01-${15 - i + 1}", closePrice = 900.0 + i)
            }
            every { dao.getRecentByMarket("KOSDAQ", limit) } returns flowOf(entities)

            repository.getRecentByMarket("KOSDAQ", limit).test {
                val result = awaitItem()
                assertEquals(limit, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getByMarketAndDateRange — 날짜 범위 내 데이터 반환")
        fun getByMarketAndDateRange_returnsRangeData() = runTest {
            val entities = listOf(
                createMarketIndexEntity("KOSPI", "2025-01-10", closePrice = 2750.0),
                createMarketIndexEntity("KOSPI", "2025-01-12", closePrice = 2760.0),
                createMarketIndexEntity("KOSPI", "2025-01-15", closePrice = 2800.0)
            )
            every { dao.getByMarketAndDateRange("KOSPI", "2025-01-10", "2025-01-15") } returns flowOf(entities)

            repository.getByMarketAndDateRange("KOSPI", "2025-01-10", "2025-01-15").test {
                val result = awaitItem()
                assertEquals(3, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getAllByMarket — 빈 데이터 흐름 처리")
        fun getAllByMarket_withEmptyData_returnsEmptyList() = runTest {
            every { dao.getAllByMarket("KOSPI") } returns flowOf(emptyList())

            repository.getAllByMarket("KOSPI").test {
                val result = awaitItem()
                assertTrue(result.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ========== 단건 / 메타 조회 테스트 ==========

    @Nested
    @DisplayName("단건 및 메타 조회 테스트")
    inner class SingleQueryTests {

        @Test
        @DisplayName("getByMarketAndDate — 특정 날짜 데이터 반환")
        fun getByMarketAndDate_returnsMarketIndex() = runTest {
            val entity = createMarketIndexEntity("KOSPI", "2025-01-15", closePrice = 2800.0)
            coEvery { dao.getByMarketAndDate("KOSPI", "2025-01-15") } returns entity

            val result = repository.getByMarketAndDate("KOSPI", "2025-01-15")

            assertNotNull(result)
            assertEquals("KOSPI", result.market)
            assertEquals("2025-01-15", result.date)
        }

        @Test
        @DisplayName("getByMarketAndDate — 없으면 null 반환")
        fun getByMarketAndDate_notFound_returnsNull() = runTest {
            coEvery { dao.getByMarketAndDate(any(), any()) } returns null

            assertNull(repository.getByMarketAndDate("KOSPI", "2025-01-15"))
        }

        @Test
        @DisplayName("getByDate — 특정 날짜 전 시장 데이터 반환")
        fun getByDate_returnsAllMarketsForDate() = runTest {
            val entities = listOf(
                createMarketIndexEntity("KOSPI", "2025-01-15"),
                createMarketIndexEntity("KOSDAQ", "2025-01-15")
            )
            coEvery { dao.getByDate("2025-01-15") } returns entities

            val result = repository.getByDate("2025-01-15")

            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("getCountByMarket — 시장 데이터 수 반환")
        fun getCountByMarket_returnsCount() = runTest {
            coEvery { dao.getCountByMarket("KOSPI") } returns 250

            assertEquals(250, repository.getCountByMarket("KOSPI"))
        }

        @Test
        @DisplayName("getLatestDate — 최신 날짜 반환")
        fun getLatestDate_returnsLatestDate() = runTest {
            coEvery { dao.getLatestDate("KOSPI") } returns "2025-01-15"

            assertEquals("2025-01-15", repository.getLatestDate("KOSPI"))
        }

        @Test
        @DisplayName("getLatestDate — 데이터 없으면 null 반환")
        fun getLatestDate_noData_returnsNull() = runTest {
            coEvery { dao.getLatestDate(any()) } returns null

            assertNull(repository.getLatestDate("KOSPI"))
        }

        @Test
        @DisplayName("getLastUpdateTime — 마지막 업데이트 시간 반환")
        fun getLastUpdateTime_returnsTimestamp() = runTest {
            val timestamp = System.currentTimeMillis()
            coEvery { dao.getLastUpdateTime("KOSDAQ") } returns timestamp

            assertEquals(timestamp, repository.getLastUpdateTime("KOSDAQ"))
        }

        @Test
        @DisplayName("hasData — 데이터 있으면 true")
        fun hasData_withData_returnsTrue() = runTest {
            coEvery { dao.getCountByMarket("KOSPI") } returns 1

            assertTrue(repository.hasData("KOSPI"))
        }

        @Test
        @DisplayName("hasData — 데이터 없으면 false")
        fun hasData_noData_returnsFalse() = runTest {
            coEvery { dao.getCountByMarket("KOSPI") } returns 0

            assertFalse(repository.hasData("KOSPI"))
        }
    }

    // ========== initializeMarketIndex 테스트 ==========

    @Nested
    @DisplayName("initializeMarketIndex 테스트")
    inner class InitializeTests {

        @Test
        @DisplayName("성공 경로 — dao.replaceAll 호출 후 Result.success")
        fun initializeMarketIndex_success_storesDataAndReturnsCount() = runTest {
            val entities = listOf(
                createMarketIndexEntity("KOSPI", "2025-01-15"),
                createMarketIndexEntity("KOSDAQ", "2025-01-15")
            )
            coEvery { getKrxIndexDataUseCase(any(), any()) } returns Result.success(entities)

            val result = repository.initializeMarketIndex(30)

            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrNull())
            coVerify(exactly = 1) { dao.replaceAll(entities) }
        }

        @Test
        @DisplayName("kotlin_krx 빈 목록 반환 → Result.failure")
        fun initializeMarketIndex_emptyResult_returnsFailure() = runTest {
            coEvery { getKrxIndexDataUseCase(any(), any()) } returns Result.success(emptyList())

            val result = repository.initializeMarketIndex(30)

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("kotlin_krx 오류 → Result.failure 전파")
        fun initializeMarketIndex_krxError_returnsFailure() = runTest {
            coEvery { getKrxIndexDataUseCase(any(), any()) } returns Result.failure(Exception("KRX network error"))

            val result = repository.initializeMarketIndex(30)

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("예외 발생 → Result.failure")
        fun initializeMarketIndex_exceptionThrown_returnsFailure() = runTest {
            coEvery { getKrxIndexDataUseCase(any(), any()) } throws RuntimeException("Unexpected error")

            val result = repository.initializeMarketIndex(30)

            assertTrue(result.isFailure)
        }
    }

    // ========== updateMarketIndex 테스트 ==========

    @Nested
    @DisplayName("updateMarketIndex 테스트")
    inner class UpdateTests {

        @Test
        @DisplayName("성공 경로 — dao.insertAll 호출 후 Result.success")
        fun updateMarketIndex_success_returnsCount() = runTest {
            val entities = listOf(createMarketIndexEntity("KOSPI", "2025-01-15"))
            coEvery { getKrxIndexDataUseCase.getRecentDays(any()) } returns Result.success(entities)

            val result = repository.updateMarketIndex(30)

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull())
            coVerify(exactly = 1) { dao.insertAll(entities) }
        }

        @Test
        @DisplayName("빈 업데이트 결과 → Result.failure")
        fun updateMarketIndex_emptyResult_returnsFailure() = runTest {
            coEvery { getKrxIndexDataUseCase.getRecentDays(any()) } returns Result.success(emptyList())

            val result = repository.updateMarketIndex(30)

            assertTrue(result.isFailure)
        }
    }

    // ========== 삭제 테스트 ==========

    @Nested
    @DisplayName("삭제 테스트")
    inner class DeleteTests {

        @Test
        @DisplayName("deleteByMarket — dao.deleteByMarket 위임")
        fun deleteByMarket_delegatesToDao() = runTest {
            coEvery { dao.deleteByMarket("KOSPI") } returns Unit

            repository.deleteByMarket("KOSPI")

            coVerify(exactly = 1) { dao.deleteByMarket("KOSPI") }
        }

        @Test
        @DisplayName("deleteAll — dao.deleteAll 위임")
        fun deleteAll_delegatesToDao() = runTest {
            coEvery { dao.deleteAll() } returns Unit

            repository.deleteAll()

            coVerify(exactly = 1) { dao.deleteAll() }
        }
    }

    // ========== Helpers ==========

    private fun createMarketIndexEntity(
        market: String,
        date: String,
        closePrice: Double = 2800.0,
        changeRate: Double = 0.5
    ): MarketIndexEntity = MarketIndexEntity(
        id = "$market-$date",
        market = market,
        date = date,
        closePrice = closePrice,
        openPrice = closePrice - 10.0,
        highPrice = closePrice + 20.0,
        lowPrice = closePrice - 15.0,
        volume = 500_000L,
        changeRate = changeRate,
        lastUpdated = System.currentTimeMillis()
    )
}
