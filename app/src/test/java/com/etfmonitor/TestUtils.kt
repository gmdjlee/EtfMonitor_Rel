package com.etfmonitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.time.Duration.Companion.seconds

/**
 * JUnit5 extension that sets up the main dispatcher for coroutine testing.
 * Use with @ExtendWith(MainDispatcherExtension::class)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext?) {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext?) {
        Dispatchers.resetMain()
    }
}

/**
 * Test utilities for common testing operations
 */
object TestUtils {

    /**
     * Collects the first emission from a Flow within a timeout.
     * Useful for testing Repository flows.
     */
    suspend fun <T> Flow<T>.firstWithTimeout(timeoutSeconds: Long = 5): T {
        return withTimeout(timeoutSeconds.seconds) {
            first()
        }
    }

    /**
     * Creates a test date string in YYYY-MM-DD format
     */
    fun createTestDate(year: Int = 2025, month: Int = 1, day: Int = 15): String {
        return String.format("%04d-%02d-%02d", year, month, day)
    }

    /**
     * Creates a list of consecutive test dates
     */
    fun createTestDateRange(startDay: Int, count: Int, year: Int = 2025, month: Int = 1): List<String> {
        return (0 until count).map { offset ->
            createTestDate(year, month, startDay + offset)
        }
    }
}

/**
 * Base class for ViewModel tests with coroutine support
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ViewModelTest {
    protected val testDispatcher = StandardTestDispatcher()
    protected val testScope = TestScope(testDispatcher)

    protected fun runViewModelTest(block: suspend TestScope.() -> Unit) = testScope.runTest {
        block()
    }
}

/**
 * Base class for Repository tests with coroutine support
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class RepositoryTest {
    protected val testDispatcher = StandardTestDispatcher()
    protected val testScope = TestScope(testDispatcher)

    protected fun runRepoTest(block: suspend TestScope.() -> Unit) = testScope.runTest {
        block()
    }
}
