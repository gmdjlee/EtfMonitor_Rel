# KrxKt — Kotlin Native KRX Data Library

## Android 프로젝트 통합 가이드 (Claude Code 최적화)

Version 1.1 | February 2026

---

## 1. 라이브러리 개요

**KrxKt**는 한국거래소(KRX) 시장 데이터에 직접 접근하는 순수 Kotlin 라이브러리입니다.

| Feature | Detail |
|---------|--------|
| **순수 Kotlin** | 외부 런타임 없이 Kotlin/JVM 환경에서 동작 |
| **Coroutine 기반** | 모든 API가 `suspend fun`으로 제공되어 Android Lifecycle과 자연스럽게 통합 |
| **APK 영향** | < 1MB (OkHttp + Gson 의존성 포함) |
| **Production-ready** | Exponential backoff 재시도, Thread-safe 캐시, sealed class 에러 핸들링 |

### 제공 데이터 범위

| 영역 | 진입점 클래스 | 제공 데이터 |
|------|-------------|------------|
| 주식 | `KrxStock` | OHLCV, 시가총액, 투자지표(PER/PBR/EPS), 투자자별 거래실적, 공매도 |
| ETF | `KrxEtf` | ETF 시세, OHLCV 히스토리, 종목 리스트, 구성종목(PDF) |
| 지수 | `KrxIndex` | 지수 OHLCV, 지수 리스트, KOSPI/KOSDAQ/KOSPI200 단축 메서드 |

---

## 2. Setup

### 2.1 Gradle Dependencies

```kotlin
// build.gradle.kts (app module)
dependencies {
    // KrxKt Core
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Testing
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
```

### 2.2 Android Manifest

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 2.3 ProGuard / R8

```proguard
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep class com.krxkt.model.** { *; }
-keepattributes Signature
```

### 2.4 네트워크 요구사항

> ⚠️ **KRX API는 한국 네트워크에서만 접근 가능합니다.**
> 해외 네트워크에서는 `"LOGOUT"` 응답이 반환되며, `KrxError.NetworkError`로 처리됩니다.
> 한국 VPN을 사용하면 해외에서도 접근 가능합니다.

---

## 3. 핵심 사용 규칙

KrxKt를 사용하는 모든 코드에서 반드시 지켜야 할 규칙입니다. Claude Code에서 코드를 생성할 때도 이 규칙이 적용됩니다.

| 규칙 | 설명 | 예시 |
|------|------|------|
| **날짜 형식** | 항상 `"yyyyMMdd"` 문자열 | `"20210122"` |
| **가격/금액 타입** | `Long` (원 단위 정수) | `close: Long = 82200` |
| **비율/지수 타입** | `Double` | `changeRate: Double = -0.50` |
| **빈 응답** | `emptyList()` 반환 (null 아님) | 공휴일, 휴장일, 잘못된 종목코드 |
| **비동기 호출** | 반드시 Coroutine scope 내에서 호출 | `viewModelScope.launch { }` |
| **리소스 정리** | 사용 종료 시 `.close()` 호출 | `onCleared()`에서 호출 |
| **종목코드** | 6자리 문자열 | `"005930"` (삼성전자) |
| **ISIN 변환** | 내부 자동 처리 (TickerCache) | 외부에서 ISIN을 다룰 필요 없음 |

---

## 4. 패키지 구조

