# 코드 품질 개선 TODO

> 마지막 업데이트: 2025-12-06
> 관련 브랜치: `claude/refactor-large-files-0151t7mQjXV4o44pcnXQ948G`

## 완료된 작업 ✅

### 1. Critical 이슈 수정
- [x] Not-null assertions (`!!`) 제거 - `CorrelationAnalyzer.kt`, `PredictionScreen.kt`
- [x] TODO 완성 - `AdvancedAnalysisRepository.kt` volatility 계산 구현
- [x] Custom Exception 타입 추가 - `utils/Exceptions.kt`
- [x] Python Client 예외 처리 개선 - `PyKrxClient.kt`, `OscillatorPyClient.kt`

### 2. 문자열 리소스화
- [x] 100+ 문자열을 `strings.xml`에 추가

### 3. KDoc 문서화
- [x] `DataRepository.kt` 문서화
- [x] `HomeViewModel.kt` 문서화
- [x] `PyKrxClient.kt` 문서화
- [x] `OscillatorPyClient.kt` 문서화

### 4. 대형 파일 분리 (>1000 lines)
- [x] `AdvancedDashboardScreen.kt` 분리 → `DashboardTab.kt`, `MarketCapTab.kt`, `LiquidityTab.kt`, `SectorTab.kt`
- [x] `SettingsScreen.kt` 분리 → `ThemeSettings.kt`, `ApiKeySettings.kt`, `ScheduleSettings.kt`
- [x] `StatisticsScreen.kt` 분리 → `RankingTab.kt`, `AnalysisTab.kt`, `CashDepositTab.kt`
- [x] `ChartComponents.kt` 분리 → `LineCharts.kt`, `BarCharts.kt`, `ChartUtils.kt`
- [x] `HomeScreen.kt` 분리 → `HomeSummaryCard.kt`, `HomeDialogs.kt`, `HomeQuickActions.kt`
- [x] `DataRepository.kt` 분리 → `EtfDataRepository.kt`, `HoldingDataRepository.kt`

### 5. String Resource 적용 (일부)
- [x] `HomeScreen.kt` - 메뉴 아이템, 다이얼로그, 요약카드 → stringResource() 적용
- [x] `HomeDialogs.kt` - 모든 다이얼로그 텍스트 → stringResource() 적용
- [x] `HomeSummaryCard.kt` - 시장 현황 레이블 → stringResource() 적용
- [x] `PredictionScreen.kt` - 예측 UI 텍스트 → stringResource() 적용
- [x] `AdvancedDashboardScreen.kt` - 탭, 카드, 차트 레이블 → stringResource() 적용
- [x] `SettingsScreen.kt` - 모든 설정 탭 컴포넌트 → stringResource() 적용
- [x] `StatisticsScreen.kt` - 메인 화면 → stringResource() 적용

### 6. Generic Exception 교체 (AI Clients)
- [x] `ClaudeApiClient.kt` - ApiException, ApiAuthenticationException, DataParsingException 사용
- [x] `GeminiApiClient.kt` - ApiException, ApiAuthenticationException, DataParsingException 사용
- [x] `AIResponseParser.kt` - DataParsingException 사용

### 7. Generic Exception 분석 (Repository/ViewModel)
- [x] `MainActivity.kt` - 방어적 예외 처리 (로깅 후 무시), 현재 패턴 적절
- [x] `HomeViewModel.kt` - 방어적 예외 처리 (로깅 후 null 반환), 현재 패턴 적절
- [x] `DataRepository.kt` - DataProgress.Error emit 패턴, 현재 패턴 적절
- [x] `AdvancedAnalysisRepository.kt` - fallback 값 반환 패턴, 현재 패턴 적절

---

## 미완료 작업 📋

### High Priority (높은 우선순위)

#### 1. Unit Tests 추가
테스트 코드가 전무하여 회귀 테스트가 불가능합니다.

**필요한 테스트:**
- [ ] Repository 테스트
  - `DataRepository` - 데이터 로딩/저장 로직
  - `AdvancedAnalysisRepository` - 분석 로직
  - `FearGreedRepository` - Fear & Greed 계산
- [ ] ViewModel 테스트
  - `HomeViewModel` - 상태 전환 로직
  - `StatisticsViewModel` - 정렬/필터 로직
- [ ] Database Migration 테스트
  - 13개 마이그레이션 (v1→v14) 검증
