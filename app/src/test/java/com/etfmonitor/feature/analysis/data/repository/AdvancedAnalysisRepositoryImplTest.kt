package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfCorrelationDao
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.LiquidityAnalysisDao
import com.etfmonitor.core.database.MarketDepositDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.SectorAnalysisDao
import com.etfmonitor.core.database.StockAnalysisDao
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.database.entities.Etf
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.LiquidityAnalysis
import com.etfmonitor.core.database.entities.MarketDeposit
import com.etfmonitor.core.database.entities.MarketIndex
import com.etfmonitor.core.database.entities.SectorAnalysis
import com.etfmonitor.core.database.entities.StockAmountRanking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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
 * AdvancedAnalysisRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - calculateMarketCapWeightedFlow: 성공, 빈 데이터, 예외 처리
 * - analyzeSupplyDemandDivergence: 성공, 빈 데이터
 * - calculateAndSaveLiquidityAnalysis: 예탁금 있음/없음, null 반환
 * - getLatestLiquidityAnalysis: DAO 위임
 * - calculateAndSaveSectorAnalysis: 성공, 빈 데이터
 * - calculateAndSaveEtfCorrelation: ETF 없음, 보유 현황 없음
 * - CancellationException 재전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("AdvancedAnalysisRepositoryImpl 테스트")
class AdvancedAnalysisRepositoryImplTest {

    private lateinit var etfDao: EtfDao
    private lateinit var stockDao: StockDao
    private lateinit var stockAnalysisDao: StockAnalysisDao
    private lateinit var marketDepositDao: MarketDepositDao
    private lateinit var fearGreedDao: FearGreedDao
    private lateinit var marketIndexDao: MarketIndexDao
    private lateinit var sectorAnalysisDao: SectorAnalysisDao
    private lateinit var etfCorrelationDao: EtfCorrelationDao
    private lateinit var liquidityAnalysisDao: LiquidityAnalysisDao

    private lateinit var repository: AdvancedAnalysisRepositoryImpl

    @BeforeEach
    fun setup() {
        etfDao = mockk(relaxed = true)
        stockDao = mockk(relaxed = true)
        stockAnalysisDao = mockk(relaxed = true)
        marketDepositDao = mockk(relaxed = true)
        fearGreedDao = mockk(relaxed = true)
        marketIndexDao = mockk(relaxed = true)
        sectorAnalysisDao = mockk(relaxed = true)
        etfCorrelationDao = mockk(relaxed = true)
        liquidityAnalysisDao = mockk(relaxed = true)

        repository = AdvancedAnalysisRepositoryImpl(
            etfDao = etfDao,
            stockDao = stockDao,
            stockAnalysisDao = stockAnalysisDao,
            marketDepositDao = marketDepositDao,
            fearGreedDao = fearGreedDao,
            marketIndexDao = marketIndexDao,
            sectorAnalysisDao = sectorAnalysisDao,
            etfCorrelationDao = etfCorrelationDao,
            liquidityAnalysisDao = liquidityAnalysisDao
        )
    }

    // ============================================================
    // calculateMarketCapWeightedFlow
    // ============================================================