```
com.krxkt/
├── KrxStock.kt              # 주식 API 진입점
├── KrxEtf.kt                # ETF API 진입점
├── KrxIndex.kt              # 지수 API 진입점
│
├── api/
│   ├── KrxClient.kt         # HTTP 클라이언트 (재시도, 헤더, 타임아웃)
│   └── KrxEndpoints.kt      # 엔드포인트 상수
│
├── model/                   # Data Classes (immutable, all fields documented)
│   ├── Market.kt             # enum: KOSPI, KOSDAQ, KONEX, ALL
│   ├── MarketOhlcv.kt
│   ├── StockOhlcvHistory.kt
│   ├── MarketCap.kt
│   ├── StockFundamental.kt
│   ├── TickerInfo.kt
│   ├── InvestorTrading.kt    # + TradingValueType, AskBidType enums
│   ├── ShortSelling.kt       # + ShortSellingHistory, ShortBalance, ShortBalanceHistory
│   ├── EtfPrice.kt
│   ├── EtfOhlcvHistory.kt
│   ├── EtfInfo.kt
│   ├── EtfPortfolio.kt
│   ├── IndexOhlcv.kt
│   └── IndexInfo.kt          # + IndexMarket enum
│
├── parser/KrxJsonParser.kt  # JSON 파싱 + 쉼표 숫자 변환
├── cache/TickerCache.kt      # ISIN 인메모리 캐시 (Thread-safe, TTL 1시간)
├── error/KrxError.kt         # sealed class 에러 정의
└── util/DateUtils.kt         # 날짜 검증
```

---

## 5. API Reference — 입출력 명세

### 5.1 초기화 & 리소스 관리

```kotlin
// 기본 생성
val krxStock = KrxStock()
val krxEtf = KrxEtf()
val krxIndex = KrxIndex()

// 커스텀 OkHttpClient 주입 (DI 환경)
val client = KrxClient(okHttpClient = yourOkHttpClient)
val cache = TickerCache()  // KrxStock과 KrxEtf 간 공유 가능
val krxStock = KrxStock(client = client, tickerCache = cache)
val krxEtf = KrxEtf(client = client, tickerCache = cache)

// 리소스 정리 — ViewModel.onCleared() 등에서 호출
krxStock.close()
krxEtf.close()
krxIndex.close()
```

---

### 5.2 KrxStock

#### `getMarketOhlcv(date, market?)` — 전종목 OHLCV

**Input:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `date` | `String` | ✅ | — | `"yyyyMMdd"` |
| `market` | `Market` | ❌ | `ALL` | `KOSPI` / `KOSDAQ` / `KONEX` / `ALL` |

**Output:** `List<MarketOhlcv>`

```kotlin
data class MarketOhlcv(
    val ticker: String,       // "005930"
    val name: String,         // "삼성전자"
    val open: Long,           // 시가 (원)
    val high: Long,           // 고가
    val low: Long,            // 저가
    val close: Long,          // 종가
    val volume: Long,         // 거래량 (주)
    val tradingValue: Long,   // 거래대금 (원)
    val changeRate: Double    // 등락률 (%)
)
```

**사용 예시:**

```kotlin
val list = krxStock.getMarketOhlcv("20210122", Market.KOSPI)

val samsung = list.find { it.ticker == "005930" }
// samsung?.close → 82200L

val top10ByVolume = list.sortedByDescending { it.tradingValue }.take(10)
```

**Edge Cases:** 공휴일 → `emptyList()` / 잘못된 날짜 → `KrxError.InvalidDateError`

---

#### `getOhlcvByTicker(startDate, endDate, ticker)` — 개별종목 기간 조회

**Input:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `startDate` | `String` | ✅ | 시작일 `"yyyyMMdd"` |
| `endDate` | `String` | ✅ | 종료일 `"yyyyMMdd"` |
| `ticker` | `String` | ✅ | 종목코드 6자리 |

**Output:** `List<StockOhlcvHistory>`

```kotlin
data class StockOhlcvHistory(
    val date: String,         // "20210122" (정규화됨)
    val open: Long,
    val high: Long,
    val low: Long,
    val close: Long,
    val volume: Long,
    val tradingValue: Long,
    val changeRate: Double
)
```

> 💡 첫 호출 시 ISIN 조회를 위해 내부적으로 `getTickerList()`가 실행됩니다. 이후 호출은 캐시를 통해 즉시 처리됩니다.

---

#### `getMarketCap(date, market?)` — 전종목 시가총액

**Output:** `List<MarketCap>`

