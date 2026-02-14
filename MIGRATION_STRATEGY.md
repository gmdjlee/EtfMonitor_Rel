# pykrx → kotlin_krx Migration Strategy

**Version**: 1.0 (Phase 1 Complete)
**Date**: 2026-02-14
**Status**: ✅ Phase 1 (Planning) Complete - Ready for Implementation

---

## Executive Summary

### Current State
- **Python Dependencies**: 5 scripts using pykrx (`core.py`, `etfcollector.py`, `stocks.py`, `market.py`, `trend_signal.py`)
- **Python Bridges**: 4 PyClient classes + 1 direct Python.getInstance() call
- **Architecture Violations**: 3 ViewModels directly inject Python clients
- **pykrx Functions Used**: 11 functions across stock, ETF, and index data

### Target State
- **Native Kotlin**: kotlin_krx library (90.9% API coverage)
- **Zero Python Dependencies**: Remove pykrx, Chaquopy overhead
- **Clean Architecture**: All ViewModels use UseCases (violations fixed)
- **Single Module**: Maintain current structure with kotlin_krx as local library

### Migration Scope
- **API Coverage**: 10/11 pykrx functions covered (1 gap with fallback)
- **Files to ADD**: ~15 (KrxModule, repositories, mappers, adapters, UseCases)
- **Files to MODIFY**: ~20 (DI modules, repository implementations, ViewModels, build files)
- **Files to REMOVE**: ~10 (PyClients, Python scripts, PythonModule)

### Timeline
- **Phase 1** (Iterations 1-6): Planning ✅ COMPLETE
- **Phase 2** (Iterations 7-11): Core Integration (5 tasks)
- **Phase 3** (Iterations 12-15): Feature Migration (4 tasks)
- **Phase 4** (Iterations 16-20): Verification (5 tasks)
  - **Note**: Originally planned for iterations 16-18, extended to 16-20 to accommodate performance benchmarking (T-016) and final review (T-019). Total scope exceeds 15-iteration Ralph loop limit and will require additional iterations.

---

## Architectural Decisions

### AD-001: kis_client.py Scope ✅ RESOLVED (T-002)

**Decision**: KEEP kis_client.py as complementary data source

**Rationale**:
- kotlin_krx uses KRX Open Data API (Korean network only, historical data)
- kis_client.py uses KIS Open API (global access, real-time data)
- Different purposes: kotlin_krx for analysis, kis_client for trading
- Both can coexist without conflict

**Impact**: Migration scope limited to pykrx-dependent scripts only

---

### AD-002: Architecture Violations (T-001)

**Issue**: 3 ViewModels directly inject Python clients (violates Clean Architecture)

**Decision**: Refactor in Phase 2 (T-009) to inject UseCases

**Affected ViewModels**:
1. `StockTrendViewModel` → Inject `GetStockTrendUseCase`
2. `OscillatorViewModel` → Inject `GetOscillatorDataUseCase`
3. `AggregatedStockTrendViewModel` → Inject `GetAggregatedTrendUseCase`

**Impact**: Enables full migration of Oscillator feature (7 consumers across 3 packages)

---

### AD-003: Index Portfolio Gap ✅ RESOLVED (T-003)

**Issue**: `get_index_portfolio_deposit_file` has NO kotlin_krx equivalent

**Decision**: Use `KrxStock.getMarketCap()` top-N proxy for index components

**Implementation**:
```kotlin
suspend fun getIndexComponentsByMarketCap(date: String, indexTicker: String, topN: Int = 200): List<String> {
    val market = when (indexTicker) {
        "1028" -> Market.KOSPI  // KOSPI 200
        "2203" -> Market.KOSDAQ  // KOSDAQ 150
        else -> Market.ALL
    }
    return krxStock.getMarketCap(date, market)
        .sortedByDescending { it.marketCap }
        .take(topN)
        .map { it.ticker }
}
```

**Rationale**:
- Lowest effort (uses existing API)
- High maintainability (dynamic, no hardcoding)
- Good approximation (top 200 by market cap ≈ index components)
- Enables full Oscillator migration to kotlin_krx

**Impact**: Python market.py can be removed

---

### AD-004: JSON Library Conflict ✅ RESOLVED (T-004)

**Issue**: MarketMonitor uses kotlinx.serialization, kotlin_krx uses Gson

**Decision**: KEEP BOTH libraries

**Updated Rationale** (Architect finding):
- **Gson ALREADY EXISTS** in APK via `google-api-client-gson` dependency (app/build.gradle.kts:169)
- **Actual APK cost**: Near-zero (not +1MB as initially estimated)
- Migrating kotlin_krx to kotlinx.serialization = HIGH effort, out of scope
- Gson isolated within kotlin_krx module (no API leakage)

