package com.etfmonitor.feature.settings.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.ui.component.HubHeader
import com.etfmonitor.feature.backup.presentation.screen.BackupTabContent
import com.etfmonitor.feature.backup.presentation.viewmodel.BackupViewModel
import com.etfmonitor.feature.settings.presentation.component.*

/**
 * Settings Screen - Main Entry Point
 * Provides comprehensive app configuration across multiple tabs:
 * - 일반 (General): Theme, AI API keys, Font settings
 * - 키워드 (Keywords): Include/Exclude keywords for ETF filtering
 * - 데이터 업데이트 (Data Update): Auto-update schedules, manual update controls
 * - 수집 기간 (Data Period): Default collection days, Fear & Greed period, etc.
 * - 차트 (Chart): Chart color customization
 *
 * Component files in settings/components/:
 * - GeneralCards.kt: ThemeSettingCard, AIApiKeyCard, FontScaleCard
 * - KeywordCards.kt: ThemeCard, ExclusionCard
 * - DataCards.kt: DataManagementCard, DefaultDaysCard, SearchHistoryLimitCard, DatabaseCard
 * - PeriodCards.kt: FearGreedPeriodCard, MarketOscillatorPeriodCard
 * - UpdateCards.kt: StockUpdateCard, MarketDepositUpdateCard, FearGreedUpdateCard, etc.
 * - ChartColorCards.kt: MarketCapOscillatorColorCard, MacdColorCard, etc.
 * - ColorPickerComponents.kt: Color picker UI components
 */

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel()
) {
    val themes by viewModel.themes.collectAsState()
    val exclusions by viewModel.exclusions.collectAsState()
    val defaultDays by viewModel.defaultDays.collectAsState()
    val searchHistoryLimit by viewModel.searchHistoryLimit.collectAsState()
    val fearGreedPeriodDays by viewModel.fearGreedPeriodDays.collectAsState()
    val marketOscillatorPeriodDays by viewModel.marketOscillatorPeriodDays.collectAsState()
    val stockUpdateSettings by viewModel.stockUpdateSettings.collectAsState()
    val marketDepositUpdateSettings by viewModel.marketDepositUpdateSettings.collectAsState()
    val fearGreedUpdateSettings by viewModel.fearGreedUpdateSettings.collectAsState()
    val marketOscillatorUpdateSettings by viewModel.marketOscillatorUpdateSettings.collectAsState()
    val marketIndexUpdateSettings by viewModel.marketIndexUpdateSettings.collectAsState()
    val etfUpdateSettings by viewModel.etfUpdateSettings.collectAsState()
    val bloodIndicatorUpdateSettings by viewModel.bloodIndicatorUpdateSettings.collectAsState()
    val marketIndexPeriodDays by viewModel.marketIndexPeriodDays.collectAsState()
    val bloodIndicatorPeriodDays by viewModel.bloodIndicatorPeriodDays.collectAsState()
    val message by viewModel.message.collectAsState()

    // General settings
    val isDarkThemeSetting by viewModel.isDarkTheme.collectAsState()
    val fontScaleSettings by viewModel.fontScaleSettings.collectAsState()
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()

    // Chart color settings
    val chartColorSettings by viewModel.chartColorSettings.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.settings_tab_general),
        stringResource(R.string.settings_tab_keyword),
        stringResource(R.string.settings_tab_data_update),
        stringResource(R.string.settings_tab_period),
        stringResource(R.string.settings_tab_chart),
        stringResource(R.string.settings_tab_backup)
    )

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            HubHeader(
                title = stringResource(R.string.settings_title),
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings_tab))
                                1 -> Icon(Icons.Default.Label, contentDescription = stringResource(R.string.cd_keyword_tab))
                                2 -> Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.cd_download_tab))
                                3 -> Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.cd_period_tab))
                                4 -> Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.cd_palette_tab))
                                5 -> Icon(Icons.Default.Backup, contentDescription = stringResource(R.string.cd_backup_tab))
                            }
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> GeneralTab(
                    isDarkTheme = isDarkThemeSetting,
                    fontScaleSettings = fontScaleSettings,
                    quickChartAnalysisEnabled = quickChartAnalysisEnabled,
                    viewModel = viewModel
                )
                1 -> KeywordTab(
                    themes = themes,
                    exclusions = exclusions,
                    viewModel = viewModel
                )
                2 -> DataUpdateTab(
                    stockUpdateSettings = stockUpdateSettings,
                    marketDepositUpdateSettings = marketDepositUpdateSettings,
                    fearGreedUpdateSettings = fearGreedUpdateSettings,
                    marketOscillatorUpdateSettings = marketOscillatorUpdateSettings,
                    marketIndexUpdateSettings = marketIndexUpdateSettings,
                    etfUpdateSettings = etfUpdateSettings,
                    bloodIndicatorUpdateSettings = bloodIndicatorUpdateSettings,
                    viewModel = viewModel
                )
                3 -> DataPeriodTab(
                    defaultDays = defaultDays,
                    searchHistoryLimit = searchHistoryLimit,
                    fearGreedPeriodDays = fearGreedPeriodDays,
                    marketOscillatorPeriodDays = marketOscillatorPeriodDays,
                    marketIndexPeriodDays = marketIndexPeriodDays,
                    bloodIndicatorPeriodDays = bloodIndicatorPeriodDays,
                    viewModel = viewModel
                )
                4 -> ChartTab(
                    chartColorSettings = chartColorSettings,
                    viewModel = viewModel
                )
                5 -> BackupTab(
                    backupViewModel = backupViewModel
                )
            }
        }
    }
}

