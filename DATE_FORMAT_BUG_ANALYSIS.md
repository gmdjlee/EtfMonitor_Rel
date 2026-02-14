# Date Format Bug 분석 및 수정 방안

**버그 발견 일시**: 2026-02-14 18:38:15
**영향 범위**: KrxStockDataRepositoryImpl (주식 분석 기능)
**심각도**: 🔴 **CRITICAL** (기능 완전 중단)

---

## 1. 에러 로그 분석

### 1.1 원본 에러 메시지

```
2026-02-14 18:38:15.855  onitor.KrxStockDataRepo  E  getOhlcvByTicker failed: Invalid date: 2024-02-15
2026-02-14 18:38:15.855  r.StockAnalysisRepoImpl  E  Failed to fetch data from kotlin_krx for 005930
```

### 1.2 호출 컨텍스트

```
날짜: 2026-02-14 (오늘)
종목: 005930 (삼성전자)
요청 기간: 730일 (2년)
계산된 시작일: 2026-02-14 - 730일 = 2024-02-15
전달된 날짜 형식: "2024-02-15" (ISO 형식)
```

### 1.3 에러 발생 지점

**파일**: `KrxStockDataRepositoryImpl.kt`

```kotlin
// Line 158-163
val end = LocalDate.now()
val start = end.minusDays(days.toLong())

val ohlcvResult = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(start.toString(), end.toString(), ticker)
    //                        ^^^^^^^^^^^^^^  ^^^^^^^^^^^^
    //                        "2024-02-15"    "2026-02-14"  ← 잘못된 형식!
}
```

---

## 2. 근본 원인 (Root Cause)

### 2.1 날짜 형식 불일치

| 컴포넌트 | 기대 형식 | 실제 전달 형식 | 결과 |
|---------|---------|-------------|------|
| **kotlin_krx** | `"yyyyMMdd"` (예: `"20240215"`) | `"yyyy-MM-dd"` (예: `"2024-02-15"`) | ❌ 검증 실패 |
| **KrxStockDataRepositoryImpl** | - | `LocalDate.toString()` 사용 | ❌ ISO 형식 생성 |

### 2.2 LocalDate.toString() 동작

```kotlin
val date = LocalDate.of(2024, 2, 15)
println(date.toString())  // 출력: "2024-02-15" (ISO-8601 형식)
```

**문제점**: `LocalDate.toString()`은 ISO-8601 형식 (`yyyy-MM-dd`)을 반환하지만, kotlin_krx는 `yyyyMMdd` 형식을 요구합니다.

### 2.3 kotlin_krx 날짜 검증 로직

**kotlin_krx의 DateUtils.kt**:
```kotlin
fun validateDate(date: String) {
    require(date.matches(Regex("\\d{8}"))) { "Invalid date: $date" }
    // 8자리 숫자만 허용: "20240215" ✅
    // 대시 포함 형식 거부: "2024-02-15" ❌
}
```

---

## 3. 영향 범위

### 3.1 영향 받는 파일

| 파일 | 영향 받는 메서드 | 라인 | 상태 |
|------|---------------|------|------|
| **KrxStockDataRepositoryImpl.kt** | `getStockOhlcv()` | 80-81 | ❌ 오류 |
| **KrxStockDataRepositoryImpl.kt** | `getStockAnalysisData()` | 163, 182 | ❌ 오류 |
| **KrxStockDataRepositoryImpl.kt** | `getAllStocksList()` | 244 | ❌ 오류 |
| **KrxStockDataRepositoryImpl.kt** | `getStockName()` | 278 | ❌ 오류 |
| **KrxStockDataRepositoryImpl.kt** | `getTrendSignalData()` | 301 | ❌ 오류 |
| **KrxStockDataRepositoryImpl.kt** | `getElderImpulseData()` | 341 | ❌ 오류 |
| **KrxStockDataRepositoryImpl.kt** | `getDemarkTDData()` | 381 | ❌ 오류 |

**총 7개 메서드**, 모두 `LocalDate.toString()` 사용으로 인한 오류

### 3.2 영향 받는 기능

| 기능 | ViewModel | 사용자 영향 |
|------|-----------|-----------|
| 주식 분석 | OscillatorViewModel | ❌ 종목 분석 불가 |
| 종목 검색 | OscillatorViewModel | ❌ 검색 실패 |
| 추세 신호 | OscillatorViewModel | ❌ 데이터 로딩 실패 |
| Elder Impulse | OscillatorViewModel | ❌ 데이터 로딩 실패 |
| DeMark TD | OscillatorViewModel | ❌ 데이터 로딩 실패 |

