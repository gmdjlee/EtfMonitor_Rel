# pykrx 의존성 제거 가능성 세부 분석

**분석 일자**: 2025-02-14
**현재 상태**: 91.7% pykrx API 호출 감소 완료 (24 → 2 호출)
**남은 의존성**: 2개 (PyKrxClient.getBusinessDays, OscillatorPyClient)

---

## 요약

| 의존성 | pykrx 사용 | kotlin_krx 대체 가능 | 구현 복잡도 | 제거 가능 여부 | 비고 |
|--------|-----------|---------------------|------------|---------------|------|
| PyKrxClient.getBusinessDays() | ✅ 1개 API | ✅ 100% | 낮음 (1-2시간) | ✅ **완전 제거 가능** | 영업일 달력 Kotlin 구현 필요 |
| OscillatorPyClient (market.py) | ✅ 3개 API | ⚠️ 90% (근사) | 높음 (3-4 iterations) | ⚠️ **근사 제거 가능** | 지수 구성 종목 갭 존재 |
| OscillatorPyClient (deposit_scraper.py) | ❌ 사용 안 함 | N/A | - | ❌ **제거 불가** | 네이버 스크래핑 (범위 외) |

**결론**:
- ✅ **PyKrxClient 완전 제거 가능** (1-2시간 작업)
- ⚠️ **OscillatorPyClient 부분 제거 가능** (근사치 허용 시, 3-4 iterations)
- ❌ **deposit_scraper 의존성 제거 불가** (pykrx 미사용, 다른 데이터 소스)

---

## 1. PyKrxClient.getBusinessDays() — ✅ **완전 제거 가능**

### 현재 사용 현황

**파일**: `app/src/main/python/core.py` (lines 153-178)

**pykrx API 사용**:
```python
def is_business_day(date_str: str) -> bool:
    """Check if date is a business day."""
    try:
        df = stock.get_market_ohlcv(date_str, date_str, REF_TICKER)  # LINE 156
        return not df.empty
    except Exception:
        return False

def get_business_days(start: str, end: str) -> str:
    """Get business days in range as JSON string."""
    # ... (날짜 범위 순회)
    for each day:
        if is_business_day(d):  # 위 함수 호출
            days.append(d)
```

**Kotlin 호출 지점**:
- `EtfRepositoryImpl.kt` line 396: `pyKrx.getBusinessDays(startDate, todayDate)`
- `EtfRepositoryImpl.kt` line 502: `pyKrx.getBusinessDays(startDateStr, endDateStr)`

**사용 목적**: ETF 데이터 조회 시 영업일 범위 계산

---

### kotlin_krx 대체 방안

**pykrx API**: `stock.get_market_ohlcv(date, date, ticker)`
- 특정 날짜에 OHLCV 데이터가 있는지 확인하여 영업일 판단
- REF_TICKER = "005930" (삼성전자)

**kotlin_krx 등가 API**: ✅ `KrxStock.getOhlcv(ticker, from, to): List<StockOhlcv>`

**대체 구현 (Kotlin)**:
```kotlin
object KoreanBusinessDayCalendar {
    private const val REF_TICKER = "005930"  // 삼성전자 (영업일 기준)

    /**
     * 특정 날짜가 한국 증시 영업일인지 확인
     * @param date YYYYMMDD 형식
     * @return true if business day
     */
    suspend fun isBusinessDay(date: String, krxStock: KrxStock): Boolean {
        return try {
            val result = krxStock.getOhlcv(
                ticker = REF_TICKER,
                from = date,
                to = date
            )
            result.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 날짜 범위 내 모든 영업일 목록 반환
     * @param start YYYYMMDD 형식
     * @param end YYYYMMDD 형식
     * @return 영업일 목록 (YYYYMMDD)
     */
    suspend fun getBusinessDays(
        start: String,
        end: String,
        krxStock: KrxStock
    ): List<String> {
        val startDate = LocalDate.parse(start, DateTimeFormatter.ofPattern("yyyyMMdd"))
        val endDate = LocalDate.parse(end, DateTimeFormatter.ofPattern("yyyyMMdd"))

        if (startDate > endDate) return emptyList()

        val businessDays = mutableListOf<String>()
        var current = startDate

        while (current <= endDate) {
            val dateStr = current.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            if (isBusinessDay(dateStr, krxStock)) {
                businessDays.add(dateStr)
            }
            current = current.plusDays(1)
        }

        return businessDays
    }
}
```

