# Market Cap & Supply/Demand Oscillator App Specification

**Version**: 1.0.0
**Date**: 2026-01-15
**Author**: Claude Code
**Status**: Draft

---

## 1. Overview

### 1.1 Purpose
종목별 시가총액과 외국인/기관 수급 데이터를 기반으로 MACD 스타일 오실레이터를 계산하고 시각화하는 독립형 애플리케이션을 개발합니다.

### 1.2 Core Value Proposition
- **수급 기반 기술적 분석**: 외국인/기관의 순매매 흐름을 오실레이터로 변환
- **다중 기술지표 통합**: MACD, Elder Impulse, DeMark TD, Trend Signal
- **실시간 매매 시그널**: 정량화된 점수 기반 매수/매도 신호

### 1.3 Target Users
- 개인 투자자 (종목 수급 분석)
- 트레이더 (기술적 분석 도구)
- 퀀트 분석가 (데이터 기반 의사결정)

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Search Panel │  │ Chart Views  │  │ Signal Dashboard     │  │
│  │ (Autocomplete)│  │ (5 Charts)   │  │ (Score/Recommendation)│ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    ViewModel Layer                               │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ OscillatorViewModel                                          ││
│  │ - State: Idle → Loading → Success/Error                     ││
│  │ - StateFlows: searchQuery, suggestions, selectedRange, etc. ││
│  └─────────────────────────────────────────────────────────────┘│
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    Domain Layer                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────────────┐   │
│  │ Repository  │  │ Oscillator  │  │ TrendSignal           │   │
│  │ Interface   │  │ Calculator  │  │ Calculator            │   │
│  └─────────────┘  └─────────────┘  └───────────────────────┘   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    Data Layer                                    │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────────────┐   │
│  │ KIS API     │  │ Local DB    │  │ Python Scripts        │   │
│  │ Client      │  │ (SQLite)    │  │ (Optional)            │   │
│  └─────────────┘  └─────────────┘  └───────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Data Flow

```
User Search → ViewModel → Repository → KIS API → Cache → Calculator → Chart
                 │                                          │
                 └──────────── State Updates ───────────────┘
```

### 2.3 Technology Stack Options

| Platform | Language | UI Framework | Chart Library |
|----------|----------|--------------|---------------|
| **Android** | Kotlin | Jetpack Compose | Vico / MPAndroidChart |
| **iOS** | Swift | SwiftUI | Charts (DGCharts) |
| **Desktop** | Kotlin | Compose Multiplatform | Vico |
| **Web** | TypeScript | React/Vue | Chart.js / Recharts / D3.js |
| **Cross-Platform** | Dart | Flutter | fl_chart |

---

## 3. Core Features

### 3.1 Stock Search & Selection
- **자동완성 검색**: 종목명 또는 티커로 검색
- **검색 기록**: 최근 검색 종목 저장 (최대 50개)
- **즐겨찾기**: 관심 종목 등록

### 3.2 Chart Views (5개 차트)

| # | Chart Name | Description | Intervals |
|---|------------|-------------|-----------|
| 1 | **Market Cap Oscillator** | 시가총액 + 수급 오실레이터 | Daily |
| 2 | **DeMark TD Setup** | 추세 피로도 카운터 | d/w/m |
| 3 | **Trend Signal** | MA + CMF + Fear & Greed | d/w |
| 4 | **Elder Impulse** | EMA + MACD Impulse | d/w |
| 5 | **MACD** | 표준 MACD + Signal + Histogram | Daily |

### 3.3 Date Range Filter
- Week (1주)
- Month (1개월)
- 3M (3개월)
- 6M (6개월)
- Year (1년)
- All (전체, 최대 2년)

### 3.4 Signal Dashboard
- **종합 점수**: -100 ~ +100
- **매매 신호**: STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL
- **신호 근거**: 개별 지표별 점수 breakdown

---

## 4. Data Models