**Impact**: No APK penalty, no migration work needed

---

### AD-005: Module Architecture (T-004)

**Decision**: Single app module + kotlin_krx as local Gradle library

**Rationale**:
- Current scale (~255 Kotlin files, 7 features) doesn't justify multi-module split
- Single-module maintains current build times
- kotlin_krx as local dependency provides isolation
- No Maven publishing needed

**Implementation**:
```kotlin
// settings.gradle.kts
include(":app")
include(":kotlin-krx")
project(":kotlin-krx").projectDir = file("../kotlin_krx")

// app/build.gradle.kts
dependencies {
    implementation(project(":kotlin-krx"))
}
```

**Impact**: Minimal build configuration changes

---

## Implementation Roadmap

### Phase 2: Core Integration (T-006 to T-010) - Coexistence

**Objective**: Add kotlin_krx alongside existing Python without breaking changes

**Tasks**:
- **T-006**: Add kotlin_krx module to Gradle, create KrxModule.kt with Hilt singletons
- **T-007**: Implement Repository interfaces with kotlin_krx (alongside PyClient implementations)
- **T-008**: Create UseCases for krx data operations
- **T-009**: Refactor 3 ViewModels to use UseCases (fix architecture violations)
- **T-010**: Validate dual-path operation (Python baseline, Kotlin new)

**Strategy**: DI-based feature flags for routing between Python and Kotlin implementations

**Success Criteria**: Both Python and Kotlin paths functional, all tests pass

---

### Phase 3: Feature Migration (T-011 to T-014) - Replace

**Objective**: Migrate features one-by-one, remove Python dependencies

**Migration Order** (based on coupling risk):
1. **T-011**: ETF monitoring (`PyKrxClient` → `KrxEtf`) - Low coupling, single PyClient
2. **T-012**: Oscillator (`OscillatorPyClient` → kotlin_krx + AD-003 proxy) - HIGH coupling, 7 consumers
3. **T-013**: Stock analysis (remaining PyClient usages) - Medium coupling
4. **T-014**: Remove PyClients, Python scripts, Chaquopy pykrx dependency

**Success Criteria**: All features using kotlin_krx, zero Python dependencies

---

### Phase 4: Verification & Completion (T-015 to T-019) - Validate

**Objective**: Ensure migration success, optimize, document

**Tasks**:
- **T-015**: Full test suite + achieve 80% coverage
- **T-016**: Performance benchmark (kotlin_krx vs pykrx baseline)
- **T-017**: Build verification (assembleDebug + assembleRelease)
- **T-018**: Update CLAUDE.md with final architecture
- **T-019**: Final Architect review and sign-off

**Success Criteria**: All tests pass, performance ≥ baseline, documentation complete

---

## Risk Assessment & Mitigation

### Critical Risks (Mitigated)

| Risk | Mitigation | Status |
|------|------------|--------|
| Index portfolio gap blocks Oscillator | AD-003: getMarketCap top-N proxy | ✅ RESOLVED |
| API incompatibility | T-002: 90.9% coverage verified | ✅ LOW RISK |
| APK size bloat (+1MB Gson) | AD-004: Gson already in APK | ✅ RESOLVED |

### High Risks (Planned)

| Risk | Mitigation | Status |
|------|------------|--------|
| Architecture violations block migration | AD-002: T-009 UseCase refactoring | 📋 PLANNED |
| Build integration failure | T-004: Clear projectDir strategy | 📋 PLANNED |
| Performance regression | T-016: Benchmark validation | 📋 PLANNED |

### Medium Risks (Acceptable)

| Risk | Mitigation | Status |
|------|------------|--------|
| Korean network restriction | Document deployment constraint | ⚠️ KNOWN |
| Date chunking transparency | kotlin_krx handles internally | ✅ HANDLED |
| Coroutines version mismatch (1.7.3 vs 1.10.2) | Align during T-006 integration | 📋 PLANNED |

---

## Success Criteria

### Phase 2 (Core Integration)
- [ ] kotlin_krx module integrated into Gradle build
- [ ] KrxModule.kt provides singletons with @Qualifier
- [ ] New Repository implementations coexist with PyClient implementations
- [ ] 3 ViewModels refactored to inject UseCases
- [ ] Feature flags enable Python/Kotlin switching
- [ ] All existing tests pass (no regressions)

### Phase 3 (Feature Migration)
- [ ] ETF feature uses KrxEtf (PyKrxClient removed)
- [ ] Oscillator feature uses kotlin_krx + AD-003 proxy (OscillatorPyClient removed)
- [ ] Stock analysis uses kotlin_krx (all PyClients removed)
- [ ] Python scripts removed (core.py, etfcollector.py, stocks.py, market.py, trend_signal.py)
- [ ] Chaquopy pykrx dependency removed from build.gradle.kts
- [ ] PythonModule.kt deleted

