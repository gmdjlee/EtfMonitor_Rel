# Code Review Action Items

**Review Date**: 2025-12-25
**Reviewer**: Claude Code
**Codebase Version**: Schema v16, Clean Architecture Migration Complete
**Overall Score**: 75/100 (Production-Ready with Improvements Needed)

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

## 🔴 Critical Priority (Week 1)

### 1. FearGreedRepository: Python 호출 시 Timeout 없음
- **파일**: `app/src/main/java/com/etfmonitor/repository/FearGreedRepository.kt`
- **라인**: 175
- **문제**: `combineFunc.call(startDate, endDate)`에 timeout이 없음
- **영향**: 네트워크 문제 시 앱 스레드 무한 대기
- **수정**:
```kotlin
// Before
val dfObject = combineFunc.call(startDate, endDate)

// After
val dfObject = withTimeout(60_000L) {
    combineFunc.call(startDate, endDate)
}
```
- [ ] 수정 완료
- [ ] 테스트 완료

---

### 2. 5개 Worker: 무한 Retry 버그
- **영향 파일**:
  - `core/worker/StockUpdateWorker.kt` (line 50)
  - `core/worker/MarketIndexUpdateWorker.kt` (line 47)
  - `core/worker/MarketOscillatorUpdateWorker.kt` (line 51)
  - `core/worker/MarketDepositUpdateWorker.kt` (line 49)
  - `core/worker/FearGreedUpdateWorker.kt` (line 48)
- **문제**: `runAttemptCount` 체크 없이 `Result.retry()` 반환
- **영향**: 데이터 소스 장애 시 무한 재시도로 배터리/네트워크 낭비
- **수정**:
```kotlin
// Before
if (result.isSuccess) {
    Result.success()
} else {
    Result.retry()  // 무한 재시도!
}

// After
if (result.isSuccess) {
    Result.success()
} else {
    if (runAttemptCount < 3) Result.retry() else Result.failure()
}
```
- [ ] StockUpdateWorker 수정
- [ ] MarketIndexUpdateWorker 수정
- [ ] MarketOscillatorUpdateWorker 수정
- [ ] MarketDepositUpdateWorker 수정
- [ ] FearGreedUpdateWorker 수정
- [ ] 테스트 완료

---

### 3. AdvancedAnalysisWorker: Retry 로직 없음
- **파일**: `core/worker/AdvancedAnalysisWorker.kt`
- **문제**: 예외 발생 시 바로 `Result.failure()` 반환
- **영향**: 일시적 네트워크 오류에도 분석 실패
- **수정**: 다른 Worker와 동일하게 retry 로직 추가
- [ ] 수정 완료
- [ ] 테스트 완료

---

## 🟠 High Priority (Week 2)

### 4. 10개 suspend 함수: Dispatchers.IO 누락
- **영향 파일 및 함수**:

**StockRepository.kt (3개)**:
- `getStockCount()` - line 49
- `getEtfHoldingCount()` - line 51
- `getLastUpdateTime()` - line 53

**MarketDepositRepository.kt (3개)**:
- `getDepositByDate()` - line 41-42
- `getDepositCount()` - line 44
- `getLastUpdateTime()` - line 46

**FearGreedRepository.kt (4개)**:
- `getByMarketAndDate()` - line 38-39
- `getCountByMarket()` - line 41
- `getLatestDate()` - line 43
- `getLastUpdateTime()` - line 45

- **수정**:
```kotlin
// Before
suspend fun getStockCount(): Int = stockDao.getCount()

// After
suspend fun getStockCount(): Int = withContext(Dispatchers.IO) {
    stockDao.getCount()
}
```
- [ ] StockRepository 수정
- [ ] MarketDepositRepository 수정
- [ ] FearGreedRepository 수정
- [ ] 테스트 완료

---

