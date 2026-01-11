# Stock Menu App - 개발 명세서

**Version**: 1.0
**Created**: 2026-01-11
**Based on**: EtfMonitor Stock Feature (38 files)

---

## 1. 프로젝트 개요

### 1.1 목표
EtfMonitor의 **종목 메뉴** 기능을 독립적인 경량 앱으로 분리하여 개발.
Python 단일 프로젝트에서 데이터 수집 로직을 먼저 검증한 후, Android 앱으로 통합.

### 1.2 핵심 기능
| # | 기능 | 설명 | 우선순위 |
|---|------|------|----------|
| 1 | 종목 검색 | 이름/코드로 종목 검색 | P0 |
| 2 | 수급 분석 | 시가총액, 외국인/기관 5일 순매수 | P0 |
| 3 | 기술적 지표 | Trend Signal, Elder Impulse, DeMark TD | P1 |
| 4 | OHLCV 차트 | 일/주봉 캔들 차트 | P1 |
| 5 | 시장 지표 | 예탁금, 신용잔고 추이 | P2 |

### 1.3 개발 원칙

```
✓ 기능 하나씩 구현 → 테스트 → 검증 → 다음 기능
✓ Python 먼저 완성 → 앱에 통합
✓ 클린 아키텍처 (Domain/Data/Presentation)
✓ 간결한 네이밍 (명확하지만 최소 길이)
✓ 에러 추적 용이한 구조
✓ Claude Code 개발 환경 최적화
```

---

## 2. 개발 Phase

### Phase 0: 프로젝트 설정 (Day 1)
```
[Python]                          [App]
├── 프로젝트 구조 생성             ├── Android 프로젝트 생성
├── 가상환경 설정                  ├── Gradle 설정 (Chaquopy)
├── 의존성 설치                    ├── Hilt DI 설정
└── 테스트 프레임워크              └── Room DB 설정
```

### Phase 1: 종목 검색 + 수급 분석 (Core)
```
[Python] stocks.py
├── search_stock()           # 종목 검색
├── get_stock_data()         # 수급 데이터 (시총, 외인/기관)
└── get_all_stocks_list()    # 전체 종목 리스트

[App]
├── domain/
│   ├── model/Stock.kt       # Stock, StockData
│   ├── repo/StockRepo.kt    # Repository interface
│   └── usecase/SearchStock.kt
├── data/
│   └── StockRepoImpl.kt
└── ui/
    ├── SearchScreen.kt
    └── AnalysisScreen.kt
```

### Phase 2: 기술적 지표 (Technical)
```
[Python] trend_signal.py
├── get_trend_signal_analysis()  # MA, CMF, Fear/Greed, Signals
├── get_elder_impulse_analysis() # EMA13, MACD, Impulse
└── get_demark_td_analysis()     # TD Setup counts

[App]
├── domain/model/Indicator.kt    # TrendSignal, Elder, DeMark
├── ui/IndicatorScreen.kt        # 지표 화면 (탭 구조)
└── ui/component/ChartCard.kt
```

### Phase 3: 차트 시각화 (Chart)
```
[App]
├── ui/chart/
│   ├── CandleChart.kt       # OHLCV 캔들
│   ├── LineChart.kt         # 라인 차트
│   └── BarChart.kt          # 바 차트
└── ui/component/DateRange.kt
```

### Phase 4: 시장 지표 (Market)
```
[Python] deposit_scraper.py
└── get_market_deposit_data()    # 예탁금, 신용잔고

[App]
├── domain/model/Deposit.kt
└── ui/DepositScreen.kt
```

---

## 3. Python 프로젝트 구조