```kotlin
data class MarketCap(
    val ticker: String,
    val name: String,
    val close: Long,
    val changeRate: Double,
    val marketCap: Long,            // 시가총액 (원)
    val sharesOutstanding: Long     // 상장주식수
)
```

---

#### `getMarketFundamental(date, market?)` — 전종목 투자지표

**Output:** `List<StockFundamental>`

```kotlin
data class StockFundamental(
    val ticker: String,
    val name: String,
    val close: Long,
    val eps: Long,              // 주당순이익
    val per: Double,            // 주가수익비율
    val bps: Long,              // 주당순자산
    val pbr: Double,            // 주가순자산비율
    val dps: Long,              // 주당배당금
    val dividendYield: Double   // 배당수익률 (%)
)
```

---

#### `getTickerList(date, market?)` — 종목 리스트

**Output:** `List<TickerInfo>`

```kotlin
data class TickerInfo(
    val ticker: String,       // "005930"
    val name: String,         // "삼성전자"
    val marketName: String,   // "KOSPI"
    val isinCode: String      // "KR7005930003"
)
```

---

#### `getMarketTradingByInvestor(...)` — 전체시장 투자자별 거래실적

**Input:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `startDate` | `String` | ✅ | — | `"yyyyMMdd"` |
| `endDate` | `String` | ✅ | — | `"yyyyMMdd"` |
| `market` | `Market` | ❌ | `ALL` | 시장 구분 |
| `valueType` | `TradingValueType` | ❌ | `VALUE` | `VOLUME`(거래량) / `VALUE`(거래대금) |
| `askBidType` | `AskBidType` | ❌ | `NET_BUY` | `SELL` / `BUY` / `NET_BUY` |

#### `getTradingByInvestor(...)` — 개별종목 투자자별 거래실적

위와 동일 + `ticker: String` 파라미터 추가.

**Output (공통):** `List<InvestorTrading>`

```kotlin
data class InvestorTrading(
    val date: String,
    val financialInvestment: Long,  // 금융투자
    val insurance: Long,            // 보험
    val investmentTrust: Long,      // 투신
    val privateEquity: Long,        // 사모
    val bank: Long,                 // 은행
    val otherFinance: Long,         // 기타금융
    val pensionFund: Long,          // 연기금
    val institutionalTotal: Long,   // 기관합계
    val otherCorporation: Long,     // 기타법인
    val individual: Long,           // 개인
    val foreigner: Long,            // 외국인
    val total: Long                 // 전체
)
// Computed: institutionalNetBuy, foreignerNetBuy, individualNetBuy
```

---

#### 공매도 API

| 메서드 | Input | Output |
|--------|-------|--------|
| `getShortSellingAll(date, market?)` | 특정일 | `List<ShortSelling>` |
| `getShortSellingByTicker(start, end, ticker)` | 기간 | `List<ShortSellingHistory>` |
| `getShortBalanceAll(date, market?)` | 특정일 | `List<ShortBalance>` |
| `getShortBalanceByTicker(start, end, ticker)` | 기간 | `List<ShortBalanceHistory>` |

```kotlin
data class ShortSelling(
    val ticker: String, val name: String,
    val shortVolume: Long, val shortValue: Long,
    val totalVolume: Long, val totalValue: Long,
    val volumeRatio: Double?
)
// Computed: calculatedVolumeRatio, calculatedValueRatio

data class ShortSellingHistory(
    val date: String,
    val shortVolume: Long, val shortValue: Long,
    val totalVolume: Long, val totalValue: Long
)
// Computed: volumeRatio, valueRatio

data class ShortBalance(
    val ticker: String, val name: String,
    val balanceQuantity: Long, val balanceAmount: Long,
    val listedShares: Long, val balanceRatio: Double?
)
// Computed: calculatedBalanceRatio

data class ShortBalanceHistory(
    val date: String,
    val balanceQuantity: Long, val balanceAmount: Long,
    val listedShares: Long, val balanceRatio: Double?
)
// Computed: calculatedBalanceRatio
```

---

### 5.3 KrxEtf

