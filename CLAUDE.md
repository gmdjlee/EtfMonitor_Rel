# CLAUDE.md — MarketMonitor (ETF Monitor)

## Project Identity

Korean stock market (KRX) ETF monitoring Android app.
Kotlin 2.1.0 | Jetpack Compose + M3 | MVVM + Clean Architecture | Hilt 2.54 | Room 2.8.3 (schema v21) | Claude & Gemini AI APIs | KIS Open API (재무정보)

Package: `com.etfmonitor` | DB: `etf_monitor.db` | ~300 Kotlin files | 0 Python scripts
Structure: `core/` (134 files) shared infra, `feature/` (163 files) 7 modules, `navigation/` (1 file)
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
| FearGreedRepositoryImpl | **90s** | 7 parallel kotlin_krx API calls + FearGreedCalculator |
| KrxRepositoryBase | 30s-180s | Configurable per kotlin_krx call |
| MarketOscillatorCalculator | **no timeout** | 200 tickers × Semaphore(5) parallel fetch — use `NonCancellable` in ViewModel |

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
Schema is v21 (20 migrations). All migrations defined inline in `AppDatabase.kt`.
IMPORTANT: Always add migration BEFORE changing schema. Never use `fallbackToDestructiveMigration()`.

### 7. Repository Caching
| Repository | Expiry | Invalidation |
|------------|--------|-------------|
| StockAnalysis | 24h | OR missing today OR <80% days |
| MarketDeposit | 12h | AND latest == today |
| FearGreed | 12h | OR latest != today |
| Financial | 24h | By ticker, manual refresh bypasses cache |

### 8. ViewModel State Exceptions
Most ViewModels use sealed class state. Two exceptions use individual StateFlows (intentional):
- **SettingsViewModel**: 25+ StateFlows (complex config)
- **StatisticsViewModel**: 12+ StateFlows (multi-column sorting)
New ViewModels should use sealed classes.

### 9. AI Integration Rules
- Check `isApiKeyConfigured` before calling AI APIs
- AI parser handles Korean signals: 강력매수, 매수, 중립, 매도, 강력매도
- Handle Gemini `SAFETY`/`RECITATION` blocks (returns empty instead of error)
- API keys stored with AES256-GCM via Android Keystore (`SharedPreferencesApiKeyProvider`, `KisApiKeyProvider`, `FredApiKeyProvider`)

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
| Navigation | `navigation/Navigation.kt` | 17 screen routes |
| Database | `core/database/AppDatabase.kt` | 23 entities, 21 DAOs, 20 migrations (v21) |
| Database entities | `core/database/entities/` | 22 files, 23 entities (AIChatSession in AIChatMessage.kt) |
| Blood Indicator | `core/network/blood/` | BloodIndicatorClient (OkHttp: Yahoo Finance + FRED API) |
| AI clients | `core/network/ai/` | ClaudeApiClient, GeminiApiClient, AIApiClientFactory (11 files) |
| Theme | `core/ui/theme/` | Theme.kt, ThemeManager.kt |
| Workers | `core/worker/` | 9 workers + WorkManagerHelper |
| DI | `core/di/` + `feature/*/di/` | 11 modules (4 core + 7 feature) |
| kotlin_krx repos | `core/data/repository/krx/` | KrxStockDataRepositoryImpl, KrxEtfDataRepositoryImpl, KrxMarketDataRepositoryImpl |
| kotlin_krx UseCases | `core/domain/usecase/krx/` | 11 UseCases (MarketCap, IndexComponents, MarketData, EtfHoldings, EtfList, BusinessDays, TrendSignal, ElderImpulse, DemarkTD, StockOhlcv, IndexData) |
| Analysis engines | `core/analysis/` | FearGreedCalculator, TechnicalAnalysisEngine, MarketOscillatorCalculator, BloodIndicatorCalculator |
| kotlin_krx adapters | `core/data/krx/adapter/` | DateAdapter, KrxErrorMapper, HoldingMapper |
| Data collection | `core/service/DataCollectionService.kt` | Foreground Service with serviceScope |
| KIS Financial Info | `feature/stock/presentation/financial/` | FinancialInfoContent, ProfitabilityContent, StabilityContent |
| KIS API client | `feature/stock/data/repository/financial/` | FinancialRepositoryImpl (OAuth2 + 5 KIS REST APIs, 24h cache) |
| FRED API keys | `core/network/blood/` | FredApiKeyProvider (EncryptedSharedPreferences, AES256-GCM) |
| KIS API keys | `core/network/kis/` | KisApiKeyProvider (EncryptedSharedPreferences), KisApiKeyConfig |
| Tests | `app/src/test/`, `app/src/androidTest/` | JUnit5, MockK, Turbine |

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

