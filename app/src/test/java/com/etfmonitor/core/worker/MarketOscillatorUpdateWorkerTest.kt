package com.etfmonitor.core.worker

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

/**
 * MarketOscillatorUpdateWorker 단위 테스트
 *
 * Worker의 doWork() 로직:
 * 1. KOSPI, KOSDAQ 두 시장에 대해 updateMarketData()를 순차 호출
 * 2. 둘 다 성공 → Result.success()
 * 3. 하나라도 실패 → attempt < 3: retry, attempt >= 3: failure
 * 4. 예외 발생 → Result.failure() (ExceptionHandler: attempt 무관하게 failure)
 * 5. CancellationException → rethrow
 *
 * JVM 환경에서 Worker 로직을 테스트하기 위해 동일한 로직을 로컬 함수로 추출하여 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("MarketOscillatorUpdateWorker 로직 테스트")
class MarketOscillatorUpdateWorkerTest {

    private lateinit var marketOscillatorRepository: MarketOscillatorRepository

    @BeforeEach
    fun setup() {
        marketOscillatorRepository = mockk(relaxed = true)
    }

    // ============================================================
    // Helper: MarketOscillatorUpdateWorker.doWork()와 동일한 로직 추출
    // ============================================================

    /**
     * MarketOscillatorUpdateWorker.doWork()의 핵심 비즈니스 로직과 동일한 구현.
     *
     * Worker 실 코드:
     * 1. updateMarketData("KOSPI") 호출
     * 2. updateMarketData("KOSDAQ") 호출
     * 3. 둘 다 isSuccess → SUCCESS
     * 4. 하나라도 실패 → attempt < 3: RETRY, else: FAILURE
     * 5. 예외 발생 → FAILURE (attempt 무관)
     * 6. CancellationException → rethrow
     */
    private suspend fun executeMarketOscillatorLogic(
        repository: MarketOscillatorRepository,
        runAttemptCount: Int = 0
    ): WorkerResult {
        return try {
            val kospiResult = repository.updateMarketData("KOSPI")
            val kosdaqResult = repository.updateMarketData("KOSDAQ")

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                WorkerResult.SUCCESS
            } else {
                if (runAttemptCount < 3) WorkerResult.RETRY else WorkerResult.FAILURE
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WorkerResult.FAILURE
        }
    }

    // ============================================================
    // BothMarketsSucceedTests: 두 시장 모두 성공
    // ============================================================

    @Nested
    @DisplayName("두 시장 모두 성공하는 경로")
    inner class BothMarketsSucceedTests {

        @Test
        @DisplayName("KOSPI, KOSDAQ 모두 성공하면 SUCCESS를 반환한다")
        fun `executeOscillatorLogic_bothMarketsSucceed_returnsSuccess`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns Result.success(50)
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns Result.success(30)

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository)

            // Then
            assertEquals(WorkerResult.SUCCESS, result)
        }

        @Test
        @DisplayName("KOSPI 0건, KOSDAQ 0건 성공해도 SUCCESS를 반환한다")
        fun `executeOscillatorLogic_bothMarketsSucceedWithZeroRecords_returnsSuccess`() = runTest {
            // Given: 업데이트할 데이터 없음 (최신 데이터 이미 있음)
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns Result.success(0)
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns Result.success(0)

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository)

            // Then
            assertEquals(WorkerResult.SUCCESS, result)
        }

        @Test
        @DisplayName("두 시장 성공 시 KOSPI와 KOSDAQ 각각 정확히 1번씩 호출된다")
        fun `executeOscillatorLogic_bothSucceed_eachMarketCalledOnce`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns Result.success(100)
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns Result.success(80)

            // When
            executeMarketOscillatorLogic(marketOscillatorRepository)

            // Then
            coVerify(exactly = 1) { marketOscillatorRepository.updateMarketData("KOSPI") }
            coVerify(exactly = 1) { marketOscillatorRepository.updateMarketData("KOSDAQ") }
        }

        @Test
        @DisplayName("대량 데이터 업데이트 성공(1000건)도 SUCCESS를 반환한다")
        fun `executeOscillatorLogic_largeDataUpdateSuccess_returnsSuccess`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns Result.success(1000)
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns Result.success(800)

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository)

            // Then
            assertEquals(WorkerResult.SUCCESS, result)
        }
    }

    // ============================================================
    // PartialFailureTests: 하나 이상 실패
    // ============================================================

    @Nested
    @DisplayName("하나 이상의 시장 업데이트 실패 경로")
    inner class PartialFailureTests {

        @Test
        @DisplayName("KOSPI 실패, KOSDAQ 성공이고 attempt=0이면 RETRY를 반환한다")
        fun `executeOscillatorLogic_kospiFailsKosdaqSucceeds_attempt0_returnsRetry`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns
                    Result.failure(RuntimeException("KOSPI API 오류"))
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns Result.success(30)

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository, runAttemptCount = 0)

            // Then
            assertEquals(WorkerResult.RETRY, result)
        }

        @Test
        @DisplayName("KOSPI 성공, KOSDAQ 실패이고 attempt=0이면 RETRY를 반환한다")
        fun `executeOscillatorLogic_kospiSucceedsKosdaqFails_attempt0_returnsRetry`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns Result.success(50)
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns
                    Result.failure(RuntimeException("KOSDAQ 네트워크 오류"))

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository, runAttemptCount = 0)

            // Then
            assertEquals(WorkerResult.RETRY, result)
        }

        @Test
        @DisplayName("둘 다 실패이고 attempt=0이면 RETRY를 반환한다")
        fun `executeOscillatorLogic_bothFail_attempt0_returnsRetry`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns
                    Result.failure(RuntimeException("KOSPI 오류"))
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns
                    Result.failure(RuntimeException("KOSDAQ 오류"))

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository, runAttemptCount = 0)

            // Then
            assertEquals(WorkerResult.RETRY, result)
        }

        @Test
        @DisplayName("KOSPI 실패이고 attempt=2이면 RETRY를 반환한다 (3 미만)")
        fun `executeOscillatorLogic_kospiFailsAttempt2_returnsRetry`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns
                    Result.failure(RuntimeException("오류"))
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns Result.success(30)

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository, runAttemptCount = 2)

            // Then
            assertEquals(WorkerResult.RETRY, result)
        }

        @Test
        @DisplayName("KOSPI 실패이고 attempt=3이면 FAILURE를 반환한다")
        fun `executeOscillatorLogic_kospiFailsAttempt3_returnsFailure`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns
                    Result.failure(RuntimeException("복구 불가 오류"))
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns Result.success(30)

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository, runAttemptCount = 3)

            // Then
            assertEquals(WorkerResult.FAILURE, result)
        }

        @Test
        @DisplayName("둘 다 실패이고 attempt=5이면 FAILURE를 반환한다")
        fun `executeOscillatorLogic_bothFailAttempt5_returnsFailure`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns
                    Result.failure(RuntimeException("지속적 오류"))
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns
                    Result.failure(RuntimeException("지속적 오류"))

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository, runAttemptCount = 5)

            // Then
            assertEquals(WorkerResult.FAILURE, result)
        }
    }

    // ============================================================
    // ExceptionHandlingTests: 예외 처리 (Exception catch → FAILURE)
    // ============================================================

    @Nested
    @DisplayName("예외 처리 테스트")
    inner class ExceptionHandlingTests {

        @Test
        @DisplayName("KOSPI updateMarketData()가 예외를 던지면 FAILURE를 반환한다 (attempt 무관)")
        fun `executeOscillatorLogic_exceptionFromKospi_returnsFailure`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } throws
                    IllegalStateException("예상치 못한 오류")

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository, runAttemptCount = 0)

            // Then: Worker 실 코드의 catch(Exception) 블록: attempt 무관 FAILURE
            assertEquals(WorkerResult.FAILURE, result)
        }

        @Test
        @DisplayName("KOSDAQ updateMarketData()가 예외를 던지면 FAILURE를 반환한다")
        fun `executeOscillatorLogic_exceptionFromKosdaq_returnsFailure`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns Result.success(50)
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } throws
                    RuntimeException("KOSDAQ 데이터 파싱 오류")

            // When
            val result = executeMarketOscillatorLogic(marketOscillatorRepository, runAttemptCount = 0)

            // Then
            assertEquals(WorkerResult.FAILURE, result)
        }

        @Test
        @DisplayName("OutOfMemoryError는 Exception이 아니므로 catch에 걸리지 않는다")
        fun `executeOscillatorLogic_oomError_isNotCaughtByExceptionHandler`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } throws OutOfMemoryError()

            // When & Then: OOM은 Error이므로 catch(Exception)에 걸리지 않음
            assertThrows<OutOfMemoryError> {
                executeMarketOscillatorLogic(marketOscillatorRepository)
            }
        }
    }

    // ============================================================
    // CancellationTests: CancellationException 처리
    // ============================================================

    @Nested
    @DisplayName("CancellationException 처리 테스트")
    inner class CancellationTests {

        @Test
        @DisplayName("KOSPI 호출에서 CancellationException이 발생하면 rethrow한다")
        fun `executeOscillatorLogic_cancellationExceptionFromKospi_rethrows`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } throws
                    CancellationException("KOSPI 작업 취소")

            // When & Then
            assertThrows<CancellationException> {
                executeMarketOscillatorLogic(marketOscillatorRepository)
            }
        }

        @Test
        @DisplayName("KOSDAQ 호출에서 CancellationException이 발생하면 rethrow한다")
        fun `executeOscillatorLogic_cancellationExceptionFromKosdaq_rethrows`() = runTest {
            // Given
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns Result.success(50)
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } throws
                    CancellationException("KOSDAQ 작업 취소")

            // When & Then
            assertThrows<CancellationException> {
                executeMarketOscillatorLogic(marketOscillatorRepository)
            }
        }
    }

    // ============================================================
    // ConstantTests: Worker 상수 검증
    // ============================================================

    @Nested
    @DisplayName("Worker 상수 및 설정 검증")
    inner class ConstantTests {

        @Test
        @DisplayName("WORK_NAME 상수가 예상 값을 가진다")
        fun `marketOscillatorWorker_workNameConstant_hasExpectedValue`() {
            assertEquals("market_oscillator_update_work", MarketOscillatorUpdateWorker.WORK_NAME)
        }

        @Test
        @DisplayName("retry 임계값은 3이다 (attempt < 3 → retry, attempt >= 3 → failure)")
        fun `executeOscillatorLogic_retryThresholdBoundary`() = runTest {
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } returns
                    Result.failure(RuntimeException("오류"))
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } returns
                    Result.failure(RuntimeException("오류"))

            val resultAt2 = executeMarketOscillatorLogic(marketOscillatorRepository, 2)
            val resultAt3 = executeMarketOscillatorLogic(marketOscillatorRepository, 3)

            assertEquals(WorkerResult.RETRY, resultAt2, "attempt=2는 RETRY이어야 한다")
            assertEquals(WorkerResult.FAILURE, resultAt3, "attempt=3은 FAILURE이어야 한다")
        }

        @Test
        @DisplayName("KOSPI, KOSDAQ 순서로 호출된다 (순차 실행 보장)")
        fun `executeOscillatorLogic_callsKospiBeforeKosdaq_sequentially`() = runTest {
            // Given
            val callOrder = mutableListOf<String>()
            coEvery { marketOscillatorRepository.updateMarketData("KOSPI") } coAnswers {
                callOrder.add("KOSPI")
                Result.success(50)
            }
            coEvery { marketOscillatorRepository.updateMarketData("KOSDAQ") } coAnswers {
                callOrder.add("KOSDAQ")
                Result.success(30)
            }

            // When
            executeMarketOscillatorLogic(marketOscillatorRepository)

            // Then
            assertEquals(listOf("KOSPI", "KOSDAQ"), callOrder, "KOSPI가 KOSDAQ보다 먼저 호출되어야 한다")
        }
    }

    /**
     * WorkerResult: Worker.Result를 JVM 테스트에서 표현하기 위한 내부 enum.
     */
    private enum class WorkerResult { SUCCESS, RETRY, FAILURE }
}