// ==================== General Tab ====================
@Composable
private fun GeneralTab(
    isDarkTheme: Boolean?,
    fontScaleSettings: com.etfmonitor.core.ui.theme.FontScaleSettings,
    quickChartAnalysisEnabled: Boolean,
    viewModel: SettingsViewModel
) {
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val isClaudeConfigured by viewModel.isClaudeApiKeyConfigured.collectAsState()
    val isGeminiConfigured by viewModel.isGeminiApiKeyConfigured.collectAsState()
    val apiKeyTestState by viewModel.apiKeyTestState.collectAsState()

    val claudeModels by viewModel.claudeModels.collectAsState()
    val geminiModels by viewModel.geminiModels.collectAsState()
    val selectedClaudeModel by viewModel.selectedClaudeModel.collectAsState()
    val selectedGeminiModel by viewModel.selectedGeminiModel.collectAsState()
    val isLoadingClaudeModels by viewModel.isLoadingClaudeModels.collectAsState()
    val isLoadingGeminiModels by viewModel.isLoadingGeminiModels.collectAsState()
    val isFredApiKeyConfigured by viewModel.isFredApiKeyConfigured.collectAsState()
    val isKisApiKeyConfigured by viewModel.isKisApiKeyConfigured.collectAsState()
    val isKiwoomApiKeyConfigured by viewModel.isKiwoomApiKeyConfigured.collectAsState()
    val kiwoomInvestmentMode by viewModel.kiwoomInvestmentMode.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 테마 설정
        item {
            ThemeSettingCard(
                isDarkTheme = isDarkTheme,
                onThemeChange = { viewModel.setDarkTheme(it) }
            )
        }

        // 빠른 차트 분석 설정
        item {
            QuickChartAnalysisCard(
                isEnabled = quickChartAnalysisEnabled,
                onEnabledChange = { viewModel.setQuickChartAnalysisEnabled(it) }
            )
        }

        // AI API 키 설정
        item {
            AIApiKeyCard(
                selectedProvider = selectedProvider,
                isClaudeConfigured = isClaudeConfigured,
                isGeminiConfigured = isGeminiConfigured,
                testState = apiKeyTestState,
                claudeModels = claudeModels,
                geminiModels = geminiModels,
                selectedClaudeModel = selectedClaudeModel,
                selectedGeminiModel = selectedGeminiModel,
                isLoadingClaudeModels = isLoadingClaudeModels,
                isLoadingGeminiModels = isLoadingGeminiModels,
                onProviderSelected = { viewModel.setSelectedProvider(it) },
                onSetClaudeApiKey = { viewModel.setClaudeApiKey(it) },
                onSetGeminiApiKey = { viewModel.setGeminiApiKey(it) },
                onClearClaudeApiKey = { viewModel.clearClaudeApiKey() },
                onClearGeminiApiKey = { viewModel.clearGeminiApiKey() },
                onTestConnection = { viewModel.testApiConnection() },
                onClearTestState = { viewModel.clearApiTestState() },
                onLoadClaudeModels = { viewModel.loadClaudeModels() },
                onLoadGeminiModels = { viewModel.loadGeminiModels() },
                onSelectClaudeModel = { viewModel.setClaudeModel(it) },
                onSelectGeminiModel = { viewModel.setGeminiModel(it) }
            )
        }

        // FRED API 키 설정 (Blood Indicator 용)
        item {
            FredApiKeyCard(
                isConfigured = isFredApiKeyConfigured,
                onSetApiKey = { viewModel.setFredApiKey(it) },
                onClearApiKey = { viewModel.clearFredApiKey() }
            )
        }

        // KIS API 키 설정 (재무정보 조회용)
        item {
            KisApiKeyCard(
                isConfigured = isKisApiKeyConfigured,
                onSetAppKey = { viewModel.setKisAppKey(it) },
                onSetAppSecret = { viewModel.setKisAppSecret(it) },
                onClearApiKeys = { viewModel.clearKisApiKeys() }
            )
        }

        // Kiwoom API 키 설정 (순위 조회용)
        item {
            KiwoomApiKeyCard(
                isConfigured = isKiwoomApiKeyConfigured,
                currentInvestmentMode = kiwoomInvestmentMode,
                onSetAppKey = { viewModel.setKiwoomAppKey(it) },
                onSetSecretKey = { viewModel.setKiwoomSecretKey(it) },
                onSetInvestmentMode = { viewModel.setKiwoomInvestmentMode(it) },
                onClearApiKeys = { viewModel.clearKiwoomApiKeys() }
            )
        }

        // 폰트 크기 설정
        item {
            FontScaleCard(
                fontScaleSettings = fontScaleSettings,
                onDisplayScaleChange = { viewModel.setDisplayScale(it) },
                onHeadlineScaleChange = { viewModel.setHeadlineScale(it) },
                onTitleScaleChange = { viewModel.setTitleScale(it) },
                onBodyScaleChange = { viewModel.setBodyScale(it) },
                onLabelScaleChange = { viewModel.setLabelScale(it) }
            )
        }
    }
}