### 3.1 디렉토리 구조
```
stock-analyzer/
├── pyproject.toml           # 프로젝트 설정 (Poetry/uv)
├── README.md
├── .env.example             # KIS API 키 템플릿
│
├── src/
│   └── stock_analyzer/
│       ├── __init__.py
│       ├── config.py        # 설정 (API 키, 상수)
│       │
│       ├── core/            # 공통 유틸
│       │   ├── __init__.py
│       │   ├── log.py       # 로거
│       │   ├── http.py      # HTTP 클라이언트
│       │   ├── date.py      # 날짜 유틸
│       │   └── json.py      # JSON 헬퍼
│       │
│       ├── client/          # 외부 API 클라이언트
│       │   ├── __init__.py
│       │   ├── kis.py       # KIS API 래퍼
│       │   └── pykrx.py     # pykrx 래퍼
│       │
│       ├── stock/           # 종목 데이터
│       │   ├── __init__.py
│       │   ├── search.py    # 검색
│       │   ├── analysis.py  # 수급 분석
│       │   └── ohlcv.py     # 가격 데이터
│       │
│       ├── indicator/       # 기술적 지표
│       │   ├── __init__.py
│       │   ├── trend.py     # Trend Signal
│       │   ├── elder.py     # Elder Impulse
│       │   └── demark.py    # DeMark TD
│       │
│       └── market/          # 시장 지표
│           ├── __init__.py
│           └── deposit.py   # 예탁금
│
├── tests/                   # 테스트
│   ├── conftest.py
│   ├── test_search.py
│   ├── test_analysis.py
│   └── test_indicator.py
│
└── scripts/                 # CLI/유틸 스크립트
    └── run_analysis.py
```

### 3.2 핵심 모듈 명세

#### 3.2.1 core/log.py
```python
"""Structured logging with context."""
import logging
from typing import Dict, Any

def get_log(name: str) -> logging.Logger:
    """Get logger with standard format."""
    ...

def log_call(func: str, args: Dict[str, Any]) -> None:
    """Log function call with args."""
    ...

def log_err(func: str, err: Exception, ctx: Dict[str, Any] = None) -> None:
    """Log error with context."""
    ...
```

#### 3.2.2 stock/search.py
```python
"""Stock search functionality."""
from typing import List, Optional
from dataclasses import dataclass

@dataclass
class StockInfo:
    ticker: str
    name: str
    market: str  # KOSPI/KOSDAQ

def search(query: str) -> List[StockInfo]:
    """Search stocks by name or ticker."""
    ...

def get_all() -> List[StockInfo]:
    """Get all stocks list."""
    ...

def get_name(ticker: str) -> Optional[str]:
    """Get stock name by ticker."""
    ...
```

#### 3.2.3 stock/analysis.py
```python
"""Stock supply-demand analysis."""
from dataclasses import dataclass
from typing import List

@dataclass
class StockData:
    ticker: str
    name: str
    dates: List[str]
    mcap: List[int]       # 시가총액
    for_5d: List[int]     # 외국인 5일 순매수
    ins_5d: List[int]     # 기관 5일 순매수

def analyze(ticker: str, days: int = 180) -> StockData:
    """Get stock analysis data."""
    ...
```

#### 3.2.4 indicator/trend.py
```python
"""Trend Signal indicator."""
from dataclasses import dataclass
from typing import List

@dataclass
class TrendSignal:
    ticker: str
    name: str
    interval: str        # d/w
    dates: List[str]
    ohlcv: List[tuple]   # (O, H, L, C, V)
    ma: List[float]
    cmf: List[float]
    fg: List[float]      # Fear/Greed
    buy: List[int]       # 0/1
    sell: List[int]      # 0/1

def calc(ticker: str, days: int = 180, interval: str = "w") -> TrendSignal:
    """Calculate trend signal."""
    ...
```

### 3.3 JSON 응답 규격

#### 성공 응답
```json
{
  "ok": true,
  "data": { ... }
}
```

#### 에러 응답
```json
{
  "ok": false,
  "error": {
    "code": "TICKER_NOT_FOUND",
    "msg": "종목을 찾을 수 없습니다",
    "ctx": {"ticker": "999999"}
  }
}
```

#### 에러 코드
| Code | Description |
|------|-------------|
| `INVALID_ARG` | 잘못된 인자 |
| `TICKER_NOT_FOUND` | 종목 없음 |
| `NO_DATA` | 데이터 없음 |
| `API_ERROR` | 외부 API 오류 |
| `KIS_NOT_INIT` | KIS API 미설정 |
| `TIMEOUT` | 타임아웃 |

---

## 4. App 프로젝트 구조

