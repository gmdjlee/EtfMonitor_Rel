package com.etfmonitor.feature.market.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.BloodIndicatorDao
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.network.blood.BloodIndicatorClient
import com.etfmonitor.core.network.blood.FredApiKeyProvider
import com.etfmonitor.feature.market.domain.model.BloodSignalType
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
import com.etfmonitor.core.database.entities.BloodIndicator as BloodIndicatorEntity

/**
 * BloodIndicatorRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - DAO를 통한 Flow 데이터 조회 (getAll, getRecent, getByDateRange, getByDate)
 * - 단순 위임 메서드 (getCount, getLatestDate, getEarliestDate, getLastUpdateTime)
 * - 다이얼로그 상태 관리 (isDialogDismissed, saveDialogDismissed)
 * - initializeBloodIndicator: FRED API 키 미설정 시 실패 반환
 * - updateBloodIndicator: FRED API 키 미설정 시 실패 반환
 *
 * 주의:
 * - fetchAndCalculate()는 BloodIndicatorClient와 BloodIndicatorCalculator에 의존하므로
 *   성공 경로 통합 테스트는 별도 테스트에서 진행합니다.
 * - FredApiKeyProvider는 Android Keystore를 사용하므로 mock으로 대체합니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("BloodIndicatorRepositoryImpl 테스트")
class BloodIndicatorRepositoryImplTest {

    private lateinit var bloodIndicatorDao: BloodIndicatorDao
    private lateinit var etfDao: EtfDao
    private lateinit var bloodClient: BloodIndicatorClient
    private lateinit var fredApiKeyProvider: FredApiKeyProvider

    private lateinit var repository: BloodIndicatorRepositoryImpl

    @BeforeEach
    fun setup() {
        bloodIndicatorDao = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
        bloodClient = mockk(relaxed = true)
        fredApiKeyProvider = mockk(relaxed = true)

        repository = BloodIndicatorRepositoryImpl(
            bloodIndicatorDao = bloodIndicatorDao,
            etfDao = etfDao,
            bloodClient = bloodClient,
            fredApiKeyProvider = fredApiKeyProvider
        )
    }

    // ========== Flow 데이터 조회 ==========

    @Nested
    @DisplayName("Flow 데이터 조회 테스트")
    inner class FlowQueryTests {

        @Test
        @DisplayName("getAll()은 DAO의 getAll()을 위임하고 Domain List를 반환한다")
        fun `getAll_delegatesToDao_returnsDomainList`() = runTest {
            // Given
            val entities = listOf(
                createTestBloodEntity("2025-01-15", "RISK_ON"),
                createTestBloodEntity("2025-01-08", "RISK_OFF")
            )
            every { bloodIndicatorDao.getAll() } returns flowOf(entities)

            // When & Then
            repository.getAll().test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertEquals("2025-01-15", result[0].date)
                assertEquals(BloodSignalType.RISK_ON, result[0].signalType)
                assertEquals("2025-01-08", result[1].date)
                assertEquals(BloodSignalType.RISK_OFF, result[1].signalType)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getAll()은 빈 리스트를 반환할 때 빈 Domain List를 전달한다")
        fun `getAll_withEmptyData_returnsEmptyList`() = runTest {
            // Given
            every { bloodIndicatorDao.getAll() } returns flowOf(emptyList())

            // When & Then
            repository.getAll().test {
                val result = awaitItem()
                assertTrue(result.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getRecent()는 limit 파라미터를 DAO에 전달하고 결과를 반환한다")
        fun `getRecent_withLimit_delegatesToDaoWithLimit`() = runTest {
            // Given
            val limit = 10
            val entities = (1..limit).map { day ->
                createTestBloodEntity("2025-01-${15 - day + 1}", "RISK_ON")
            }
            every { bloodIndicatorDao.getRecent(limit) } returns flowOf(entities)

            // When & Then
            repository.getRecent(limit).test {
                val result = awaitItem()
                assertEquals(limit, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getByDateRange()는 날짜 범위를 DAO에 전달하고 결과를 반환한다")
        fun `getByDateRange_withDateRange_delegatesToDaoWithRange`() = runTest {
            // Given
            val startDate = "2025-01-01"
            val endDate = "2025-01-15"
            val entities = listOf(
                createTestBloodEntity("2025-01-15", "RISK_ON"),
                createTestBloodEntity("2025-01-08", "NEUTRAL")
            )
            every { bloodIndicatorDao.getByDateRange(startDate, endDate) } returns flowOf(entities)

            // When & Then
            repository.getByDateRange(startDate, endDate).test {
                val result = awaitItem()
                assertEquals(2, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getByDate()는 특정 날짜의 데이터를 Domain 모델로 반환한다")
        fun `getByDate_withExistingDate_returnsDomainModel`() = runTest {
            // Given
            val date = "2025-01-15"
            val entity = createTestBloodEntity(date, "RISK_ON")
            coEvery { bloodIndicatorDao.getByDate(date) } returns entity

            // When
            val result = repository.getByDate(date)

            // Then
            assertNotNull(result)
            assertEquals(date, result.date)
            assertEquals(BloodSignalType.RISK_ON, result.signalType)
        }

        @Test
        @DisplayName("getByDate()는 데이터가 없을 때 null을 반환한다")
        fun `getByDate_withNoData_returnsNull`() = runTest {
            // Given
            coEvery { bloodIndicatorDao.getByDate(any()) } returns null

            // When
            val result = repository.getByDate("2025-01-01")

            // Then
            assertNull(result)
        }
    }

    // ========== 단순 위임 메서드 ==========

    @Nested
    @DisplayName("단순 위임 메서드 테스트")
    inner class SimpleDelegationTests {

        @Test
        @DisplayName("getCount()는 DAO에서 카운트를 반환한다")
        fun `getCount_delegatesToDao_returnsCount`() = runTest {
            // Given
            coEvery { bloodIndicatorDao.getCount() } returns 52

            // When
            val result = repository.getCount()

            // Then
            assertEquals(52, result)
        }

        @Test
        @DisplayName("getLatestDate()는 DAO에서 최신 날짜를 반환한다")
        fun `getLatestDate_delegatesToDao_returnsLatestDate`() = runTest {
            // Given
            coEvery { bloodIndicatorDao.getLatestDate() } returns "2025-01-15"

            // When
            val result = repository.getLatestDate()

            // Then
            assertEquals("2025-01-15", result)
        }

        @Test
        @DisplayName("getLatestDate()는 데이터가 없을 때 null을 반환한다")
        fun `getLatestDate_withNoData_returnsNull`() = runTest {
            // Given
            coEvery { bloodIndicatorDao.getLatestDate() } returns null

            // When
            val result = repository.getLatestDate()

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("getEarliestDate()는 DAO에서 가장 이른 날짜를 반환한다")
        fun `getEarliestDate_delegatesToDao_returnsEarliestDate`() = runTest {
            // Given
            coEvery { bloodIndicatorDao.getEarliestDate() } returns "2023-01-06"

            // When
            val result = repository.getEarliestDate()

            // Then
            assertEquals("2023-01-06", result)
        }

        @Test
        @DisplayName("getLastUpdateTime()는 DAO에서 마지막 업데이트 시간을 반환한다")
        fun `getLastUpdateTime_delegatesToDao_returnsTimestamp`() = runTest {
            // Given
            val expectedTime = 1705300800000L
            coEvery { bloodIndicatorDao.getLastUpdateTime() } returns expectedTime

            // When
            val result = repository.getLastUpdateTime()

            // Then
            assertEquals(expectedTime, result)
        }

        @Test
        @DisplayName("getLastUpdateTime()는 데이터가 없을 때 null을 반환한다")
        fun `getLastUpdateTime_withNoData_returnsNull`() = runTest {
            // Given
            coEvery { bloodIndicatorDao.getLastUpdateTime() } returns null

            // When
            val result = repository.getLastUpdateTime()

            // Then
            assertNull(result)
        }
    }

    // ========== 다이얼로그 상태 관리 ==========

    @Nested
    @DisplayName("다이얼로그 상태 관리 테스트")
    inner class DialogStateTests {

        @Test
        @DisplayName("isDialogDismissed()는 설정값이 'true'일 때 true를 반환한다")
        fun `isDialogDismissed_whenSettingIsTrue_returnsTrue`() = runTest {
            // Given
            coEvery { etfDao.getSetting("blood_indicator_dialog_dismissed") } returns "true"

            // When
            val result = repository.isDialogDismissed()

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("isDialogDismissed()는 설정이 없을 때 false를 반환한다")
        fun `isDialogDismissed_whenSettingIsNull_returnsFalse`() = runTest {
            // Given
            coEvery { etfDao.getSetting("blood_indicator_dialog_dismissed") } returns null

            // When
            val result = repository.isDialogDismissed()

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("isDialogDismissed()는 설정값이 'false'일 때 false를 반환한다")
        fun `isDialogDismissed_whenSettingIsFalse_returnsFalse`() = runTest {
            // Given
            coEvery { etfDao.getSetting("blood_indicator_dialog_dismissed") } returns "false"

            // When
            val result = repository.isDialogDismissed()

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("saveDialogDismissed()는 'blood_indicator_dialog_dismissed' 키에 'true'를 저장한다")
        fun `saveDialogDismissed_savesCorrectKeyAndValue`() = runTest {
            // Given
            val settingSlot = slot<Setting>()
            coEvery { etfDao.saveSetting(capture(settingSlot)) } returns Unit

            // When
            repository.saveDialogDismissed()

            // Then
            coVerify(exactly = 1) { etfDao.saveSetting(any()) }
            assertEquals("blood_indicator_dialog_dismissed", settingSlot.captured.key)
            assertEquals("true", settingSlot.captured.value)
        }
    }

    // ========== initializeBloodIndicator ==========

    @Nested
    @DisplayName("initializeBloodIndicator 테스트")
    inner class InitializeBloodIndicatorTests {

        @Test
        @DisplayName("FRED API 키가 없으면 실패 Result를 반환한다")
        fun `initializeBloodIndicator_withNoFredApiKey_returnsFailure`() = runTest {
            // Given
            every { fredApiKeyProvider.getApiKey() } returns null

            // When
            val result = repository.initializeBloodIndicator(days = 365)

            // Then
            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }

        @Test
        @DisplayName("FRED API 키가 빈 문자열이면 실패 Result를 반환한다")
        fun `initializeBloodIndicator_withBlankFredApiKey_returnsFailure`() = runTest {
            // Given
            every { fredApiKeyProvider.getApiKey() } returns ""

            // When
            val result = repository.initializeBloodIndicator(days = 365)

            // Then
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("FRED API 키 없음 오류 메시지에 FRED API 관련 내용이 포함된다")
        fun `initializeBloodIndicator_withNoFredApiKey_errorMessageMentionsFredApi`() = runTest {
            // Given
            every { fredApiKeyProvider.getApiKey() } returns null

            // When
            val result = repository.initializeBloodIndicator(days = 365)

            // Then
            val errorMessage = result.exceptionOrNull()?.message
            assertNotNull(errorMessage)
            assertTrue(
                errorMessage.contains("FRED") || errorMessage.contains("API"),
                "Error message should mention FRED API: $errorMessage"
            )
        }

        @Test
        @DisplayName("진행 상황 콜백이 제공되지 않아도 실패 Result를 올바르게 반환한다")
        fun `initializeBloodIndicator_withNullProgress_handlesCallbackGracefully`() = runTest {
            // Given
            every { fredApiKeyProvider.getApiKey() } returns null

            // When
            val result = repository.initializeBloodIndicator(days = 90, onProgress = null)

            // Then
            assertTrue(result.isFailure)
        }
    }

    // ========== updateBloodIndicator ==========

    @Nested
    @DisplayName("updateBloodIndicator 테스트")
    inner class UpdateBloodIndicatorTests {

        @Test
        @DisplayName("FRED API 키가 없으면 실패 Result를 반환한다")
        fun `updateBloodIndicator_withNoFredApiKey_returnsFailure`() = runTest {
            // Given
            every { fredApiKeyProvider.getApiKey() } returns null

            // When
            val result = repository.updateBloodIndicator()

            // Then
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("FRED API 키가 빈 문자열이면 실패 Result를 반환한다")
        fun `updateBloodIndicator_withBlankFredApiKey_returnsFailure`() = runTest {
            // Given
            every { fredApiKeyProvider.getApiKey() } returns ""

            // When
            val result = repository.updateBloodIndicator()

            // Then
            assertTrue(result.isFailure)
        }
    }

    // ========== Helper Functions ==========

    private fun createTestBloodEntity(
        date: String,
        signalType: String,
        bloodValue: Double = 1.2,
        bloodSma: Double = 1.0,
        us03my: Double = 5.3,
        highYieldSpread: Double = 3.5,
        spyClose: Double? = 475.0,
        signalColor: String = if (signalType == "RISK_ON") "green" else if (signalType == "RISK_OFF") "red" else "gray"
    ): BloodIndicatorEntity = BloodIndicatorEntity(
        id = "BLOOD-$date",
        date = date,
        bloodValue = bloodValue,
        bloodSma = bloodSma,
        us03my = us03my,
        highYieldSpread = highYieldSpread,
        spyClose = spyClose,
        signalType = signalType,
        signalColor = signalColor,
        lastUpdated = System.currentTimeMillis()
    )
}
