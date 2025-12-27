package com.etfmonitor.feature.etf.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.network.python.PyKrxClient
import com.etfmonitor.feature.etf.data.datasource.EtfLocalDataSource
import com.etfmonitor.feature.etf.domain.model.HoldingStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
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
 * EtfRepositoryImpl 테스트
 *
 * 테스트 범위:
 * - 데이터 상태 확인
 * - ETF 목록 조회
 * - 보유 종목 비교 분석
 * - 설정 관리
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class EtfRepositoryImplTest {

    // Mocks
    private lateinit var localDataSource: EtfLocalDataSource
    private lateinit var etfDao: EtfDao
    private lateinit var dailyEtfStatisticsDao: DailyEtfStatisticsDao
    private lateinit var stockDao: StockDao
    private lateinit var pyKrxClient: PyKrxClient

    private lateinit var repository: EtfRepositoryImpl

    @BeforeEach
    fun setup() {
        localDataSource = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
        dailyEtfStatisticsDao = mockk(relaxed = true)
        stockDao = mockk(relaxed = true)
        pyKrxClient = mockk(relaxed = true)

        repository = EtfRepositoryImpl(
            localDataSource = localDataSource,
            etfDao = etfDao,
            dailyEtfStatisticsDao = dailyEtfStatisticsDao,
            stockDao = stockDao,
            pyKrx = pyKrxClient
        )
    }

    @Nested
    @DisplayName("데이터 상태 테스트")
    inner class DataStatusTests {

        @Test
        @DisplayName("ETF가 없을 때 hasData()는 false")
        fun whenNoEtfs_thenHasDataIsFalse() = runTest {
            // Given
            coEvery { localDataSource.getEtfCount() } returns 0

            // When
            val result = repository.hasData()

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("ETF가 있을 때 hasData()는 true")
        fun whenHasEtfs_thenHasDataIsTrue() = runTest {
            // Given
            coEvery { localDataSource.getEtfCount() } returns 5

            // When
            val result = repository.hasData()

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("getDataStatus()는 ETF 개수와 최신 날짜를 반환")
        fun getDataStatus_returnsCorrectStatus() = runTest {
            // Given
            val testDate = "2025-01-15"
            coEvery { localDataSource.getEtfCount() } returns 10
            coEvery { localDataSource.getLatestDate() } returns testDate

            // When
            val result = repository.getDataStatus()

            // Then
            assertTrue(result.hasData)
            assertEquals(testDate, result.latestDate)
        }

        @Test
        @DisplayName("최신 날짜가 없을 때 null 반환")
        fun whenNoData_thenLatestDateIsNull() = runTest {
            // Given
            coEvery { localDataSource.getLatestDate() } returns null

            // When
            val result = repository.getLatestDate()

            // Then
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("ETF 목록 테스트")
    inner class EtfListTests {

        @Test
        @DisplayName("getAllEtfs()는 모든 ETF를 Flow로 반환")
        fun getAllEtfs_returnsAllEtfsAsFlow() = runTest {
            // Given
            val etfEntities = listOf(
                com.etfmonitor.core.database.entities.Etf("069500", "KODEX 200"),
                com.etfmonitor.core.database.entities.Etf("102110", "TIGER 200")
            )
            every { localDataSource.getAllEtfs() } returns flowOf(etfEntities)

            // When & Then
            repository.getAllEtfs().test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertEquals("069500", result[0].ticker)
                assertEquals("KODEX 200", result[0].name)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("searchEtfs()는 검색어와 일치하는 ETF 반환")
        fun searchEtfs_returnsMatchingEtfs() = runTest {
            // Given
            val query = "KODEX"
            val etfEntities = listOf(
                com.etfmonitor.core.database.entities.Etf("069500", "KODEX 200")
            )
            every { localDataSource.searchEtfs(query) } returns flowOf(etfEntities)

            // When & Then
            repository.searchEtfs(query).test {
                val result = awaitItem()
                assertEquals(1, result.size)
                assertTrue(result[0].name.contains("KODEX"))
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("보유 종목 비교 테스트")
    inner class ComparisonTests {

        @Test
        @DisplayName("날짜가 없으면 null 반환")
        fun whenNoDates_thenReturnNull() = runTest {
            // Given
            coEvery { localDataSource.getDates(any()) } returns emptyList()

            // When
            val result = repository.getComparison("069500")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("날짜가 1개면 모든 종목 NEW로 표시")
        fun whenSingleDate_thenAllHoldingsAreNew() = runTest {
            // Given
            val etfTicker = "069500"
            val date = "2025-01-15"
            val holdings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", date, 0.25f, 50000000000f),
                createTestHolding(etfTicker, "000660", "SK하이닉스", date, 0.15f, 30000000000f)
            )

            coEvery { localDataSource.getDates(etfTicker) } returns listOf(date)
            coEvery { localDataSource.getHoldings(etfTicker, date) } returns holdings

            // When
            val result = repository.getComparison(etfTicker)

            // Then
            assertNotNull(result)
            assertEquals(etfTicker, result.etfTicker)
            assertEquals(date, result.currentDate)
            assertEquals("N/A", result.previousDate)
            assertEquals(2, result.items.size)
            assertTrue(result.items.all { it.status == HoldingStatus.NEW })
        }

        @Test
        @DisplayName("새로 편입된 종목은 NEW 상태")
        fun whenNewHolding_thenStatusIsNew() = runTest {
            // Given
            val etfTicker = "069500"
            val currentDate = "2025-01-15"
            val previousDate = "2025-01-14"

            val previousHoldings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", previousDate, 0.25f, 50000000000f)
            )
            val currentHoldings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", currentDate, 0.25f, 50000000000f),
                createTestHolding(etfTicker, "000660", "SK하이닉스", currentDate, 0.15f, 30000000000f)
            )

            coEvery { localDataSource.getDates(etfTicker) } returns listOf(currentDate, previousDate)
            coEvery { localDataSource.getHoldings(etfTicker, currentDate) } returns currentHoldings
            coEvery { localDataSource.getHoldings(etfTicker, previousDate) } returns previousHoldings

            // When
            val result = repository.getComparison(etfTicker)

            // Then
            assertNotNull(result)
            val newHolding = result.items.find { it.stockTicker == "000660" }
            assertNotNull(newHolding)
            assertEquals(HoldingStatus.NEW, newHolding.status)
        }

        @Test
        @DisplayName("제외된 종목은 REMOVED 상태")
        fun whenRemovedHolding_thenStatusIsRemoved() = runTest {
            // Given
            val etfTicker = "069500"
            val currentDate = "2025-01-15"
            val previousDate = "2025-01-14"

            val previousHoldings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", previousDate, 0.25f, 50000000000f),
                createTestHolding(etfTicker, "000660", "SK하이닉스", previousDate, 0.15f, 30000000000f)
            )
            val currentHoldings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", currentDate, 0.25f, 50000000000f)
            )

            coEvery { localDataSource.getDates(etfTicker) } returns listOf(currentDate, previousDate)
            coEvery { localDataSource.getHoldings(etfTicker, currentDate) } returns currentHoldings
            coEvery { localDataSource.getHoldings(etfTicker, previousDate) } returns previousHoldings

            // When
            val result = repository.getComparison(etfTicker)

            // Then
            assertNotNull(result)
            val removedHolding = result.items.find { it.stockTicker == "000660" }
            assertNotNull(removedHolding)
            assertEquals(HoldingStatus.REMOVED, removedHolding.status)
        }

        @Test
        @DisplayName("비중 증가 종목은 INCREASE 상태 (>0.01%)")
        fun whenWeightIncreased_thenStatusIsIncrease() = runTest {
            // Given
            val etfTicker = "069500"
            val currentDate = "2025-01-15"
            val previousDate = "2025-01-14"

            val previousHoldings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", previousDate, 0.20f, 40000000000f)
            )
            val currentHoldings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", currentDate, 0.25f, 50000000000f)
            )

            coEvery { localDataSource.getDates(etfTicker) } returns listOf(currentDate, previousDate)
            coEvery { localDataSource.getHoldings(etfTicker, currentDate) } returns currentHoldings
            coEvery { localDataSource.getHoldings(etfTicker, previousDate) } returns previousHoldings

            // When
            val result = repository.getComparison(etfTicker)

            // Then
            assertNotNull(result)
            val holding = result.items.find { it.stockTicker == "005930" }
            assertNotNull(holding)
            assertEquals(HoldingStatus.INCREASE, holding.status)
            assertEquals(0.05f, holding.change, 0.001f)
        }

        @Test
        @DisplayName("비중 감소 종목은 DECREASE 상태 (<-0.01%)")
        fun whenWeightDecreased_thenStatusIsDecrease() = runTest {
            // Given
            val etfTicker = "069500"
            val currentDate = "2025-01-15"
            val previousDate = "2025-01-14"

            val previousHoldings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", previousDate, 0.25f, 50000000000f)
            )
            val currentHoldings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", currentDate, 0.20f, 40000000000f)
            )

            coEvery { localDataSource.getDates(etfTicker) } returns listOf(currentDate, previousDate)
            coEvery { localDataSource.getHoldings(etfTicker, currentDate) } returns currentHoldings
            coEvery { localDataSource.getHoldings(etfTicker, previousDate) } returns previousHoldings

            // When
            val result = repository.getComparison(etfTicker)

            // Then
            assertNotNull(result)
            val holding = result.items.find { it.stockTicker == "005930" }
            assertNotNull(holding)
            assertEquals(HoldingStatus.DECREASE, holding.status)
            assertEquals(-0.05f, holding.change, 0.001f)
        }

        @Test
        @DisplayName("비중 변화 없으면 MAINTAIN 상태")
        fun whenWeightUnchanged_thenStatusIsMaintain() = runTest {
            // Given
            val etfTicker = "069500"
            val currentDate = "2025-01-15"
            val previousDate = "2025-01-14"

            val previousHoldings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", previousDate, 0.25f, 50000000000f)
            )
            val currentHoldings = listOf(
                createTestHolding(etfTicker, "005930", "삼성전자", currentDate, 0.2501f, 50020000000f)
            )

            coEvery { localDataSource.getDates(etfTicker) } returns listOf(currentDate, previousDate)
            coEvery { localDataSource.getHoldings(etfTicker, currentDate) } returns currentHoldings
            coEvery { localDataSource.getHoldings(etfTicker, previousDate) } returns previousHoldings

            // When
            val result = repository.getComparison(etfTicker)

            // Then
            assertNotNull(result)
            val holding = result.items.find { it.stockTicker == "005930" }
            assertNotNull(holding)
            assertEquals(HoldingStatus.MAINTAIN, holding.status)
        }
    }

    @Nested
    @DisplayName("설정 관리 테스트")
    inner class SettingsTests {

        @Test
        @DisplayName("기본 일수 조회 - 저장된 값이 있으면 반환")
        fun getDefaultDays_whenSaved_thenReturnSavedValue() = runTest {
            // Given
            coEvery { etfDao.getSetting("default_days") } returns "30"

            // When
            val result = repository.getDefaultDays()

            // Then
            assertEquals(30, result)
        }

        @Test
        @DisplayName("기본 일수 조회 - 저장된 값이 없으면 25 반환")
        fun getDefaultDays_whenNotSaved_thenReturn25() = runTest {
            // Given
            coEvery { etfDao.getSetting("default_days") } returns null

            // When
            val result = repository.getDefaultDays()

            // Then
            assertEquals(25, result)
        }

        @Test
        @DisplayName("기본 일수 설정")
        fun setDefaultDays_savesValue() = runTest {
            // Given
            val days = 40

            // When
            repository.setDefaultDays(days)

            // Then
            coVerify {
                etfDao.saveSetting(Setting("default_days", "40"))
            }
        }

        @Test
        @DisplayName("테마 조회 - 저장된 값이 있으면 파싱하여 반환")
        fun getThemes_whenSaved_thenReturnParsedList() = runTest {
            // Given
            coEvery { etfDao.getSetting("themes") } returns "반도체,바이오,AI"

            // When
            val result = repository.getThemes()

            // Then
            assertEquals(3, result.size)
            assertTrue(result.contains("반도체"))
            assertTrue(result.contains("바이오"))
            assertTrue(result.contains("AI"))
        }

        @Test
        @DisplayName("테마 추가 - 중복이 아니면 추가")
        fun addTheme_whenNotDuplicate_thenAdds() = runTest {
            // Given
            coEvery { etfDao.getSetting("themes") } returns "반도체,바이오"

            // When
            repository.addTheme("AI")

            // Then
            coVerify {
                etfDao.saveSetting(match { it.key == "themes" && it.value.contains("AI") })
            }
        }

        @Test
        @DisplayName("테마 제거")
        fun removeTheme_removesFromList() = runTest {
            // Given
            coEvery { etfDao.getSetting("themes") } returns "반도체,바이오,AI"

            // When
            repository.removeTheme("바이오")

            // Then
            coVerify {
                etfDao.saveSetting(match {
                    it.key == "themes" && !it.value.contains("바이오")
                })
            }
        }

        @Test
        @DisplayName("제외 키워드 조회")
        fun getExclusions_returnsList() = runTest {
            // Given
            coEvery { etfDao.getSetting("exclusions") } returns "인버스,레버리지"

            // When
            val result = repository.getExclusions()

            // Then
            assertEquals(2, result.size)
            assertTrue(result.contains("인버스"))
            assertTrue(result.contains("레버리지"))
        }
    }

    // ========== Helper Functions ==========

    private fun createTestHolding(
        etfTicker: String,
        stockTicker: String,
        stockName: String,
        date: String,
        weight: Float,
        amount: Float
    ): Holding {
        return Holding.create(
            etfTicker = etfTicker,
            stockTicker = stockTicker,
            stockName = stockName,
            date = date,
            weight = weight,
            amount = amount
        )
    }
}
