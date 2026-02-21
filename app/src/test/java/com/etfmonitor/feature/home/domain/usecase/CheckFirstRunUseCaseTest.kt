package com.etfmonitor.feature.home.domain.usecase

import com.etfmonitor.MainDispatcherExtension
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
 * CheckFirstRunUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 첫 실행 시 true 반환 (다이얼로그 표시 필요)
 * - invoke() 이미 초기화된 경우 false 반환
 * - repository 정확히 1회 호출 검증
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("CheckFirstRunUseCase 테스트")
class CheckFirstRunUseCaseTest {

    private val repository: HomeRepository = mockk()
    private lateinit var useCase: CheckFirstRunUseCase

    @BeforeEach
    fun setUp() {
        useCase = CheckFirstRunUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_onFirstRun_returnsTrue")
        fun `invoke_onFirstRun_returnsTrue`() = runTest {
            // Given: 첫 실행 — 통합 초기화 다이얼로그 표시 필요
            coEvery { repository.shouldShowUnifiedInitDialog() } returns true

            // When
            val result = useCase()

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("invoke_afterInitialization_returnsFalse")
        fun `invoke_afterInitialization_returnsFalse`() = runTest {
            // Given: 이미 초기화 완료 — 다이얼로그 불필요
            coEvery { repository.shouldShowUnifiedInitDialog() } returns false

            // When
            val result = useCase()

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("invoke_delegatesExactlyOnce_toRepository")
        fun `invoke_delegatesExactlyOnce_toRepository`() = runTest {
            // Given
            coEvery { repository.shouldShowUnifiedInitDialog() } returns false

            // When
            useCase()

            // Then
            coVerify(exactly = 1) { repository.shouldShowUnifiedInitDialog() }
        }

        @Test
        @DisplayName("invoke_calledTwice_callsRepositoryTwice")
        fun `invoke_calledTwice_callsRepositoryTwice`() = runTest {
            // Given
            coEvery { repository.shouldShowUnifiedInitDialog() } returns true

            // When
            useCase()
            useCase()

            // Then
            coVerify(exactly = 2) { repository.shouldShowUnifiedInitDialog() }
        }

        @Test
        @DisplayName("invoke_returnsRepositoryResultDirectly_noTransformation")
        fun `invoke_returnsRepositoryResultDirectly_noTransformation`() = runTest {
            // Given: 결과를 그대로 전달하는지 검증
            for (expected in listOf(true, false)) {
                coEvery { repository.shouldShowUnifiedInitDialog() } returns expected

                // When
                val result = useCase()

                // Then
                assertEquals(expected, result)
            }
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
            coEvery { repository.shouldShowUnifiedInitDialog() } throws RuntimeException("설정 읽기 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("설정 읽기 실패", caught?.message)
        }
    }
}
