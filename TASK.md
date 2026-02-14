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
- [ ] **R-011** Static analysis: lint, unused resources, import optimization
- [ ] **R-012** Stability check: no crashes on key user flows (ETF, oscillator, analysis)

## Phase 4: Documentation (iterations 13-15)
- [ ] **R-013** Generate final review report: REVIEW_REPORT.md
- [ ] **R-014** Update CLAUDE.md: architecture changes, removed deps, new module structure
- [ ] **R-015** Clean git history: squash fixup commits if needed

## Context
- Migration: pykrx (Python) → kotlin_krx (native Kotlin)
- Architecture: MVVM + Clean Architecture + Feature modules
- Key requirement: 100% functional parity with pre-migration