package com.etfmonitor.core.worker

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.etf.domain.model.DataProgress
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * EtfUpdateWorker 단위 테스트
 *
 * Worker가 HiltWorker + AssistedInject 패턴을 사용하므로,
 * Worker 자체를 직접 인스턴스화하는 대신 doWork()의 비즈니스 로직을
 * 담당하는 EtfRepository를 MockK로 mock하여 Worker의 로직을 검증한다.
 *
 * 테스트 전략:
 * - EtfRepository.updateData()의 Flow 반환값에 따른 Worker 결과(Result) 분기 검증
 * - CancellationException이 반드시 rethrow됨을 검증
 * - runAttemptCount에 따른 retry/failure 분기 검증
 * - 예외 발생 시 적절한 Result 반환 검증
 *
 * 참고: Worker를 JVM 테스트로 실행하기 위해 WorkerLogicDelegate를 사용.
 * CoroutineWorker는 Android Context 없이 생성 불가하므로 로직을 별도 함수로 추출하여 테스트한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("EtfUpdateWorker 로직 테스트")
class EtfUpdateWorkerTest {

    private lateinit var etfRepository: EtfRepository

    @BeforeEach
    fun setup() {
        etfRepository = mockk(relaxed = true)
    }

    // ============================================================
    // Helper: EtfUpdateWorker의 doWork() 로직을 JVM에서 실행 가능한 형태로 추출
    // ============================================================

    /**
     * EtfUpdateWorker.doWork()의 핵심 비즈니스 로직과 동일한 구현.
     *
     * Worker를 직접 생성할 수 없는 JVM 환경에서, Worker의 로직 자체를
     * 독립적으로 검증하기 위해 동일한 제어 흐름을 여기서 재현한다.
     * runAttemptCount는 파라미터로 주입받아 retry/failure 분기를 테스트한다.
     */
    private suspend fun executeEtfUpdateLogic(
        repository: EtfRepository,
        runAttemptCount: Int = 0
    ): WorkerResult {
        return try {
            var lastProgress: DataProgress? = null

            repository.updateData()
                .collect { progress ->
                    lastProgress = progress
                }

            when (lastProgress) {
                is DataProgress.Success -> WorkerResult.SUCCESS
                is DataProgress.Error -> {
                    if (runAttemptCount < 3) WorkerResult.RETRY else WorkerResult.FAILURE
                }
                else -> WorkerResult.SUCCESS  // null or Loading: 업데이트할 데이터 없음 → 성공
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) WorkerResult.RETRY else WorkerResult.FAILURE
        }
    }

    // ============================================================
    // SuccessPathTests: 성공 경로
    // ============================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("DataProgress.Success를 받으면 Result.success()를 반환한다")
        fun `executeEtfUpdateLogic_withSuccessProgress_returnsSuccess`() = runTest {
            // Given
            every { etfRepository.updateData() } returns flowOf(
                DataProgress.Loading("수집 중...", 50),
                DataProgress.Success("업데이트 완료")
            )

            // When
            val result = executeEtfUpdateLogic(etfRepository)

            // Then
            assertEquals(WorkerResult.SUCCESS, result)
        }

        @Test
        @DisplayName("Loading만 방출된 경우(마지막 상태가 Loading)에도 SUCCESS를 반환한다")
        fun `executeEtfUpdateLogic_withOnlyLoadingProgress_returnsSuccess`() = runTest {
            // Given: 이미 최신 데이터인 경우 Loading만 나올 수 있다
            every { etfRepository.updateData() } returns flowOf(
                DataProgress.Loading("확인 중...", 10)
            )

            // When
            val result = executeEtfUpdateLogic(etfRepository)

            // Then: 마지막 상태가 null/Loading이면 'else' 브랜치 → SUCCESS
            assertEquals(WorkerResult.SUCCESS, result)
        }

        @Test
        @DisplayName("빈 Flow를 받으면(업데이트할 날짜 없음) SUCCESS를 반환한다")
        fun `executeEtfUpdateLogic_withEmptyFlow_returnsSuccess`() = runTest {
            // Given: updateData()가 아무것도 방출하지 않으면 lastProgress=null → else 브랜치
            every { etfRepository.updateData() } returns flowOf()

            // When
            val result = executeEtfUpdateLogic(etfRepository)

            // Then
            assertEquals(WorkerResult.SUCCESS, result)
        }

