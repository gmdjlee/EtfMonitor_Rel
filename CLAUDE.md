# CLAUDE.md — MarketMonitor (ETF Monitor)

## Project Identity

Korean stock market (KRX) ETF monitoring Android app.
Kotlin 2.1.0 | Jetpack Compose + M3 | MVVM + Clean Architecture | Hilt 2.54 | Room 2.8.3 (schema v22) | Claude & Gemini AI APIs | KIS Open API (재무정보) | Kiwoom Open API (실시간 순위 + 장중수급)

Package: `com.etfmonitor` | DB: `etf_monitor.db` | 320 Kotlin files | 0 Python scripts
Structure: `core/` (141 files) shared infra, `feature/` (176 files) 8 modules, `navigation/` (1 file)
Each feature: `domain/{model,repository,usecase}` → `data/{mapper,repository}` → `presentation/` → `di/`

---

## Critical Rules

These are project-specific traps. Violating these causes bugs, crashes, or data corruption.

### 1. IMPORTANT: Holding Entity — Use Factory Only
`Holding` uses compressed storage (`weightBps: Short`, `amountMillion: Int`).
```kotlin
// ✅ ALWAYS use factory
Holding.create(etfTicker, stockTicker, name, date, weight, amount)
// ❌ NEVER construct directly — causes overflow/underflow
```
In SQL queries, convert: `CAST(weightBps AS REAL) / 10000.0`, `CAST(amountMillion AS REAL) * 1000000.0`

### 2. IMPORTANT: StockAnalysisData — JOIN Required
`name` was removed from `stock_analysis_data` in migration v12→13.
```kotlin
// ✅ stockAnalysisDao.getAnalysisDataWithName(ticker)  // JOIN with stocks table
// ❌ stockAnalysisDao.getAnalysisData(ticker)           // name will be null
```

### 3. IMPORTANT: KRX/HTTP Timeouts — Not All 30s
| Client | Timeout | Why |
|--------|---------|-----|
| BloodIndicatorClient | **30s** per HTTP call | Yahoo Finance + FRED API (3 retries) |
| FearGreedRepositoryImpl | **90s** | 7 kotlin_krx API calls (2 batches: 4+3) + FearGreedCalculator |
| KrxRepositoryBase | 30s-180s | Configurable per kotlin_krx call |
| MarketOscillatorCalculator | **no timeout** | N dates × sequential getMarketOhlcv (500ms delay) + 1 getPortfolioTickers — use `NonCancellable` in ViewModel |

### 4. IMPORTANT: FearGreed — Repository Handles 3x Internally
Moving averages lose leading data. `FearGreedRepositoryImpl` internally multiplies by 3x.
```kotlin
// ✅ Pass desired output days — repository multiplies internally
fearGreedRepository.initializeFearGreed(days = 90)  // collects 270 days internally
// ❌ Do NOT pre-multiply — causes 9x over-collection or hits 730-day cap
fearGreedRepository.initializeFearGreed(days = 90 * 3)  // WRONG: becomes 810, capped to 730
```

### 5. DAO Queries — Always Use LIMIT
Ranking queries without LIMIT cause OOM on Android. Existing limits: rankings=500, changes=300, lists=100.

### 6. Database Migrations — Inline in AppDatabase.kt
Schema is v22 (21 migrations). All migrations defined inline in `AppDatabase.kt`.
IMPORTANT: Always add migration BEFORE changing schema. Never use `fallbackToDestructiveMigration()`.

### 7. Repository Caching
| Repository | Expiry | Invalidation |
|------------|--------|-------------|
| StockAnalysis | 24h | OR missing today OR <80% days |
| MarketDeposit | 12h | AND latest == today |
| FearGreed | 12h | OR latest != today |
| Financial | 24h | By ticker, manual refresh bypasses cache |
| RealtimeSupply | 60s | In-memory ConcurrentHashMap, by ticker |

### 8. ViewModel State Exceptions
Most ViewModels use sealed class state. Two exceptions use individual StateFlows (intentional):
- **SettingsViewModel**: 25+ StateFlows (complex config)
- **StatisticsViewModel**: 12+ StateFlows (multi-column sorting)
New ViewModels should use sealed classes.

### 9. AI Integration Rules
- Check `isApiKeyConfigured` before calling AI APIs
- AI parser handles Korean signals: 강력매수, 매수, 중립, 매도, 강력매도
- Handle Gemini `SAFETY`/`RECITATION` blocks (returns empty instead of error)
- API keys stored with AES256-GCM via Android Keystore (`SharedPreferencesApiKeyProvider`, `KisApiKeyProvider`, `FredApiKeyProvider`, `KiwoomApiKeyProvider`)