// ==================== Backup Tab ====================
@Composable
private fun BackupTab(
    backupViewModel: BackupViewModel
) {
    BackupTabContent(viewModel = backupViewModel)
}

// ==================== Keyword Tab ====================
@Composable
private fun KeywordTab(
    themes: List<String>,
    exclusions: List<String>,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 포함 테마 설정
        item {
            ThemeCard(
                themes = themes,
                onAddTheme = { viewModel.addTheme(it) },
                onRemoveTheme = { viewModel.removeTheme(it) }
            )
        }

        // 제외 키워드 설정
        item {
            ExclusionCard(
                exclusions = exclusions,
                onAddExclusion = { viewModel.addExclusion(it) },
                onRemoveExclusion = { viewModel.removeExclusion(it) }
            )
        }
    }
}

// ==================== Data Update Tab ====================
@Composable
private fun DataUpdateTab(
    stockUpdateSettings: StockUpdateSettings,
    marketDepositUpdateSettings: MarketDepositUpdateSettings,
    fearGreedUpdateSettings: FearGreedUpdateSettings,
    marketOscillatorUpdateSettings: MarketOscillatorUpdateSettings,
    marketIndexUpdateSettings: MarketIndexUpdateSettings,
    etfUpdateSettings: EtfUpdateSettings,
    bloodIndicatorUpdateSettings: BloodIndicatorUpdateSettings,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ETF 데이터 자동 업데이트 설정
        // 참고: ETF 데이터 초기화는 하단의 DatabaseCard에서 지원됨
        item {
            EtfDataManagementCard(
                settings = etfUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setEtfUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateEtfNow() }
            )
        }

        // 종목 DB 자동 업데이트 설정
        item {
            StockUpdateCard(
                settings = stockUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateStocksNow() }
            )
        }

        // 증시 자금 DB 자동 업데이트 설정
        item {
            MarketDepositUpdateCard(
                settings = marketDepositUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setMarketDepositUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateMarketDepositsNow() }
            )
        }

        // Fear & Greed Index DB 자동 업데이트 설정
        item {
            FearGreedUpdateCard(
                settings = fearGreedUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setFearGreedUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateFearGreedNow() }
            )
        }

        // 과매수/과매도 DB 자동 업데이트 설정
        item {
            MarketOscillatorUpdateCard(
                settings = marketOscillatorUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setMarketOscillatorUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateMarketOscillatorsNow() }
            )
        }

        // 시장 지수 DB 자동 업데이트 설정
        item {
            MarketIndexUpdateCard(
                settings = marketIndexUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setMarketIndexUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateMarketIndexNow() }
            )
        }

        // Blood Indicator DB 자동 업데이트 설정
        item {
            BloodIndicatorUpdateCard(
                settings = bloodIndicatorUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setBloodIndicatorUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateBloodIndicatorNow() }
            )
        }

        // 데이터베이스 초기화
        item {
            DatabaseCard(
                onReset = { viewModel.resetDatabase() }
            )
        }
    }
}

