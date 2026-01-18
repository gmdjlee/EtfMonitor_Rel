# Stock Input Autocomplete Module - 개발 명세서

**Version**: 1.0
**Created**: 2026-01-18
**Based on**: EtfMonitor UnifiedStockSearchField 컴포넌트

---

## 1. 모듈 개요

### 1.1 목표
EtfMonitor에 구현된 **자동완성 기능이 포함된 종목 입력 텍스트필드**를 독립적인 재사용 가능 모듈로 분리하여, 다른 프로젝트에서도 쉽게 사용할 수 있도록 함.

### 1.2 현재 구현 현황

| 항목 | 현재 상태 |
|------|----------|
| 메인 컴포넌트 | `UnifiedStockSearchField.kt` |
| 사용 화면 | 3개 (Stock Hub, ETF Statistics, AI Analysis) |
| 검색 히스토리 | Room DB 저장, 유형별 분리 (STOCK, STATISTICS, AI_ANALYSIS) |
| 검색 소스 | `StockDao.searchStocks()` (Room DB) |

### 1.3 핵심 기능

| # | 기능 | 설명 | 우선순위 |
|---|------|------|----------|
| 1 | 자동완성 검색 | 종목명/코드 입력 시 실시간 검색 결과 표시 | P0 |
| 2 | 검색 히스토리 | 최근 검색 기록 저장 및 표시 | P0 |
| 3 | 디바운싱 | 입력 시 300ms 지연 후 검색 실행 | P0 |
| 4 | 선택 콜백 | 종목 선택 시 ticker, name 반환 | P0 |
| 5 | 히스토리 유형 분리 | 화면별 독립적인 검색 히스토리 | P1 |
| 6 | 커스텀 스타일링 | Material 3 테마 호환 | P1 |

### 1.4 모듈 분리 원칙

```
✓ Composable 단독 사용 가능 (ViewModel 의존성 제거)
✓ 검색 로직 외부 주입 (콜백 기반)
✓ 히스토리 저장 로직 분리 (옵션)
✓ Material 3 테마 호환
✓ 다양한 커스터마이징 지원
✓ 최소 의존성 (Compose Material 3만 필수)
```

---

## 2. 현재 구현 분석

### 2.1 컴포넌트 구조

```
UnifiedStockSearchField
├── OutlinedTextField (검색 입력)
│   ├── Leading Icon (Search / Loading)
│   └── Trailing Icons
│       ├── History Button (빈 입력 시)
│       └── Clear Button (입력 있을 시)
├── Card (자동완성 드롭다운) - 오버레이
│   └── LazyColumn
│       └── ListItem (검색 결과)
└── StockSearchHistoryDialog (히스토리 다이얼로그)
    └── LazyColumn
        └── ListItem (히스토리 항목)
```

### 2.2 현재 파라미터

```kotlin
@Composable
fun UnifiedStockSearchField(
    // 필수 파라미터
    searchQuery: String,                              // 현재 검색어
    onSearchQueryChange: (String) -> Unit,            // 검색어 변경 콜백
    searchResults: List<StockSearchItem>,             // 검색 결과
    searchHistory: List<SearchHistory>,               // 검색 히스토리
    onSelectStock: (ticker: String, name: String) -> Unit,  // 선택 콜백

    // 선택 파라미터
    isSearching: Boolean = false,                     // 로딩 상태
    placeholder: String = "종목명 또는 티커 검색...",   // 플레이스홀더
    onSelectFromHistory: ((ticker: String, name: String) -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

### 2.3 데이터 모델

```kotlin
// 검색 결과 아이템
data class StockSearchItem(
    val ticker: String,
    val name: String,
    val market: String = ""
)

// 검색 히스토리 (Room Entity)
@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticker: String,
    val name: String,
    val market: String,
    val historyType: String = SearchHistoryType.STATISTICS,
    val searchedAt: Long = System.currentTimeMillis()
)

