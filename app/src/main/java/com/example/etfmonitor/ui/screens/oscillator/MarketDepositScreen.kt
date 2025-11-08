package com.etfmonitor.ui.screens.oscillator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketDepositScreen(
    onNavigateBack: () -> Unit,
    viewModel: MarketDepositViewModel = viewModel(factory = MarketDepositViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("증시 자금 동향") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
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
            // 분석 버튼 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "고객예탁금 & 신용잔고",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        "네이버 증권에서 최신 증시 자금 동향 데이터를 수집합니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    Button(
                        onClick = { viewModel.analyzeMarket() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state !is MarketDepositState.Loading
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("자금 동향 분석")
                    }
                }
            }

            // 상태별 UI
            when (val currentState = state) {
                is MarketDepositState.Loading -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("데이터 수집 중...")
                            }
                        }
                    }
                }

                is MarketDepositState.Success -> {
                    // 시장 분석 카드
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "시장 분석",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                currentState.analysis,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    // 최신 데이터 요약
                    if (currentState.data.dates.isNotEmpty()) {
                        val lastIdx = currentState.data.dates.size - 1

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "최신 데이터 (${currentState.data.dates[lastIdx]})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                HorizontalDivider()

                                // 고객예탁금
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "고객예탁금",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            String.format("%.0f억원", currentState.data.depositAmounts[lastIdx]),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    val depositChange = currentState.data.depositChanges[lastIdx]
                                    Text(
                                        String.format("%+.0f억", depositChange),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (depositChange > 0) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                HorizontalDivider()

                                // 신용잔고
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "신용잔고",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            String.format("%.0f억원", currentState.data.creditAmounts[lastIdx]),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    val creditChange = currentState.data.creditChanges[lastIdx]
                                    Text(
                                        String.format("%+.0f억", creditChange),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (creditChange > 0) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // 데이터 상세
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "데이터 상세",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "데이터 포인트: ${currentState.data.dates.size}개",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "날짜 범위: ${currentState.data.dates.first()} ~ ${currentState.data.dates.last()}",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                HorizontalDivider()

                                Text(
                                    "최근 5일 추이",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )

                                val recentCount = minOf(5, currentState.data.dates.size)
                                val startIdx = maxOf(0, currentState.data.dates.size - recentCount)

                                for (i in (currentState.data.dates.size - 1) downTo startIdx) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            currentState.data.dates[i],
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            String.format("%.0f억", currentState.data.depositAmounts[i]),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            String.format("%.0f억", currentState.data.creditAmounts[i]),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is MarketDepositState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                currentState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                is MarketDepositState.Idle -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "버튼을 눌러 증시 자금 동향을 확인하세요",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
