package com.etfmonitor.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
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
    val message by viewModel.message.collectAsState()

    // General settings
    val fontSize by viewModel.fontSize.collectAsState()
    val fontColor by viewModel.fontColor.collectAsState()
    val chartLineColor by viewModel.chartLineColor.collectAsState()
    val chartFontColor by viewModel.chartFontColor.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("데이터 업데이트", "키워드", "일반")

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                                0 -> Icon(Icons.Default.CloudDownload, contentDescription = null)
                                1 -> Icon(Icons.Default.Label, contentDescription = null)
                                2 -> Icon(Icons.Default.Settings, contentDescription = null)
                            }
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> DataUpdateTab(
                    defaultDays = defaultDays,
                    searchHistoryLimit = searchHistoryLimit,
                    fearGreedPeriodDays = fearGreedPeriodDays,
                    marketOscillatorPeriodDays = marketOscillatorPeriodDays,
                    stockUpdateSettings = stockUpdateSettings,
                    marketDepositUpdateSettings = marketDepositUpdateSettings,
                    fearGreedUpdateSettings = fearGreedUpdateSettings,
                    marketOscillatorUpdateSettings = marketOscillatorUpdateSettings,
                    viewModel = viewModel
                )
                1 -> KeywordTab(
                    themes = themes,
                    exclusions = exclusions,
                    viewModel = viewModel
                )
                2 -> GeneralTab(
                    fontSize = fontSize,
                    fontColor = fontColor,
                    chartLineColor = chartLineColor,
                    chartFontColor = chartFontColor,
                    viewModel = viewModel
                )
            }
        }
    }
}