**결과**: **주식 분석 기능 완전 중단** 🔴

---

## 4. 올바른 구현 패턴

### 4.1 DateAdapter 유틸리티

**파일**: `core/data/krx/adapter/DateAdapter.kt`

```kotlin
object DateAdapter {
    private val KRX_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun today(): String = LocalDate.now().format(KRX_FORMAT)

    fun format(date: LocalDate): String = date.format(KRX_FORMAT)

    fun daysAgo(days: Int): String =
        LocalDate.now().minusDays(days.toLong()).format(KRX_FORMAT)
}
```

**사용 예시**:
```kotlin
// ✅ 올바른 방법
val today = DateAdapter.today()           // "20260214"
val start = DateAdapter.daysAgo(730)      // "20240215"

// ❌ 잘못된 방법 (현재 코드)
val today = LocalDate.now().toString()    // "2026-02-14"
val start = LocalDate.now().minusDays(730).toString()  // "2024-02-15"
```

### 4.2 기존 정상 동작 코드 (참고)

**KrxEtfRepositoryImpl.kt** (올바른 구현):

```kotlin
// Line 14
suspend fun getEtfList(date: String = DateAdapter.today()): Result<List<String>>

// Line 20
suspend fun getEtfHoldings(ticker: String, date: String = DateAdapter.today())

// Line 28
suspend fun getEtfName(ticker: String, date: String = DateAdapter.today())
```

**MarketIndexRepositoryImpl.kt** (올바른 구현):

```kotlin
// Line 102
val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
val endStr = endDate.format(formatter)
val startStr = startDate.format(formatter)
```

---

## 5. 수정 방안

### 5.1 수정 대상

**파일**: `app/src/main/java/com/etfmonitor/core/data/repository/krx/KrxStockDataRepositoryImpl.kt`

**변경 필요 라인**:
- Line 3: Import 추가 (`DateAdapter`)
- Line 80-81: `getStockOhlcv()` 날짜 형식 변경
- Line 163, 182: `getStockAnalysisData()` 날짜 형식 변경
- Line 244: `getAllStocksList()` 날짜 형식 변경
- Line 278: `getStockName()` 날짜 형식 변경
- Line 301: `getTrendSignalData()` 날짜 형식 변경
- Line 341: `getElderImpulseData()` 날짜 형식 변경
- Line 381: `getDemarkTDData()` 날짜 형식 변경

### 5.2 수정 코드 (Before/After)

#### Import 추가

```kotlin
// BEFORE
package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.core.analysis.TechnicalAnalysisEngine
// ... 기타 imports

// AFTER
package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.core.analysis.TechnicalAnalysisEngine
import com.etfmonitor.core.data.krx.adapter.DateAdapter  // ← 추가
// ... 기타 imports
```

#### getStockOhlcv() 메서드 (Line 74-82)

```kotlin
// BEFORE
val end = LocalDate.now()
val start = end.minusDays(fetchDays.toLong())

val result = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(
        startDate = start.toString(),  // ❌ "2024-02-15"
        endDate = end.toString(),      // ❌ "2026-02-14"
        ticker = ticker
    )
}

// AFTER
val end = LocalDate.now()
val start = end.minusDays(fetchDays.toLong())

val result = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(
        startDate = DateAdapter.format(start),  // ✅ "20240215"
        endDate = DateAdapter.format(end),      // ✅ "20260214"
        ticker = ticker
    )
}
```

#### getStockAnalysisData() 메서드 (Line 158-183)

```kotlin
// BEFORE
val end = LocalDate.now()
val start = end.minusDays(days.toLong())

val ohlcvResult = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(start.toString(), end.toString(), ticker)  // ❌
}

// ... (중략)

val capResult = krxCall(TIMEOUT_30S) {
    krxStock.getMarketCap(end.toString(), Market.ALL)  // ❌
}

// AFTER
val end = LocalDate.now()
val start = end.minusDays(days.toLong())

val ohlcvResult = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(
        DateAdapter.format(start),  // ✅
        DateAdapter.format(end),    // ✅
        ticker
    )
}

// ... (중략)

val capResult = krxCall(TIMEOUT_30S) {
    krxStock.getMarketCap(DateAdapter.format(end), Market.ALL)  // ✅
}
```

