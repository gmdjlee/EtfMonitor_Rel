# TASK.md — Current Tasks

No active tasks.

## Completed

### Phase 7: Enhancement Sprint (v1.6.0, 2026-02-20)
- [x] U-013 CLAUDE.md corrections (Rule #4 FearGreed, entity count, FredApiKeyProvider path, Do NOT table)
- [x] U-014 exportSchema=true (Room KSP + kotlinxSerialization 1.7.1→1.8.1)
- [x] U-015 BackupDao OOM fix (monthly date-range chunking, removed getAllHoldingKeys)
- [x] U-016 MarketOscillator parallelism (InMemoryCookieJar thread-safety + Semaphore(5))
- [x] U-017 CollectionState persistence (SharedPreferences + wasInterrupted + HomeViewModel banner)
- [x] U-018 Worker tests (65 tests: EtfUpdate, AdvancedAnalysis, MarketOscillator)
- [x] U-019 DAO tests (94 tests: EtfDao, BackupDao, StockAnalysisDao)
- [x] U-020 PROJECT_REVIEW.md + CLAUDE.md + PROGRESS.md documentation update

### Phase 5-6: Test Tier 2 + Polish (v1.5.0, 2026-02-20)
- [x] U-009 AIResponseParserTest.kt (40 tests — Korean/English signals, JSON extraction)
- [x] U-010 MarketOscillatorCalculatorTest.kt (15 tests — MockK, oscillator via analyze())
- [x] U-011 CorrelationAnalyzerTest rewrite + StockAnalysisRepositoryImplTest (27 tests — anti-pattern fix + cache TTL)
- [x] U-012 WorkManager BackoffPolicy + MigrationTest update (v17→v20, JUnit assertions)

### Hardening Sprint (v1.4.0, 2026-02-20)
- [x] U-001 OkHttp Response leaks (3 locations)
- [x] U-002 CancellationException guards (216+ catch blocks, 59 files)
- [x] U-003 NonCancellable wrapping (FearGreedVM, SettingsVM)
- [x] U-004 FredApiKeyProvider + backup filtering
- [x] U-005 @Transaction (22 BackupDao methods) + LIMIT clauses + DB indices (v21)
- [x] U-006 Security hardening (network config, log redaction, Gemini token limit)
- [x] U-007 Core engine tests (160 new: Holding, DateAdapter, BloodIndicator, FearGreed, TechnicalAnalysis)
- [x] U-008 UPDATE_REPORT.md + CLAUDE.md + PROGRESS.md finalization

### blood_indicator.py Kotlin Migration (v1.3.0, 2026-02-20)
- [x] B-001~B-003 Analysis and planning
- [x] B-004~B-006 BloodIndicatorClient + Calculator + Repository
- [x] B-007~B-009 Chaquopy removal, DI cleanup, test update
- [x] B-010~B-011 Build verification (57/57 PASS), documentation