### 5. LazyColumn key 파라미터 누락 (12개소)
- **영향 파일**:
  - `ui/screens/oscillator/OscillatorScreen.kt` (line 210, 627)
  - `ui/screens/aianalysis/NewAIAnalysisScreen.kt` (line 287, 325, 1007, 1060, 2025)
  - `ui/screens/hub/AnalysisHubScreen.kt` (line 362, 400, 2118)
  - `ui/screens/hub/StocksHubScreen.kt` (line 144, 811)
- **문제**: `items(list) { }` 에서 key 파라미터 없음
- **영향**: 리컴포지션 시 성능 저하, 애니메이션 오류
- **수정**:
```kotlin
// Before
items(suggestions) { stock -> ... }

// After
items(suggestions, key = { it.ticker }) { stock -> ... }
```
- [ ] OscillatorScreen 수정
- [ ] NewAIAnalysisScreen 수정
- [ ] AnalysisHubScreen 수정
- [ ] StocksHubScreen 수정
- [ ] 테스트 완료

---

### 6. AI API testApiKey() null 체크 미흡
- **파일**:
  - `core/network/ai/ClaudeApiClient.kt` (line 233-242)
  - `core/network/ai/GeminiApiClient.kt` (line 377)
- **문제**: API 키가 null일 때 빈 문자열로 API 호출 시도
- **수정**:
```kotlin
// Before
val apiKey = apiKeyProvider.getApiKey(AIProvider.CLAUDE) ?: ""

// After
val apiKey = apiKeyProvider.getApiKey(AIProvider.CLAUDE)
if (apiKey.isNullOrBlank()) {
    return@withContext Result.failure(
        ApiAuthenticationException("Claude", "API key not configured")
    )
}
```
- [ ] ClaudeApiClient 수정
- [ ] GeminiApiClient 수정
- [ ] 테스트 완료

---

### 7. Presentation Layer DAO 직접 접근 (Clean Architecture 위반)
- **영향 파일**:
  - `feature/market/presentation/feargreed/FearGreedViewModel.kt` (line 6, 43, 67, 86-88)
  - `feature/market/presentation/oscillator/MarketOscillatorViewModel.kt` (line 6, 44, 89, 108-110)
  - `ui/screens/statistics/StatisticsViewModel.kt` (line 5-6, 52-53, 124, 177-184)
- **문제**: ViewModels이 EtfDao, SearchHistoryDao 직접 사용
- **해결방안**: Settings UseCase를 통해 접근하도록 리팩토링
- [ ] FearGreedViewModel 수정
- [ ] MarketOscillatorViewModel 수정
- [ ] StatisticsViewModel 수정
- [ ] 테스트 완료

---

### 8. Factory 기본 모델 불일치
- **파일**: `core/network/ai/AIApiClientFactory.kt` (line 62)
- **문제**: Gemini `-exp` 모델 반환 후 GeminiApiClient에서 바로 교체됨
- **수정**: `gemini-2.0-flash-exp` → `gemini-2.0-flash`
- [ ] 수정 완료
- [ ] 테스트 완료

---

## 🟡 Medium Priority (Week 3)

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

### 10. WakeLock timeout 30분 제한
- **파일**: `core/service/DataCollectionService.kt` (line 77)
- **문제**: 대용량 데이터 수집 시 불충분 (FearGreed 90일 + MarketOscillator 365일)
- **수정**: `30 * 60 * 1000L` → `180 * 60 * 1000L` (3시간)
- [ ] 수정 완료
- [ ] 테스트 완료

---

### 11. 기본 Worker 스케줄링 누락
- **파일**: `EtfMonitorApp.kt`
- **문제**: 8개 Worker 중 2개만 기본 스케줄링됨
- **누락된 Worker**:
  - EtfUpdateWorker
  - StockUpdateWorker
  - MarketDepositUpdateWorker
  - FearGreedUpdateWorker
  - MarketIndexUpdateWorker
  - DataArchiveWorker
- [ ] 모든 Worker 스케줄링 추가
- [ ] 테스트 완료

---