#### getAllStocksList() 메서드 (Line 243-245)

```kotlin
// BEFORE
val date = LocalDate.now().minusDays(1)
val result = krxCall(TIMEOUT_30S) {
    krxStock.getTickerList(date.toString(), Market.ALL)  // ❌
}

// AFTER
val date = LocalDate.now().minusDays(1)
val result = krxCall(TIMEOUT_30S) {
    krxStock.getTickerList(DateAdapter.format(date), Market.ALL)  // ✅
}
```

#### getStockName() 메서드 (Line 277-279)

```kotlin
// BEFORE
val date = LocalDate.now().minusDays(1)
val result = krxCall(TIMEOUT_30S) {
    krxStock.getTickerName(ticker, date.toString())  // ❌
}

// AFTER
val date = LocalDate.now().minusDays(1)
val result = krxCall(TIMEOUT_30S) {
    krxStock.getTickerName(ticker, DateAdapter.format(date))  // ✅
}
```

#### getTrendSignalData() 메서드 (Line 300-302)

```kotlin
// BEFORE
val end = LocalDate.now()
val start = end.minusDays(fetchDays.toLong())
val result = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(start.toString(), end.toString(), ticker)  // ❌
}

// AFTER
val end = LocalDate.now()
val start = end.minusDays(fetchDays.toLong())
val result = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(
        DateAdapter.format(start),  // ✅
        DateAdapter.format(end),    // ✅
        ticker
    )
}
```

#### getElderImpulseData() 메서드 (Line 340-342)

```kotlin
// BEFORE
val end = LocalDate.now()
val start = end.minusDays(fetchDays.toLong())
val result = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(start.toString(), end.toString(), ticker)  // ❌
}

// AFTER
val end = LocalDate.now()
val start = end.minusDays(fetchDays.toLong())
val result = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(
        DateAdapter.format(start),  // ✅
        DateAdapter.format(end),    // ✅
        ticker
    )
}
```

#### getDemarkTDData() 메서드 (Line 380-382)

```kotlin
// BEFORE
val end = LocalDate.now()
val start = end.minusDays(fetchDays.toLong())
val result = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(start.toString(), end.toString(), ticker)  // ❌
}

// AFTER
val end = LocalDate.now()
val start = end.minusDays(fetchDays.toLong())
val result = krxCall(TIMEOUT_30S) {
    krxStock.getOhlcvByTicker(
        DateAdapter.format(start),  // ✅
        DateAdapter.format(end),    // ✅
        ticker
    )
}
```

---

## 6. 수정 후 검증 계획

### 6.1 단위 테스트

```kotlin
@Test
fun `DateAdapter formats dates correctly for kotlin_krx`() {
    val date = LocalDate.of(2024, 2, 15)
    val formatted = DateAdapter.format(date)

    assertEquals("20240215", formatted)
    assertTrue(formatted.matches(Regex("\\d{8}")))
}

@Test
fun `getStockOhlcv uses correct date format`() = runBlocking {
    val repo = KrxStockDataRepositoryImpl(mockKrxStock)

    // Mock should receive "yyyyMMdd" format
    coEvery {
        mockKrxStock.getOhlcvByTicker(
            match { it.matches(Regex("\\d{8}")) },  // "20240215" 형식 확인
            match { it.matches(Regex("\\d{8}")) },  // "20260214" 형식 확인
            any()
        )
    } returns listOf(...)

    val result = repo.getStockOhlcv("005930", 730, "d")
    assertNotNull(result)
}
```

### 6.2 통합 테스트

```bash
# Android 기기에서 실제 KRX API 호출 테스트
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.etfmonitor.krx.KrxApiFunctionalityTest#test_kotlin_krx_stock
```

### 6.3 수동 테스트

1. **앱 실행** → Stock 화면으로 이동
2. **종목 검색** → "005930" (삼성전자) 입력
3. **분석 실행** → "종목 분석" 버튼 클릭
4. **결과 확인**:
   - ✅ 에러 없이 데이터 로드
   - ✅ 추세 신호 표시
   - ✅ Elder Impulse 그래프 표시
   - ✅ DeMark TD 신호 표시

### 6.4 Logcat 검증

**Before (에러)**:
```
E  getOhlcvByTicker failed: Invalid date: 2024-02-15
E  Failed to fetch data from kotlin_krx for 005930
```