#### `getEtfPrice(date)` — 전종목 ETF 시세

**Output:** `List<EtfPrice>`

```kotlin
data class EtfPrice(
    val ticker: String,           // "069500"
    val name: String,             // "KODEX 200"
    val nav: Double?,             // 순자산가치
    val open: Long, val high: Long, val low: Long, val close: Long,
    val volume: Long, val tradingValue: Long,
    val underlyingIndex: Double?, // 기초지수
    val changeRate: Double?
)
```

---

#### `getOhlcvByTicker(startDate, endDate, ticker)` — ETF 기간 조회

**Output:** `List<EtfOhlcvHistory>`

```kotlin
data class EtfOhlcvHistory(
    val date: String,
    val nav: Double?,
    val open: Long, val high: Long, val low: Long, val close: Long,
    val volume: Long, val tradingValue: Long,
    val underlyingIndex: Double?
)
```

---

#### `getEtfTickerList(date)` — ETF 종목 리스트

**Output:** `List<EtfInfo>`

```kotlin
data class EtfInfo(
    val ticker: String,
    val name: String,
    val isinCode: String,
    val indexName: String?,       // 기초지수명
    val targetIndexName: String?, // 추적지수명
    val indexProvider: String?,   // 지수산출기관
    val cu: Long?,                // 설정단위 (Creation Unit)
    val totalFee: Double?         // 총보수율 (%)
)
```

---

#### `getPortfolio(date, ticker)` — ETF 구성종목

**Output:** `List<EtfPortfolio>`

```kotlin
data class EtfPortfolio(
    val ticker: String,          // 구성종목 코드 (6자리 정규화)
    val name: String,
    val shares: Long,            // 주식수
    val valuationAmount: Long,   // 평가금액
    val amount: Long,            // 구성금액
    val weight: Double?          // 비중 (%)
)
```

---

#### `getEtfName(ticker, date)` — ETF 이름 조회

**Output:** `String?`

---

### 5.4 KrxIndex

#### 주요 지수 티커 상수

```kotlin
KrxIndex.TICKER_KOSPI       // "1001"
KrxIndex.TICKER_KOSPI_200   // "1028"
KrxIndex.TICKER_KOSPI_LARGE // "1002"
KrxIndex.TICKER_KOSPI_MID   // "1003"
KrxIndex.TICKER_KOSPI_SMALL // "1004"
KrxIndex.TICKER_KOSDAQ      // "2001"
KrxIndex.TICKER_KOSDAQ_150  // "2203"
```

#### `getOhlcvByTicker(startDate, endDate, ticker)` — 지수 OHLCV 기간 조회

**Output:** `List<IndexOhlcv>`

```kotlin
data class IndexOhlcv(
    val date: String,
    val open: Double,          // 지수는 Double (소수점 포함)
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val tradingValue: Long,    // 거래대금 (백만원 단위)
    val changeType: Int?,      // 1: 상승, 2: 하락, 3: 보합
    val change: Double?        // 전일대비 포인트
)
// Computed: isUp, isDown, isUnchanged
```

#### 단축 메서드

```kotlin
krxIndex.getKospi(startDate, endDate)      // KOSPI
krxIndex.getKospi200(startDate, endDate)    // KOSPI 200
krxIndex.getKosdaq(startDate, endDate)      // KOSDAQ
krxIndex.getKosdaq150(startDate, endDate)   // KOSDAQ 150
```

#### `getIndexList(date, market?)` — 지수 리스트

**Output:** `List<IndexInfo>`

```kotlin
data class IndexInfo(
    val ticker: String,     // "1028"
    val code: String,       // "028"
    val name: String,       // "코스피 200"
    val typeCode: String,   // "1"
    val baseDate: String?
)
// Computed: isKospi, isKosdaq, isDerivatives, isTheme

enum class IndexMarket { ALL, KOSPI, KOSDAQ, DERIVATIVES, THEME }
```

---

## 6. 에러 핸들링

### 6.1 에러 타입