### 4.1 Stock Data (기본 데이터)

```kotlin
data class StockData(
    val ticker: String,           // 종목코드 (예: "005930")
    val name: String,             // 종목명 (예: "삼성전자")
    val dates: List<String>,      // 날짜 리스트 (YYYY-MM-DD)
    val marketCap: List<Long>,    // 시가총액 (원)
    val foreign5d: List<Long>,    // 외국인 5일 누적 순매매 (원)
    val institution5d: List<Long> // 기관 5일 누적 순매매 (원)
)
```

### 4.2 Oscillator Result (오실레이터 계산 결과)

```kotlin
data class OscillatorResult(
    val dates: List<String>,
    val marketCap: List<Float>,    // 정규화된 시가총액
    val oscillator: List<Float>,   // 수급 오실레이터 (%)
    val ema: List<Float>,          // Supply Ratio EMA
    val macd: List<Float>,         // MACD 값
    val signal: List<Float>,       // Signal 라인
    val histogram: List<Float>     // Histogram (MACD - Signal)
)
```

### 4.3 Signal Analysis (신호 분석)

```kotlin
data class SignalAnalysis(
    val totalScore: Int,           // -100 ~ +100
    val signalType: SignalType,    // STRONG_BUY, BUY, etc.
    val oscillatorScore: Int,      // 오실레이터 점수 (±40)
    val crossScore: Int,           // 골든/데드 크로스 점수 (±30)
    val trendScore: Int,           // 추세 점수 (±30)
    val description: String        // 신호 설명
)

enum class SignalType {
    STRONG_BUY,  // score >= 60
    BUY,         // score >= 20
    NEUTRAL,     // -20 < score < 20
    SELL,        // score <= -20
    STRONG_SELL  // score <= -60
}
```

### 4.4 Trend Signal Data

```kotlin
data class TrendSignalData(
    val ticker: String,
    val name: String,
    val interval: String,          // "d", "w"
    val dates: List<String>,
    val open: List<Float>,
    val high: List<Float>,
    val low: List<Float>,
    val close: List<Float>,
    val volume: List<Long>,
    val ma: List<Float>,           // 20일 이동평균
    val cmf: List<Float>,          // Chaikin Money Flow
    val fearGreed: List<Float>,    // Fear & Greed Index
    val buySignal: List<Boolean>,  // 매수 신호
    val auxBuySignal: List<Boolean>,
    val sellSignal: List<Boolean>, // 매도 신호
    val auxSellSignal: List<Boolean>
)
```

### 4.5 Elder Impulse Data

```kotlin
data class ElderImpulseData(
    val ticker: String,
    val name: String,
    val interval: String,          // "d", "w"
    val dates: List<String>,
    val close: List<Float>,
    val marketCap: List<Long>,
    val ema: List<Float>,          // EMA13
    val macd: List<Float>,
    val macdSignal: List<Float>,
    val macdHist: List<Float>,
    val impulse: List<Int>         // 1 (bull), -1 (bear), 0 (neutral)
)
```

### 4.6 DeMark TD Data

```kotlin
data class DemarkTDData(
    val ticker: String,
    val name: String,
    val interval: String,          // "d", "w", "m"
    val intervalName: String,      // "일봉", "주봉", "월봉"
    val dates: List<String>,
    val close: List<Float>,
    val marketCap: List<Long>,
    val tdSell: List<Int>,         // TD Sell Setup Count (0-13+)
    val tdBuy: List<Int>           // TD Buy Setup Count (0-13+)
)
```

### 4.7 Database Entity (캐싱용)

```kotlin
@Entity(tableName = "stock_analysis_cache")
data class StockAnalysisCache(
    @PrimaryKey
    val ticker: String,
    val name: String,
    val dates: String,              // JSON Array
    val marketCap: String,          // JSON Array (Long)
    val foreign5d: String,          // JSON Array (Long)
    val institution5d: String,      // JSON Array (Long)
    val lastUpdated: Long,          // Epoch milliseconds
    val dataStartDate: String,
    val dataEndDate: String
)
```

