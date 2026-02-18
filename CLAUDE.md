# CLAUDE.md — MarketMonitor (ETF Monitor)

## Project Identity

Korean stock market (KRX) ETF monitoring Android app.
Kotlin 2.1.0 | Jetpack Compose + M3 | MVVM + Clean Architecture | Hilt 2.54 | Room 2.8.3 (schema v19) | Chaquopy (embedded Python) | Claude & Gemini AI APIs

Package: `com.etfmonitor` | DB: `etf_monitor.db` | ~255 Kotlin files | 8 Python scripts
Structure: `core/` (97 files) shared infra, `feature/` (155 files) 6 modules, `navigation/` (1 file)
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

### 3. IMPORTANT: Python Timeouts — Not All 30s
| Client | Timeout | Why |
|--------|---------|-----|
| PyKrxClient | 30s | Standard (2 retries for holdings) |
| MarketIndexPyClient | 30s | Standard |
| BloodIndicatorPyClient | **90s** | 100-week SMA calculation |
| EnhancedPredictorClient | **120s** | ML ensemble training (28 features) |
| OscillatorPyClient | **180s** | Collects 200+ component stocks |

### 4. IMPORTANT: FearGreed — Request 3x Days
Moving averages lose leading data. To get N days of Fear & Greed data:
```kotlin
fearGreedRepository.initializeFearGreed(days = N * 3)
```

### 5. DAO Queries — Always Use LIMIT
Ranking queries without LIMIT cause OOM on Android. Existing limits: rankings=500, changes=300, lists=100.

### 6. Database Migrations — Inline in AppDatabase.kt
Schema is v19 (18 migrations). All migrations defined inline in `AppDatabase.kt`.
IMPORTANT: Always add migration BEFORE changing schema. Never use `fallbackToDestructiveMigration()`.

### 7. Repository Caching
| Repository | Expiry | Invalidation |
|------------|--------|-------------|
| StockAnalysis | 24h | OR missing today OR <80% days |
| MarketDeposit | 12h | AND latest == today |
| FearGreed | 12h | OR latest != today |

### 8. ViewModel State Exceptions
Most ViewModels use sealed class state. Two exceptions use individual StateFlows (intentional):
- **SettingsViewModel**: 25+ StateFlows (complex config)
- **StatisticsViewModel**: 12+ StateFlows (multi-column sorting)
New ViewModels should use sealed classes.

### 9. AI Integration Rules
- Check `isApiKeyConfigured` before calling AI APIs
- AI parser handles Korean signals: 강력매수, 매수, 중립, 매도, 강력매도
- Handle Gemini `SAFETY`/`RECITATION` blocks (returns empty instead of error)
- API keys stored with AES256-GCM via Android Keystore (`SharedPreferencesApiKeyProvider`)

### 10. Python Calls — Always withContext(IO) + withTimeout
```kotlin
suspend fun fetch() = withContext(Dispatchers.IO) {
    withTimeout(30_000L) { module.callAttr("fn").toString() }
}
```
Use `Json { ignoreUnknownKeys = true }` for Python JSON parsing.

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
| Entry | `MainActivity.kt`, `EtfMonitorApp.kt` | Theme, permissions, Python init |
| Navigation | `navigation/Navigation.kt` | 14 screen routes |
| Database | `core/database/AppDatabase.kt` | 21 entities, 18 DAOs, 18 migrations (v19) |
| Database entities | `core/database/entities/` | 20 files (AIChatSession in AIChatMessage.kt) |
| Python scripts | `app/src/main/python/` | 8 files: etfcollector, stocks, market, feargreed, deposit_scraper, trend_signal, core, logger |
| Python bridge | `core/network/python/` | OscillatorPyClient, MarketIndexPyClient, BloodIndicatorPyClient (PyKrxClient removed) |
| AI clients | `core/network/ai/` | ClaudeApiClient, GeminiApiClient, AIApiClientFactory (11 files) |
| Theme | `core/ui/theme/` | Theme.kt, ThemeManager.kt |
| Workers | `core/worker/` | 8 workers + WorkManagerHelper |
| DI | `core/di/` + `feature/*/di/` | 11 modules total (5 core: AppModule, DatabaseModule, NetworkModule, WorkerModule, KrxModule + 6 feature) |
| kotlin_krx integration | `core/data/repository/krx/` | 3 repositories + 3 UseCases (T-007, T-008) |
| kotlin_krx UseCases | `core/domain/usecase/krx/` | GetKrxMarketCapUseCase, GetKrxIndexComponentsUseCase, GetKrxMarketDataUseCase |
| Tests | `app/src/test/`, `app/src/androidTest/` | JUnit5, MockK, Turbine |