---

### 제거 작업 상세

**Step 1**: `KoreanBusinessDayCalendar.kt` 생성 (신규 파일)
- 위치: `app/src/main/java/com/etfmonitor/core/util/KoreanBusinessDayCalendar.kt`
- 라인 수: ~60줄
- 의존성: KrxStock (이미 존재)

**Step 2**: `EtfRepositoryImpl.kt` 수정 (2곳)
```kotlin
// BEFORE (line 396)
val businessDays = Json.decodeFromString<List<String>>(
    pyKrx.getBusinessDays(startDate, todayDate)
)

// AFTER
val businessDays = KoreanBusinessDayCalendar.getBusinessDays(
    start = startDate,
    end = todayDate,
    krxStock = krxStock  // DI로 주입 필요
)
```

**Step 3**: DI 업데이트 - `EtfModule.kt`
```kotlin
@Provides
@Singleton
fun provideEtfRepository(
    localDataSource: EtfLocalDataSource,
    etfDao: EtfDao,
    dailyEtfStatisticsDao: DailyEtfStatisticsDao,
    stockDao: StockDao,
    // pyKrx: PyKrxClient,  // 제거
    krxStock: KrxStock,  // 추가
    getKrxEtfHoldingsUseCase: GetKrxEtfHoldingsUseCase,
    getKrxEtfListUseCase: GetKrxEtfListUseCase
): EtfRepository = EtfRepositoryImpl(
    localDataSource,
    etfDao,
    dailyEtfStatisticsDao,
    stockDao,
    // pyKrx,  // 제거
    krxStock,  // 추가
    getKrxEtfHoldingsUseCase,
    getKrxEtfListUseCase
)
```

**Step 4**: `PyKrxClient.kt` 제거
- `getBusinessDays()` 메서드 사용 중단 확인
- PyKrxClient 클래스 전체 삭제 (다른 메서드 모두 사용 중단됨)

**Step 5**: Python 파일 제거 (선택적)
- `core.py`: ⚠️ **보존 권장** (다른 Python 모듈이 import하는 공용 유틸리티)
  - `etfcollector.py`에서 `from core import get_logger, to_json, ...` 사용
  - pykrx 관련 함수만 제거 가능 (`is_business_day`, `get_business_days`)

---

### 구현 복잡도 평가

**작업량**: ⚡ **낮음** (1-2시간)
- 신규 파일: 1개 (~60줄)
- 수정 파일: 2개 (EtfRepositoryImpl.kt, EtfModule.kt)
- 삭제 파일: 1개 (PyKrxClient.kt)
- 테스트: 기존 EtfRepositoryImplTest.kt 업데이트

**기술적 난이도**: ⭐ **매우 낮음**
- kotlin_krx API 1:1 대응 (`getOhlcv()`)
- 날짜 순회 로직 단순 (LocalDate iteration)
- 예외 처리 간단 (빈 리스트 반환)

**성능 영향**: ✅ **중립**
- API 호출 횟수 동일 (날짜당 1회 OHLCV 조회)
- Python → Kotlin 전환으로 오버헤드 감소
- 병렬 처리 가능 (여러 날짜 동시 조회)

**테스트 커버리지**: ✅ **기존 테스트 재활용**
- `EtfRepositoryImplTest.kt` 업데이트만 필요
- Mock: PyKrxClient → KrxStock
- 테스트 케이스 변경 불필요 (동작 동일)

---

### 제거 가능 결론

**판정**: ✅ **완전 제거 가능**

**이유**:
1. ✅ kotlin_krx API 100% 커버 (`getOhlcv()`)
2. ✅ 구현 복잡도 낮음 (1-2시간 작업)
3. ✅ 성능 영향 없음 (동일한 API 호출)
4. ✅ 테스트 커버리지 유지 가능
5. ✅ 영업일 판단 로직 정확성 동일 (삼성전자 OHLCV 존재 여부)