object SearchHistoryType {
    const val STATISTICS = "STATISTICS"
    const val STOCK = "STOCK"
    const val AI_ANALYSIS = "AI_ANALYSIS"
}
```

### 2.4 현재 사용 화면별 구현

| 화면 | ViewModel | 히스토리 타입 | 디바운스 | 최소 쿼리 |
|------|-----------|--------------|----------|-----------|
| Stock Hub | OscillatorViewModel | STOCK | 300ms | 1자 |
| ETF Statistics | StatisticsViewModel | STATISTICS | 없음 | 2자 |
| AI Analysis | NewAIAnalysisViewModel | AI_ANALYSIS | 없음 | - |

---

## 3. 모듈 설계

### 3.1 모듈 구조

```
stockinput/
├── StockInputField.kt           # 메인 Composable
├── StockInputHistoryDialog.kt   # 히스토리 다이얼로그
├── StockInputDefaults.kt        # 기본값 및 스타일
├── model/
│   └── StockInputModels.kt      # 데이터 모델
└── state/
    └── StockInputState.kt       # 상태 관리 (선택적)
```

### 3.2 핵심 API 설계

#### 3.2.1 기본 사용 (Stateless)

```kotlin
@Composable
fun StockInputField(
    // 필수
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<StockSuggestion>,
    onSelect: (StockSuggestion) -> Unit,

    // 선택
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isLoading: Boolean = false,
    placeholder: String = "종목명 또는 종목코드 입력",

    // 히스토리 (선택)
    history: List<StockSuggestion> = emptyList(),
    onHistorySelect: ((StockSuggestion) -> Unit)? = null,

    // 커스터마이징
    colors: StockInputColors = StockInputDefaults.colors(),
    shape: Shape = StockInputDefaults.shape,
    contentPadding: PaddingValues = StockInputDefaults.contentPadding
)
```

#### 3.2.2 상태 관리 사용 (Stateful)

```kotlin
@Composable
fun rememberStockInputState(
    initialValue: String = "",
    debounceMs: Long = 300L,
    onSearch: suspend (String) -> List<StockSuggestion>
): StockInputState

class StockInputState {
    val value: String
    val suggestions: List<StockSuggestion>
    val isLoading: Boolean
    val selectedStock: StockSuggestion?

    fun onValueChange(newValue: String)
    fun onSelect(stock: StockSuggestion)
    fun clear()
}

// 사용 예시
@Composable
fun MyScreen(viewModel: MyViewModel) {
    val state = rememberStockInputState(
        onSearch = { query -> viewModel.searchStocks(query) }
    )

    StockInputField(
        state = state,
        onSelect = { stock -> viewModel.analyzeStock(stock.ticker) }
    )
}
```

### 3.3 데이터 모델

```kotlin
/**
 * 종목 검색 결과/제안 항목
 */
data class StockSuggestion(
    val ticker: String,
    val name: String,
    val market: String = "",
    val extra: Map<String, Any> = emptyMap()  // 확장용
) {
    companion object {
        /**
         * SearchHistory에서 변환
         */
        fun fromHistory(history: SearchHistory) = StockSuggestion(
            ticker = history.ticker,
            name = history.name,
            market = history.market
        )
    }
}

/**
 * 선택된 종목 결과
 */
data class SelectedStock(
    val ticker: String,
    val name: String,
    val market: String = ""
)
```

### 3.4 스타일링 API

```kotlin
object StockInputDefaults {

    val shape: Shape
        @Composable get() = MaterialTheme.shapes.medium

    val contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        textColor: Color = MaterialTheme.colorScheme.onSurface,
        placeholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor: Color = MaterialTheme.colorScheme.outline,
        unfocusedBorderColor: Color = MaterialTheme.colorScheme.outline,
        dropdownContainerColor: Color = MaterialTheme.colorScheme.surface,
        dropdownElevation: Dp = 8.dp
    ): StockInputColors = StockInputColors(
        containerColor = containerColor,
        focusedContainerColor = focusedContainerColor,
        textColor = textColor,
        placeholderColor = placeholderColor,
        iconColor = iconColor,
        focusedBorderColor = focusedBorderColor,
        unfocusedBorderColor = unfocusedBorderColor,
        dropdownContainerColor = dropdownContainerColor,
        dropdownElevation = dropdownElevation
    )
}

