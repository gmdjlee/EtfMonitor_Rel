# ETF Monitor - Production Quality Plan

## 목표: 상용 레벨 코드 완성도 100% 달성

**현재 상태**: 5.2/10 (MODERATELY READY)
**목표 상태**: 9.5+/10 (PRODUCTION READY)

---

## 요약 대시보드

| 카테고리 | 현재 점수 | 목표 점수 | 발견된 이슈 |
|----------|-----------|-----------|-------------|
| 보안 | 7/10 | 9/10 | 7개 |
| 성능 | 6/10 | 9/10 | 8개 |
| 에러 핸들링 | 5/10 | 9/10 | 7개 |
| UI/UX | 7/10 | 9/10 | 7개 |
| 빌드/릴리스 | 4/10 | 10/10 | 9개 |
| 테스팅 | 2/10 | 8/10 | 전체 미구현 |
| 아키텍처 | 8/10 | 9/10 | 2개 |

---

## Phase 1: CRITICAL FIXES (출시 차단 이슈)

### 1.1 데이터베이스 스키마 불일치 해결 [CRITICAL]

**문제**: Migration 14→15에서 생성된 `price_cache`, `enhanced_predictions` 테이블이 Entity로 등록되지 않음

**위치**: `core/database/AppDatabase.kt:635-686`

**해결 방안**:
```kotlin
// 옵션 A: Entity 클래스 생성 (권장)
// 1. core/database/entities/PriceCache.kt 생성
// 2. core/database/entities/EnhancedPrediction.kt 생성
// 3. AppDatabase @Database entities에 등록
// 4. DAO 인터페이스 생성

// 옵션 B: Migration 제거 (데이터 손실 가능)
// - Migration 14→15 제거 및 스키마 정리
```

**작업 항목**:
- [ ] `PriceCache.kt` Entity 생성
- [ ] `EnhancedPrediction.kt` Entity 생성
- [ ] `PriceCacheDao.kt` 생성
- [ ] `EnhancedPredictionDao.kt` 생성
- [ ] AppDatabase entities 배열에 추가
- [ ] DatabaseModule에 DAO 제공자 추가

---

### 1.2 버전 관리 수정 [CRITICAL]

**문제**: `versionCode = 1`, `versionName = "1.0"` - 앱 업데이트 불가능

**위치**: `app/build.gradle.kts:24-25`

**해결 방안**:
```kotlin
android {
    defaultConfig {
        versionCode = 2  // 매 릴리스마다 증가
        versionName = "1.0.1"  // 시맨틱 버저닝 사용
    }
}
```

**작업 항목**:
- [ ] versionCode를 2로 증가
- [ ] versionName을 "1.0.1" 형식으로 변경
- [ ] CHANGELOG.md 파일 생성
- [ ] 버전 증가 체크리스트 문서화

---

### 1.3 Null Safety 개선 [CRITICAL]

**문제**: 504개의 `null` 또는 `!!` 사용으로 런타임 크래시 위험

**주요 위치**:
- Repository 레이어
- ViewModel 상태 처리
- Python 클라이언트 응답 파싱

**해결 방안**:
```kotlin
// ❌ 위험한 패턴
val data = response!!.data

// ✅ 안전한 패턴
val data = response?.data ?: return Result.failure(NullDataException())

// ✅ Elvis 연산자 + 기본값
val data = response?.data.orEmpty()
```

**작업 항목**:
- [ ] 모든 `!!` 연산자 검색 및 제거 (약 200개)
- [ ] null-safe 대안으로 교체
- [ ] Kotlin null safety lint 규칙 활성화

---

### 1.4 Silent Failure 해결 [CRITICAL]

**문제**: Repository에서 실패해도 UI에 알리지 않는 경우 존재

**위치**:
- `FearGreedRepository.kt`
- `MarketDepositRepository.kt`
- `StockAnalysisRepository.kt`

**해결 방안**:
```kotlin
// ViewModel에서 Result 타입 검증
viewModelScope.launch {
    when (val result = repository.loadData()) {
        is Result.Success -> _state.value = State.Success(result.data)
        is Result.Failure -> {
            _state.value = State.Error(result.exception.message)
            // Snackbar 표시
            _snackbarEvent.emit(SnackbarEvent.Error(result.exception))
        }
    }
}
```

