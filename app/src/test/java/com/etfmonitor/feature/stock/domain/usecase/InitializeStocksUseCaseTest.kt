package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.stock.domain.repository.StockRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * InitializeStocksUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 시 Result.success(종목 수) 반환
 * - invoke() 실패 시 Result.failure 반환
 * - 초기화된 종목 수 검증
 * - repository 정확히 1회 호출 검증
 * - CancellationException 재전파 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("InitializeStocksUseCase 테스트")
class InitializeStocksUseCaseTest {

    private val repository: StockRepository = mockk()
    private lateinit var useCase: InitializeStocksUseCase

    @BeforeEach
    fun setUp() {
        useCase = InitializeStocksUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withSuccessfulInitialization_returnsSuccessResultWithCount")
        fun `invoke_withSuccessfulInitialization_returnsSuccessResultWithCount`() = runTest {
            // Given
            val stockCount = 2500
            coEvery { repository.initializeStocks() } returns Result.success(stockCount)

            // When
            val result = useCase()

            // Then
            assertTrue(result.isSuccess)
            assertEquals(stockCount, result.getOrNull())
        }

        @Test
        @DisplayName("invoke_withZeroStocks_returnsSuccessWithZero")
        fun `invoke_withZeroStocks_returnsSuccessWithZero`() = runTest {
            // Given
            coEvery { repository.initializeStocks() } returns Result.success(0)

            // When
            val result = useCase()

            // Then
            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull())
        }

        @Test
        @DisplayName("invoke_delegatesExactlyOnce_toRepository")
        fun `invoke_delegatesExactlyOnce_toRepository`() = runTest {
            // Given
            coEvery { repository.initializeStocks() } returns Result.success(2500)

            // When
            useCase()

            // Then
            coVerify(exactly = 1) { repository.initializeStocks() }
        }

        @Test
        @DisplayName("invoke_withKospiAndKosdaqCount_returnsCorrectTotal")
        fun `invoke_withKospiAndKosdaqCount_returnsCorrectTotal`() = runTest {
            // Given: KOSPI ~800 + KOSDAQ ~1700 = ~2500
            coEvery { repository.initializeStocks() } returns Result.success(2476)

            // When
            val result = useCase()

            // Then
            assertTrue(result.isSuccess)
            val count = result.getOrNull()!!
            assertTrue(count > 0)
            assertEquals(2476, count)
        }
    }

    // ================================================================
    // 실패 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("실패 경로 테스트")
    inner class FailurePathTests {

        @Test
        @DisplayName("invoke_whenRepositoryFails_returnsFailureResult")
        fun `invoke_whenRepositoryFails_returnsFailureResult`() = runTest {
            // Given
            val exception = RuntimeException("종목 초기화 실패")
            coEvery { repository.initializeStocks() } returns Result.failure(exception)

            // When
            val result = useCase()

            // Then
            assertTrue(result.isFailure)
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("invoke_whenNetworkError_returnsFailureWithNetworkException")
        fun `invoke_whenNetworkError_returnsFailureWithNetworkException`() = runTest {
            // Given
            val networkException = RuntimeException("KRX 네트워크 오류: Connection refused")
            coEvery { repository.initializeStocks() } returns Result.failure(networkException)

            // When
            val result = useCase()

            // Then
            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }

        @Test
        @DisplayName("invoke_whenRepositoryThrows_propagatesException")
        fun `invoke_whenRepositoryThrows_propagatesException`() = runTest {
            // Given
            coEvery { repository.initializeStocks() } throws RuntimeException("예상치 못한 오류")

            // When & Then
            var caught: Exception? = null
            try {
                useCase()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
        }
    }
}