// ==================== Data Period Tab ====================
@Composable
private fun DataPeriodTab(
    defaultDays: Int,
    searchHistoryLimit: Int,
    fearGreedPeriodDays: Int,
    marketOscillatorPeriodDays: Int,
    marketIndexPeriodDays: Int,
    bloodIndicatorPeriodDays: Int,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ETF 수집 기간 설정
        item {
            DefaultDaysCard(
                currentDays = defaultDays,
                onDaysChange = { days, reinitialize -> viewModel.setDefaultDays(days, reinitialize) }
            )
        }

        // Fear & Greed Index 데이터 수집 기간 설정
        item {
            FearGreedPeriodCard(
                currentDays = fearGreedPeriodDays,
                onDaysChange = { days, reinitialize -> viewModel.setFearGreedPeriodDays(days, reinitialize) }
            )
        }

        // 과매수/과매도 데이터 수집 기간 설정
        item {
            MarketOscillatorPeriodCard(
                currentDays = marketOscillatorPeriodDays,
                onDaysChange = { days, reinitialize -> viewModel.setMarketOscillatorPeriodDays(days, reinitialize) }
            )
        }

        // 시장 지수 데이터 수집 기간 설정
        item {
            MarketIndexPeriodCard(
                currentDays = marketIndexPeriodDays,
                onDaysChange = { days, reinitialize -> viewModel.setMarketIndexPeriodDays(days, reinitialize) }
            )
        }

        // Blood Indicator 데이터 수집 기간 설정
        item {
            BloodIndicatorPeriodCard(
                currentDays = bloodIndicatorPeriodDays,
                onDaysChange = { days, reinitialize -> viewModel.setBloodIndicatorPeriodDays(days, reinitialize) }
            )
        }

        // 검색 히스토리 개수 설정
        item {
            SearchHistoryLimitCard(
                currentLimit = searchHistoryLimit,
                onLimitChange = { viewModel.setSearchHistoryLimit(it) }
            )
        }
    }
}

// ==================== Chart Tab ====================
@Composable
private fun ChartTab(
    chartColorSettings: com.etfmonitor.core.ui.theme.ChartColorSettings,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 시가총액 & 오실레이터 차트 색상
        item {
            MarketCapOscillatorColorCard(
                colors = chartColorSettings.marketCapOscillator,
                onLineColor1Changed = { viewModel.setMarketCapOscillatorLineColor1(it) },
                onLineColor2Changed = { viewModel.setMarketCapOscillatorLineColor2(it) },
                onTextColorChanged = { viewModel.setMarketCapOscillatorTextColor(it) },
                onTextColorReset = { viewModel.setMarketCapOscillatorTextColor(null) },
                onLegendColorChanged = { viewModel.setMarketCapOscillatorLegendColor(it) },
                onLegendColorReset = { viewModel.setMarketCapOscillatorLegendColor(null) }
            )
        }

        // MACD 차트 색상
        item {
            MacdColorCard(
                colors = chartColorSettings.macd,
                onLineColor1Changed = { viewModel.setMacdLineColor1(it) },
                onLineColor2Changed = { viewModel.setMacdLineColor2(it) },
                onPositiveColorChanged = { viewModel.setMacdPositiveColor(it) },
                onNegativeColorChanged = { viewModel.setMacdNegativeColor(it) },
                onTextColorChanged = { viewModel.setMacdTextColor(it) },
                onTextColorReset = { viewModel.setMacdTextColor(null) },
                onLegendColorChanged = { viewModel.setMacdLegendColor(it) },
                onLegendColorReset = { viewModel.setMacdLegendColor(null) }
            )
        }

        // 증시 자금 동향 차트 색상
        item {
            MarketDepositColorCard(
                colors = chartColorSettings.marketDeposit,
                onLineColor1Changed = { viewModel.setMarketDepositLineColor1(it) },
                onLineColor2Changed = { viewModel.setMarketDepositLineColor2(it) },
                onTextColorChanged = { viewModel.setMarketDepositTextColor(it) },
                onTextColorReset = { viewModel.setMarketDepositTextColor(null) },
                onLegendColorChanged = { viewModel.setMarketDepositLegendColor(it) },
                onLegendColorReset = { viewModel.setMarketDepositLegendColor(null) }
            )
        }

        // Fear & Greed Index 차트 색상
        item {
            FearGreedColorCard(
                colors = chartColorSettings.fearGreed,
                onLineColor1Changed = { viewModel.setFearGreedLineColor1(it) },
                onLineColor2Changed = { viewModel.setFearGreedLineColor2(it) },
                onTextColorChanged = { viewModel.setFearGreedTextColor(it) },
                onTextColorReset = { viewModel.setFearGreedTextColor(null) },
                onLegendColorChanged = { viewModel.setFearGreedLegendColor(it) },
                onLegendColorReset = { viewModel.setFearGreedLegendColor(null) }
            )
        }

        // 초기화 버튼
        item {
            ResetChartColorsCard(
                onReset = { viewModel.resetChartColors() }
            )
        }
    }
}