**작업 항목**:
- [ ] 모든 Repository Result.failure 호출 검토
- [ ] ViewModel에서 실패 상태 처리 추가
- [ ] 사용자에게 Snackbar로 에러 알림

---

## Phase 2: HIGH PRIORITY FIXES (출시 전 필수)

### 2.1 릴리스 빌드에서 디버그 로깅 제거 [HIGH]

**문제**: 3,212개의 Log 호출이 릴리스 빌드에 포함됨

**해결 방안**:
```kotlin
// core/common/util/AppLogger.kt 수정
object AppLogger {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
    }

    // e, w는 릴리스에서도 유지 (크래시 분석용)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
```

**작업 항목**:
- [ ] AppLogger에 BuildConfig.DEBUG 검사 추가
- [ ] 모든 Log.d/Log.v를 AppLogger.d/v로 교체
- [ ] ProGuard 규칙으로 Log.d/v 제거 추가

```proguard
# proguard-rules.pro
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
```

---

### 2.2 접근성 개선 [HIGH]

**문제**: 6개 아이콘의 contentDescription이 null

**위치**:
- `EtfListScreen.kt`
- `OscillatorScreen.kt`
- 기타 Screen 컴포넌트

**해결 방안**:
```kotlin
// strings.xml에 추가
<string name="cd_back_button">뒤로 가기</string>
<string name="cd_close_button">닫기</string>
<string name="cd_search_button">검색</string>
<string name="cd_refresh_button">새로고침</string>
<string name="cd_settings_button">설정</string>

// 사용
Icon(
    Icons.Default.ArrowBack,
    contentDescription = stringResource(R.string.cd_back_button)
)
```

**작업 항목**:
- [ ] 모든 null contentDescription 검색
- [ ] strings.xml에 접근성 문자열 추가
- [ ] stringResource()로 교체
- [ ] TalkBack으로 테스트

---

### 2.3 SharedPreferences UI 스레드 차단 해결 [HIGH]

**문제**: API 키 저장 시 `.commit()` 사용으로 UI 스레드 차단

**위치**: `SharedPreferencesApiKeyProvider.kt:49`

**해결 방안**:
```kotlin
// ❌ 현재 (차단)
preferences.edit().putString(key, value).commit()

// ✅ 수정 (비동기)
preferences.edit().putString(key, value).apply()

// 또는 CoroutineScope에서 withContext(Dispatchers.IO) 사용
suspend fun saveApiKey(key: String, value: String) = withContext(Dispatchers.IO) {
    preferences.edit().putString(key, value).commit()
}
```

**작업 항목**:
- [ ] `.commit()` 호출을 `.apply()`로 변경
- [ ] 필요시 suspend function으로 래핑

---

### 2.4 Python 패키지 버전 고정 [HIGH]

**문제**: Python 패키지 버전이 고정되지 않아 빌드 재현성 없음

**위치**: `app/build.gradle.kts:77-92`

**해결 방안**:
```kotlin
chaquopy {
    pip {
        install("pandas==2.1.4")
        install("scikit-learn==1.3.2")
        install("pykrx==1.0.47")
        install("beautifulsoup4==4.12.2")
        install("numpy==1.26.3")
        install("requests==2.31.0")
        install("xgboost==2.0.3")
        install("lightgbm==4.2.0")
    }
}
```

**작업 항목**:
- [ ] 현재 사용 중인 패키지 버전 확인
- [ ] 모든 패키지에 버전 명시
- [ ] requirements.txt 파일로 문서화

---

### 2.5 아키텍처 위반 수정 [HIGH]

**문제**: EtfHubScreen이 Stock feature의 presentation 레이어 직접 참조

**위치**: `feature/etf/presentation/hub/EtfHubScreen.kt:42-46`

**해결 방안**:
```
옵션 A: 공유 컴포넌트를 core/ui/component로 이동
옵션 B: 네비게이션을 통한 느슨한 결합
옵션 C: 인터페이스 기반 의존성 역전
```

**작업 항목**:
- [ ] StatisticsViewModel 의존성 분석
- [ ] 공유 컴포넌트를 core/ui/component로 추출
- [ ] EtfHubScreen에서 직접 import 제거
- [ ] Feature 모듈 간 의존성 검증

---