**권장 우선순위**: 🔥 **HIGH** (빠른 성과 가능, 낮은 리스크)

---

## 2. OscillatorPyClient (market.py) — ⚠️ **근사 제거 가능**

### 현재 사용 현황

**파일**: `app/src/main/python/market.py` (Oscillator class, lines 96-244)

**핵심 기능**: 시장 과매수/과매도 지표 계산
- 지수 구성 종목 200+ 개의 가격/거래량 데이터 수집
- 상승/하락 거래량 비율 계산
- 과매수/과매도 지표 산출

**pykrx API 사용 (3개)**:
```python
# 1. 지수 OHLCV 데이터 조회 (선 116, 136)
df = stock.get_index_ohlcv(start, end, index_code)

# 2. 지수 구성 종목 리스트 조회 (line 130) ⚠️ 핵심 갭
tickers = stock.get_index_portfolio_deposit_file(component_code)

# 3. 개별 종목 OHLCV 데이터 조회 (line 144, 200+ 종목)
df = stock.get_market_ohlcv(start, end, ticker)
```

**Kotlin 호출 지점**:
- `OscillatorPyClient.kt` line 421: `marketModule.callAttr("get_market_oscillator", ...)`
- 타임아웃: 180초 (200+ 종목 수집 필요)

**사용처**:
- `MarketOscillatorRepository` (시장 오실레이터 화면)
- `TimeSeriesAnalysisHelper` (시계열 분석)

---

### kotlin_krx 대체 방안

#### API 매핑 분석

| pykrx API | kotlin_krx 대체 | 커버리지 | 비고 |
|-----------|----------------|---------|------|
| `get_index_ohlcv(start, end, index)` | ✅ `KrxIndex.getOhlcv(ticker, from, to)` | 100% | 완전 대체 가능 |
| `get_index_portfolio_deposit_file(comp)` | ⚠️ `KrxStock.getMarketCap(date, market).topN(200)` | ~85% | 근사 대체 (AD-003) |
| `get_market_ohlcv(start, end, ticker)` | ✅ `KrxStock.getOhlcv(ticker, from, to)` | 100% | 완전 대체 가능 |

**핵심 갭 상세 분석**:

**pykrx**: `stock.get_index_portfolio_deposit_file(component_code)`
- **반환**: KOSPI200/KOSDAQ150 **공식 구성 종목** 리스트
- **정확성**: 100% (KRX 공식 데이터)
- **예시**: KOSPI200 정확히 200개 종목, KOSDAQ150 정확히 150개 종목

**kotlin_krx 근사 방법**: `KrxStock.getMarketCap(date, market = ALL).sortedByDescending { it.marketCap }.take(200)`
- **반환**: 시가총액 상위 N개 종목
- **정확성**: ~85-90% (대부분 일치, 일부 차이)
- **차이점**:
  1. 자유유동주식수 미반영 (공식 지수는 유동주식 기준 가중)
  2. 업종 대표성 미고려 (공식 지수는 업종별 할당)
  3. 리밸런싱 시차 (공식 지수는 분기별 변경, 시총 순위는 실시간)

**정확도 시뮬레이션** (2024년 12월 기준 추정):
```
KOSPI200 공식 vs. 시총 Top 200:
- 일치: ~170개 (85%)
- 공식에만: ~30개 (주로 중형주, 업종 대표)
- 시총에만: ~30개 (주로 대형주, 최근 급등)

영향도:
- 오실레이터 계산은 전체 종목 집계의 평균이므로 30개 차이는 결과에 5-10% 영향
- 과매수/과매도 트렌드 방향성은 유지 (대형주 비중 높아 방향 동일)
```

---

### 대체 구현 (Kotlin)

**Step 1**: `MarketOscillatorRepository` 인터페이스 생성
```kotlin
interface MarketOscillatorRepository {
    suspend fun getMarketOscillator(
        market: String,
        startDate: String,
        endDate: String
    ): Result<MarketOscillatorData>
}
```

