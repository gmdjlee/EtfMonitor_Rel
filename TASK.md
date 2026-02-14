# TASK.md — Post-Migration Review

## Phase 1: Parity Verification (iterations 1-4)
- [x] **R-001** Diff pre/post migration: list all pykrx API calls, verify kotlin_krx equivalents exist
- [x] **R-002** Verify data flow: each ViewModel → UseCase → Repository → kotlin_krx call chain intact
- [x] **R-003** Compare output formats: ensure response DTOs match previous pykrx data structures
- [x] **R-004** Validate edge cases: error handling, empty responses, network failures, rate limits

## Phase 2: Cleanup (iterations 5-7)
- [x] **R-005** Remove dead code: unused pykrx wrappers, Python bridge files, deprecated imports
- [x] **R-006** Remove unused files/folders: old Python scripts, __pycache__, .py files, legacy configs
- [x] **R-007** Clean build.gradle: remove Python/Chaquopy dependencies, unused libraries

## Phase 3: Quality Gate (iterations 8-12)
- [x] **R-008** Test coverage: run all tests, identify untested migration paths, add missing tests
- [x] **R-009** Performance benchmark: API call latency kotlin_krx vs previous pykrx baseline
- [x] **R-010** Build verification: assembleDebug + assembleRelease clean build
- [x] **R-011** Static analysis: lint, unused resources, import optimization
- [x] **R-012** Stability check: no crashes on key user flows (ETF, oscillator, analysis)

## Phase 4: Documentation (iterations 13-15)
- [x] **R-013** Generate final review report: REVIEW_REPORT.md
- [x] **R-014** Update CLAUDE.md: architecture changes, removed deps, new module structure
- [x] **R-015** Clean git history: squash fixup commits if needed

## Phase 5: USER_MANUAL.md Coverage Verification (Ralph Loop)
- [x] **R-016** Map all USER_MANUAL.md features to implementation (COVERAGE_MAP.md)
- [x] **R-017** Verify function signatures match manual specifications
- [x] **R-018** Validate edge cases documented in manual are handled
- [x] **R-019** Generate COVERAGE_REVIEW_REPORT.md with findings

## Context
- Migration: pykrx (Python) → kotlin_krx (native Kotlin)
- Architecture: MVVM + Clean Architecture + Feature modules
- Key requirement: 100% functional parity with pre-migration
- New verification: USER_MANUAL.md coverage against MarketMonitor implementation