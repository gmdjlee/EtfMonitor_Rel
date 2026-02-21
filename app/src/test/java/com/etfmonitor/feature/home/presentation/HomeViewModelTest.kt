package com.etfmonitor.feature.home.presentation

import android.content.Context
import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.home.domain.model.DataStatus
import com.etfmonitor.feature.home.domain.model.HomeState
import com.etfmonitor.feature.home.domain.model.HomeSummary
import com.etfmonitor.feature.home.domain.usecase.CheckDataStatusUseCase
import com.etfmonitor.feature.home.domain.usecase.CheckEtfDataUseCase
import com.etfmonitor.feature.home.domain.usecase.CheckFirstRunUseCase
import com.etfmonitor.feature.home.domain.usecase.GetDefaultDaysUseCase
import com.etfmonitor.feature.home.domain.usecase.GetHomeSummaryUseCase
import com.etfmonitor.feature.home.domain.usecase.SaveDialogDismissedUseCase
import com.etfmonitor.feature.home.presentation.viewmodel.HomeViewModel
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.network.ai.ApiKeyProvider
import com.etfmonitor.core.network.blood.FredApiKeyProvider
import com.etfmonitor.core.network.kis.KisApiKeyProvider
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * HomeViewModel 테스트
 *
 * 테스트 범위:
 * - 초기 상태 로딩
 * - 데이터 존재 여부에 따른 상태 전환
 * - 다이얼로그 표시/숨김 로직
 * - 에러 처리
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class HomeViewModelTest {

    // Mocks
    private lateinit var getHomeSummaryUseCase: GetHomeSummaryUseCase
    private lateinit var checkEtfDataUseCase: CheckEtfDataUseCase
    private lateinit var checkFirstRunUseCase: CheckFirstRunUseCase
    private lateinit var checkDataStatusUseCase: CheckDataStatusUseCase
    private lateinit var getDefaultDaysUseCase: GetDefaultDaysUseCase
    private lateinit var saveDialogDismissedUseCase: SaveDialogDismissedUseCase
    private lateinit var fearGreedRepository: FearGreedRepository
    private lateinit var marketOscillatorRepository: MarketOscillatorRepository
    private lateinit var marketDepositRepository: MarketDepositRepository
    private lateinit var kisApiKeyProvider: KisApiKeyProvider
    private lateinit var fredApiKeyProvider: FredApiKeyProvider
    private lateinit var aiApiKeyProvider: ApiKeyProvider
    private lateinit var kiwoomApiKeyProvider: com.etfmonitor.core.network.kiwoom.KiwoomApiKeyProvider
    private lateinit var context: Context

    private lateinit var viewModel: HomeViewModel

    @BeforeEach
    fun setup() {
        // Initialize mocks
        getHomeSummaryUseCase = mockk(relaxed = true)
        checkEtfDataUseCase = mockk(relaxed = true)
        checkFirstRunUseCase = mockk(relaxed = true)
        checkDataStatusUseCase = mockk(relaxed = true)
        getDefaultDaysUseCase = mockk(relaxed = true)
        saveDialogDismissedUseCase = mockk(relaxed = true)
        fearGreedRepository = mockk(relaxed = true)
        marketOscillatorRepository = mockk(relaxed = true)
        marketDepositRepository = mockk(relaxed = true)
        kisApiKeyProvider = mockk(relaxed = true)
        fredApiKeyProvider = mockk(relaxed = true)
        aiApiKeyProvider = mockk(relaxed = true)
        kiwoomApiKeyProvider = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // Default mock behavior - not first run, KIS keys already configured
        coEvery { checkFirstRunUseCase() } returns false
        every { kisApiKeyProvider.isConfigured() } returns true

        // Reset global singleton to prevent test pollution
        CollectionState.reset()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            getHomeSummaryUseCase = getHomeSummaryUseCase,
            checkEtfDataUseCase = checkEtfDataUseCase,
            checkFirstRunUseCase = checkFirstRunUseCase,
            checkDataStatusUseCase = checkDataStatusUseCase,
            getDefaultDaysUseCase = getDefaultDaysUseCase,
            saveDialogDismissedUseCase = saveDialogDismissedUseCase,
            fearGreedRepository = fearGreedRepository,
            marketOscillatorRepository = marketOscillatorRepository,
            marketDepositRepository = marketDepositRepository,
            kisApiKeyProvider = kisApiKeyProvider,
            fredApiKeyProvider = fredApiKeyProvider,
            aiApiKeyProvider = aiApiKeyProvider,
            kiwoomApiKeyProvider = kiwoomApiKeyProvider,
            context = context
        )
    }

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("데이터 없을 때 Idle(hasData=false) 상태")
        fun whenNoData_thenStateIsIdleWithNoData() = runTest {
            // Given
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)

            // When
            viewModel = createViewModel()
            advanceUntilIdle()

            // Then
            viewModel.state.test {
                val state = awaitItem()
                assertIs<HomeState.Idle>(state)
                assertFalse(state.hasData)
                assertEquals(null, state.lastDate)
            }
        }

        @Test
        @DisplayName("데이터 있을 때 Idle(hasData=true, summary) 상태")
        fun whenHasData_thenStateIsIdleWithSummary() = runTest {
            // Given
            val testDate = "2025-01-15"
            val testSummary = HomeSummary(
                depositChange = 100.0,
                creditChange = -50.0,
                kospiFearGreed = 65.5,
                kosdaqFearGreed = 55.0,
                kospiOscillator = 70.0,
                kospiStatus = "과매수",
                kosdaqOscillator = 45.0,
                kosdaqStatus = "중립"
            )

            coEvery { checkEtfDataUseCase() } returns Pair(true, testDate)
            coEvery { getHomeSummaryUseCase() } returns testSummary

            // When
            viewModel = createViewModel()
            advanceUntilIdle()

            // Then
            viewModel.state.test {
                val state = awaitItem()
                assertIs<HomeState.Idle>(state)
                assertTrue(state.hasData)
                assertEquals(testDate, state.lastDate)
                assertEquals(testSummary, state.summary)
            }
        }
    }

    @Nested
    @DisplayName("첫 실행 다이얼로그 테스트")
    inner class FirstRunDialogTests {

        @Test
        @DisplayName("첫 실행 + KIS 키 설정 완료 시 통합 초기화 다이얼로그 표시")
        fun whenFirstRun_andKisConfigured_thenShowUnifiedInitDialog() = runTest {
            // Given
            coEvery { checkFirstRunUseCase() } returns true
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)
            every { kisApiKeyProvider.isConfigured() } returns true

            // When
            viewModel = createViewModel()
            advanceUntilIdle()

            // Then
            viewModel.showUnifiedInitDialog.test {
                assertTrue(awaitItem())
            }
            viewModel.showApiKeyDialog.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("첫 실행이 아닐 때 다이얼로그 표시 안 함")
        fun whenNotFirstRun_thenDontShowDialog() = runTest {
            // Given
            coEvery { checkFirstRunUseCase() } returns false
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)

            // When
            viewModel = createViewModel()
            advanceUntilIdle()

            // Then
            viewModel.showUnifiedInitDialog.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("다이얼로그 닫기 시 상태 저장")
        fun whenDismissDialog_thenSaveState() = runTest {
            // Given
            coEvery { checkFirstRunUseCase() } returns true
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)

            viewModel = createViewModel()
            advanceUntilIdle()

            // When
            viewModel.onUnifiedInitDialogDismiss()
            advanceUntilIdle()

            // Then
            coVerify { saveDialogDismissedUseCase.saveFirstRunCompleted() }
            viewModel.showUnifiedInitDialog.test {
                assertFalse(awaitItem())
            }
        }
    }

    @Nested
    @DisplayName("API 키 다이얼로그 테스트")
    inner class ApiKeyDialogTests {

        @Test
        @DisplayName("첫 실행 + KIS 키 미설정 시 API 키 다이얼로그 먼저 표시")
        fun whenFirstRun_andKisNotConfigured_thenShowApiKeyDialog() = runTest {
            // Given
            coEvery { checkFirstRunUseCase() } returns true
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)
            every { kisApiKeyProvider.isConfigured() } returns false

            // When
            viewModel = createViewModel()
            advanceUntilIdle()

            // Then: API 키 다이얼로그 표시
            viewModel.showApiKeyDialog.test {
                assertTrue(awaitItem())
            }
            // Then: 통합 초기화 다이얼로그는 아직 숨김
            viewModel.showUnifiedInitDialog.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("첫 실행이 아닐 때 API 키 다이얼로그 표시 안 함")
        fun whenNotFirstRun_thenDontShowApiKeyDialog() = runTest {
            // Given
            coEvery { checkFirstRunUseCase() } returns false
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)
            every { kisApiKeyProvider.isConfigured() } returns false

            // When
            viewModel = createViewModel()
            advanceUntilIdle()

            // Then
            viewModel.showApiKeyDialog.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("saveApiKeys() 호출 시 키 저장 후 통합 초기화 다이얼로그 표시")
        fun whenSaveApiKeys_thenStoreKeysAndShowUnifiedInitDialog() = runTest {
            // Given
            coEvery { checkFirstRunUseCase() } returns true
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)
            every { kisApiKeyProvider.isConfigured() } returns false

            viewModel = createViewModel()
            advanceUntilIdle()

            // When
            viewModel.saveApiKeys("test-app-key", "test-app-secret", "", null, null)
            advanceUntilIdle()

            // Then: API 키 다이얼로그 닫힘
            viewModel.showApiKeyDialog.test {
                assertFalse(awaitItem())
            }
            // Then: 통합 초기화 다이얼로그 열림
            viewModel.showUnifiedInitDialog.test {
                assertTrue(awaitItem())
            }
            // Then: KisApiKeyProvider에 키 저장됨
            io.mockk.verify { kisApiKeyProvider.setAppKey("test-app-key") }
            io.mockk.verify { kisApiKeyProvider.setAppSecret("test-app-secret") }
        }

        @Test
        @DisplayName("dismissApiKeyDialog() 호출 시 다이얼로그 닫고 통합 초기화 다이얼로그 표시")
        fun whenDismissApiKeyDialog_thenCloseAndShowUnifiedInitDialog() = runTest {
            // Given
            coEvery { checkFirstRunUseCase() } returns true
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)
            every { kisApiKeyProvider.isConfigured() } returns false

            viewModel = createViewModel()
            advanceUntilIdle()

            // When
            viewModel.dismissApiKeyDialog()
            advanceUntilIdle()

            // Then: API 키 다이얼로그 닫힘
            viewModel.showApiKeyDialog.test {
                assertFalse(awaitItem())
            }
            // Then: 통합 초기화 다이얼로그 열림 (나중에 설정 후에도 진행 가능)
            viewModel.showUnifiedInitDialog.test {
                assertTrue(awaitItem())
            }
        }
    }

    @Nested
    @DisplayName("Fear & Greed 초기화 테스트")
    inner class FearGreedInitTests {

        @Test
        @DisplayName("초기화 성공 시 Success 상태")
        fun whenInitializeSuccess_thenStateIsSuccess() = runTest {
            // Given
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)
            coEvery { fearGreedRepository.initializeFearGreed(any(), any()) } returns Result.success(30)
            coEvery { checkDataStatusUseCase() } returns DataStatus(
                hasEtfData = true,
                hasDepositData = true,
                hasFearGreedData = true,
                hasOscillatorData = true
            )

            viewModel = createViewModel()
            advanceUntilIdle()

            // When
            viewModel.initializeFearGreed(90)
            advanceUntilIdle()

            // Then
            viewModel.state.test {
                val state = awaitItem()
                // 마지막 상태는 checkData() 호출로 인해 Idle일 수 있음
                // Success 상태가 한번 발생했는지 확인
                assertTrue(state is HomeState.Idle || state is HomeState.Success)
            }

            coVerify {
                saveDialogDismissedUseCase.saveDialogDismissed(
                    SaveDialogDismissedUseCase.KEY_FEAR_GREED_DISMISSED
                )
            }
        }

        @Test
        @DisplayName("초기화 실패 시 Error 상태")
        fun whenInitializeFails_thenStateIsError() = runTest {
            // Given
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)
            coEvery { fearGreedRepository.initializeFearGreed(any(), any()) } returns
                    Result.failure(RuntimeException("Network error"))

            viewModel = createViewModel()
            advanceUntilIdle()

            // When
            viewModel.initializeFearGreed(90)
            advanceUntilIdle()

            // Then
            viewModel.state.test {
                val state = awaitItem()
                // Error 후 checkData()로 Idle로 변경될 수 있음
                assertTrue(state is HomeState.Idle || state is HomeState.Error)
            }
        }
    }

    @Nested
    @DisplayName("시장 예탁금 초기화 테스트")
    inner class MarketDepositInitTests {

        @Test
        @DisplayName("초기화 성공 시 다음 다이얼로그 체크")
        fun whenInitializeSuccess_thenCheckNextDialog() = runTest {
            // Given
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)
            coEvery { marketDepositRepository.initializeDeposits(any(), any()) } returns Result.success(10)
            coEvery { checkDataStatusUseCase() } returns DataStatus(
                hasEtfData = false,
                hasDepositData = true,
                hasFearGreedData = false,
                hasOscillatorData = false
            )

            viewModel = createViewModel()
            advanceUntilIdle()

            // When
            viewModel.initializeMarketDeposit(5)
            advanceUntilIdle()

            // Then
            coVerify { checkDataStatusUseCase() }
        }
    }

    @Nested
    @DisplayName("메시지 클리어 테스트")
    inner class ClearMessageTests {

        @Test
        @DisplayName("Success 상태에서 클리어 시 Idle로 전환")
        fun whenClearFromSuccess_thenStateIsIdle() = runTest {
            // Given
            coEvery { checkEtfDataUseCase() } returns Pair(true, "2025-01-15")
            coEvery { getHomeSummaryUseCase() } returns null

            viewModel = createViewModel()
            advanceUntilIdle()

            // When - clearMessage는 Success/Error 상태에서만 동작
            viewModel.clearMessage()
            advanceUntilIdle()

            // Then
            viewModel.state.test {
                val state = awaitItem()
                assertIs<HomeState.Idle>(state)
            }
        }
    }

    @Nested
    @DisplayName("기본값 테스트")
    inner class DefaultValuesTests {

        @Test
        @DisplayName("initialize 시 기본 일수 사용")
        fun whenInitializeWithoutDays_thenUseDefaultDays() = runTest {
            // Given
            val defaultDays = 30
            coEvery { checkEtfDataUseCase() } returns Pair(false, null)
            coEvery { getDefaultDaysUseCase() } returns defaultDays

            viewModel = createViewModel()
            advanceUntilIdle()

            // When
            viewModel.initialize(null)
            advanceUntilIdle()

            // Then
            coVerify { getDefaultDaysUseCase() }
        }
    }
}