@Immutable
data class StockInputColors(
    val containerColor: Color,
    val focusedContainerColor: Color,
    val textColor: Color,
    val placeholderColor: Color,
    val iconColor: Color,
    val focusedBorderColor: Color,
    val unfocusedBorderColor: Color,
    val dropdownContainerColor: Color,
    val dropdownElevation: Dp
)
```

---

## 4. 컴포넌트 상세 명세

### 4.1 StockInputField

#### 구조
```
Box (fillMaxWidth)
├── OutlinedTextField
│   ├── leadingIcon
│   │   ├── CircularProgressIndicator (isLoading = true)
│   │   └── Icon(Search) (isLoading = false)
│   └── trailingIcon
│       ├── IconButton(History) (value.isEmpty && history.isNotEmpty)
│       └── IconButton(Clear) (value.isNotEmpty)
│
├── Card (드롭다운, suggestions.isNotEmpty && value.isNotBlank)
│   └── LazyColumn
│       └── items(suggestions)
│           └── SuggestionItem
│               ├── headlineContent: Text(name)
│               └── supportingContent: Text("ticker • market")
│
└── HistoryDialog (showHistoryDialog = true)
```

#### 동작 흐름

```
1. 사용자 입력
   └─→ onValueChange(newValue) 호출
       └─→ 외부에서 검색 실행 (debounce 적용)
           └─→ suggestions 업데이트

2. 제안 항목 클릭
   └─→ value = suggestion.name 설정
   └─→ onSelect(suggestion) 호출
   └─→ suggestions 클리어

3. 히스토리 버튼 클릭
   └─→ 히스토리 다이얼로그 표시

4. 히스토리 항목 선택
   └─→ value = history.name 설정
   └─→ onHistorySelect(history) 또는 onSelect(history) 호출
   └─→ 다이얼로그 닫기
