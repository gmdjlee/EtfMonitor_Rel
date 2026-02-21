package com.etfmonitor.feature.home.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.home.domain.repository.HomeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * SaveDialogDismissedUseCase 단위 테스트
 *
 * 테스트 범위:
 * - saveAllDialogsDismissed() 모든 키 저장 검증
 * - saveDialogDismissed() 특정 키 저장 검증
 * - saveFirstRunCompleted() KEY_IS_FIRST_RUN "false" 저장 검증
 * - 파라미터 정확히 전달 검증
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("SaveDialogDismissedUseCase 테스트")
class SaveDialogDismissedUseCaseTest {

    private val repository: HomeRepository = mockk()
    private lateinit var useCase: SaveDialogDismissedUseCase

    @BeforeEach
    fun setUp() {
        useCase = SaveDialogDismissedUseCase(repository)
        coEvery { repository.saveSetting(any(), any()) } just runs
    }

    // ================================================================
    // saveAllDialogsDismissed() 테스트
    // ================================================================

    @Nested
    @DisplayName("saveAllDialogsDismissed() 테스트")
    inner class SaveAllDialogsDismissedTests {

        @Test
        @DisplayName("saveAllDialogsDismissed_savesIsFirstRunFalse")
        fun `saveAllDialogsDismissed_savesIsFirstRunFalse`() = runTest {
            // When
            useCase.saveAllDialogsDismissed()

            // Then
            coVerify { repository.saveSetting(SaveDialogDismissedUseCase.KEY_IS_FIRST_RUN, "false") }
        }

        @Test
        @DisplayName("saveAllDialogsDismissed_savesMarketDepositDismissedTrue")
        fun `saveAllDialogsDismissed_savesMarketDepositDismissedTrue`() = runTest {
            // When
            useCase.saveAllDialogsDismissed()

            // Then
            coVerify { repository.saveSetting(SaveDialogDismissedUseCase.KEY_MARKET_DEPOSIT_DISMISSED, "true") }
        }

        @Test
        @DisplayName("saveAllDialogsDismissed_savesFearGreedDismissedTrue")
        fun `saveAllDialogsDismissed_savesFearGreedDismissedTrue`() = runTest {
            // When
            useCase.saveAllDialogsDismissed()

            // Then
            coVerify { repository.saveSetting(SaveDialogDismissedUseCase.KEY_FEAR_GREED_DISMISSED, "true") }
        }

        @Test
        @DisplayName("saveAllDialogsDismissed_savesAllFiveSettings")
        fun `saveAllDialogsDismissed_savesAllFiveSettings`() = runTest {
            // When
            useCase.saveAllDialogsDismissed()

            // Then — 총 5개 설정 저장
            coVerify(exactly = 5) { repository.saveSetting(any(), any()) }
            coVerify { repository.saveSetting(SaveDialogDismissedUseCase.KEY_IS_FIRST_RUN, "false") }
            coVerify { repository.saveSetting(SaveDialogDismissedUseCase.KEY_MARKET_DEPOSIT_DISMISSED, "true") }
            coVerify { repository.saveSetting(SaveDialogDismissedUseCase.KEY_FEAR_GREED_DISMISSED, "true") }
            coVerify { repository.saveSetting(SaveDialogDismissedUseCase.KEY_MARKET_OSCILLATOR_DISMISSED, "true") }
            coVerify { repository.saveSetting(SaveDialogDismissedUseCase.KEY_MARKET_INDEX_DISMISSED, "true") }
        }
    }

    // ================================================================
    // saveDialogDismissed() 테스트
    // ================================================================

    @Nested
    @DisplayName("saveDialogDismissed() 테스트")
    inner class SaveDialogDismissedTests {

        @Test
        @DisplayName("saveDialogDismissed_withFearGreedKey_savesKeyWithTrue")
        fun `saveDialogDismissed_withFearGreedKey_savesKeyWithTrue`() = runTest {
            // Given
            val key = SaveDialogDismissedUseCase.KEY_FEAR_GREED_DISMISSED

            // When
            useCase.saveDialogDismissed(key)

            // Then
            coVerify(exactly = 1) { repository.saveSetting(key, "true") }
        }

        @Test
        @DisplayName("saveDialogDismissed_withOscillatorKey_savesKeyWithTrue")
        fun `saveDialogDismissed_withOscillatorKey_savesKeyWithTrue`() = runTest {
            // Given
            val key = SaveDialogDismissedUseCase.KEY_MARKET_OSCILLATOR_DISMISSED

            // When
            useCase.saveDialogDismissed(key)

            // Then
            coVerify(exactly = 1) { repository.saveSetting(key, "true") }
        }

        @Test
        @DisplayName("saveDialogDismissed_withCustomKey_savesExactKey")
        fun `saveDialogDismissed_withCustomKey_savesExactKey`() = runTest {
            // Given
            val customKey = "custom_dialog_key"

            // When
            useCase.saveDialogDismissed(customKey)

            // Then
            coVerify(exactly = 1) { repository.saveSetting(customKey, "true") }
        }
    }

    // ================================================================
    // saveFirstRunCompleted() 테스트
    // ================================================================

    @Nested
    @DisplayName("saveFirstRunCompleted() 테스트")
    inner class SaveFirstRunCompletedTests {

        @Test
        @DisplayName("saveFirstRunCompleted_savesIsFirstRunFalse")
        fun `saveFirstRunCompleted_savesIsFirstRunFalse`() = runTest {
            // When
            useCase.saveFirstRunCompleted()

            // Then
            coVerify(exactly = 1) { repository.saveSetting(SaveDialogDismissedUseCase.KEY_IS_FIRST_RUN, "false") }
        }

        @Test
        @DisplayName("saveFirstRunCompleted_onlySavesOneKey")
        fun `saveFirstRunCompleted_onlySavesOneKey`() = runTest {
            // When
            useCase.saveFirstRunCompleted()

            // Then
            coVerify(exactly = 1) { repository.saveSetting(any(), any()) }
        }
    }

    // ================================================================
    // 실패 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("실패 경로 테스트")
    inner class FailurePathTests {

        @Test
        @DisplayName("saveDialogDismissed_whenRepositoryThrows_propagatesException")
        fun `saveDialogDismissed_whenRepositoryThrows_propagatesException`() = runTest {
            // Given
            coEvery { repository.saveSetting(any(), any()) } throws RuntimeException("저장 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase.saveDialogDismissed("some_key")
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("저장 실패", caught?.message)
        }
    }
}
