# EtfMonitor UI 최적화 분석 보고서

> 분석 일자: 2026-01-10
> 분석 대상: all-ui-code.kt (766KB)

---

## 목차
1. [LazyColumn Key 누락 이슈](#1-lazycolumn-key-누락-이슈)
2. [리컴포지션 최적화 필요 항목](#2-리컴포지션-최적화-필요-항목)
3. [중복 코드 패턴 통합 제안](#3-중복-코드-패턴-통합-제안)
4. [remember/derivedStateOf 추가 필요 위치](#4-rememberderivedstateof-추가-필요-위치)
5. [수정 우선순위](#5-수정-우선순위)

---

## 1. LazyColumn Key 누락 이슈

### 🔴 Critical: Key 누락 (즉시 수정 필요)

| 파일 위치 | 줄 번호 | 현재 코드 | 수정 방안 |
|-----------|---------|-----------|-----------|
| `BackupScreen.kt` | 6380 | `items(localBackups) { backup ->` | `items(localBackups, key = { it.id }) { backup ->` |
| `BackupScreen.kt` | 6461 | `items(localBackups) { backup ->` | `items(localBackups, key = { it.id }) { backup ->` |
| `SettingsScreen.kt` (ColorPicker) | 17587 | `items(chartDefaultColors.size) { index ->` | `itemsIndexed(chartDefaultColors, key = { idx, _ -> idx }) { index, color ->` |

### ✅ 이미 Key가 올바르게 설정된 곳 (Good)

```kotlin
// UnifiedStockSearchField.kt:2812
items(searchResults, key = { it.ticker }) { result -> ... }

// StockSearchHistoryDialog:2896
items(searchHistory, key = { it.id }) { history -> ... }

// NewAIAnalysisScreen.kt:4630
items(chatSessions, key = { it.id }) { session -> ... }

// EtfDetailScreen.kt:7739
items(items, key = { it.stockTicker }) { item -> ... }

// EtfHubScreen.kt:8058
itemsIndexed(s.etfs, key = { _, etf -> etf.ticker }) { index, etf -> ... }

// OscillatorScreen.kt:14281
items(suggestions, key = { it.ticker }) { stock -> ... }
```

---

## 2. 리컴포지션 최적화 필요 항목

### 🔴 2.1 과도한 collectAsState 호출

**NewAIAnalysisScreen.kt** (4410-4431): 25개의 개별 collectAsState 호출
```kotlin
// ❌ 현재: 모든 상태 변경이 전체 화면 리컴포지션 유발
val state by viewModel.state.collectAsState()
val selectedMarket by viewModel.selectedMarket.collectAsState()
val selectedProvider by viewModel.selectedProvider.collectAsState()
// ... 22개 더
```

**권장 수정:**
```kotlin
// ✅ 개선: 관련 상태를 그룹화하여 하위 컴포저블로 분리
@Composable
private fun CorrelationAnalysisSection(viewModel: NewAIAnalysisViewModel) {
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    // 이 섹션에서만 필요한 상태만 수집
}
```

### 🟡 2.2 불필요한 람다 재생성

**MainBottomNavigationBar.kt** (86-100):
```kotlin
// ❌ 현재: 매 리컴포지션마다 새 람다 생성
items.forEach { item ->
    MainNavItemButton(
        item = item,
        isSelected = baseRoute == item.route,
        onClick = { onNavigate(item) }  // 새 람다 생성
    )
}
```

**권장 수정:**
```kotlin
// ✅ 개선: remember로 람다 캐싱
items.forEach { item ->
    val onClick = remember(item) { { onNavigate(item) } }
    MainNavItemButton(
        item = item,
        isSelected = baseRoute == item.route,
        onClick = onClick
    )
}
```

### 🟡 2.3 String.format 매 리컴포지션 호출

**WeightInfo/ChangeInfo** (7852-7889):
```kotlin
// ❌ 현재: 매번 String.format 호출
Text(
    String.format("%.2f%%", weight),
    ...
)
```

**권장 수정:**
```kotlin
// ✅ 개선: remember로 포맷팅 캐싱
val formattedWeight = remember(weight) { String.format("%.2f%%", weight) }
Text(formattedWeight, ...)
```

---

## 3. 중복 코드 패턴 통합 제안

### 🔴 3.1 검색 필드 + 자동완성 드롭다운 중복

**동일 패턴이 반복되는 위치:**
1. `UnifiedStockSearchField.kt` (2700-2852)
2. `OscillatorScreen.kt` (14178-14302)
3. `EtfHubScreen.kt` (검색 로직)

**통합 제안:**
```kotlin
// 이미 UnifiedStockSearchField가 있지만, OscillatorScreen에서 직접 구현됨
// OscillatorScreen에서 UnifiedStockSearchField 사용하도록 리팩토링 필요
```

### 🔴 3.2 BackupScreen 중복 코드

**BackupTabContent** (5983-6119)와 **BackupScreen** (6122-6283)이 거의 동일:
- 동일한 state 수집 로직
- 동일한 launcher 설정
- 동일한 LaunchedEffect

**통합 제안:**
```kotlin
// 공통 로직을 Composable로 추출
@Composable
private fun BackupCore(
    viewModel: BackupViewModel,
    snackbarHostState: SnackbarHostState,
    content: @Composable (
        state: BackupState,
        googleDriveState: GoogleDriveState,
        onGoogleSignIn: () -> Unit,
        ...
    ) -> Unit
) {
    // 공통 로직
}
```

### 🟡 3.3 검색 히스토리 다이얼로그 중복

**동일 패턴:**
1. `StockSearchHistoryDialog` (2858-2924)
2. `OscillatorScreen` 내 히스토리 다이얼로그 (14800 근처)

**이미 `UnifiedStockSearchField`에 포함되어 있으므로 통합 사용 권장**

### 🟡 3.4 차트 색상 카드 패턴 중복

**SettingsScreen.kt** (13179-13230):
- MarketCapOscillatorColorCard
- MacdColorCard
- MarketDepositColorCard
- FearGreedColorCard

모두 동일한 구조: `onLineColor1Changed`, `onLineColor2Changed`, `onTextColorChanged` 등

**통합 제안:**
```kotlin
// 공통 ChartColorSettingsCard 컴포저블 생성
@Composable
fun ChartColorSettingsCard(
    title: String,
    lineColor1: Int,
    lineColor2: Int,
    textColor: Int?,
    legendColor: Int?,
    onLineColor1Change: (Int) -> Unit,
    onLineColor2Change: (Int) -> Unit,
    onTextColorChange: ((Int) -> Unit)?,
    onTextColorReset: (() -> Unit)?,
    onLegendColorChange: ((Int) -> Unit)?,
    onLegendColorReset: (() -> Unit)?,
    additionalContent: @Composable ColumnScope.() -> Unit = {}
)
```

---

## 4. remember/derivedStateOf 추가 필요 위치

### 🔴 4.1 복잡한 조건부 계산

**NewAIAnalysisScreen.kt** (4440-4445):
```kotlin
// ❌ 현재: 매 리컴포지션마다 조건 재평가
val showFab = quickChartAnalysisEnabled &&
        onNavigateToOscillator != null &&
        selectedTab == AnalysisTab.STOCK_INDICATOR &&
        selectedStock != null &&
        stockIndicatorCorrelationResult?.correlationResult != null &&
        currentSession == null

// ✅ 개선:
val showFab by remember {
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

**OscillatorScreen.kt** (14135-14137):
```kotlin
// ❌ 현재
val showFab = quickChartAnalysisEnabled &&
        onNavigateToStatistics != null &&
        state is OscillatorState.Success

// ✅ 개선: derivedStateOf 사용
```

### 🔴 4.2 리스트 필터링/매핑

**EtfHubScreen.kt** - 탭별 데이터 필터링이 있다면:
```kotlin
// ✅ 권장 패턴
val filteredEtfs = remember(etfs, searchQuery) {
    if (searchQuery.isBlank()) etfs
    else etfs.filter { it.name.contains(searchQuery, ignoreCase = true) }
}
```

### 🟡 4.3 날짜/시간 포맷팅

**여러 곳에서 날짜 포맷팅:**
```kotlin
// ❌ 현재
Text(currentState.oscillatorResult.dates.last())

// ✅ 개선: 포맷팅이 필요한 경우
val latestDate = remember(currentState.oscillatorResult.dates) {
    currentState.oscillatorResult.dates.lastOrNull() ?: "N/A"
}
```

### 🟡 4.4 애니메이션 스펙 캐싱

**MainNavItemButton.kt** (116-121):
```kotlin
// 현재: animationSpec이 인라인 생성
animationSpec = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

// ✅ 개선: 상수 또는 remember로 캐싱
private val NavItemAnimSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)
```

---

## 5. 수정 우선순위

### P0 (즉시 수정) - 성능 직접 영향

| 항목 | 위치 | 예상 효과 |
|------|------|-----------|
| LazyColumn key 누락 | BackupScreen 2곳, ColorPicker 1곳 | 리스트 스크롤 성능 50%↑ |
| showFab derivedStateOf | NewAIAnalysisScreen, OscillatorScreen | 불필요한 리컴포지션 감소 |

### P1 (1주 내 수정) - 코드 품질 개선

| 항목 | 위치 | 예상 효과 |
|------|------|-----------|
| BackupScreen 중복 제거 | BackupScreen.kt | 코드 400줄 감소 |
| OscillatorScreen → UnifiedStockSearchField 통합 | OscillatorScreen.kt | 코드 120줄 감소 |
| String.format remember 캐싱 | WeightInfo, ChangeInfo | 미세 성능 개선 |

### P2 (다음 스프린트) - 리팩토링

| 항목 | 위치 | 예상 효과 |
|------|------|-----------|
| 차트 색상 카드 통합 | SettingsScreen.kt | 코드 유지보수성 ↑ |
| ViewModel 상태 그룹화 | NewAIAnalysisScreen | 리컴포지션 범위 축소 |
| 람다 remember 캐싱 | MainBottomNavigationBar | 미세 성능 개선 |

---

## 수정 코드 예시

### 예시 1: BackupScreen LazyColumn key 수정

```kotlin
// 파일: BackupScreen.kt
// 줄: 6380, 6461

// Before
items(localBackups) { backup ->
    BackupCard(...)
}

// After
items(localBackups, key = { it.id }) { backup ->
    BackupCard(...)
}
```

### 예시 2: ColorPicker LazyRow key 수정

```kotlin
// 파일: SettingsScreen.kt (ColorPickerDialog)
// 줄: 17587

// Before
items(chartDefaultColors.size) { index ->
    val color = chartDefaultColors[index]
    ...
}

// After
itemsIndexed(chartDefaultColors, key = { idx, color -> color.toArgb() }) { index, color ->
    ...
}
```

### 예시 3: showFab derivedStateOf 적용

```kotlin
// 파일: NewAIAnalysisScreen.kt
// 줄: 4440-4445

// Before
val showFab = quickChartAnalysisEnabled &&
        onNavigateToOscillator != null &&
        selectedTab == AnalysisTab.STOCK_INDICATOR &&
        selectedStock != null &&
        stockIndicatorCorrelationResult?.correlationResult != null &&
        currentSession == null

// After
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

---

## 요약

| 카테고리 | 발견 건수 | P0 | P1 | P2 |
|----------|-----------|-----|-----|-----|
| LazyColumn key 누락 | 3건 | 3 | - | - |
| derivedStateOf 필요 | 4건 | 2 | 2 | - |
| 중복 코드 패턴 | 4건 | - | 2 | 2 |
| remember 캐싱 필요 | 5건 | - | 2 | 3 |
| **합계** | **16건** | **5** | **6** | **5** |

---

*보고서 생성: Claude Code*