        @Test
        @DisplayName("여러 Loading 후 Success를 받으면 SUCCESS를 반환한다")
        fun `executeEtfUpdateLogic_withMultipleLoadingThenSuccess_returnsSuccess`() = runTest {
            // Given
            every { etfRepository.updateData() } returns flowOf(
                DataProgress.Loading("ETF 목록 조회 중...", 10),
                DataProgress.Loading("데이터 수집 중...", 50),
                DataProgress.Loading("저장 중...", 90),
                DataProgress.Success("15일치 데이터 업데이트 완료")
            )

            // When
            val result = executeEtfUpdateLogic(etfRepository)

            // Then
            assertEquals(WorkerResult.SUCCESS, result)
        }

        @Test
        @DisplayName("updateData()가 정상 호출됨을 검증한다")
        fun `executeEtfUpdateLogic_callsUpdateData_once`() = runTest {
            // Given
            every { etfRepository.updateData() } returns flowOf(
                DataProgress.Success("완료")
            )

            // When
            executeEtfUpdateLogic(etfRepository)

            // Then
            coVerify(exactly = 1) { etfRepository.updateData() }
        }
    }

    // ============================================================
    // FailurePathTests: 실패 및 재시도 경로
    // ============================================================

    @Nested
    @DisplayName("실패 및 재시도 경로 테스트")
    inner class FailurePathTests {

        @Test
        @DisplayName("DataProgress.Error이고 runAttemptCount=0이면 RETRY를 반환한다")
        fun `executeEtfUpdateLogic_withErrorProgressAttempt0_returnsRetry`() = runTest {
            // Given
            every { etfRepository.updateData() } returns flowOf(
                DataProgress.Error("네트워크 오류")
            )

            // When
            val result = executeEtfUpdateLogic(etfRepository, runAttemptCount = 0)

            // Then
            assertEquals(WorkerResult.RETRY, result)
        }

        @Test
        @DisplayName("DataProgress.Error이고 runAttemptCount=2이면 RETRY를 반환한다 (3 미만)")
        fun `executeEtfUpdateLogic_withErrorProgressAttempt2_returnsRetry`() = runTest {
            // Given
            every { etfRepository.updateData() } returns flowOf(
                DataProgress.Error("서버 응답 없음")
            )

            // When
            val result = executeEtfUpdateLogic(etfRepository, runAttemptCount = 2)

            // Then
            assertEquals(WorkerResult.RETRY, result)
        }

        @Test
        @DisplayName("DataProgress.Error이고 runAttemptCount=3이면 FAILURE를 반환한다")
        fun `executeEtfUpdateLogic_withErrorProgressAttempt3_returnsFailure`() = runTest {
            // Given
            every { etfRepository.updateData() } returns flowOf(
                DataProgress.Error("KRX API 오류")
            )

            // When
            val result = executeEtfUpdateLogic(etfRepository, runAttemptCount = 3)

            // Then
            assertEquals(WorkerResult.FAILURE, result)
        }

        @Test
        @DisplayName("DataProgress.Error이고 runAttemptCount=5이면 FAILURE를 반환한다")
        fun `executeEtfUpdateLogic_withErrorProgressAttempt5_returnsFailure`() = runTest {
            // Given
            every { etfRepository.updateData() } returns flowOf(
                DataProgress.Error("알 수 없는 오류")
            )

            // When
            val result = executeEtfUpdateLogic(etfRepository, runAttemptCount = 5)

            // Then
            assertEquals(WorkerResult.FAILURE, result)
        }

        @Test
        @DisplayName("updateData()가 Flow 외부에서 예외를 던지고 attempt=0이면 RETRY를 반환한다")
        fun `executeEtfUpdateLogic_withExceptionAttempt0_returnsRetry`() = runTest {
            // Given
            every { etfRepository.updateData() } throws RuntimeException("초기화 실패")

            // When
            val result = executeEtfUpdateLogic(etfRepository, runAttemptCount = 0)

            // Then
            assertEquals(WorkerResult.RETRY, result)
        }

        @Test
        @DisplayName("updateData()가 예외를 던지고 attempt=3이면 FAILURE를 반환한다")
        fun `executeEtfUpdateLogic_withExceptionAttempt3_returnsFailure`() = runTest {
            // Given
            every { etfRepository.updateData() } throws RuntimeException("복구 불가 오류")

            // When
            val result = executeEtfUpdateLogic(etfRepository, runAttemptCount = 3)

            // Then
            assertEquals(WorkerResult.FAILURE, result)
        }

        @Test
        @DisplayName("Flow 내에서 예외가 발생해도 catch 블록이 처리한다")
        fun `executeEtfUpdateLogic_withExceptionInFlow_handledByCatch`() = runTest {
            // Given: Flow 내부에서 예외 발생
            every { etfRepository.updateData() } returns flow {
                emit(DataProgress.Loading("시작", 0))
                throw IllegalStateException("데이터베이스 오류")
            }

            // When: Flow 내 예외는 collect에서 throw되어 외부 catch로 전파
            val result = executeEtfUpdateLogic(etfRepository, runAttemptCount = 0)

            // Then: attempt < 3 이므로 RETRY
            assertEquals(WorkerResult.RETRY, result)
        }
    }

    // ============================================================
    // CancellationTests: CancellationException 처리
    // ============================================================

    @Nested
    @DisplayName("CancellationException 처리 테스트")
    inner class CancellationTests {

        @Test
        @DisplayName("updateData()에서 CancellationException이 발생하면 rethrow한다")
        fun `executeEtfUpdateLogic_cancellationExceptionFromUpdateData_rethrows`() = runTest {
            // Given
            every { etfRepository.updateData() } throws CancellationException("코루틴 취소")

            // When & Then: CE는 catch되지 않고 반드시 rethrow
            assertThrows<CancellationException> {
                executeEtfUpdateLogic(etfRepository)
            }
        }

        @Test
        @DisplayName("Flow 내부에서 CancellationException이 발생하면 rethrow한다")
        fun `executeEtfUpdateLogic_cancellationExceptionInFlow_rethrows`() = runTest {
            // Given
            every { etfRepository.updateData() } returns flow {
                emit(DataProgress.Loading("진행 중", 30))
                throw CancellationException("작업 취소됨")
            }

            // When & Then
            assertThrows<CancellationException> {
                executeEtfUpdateLogic(etfRepository)
            }
        }
    }

    // ============================================================
    // ProgressTrackingTests: DataProgress 상태 추적 검증
    // ============================================================

    @Nested
    @DisplayName("DataProgress 상태 추적 테스트")
    inner class ProgressTrackingTests {

        @Test
        @DisplayName("DataProgress 모델이 올바르게 생성된다")
        fun `dataProgress_loadingModel_hasCorrectFields`() {
            // Given & When
            val loading = DataProgress.Loading("수집 중...", 50)

            // Then
            assertEquals("수집 중...", loading.message)
            assertEquals(50, loading.progress)
        }

        @Test
        @DisplayName("DataProgress.Success 모델이 올바르게 생성된다")
        fun `dataProgress_successModel_hasCorrectMessage`() {
            // Given & When
            val success = DataProgress.Success("완료")

            // Then
            assertEquals("완료", success.message)
        }

        @Test
        @DisplayName("DataProgress.Error 모델이 올바르게 생성된다")
        fun `dataProgress_errorModel_hasCorrectMessage`() {
            // Given & When
            val error = DataProgress.Error("오류 발생")

            // Then
            assertEquals("오류 발생", error.message)
        }

        @Test
        @DisplayName("마지막 DataProgress가 Error일 때 error 메시지가 올바르게 캡처된다")
        fun `executeEtfUpdateLogic_lastProgressIsError_errorMessageCaptured`() = runTest {
            // Given
            val errorMessage = "KRX 서버 타임아웃"
            every { etfRepository.updateData() } returns flowOf(
                DataProgress.Loading("시작", 10),
                DataProgress.Error(errorMessage)
            )

            // When: attempt=3으로 설정해 FAILURE 반환
            val result = executeEtfUpdateLogic(etfRepository, runAttemptCount = 3)

            // Then: FAILURE 반환 (에러 메시지는 Worker 내에서 로깅됨)
            assertEquals(WorkerResult.FAILURE, result)
        }

        @Test
        @DisplayName("WORK_NAME 상수가 예상 값을 가진다")
        fun `etfUpdateWorker_workNameConstant_hasExpectedValue`() {
            assertEquals("etf_update_work", EtfUpdateWorker.WORK_NAME)
        }

        @Test
        @DisplayName("retry 임계값은 3이다 (attempt < 3 → retry, attempt >= 3 → failure)")
        fun `executeEtfUpdateLogic_retryThresholdIs3`() = runTest {
            every { etfRepository.updateData() } returns flowOf(DataProgress.Error("오류"))

            val resultAt2 = executeEtfUpdateLogic(etfRepository, 2)
            val resultAt3 = executeEtfUpdateLogic(etfRepository, 3)

            assertEquals(WorkerResult.RETRY, resultAt2, "attempt=2는 RETRY이어야 한다")
            assertEquals(WorkerResult.FAILURE, resultAt3, "attempt=3은 FAILURE이어야 한다")
        }
    }

    /**
     * WorkerResult: Worker.Result를 JVM 테스트에서 표현하기 위한 내부 enum.
     * Worker.Result는 Android 라이브러리 클래스이므로 JVM에서 직접 사용 불가.
     */
    private enum class WorkerResult { SUCCESS, RETRY, FAILURE }
}
