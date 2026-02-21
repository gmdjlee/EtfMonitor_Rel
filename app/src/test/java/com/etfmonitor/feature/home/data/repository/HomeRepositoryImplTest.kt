package com.etfmonitor.feature.home.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import com.etfmonitor.feature.home.domain.model.DataStatus
import com.etfmonitor.feature.market.domain.model.FearGreedIndex
import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
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
 * HomeRepositoryImpl 테스트
 *
 * 테스트 범위:
 * - hasEtfData, getLatestDate 위임 검증
 * - getHomeSummary — 정상 데이터, 오류 시 null 반환
 * - getDataStatus — 각 하위 repository 데이터 유무 반영
 * - getSetting / saveSetting — EtfDao 위임 검증
 * - shouldShowUnifiedInitDialog — is_first_run 조건 분기
 * - calculateOscillatorStatus — 과매수/중립/과매도 경계값
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class HomeRepositoryImplTest {

    private lateinit var etfRepository: EtfRepository
    private lateinit var fearGreedRepository: FearGreedRepository
    private lateinit var marketOscillatorRepository: MarketOscillatorRepository
    private lateinit var marketDepositRepository: MarketDepositRepository
    private lateinit var etfDao: EtfDao

    private lateinit var repository: HomeRepositoryImpl

    @BeforeEach
    fun setup() {
        etfRepository = mockk(relaxed = true)
        fearGreedRepository = mockk(relaxed = true)
        marketOscillatorRepository = mockk(relaxed = true)
        marketDepositRepository = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)

        repository = HomeRepositoryImpl(
            etfRepository = etfRepository,
            fearGreedRepository = fearGreedRepository,
            marketOscillatorRepository = marketOscillatorRepository,
            marketDepositRepository = marketDepositRepository,
            etfDao = etfDao
        )
    }

    // ========== hasEtfData / getLatestDate ==========

    @Nested
    @DisplayName("ETF 데이터 상태 위임 테스트")
    inner class EtfDelegationTests {

        @Test
        @DisplayName("hasEtfData — etfRepository.hasData()에 위임")
        fun hasEtfData_delegatesToEtfRepository() = runTest {
            coEvery { etfRepository.hasData() } returns true

            val result = repository.hasEtfData()

            assertTrue(result)
            coVerify(exactly = 1) { etfRepository.hasData() }
        }

        @Test
        @DisplayName("hasEtfData — 데이터 없을 때 false 반환")
        fun hasEtfData_returnsFalseWhenNoData() = runTest {
            coEvery { etfRepository.hasData() } returns false

            assertFalse(repository.hasEtfData())
        }

        @Test
        @DisplayName("getLatestDate — etfRepository.getLatestDate()에 위임")
        fun getLatestDate_delegatesToEtfRepository() = runTest {
            val expectedDate = "2025-01-15"
            coEvery { etfRepository.getLatestDate() } returns expectedDate

            val result = repository.getLatestDate()

            assertEquals(expectedDate, result)
        }

        @Test
        @DisplayName("getLatestDate — 데이터 없으면 null 반환")
        fun getLatestDate_returnsNullWhenNoData() = runTest {
            coEvery { etfRepository.getLatestDate() } returns null

            assertNull(repository.getLatestDate())
        }
    }

    // ========== getHomeSummary ==========

    @Nested
    @DisplayName("getHomeSummary 테스트")
    inner class HomeSummaryTests {

        @Test
        @DisplayName("정상 데이터 있을 때 HomeSummary 반환")
        fun getHomeSummary_allDataAvailable_returnsSummary() = runTest {
            val deposit = createTestDeposit("2025-01-15", depositChange = 1000.0, creditChange = -500.0)
            val kospiFg = createTestFearGreed("KOSPI", "2025-01-15", oscillator = 0.65)
            val kosdaqFg = createTestFearGreed("KOSDAQ", "2025-01-15", oscillator = 0.45)
            val kospiOsc = createTestOscillator("KOSPI", "2025-01-15", oscillator = 75.0)
            val kosdaqOsc = createTestOscillator("KOSDAQ", "2025-01-15", oscillator = 50.0)

            every { marketDepositRepository.getRecentDeposits(2) } returns flowOf(listOf(deposit))
            every { fearGreedRepository.getRecentByMarket("KOSPI", 1) } returns flowOf(listOf(kospiFg))
            every { fearGreedRepository.getRecentByMarket("KOSDAQ", 1) } returns flowOf(listOf(kosdaqFg))
            coEvery { marketOscillatorRepository.getLatestData("KOSPI") } returns kospiOsc
            coEvery { marketOscillatorRepository.getLatestData("KOSDAQ") } returns kosdaqOsc

            val result = repository.getHomeSummary()

            assertNotNull(result)
            assertEquals(1000.0, result.depositChange)
            assertEquals(-500.0, result.creditChange)
            assertEquals(0.65, result.kospiFearGreed)
            assertEquals(0.45, result.kosdaqFearGreed)
            assertEquals(75.0, result.kospiOscillator)
            assertEquals("과매수", result.kospiStatus)
            assertEquals(50.0, result.kosdaqOscillator)
            assertEquals("중립", result.kosdaqStatus)
        }

        @Test
        @DisplayName("예탁금 데이터 없을 때 depositChange는 null")
        fun getHomeSummary_noDepositData_depositChangeIsNull() = runTest {
            every { marketDepositRepository.getRecentDeposits(2) } returns flowOf(emptyList())
            every { fearGreedRepository.getRecentByMarket(any(), any()) } returns flowOf(emptyList())
            coEvery { marketOscillatorRepository.getLatestData(any()) } returns null

            val result = repository.getHomeSummary()

            assertNotNull(result)
            assertNull(result.depositChange)
            assertNull(result.creditChange)
        }

        @Test
        @DisplayName("예외 발생 시 null 반환 (CancellationException 제외)")
        fun getHomeSummary_exceptionThrown_returnsNull() = runTest {
            every { marketDepositRepository.getRecentDeposits(any()) } throws RuntimeException("DB error")

            val result = repository.getHomeSummary()

            assertNull(result)
        }

        @Test
        @DisplayName("오실레이터 70 이상 — 과매수 상태")
        fun getHomeSummary_oscillatorAbove70_returnsOverbought() = runTest {
            val oscData = createTestOscillator("KOSPI", "2025-01-15", oscillator = 70.0)
            every { marketDepositRepository.getRecentDeposits(any()) } returns flowOf(emptyList())
            every { fearGreedRepository.getRecentByMarket(any(), any()) } returns flowOf(emptyList())
            coEvery { marketOscillatorRepository.getLatestData("KOSPI") } returns oscData
            coEvery { marketOscillatorRepository.getLatestData("KOSDAQ") } returns null

            val result = repository.getHomeSummary()

            assertNotNull(result)
            assertEquals("과매수", result.kospiStatus)
        }

        @Test
        @DisplayName("오실레이터 -70 이하 — 과매도 상태")
        fun getHomeSummary_oscillatorBelow70Negative_returnsOversold() = runTest {
            val oscData = createTestOscillator("KOSDAQ", "2025-01-15", oscillator = -70.0)
            every { marketDepositRepository.getRecentDeposits(any()) } returns flowOf(emptyList())
            every { fearGreedRepository.getRecentByMarket(any(), any()) } returns flowOf(emptyList())
            coEvery { marketOscillatorRepository.getLatestData("KOSPI") } returns null
            coEvery { marketOscillatorRepository.getLatestData("KOSDAQ") } returns oscData

            val result = repository.getHomeSummary()

            assertNotNull(result)
            assertEquals("과매도", result.kosdaqStatus)
        }

        @Test
        @DisplayName("오실레이터 0 — 중립 상태")
        fun getHomeSummary_oscillatorZero_returnsNeutral() = runTest {
            val oscData = createTestOscillator("KOSPI", "2025-01-15", oscillator = 0.0)
            every { marketDepositRepository.getRecentDeposits(any()) } returns flowOf(emptyList())
            every { fearGreedRepository.getRecentByMarket(any(), any()) } returns flowOf(emptyList())
            coEvery { marketOscillatorRepository.getLatestData("KOSPI") } returns oscData
            coEvery { marketOscillatorRepository.getLatestData("KOSDAQ") } returns null

            val result = repository.getHomeSummary()

            assertNotNull(result)
            assertEquals("중립", result.kospiStatus)
        }
    }

    // ========== getDataStatus ==========

    @Nested
    @DisplayName("getDataStatus 테스트")
    inner class DataStatusTests {

        @Test
        @DisplayName("모든 데이터 있을 때 hasAllData == true")
        fun getDataStatus_allDataPresent_hasAllDataIsTrue() = runTest {
            coEvery { etfRepository.hasData() } returns true
            coEvery { marketDepositRepository.getDepositCount() } returns 10
            coEvery { fearGreedRepository.getCountByMarket("KOSPI") } returns 5
            coEvery { fearGreedRepository.getCountByMarket("KOSDAQ") } returns 3
            coEvery { marketOscillatorRepository.getDataCount("KOSPI") } returns 30
            coEvery { marketOscillatorRepository.getDataCount("KOSDAQ") } returns 30

            val status = repository.getDataStatus()

            assertTrue(status.hasEtfData)
            assertTrue(status.hasDepositData)
            assertTrue(status.hasFearGreedData)
            assertTrue(status.hasOscillatorData)
            assertTrue(status.hasAllData)
        }

        @Test
        @DisplayName("데이터 모두 없을 때 hasAnyData == false")
        fun getDataStatus_noData_hasAnyDataIsFalse() = runTest {
            coEvery { etfRepository.hasData() } returns false
            coEvery { marketDepositRepository.getDepositCount() } returns 0
            coEvery { fearGreedRepository.getCountByMarket(any()) } returns 0
            coEvery { marketOscillatorRepository.getDataCount(any()) } returns 0

            val status = repository.getDataStatus()

            assertFalse(status.hasEtfData)
            assertFalse(status.hasDepositData)
            assertFalse(status.hasFearGreedData)
            assertFalse(status.hasOscillatorData)
            assertFalse(status.hasAnyData)
        }

        @Test
        @DisplayName("KOSDAQ Fear&Greed 데이터만 있을 때 hasFearGreedData == true")
        fun getDataStatus_onlyKosdaqFearGreed_hasFearGreedDataIsTrue() = runTest {
            coEvery { etfRepository.hasData() } returns false
            coEvery { marketDepositRepository.getDepositCount() } returns 0
            coEvery { fearGreedRepository.getCountByMarket("KOSPI") } returns 0
            coEvery { fearGreedRepository.getCountByMarket("KOSDAQ") } returns 5
            coEvery { marketOscillatorRepository.getDataCount(any()) } returns 0

            val status = repository.getDataStatus()

            assertTrue(status.hasFearGreedData)
        }
    }

    // ========== getSetting / saveSetting ==========

    @Nested
    @DisplayName("설정 테스트")
    inner class SettingsTests {

        @Test
        @DisplayName("getSetting — EtfDao.getSetting 위임")
        fun getSetting_delegatesToEtfDao() = runTest {
            coEvery { etfDao.getSetting("test_key") } returns "test_value"

            val result = repository.getSetting("test_key")

            assertEquals("test_value", result)
            coVerify(exactly = 1) { etfDao.getSetting("test_key") }
        }

        @Test
        @DisplayName("getSetting — 키 없으면 null 반환")
        fun getSetting_missingKey_returnsNull() = runTest {
            coEvery { etfDao.getSetting(any()) } returns null

            assertNull(repository.getSetting("nonexistent"))
        }

        @Test
        @DisplayName("saveSetting — EtfDao.saveSetting에 Setting 객체로 위임")
        fun saveSetting_delegatesToEtfDao() = runTest {
            val settingSlot = slot<Setting>()
            coEvery { etfDao.saveSetting(capture(settingSlot)) } returns Unit

            repository.saveSetting("my_key", "my_value")

            coVerify(exactly = 1) { etfDao.saveSetting(any()) }
            assertEquals("my_key", settingSlot.captured.key)
            assertEquals("my_value", settingSlot.captured.value)
        }
    }

    // ========== shouldShowUnifiedInitDialog ==========

    @Nested
    @DisplayName("shouldShowUnifiedInitDialog 테스트")
    inner class UnifiedInitDialogTests {

        @Test
        @DisplayName("첫 실행(설정 없음) + ETF 데이터 없음 → true")
        fun shouldShowUnifiedInitDialog_firstRunNoEtfData_returnsTrue() = runTest {
            coEvery { etfDao.getSetting("is_first_run") } returns null
            coEvery { etfRepository.hasData() } returns false

            assertTrue(repository.shouldShowUnifiedInitDialog())
        }

        @Test
        @DisplayName("is_first_run == true + ETF 데이터 없음 → true")
        fun shouldShowUnifiedInitDialog_isFirstRunTrueNoEtfData_returnsTrue() = runTest {
            coEvery { etfDao.getSetting("is_first_run") } returns "true"
            coEvery { etfRepository.hasData() } returns false

            assertTrue(repository.shouldShowUnifiedInitDialog())
        }

        @Test
        @DisplayName("첫 실행이지만 ETF 데이터 있으면 → false")
        fun shouldShowUnifiedInitDialog_firstRunWithEtfData_returnsFalse() = runTest {
            coEvery { etfDao.getSetting("is_first_run") } returns null
            coEvery { etfRepository.hasData() } returns true

            assertFalse(repository.shouldShowUnifiedInitDialog())
        }

        @Test
        @DisplayName("is_first_run == false → false (ETF 데이터 여부와 무관)")
        fun shouldShowUnifiedInitDialog_isFirstRunFalse_returnsFalse() = runTest {
            coEvery { etfDao.getSetting("is_first_run") } returns "false"
            coEvery { etfRepository.hasData() } returns false

            assertFalse(repository.shouldShowUnifiedInitDialog())
        }
    }

    // ========== getDefaultDays ==========

    @Test
    @DisplayName("getDefaultDays — etfRepository에 위임")
    fun getDefaultDays_delegatesToEtfRepository() = runTest {
        coEvery { etfRepository.getDefaultDays() } returns 365

        val result = repository.getDefaultDays()

        assertEquals(365, result)
    }

    // ========== Helpers ==========

    private fun createTestDeposit(date: String, depositChange: Double, creditChange: Double): MarketDeposit =
        MarketDeposit(
            date = date,
            depositAmount = 500_000.0,
            depositChange = depositChange,
            creditAmount = 100_000.0,
            creditChange = creditChange,
            lastUpdated = System.currentTimeMillis()
        )

    private fun createTestFearGreed(
        market: String,
        date: String,
        oscillator: Double
    ): FearGreedIndex = FearGreedIndex(
        id = "$market-$date",
        market = market,
        date = date,
        indexValue = 2800.0,
        fearGreedValue = oscillator,
        oscillator = oscillator,
        rsi = 50.0,
        momentum = 0.0,
        putCallRatio = 1.0,
        volatility = 15.0,
        spread = 0.5,
        lastUpdated = System.currentTimeMillis()
    )

    private fun createTestOscillator(
        market: String,
        date: String,
        oscillator: Double
    ): MarketOscillator = MarketOscillator(
        id = "$market-$date",
        market = market,
        date = date,
        indexValue = 2800.0,
        oscillator = oscillator,
        lastUpdated = System.currentTimeMillis()
    )
}
