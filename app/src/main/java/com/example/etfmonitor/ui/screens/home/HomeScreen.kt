package com.etfmonitor.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToOscillator: () -> Unit,
    onNavigateToMarketDeposit: () -> Unit,  // ✅ 파라미터 추가
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDaysDialog by remember { mutableStateOf(false) }

    // 첫 실행 다이얼로그 표시
    LaunchedEffect(showFirstRunDialog) {
        if (showFirstRunDialog) {
            showDaysDialog = true
        }
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
        topBar = {
            TopAppBar(
                title = { Text("ETF Monitor") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    onNavigateToList = onNavigateToList,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToStatistics = onNavigateToStatistics,
                    onNavigateToOscillator = onNavigateToOscillator,
                    onNavigateToMarketDeposit = onNavigateToMarketDeposit  // ✅ 전달
                )
            }
        }
    }

    if (showDaysDialog) {
        DaysSelectionDialog(
            onDismiss = {
                showDaysDialog = false
                if (showFirstRunDialog) {
                    viewModel.onFirstRunDialogShown()
                }
            },
            onConfirm = { days ->
                viewModel.initialize(days)
                showDaysDialog = false
                if (showFirstRunDialog) {
                    viewModel.onFirstRunDialogShown()
                }
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.size(68.dp),
                strokeWidth = 6.dp
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                "$progress%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier = Modifier,
    state: HomeState,
    onNavigateToList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToOscillator: () -> Unit,
    onNavigateToMarketDeposit: () -> Unit  // ✅ 파라미터 추가
) {
    val hasData = (state as? HomeState.Idle)?.hasData ?: false
    val lastDate = (state as? HomeState.Idle)?.lastDate

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Card - Jetcaster style with elevation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(200)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "ETF Monitor",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    if (hasData) "액티브 ETF 구성 종목 모니터링"
                    else "시작하려면 설정에서 초기화를 진행하세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (lastDate != null) {
                    Text(
                        "마지막 업데이트: $lastDate",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Features
        if (hasData) {
            Text(
                "기능",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )

            FeatureCard(
                icon = Icons.AutoMirrored.Filled.List,
                title = "ETF 목록",
                description = "액티브 ETF 목록 조회 및 검색",
                onClick = onNavigateToList
            )

            // ✅ 통계 카드 추가
            FeatureCard(
                icon = Icons.Default.Analytics,
                title = "전체 통계",
                description = "금액 순위, 신규/제외 종목, 비중 변화",
                onClick = onNavigateToStatistics
            )
        }

        Text(
            "수급 분석",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )

        // ✅ 수급 오실레이터 카드 (데이터 없이도 사용 가능)
        FeatureCard(
            icon = Icons.Default.ShowChart,
            title = "차트 분석",
            description = "종목별 수급 오실레이터 및 매매 신호",
            onClick = onNavigateToOscillator
        )

        // ✅ 증시 자금 카드 (데이터 없이도 사용 가능)
        FeatureCard(
            icon = Icons.Default.TrendingUp,
            title = "증시 자금",
            description = "고객예탁금 & 신용잔고 동향 분석",
            onClick = onNavigateToMarketDeposit
        )

        FeatureCard(
            icon = Icons.Default.Settings,
            title = "설정",
            description = "테마 및 제외 키워드 관리",
            onClick = onNavigateToSettings
        )
    }
}

@Composable
private fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    // Jetcaster style: elevated cards with better spacing
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 4.dp
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DaysSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(
        DaysOption(5, "5일", "빠른 테스트"),
        DaysOption(10, "10일", "약 2주"),
        DaysOption(15, "15일", "약 3주"),
        DaysOption(20, "20일", "약 1개월"),
        DaysOption(25, "25일 (권장)", "약 1.5개월"),
        DaysOption(30, "30일", "약 2개월"),
        DaysOption(40, "40일", "약 2.5개월"),
        DaysOption(50, "50일", "약 3개월")
    )

    var selectedOption by remember { mutableStateOf(options[4]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("데이터 수집 기간 선택")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "수집할 영업일 수를 선택하세요",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (option == selectedOption),
                                onClick = { selectedOption = option }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (option == selectedOption),
                            onClick = { selectedOption = option }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            "ℹ️ 참고사항",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• 기간이 길수록 수집 시간이 오래 걸립니다\n" +
                                    "• 25일 권장 (약 1-2분 소요)\n" +
                                    "• 최초 실행 시 Python 패키지 로딩에 추가 시간이 필요할 수 있습니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedOption.days) }
            ) {
                Text("시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private data class DaysOption(
    val days: Int,
    val label: String,
    val description: String
)