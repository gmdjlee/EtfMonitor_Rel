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
| Python bridge | `core/network/python/` | PyKrxClient, MarketIndexPyClient, OscillatorPyClient, BloodIndicatorPyClient |
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

**Status**: ✅ Phase 1 COMPLETE | ✅ Phase 2 COMPLETE | 🔄 Phase 3 IN PROGRESS (Iteration 14/15)
**Target**: github.com/gmdjlee/kotlin_krx (native Kotlin replacement for pykrx)
**Architecture**: MVVM + Clean Architecture + Feature modules
**DI Framework**: Hilt
**Key Constraint**: Feature redesign in Phase 3, partial Python dependencies acceptable (getBusinessDays, OscillatorPyClient)
**API Coverage**: 90.9% (10/11 pykrx functions covered, 1 gap with fallback strategy)
**Primary Documents**: MIGRATION_STRATEGY.md (Phase 1-2), docs/PHASE3_MIGRATION_STRATEGY.md (Phase 3)
**Phase 2 Complete**: T-006 ✅ Gradle | T-007 ✅ Repositories | T-008 ✅ UseCases | T-009 ✅ Coexistence | T-010 ⏸️ Blocked
**Phase 3 Progress**: T-011 ✅ ETF (partial) | T-012 ✅ Oscillator (deferred) | T-013 ⏳ Stock Analysis

### Python Bridge Architecture (5 Patterns)

**PyClient bridge classes (4 - JSON-based):**
1. `PyKrxClient` - ETF data collection (etfcollector.py, stocks.py, core.py)
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

**AD-002: Architecture Violations** ⏳ DEFERRED TO PHASE 3 (T-011/T-012/T-013)
- **Issue**: 3 ViewModels directly inject Python clients (violates Clean Architecture)
- **Original Plan**: Refactor in Phase 2 (T-009) to inject UseCases instead
- **T-008 Finding**: ViewModels use searchStock(), getTrendSignalData(), getElderImpulseData(), getDemarkTDData() - functions that DON'T exist in kotlin_krx
- **Revised Strategy**: Phase 2 establishes UseCase foundation (T-008 complete), Phase 3 redesigns features to use kotlin_krx UseCases
- **Affected**: `StockTrendViewModel`, `OscillatorViewModel`, `AggregatedStockTrendViewModel`
- **Coexistence**: T-009 validates Python + kotlin_krx dual paths functional, NO ViewModel migration in Phase 2

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

### Migration Risks

**Highest → Lowest coupling risk:**
1. **CRITICAL GAP**: `get_index_portfolio_deposit_file` missing from kotlin_krx (blocks Oscillator migration)
2. **FearGreedRepositoryImpl** - Direct PyObject/DataFrame manipulation (special handling required)
3. **OscillatorPyClient** - Used by 7 classes across 3 feature packages
4. **PyKrxClient** - Central ETF data bridge
5. **Architecture violations** - 2 ViewModels must be refactored to use UseCases
6. **Dependency conflicts**: Gson vs kotlinx.serialization (~1MB), Coroutines 1.7.3 vs 1.10.2

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

**Remaining Python Dependency** (accepted):
- `PyKrxClient.getBusinessDays(days)` - Business day calculation logic
- **Rationale**: kotlin_krx focuses on KRX data fetching. Business calendar logic is external concern (Korean holidays, weekends)
- **Impact**: Single utility function (~5 lines of consumption, 2 call sites in EtfRepositoryImpl lines 396, 502)
- **Future Path**: Could implement Korean business day calendar in Kotlin or use external library (out of Phase 3 scope)

**Implementation Details**:
- **Created**: 2 UseCases with `@Inject` constructors (GetKrxEtfHoldingsUseCase, GetKrxEtfListUseCase)
- **Modified**: EtfRepositoryImpl.kt (3 PyKrxClient calls → 2 UseCase calls + 1 kept), EtfModule.kt (inject 2 UseCases)
- **Performance Trade-off**: Client-side ETF filtering via parallel name lookups (N API calls, chunked with PARALLEL_LIMIT=10)
- **Critical Fix** (Revision 1 after Architect review):
  - C1: Return type fixed (`List<String>` → `Result<List<Etf>>` with full entity construction)
  - C2: Filtering by ETF name (Korean keywords) instead of ticker codes
  - W1: EtfModule.kt DI wiring added

