package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.etf.domain.model.DataStatus
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CheckDataStatusUseCase (etf) 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 데이터 존재 시 DataStatus 반환
 * - invoke() 데이터 없을 때 DataStatus 반환
 * - hasData() 위임 검증
 * - 예외 전파
 * - repository 정확히 1회 호출 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("etf.CheckDataStatusUseCase 테스트")
class CheckDataStatusUseCaseTest {

    private val repository: EtfRepository = mockk()
    private lateinit var useCase: CheckDataStatusUseCase

    @BeforeEach
    fun setUp() {
        useCase = CheckDataStatusUseCase(repository)
    }

    // ================================================================
    // invoke() 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("invoke() 성공 경로")
    inner class InvokeSuccessTests {

        @Test
        @DisplayName("invoke_withDataPresent_returnsDataStatusWithHasDataTrue")
        fun `invoke_withDataPresent_returnsDataStatusWithHasDataTrue`() = runTest {
            // Given
            val expected = DataStatus(hasData = true, latestDate = "2026-02-19")
            coEvery { repository.getDataStatus() } returns expected

            // When
            val result = useCase()

            // Then
            assertTrue(result.hasData)
            assertEquals("2026-02-19", result.latestDate)
        }

        @Test
        @DisplayName("invoke_withNoData_returnsDataStatusWithHasDataFalse")
        fun `invoke_withNoData_returnsDataStatusWithHasDataFalse`() = runTest {
            // Given
            val expected = DataStatus(hasData = false, latestDate = null)
            coEvery { repository.getDataStatus() } returns expected

            // When
            val result = useCase()

            // Then
            assertFalse(result.hasData)
            assertNull(result.latestDate)
        }

        @Test
        @DisplayName("invoke_delegatesExactlyOnce_toRepository")
        fun `invoke_delegatesExactlyOnce_toRepository`() = runTest {
            // Given
            coEvery { repository.getDataStatus() } returns DataStatus(true, "2026-02-19")

            // When
            useCase()

            // Then
            coVerify(exactly = 1) { repository.getDataStatus() }
        }
    }

    // ================================================================
    // hasData() 위임 검증 테스트
    // ================================================================

    @Nested
    @DisplayName("hasData() 위임 테스트")
    inner class HasDataTests {

        @Test
        @DisplayName("hasData_whenDataExists_returnsTrue")
        fun `hasData_whenDataExists_returnsTrue`() = runTest {
            // Given
            coEvery { repository.hasData() } returns true

            // When
            val result = useCase.hasData()

            // Then
            assertTrue(result)
            coVerify(exactly = 1) { repository.hasData() }
        }

        @Test
        @DisplayName("hasData_whenNoData_returnsFalse")
        fun `hasData_whenNoData_returnsFalse`() = runTest {
            // Given
            coEvery { repository.hasData() } returns false

            // When
            val result = useCase.hasData()

            // Then
            assertFalse(result)
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
            coEvery { repository.getDataStatus() } throws RuntimeException("DB 오류")

            // When & Then
            var caught: Exception? = null
            try {
                useCase()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("DB 오류", caught?.message)
        }
    }
}