### 4.1 디렉토리 구조
```
StockApp/
├── app/
│   ├── build.gradle.kts
│   │
│   └── src/main/
│       ├── java/com/stockapp/
│       │   ├── App.kt               # Hilt Application
│       │   ├── MainActivity.kt
│       │   │
│       │   ├── core/                # 공통 인프라
│       │   │   ├── db/              # Room DB
│       │   │   │   ├── AppDb.kt
│       │   │   │   ├── entity/
│       │   │   │   └── dao/
│       │   │   ├── py/              # Python Bridge
│       │   │   │   └── PyClient.kt
│       │   │   ├── ui/              # 공통 UI
│       │   │   │   ├── theme/
│       │   │   │   └── component/
│       │   │   └── di/              # DI Modules
│       │   │       ├── DbModule.kt
│       │   │       └── PyModule.kt
│       │   │
│       │   ├── feature/             # 기능별 모듈
│       │   │   ├── search/          # 종목 검색
│       │   │   │   ├── domain/
│       │   │   │   │   ├── model/
│       │   │   │   │   ├── repo/
│       │   │   │   │   └── usecase/
│       │   │   │   ├── data/
│       │   │   │   │   └── repo/
│       │   │   │   ├── ui/
│       │   │   │   │   ├── SearchScreen.kt
│       │   │   │   │   └── SearchVm.kt
│       │   │   │   └── di/
│       │   │   │       └── SearchModule.kt
│       │   │   │
│       │   │   ├── analysis/        # 수급 분석
│       │   │   ├── indicator/       # 기술적 지표
│       │   │   └── market/          # 시장 지표
│       │   │
│       │   └── nav/                 # 네비게이션
│       │       └── Nav.kt
│       │
│       ├── python/                  # Python 스크립트
│       │   ├── stock_analyzer/      # Python 패키지 복사
│       │   └── __init__.py
│       │
│       └── res/
│
├── gradle/
│   └── libs.versions.toml
│
└── build.gradle.kts
```

### 4.2 클린 아키텍처 계층

```
┌─────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                 │
│  Screen ← ViewModel (StateFlow)             │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│  Domain Layer                               │
│  UseCase ← Repository (interface)           │
│  Model (data class)                         │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│  Data Layer                                 │
│  RepositoryImpl → LocalDataSource (Room)    │
│                 → RemoteDataSource (Python) │
└─────────────────────────────────────────────┘
```

### 4.3 핵심 컴포넌트 명세

#### 4.3.1 PyClient (Python Bridge)
```kotlin
/**
 * Python 호출 클라이언트.
 * 모든 Python 호출은 이 클래스를 통해 수행.
 */
@Singleton
class PyClient @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val py = Python.getInstance()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun <T> call(
        module: String,
        func: String,
        args: List<Any> = emptyList(),
        timeoutMs: Long = 30_000,
        parser: (String) -> T
    ): Result<T>
}
```

#### 4.3.2 Stock Domain Model
```kotlin
// domain/model/Stock.kt
data class Stock(
    val ticker: String,
    val name: String,
    val market: Market  // KOSPI, KOSDAQ
)

enum class Market { KOSPI, KOSDAQ }

data class StockData(
    val ticker: String,
    val name: String,
    val dates: List<String>,
    val mcap: List<Long>,
    val for5d: List<Long>,
    val ins5d: List<Long>
)
```

#### 4.3.3 Repository Interface
```kotlin
// domain/repo/StockRepo.kt
interface StockRepo {
    suspend fun search(query: String): Result<List<Stock>>
    suspend fun getAnalysis(ticker: String, days: Int = 180): Result<StockData>
    fun getHistory(): Flow<List<String>>  // 검색 히스토리
    suspend fun saveHistory(ticker: String)
}
```

#### 4.3.4 ViewModel State
```kotlin
// ui/SearchVm.kt
sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Results(val stocks: List<Stock>) : SearchState()
    data class Error(val code: String, val msg: String) : SearchState()
}

@HiltViewModel
class SearchVm @Inject constructor(
    private val searchUC: SearchStockUC
) : ViewModel() {
    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun search(q: String) { ... }
}
```

---

## 5. 데이터 모델

### 5.1 Database Entities

