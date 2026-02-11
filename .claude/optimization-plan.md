# EtfMonitor Optimization Plan

**Date**: 2026-02-11
**Based on**: project-review-report.md + review-report.md
**Constraint**: ZERO functional changes to the app

---

## 7 Engineers - Work Assignment

### Engineer 1: Security Engineer (보안 엔지니어)

**Task: Fix ProGuard rules + Converters error handling**

| # | File | Change | Rationale |
|---|------|--------|-----------|
| 1 | `proguard-rules.pro:91-92` | `com.etfmonitor.ai.**` → `com.etfmonitor.core.network.ai.**` | SEC-01: Stale path causes AI classes to be stripped in release |
| 2 | `proguard-rules.pro:133` | DELETE `com.etfmonitor.repository.**` rule | SEC-02: Package deleted; Hilt auto-keeps repos |
| 3 | `proguard-rules.pro:134-137` | `com.etfmonitor.ui.screens.**ViewModel` → `com.etfmonitor.feature.**ViewModel` | SEC-02: ViewModels moved to feature packages |
| 4 | `proguard-rules.pro:142` | `com.etfmonitor.analysis.**` → `com.etfmonitor.core.analysis.**` | SEC-03: Moved during Clean Architecture migration |
| 5 | `proguard-rules.pro:143` | DELETE `com.etfmonitor.oscillator.**` rule | SEC-03: Package merged into core.analysis |
| 6 | `Converters.kt:15-22` | Wrap `JSONArray(value)` in try-catch, return `emptyList()` on failure | SEC-01/review, STAB-03: Corrupted DB data crashes app |

**Risk**: None - ProGuard rules are build-time only; Converters change only adds a safety net.

---

### Engineer 2: Performance Engineer (성능 개선 엔지니어)

**Task: Fix dispatcher issues**

| # | File | Change | Rationale |
|---|------|--------|-----------|
| 1 | `DataCollectionService.kt:60` | `Dispatchers.Default` → `Dispatchers.IO` | PERF-01: IO operations on CPU pool causes thread starvation |
| 2 | `StatisticsViewModel.kt:160-202` | Wrap `loadStatistics()` body in `withContext(Dispatchers.IO)` | PERF-02: DB queries running on Main thread |

**Risk**: None - changing dispatcher does not change logic, only which thread pool runs it.

---

### Engineer 3: Stability Engineer (안정성 검증 엔지니어)

**Task: SupervisorJob + getOrThrow safety + bounds checking**

| # | File | Change | Rationale |
|---|------|--------|-----------|
| 1 | `DataCollectionService.kt:60` | `Job()` → `SupervisorJob()` | STAB-05: One failed coroutine cancels ALL siblings |
| 2 | `NewAIAnalysisViewModel.kt` (6 locations) | Verify `.getOrThrow()` is within try-catch; if not, add try-catch | STAB-01: Uncaught exceptions crash app |
| 3 | `TimeSeriesAnalysisHelper.kt` (8 locations) | Verify `.getOrThrow()` is within try-catch; if not, add try-catch | STAB-02: Same issue |
| 4 | `CorrelationAnalysisRepositoryImpl.kt` (4 locations) | Verify `.getOrThrow()` is within try-catch | Same pattern |
| 5 | `ChatRepositoryImpl.kt` (1 location) | Verify `.getOrThrow()` is within try-catch | Same pattern |
| 6 | `AIAnalysisRepositoryImpl.kt` (1 location) | Verify `.getOrThrow()` is within try-catch | Same pattern |
| 7 | `MarketDepositRepositoryImpl.kt:98-107` | Add `minOf()` bounds validation for parallel list access | PERF-02/review: Mismatched arrays → IndexOutOfBoundsException |

**Risk**: None - adds safety without changing logic flow.

---

### Engineer 4: Bug & Error Fix Engineer (버그 및 에러 fix 담당)

**Task: Fix backup_rules.xml + unsafe .first() calls**

