package com.etfmonitor.utils

import android.util.Log
import com.etfmonitor.BuildConfig

/**
 * Application-wide logging utility that provides:
 * - Debug/Release build separation
 * - Consistent TAG formatting
 * - Structured error logging with exception handling
 * - Performance-conscious logging (no-op in release for verbose logs)
 *
 * Usage:
 * ```kotlin
 * class MyClass {
 *     companion object {
 *         private val logger = AppLogger.getLogger("MyClass")
 *     }
 *
 *     fun doSomething() {
 *         logger.d("Starting operation")
 *         try {
 *             // ... operation
 *             logger.i("Operation completed successfully")
 *         } catch (e: Exception) {
 *             logger.e("Operation failed", e)
 *         }
 *     }
 * }
 * ```
 */
class AppLogger private constructor(private val tag: String) {

    companion object {
        private const val TAG_PREFIX = "EtfMonitor"
        private const val MAX_TAG_LENGTH = 23 // Android Log tag limit

        /**
         * Get a logger instance for the specified class/component name.
         * The tag will be formatted as "EtfMonitor.ClassName" (truncated if necessary).
         */
        fun getLogger(name: String): AppLogger {
            val fullTag = "$TAG_PREFIX.$name"
            val tag = if (fullTag.length > MAX_TAG_LENGTH) {
                fullTag.takeLast(MAX_TAG_LENGTH)
            } else {
                fullTag
            }
            return AppLogger(tag)
        }

        /**
         * Get a logger instance for the specified class.
         */
        inline fun <reified T> getLogger(): AppLogger {
            return getLogger(T::class.java.simpleName)
        }
    }

    /**
     * Log verbose message (only in debug builds).
     * Use for detailed tracing information.
     */
    fun v(message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
    }

    /**
     * Log verbose message with exception (only in debug builds).
     */
    fun v(message: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message, throwable)
        }
    }

    /**
     * Log debug message (only in debug builds).
     * Use for debugging information during development.
     */
    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    /**
     * Log debug message with exception (only in debug builds).
     */
    fun d(message: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message, throwable)
        }
    }

    /**
     * Log info message.
     * Use for significant events that are part of normal operation.
     */
    fun i(message: String) {
        Log.i(tag, message)
    }

    /**
     * Log info message with exception.
     */
    fun i(message: String, throwable: Throwable) {
        Log.i(tag, message, throwable)
    }

    /**
     * Log warning message.
     * Use for potentially harmful situations.
     */
    fun w(message: String) {
        Log.w(tag, message)
    }

    /**
     * Log warning message with exception.
     */
    fun w(message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
    }

    /**
     * Log error message.
     * Use for error events that might still allow the app to continue.
     */
    fun e(message: String) {
        Log.e(tag, message)
    }

    /**
     * Log error message with exception.
     * Always includes full stack trace in logcat.
     */
    fun e(message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }

    /**
     * Log a structured error with context information.
     * Useful for tracking errors with additional metadata.
     *
     * @param operation The operation that failed (e.g., "loadData", "saveSettings")
     * @param context Additional context map (e.g., mapOf("userId" to "123", "action" to "save"))
     * @param throwable The exception that occurred
     */
    fun logError(
        operation: String,
        context: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    ) {
        val contextStr = if (context.isNotEmpty()) {
            context.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else {
            "no context"
        }

        val message = "Operation '$operation' failed [$contextStr]"

        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    /**
     * Log method entry (only in debug builds).
     * Useful for tracing execution flow.
     */
    fun entering(methodName: String, vararg params: Any?) {
        if (BuildConfig.DEBUG) {
            val paramsStr = params.joinToString(", ") { it?.toString() ?: "null" }
            Log.v(tag, "--> $methodName($paramsStr)")
        }
    }

    /**
     * Log method exit (only in debug builds).
     */
    fun exiting(methodName: String, result: Any? = null) {
        if (BuildConfig.DEBUG) {
            val resultStr = result?.let { " = $it" } ?: ""
            Log.v(tag, "<-- $methodName$resultStr")
        }
    }

    /**
     * Log operation timing (only in debug builds).
     * Returns the elapsed time in milliseconds.
     */
    inline fun <T> timed(operationName: String, block: () -> T): T {
        if (!BuildConfig.DEBUG) {
            return block()
        }

        val startTime = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(tag, "$operationName completed in ${elapsed}ms")
        }
    }
}

/**
 * Extension function to safely log exceptions without using printStackTrace().
 * Use this instead of e.printStackTrace() for proper logging.
 */
fun Throwable.logError(logger: AppLogger, message: String = "Exception occurred") {
    logger.e(message, this)
}

/**
 * Constants for common logging scenarios.
 */
object LogConstants {
    // Operation tags
    const val OP_LOAD = "load"
    const val OP_SAVE = "save"
    const val OP_DELETE = "delete"
    const val OP_SYNC = "sync"
    const val OP_API_CALL = "api_call"
    const val OP_DATABASE = "database"
    const val OP_PYTHON = "python"

    // Context keys
    const val KEY_TICKER = "ticker"
    const val KEY_ETF_NAME = "etfName"
    const val KEY_DATE = "date"
    const val KEY_COUNT = "count"
    const val KEY_ERROR_CODE = "errorCode"
}