```kotlin
// Stock 캐시 (자동완성용)
@Entity(tableName = "stocks")
data class StockEntity(
    @PrimaryKey val ticker: String,
    val name: String,
    val market: String,
    val updatedAt: Long
)

// 수급 분석 캐시
@Entity(tableName = "analysis_cache")
data class AnalysisCacheEntity(
    @PrimaryKey val ticker: String,
    val data: String,      // JSON serialized
    val startDate: String,
    val endDate: String,
    val cachedAt: Long
)

// 검색 히스토리
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticker: String,
    val name: String,
    val searchedAt: Long
)
```

### 5.2 캐시 정책

| Entity | TTL | 무효화 조건 |
|--------|-----|------------|
| stocks | 24h | 앱 시작 시 체크 |
| analysis_cache | 24h | 요청 일자가 캐시 범위 밖 |
| search_history | - | 최대 50개 유지 |

---

## 6. Python-App 인터페이스

### 6.1 호출 규격

```kotlin
// Python 호출 예시
val result = pyClient.call(
    module = "stock_analyzer.stock.search",
    func = "search",
    args = listOf("삼성전자"),
    timeoutMs = 30_000
) { json ->
    json.decodeFromString<SearchResponse>(json)
}
```

### 6.2 모듈별 함수 매핑

| Python Module | Function | App UseCase |
|---------------|----------|-------------|
| `stock.search` | `search(query)` | SearchStockUC |
| `stock.search` | `get_all()` | GetAllStocksUC |
| `stock.analysis` | `analyze(ticker, days)` | GetAnalysisUC |
| `indicator.trend` | `calc(ticker, days, interval)` | GetTrendSignalUC |
| `indicator.elder` | `calc(ticker, days, interval)` | GetElderImpulseUC |
| `indicator.demark` | `calc(ticker, days, interval)` | GetDemarkTDUC |
| `market.deposit` | `scrape(pages)` | GetDepositUC |

### 6.3 타임아웃 설정

| Function | Timeout | 비고 |
|----------|---------|------|
| search | 30s | - |
| analyze | 60s | KIS API 2회 호출 |
| indicator.* | 30s | pykrx only |
| deposit | 60s | 웹 스크래핑 |

---

## 7. 네이밍 규칙

### 7.1 Python

| Type | Convention | Example |
|------|------------|---------|
| Module | snake_case | `trend_signal.py` |
| Function | snake_case, 동사 | `search()`, `calc()` |
| Class | PascalCase | `StockData` |
| Variable | snake_case, 약어 허용 | `mcap`, `for_5d` |
| Constant | UPPER_SNAKE | `MAX_RETRIES` |

### 7.2 Kotlin/App

| Type | Convention | Example |
|------|------------|---------|
| Package | lowercase | `com.stockapp.feature.search` |
| Class | PascalCase | `SearchVm`, `StockRepo` |
| Function | camelCase, 동사 | `search()`, `getAnalysis()` |
| Variable | camelCase | `stockList`, `for5d` |
| Constant | UPPER_SNAKE | `CACHE_TTL_MS` |

### 7.3 약어 사전

| Full | Abbrev | Usage |
|------|--------|-------|
| ViewModel | Vm | `SearchVm` |
| Repository | Repo | `StockRepo` |
| UseCase | UC | `SearchStockUC` |
| Implementation | Impl | `StockRepoImpl` |
| DataSource | DS | `LocalDS`, `RemoteDS` |
| Module | Mod | `SearchMod` |
| market capitalization | mcap | `mcap` |
| foreign | for | `for5d` |
| institution | ins | `ins5d` |
| indicator | ind | `TrendInd` |

---

## 8. 에러 처리

### 8.1 Python 에러 처리

```python
# 표준 에러 반환
def search(query: str) -> dict:
    if not query:
        return {"ok": False, "error": {
            "code": "INVALID_ARG",
            "msg": "검색어가 필요합니다"
        }}

    try:
        # ... logic
        return {"ok": True, "data": results}
    except Exception as e:
        log_err("search", e, {"query": query})
        return {"ok": False, "error": {
            "code": "API_ERROR",
            "msg": str(e)
        }}
```