    @Nested
    @DisplayName("calculateMarketCapWeightedFlow 테스트")
    inner class MarketCapWeightedFlowTests {

        @Test
        @DisplayName("calculateMarketCapWeightedFlow_withStockChanges_returnsValidFlow")
        fun `calculateMarketCapWeightedFlow_withStockChanges_returnsValidFlow`() = runTest {
            // Given
            val stockChanges = listOf(
                createStockAmountRanking("005930", "삼성전자", newEtfCount = 1, totalAmount = 5_000_000_000f),
                createStockAmountRanking("000660", "SK하이닉스", removedEtfCount = 1, totalAmount = 2_000_000_000f)
            )
            coEvery { etfDao.getStockAmountRanking(any(), any(), any()) } returns stockChanges

            // When
            val result = repository.calculateMarketCapWeightedFlow("2026-01-15", "2026-01-14", "ALL")

            // Then
            assertNotNull(result)
            assertEquals("2026-01-15", result.date)
            assertEquals("ALL", result.market)
            assertTrue(result.totalInflow >= 0)
        }

        @Test
        @DisplayName("calculateMarketCapWeightedFlow_withEmptyStockChanges_returnsEmptyFlow")
        fun `calculateMarketCapWeightedFlow_withEmptyStockChanges_returnsEmptyFlow`() = runTest {
            // Given
            coEvery { etfDao.getStockAmountRanking(any(), any(), any()) } returns emptyList()

            // When
            val result = repository.calculateMarketCapWeightedFlow("2026-01-15", "2026-01-14", "ALL")

            // Then: empty flow with correct date
            assertNotNull(result)
            assertEquals("2026-01-15", result.date)
            assertEquals(0L, result.totalInflow)
            assertEquals(0L, result.totalOutflow)
        }

        @Test
        @DisplayName("calculateMarketCapWeightedFlow_whenDaoThrows_returnsEmptyFlow")
        fun `calculateMarketCapWeightedFlow_whenDaoThrows_returnsEmptyFlow`() = runTest {
            // Given
            coEvery { etfDao.getStockAmountRanking(any(), any(), any()) } throws RuntimeException("DB error")

            // When
            val result = repository.calculateMarketCapWeightedFlow("2026-01-15", "2026-01-14", "ALL")

            // Then: exception swallowed, returns empty flow
            assertNotNull(result)
            assertEquals(0L, result.netFlow)
        }
    }

    // ============================================================
    // analyzeSupplyDemandDivergence
    // ============================================================

    @Nested
    @DisplayName("analyzeSupplyDemandDivergence 테스트")
    inner class SupplyDemandDivergenceTests {

        @Test
        @DisplayName("analyzeSupplyDemandDivergence_withEmptyData_returnsEmptyAnalysis")
        fun `analyzeSupplyDemandDivergence_withEmptyData_returnsEmptyAnalysis`() = runTest {
            // Given
            coEvery { stockAnalysisDao.getAllAnalysisDataWithName() } returns emptyList()

            // When
            val result = repository.analyzeSupplyDemandDivergence("2026-01-15", "ALL")

            // Then
            assertNotNull(result)
            assertEquals("2026-01-15", result.date)
            assertEquals(0, result.foreignBullishCount)
            assertEquals(0, result.institutionBullishCount)
        }

        @Test
        @DisplayName("analyzeSupplyDemandDivergence_whenDaoThrows_returnsEmptyAnalysis")
        fun `analyzeSupplyDemandDivergence_whenDaoThrows_returnsEmptyAnalysis`() = runTest {
            // Given
            coEvery { stockAnalysisDao.getAllAnalysisDataWithName() } throws RuntimeException("DB error")

            // When
            val result = repository.analyzeSupplyDemandDivergence("2026-01-15", "ALL")

            // Then: exception swallowed, returns empty analysis
            assertNotNull(result)
            assertEquals(0, result.foreignBullishCount)
        }
    }

    // ============================================================
    // calculateAndSaveLiquidityAnalysis
    // ============================================================

