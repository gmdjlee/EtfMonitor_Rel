package com.etfmonitor.core.common.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RetryHelper 단위 테스트
 *
 * 테스트 범위:
 * - retryWithBackoff: 성공, 재시도, 비재시도 예외, 횟수 소진
 * - retryWithBackoffResult: Result.success / Result.failure / CancellationException 재전파
 * - isRetryableException: IOException, SocketTimeoutException, 기타
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("RetryHelper 테스트")
class RetryHelperTest {

    // =========================================================================
    // retryWithBackoff
    // =========================================================================

    @Nested
    @DisplayName("retryWithBackoff — 재시도 로직")
    inner class RetryWithBackoffTests {

        @Test
        @DisplayName("첫 번째 시도에 성공하면 즉시 결과를 반환한다")
        fun `retryWithBackoff succeeds on first try`() = runTest {
            val result = RetryHelper.retryWithBackoff(
                times = 3,
                initialDelay = 0L,
                maxDelay = 0L
            ) {
                "success"
            }

            assertEquals("success", result)
        }

        @Test
        @DisplayName("IOException 발생 후 재시도하여 성공한다")
        fun `retryWithBackoff retries on IOException and succeeds`() = runTest {
            var callCount = 0

            val result = RetryHelper.retryWithBackoff(
                times = 3,
                initialDelay = 0L,
                maxDelay = 0L
            ) {
                callCount++
                if (callCount < 2) throw IOException("Network error")
                "recovered"
            }

            assertEquals("recovered", result)
            assertEquals(2, callCount)
        }

        @Test
        @DisplayName("SocketTimeoutException 발생 시 재시도한다")
        fun `retryWithBackoff retries on SocketTimeoutException`() = runTest {
            var callCount = 0

            val result = RetryHelper.retryWithBackoff(
                times = 3,
                initialDelay = 0L,
                maxDelay = 0L
            ) {
                callCount++
                if (callCount < 3) throw SocketTimeoutException("Timeout")
                "ok"
            }

            assertEquals("ok", result)
            assertEquals(3, callCount)
        }

        @Test
        @DisplayName("CancellationException은 재시도 없이 즉시 전파된다")
        fun `retryWithBackoff throws CancellationException immediately without retry`() = runTest {
            var callCount = 0

            assertThrows<CancellationException> {
                RetryHelper.retryWithBackoff(
                    times = 3,
                    initialDelay = 0L,
                    maxDelay = 0L,
                    shouldRetry = { e -> e !is CancellationException }
                ) {
                    callCount++
                    throw CancellationException("cancelled")
                }
            }

            // CancellationException은 shouldRetry=false → 재시도 없이 즉시 던져야 함
            assertEquals(1, callCount)
        }

        @Test
        @DisplayName("비재시도 예외(IllegalArgumentException)는 첫 번째 시도 후 즉시 던진다")
        fun `retryWithBackoff throws non-retryable exception immediately`() = runTest {
            var callCount = 0

            assertThrows<IllegalArgumentException> {
                RetryHelper.retryWithBackoff(
                    times = 3,
                    initialDelay = 0L,
                    maxDelay = 0L
                ) {
                    callCount++
                    throw IllegalArgumentException("bad argument")
                }
            }

            assertEquals(1, callCount)
        }

        @Test
        @DisplayName("모든 재시도 횟수를 소진하면 마지막 예외를 던진다")
        fun `retryWithBackoff exhausts all retries and throws last exception`() = runTest {
            var callCount = 0

            val thrown = assertThrows<IOException> {
                RetryHelper.retryWithBackoff(
                    times = 3,
                    initialDelay = 0L,
                    maxDelay = 0L
                ) {
                    callCount++
                    throw IOException("attempt $callCount")
                }
            }

            assertEquals(3, callCount)
            // 마지막 예외 메시지 확인
            assertEquals("attempt 3", thrown.message)
        }

        @Test
        @DisplayName("times=1이면 재시도 없이 즉시 실패한다")
        fun `retryWithBackoff with times=1 fails on first attempt`() = runTest {
            var callCount = 0

            assertThrows<IOException> {
                RetryHelper.retryWithBackoff(
                    times = 1,
                    initialDelay = 0L,
                    maxDelay = 0L
                ) {
                    callCount++
                    throw IOException("single attempt")
                }
            }

            assertEquals(1, callCount)
        }

        @Test
        @DisplayName("커스텀 shouldRetry 조건으로 RuntimeException을 재시도한다")
        fun `retryWithBackoff with custom shouldRetry retries RuntimeException`() = runTest {
            var callCount = 0

            val result = RetryHelper.retryWithBackoff(
                times = 3,
                initialDelay = 0L,
                maxDelay = 0L,
                shouldRetry = { it is RuntimeException }
            ) {
                callCount++
                if (callCount < 2) throw RuntimeException("runtime error")
                42
            }

            assertEquals(42, result)
            assertEquals(2, callCount)
        }
    }

    // =========================================================================
    // retryWithBackoffResult
    // =========================================================================