---

## 5. API & Data Sources

### 5.1 KIS (Korea Investment & Securities) API

**필수 인증 정보:**
```json
{
  "appkey": "YOUR_APP_KEY",
  "appsecret": "YOUR_APP_SECRET",
  "access_token": "Bearer ...",
  "cano": "계좌번호",
  "acnt_prdt_cd": "계좌상품코드"
}
```

**주요 엔드포인트:**

| API | Path | Description |
|-----|------|-------------|
| 시세 조회 | `/uapi/domestic-stock/v1/quotations/inquire-price` | 현재가/시가총액 |
| 일별 시세 | `/uapi/domestic-stock/v1/quotations/inquire-daily-price` | OHLCV 데이터 |
| 투자자별 매매동향 | `/uapi/domestic-stock/v1/quotations/inquire-investor` | 외국인/기관 순매매 |

**Rate Limits:**
- 초당 20건 (실전투자)
- 초당 2건 (모의투자)

### 5.2 Alternative Data Sources

| Source | Data Type | Notes |
|--------|-----------|-------|
| **Naver Finance** | 시세, 수급 | 웹 스크래핑 필요 |
| **KRX 정보데이터시스템** | 공식 데이터 | API 인증 필요 |
| **FinanceDataReader** | Python 라이브러리 | 편리하지만 제한적 |
| **pykrx** | Python 라이브러리 | KRX 데이터 수집 |

### 5.3 Caching Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    Cache Decision Logic                      │
├─────────────────────────────────────────────────────────────┤
│ 1. Check local cache                                         │
│    ├── lastUpdated < 24 hours ago?                          │
│    ├── dataEndDate == today?                                │
│    └── dataPoints >= 80% of requested days?                 │
│                                                              │
│ 2. If ALL conditions met → Use cache                        │
│    Otherwise → Fetch fresh data from API                    │
│                                                              │
│ 3. Date range changes → Client-side filtering (no API call) │
│                                                              │
│ 4. Max cache: 730 days (2 years)                            │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. Calculation Formulas

### 6.1 Supply Ratio (수급 비율)

```
Supply Ratio[i] = (Foreign5d[i] + Institution5d[i]) / MarketCap[i]
```

### 6.2 EMA (Exponential Moving Average)

```
α = 2 / (period + 1)

EMA[0] = first_value
EMA[t] = α × value[t] + (1 - α) × EMA[t-1]
```

### 6.3 Market Cap Oscillator (MACD 스타일)

```python
# Step 1: Calculate Supply Ratio
supply_ratio = (foreign_5d + institution_5d) / market_cap

# Step 2: Calculate EMAs
ema12 = EMA(supply_ratio, period=12)
ema26 = EMA(supply_ratio, period=26)

# Step 3: MACD
macd = ema12 - ema26

# Step 4: Signal Line
signal = EMA(macd, period=9)

# Step 5: Oscillator (Histogram)
oscillator = macd - signal
```

### 6.4 Signal Score Calculation

```python
def calculate_signal_score(oscillator, macd, signal, histogram):
    score = 0

    # 1. Oscillator Value (±40 points)
    latest_osc = oscillator[-1]
    if latest_osc > 0.005:    score += 40   # > 0.5%
    elif latest_osc > 0.002:  score += 20   # > 0.2%
    elif latest_osc < -0.005: score -= 40   # < -0.5%
    elif latest_osc < -0.002: score -= 20   # < -0.2%

    # 2. MACD Cross (±30 points)
    if macd[-1] > signal[-1] and macd[-2] <= signal[-2]:
        score += 30  # Golden Cross
    elif macd[-1] < signal[-1] and macd[-2] >= signal[-2]:
        score -= 30  # Dead Cross
    elif macd[-1] > signal[-1]:
        score += 15  # Above Signal
    else:
        score -= 15  # Below Signal

    # 3. Histogram Trend (±30 points)
    recent_hist = histogram[-3:]
    if all(h > 0 for h in recent_hist) and is_increasing(recent_hist):
        score += 30
    elif all(h < 0 for h in recent_hist) and is_decreasing(recent_hist):
        score -= 30

    return score  # -100 to +100
```

