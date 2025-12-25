package com.etfmonitor.feature.settings.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.core.ui.theme.SingleChartColorSettings

/**
 * 색상 항목 설정
 */
data class ColorItemConfig(
    val label: String,
    val getCurrentColor: (SingleChartColorSettings) -> Int,
    val onColorSelected: (Int) -> Unit
)

/**
 * 선택적 색상 항목 설정
 */
data class OptionalColorItemConfig(
    val label: String,
    val getCurrentColor: (SingleChartColorSettings) -> Int?,
    val onColorSelected: (Int) -> Unit,
    val onReset: () -> Unit
)

/**
 * 색상 섹션 설정
 */
data class ColorSectionConfig(
    val title: String,
    val items: List<ColorItemConfig> = emptyList(),
    val optionalItems: List<OptionalColorItemConfig> = emptyList()
)

/**
 * 차트 색상 카드 설정
 */
data class ChartColorCardConfig(
    val title: String,
    val icon: ImageVector,
    val sections: List<ColorSectionConfig>
)

/**
 * 공통 차트 색상 카드 컴포넌트
 */
@Composable
fun ChartColorCard(
    config: ChartColorCardConfig,
    colors: SingleChartColorSettings,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    config.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(config.title, style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            // 각 섹션 렌더링
            config.sections.forEachIndexed { index, section ->
                // 섹션 제목
                Text(
                    section.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 필수 색상 항목들
                section.items.forEach { item ->
                    ColorPickerRow(
                        label = item.label,
                        currentColor = item.getCurrentColor(colors),
                        onColorSelected = item.onColorSelected
                    )
                }

                // 선택적 색상 항목들
                section.optionalItems.forEach { item ->
                    OptionalColorPickerRow(
                        label = item.label,
                        currentColor = item.getCurrentColor(colors),
                        onColorSelected = item.onColorSelected,
                        onReset = item.onReset
                    )
                }

                // 마지막 섹션이 아니면 구분선 추가
                if (index < config.sections.size - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * 시가총액 & 오실레이터 차트 색상 카드
 */
@Composable
fun MarketCapOscillatorColorCard(
    colors: SingleChartColorSettings,
    onLineColor1Changed: (Int) -> Unit,
    onLineColor2Changed: (Int) -> Unit,
    onTextColorChanged: (Int) -> Unit,
    onTextColorReset: () -> Unit,
    onLegendColorChanged: (Int) -> Unit,
    onLegendColorReset: () -> Unit
) {
    val config = ChartColorCardConfig(
        title = stringResource(R.string.settings_chart_marketcap),
        icon = Icons.Default.ShowChart,
        sections = listOf(
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_line_color),
                items = listOf(
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_marketcap_line),
                        getCurrentColor = { it.lineColor1 },
                        onColorSelected = onLineColor1Changed
                    ),
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_oscillator_line),
                        getCurrentColor = { it.lineColor2 },
                        onColorSelected = onLineColor2Changed
                    )
                )
            ),
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_text_legend),
                optionalItems = listOf(
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_axis_color),
                        getCurrentColor = { it.textColor },
                        onColorSelected = onTextColorChanged,
                        onReset = onTextColorReset
                    ),
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_legend_color),
                        getCurrentColor = { it.legendColor },
                        onColorSelected = onLegendColorChanged,
                        onReset = onLegendColorReset
                    )
                )
            )
        )
    )

    ChartColorCard(config = config, colors = colors)
}

/**
 * MACD 차트 색상 카드
 */
