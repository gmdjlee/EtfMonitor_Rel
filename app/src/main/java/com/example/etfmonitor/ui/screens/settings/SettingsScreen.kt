package com.etfmonitor.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val themes by viewModel.themes.collectAsState()
    val exclusions by viewModel.exclusions.collectAsState()
    val defaultDays by viewModel.defaultDays.collectAsState()
    val stockUpdateSettings by viewModel.stockUpdateSettings.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ✅ 종목 DB 자동 업데이트 설정
            item {
                StockUpdateCard(
                    settings = stockUpdateSettings,
                    onTimeChange = { hour, minute -> viewModel.setUpdateTime(hour, minute) },
                    onUpdateNow = { viewModel.updateStocksNow() }
                )
            }

            // ✅ 기본 수집 기간 설정
            item {
                DefaultDaysCard(
                    currentDays = defaultDays,
                    onDaysChange = { viewModel.setDefaultDays(it) }
                )
            }

            // 테마 설정
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

            // 데이터베이스 초기화
            item {
                DatabaseCard(
                    onReset = { viewModel.resetDatabase() }
                )
            }
        }
    }
}

// ✅ 기본 수집 기간 카드
@Composable
private fun DefaultDaysCard(
    currentDays: Int,
    onDaysChange: (Int) -> Unit
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

    var selectedDays by remember { mutableStateOf(currentDays) }

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

            themes.chunked(3).forEach { rowThemes ->
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

            exclusions.chunked(3).forEach { rowExclusions ->
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

            // 마지막 업데이트 정보
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

            // 즉시 업데이트 버튼
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

@Composable
private fun TimePickerDialog(
    currentHour: Int,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var selectedHour by remember { mutableStateOf(currentHour) }
    var selectedMinute by remember { mutableStateOf(currentMinute) }

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