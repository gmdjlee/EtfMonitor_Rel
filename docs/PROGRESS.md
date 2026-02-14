# Ralph Loop Progress Tracker

## QA-Engineer Status Update (2026-02-14)

**Role**: QA-Engineer (Sonnet) - Test coverage and performance specialist
**Status**: STANDBY - Ready for R-008 through R-012

---

## Test Baseline Analysis (Pre-R-008)

### Current Test Structure

**Project Scale**:
- Total Kotlin files: 376
- Unit test files: 8 (2.1% coverage by file count)
- Instrumented test files: 1 (migration tests only)

**Test Location**:
- Unit tests: `/app/src/test/java/com/etfmonitor/`
- Instrumented tests: `/app/src/androidTest/java/com/etfmonitor/`

**Test Framework Configuration**:
- JUnit5 5.10.2
- MockK 1.13.10
- Turbine 1.1.0 (Flow testing)
- Coroutines Test 1.10.2
- Room Testing 2.8.3 (MigrationTestHelper)

---

## Existing Test Coverage (kotlin_krx Integration)

### ✅ Tests to Keep (5 files)

#### 1. PyKrxClientTest.kt (447 lines)
**Status**: Keep as-is (Python bridge validation)
**Coverage**:
- ETF list retrieval (5 tests): success, empty keywords, empty response, invalid JSON, Python exceptions
- Holdings retrieval (3 tests): success, empty response, stock name fallback
- Business days retrieval (3 tests): success, failure handling, partial results
- Retry logic (2 tests): first attempt success, all attempts fail
- Date format conversion (1 test): YYYYMMDD to YYYY-MM-DD
- JSON parsing (2 tests): extra fields handling, Korean names

**kotlin_krx Relevance**: Documents Python baseline behavior for comparison with kotlin_krx migration

#### 2. EtfRepositoryImplTest.kt (496 lines)
**Status**: Needs update after T-011 (partial migration)
**Coverage**:
- Data status checks (4 tests): hasData(), getDataStatus(), latestDate handling
- ETF list operations (2 tests): getAllEtfs(), searchEtfs()
- Holdings comparison logic (7 tests): NEW, REMOVED, INCREASE, DECREASE, MAINTAIN statuses
- Settings management (7 tests): default days, themes, exclusions

**T-011 Impact**:
- EtfRepositoryImpl now uses GetKrxEtfHoldingsUseCase, GetKrxEtfListUseCase
- Tests still mock PyKrxClient (needs refactoring to mock UseCases)
- Comparison logic tests remain valid (no kotlin_krx involvement)

**Action Required**: Refactor tests to mock kotlin_krx UseCases instead of PyKrxClient

#### 3. FearGreedRepositoryImplTest.kt
**Status**: Keep as-is (out of kotlin_krx scope)
**Rationale**: FearGreed uses KRX API via direct Python.getInstance(), not pykrx-dependent

#### 4. CorrelationAnalyzerTest.kt
**Status**: Keep as-is (analysis logic only)
**Rationale**: Tests mathematical correlation calculations, no data source dependency

#### 5. MigrationTest.kt (411 lines, instrumented)
**Status**: Keep as-is (database schema validation)
**Coverage**: All 18 migrations (v1 to v19) with data preservation checks
**kotlin_krx Relevance**: None (schema independent of data source)

---

### ❌ Missing Test Coverage (kotlin_krx Integration)

#### Critical Gaps (Phase 3 T-011 deliverables):

1. **GetKrxEtfHoldingsUseCase** (created in T-011)
   - No unit tests for UseCase logic
   - No error handling tests (Result<List<Holding>>)
   - No Holding.create() factory compliance verification

2. **GetKrxEtfListUseCase** (created in T-011)
   - No unit tests for parallel name lookups
   - No client-side filtering tests
   - No PARALLEL_LIMIT=10 chunking verification

3. **KrxEtfRepository** (created in T-007)
   - No repository layer tests
   - No KrxErrorMapper behavior validation
   - No timeout handling tests (30s default)