## Phase 3: MEDIUM PRIORITY (출시 후 우선)

### 3.1 네트워크 재시도 로직 추가 [MEDIUM]

**문제**: 일시적 네트워크 오류 시 즉시 실패

**해결 방안**:
```kotlin
suspend fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: IOException) {
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return block() // 마지막 시도
}
```

**작업 항목**:
- [ ] RetryHelper 유틸리티 클래스 생성
- [ ] 모든 네트워크 호출에 재시도 로직 적용
- [ ] 재시도 횟수 및 지연 시간 설정 가능하게

---

### 3.2 Compose 에러 바운더리 추가 [MEDIUM]

**문제**: 개별 필드 오류가 전체 화면 크래시 유발

**해결 방안**:
```kotlin
@Composable
fun ErrorBoundary(
    fallback: @Composable (Throwable) -> Unit = { DefaultErrorFallback(it) },
    content: @Composable () -> Unit
) {
    val errorState = remember { mutableStateOf<Throwable?>(null) }

    if (errorState.value != null) {
        fallback(errorState.value!!)
    } else {
        // Try-catch는 Compose에서 직접 지원하지 않음
        // 대신 상태 기반 에러 처리
        content()
    }
}
```

**작업 항목**:
- [ ] ErrorBoundary 컴포넌트 구현
- [ ] 각 Screen에 에러 바운더리 래핑
- [ ] 에러 상태 UI 디자인

---

### 3.3 인증서 피닝 구현 [MEDIUM]

**문제**: API 엔드포인트에 인증서 피닝 없음

**위치**: `res/xml/network_security_config.xml`

**해결 방안**:
```xml
<network-security-config>
    <domain-config>
        <domain includeSubdomains="true">api.anthropic.com</domain>
        <pin-set expiration="2025-12-31">
            <pin digest="SHA-256">base64EncodedPin=</pin>
            <pin digest="SHA-256">backupPin=</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

**작업 항목**:
- [ ] API 서버 인증서 핀 추출
- [ ] network_security_config.xml에 피닝 추가
- [ ] 백업 핀 설정 (만료 대비)

---

### 3.4 Coroutine Scope 개선 [MEDIUM]

**문제**: CashDepositTab에서 불필요한 rememberCoroutineScope 사용

**위치**: `feature/stock/presentation/statistics/CashDepositTab.kt:125-139`

**해결 방안**:
```kotlin
// ❌ 현재
val scope = rememberCoroutineScope()
LaunchedEffect(trend) {
    scope.launch(Dispatchers.Default) {
        modelProducer.runTransaction { ... }
    }
}

// ✅ 수정
LaunchedEffect(trend) {
    withContext(Dispatchers.Default) {
        modelProducer.runTransaction { ... }
    }
}
```

**작업 항목**:
- [ ] 불필요한 rememberCoroutineScope 제거
- [ ] LaunchedEffect 내부 scope 활용
- [ ] 모든 Chart 컴포넌트 검토

---

## Phase 4: TESTING (품질 보증)

### 4.1 단위 테스트 추가 [HIGH]

**목표**: 최소 50% 코드 커버리지

**테스트 구조**:
```
app/src/test/java/com/etfmonitor/
├── core/
│   ├── database/
│   │   └── MigrationTest.kt
│   └── analysis/
│       └── CorrelationAnalyzerTest.kt
├── feature/
│   ├── home/
│   │   ├── domain/usecase/
│   │   │   └── GetHomeSummaryUseCaseTest.kt
│   │   └── presentation/
│   │       └── HomeViewModelTest.kt
│   ├── etf/
│   │   ├── data/repository/
│   │   │   └── EtfRepositoryImplTest.kt
│   │   └── presentation/
│   │       └── EtfListViewModelTest.kt
│   ├── market/
│   │   └── data/repository/
│   │       ├── FearGreedRepositoryImplTest.kt
│   │       └── MarketDepositRepositoryImplTest.kt
│   └── analysis/
│       └── presentation/
│           └── AIAnalysisViewModelTest.kt
└── TestUtils.kt
```

**작업 항목**:
- [ ] 테스트 의존성 추가 (JUnit5, MockK, Turbine)
- [ ] Repository 테스트 작성 (캐시 만료 로직)
- [ ] ViewModel 테스트 작성 (상태 전환)
- [ ] UseCase 테스트 작성

---

### 4.2 데이터베이스 마이그레이션 테스트 [CRITICAL]

**목표**: 모든 17개 마이그레이션 검증

**해결 방안**:
```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To17() {
        // v1 데이터베이스 생성
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO etfs VALUES ('069500', 'KODEX 200')")
            close()
        }

        // v17로 마이그레이션
        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true,
            *AppDatabase.MIGRATIONS.toTypedArray()
        )

        // 데이터 검증
        val cursor = db.query("SELECT * FROM etfs")
        assertTrue(cursor.moveToFirst())
        assertEquals("069500", cursor.getString(0))
    }
}
```

**작업 항목**:
- [ ] AndroidX Test 의존성 추가
- [ ] MigrationTest 클래스 작성
- [ ] 각 마이그레이션 단계별 테스트
- [ ] exportSchema = true 설정

---

### 4.3 Python 클라이언트 테스트 [MEDIUM]

**목표**: 타임아웃 및 에러 핸들링 검증

**작업 항목**:
- [ ] PyKrxClient 모킹 테스트
- [ ] 타임아웃 시나리오 테스트
- [ ] JSON 파싱 에러 테스트

---

## Phase 5: DOCUMENTATION (문서화)

### 5.1 CLAUDE.md 업데이트

**필요 업데이트**:
- [ ] 스키마 버전 v14 → v17 업데이트
- [ ] 새로운 Entity/DAO 문서화
- [ ] Migration 14→17 내용 추가
- [ ] 테스트 가이드 섹션 추가

---

### 5.2 CHANGELOG.md 생성

```markdown
# Changelog