```

### 4.2 StockInputHistoryDialog

```kotlin
@Composable
fun StockInputHistoryDialog(
    history: List<StockSuggestion>,
    onDismiss: () -> Unit,
    onSelect: (StockSuggestion) -> Unit,

    // 커스터마이징
    title: String = "최근 검색",
    emptyMessage: String = "검색 기록이 없습니다",
    confirmButtonText: String = "닫기"
)
```

### 4.3 SuggestionItem

```kotlin
@Composable
private fun SuggestionItem(
    suggestion: StockSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(suggestion.name) },
        supportingContent = {
            Text(
                if (suggestion.market.isNotEmpty()) {
                    "${suggestion.ticker} • ${suggestion.market}"
                } else {
                    suggestion.ticker
                },
                style = MaterialTheme.typography.bodySmall
            )
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}
```

---

## 5. 상태 관리

### 5.1 StockInputState 클래스

```kotlin
@Stable
class StockInputState(
    initialValue: String = "",
    private val debounceMs: Long = 300L,
    private val onSearch: suspend (String) -> List<StockSuggestion>,
    private val scope: CoroutineScope
) {
    private val _value = mutableStateOf(initialValue)
    val value: String by _value

    private val _suggestions = mutableStateOf<List<StockSuggestion>>(emptyList())
    val suggestions: List<StockSuggestion> by _suggestions

    private val _isLoading = mutableStateOf(false)
    val isLoading: Boolean by _isLoading

    private val _selectedStock = mutableStateOf<StockSuggestion?>(null)
    val selectedStock: StockSuggestion? by _selectedStock

    private var searchJob: Job? = null

    fun onValueChange(newValue: String) {
        _value.value = newValue
        _selectedStock.value = null

        if (newValue.isBlank()) {
            _suggestions.value = emptyList()
            return
        }

        searchJob?.cancel()
        searchJob = scope.launch {
            delay(debounceMs)
            _isLoading.value = true
            try {
                _suggestions.value = onSearch(newValue)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSelect(stock: StockSuggestion) {
        _value.value = stock.name
        _selectedStock.value = stock
        _suggestions.value = emptyList()
    }

    fun clear() {
        _value.value = ""
        _suggestions.value = emptyList()
        _selectedStock.value = null
    }
}

@Composable
fun rememberStockInputState(
    initialValue: String = "",
    debounceMs: Long = 300L,
    onSearch: suspend (String) -> List<StockSuggestion>
): StockInputState {
    val scope = rememberCoroutineScope()
    return remember {
        StockInputState(
            initialValue = initialValue,
            debounceMs = debounceMs,
            onSearch = onSearch,
            scope = scope
        )
    }
}
```

### 5.2 오버로드 함수 (State 기반)

```kotlin
@Composable
fun StockInputField(
    state: StockInputState,
    onSelect: (StockSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    // ... 기타 파라미터
) {
    StockInputField(
        value = state.value,
        onValueChange = state::onValueChange,
        suggestions = state.suggestions,
        onSelect = { suggestion ->
            state.onSelect(suggestion)
            onSelect(suggestion)
        },
        isLoading = state.isLoading,
        modifier = modifier,
        // ...
    )
}
```

---

## 6. 히스토리 관리 (선택적 확장)

### 6.1 히스토리 인터페이스

```kotlin
interface StockInputHistoryManager {
    fun getHistory(): Flow<List<StockSuggestion>>
    suspend fun saveToHistory(stock: StockSuggestion)
    suspend fun clearHistory()
    suspend fun removeFromHistory(stock: StockSuggestion)
}
```

### 6.2 Room 기반 구현 예시

```kotlin
class RoomStockInputHistoryManager(
    private val searchHistoryDao: SearchHistoryDao,
    private val historyType: String,
    private val maxHistorySize: Int = 20
) : StockInputHistoryManager {

    override fun getHistory(): Flow<List<StockSuggestion>> {
        return searchHistoryDao.getRecentSearchesByType(historyType, maxHistorySize)
            .map { list -> list.map { StockSuggestion.fromHistory(it) } }
    }

    override suspend fun saveToHistory(stock: StockSuggestion) {
        searchHistoryDao.insertSearch(
            SearchHistory(
                ticker = stock.ticker,
                name = stock.name,
                market = stock.market,
                historyType = historyType
            )
        )
        searchHistoryDao.deleteOldSearchesByType(historyType, maxHistorySize)
    }

    override suspend fun clearHistory() {
        searchHistoryDao.deleteAllByType(historyType)
    }

    override suspend fun removeFromHistory(stock: StockSuggestion) {
        searchHistoryDao.deleteByTicker(stock.ticker, historyType)
    }
}
```

---

## 7. 사용 예시

### 7.1 기본 사용 (Stateless)

```kotlin
@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val history by viewModel.searchHistory.collectAsState(initial = emptyList())

    Column {
        StockInputField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            suggestions = suggestions,
            onSelect = { stock ->
                viewModel.analyzeStock(stock.ticker)
            },
            isLoading = isSearching,
            history = history.map { StockSuggestion.fromHistory(it) },
            onHistorySelect = { stock ->
                viewModel.analyzeStock(stock.ticker)
            }
        )

        // 분석 결과 표시
        // ...
    }
}
```

### 7.2 상태 관리 사용 (Stateful)

```kotlin
@Composable
fun QuickSearchScreen(viewModel: QuickSearchViewModel = hiltViewModel()) {
    val state = rememberStockInputState(
        debounceMs = 300L,
        onSearch = { query -> viewModel.searchStocks(query) }
    )

    Column {
        StockInputField(
            state = state,
            onSelect = { stock ->
                viewModel.onStockSelected(stock.ticker, stock.name)
            }
        )

        // 선택된 종목 표시
        state.selectedStock?.let { stock ->
            Card {
                Text("선택: ${stock.name} (${stock.ticker})")
            }
        }
    }
}
```

### 7.3 커스텀 스타일링

```kotlin
@Composable
fun ThemedSearchScreen() {
    StockInputField(
        value = query,
        onValueChange = { query = it },
        suggestions = suggestions,
        onSelect = { /* ... */ },

        // 커스텀 스타일
        colors = StockInputDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            dropdownElevation = 12.dp
        ),
        shape = RoundedCornerShape(16.dp),
        placeholder = "종목 검색..."
    )
}
```

### 7.4 다른 프로젝트에서 사용

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.yourcompany:stock-input:1.0.0")
}

// 사용
@Composable
fun MyAppSearchScreen() {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<StockSuggestion>>(emptyList()) }

    // 자체 검색 로직 연결
    LaunchedEffect(query) {
        if (query.length >= 2) {
            delay(300)
            suggestions = myStockApi.search(query).map {
                StockSuggestion(it.code, it.name, it.market)
            }
        }
    }

    StockInputField(
        value = query,
        onValueChange = { query = it },
        suggestions = suggestions,
        onSelect = { stock ->
            navigateToDetail(stock.ticker)
        }
    )
}
```

---

## 8. 마이그레이션 가이드

### 8.1 현재 코드 → 새 모듈

#### Before (현재)

```kotlin
UnifiedStockSearchField(
    searchQuery = searchQuery,
    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
    searchResults = suggestions.map { stock ->
        StockSearchItem(
            ticker = stock.ticker,
            name = stock.name,
            market = stock.market
        )
    },
    searchHistory = searchHistory,
    isSearching = false,
    placeholder = stringResource(R.string.search_hint),
    onSelectStock = { ticker, _ ->
        viewModel.onClearSuggestions()
        viewModel.analyzeStock(ticker)
    }
)
```

#### After (새 모듈)

```kotlin
StockInputField(
    value = searchQuery,
    onValueChange = { viewModel.onSearchQueryChanged(it) },
    suggestions = suggestions.map { stock ->
        StockSuggestion(
            ticker = stock.ticker,
            name = stock.name,
            market = stock.market
        )
    },
    history = searchHistory.map { StockSuggestion.fromHistory(it) },
    isLoading = false,
    placeholder = stringResource(R.string.search_hint),
    onSelect = { suggestion ->
        viewModel.onClearSuggestions()
        viewModel.analyzeStock(suggestion.ticker)
    }
)
```

### 8.2 변경점 요약

| 항목 | 이전 | 이후 |
|------|------|------|
| 컴포넌트명 | `UnifiedStockSearchField` | `StockInputField` |
| 검색어 파라미터 | `searchQuery` | `value` |
| 결과 타입 | `StockSearchItem` | `StockSuggestion` |
| 히스토리 타입 | `SearchHistory` (Entity) | `StockSuggestion` |
| 선택 콜백 | `onSelectStock(ticker, name)` | `onSelect(StockSuggestion)` |
| 히스토리 콜백 | `onSelectFromHistory` | `onHistorySelect` |

---

## 9. 테스트

### 9.1 단위 테스트

```kotlin
class StockInputStateTest {

    @Test
    fun `debounce prevents rapid search calls`() = runTest {
        var searchCount = 0
        val state = StockInputState(
            debounceMs = 100L,
            onSearch = { searchCount++; emptyList() },
            scope = this
        )

        state.onValueChange("삼")
        state.onValueChange("삼성")
        state.onValueChange("삼성전")

        advanceTimeBy(50)
        assertEquals(0, searchCount)

        advanceTimeBy(100)
        assertEquals(1, searchCount)
    }

    @Test
    fun `onSelect updates value and clears suggestions`() {
        val state = StockInputState(
            onSearch = { listOf(StockSuggestion("005930", "삼성전자", "KOSPI")) },
            scope = TestScope()
        )

        state.onSelect(StockSuggestion("005930", "삼성전자", "KOSPI"))

        assertEquals("삼성전자", state.value)
        assertEquals("005930", state.selectedStock?.ticker)
        assertTrue(state.suggestions.isEmpty())
    }
}
```

### 9.2 UI 테스트

```kotlin
class StockInputFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows suggestions when input has text`() {
        val suggestions = listOf(
            StockSuggestion("005930", "삼성전자", "KOSPI"),
            StockSuggestion("000660", "SK하이닉스", "KOSPI")
        )

        composeTestRule.setContent {
            StockInputField(
                value = "삼성",
                onValueChange = {},
                suggestions = suggestions,
                onSelect = {}
            )
        }

        composeTestRule.onNodeWithText("삼성전자").assertIsDisplayed()
        composeTestRule.onNodeWithText("SK하이닉스").assertIsDisplayed()
    }

    @Test
    fun `shows history button when input is empty`() {
        composeTestRule.setContent {
            StockInputField(
                value = "",
                onValueChange = {},
                suggestions = emptyList(),
                onSelect = {},
                history = listOf(StockSuggestion("005930", "삼성전자", "KOSPI"))
            )
        }

        composeTestRule.onNodeWithContentDescription("검색 히스토리").assertIsDisplayed()
    }

    @Test
    fun `shows clear button when input has text`() {
        composeTestRule.setContent {
            StockInputField(
                value = "삼성",
                onValueChange = {},
                suggestions = emptyList(),
                onSelect = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("지우기").assertIsDisplayed()
    }
}
```

---

## 10. 의존성

### 10.1 필수 의존성

```toml
# gradle/libs.versions.toml
[versions]
compose-bom = "2024.12.01"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
```

### 10.2 선택 의존성

```toml
# Room (히스토리 저장 시)
room-runtime = { module = "androidx.room:room-runtime", version = "2.8.3" }
room-ktx = { module = "androidx.room:room-ktx", version = "2.8.3" }

# Coroutines (State 사용 시)
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "1.10.2" }
```

---

## 11. 파일 구조 (최종)

```
stock-input-module/
├── build.gradle.kts
├── src/main/kotlin/com/stockinput/
│   ├── StockInputField.kt              # 메인 Composable (Stateless)
│   ├── StockInputFieldStateful.kt      # State 기반 오버로드
│   ├── StockInputHistoryDialog.kt      # 히스토리 다이얼로그
│   ├── StockInputDefaults.kt           # 기본값 및 스타일
│   │
│   ├── model/
│   │   ├── StockSuggestion.kt          # 제안/결과 모델
│   │   └── StockInputColors.kt         # 색상 설정
│   │
│   ├── state/
│   │   └── StockInputState.kt          # 상태 관리 클래스
│   │
│   └── internal/
│       ├── SuggestionItem.kt           # 내부 컴포넌트
│       └── SuggestionDropdown.kt       # 드롭다운 컴포넌트
│
└── src/test/kotlin/com/stockinput/
    ├── StockInputStateTest.kt
    └── StockInputFieldTest.kt
```

---

## 12. 구현 체크리스트

### Phase 1: 핵심 컴포넌트 (P0)
- [ ] `StockSuggestion` 데이터 모델 정의
- [ ] `StockInputColors` 스타일 클래스 정의
- [ ] `StockInputDefaults` 기본값 객체 정의
- [ ] `StockInputField` 메인 Composable 구현
- [ ] `StockInputHistoryDialog` 구현
- [ ] 기본 단위 테스트 작성

### Phase 2: 상태 관리 (P1)
- [ ] `StockInputState` 클래스 구현
- [ ] `rememberStockInputState` 함수 구현
- [ ] Stateful 오버로드 함수 구현
- [ ] 디바운싱 테스트 작성

### Phase 3: 확장 기능 (P2)
- [ ] `StockInputHistoryManager` 인터페이스 정의
- [ ] Room 기반 구현 예시 작성
- [ ] 키보드 동작 최적화 (IME Action)
- [ ] 접근성 개선 (Content Description)

### Phase 4: 문서화 (P2)
- [ ] API 문서 (KDoc) 작성
- [ ] 사용 예시 README 작성
- [ ] 마이그레이션 가이드 완성

---

## 부록 A: 현재 구현 파일 참조

| 파일 | 경로 | 역할 |
|------|------|------|
| UnifiedStockSearchField | `core/ui/component/UnifiedStockSearchField.kt` | 현재 메인 컴포넌트 |
| SearchHistory Entity | `core/database/entities/SearchHistory.kt` | 히스토리 Entity |
| SearchHistoryDao | `core/database/SearchHistoryDao.kt` | 히스토리 DAO |
| StockDao | `core/database/StockDao.kt` | 종목 검색 DAO |
| OscillatorViewModel | `feature/stock/presentation/oscillator/OscillatorViewModel.kt` | Stock Hub VM |
| StatisticsViewModel | `feature/stock/presentation/statistics/StatisticsViewModel.kt` | Statistics VM |
| NewAIAnalysisViewModel | `feature/analysis/presentation/aianalysis/NewAIAnalysisViewModel.kt` | AI Analysis VM |

---

**End of Specification**
