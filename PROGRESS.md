# PROGRESS.md

## Latest: v1.6.0 (2026-02-20) — Phase 7 (Enhancement Sprint)
**Status**: COMPLETE | **Build**: assembleDebug SUCCESS | **Tests**: 375 unit + 94 androidTest, 159 new

**Key Achievement**: All remaining P2/P3 items resolved + 159 new tests.
- CLAUDE.md: Fixed Rule #4 FearGreed (repository already 3x internally), entity count 22→23, FredApiKeyProvider path
- exportSchema=true: Resolved kotlinxSerialization 1.7.1→1.8.1 conflict, Room schema export enabled
- BackupDao OOM: Monthly date-range chunking for holdings/priceCache, removed getAllHoldingKeys() (IGNORE handles dupes)
- Oscillator parallelism: Semaphore(5) + ISIN cache warmup, InMemoryCookieJar thread-safety fix (~4.8x speedup)
- CollectionState persistence: SharedPreferences with wasInterrupted detection + HomeViewModel banner
- Worker tests: 65 tests (EtfUpdate 20, AdvancedAnalysis 27, MarketOscillator 18)
- DAO tests: 94 tests (EtfDao 40, BackupDao 32, StockAnalysisDao 22)
- KrxApiFunctionalityTest: Fixed pre-existing compilation errors

**Score improvement**: 78 → ~83.5/100 (+5.5 points, cumulative +30 from initial 53.5)

---

## Previous: v1.5.0 (2026-02-20) — Phase 5-6 (Test Tier 2 + Polish)
**Status**: COMPLETE | **Build**: assembleDebug SUCCESS | **Tests**: 310 run, 232 new total

**Key Achievement**: Phase 5 test coverage + Phase 6 polish items completed.
- AIResponseParserTest: 40 tests (Korean/English signal parsing, JSON extraction)
- MarketOscillatorCalculatorTest: 15 tests (MockK, oscillator calculation via analyze())
- StockAnalysisRepositoryImplTest: 13 tests (cache TTL invalidation logic)
- CorrelationAnalyzerTest: rewritten 14 tests (removed anti-pattern, tests production code)
- MigrationTest: updated v17→v20, 3 new migration tests, assert()→JUnit assertions
- WorkManagerHelper: BackoffPolicy.EXPONENTIAL 30s on all PeriodicWorkRequests

**Score improvement**: 74 → ~78/100 (+4 points, cumulative +24.5 from initial 53.5)

---

## Previous: v1.4.0 (2026-02-20) — Hardening Sprint (Phase 1-4)
**Status**: COMPLETE | **Build**: assembleDebug SUCCESS | **Tests**: 238 run, 160 new

**Key Achievement**: All P0 Critical and P1 High items from PROJECT_REVIEW.md resolved.
- CancellationException: 216+ catch blocks fixed across 59 files
- FRED API key migrated to EncryptedSharedPreferences (FredApiKeyProvider)
- OkHttp Response leaks fixed (3 locations)
- DB schema v20→v21 (8 indices, 22 @Transaction, LIMIT clauses)
- Security hardening: network config, log redaction, backup filtering
- 160 new unit tests for 5 core computation engines

Full details: `UPDATE_REPORT.md` | Review: `PROJECT_REVIEW.md`

---

## Previous: v1.3.0 (2026-02-20) — blood_indicator.py Migration
**Status**: COMPLETE | **Build**: assembleDebug SUCCESS | **Tests**: 57/57 PASS

**Key Achievement**: Chaquopy embedded Python completely removed from project.
- APK size reduction ~30-50MB
- Configuration cache enabled
- Zero Python dependencies

**Created**: BloodIndicatorClient.kt (OkHttp), BloodIndicatorCalculator.kt (pure Kotlin)
**Deleted**: blood_indicator.py, core.py, __init__.py, BloodIndicatorPyClient.kt, PythonModule.kt
**Modified**: BloodIndicatorRepositoryImpl, build configs (6 files), KrxApiFunctionalityTest