---

## Do NOT (project-specific mistakes)

| Do NOT | Do Instead |
|--------|-----------|
| Construct `Holding(...)` directly | Use `Holding.create()` factory method |
| Query `stock_analysis_data` without JOIN | Use `getAnalysisDataWithName()` (JOIN with stocks) |
| Use 30s timeout for `getMarketOscillator()` | Use 180s — collects 200+ stocks |
| Use 30s timeout for ML prediction | Use 120s — ensemble training is expensive |
| Request exact days for FearGreed | Request 3x days (MA data loss) |
| Write ranking queries without LIMIT | Add LIMIT clause (OOM on Android) |
| Expose `MutableStateFlow` publicly | Use `_state` private + `state: StateFlow` public via `.asStateFlow()` |
| Use `LiveData` | This project uses `StateFlow` exclusively |
| Omit dispatcher on DB/Python/network calls | Always `withContext(Dispatchers.IO)` |
| Call AI without checking API key | Check `isApiKeyConfigured` first |
| Assume English signal names only | Parser handles Korean: 강력매수, 매수, 중립, 매도, 강력매도 |
| Create ViewModels manually | Use `hiltViewModel()` in Composables |
| Change DB schema without migration | Add migration in AppDatabase.kt first |
| Add unrequested features or refactoring | Make ONLY the requested changes |
| Over-engineer with abstractions | Minimum complexity for current task |

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

- Changes touch **security-critical paths**: `SharedPreferencesApiKeyProvider`, `*ApiClient.kt`, `*PyClient.kt` callAttr()
- Database migration changes (schema integrity)
- Changes span **4+ feature modules** simultaneously
- Performance-sensitive code (DAO queries, Python bridge, caching logic)
- Pre-merge review for PRs with 10+ file changes

### De-escalation Criteria (→ haiku)

- Read-only operations: finding files, tracing imports, answering "where is X?"
- Documentation-only changes: KDoc, comments, CLAUDE.md, CHANGELOG
- Simple renames or string changes
- Counting files, listing dependencies, summarizing structure

### Always Sonnet+ (never haiku)

- Any code modification (Write/Edit operations)
- Kotlin/Python logic changes
- Build configuration changes (`build.gradle.kts`, `libs.versions.toml`)
- Test writing and execution
- Migration authoring

### Routing Decision Tree

```
Is it read-only exploration?
  YES → explorer (haiku)
  NO → Is it documentation-only?
    YES → doc-writer (haiku)
    NO → Is it test writing?
      YES → test-writer (sonnet)
      NO → Is it a review/audit?
        YES → Does it touch security paths or 4+ modules?
          YES → reviewer (opus)
          NO → reviewer (opus) — reviews always use opus
        NO → implementer (sonnet)
```

---

## Coding Philosophy

- **Minimal Engineering**: Only requested changes. No unrequested features, refactoring, or "improvements"
- **Trust Framework**: No error handling for impossible scenarios. Validate only at boundaries
- **No Premature Abstraction**: No helpers/utilities for one-time tasks. No hypothetical future design
- **Read Before Edit**: Always read the full file before making changes
- **Follow Existing Patterns**: Sealed state classes, StateFlow, Hilt injection, Clean Architecture layers

---

## Compaction Instructions

When context is compacted, PRESERVE:
- All 10 Critical Rules (especially Holding factory, StockAnalysisData JOIN, Python timeouts, FearGreed 3x)
- Do NOT table (project-specific mistakes)
- Project Identity (package name, DB name, structure)
- Commands section

DISCARD during compaction:
- Key Files table (can be re-discovered via Glob)
- Coding Philosophy (Claude retains behavioral context)
- This Compaction Instructions section itself

## Model Routing Rules
- Codebase exploration → use explorer subagent (haiku)
- kotlin_krx integration → Integrator teammate (sonnet)
- Test/performance/stability → QA-Engineer teammate (sonnet)
- Architecture decisions → Architect-Reviewer teammate (opus)
- Documentation updates → use doc-writer subagent (haiku)

## Migration Context: pykrx → kotlin_krx