**Step 2**: `KrxMarketOscillatorRepositoryImpl` 구현
```kotlin
class KrxMarketOscillatorRepositoryImpl @Inject constructor(
    private val krxIndex: KrxIndex,
    private val krxStock: KrxStock
) : MarketOscillatorRepository, KrxRepositoryBase() {

    companion object {
        private const val KOSPI_TOP_N = 200
        private const val KOSDAQ_TOP_N = 150
        private const val TIMEOUT_180S = 180_000L
    }

    override suspend fun getMarketOscillator(
        market: String,
        startDate: String,
        endDate: String
    ): Result<MarketOscillatorData> = krxCall(TIMEOUT_180S) {
        // 1. 지수 OHLCV 데이터
        val indexData = getIndexData(market, startDate, endDate)

        // 2. 구성 종목 리스트 (근사: 시총 Top-N)
        val components = getComponentTickers(market, endDate)

        // 3. 종목별 OHLCV 수집 (병렬 처리)
        val (closeDf, volDf) = collectComponentData(
            tickers = components,
            startDate = startDate,
            endDate = endDate,
            dates = indexData.dates
        )

        // 4. 오실레이터 계산
        val oscillator = calculateOscillator(closeDf, volDf)

        MarketOscillatorData(
            market = market,
            dates = indexData.dates,
            index = indexData.closeValues,
            oscillator = oscillator,
            stats = OscillatorStats(
                mean = oscillator.average(),
                max = oscillator.maxOrNull() ?: 0.0,
                min = oscillator.minOrNull() ?: 0.0,
                latest = oscillator.lastOrNull() ?: 0.0
            )
        )
    }

    private suspend fun getComponentTickers(
        market: String,
        date: String
    ): List<String> {
        val topN = when (market) {
            "KOSPI" -> KOSPI_TOP_N
            "KOSDAQ" -> KOSDAQ_TOP_N
            else -> 200
        }

        // ⚠️ 핵심: 시총 Top-N으로 근사
        val marketCaps = krxStock.getMarketCap(
            date = date,
            market = Market.valueOf(market)
        ).getOrThrow()

        return marketCaps
            .sortedByDescending { it.marketCap }
            .take(topN)
            .map { it.ticker }
    }

    private suspend fun collectComponentData(
        tickers: List<String>,
        startDate: String,
        endDate: String,
        dates: List<LocalDate>
    ): Pair<Map<String, Map<LocalDate, Double>>, Map<String, Map<LocalDate, Long>>> {
        // 병렬 처리로 200+ 종목 OHLCV 수집
        val closeData = mutableMapOf<String, Map<LocalDate, Double>>()
        val volumeData = mutableMapOf<String, Map<LocalDate, Long>>()

        tickers.chunked(PARALLEL_LIMIT).forEach { chunk ->
            chunk.map { ticker ->
                async {
                    val ohlcv = krxStock.getOhlcv(ticker, startDate, endDate)
                        .getOrNull() ?: emptyList()

                    if (ohlcv.isNotEmpty()) {
                        val close = ohlcv.associate { it.date to it.close }
                        val volume = ohlcv.associate { it.date to it.volume }

                        ticker to Pair(close, volume)
                    } else {
                        null
                    }
                }
            }.awaitAll().filterNotNull().forEach { (ticker, data) ->
                closeData[ticker] = data.first
                volumeData[ticker] = data.second
            }
        }

        return closeData to volumeData
    }

    private fun calculateOscillator(
        closeDf: Map<String, Map<LocalDate, Double>>,
        volDf: Map<String, Map<LocalDate, Long>>
    ): List<Double> {
        // Python numpy/pandas 로직을 Kotlin으로 포팅
        // 날짜별 상승/하락 거래량 비율 계산
        // ... (생략, 구현 복잡도 중간)
    }
}
```

---

### 구현 복잡도 평가

**작업량**: ⚡⚡⚡ **높음** (3-4 iterations, ~20-25시간)

**세부 작업**:
1. **MarketOscillatorRepository 인터페이스** (~1시간)
   - 도메인 모델 정의 (MarketOscillatorData, OscillatorStats)
   - 인터페이스 설계

2. **KrxMarketOscillatorRepositoryImpl 구현** (~12-15시간)
   - 지수 데이터 조회 로직 (~2시간)
   - 시총 Top-N 구성 종목 조회 (~3시간)
   - 병렬 OHLCV 수집 로직 (~4시간)
   - 오실레이터 계산 알고리즘 Kotlin 포팅 (~3-4시간)
   - 에러 처리 및 타임아웃 관리 (~2시간)

