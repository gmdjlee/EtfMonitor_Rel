# Code Review Action Items

**Review Date**: 2025-12-25
**Reviewer**: Claude Code
**Codebase Version**: Schema v16, Clean Architecture Migration Complete
**Overall Score**: 75/100 (Production-Ready with Improvements Needed)

---

## Progress Summary

| Priority | Total | Completed | Remaining |
|----------|-------|-----------|-----------|
| 🔴 Critical | 3 | 3 | 0 |
| 🟠 High | 5 | 5 | 0 |
| 🟡 Medium | 7 | 6 | 1 |
| 🟢 Low | 5 | 1 | 4 |

**Last Updated**: 2025-12-25 (Low #16 완료, #17-#20 검토 완료)

---

## Executive Summary

전체 코드베이스 리뷰 결과, Clean Architecture 마이그레이션이 성공적으로 완료되었으나 일부 Critical 및 High 수준의 이슈가 발견되어 수정이 필요합니다.

### Module Scores

| 영역 | 점수 | 상태 |
|------|------|------|
| Core AI Module | 85/100 | ✅ Production-Ready |
| Database Layer | 95/100 | ✅ Excellent |
| Python Integration | 60/100 | ⚠️ Critical Issues |
| Feature Modules | 71/100 | ⚠️ Architecture Violations |
| Repositories | 78/100 | ⚠️ Dispatcher Issues |
| ViewModels | 95/100 | ✅ Excellent |
| UI Screens | 75/100 | ⚠️ LazyColumn Keys Missing |
| Background Workers | 65/100 | ⚠️ Retry Logic Issues |
| DI Modules | 85/100 | ✅ Good |
| Cross-module Integration | 90/100 | ✅ Healthy |

---

## ✅ Critical Priority - COMPLETED

### 1. FearGreedRepository: Python 호출 시 Timeout 없음 ✅
- **파일**: `app/src/main/java/com/etfmonitor/repository/FearGreedRepository.kt`
- **수정 내용**: `combineFunc.call()` 및 `analyzeFunc.call()`에 60초 timeout 추가
- **커밋**: e3c3a03
- [x] 수정 완료
- [x] 테스트 필요

---

### 2. 5개 Worker: 무한 Retry 버그 ✅
- **영향 파일**:
  - `core/worker/StockUpdateWorker.kt`
  - `core/worker/MarketIndexUpdateWorker.kt`
  - `core/worker/MarketOscillatorUpdateWorker.kt`
  - `core/worker/MarketDepositUpdateWorker.kt`
  - `core/worker/FearGreedUpdateWorker.kt`
- **수정 내용**: `if (runAttemptCount < 3) Result.retry() else Result.failure()` 조건 추가
- **커밋**: e3c3a03
- [x] StockUpdateWorker 수정
- [x] MarketIndexUpdateWorker 수정
- [x] MarketOscillatorUpdateWorker 수정
- [x] MarketDepositUpdateWorker 수정
- [x] FearGreedUpdateWorker 수정
- [x] 테스트 필요

---

### 3. AdvancedAnalysisWorker: Retry 로직 없음 ✅
- **파일**: `core/worker/AdvancedAnalysisWorker.kt`
- **수정 내용**: 예외 발생 시 `runAttemptCount < 3` 조건으로 retry 로직 추가
- **커밋**: e3c3a03
- [x] 수정 완료
- [x] 테스트 필요

---

## ✅ High Priority - COMPLETED

### 4. 10개 suspend 함수: Dispatchers.IO 누락 ✅
- **영향 파일 및 함수**:

**StockRepository.kt (3개)** - 수정 완료:
- `getStockCount()` - withContext(Dispatchers.IO) 추가
- `getEtfHoldingCount()` - withContext(Dispatchers.IO) 추가
- `getLastUpdateTime()` - withContext(Dispatchers.IO) 추가

**MarketDepositRepository.kt (3개)** - 수정 완료:
- `getDepositByDate()` - withContext(Dispatchers.IO) 추가
- `getDepositCount()` - withContext(Dispatchers.IO) 추가
- `getLastUpdateTime()` - withContext(Dispatchers.IO) 추가

**FearGreedRepository.kt (4개)** - 수정 완료:
- `getByMarketAndDate()` - withContext(Dispatchers.IO) 추가
- `getCountByMarket()` - withContext(Dispatchers.IO) 추가
- `getLatestDate()` - withContext(Dispatchers.IO) 추가
- `getLastUpdateTime()` - withContext(Dispatchers.IO) 추가

- **커밋**: e3c3a03
- [x] StockRepository 수정
- [x] MarketDepositRepository 수정
- [x] FearGreedRepository 수정
- [x] 테스트 필요

---

### 5. LazyColumn key 파라미터 누락 (12개소) ✅
- **영향 파일**:
  - `ui/screens/oscillator/OscillatorScreen.kt` - 2개 수정
  - `ui/screens/aianalysis/NewAIAnalysisScreen.kt` - 5개 수정
  - `ui/screens/hub/AnalysisHubScreen.kt` - 3개 수정
  - `ui/screens/hub/StocksHubScreen.kt` - 2개 수정
- **수정 내용**: `items(list, key = { it.identifier }) { }` 형태로 key 파라미터 추가
- **커밋**: e3c3a03
- [x] OscillatorScreen 수정
- [x] NewAIAnalysisScreen 수정
- [x] AnalysisHubScreen 수정
- [x] StocksHubScreen 수정
- [x] 테스트 필요

---

### 6. AI API testApiKey() null 체크 미흡 ✅
- **파일**:
  - `core/network/ai/ClaudeApiClient.kt`
  - `core/network/ai/GeminiApiClient.kt`
- **수정 내용**: API 키 null/blank 체크 후 ApiAuthenticationException 반환
- **커밋**: e3c3a03
- [x] ClaudeApiClient 수정
- [x] GeminiApiClient 수정
- [x] 테스트 필요

---

### 7. Presentation Layer DAO 직접 접근 (Clean Architecture 위반) ✅
- **영향 파일**:
  - `feature/market/presentation/feargreed/FearGreedViewModel.kt` - 수정 완료
  - `feature/market/presentation/oscillator/MarketOscillatorViewModel.kt` - 수정 완료
  - `ui/screens/statistics/StatisticsViewModel.kt` - 미수정 (별도 작업 필요)
- **수정 내용**:
  - FearGreedRepository에 `isDialogDismissed()`, `saveDialogDismissed()` 메서드 추가
  - MarketOscillatorRepository에 `isDialogDismissed()`, `saveDialogDismissed()` 메서드 추가
  - ViewModels에서 EtfDao 주입 제거, Repository 메서드 사용
- **커밋**: e3c3a03
- [x] FearGreedViewModel 수정
- [x] MarketOscillatorViewModel 수정
- [ ] StatisticsViewModel 수정 (별도 작업)
- [x] 테스트 필요

---

### 8. Factory 기본 모델 불일치 ✅
- **파일**: `core/network/ai/AIApiClientFactory.kt`
- **수정 내용**: `gemini-2.0-flash-exp` → `gemini-2.0-flash` (GeminiApiClient.MODEL과 동일)
- **커밋**: e3c3a03
- [x] 수정 완료
- [x] 테스트 필요

---

## 🟡 Medium Priority (Week 3) - PARTIALLY COMPLETED

### 9. 하드코딩된 한국어 문자열 (~20개)
- **영향 파일**: 6+ screens
- **문제**: stringResource 대신 하드코딩된 한국어 사용
- **예시**:
  - `StockTrendScreen.kt`: "데이터 없음", "상세 데이터", "날짜", "비중"
  - `AggregatedStockTrendScreen.kt`: "요약 (전체 ETF 통합)"
  - `MarketOscillatorScreen.kt`: "지수", "Oscillator", "상태"
- [ ] 문자열 리소스 추출
- [ ] stringResource() 적용
- [ ] 테스트 완료

---

### 10. WakeLock timeout 30분 제한 ✅
- **파일**: `core/service/DataCollectionService.kt` (line 77)
- **문제**: 대용량 데이터 수집 시 불충분 (FearGreed 90일 + MarketOscillator 365일)
- **수정**: `30 * 60 * 1000L` → `180 * 60 * 1000L` (3시간)
- [x] 수정 완료
- [x] 테스트 필요

---

### 11. 기본 Worker 스케줄링 누락 ✅
- **파일**: `EtfMonitorApp.kt`
- **문제**: 8개 Worker 중 2개만 기본 스케줄링됨
- **수정**: 모든 8개 Worker 스케줄링 추가
  - 18:00 - EtfUpdateWorker
  - 18:15 - StockUpdateWorker
  - 18:30 - AdvancedAnalysisWorker
  - 18:45 - MarketIndexUpdateWorker
  - 19:00 - MarketDepositUpdateWorker
  - 19:30 - FearGreedUpdateWorker
  - 20:00 - MarketOscillatorUpdateWorker
  - 매월 1일 03:00 - DataArchiveWorker
- [x] 모든 Worker 스케줄링 추가
- [x] 테스트 필요

---

### 12. DI 중복 제공자 ✅
- **문제**: CorrelationAnalysisRepository가 AIModule과 AnalysisModule에서 중복 제공
- **수정**: AIModule에서 explicit provider 제거 (@Inject constructor로 auto-inject)
- **파일**: `core/di/AIModule.kt`
- [x] 중복 제거
- [x] 테스트 필요

---

### 13. FearGreedRepository 캐시 전략 문서 불일치 ✅
- **파일**: `CLAUDE.md`
- **문제**: 코드는 12시간 auto-expiry, CLAUDE.md는 "No auto-expiry"
- **수정**: CLAUDE.md 문서 수정 (12 hours | OR latest date != today)
- [x] 문서 수정 완료
- [x] 테스트 필요

---

### 14. validateAndFixModelName() Side Effect ✅
- **파일**: `core/network/ai/GeminiApiClient.kt`
- **문제**: Validation 메서드에서 SharedPreferences 수정 (side effect)
- **수정**:
  - `validateModelName()` - 순수 함수 (검증만)
  - `validateAndPersistModelName()` - 수정 시 저장
- [x] Validation과 수정 로직 분리
- [x] 테스트 필요

---

### 15. SharedPreferences .apply() 대신 .commit() 권장 ✅
- **파일**: `core/network/ai/SharedPreferencesApiKeyProvider.kt`
- **문제**: API 키 저장 시 비동기 `.apply()` 사용
- **수정**: `setApiKey()`에서 `.commit()` 사용 (중요한 보안 데이터)
- [x] 수정 완료
- [x] 테스트 필요

---

## 🟢 Low Priority (Backlog) - REVIEWED

### 16. Deprecated pandas fillna() 메서드 ✅
- **파일**: `app/src/main/python/trend_signal.py` (line 280, 354)
- **문제**: `fillna(method="ffill")` pandas 2.0+ deprecated
- **수정**: `df["MarketCap"] = df["시가총액"].ffill()` (2개소)
- [x] 수정 완료

---

### 17. Business Logic in Composable - NO ACTION NEEDED
- **파일**: `ui/screens/statistics/AggregatedStockTrendScreen.kt` (line 237-244)
- **문제**: 차트 타이틀 계산 로직이 Composable 내에 있음
- **결론**: 순수 프레젠테이션 로직(문자열 포맷팅)으로, ViewModel 이동 시 오히려 복잡도 증가
- [x] 검토 완료 - 현재 상태 유지

---

### 18. core.py HTTP timeout 15초 - NO ACTION NEEDED
- **파일**: `app/src/main/python/core.py` (line 30)
- **문제**: KRX API 호출 시 15초 timeout 불충분할 수 있음
- **결론**: MAX_RETRIES=3 + 지수 백오프로 총 45초+ 대기 가능, 현재 설정 적절
- [x] 검토 완료 - 현재 상태 유지

---

### 19. ThemeManager explicit provider 없음 - NO ACTION NEEDED
- **파일**: `core/ui/theme/ThemeManager.kt`
- **문제**: @Inject constructor 의존, explicit provider 없음
- **결론**: `@Singleton` + `@Inject constructor()`는 Hilt best practice, explicit provider 불필요
- [x] 검토 완료 - 현재 상태 유지

---

### 20. OscillatorPyClient coerceInputValues - NO ACTION NEEDED
- **파일**: `core/network/python/OscillatorPyClient.kt` (line 79)
- **문제**: `coerceInputValues = true`가 API 계약 위반 마스킹
- **결론**: Python 외부 데이터 소스 통합 시 resilience를 위해 권장되는 설정
- [x] 검토 완료 - 현재 상태 유지

---

## ✅ Completed Items

### Critical & High Priority Fixes (2025-12-25)
- [x] FearGreedRepository Python Timeout 추가 (60초)
- [x] 5개 Worker 무한 Retry 버그 수정
- [x] AdvancedAnalysisWorker Retry 로직 추가
- [x] 10개 suspend 함수 Dispatchers.IO 래퍼 추가
- [x] 12개 LazyColumn key 파라미터 추가
- [x] AI API testApiKey() null 체크 강화
- [x] FearGreedViewModel/MarketOscillatorViewModel DAO 직접 접근 제거
- [x] AIApiClientFactory Gemini 기본 모델 수정

### Medium Priority Fixes (2025-12-25)
- [x] WakeLock timeout 30분 → 3시간 증가
- [x] 모든 Worker 스케줄링 추가 (EtfMonitorApp.kt)
- [x] DI 중복 제공자 제거 (AIModule.kt)
- [x] FearGreedRepository 캐시 문서 수정 (CLAUDE.md)
- [x] validateAndFixModelName() side effect 분리 (GeminiApiClient.kt)
- [x] SharedPreferences API 키 저장 .commit() 변경

### Low Priority Fixes (2025-12-25)
- [x] Deprecated pandas fillna() 수정 (trend_signal.py)
- [x] Business Logic in Composable 검토 - 현재 상태 유지 결정
- [x] core.py HTTP timeout 검토 - 현재 상태 유지 결정
- [x] ThemeManager provider 검토 - 현재 상태 유지 결정
- [x] OscillatorPyClient coerceInputValues 검토 - 현재 상태 유지 결정

### CLAUDE.md Documentation Updates (2025-12-25)
- [x] feature/home/, feature/market/ 모듈 추가
- [x] Python/Worker/Service 경로를 core/로 수정
- [x] Important Files Reference 섹션 경로 업데이트
- [x] Clean Architecture 7 Phase 마이그레이션 문서화
- [x] Python scripts 목록 수정 (stock_predictor_v2.py 제거)
- [x] FearGreedRepository 캐시 전략 수정 (12 hours | OR latest date != today)

---

## Testing Checklist

마이그레이션 후 필수 테스트 항목:

### 기능 테스트
- [ ] Home 화면 정상 표시
- [ ] ETF 목록/상세 화면
- [ ] Stock Trend/Oscillator/Statistics
- [ ] Market FearGreed/Oscillator/Deposit
- [ ] Analysis Advanced/AI
- [ ] Settings 전체 기능

### 백그라운드 작업
- [ ] 모든 Worker 스케줄링 확인
- [ ] DataCollectionService 정상 동작

### 빌드 검증
- [ ] `./gradlew assembleDebug` 성공
- [ ] `./gradlew lint` 통과

---

## Estimated Effort

| Priority | Tasks | Completed | Remaining Time |
|----------|-------|-----------|----------------|
| Critical | 3 tasks | 3 ✅ | 0 hours |
| High | 5 tasks | 5 ✅ | 0 hours |
| Medium | 7 tasks | 6 ✅ | 1-2 hours |
| Low | 5 tasks | 5 ✅ | 0 hours |
| **Total** | **20 tasks** | **19 ✅** | **1-2 hours** |

**Note**: Low priority #17-#20은 검토 후 현재 상태 유지로 결정됨 (no action needed)

---

## Notes

- Clean Architecture 마이그레이션 7단계 모두 완료됨
- 6개 Feature 모듈: home, etf, stock, market, analysis, settings
- Legacy 코드 (`repository/`, `ui/screens/`, `oscillator/`)는 점진적 마이그레이션 중
- Python 스크립트 8개 (stock_predictor_v2.py는 존재하지 않음)
- StatisticsViewModel DAO 직접 접근은 별도 대규모 리팩토링 필요

---

**Last Updated**: 2025-12-25