4. **HoldingMapper** (created in T-007)
   - No adapter tests for compression roundtrip (weightBps/amountMillion)
   - No Short/Int overflow scenario tests (CLAUDE.md Rule #1)

5. **Integration Tests**
   - No end-to-end tests for PyKrxClient → kotlin_krx migration path
   - No performance comparison tests (pykrx baseline vs kotlin_krx)

---

## Test Strategy for R-008 (Run Test Suite)

### Phase 1: Execute Existing Tests
```bash
./gradlew test  # Run 8 unit tests
./gradlew connectedAndroidTest  # Run 1 migration test (requires device)
```

### Phase 2: Identify Coverage Gaps
- Generate coverage report via JaCoCo (if configured)
- Document test/source ratio by feature module
- Map untested kotlin_krx integration points

### Phase 3: Prioritize Test Creation (R-008 deliverable)
**High Priority** (affects T-013 Stock Analysis migration):
1. GetKrxEtfHoldingsUseCase tests (Holding.create() compliance)
2. GetKrxEtfListUseCase tests (parallel API calls)
3. HoldingMapper tests (compression roundtrip)

**Medium Priority** (repository layer):
4. KrxEtfRepository tests (error handling, timeouts)
5. DateAdapter tests (KRX date format conversion)

**Low Priority** (future-proofing):
6. Integration tests (Python vs Kotlin comparison)

---

## Performance Benchmark Strategy (R-009)

### Baseline Metrics to Collect

**PyKrxClient (current Python implementation)**:
- ETF list retrieval: avg latency, success rate
- Holdings retrieval: avg latency, retry rate
- Business days calculation: avg latency

**kotlin_krx (new Kotlin implementation)**:
- KrxEtf.getList() latency (10 markets × 2 calls = 20 API requests)
- KrxEtf.getPortfolio() latency (single ticker)
- Date chunking overhead (365-day limit)

### Comparison Targets
- API call latency: kotlin_krx should match or improve pykrx (network I/O dominant)
- Retry logic: kotlin_krx has no retries (vs PyKrxClient MAX_RETRIES=2)
- Memory usage: Kotlin data classes vs Python DataFrame serialization

---

## Build Verification Plan (R-010)

```bash
# Clean build test
./gradlew clean
./gradlew assembleDebug  # Expected: SUCCESS
./gradlew assembleRelease  # Expected: SUCCESS

# Dependency verification
./gradlew app:dependencies | grep kotlin_krx  # Confirm local module integration
./gradlew app:dependencies | grep gson  # Confirm version alignment (AD-004)
```

---

## Static Analysis Plan (R-011)

```bash
# Lint check
./gradlew lint  # Check for warnings in kotlin_krx integration code

# Unused resource scan
./gradlew lintDebug  # Identify unused Python scripts after migration
```

**Expected Lint Warnings** (acceptable in coexistence phase):
- Unused PyKrxClient methods (getHoldings, getFilteredEtfList migrated to kotlin_krx)
- Unused etfcollector.py functions (get_etf_list_with_names, get_etf_holdings)

---

## Key User Flow Stability Checks (R-012)

### Critical Paths to Test

1. **ETF Feature** (T-011 migrated)
   - Launch app → ETF tab → View holdings
   - Search ETF by name → View comparison (NEW/REMOVED/INCREASE)
   - Filter ETF list by theme keywords

2. **Oscillator Feature** (T-012 deferred)
   - Stock Trend screen → Market Oscillator data load
   - Verify OscillatorPyClient still functional (Python dependency accepted)

3. **Stock Analysis Feature** (T-013 pending)
   - Search stock → View analysis data
   - Verify StockAnalysisData JOIN with stocks table (CLAUDE.md Rule #2)

### Regression Indicators
- ANR (Application Not Responding) during API calls
- Crash on Holding entity creation (Short/Int overflow)
- Empty data with valid API responses (JSON parsing errors)

---

## Next Steps

**Waiting for Verifier to complete**: R-001 through R-004
- R-001: Code review (UseCase constructor patterns)
- R-002: Import verification (kotlin_krx module resolution)
- R-003: Coexistence validation (Python + Kotlin dual paths)
- R-004: Documentation accuracy (CLAUDE.md, PHASE3_MIGRATION_STRATEGY.md)

**Once activated, QA-Engineer will**:
1. Execute test suite and document results (R-008)
2. Run performance benchmarks (R-009)
3. Verify clean builds (R-010)
4. Run lint and identify unused resources (R-011)
5. Test critical user flows (R-012)

**Confidence Level**: 95% (test framework familiarity, clear success criteria)

---

**Prepared by**: QA-Engineer (Sonnet)
**Date**: 2026-02-14
**Status**: STANDBY
