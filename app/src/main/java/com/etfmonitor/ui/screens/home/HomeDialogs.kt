package com.etfmonitor.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etfmonitor.ui.theme.*

/**
 * Home Screen Dialogs
 * - DaysSelectionDialog: ETF 데이터 수집 기간 선택
 * - MarketDepositPagesSelectionDialog: 증시 자금 동향 페이지 수 선택
 * - FearGreedPeriodSelectionDialog: Fear & Greed 기간 선택
 * - MarketOscillatorPeriodSelectionDialog: 과매수/과매도 기간 선택
 * - UnifiedInitializationDialog: 통합 초기화 다이얼로그
 */

internal data class DaysOption(
    val days: Int,
    val label: String,
    val description: String
)

internal data class SelectionOption(
    val value: Int,
    val label: String,
    val description: String
)

@Composable
internal fun DaysSelectionDialog(
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
        title = { Text("초기 데이터 수집") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "ETF 데이터 수집 기간을 선택하세요",
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

                Spacer(Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "Fear & Greed Index",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "ETF 데이터 초기화 완료 후\nFear & Greed Index 데이터 1년(365일)을\n자동으로 수집합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            "참고사항",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• 기간이 길수록 수집 시간이 오래 걸립니다\n" +
                                    "• 25일 권장 (약 1-2분 소요)\n" +
                                    "• Fear & Greed Index는 추가 1-2분 소요\n" +
                                    "• 최초 실행 시 Python 패키지 로딩에 추가 시간이 필요할 수 있습니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(selectedOption.days) },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text("시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

/**
 * 통합 옵션 선택 다이얼로그
 */
@Composable
internal fun OptionsSelectionDialog(
    title: String,
    description: String,
    options: List<SelectionOption>,
    defaultValue: Int,
    infoText: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedValue by remember { mutableStateOf(defaultValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))

                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedValue == option.value),
                                onClick = { selectedValue = option.value }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedValue == option.value),
                            onClick = { selectedValue = option.value }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
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
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Text(
                        infoText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(selectedValue) },
                shape = MaterialTheme.extendedShapes.button
            ) { Text("수집 시작") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("나중에") }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

@Composable
internal fun MarketDepositPagesSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    OptionsSelectionDialog(
        title = "증시 자금 동향 초기화",
        description = "증시 자금 동향 데이터 수집 페이지 수를 선택하세요.",
        options = listOf(
            SelectionOption(5, "5페이지", "약 최근 5일"),
            SelectionOption(10, "10페이지 (권장)", "약 최근 10일"),
            SelectionOption(15, "15페이지", "약 최근 15일"),
            SelectionOption(20, "20페이지", "약 최근 20일"),
            SelectionOption(30, "30페이지", "약 최근 30일")
        ),
        defaultValue = 10,
        infoText = "데이터 수집에는 약 30초-1분 정도 소요됩니다.",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
internal fun FearGreedPeriodSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    OptionsSelectionDialog(
        title = "Fear & Greed Index 초기화",
        description = "Fear & Greed Index 데이터 수집 기간을 선택하세요.",
        options = listOf(
            SelectionOption(180, "6개월", "약 180일"),
            SelectionOption(365, "12개월 (권장)", "약 365일"),
            SelectionOption(540, "18개월", "약 540일"),
            SelectionOption(730, "24개월", "약 730일")
        ),
        defaultValue = 365,
        infoText = "데이터 수집에는 선택한 기간에 따라 1-3분 정도 소요됩니다.",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
internal fun MarketOscillatorPeriodSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    OptionsSelectionDialog(
        title = "과매수/과매도 지표 초기화",
        description = "과매수/과매도 지표 데이터 수집 기간을 선택하세요.",
        options = listOf(
            SelectionOption(180, "6개월", "약 180일"),
            SelectionOption(365, "12개월 (권장)", "약 365일"),
            SelectionOption(540, "18개월", "약 540일"),
            SelectionOption(730, "24개월", "약 730일")
        ),
        defaultValue = 365,
        infoText = "데이터 수집에는 선택한 기간에 따라 1-5분 정도 소요됩니다.",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/**
 * 통합 초기화 다이얼로그
 * 앱 첫 실행 시 모든 데이터 수집 옵션을 한 번에 선택
 */
@Composable
internal fun UnifiedInitializationDialog(
    onDismiss: () -> Unit,
    onConfirm: (etfDays: Int, depositPages: Int?, fearGreedDays: Int?, oscillatorDays: Int?) -> Unit
) {
    // ETF 수집 기간
    val etfOptions = listOf(
        DaysOption(5, "5일", "빠른 테스트"),
        DaysOption(10, "10일", "약 2주"),
        DaysOption(15, "15일", "약 3주"),
        DaysOption(20, "20일", "약 1개월"),
        DaysOption(25, "25일 (권장)", "약 1.5개월")
    )
    var selectedEtfDays by remember { mutableStateOf(25) }

    // 증시 자금 동향 수집 여부
    var collectDeposit by remember { mutableStateOf(true) }
    var selectedDepositPages by remember { mutableStateOf(10) }

    // Fear & Greed Index 수집 여부
    var collectFearGreed by remember { mutableStateOf(true) }
    var selectedFearGreedDays by remember { mutableStateOf(365) }

    // 과매수/과매도 수집 여부
    var collectOscillator by remember { mutableStateOf(true) }
    var selectedOscillatorDays by remember { mutableStateOf(365) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "초기 데이터 수집",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "수집할 데이터를 선택하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. ETF 데이터 수집 기간 (필수)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "ETF 데이터 (필수)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        etfOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (selectedEtfDays == option.days),
                                        onClick = { selectedEtfDays = option.days }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedEtfDays == option.days),
                                    onClick = { selectedEtfDays = option.days }
                                )
                                Text(
                                    "${option.label} - ${option.description}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 2. 증시 자금 동향 (선택)
                UnifiedOptionSection(
                    title = "증시 자금 동향",
                    enabled = collectDeposit,
                    onEnabledChange = { collectDeposit = it },
                    options = listOf(
                        "5페이지 (약 5일)" to 5,
                        "10페이지 (권장)" to 10,
                        "20페이지 (약 20일)" to 20
                    ),
                    selectedValue = selectedDepositPages,
                    onValueChange = { selectedDepositPages = it }
                )

                // 3. Fear & Greed Index (선택)
                UnifiedOptionSection(
                    title = "Fear & Greed Index",
                    enabled = collectFearGreed,
                    onEnabledChange = { collectFearGreed = it },
                    options = listOf(
                        "6개월" to 180,
                        "12개월 (권장)" to 365,
                        "18개월" to 540
                    ),
                    selectedValue = selectedFearGreedDays,
                    onValueChange = { selectedFearGreedDays = it }
                )

                // 4. 과매수/과매도 지표 (선택)
                UnifiedOptionSection(
                    title = "시장 과매수/과매도",
                    enabled = collectOscillator,
                    onEnabledChange = { collectOscillator = it },
                    options = listOf(
                        "6개월" to 180,
                        "12개월 (권장)" to 365,
                        "18개월" to 540
                    ),
                    selectedValue = selectedOscillatorDays,
                    onValueChange = { selectedOscillatorDays = it }
                )

                // 안내 문구
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Text(
                        "• ETF 데이터: 약 1-2분\n• 증시 자금 동향: 약 30초\n• Fear & Greed: 약 1-2분\n• 과매수/과매도: 약 3-5분",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(
                        selectedEtfDays,
                        if (collectDeposit) selectedDepositPages else null,
                        if (collectFearGreed) selectedFearGreedDays else null,
                        if (collectOscillator) selectedOscillatorDays else null
                    )
                },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text("수집 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

/**
 * 통합 다이얼로그의 선택 옵션 섹션
 */
@Composable
internal fun UnifiedOptionSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit
) {
    Surface(
        color = if (enabled)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.extendedShapes.card
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (label, value) ->
                        FilterChip(
                            selected = selectedValue == value,
                            onClick = { onValueChange(value) },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