### 12. DI 중복 제공자
- **문제**: CorrelationAnalysisRepository가 AIModule과 AnalysisModule에서 중복 제공
- **파일**:
  - `core/di/AIModule.kt` (line 145-163)
  - `feature/analysis/di/AnalysisModule.kt` (line 39-48)
- [ ] 중복 제거
- [ ] 테스트 완료

---

### 13. FearGreedRepository 캐시 전략 문서 불일치
- **파일**: `repository/FearGreedRepository.kt` (line 26)
- **문제**: 코드는 12시간 auto-expiry, CLAUDE.md는 "No auto-expiry"
- [ ] 코드 또는 문서 수정
- [ ] 테스트 완료

---

### 14. validateAndFixModelName() Side Effect
- **파일**: `core/network/ai/GeminiApiClient.kt` (line 193-228)
- **문제**: Validation 메서드에서 SharedPreferences 수정 (side effect)
- [ ] Validation과 수정 로직 분리
- [ ] 테스트 완료

---

### 15. SharedPreferences .apply() 대신 .commit() 권장
- **파일**: `core/network/ai/SharedPreferencesApiKeyProvider.kt` (line 46-48)
- **문제**: API 키 저장 시 비동기 `.apply()` 사용
- **영향**: Race condition 가능성 (낮음)
- [ ] 검토 후 필요시 수정

---

## 🟢 Low Priority (Backlog)

### 16. Deprecated pandas fillna() 메서드
- **파일**: `app/src/main/python/trend_signal.py` (line 280)
- **문제**: `fillna(method="ffill")` pandas 2.0+ deprecated
- **수정**: `df["MarketCap"] = df["시가총액"].ffill()`
- [ ] 수정 완료

---

### 17. Business Logic in Composable
- **파일**: `ui/screens/trend/AggregatedStockTrendScreen.kt` (line 192-244)
- **문제**: 차트 타이틀 계산 로직이 Composable 내에 있음
- [ ] ViewModel로 이동

---

### 18. core.py HTTP timeout 15초
- **파일**: `app/src/main/python/core.py` (line 30)
- **문제**: KRX API 호출 시 15초 timeout 불충분할 수 있음
- [ ] 검토 후 필요시 30초로 증가

---

### 19. ThemeManager explicit provider 없음
- **파일**: `core/ui/theme/ThemeManager.kt`
- **문제**: @Inject constructor 의존, explicit provider 없음
- [ ] CoreUIModule 생성 검토

---

### 20. OscillatorPyClient coerceInputValues
- **파일**: `core/network/python/OscillatorPyClient.kt` (line 79)
- **문제**: `coerceInputValues = true`가 API 계약 위반 마스킹
- [ ] Strict mode로 변경 검토

---

## ✅ Completed Items

### CLAUDE.md Documentation Updates (2025-12-25)
- [x] feature/home/, feature/market/ 모듈 추가
- [x] Python/Worker/Service 경로를 core/로 수정
- [x] Important Files Reference 섹션 경로 업데이트
- [x] Clean Architecture 7 Phase 마이그레이션 문서화
- [x] Python scripts 목록 수정 (stock_predictor_v2.py 제거)

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

| Priority | Tasks | Estimated Time |
|----------|-------|----------------|
| Critical | 3 tasks | 4-6 hours |
| High | 5 tasks | 8-12 hours |
| Medium | 7 tasks | 6-8 hours |
| Low | 5 tasks | 3-4 hours |
| **Total** | **20 tasks** | **21-30 hours** |

---

## Notes

- Clean Architecture 마이그레이션 7단계 모두 완료됨
- 6개 Feature 모듈: home, etf, stock, market, analysis, settings
- Legacy 코드 (`repository/`, `ui/screens/`, `oscillator/`)는 점진적 마이그레이션 중
- Python 스크립트 8개 (stock_predictor_v2.py는 존재하지 않음)

---

**Last Updated**: 2025-12-25