- [ ] Python Client 테스트
  - 타임아웃/재시도 로직

**예시 테스트 위치:**
```
app/src/test/java/com/etfmonitor/
├── repository/
│   ├── DataRepositoryTest.kt
│   └── AdvancedAnalysisRepositoryTest.kt
├── viewmodel/
│   └── HomeViewModelTest.kt
└── database/
    └── MigrationTest.kt
```

#### 2. Screen에 String Resource 적용 (나머지) ✅ 완료
HomeScreen과 PredictionScreen은 완료되었으며, 나머지 Screen에도 적용 완료.

**완료된 파일:**
- [x] `AdvancedDashboardScreen.kt` - 80+ strings → stringResource() 적용
- [x] `SettingsScreen.kt` - 150+ strings → stringResource() 적용 (모든 컴포넌트)
- [x] `StatisticsScreen.kt` - 20+ strings → stringResource() 적용

#### 3. Generic Exception 교체 분석 ✅ 완료
AI Clients는 완료되었으며, Repository/ViewModel은 분석 결과 현재 패턴이 적절합니다.

**분석 결과:**
- [x] `MainActivity.kt` - 방어적 예외 처리 패턴 (로깅 후 무시), 적절함
- [x] `HomeViewModel.kt` - 방어적 예외 처리 패턴 (fallback 반환), 적절함
- [x] `DataRepository.kt` - DataProgress.Error emit 패턴, 적절함
- [x] `AdvancedAnalysisRepository.kt` - fallback 값 반환 패턴, 적절함

**참고:** Result.failure(Exception(...)) 패턴을 사용하는 다른 Repository 파일들
(FearGreedRepository, MarketDepositRepository 등)은 향후 개선 가능

---

### Medium Priority (중간 우선순위)

#### 4. 중복 코드 제거

**`AdvancedAnalysisRepository.kt`:**
- 상관관계 계산 패턴 중복 (lines 160-250)
- 데이터 수집 패턴 중복

**제안:**
```kotlin
// 공통 헬퍼 함수 추출
private fun calculateCorrelationMetrics(data: List<Double>): CorrelationMetrics
private fun collectMarketData(market: String, dateRange: Pair<String, String>): MarketData
```

#### 5. AppLogger 활용
`utils/AppLogger.kt`가 존재하지만 직접 `Log` 호출이 57개 있습니다.

**변환 예시:**
```kotlin
// Before
Log.d(TAG, "message")

// After
AppLogger.d(TAG, "message")  // 조건부 로깅 지원
```

---

### Low Priority (낮은 우선순위)

#### 6. 주석 처리된 코드 제거
- [ ] `PyKrxClient.kt` lines 92-100+ - 디버그 로그 블록

#### 7. SQL 쿼리 문서화
`EtfDao.kt`의 복잡한 쿼리에 설명 추가

#### 8. 추가 KDoc 문서화
- [ ] `FearGreedRepository.kt`
- [ ] `MarketDepositRepository.kt`
- [ ] `StockAnalysisRepository.kt`
- [ ] `StatisticsViewModel.kt`
- [ ] `SettingsViewModel.kt`

---

## 참고 사항

### 새로 추가된 파일
- `app/src/main/java/com/etfmonitor/utils/Exceptions.kt` - 커스텀 예외 클래스

### 커스텀 예외 계층 구조
```
EtfMonitorException (기본)
├── NetworkException
├── DataException
│   ├── DataNotFoundException
│   ├── DataParsingException
│   └── InsufficientDataException
├── PythonException
│   ├── PythonTimeoutException
│   └── PythonRuntimeException
└── ApiException
    ├── ApiAuthenticationException
    └── ApiRateLimitException
```

### strings.xml 카테고리
- Error Messages (`error_*`)
- Dialog Titles (`dialog_*`)
- PredictionScreen (`prediction_*`)
- HomeScreen (`home_*`)
- StatisticsScreen (`statistics_*`)
- AdvancedDashboard (`advanced_*`)
- Content Descriptions (`cd_*`)
- Formatters (`format_*`)

---

## 작업 시작 방법

1. 이 파일을 참고하여 작업 항목 선택
2. 해당 브랜치에서 작업 진행
3. 완료된 항목은 `[x]`로 표시
4. PR 생성 시 이 파일 업데이트
