package com.etfmonitor.ui.screens.settings.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 기간 옵션 데이터 클래스
 */
data class PeriodOption(
    val days: Int,
    val label: String,
    val description: String
)

/**
 * 기간 설정 카드 구성 데이터 클래스
 */
data class PeriodCardConfig(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val dialogTitle: String,
    val recommendationText: String
)

/**
 * 일 수를 표시 텍스트로 변환
 */
private fun daysToDisplayText(days: Int): String = when (days) {
    180 -> "6개월"
    365 -> "12개월"
    540 -> "18개월"
    730 -> "24개월"
    else -> "${days}일"
}

/**
 * 공통 기간 설정 카드 컴포넌트
 */
@Composable
fun PeriodCard(
    config: PeriodCardConfig,
    currentDays: Int,
    onDaysChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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

            // 설명
            Text(
                config.description,
                style = MaterialTheme.typography.bodyMedium
            )

            // 현재 설정 표시 및 변경 버튼
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
                        daysToDisplayText(currentDays),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text("변경")
                }
            }

            // 권장 사항 표시
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    config.recommendationText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        PeriodSelectionDialog(
            title = config.dialogTitle,
            currentDays = currentDays,
            onDismiss = { showDialog = false },
            onConfirm = { days ->
                onDaysChange(days)
                showDialog = false
            }
        )
    }
}

/**
 * Fear & Greed Index 수집 기간 카드
 */
@Composable
fun FearGreedPeriodCard(
    currentDays: Int,
    onDaysChange: (Int) -> Unit
) {
    val config = PeriodCardConfig(
        title = "Fear & Greed Index 수집 기간",
        icon = Icons.Default.BarChart,
        description = "Fear & Greed Index 데이터 초기화 시 수집할 기간",
        dialogTitle = "Fear & Greed Index 수집 기간 설정",
        recommendationText = "권장: 12개월 (약 365일, 약 1-2분 소요)"
    )

    PeriodCard(
        config = config,
        currentDays = currentDays,
        onDaysChange = onDaysChange
    )
}

/**
 * 과매수/과매도 수집 기간 카드
 */
@Composable
fun MarketOscillatorPeriodCard(
    currentDays: Int,
    onDaysChange: (Int) -> Unit
) {
    val config = PeriodCardConfig(
        title = "과매수/과매도 수집 기간",
        icon = Icons.Default.Leaderboard,
        description = "과매수/과매도 데이터 초기화 시 수집할 기간",
        dialogTitle = "과매수/과매도 수집 기간 설정",
        recommendationText = "권장: 12개월 (약 365일, 약 1-2분 소요)"
    )

    PeriodCard(
        config = config,
        currentDays = currentDays,
        onDaysChange = onDaysChange
    )
}

/**
 * 기간 선택 다이얼로그
 */
@Composable
fun PeriodSelectionDialog(
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