**Status**: ✅ Phase 1-3 COMPLETE | ✅ Phase A COMPLETE | ✅ **Market Migration COMPLETE** | ✅ **pykrx REMOVED**
**Target**: github.com/gmdjlee/kotlin_krx (native Kotlin replacement for pykrx)
**Architecture**: MVVM + Clean Architecture + Feature modules
**DI Framework**: Hilt
**Key Achievement**: **PyKrxClient completely removed** - No Python dependency for KRX data fetching
**API Coverage**: 100% (11/11 pykrx functions covered via kotlin_krx)
**Primary Documents**: MIGRATION_STRATEGY.md (Phase 1-2), docs/PHASE3_MIGRATION_STRATEGY.md (Phase 3), PHASE_A_COMPLETION_REPORT.md (Phase A)
**All Phases Complete**: T-006~T-013 ✅ | Market migration ✅ | pykrx removed ✅
**Post-Migration Fixes**: Zero-data bug ✅ | Investor trading data ✅ | Chart period selection ✅

### Python Bridge Architecture (4 Patterns - PyKrxClient Removed)

**PyClient bridge classes (3 - JSON-based):**
1. ~~`PyKrxClient`~~ - ✅ **REMOVED** (Phase A: Replaced by kotlin_krx UseCases)
2. `OscillatorPyClient` - Multi-module consumer (used by 7 classes across 3 features)
3. `MarketIndexPyClient` - Market index data (market.py)
4. `BloodIndicatorPyClient` - Blood indicator data (blood_indicator.py)

**Direct Python.getInstance() pattern (1 - PyObject manipulation):**
5. `FearGreedRepositoryImpl` - **TIGHTEST COUPLING** - Bypasses PyClient layer, directly manipulates PyObject/DataFrame

### Phase 1 Deliverables (Architect-Approved)

**Completed Tasks (5/5)**:
- [x] T-001: pykrx usage analysis (5 scripts, 4 PyClients, 11 functions)
- [x] T-002: kotlin_krx API review (90.9% coverage, 1 gap, 5 behavioral differences)
- [x] T-003: API mapping document (10 function mappings, 5 adapters, AD-003 resolution)
- [x] T-004: Module structure design (Hilt DI, Gradle integration, phasing strategy)
- [x] T-005: Comprehensive migration strategy (MIGRATION_STRATEGY.md)

**Architectural Decisions (5 - ALL RESOLVED)**:

**AD-001: kis_client.py Scope** ✅ RESOLVED (T-002)
- **Decision**: KEEP kis_client.py as complementary data source
- **Rationale**: kotlin_krx uses KRX Open Data API (Korean network only, historical), kis_client uses KIS Open API (global access, real-time)
- **Migration Scope**: Only pykrx-dependent scripts migrate to kotlin_krx

**AD-002: Architecture Violations** ✅ RESOLVED (T-012)
- **Issue**: 3 ViewModels directly inject Python clients (violates Clean Architecture)
- **Original Plan**: Refactor in Phase 2 (T-009) to inject UseCases instead
- **T-008 Finding**: ViewModels use searchStock(), getTrendSignalData(), getElderImpulseData(), getDemarkTDData() - functions that DON'T exist in kotlin_krx
- **T-012 Solution**: Created TechnicalAnalysisEngine + StockDataRepository + 4 UseCases to provide missing functionality
- **Resolution**:
  - `OscillatorViewModel`: Now injects 3 UseCases (GetTrendSignalDataUseCase, GetElderImpulseDataUseCase, GetDemarkTDDataUseCase)
  - `StockTrendViewModel`: Removed unused `val pyClient: OscillatorPyClient`
  - `AggregatedStockTrendViewModel`: Removed unused `val pyClient: OscillatorPyClient`
- **Clean Architecture Compliance**: All ViewModels now properly inject domain layer UseCases instead of data layer clients

**AD-003: Index Portfolio Gap** ✅ RESOLVED (T-003)
- **Issue**: `get_index_portfolio_deposit_file` has NO kotlin_krx equivalent
- **Decision**: Use `KrxStock.getMarketCap()` top-N proxy for index components
- **Implementation**: Top 200 by market cap ≈ index components (KOSPI 200, KOSDAQ 150)
- **Enables**: Full Oscillator migration to kotlin_krx (no Python fallback needed)

