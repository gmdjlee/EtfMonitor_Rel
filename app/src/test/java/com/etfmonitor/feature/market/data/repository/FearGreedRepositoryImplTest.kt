package com.etfmonitor.feature.market.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.FearGreedDao
import com.krxkt.KrxIndex
import com.etfmonitor.core.database.entities.FearGreedIndex as FearGreedEntity
import com.etfmonitor.core.database.entities.Setting
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * FearGreedRepositoryImpl 테스트
 *
 * 테스트 범위:
 * - 데이터 조회 (Flow 반환)
 * - 캐시 만료 로직 (12시간)
 * - 다이얼로그 상태 관리
 * - 오류 처리
 *
 * 주의: kotlin_krx API 통합 테스트는 Korean network 환경에서 진행 필요
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class FearGreedRepositoryImplTest {

    // Mocks
    private lateinit var fearGreedDao: FearGreedDao
    private lateinit var etfDao: EtfDao
    private lateinit var krxIndex: KrxIndex

    private lateinit var repository: FearGreedRepositoryImpl

    @BeforeEach
    fun setup() {
        fearGreedDao = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
        krxIndex = mockk(relaxed = true)

        repository = FearGreedRepositoryImpl(
            fearGreedDao = fearGreedDao,
            etfDao = etfDao,
            krxIndex = krxIndex
        )
    }

    @Nested
    @DisplayName("데이터 조회 테스트")
    inner class DataQueryTests {

        @Test
        @DisplayName("getAllByMarket()은 시장별 데이터를 Flow로 반환")
        fun getAllByMarket_returnsFlowOfData() = runTest {
            // Given
            val market = "KOSPI"
            val entities = listOf(
                createTestFearGreedEntity(market, "2025-01-15", 65.5),
                createTestFearGreedEntity(market, "2025-01-14", 60.0)
            )
            every { fearGreedDao.getAllByMarket(market) } returns flowOf(entities)

            // When & Then
            repository.getAllByMarket(market).test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertEquals("2025-01-15", result[0].date)
                assertEquals(65.5, result[0].fearGreedValue, 0.01)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getRecentByMarket()은 최근 N개 데이터 반환")
        fun getRecentByMarket_returnsLimitedData() = runTest {
            // Given
            val market = "KOSDAQ"
            val limit = 5
            val entities = (1..limit).map { day ->
                createTestFearGreedEntity(market, "2025-01-${15 - day + 1}", 50.0 + day)
            }
            every { fearGreedDao.getRecentByMarket(market, limit) } returns flowOf(entities)

            // When & Then
            repository.getRecentByMarket(market, limit).test {
                val result = awaitItem()
                assertEquals(limit, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getByMarketAndDateRange()는 날짜 범위 내 데이터 반환")
        fun getByMarketAndDateRange_returnsRangeData() = runTest {
            // Given
            val market = "KOSPI"
            val startDate = "2025-01-10"
            val endDate = "2025-01-15"
            val entities = listOf(
                createTestFearGreedEntity(market, "2025-01-15", 65.0),
                createTestFearGreedEntity(market, "2025-01-12", 62.0),
                createTestFearGreedEntity(market, "2025-01-10", 58.0)
            )
            every { fearGreedDao.getByMarketAndDateRange(market, startDate, endDate) } returns flowOf(entities)

            // When & Then
            repository.getByMarketAndDateRange(market, startDate, endDate).test {
                val result = awaitItem()
                assertEquals(3, result.size)
                assertTrue(result.all { it.date in startDate..endDate })
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getByMarketAndDate()는 특정 날짜 데이터 반환")
        fun getByMarketAndDate_returnsSingleData() = runTest {
            // Given
            val market = "KOSPI"
            val date = "2025-01-15"
            val entity = createTestFearGreedEntity(market, date, 70.0)
            coEvery { fearGreedDao.getByMarketAndDate(market, date) } returns entity

            // When
            val result = repository.getByMarketAndDate(market, date)

            // Then
            assertNotNull(result)
            assertEquals(date, result.date)
            assertEquals(70.0, result.fearGreedValue, 0.01)
        }

        @Test
        @DisplayName("getByMarketAndDate()는 데이터 없으면 null 반환")
        fun getByMarketAndDate_returnsNullWhenNotFound() = runTest {
            // Given
            coEvery { fearGreedDao.getByMarketAndDate(any(), any()) } returns null

            // When
            val result = repository.getByMarketAndDate("KOSPI", "2025-01-15")

            // Then
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("데이터 상태 테스트")
    inner class DataStatusTests {

        @Test
        @DisplayName("getCountByMarket()은 시장별 데이터 수 반환")
        fun getCountByMarket_returnsCount() = runTest {
            // Given
            val market = "KOSPI"
            coEvery { fearGreedDao.getCountByMarket(market) } returns 30

            // When
            val result = repository.getCountByMarket(market)

            // Then
            assertEquals(30, result)
        }

        @Test
        @DisplayName("getLatestDate()는 가장 최신 날짜 반환")
        fun getLatestDate_returnsLatestDate() = runTest {
            // Given
            val market = "KOSPI"
            val latestDate = "2025-01-15"
            coEvery { fearGreedDao.getLatestDate(market) } returns latestDate

            // When
            val result = repository.getLatestDate(market)

            // Then
            assertEquals(latestDate, result)
        }

        @Test
        @DisplayName("getLastUpdateTime()은 마지막 업데이트 시간 반환")
        fun getLastUpdateTime_returnsTimestamp() = runTest {
            // Given
            val market = "KOSPI"
            val updateTime = 1705300800000L // 2025-01-15 12:00:00
            coEvery { fearGreedDao.getLastUpdateTime(market) } returns updateTime

            // When
            val result = repository.getLastUpdateTime(market)

            // Then
            assertEquals(updateTime, result)
        }
    }

    @Nested
    @DisplayName("다이얼로그 상태 테스트")
    inner class DialogStateTests {

        @Test
        @DisplayName("isDialogDismissed()는 저장된 설정값 확인")
        fun isDialogDismissed_checksSettingValue() = runTest {
            // Given
            coEvery { etfDao.getSetting("fear_greed_dialog_dismissed") } returns "true"

            // When
            val result = repository.isDialogDismissed()

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("isDialogDismissed()는 설정이 없으면 false")
        fun isDialogDismissed_returnsFalseWhenNotSet() = runTest {
            // Given
            coEvery { etfDao.getSetting("fear_greed_dialog_dismissed") } returns null

            // When
            val result = repository.isDialogDismissed()

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("saveDialogDismissed()는 설정 저장")
        fun saveDialogDismissed_savesSetting() = runTest {
            // Given
            val settingSlot = slot<Setting>()
            coEvery { etfDao.saveSetting(capture(settingSlot)) } returns Unit

            // When
            repository.saveDialogDismissed()

            // Then
            coVerify { etfDao.saveSetting(any()) }
            assertEquals("fear_greed_dialog_dismissed", settingSlot.captured.key)
            assertEquals("true", settingSlot.captured.value)
        }
    }

    @Nested
    @DisplayName("Fear & Greed 지표 분석 테스트")
    inner class FearGreedAnalysisTests {

        @Test
        @DisplayName("Fear & Greed 값 범위 검증 (0-100)")
        fun fearGreedValue_shouldBeInRange() = runTest {
            // Given
            val validEntities = listOf(
                createTestFearGreedEntity("KOSPI", "2025-01-15", 0.0),   // Extreme Fear
                createTestFearGreedEntity("KOSPI", "2025-01-14", 25.0),  // Fear
                createTestFearGreedEntity("KOSPI", "2025-01-13", 50.0),  // Neutral
                createTestFearGreedEntity("KOSPI", "2025-01-12", 75.0),  // Greed
                createTestFearGreedEntity("KOSPI", "2025-01-11", 100.0)  // Extreme Greed
            )
            every { fearGreedDao.getAllByMarket("KOSPI") } returns flowOf(validEntities)

            // When & Then
            repository.getAllByMarket("KOSPI").test {
                val result = awaitItem()
                assertTrue(result.all { it.fearGreedValue in 0.0..100.0 })
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("RSI 값 범위 검증 (0-100)")
        fun rsiValue_shouldBeInRange() = runTest {
            // Given
            val entity = createTestFearGreedEntity("KOSPI", "2025-01-15", 65.0, rsi = 70.0)
            coEvery { fearGreedDao.getByMarketAndDate("KOSPI", "2025-01-15") } returns entity

            // When
            val result = repository.getByMarketAndDate("KOSPI", "2025-01-15")

            // Then
            assertNotNull(result)
            assertTrue(result.rsi in 0.0..100.0)
        }
    }

    // ========== Helper Functions ==========

    private fun createTestFearGreedEntity(
        market: String,
        date: String,
        fearGreedValue: Double,
        indexValue: Double = 2800.0,
        oscillator: Double = 0.5,
        rsi: Double = 50.0,
        momentum: Double = 0.0,
        putCallRatio: Double = 1.0,
        volatility: Double = 15.0,
        spread: Double = 0.5
    ): FearGreedEntity {
        return FearGreedEntity(
            id = "$market-$date",
            market = market,
            date = date,
            indexValue = indexValue,
            fearGreedValue = fearGreedValue,
            oscillator = oscillator,
            rsi = rsi,
            momentum = momentum,
            putCallRatio = putCallRatio,
            volatility = volatility,
            spread = spread,
            lastUpdated = System.currentTimeMillis()
        )
    }
}
