# Chart Range Selection & Historical Data Loading Plan

**Feature**: 차트 X축 범위 조절 및 DB 히스토리 데이터 로딩
**Scope**: Medium (4 phases, 8-12 hours estimated)
**Created**: 2025-12-26
**Status**: 🔵 Planning

---

## 1. Overview

### 1.1 Feature Description

사용자가 차트의 X축 날짜 범위를 조절하고, DB에 축적된 이전 날짜 데이터를 불러올 수 있는 기능 구현.

### 1.2 Goals

1. **차트 범위 조절**: 1주일/1개월/3개월/6개월/1년/전체 버튼으로 X축 범위 선택
2. **히스토리 데이터 로딩**: 선택한 날짜 범위에 맞는 DB 데이터 동적 로딩
3. **동적 라벨 조절**: 데이터 개수에 따른 X축 라벨 자동 조절

### 1.3 Affected Screens

| Screen | Feature Module | Chart Type | Priority |
|--------|---------------|------------|----------|
| FearGreedScreen | market | Line Chart | High |
| MarketDepositScreen | market | Dual-Axis Chart | High |
| MarketOscillatorScreen | market | Line Chart | High |
| EtfDetailScreen | etf | Time Series Chart | Medium |
| AdvancedDashboardScreen | analysis | Multiple Charts | Medium |
| StatisticsScreen (Tabs) | stock | Various | Low |

---

## 2. Current State Analysis

### 2.1 Chart Technology Stack

- **Library**: MPAndroidChart (AndroidView 래핑)
- **Location**: `core/ui/component/` (ChartUtils.kt, MarketCharts.kt, TechnicalCharts.kt, TimeSeriesCharts.kt)
- **Existing Features**: 핀치 줌, 드래그 팬 지원

### 2.2 Current X-Axis Configuration

```kotlin
// 현재 방식 - 고정된 라벨 개수
xAxis.apply {
    setLabelCount(8, false)  // 고정!
    valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index >= 0 && index < dates.size) {
                dates[index]
            } else ""
        }
    }
}
```

### 2.3 Current Data Loading Pattern

```kotlin
// 현재 방식 - 고정된 일수
fun loadData() {
    repository.getRecentByMarket(market, 365)  // 항상 365일 고정!
}
```

### 2.4 Date Range Query Support Status

| Entity | DAO Method | Repository Method | Status |
|--------|-----------|-------------------|--------|
| FearGreedIndex | ✅ `getByMarketAndDateRange()` | ✅ Supported | Ready |
| MarketIndex | ✅ `getByMarketAndDateRange()` | ✅ Supported | Ready |
| MarketOscillatorData | ✅ `getDataByDateRange()` | ❌ Not exposed | Needs work |
| DailyEtfStatistics | ✅ `getByDateRange()` | ✅ Supported | Ready |
| MarketDeposit | ❌ Missing | ❌ Missing | **Needs addition** |
| Holding | ✅ `getHoldingTimeSeries()` | ✅ Supported | Ready |

---

## 3. Implementation Plan

### Phase 1: Database Layer Updates (30분)

#### 1.1 Add MarketDepositDao Date Range Query

**File**: `core/database/MarketDepositDao.kt`

```kotlin
// 추가할 메서드
@Query("SELECT * FROM market_deposits WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
fun getByDateRange(startDate: String, endDate: String): Flow<List<MarketDeposit>>

@Query("SELECT * FROM market_deposits WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
suspend fun getByDateRangeSuspend(startDate: String, endDate: String): List<MarketDeposit>
```

#### 1.2 Add MarketDepositRepository Date Range Method

**File**: `feature/market/domain/repository/MarketDepositRepository.kt`

```kotlin
// 인터페이스에 추가
fun getByDateRange(startDate: String, endDate: String): Flow<List<MarketDeposit>>
```

**File**: `feature/market/data/repository/MarketDepositRepositoryImpl.kt`

```kotlin
// 구현 추가
override fun getByDateRange(startDate: String, endDate: String): Flow<List<MarketDeposit>> {
    return marketDepositDao.getByDateRange(startDate, endDate)
}
```

#### 1.3 Expose MarketOscillatorRepository Date Range Method

**File**: `feature/market/domain/repository/MarketOscillatorRepository.kt`

```kotlin
// 인터페이스에 추가
fun getDataByDateRange(market: String, startDate: String, endDate: String): Flow<List<MarketOscillatorData>>
```

---

