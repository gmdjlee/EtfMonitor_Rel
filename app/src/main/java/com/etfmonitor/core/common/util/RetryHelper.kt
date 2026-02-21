package com.etfmonitor.core.common.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Network retry utility with exponential backoff.
 *
 * Usage:
 * ```kotlin
 * val result = retryWithBackoff {
 *     apiClient.fetchData()
 * }
 * ```
 *
 * Or with custom configuration:
 * ```kotlin
 * val result = retryWithBackoff(
 *     times = 5,
 *     initialDelay = 2000L,
 *     maxDelay = 30000L,
 *     factor = 2.0
 * ) {
 *     apiClient.fetchData()
 * }
 * ```
 */
object RetryHelper {

    private val logger = AppLogger.getLogger("RetryHelper")

    /**
     * Default retry configuration
     */
    object Defaults {
        const val TIMES = 3
        const val INITIAL_DELAY_MS = 1000L
        const val MAX_DELAY_MS = 10000L
        const val FACTOR = 2.0
    }

    /**
     * Executes a suspending block with exponential backoff retry on network errors.
     *
     * @param times Maximum number of retry attempts (default: 3)
     * @param initialDelay Initial delay between retries in milliseconds (default: 1000ms)
     * @param maxDelay Maximum delay between retries in milliseconds (default: 10000ms)
     * @param factor Multiplier for exponential backoff (default: 2.0)
     * @param shouldRetry Custom predicate to determine if an exception should trigger a retry
     * @param block The suspending block to execute
     * @return Result of the block execution
     * @throws Exception if all retry attempts fail
     */
    suspend fun <T> retryWithBackoff(
        times: Int = Defaults.TIMES,
        initialDelay: Long = Defaults.INITIAL_DELAY_MS,
        maxDelay: Long = Defaults.MAX_DELAY_MS,
        factor: Double = Defaults.FACTOR,
        shouldRetry: (Exception) -> Boolean = { isRetryableException(it) },
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        var lastException: Exception? = null

        repeat(times) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastException = e

                if (!shouldRetry(e)) {
                    logger.d("Non-retryable exception on attempt ${attempt + 1}: ${e.javaClass.simpleName}")
                    throw e
                }

                if (attempt < times - 1) {
                    logger.d("Attempt ${attempt + 1} failed, retrying in ${currentDelay}ms: ${e.message}")
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                } else {
                    logger.d("All $times attempts failed: ${e.message}")
                }
            }
        }

        throw lastException ?: IllegalStateException("Retry failed with no exception")
    }

    /**
     * Executes a suspending block with retry, returning Result instead of throwing.
     *
     * @param times Maximum number of retry attempts
     * @param initialDelay Initial delay between retries in milliseconds
     * @param maxDelay Maximum delay between retries in milliseconds
     * @param factor Multiplier for exponential backoff
     * @param block The suspending block to execute
     * @return Result.success with value or Result.failure with exception
     */
    suspend fun <T> retryWithBackoffResult(
        times: Int = Defaults.TIMES,
        initialDelay: Long = Defaults.INITIAL_DELAY_MS,
        maxDelay: Long = Defaults.MAX_DELAY_MS,
        factor: Double = Defaults.FACTOR,
        block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(retryWithBackoff(times, initialDelay, maxDelay, factor, block = block))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Determines if an exception is retryable (network-related).
     */
    fun isRetryableException(e: Exception): Boolean {
        return when (e) {
            is IOException,
            is SocketTimeoutException,
            is UnknownHostException -> true
            else -> {
                // Check for nested retryable exceptions
                e.cause?.let { cause ->
                    cause is IOException ||
                    cause is SocketTimeoutException ||
                    cause is UnknownHostException
                } ?: false
            }
        }
    }
}

/**
 * Top-level function for convenient usage.
 */
suspend fun <T> retryWithBackoff(
    times: Int = RetryHelper.Defaults.TIMES,
    initialDelay: Long = RetryHelper.Defaults.INITIAL_DELAY_MS,
    maxDelay: Long = RetryHelper.Defaults.MAX_DELAY_MS,
    factor: Double = RetryHelper.Defaults.FACTOR,
    block: suspend () -> T
): T = RetryHelper.retryWithBackoff(times, initialDelay, maxDelay, factor, block = block)
