# TASK.md — Post-Migration Bug Fixes & Improvements

## Phase 1: Zero-Data Bug Root Cause Analysis (D-001 ~ D-010) ✅

- [x] **D-001** Trace data pipeline: Network → Parsing → DTO → Repository → UseCase → ViewModel → UI
- [x] **D-002** Identify kotlin_krx behaviors (reverse chronological order + wrong endpoint)
- [x] **D-003** Isolate zero-point: Repository layer wrong date + wrong API endpoint
- [x] **D-004** Document root cause in PROGRESS.md
- [x] **D-005** Fix: kotlin_krx endpoint (MDCSTAT01501) + `dates.firstOrNull()` + 30-day fallback
- [x] **D-006** Add 6-checkpoint logging at all pipeline boundaries
- [x] **D-007** Verify: Non-zero data (Samsung 005930, sharesOutstanding=5,919,637,922)
- [x] **D-008** Output correctness confirmed (correct KRX API usage)
- [x] **D-009** Integration test: SamsungMarketCapTest.kt in kotlin_krx
- [x] **D-010** Build verification + ROOT_CAUSE_REPORT.md

## Phase 2: Investor Trading Data Fix ✅

- [x] **F-001** Fix investor trading data (외국인/기관 5일 누적) all-zero bug
  - Commit: 5c0e981

## Phase 3: Market Feature Migration ✅

- [x] **M-001** Migrate MarketIndexRepositoryImpl to kotlin_krx
  - Commit: 32c9d82
- [x] **M-002** Migrate MarketDepositRepositoryImpl to Kotlin web scraping
  - Commit: 8f97d74
- [x] **M-003** Complete Market feature migration (3/3)
  - Commit: a71f26f

## Phase 4: pykrx Complete Removal ✅

- [x] **P-001** Remove pykrx dependency and PyKrxClient class
  - Commit: e9cd9e0
  - Achievement: 100% pykrx migration

## Phase 5: Chart Period Selection Fix ✅

- [x] **C-001** Fix date format mismatch in `filterStockDataByRange()` (yyyy-MM-dd → yyyyMMdd)
- [x] **C-002** Add period filtering for TrendSignal, ElderImpulse, DemarkTD charts
- [x] **C-003** Cache full chart data for client-side filtering (fullTrendSignalData, fullElderImpulseData, fullDemarkTDData)
- [x] **C-004** Reverse chart data ordering (newest data on right side)
- [x] **C-005** Move marker view and x-axis formatter to update block for correct recomposition
  - Commit: c2ff295

## Pending

- [ ] **N-001** Remove debug checkpoint logs or adjust to VERBOSE level
- [ ] **N-002** Restore maxDays from 365 to 730 (after kotlin_krx date chunking fix)
- [ ] **N-003** Increase ETF list timeout to 60s
- [ ] **N-004** Manual QA: 5개 차트 기간 선택 동작 검증