    @Nested
    @DisplayName("calculateAndSaveLiquidityAnalysis 테스트")
    inner class LiquidityAnalysisTests {

        @Test
        @DisplayName("calculateAndSaveLiquidityAnalysis_withDepositData_savesAndReturns")
        fun `calculateAndSaveLiquidityAnalysis_withDepositData_savesAndReturns`() = runTest {
            // Given
            val deposit = createTestMarketDeposit("2026-01-15", 500_000.0)
            coEvery { marketDepositDao.getDepositByDate("2026-01-15") } returns deposit
            coEvery { marketIndexDao.getByMarketAndDate("KOSPI", "2026-01-15") } returns null
            coEvery { marketIndexDao.getByMarketAndDate("KOSDAQ", "2026-01-15") } returns null
            coEvery { liquidityAnalysisDao.getDepositRatioPercentile(any()) } returns 50.0
            coEvery { liquidityAnalysisDao.insert(any()) } returns Unit

            // When
            val result = repository.calculateAndSaveLiquidityAnalysis("2026-01-15")

            // Then
            assertNotNull(result)
            coVerify(exactly = 1) { liquidityAnalysisDao.insert(any()) }
        }

        @Test
        @DisplayName("calculateAndSaveLiquidityAnalysis_withNoDepositData_returnsNull")
        fun `calculateAndSaveLiquidityAnalysis_withNoDepositData_returnsNull`() = runTest {
            // Given: no deposit for date AND no latest deposit
            coEvery { marketDepositDao.getDepositByDate(any()) } returns null
            coEvery { marketDepositDao.getLatestDeposit() } returns null

            // When
            val result = repository.calculateAndSaveLiquidityAnalysis("2026-01-15")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("calculateAndSaveLiquidityAnalysis_withLatestDepositFallback_usesLatestDate")
        fun `calculateAndSaveLiquidityAnalysis_withLatestDepositFallback_usesLatestDate`() = runTest {
            // Given: no deposit for requested date, but latest available
            coEvery { marketDepositDao.getDepositByDate("2026-01-15") } returns null
            coEvery { marketDepositDao.getLatestDeposit() } returns createTestMarketDeposit("2026-01-14", 490_000.0)
            coEvery { marketIndexDao.getByMarketAndDate(any(), any()) } returns null
            coEvery { liquidityAnalysisDao.getDepositRatioPercentile(any()) } returns 45.0
            coEvery { liquidityAnalysisDao.insert(any()) } returns Unit

            // When
            val result = repository.calculateAndSaveLiquidityAnalysis("2026-01-15")

            // Then: falls back to latest deposit
            assertNotNull(result)
        }
    }

    // ============================================================
    // getLatestLiquidityAnalysis
    // ============================================================

    @Test
    @DisplayName("getLatestLiquidityAnalysis_delegatesToDao")
    fun `getLatestLiquidityAnalysis_delegatesToDao`() = runTest {
        // Given
        val analysis = createTestLiquidityAnalysis("2026-01-15")
        coEvery { liquidityAnalysisDao.getLatest() } returns analysis

        // When
        val result = repository.getLatestLiquidityAnalysis()

        // Then
        assertNotNull(result)
        coVerify(exactly = 1) { liquidityAnalysisDao.getLatest() }
    }

    @Test
    @DisplayName("getLatestLiquidityAnalysis_withNoData_returnsNull")
    fun `getLatestLiquidityAnalysis_withNoData_returnsNull`() = runTest {
        // Given
        coEvery { liquidityAnalysisDao.getLatest() } returns null

        // When
        val result = repository.getLatestLiquidityAnalysis()

        // Then
        assertNull(result)
    }

    // ============================================================
    // calculateAndSaveSectorAnalysis
    // ============================================================

    @Nested
    @DisplayName("calculateAndSaveSectorAnalysis 테스트")
    inner class SectorAnalysisTests {

        @Test
        @DisplayName("calculateAndSaveSectorAnalysis_withStockData_savesAndReturnsSectors")
        fun `calculateAndSaveSectorAnalysis_withStockData_savesAndReturnsSectors`() = runTest {
            // Given
            val stockChanges = listOf(
                createStockAmountRanking("005930", "삼성전자", newEtfCount = 1)
            )
            coEvery { etfDao.getStockAmountRanking(any(), any(), any()) } returns stockChanges
            coEvery { sectorAnalysisDao.insertAll(any()) } returns Unit

            // When
            val result = repository.calculateAndSaveSectorAnalysis("2026-01-15", "2026-01-14")

            // Then
            coVerify(exactly = 1) { sectorAnalysisDao.insertAll(any()) }
            assertNotNull(result)
        }

        @Test
        @DisplayName("calculateAndSaveSectorAnalysis_withEmptyData_returnsEmptyList")
        fun `calculateAndSaveSectorAnalysis_withEmptyData_returnsEmptyList`() = runTest {
            // Given
            coEvery { etfDao.getStockAmountRanking(any(), any(), any()) } returns emptyList()
            coEvery { sectorAnalysisDao.insertAll(any()) } returns Unit

            // When
            val result = repository.calculateAndSaveSectorAnalysis("2026-01-15", "2026-01-14")

            // Then
            assertTrue(result.isEmpty())
        }
    }

    // ============================================================
    // calculateAndSaveEtfCorrelation
    // ============================================================

    @Nested
    @DisplayName("calculateAndSaveEtfCorrelation 테스트")
    inner class EtfCorrelationTests {

        @Test
        @DisplayName("calculateAndSaveEtfCorrelation_whenEtf1NotFound_returnsNull")
        fun `calculateAndSaveEtfCorrelation_whenEtf1NotFound_returnsNull`() = runTest {
            // Given
            coEvery { etfDao.getEtf("069500") } returns null
            coEvery { etfDao.getEtf("229200") } returns createTestEtf("229200")

            // When
            val result = repository.calculateAndSaveEtfCorrelation("069500", "229200", "2026-01-15")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("calculateAndSaveEtfCorrelation_whenEtf2NotFound_returnsNull")
        fun `calculateAndSaveEtfCorrelation_whenEtf2NotFound_returnsNull`() = runTest {
            // Given
            coEvery { etfDao.getEtf("069500") } returns createTestEtf("069500")
            coEvery { etfDao.getEtf("229200") } returns null

            // When
            val result = repository.calculateAndSaveEtfCorrelation("069500", "229200", "2026-01-15")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("calculateAndSaveEtfCorrelation_whenHoldingsEmpty_returnsNull")
        fun `calculateAndSaveEtfCorrelation_whenHoldingsEmpty_returnsNull`() = runTest {
            // Given
            coEvery { etfDao.getEtf("069500") } returns createTestEtf("069500")
            coEvery { etfDao.getEtf("229200") } returns createTestEtf("229200")
            coEvery { etfDao.getHoldings("069500", any()) } returns emptyList()
            coEvery { etfDao.getHoldings("229200", any()) } returns emptyList()

            // When
            val result = repository.calculateAndSaveEtfCorrelation("069500", "229200", "2026-01-15")

            // Then
            assertNull(result)
        }
    }

    // ============================================================
    // CancellationException rethrow
    // ============================================================

    @Test
    @DisplayName("calculateMarketCapWeightedFlow_whenCancelled_rethrowsCancellationException")
    fun `calculateMarketCapWeightedFlow_whenCancelled_rethrowsCancellationException`() = runTest {
        // Given
        coEvery { etfDao.getStockAmountRanking(any(), any(), any()) } throws CancellationException("Cancelled")

        // When & Then
        var exceptionCaught: Throwable? = null
        try {
            repository.calculateMarketCapWeightedFlow("2026-01-15", "2026-01-14", "ALL")
        } catch (e: CancellationException) {
            exceptionCaught = e
        }
        assertNotNull(exceptionCaught, "CancellationException must be rethrown")
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun createStockAmountRanking(
        ticker: String,
        name: String,
        newEtfCount: Int = 0,
        removedEtfCount: Int = 0,
        increasedEtfCount: Int = 0,
        decreasedEtfCount: Int = 0,
        totalAmount: Float = 1_000_000_000f
    ) = StockAmountRanking(
        stockTicker = ticker,
        stockName = name,
        totalAmount = totalAmount,
        etfCount = 5,
        maxWeight = 10.0f,
        etfList = ticker,
        newEtfCount = newEtfCount,
        removedEtfCount = removedEtfCount,
        increasedEtfCount = increasedEtfCount,
        decreasedEtfCount = decreasedEtfCount
    )

    private fun createTestEtf(ticker: String) = Etf(
        ticker = ticker,
        name = "KODEX $ticker"
    )

    private fun createTestMarketDeposit(date: String, depositAmount: Double) = MarketDeposit(
        date = date,
        depositAmount = depositAmount,
        depositChange = 5_000.0,
        creditAmount = 20_000.0,
        creditChange = -500.0,
        lastUpdated = System.currentTimeMillis()
    )

    private fun createTestLiquidityAnalysis(date: String) = LiquidityAnalysis(
        date = date,
        depositAmount = 500_000.0,
        creditAmount = 20_000.0,
        totalMarketCap = 2_500_000_000L,
        kospiMarketCap = 2_000_000_000L,
        kosdaqMarketCap = 500_000_000L,
        depositToMarketCapRatio = 2.0,
        creditToDepositRatio = 4.0,
        depositChange = 5_000.0,
        creditChange = -500.0,
        riskLevel = "MEDIUM",
        signal = "NEUTRAL",
        historicalPercentile = 50.0
    )
}
