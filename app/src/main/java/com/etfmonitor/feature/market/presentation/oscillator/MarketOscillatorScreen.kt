package com.etfmonitor.feature.market.presentation.oscillator

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.database.entities.MarketOscillatorData
import com.etfmonitor.core.ui.component.LoadingCard
import com.etfmonitor.core.ui.component.ErrorCard
import com.etfmonitor.core.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketOscillatorScreen(
    onNavigateBack: () -> Unit,
    viewModel: MarketOscillatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val marketData by viewModel.marketData.collectAsState()
    val displayDays by viewModel.displayDays.collectAsState()
    val overboughtThreshold by viewModel.overboughtThreshold.collectAsState()
    val oversoldThreshold by viewModel.oversoldThreshold.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    val bodyScale by viewModel.bodyScale.collectAsState()

    var showManualPeriodDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // 첫 실행 다이얼로그
    if (showFirstRunDialog) {
        MarketOscillatorInitializeDialog(
            onDismiss = { viewModel.onFirstRunDialogShown() },
            onConfirm = { days ->
                viewModel.onFirstRunDialogConfirmed()
                viewModel.initialize(days)
            }
        )
    }

    // 수동 데이터 수집 다이얼로그
    if (showManualPeriodDialog) {
        MarketOscillatorInitializeDialog(
            onDismiss = { showManualPeriodDialog = false },
            onConfirm = { days ->
                showManualPeriodDialog = false
                viewModel.initialize(days)
            }
        )
    }

    // 설정 다이얼로그
    if (showSettingsDialog) {
        SettingsDialog(
            displayDays = displayDays,
            overboughtThreshold = overboughtThreshold,
            oversoldThreshold = oversoldThreshold,
            onDismiss = { showSettingsDialog = false },
            onConfirm = { days, overbought, oversold ->
                viewModel.onDisplayDaysChanged(days)
                viewModel.onOverboughtThresholdChanged(overbought)
                viewModel.onOversoldThresholdChanged(oversold)
                showSettingsDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.market_oscillator_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, stringResource(R.string.nav_settings))
                    }
                    val currentState = state
                    if (currentState is MarketOscillatorState.Idle && currentState.hasData) {
                        IconButton(onClick = { viewModel.update() }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.nav_update))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Market Selection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.market_select),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMarket == "KOSPI",
                            onClick = { viewModel.onSelectedMarketChanged("KOSPI") },
                            label = { Text(stringResource(R.string.market_kospi)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedMarket == "KOSDAQ",
                            onClick = { viewModel.onSelectedMarketChanged("KOSDAQ") },
                            label = { Text(stringResource(R.string.market_kosdaq)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // State Display
            when (val currentState = state) {
                is MarketOscillatorState.Loading -> LoadingCard(stringResource(R.string.data_loading))
                is MarketOscillatorState.Initializing -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Text(currentState.message)
                            Text(stringResource(R.string.progress_percent, currentState.progress), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is MarketOscillatorState.Updating -> LoadingCard(currentState.message)
                is MarketOscillatorState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            currentState.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearMessage()
                    }
                }
                is MarketOscillatorState.Error -> ErrorCard(currentState.message)
                is MarketOscillatorState.Idle -> {
                    if (!currentState.hasData) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.market_oscillator_no_data),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { showManualPeriodDialog = true }) {
                                    Text(stringResource(R.string.action_collect_data))
                                }
                            }
                        }
                    }
                }
            }

            // Latest Data Card
            if (marketData.isNotEmpty()) {
                val latest = marketData.firstOrNull()
                if (latest != null) {
                    LatestDataCard(
                        latest = latest,
                        overboughtThreshold = overboughtThreshold,
                        oversoldThreshold = oversoldThreshold
                    )
                }
            }

            // Data Table
            if (marketData.isNotEmpty()) {
                DataTable(
                    data = marketData,
                    overboughtThreshold = overboughtThreshold,
                    oversoldThreshold = oversoldThreshold,
                    bodyScale = bodyScale
                )
            }
        }
    }
}