// ==================== Data Update Tab ====================
@Composable
private fun DataUpdateTab(
    defaultDays: Int,
    searchHistoryLimit: Int,
    fearGreedPeriodDays: Int,
    marketOscillatorPeriodDays: Int,
    stockUpdateSettings: StockUpdateSettings,
    marketDepositUpdateSettings: MarketDepositUpdateSettings,
    fearGreedUpdateSettings: FearGreedUpdateSettings,
    marketOscillatorUpdateSettings: MarketOscillatorUpdateSettings,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 데이터 관리
        item {
            DataManagementCard(
                onInitialize = { days -> viewModel.initializeData(days) },
                onUpdate = { viewModel.updateData() }
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

        // Fear & Greed Index 데이터 수집 기간 설정
        item {
            FearGreedPeriodCard(
                currentDays = fearGreedPeriodDays,
                onDaysChange = { viewModel.setFearGreedPeriodDays(it) }
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

        // 과매수/과매도 데이터 수집 기간 설정
        item {
            MarketOscillatorPeriodCard(
                currentDays = marketOscillatorPeriodDays,
                onDaysChange = { viewModel.setMarketOscillatorPeriodDays(it) }
            )
        }

        // 기본 수집 기간 설정
        item {
            DefaultDaysCard(
                currentDays = defaultDays,
                onDaysChange = { viewModel.setDefaultDays(it) }
            )
        }

        // 검색 히스토리 개수 설정
        item {
            SearchHistoryLimitCard(
                currentLimit = searchHistoryLimit,
                onLimitChange = { viewModel.setSearchHistoryLimit(it) }
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

// ==================== General Tab ====================
@Composable
private fun GeneralTab(
    fontSize: Int,
    fontColor: Long,
    chartLineColor: Long,
    chartFontColor: Long,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 폰트 사이즈 설정
        item {
            FontSizeCard(
                currentSize = fontSize,
                onSizeChange = { viewModel.setFontSize(it) }
            )
        }

        // 폰트 색깔 설정
        item {
            FontColorCard(
                currentColor = fontColor,
                onColorChange = { viewModel.setFontColor(it) }
            )
        }

        // 차트 라인 색깔 설정
        item {
            ChartLineColorCard(
                currentColor = chartLineColor,
                onColorChange = { viewModel.setChartLineColor(it) }
            )
        }

        // 차트 폰트 색깔 설정
        item {
            ChartFontColorCard(
                currentColor = chartFontColor,
                onColorChange = { viewModel.setChartFontColor(it) }
            )
        }
    }
}

// ==================== General Tab Cards ====================

@Composable
private fun FontSizeCard(
    currentSize: Int,
    onSizeChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.FormatSize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("폰트 사이즈", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "앱 전체 폰트 크기를 조절합니다 (1~10단계)",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "현재 설정",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "레벨 $currentSize",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Preview text
                Text(
                    "미리보기",
                    fontSize = (10 + currentSize * 2).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Slider for font size (1-10)
            Slider(
                value = currentSize.toFloat(),
                onValueChange = { onSizeChange(it.toInt()) },
                valueRange = 1f..10f,
                steps = 8
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("작게 (1)", style = MaterialTheme.typography.bodySmall)
                Text("크게 (10)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FontColorCard(
    currentColor: Long,
    onColorChange: (Long) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("폰트 색깔", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "앱 전체 폰트 색깔을 변경합니다",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(currentColor.toInt()))
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Text(
                        "현재 색상",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(onClick = { showColorPicker = true }) {
                    Text("변경")
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            title = "폰트 색깔 선택",
            currentColor = currentColor,
            onDismiss = { showColorPicker = false },
            onConfirm = { color ->
                onColorChange(color)
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun ChartLineColorCard(
    currentColor: Long,
    onColorChange: (Long) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("차트 라인 색깔", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "차트의 라인 색깔을 변경합니다",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(currentColor.toInt()))
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Text(
                        "현재 색상",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(onClick = { showColorPicker = true }) {
                    Text("변경")
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            title = "차트 라인 색깔 선택",
            currentColor = currentColor,
            onDismiss = { showColorPicker = false },
            onConfirm = { color ->
                onColorChange(color)
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun ChartFontColorCard(
    currentColor: Long,
    onColorChange: (Long) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("차트 폰트 색깔", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "차트의 레이블, 축 등의 폰트 색깔을 변경합니다",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(currentColor.toInt()))
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Text(
                        "현재 색상",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(onClick = { showColorPicker = true }) {
                    Text("변경")
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            title = "차트 폰트 색깔 선택",
            currentColor = currentColor,
            onDismiss = { showColorPicker = false },
            onConfirm = { color ->
                onColorChange(color)
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    currentColor: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentColor) }

    val predefinedColors = listOf(
        0xFF000000, // Black
        0xFF424242, // Dark Gray
        0xFF757575, // Gray
        0xFFBDBDBD, // Light Gray
        0xFFFFFFFF, // White
        0xFFF44336, // Red
        0xFFE91E63, // Pink
        0xFF9C27B0, // Purple
        0xFF673AB7, // Deep Purple
        0xFF3F51B5, // Indigo
        0xFF2196F3, // Blue
        0xFF03A9F4, // Light Blue
        0xFF00BCD4, // Cyan
        0xFF009688, // Teal
        0xFF4CAF50, // Green
        0xFF8BC34A, // Light Green
        0xFFCDDC39, // Lime
        0xFFFFEB3B, // Yellow
        0xFFFFC107, // Amber
        0xFFFF9800, // Orange
        0xFFFF5722, // Deep Orange
        0xFF795548, // Brown
        0xFF607D8B  // Blue Gray
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "색상을 선택하세요",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Color grid
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(predefinedColors.chunked(6)) { colorRow ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            colorRow.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(color.toInt()))
                                        .border(
                                            width = if (selectedColor == color) 3.dp else 1.dp,
                                            color = if (selectedColor == color)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = color }
                                )
                            }
                        }
                    }
                }

                // Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("미리보기: ", style = MaterialTheme.typography.bodyMedium)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(selectedColor.toInt()))
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedColor) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

// ==================== Data Update Tab Cards (기존 코드 유지) ====================

@Composable
private fun DefaultDaysCard(
    currentDays: Int,
    onDaysChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("기본 수집 기간", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "초기화 시 수집할 영업일 수",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "현재 설정",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${currentDays}일",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text("변경")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "권장: 25일 (약 1-2분 소요)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        DaysSelectionDialog(
            currentDays = currentDays,
            onDismiss = { showDialog = false },
            onConfirm = { days ->
                onDaysChange(days)
                showDialog = false
            }
        )
    }
}

@Composable
private fun SearchHistoryLimitCard(
    currentLimit: Int,
    onLimitChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("검색 히스토리", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "차트 분석에서 저장할 최대 검색 히스토리 개수",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "현재 설정",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${currentLimit}개",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text("변경")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "범위: 5~30개 (기본: 15개)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        SearchHistoryLimitDialog(
            currentLimit = currentLimit,
            onDismiss = { showDialog = false },
            onConfirm = { limit ->
                onLimitChange(limit)
                showDialog = false
            }
        )
    }
}

@Composable
private fun SearchHistoryLimitDialog(
    currentLimit: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedLimit by remember { mutableIntStateOf(currentLimit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("검색 히스토리 개수 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("저장할 최대 검색 히스토리 개수를 선택하세요")

                Column {
                    Text(
                        "${selectedLimit}개",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = selectedLimit.toFloat(),
                        onValueChange = { selectedLimit = it.toInt() },
                        valueRange = 5f..30f,
                        steps = 24
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("5개", style = MaterialTheme.typography.bodySmall)
                        Text("30개", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedLimit) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun DataManagementCard(
    onInitialize: (Int) -> Unit,
    onUpdate: () -> Unit
) {
    var showDaysDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("데이터 관리", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "ETF 데이터 초기화 및 업데이트",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { showDaysDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("데이터 초기화")
            }

            OutlinedButton(
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("데이터 업데이트")
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "초기화: 선택한 기간의 데이터를 수집합니다\n업데이트: 최신 데이터를 가져옵니다",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDaysDialog) {
        DaysSelectionDialog(
            currentDays = 25,
            onDismiss = { showDaysDialog = false },
            onConfirm = { days ->
                onInitialize(days)
                showDaysDialog = false
            }
        )
    }
}

@Composable
private fun DaysSelectionDialog(
    currentDays: Int,
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

    var selectedDays by remember { mutableIntStateOf(currentDays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("기본 수집 기간 변경") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
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
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) {
                Text("확인")
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

@Composable
private fun ThemeCard(
    themes: List<String>,
    onAddTheme: (String) -> Unit,
    onRemoveTheme: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var newTheme by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("포함 테마", style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, "추가")
                }
            }

            HorizontalDivider()

            Text(
                "이 키워드가 포함된 ETF를 수집합니다",
                style = MaterialTheme.typography.bodySmall
            )

            themes.chunked(6).forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowThemes.forEach { theme ->
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveTheme(theme) },
                            label = { Text(theme) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("테마 추가") },
            text = {
                OutlinedTextField(
                    value = newTheme,
                    onValueChange = { newTheme = it },
                    label = { Text("키워드") },
                    placeholder = { Text("예: 반도체") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddTheme(newTheme)
                        newTheme = ""
                        showDialog = false
                    }
                ) {
                    Text("추가")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun ExclusionCard(
    exclusions: List<String>,
    onAddExclusion: (String) -> Unit,
    onRemoveExclusion: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var newExclusion by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("제외 키워드", style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, "추가")
                }
            }

            HorizontalDivider()

            Text(
                "이 키워드가 포함된 ETF는 제외합니다",
                style = MaterialTheme.typography.bodySmall
            )

            exclusions.chunked(6).forEach { rowExclusions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowExclusions.forEach { exclusion ->
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveExclusion(exclusion) },
                            label = { Text(exclusion) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("제외 키워드 추가") },
            text = {
                OutlinedTextField(
                    value = newExclusion,
                    onValueChange = { newExclusion = it },
                    label = { Text("키워드") },
                    placeholder = { Text("예: 레버리지") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddExclusion(newExclusion)
                        newExclusion = ""
                        showDialog = false
                    }
                ) {
                    Text("추가")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun DatabaseCard(
    onReset: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text("데이터베이스", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "모든 수집된 데이터를 삭제합니다",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("데이터베이스 초기화")
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("데이터베이스 초기화") },
            text = { Text("모든 수집된 데이터가 삭제됩니다. 계속하시겠습니까?") },
            confirmButton = {
                Button(
                    onClick = {
                        onReset()
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("초기화")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

// 종목 DB 자동 업데이트 카드
@Composable
private fun StockUpdateCard(
    settings: StockUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("종목 DB 자동 업데이트", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "매일 지정된 시간에 종목 데이터를 자동으로 업데이트합니다",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "업데이트 시간",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${String.format("%02d", settings.updateHour)}:${String.format("%02d", settings.updateMinute)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showTimePicker = true }) {
                    Text("변경")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "저장된 종목 수:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${settings.stockCount}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    settings.lastUpdateTime?.let { time ->
                        val dateStr = java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date(time))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "마지막 업데이트:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                dateStr,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onUpdateNow,
                enabled = !settings.isUpdating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (settings.isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("업데이트 중...")
                } else {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("지금 업데이트")
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            currentHour = settings.updateHour,
            currentMinute = settings.updateMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                onTimeChange(hour, minute)
                showTimePicker = false
            }
        )
    }
}

// 증시 자금 DB 자동 업데이트 카드
@Composable
private fun MarketDepositUpdateCard(
    settings: MarketDepositUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("증시 자금 DB 자동 업데이트", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "매일 지정된 시간에 증시 자금 데이터를 자동으로 업데이트합니다",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "업데이트 시간",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${String.format("%02d", settings.updateHour)}:${String.format("%02d", settings.updateMinute)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showTimePicker = true }) {
                    Text("변경")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "저장된 데이터 수:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${settings.depositCount}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    settings.lastUpdateTime?.let { time ->
                        val dateStr = java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date(time))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "마지막 업데이트:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                dateStr,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onUpdateNow,
                enabled = !settings.isUpdating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (settings.isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("업데이트 중...")
                } else {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("지금 업데이트")
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            currentHour = settings.updateHour,
            currentMinute = settings.updateMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                onTimeChange(hour, minute)
                showTimePicker = false
            }
        )
    }
}

// Fear & Greed Index DB 자동 업데이트 카드
@Composable
private fun FearGreedUpdateCard(
    settings: FearGreedUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Fear & Greed Index 자동 업데이트", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "매일 지정된 시간에 Fear & Greed Index 데이터를 자동으로 업데이트합니다",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "업데이트 시간",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${String.format("%02d", settings.updateHour)}:${String.format("%02d", settings.updateMinute)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showTimePicker = true }) {
                    Text("변경")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("KOSPI:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${settings.kospiCount}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("KOSDAQ:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${settings.kosdaqCount}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    settings.lastUpdateTime?.let { time ->
                        val dateStr = java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date(time))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("마지막 업데이트:", style = MaterialTheme.typography.bodySmall)
                            Text(dateStr, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Button(
                onClick = onUpdateNow,
                enabled = !settings.isUpdating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (settings.isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("업데이트 중...")
                } else {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("지금 업데이트")
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            currentHour = settings.updateHour,
            currentMinute = settings.updateMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                onTimeChange(hour, minute)
                showTimePicker = false
            }
        )
    }
}

// Fear & Greed Index 데이터 수집 기간 카드
@Composable
private fun FearGreedPeriodCard(
    currentDays: Int,
    onDaysChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Fear & Greed Index 수집 기간", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "Fear & Greed Index 데이터 초기화 시 수집할 기간",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "현재 설정",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when (currentDays) {
                            180 -> "6개월"
                            365 -> "12개월"
                            540 -> "18개월"
                            730 -> "24개월"
                            else -> "${currentDays}일"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text("변경")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "권장: 12개월 (약 365일, 약 1-2분 소요)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        PeriodSelectionDialog(
            title = "Fear & Greed Index 수집 기간 설정",
            currentDays = currentDays,
            onDismiss = { showDialog = false },
            onConfirm = { days ->
                onDaysChange(days)
                showDialog = false
            }
        )
    }
}

// 과매수/과매도 DB 자동 업데이트 카드
@Composable
private fun MarketOscillatorUpdateCard(
    settings: MarketOscillatorUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Leaderboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("과매수/과매도 자동 업데이트", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "매일 지정된 시간에 과매수/과매도 데이터를 자동으로 업데이트합니다",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "업데이트 시간",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${String.format("%02d", settings.updateHour)}:${String.format("%02d", settings.updateMinute)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showTimePicker = true }) {
                    Text("변경")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("KOSPI:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${settings.kospiCount}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("KOSDAQ:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${settings.kosdaqCount}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    settings.lastUpdateTime?.let { time ->
                        val dateStr = java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date(time))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("마지막 업데이트:", style = MaterialTheme.typography.bodySmall)
                            Text(dateStr, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Button(
                onClick = onUpdateNow,
                enabled = !settings.isUpdating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (settings.isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("업데이트 중...")
                } else {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("지금 업데이트")
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            currentHour = settings.updateHour,
            currentMinute = settings.updateMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                onTimeChange(hour, minute)
                showTimePicker = false
            }
        )
    }
}

// 과매수/과매도 데이터 수집 기간 카드
@Composable
private fun MarketOscillatorPeriodCard(
    currentDays: Int,
    onDaysChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Leaderboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("과매수/과매도 수집 기간", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "과매수/과매도 데이터 초기화 시 수집할 기간",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "현재 설정",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when (currentDays) {
                            180 -> "6개월"
                            365 -> "12개월"
                            540 -> "18개월"
                            730 -> "24개월"
                            else -> "${currentDays}일"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text("변경")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "권장: 12개월 (약 365일, 약 1-2분 소요)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        PeriodSelectionDialog(
            title = "과매수/과매도 수집 기간 설정",
            currentDays = currentDays,
            onDismiss = { showDialog = false },
            onConfirm = { days ->
                onDaysChange(days)
                showDialog = false
            }
        )
    }
}

@Composable
private fun PeriodSelectionDialog(
    title: String,
    currentDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val periodOptions = listOf(
        PeriodOption(180, "6개월", "약 180일"),
        PeriodOption(365, "12개월 (권장)", "약 365일"),
        PeriodOption(540, "18개월", "약 540일"),
        PeriodOption(730, "24개월", "약 730일")
    )

    var selectedDays by remember { mutableIntStateOf(currentDays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "데이터 수집 기간을 선택하세요",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                periodOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
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
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private data class PeriodOption(
    val days: Int,
    val label: String,
    val description: String
)

@Composable
private fun TimePickerDialog(
    currentHour: Int,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(currentHour) }
    var selectedMinute by remember { mutableIntStateOf(currentMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("업데이트 시간 설정") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hour picker
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("시간", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedHour = (selectedHour - 1 + 24) % 24 }) {
                            Icon(Icons.Default.KeyboardArrowUp, "증가")
                        }
                    }
                    Text(
                        String.format("%02d", selectedHour),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                            Icon(Icons.Default.KeyboardArrowDown, "감소")
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))
                Text(":", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.width(16.dp))

                // Minute picker
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("분", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedMinute = (selectedMinute - 15 + 60) % 60 }) {
                            Icon(Icons.Default.KeyboardArrowUp, "증가")
                        }
                    }
                    Text(
                        String.format("%02d", selectedMinute),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedMinute = (selectedMinute + 15) % 60 }) {
                            Icon(Icons.Default.KeyboardArrowDown, "감소")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedHour, selectedMinute) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
