package com.etfmonitor.feature.stock.data.repository

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.HoldingTimeSeries
import com.etfmonitor.feature.stock.data.datasource.StockStatisticsLocalDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * StockTrendRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - getStockTrend: 성공 (시계열 있음 + 종목명 조회)
 * - getStockTrend: 시계열 없음 → null 반환
 * - getStockTrend: 종목명 없음 → ticker 폴백
 * - getStockTrend: 보유 현황 없음 → ticker 폴백
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("StockTrendRepositoryImpl 테스트")
class StockTrendRepositoryImplTest {

    private lateinit var localDataSource: StockStatisticsLocalDataSource
    private lateinit var repository: StockTrendRepositoryImpl

    @BeforeEach
    fun setup() {
        localDataSource = mockk(relaxed = true)
        repository = StockTrendRepositoryImpl(localDataSource)
    }

    // ============================================================
    // getStockTrend
    // ============================================================

    @Nested
    @DisplayName("getStockTrend 테스트")
    inner class GetStockTrendTests {

        @Test
        @DisplayName("getStockTrend_withValidData_returnsStockTrend")
        fun `getStockTrend_withValidData_returnsStockTrend`() = runTest {
            // Given
            val etfTicker = "069500"
            val stockTicker = "005930"
            val timeSeries = listOf(
                HoldingTimeSeries(date = "2026-01-15", weight = 30.5f, amount = 10_000_000f),
                HoldingTimeSeries(date = "2026-01-22", weight = 31.0f, amount = 10_200_000f)
            )
            val firstDateHoldings = listOf(
                createTestHolding(etfTicker, stockTicker, "삼성전자", "2026-01-15")
            )

            coEvery { localDataSource.getHoldingTimeSeries(etfTicker, stockTicker) } returns timeSeries
            coEvery { localDataSource.getHoldings(etfTicker, "2026-01-15") } returns firstDateHoldings

            // When
            val result = repository.getStockTrend(etfTicker, stockTicker)

            // Then
            assertNotNull(result)
            assertEquals(etfTicker, result.etfTicker)
            assertEquals(stockTicker, result.stockTicker)
            assertEquals("삼성전자", result.stockName)
            assertEquals(2, result.timeSeries.size)
        }

        @Test
        @DisplayName("getStockTrend_withEmptyTimeSeries_returnsNull")
        fun `getStockTrend_withEmptyTimeSeries_returnsNull`() = runTest {
            // Given
            coEvery { localDataSource.getHoldingTimeSeries(any(), any()) } returns emptyList()

            // When
            val result = repository.getStockTrend("069500", "005930")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("getStockTrend_whenHoldingNotFound_usesTickerAsName")
        fun `getStockTrend_whenHoldingNotFound_usesTickerAsName`() = runTest {
            // Given: time series exists but no holding for the first date
            val etfTicker = "069500"
            val stockTicker = "005930"
            val timeSeries = listOf(
                HoldingTimeSeries(date = "2026-01-15", weight = 30.5f, amount = 10_000_000f)
            )

            coEvery { localDataSource.getHoldingTimeSeries(etfTicker, stockTicker) } returns timeSeries
            // No matching holding for stock ticker
            coEvery { localDataSource.getHoldings(etfTicker, "2026-01-15") } returns emptyList()

            // When
            val result = repository.getStockTrend(etfTicker, stockTicker)

            // Then: ticker used as fallback name
            assertNotNull(result)
            assertEquals(stockTicker, result.stockName)
        }

        @Test
        @DisplayName("getStockTrend_preservesTimeSeriesOrder")
        fun `getStockTrend_preservesTimeSeriesOrder`() = runTest {
            // Given
            val etfTicker = "069500"
            val stockTicker = "005930"
            val timeSeries = listOf(
                HoldingTimeSeries(date = "2026-01-01", weight = 28.0f, amount = 9_000_000f),
                HoldingTimeSeries(date = "2026-01-08", weight = 29.5f, amount = 9_500_000f),
                HoldingTimeSeries(date = "2026-01-15", weight = 30.5f, amount = 10_000_000f)
            )
            coEvery { localDataSource.getHoldingTimeSeries(etfTicker, stockTicker) } returns timeSeries
            coEvery { localDataSource.getHoldings(any(), any()) } returns listOf(
                createTestHolding(etfTicker, stockTicker, "삼성전자", "2026-01-01")
            )

            // When
            val result = repository.getStockTrend(etfTicker, stockTicker)

            // Then
            assertNotNull(result)
            assertEquals(3, result.timeSeries.size)
        }

        @Test
        @DisplayName("getStockTrend_withMultipleHoldingsFindsCorrectStock")
        fun `getStockTrend_withMultipleHoldingsFindsCorrectStock`() = runTest {
            // Given: multiple holdings for first date, different stock tickers
            val etfTicker = "069500"
            val stockTicker = "005930"
            val timeSeries = listOf(
                HoldingTimeSeries(date = "2026-01-15", weight = 30.5f, amount = 10_000_000f)
            )
            val holdingsForDate = listOf(
                createTestHolding(etfTicker, "000660", "SK하이닉스", "2026-01-15"),
                createTestHolding(etfTicker, stockTicker, "삼성전자", "2026-01-15"),
                createTestHolding(etfTicker, "035420", "NAVER", "2026-01-15")
            )

            coEvery { localDataSource.getHoldingTimeSeries(etfTicker, stockTicker) } returns timeSeries
            coEvery { localDataSource.getHoldings(etfTicker, "2026-01-15") } returns holdingsForDate

            // When
            val result = repository.getStockTrend(etfTicker, stockTicker)

            // Then: correct stock name found among multiple holdings
            assertNotNull(result)
            assertEquals("삼성전자", result.stockName)
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun createTestHolding(
        etfTicker: String,
        stockTicker: String,
        stockName: String,
        date: String,
        weight: Float = 30.5f,
        amount: Float = 10_000_000f
    ): Holding = Holding.create(
        etfTicker = etfTicker,
        stockTicker = stockTicker,
        stockName = stockName,
        date = date,
        weight = weight,
        amount = amount
    )
}