### Phase 4 (Verification)
- [ ] Test coverage ≥80% (unit + integration)
- [ ] Performance benchmarks show kotlin_krx ≥ pykrx baseline
- [ ] assembleDebug succeeds
- [ ] assembleRelease succeeds
- [ ] CLAUDE.md updated with migration notes
- [ ] Architect final review: APPROVED

---

## Technical Specifications

### kotlin_krx Integration (T-006)

**Hilt DI Structure**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object KrxModule {
    @Provides @Singleton @KrxOkHttp
    fun provideKrxOkHttpClient(): OkHttpClient

    @Provides @Singleton
    fun provideKrxClient(@KrxOkHttp okHttp: OkHttpClient): KrxClient

    @Provides @Singleton
    fun provideTickerCache(): TickerCache

    @Provides @Singleton
    fun provideKrxStock(client: KrxClient, cache: TickerCache): KrxStock

    @Provides @Singleton
    fun provideKrxEtf(client: KrxClient, cache: TickerCache): KrxEtf

    @Provides @Singleton
    fun provideKrxIndex(client: KrxClient): KrxIndex
}
```

### Adapter Layer (T-007)

**5 Adapters Required**:
1. **Error Adapter**: `KrxError` → `AppError` sealed class
2. **Date Adapter**: `yyyyMMdd` String ↔ `LocalDate`
3. **Holding Mapper**: `EtfPortfolio` → `Holding.create()` (CRITICAL)
4. **Nullability Handler**: pandas NaN → Kotlin nullable types
5. **Repository Wrapper**: kotlin_krx calls in Clean Architecture repositories

### API Mapping (T-003)

**10 Covered Functions**:
1. `get_market_ticker_list` → `KrxStock.getTickerList()`
2. `get_market_ohlcv` → `KrxStock.getMarketOhlcv()` / `getOhlcvByTicker()`
3. `get_market_ticker_name` → Extract from `TickerInfo.name`
4. `get_etf_ticker_list` → `KrxEtf.getEtfTickerList()`
5. `get_etf_ticker_name` → `KrxEtf.getEtfName(ticker, date)`
6. `get_etf_portfolio_deposit_file` → `KrxEtf.getPortfolio()`
7. `get_market_cap` → `KrxStock.getMarketCap()`
8. `get_market_trading_value_by_date` → `KrxStock.getTradingByInvestor()`
9. `get_index_ohlcv` → `KrxIndex.getOhlcvByTicker()`
10. ~~`get_index_portfolio_deposit_file`~~ → **AD-003 PROXY**

---

## Migration Checklist

### Pre-Migration (Phase 1) ✅ COMPLETE
- [x] T-001: pykrx usage analysis
- [x] T-002: kotlin_krx API review
- [x] T-003: API mapping document
- [x] T-004: Module structure design
- [x] T-005: Migration strategy approved

### Implementation (Phase 2-4) 📋 READY
- [ ] T-006: Gradle integration + KrxModule
- [ ] T-007: Repository implementations
- [ ] T-008: UseCase creation
- [ ] T-009: ViewModel refactoring
- [ ] T-010: Dual-path validation
- [ ] T-011: ETF feature migration
- [ ] T-012: Oscillator feature migration
- [ ] T-013: Stock analysis migration
- [ ] T-014: Python dependency removal
- [ ] T-015: Test coverage validation
- [ ] T-016: Performance benchmarking
- [ ] T-017: Build verification
- [ ] T-018: Documentation update
- [ ] T-019: Final Architect review

---

## Notes for Implementation

### Critical Rules (from CLAUDE.md)
1. **Holding Entity**: ALWAYS use `Holding.create()` factory (compressed storage)
2. **Date Adapter**: Handle 365-day chunking transparency (kotlin_krx internal)
3. **Error Handling**: `KrxError` exceptions must be caught and mapped
4. **OkHttpClient**: Use @KrxOkHttp qualifier to avoid DI collision
5. **Package Structure**: NO reorganization - modify implementations in-place

### Recommended Implementation Order
1. Start with T-006 (Gradle + KrxModule) - establishes foundation
2. Complete T-007 through T-009 in sequence - builds layers
3. Validate with T-010 before proceeding to feature migration
4. Migrate ETF first (T-011) as it's lowest risk
5. Save Oscillator (T-012) for second - it's highest complexity with 7 consumers

### Rollback Strategy
- Phase 2 maintains Python path via feature flags
- If kotlin_krx path fails validation, revert to Python baseline
- Remove feature flags only after Phase 3 complete and validated

---

**PHASE 1 COMPLETE - READY FOR IMPLEMENTATION**

