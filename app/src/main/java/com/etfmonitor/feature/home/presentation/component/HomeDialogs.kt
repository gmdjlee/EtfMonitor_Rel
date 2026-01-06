package com.etfmonitor.feature.home.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.core.ui.theme.*

/**
 * Home Screen Dialogs
 * - KisApiSetupDialog: 초기 실행 시 KIS API 설정
 * - DaysSelectionDialog: ETF 데이터 수집 기간 선택
 * - MarketDepositPagesSelectionDialog: 증시 자금 동향 페이지 수 선택
 * - FearGreedPeriodSelectionDialog: Fear & Greed 기간 선택
 * - MarketOscillatorPeriodSelectionDialog: 과매수/과매도 기간 선택
 * - UnifiedInitializationDialog: 통합 초기화 다이얼로그
 */

/**
 * KIS API 설정 다이얼로그
 * 앱 첫 실행 시 KIS API 자격 증명을 입력받습니다.
 * 데이터 수집 다이얼로그 전에 표시됩니다.
 */
@Composable
internal fun KisApiSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (appKey: String, appSecret: String) -> Unit,
    onSkip: () -> Unit
) {
    var appKey by remember { mutableStateOf("") }
    var appSecret by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                stringResource(R.string.dialog_kis_api_setup_title),
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
                // 설명
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.dialog_kis_api_setup_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // APP KEY 입력
                OutlinedTextField(
                    value = appKey,
                    onValueChange = { appKey = it },
                    label = { Text("APP KEY *") },
                    placeholder = { Text("PSxxxxxxxx...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                // APP SECRET 입력
                OutlinedTextField(
                    value = appSecret,
                    onValueChange = { appSecret = it },
                    label = { Text("APP SECRET *") },
                    placeholder = { Text("xxxxxxxxxx...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )

                // 안내 문구
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.settings_kis_api_url_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "https://apiportal.koreainvestment.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_api_key_secure),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // 경고 문구
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.dialog_kis_api_required_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(appKey, appSecret) },
                enabled = appKey.isNotBlank() && appSecret.isNotBlank(),
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.action_skip))
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

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
        DaysOption(5, stringResource(R.string.option_days_5), stringResource(R.string.option_days_5_desc)),
        DaysOption(10, stringResource(R.string.option_days_10), stringResource(R.string.option_days_10_desc)),
        DaysOption(15, stringResource(R.string.option_days_15), stringResource(R.string.option_days_15_desc)),
        DaysOption(20, stringResource(R.string.option_days_20), stringResource(R.string.option_days_20_desc)),
        DaysOption(25, stringResource(R.string.option_days_25), stringResource(R.string.option_days_25_desc)),
        DaysOption(30, stringResource(R.string.option_days_30), stringResource(R.string.option_days_30_desc)),
        DaysOption(40, stringResource(R.string.option_days_40), stringResource(R.string.option_days_40_desc)),
        DaysOption(50, stringResource(R.string.option_days_50), stringResource(R.string.option_days_50_desc))
    )

    var selectedOption by remember { mutableStateOf(options[4]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_init_data_collection)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.dialog_etf_collection_period),
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
                                stringResource(R.string.label_fear_greed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.dialog_fear_greed_auto_collect),
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
                            stringResource(R.string.dialog_notes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.dialog_collection_notes),
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
                Text(stringResource(R.string.action_start_collection))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
            ) { Text(stringResource(R.string.action_start_collection)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_later)) }
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
        title = stringResource(R.string.dialog_market_deposit_init),
        description = stringResource(R.string.dialog_market_deposit_desc),
        options = listOf(
            SelectionOption(5, stringResource(R.string.option_pages_5), stringResource(R.string.option_pages_5_desc)),
            SelectionOption(10, stringResource(R.string.option_pages_10), stringResource(R.string.option_pages_10_desc)),
            SelectionOption(15, stringResource(R.string.option_pages_15), stringResource(R.string.option_pages_15_desc)),
            SelectionOption(20, stringResource(R.string.option_pages_20), stringResource(R.string.option_pages_20_desc)),
            SelectionOption(30, stringResource(R.string.option_pages_30), stringResource(R.string.option_pages_30_desc))
        ),
        defaultValue = 10,
        infoText = stringResource(R.string.dialog_deposit_time_estimate),
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
        title = stringResource(R.string.dialog_fear_greed_init),
        description = stringResource(R.string.dialog_fear_greed_desc),
        options = listOf(
            SelectionOption(180, stringResource(R.string.option_months_6), stringResource(R.string.option_months_6_desc)),
            SelectionOption(365, stringResource(R.string.option_months_12), stringResource(R.string.option_months_12_desc)),
            SelectionOption(540, stringResource(R.string.option_months_18), stringResource(R.string.option_months_18_desc)),
            SelectionOption(730, stringResource(R.string.option_months_24), stringResource(R.string.option_months_24_desc))
        ),
        defaultValue = 365,
        infoText = stringResource(R.string.dialog_fear_greed_time_estimate),
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
        title = stringResource(R.string.dialog_oscillator_init),
        description = stringResource(R.string.dialog_oscillator_desc),
        options = listOf(
            SelectionOption(180, stringResource(R.string.option_months_6), stringResource(R.string.option_months_6_desc)),
            SelectionOption(365, stringResource(R.string.option_months_12), stringResource(R.string.option_months_12_desc)),
            SelectionOption(540, stringResource(R.string.option_months_18), stringResource(R.string.option_months_18_desc)),
            SelectionOption(730, stringResource(R.string.option_months_24), stringResource(R.string.option_months_24_desc))
        ),
        defaultValue = 365,
        infoText = stringResource(R.string.dialog_oscillator_time_estimate),
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
internal fun MarketIndexPeriodSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    OptionsSelectionDialog(
        title = stringResource(R.string.dialog_market_index_init),
        description = stringResource(R.string.dialog_market_index_desc),
        options = listOf(
            SelectionOption(30, stringResource(R.string.option_days_30), stringResource(R.string.option_days_30_desc)),
            SelectionOption(60, "60일", "약 3개월"),
            SelectionOption(90, "90일", "약 4.5개월"),
            SelectionOption(180, stringResource(R.string.option_months_6), stringResource(R.string.option_months_6_desc))
        ),
        defaultValue = 30,
        infoText = stringResource(R.string.dialog_market_index_time_estimate),
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
    onConfirm: (etfDays: Int, depositPages: Int?, fearGreedDays: Int?, oscillatorDays: Int?, marketIndexDays: Int?, bloodIndicatorDays: Int?) -> Unit
) {
    // ETF 수집 기간
    val etfOptions = listOf(
        DaysOption(5, stringResource(R.string.option_days_5), stringResource(R.string.option_days_5_desc)),
        DaysOption(10, stringResource(R.string.option_days_10), stringResource(R.string.option_days_10_desc)),
        DaysOption(15, stringResource(R.string.option_days_15), stringResource(R.string.option_days_15_desc)),
        DaysOption(20, stringResource(R.string.option_days_20), stringResource(R.string.option_days_20_desc)),
        DaysOption(25, stringResource(R.string.option_days_25), stringResource(R.string.option_days_25_desc))
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

    // 시장 지수 수집 여부
    var collectMarketIndex by remember { mutableStateOf(true) }
    var selectedMarketIndexDays by remember { mutableStateOf(30) }

    // Blood Indicator 수집 여부
    var collectBloodIndicator by remember { mutableStateOf(true) }
    var selectedBloodIndicatorDays by remember { mutableStateOf(1825) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.dialog_init_data_collection),
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
                    stringResource(R.string.dialog_select_data_to_collect),
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
                            stringResource(R.string.dialog_etf_data_required),
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
                    title = stringResource(R.string.menu_market_fund),
                    enabled = collectDeposit,
                    onEnabledChange = { collectDeposit = it },
                    options = listOf(
                        stringResource(R.string.option_pages_5_desc) to 5,
                        stringResource(R.string.option_pages_10) to 10,
                        stringResource(R.string.option_pages_20_desc) to 20
                    ),
                    selectedValue = selectedDepositPages,
                    onValueChange = { selectedDepositPages = it }
                )

                // 3. Fear & Greed Index (선택)
                UnifiedOptionSection(
                    title = stringResource(R.string.label_fear_greed),
                    enabled = collectFearGreed,
                    onEnabledChange = { collectFearGreed = it },
                    options = listOf(
                        stringResource(R.string.option_months_6) to 180,
                        stringResource(R.string.option_months_12) to 365,
                        stringResource(R.string.option_months_18) to 540
                    ),
                    selectedValue = selectedFearGreedDays,
                    onValueChange = { selectedFearGreedDays = it }
                )

                // 4. 과매수/과매도 지표 (선택)
                UnifiedOptionSection(
                    title = stringResource(R.string.menu_market_overbought),
                    enabled = collectOscillator,
                    onEnabledChange = { collectOscillator = it },
                    options = listOf(
                        stringResource(R.string.option_months_6) to 180,
                        stringResource(R.string.option_months_12) to 365,
                        stringResource(R.string.option_months_18) to 540
                    ),
                    selectedValue = selectedOscillatorDays,
                    onValueChange = { selectedOscillatorDays = it }
                )

                // 5. 시장 지수 (선택)
                UnifiedOptionSection(
                    title = stringResource(R.string.menu_market_index),
                    enabled = collectMarketIndex,
                    onEnabledChange = { collectMarketIndex = it },
                    options = listOf(
                        "30일" to 30,
                        "60일" to 60,
                        "90일" to 90
                    ),
                    selectedValue = selectedMarketIndexDays,
                    onValueChange = { selectedMarketIndexDays = it }
                )

                // 6. Blood Indicator (선택)
                UnifiedOptionSection(
                    title = "Blood Indicator (US)",
                    enabled = collectBloodIndicator,
                    onEnabledChange = { collectBloodIndicator = it },
                    options = listOf(
                        "1년" to 365,
                        "5년" to 1825,
                        "10년" to 3650
                    ),
                    selectedValue = selectedBloodIndicatorDays,
                    onValueChange = { selectedBloodIndicatorDays = it }
                )

                // 안내 문구
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Text(
                        stringResource(R.string.dialog_unified_time_estimate),
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
                        if (collectOscillator) selectedOscillatorDays else null,
                        if (collectMarketIndex) selectedMarketIndexDays else null,
                        if (collectBloodIndicator) selectedBloodIndicatorDays else null
                    )
                },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text(stringResource(R.string.action_start_collection))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_later))
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
