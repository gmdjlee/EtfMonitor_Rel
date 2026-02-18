# PROGRESS.md — Project Review
## Status: LOOP_COMPLETE
## Completed
- [x] P-001: Feature catalog → FEATURE_CHECKLIST.md
- [x] P-002: Feature chain verification → 4 CRITICAL, 6 WARNING, 3 SUGGESTION
- [x] P-003: Tests → 57/57 PASS (fixed 23 failures)
- [x] P-004: Build → assembleDebug PASS (8s)
- [x] P-005: Dead code cleanup → Removed BackupViewModel dead import + 5 redundant @Provides in EtfModule (Architect APPROVED)
- [x] P-006: Unused files removed → 27 files deleted (Architect APPROVED)
- [x] P-007: Dependency cleanup → 5 pip + 8 Kotlin deps removed, MinMaxScaler→numpy (Architect APPROVED)
- [x] P-008: Post-cleanup verification → BUILD SUCCESSFUL (3s), 57/57 tests PASS
- [x] P-009: Performance review → 6 CRITICAL, 9 WARNING, 4 INFO (unbounded DAO queries, inefficient patterns)
- [x] P-010: Stability review → 3 CRITICAL, 6 WARNING, 7 INFO (missing guards, error handling gaps)
- [x] P-011: PROJECT_REVIEW_REPORT.md generated (comprehensive 3-phase report with priority fixes)
- [x] P-012: CLAUDE.md updated with current architecture state

## Test Fixes Applied (P-003)
1. `app/build.gradle.kts`: Added `unitTests.isReturnDefaultValues = true`
2. `TestUtils.kt`: Changed `StandardTestDispatcher` → `UnconfinedTestDispatcher`
3. `HomeViewModelTest.kt`: Added `CollectionState.reset()` in @BeforeEach

## Architecture Findings (P-002)
### CRITICAL (4)
- **C1**: `BackupViewModel.kt:8` — Dead import (**FIXED in P-005**)
- **C2**: `AdvancedDashboardViewModel.kt` — 7 DAOs injected directly (out of scope for cleanup)
- **C3**: `HomeViewModel.kt` — 3 cross-feature repos bypassing UseCase (out of scope)
- **C4**: `StatisticsViewModel.kt` — Bypasses UseCase layer (out of scope)

### WARNING (6) — W1 FIXED in P-005, others out of scope for cleanup
### SUGGESTION (3) — out of scope for cleanup

## P-006 Execution Summary (Architect APPROVED)
### Files Removed (27)
- 7 build log dumps (.txt)
- 12 migration report .md files
- 2 disabled test files (.kt.disabled)
- 1 orphan docs/PROGRESS.md
- 1 OscillatorPyClient.kt (zero imports)
- 1 logger.py (unused shim)
- 1 __pycache__/feargreed.cpython-38.pyc
- 2 ui-optimization/ files (all-ui-code.kt, extract-ui.sh) + 2 .md reports

### Comment Cleanup (3 files)
- PythonModule.kt: Removed stale OscillatorPyClient comment
- GetKrxIndexComponentsUseCase.kt: Removed Phase 2 coexistence comment
- GetKrxMarketDataUseCase.kt: Removed Phase 2 coexistence comment

### Added
- `.gitignore`: Added `__pycache__/`

### Kept (Architect decision)
- `kis_client.py`: Keep for future KIS integration (AD-001)
- Migration docs referenced in CLAUDE.md: MIGRATION_STRATEGY.md, PHASE_A_COMPLETION_REPORT.md, ROOT_CAUSE_REPORT.md, docs/PHASE3_MIGRATION_STRATEGY.md

## P-007 Execution Summary (Architect APPROVED)
### Python pip — REMOVED (5 packages)
1. `beautifulsoup4` — replaced by NaverFinanceScraper.kt
2. `scikit-learn` — MinMaxScaler replaced with numpy equivalent in feargreed.py
3. `joblib==1.3.2` — ML scripts deleted
4. `setuptools` — build tool, not runtime
5. `wheel` — build tool, not runtime

### Kotlin deps — REMOVED (8 declarations)
1. `androidx.graphics:graphics-shapes:1.1.0` — zero imports
2. `material3-adaptive` (4 artifacts) — zero imports
3. `junit:junit:4.13.2` — all tests use JUnit5
4. `androidx.arch.core:core-testing:2.2.0` — zero imports (StateFlow, not LiveData)
5. `io.mockk:mockk-android:1.13.10` — zero imports in instrumented tests
6. `com.google.dagger:hilt-android-testing:2.54` + `kspAndroidTest(hilt.android.compiler)` — zero Hilt test annotations

### Kept (Architect REJECTED removal)
- `androidx.cardview:cardview:1.0.0` — active runtime dependency (marker_view.xml → CustomMarkerView)
