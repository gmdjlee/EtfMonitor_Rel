package com.etfmonitor.ui.screens.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack  // ✅ 추가
import androidx.compose.material.icons.automirrored.filled.TrendingUp  // ✅ 추가
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.database.entities.Etf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtfListScreen(
    onNavigateBack: () -> Unit,
    onEtfClick: (String) -> Unit,
    viewModel: EtfListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ETF 목록") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")  // ✅ 변경
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("ETF 검색...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearSearch) {
                            Icon(Icons.Default.Clear, "지우기")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            // Content
            when (val s = state) {
                is ListState.Loading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ListState.Success -> {
                    EtfList(etfs = s.etfs, onEtfClick = onEtfClick)
                }
                is ListState.Empty -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("검색 결과가 없습니다")
                        }
                    }
                }
                is ListState.Error -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun EtfList(etfs: List<Etf>, onEtfClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(etfs, key = { it.ticker }) { etf ->
            EtfCard(etf = etf, onClick = { onEtfClick(etf.ticker) })
        }
    }
}

@Composable
private fun EtfCard(etf: Etf, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(48.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,  // ✅ 변경
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(etf.name, style = MaterialTheme.typography.titleMedium)
                Text(etf.ticker, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}