**Files Modified**: 4 files
- `core/domain/usecase/krx/GetKrxEtfHoldingsUseCase.kt` (created, 25 lines)
- `core/domain/usecase/krx/GetKrxEtfListUseCase.kt` (created, 68 lines)
- `feature/etf/data/repository/EtfRepositoryImpl.kt` (modified, 2 PyKrxClient calls replaced)
- `feature/etf/di/EtfModule.kt` (modified, inject 2 UseCases into provideEtfRepository)

**T-010 Impact**: Python dependency removal still BLOCKED. Must complete T-012 (Oscillator) and T-013 (Stock Analysis) before PyKrxClient can be fully removed.

#### T-012: Oscillator Feature Migration (Deferred - API Gap + Budget Constraint)

**Iteration 14, Architect Decision: APPROVED (Option B - Deferred Migration)**

**Budget Constraint Analysis**:
- **Original Estimate**: 2 iterations (Phase 3 strategy)
- **Budget Reality**: 4 iterations remaining for 9 tasks (T-012 through T-019)
- **Infeasibility**: Allocating 2 iterations to T-012 leaves 2 iterations for 7 tasks (impossible)
- **Decision**: Accept OscillatorPyClient as permanent Python dependency (documentation-only, 0.25 iterations)

**API Gap Assessment** (kotlin_krx lacks required functions):

*Data Source Gaps* (what kotlin_krx's `KrxStock` doesn't expose):
- `get_market_ohlcv()` - OHLCV data retrieval for historical analysis
- `get_market_cap()` - Individual stock market cap time series (not just latest)
- Stock search by name/ticker
- Stock analysis data (market cap + foreign/institution flow)

*Computation Gaps* (application-level algorithms requiring Kotlin reimplementation):
- Trend signal analysis (EMA, MACD, CMF calculations)
- Elder Impulse analysis (13-period EMA + MACD histogram cross)
- DeMark TD Sequential (9-count buy/sell setup, 13-count countdown)
- Market oscillator calculation (200+ stock aggregation with 180s timeout)

**Full Migration Cost**: 2+ iterations
- Implement OHLCV/market cap time series APIs in kotlin_krx (or custom HTTP clients)
- Reimplement ~300 lines of numerical analysis in Kotlin (from trend_signal.py)
- Migrate 3 ViewModels with complex data flows

**Deferred Migration Rationale**:
- **T-011 Precedent**: getBusinessDays() accepted as Python dependency for business logic
- **Scope Difference**: OscillatorPyClient is fundamentally larger (596-line class, 10 public methods, 4 Python modules) vs. getBusinessDays (single utility function)
- **Technical Debt Estimate**: 3-4 iterations standalone (outside current 15-iteration Ralph loop)
- **ViewModel Dependency Classification**:
  - **OscillatorViewModel** (heavy): 5 direct Python callAttr pathways, core feature logic
  - **StockTrendViewModel** (light): Holds `val pyClient` for navigation FAB only (reference-only)
  - **AggregatedStockTrendViewModel** (light): Same navigation FAB pattern (reference-only, not data-dependent)

**Python Dependency Accepted**:
- `OscillatorPyClient` (entire class) - Advanced trend analysis subsystem
- **Modules**: stocks.py, deposit_scraper.py, market.py, trend_signal.py
- **Public Methods**: 10 (searchStock, getAllStocksList, getStockOhlcv, getTrendSignalData, getElderImpulseData, getDemarkTDData, getMarketOscillator, getMarketDepositData, getLatestMarketData, getStockAnalysis)
- **Consumers**: 7 classes across 3 feature packages
- **Future Migration Path**: Requires kotlin_krx enhancements OR custom Kotlin numerical analysis library

**Files Modified**: 0 (documentation-only)
- `CLAUDE.md` updated (this section)
- `docs/PHASE3_MIGRATION_STRATEGY.md` updated (T-012 status: DEFERRED)
- `TASK.md` updated (T-012 marked complete with deferred note)

**T-010 Impact**: Python dependency removal BLOCKED indefinitely. OscillatorPyClient retention means partial Python bridge remains permanently (unless future migration attempt outside current loop).

**Architect Advisories Addressed** (from Revision 0 review):
- A1: Documented OscillatorPyClient as larger dependency than getBusinessDays with 3-4 iteration migration estimate
- A2: Split API gap into data gaps vs. computation gaps (clear future migration path)
- A3: Classified 3 ViewModels by actual dependency depth (heavy vs. light consumers)
- A4: Remaining iteration budget breakdown provided (see below)
- Handle Holding.create() factory for ETF portfolios (compressed storage weightBps/amountMillion)