### 8.2 App 에러 처리

```kotlin
sealed class AppError(val code: String, val msg: String) {
    class InvalidArg(msg: String) : AppError("INVALID_ARG", msg)
    class NotFound(msg: String) : AppError("NOT_FOUND", msg)
    class Network(msg: String) : AppError("NETWORK", msg)
    class Python(code: String, msg: String) : AppError(code, msg)
    class Unknown(e: Throwable) : AppError("UNKNOWN", e.message ?: "알 수 없는 오류")
}

// ViewModel에서 에러 표시
when (val state = _state.value) {
    is SearchState.Error -> {
        // code로 에러 유형 구분, msg로 사용자 표시
        ErrorCard(code = state.code, msg = state.msg)
    }
}
```

### 8.3 로깅 구조

```
[Module] LEVEL: Message {context}

예시:
[stock.search] INFO: search called {"query": "삼성"}
[stock.search] INFO: search complete {"count": 15}
[stock.analysis] ERROR: KIS API failed {"ticker": "005930", "error": "timeout"}
```

---

## 9. 테스트 계획

### 9.1 Python 테스트

```
tests/
├── unit/
│   ├── test_date.py       # 날짜 유틸
│   ├── test_search.py     # 검색 로직
│   └── test_indicator.py  # 지표 계산
│
├── integration/
│   ├── test_kis.py        # KIS API (mock)
│   └── test_pykrx.py      # pykrx (live)
│
└── e2e/
    └── test_full_flow.py  # 전체 흐름
```

### 9.2 App 테스트

```
app/src/test/           # Unit Tests
├── PyClientTest.kt     # Python 호출
├── RepoTest.kt         # Repository
└── VmTest.kt           # ViewModel

app/src/androidTest/    # Instrumented Tests
├── DbTest.kt           # Room DB
└── ScreenTest.kt       # Compose UI
```

### 9.3 Phase별 테스트 체크리스트

#### Phase 1 체크리스트
- [ ] Python: `search("")` → 에러 반환
- [ ] Python: `search("삼성")` → 결과 반환
- [ ] Python: `analyze("005930", 180)` → 데이터 반환
- [ ] Python: `analyze("999999", 180)` → 에러 반환
- [ ] App: 검색 → 결과 표시
- [ ] App: 검색 → 히스토리 저장
- [ ] App: 오프라인 → 캐시 사용
- [ ] App: 에러 → 에러 화면 표시

---

## 10. 개발 환경

### 10.1 Python 환경

```toml
# pyproject.toml
[project]
name = "stock-analyzer"
version = "0.1.0"
requires-python = ">=3.10"

dependencies = [
    "pykrx>=1.0.45",
    "pandas>=2.0.0",
    "numpy>=1.24.0",
    "requests>=2.31.0",
    "python-dotenv>=1.0.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=7.4.0",
    "pytest-cov>=4.1.0",
    "ruff>=0.1.0",
]
```

### 10.2 App 환경

```toml
# gradle/libs.versions.toml
[versions]
kotlin = "2.1.0"
compose-bom = "2024.12.01"
hilt = "2.54"
room = "2.8.3"
chaquopy = "15.0.1"

[libraries]
# ... 생략
```

### 10.3 Claude Code 최적화

```markdown
# CLAUDE.md (신규 프로젝트용)

## Quick Commands
- `python -m pytest tests/` - Python 테스트
- `./gradlew test` - App 단위 테스트
- `./gradlew connectedAndroidTest` - App 통합 테스트

## File Locations
- Python: `stock-analyzer/src/stock_analyzer/`
- App: `StockApp/app/src/main/java/com/stockapp/`
- Tests: `tests/`, `app/src/test/`

## Common Patterns
- 모든 Python 함수는 `{"ok": bool, "data/error": ...}` 반환
- ViewModel은 sealed class로 상태 관리
- Repository는 Result<T> 반환
```

---

## 11. 마일스톤

| Phase | 목표 | 산출물 |
|-------|------|--------|
| P0 | 프로젝트 설정 | Python/App 프로젝트 구조 |
| P1 | 종목 검색 + 수급 | 검색 화면, 분석 화면 |
| P2 | 기술적 지표 | 지표 화면 (3 tabs) |
| P3 | 차트 시각화 | 캔들/라인 차트 |
| P4 | 시장 지표 | 예탁금 화면 |
| P5 | 최적화 | 캐싱, 성능 개선 |