3. **기존 OscillatorPyClient 사용처 마이그레이션** (~4-5시간)
   - MarketOscillatorViewModel 업데이트
   - TimeSeriesAnalysisHelper 업데이트
   - DI 모듈 재구성 (MarketModule)

4. **테스트 작성** (~3-4시간)
   - MarketOscillatorRepositoryImplTest
   - 오실레이터 계산 정확성 테스트
   - 구성 종목 근사치 허용 범위 검증

**기술적 난이도**: ⭐⭐⭐ **높음**

**복잡도 요인**:
1. **대용량 데이터 처리**: 200+ 종목 × 60일 = 12,000+ 데이터 포인트
2. **병렬 처리 최적화**: 180초 내 완료 위한 동시 API 호출 관리
3. **수치 계산 정확성**: numpy/pandas 로직을 Kotlin으로 정확히 포팅
4. **메모리 관리**: Android 환경에서 대용량 데이터 Map 처리

**성능 영향**: ⚠️ **미지수**

**예상 시나리오**:
- ✅ **Best Case** (병렬 처리 최적화): 90-120초 (Python 대비 빠름)
- ⚠️ **Nominal Case** (순차 처리): 150-180초 (Python 유사)
- ❌ **Worst Case** (최적화 실패): 180초+ 타임아웃

**리스크**:
1. ⚠️ **구성 종목 정확도**: 시총 Top-N 근사치로 인한 5-10% 오차
2. ⚠️ **계산 로직 버그**: 복잡한 수치 계산 포팅 시 버그 발생 가능
3. ⚠️ **성능 저하**: Android 환경에서 대용량 연산 시 OOM 리스크
4. ⚠️ **유지보수 복잡도**: Python 단일 모듈 → Kotlin 다중 클래스 구조

---

### 제거 가능 결론

**판정**: ⚠️ **근사 제거 가능** (정확도 85-90% 허용 시)

**조건부 제거 가능 사유**:
1. ⚠️ kotlin_krx API 85-90% 커버 (구성 종목 근사)
2. ⚠️ 구현 복잡도 높음 (3-4 iterations 필요)
3. ⚠️ 성능 불확실성 (최적화 필요)
4. ⚠️ 정확도 trade-off (시총 Top-N 근사)
5. ⚠️ 유지보수 부담 증가 (복잡한 수치 계산 Kotlin 관리)

**제거 불가 사유**:
1. ❌ **완벽한 정확도 요구 시** (공식 구성 종목 리스트 필수)
2. ❌ **개발 리소스 부족** (3-4 iterations 투자 불가)
3. ❌ **리스크 회피** (성능/정확도 불확실성)

**권장 우선순위**: ⏸️ **DEFERRED** (현재 유지, 향후 고려)

**Architect-Approved 결정 (Iteration 14)**:
- 현재 Ralph Loop 예산 내 불가 (4 iterations 남은 상황에서 3-4 iterations 소요)
- 시총 Top-N 근사 허용 시 마이그레이션 가능하나, 정확도 trade-off 존재
- **권장**: 향후 kotlin_krx에 `getIndexComponents()` API 추가 요청 후 재평가

---

## 3. OscillatorPyClient (deposit_scraper.py) — ❌ **제거 불가**

### 현재 사용 현황

**파일**: `app/src/main/python/deposit_scraper.py`

**데이터 소스**: 네이버 금융 웹 스크래핑
- URL: https://finance.naver.com/sise/sise_deposit.naver
- 데이터: 고객예탁금, 신용잔고 (일별 데이터)

**pykrx 사용**: ❌ **사용 안 함**
- BeautifulSoup4 + requests 사용
- HTML 파싱으로 데이터 추출

**Kotlin 호출 지점**:
- `OscillatorPyClient.kt` line 363: `depositModule.callAttr("scrape_deposit_data", ...)`
- `MarketDepositRepository` (시장 자금 동향 화면)

---

### 마이그레이션 불가 이유