**After (정상)**:
```
D  getStockAnalysisData: 005930, 730 days
D  Stock analysis data complete: 삼성전자, 480 records
```

---

## 7. 회귀 방지 전략

### 7.1 정적 분석 추가

**Detekt 규칙** (`.detekt.yml`):
```yaml
custom-rules:
  KrxDateFormatRule:
    active: true
    description: "Ensure all kotlin_krx date parameters use DateAdapter.format()"
    excludes: []
    includes:
      - '**/krx/**/*.kt'
```

### 7.2 코드 리뷰 체크리스트

- [ ] kotlin_krx API 호출 시 날짜 형식 확인
- [ ] `LocalDate.toString()` 직접 사용 금지
- [ ] `DateAdapter.format()` 또는 `DateAdapter.today()` 사용
- [ ] 날짜 형식: `"yyyyMMdd"` (8자리 숫자)

### 7.3 문서 업데이트

**CLAUDE.md에 추가**:

```markdown
### 11. kotlin_krx Date Format — Always Use DateAdapter

kotlin_krx requires `"yyyyMMdd"` format (8-digit numbers). **NEVER** use `LocalDate.toString()`.

```kotlin
// ✅ ALWAYS use DateAdapter
val date = DateAdapter.today()           // "20260214"
val start = DateAdapter.daysAgo(30)      // "20260115"
val formatted = DateAdapter.format(localDate)

// ❌ NEVER use toString()
val date = LocalDate.now().toString()    // "2026-02-14" ← kotlin_krx rejects this
```

All kotlin_krx date parameters must use `DateAdapter` to prevent "Invalid date" errors.
```

---

## 8. 예상 수정 시간

| 작업 | 예상 시간 |
|------|----------|
| 코드 수정 (7개 메서드) | 15분 |
| 단위 테스트 작성 | 20분 |
| 통합 테스트 실행 | 5분 |
| 수동 테스트 | 10분 |
| **총계** | **50분** |

**난이도**: ⭐ **매우 쉬움** (단순 형식 변환)
**우선순위**: 🔴 **긴급** (기능 완전 중단)

---

## 9. 근본 원인 분석 (5 Whys)

1. **Why?** 주식 분석 기능이 작동하지 않는다
   → kotlin_krx가 날짜 형식 검증에 실패

2. **Why?** kotlin_krx가 날짜 형식 검증에 실패하는가?
   → "2024-02-15" 형식을 받았지만 "20240215" 형식을 기대

3. **Why?** "2024-02-15" 형식이 전달되었는가?
   → `LocalDate.toString()` 사용

4. **Why?** `LocalDate.toString()`을 사용했는가?
   → DateAdapter 유틸리티 존재를 인지하지 못함

5. **Why?** DateAdapter 존재를 인지하지 못했는가?
   → T-013 마이그레이션 시 KrxEtfRepositoryImpl 패턴 참조 누락

**근본 원인**: Phase 3 마이그레이션 시 기존 정상 코드 (KrxEtfRepositoryImpl) 패턴 미참조

---

## 10. 교훈 (Lessons Learned)

### 10.1 마이그레이션 체크리스트 개선

**Before**:
- ✅ UseCase 생성
- ✅ Repository 구현
- ✅ DI 연결

**After (추가 항목)**:
- ✅ 기존 정상 동작 코드 패턴 참조
- ✅ 날짜 형식 검증 (kotlin_krx는 `yyyyMMdd`)
- ✅ 유틸리티 클래스 재사용 (DateAdapter)
- ✅ 통합 테스트 실행 (connectedAndroidTest)

### 10.2 코드 리뷰 강화

**Phase 3 리뷰 시 놓친 점**:
- ❌ 날짜 형식 변환 누락 확인 안 함
- ❌ 기존 KrxEtfRepositoryImpl 패턴 비교 안 함
- ❌ 실제 API 호출 테스트 미실행

**개선 방안**:
- ✅ 마이그레이션 시 기존 정상 코드와 diff 비교
- ✅ 필수 통합 테스트 실행 (connectedAndroidTest)
- ✅ Logcat 확인 (실제 동작 검증)

---

**분석 완료 일시**: 2026-02-14
**분석자**: Claude Sonnet 4.5
**다음 단계**: KrxStockDataRepositoryImpl.kt 수정 및 테스트 실행