### 6.5 Trend Signal Indicators

**Chaikin Money Flow (CMF):**
```python
# Money Flow Multiplier
mfm = ((close - low) - (high - close)) / (high - low)

# CMF (4-period)
cmf = (mfm * volume).rolling(4).sum() / volume.rolling(4).sum()
```

**Fear & Greed Index:**
```python
# Momentum (45%)
log_return = log(close / close.shift(1))
momentum = clip(log_return, -1, 1)

# Position in 52-week range (45%)
high_52w = high.rolling(252).max()
low_52w = low.rolling(252).min()
position = ((close - low_52w) / (high_52w - low_52w) - 0.5) * 2

# Volume Spike (5%)
vol_ratio = volume / volume.rolling(20).mean()
vol_spike = clip((vol_ratio - 1) / 2, -1, 1)

# Volatility (5%, inverted)
volatility = close.pct_change().rolling(20).std() * sqrt(252)
vol_component = clip((1 - volatility) * 2 - 1, -1, 1)

# Composite
fear_greed = (momentum * 0.45 + position * 0.45 +
              vol_spike * 0.05 + vol_component * 0.05)
```

**Buy/Sell Signal:**
```python
# Buy Conditions
cond1 = high > high.shift(1)    # Higher High
cond2 = close > ma              # Above MA
cond3 = cmf > 0                 # Positive CMF

buy_signal = cond1 & cond2 & cond3
aux_buy_signal = (cond1.astype(int) + cond2.astype(int) + cond3.astype(int) >= 2) & cond2

# Sell Conditions
cond1 = low < low.shift(1)      # Lower Low
cond2 = close < ma              # Below MA
cond3 = cmf < 0                 # Negative CMF

sell_signal = cond1 & cond2 & cond3
aux_sell_signal = (cond1.astype(int) + cond2.astype(int) + cond3.astype(int) >= 2) & cond2
```

### 6.6 Elder Impulse System

```python
# EMA 13
ema13 = close.ewm(span=13, adjust=False).mean()

# MACD
ema12 = close.ewm(span=12, adjust=False).mean()
ema26 = close.ewm(span=26, adjust=False).mean()
macd = ema12 - ema26

# Signal Line
signal = macd.ewm(span=9, adjust=False).mean()

# Histogram
histogram = macd - signal

# Impulse
ema_slope = ema13 - ema13.shift(1)
hist_slope = histogram - histogram.shift(1)

impulse = np.where(
    (ema_slope > 0) & (hist_slope > 0), 1,   # Bull (Green)
    np.where(
        (ema_slope < 0) & (hist_slope < 0), -1,  # Bear (Red)
        0  # Neutral (Gray)
    )
)
```

### 6.7 DeMark TD Setup

```python
def calculate_td_setup(close):
    n = len(close)
    td_sell = [0] * n
    td_buy = [0] * n

    for i in range(4, n):
        # TD Sell Setup (counting closes > close 4 bars ago)
        if close[i] > close[i-4]:
            td_sell[i] = td_sell[i-1] + 1
        else:
            td_sell[i] = 0

        # TD Buy Setup (counting closes < close 4 bars ago)
        if close[i] < close[i-4]:
            td_buy[i] = td_buy[i-1] + 1
        else:
            td_buy[i] = 0

    return td_sell, td_buy

# TD 9 = Potential reversal signal (fatigue point)
# TD 13+ = Extended exhaustion
```

---

## 7. UI/UX Specifications

### 7.1 Screen Layout