```kotlin
sealed class KrxError(message: String, cause: Throwable?) : Exception {
    class NetworkError(...)       // 네트워크 장애, 타임아웃
    class ParseError(...)         // JSON 파싱 실패
    class InvalidDateError(...)   // 날짜 형식 오류
}
// KrxError.isRetriable() → NetworkError만 true
```

### 6.2 내부 재시도 전략

`KrxClient`가 `NetworkError`에 대해 자동으로 재시도합니다:

| 시도 | 대기 | 비고 |
|------|------|------|
| 1차 | 즉시 | — |
| 2차 | 1초 | IOException 발생 시 |
| 3차 | 2초 | 2차도 실패 시 |
| 실패 | — | `KrxError.NetworkError` throw |

Coroutine 취소(`CancellationException`)는 재시도 없이 즉시 전파됩니다.

### 6.3 권장 에러 처리 패턴

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    try {
        val data = krxStock.getMarketOhlcv("20210122")
        _uiState.value = UiState.Success(data)
    } catch (e: KrxError.InvalidDateError) {
        _uiState.value = UiState.Error("잘못된 날짜: ${e.date}")
    } catch (e: KrxError.NetworkError) {
        _uiState.value = UiState.Error("네트워크 오류. 연결 상태를 확인하세요.")
    } catch (e: KrxError.ParseError) {
        _uiState.value = UiState.Error("데이터 처리 오류")
    }
}
```

---

## 7. Android 통합 패턴

### 7.1 Hilt DI 설정

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object KrxModule {

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides @Singleton
    fun provideKrxClient(okHttp: OkHttpClient): KrxClient = KrxClient(okHttp)

    @Provides @Singleton
    fun provideTickerCache(): TickerCache = TickerCache()

    @Provides @Singleton
    fun provideKrxStock(client: KrxClient, cache: TickerCache) =
        KrxStock(client, cache)

    @Provides @Singleton
    fun provideKrxEtf(client: KrxClient, cache: TickerCache) =
        KrxEtf(client, cache)

    @Provides @Singleton
    fun provideKrxIndex(client: KrxClient) = KrxIndex(client)
}
```

> 💡 `TickerCache`를 `KrxStock`과 `KrxEtf`에 공유 주입하면 ISIN 조회 API 호출을 최소화할 수 있습니다.

### 7.2 Repository 패턴 — 병렬 조회

```kotlin
class StockRepository @Inject constructor(
    private val krxStock: KrxStock,
    private val krxEtf: KrxEtf
) {
    suspend fun getMarketSnapshot(date: String): MarketSnapshot =
        coroutineScope {
            val ohlcv = async { krxStock.getMarketOhlcv(date, Market.KOSPI) }
            val caps = async { krxStock.getMarketCap(date, Market.KOSPI) }
            val fundamentals = async { krxStock.getMarketFundamental(date, Market.KOSPI) }

            MarketSnapshot(
                ohlcv = ohlcv.await(),
                marketCaps = caps.await(),
                fundamentals = fundamentals.await()
            )
        }
}
```

### 7.3 ViewModel 패턴

```kotlin
@HiltViewModel
class StockViewModel @Inject constructor(
    private val repository: StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadMarketData(date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                val snapshot = repository.getMarketSnapshot(date)
                _uiState.value = UiState.Success(snapshot)
            } catch (e: KrxError) {
                _uiState.value = UiState.Error(e.message ?: "오류 발생")
            }
        }
    }
}
```

### 7.4 Room 캐싱 패턴

API 결과를 Room DB에 캐싱하면 오프라인 지원과 재조회 성능을 개선할 수 있습니다.

```kotlin
@Entity(tableName = "market_ohlcv")
data class MarketOhlcvEntity(
    @PrimaryKey val id: String,    // "$date-$ticker"
    val date: String,
    val ticker: String,
    val name: String,
    val open: Long,
    val high: Long,
    val low: Long,
    val close: Long,
    val volume: Long,
    val tradingValue: Long,
    val changeRate: Double,
    val fetchedAt: Long = System.currentTimeMillis()
)

// 변환 함수
fun MarketOhlcv.toEntity(date: String) = MarketOhlcvEntity(
    id = "$date-$ticker", date = date,
    ticker = ticker, name = name,
    open = open, high = high, low = low, close = close,
    volume = volume, tradingValue = tradingValue,
    changeRate = changeRate
)
```

