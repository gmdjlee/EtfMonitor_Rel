package com.etfmonitor.feature.home.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.home.domain.model.DataStatus
import com.etfmonitor.feature.home.domain.repository.HomeRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * home.CheckDataStatusUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 모든 데이터 존재 시 DataStatus 반환
 * - invoke() 일부 데이터만 존재 시 DataStatus 반환
 * - invoke() 데이터 없을 때 DataStatus 반환
 * - hasAnyData / hasAllData 속성 검증
 * - CheckEtfDataUseCase 위임 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("home.CheckDataStatusUseCase 테스트")
class CheckDataStatusUseCaseTest {

    private val repository: HomeRepository = mockk()
    private lateinit var useCase: CheckDataStatusUseCase
    private lateinit var checkEtfDataUseCase: CheckEtfDataUseCase

    @BeforeEach
    fun setUp() {
        useCase = CheckDataStatusUseCase(repository)
        checkEtfDataUseCase = CheckEtfDataUseCase(repository)
    }

    // ================================================================
    // CheckDataStatusUseCase 테스트
    // ================================================================

    @Nested
    @DisplayName("CheckDataStatusUseCase 성공 경로")
    inner class CheckDataStatusSuccessTests {

        @Test
        @DisplayName("invoke_withAllDataPresent_returnsDataStatusHasAllData")
        fun `invoke_withAllDataPresent_returnsDataStatusHasAllData`() = runTest {
            // Given
            val expected = DataStatus(
                hasEtfData = true,
                hasDepositData = true,
                hasFearGreedData = true,
                hasOscillatorData = true
            )
            coEvery { repository.getDataStatus() } returns expected

            // When
            val result = useCase()

            // Then
            assertTrue(result.hasEtfData)
            assertTrue(result.hasDepositData)
            assertTrue(result.hasFearGreedData)
            assertTrue(result.hasOscillatorData)
            assertTrue(result.hasAllData)
            assertTrue(result.hasAnyData)
        }

        @Test
        @DisplayName("invoke_withNoData_returnsDataStatusHasNoData")
        fun `invoke_withNoData_returnsDataStatusHasNoData`() = runTest {
            // Given
            val expected = DataStatus(
                hasEtfData = false,
                hasDepositData = false,
                hasFearGreedData = false,
                hasOscillatorData = false
            )
            coEvery { repository.getDataStatus() } returns expected

            // When
            val result = useCase()

            // Then
            assertFalse(result.hasEtfData)
            assertFalse(result.hasAllData)
            assertFalse(result.hasAnyData)
        }

        @Test
        @DisplayName("invoke_withPartialData_hasAnyDataTrue")
        fun `invoke_withPartialData_hasAnyDataTrue`() = runTest {
            // Given
            val expected = DataStatus(
                hasEtfData = true,
                hasDepositData = false,
                hasFearGreedData = false,
                hasOscillatorData = false
            )
            coEvery { repository.getDataStatus() } returns expected

            // When
            val result = useCase()

            // Then
            assertTrue(result.hasAnyData)
            assertFalse(result.hasAllData)
        }

        @Test
        @DisplayName("invoke_delegatesExactlyOnce_toRepository")
        fun `invoke_delegatesExactlyOnce_toRepository`() = runTest {
            // Given
            coEvery { repository.getDataStatus() } returns DataStatus(
                hasEtfData = false, hasDepositData = false,
                hasFearGreedData = false, hasOscillatorData = false
            )

            // When
            useCase()

            // Then
            coVerify(exactly = 1) { repository.getDataStatus() }
        }
    }

    // ================================================================
    // CheckEtfDataUseCase 테스트
    // ================================================================

    @Nested
    @DisplayName("CheckEtfDataUseCase 성공 경로")
    inner class CheckEtfDataSuccessTests {

        @Test
        @DisplayName("invoke_withEtfDataAndLatestDate_returnsPairWithTrue")
        fun `invoke_withEtfDataAndLatestDate_returnsPairWithTrue`() = runTest {
            // Given
            coEvery { repository.hasEtfData() } returns true
            coEvery { repository.getLatestDate() } returns "2026-02-19"

            // When
            val result = checkEtfDataUseCase()

            // Then
            assertTrue(result.first)
            assertEquals("2026-02-19", result.second)
        }

        @Test
        @DisplayName("invoke_withNoEtfData_returnsPairWithFalseAndNullDate")
        fun `invoke_withNoEtfData_returnsPairWithFalseAndNullDate`() = runTest {
            // Given
            coEvery { repository.hasEtfData() } returns false
            coEvery { repository.getLatestDate() } returns null

            // When
            val result = checkEtfDataUseCase()

            // Then
            assertFalse(result.first)
            assertEquals(null, result.second)
        }
    }

    // ================================================================
    // 실패 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("실패 경로 테스트")
    inner class FailurePathTests {

        @Test
        @DisplayName("invoke_whenRepositoryThrows_propagatesException")
        fun `invoke_whenRepositoryThrows_propagatesException`() = runTest {
            // Given
            coEvery { repository.getDataStatus() } throws RuntimeException("상태 조회 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("상태 조회 실패", caught?.message)
        }
    }
}
