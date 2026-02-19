# TASK.md — blood_indicator.py Kotlin Migration

## Phase 1: Analysis (iterations 1-2)
- [x] B-001 Read existing migration analysis reports for blood_indicator.py context
- [x] B-002 Analyze blood_indicator.py: catalog all functions, KRX API calls, calculation logic, data flows
- [x] B-003 Create implementation plan: function-to-module mapping. Architect approves.

## Phase 2: Data and Domain Layer (iterations 3-5)
- [x] B-004 Create BloodIndicatorClient.kt — OkHttp HTTP client for Yahoo Finance + FRED API
- [x] B-005 Create BloodIndicatorCalculator.kt — Weekly resampling, 100-week SMA, signal logic
- [x] B-006 Update BloodIndicatorRepositoryImpl to use new Kotlin client

## Phase 3: Integration and Cleanup (iterations 6-7)
- [x] B-007 Remove BloodIndicatorPyClient, PythonModule, blood_indicator.py, core.py
- [x] B-008 Remove Chaquopy from build config (plugin, pip, proguard, versions, maven repos)
- [x] B-009 Update KrxApiFunctionalityTest to remove Python imports

## Phase 4: Verification and Documentation (iterations 8-10)
- [x] B-010 Build verification: assembleDebug SUCCESS, test 57/57 PASS
- [x] B-011 Update CLAUDE.md, CHANGELOG.md, FEATURE_CHECKLIST.md, PROGRESS.md