### 7.5 TickerCache 활용 전략

`TickerCache`는 종목코드 → ISIN 코드 매핑을 인메모리에 유지합니다.

| 속성 | 값 |
|------|-----|
| TTL | 1시간 (기본값, 생성자에서 변경 가능) |
| Thread Safety | `ConcurrentHashMap` 기반 |
| 범위 | Stock / ETF 별도 캐시 공간 |
| 초기화 | 첫 개별종목 조회 시 자동 (전체 티커 일괄 캐시) |

```kotlin
// 캐시 TTL 커스터마이징
val cache = TickerCache(ttlMillis = 7_200_000L)  // 2시간

// 수동 초기화
cache.clear()

// 캐시 상태 확인
println("Stock cache: ${cache.stockCacheSize()}")
println("ETF cache: ${cache.etfCacheSize()}")
```

---

## 8. 데이터 흐름

```
┌─────────────┐    suspend fun    ┌─────────────┐    POST    ┌──────────┐
│  Android     │  ─────────────►  │  KrxStock    │  ───────► │  KRX     │
│  ViewModel   │                  │  KrxEtf      │           │  Data    │
│  (Coroutine) │  ◄─────────────  │  KrxIndex    │  ◄─────── │  Server  │
│              │  List<Model>     │              │   JSON    │          │
└─────────────┘                   └──────┬───────┘           └──────────┘
                                         │
                                  ┌──────▼───────┐
                                  │  KrxClient   │
                                  │  - Retry 3x  │
                                  │  - Headers   │
                                  │  - 30s Timeout│
                                  └──────┬───────┘
                                         │
                                  ┌──────▼───────┐
                                  │ KrxJsonParser│
                                  │ - OutBlock_1 │
                                  │ - "82,200"→L │
                                  │ - Null safe  │
                                  └──────┬───────┘
                                         │
                                  ┌──────▼───────┐
                                  │ TickerCache  │
                                  │ - ticker→ISIN│
                                  │ - TTL 1hour  │
                                  │ - Thread-safe│
                                  └──────────────┘
```

---

## 9. 전체 API 목록 요약

### KrxStock

| 메서드 | 입력 | 출력 | 용도 |
|--------|------|------|------|
| `getMarketOhlcv` | date, market? | `List<MarketOhlcv>` | 전종목 시세 |
| `getOhlcvByTicker` | start, end, ticker | `List<StockOhlcvHistory>` | 개별종목 기간 시세 |
| `getMarketCap` | date, market? | `List<MarketCap>` | 전종목 시가총액 |
| `getMarketFundamental` | date, market? | `List<StockFundamental>` | 전종목 PER/PBR/EPS |
| `getTickerList` | date, market? | `List<TickerInfo>` | 종목 목록 |
| `getMarketTradingByInvestor` | start, end, market?, valueType?, askBidType? | `List<InvestorTrading>` | 시장 투자자별 거래 |
| `getTradingByInvestor` | start, end, ticker, valueType?, askBidType? | `List<InvestorTrading>` | 종목 투자자별 거래 |
| `getShortSellingAll` | date, market? | `List<ShortSelling>` | 전종목 공매도 거래 |
| `getShortSellingByTicker` | start, end, ticker | `List<ShortSellingHistory>` | 종목 공매도 일별 |
| `getShortBalanceAll` | date, market? | `List<ShortBalance>` | 전종목 공매도 잔고 |
| `getShortBalanceByTicker` | start, end, ticker | `List<ShortBalanceHistory>` | 종목 공매도 잔고 일별 |

### KrxEtf

