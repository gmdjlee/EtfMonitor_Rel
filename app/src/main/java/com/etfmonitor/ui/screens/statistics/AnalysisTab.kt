package com.etfmonitor.ui.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.etfmonitor.database.StockSearchResult
import com.etfmonitor.database.entities.HoldingStatus
import com.etfmonitor.database.entities.SearchHistory
import com.etfmonitor.database.entities.StockAnalysisResult
import com.etfmonitor.database.entities.StockEtfDetail
import com.etfmonitor.ui.theme.*
import com.etfmonitor.ui.utils.AmountFormatter

/**
 * Statistics Screen - Analysis Tab Components
 * Contains StockAnalysisTab and related components for stock analysis
 */

@Composable
internal fun StockAnalysisTab(
    searchQuery: String,
    searchResults: List<StockSearchResult>,
    analysisResult: StockAnalysisResult?,
    isAnalyzing: Boolean,
    searchHistory: List<SearchHistory> = emptyList(),
    onSearchQueryChange: (String) -> Unit,
    onSearchAndAnalyze: (String) -> Unit,
    onStockSelect: (String) -> Unit,
    onClearAnalysis: () -> Unit,
    onStockClick: (String) -> Unit
) {
    var textFieldValue by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        // 검색 입력 - Box로 감싸서 드롭다운 오버레이
        Box(modifier = Modifier.fillMaxWidth()) {
            // 검색 필드 - EtfListScreen 스타일
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onSearchQueryChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "종목명 또는 티커 검색...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // History 버튼
                        if (searchHistory.isNotEmpty() && textFieldValue.isEmpty()) {
                            IconButton(onClick = { showHistoryDialog = true }) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = "검색 히스토리",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Clear 버튼
                        if (textFieldValue.isNotEmpty()) {
                            IconButton(onClick = {
                                textFieldValue = ""
                                onSearchQueryChange("")
                            }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "지우기",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.extendedShapes.searchBar,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
            )

            // 자동완성 드롭다운 - 오버레이
            if (searchResults.isNotEmpty() && textFieldValue.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp)
                        .heightIn(max = 300.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.extendedShapes.cardLarge
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(searchResults) { result ->
                            ListItem(
                                headlineContent = { Text(result.stockName) },
                                supportingContent = {
                                    Text(
                                        result.stockTicker,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                modifier = Modifier.clickable {
                                    textFieldValue = result.stockName
                                    onSearchQueryChange("")
                                    onStockSelect(result.stockTicker)
                                }
                            )
                            if (result != searchResults.last()) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        // 분석 중 표시
        if (isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 분석 결과 표시
        analysisResult?.let { result ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                item {
                    StockAnalysisSummaryCard(result, onClearAnalysis)
                }

                item {
                    StockAnalysisStatisticsCard(result)
                }

                item {
                    StockAnalysisDetailsCard(result, onStockClick)
                }
            }
        }

        // 초기 안내 메시지
        if (!isAnalyzing && analysisResult == null && searchQuery.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.extendedShapes.cardLarge
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.extraLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "종목을 검색하여 ETF 편입 현황을 분석하세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // 검색 히스토리 다이얼로그
    if (showHistoryDialog && searchHistory.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("최근 검색")
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchHistory) { history ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(history.name) },
                                    supportingContent = {
                                        Text(
                                            "${history.ticker} • ${history.market}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        textFieldValue = history.name
                                        onStockSelect(history.ticker)
                                        showHistoryDialog = false
                                    }
                                )
                                if (history != searchHistory.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }
}

@Composable
internal fun StockAnalysisSummaryCard(
    result: StockAnalysisResult,
    onClearAnalysis: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        result.stockName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        result.stockTicker,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onClearAnalysis) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "포함 ETF",
                    value = "${result.currentEtfCount}개"
                )
                SummaryItem(
                    label = "평가금액",
                    value = AmountFormatter.format(result.totalAmount, showUnit = true)
                )
                SummaryItem(
                    label = "평균 비중",
                    value = String.format("%.2f%%", result.avgWeight)
                )
            }
        }
    }
}

@Composable
internal fun StockAnalysisStatisticsCard(
    result: StockAnalysisResult
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                "ETF 편입 변동",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "신규 편입",
                    value = "${result.newIncludedCount}",
                    color = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    label = "비중 증가",
                    value = "${result.increasedCount}",
                    color = MaterialTheme.colorScheme.tertiary
                )
                StatItem(
                    label = "비중 감소",
                    value = "${result.decreasedCount}",
                    color = MaterialTheme.colorScheme.error
                )
                StatItem(
                    label = "제외",
                    value = "${result.removedCount}",
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (result.previousEtfCount > 0) {
                val change = result.currentEtfCount - result.previousEtfCount
                Text(
                    "이전 대비: ${if (change >= 0) "+" else ""}$change ETF",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        change > 0 -> MaterialTheme.colorScheme.tertiary
                        change < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
internal fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
    }
}

@Composable
internal fun StockAnalysisDetailsCard(
    result: StockAnalysisResult,
    onStockClick: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                "ETF별 상세 현황 (${result.etfDetails.size}개)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            result.etfDetails.forEach { detail ->
                StockAnalysisDetailItem(detail, onStockClick)
            }
        }
    }
}

@Composable
internal fun StockAnalysisDetailItem(
    detail: StockEtfDetail,
    onStockClick: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStockClick(detail.etfTicker) },
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(MaterialTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        detail.etfName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        detail.etfTicker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(detail.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                when (detail.status) {
                    HoldingStatus.NEW -> {
                        WeightInfo("비중", detail.currentWeight, Modifier.weight(1f))
                    }
                    HoldingStatus.REMOVED -> {
                        WeightInfo("이전", detail.previousWeight, Modifier.weight(1f))
                    }
                    else -> {
                        WeightInfo("이전", detail.previousWeight, Modifier.weight(1f))
                        WeightInfo("현재", detail.currentWeight, Modifier.weight(1f))
                        ChangeInfo(detail.change, Modifier.weight(1f))
                    }
                }
            }

            if (detail.amount > 0) {
                Text(
                    "평가금액: ${AmountFormatter.format(detail.amount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