```
┌─────────────────────────────────────────────────────────────┐
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   Search Bar                         │   │
│  │  🔍 [종목명 또는 티커 입력...]          [검색기록]  │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Stock Info Card                         │   │
│  │  삼성전자 (005930)                                   │   │
│  │  최신 데이터: 2026-01-15 | 데이터 수: 365개         │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Date Range Selector                     │   │
│  │  [1W] [1M] [3M] [6M] [1Y] [ALL]                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                       │   │
│  │                                                       │   │
│  │              Chart Area (Pager)                      │   │
│  │                                                       │   │
│  │           ◀  [1] [2] [3] [4] [5]  ▶                 │   │
│  │                                                       │   │
│  │                                                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Signal Dashboard                        │   │
│  │  ┌─────────┐  ┌───────────────────────────────────┐ │   │
│  │  │  +67    │  │ 오실레이터: +40                   │ │   │
│  │  │ STRONG  │  │ MACD 크로스: +15                  │ │   │
│  │  │  BUY    │  │ 추세: +12                         │ │   │
│  │  └─────────┘  └───────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  [Interval: d/w/m]    (차트 2,3,4에서만 표시)       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 Chart Specifications

#### Chart 1: Market Cap Oscillator
- **Type**: Dual-Axis Line Chart
- **Left Y-Axis**: Market Cap (조/억원 단위)
- **Right Y-Axis**: Oscillator (%)
- **Lines**:
  - Market Cap: Bold primary color
  - Oscillator: Secondary color
- **Zero Line**: Dashed horizontal line at 0%

#### Chart 2: DeMark TD Setup
- **Type**: Candlestick + Markers
- **Main**: Close price line
- **Markers**:
  - TD Sell 9+: Red circle with number
  - TD Buy 9+: Green circle with number
- **Intervals**: Daily / Weekly / Monthly tabs

#### Chart 3: Trend Signal
- **Type**: Multi-Line + Scatter
- **Lines**:
  - Close price (primary)
  - MA20 (dashed)
- **Secondary Y-Axis**:
  - CMF (area fill)
  - Fear & Greed (line)
- **Scatter Points**:
  - Buy signals: Green triangle up
  - Sell signals: Red triangle down

#### Chart 4: Elder Impulse
- **Type**: Line + Bar Combo
- **Lines**:
  - Close price
  - EMA13
- **Bars**: MACD Histogram
- **Color Coding**:
  - Green: Bull impulse (impulse = 1)
  - Red: Bear impulse (impulse = -1)
  - Gray: Neutral (impulse = 0)

#### Chart 5: Standard MACD
- **Type**: Line + Bar Combo
- **Lines**:
  - MACD line (blue)
  - Signal line (orange, dashed)
- **Bars**: Histogram (green positive, red negative)
- **Zero Line**: Visible

### 7.3 Color Scheme

```kotlin
// Light Theme
val Primary = Color(0xFF1976D2)        // Blue
val OnPrimary = Color(0xFFFFFFFF)
val BullGreen = Color(0xFF4CAF50)
val BearRed = Color(0xFFF44336)
val NeutralGray = Color(0xFF9E9E9E)
val ChartBackground = Color(0xFFFAFAFA)
val ChartGrid = Color(0xFFE0E0E0)