@Composable
private fun LatestDataCard(
    latest: MarketOscillatorData,
    overboughtThreshold: Double,
    oversoldThreshold: Double
) {
    // 라이트 모드 색상 강제 적용
    val cardBackground = Color(0xFFFFFBFE) // Surface light
    val textColor = Color(0xFF1C1B1F) // OnSurface light
    val dividerColor = Color(0xFFCAC4D0) // OutlineVariant light

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "최신 데이터 (${latest.date})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            HorizontalDivider(color = dividerColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("지수", style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(
                    String.format("%.2f", latest.indexValue),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Oscillator", style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(
                    String.format("%.2f%%", latest.oscillator),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        latest.oscillator >= overboughtThreshold -> Color.Red
                        latest.oscillator <= oversoldThreshold -> Color.Blue
                        else -> textColor
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("상태", style = MaterialTheme.typography.bodyMedium, color = textColor)
                val status = when {
                    latest.oscillator >= overboughtThreshold -> "과매수"
                    latest.oscillator <= oversoldThreshold -> "과매도"
                    else -> "중립"
                }
                val statusColor = when {
                    latest.oscillator >= overboughtThreshold -> Color.Red
                    latest.oscillator <= oversoldThreshold -> Color.Blue
                    else -> textColor
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun DataTable(
    data: List<MarketOscillatorData>,
    overboughtThreshold: Double,
    oversoldThreshold: Double,
    bodyScale: Float
) {
    // 라이트 모드 색상 강제 적용
    val cardBackground = Color(0xFFFFFBFE) // Surface light
    val textColor = Color(0xFF1C1B1F) // OnSurface light
    val secondaryTextColor = Color(0xFF49454F) // OnSurfaceVariant light
    val headerBackground = Color(0xFFE7E0EC) // SurfaceVariant light
    val dividerColor = Color(0xFFCAC4D0) // OutlineVariant light

    // 스케일이 적용된 폰트 크기
    val dateFontSize = (11 * bodyScale).sp
    val valueFontSize = (11 * bodyScale).sp
    val statusFontSize = (10 * bodyScale).sp

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "과매수/과매도 내역",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Text(
                "표시 기간: 최근 ${data.size}일",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )

            HorizontalDivider(color = dividerColor)

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBackground)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "날짜",
                    modifier = Modifier.weight(0.4f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
                Text(
                    "지수",
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = textColor
                )
                Text(
                    "Oscillator",
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = textColor
                )
                Text(
                    "상태",
                    modifier = Modifier.weight(0.25f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
            }

            // Table Rows
            data.forEach { item ->
                val status = when {
                    item.oscillator >= overboughtThreshold -> "과매수"
                    item.oscillator <= oversoldThreshold -> "과매도"
                    else -> "중립"
                }
                val statusColor = when {
                    item.oscillator >= overboughtThreshold -> Color.Red
                    item.oscillator <= oversoldThreshold -> Color.Blue
                    else -> textColor
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.date,
                        modifier = Modifier.weight(0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = dateFontSize,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                    Text(
                        String.format("%.0f", item.indexValue),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = valueFontSize,
                        textAlign = TextAlign.End,
                        color = textColor
                    )
                    Text(
                        String.format("%.1f%%", item.oscillator),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = valueFontSize,
                        fontWeight = if (status != "중립") FontWeight.Bold else FontWeight.Normal,
                        color = statusColor,
                        textAlign = TextAlign.End
                    )
                    Text(
                        status,
                        modifier = Modifier.weight(0.25f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = statusFontSize,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )
                }

                if (item != data.last()) {
                    HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
                }
            }
        }
    }
}

@Composable
private fun MarketOscillatorInitializeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val periodOptions = listOf(
        PeriodOption(180, "6개월", "약 180일"),
        PeriodOption(365, "12개월 (권장)", "약 365일"),
        PeriodOption(540, "18개월", "약 540일"),
        PeriodOption(730, "24개월", "약 730일")
    )

    var selectedDays by remember { mutableStateOf(365) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("시장 과매수/과매도 초기화") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "코스피/코스닥 데이터 수집 기간을 선택하세요.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                periodOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedDays == option.days),
                                onClick = { selectedDays = option.days }
                            )
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

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "데이터 수집에는 선택한 기간에 따라 2-5분 정도 소요됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) {
                Text("수집 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        }
    )
}

@Composable
private fun SettingsDialog(
    displayDays: Int,
    overboughtThreshold: Double,
    oversoldThreshold: Double,
    onDismiss: () -> Unit,
    onConfirm: (Int, Double, Double) -> Unit
) {
    var days by remember { mutableStateOf(displayDays) }
    var overbought by remember { mutableStateOf(overboughtThreshold.toString()) }
    var oversold by remember { mutableStateOf(oversoldThreshold.toString()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("표시 설정") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Display Days
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("표시 기간", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 60, 90).forEach { option ->
                            FilterChip(
                                selected = days == option,
                                onClick = { days = option },
                                label = { Text("${option}일") }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Overbought Threshold
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("과매수 기준 (%)", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = overbought,
                        onValueChange = { overbought = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "예: 80",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = MaterialTheme.extendedShapes.searchBar,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                    )
                }

                // Oversold Threshold
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("과매도 기준 (%)", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = oversold,
                        onValueChange = { oversold = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "예: -80",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = MaterialTheme.extendedShapes.searchBar,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "• 과매수: Oscillator가 설정값 이상\n• 과매도: Oscillator가 설정값 이하",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val overboughtVal = overbought.toDoubleOrNull() ?: overboughtThreshold
                val oversoldVal = oversold.toDoubleOrNull() ?: oversoldThreshold
                onConfirm(days, overboughtVal, oversoldVal)
            }) {
                Text("적용")
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