---

## 12. 참고 자료

### 12.1 현재 프로젝트 파일 (EtfMonitor)

| 카테고리 | 파일 | 참고용 |
|----------|------|--------|
| Python | `stocks.py` | 검색, 분석 로직 |
| Python | `trend_signal.py` | 기술적 지표 |
| Python | `core.py` | 공통 유틸 |
| Python | `kis_client.py` | KIS API 래퍼 |
| App | `OscillatorPyClient.kt` | Python Bridge |
| App | `StockRepositoryImpl.kt` | Repository 패턴 |
| App | `OscillatorViewModel.kt` | 상태 관리 |
| App | `OscillatorScreen.kt` | UI 구성 |

### 12.2 외부 라이브러리

- **pykrx**: https://github.com/sharebook-kr/pykrx
- **KIS API**: https://apiportal.koreainvestment.com
- **Jetpack Compose**: https://developer.android.com/jetpack/compose
- **Vico Charts**: https://github.com/patrykandpatrick/vico

---

## 부록 A: Python 함수 상세 명세

### A.1 stock/search.py

```python
def search(query: str) -> dict:
    """
    종목 검색.

    Args:
        query: 검색어 (이름 또는 코드)

    Returns:
        {
            "ok": True,
            "data": [
                {"ticker": "005930", "name": "삼성전자", "market": "KOSPI"},
                ...
            ]
        }

        또는

        {
            "ok": False,
            "error": {"code": "INVALID_ARG", "msg": "검색어가 필요합니다"}
        }
    """
```

### A.2 stock/analysis.py

```python
def analyze(ticker: str, days: int = 180) -> dict:
    """
    수급 분석.

    Args:
        ticker: 종목 코드
        days: 조회 기간 (일)

    Returns:
        {
            "ok": True,
            "data": {
                "ticker": "005930",
                "name": "삼성전자",
                "dates": ["2024-01-02", ...],
                "mcap": [380000000000000, ...],
                "for_5d": [1500000000, ...],
                "ins_5d": [-500000000, ...]
            }
        }

    Errors:
        - KIS_NOT_INIT: KIS API 미설정
        - TICKER_NOT_FOUND: 종목 없음
        - NO_DATA: 데이터 없음
    """
```

### A.3 indicator/trend.py

```python
def calc(ticker: str, days: int = 180, interval: str = "w") -> dict:
    """
    Trend Signal 계산.

    Args:
        ticker: 종목 코드
        days: 기간
        interval: "d" (일봉) 또는 "w" (주봉)

    Returns:
        {
            "ok": True,
            "data": {
                "ticker": "005930",
                "name": "삼성전자",
                "interval": "w",
                "dates": [...],
                "ohlcv": [[O, H, L, C, V], ...],
                "ma": [...],
                "cmf": [...],
                "fg": [...],
                "buy": [0, 0, 1, 0, ...],
                "aux_buy": [...],
                "sell": [...],
                "aux_sell": [...]
            }
        }
    """
```

---

## 부록 B: App UseCase 상세

### B.1 SearchStockUC

```kotlin
class SearchStockUC @Inject constructor(
    private val repo: StockRepo
) {
    suspend operator fun invoke(query: String): Result<List<Stock>> {
        if (query.isBlank()) {
            return Result.failure(AppError.InvalidArg("검색어를 입력하세요"))
        }
        return repo.search(query)
    }
}
```

### B.2 GetAnalysisUC

```kotlin
class GetAnalysisUC @Inject constructor(
    private val repo: StockRepo
) {
    suspend operator fun invoke(
        ticker: String,
        days: Int = 180,
        useCache: Boolean = true
    ): Result<StockData> {
        // 캐시 체크
        if (useCache) {
            val cached = repo.getCachedAnalysis(ticker, days)
            if (cached != null) return Result.success(cached)
        }
        return repo.getAnalysis(ticker, days)
    }
}
```

---

**End of Specification**