**AD-004: JSON Library Conflict** ✅ RESOLVED (T-004)
- **Issue**: MarketMonitor uses kotlinx.serialization, kotlin_krx uses Gson
- **Decision**: KEEP BOTH libraries
- **Rationale**: Gson ALREADY EXISTS in APK via google-api-client-gson dependency (app/build.gradle.kts:169)
- **Actual APK Cost**: Near-zero (not +1MB as initially estimated)

**AD-005: Module Architecture** ✅ RESOLVED (T-004)
- **Decision**: Single app module + kotlin_krx as local Gradle library
- **Rationale**: Current scale (~255 Kotlin files) doesn't justify multi-module split
- **Implementation**: include(":kotlin-krx") with projectDir reference

### Phase 2 Readiness (Iterations 7+)

**Prerequisites Met**:
- ✅ kotlin_krx API compatibility verified (90.9% coverage)
- ✅ Dependency conflicts identified and resolved (Gson, coroutines)
- ✅ DI integration strategy designed (KrxModule with @Qualifier)
- ✅ Adapter layer specified (5 adapters including Holding mapper)
- ✅ Architecture violations identified (3 ViewModels to refactor)
- ✅ Rollback strategy defined (dual-path coexistence)

**Phase 2 Strategy (T-006 to T-010)**:
- **Coexistence Approach**: Add kotlin_krx alongside Python without breaking changes
- **DI-Based Feature Flags**: Route between Python and Kotlin implementations
- **Success Criteria**: Both Python and Kotlin paths functional, all tests pass

### Phase 2 Deliverables (Iterations 7-10) - ✅ COMPLETE

**Completed Tasks (4/5)**:
- [x] T-006: Gradle integration + KrxModule DI (6 providers: KrxOkHttpClient, KrxClient, TickerCache, KrxStock, KrxEtf, KrxIndex)
- [x] T-007: Repository layer (3 repositories + 3 adapters: KrxErrorMapper, DateAdapter, HoldingMapper)
- [x] T-008: UseCase layer (3 UseCases: GetKrxMarketCapUseCase, GetKrxIndexComponentsUseCase, GetKrxMarketDataUseCase)
- [x] T-009: Coexistence validation (BUILD SUCCESS 7m 12s, Python + kotlin_krx dual paths functional, Phase 3 strategy documented)

**Blocked**:
- [ ] T-010: Python dependency removal (BLOCKED - deferred to Phase 4 after T-011/T-012/T-013 feature migration complete)

**Phase 2 Summary**:
- **Duration**: 4 iterations (7-10), on schedule
- **Build Status**: ✅ SUCCESS (7m 12s, no regression)
- **Hilt DI**: ✅ 3 UseCases + 3 Repositories + 6 Providers (all injectable via @Inject constructor)
- **Coexistence**: ✅ Python bridge unchanged, kotlin_krx operational
- **Documentation**: ✅ Phase 3 migration strategy (5.7KB, risk assessment + rollback plan)