// Dark Theme
val PrimaryDark = Color(0xFF90CAF9)
val OnPrimaryDark = Color(0xFF000000)
val BullGreenDark = Color(0xFF81C784)
val BearRedDark = Color(0xFFEF5350)
val NeutralGrayDark = Color(0xFFBDBDBD)
val ChartBackgroundDark = Color(0xFF121212)
val ChartGridDark = Color(0xFF424242)
```

### 7.4 Signal Dashboard Colors

| Score Range | Signal | Background | Text |
|-------------|--------|------------|------|
| >= 60 | STRONG_BUY | Dark Green | White |
| >= 20 | BUY | Light Green | Black |
| -20 ~ 20 | NEUTRAL | Gray | Black |
| <= -20 | SELL | Light Red | Black |
| <= -60 | STRONG_SELL | Dark Red | White |

### 7.5 Interaction Patterns

| Action | Behavior |
|--------|----------|
| **Search** | Debounce 300ms, show autocomplete dropdown |
| **Chart Swipe** | Horizontal pager navigation |
| **Date Range Tap** | Instant filter (client-side) |
| **Interval Change** | Fetch new data for affected chart only |
| **Chart Tap** | Show tooltip with exact values |
| **Long Press Chart** | Export/Share options |

---

## 8. Technical Requirements

### 8.1 Android (Kotlin + Compose)

**Minimum Requirements:**
- Min SDK: 26 (Android 8.0)
- Target SDK: 35 (Android 15)
- 64-bit only (arm64-v8a, x86_64)

**Dependencies:**
```toml
[versions]
kotlin = "2.1.0"
compose-bom = "2024.12.01"
hilt = "2.54"
room = "2.8.3"
coroutines = "1.10.2"
vico = "2.0.0-alpha.28"
okhttp = "4.12.0"

[libraries]
# Core
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android" }
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core" }

# Compose
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }

# DI
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose" }

# Database
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }

# Network
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json" }

# Charts
vico-compose = { module = "com.patrykandpatrick.vico:compose", version.ref = "vico" }
vico-compose-m3 = { module = "com.patrykandpatrick.vico:compose-m3", version.ref = "vico" }

# Security (API Key 암호화)
security-crypto = { module = "androidx.security:security-crypto" }
```

### 8.2 Web (React + TypeScript)

**Dependencies:**
```json
{
  "dependencies": {
    "react": "^18.2.0",
    "recharts": "^2.8.0",
    "axios": "^1.6.0",
    "zustand": "^4.4.0",
    "date-fns": "^2.30.0",
    "@tanstack/react-query": "^5.0.0",
    "dexie": "^3.2.0"
  },
  "devDependencies": {
    "typescript": "^5.3.0",
    "vite": "^5.0.0"
  }
}
```

### 8.3 Flutter (Cross-Platform)

**Dependencies:**
```yaml
dependencies:
  flutter:
    sdk: flutter
  fl_chart: ^0.66.0
  dio: ^5.4.0
  riverpod: ^2.4.0
  sqflite: ^2.3.0
  intl: ^0.18.0
  flutter_secure_storage: ^9.0.0
```

---

## 9. Implementation Phases

### Phase 1: Core Infrastructure (1-2 weeks)
- [ ] Project setup & architecture
- [ ] API client implementation (KIS or alternative)
- [ ] Local database setup (caching)
- [ ] Data models definition

### Phase 2: Oscillator Calculator (1 week)
- [ ] Supply Ratio calculation
- [ ] EMA implementation
- [ ] MACD calculation
- [ ] Signal score algorithm
- [ ] Unit tests for calculations

### Phase 3: Basic UI (1-2 weeks)
- [ ] Search component with autocomplete
- [ ] Stock info card
- [ ] Date range selector
- [ ] Basic chart view (Market Cap Oscillator)
- [ ] Signal dashboard

### Phase 4: Advanced Charts (1-2 weeks)
- [ ] DeMark TD Setup chart
- [ ] Trend Signal chart
- [ ] Elder Impulse chart
- [ ] Standard MACD chart
- [ ] Interval switching (d/w/m)

### Phase 5: Polish & Optimization (1 week)
- [ ] Chart interactions (tooltip, zoom)
- [ ] Search history
- [ ] Favorites/Watchlist
- [ ] Dark mode support
- [ ] Performance optimization
- [ ] Error handling & edge cases

### Phase 6: Testing & Release (1 week)
- [ ] Unit tests
- [ ] Integration tests
- [ ] UI tests
- [ ] Performance testing
- [ ] Beta testing
- [ ] Release

---

## 10. API Key Management

### 10.1 Security Requirements
- **Never** hardcode API keys in source code
- Use encrypted storage (Android Keystore, iOS Keychain)
- Support user-provided API keys
- Validate API keys on input

### 10.2 Settings Screen

```
┌─────────────────────────────────────────────────────────────┐
│                    API 설정                                  │
├─────────────────────────────────────────────────────────────┤
│  KIS API 설정                                               │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ App Key:     [••••••••••••••••]     [테스트] [저장]  │ │
│  │ App Secret:  [••••••••••••••••]                       │ │
│  │ 계좌번호:    [________-__]                            │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  상태: ✅ 연결됨 / ❌ 미설정 / ⚠️ 만료됨                   │
│                                                             │
│  ⓘ KIS 개발자센터에서 API 키를 발급받으세요.              │
│     https://apiportal.koreainvestment.com                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 11. Error Handling

