# 고급 분석 기능 개발 명세서

**문서 버전**: 1.0
**작성일**: 2025-12-03
**프로젝트**: ETF Monitor

---

## 목차

1. [개요](#1-개요)
2. [시가총액 가중 ETF 흐름 분석](#2-시가총액-가중-etf-흐름-분석)
3. [외국인/기관 수급 Divergence 분석](#3-외국인기관-수급-divergence-분석)
4. [예탁금 대비 시가총액 비율 분석](#4-예탁금-대비-시가총액-비율-분석)
5. [섹터별 Fear & Greed 분석](#5-섹터별-fear--greed-분석)
6. [ETF 간 상관관계 분석](#6-etf-간-상관관계-분석)
7. [통합 대시보드](#7-통합-대시보드)
8. [데이터베이스 변경 사항](#8-데이터베이스-변경-사항)
9. [구현 우선순위 및 일정](#9-구현-우선순위-및-일정)

---

## 1. 개요

### 1.1 목적
기존 ETF Monitor 앱의 데이터를 활용하여 더 깊이 있는 시장 분석 기능을 제공합니다.
현재 수집 중인 데이터(ETF 보유 종목, 시가총액, 외국인/기관 수급, 예탁금, Fear & Greed 지수)를
복합적으로 분석하여 투자 의사결정에 도움이 되는 인사이트를 제공합니다.

### 1.2 기대 효과
- 기관 투자자의 자금 흐름을 더 정확하게 파악
- 시장 과열/침체 상황을 조기에 감지
- 섹터별 투자 기회 식별
- 복합 지표를 통한 신뢰도 높은 시그널 생성

### 1.3 기술 스택
- **언어**: Kotlin 2.1.0
- **UI**: Jetpack Compose + Material Design 3
- **차트**: Vico 2.0.0-alpha.28
- **데이터베이스**: Room 2.8.3
- **비동기**: Coroutines + Flow
- **DI**: Hilt 2.54

---

## 2. 시가총액 가중 ETF 흐름 분석

### 2.1 기능 설명
ETF 보유 종목의 비중 변화를 시가총액으로 가중하여 실제 자금 흐름의 규모를 파악합니다.
단순 비중(%) 변화가 아닌, 시가총액 기준 금액 변화를 추적합니다.

### 2.2 분석 로직

```
시총 가중 유입 = Σ (비중 증가 종목의 시가총액 × 비중 변화율)
시총 가중 유출 = Σ (비중 감소 종목의 시가총액 × 비중 변화율)
순 자금 흐름 = 시총 가중 유입 - 시총 가중 유출
```

### 2.3 데이터 소스

| 데이터 | 테이블 | 필드 |
|--------|--------|------|
| ETF 비중 변화 | `holdings` | `weightBps`, `amountMillion` |
| 시가총액 | `stock_analysis_data` | `marketCap` (List<Long>) |
| 종목 정보 | `stocks` | `ticker`, `name`, `market` |

### 2.4 구현 상세

#### 2.4.1 데이터 클래스

```kotlin
// 시총 가중 흐름 결과
data class MarketCapWeightedFlow(
    val date: String,
    val market: String,                    // KOSPI, KOSDAQ, ALL
    val totalInflow: Long,                 // 시총 가중 유입 (억원)
    val totalOutflow: Long,                // 시총 가중 유출 (억원)
    val netFlow: Long,                     // 순 흐름
    val topInflowStocks: List<StockFlow>,  // 상위 유입 종목
    val topOutflowStocks: List<StockFlow>, // 상위 유출 종목
    val inflowBySize: Map<MarketCapSize, Long>,  // 대/중/소형주별
    val marketCapChangeRate: Double        // 전체 시총 변화율
)

data class StockFlow(
    val ticker: String,
    val name: String,
    val marketCap: Long,           // 시가총액 (억원)
    val weightChange: Double,      // 비중 변화 (%)
    val flowAmount: Long,          // 시총 가중 흐름 (억원)
    val etfCount: Int              // 보유 ETF 수
)

enum class MarketCapSize {
    LARGE,    // 시총 10조 이상
    MID,      // 시총 1조~10조
    SMALL     // 시총 1조 미만
}
```

#### 2.4.2 Repository 메서드

```kotlin
// AdvancedAnalysisRepository.kt
interface AdvancedAnalysisRepository {

    suspend fun calculateMarketCapWeightedFlow(
        date: String,
        previousDate: String,
        market: String = "ALL"
    ): MarketCapWeightedFlow

    fun observeMarketCapWeightedFlowHistory(
        days: Int = 30,
        market: String = "ALL"
    ): Flow<List<MarketCapWeightedFlow>>
}
```

#### 2.4.3 DAO 쿼리

```kotlin
// EtfDao.kt 추가 쿼리
@Query("""
    SELECT
        h.stockTicker as ticker,
        s.name as name,
        s.market as market,
        h.date as date,
        (h.weightBps - COALESCE(prev.weightBps, 0)) as weightChangeBps,
        h.amountMillion as currentAmount,
        COALESCE(prev.amountMillion, 0) as previousAmount
    FROM holdings h
    LEFT JOIN holdings prev ON h.etfTicker = prev.etfTicker
        AND h.stockTicker = prev.stockTicker
        AND prev.date = :previousDate
    JOIN stocks s ON h.stockTicker = s.ticker
    WHERE h.date = :currentDate
        AND (:market = 'ALL' OR s.market = :market)
        AND (h.weightBps - COALESCE(prev.weightBps, 0)) != 0
    ORDER BY ABS(h.weightBps - COALESCE(prev.weightBps, 0)) DESC
""")
suspend fun getWeightChangesWithMarket(
    currentDate: String,
    previousDate: String,
    market: String
): List<WeightChangeData>
```

### 2.5 UI 설계

#### 2.5.1 화면 구성

```
┌─────────────────────────────────────┐
│  시총 가중 ETF 흐름                  │
├─────────────────────────────────────┤
│  [KOSPI] [KOSDAQ] [전체]            │  ← 시장 필터
├─────────────────────────────────────┤
│  ┌─────────────────────────────┐    │
│  │    순유입: +1,234억원        │    │  ← 핵심 지표 카드
│  │    유입: 3,456억 / 유출: 2,222억 │
│  └─────────────────────────────┘    │
├─────────────────────────────────────┤
│  [시총 가중 흐름 차트 - 30일]        │  ← 영역 차트
│  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓      │
│  ░░░░░░░░░░░░░░░░░░░░░░░░░░░       │
├─────────────────────────────────────┤
│  대형주  ████████  +800억           │  ← 시총 규모별 분포
│  중형주  ████      +300억           │
│  소형주  ██        +134억           │
├─────────────────────────────────────┤
│  📈 상위 유입 종목                   │
│  1. 삼성전자  +234억 (시총 400조)   │
│  2. SK하이닉스  +156억 (시총 120조) │
│  ...                                │
├─────────────────────────────────────┤
│  📉 상위 유출 종목                   │
│  1. 카카오  -89억 (시총 25조)       │
│  ...                                │
└─────────────────────────────────────┘
```

#### 2.5.2 Composable 구조

```kotlin
@Composable
fun MarketCapWeightedFlowScreen(
    navController: NavHostController,
    viewModel: MarketCapFlowViewModel = hiltViewModel()
)

@Composable
fun FlowSummaryCard(flow: MarketCapWeightedFlow)

@Composable
fun FlowHistoryChart(history: List<MarketCapWeightedFlow>)

@Composable
fun SizeDistributionBar(distribution: Map<MarketCapSize, Long>)

@Composable
fun TopFlowStocksList(
    title: String,
    stocks: List<StockFlow>,
    isInflow: Boolean
)
```

---

## 3. 외국인/기관 수급 Divergence 분석

### 3.1 기능 설명
외국인과 기관 투자자의 매매 방향이 다를 때(Divergence) 이를 감지하고,
과거 패턴을 기반으로 향후 주가 방향을 예측합니다.

### 3.2 분석 로직

```
Divergence Score = (외국인 5일 누적 / 거래대금) - (기관 5일 누적 / 거래대금)

해석:
- Score > 0.5: 외국인 강세 (외국인 매수, 기관 매도)
- Score < -0.5: 기관 강세 (기관 매수, 외국인 매도)
- -0.5 ≤ Score ≤ 0.5: 방향 일치 또는 약한 Divergence
```

### 3.3 데이터 소스

| 데이터 | 테이블 | 필드 |
|--------|--------|------|
| 외국인 수급 | `stock_analysis_data` | `foreign5d` (List<Long>) |
| 기관 수급 | `stock_analysis_data` | `institution5d` (List<Long>) |
| 날짜 정보 | `stock_analysis_data` | `dates` (List<String>) |

### 3.4 구현 상세

#### 3.4.1 데이터 클래스

```kotlin
// 수급 Divergence 결과
data class SupplyDemandDivergence(
    val ticker: String,
    val name: String,
    val market: String,
    val date: String,
    val foreign5d: Long,              // 외국인 5일 누적 (백만원)
    val institution5d: Long,          // 기관 5일 누적 (백만원)
    val divergenceScore: Double,      // -1.0 ~ 1.0
    val divergenceType: DivergenceType,
    val historicalAccuracy: Double?,  // 과거 정확도 (있는 경우)
    val priceChange5d: Double?        // 5일 후 주가 변화 (검증용)
)

enum class DivergenceType {
    FOREIGN_BULLISH,      // 외국인 매수, 기관 매도
    INSTITUTION_BULLISH,  // 기관 매수, 외국인 매도
    ALIGNED_BULLISH,      // 동반 매수
    ALIGNED_BEARISH,      // 동반 매도
    NEUTRAL               // 중립
}

// 시장 전체 Divergence 요약
data class MarketDivergenceSummary(
    val date: String,
    val market: String,
    val foreignBullishCount: Int,
    val institutionBullishCount: Int,
    val alignedBullishCount: Int,
    val alignedBearishCount: Int,
    val topForeignBullish: List<SupplyDemandDivergence>,
    val topInstitutionBullish: List<SupplyDemandDivergence>,
    val marketSentiment: MarketSentiment
)

enum class MarketSentiment {
    STRONG_FOREIGN_LED,   // 외국인 주도 상승
    STRONG_INSTITUTION_LED, // 기관 주도 상승
    CONSENSUS_BULLISH,    // 컨센서스 상승
    CONSENSUS_BEARISH,    // 컨센서스 하락
    MIXED                 // 혼조
}
```

#### 3.4.2 분석 알고리즘

```kotlin
class DivergenceAnalyzer @Inject constructor(
    private val stockAnalysisDao: StockAnalysisDao
) {

    fun calculateDivergenceScore(
        foreign5d: Long,
        institution5d: Long,
        marketCap: Long
    ): Double {
        val normalizedForeign = foreign5d.toDouble() / (marketCap / 100)
        val normalizedInstitution = institution5d.toDouble() / (marketCap / 100)

        return (normalizedForeign - normalizedInstitution).coerceIn(-1.0, 1.0)
    }

    fun classifyDivergence(
        foreign5d: Long,
        institution5d: Long,
        threshold: Long = 1_000_000_000L  // 10억원
    ): DivergenceType {
        return when {
            foreign5d > threshold && institution5d < -threshold ->
                DivergenceType.FOREIGN_BULLISH
            institution5d > threshold && foreign5d < -threshold ->
                DivergenceType.INSTITUTION_BULLISH
            foreign5d > threshold && institution5d > threshold ->
                DivergenceType.ALIGNED_BULLISH
            foreign5d < -threshold && institution5d < -threshold ->
                DivergenceType.ALIGNED_BEARISH
            else -> DivergenceType.NEUTRAL
        }
    }
}
```

### 3.5 UI 설계

```
┌─────────────────────────────────────┐
│  외국인/기관 수급 Divergence         │
├─────────────────────────────────────┤
│  시장 심리: 🟢 외국인 주도 상승       │
├─────────────────────────────────────┤
│  ┌───────────────────────────────┐  │
│  │ 외국인▲  기관▲  동반▲  동반▼  │  │  ← 분포 현황
│  │   45      23      67     12   │  │
│  └───────────────────────────────┘  │
├─────────────────────────────────────┤
│  🔵 외국인 강세 종목 (기관은 매도)   │
│  ┌─────────────────────────────┐    │
│  │ 삼성전자                     │    │
│  │ 외국인: +1,234억  기관: -567억│    │
│  │ Divergence: 0.82 📈         │    │
│  └─────────────────────────────┘    │
│  ...                                │
├─────────────────────────────────────┤
│  🟠 기관 강세 종목 (외국인은 매도)   │
│  ...                                │
├─────────────────────────────────────┤
│  📊 과거 Divergence 신호 정확도      │
│  외국인 강세 후 5일: 67% 상승        │
│  기관 강세 후 5일: 58% 상승          │
└─────────────────────────────────────┘
```

---

## 4. 예탁금 대비 시가총액 비율 분석

### 4.1 기능 설명
시장 전체 시가총액 대비 고객 예탁금 비율을 추적하여 시장의 유동성 수준과
투자 여력을 분석합니다. 이 비율이 높을수록 대기 자금이 풍부하여
상승 여력이 있음을 의미합니다.

### 4.2 분석 로직

```
예탁금/시총 비율 = (고객 예탁금 / 시장 시가총액) × 100

신용/예탁금 비율 = (신용 잔고 / 고객 예탁금) × 100

레버리지 위험도:
- 낮음: 신용/예탁금 < 30%
- 보통: 30% ≤ 신용/예탁금 < 50%
- 높음: 신용/예탁금 ≥ 50%
```

### 4.3 데이터 소스

| 데이터 | 테이블 | 필드 |
|--------|--------|------|
| 예탁금 | `market_deposits` | `depositAmount` (억원) |
| 신용잔고 | `market_deposits` | `creditAmount` (억원) |
| 시가총액 | `stock_analysis_data` | `marketCap` (합산 필요) |
| 시장지수 | `market_index` | `closePrice` |

### 4.4 구현 상세

#### 4.4.1 데이터 클래스

```kotlin
data class LiquidityAnalysis(
    val date: String,
    val depositAmount: Double,           // 예탁금 (억원)
    val creditAmount: Double,            // 신용잔고 (억원)
    val totalMarketCap: Long,            // 시장 시총 (억원)
    val depositToMarketCapRatio: Double, // 예탁금/시총 비율 (%)
    val creditToDepositRatio: Double,    // 신용/예탁금 비율 (%)
    val leverageRiskLevel: RiskLevel,
    val depositChange: Double,           // 예탁금 변화 (억원)
    val creditChange: Double,            // 신용 변화 (억원)
    val historicalPercentile: Double,    // 과거 대비 백분위
    val signal: LiquiditySignal
)

enum class RiskLevel {
    LOW, MEDIUM, HIGH, EXTREME
}

enum class LiquiditySignal {
    BULLISH_LIQUIDITY,    // 예탁금 증가 + 낮은 신용 = 상승 여력
    BEARISH_LEVERAGE,     // 신용 증가 + 예탁금 감소 = 하락 위험
    NEUTRAL,
    DELEVERAGING          // 신용 감소 = 조정 진행 중
}

// 장기 추이 분석
data class LiquidityTrend(
    val history: List<LiquidityAnalysis>,
    val avgDepositRatio: Double,
    val avgCreditRatio: Double,
    val currentVsAvgDeposit: Double,     // 현재/평균 비율
    val trendDirection: TrendDirection
)

enum class TrendDirection {
    ACCUMULATING,  // 예탁금 증가 추세
    DISTRIBUTING,  // 예탁금 감소 추세
    LEVERAGING,    // 신용 증가 추세
    DELEVERAGING,  // 신용 감소 추세
    STABLE
}
```

#### 4.4.2 시가총액 집계 쿼리

```kotlin
// StockAnalysisDao.kt 추가
@Query("""
    SELECT SUM(
        CAST(
            SUBSTR(marketCap,
                   INSTR(marketCap, ',') * :dateIndex + 1,
                   INSTR(SUBSTR(marketCap, INSTR(marketCap, ',') * :dateIndex + 1), ',') - 1
            ) AS INTEGER
        )
    ) as totalMarketCap
    FROM stock_analysis_data
    WHERE market = :market
""")
suspend fun getTotalMarketCap(market: String, dateIndex: Int): Long

// 또는 Repository에서 계산
suspend fun calculateTotalMarketCap(date: String, market: String): Long {
    val allStocks = stockAnalysisDao.getAllAnalysisData()
    return allStocks
        .filter { it.market == market }
        .sumOf { stockData ->
            val dateIdx = stockData.dates.indexOf(date)
            if (dateIdx >= 0 && dateIdx < stockData.marketCap.size) {
                stockData.marketCap[dateIdx]
            } else 0L
        }
}
```

### 4.5 UI 설계

```
┌─────────────────────────────────────┐
│  시장 유동성 분석                    │
├─────────────────────────────────────┤
│  ┌─────────┐  ┌─────────┐          │
│  │ 예탁금   │  │ 신용잔고 │          │
│  │ 52.3조  │  │ 18.7조  │          │
│  │ +1.2조  │  │ -0.3조  │          │
│  └─────────┘  └─────────┘          │
├─────────────────────────────────────┤
│  유동성 신호: 🟢 상승 여력 충분       │
│  레버리지: 🟡 보통 (35.8%)          │
├─────────────────────────────────────┤
│  예탁금/시총 비율                    │
│  ┌─────────────────────────────┐    │
│  │  현재: 2.3%                  │    │
│  │  평균: 2.1%  │  상위 30%     │    │
│  │  ▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░     │    │
│  └─────────────────────────────┘    │
├─────────────────────────────────────┤
│  [예탁금/신용 추이 차트 - 90일]      │
│  ════════════════════════════       │  ← 예탁금
│  ────────────────────────────       │  ← 신용잔고
├─────────────────────────────────────┤
│  📋 분석 요약                        │
│  • 예탁금 3일 연속 증가              │
│  • 신용잔고 감소 추세 (디레버리징)   │
│  • 역사적 상위 30% 유동성 수준       │
└─────────────────────────────────────┘
```

---

## 5. 섹터별 Fear & Greed 분석

### 5.1 기능 설명
전체 시장의 Fear & Greed 지수를 섹터별로 세분화하여 분석합니다.
각 섹터의 ETF 유입/유출, 변동성, 모멘텀을 기반으로 섹터별 심리 지수를 산출합니다.

### 5.2 분석 로직

```
섹터별 Fear & Greed = w1×ETF흐름점수 + w2×모멘텀점수 + w3×변동성점수

ETF 흐름 점수 = (섹터 신규편입 - 섹터 제외) / 섹터 전체 종목 수
모멘텀 점수 = 섹터 평균 5일 수익률 정규화
변동성 점수 = 1 - (섹터 변동성 / 시장 변동성)  // 낮을수록 좋음

가중치: w1=0.4, w2=0.35, w3=0.25
```

### 5.3 데이터 소스

| 데이터 | 테이블 | 필드 |
|--------|--------|------|
| 섹터 정보 | `stocks` | `sector` |
| ETF 흐름 | `holdings` | status별 집계 |
| 시장 지수 | `market_index` | `changeRate` |
| 전체 F&G | `fear_greed_index` | `fearGreedValue` |

### 5.4 구현 상세

#### 5.4.1 데이터 클래스

```kotlin
data class SectorFearGreed(
    val sector: String,
    val sectorName: String,            // 한글 섹터명
    val date: String,
    val fearGreedValue: Double,        // 0.0 ~ 1.0
    val etfFlowScore: Double,          // -1.0 ~ 1.0
    val momentumScore: Double,         // -1.0 ~ 1.0
    val volatilityScore: Double,       // 0.0 ~ 1.0
    val stockCount: Int,               // 섹터 내 종목 수
    val newEntries: Int,               // 신규 ETF 편입
    val removals: Int,                 // ETF 제외
    val avgWeightChange: Double,       // 평균 비중 변화
    val sentiment: SectorSentiment
)

enum class SectorSentiment {
    EXTREME_GREED,    // > 0.8
    GREED,            // 0.6 ~ 0.8
    NEUTRAL,          // 0.4 ~ 0.6
    FEAR,             // 0.2 ~ 0.4
    EXTREME_FEAR      // < 0.2
}

data class SectorAnalysisSummary(
    val date: String,
    val sectors: List<SectorFearGreed>,
    val topGreedSectors: List<SectorFearGreed>,
    val topFearSectors: List<SectorFearGreed>,
    val sectorRotationSignal: SectorRotationSignal?
)

data class SectorRotationSignal(
    val fromSector: String,
    val toSector: String,
    val confidence: Double,
    val description: String
)
```

#### 5.4.2 섹터 분류 매핑

```kotlin
object SectorMapping {
    val SECTOR_NAMES = mapOf(
        "반도체" to "Semiconductor",
        "2차전지" to "Battery",
        "바이오" to "Bio/Healthcare",
        "자동차" to "Automotive",
        "금융" to "Financial",
        "IT/소프트웨어" to "IT/Software",
        "화학" to "Chemical",
        "철강" to "Steel",
        "건설" to "Construction",
        "유통" to "Retail",
        "엔터테인먼트" to "Entertainment",
        "기타" to "Others"
    )

    // ETF 키워드 기반 섹터 매핑
    fun mapEtfToSector(etfName: String): String {
        return when {
            etfName.contains("반도체") -> "반도체"
            etfName.contains("2차전지") || etfName.contains("배터리") -> "2차전지"
            etfName.contains("바이오") || etfName.contains("헬스케어") -> "바이오"
            etfName.contains("자동차") || etfName.contains("모빌리티") -> "자동차"
            etfName.contains("금융") || etfName.contains("은행") -> "금융"
            etfName.contains("IT") || etfName.contains("소프트웨어") -> "IT/소프트웨어"
            else -> "기타"
        }
    }
}
```

### 5.5 UI 설계

```
┌─────────────────────────────────────┐
│  섹터별 Fear & Greed               │
├─────────────────────────────────────┤
│  시장 전체: 0.62 (탐욕)             │
├─────────────────────────────────────┤
│  섹터 히트맵                        │
│  ┌─────────────────────────────┐    │
│  │ 반도체 🟢  2차전지 🟢        │    │
│  │  0.75       0.71            │    │
│  │ 바이오 🟡  자동차 🟡         │    │
│  │  0.52       0.48            │    │
│  │ 금융 🟠    IT 🟢            │    │
│  │  0.35       0.68            │    │
│  └─────────────────────────────┘    │
├─────────────────────────────────────┤
│  🔥 탐욕 상위 섹터                   │
│  1. 반도체    0.75  ETF +12 종목    │
│  2. 2차전지   0.71  ETF +8 종목     │
│  3. IT       0.68  ETF +5 종목     │
├─────────────────────────────────────┤
│  ❄️ 공포 상위 섹터                   │
│  1. 건설     0.28  ETF -6 종목     │
│  2. 금융     0.35  ETF -3 종목     │
├─────────────────────────────────────┤
│  🔄 섹터 로테이션 신호               │
│  금융 → 반도체 이동 감지 (신뢰도 72%)│
└─────────────────────────────────────┘
```

---

## 6. ETF 간 상관관계 분석

### 6.1 기능 설명
여러 ETF 간의 보유 종목 중복도와 비중 변화 상관관계를 분석하여
ETF 포트폴리오 다변화에 도움이 되는 정보를 제공합니다.

### 6.2 분석 로직

```
종목 중복률 = (ETF A ∩ ETF B) / (ETF A ∪ ETF B) × 100

비중 변화 상관계수 = Corr(ETF A 비중변화, ETF B 비중변화)

포트폴리오 다변화 점수 = 1 - 평균 상관계수
```

### 6.3 데이터 소스

| 데이터 | 테이블 | 필드 |
|--------|--------|------|
| ETF 목록 | `etfs` | `ticker`, `name` |
| 보유 종목 | `holdings` | 전체 필드 |

### 6.4 구현 상세

#### 6.4.1 데이터 클래스

```kotlin
data class EtfCorrelation(
    val etf1Ticker: String,
    val etf1Name: String,
    val etf2Ticker: String,
    val etf2Name: String,
    val overlapRatio: Double,          // 종목 중복률 (0.0 ~ 1.0)
    val commonStockCount: Int,         // 공통 종목 수
    val weightChangeCorrelation: Double, // 비중 변화 상관계수 (-1.0 ~ 1.0)
    val topCommonStocks: List<CommonStock>
)

data class CommonStock(
    val ticker: String,
    val name: String,
    val etf1Weight: Double,
    val etf2Weight: Double,
    val avgWeight: Double
)

data class EtfCorrelationMatrix(
    val date: String,
    val etfs: List<String>,
    val correlationMatrix: List<List<Double>>,  // N x N 행렬
    val clusters: List<EtfCluster>              // 유사 ETF 그룹
)

data class EtfCluster(
    val clusterId: Int,
    val etfs: List<String>,
    val avgIntraCorrelation: Double,
    val dominantSector: String?
)

// 포트폴리오 분석
data class PortfolioDiversification(
    val selectedEtfs: List<String>,
    val overallDiversificationScore: Double,  // 0.0 ~ 1.0 (높을수록 분산)
    val pairwiseCorrelations: List<EtfCorrelation>,
    val suggestions: List<DiversificationSuggestion>
)

data class DiversificationSuggestion(
    val type: SuggestionType,
    val message: String,
    val affectedEtfs: List<String>
)

enum class SuggestionType {
    HIGH_OVERLAP_WARNING,      // 높은 중복률 경고
    ADD_FOR_DIVERSIFICATION,   // 분산을 위한 추가 추천
    REMOVE_REDUNDANT           // 중복 ETF 제거 추천
}
```

#### 6.4.2 상관계수 계산

```kotlin
class EtfCorrelationAnalyzer @Inject constructor(
    private val etfDao: EtfDao
) {

    suspend fun calculateOverlapRatio(
        etf1Holdings: List<Holding>,
        etf2Holdings: List<Holding>
    ): Double {
        val stocks1 = etf1Holdings.map { it.stockTicker }.toSet()
        val stocks2 = etf2Holdings.map { it.stockTicker }.toSet()

        val intersection = stocks1.intersect(stocks2).size
        val union = stocks1.union(stocks2).size

        return if (union > 0) intersection.toDouble() / union else 0.0
    }

    suspend fun calculateWeightChangeCorrelation(
        etf1Ticker: String,
        etf2Ticker: String,
        dates: List<String>
    ): Double {
        // 공통 종목의 비중 변화 시계열 추출
        val commonStocks = getCommonStocks(etf1Ticker, etf2Ticker, dates.last())

        if (commonStocks.size < 5) return 0.0  // 최소 5개 종목 필요

        val etf1Changes = mutableListOf<Double>()
        val etf2Changes = mutableListOf<Double>()

        for (stock in commonStocks) {
            val changes1 = getWeightChanges(etf1Ticker, stock, dates)
            val changes2 = getWeightChanges(etf2Ticker, stock, dates)

            etf1Changes.addAll(changes1)
            etf2Changes.addAll(changes2)
        }

        return pearsonCorrelation(etf1Changes, etf2Changes)
    }

    private fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        require(x.size == y.size) { "Lists must have same size" }
        val n = x.size
        if (n < 2) return 0.0

        val meanX = x.average()
        val meanY = y.average()

        var numerator = 0.0
        var denomX = 0.0
        var denomY = 0.0

        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            numerator += dx * dy
            denomX += dx * dx
            denomY += dy * dy
        }

        val denominator = sqrt(denomX * denomY)
        return if (denominator > 0) numerator / denominator else 0.0
    }
}
```

### 6.5 UI 설계

```
┌─────────────────────────────────────┐
│  ETF 상관관계 분석                   │
├─────────────────────────────────────┤
│  상관관계 히트맵                     │
│  ┌─────────────────────────────┐    │
│  │     A    B    C    D    E   │    │
│  │ A  1.0  0.8  0.3  0.2  0.5 │    │
│  │ B  0.8  1.0  0.4  0.3  0.6 │    │
│  │ C  0.3  0.4  1.0  0.7  0.2 │    │
│  │ D  0.2  0.3  0.7  1.0  0.1 │    │
│  │ E  0.5  0.6  0.2  0.1  1.0 │    │
│  └─────────────────────────────┘    │
│  🔴 높음 (>0.7)  🟡 보통  🟢 낮음    │
├─────────────────────────────────────┤
│  📊 ETF 클러스터                     │
│  ┌─────────────────────────────┐    │
│  │ 그룹 1 (반도체 중심)         │    │
│  │ • KODEX 반도체              │    │
│  │ • TIGER 반도체              │    │
│  │ 내부 상관: 0.85             │    │
│  └─────────────────────────────┘    │
│  ┌─────────────────────────────┐    │
│  │ 그룹 2 (2차전지 중심)        │    │
│  │ • KODEX 2차전지             │    │
│  │ • TIGER 2차전지             │    │
│  │ 내부 상관: 0.78             │    │
│  └─────────────────────────────┘    │
├─────────────────────────────────────┤
│  ⚠️ 높은 중복 경고                   │
│  KODEX 반도체 ↔ TIGER 반도체        │
│  중복률: 82%  상관: 0.85            │
│  → 분산 효과 낮음, 하나만 선택 권장  │
├─────────────────────────────────────┤
│  💡 분산 투자 제안                   │
│  현재 포트폴리오 분산도: 0.45        │
│  KODEX 바이오 추가 시: 0.68 (+0.23) │
└─────────────────────────────────────┘
```

---

## 7. 통합 대시보드

### 7.1 기능 설명
모든 고급 분석 기능을 한 화면에서 요약하여 보여주는 대시보드입니다.
핵심 지표와 신호를 빠르게 파악할 수 있습니다.

### 7.2 UI 설계

```
┌─────────────────────────────────────┐
│  📊 고급 분석 대시보드               │
│  2025-12-03 기준                    │
├─────────────────────────────────────┤
│  ┌─────────┐ ┌─────────┐ ┌────────┐│
│  │시총가중  │ │수급     │ │유동성  ││
│  │+1,234억 │ │외국인↑  │ │양호    ││
│  │ 🟢     │ │ 🔵     │ │ 🟢    ││
│  └─────────┘ └─────────┘ └────────┘│
├─────────────────────────────────────┤
│  섹터 심리 요약                      │
│  탐욕: 반도체, 2차전지               │
│  공포: 건설, 금융                    │
│  🔄 금융→반도체 로테이션 감지        │
├─────────────────────────────────────┤
│  ETF 포트폴리오 상태                 │
│  분산도: 0.65 (양호)                │
│  ⚠️ KODEX/TIGER 반도체 중복 주의    │
├─────────────────────────────────────┤
│  📈 종합 신호                        │
│  ┌─────────────────────────────┐    │
│  │      🟢 매수 우위            │    │
│  │  시총흐름(+) + 유동성(+)     │    │
│  │  + 외국인주도 + 반도체탐욕   │    │
│  └─────────────────────────────┘    │
├─────────────────────────────────────┤
│  [시총흐름] [수급] [유동성]          │
│  [섹터F&G] [ETF상관] [상세설정]     │
└─────────────────────────────────────┘
```

---

## 8. 데이터베이스 변경 사항

### 8.1 신규 테이블

#### 8.1.1 SectorAnalysis 테이블

```kotlin
@Entity(tableName = "sector_analysis")
data class SectorAnalysis(
    @PrimaryKey
    val id: String,  // "{sector}-{date}"
    val sector: String,
    val date: String,
    val fearGreedValue: Double,
    val etfFlowScore: Double,
    val momentumScore: Double,
    val volatilityScore: Double,
    val stockCount: Int,
    val newEntries: Int,
    val removals: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

#### 8.1.2 EtfCorrelationCache 테이블

```kotlin
@Entity(tableName = "etf_correlation_cache")
data class EtfCorrelationCache(
    @PrimaryKey
    val id: String,  // "{etf1}-{etf2}-{date}"
    val etf1Ticker: String,
    val etf2Ticker: String,
    val date: String,
    val overlapRatio: Double,
    val weightCorrelation: Double,
    val commonStockCount: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

#### 8.1.3 LiquidityAnalysisResult 테이블

```kotlin
@Entity(tableName = "liquidity_analysis")
data class LiquidityAnalysisResult(
    @PrimaryKey
    val date: String,
    val depositAmount: Double,
    val creditAmount: Double,
    val totalMarketCap: Long,
    val depositToMarketCapRatio: Double,
    val creditToDepositRatio: Double,
    val riskLevel: String,
    val signal: String,
    val historicalPercentile: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

### 8.2 마이그레이션

```kotlin
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // sector_analysis 테이블
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS sector_analysis (
                id TEXT PRIMARY KEY NOT NULL,
                sector TEXT NOT NULL,
                date TEXT NOT NULL,
                fearGreedValue REAL NOT NULL,
                etfFlowScore REAL NOT NULL,
                momentumScore REAL NOT NULL,
                volatilityScore REAL NOT NULL,
                stockCount INTEGER NOT NULL,
                newEntries INTEGER NOT NULL,
                removals INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
        """)
        database.execSQL("CREATE INDEX index_sector_analysis_date ON sector_analysis(date)")
        database.execSQL("CREATE INDEX index_sector_analysis_sector ON sector_analysis(sector)")

        // etf_correlation_cache 테이블
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS etf_correlation_cache (
                id TEXT PRIMARY KEY NOT NULL,
                etf1Ticker TEXT NOT NULL,
                etf2Ticker TEXT NOT NULL,
                date TEXT NOT NULL,
                overlapRatio REAL NOT NULL,
                weightCorrelation REAL NOT NULL,
                commonStockCount INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
        """)
        database.execSQL("CREATE INDEX index_etf_correlation_date ON etf_correlation_cache(date)")

        // liquidity_analysis 테이블
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS liquidity_analysis (
                date TEXT PRIMARY KEY NOT NULL,
                depositAmount REAL NOT NULL,
                creditAmount REAL NOT NULL,
                totalMarketCap INTEGER NOT NULL,
                depositToMarketCapRatio REAL NOT NULL,
                creditToDepositRatio REAL NOT NULL,
                riskLevel TEXT NOT NULL,
                signal TEXT NOT NULL,
                historicalPercentile REAL NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
        """)
    }
}
```

### 8.3 Stock 테이블 섹터 업데이트

기존 `stocks` 테이블의 `sector` 필드 활용을 위해 섹터 데이터 수집 로직 추가 필요.

```python
# stocks.py 추가 함수
def get_stock_sector(ticker: str) -> str:
    """KRX에서 종목의 업종 정보 조회"""
    try:
        from pykrx import stock
        info = stock.get_market_ticker_name(ticker)
        # 업종 분류 로직
        return sector
    except:
        return "기타"
```

---

## 9. 구현 우선순위 및 일정

### 9.1 우선순위

| 순위 | 기능 | 복잡도 | 가치 | 의존성 |
|------|------|--------|------|--------|
| 1 | 시총 가중 ETF 흐름 | 중 | 높음 | 없음 |
| 2 | 외국인/기관 Divergence | 중 | 높음 | 없음 |
| 3 | 예탁금/시총 비율 | 낮음 | 중간 | 없음 |
| 4 | 섹터별 Fear & Greed | 높음 | 높음 | 섹터 데이터 |
| 5 | ETF 상관관계 | 높음 | 중간 | 없음 |
| 6 | 통합 대시보드 | 중 | 높음 | 1-5 완료 |

### 9.2 구현 단계

#### Phase 1: 기반 구축
- [ ] AdvancedAnalysisRepository 인터페이스 정의
- [ ] 데이터베이스 마이그레이션 (v13 → v14)
- [ ] 기본 분석 유틸리티 클래스 구현

#### Phase 2: 핵심 기능 (우선순위 1-3)
- [ ] 시총 가중 ETF 흐름 분석 구현
- [ ] 외국인/기관 Divergence 분석 구현
- [ ] 예탁금/시총 비율 분석 구현
- [ ] 각 기능별 화면 UI 구현

#### Phase 3: 고급 기능 (우선순위 4-5)
- [ ] 섹터 분류 데이터 수집 로직 추가
- [ ] 섹터별 Fear & Greed 분석 구현
- [ ] ETF 상관관계 분석 구현
- [ ] 상관관계 히트맵 UI 구현

#### Phase 4: 통합 및 최적화
- [ ] 통합 대시보드 구현
- [ ] 캐싱 및 성능 최적화
- [ ] 백그라운드 분석 Worker 구현
- [ ] 테스트 및 버그 수정

### 9.3 파일 구조

```
app/src/main/java/com/example/etfmonitor/
├── analysis/
│   ├── advanced/
│   │   ├── MarketCapFlowAnalyzer.kt
│   │   ├── DivergenceAnalyzer.kt
│   │   ├── LiquidityAnalyzer.kt
│   │   ├── SectorFearGreedAnalyzer.kt
│   │   └── EtfCorrelationAnalyzer.kt
│   └── AdvancedAnalysisRepository.kt
├── database/
│   ├── entities/
│   │   ├── SectorAnalysis.kt
│   │   ├── EtfCorrelationCache.kt
│   │   └── LiquidityAnalysisResult.kt
│   └── DAOs/
│       ├── SectorAnalysisDao.kt
│       ├── EtfCorrelationDao.kt
│       └── LiquidityAnalysisDao.kt
├── ui/
│   └── screens/
│       └── advanced/
│           ├── AdvancedDashboardScreen.kt
│           ├── MarketCapFlowScreen.kt
│           ├── DivergenceScreen.kt
│           ├── LiquidityScreen.kt
│           ├── SectorFearGreedScreen.kt
│           ├── EtfCorrelationScreen.kt
│           └── viewmodels/
│               ├── AdvancedDashboardViewModel.kt
│               ├── MarketCapFlowViewModel.kt
│               ├── DivergenceViewModel.kt
│               ├── LiquidityViewModel.kt
│               ├── SectorFearGreedViewModel.kt
│               └── EtfCorrelationViewModel.kt
└── worker/
    └── AdvancedAnalysisWorker.kt
```

---

## 부록: 용어 정의

| 용어 | 정의 |
|------|------|
| **시총 가중** | 시가총액을 기준으로 가중치를 부여한 계산 방식 |
| **Divergence** | 두 지표가 서로 다른 방향을 가리키는 상황 |
| **Fear & Greed** | 시장 심리를 0(극도 공포)~1(극도 탐욕)로 표현한 지수 |
| **섹터 로테이션** | 투자 자금이 한 섹터에서 다른 섹터로 이동하는 현상 |
| **레버리지** | 신용 거래를 통한 차입 투자 비율 |
| **BPS (Basis Points)** | 0.01%를 1로 표현하는 단위 (100bps = 1%) |

---

**문서 끝**