| 메서드 | 입력 | 출력 | 용도 |
|--------|------|------|------|
| `getEtfPrice` | date | `List<EtfPrice>` | 전종목 ETF 시세 |
| `getOhlcvByTicker` | start, end, ticker | `List<EtfOhlcvHistory>` | ETF 기간 시세 |
| `getEtfTickerList` | date | `List<EtfInfo>` | ETF 종목 목록 |
| `getPortfolio` | date, ticker | `List<EtfPortfolio>` | ETF 구성종목 |
| `getEtfName` | ticker, date | `String?` | ETF 이름 조회 |

### KrxIndex

| 메서드 | 입력 | 출력 | 용도 |
|--------|------|------|------|
| `getOhlcvByTicker` | start, end, ticker | `List<IndexOhlcv>` | 지수 기간 OHLCV |
| `getKospi` | start, end | `List<IndexOhlcv>` | KOSPI 조회 |
| `getKospi200` | start, end | `List<IndexOhlcv>` | KOSPI 200 조회 |
| `getKosdaq` | start, end | `List<IndexOhlcv>` | KOSDAQ 조회 |
| `getKosdaq150` | start, end | `List<IndexOhlcv>` | KOSDAQ 150 조회 |
| `getIndexList` | date, market? | `List<IndexInfo>` | 지수 목록 |
| `getIndexName` | ticker, date | `String?` | 지수 이름 조회 |

---

## 10. Claude Code 통합

### 10.1 CLAUDE.md에 추가할 내용

KrxKt를 사용하는 Android 프로젝트의 `CLAUDE.md`에 다음을 추가하면 Claude Code가 KrxKt 관련 코드를 올바르게 생성합니다.

```markdown
## KrxKt 라이브러리 사용 규칙

이 프로젝트는 KrxKt 라이브러리로 KRX 시장 데이터를 조회합니다.

### 필수 규칙
- 모든 KrxKt API는 suspend fun → 반드시 Coroutine scope 내에서 호출
- 날짜: "yyyyMMdd" 문자열 (예: "20210122")
- 가격/금액: Long (원), 비율/지수: Double
- 빈 응답은 emptyList() (null 아님)
- KrxStock/KrxEtf/KrxIndex는 Hilt @Singleton으로 관리
- TickerCache는 KrxStock과 KrxEtf에 공유 주입

### 에러 처리
- KrxError.NetworkError → 네트워크 확인 안내
- KrxError.InvalidDateError → 입력값 검증 실패
- KrxError.ParseError → 로깅 후 빈 결과 처리

### 참조
- KrxKt API 명세: @docs/KrxKt_Integration_Guide.md
```

### 10.2 Skill 설정 (`krxkt-integration`)

`.claude/skills/krxkt-integration/SKILL.md`:

```yaml
---
name: krxkt-integration
description: >
  KrxKt 라이브러리를 사용한 KRX 시장 데이터 조회 코드 생성.
  Android ViewModel에서 KrxStock/KrxEtf/KrxIndex API 호출,
  Room 캐싱, Hilt DI 설정, 에러 핸들링 패턴 적용 시 사용.
---

# KrxKt Integration Skill

## 코드 생성 시 적용할 패턴
- viewModelScope.launch(Dispatchers.IO) 내에서 API 호출
- try-catch로 KrxError sealed class 처리
- StateFlow로 UI 상태 전달
- 병렬 조회 시 coroutineScope + async 패턴 사용

## 데이터 타입 규칙
- 가격/금액: Long (원 단위)
- 비율/지수: Double
- 날짜: String ("yyyyMMdd")
- 빈 응답: emptyList() (null 아님)

## DI 규칙
- KrxStock, KrxEtf, KrxIndex: @Singleton
- TickerCache: @Singleton, KrxStock/KrxEtf 공유 주입
- KrxClient: @Singleton, OkHttpClient 주입
```

### 10.3 Agent 설정 (`krx-data-analyzer`)

`.claude/agents/krx-data-analyzer.md`:

