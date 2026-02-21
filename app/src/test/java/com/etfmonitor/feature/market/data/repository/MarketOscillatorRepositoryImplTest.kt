package com.etfmonitor.feature.market.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.analysis.MarketOscillatorCalculator
import com.etfmonitor.core.analysis.MarketOscillatorCalculator.OscillatorResult
import com.etfmonitor.core.analysis.MarketOscillatorCalculator.OscillatorStats
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.MarketOscillatorDao
import com.etfmonitor.core.database.entities.MarketOscillatorData
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
 * MarketOscillatorRepositoryImpl 테스트
 *
 * 테스트 범위:
 * - Flow 조회 (getMarketData, getRecentData, getDataByDateRange)
 * - getLatestData — 단건 조회
 * - getDataCount — 카운트 위임
 * - isDialogDismissed / saveDialogDismissed — EtfDao 위임
 * - initializeMarketData — 성공, OscillatorCalculator null 반환, 빈 데이터
 * - updateMarketData — 성공, 오래된 데이터 삭제 (deleteOldData)
 * - 날짜 포맷 변환 — yyyyMMdd → yyyy-MM-dd (DateAdapter.fromKrxFormat)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class MarketOscillatorRepositoryImplTest {

    private lateinit var dao: MarketOscillatorDao
    private lateinit var etfDao: EtfDao
    private lateinit var oscillatorCalculator: MarketOscillatorCalculator

    private lateinit var repository: MarketOscillatorRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
        oscillatorCalculator = mockk(relaxed = true)

        repository = MarketOscillatorRepositoryImpl(
            dao = dao,
            etfDao = etfDao,
            oscillatorCalculator = oscillatorCalculator
        )
    }

    // ========== Flow 조회 테스트 ==========

    @Nested
    @DisplayName("Flow 조회 테스트")
    inner class FlowQueryTests {

        @Test
        @DisplayName("getMarketData — 시장별 전체 데이터를 도메인 모델 Flow로 반환")
        fun getMarketData_returnsMappedFlow() = runTest {
            val entities = listOf(
                createOscillatorEntity("KOSPI", "2025-01-15", indexValue = 2800.0, oscillator = 75.0),
                createOscillatorEntity("KOSPI", "2025-01-14", indexValue = 2780.0, oscillator = 70.0)
            )
            every { dao.getMarketData("KOSPI") } returns flowOf(entities)

            repository.getMarketData("KOSPI").test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertEquals("KOSPI", result[0].market)
                assertEquals("2025-01-15", result[0].date)
                assertEquals(75.0, result[0].oscillator, 0.01)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getRecentData — 최근 N개 데이터 반환")
        fun getRecentData_returnsLimitedData() = runTest {
            val limit = 10
            val entities = (1..limit).map { i ->
                createOscillatorEntity("KOSDAQ", "2025-01-${15 - i + 1}", oscillator = 50.0 + i)
            }
            every { dao.getRecentData("KOSDAQ", limit) } returns flowOf(entities)

            repository.getRecentData("KOSDAQ", limit).test {
                val result = awaitItem()
                assertEquals(limit, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getDataByDateRange — 날짜 범위 내 데이터 반환")
        fun getDataByDateRange_returnsRangeData() = runTest {
            val entities = listOf(
                createOscillatorEntity("KOSPI", "2025-01-10", oscillator = 60.0),
                createOscillatorEntity("KOSPI", "2025-01-12", oscillator = 65.0),
                createOscillatorEntity("KOSPI", "2025-01-15", oscillator = 75.0)
            )
            every { dao.getDataByDateRange("KOSPI", "2025-01-10", "2025-01-15") } returns flowOf(entities)

            repository.getDataByDateRange("KOSPI", "2025-01-10", "2025-01-15").test {
                val result = awaitItem()
                assertEquals(3, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getMarketData — 빈 데이터 흐름 처리")
        fun getMarketData_emptyData_returnsEmptyFlow() = runTest {
            every { dao.getMarketData("KOSPI") } returns flowOf(emptyList())

            repository.getMarketData("KOSPI").test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ========== 단건 조회 테스트 ==========

    @Nested
    @DisplayName("단건 및 카운트 조회 테스트")
    inner class SingleQueryTests {

        @Test
        @DisplayName("getLatestData — 최신 데이터 반환")
        fun getLatestData_returnsLatestOscillator() = runTest {
            val entity = createOscillatorEntity("KOSPI", "2025-01-15", oscillator = 80.0)
            coEvery { dao.getLatestData("KOSPI") } returns entity

            val result = repository.getLatestData("KOSPI")

            assertNotNull(result)
            assertEquals("KOSPI", result.market)
            assertEquals("2025-01-15", result.date)
            assertEquals(80.0, result.oscillator, 0.01)
        }

        @Test
        @DisplayName("getLatestData — 데이터 없으면 null 반환")
        fun getLatestData_noData_returnsNull() = runTest {
            coEvery { dao.getLatestData(any()) } returns null

            assertNull(repository.getLatestData("KOSPI"))
        }

        @Test
        @DisplayName("getDataCount — 데이터 수 위임")
        fun getDataCount_returnsCount() = runTest {
            coEvery { dao.getDataCount("KOSPI") } returns 365

            assertEquals(365, repository.getDataCount("KOSPI"))
        }
    }

    // ========== 다이얼로그 상태 테스트 ==========

    @Nested
    @DisplayName("다이얼로그 상태 테스트")
    inner class DialogStateTests {

        @Test
        @DisplayName("isDialogDismissed — true 값 저장 시 true 반환")
        fun isDialogDismissed_trueSetting_returnsTrue() = runTest {
            coEvery { etfDao.getSetting("market_oscillator_dialog_dismissed") } returns "true"

            assertTrue(repository.isDialogDismissed())
        }

        @Test
        @DisplayName("isDialogDismissed — 설정 없으면 false")
        fun isDialogDismissed_notSet_returnsFalse() = runTest {
            coEvery { etfDao.getSetting("market_oscillator_dialog_dismissed") } returns null

            assertFalse(repository.isDialogDismissed())
        }

        @Test
        @DisplayName("saveDialogDismissed — 올바른 키/값으로 EtfDao 호출")
        fun saveDialogDismissed_savesCorrectSetting() = runTest {
            val settingSlot = slot<Setting>()
            coEvery { etfDao.saveSetting(capture(settingSlot)) } returns Unit

            repository.saveDialogDismissed()

            coVerify(exactly = 1) { etfDao.saveSetting(any()) }
            assertEquals("market_oscillator_dialog_dismissed", settingSlot.captured.key)
            assertEquals("true", settingSlot.captured.value)
        }
    }

    // ========== initializeMarketData 테스트 ==========

    @Nested
    @DisplayName("initializeMarketData 테스트")
    inner class InitializeMarketDataTests {

        @Test
        @DisplayName("성공 경로 — 오실레이터 계산 후 DB 저장, Result.success")
        fun initializeMarketData_success_savesDataAndReturnsCount() = runTest {
            val oscResult = createOscillatorResult(
                "KOSPI",
                dates = listOf("20250115", "20250114", "20250113"),
                indexValues = listOf(2800.0, 2780.0, 2760.0),
                oscillators = listOf(75.0, 70.0, 65.0)
            )
            coEvery { oscillatorCalculator.analyze("KOSPI", any(), any()) } returns oscResult

            val result = repository.initializeMarketData("KOSPI", 30)

            assertTrue(result.isSuccess)
            assertEquals(3, result.getOrNull())
            coVerify(exactly = 1) { dao.insertAll(any()) }
        }

        @Test
        @DisplayName("OscillatorCalculator null 반환 → Result.failure")
        fun initializeMarketData_calculatorReturnsNull_returnsFailure() = runTest {
            coEvery { oscillatorCalculator.analyze(any(), any(), any()) } returns null

            val result = repository.initializeMarketData("KOSPI", 30)

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("빈 날짜 목록 → Result.failure")
        fun initializeMarketData_emptyDates_returnsFailure() = runTest {
            val emptyResult = createOscillatorResult("KOSPI", emptyList(), emptyList(), emptyList())
            coEvery { oscillatorCalculator.analyze("KOSPI", any(), any()) } returns emptyResult

            val result = repository.initializeMarketData("KOSPI", 30)

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("날짜 포맷 변환 — yyyyMMdd → yyyy-MM-dd 변환 확인")
        fun initializeMarketData_convertsKrxDateToIso() = runTest {
            val oscResult = createOscillatorResult(
                "KOSPI",
                dates = listOf("20250115"),
                indexValues = listOf(2800.0),
                oscillators = listOf(75.0)
            )
            coEvery { oscillatorCalculator.analyze("KOSPI", any(), any()) } returns oscResult

            val insertSlot = slot<List<MarketOscillatorData>>()
            coEvery { dao.insertAll(capture(insertSlot)) } returns Unit

            repository.initializeMarketData("KOSPI", 30)

            val savedData = insertSlot.captured
            assertEquals(1, savedData.size)
            // DateAdapter.fromKrxFormat("20250115") → "2025-01-15"
            assertEquals("2025-01-15", savedData[0].date)
            assertEquals("KOSPI-2025-01-15", savedData[0].id)
        }

        @Test
        @DisplayName("예외 발생 → Result.failure")
        fun initializeMarketData_exceptionThrown_returnsFailure() = runTest {
            coEvery { oscillatorCalculator.analyze(any(), any(), any()) } throws RuntimeException("Network failure")

            val result = repository.initializeMarketData("KOSPI", 30)

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("onProgress 콜백 호출 검증")
        fun initializeMarketData_callsProgressCallback() = runTest {
            val oscResult = createOscillatorResult(
                "KOSPI",
                dates = listOf("20250115"),
                indexValues = listOf(2800.0),
                oscillators = listOf(75.0)
            )
            coEvery { oscillatorCalculator.analyze("KOSPI", any(), any()) } returns oscResult

            val progressValues = mutableListOf<Int>()
            repository.initializeMarketData("KOSPI", 30) { _, progress -> progressValues.add(progress) }

            // 최소 시작(0)과 완료(100) 콜백이 있어야 함
            assertTrue(progressValues.isNotEmpty())
            assertTrue(0 in progressValues)
            assertTrue(100 in progressValues)
        }
    }

    // ========== updateMarketData 테스트 ==========

    @Nested
    @DisplayName("updateMarketData 테스트")
    inner class UpdateMarketDataTests {

        @Test
        @DisplayName("성공 경로 — DB 저장 + 오래된 데이터 삭제")
        fun updateMarketData_success_savesAndCleansOldData() = runTest {
            val oscResult = createOscillatorResult(
                "KOSDAQ",
                dates = listOf("20250115", "20250114"),
                indexValues = listOf(900.0, 895.0),
                oscillators = listOf(50.0, 45.0)
            )
            coEvery { oscillatorCalculator.analyze("KOSDAQ", any(), any()) } returns oscResult

            val result = repository.updateMarketData("KOSDAQ")

            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrNull())
            coVerify(exactly = 1) { dao.insertAll(any()) }
            coVerify(exactly = 1) { dao.deleteOldData("KOSDAQ", 365) }
        }

        @Test
        @DisplayName("OscillatorCalculator null 반환 → Result.failure")
        fun updateMarketData_calculatorNull_returnsFailure() = runTest {
            coEvery { oscillatorCalculator.analyze(any(), any(), any()) } returns null

            val result = repository.updateMarketData("KOSPI")

            assertTrue(result.isFailure)
        }
    }

    // ========== deleteMarketData / deleteAll ==========

    @Nested
    @DisplayName("삭제 테스트")
    inner class DeleteTests {

        @Test
        @DisplayName("deleteMarketData — dao.deleteMarketData 위임")
        fun deleteMarketData_delegatesToDao() = runTest {
            coEvery { dao.deleteMarketData("KOSPI") } returns Unit

            repository.deleteMarketData("KOSPI")

            coVerify(exactly = 1) { dao.deleteMarketData("KOSPI") }
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

    private fun createOscillatorEntity(
        market: String,
        date: String,
        indexValue: Double = 2800.0,
        oscillator: Double = 50.0
    ): MarketOscillatorData = MarketOscillatorData(
        id = "$market-$date",
        market = market,
        date = date,
        indexValue = indexValue,
        oscillator = oscillator,
        lastUpdated = System.currentTimeMillis()
    )

    private fun createOscillatorResult(
        market: String,
        dates: List<String>,
        indexValues: List<Double>,
        oscillators: List<Double>
    ): OscillatorResult = OscillatorResult(
        market = market,
        dates = dates,
        indexValues = indexValues,
        oscillator = oscillators,
        stats = OscillatorStats(
            mean = if (oscillators.isEmpty()) 0.0 else oscillators.average(),
            max = oscillators.maxOrNull() ?: 0.0,
            min = oscillators.minOrNull() ?: 0.0,
            latest = oscillators.lastOrNull() ?: 0.0
        )
    )
}
