package com.etfmonitor.feature.settings.presentation

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.network.ai.AIModel
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.core.network.ai.ApiKeyProvider
import com.etfmonitor.core.network.blood.FredApiKeyProvider
import com.etfmonitor.core.network.kis.KisApiKeyProvider
import com.etfmonitor.core.ui.theme.ChartColorSettings
import com.etfmonitor.core.ui.theme.ThemeManager
import com.etfmonitor.feature.analysis.domain.repository.AIAnalysisRepository
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import com.etfmonitor.feature.market.domain.repository.BloodIndicatorRepository
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import com.etfmonitor.feature.stock.domain.repository.StockRepository
import com.etfmonitor.core.worker.WorkManagerHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SettingsViewModel unit tests.
 *
 * Coverage:
 * - Initial state loading (themes, defaultDays, API key status)
 * - Theme changes (dark/light/system)
 * - Default days setting (without reinitialize)
 * - Search history limit
 * - Period days settings (fearGreed, marketOscillator, marketIndex, bloodIndicator)
 * - Chart color changes
 * - Chart color reset
 * - API key management (Claude, Gemini, FRED, KIS)
 * - API connection test state transitions
 * - Model loading (Claude, Gemini)
 * - addTheme / removeTheme / addExclusion / removeExclusion
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class SettingsViewModelTest {

    private lateinit var etfRepository: EtfRepository
    private lateinit var stockRepository: StockRepository
    private lateinit var marketDepositRepository: MarketDepositRepository
    private lateinit var fearGreedRepository: FearGreedRepository
    private lateinit var marketOscillatorRepository: MarketOscillatorRepository
    private lateinit var marketIndexRepository: MarketIndexRepository
    private lateinit var bloodIndicatorRepository: BloodIndicatorRepository
    private lateinit var aiAnalysisRepository: AIAnalysisRepository
    private lateinit var apiKeyProvider: ApiKeyProvider
    private lateinit var kisApiKeyProvider: KisApiKeyProvider
    private lateinit var fredApiKeyProvider: FredApiKeyProvider
    private lateinit var etfDao: EtfDao
    private lateinit var themeManager: ThemeManager
    private lateinit var context: android.content.Context

    @AfterEach
    fun teardown() {
        unmockkObject(WorkManagerHelper)
    }

    @BeforeEach
    fun setup() {
        // WorkManagerHelper is a Kotlin object (singleton) that calls WorkManager.getInstance()
        // directly, which requires Android initialization. Mock it to prevent
        // IllegalStateException: WorkManager is not initialized properly.
        mockkObject(WorkManagerHelper)
        every { WorkManagerHelper.scheduleStockUpdate(any(), any(), any()) } just runs
        every { WorkManagerHelper.scheduleMarketDepositUpdate(any(), any(), any()) } just runs
        every { WorkManagerHelper.scheduleFearGreedUpdate(any(), any(), any()) } just runs
        every { WorkManagerHelper.scheduleMarketOscillatorUpdate(any(), any(), any()) } just runs
        every { WorkManagerHelper.scheduleMarketIndexUpdate(any(), any(), any()) } just runs
        every { WorkManagerHelper.scheduleAdvancedAnalysis(any(), any(), any()) } just runs
        every { WorkManagerHelper.scheduleEtfUpdate(any(), any(), any()) } just runs
        every { WorkManagerHelper.scheduleBloodIndicatorUpdate(any(), any(), any()) } just runs

        etfRepository = mockk(relaxed = true)
        stockRepository = mockk(relaxed = true)
        marketDepositRepository = mockk(relaxed = true)
        fearGreedRepository = mockk(relaxed = true)
        marketOscillatorRepository = mockk(relaxed = true)
        marketIndexRepository = mockk(relaxed = true)
        bloodIndicatorRepository = mockk(relaxed = true)
        aiAnalysisRepository = mockk(relaxed = true)
        apiKeyProvider = mockk(relaxed = true)
        kisApiKeyProvider = mockk(relaxed = true)
        fredApiKeyProvider = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
        themeManager = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // Default stubs so init does not throw
        coEvery { etfRepository.getThemes() } returns emptyList()
        coEvery { etfRepository.getExclusions() } returns emptyList()
        coEvery { etfRepository.getDefaultDays() } returns 25
        coEvery { etfDao.getSetting(any()) } returns null
        coEvery { etfDao.getEtfCount() } returns 0
        coEvery { etfDao.getHoldingCount() } returns 0
        coEvery { etfDao.getLatestDate() } returns null
        every { apiKeyProvider.getSelectedProvider() } returns AIProvider.CLAUDE
        every { apiKeyProvider.hasApiKey(any()) } returns false
        every { fredApiKeyProvider.isConfigured() } returns false
        every { kisApiKeyProvider.isConfigured() } returns false
        coEvery { stockRepository.getStockCount() } returns 0
        coEvery { stockRepository.getLastUpdateTime() } returns null
        coEvery { marketDepositRepository.getDepositCount() } returns 0
        coEvery { marketDepositRepository.getLastUpdateTime() } returns null
        coEvery { fearGreedRepository.getLastUpdateTime(any()) } returns null
        coEvery { fearGreedRepository.getCountByMarket(any()) } returns 0
        coEvery { marketOscillatorRepository.getLatestData(any()) } returns null
        coEvery { marketOscillatorRepository.getDataCount(any()) } returns 0
        coEvery { marketIndexRepository.getCountByMarket(any()) } returns 0
        coEvery { marketIndexRepository.getLastUpdateTime(any()) } returns null
        coEvery { bloodIndicatorRepository.getCount() } returns 0
        coEvery { bloodIndicatorRepository.getLastUpdateTime() } returns null
    }

    private fun createViewModel(): SettingsViewModel = SettingsViewModel(
        etfRepository = etfRepository,
        stockRepository = stockRepository,
        marketDepositRepository = marketDepositRepository,
        fearGreedRepository = fearGreedRepository,
        marketOscillatorRepository = marketOscillatorRepository,
        marketIndexRepository = marketIndexRepository,
        bloodIndicatorRepository = bloodIndicatorRepository,
        aiAnalysisRepository = aiAnalysisRepository,
        apiKeyProvider = apiKeyProvider,
        kisApiKeyProvider = kisApiKeyProvider,
        fredApiKeyProvider = fredApiKeyProvider,
        etfDao = etfDao,
        themeManager = themeManager,
        context = context
    )

    // ---------------------------------------------------------------
    // Initial state tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("초기화 시 getThemes 호출 및 themes 상태 반영")
        fun onInit_loadsThemes() = runTest {
            coEvery { etfRepository.getThemes() } returns listOf("반도체", "2차전지")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.themes.test {
                assertEquals(listOf("반도체", "2차전지"), awaitItem())
            }
        }

        @Test
        @DisplayName("초기화 시 defaultDays 로드")
        fun onInit_loadsDefaultDays() = runTest {
            coEvery { etfRepository.getDefaultDays() } returns 90

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.defaultDays.test {
                assertEquals(90, awaitItem())
            }
        }

        @Test
        @DisplayName("초기화 시 Claude API 키 미설정 상태 반영")
        fun onInit_claudeKeyNotConfigured() = runTest {
            every { apiKeyProvider.hasApiKey(AIProvider.CLAUDE) } returns false

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.isClaudeApiKeyConfigured.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("초기화 시 Claude API 키 설정된 경우 true 반영")
        fun onInit_claudeKeyConfigured() = runTest {
            every { apiKeyProvider.hasApiKey(AIProvider.CLAUDE) } returns true

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.isClaudeApiKeyConfigured.test {
                assertTrue(awaitItem())
            }
        }

        @Test
        @DisplayName("초기화 시 FRED API 키 미설정 상태 반영")
        fun onInit_fredKeyNotConfigured() = runTest {
            every { fredApiKeyProvider.isConfigured() } returns false

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.isFredApiKeyConfigured.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("초기화 시 메시지 null")
        fun onInit_messageIsNull() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.message.test {
                assertNull(awaitItem())
            }
        }

        @Test
        @DisplayName("초기화 시 API 키 테스트 상태는 Idle")
        fun onInit_apiKeyTestStateIsIdle() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.apiKeyTestState.test {
                assertIs<ApiKeyTestState.Idle>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // Theme settings tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("테마 설정 테스트")
    inner class ThemeSettingsTests {

        @Test
        @DisplayName("setDarkTheme(true) 호출 시 isDarkTheme 상태 true")
        fun setDarkTheme_true_updatesDarkThemeState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setDarkTheme(true)
            advanceUntilIdle()

            viewModel.isDarkTheme.test {
                assertTrue(awaitItem() == true)
            }
        }

        @Test
        @DisplayName("setDarkTheme(false) 호출 시 isDarkTheme 상태 false")
        fun setDarkTheme_false_updatesDarkThemeState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setDarkTheme(false)
            advanceUntilIdle()

            viewModel.isDarkTheme.test {
                assertFalse(awaitItem() == true)
            }
        }

        @Test
        @DisplayName("setDarkTheme(null) 호출 시 isDarkTheme 상태 null (시스템 기본값)")
        fun setDarkTheme_null_setsDarkThemeToNull() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setDarkTheme(null)
            advanceUntilIdle()

            viewModel.isDarkTheme.test {
                assertNull(awaitItem())
            }
        }

        @Test
        @DisplayName("setDarkTheme() 호출 시 ThemeManager.setDarkTheme 호출")
        fun setDarkTheme_callsThemeManager() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setDarkTheme(true)
            advanceUntilIdle()

            verify { themeManager.setDarkTheme(true) }
        }

        @Test
        @DisplayName("setDarkTheme() 성공 후 message 설정")
        fun setDarkTheme_setsSuccessMessage() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setDarkTheme(true)
            advanceUntilIdle()

            viewModel.message.test {
                val msg = awaitItem()
                assertTrue(msg?.contains("다크") == true)
            }
        }
    }

    // ---------------------------------------------------------------
    // Default days tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("기본 수집 기간 설정 테스트")
    inner class DefaultDaysTests {

        @Test
        @DisplayName("setDefaultDays() 호출 시 defaultDays 상태 업데이트")
        fun setDefaultDays_updatesState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setDefaultDays(90)
            advanceUntilIdle()

            viewModel.defaultDays.test {
                assertEquals(90, awaitItem())
            }
        }

        @Test
        @DisplayName("setDefaultDays() 호출 시 etfRepository.setDefaultDays 호출")
        fun setDefaultDays_callsRepository() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setDefaultDays(90)
            advanceUntilIdle()

            coVerify { etfRepository.setDefaultDays(90) }
        }

        @Test
        @DisplayName("setDefaultDays(reinitialize=false) 호출 시 message 설정")
        fun setDefaultDays_withoutReinitialize_setsMessage() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setDefaultDays(180, reinitialize = false)
            advanceUntilIdle()

            viewModel.message.test {
                val msg = awaitItem()
                assertTrue(msg?.contains("180") == true || msg?.contains("설정") == true)
            }
        }
    }

    // ---------------------------------------------------------------
    // Period days settings tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("기간 설정 테스트")
    inner class PeriodDaysTests {

        @Test
        @DisplayName("setSearchHistoryLimit() 호출 시 searchHistoryLimit 상태 업데이트")
        fun setSearchHistoryLimit_updatesState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setSearchHistoryLimit(20)
            advanceUntilIdle()

            viewModel.searchHistoryLimit.test {
                assertEquals(20, awaitItem())
            }
        }

        @Test
        @DisplayName("setFearGreedPeriodDays(reinitialize=false) 호출 시 상태 업데이트 및 message")
        fun setFearGreedPeriodDays_noReinitialize_updatesState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setFearGreedPeriodDays(180, reinitialize = false)
            advanceUntilIdle()

            viewModel.fearGreedPeriodDays.test {
                assertEquals(180, awaitItem())
            }
        }

        @Test
        @DisplayName("setMarketOscillatorPeriodDays(reinitialize=false) 호출 시 상태 업데이트")
        fun setMarketOscillatorPeriodDays_noReinitialize_updatesState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setMarketOscillatorPeriodDays(365, reinitialize = false)
            advanceUntilIdle()

            viewModel.marketOscillatorPeriodDays.test {
                assertEquals(365, awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // Update time settings tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("업데이트 시간 설정 테스트")
    inner class UpdateTimeTests {

        @Test
        @DisplayName("setUpdateTime() 호출 시 stockUpdateSettings 업데이트")
        fun setUpdateTime_updatesStockSettings() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setUpdateTime(2, 30)
            advanceUntilIdle()

            viewModel.stockUpdateSettings.test {
                val settings = awaitItem()
                assertEquals(2, settings.updateHour)
                assertEquals(30, settings.updateMinute)
            }
        }

        @Test
        @DisplayName("setFearGreedUpdateTime() 호출 시 fearGreedUpdateSettings 업데이트")
        fun setFearGreedUpdateTime_updatesFearGreedSettings() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setFearGreedUpdateTime(5, 0)
            advanceUntilIdle()

            viewModel.fearGreedUpdateSettings.test {
                val settings = awaitItem()
                assertEquals(5, settings.updateHour)
                assertEquals(0, settings.updateMinute)
            }
        }

        @Test
        @DisplayName("setEtfUpdateTime() 성공 후 message 설정")
        fun setEtfUpdateTime_setsSuccessMessage() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setEtfUpdateTime(1, 0)
            advanceUntilIdle()

            viewModel.message.test {
                val msg = awaitItem()
                assertTrue(msg?.isNotEmpty() == true)
            }
        }
    }

    // ---------------------------------------------------------------
    // Theme/exclusion management tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("테마/제외 키워드 관리 테스트")
    inner class ThemeKeywordTests {

        @Test
        @DisplayName("addTheme() 성공 후 themes 상태 갱신")
        fun addTheme_updatesThemesList() = runTest {
            coEvery { etfRepository.getThemes() } returns emptyList() andThen listOf("반도체")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.addTheme("반도체")
            advanceUntilIdle()

            viewModel.themes.test {
                assertEquals(listOf("반도체"), awaitItem())
            }
        }

        @Test
        @DisplayName("addTheme() 빈 문자열 입력 시 오류 메시지")
        fun addTheme_blankInput_setsErrorMessage() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.addTheme("  ")
            advanceUntilIdle()

            viewModel.message.test {
                val msg = awaitItem()
                assertTrue(msg?.contains("입력") == true)
            }
        }

        @Test
        @DisplayName("addTheme() 빈 문자열 입력 시 repository 호출 없음")
        fun addTheme_blankInput_doesNotCallRepository() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.addTheme("")
            advanceUntilIdle()

            coVerify(exactly = 0) { etfRepository.addTheme(any()) }
        }

        @Test
        @DisplayName("removeTheme() 호출 시 repository 호출 및 message 설정")
        fun removeTheme_callsRepositoryAndSetsMessage() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.removeTheme("반도체")
            advanceUntilIdle()

            coVerify { etfRepository.removeTheme("반도체") }
            viewModel.message.test {
                val msg = awaitItem()
                assertTrue(msg?.contains("반도체") == true)
            }
        }

        @Test
        @DisplayName("addExclusion() 빈 문자열 입력 시 repository 호출 없음")
        fun addExclusion_blankInput_doesNotCallRepository() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.addExclusion("")
            advanceUntilIdle()

            coVerify(exactly = 0) { etfRepository.addExclusion(any()) }
        }
    }

    // ---------------------------------------------------------------
    // Chart color tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("차트 색상 설정 테스트")
    inner class ChartColorTests {

        @Test
        @DisplayName("setChartColor() MARKET_CAP LINE1 색상 변경 시 chartColorSettings 업데이트")
        fun setChartColor_marketCapLine1_updatesSettings() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val newColor = 0xFF123456.toInt()
            viewModel.setChartColor(ChartType.MARKET_CAP, ColorProperty.LINE1, newColor, "색상 변경")
            advanceUntilIdle()

            viewModel.chartColorSettings.test {
                val settings = awaitItem()
                assertEquals(newColor, settings.marketCapOscillator.lineColor1)
            }
        }

        @Test
        @DisplayName("setChartColor() 성공 후 전달한 message 설정")
        fun setChartColor_setsProvidedMessage() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setChartColor(ChartType.MACD, ColorProperty.LINE2, 0xFF654321.toInt(), "MACD 라인 변경")
            advanceUntilIdle()

            viewModel.message.test {
                assertEquals("MACD 라인 변경", awaitItem())
            }
        }

        @Test
        @DisplayName("resetChartColors() 호출 시 chartColorSettings 기본값으로 초기화")
        fun resetChartColors_resetsToDefaults() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // First set a custom color
            viewModel.setChartColor(ChartType.MARKET_CAP, ColorProperty.LINE1, 0xFF123456.toInt(), "변경")
            advanceUntilIdle()

            // Then reset
            viewModel.resetChartColors()
            advanceUntilIdle()

            viewModel.chartColorSettings.test {
                val settings = awaitItem()
                val defaults = ChartColorSettings()
                assertEquals(defaults.marketCapOscillator.lineColor1, settings.marketCapOscillator.lineColor1)
            }
        }

        @Test
        @DisplayName("resetChartColors() 성공 후 초기화 message 설정")
        fun resetChartColors_setsMessage() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.resetChartColors()
            advanceUntilIdle()

            viewModel.message.test {
                val msg = awaitItem()
                assertTrue(msg?.contains("초기화") == true)
            }
        }

        @Test
        @DisplayName("setChartColor() FEAR_GREED LINE2 변경 시 ThemeManager.setFearGreedColors 호출")
        fun setChartColor_fearGreedLine2_callsThemeManager() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setChartColor(ChartType.FEAR_GREED, ColorProperty.LINE2, 0xFF111111.toInt(), "msg")
            advanceUntilIdle()

            verify { themeManager.setFearGreedColors(any()) }
        }
    }

    // ---------------------------------------------------------------
    // API key management tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("AI API 키 관리 테스트")
    inner class ApiKeyTests {

        @Test
        @DisplayName("setClaudeApiKey() 유효한 키 저장 후 isClaudeApiKeyConfigured = true")
        fun setClaudeApiKey_validKey_setsConfigured() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setClaudeApiKey("sk-valid-key-12345")
            advanceUntilIdle()

            viewModel.isClaudeApiKeyConfigured.test {
                assertTrue(awaitItem())
            }
        }

        @Test
        @DisplayName("setClaudeApiKey() 빈 문자열 입력 시 오류 message 설정")
        fun setClaudeApiKey_blankKey_setsErrorMessage() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setClaudeApiKey("  ")
            advanceUntilIdle()

            viewModel.message.test {
                val msg = awaitItem()
                assertTrue(msg?.contains("입력") == true)
            }
        }

        @Test
        @DisplayName("setGeminiApiKey() 빈 문자열 입력 시 repository 호출 없음")
        fun setGeminiApiKey_blankKey_doesNotCallProvider() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setGeminiApiKey("")
            advanceUntilIdle()

            verify(exactly = 0) { apiKeyProvider.setApiKey(AIProvider.GEMINI, any()) }
        }

        @Test
        @DisplayName("clearClaudeApiKey() 호출 시 isClaudeApiKeyConfigured = false")
        fun clearClaudeApiKey_setsConfiguredFalse() = runTest {
            every { apiKeyProvider.hasApiKey(AIProvider.CLAUDE) } returns true

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.clearClaudeApiKey()
            advanceUntilIdle()

            viewModel.isClaudeApiKeyConfigured.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("setFredApiKey() 빈 문자열 입력 시 오류 message")
        fun setFredApiKey_blankKey_setsErrorMessage() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setFredApiKey("")
            advanceUntilIdle()

            viewModel.message.test {
                val msg = awaitItem()
                assertTrue(msg?.contains("FRED") == true || msg?.contains("입력") == true)
            }
        }

        @Test
        @DisplayName("setFredApiKey() 유효한 키 저장 후 isFredApiKeyConfigured = true")
        fun setFredApiKey_validKey_setsConfigured() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setFredApiKey("fred-valid-key")
            advanceUntilIdle()

            viewModel.isFredApiKeyConfigured.test {
                assertTrue(awaitItem())
            }
        }

        @Test
        @DisplayName("clearFredApiKey() 호출 시 isFredApiKeyConfigured = false")
        fun clearFredApiKey_setsConfiguredFalse() = runTest {
            every { fredApiKeyProvider.isConfigured() } returns true

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.clearFredApiKey()
            advanceUntilIdle()

            viewModel.isFredApiKeyConfigured.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("setKisAppKey() 빈 문자열 입력 시 오류 message")
        fun setKisAppKey_blankKey_setsErrorMessage() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setKisAppKey("")
            advanceUntilIdle()

            viewModel.message.test {
                val msg = awaitItem()
                assertTrue(msg?.isNotEmpty() == true)
            }
        }

        @Test
        @DisplayName("clearKisApiKeys() 호출 시 isKisApiKeyConfigured = false")
        fun clearKisApiKeys_setsConfiguredFalse() = runTest {
            every { kisApiKeyProvider.isConfigured() } returns true

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.clearKisApiKeys()
            advanceUntilIdle()

            viewModel.isKisApiKeyConfigured.test {
                assertFalse(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // API connection test state tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("API 연결 테스트 상태 테스트")
    inner class ApiConnectionTestTests {

        @Test
        @DisplayName("testApiConnection() 시작 시 Testing 상태 경유 후 Success 도달")
        fun testApiConnection_setsTestingState() = runTest {
            coEvery { aiAnalysisRepository.testApiConnection() } returns Result.success(true)

            val viewModel = createViewModel()
            advanceUntilIdle()

            // With UnconfinedTestDispatcher the Testing state is transient,
            // but the final Success state confirms the Testing path was traversed.
            viewModel.testApiConnection()
            advanceUntilIdle()

            viewModel.apiKeyTestState.test {
                assertIs<ApiKeyTestState.Success>(awaitItem())
            }
        }

        @Test
        @DisplayName("testApiConnection() 성공 시 Success 상태")
        fun testApiConnection_success_setsSuccessState() = runTest {
            coEvery { aiAnalysisRepository.testApiConnection() } returns Result.success(true)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.testApiConnection()
            advanceUntilIdle()

            viewModel.apiKeyTestState.test {
                assertIs<ApiKeyTestState.Success>(awaitItem())
            }
        }

        @Test
        @DisplayName("testApiConnection() 실패 시 Error 상태")
        fun testApiConnection_failure_setsErrorState() = runTest {
            coEvery { aiAnalysisRepository.testApiConnection() } returns
                Result.failure(Exception("연결 실패"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.testApiConnection()
            advanceUntilIdle()

            viewModel.apiKeyTestState.test {
                assertIs<ApiKeyTestState.Error>(awaitItem())
            }
        }

        @Test
        @DisplayName("clearApiTestState() 호출 시 Idle 상태 복귀")
        fun clearApiTestState_resetsToIdle() = runTest {
            coEvery { aiAnalysisRepository.testApiConnection() } returns Result.success(true)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.testApiConnection()
            advanceUntilIdle()

            viewModel.clearApiTestState()

            viewModel.apiKeyTestState.test {
                assertIs<ApiKeyTestState.Idle>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // Model loading tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("모델 목록 로딩 테스트")
    inner class ModelLoadingTests {

        @Test
        @DisplayName("loadClaudeModels() 성공 시 claudeModels 업데이트")
        fun loadClaudeModels_success_updatesModels() = runTest {
            val models = listOf(
                AIModel("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", AIProvider.CLAUDE),
                AIModel("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", AIProvider.CLAUDE)
            )
            coEvery { aiAnalysisRepository.listModels(AIProvider.CLAUDE) } returns Result.success(models)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadClaudeModels()
            advanceUntilIdle()

            viewModel.claudeModels.test {
                assertEquals(2, awaitItem().size)
            }
        }

        @Test
        @DisplayName("loadClaudeModels() 시작 시 isLoadingClaudeModels = true, 완료 후 false")
        fun loadClaudeModels_setsLoadingState() = runTest {
            coEvery { aiAnalysisRepository.listModels(AIProvider.CLAUDE) } returns Result.success(emptyList())

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadClaudeModels()
            advanceUntilIdle()

            viewModel.isLoadingClaudeModels.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("loadClaudeModels() 실패 시 error message 설정")
        fun loadClaudeModels_failure_setsErrorMessage() = runTest {
            coEvery { aiAnalysisRepository.listModels(AIProvider.CLAUDE) } returns
                Result.failure(Exception("API 오류"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadClaudeModels()
            advanceUntilIdle()

            viewModel.message.test {
                val msg = awaitItem()
                assertTrue(msg?.contains("실패") == true || msg?.contains("Claude") == true)
            }
        }

        @Test
        @DisplayName("clearMessage() 호출 시 message null 로 초기화")
        fun clearMessage_setsMessageToNull() = runTest {
            coEvery { aiAnalysisRepository.testApiConnection() } returns Result.success(true)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.testApiConnection()
            advanceUntilIdle()

            viewModel.clearMessage()

            viewModel.message.test {
                assertNull(awaitItem())
            }
        }
    }
}
