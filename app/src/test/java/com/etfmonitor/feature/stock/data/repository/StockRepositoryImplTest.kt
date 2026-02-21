package com.etfmonitor.feature.stock.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.entities.Stock as StockEntity
import com.etfmonitor.core.domain.repository.StockDataRepository
import com.etfmonitor.feature.stock.data.datasource.StockLocalDataSource
import com.etfmonitor.feature.stock.domain.model.Stock
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * StockRepositoryImpl 테스트
 *
 * 테스트 범위:
 * - getAllStocks / searchStocks / getEtfHoldingStocks — Flow 반환 및 도메인 매핑
 * - getStock / getStockName — 단건 조회
 * - syncFromHolding / syncFromHoldings — 동기화 성공 및 예외 처리
 * - initializeStocks — 성공, 빈 목록 실패, 예외 처리
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class StockRepositoryImplTest {

    private lateinit var localDataSource: StockLocalDataSource
    private lateinit var stockDataRepository: StockDataRepository

    private lateinit var repository: StockRepositoryImpl

    @BeforeEach
    fun setup() {
        localDataSource = mockk(relaxed = true)
        stockDataRepository = mockk(relaxed = true)

        repository = StockRepositoryImpl(
            localDataSource = localDataSource,
            stockDataRepository = stockDataRepository
        )
    }

    // ========== 조회 테스트 ==========

    @Nested
    @DisplayName("종목 조회 테스트")
    inner class QueryTests {

        @Test
        @DisplayName("getAllStocks — 전체 종목 목록을 도메인 모델로 변환하여 반환")
        fun getAllStocks_returnsMappedDomainList() = runTest {
            val entities = listOf(
                createStockEntity("005930", "삼성전자", "KOSPI"),
                createStockEntity("000660", "SK하이닉스", "KOSPI"),
                createStockEntity("035720", "카카오", "KOSDAQ")
            )
            every { localDataSource.getAllStocks() } returns flowOf(entities)

            repository.getAllStocks().test {
                val result = awaitItem()
                assertEquals(3, result.size)
                assertEquals("005930", result[0].ticker)
                assertEquals("삼성전자", result[0].name)
                assertEquals("KOSPI", result[0].market)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("searchStocks — 검색어로 필터링된 종목 반환")
        fun searchStocks_returnsFilteredStocks() = runTest {
            val searchQuery = "삼성"
            val entities = listOf(
                createStockEntity("005930", "삼성전자", "KOSPI"),
                createStockEntity("005380", "현대차", "KOSPI")
            )
            every { localDataSource.searchStocks(searchQuery) } returns flowOf(entities)

            repository.searchStocks(searchQuery).test {
                val result = awaitItem()
                assertEquals(2, result.size)
                cancelAndIgnoreRemainingEvents()
            }
            every { localDataSource.searchStocks(searchQuery) } // verify argument passed correctly
        }

        @Test
        @DisplayName("getEtfHoldingStocks — ETF 보유 종목만 반환")
        fun getEtfHoldingStocks_returnsEtfHoldingStocksOnly() = runTest {
            val entities = listOf(
                createStockEntity("005930", "삼성전자", "KOSPI", isEtfHolding = true)
            )
            every { localDataSource.getEtfHoldingStocks() } returns flowOf(entities)

            repository.getEtfHoldingStocks().test {
                val result = awaitItem()
                assertEquals(1, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getStocksByMarket — 특정 시장 종목 반환")
        fun getStocksByMarket_returnsMarketSpecificStocks() = runTest {
            val market = "KOSDAQ"
            val entities = listOf(
                createStockEntity("035720", "카카오", market),
                createStockEntity("293490", "카카오페이", market)
            )
            every { localDataSource.getStocksByMarket(market) } returns flowOf(entities)

            repository.getStocksByMarket(market).test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertTrue(result.all { it.market == market })
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getStock — 특정 종목코드로 단건 조회")
        fun getStock_withValidTicker_returnsStock() = runTest {
            val entity = createStockEntity("005930", "삼성전자", "KOSPI")
            coEvery { localDataSource.getStock("005930") } returns entity

            val result = repository.getStock("005930")

            assertNotNull(result)
            assertEquals("005930", result.ticker)
            assertEquals("삼성전자", result.name)
        }

        @Test
        @DisplayName("getStock — 없는 종목코드 → null 반환")
        fun getStock_withUnknownTicker_returnsNull() = runTest {
            coEvery { localDataSource.getStock(any()) } returns null

            assertNull(repository.getStock("999999"))
        }

        @Test
        @DisplayName("getStockName — 종목명 반환")
        fun getStockName_returnsStockName() = runTest {
            coEvery { localDataSource.getStockName("005930") } returns "삼성전자"

            val result = repository.getStockName("005930")

            assertEquals("삼성전자", result)
        }

        @Test
        @DisplayName("getStockName — 종목명 없으면 ticker 반환 (fallback)")
        fun getStockName_unknownTicker_returnsTicker() = runTest {
            coEvery { localDataSource.getStockName("999999") } returns null

            val result = repository.getStockName("999999")

            assertEquals("999999", result)
        }

        @Test
        @DisplayName("getStockCount — 전체 종목 수 반환")
        fun getStockCount_returnsCount() = runTest {
            coEvery { localDataSource.getCount() } returns 2500

            assertEquals(2500, repository.getStockCount())
        }

        @Test
        @DisplayName("getEtfHoldingCount — ETF 보유 종목 수 반환")
        fun getEtfHoldingCount_returnsCount() = runTest {
            coEvery { localDataSource.getEtfHoldingCount() } returns 150

            assertEquals(150, repository.getEtfHoldingCount())
        }
    }

    // ========== 동기화 테스트 ==========

    @Nested
    @DisplayName("종목 동기화 테스트")
    inner class SyncTests {

        @Test
        @DisplayName("syncFromHolding — localDataSource.upsertFromHolding 호출")
        fun syncFromHolding_callsUpsert() = runTest {
            coEvery { localDataSource.upsertFromHolding(any(), any(), any(), any()) } returns Unit

            repository.syncFromHolding("005930", "삼성전자")

            coVerify(exactly = 1) { localDataSource.upsertFromHolding("005930", "삼성전자", any(), any()) }
        }

        @Test
        @DisplayName("syncFromHolding — 예외 발생 시 삼킴 (CancellationException 제외)")
        fun syncFromHolding_exceptionIsSwallowed() = runTest {
            coEvery { localDataSource.upsertFromHolding(any(), any(), any(), any()) } throws RuntimeException("DB error")

            // Should NOT throw
            repository.syncFromHolding("005930", "삼성전자")
        }

        @Test
        @DisplayName("syncFromHoldings — 비어 있는 목록이면 아무 것도 안 함")
        fun syncFromHoldings_emptyList_doesNothing() = runTest {
            repository.syncFromHoldings(emptyList())

            coVerify(exactly = 0) { localDataSource.syncFromHoldings(any()) }
        }

        @Test
        @DisplayName("syncFromHoldings — 목록이 있으면 localDataSource 호출")
        fun syncFromHoldings_withItems_callsLocalDataSource() = runTest {
            val holdings = listOf("005930" to "삼성전자", "000660" to "SK하이닉스")
            coEvery { localDataSource.syncFromHoldings(any()) } returns Unit

            repository.syncFromHoldings(holdings)

            coVerify(exactly = 1) { localDataSource.syncFromHoldings(holdings) }
        }
    }

    // ========== 초기화 테스트 ==========

    @Nested
    @DisplayName("initializeStocks 테스트")
    inner class InitializeTests {

        @Test
        @DisplayName("성공 경로 — kotlin_krx 데이터 저장 후 Result.success")
        fun initializeStocks_success_returnsSuccessWithCount() = runTest {
            val stockList = listOf("005930" to "삼성전자", "000660" to "SK하이닉스", "035720" to "카카오")
            coEvery { stockDataRepository.getAllStocksList() } returns stockList
            coEvery { localDataSource.deleteAll() } returns Unit
            coEvery { localDataSource.insertAll(any()) } returns Unit

            val result = repository.initializeStocks()

            assertTrue(result.isSuccess)
            assertEquals(3, result.getOrNull())
            coVerify(exactly = 1) { localDataSource.deleteAll() }
            coVerify(exactly = 1) { localDataSource.insertAll(any()) }
        }

        @Test
        @DisplayName("kotlin_krx 빈 목록 반환 → Result.failure (네트워크 오류)")
        fun initializeStocks_emptyStockList_returnsFailure() = runTest {
            coEvery { stockDataRepository.getAllStocksList() } returns emptyList()

            val result = repository.initializeStocks()

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("예외 발생 → Result.failure")
        fun initializeStocks_exceptionThrown_returnsFailure() = runTest {
            coEvery { stockDataRepository.getAllStocksList() } throws RuntimeException("Network error")

            val result = repository.initializeStocks()

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("updateStocks — initializeStocks와 동일 동작")
        fun updateStocks_delegatesToInitializeStocks() = runTest {
            val stockList = listOf("005930" to "삼성전자")
            coEvery { stockDataRepository.getAllStocksList() } returns stockList
            coEvery { localDataSource.deleteAll() } returns Unit
            coEvery { localDataSource.insertAll(any()) } returns Unit

            val result = repository.updateStocks()

            assertTrue(result.isSuccess)
        }
    }

    // ========== Helpers ==========

    private fun createStockEntity(
        ticker: String,
        name: String,
        market: String,
        isEtfHolding: Boolean = false
    ): StockEntity = StockEntity(
        ticker = ticker,
        name = name,
        market = market,
        isEtfHolding = isEtfHolding,
        lastUpdated = System.currentTimeMillis()
    )
}