### Phase 2: UI Component Creation (2-3시간)

#### 2.1 Create DateRangeSelector Component

**File**: `core/ui/component/DateRangeSelector.kt`

```kotlin
enum class DateRangeOption(val label: String, val days: Int) {
    WEEK("1주", 7),
    MONTH("1개월", 30),
    THREE_MONTHS("3개월", 90),
    SIX_MONTHS("6개월", 180),
    YEAR("1년", 365),
    ALL("전체", -1)  // -1 means all available data
}

@Composable
fun DateRangeSelector(
    selectedRange: DateRangeOption,
    onRangeSelected: (DateRangeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        DateRangeOption.entries.forEach { option ->
            FilterChip(
                selected = selectedRange == option,
                onClick = { onRangeSelected(option) },
                label = { Text(option.label) }
            )
        }
    }
}
```

#### 2.2 Create Dynamic Label Count Calculator

**File**: `core/ui/component/ChartUtils.kt` (기존 파일에 추가)

```kotlin
object ChartLabelCalculator {
    fun calculateOptimalLabelCount(dataPoints: Int): Int {
        return when {
            dataPoints <= 7 -> dataPoints      // 1주: 매일 표시
            dataPoints <= 14 -> 7              // 2주: 2일마다
            dataPoints <= 30 -> 10             // 1개월: 3일마다
            dataPoints <= 90 -> 10             // 3개월: 9일마다
            dataPoints <= 180 -> 8             // 6개월: 22일마다
            dataPoints <= 365 -> 6             // 1년: 60일마다
            else -> 4                          // 1년 이상: 90일마다
        }
    }

    fun calculateDateRange(option: DateRangeOption): Pair<String, String> {
        val endDate = LocalDate.now()
        val startDate = when (option) {
            DateRangeOption.ALL -> LocalDate.of(2020, 1, 1)  // Earliest possible
            else -> endDate.minusDays(option.days.toLong())
        }
        return Pair(
            startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
    }
}
```

#### 2.3 Update Chart Components with Dynamic Labels

**Files to update**:
- `core/ui/component/MarketCharts.kt`
- `core/ui/component/TechnicalCharts.kt`
- `core/ui/component/TimeSeriesCharts.kt`

```kotlin
// 변경 전
xAxis.setLabelCount(8, false)

// 변경 후
xAxis.setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dates.size), false)
```

---

### Phase 3: ViewModel Refactoring (3-4시간)

#### 3.1 FearGreedViewModel Update

**File**: `feature/market/presentation/feargreed/FearGreedViewModel.kt`

```kotlin
@HiltViewModel
class FearGreedViewModel @Inject constructor(
    private val repository: FearGreedRepository
) : ViewModel() {

    // 새로 추가
    private val _selectedRange = MutableStateFlow(DateRangeOption.YEAR)
    val selectedRange: StateFlow<DateRangeOption> = _selectedRange.asStateFlow()

    private val _dateRange = MutableStateFlow<Pair<String, String>>(
        ChartLabelCalculator.calculateDateRange(DateRangeOption.YEAR)
    )

    // 기존 데이터 로딩 수정
    private fun loadData() {
        viewModelScope.launch {
            combine(_selectedMarket, _dateRange) { market, (startDate, endDate) ->
                Pair(market, Pair(startDate, endDate))
            }.collectLatest { (market, dates) ->
                repository.getByMarketAndDateRange(market, dates.first, dates.second)
                    .collect { data ->
                        _fearGreedData.value = data
                    }
            }
        }
    }

    // 새로 추가
    fun updateDateRange(option: DateRangeOption) {
        _selectedRange.value = option
        _dateRange.value = ChartLabelCalculator.calculateDateRange(option)
    }
}
```

#### 3.2 MarketDepositViewModel Update

**File**: `feature/market/presentation/deposit/MarketDepositViewModel.kt`

동일한 패턴 적용.

#### 3.3 MarketOscillatorViewModel Update

**File**: `feature/market/presentation/oscillator/MarketOscillatorViewModel.kt`

동일한 패턴 적용.

---

### Phase 4: Screen Integration (2-3시간)

#### 4.1 FearGreedScreen Integration

**File**: `feature/market/presentation/feargreed/FearGreedScreen.kt`

