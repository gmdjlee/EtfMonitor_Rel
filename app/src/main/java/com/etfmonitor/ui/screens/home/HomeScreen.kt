package com.etfmonitor.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.ui.theme.*

/**
 * Home Screen - Summary Dashboard
 * Shows summary cards for each menu section
 *
 * Menu Structure:
 * - 시장 지표: Fear & Greed, 과매수/과매도, 증시 자금 동향
 * - ETF: ETF 목록, ETF 통계
 * - 종목: 종목 수급 분석
 * - 분석: AI 분석, ML 예측, 고급 분석
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMarketIndicator: () -> Unit,
    onNavigateToEtf: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    val showUnifiedInitDialog by viewModel.showUnifiedInitDialog.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lastDate = (state as? HomeState.Idle)?.lastDate

    var showDaysDialog by remember { mutableStateOf(false) }
    var showUnifiedDialog by remember { mutableStateOf(false) }

    // Dialog handlers
    LaunchedEffect(showUnifiedInitDialog) {
        if (showUnifiedInitDialog) showUnifiedDialog = true
    }

    LaunchedEffect(showFirstRunDialog) {
        if (showFirstRunDialog) showDaysDialog = true
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is HomeState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearMessage()
            }
            is HomeState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearMessage()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val s = state) {
            is HomeState.Initializing -> {
                LoadingScreen(
                    modifier = Modifier.padding(padding),
                    message = s.message,
                    progress = s.progress
                )
            }
            is HomeState.Updating -> {
                LoadingScreen(
                    modifier = Modifier.padding(padding),
                    message = s.message,
                    progress = s.progress
                )
            }
            else -> {
                HomeContent(
                    modifier = Modifier.padding(padding),
                    state = s,
                    lastDate = lastDate,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToMarketIndicator = onNavigateToMarketIndicator,
                    onNavigateToEtf = onNavigateToEtf,
                    onNavigateToStocks = onNavigateToStocks,
                    onNavigateToAnalysis = onNavigateToAnalysis
                )
            }
        }
    }

    // Dialogs
    if (showDaysDialog) {
        DaysSelectionDialog(
            onDismiss = {
                showDaysDialog = false
                if (showFirstRunDialog) viewModel.onFirstRunDialogShown()
            },
            onConfirm = { days ->
                viewModel.initialize(days)
                showDaysDialog = false
                if (showFirstRunDialog) viewModel.onFirstRunDialogShown()
            }
        )
    }

    if (showUnifiedDialog) {
        UnifiedInitializationDialog(
            onDismiss = {
                showUnifiedDialog = false
                viewModel.onUnifiedInitDialogDismiss()
            },
            onConfirm = { etfDays, depositPages, fearGreedDays, oscillatorDays ->
                showUnifiedDialog = false
                viewModel.initializeAll(etfDays, depositPages, fearGreedDays, oscillatorDays)
            }
        )
    }
}

@Composable
private fun LoadingScreen(
    modifier: Modifier = Modifier,
    message: String,
    progress: Int
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(MaterialTheme.spacing.large),
            shape = MaterialTheme.extendedShapes.card,
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = MaterialTheme.elevation.level3
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge)
            ) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.extendedShapes.circle),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier = Modifier,
    state: HomeState,
    lastDate: String?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMarketIndicator: () -> Unit,
    onNavigateToEtf: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToAnalysis: () -> Unit
) {
    val hasData = (state as? HomeState.Idle)?.hasData ?: false
    val summary = (state as? HomeState.Idle)?.summary
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
    ) {
        // Header with theme toggle
        HomeHeader(
            lastDate = lastDate,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSettingsClick = onNavigateToSettings
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Market Indicator Summary Card
            SummaryCard(
                title = "시장 지표",
                description = "Fear & Greed, 과매수/과매도, 증시 자금",
                icon = Icons.Default.BarChart,
                onClick = onNavigateToMarketIndicator,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                summaryContent = {
                    if (summary != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SummaryItem(
                                label = "F&G",
                                value = summary.kospiFearGreed?.let {
                                    "${((it + 100) / 2).toInt()}"
                                } ?: "—"
                            )
                            SummaryItem(
                                label = "과매수/과매도",
                                value = summary.kospiStatus ?: "—"
                            )
                        }
                    }
                }
            )

            // ETF Summary Card
            SummaryCard(
                title = "ETF",
                description = "테마별 ETF 목록 및 통계",
                icon = Icons.Default.PieChart,
                onClick = onNavigateToEtf,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                summaryContent = {
                    if (hasData) {
                        Text(
                            text = "ETF 데이터 수집 완료",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = "데이터를 수집해주세요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            )

            // Stocks Summary Card
            SummaryCard(
                title = "종목",
                description = "종목 수급 분석 및 추세 신호",
                icon = Icons.Default.ShowChart,
                onClick = onNavigateToStocks,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                summaryContent = {
                    Text(
                        text = "종목 검색 및 분석",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            )

            // Analysis Summary Card
            SummaryCard(
                title = "분석",
                description = "AI 시장 분석, ML 주가 예측, 고급 분석",
                icon = Icons.Default.Analytics,
                onClick = onNavigateToAnalysis,
                backgroundColor = AIInsightsBackground,
                contentColor = AIInsightsAccent,
                summaryContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AnalysisItem(icon = Icons.Default.AutoAwesome, label = "AI")
                        AnalysisItem(icon = Icons.Default.Psychology, label = "ML")
                        AnalysisItem(icon = Icons.Default.Dashboard, label = "고급")
                    }
                }
            )

            // Bottom padding
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun HomeHeader(
    lastDate: String?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            lastDate?.let {
                Text(
                    text = stringResource(R.string.home_last_update_short, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Theme toggle
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkTheme) "라이트 모드" else "다크 모드",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            // Settings
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    summaryContent: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = contentColor.copy(alpha = 0.2f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = contentColor
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary content
            summaryContent()
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun AnalysisItem(
    icon: ImageVector,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AIInsightsAccent,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AIInsightsAccent.copy(alpha = 0.8f)
        )
    }
}