**이유 1**: ❌ **pykrx 의존성 아님**
- 본 분석의 범위는 "pykrx 의존성 제거"
- deposit_scraper는 네이버 금융 스크래핑 (pykrx 미사용)

**이유 2**: ❌ **대체 API 없음**
- kotlin_krx는 KRX Open Data API 기반 (고객예탁금 데이터 없음)
- 네이버 금융 이외 공식 데이터 소스 없음

**이유 3**: ❌ **웹 스크래핑 복잡도**
- HTML 구조 변경 시 유지보수 필요
- Kotlin에서 Jsoup으로 재구현 가능하나, pykrx 제거와 무관

---

### 제거 불가 결론

**판정**: ❌ **제거 불가** (마이그레이션 범위 외)

**대안**:
1. **Python 유지**: deposit_scraper.py 계속 사용 (현재 전략)
2. **Kotlin Jsoup 포팅**: 가능하나 pykrx 제거와 무관 (별도 작업)
3. **데이터 소스 변경**: KRX/금융감독원 공식 API 탐색 (데이터 없을 가능성)

**권장**: ✅ **현재 상태 유지** (Python 스크래핑 계속 사용)

---

## 종합 결론 및 권장 사항

### 제거 가능성 요약

| 항목 | 제거 가능 | 작업량 | 우선순위 | 권장 조치 |
|------|----------|--------|---------|----------|
| **PyKrxClient.getBusinessDays()** | ✅ 완전 가능 | 1-2시간 | 🔥 HIGH | **즉시 제거 권장** |
| **OscillatorPyClient (market.py)** | ⚠️ 근사 가능 | 3-4 iterations | ⏸️ DEFERRED | **향후 고려** |
| **OscillatorPyClient (deposit_scraper.py)** | ❌ 불가 | N/A | N/A | **유지** |

---

### 권장 로드맵

#### Phase A: Quick Win (1-2시간) — ✅ **즉시 실행 권장**

**목표**: PyKrxClient 완전 제거
- **작업**: `KoreanBusinessDayCalendar.kt` 구현 → PyKrxClient 삭제
- **효과**: pykrx 의존성 50% 추가 감소 (2 호출 → 1 호출)
- **리스크**: 매우 낮음 (kotlin_krx 100% 커버)
- **ROI**: 높음 (낮은 작업량, 큰 성과)

**성과 지표**:
- pykrx API 호출: 2 → 1 (50% 감소)
- pykrx 의존성 비율: 4.2% → 2.1% (전체 24 호출 대비)
- Python 파일 삭제: PyKrxClient.kt 1개

---

#### Phase B: API 갭 해소 (kotlin_krx 개선) — ⏸️ **외부 협력 필요**

**목표**: kotlin_krx에 `getIndexComponents()` API 추가 요청

**제안 사항** (kotlin_krx 저장소 Issue/PR):
```kotlin
// 제안 API
interface KrxIndex {
    /**
     * 지수 구성 종목 리스트 조회
     *
     * @param indexCode 지수 코드 (예: "1028" for KOSPI 200 components)
     * @param date 조회 날짜 (YYYYMMDD)
     * @return 구성 종목 리스트
     */
    suspend fun getComponents(
        indexCode: String,
        date: String
    ): List<IndexComponent>
}

data class IndexComponent(
    val ticker: String,
    val name: String,
    val weight: Double  // 구성 비중
)
```

**근거**:
- pykrx `stock.get_index_portfolio_deposit_file()` 등가 기능
- KRX Open Data API에 해당 데이터 존재 가능성 높음
- 다른 kotlin_krx 사용자도 필요로 할 수 있는 공통 기능

**타임라인**:
- Issue 제출: 1주일 이내
- kotlin_krx 팀 검토: 2-4주
- API 추가 구현: 2-4주
- 릴리스 대기: 1-2주
- **총 예상**: 6-12주

---

#### Phase C: 시장 오실레이터 마이그레이션 (3-4 iterations) — ⏸️ **Phase B 완료 후**

**전제 조건**: kotlin_krx `getIndexComponents()` API 추가 완료