### 11.1 Error Types

| Error Type | Description | User Message |
|------------|-------------|--------------|
| `NetworkError` | 네트워크 연결 실패 | "인터넷 연결을 확인해주세요" |
| `ApiKeyError` | API 키 미설정/만료 | "API 설정을 확인해주세요" |
| `RateLimitError` | API 호출 한도 초과 | "잠시 후 다시 시도해주세요" |
| `DataParsingError` | 데이터 파싱 실패 | "데이터 처리 중 오류가 발생했습니다" |
| `StockNotFoundError` | 종목 검색 실패 | "종목을 찾을 수 없습니다" |
| `CacheExpiredError` | 캐시 만료 | (자동으로 새 데이터 요청) |

### 11.2 Retry Strategy

```kotlin
suspend fun <T> withRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(maxRetries - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return block() // Last attempt
}
```

---

## 12. Testing Strategy

### 12.1 Unit Tests
- Oscillator calculation accuracy
- EMA calculation correctness
- Signal score algorithm
- Date filtering logic

### 12.2 Integration Tests
- API client with mock server
- Database CRUD operations
- Repository caching logic

### 12.3 UI Tests
- Search flow
- Chart navigation
- Date range selection
- Error state display

---

## 13. Performance Considerations

### 13.1 Data Volume
- Max 730 data points (2 years daily)
- Chart rendering optimization for large datasets
- Lazy loading for historical data

### 13.2 Memory Management
- Release chart resources on screen exit
- Use immutable data classes
- Avoid memory leaks in callbacks

### 13.3 Network Optimization
- Cache-first strategy (24-hour TTL)
- Request batching where possible
- Compression for API responses

---

## 14. Future Enhancements

### 14.1 Planned Features
- [ ] Push notifications for signal alerts
- [ ] Portfolio tracking (multiple stocks)
- [ ] Export to CSV/Excel
- [ ] Share chart images
- [ ] Widget support (Android/iOS)

### 14.2 Potential Integrations
- [ ] Telegram/Kakao alerts
- [ ] Trading system API integration
- [ ] AI-powered signal interpretation

---

## Appendix A: Reference Implementation

### A.1 OscillatorCalculator.kt