**Key Technical Achievements**:
- ✅ **Holding.create() Compliance**: HoldingMapper uses factory pattern (CLAUDE.md Rule #1)
- ✅ **Timeout Patterns**: KrxRepositoryBase.krxCall() uses configurable timeouts (30s-180s, CLAUDE.md Rule #3)
- ✅ **Dispatcher Usage**: All repository operations use withContext(Dispatchers.IO) (CLAUDE.md Rule #10)
- ✅ **Error Handling**: Result<T> pattern with KrxErrorMapper, fail-fast strategy in multi-market queries
- ✅ **AD-003 Resolution**: Index components via top-N market cap proxy (getMarketCap + topN parameter)

**Technical Debt Acknowledged**:
- **C2 (T-008)**: UseCases inject concrete *RepositoryImpl classes instead of interfaces
  - Rationale: Coexistence phase shortcut, Clean Architecture interfaces deferred to Phase 3
  - Remediation: T-011/T-012/T-013 will create repository interfaces + @Binds modules

**Architectural Findings**:
- **AD-002 Scope Mismatch** (T-008 Architect review): ViewModels use Python functions (searchStock, getTrendSignalData) that don't exist in kotlin_krx
  - Original T-009 plan (ViewModel refactoring) is impossible without feature redesign
  - Revised T-009 scope: Coexistence validation only
  - Full AD-002 resolution deferred to Phase 3 (T-011/T-012/T-013)

**Implementation Order**:
1. T-006: Gradle integration + KrxModule (Hilt singletons with @KrxOkHttp qualifier)
2. T-007: Repository implementations (coexist with PyClient implementations)
3. T-008: UseCase creation for krx data operations
4. T-009: ViewModel refactoring (fix 3 architecture violations)
5. T-010: Dual-path validation

**Outstanding Advisories**:
- W1: Verify Gson version alignment via `./gradlew app:dependencies`
- W5: Confirm kotlin_krx compatibility with coroutines 1.10.2
- W6: Account for Korean network restriction in testing

### Migration Scope

**pykrx-dependent (5 Python scripts to migrate):**
- `core.py`, `etfcollector.py`, `stocks.py`, `market.py`, `trend_signal.py`
- Uses: `get_market_ticker_list`, `get_market_ohlcv`, `get_market_ticker_name`, `get_etf_ticker_list`, `get_etf_ticker_name`, `get_etf_portfolio_deposit_file`, `get_market_cap`, `get_market_trading_value_by_date`, `get_index_ohlcv`, `get_index_portfolio_deposit_file`

**Non-pykrx (5 Python scripts - OUT OF SCOPE):**
- `feargreed.py` (KRX API), `deposit_scraper.py` (Naver), `blood_indicator.py` (Yahoo/FRED), `kis_client.py` (KIS API), `logger.py` (utility)

**Test migration strategy:**
- **MIGRATE**: `PyKrxClientTest.kt`, `EtfRepositoryImplTest.kt`
- **KEEP AS-IS**: `FearGreedRepositoryImplTest.kt`, `CorrelationAnalyzerTest.kt`, `ApiKeyProviderKisTest.kt`, `SettingsViewModelKisTest.kt`
- **VERIFY AFTER**: `HomeViewModelTest.kt`

### Migration Risks (Updated After Phase A)

**Remaining coupling risks:**
1. ~~**CRITICAL GAP**: `get_index_portfolio_deposit_file`~~ ✅ **RESOLVED** (T-012: kotlin_krx getIndexPortfolio API added)
2. **FearGreedRepositoryImpl** - Direct PyObject/DataFrame manipulation (special handling required)
3. **OscillatorPyClient** - Used by 7 classes across 3 features (accepted as permanent Python dependency)
4. ~~**PyKrxClient**~~ ✅ **COMPLETELY REMOVED** (Phase A: 100% migration to kotlin_krx)
5. ~~**Architecture violations**~~ ✅ **RESOLVED** (T-012: All ViewModels now use UseCases)
6. **Dependency conflicts**: Gson vs kotlinx.serialization (~1MB) ✅ **ACCEPTED** (Gson already in APK via google-api-client-gson)

### Build Configuration Changes

**Target for removal in `app/build.gradle.kts`:**
```kotlin
chaquopy {
    defaultConfig {
        pip {
            install("pykrx")  // REMOVE after kotlin_krx integration
        }
    }
}
```

**Note**: Other Python dependencies (pandas, requests, beautifulsoup4, scikit-learn) remain for non-pykrx scripts.

### kotlin_krx Integration (T-002 Findings)

**Tech Stack**:
- Kotlin: 2.x (compatible with MarketMonitor 2.1.0)
- OkHttp: 4.12.0 (matches MarketMonitor)
- Gson: 2.10.1 (conflict with kotlinx.serialization)
- Coroutines: 1.7.3 (must align to 1.10.2)
- kotlinx-datetime: 0.5.0 (new dependency ~50KB)
- Library Type: JVM (`kotlin("jvm")`), not Android (requires local module integration)

**Key Behaviors**:
- Date chunking: 365-day limit (auto-splits queries via `fetchByDateChunks()`)
- ISIN resolution: Internal translation from 6-digit ticker to ISIN code (transparent to caller)
- Error handling: Throws `KrxError` sealed class (NetworkError, ParseError, InvalidDateError) vs pykrx empty DataFrames
- Network restriction: Korean network only (returns "LOGOUT" from overseas)

**Integration Requirements (T-006)**:
- Add kotlin_krx as local Gradle module
- Hilt singletons: `@Singleton OkHttpClient`, `@Singleton TickerCache`
- Repository adapters: Convert `KrxError` → app error states, pandas DataFrame → Kotlin data classes → Room entities

### Phase 3 Deliverables (Iterations 11-14) - 🔄 IN PROGRESS

**Completed Tasks (2/3 feature migrations)**:
- [x] T-011: ETF feature migration (partial - 2 of 3 PyKrxClient methods migrated)
- [x] T-012: Oscillator feature migration (deferred - API gap, budget constraint)
- [ ] T-013: Stock analysis feature migration (pending)

#### T-011: ETF Feature Migration (Partial Python Dependency)

**Iteration 11, Build: SUCCESS (7m 19s), QA Confidence: 92%**

**Migrated to kotlin_krx**:
- ✅ **ETF holdings**: `PyKrxClient.getHoldings()` → `GetKrxEtfHoldingsUseCase` wrapping `KrxEtf.getPortfolio()`
- ✅ **ETF filtered list**: `PyKrxClient.getFilteredEtfList()` → `GetKrxEtfListUseCase` with parallel name lookups + client-side filtering

**Python Dependency Status**: ✅ **COMPLETELY REMOVED via Phase A**
- ~~`PyKrxClient.getBusinessDays(days)`~~ → **Migrated to GetKrxBusinessDaysUseCase**
- **kotlin_krx API**: `KrxIndex.getBusinessDays(startDate, endDate)` (added in commit 79d03bb)
- **Migration**: Phase A (Iteration 15), Build: SUCCESS (1m 10s)
- **Impact**: 2 call sites replaced (EtfRepositoryImpl lines 396, 502)
- **Achievement**: **100% pykrx migration complete** (91.7% → 100%)

**Implementation Details**:
- **Created**: 2 UseCases with `@Inject` constructors (GetKrxEtfHoldingsUseCase, GetKrxEtfListUseCase)
- **Modified**: EtfRepositoryImpl.kt (3 PyKrxClient calls → 2 UseCase calls + 1 kept), EtfModule.kt (inject 2 UseCases)
- **Performance Trade-off**: Client-side ETF filtering via parallel name lookups (N API calls, chunked with PARALLEL_LIMIT=10)
- **Critical Fix** (Revision 1 after Architect review):
  - C1: Return type fixed (`List<String>` → `Result<List<Etf>>` with full entity construction)
  - C2: Filtering by ETF name (Korean keywords) instead of ticker codes
  - W1: EtfModule.kt DI wiring added

**Files Modified**: 7 files (T-011 + Phase A)
- `core/domain/usecase/krx/GetKrxEtfHoldingsUseCase.kt` (created, 25 lines)
- `core/domain/usecase/krx/GetKrxEtfListUseCase.kt` (created, 68 lines)
- `core/domain/usecase/krx/GetKrxBusinessDaysUseCase.kt` (created Phase A, 60 lines)
- `feature/etf/data/repository/EtfRepositoryImpl.kt` (modified, 3 PyKrxClient calls → 3 UseCase calls)
- `feature/etf/di/EtfModule.kt` (modified, PyKrxClient removed, 3 UseCases injected)
- `test/.../EtfRepositoryImplTest.kt` (modified Phase A, PyKrxClient → GetKrxBusinessDaysUseCase)
- `androidTest/.../KrxApiFunctionalityTest.kt` (unchanged, retains PyKrxClient for integration testing)

**T-010 Impact**: ✅ **UNBLOCKED** - PyKrxClient completely removed from production code via Phase A.

#### T-012: Oscillator Feature Migration (Complete - Native Kotlin + TechnicalAnalysisEngine)

**Iteration 14-15, Build: SUCCESS (1m 23s), AD-002 RESOLVED**

**Migration Achievement**: Replaced entire OscillatorPyClient Python bridge with native kotlin_krx + pure Kotlin computation engine

**Created Files** (8):

1. **TechnicalAnalysisEngine.kt** (487 lines) - Pure Kotlin computation engine
   - Ported Python trend_signal.py (~130 lines numerical analysis)
   - Functions: calculateEMA, resampleWeekly/Monthly, calculateCMF, calculateFearGreed, generateSignals, calculateElderImpulse, calculateDemarkTD, rollingSum
   - No dependencies, maximum testability, object pattern for stateless operation

2. **StockDataRepository.kt** (78 lines) - Domain interface
   - Abstracts kotlin_krx data + technical analysis
   - Methods: getStockOhlcv, getStockAnalysisData, getAllStocksList, getStockName, getTrendSignalData, getElderImpulseData, getDemarkTDData

3. **KrxStockDataRepositoryImpl.kt** (441 lines) - Implementation
   - Wires KrxStock API + TechnicalAnalysisEngine
   - Market cap approximation: `close[i] * sharesOutstanding` (single getMarketCap call)
   - Investor trading: Zero values (API gap, minimal impact on oscillator ratios)
   - Type conversion: kotlin_krx Long → Double for TechnicalAnalysisEngine compatibility
   - Extends KrxRepositoryBase for timeout/error handling

4-7. **4 UseCases** (~25 lines each):
   - GetTrendSignalDataUseCase.kt
   - GetElderImpulseDataUseCase.kt
   - GetDemarkTDDataUseCase.kt
   - GetStockOhlcvUseCase.kt
   - Standard pattern: `@Inject constructor(repo)`, `suspend operator fun invoke()`

**Modified Files** (6):

- **StockRepositoryImpl.kt**: `OscillatorPyClient` → `StockDataRepository`, getAllStocksList() migrated
- **StockAnalysisRepositoryImpl.kt**: `OscillatorPyClient` → `StockDataRepository`, getStockAnalysis() migrated
- **OscillatorViewModel.kt**: 18 pyClient calls → 3 kotlin_krx UseCases
  - Stock search: DB-based `stockRepository.searchStocks()` (replaces Python searchStock)
  - Trend signal: `getTrendSignalDataUseCase(ticker, days = 365, interval)`
  - Elder Impulse: `getElderImpulseDataUseCase(ticker, interval)`
  - DeMark TD: `getDemarkTDDataUseCase(ticker, interval)`
- **StockTrendViewModel.kt**: Removed unused `val pyClient: OscillatorPyClient`
- **AggregatedStockTrendScreen.kt**: Removed unused `val pyClient` from AggregatedStockTrendViewModel
- **StockModule.kt**: Added StockDataRepository binding, 4 UseCase providers, updated repository DI

### Post-Migration Status (2026-02-18) — COMPLETE

**Migration Achievement**: ✅ **100% pykrx migration** — PyKrxClient completely removed
**Build Status**: ✅ **PRODUCTION-READY**
**Deployment Status**: ✅ **APPROVED** (Architect-reviewed)

**Completed Migrations (All)**:
- T-011: ETF feature ✅ (Phase A: getBusinessDays → GetKrxBusinessDaysUseCase)
- T-012: Oscillator feature ✅ (TechnicalAnalysisEngine + 4 UseCases)
- T-013: Stock analysis feature ✅ (native Kotlin oscillator calculations)
- Market feature ✅ (MarketIndex + MarketDeposit → kotlin_krx + Kotlin web scraping)
- pykrx dependency ✅ **REMOVED** from build.gradle.kts

**Remaining Python Dependencies** (non-pykrx, out of migration scope):
- `OscillatorPyClient` - Market oscillator feature (stocks.py, market.py)
- `MarketIndexPyClient` - Market index data (market.py)
- `BloodIndicatorPyClient` - Blood indicator data (blood_indicator.py, Yahoo/FRED)
- `FearGreedRepositoryImpl` - Direct Python/DataFrame manipulation (feargreed.py)

**Post-Migration Bug Fixes**:
- ✅ Zero-data bug: kotlin_krx wrong API endpoint + reverse chronological order (ROOT_CAUSE_REPORT.md)
- ✅ Investor trading data: 외국인/기관 수급 데이터 zero 수정
- ✅ Chart period selection: 날짜 포맷 불일치 (yyyy-MM-dd → yyyyMMdd) 수정 + 5개 차트 전체 필터링

**Key Technical Achievements**:
- ✅ **AD-002 RESOLVED**: All 3 ViewModels now use UseCases (Clean Architecture compliance)
- ✅ **Type Safety**: kotlin_krx compile-time validation vs. pykrx runtime parsing
- ✅ **Null Safety**: Result<T> pattern with explicit error handling
- ✅ **Architecture**: Clean Architecture maintained (ViewModel → UseCase → Repository → kotlin_krx)
- ✅ **CLAUDE.md Rule #3**: KrxRepositoryBase uses configurable timeouts (30s-180s)
- ✅ **CLAUDE.md Rule #10**: All repository operations use `withContext(Dispatchers.IO)`

**Known Issues / Future Work**:
- maxDays 365로 제한 중 (kotlin_krx date chunking 수정 후 730 복원 필요)
- ETF 목록 조회 timeout 60s 증가 검토
- 디버그 checkpoint 로그 정리 필요