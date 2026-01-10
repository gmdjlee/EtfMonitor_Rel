# EtfMonitor UI 최적화 분석 보고서

> 분석 일자: 2026-01-10
> 분석 대상: all-ui-code.kt (766KB)
> **수정 완료: 2026-01-10 (커밋 92a4a9d)**

---

## 목차
1. [수정 완료 항목](#1-수정-완료-항목)
2. [LazyColumn Key 이슈](#2-lazycolumn-key-이슈)
3. [리컴포지션 최적화](#3-리컴포지션-최적화)
4. [중복 코드 패턴 통합 제안](#4-중복-코드-패턴-통합-제안)
5. [남은 작업 (P2)](#5-남은-작업-p2)

---

## 1. 수정 완료 항목

### ✅ P0 수정 완료 (즉시 효과)

| 항목 | 파일 | 수정 내용 | 상태 |
|------|------|-----------|------|
| LazyColumn key | `BackupScreen.kt:440` | `items(localBackups, key = { it.id })` 추가 | ✅ 완료 |
| LazyColumn key | `BackupScreen.kt:521` | `items(localBackups, key = { it.id })` 추가 | ✅ 완료 |
| LazyRow key | `ColorPickerComponents.kt:338` | `items(count, key = {...})` 추가 | ✅ 완료 |
| derivedStateOf | `NewAIAnalysisScreen.kt:73-90` | showFab에 `remember + derivedStateOf` 적용 | ✅ 완료 |
| derivedStateOf | `OscillatorScreen.kt:71-78` | showFab에 `remember + derivedStateOf` 적용 | ✅ 완료 |

### ✅ P1 수정 완료 (성능 개선)

| 항목 | 파일 | 수정 내용 | 상태 |
|------|------|-----------|------|
| remember 캐싱 | `EtfDetailScreen.kt:285` | WeightInfo의 String.format 캐싱 | ✅ 완료 |
| remember 캐싱 | `EtfDetailScreen.kt:305` | ChangeInfo의 String.format 캐싱 | ✅ 완료 |

---

## 2. LazyColumn Key 이슈

### ✅ 수정 완료

```kotlin
// BackupScreen.kt - BackupContentWithFab (line 440)
items(localBackups, key = { it.id }) { backup ->
    BackupCard(...)
}

// BackupScreen.kt - BackupContent (line 521)
items(localBackups, key = { it.id }) { backup ->
    BackupCard(...)
}

// ColorPickerComponents.kt (line 338)
items(
    count = chartDefaultColors.size,
    key = { index -> chartDefaultColors[index].toArgb() }
) { index ->
    ...
}
```

### ✅ 이미 Key가 올바르게 설정된 곳

```kotlin
// UnifiedStockSearchField.kt
items(searchResults, key = { it.ticker }) { result -> ... }

// StockSearchHistoryDialog
items(searchHistory, key = { it.id }) { history -> ... }

// NewAIAnalysisScreen.kt
items(chatSessions, key = { it.id }) { session -> ... }

// EtfDetailScreen.kt
items(items, key = { it.stockTicker }) { item -> ... }

// EtfHubScreen.kt
itemsIndexed(s.etfs, key = { _, etf -> etf.ticker }) { index, etf -> ... }

// OscillatorScreen.kt
items(suggestions, key = { it.ticker }) { stock -> ... }
```

---

## 3. 리컴포지션 최적화

### ✅ 3.1 derivedStateOf 적용 완료

**NewAIAnalysisScreen.kt** (수정 완료):
```kotlin
// ✅ 수정됨: remember + derivedStateOf 적용
val showFab by remember(
    quickChartAnalysisEnabled,
    onNavigateToOscillator,
    selectedTab,
    selectedStock,
    stockIndicatorCorrelationResult,
    currentSession
) {
    derivedStateOf {
        quickChartAnalysisEnabled &&
                onNavigateToOscillator != null &&
                selectedTab == AnalysisTab.STOCK_INDICATOR &&
                selectedStock != null &&
                stockIndicatorCorrelationResult?.correlationResult != null &&
                currentSession == null
    }
}
```

**OscillatorScreen.kt** (수정 완료):
```kotlin
// ✅ 수정됨: remember + derivedStateOf 적용
val showFab by remember(quickChartAnalysisEnabled, onNavigateToStatistics, state) {
    derivedStateOf {
        quickChartAnalysisEnabled &&
                onNavigateToStatistics != null &&
                state is OscillatorState.Success
    }
}
```

### ✅ 3.2 String.format 캐싱 완료

**EtfDetailScreen.kt** (수정 완료):
```kotlin
// WeightInfo - 수정됨
@Composable
private fun WeightInfo(label: String, weight: Float, modifier: Modifier = Modifier) {
    val formattedWeight = remember(weight) { String.format("%.2f%%", weight) }
    // ...
    Text(formattedWeight, ...)
}

// ChangeInfo - 수정됨
@Composable
private fun ChangeInfo(change: Float, modifier: Modifier = Modifier) {
    val formattedChange = remember(change) { String.format("%+.2f%%", change) }
    // ...
    Text(formattedChange, ...)
}
```

### 🟡 3.3 추가 개선 가능 (P2)

**과도한 collectAsState 호출** - NewAIAnalysisScreen.kt (25개):
```kotlin
// 권장: 관련 상태를 그룹화하여 하위 컴포저블로 분리
@Composable
private fun CorrelationAnalysisSection(viewModel: NewAIAnalysisViewModel) {
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    // 이 섹션에서만 필요한 상태만 수집
}
```

**람다 remember 캐싱** - MainBottomNavigationBar.kt:
```kotlin
// 권장: remember로 람다 캐싱
items.forEach { item ->
    val onClick = remember(item) { { onNavigate(item) } }
    MainNavItemButton(item = item, onClick = onClick)
}
```

---

## 4. 중복 코드 패턴 통합 제안

### 🟡 4.1 BackupScreen 중복 코드 (P2)

**BackupTabContent**와 **BackupScreen**이 거의 동일:
- 동일한 state 수집 로직
- 동일한 launcher 설정
- 동일한 LaunchedEffect

**통합 제안:**
```kotlin
@Composable
private fun BackupCore(
    viewModel: BackupViewModel,
    snackbarHostState: SnackbarHostState,
    content: @Composable (...) -> Unit
) {
    // 공통 로직
}
```

### 🟡 4.2 검색 필드 중복 (P2)

**OscillatorScreen** → **UnifiedStockSearchField** 통합 권장
- 예상 코드 감소: ~120줄

### 🟡 4.3 차트 색상 카드 패턴 (P2)

**SettingsScreen.kt**의 여러 ColorCard 통합:
```kotlin
@Composable
fun ChartColorSettingsCard(
    title: String,
    lineColor1: Int,
    lineColor2: Int,
    onLineColor1Change: (Int) -> Unit,
    onLineColor2Change: (Int) -> Unit,
    additionalContent: @Composable ColumnScope.() -> Unit = {}
)
```

---

## 5. 남은 작업 (P2)

| 항목 | 위치 | 예상 효과 | 우선순위 |
|------|------|-----------|----------|
| BackupScreen 중복 제거 | BackupScreen.kt | 코드 400줄 감소 | P2 |
| OscillatorScreen 검색 통합 | OscillatorScreen.kt | 코드 120줄 감소 | P2 |
| 차트 색상 카드 통합 | SettingsScreen.kt | 유지보수성 향상 | P2 |
| ViewModel 상태 그룹화 | NewAIAnalysisScreen | 리컴포지션 범위 축소 | P2 |
| 람다 remember 캐싱 | MainBottomNavigationBar | 미세 성능 개선 | P2 |

---

## 요약

| 카테고리 | 발견 건수 | 완료 | 남음 |
|----------|-----------|------|------|
| LazyColumn key 누락 | 3건 | ✅ 3 | 0 |
| derivedStateOf 필요 | 4건 | ✅ 2 | 2 |
| 중복 코드 패턴 | 4건 | 0 | 4 |
| remember 캐싱 필요 | 5건 | ✅ 2 | 3 |
| **합계** | **16건** | **7** | **9** |

### 수정 커밋 정보

```
커밋: 92a4a9d
브랜치: claude/optimize-kotlin-ui-L0Q66
수정 파일:
  - BackupScreen.kt (2 changes)
  - ColorPickerComponents.kt (1 change)
  - NewAIAnalysisScreen.kt (1 change)
  - OscillatorScreen.kt (1 change)
  - EtfDetailScreen.kt (2 changes)
```

---

*보고서 생성: Claude Code*
*최종 수정: 2026-01-10*
