package com.etfmonitor.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.ui.theme.*

/**
 * 종목 검색 결과 데이터
 */
data class StockSearchItem(
    val ticker: String,
    val name: String,
    val market: String = ""
)

/**
 * 통합 종목 검색 텍스트 필드
 *
 * ETF 통계탭 분석, 종목 메뉴, AI 분석 종목-지표 탭에서 사용하는
 * 통일된 디자인의 종목 검색 필드입니다.
 *
 * @param searchQuery 현재 검색어
 * @param onSearchQueryChange 검색어 변경 콜백
 * @param searchResults 검색 결과 목록
 * @param searchHistory 검색 히스토리 목록
 * @param isSearching 검색 중 여부 (로딩 표시)
 * @param placeholder 플레이스홀더 텍스트
 * @param onSelectStock 종목 선택 콜백 (ticker, name)
 * @param onSelectFromHistory 히스토리에서 선택 콜백 (기본: onSelectStock과 동일)
 * @param modifier Modifier
 */
@Composable
fun UnifiedStockSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<StockSearchItem>,
    searchHistory: List<SearchHistory>,
    isSearching: Boolean = false,
    placeholder: String = "종목명 또는 티커 검색...",
    onSelectStock: (ticker: String, name: String) -> Unit,
    onSelectFromHistory: ((ticker: String, name: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 외부 searchQuery가 변경되면 내부 상태도 업데이트
    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty() && textFieldValue.isNotEmpty()) {
            // 외부에서 클리어된 경우
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 검색 필드
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                onSearchQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search_button),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    items(searchResults, key = { it.ticker }) { result ->
                        ListItem(
                            headlineContent = { Text(result.name) },
                            supportingContent = {
                                Text(
                                    if (result.market.isNotEmpty()) {
                                        "${result.ticker} • ${result.market}"
                                    } else {
                                        result.ticker
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            modifier = Modifier.clickable {
                                textFieldValue = result.name
                                onSearchQueryChange("")
                                onSelectStock(result.ticker, result.name)
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

    // 검색 히스토리 다이얼로그
    if (showHistoryDialog && searchHistory.isNotEmpty()) {
        StockSearchHistoryDialog(
            searchHistory = searchHistory,
            onDismiss = { showHistoryDialog = false },
            onSelectStock = { ticker, name ->
                textFieldValue = name
                val selectCallback = onSelectFromHistory ?: onSelectStock
                selectCallback(ticker, name)
                showHistoryDialog = false
            }
        )
    }
}

/**
 * 검색 히스토리 다이얼로그
 */
@Composable
fun StockSearchHistoryDialog(
    searchHistory: List<SearchHistory>,
    onDismiss: () -> Unit,
    onSelectStock: (ticker: String, name: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                if (searchHistory.isEmpty()) {
                    Text(
                        "검색 기록이 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchHistory, key = { it.id }) { history ->
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
                                        onSelectStock(history.ticker, history.name)
                                    }
                                )
                                if (history != searchHistory.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}