```kotlin
object OscillatorCalculator {

    fun calculate(stockData: StockData): OscillatorResult {
        val n = stockData.dates.size
        if (n < 26) return OscillatorResult.empty()

        // Step 1: Supply Ratio
        val supplyRatio = FloatArray(n) { i ->
            val supply = stockData.foreign5d[i] + stockData.institution5d[i]
            supply.toFloat() / stockData.marketCap[i].toFloat()
        }

        // Step 2: EMAs
        val ema12 = calculateEMA(supplyRatio, 12)
        val ema26 = calculateEMA(supplyRatio, 26)

        // Step 3: MACD
        val macd = FloatArray(n) { i -> ema12[i] - ema26[i] }

        // Step 4: Signal
        val signal = calculateEMA(macd, 9)

        // Step 5: Oscillator (Histogram)
        val oscillator = FloatArray(n) { i -> macd[i] - signal[i] }

        return OscillatorResult(
            dates = stockData.dates,
            marketCap = stockData.marketCap.map { it.toFloat() },
            oscillator = oscillator.toList(),
            ema = ema12.toList(),
            macd = macd.toList(),
            signal = signal.toList(),
            histogram = oscillator.toList()
        )
    }

    private fun calculateEMA(values: FloatArray, period: Int): FloatArray {
        val ema = FloatArray(values.size)
        val alpha = 2.0f / (period + 1)

        ema[0] = values[0]
        for (i in 1 until values.size) {
            ema[i] = alpha * values[i] + (1 - alpha) * ema[i - 1]
        }

        return ema
    }

    fun analyzeSignal(result: OscillatorResult): SignalAnalysis {
        val n = result.oscillator.size
        if (n < 3) return SignalAnalysis.neutral()

        var score = 0

        // 1. Oscillator Value (±40)
        val latestOsc = result.oscillator.last()
        score += when {
            latestOsc > 0.005f -> 40
            latestOsc > 0.002f -> 20
            latestOsc < -0.005f -> -40
            latestOsc < -0.002f -> -20
            else -> 0
        }

        // 2. MACD Cross (±30)
        val macd = result.macd
        val signal = result.signal
        score += when {
            macd[n-1] > signal[n-1] && macd[n-2] <= signal[n-2] -> 30  // Golden
            macd[n-1] < signal[n-1] && macd[n-2] >= signal[n-2] -> -30 // Dead
            macd[n-1] > signal[n-1] -> 15  // Above
            else -> -15  // Below
        }

        // 3. Histogram Trend (±30)
        val recentHist = result.histogram.takeLast(3)
        score += when {
            recentHist.all { it > 0 } && isIncreasing(recentHist) -> 30
            recentHist.all { it < 0 } && isDecreasing(recentHist) -> -30
            else -> 0
        }

        val signalType = when {
            score >= 60 -> SignalType.STRONG_BUY
            score >= 20 -> SignalType.BUY
            score <= -60 -> SignalType.STRONG_SELL
            score <= -20 -> SignalType.SELL
            else -> SignalType.NEUTRAL
        }

        return SignalAnalysis(
            totalScore = score.coerceIn(-100, 100),
            signalType = signalType,
            oscillatorScore = /* calculated */,
            crossScore = /* calculated */,
            trendScore = /* calculated */,
            description = generateDescription(signalType)
        )
    }

    private fun isIncreasing(values: List<Float>): Boolean {
        for (i in 1 until values.size) {
            if (values[i] <= values[i-1]) return false
        }
        return true
    }

    private fun isDecreasing(values: List<Float>): Boolean {
        for (i in 1 until values.size) {
            if (values[i] >= values[i-1]) return false
        }
        return true
    }
}
```

---

## Appendix B: Sample API Responses

### B.1 KIS API - 투자자별 매매동향

```json
{
  "rt_cd": "0",
  "msg_cd": "MCA00000",
  "msg1": "정상처리되었습니다",
  "output": [
    {
      "stck_bsop_date": "20260115",
      "frgn_ntby_qty": "1500000",
      "frgn_ntby_tr_pbmn": "150000000000",
      "orgn_ntby_qty": "500000",
      "orgn_ntby_tr_pbmn": "50000000000"
    }
  ]
}
```

### B.2 Stock Analysis Response

```json
{
  "ticker": "005930",
  "name": "삼성전자",
  "dates": ["2026-01-13", "2026-01-14", "2026-01-15"],
  "marketCap": [350000000000000, 352000000000000, 355000000000000],
  "foreign5d": [500000000000, 450000000000, 600000000000],
  "institution5d": [200000000000, 180000000000, 250000000000]
}
```

---

**Document Version History:**

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-01-15 | Initial specification |

---

*End of Specification Document*