### 10. kotlin_krx Date Format — Always Convert
kotlin_krx returns dates in `"yyyyMMdd"` format. Room entities and UI expect `"yyyy-MM-dd"` (ISO).
```kotlin
// ✅ Convert before DB storage
val isoDate = DateAdapter.fromKrxFormat(krxDate).toString()  // "20260219" → "2026-02-19"
// ❌ Store raw kotlin_krx dates — SQL BETWEEN queries will fail
```

### 11. Long-Running Data Collection — Use NonCancellable
Data collection (200+ tickers) takes minutes. If bound to `viewModelScope`, user navigation cancels the job.
```kotlin
// ✅ Wrap in NonCancellable so collection completes even if user navigates away
val result = withContext(NonCancellable) { repository.initializeMarketData(...) }
// ❌ Run directly in viewModelScope.launch — back button kills collection
```
Also: never swallow `CancellationException` in catch blocks — always rethrow.

### 12. KIS Financial API — OAuth2 Token + Cache
Financial info uses KIS Open API with OAuth2 client credentials (`/oauth2/tokenP`).
- Token cached in-memory for 23h (Mutex-protected)
- Financial data cached in Room `financial_cache` table with 24h TTL
- KIS API keys stored in separate EncryptedSharedPreferences via `KisApiKeyProvider`
- Check `kisApiKeyProvider.isConfigured()` before calling KIS APIs
- KIS income statement returns **cumulative YTD** values — use `convertYtdToQuarterly()` to get quarterly deltas

### 13. KRX Akamai WAF Rate Limiting — All Sources Must Throttle
KRX CDN returns HTTP 403 "Access Denied" after ~50-65 rapid requests. All KRX data sources use chunked parallel with delays:
| Source | Concurrency | Delay | Pattern |
|--------|-------------|-------|---------|
| MarketOscillatorCalculator | sequential | 500ms per request | 1 getPortfolioTickers + N getMarketOhlcv sequential |
| GetKrxEtfListUseCase | chunked(3) | 500ms per chunk | ETF name lookups in chunks of 3 |
| EtfRepositoryImpl | chunked(3) | 500ms per chunk | ETF holdings fetches in chunks of 3 |
| FearGreedRepositoryImpl | 2 batches (4+3) | 2s between batches | 7 index API calls split into 2 batches |
| MarketOscillatorViewModel | sequential | 15s cooldown | Between KOSPI and KOSDAQ oscillator runs |
| DataCollectionService | sequential | 15s cooldown | Between KOSPI and KOSDAQ in init/update paths |
```kotlin
// ❌ Fire all KRX API calls simultaneously — triggers Akamai WAF 403
// ✅ Use Semaphore(3) + delay(500) or chunked(3) + delay(500) pattern
```

### 14. Chart Color Constants — No Duplicate ARGB Values
`chartDefaultColors` list in `ColorPickerComponents.kt` is used as `LazyRow` items. If two colors have the same ARGB value, `LazyRow` keys collide and the app crashes with `IllegalArgumentException`.
```kotlin
// ❌ ChartBlue = Color(0xFF396663) — same as ChartSecondary, causes LazyRow key collision crash
// ✅ All chart color constants in Color.kt must have unique ARGB values
```

---

## Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install on device
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests (device required)
./gradlew clean                  # Clean build
```

Versions: `gradle/libs.versions.toml` | Build config: `app/build.gradle.kts`
ABI: arm64-v8a, x86_64 only (64-bit)

---

## Key Files

| Category | Path | Notes |
|----------|------|-------|
| Entry | `MainActivity.kt`, `EtfMonitorApp.kt` | Theme, permissions |
| Navigation | `navigation/Navigation.kt` | 18 screen routes, 7-tab bottom nav |
| Database | `core/database/AppDatabase.kt` | 23 entities, 21 DAOs, 21 migrations (v22) |
| Database entities | `core/database/entities/` | 22 files, 23 entities (AIChatSession in AIChatMessage.kt) |
| Blood Indicator | `core/network/blood/` | BloodIndicatorClient (OkHttp: Yahoo Finance + FRED API) |
| AI clients | `core/network/ai/` | ClaudeApiClient, GeminiApiClient, AIApiClientFactory (11 files) |
| Theme | `core/ui/theme/` | Theme.kt, ThemeManager.kt |
| Workers | `core/worker/` | 9 workers + WorkManagerHelper |
| DI | `core/di/` + `feature/*/di/` | 13 modules (5 core + 8 feature) |
| kotlin_krx repos | `core/data/repository/krx/` | KrxStockDataRepositoryImpl, KrxEtfDataRepositoryImpl, KrxMarketDataRepositoryImpl |
| kotlin_krx UseCases | `core/domain/usecase/krx/` | 11 UseCases (MarketCap, IndexComponents, MarketData, EtfHoldings, EtfList, BusinessDays, TrendSignal, ElderImpulse, DemarkTD, StockOhlcv, IndexData) |
| Analysis engines | `core/analysis/` | FearGreedCalculator, TechnicalAnalysisEngine, MarketOscillatorCalculator, BloodIndicatorCalculator |
| kotlin_krx adapters | `core/data/krx/adapter/` | DateAdapter, KrxErrorMapper, HoldingMapper |
| Data collection | `core/service/DataCollectionService.kt` | Foreground Service with serviceScope |
| KIS Financial Info | `feature/stock/presentation/financial/` | FinancialInfoContent, ProfitabilityContent, StabilityContent |
| KIS API client | `feature/stock/data/repository/financial/` | FinancialRepositoryImpl (OAuth2 + 5 KIS REST APIs, 24h cache) |
| FRED API keys | `core/network/blood/` | FredApiKeyProvider (EncryptedSharedPreferences, AES256-GCM) |
| KIS API keys | `core/network/kis/` | KisApiKeyProvider (EncryptedSharedPreferences), KisApiKeyConfig |
| API Key Dialog | `feature/home/presentation/component/ApiKeyInputDialog.kt` | First-launch key input: KIS + FRED + Kiwoom + AI (shown before UnifiedInitializationDialog if keys not set) |
| Kiwoom API | `core/network/kiwoom/` | KiwoomApiClient, KiwoomTokenManager, KiwoomApiKeyProvider (6 files) |
| Kiwoom DI | `core/di/KiwoomModule.kt` | @KiwoomOkHttp OkHttpClient (30s timeout) |
| Ranking feature | `feature/ranking/` | 5 ranking types via Kiwoom REST API (12 files: domain/data/presentation/di) |
| Realtime Supply | `feature/stock/*/realtime/` + `feature/stock/di/RealtimeSupplyModule.kt` | 장중수급 (ka10063): ViewModel + Compose UI + 60s auto-refresh (9 files) |
| Chart Color Settings | `feature/settings/presentation/component/ChartColorCards.kt`, `ColorPickerComponents.kt` | 4 chart color cards + color picker dialog |
| KRX Constants | `core/common/util/KrxConstants.kt` | Shared KRX rate limit constants |
| Tests | `app/src/test/`, `app/src/androidTest/` | JUnit5, MockK, Turbine (1861 tests, 100+ test files + 5 androidTest files) |

---

## Do NOT (project-specific mistakes)

| Do NOT | Do Instead |
|--------|-----------|
| Construct `Holding(...)` directly | Use `Holding.create()` factory method |
| Query `stock_analysis_data` without JOIN | Use `getAnalysisDataWithName()` (JOIN with stocks) |
| Use 30s timeout for all KRX calls | Use KrxRepositoryBase with configurable timeouts (30s-180s) |
| Pre-multiply days for FearGreed (repository does 3x internally) | Pass desired output days only: `initializeFearGreed(days = 90)` |
| Write ranking queries without LIMIT | Add LIMIT clause (OOM on Android) |
| Expose `MutableStateFlow` publicly | Use `_state` private + `state: StateFlow` public via `.asStateFlow()` |
| Use `LiveData` | This project uses `StateFlow` exclusively |
| Omit dispatcher on DB/network calls | Always `withContext(Dispatchers.IO)` |
| Call AI without checking API key | Check `isApiKeyConfigured` first |
| Assume English signal names only | Parser handles Korean: 강력매수, 매수, 중립, 매도, 강력매도 |
| Create ViewModels manually | Use `hiltViewModel()` in Composables |
| Change DB schema without migration | Add migration in AppDatabase.kt first |
| Add unrequested features or refactoring | Make ONLY the requested changes |
| Over-engineer with abstractions | Minimum complexity for current task |
| Store kotlin_krx dates without conversion | Convert yyyyMMdd → yyyy-MM-dd via `DateAdapter.fromKrxFormat()` |
| Run long data collection in viewModelScope | Wrap with `NonCancellable` or use DataCollectionService |
| Catch Exception without rethrowing CancellationException | Always `catch (e: CancellationException) { throw e }` first |
| Fire parallel KRX requests without rate limiting | Use Semaphore(3) + delay(500ms) or chunked(3) + delay(500ms) pattern |
| Use ARGB color values as LazyRow/LazyColumn keys | Use index or unique identifiers — duplicate colors crash with IllegalArgumentException |

---

## Model Routing Rules

Default model: **sonnet**. Use tiered agents in `.claude/agents/` for cost-efficient delegation.

### Agent Tier Map

| Agent | Model | Cost | Use When |
|-------|-------|------|----------|
| `explorer` | haiku | $ | "Where is X?", file search, dependency tracing, codebase questions |
| `doc-writer` | haiku | $ | CLAUDE.md updates, KDoc, CHANGELOG, documentation |
| `implementer` | sonnet | $$ | Feature implementation, bug fixes, refactoring |
| `test-writer` | sonnet | $$ | Unit/integration test generation |
| `reviewer` | opus | $$$$ | Architecture review, security audit, PR review |

### Escalation Criteria (→ opus reviewer)

- Changes touch **security-critical paths**: `SharedPreferencesApiKeyProvider`, `KisApiKeyProvider`, `*ApiClient.kt`
- Database migration changes (schema integrity)
- Changes span **4+ feature modules** simultaneously
- Performance-sensitive code (DAO queries, kotlin_krx, caching logic)
- Pre-merge review for PRs with 10+ file changes

### De-escalation Criteria (→ haiku)

- Read-only operations: finding files, tracing imports, answering "where is X?"
- Documentation-only changes: KDoc, comments, CLAUDE.md, CHANGELOG
- Simple renames or string changes
- Counting files, listing dependencies, summarizing structure

### Always Sonnet+ (never haiku)

- Any code modification (Write/Edit operations)
- Kotlin logic changes
- Build configuration changes (`build.gradle.kts`, `libs.versions.toml`)
- Test writing and execution

---

## Coding Philosophy

- **Minimal Engineering**: Only requested changes. No unrequested features, refactoring, or "improvements"
- **Trust Framework**: No error handling for impossible scenarios. Validate only at boundaries
- **No Premature Abstraction**: No helpers/utilities for one-time tasks. No hypothetical future design
- **Read Before Edit**: Always read the full file before making changes
- **Follow Existing Patterns**: Sealed state classes, StateFlow, Hilt injection, Clean Architecture layers

---

## Architecture & Data Sources

### kotlin_krx (Native KRX Data — Korean Network Only)

**Library**: `include(":kotlin-krx")` local Gradle module (github.com/gmdjlee/kotlin_krx)
**DI**: `KrxModule` provides `KrxClient`, `KrxStock`, `KrxEtf`, `KrxIndex`, `TickerCache` as Hilt singletons

**Key Behaviors**:
- Date chunking: 365-day max per request (auto-splits via `fetchByDateChunks()`)
- ISIN resolution: Transparent 6-digit ticker → ISIN translation
- Error handling: `KrxError` sealed class (NetworkError, ParseError, InvalidDateError)
- Network restriction: Korean network only (returns "LOGOUT" from overseas)
- Date format: Returns `"yyyyMMdd"` — must convert to ISO before DB storage

**Architecture**: ViewModel → UseCase → Repository (extends KrxRepositoryBase) → kotlin_krx API

### Python Dependencies — FULLY REMOVED

**Chaquopy embedded Python has been completely removed** from this project.
All former Python scripts have been replaced by native Kotlin implementations:

### Kiwoom Open API (실시간 순위 — Any Network)

**Module**: `core/network/kiwoom/` (6 files) + `feature/ranking/` (12 files)
**DI**: `KiwoomModule` provides `@KiwoomOkHttp OkHttpClient` (30s timeout), `RankingModule` binds `RankingRepository`

**API Endpoints** (all POST to `$baseUrl/api/dostk/rkinfo`):
| Ranking Type | API ID | Description |
|-------------|--------|-------------|
| 호가잔량급증 | ka10021 | Order book surge |
| 거래량급증 | ka10023 | Volume surge |
| 거래량상위 | ka10030 | Daily volume top |
| 신용잔고율상위 | ka10033 | Credit ratio top |
| 외국인/기관순매수 | ka90009 | Foreign/institution trading |
| 장중투자자별매매 | ka10063 | Realtime investor supply/demand (per-stock) |

**Key Behaviors**:
- OAuth2 token: `POST $baseUrl/oauth2/token` (api-id: au10001), cached in-memory with Mutex
- Rate limiting: 500ms min interval via `CategoryRateLimiter` (Mutex + delay)
- Token retry: 3 attempts with exponential backoff (1s, 2s, 4s)
- Auto-retry on 401/403 with token refresh
- JSON normalization: strips `+` prefix from numbers (`"+12345"` → `"12345"`)
- API keys: EncryptedSharedPreferences via `KiwoomApiKeyProvider` (AES256-GCM)
- Investment modes: MOCK (`mockapi.kiwoom.com`), PRODUCTION (`api.kiwoom.com`)
- CancellationException rethrow guard in all catch blocks

**Architecture**: ViewModel (12 StateFlows) → GetRankingUseCase → RankingRepository → KiwoomApiClient → OkHttp
**Realtime Supply**: RealtimeSupplyViewModel (sealed state) → GetRealtimeSupplyUseCase → RealtimeSupplyRepository → KiwoomApiClient (ka10063) → OkHttp. Auto-refresh 60s during trading hours (09:00-15:30 KST Mon-Fri).
**UI**: 7-tab bottom nav (시장지표, 순위, ETF, 홈, 종목, 분석, 설정), Korean stock colors (Red=Up/Buy, Blue=Down/Sell)

### Kotlin Native Computation Engines (replaced Python)

| Engine | Replaces | Key Functions |
|--------|----------|---------------|
| `BloodIndicatorCalculator` (object) | blood_indicator.py | resampleWeeklyFriday, rollingMean, calcSignal (100-week SMA) |
| `BloodIndicatorClient` (@Singleton) | blood_indicator.py HTTP | fetchIrxData (Yahoo), fetchHighYieldSpread (FRED), fetchSpyData (Yahoo) |
| `FearGreedCalculator` (object) | feargreed.py | calcRsi, calcMacd, calcFearGreed, rollingMean5, minMaxNormalize |
| `TechnicalAnalysisEngine` (object) | trend_signal.py | calculateEMA, resampleWeekly/Monthly, calculateCMF, generateSignals, calculateElderImpulse, calculateDemarkTD |
| `MarketOscillatorCalculator` (@Singleton) | market.py Oscillator | analyze (pykrx _calc(): KOSPI200/KOSDAQ150 component filtering + vol/pts dual-weighted nonlinear transform → [-100,-50]∪(50,100]) |

---

## Known Issues / Future Work

- maxDays 365로 제한 중 (kotlin_krx date chunking 수정 후 730 복원 필요)
- ETF 목록 조회 timeout 60s 증가 검토
- Certificate pin rotation: Anthropic API pin expires 2026-06-30
- First-launch flow has TWO dialogs: ApiKeyInputDialog (KIS + FRED + Kiwoom + AI keys, all optional) → UnifiedInitializationDialog
- AndroidView `factory` 블록의 axis/legend 색상이 `update` 블록에서 갱신 안 됨 (chart color 변경 시 축/범례 색상 stale)
- KIS appKey/appSecret sent as plaintext HTTP headers (KIS API design limitation — cert pinning not recommended due to unknown KIS cert rotation schedule)
- Database not encrypted at rest (risk-acceptance: only API keys need protection, not market data)

---

## Project Review & Hardening Summary (2026-02-22)

| Category | Before | After (v1) | Final (v3) | Key Improvements |
|----------|--------|------------|------------|------------------|
| Security | 72 | 96 | **98** | CE guards (216+), AES256-GCM (4 providers), backup exclusions (4/4), network security config (7 domains), PasswordVisualTransformation, RetryHelper CE guard |
| Performance | 62 | 95 | **98** | KRX rate limiting, DB indices (11개), LIMIT clauses (all DAOs), O(n) running-sum (FearGreed+TechnicalAnalysis), LazyColumn keys, Oscillator 4.8x speedup |
| Reliability | 91 | 96 | **98** | NonCancellable wraps, @Transaction (22 methods), CE rethrow everywhere, safe-navigation (no !!), Elder Impulse tests fixed, 0 test failures |
| Test Coverage | 18 | 95 | **96** | 1861 tests (100+ files), 16/16 ViewModels, 35/35 UseCases, 25/25 Repositories, 0 failures |
| **Overall** | **53.5** | **~95.5** | **~97.5** | |

---

## Compaction Instructions

When context is compacted, PRESERVE:
- All Critical Rules (especially Holding factory, StockAnalysisData JOIN, KRX timeouts, FearGreed internal 3x, date format conversion, NonCancellable)
- Do NOT table (project-specific mistakes)
- Project Identity (package name, DB name, structure)
- Commands section

DISCARD during compaction:
- Key Files table (can be re-discovered via Glob)
- Coding Philosophy (Claude retains behavioral context)
- Architecture & Data Sources (can be re-discovered)
- This Compaction Instructions section itself