### Kotlin Native Computation Engines (replaced Python)

| Engine | Replaces | Key Functions |
|--------|----------|---------------|
| `BloodIndicatorCalculator` (object) | blood_indicator.py | resampleWeeklyFriday, rollingMean, calcSignal (100-week SMA) |
| `BloodIndicatorClient` (@Singleton) | blood_indicator.py HTTP | fetchIrxData (Yahoo), fetchHighYieldSpread (FRED), fetchSpyData (Yahoo) |
| `FearGreedCalculator` (object) | feargreed.py | calcRsi, calcMacd, calcFearGreed, rollingMean5, minMaxNormalize |
| `TechnicalAnalysisEngine` (object) | trend_signal.py | calculateEMA, resampleWeekly/Monthly, calculateCMF, generateSignals, calculateElderImpulse, calculateDemarkTD |
| `MarketOscillatorCalculator` (@Singleton) | market.py Oscillator | analyze (top-200 market cap proxy for index components) |

---

## Known Issues / Future Work

- maxDays 365로 제한 중 (kotlin_krx date chunking 수정 후 730 복원 필요)
- ETF 목록 조회 timeout 60s 증가 검토
- Certificate pin rotation: Anthropic API pin expires 2026-06-30
- API key 가드: NewAIAnalysisViewModel.startNewChat/sendMessage에 isApiKeyConfigured 체크 누락
- Room TypeConverter JSON 파싱 에러 처리 미비
- CorrelationAnalyzer: 전체 테이블 로드 후 메모리 필터링 → `getByDateRangeSuspend` DAO 메서드 필요
- forwardFillToIndex(): O(n*m) → O(n+m) 최적화 가능 (현재 영향 적음)
- TechnicalAnalysisEngineTest: Elder Impulse 경계값 테스트 2개 실패 (pre-existing)

---

## Project Review & Hardening Summary (2026-02-20)

Full reports: `PROJECT_REVIEW.md` (findings), `UPDATE_REPORT.md` (fixes applied)

**Before**: Security 72, Performance 62, Reliability 62, Tests 18 (Overall 53.5/100)
**After**: Security ~89, Performance ~86, Reliability ~91, Tests ~68 (Overall ~83.5/100)

### Resolved (P0 Critical)
- CancellationException: 216+ catch blocks fixed across 59 files
- OkHttp Response leaks: 3 locations wrapped with `response.use {}`
- FRED API key: Migrated from plaintext Room to `FredApiKeyProvider` (EncryptedSharedPreferences)
- NonCancellable: Added to FearGreedViewModel + SettingsViewModel long-running ops
- BackupDao OOM: Monthly date-range chunking for holdings/priceCache, removed getAllHoldingKeys()

### Resolved (P1 High)
- @Transaction: 22 BackupDao batch insert methods
- DB indices: 8 CREATE INDEX via migration v20→v21
- LIMIT clauses: Added to unbounded EtfDao queries
- Network security config: KIS, FRED, Yahoo Finance domains added
- Log redaction: API keys redacted via `redactUrl()`, `android.util.Log` → `AppLogger`
- Backup rules: `fred_api_prefs.xml` + `kis_api_prefs.xml` excluded
- exportSchema=true: Room schema export enabled (kotlinxSerialization 1.7.1→1.8.1)
- CollectionState persistence: SharedPreferences with wasInterrupted detection

### Resolved (P2 Enhancement)
- MarketOscillator parallelism: Semaphore(5) + ISIN cache warmup (~4.8x speedup)
- InMemoryCookieJar thread-safety: synchronized(lock) in kotlin_krx KrxClient

### Resolved (P2 Tests)
- 391 new tests total
- Phase 3: 160 tests (Holding, DateAdapter, BloodIndicatorCalculator, FearGreedCalculator, TechnicalAnalysisEngine)
- Phase 5: 72 tests (AIResponseParser, MarketOscillatorCalculator, StockAnalysisRepositoryImpl, CorrelationAnalyzer)
- Phase 7: 159 tests (Workers 65 + DAOs 94)

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
