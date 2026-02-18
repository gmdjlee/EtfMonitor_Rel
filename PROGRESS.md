# PROGRESS.md — Post-Migration Bug Fixes & Improvements

## Status: ✅ ALL COMPLETE (2026-02-18)

---

## Phase 1: Zero-Data Bug Fix (Iterations 1-10)

### Root Cause: TWO Bugs in kotlin_krx Integration

**Bug #1: kotlin_krx Wrong API Endpoint**
- `MDCSTAT01602` (전종목등락률) → `MDCSTAT01501` (전종목시세)
- `getMarketCap()` returned 0 records for ALL dates
- Fix: kotlin_krx commit 1438346

**Bug #2: OHLCV Reverse Chronological Order**
- kotlin_krx returns dates [newest...oldest], code used `dates.lastOrNull()`
- Fix: `dates.firstOrNull()` + 30-day fallback loop

**Verification**: Samsung(005930) sharesOutstanding=5,919,637,922, marketCap=1,072,638,391,466,400

### Completed Tasks (D-001 ~ D-010)
- [x] D-001: 6-checkpoint logging system
- [x] D-002: kotlin_krx reverse order + wrong endpoint 식별
- [x] D-003: Wrong date parameter + wrong BLD code 분리
- [x] D-004: Root cause 문서화
- [x] D-005: Endpoint 수정 + firstOrNull + 30일 fallback
- [x] D-006: 파이프라인 전체 checkpoint logging
- [x] D-007: 실제 데이터 검증 SUCCESS
- [x] D-008: 출력값 정합성 확인
- [x] D-009: kotlin_krx 통합 테스트 (SamsungMarketCapTest.kt)
- [x] D-010: 빌드 검증 + ROOT_CAUSE_REPORT.md 생성

---

## Phase 2: Investor Trading Data (commit 5c0e981)

**문제**: 수급 데이터(외국인/기관 5일 누적)가 모두 0으로 표시
**원인**: getTradingByInvestor API 응답 처리 로직 미흡
**수정**: 날짜 매핑 + 빈 응답 처리 + rolling sum 계산 보강

---

## Phase 3: Market Feature Migration (commits 32c9d82 ~ a71f26f)

kotlin_krx 기반 마켓 기능 완전 마이그레이션 (3단계):

1. **MarketIndexRepositoryImpl** → kotlin_krx (commit 32c9d82)
2. **MarketDepositRepositoryImpl** → Kotlin web scraping (commit 8f97d74)
3. **Market feature 마이그레이션 완료** (commit a71f26f)

---

## Phase 4: pykrx 완전 제거 (commit e9cd9e0)

- PyKrxClient 클래스 제거
- build.gradle.kts에서 `pip("pykrx")` 제거
- **100% pykrx 마이그레이션 달성** (kotlin_krx 전환 완료)

---

## Phase 5: Chart Period Selection Fix (commit c2ff295)

### 문제
시가총액 & 수급 오실레이터 차트와 MACD 차트에서 기간 옵션이 동작하지 않음

### Root Cause: 날짜 포맷 불일치
```kotlin
// Bug: toString() → "2025-08-18" (yyyy-MM-dd)
val cutoffStr = cutoffDate.toString()

// Fix: yyyyMMdd 포맷으로 변환
val cutoffStr = cutoffDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
```
kotlin_krx 날짜 데이터는 `yyyyMMdd` 형식인데 `toString()`은 `yyyy-MM-dd`를 반환하여 문자열 비교가 항상 true → 필터링 무동작

### 수정 내용 (4 files, +345/-77)
1. **OscillatorViewModel.kt**: 날짜 포맷 수정 + 5개 차트 전체 기간 필터링 추가
2. **MarketCharts.kt**: 시가총액 차트 데이터 역순 정렬 (최신→오른쪽)
3. **TechnicalCharts.kt**: MACD/Elder/DeMark 차트 역순 정렬 + marker/formatter를 update 블록으로 이동
4. **KrxStockDataRepositoryImpl.kt**: 시가총액 조회 재시도 로직 + 디버그 로깅

### 영향 범위

| 차트 | 수정 전 | 수정 후 |
|------|---------|---------|
| 시가총액 & 수급 오실레이터 | 기간 필터 무동작 | 정상 동작 |
| MACD | 기간 필터 무동작 | 정상 동작 |
| Trend Signal | 기간 필터 없음 | 기간 필터 추가 |
| Elder Impulse | 기간 필터 없음 | 기간 필터 추가 |
| DeMark TD | 기간 필터 없음 | 기간 필터 추가 |

---

## Commit History (Recent)

| Commit | Description |
|--------|-------------|
| c2ff295 | fix: 오실레이터 차트 기간 선택 버그 수정 및 차트 표시 개선 |
| 5c0e981 | fix: Implement investor trading data in stock analysis |
| e9cd9e0 | feat: Remove pykrx dependency completely |
| a71f26f | feat: Complete Market feature migration to kotlin_krx (3/3) |
| 8f97d74 | feat: Migrate MarketDepositRepositoryImpl to Kotlin web scraping |
| 32c9d82 | feat: Migrate MarketIndexRepositoryImpl to kotlin_krx |
| aac4d8a | feat: Migrate TimeSeriesAnalysisHelper to kotlin_krx |

---

## Next Steps

- [ ] Manual QA: 5개 차트 기간 선택 동작 검증
- [ ] 디버그 로그 정리 (checkpoint 로그 제거 또는 레벨 조정)
- [ ] maxDays 730 복원 (kotlin_krx date chunking 수정 후)
- [ ] ETF 목록 조회 timeout 60s로 증가 검토