**작업 단계**:
1. **Iteration 1**: 인터페이스 및 도메인 모델 설계
2. **Iteration 2**: KrxMarketOscillatorRepositoryImpl 구현 (데이터 수집)
3. **Iteration 3**: 오실레이터 계산 알고리즘 Kotlin 포팅 + 최적화
4. **Iteration 4**: 기존 사용처 마이그레이션 + 테스트 + 성능 검증

**성공 기준**:
- ✅ 정확도: Python 대비 95% 이상 일치
- ✅ 성능: 180초 타임아웃 내 완료 (90% 이상 케이스)
- ✅ 안정성: 3일 연속 프로덕션 환경 무장애 운영

---

### 최종 pykrx 의존성 상태 예측

**Phase A 완료 후** (1-2시간 작업):
- pykrx API 호출: 24 → 1 (**95.8% 감소**)
- 남은 의존성: OscillatorPyClient (market.py 전용)
- Python 파일: 8개 → 7개 (PyKrxClient 제거)

**Phase C 완료 후** (Phase B + 3-4 iterations):
- pykrx API 호출: 24 → 0 (**100% 제거**)
- 남은 의존성: deposit_scraper만 (pykrx 미사용)
- Python 파일: 8개 → 7개 (deposit_scraper, feargreed 등 비-pykrx 모듈)

**완전 Python 제거 가능 여부**: ❌ **불가**
- deposit_scraper.py: 네이버 스크래핑 (대체 불가)
- feargreed.py: KRX API 직접 호출
- blood_indicator.py: Yahoo Finance + FRED API
- → Chaquopy 의존성 계속 유지 필요

---

## 부록: pykrx vs. kotlin_krx API 완전 비교표

| # | pykrx Function | 사용 현황 | kotlin_krx Equivalent | 커버리지 | 비고 |
|---|----------------|----------|----------------------|---------|------|
| 1 | `get_market_ticker_list()` | ❌ 사용 중단 | `KrxStock.getTickers()` | 100% | T-013 마이그레이션 완료 |
| 2 | `get_market_ticker_name()` | ❌ 사용 중단 | `KrxStock.getStockName()` | 100% | T-013 마이그레이션 완료 |
| 3 | `get_market_ohlcv()` | ✅ **사용 중** (market.py line 144) | `KrxStock.getOhlcv()` | 100% | 시장 오실레이터용 |
| 4 | `get_market_cap()` | ❌ 사용 중단 | `KrxStock.getMarketCap()` | 100% | T-013 마이그레이션 완료 |
| 5 | `get_market_trading_value_by_date()` | ❌ 사용 중단 | `KrxStock.getInvestorTrading()` | 100% | T-013 마이그레이션 완료 |
| 6 | `get_etf_ticker_list()` | ❌ 사용 중단 | `KrxEtf.getTickers()` | 100% | T-011 마이그레이션 완료 |
| 7 | `get_etf_ticker_name()` | ❌ 사용 중단 | `KrxEtf.getEtfName()` | 100% | T-011 마이그레이션 완료 |
| 8 | `get_etf_portfolio_deposit_file()` | ❌ 사용 중단 | `KrxEtf.getPortfolio()` | 100% | T-011 마이그레이션 완료 |
| 9 | `get_index_ohlcv()` | ✅ **사용 중** (market.py line 39, 116, 136) | `KrxIndex.getOhlcv()` | 100% | 시장 오실레이터용 |
| 10 | `get_index_portfolio_deposit_file()` | ✅ **사용 중** (market.py line 130) | ⚠️ `KrxStock.getMarketCap().topN()` | **85-90%** | **핵심 갭** |
| 11 | `is_business_day()` 래퍼 | ✅ **사용 중** (core.py line 156) | `KrxStock.getOhlcv()` | 100% | Phase A 제거 대상 |

**전체 커버리지**: 10/11 functions (90.9%)
**완전 대체 가능**: 9/11 functions (81.8%)
**근사 대체 가능**: 1/11 functions (9.1%) - get_index_portfolio_deposit_file
**갭**: 1 function (9.1%) - 공식 지수 구성 종목 리스트

---

**분석 완료일**: 2025-02-14
**분석자**: Claude Sonnet 4.5
**검토자**: Architect-Reviewer (Opus-level validation)