| # | File | Change | Rationale |
|---|------|--------|-----------|
| 1 | `backup_rules.xml:23-28` | `etf_db` → `etf_monitor.db` (all 4 occurrences) | Bug: DB name mismatch means database is NOT backed up |
| 2 | `AdvancedAnalysisWorker.kt:68` | `dates.first()` → add `isEmpty()` guard | STAB-04/PERF-05: Throws on empty list |
| 3 | `AdvancedDashboardViewModel.kt` (4 locations) | `dates.first()` → add `isEmpty()` guard | Same pattern |
| 4 | `StockTrendScreen.kt:177` | `timeSeries.first()` → add `isEmpty()` guard | Same pattern |
| 5 | `CashDepositTab.kt:73` | `trend.first()` → add `isEmpty()` guard | Same pattern |
| 6 | `AggregatedStockTrendScreen.kt:207` | `timeSeries.first()` → add `isEmpty()` guard | Same pattern |
| 7 | `StockStatisticsRepositoryImpl.kt:82-83` | `datesInRange.first()/last()` → add `isEmpty()` guard | Same pattern |
| 8 | `DateRangeSelector.kt:145` | `dates.first()/last()` → add `isEmpty()` guard | Same pattern |

**Risk**: None - adds safety guards only. Returns early with safe defaults when data is empty.

---

### Engineer 5: Code Integration Engineer (code integration 엔지니어)

**Task: Verify all changes, ensure no functional regressions**

- Review all changes from Engineers 1-4 and 6-7
- Verify import consistency
- Ensure no behavioral changes (only safety improvements)
- Verify package references after ProGuard updates
- Cross-check that `.getOrThrow()` calls inside try-catch blocks are NOT modified (no false positives)

---

### Engineer 6: Build & Emulation Engineer (빌드 및 에뮬레이션 담당)

**Task: Cleanup dead files + update .gitignore + validate build**

| # | Action | Target | Rationale |
|---|--------|--------|-----------|
| 1 | DELETE | `core/ui/component/ErrorBoundary.kt` | Dead code: Not imported anywhere |
| 2 | DELETE | `ui-optimization/` (entire folder) | Stale: audit artifacts, not used by build |
| 3 | DELETE | `TODO_CODE_QUALITY.md` | Stale: pre-Clean Architecture TODO |
| 4 | DELETE | `QUALITY_PLAN.md` | Stale: completed quality plan |
| 5 | DELETE | `docs/CODE_REVIEW_TODO.md` | Stale: outdated review checklist |
| 6 | DELETE | `docs/plans/PLAN_clean-architecture-migration.md` | Stale: migration completed |
| 7 | DELETE | `docs/plans/PLAN_cleanup-architecture.md` | Stale: cleanup completed |
| 8 | DELETE | `docs/ML_PREDICTION_ENHANCEMENT_SPEC.md` | Stale: v1 prediction removed |
| 9 | UPDATE | `.gitignore` | Add: `app/release/`, `app/schemas/` |
| 10 | VERIFY | Build compiles | `./gradlew assembleDebug` after all changes |

**Not deleting** (confirmed used):
- `RetryHelper.kt` - Used by `PyKrxClient.kt`
- `Motion.kt` - Used by `Theme.kt`

---

### Engineer 7: Test Coverage Verification Engineer (테스트 커버리지 검증 엔지니어)

**Task: Verify existing tests pass with changes**

- Review that Converters.kt changes don't break MigrationTest
- Verify that SupervisorJob change doesn't affect HomeViewModelTest
- Confirm getOrThrow changes maintain existing test behavior
- Document current test coverage status and gaps
- Report recommendations for future test additions (no code changes)

---

## Execution Order

```
Phase 1 (Parallel): Security + Performance + Stability + Bug Fix engineers work independently
Phase 2: Code Integration engineer reviews all Phase 1 changes
Phase 3 (Parallel): Build engineer + Test Coverage engineer validate
```

## Summary of All Changes

| Category | Files Modified | Files Deleted | Net Impact |
|----------|---------------|---------------|------------|
| ProGuard | 1 (proguard-rules.pro) | 0 | Release build safety |
| Converters | 1 (Converters.kt) | 0 | Crash prevention |
| Dispatchers | 2 (DataCollectionService, StatisticsViewModel) | 0 | Thread correctness |
| SupervisorJob | 1 (DataCollectionService) | 0 | Failure isolation |
| getOrThrow safety | ~5 files (verify only) | 0 | Exception safety |
| .first() safety | ~8 files | 0 | Empty list safety |
| Bounds checking | 1 (MarketDepositRepositoryImpl) | 0 | IndexOutOfBounds prevention |
| Backup rules | 1 (backup_rules.xml) | 0 | DB backup correctness |
| Dead code | 0 | 1 (ErrorBoundary.kt) | Cleaner codebase |
| Stale files | 0 | 7 files + 1 folder | Cleaner project |
| .gitignore | 1 (.gitignore) | 0 | Build artifacts excluded |
| **Total** | **~20 files** | **8 files + 1 folder** | **Zero functional changes** |