@Composable
fun MacdColorCard(
    colors: SingleChartColorSettings,
    onLineColor1Changed: (Int) -> Unit,
    onLineColor2Changed: (Int) -> Unit,
    onPositiveColorChanged: (Int) -> Unit,
    onNegativeColorChanged: (Int) -> Unit,
    onTextColorChanged: (Int) -> Unit,
    onTextColorReset: () -> Unit,
    onLegendColorChanged: (Int) -> Unit,
    onLegendColorReset: () -> Unit
) {
    val config = ChartColorCardConfig(
        title = stringResource(R.string.settings_chart_macd),
        icon = Icons.Default.BarChart,
        sections = listOf(
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_line_color),
                items = listOf(
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_macd_line),
                        getCurrentColor = { it.lineColor1 },
                        onColorSelected = onLineColor1Changed
                    ),
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_signal_line),
                        getCurrentColor = { it.lineColor2 },
                        onColorSelected = onLineColor2Changed
                    )
                )
            ),
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_histogram),
                items = listOf(
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_positive),
                        getCurrentColor = { it.positiveColor },
                        onColorSelected = onPositiveColorChanged
                    ),
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_negative),
                        getCurrentColor = { it.negativeColor },
                        onColorSelected = onNegativeColorChanged
                    )
                )
            ),
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_text_legend),
                optionalItems = listOf(
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_axis_color),
                        getCurrentColor = { it.textColor },
                        onColorSelected = onTextColorChanged,
                        onReset = onTextColorReset
                    ),
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_legend_color),
                        getCurrentColor = { it.legendColor },
                        onColorSelected = onLegendColorChanged,
                        onReset = onLegendColorReset
                    )
                )
            )
        )
    )

    ChartColorCard(config = config, colors = colors)
}

/**
 * 증시 자금 동향 차트 색상 카드
 */
@Composable
fun MarketDepositColorCard(
    colors: SingleChartColorSettings,
    onLineColor1Changed: (Int) -> Unit,
    onLineColor2Changed: (Int) -> Unit,
    onTextColorChanged: (Int) -> Unit,
    onTextColorReset: () -> Unit,
    onLegendColorChanged: (Int) -> Unit,
    onLegendColorReset: () -> Unit
) {
    val config = ChartColorCardConfig(
        title = stringResource(R.string.settings_chart_deposit),
        icon = Icons.Default.TrendingUp,
        sections = listOf(
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_line_color),
                items = listOf(
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_customer_deposit),
                        getCurrentColor = { it.lineColor1 },
                        onColorSelected = onLineColor1Changed
                    ),
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_credit),
                        getCurrentColor = { it.lineColor2 },
                        onColorSelected = onLineColor2Changed
                    )
                )
            ),
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_text_legend),
                optionalItems = listOf(
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_axis_color),
                        getCurrentColor = { it.textColor },
                        onColorSelected = onTextColorChanged,
                        onReset = onTextColorReset
                    ),
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_legend_color),
                        getCurrentColor = { it.legendColor },
                        onColorSelected = onLegendColorChanged,
                        onReset = onLegendColorReset
                    )
                )
            )
        )
    )

    ChartColorCard(config = config, colors = colors)
}

/**
 * 공포 탐욕 지수 차트 색상 카드
 */
@Composable
fun FearGreedColorCard(
    colors: SingleChartColorSettings,
    onLineColor1Changed: (Int) -> Unit,
    onLineColor2Changed: (Int) -> Unit,
    onTextColorChanged: (Int) -> Unit,
    onTextColorReset: () -> Unit,
    onLegendColorChanged: (Int) -> Unit,
    onLegendColorReset: () -> Unit
) {
    val config = ChartColorCardConfig(
        title = stringResource(R.string.settings_chart_feargreed),
        icon = Icons.Default.Psychology,
        sections = listOf(
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_line_color),
                items = listOf(
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_fg_line),
                        getCurrentColor = { it.lineColor1 },
                        onColorSelected = onLineColor1Changed
                    ),
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_index_line),
                        getCurrentColor = { it.lineColor2 },
                        onColorSelected = onLineColor2Changed
                    )
                )
            ),
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_text_legend),
                optionalItems = listOf(
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_axis_color),
                        getCurrentColor = { it.textColor },
                        onColorSelected = onTextColorChanged,
                        onReset = onTextColorReset
                    ),
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_legend_color),
                        getCurrentColor = { it.legendColor },
                        onColorSelected = onLegendColorChanged,
                        onReset = onLegendColorReset
                    )
                )
            )
        )
    )

    ChartColorCard(config = config, colors = colors)
}

/**
 * 차트 색상 초기화 카드
 */
@Composable
fun ResetChartColorsCard(
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
                    Icons.Default.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(stringResource(R.string.settings_chart_reset), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_chart_reset_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_chart_reset_all))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text(stringResource(R.string.settings_chart_reset)) },
            text = { Text(stringResource(R.string.settings_chart_reset_confirm)) },
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
                    Text(stringResource(R.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
