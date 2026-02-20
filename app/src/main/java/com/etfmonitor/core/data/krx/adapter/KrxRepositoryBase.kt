package com.etfmonitor.core.data.krx.adapter

import com.krxkt.error.KrxError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

abstract class KrxRepositoryBase {
    /**
     * Wrapper for kotlin_krx calls with timeout, error mapping, and IO dispatching.
     *
     * FIX W1: Supports configurable timeout (default 30s, up to 180s for large operations)
     * FIX W5: Catches both KrxError and generic Exception
     */
    protected suspend fun <T> krxCall(
        timeoutMs: Long = 30_000L,
        block: suspend () -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) {
                Result.success(block())
            }
        } catch (e: KrxError) {
            Result.failure(KrxErrorMapper.toException(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // FIX W5: Generic exception catch to prevent uncaught errors
            Result.failure(Exception("Unexpected error: ${e.message}", e))
        }
    }
}