```kotlin
@Composable
fun FearGreedScreen(
    viewModel: FearGreedViewModel = hiltViewModel()
) {
    val selectedRange by viewModel.selectedRange.collectAsState()
    val fearGreedData by viewModel.fearGreedData.collectAsState()

    Column {
        // 날짜 범위 선택기 추가
        DateRangeSelector(
            selectedRange = selectedRange,
            onRangeSelected = { viewModel.updateDateRange(it) }
        )

        // 기존 차트 컴포넌트
        FearGreedChart(
            data = fearGreedData,
            // ...
        )
    }
}
```

#### 4.2 MarketDepositScreen Integration

동일한 패턴 적용.

#### 4.3 MarketOscillatorScreen Integration

동일한 패턴 적용.

---

## 4. File Changes Summary

### New Files (2)

| File | Purpose |
|------|---------|
| `core/ui/component/DateRangeSelector.kt` | 날짜 범위 선택 UI 컴포넌트 |

### Modified Files (12)

| File | Changes |
|------|---------|
| `core/database/MarketDepositDao.kt` | Date range query 추가 |
| `feature/market/domain/repository/MarketDepositRepository.kt` | Interface method 추가 |
| `feature/market/data/repository/MarketDepositRepositoryImpl.kt` | Implementation 추가 |
| `feature/market/domain/repository/MarketOscillatorRepository.kt` | Interface method 추가 |
| `feature/market/data/repository/MarketOscillatorRepositoryImpl.kt` | Implementation 추가 |
| `core/ui/component/ChartUtils.kt` | ChartLabelCalculator 추가 |
| `core/ui/component/MarketCharts.kt` | Dynamic label count 적용 |
| `core/ui/component/TechnicalCharts.kt` | Dynamic label count 적용 |
| `core/ui/component/TimeSeriesCharts.kt` | Dynamic label count 적용 |
| `feature/market/presentation/feargreed/FearGreedViewModel.kt` | Date range state 추가 |
| `feature/market/presentation/deposit/MarketDepositViewModel.kt` | Date range state 추가 |
| `feature/market/presentation/oscillator/MarketOscillatorViewModel.kt` | Date range state 추가 |
| `feature/market/presentation/feargreed/FearGreedScreen.kt` | DateRangeSelector 통합 |
| `feature/market/presentation/deposit/MarketDepositScreen.kt` | DateRangeSelector 통합 |
| `feature/market/presentation/oscillator/MarketOscillatorScreen.kt` | DateRangeSelector 통합 |

---

## 5. Testing Checklist

### Phase 1 Tests
- [ ] MarketDepositDao.getByDateRange() 쿼리 정상 동작
- [ ] 빈 날짜 범위에서 빈 리스트 반환

### Phase 2 Tests
- [ ] DateRangeSelector UI 렌더링
- [ ] 버튼 선택 시 콜백 호출
- [ ] ChartLabelCalculator 다양한 입력값 테스트

### Phase 3 Tests
- [ ] ViewModel에서 날짜 범위 변경 시 데이터 재로딩
- [ ] Flow combine 정상 동작

### Phase 4 Tests
- [ ] 화면에서 날짜 범위 변경 시 차트 업데이트
- [ ] 빈 데이터 처리 (Empty State)
- [ ] 로딩 상태 표시

---

## 6. Future Enhancements (Optional)

### 6.1 Custom Date Range Picker
- 시작/종료 날짜 직접 선택
- DatePickerDialog 통합

### 6.2 Date Range Persistence
- DataStore에 사용자 선호 범위 저장
- 앱 재시작 시 복원

### 6.3 Zoom Sync
- 핀치 줌 시 DateRangeSelector 상태 동기화
- 줌 레벨에 따른 자동 범위 업데이트

### 6.4 Animation
- 범위 변경 시 차트 애니메이션
- 데이터 전환 효과

---

## 7. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| 대용량 데이터 로딩 지연 | Medium | 로딩 인디케이터, 페이징 고려 |
| 메모리 사용량 증가 | Low | 기존 LIMIT 쿼리 패턴 유지 |
| 날짜 형식 불일치 | Low | DateFormatter 유틸리티 사용 |
| UI 레이아웃 변경 | Low | 기존 디자인 시스템 활용 |

---

## 8. Dependencies

### Required (No external additions)
- 기존 MPAndroidChart 라이브러리 사용
- Material3 FilterChip 컴포넌트
- Room Flow 쿼리

### No New Dependencies Required
이 기능은 기존 라이브러리만으로 구현 가능합니다.

---

**Last Updated**: 2025-12-26
**Author**: Claude AI Assistant