```yaml
---
name: krx-data-analyzer
description: >
  KrxKt 라이브러리를 활용한 KRX 시장 데이터 분석 코드 작성.
  OHLCV, 시가총액, 투자자별 거래실적, 공매도 데이터를 조합한
  분석 로직 구현 시 사용.
tools: Read, Write, Edit, Bash
model: sonnet
---

You are a KRX market data analysis specialist using KrxKt library.

## 핵심 규칙
1. 모든 KrxKt 함수는 suspend fun — Coroutine 내에서만 호출
2. 병렬 조회 시 coroutineScope + async 패턴 사용
3. 날짜는 "yyyyMMdd" 형식만 허용
4. 가격 데이터는 Long, 지수는 Double
5. 에러는 KrxError sealed class로 처리
6. 공휴일/휴장일은 빈 리스트 반환
```

---

## 11. 테스트 가이드

### 11.1 MockWebServer 기반 단위 테스트

```kotlin
class KrxStockTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var krxStock: KrxStock

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val client = KrxClient(baseUrl = mockWebServer.url("/").toString())
        krxStock = KrxStock(client = client)
    }

    @Test
    fun `getMarketOhlcv returns parsed data`() = runTest {
        val mockJson = """
        {
            "OutBlock_1": [{
                "ISU_SRT_CD": "005930",
                "ISU_ABBRV": "삼성전자",
                "TDD_OPNPRC": "82,200",
                "TDD_HGPRC": "82,200",
                "TDD_LWPRC": "81,600",
                "TDD_CLSPRC": "82,200",
                "ACC_TRDVOL": "16,543,541",
                "ACC_TRDVAL": "1,350,862,127,900",
                "FLUC_RT": "-0.50"
            }]
        }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(mockJson))

        val result = krxStock.getMarketOhlcv("20210122")

        assertEquals(1, result.size)
        assertEquals("005930", result[0].ticker)
        assertEquals(82200L, result[0].close)
        assertEquals(16543541L, result[0].volume)
        assertEquals(-0.50, result[0].changeRate)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        krxStock.close()
    }
}
```

### 11.2 테스트 기준 데이터

| 항목 | 값 | 용도 |
|------|-----|------|
| 기준 날짜 | `"20210122"` | 안정적인 과거 거래일 |
| 주식 종목 | `"005930"` (삼성전자) | Stock API 검증 |
| ETF 종목 | `"069500"` (KODEX 200) | ETF API 검증 |
| 공휴일 | `"20210101"` | 빈 응답 반환 확인 |

### 11.3 Build Commands

```bash
# 단위 테스트 (네트워크 불필요)
./gradlew test

# 통합 테스트 (한국 네트워크 필요)
./gradlew runIntegrationTest
./gradlew runIntegrationTest -PmainClass=com.krxkt.integration.EtfPortfolioTestKt
```

---

## 12. Quick Start

```kotlin
import com.krxkt.KrxStock
import com.krxkt.KrxEtf
import com.krxkt.KrxIndex
import com.krxkt.model.Market
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val krxStock = KrxStock()
    val krxEtf = KrxEtf()
    val krxIndex = KrxIndex()

    try {
        // 전종목 OHLCV
        val allStocks = krxStock.getMarketOhlcv("20210122", Market.KOSPI)
        println("KOSPI 종목 수: ${allStocks.size}")

        // 개별종목 히스토리
        val samsung = krxStock.getOhlcvByTicker("20210104", "20210129", "005930")
        println("삼성전자 거래일 수: ${samsung.size}")

        // ETF 구성종목
        val portfolio = krxEtf.getPortfolio("20210122", "069500")
        println("KODEX 200 구성종목: ${portfolio.size}개")

        // KOSPI 지수
        val kospi = krxIndex.getKospi("20210104", "20210129")
        println("KOSPI 마지막 종가: ${kospi.lastOrNull()?.close}")

    } finally {
        krxStock.close()
        krxEtf.close()
        krxIndex.close()
    }
}
```

---

*Document Version 1.1 | February 2026*