## [1.0.1] - 2025-12-XX

### Fixed
- 데이터베이스 스키마 불일치 해결
- Null safety 개선 (504개 이슈 해결)
- 릴리스 빌드 디버그 로깅 제거

### Added
- 접근성 지원 개선
- 네트워크 재시도 로직
- 단위 테스트 (50% 커버리지)

### Changed
- Python 패키지 버전 고정
- SharedPreferences 비동기 저장

## [1.0.0] - 2025-12-XX
- 초기 릴리스
```

---

## 실행 우선순위

### Week 1: Critical Fixes
1. ✅ 데이터베이스 Entity 누락 해결
2. ✅ 버전 코드 업데이트
3. ✅ Null safety 개선 시작
4. ✅ Silent failure 해결

### Week 2: High Priority
1. ✅ 디버그 로깅 제거
2. ✅ 접근성 개선
3. ✅ SharedPreferences 수정
4. ✅ Python 버전 고정
5. ✅ 아키텍처 위반 수정

### Week 3: Testing
1. ✅ 마이그레이션 테스트 작성
2. ✅ Repository 테스트 작성
3. ✅ ViewModel 테스트 작성

### Week 4: Polish
1. ✅ Medium priority 이슈 해결
2. ✅ 문서 업데이트
3. ✅ 최종 검토 및 릴리스

---

## 완료 기준

각 Phase 완료 시 체크:

### Phase 1 완료 조건
- [ ] `./gradlew build` 성공
- [ ] 모든 Entity가 AppDatabase에 등록됨
- [ ] versionCode > 1
- [ ] `!!` 연산자 0개
- [ ] 모든 Repository 실패가 UI에 표시됨

### Phase 2 완료 조건
- [ ] 릴리스 APK에서 Log.d/v 없음
- [ ] 모든 Icon에 contentDescription 있음
- [ ] Python 패키지 버전 모두 고정
- [ ] Feature 모듈 간 직접 참조 없음

### Phase 3 완료 조건
- [ ] 네트워크 재시도 로직 구현됨
- [ ] 인증서 피닝 설정됨
- [ ] 불필요한 coroutine scope 제거됨

### Phase 4 완료 조건
- [ ] 테스트 커버리지 50% 이상
- [ ] 모든 마이그레이션 테스트 통과
- [ ] CI에서 테스트 자동 실행

### 최종 완료 조건
- [ ] 모든 lint 경고 해결
- [ ] ProGuard 빌드 성공
- [ ] Play Store 업로드 가능
- [ ] 품질 점수 9.5/10 이상

---

**작성일**: 2025-12-27
**작성자**: Claude (AI Assistant)
**검토 필요**: gmdjlee