    @Nested
    @DisplayName("retryWithBackoffResult — Result 래퍼")
    inner class RetryWithBackoffResultTests {

        @Test
        @DisplayName("성공 시 Result.success를 반환한다")
        fun `retryWithBackoffResult returns Result_success on success`() = runTest {
            val result = RetryHelper.retryWithBackoffResult(
                times = 3,
                initialDelay = 0L,
                maxDelay = 0L
            ) {
                "hello"
            }

            assertTrue(result.isSuccess)
            assertEquals("hello", result.getOrNull())
        }

        @Test
        @DisplayName("비재시도 예외는 Result.failure로 래핑된다")
        fun `retryWithBackoffResult returns Result_failure on non-retryable exception`() = runTest {
            val result = RetryHelper.retryWithBackoffResult(
                times = 3,
                initialDelay = 0L,
                maxDelay = 0L
            ) {
                throw IllegalArgumentException("bad arg")
            }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

        @Test
        @DisplayName("재시도 소진 후 IOException은 Result.failure로 래핑된다")
        fun `retryWithBackoffResult wraps IOException in Result_failure after exhausting retries`() = runTest {
            val result = RetryHelper.retryWithBackoffResult(
                times = 2,
                initialDelay = 0L,
                maxDelay = 0L
            ) {
                throw IOException("connection refused")
            }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }

        @Test
        @DisplayName("CancellationException은 Result로 래핑되지 않고 재전파된다")
        fun `retryWithBackoffResult rethrows CancellationException not wrapped in Result`() = runTest {
            assertThrows<CancellationException> {
                RetryHelper.retryWithBackoffResult(
                    times = 3,
                    initialDelay = 0L,
                    maxDelay = 0L
                ) {
                    // retryWithBackoffResult itself rethrows CancellationException
                    // We simulate this via a shouldRetry that doesn't match, so
                    // the underlying retryWithBackoff will rethrow it. But since
                    // the default shouldRetry only checks for IOException types,
                    // a CancellationException thrown inside will be caught by the
                    // outer catch(e: CancellationException) { throw e } block.
                    throw CancellationException("test cancellation")
                }
            }
        }

        @Test
        @DisplayName("성공 결과의 값은 원본 블록의 반환값과 같다")
        fun `retryWithBackoffResult success value matches block return value`() = runTest {
            val expected = listOf(1, 2, 3)

            val result = RetryHelper.retryWithBackoffResult(
                initialDelay = 0L
            ) {
                expected
            }

            assertEquals(expected, result.getOrNull())
        }
    }

    // =========================================================================
    // isRetryableException
    // =========================================================================

    @Nested
    @DisplayName("isRetryableException — 재시도 가능 여부 판단")
    inner class IsRetryableExceptionTests {

        @Test
        @DisplayName("IOException은 재시도 가능하다")
        fun `isRetryableException returns true for IOException`() {
            assertTrue(RetryHelper.isRetryableException(IOException("io error")))
        }

        @Test
        @DisplayName("SocketTimeoutException은 재시도 가능하다")
        fun `isRetryableException returns true for SocketTimeoutException`() {
            assertTrue(RetryHelper.isRetryableException(SocketTimeoutException("timeout")))
        }

        @Test
        @DisplayName("UnknownHostException은 재시도 가능하다")
        fun `isRetryableException returns true for UnknownHostException`() {
            assertTrue(RetryHelper.isRetryableException(UnknownHostException("unknown host")))
        }

        @Test
        @DisplayName("SocketTimeoutException은 IOException의 하위 클래스이므로 재시도 가능하다")
        fun `isRetryableException returns true for SocketTimeoutException as IOException subtype`() {
            // SocketTimeoutException extends InterruptedIOException extends IOException
            val e = SocketTimeoutException("timed out")
            assertTrue(RetryHelper.isRetryableException(e))
        }

        @Test
        @DisplayName("IllegalArgumentException은 재시도 불가능하다")
        fun `isRetryableException returns false for IllegalArgumentException`() {
            assertFalse(RetryHelper.isRetryableException(IllegalArgumentException("bad arg")))
        }

        @Test
        @DisplayName("RuntimeException은 재시도 불가능하다")
        fun `isRetryableException returns false for RuntimeException`() {
            assertFalse(RetryHelper.isRetryableException(RuntimeException("runtime")))
        }

        @Test
        @DisplayName("IllegalStateException은 재시도 불가능하다")
        fun `isRetryableException returns false for IllegalStateException`() {
            assertFalse(RetryHelper.isRetryableException(IllegalStateException("bad state")))
        }

        @Test
        @DisplayName("원인이 IOException인 RuntimeException은 재시도 가능하다")
        fun `isRetryableException returns true for RuntimeException with IOException cause`() {
            val e = RuntimeException("wrapped", IOException("inner io"))
            assertTrue(RetryHelper.isRetryableException(e))
        }

        @Test
        @DisplayName("원인이 없는 일반 Exception은 재시도 불가능하다")
        fun `isRetryableException returns false for plain Exception without io cause`() {
            assertFalse(RetryHelper.isRetryableException(Exception("plain exception")))
        }

        @Test
        @DisplayName("원인이 UnknownHostException인 RuntimeException은 재시도 가능하다")
        fun `isRetryableException returns true for exception with UnknownHostException cause`() {
            val e = RuntimeException("wrapped", UnknownHostException("no host"))
            assertTrue(RetryHelper.isRetryableException(e))
        }
    }
}
