# 추세 시그널 차트 (Trend Signal Chart) 기술 문서

## 개요

추세 시그널 차트는 종목 메뉴에서 개별 종목의 기술적 분석을 제공하는 차트입니다.
종가, 이동평균(MA), Fear & Greed 지수를 표시하고, 매수/매도 시그널을 마커로 표시합니다.

**관련 파일:**
- Python: `app/src/main/python/trend_signal.py`
- Kotlin Chart: `app/src/main/java/com/etfmonitor/core/ui/component/TechnicalCharts.kt`
- Data Model: `app/src/main/java/com/etfmonitor/core/analysis/model/StockData.kt`

---

## 1. 종가 라인 (Close Price Line)

### 1.1 계산 로직

종가(Close)는 pykrx 라이브러리를 통해 한국거래소(KRX)에서 직접 수집한 원시 데이터입니다.

```python
# trend_signal.py:112-117
df = stock.get_market_ohlcv(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"), ticker)
df = df.rename(columns={"시가": "O", "고가": "H", "저가": "L", "종가": "C", "거래량": "V"})
```

### 1.2 데이터 수집 기간

| 인터벌 | 기본 요청 기간 | 실제 수집 범위 | 비고 |
|--------|---------------|---------------|------|
| 일봉 (d) | 180일 | 180일 | 기본값 |
| 주봉 (w) | 180일 | 360일 (2배) | 주간 리샘플링 적용 |
| 월봉 (m) | 180일 | 540일 (3배) | 월간 리샘플링 적용 |

**리샘플링 로직:**
```python
# trend_signal.py:119-122
if interval == "w":
    df = df.resample("W").agg({"O": "first", "H": "max", "L": "min", "C": "last", "V": "sum"})
elif interval == "m":
    df = df.resample("ME").agg({"O": "first", "H": "max", "L": "min", "C": "last", "V": "sum"})
```

### 1.3 차트 표시

- **라인 스타일**: 실선 (Cubic Bezier 곡선)
- **두께**: 2.5px
- **Y축**: 왼쪽 축 (가격 축)
- **색상**: MACD 설정의 `lineColor1` 사용

```kotlin
// TechnicalCharts.kt:347-355
LineDataSet(closeEntries, "종가").apply {
    axisDependency = YAxis.AxisDependency.LEFT
    color = priceColor
    lineWidth = 2.5f
    setDrawCircles(false)
    mode = LineDataSet.Mode.CUBIC_BEZIER
}
```

---

## 2. MA 라인 (Moving Average Line)

### 2.1 계산 로직

단순 이동평균(SMA)을 사용합니다.

```python
# trend_signal.py:167
r["MA"] = r["C"].rolling(ma_period).mean()
```

**수식:**
```
MA(t) = (C(t) + C(t-1) + ... + C(t-n+1)) / n
```
- C(t): t시점의 종가
- n: MA 기간 (기본값 20)

### 2.2 데이터 수집 기간

| 파라미터 | 기본값 | 범위 |
|----------|--------|------|
| ma_period | 20 | 양의 정수 |
| 유효 데이터 시작점 | 20번째 데이터 | rolling window 적용 후 |

**참고:** MA 계산을 위해 최소 `ma_period`개의 데이터가 필요합니다.
처음 19개 데이터는 NaN이 되며, `dropna()` 처리됩니다.

```python
# trend_signal.py:219
r = _gen_signals(df, ma_period, cmf_period).dropna()
```

### 2.3 차트 표시

- **라인 스타일**: 점선 (Dashed Line)
- **두께**: 2.0px
- **Y축**: 왼쪽 축 (가격 축)
- **색상**: MACD 설정의 `lineColor2` 사용

```kotlin
// TechnicalCharts.kt:362-370
LineDataSet(maEntries, "MA").apply {
    axisDependency = YAxis.AxisDependency.LEFT
    color = maColor
    lineWidth = 2f
    enableDashedLine(10f, 5f, 0f)  // 점선: 10px 선, 5px 공백
}
```

---

## 3. Fear & Greed 라인 (Fear & Greed Index Line)

### 3.1 계산 로직

Fear & Greed 지수는 4개의 구성 요소를 가중 평균하여 -1.0 ~ +1.0 범위로 계산합니다.

```python
# trend_signal.py:139-161
def _calc_fg(df: pd.DataFrame, mom_period: int = 5, pos_period: int = 52) -> pd.Series:
    return mom * 0.45 + pos * 0.45 + vol_score * 0.05 + vol_spike * 0.05
```

#### 구성 요소 1: 모멘텀 (Momentum) - 가중치 45%

```python
# trend_signal.py:141-143
log_ret = np.log(df["C"] / df["C"].shift(mom_period))  # 5일 로그 수익률
mom = (log_ret / 0.1).clip(-1, 1)
```

- **계산**: 5일 로그 수익률을 0.1로 나누어 정규화
- **범위**: [-1, +1] (clip 적용)
- **의미**: 단기 가격 모멘텀

#### 구성 요소 2: 52주 범위 내 위치 (Position in Range) - 가중치 45%

```python
# trend_signal.py:145-149
hi = df["C"].rolling(pos_period, min_periods=10).max()  # 52주 최고가
lo = df["C"].rolling(pos_period, min_periods=10).min()  # 52주 최저가
rng = (hi - lo).replace(0, np.nan)
pos = ((df["C"] - lo) / rng * 2) - 1
```

- **계산**: 현재 종가가 52주 고저 범위 내 어느 위치에 있는지
- **범위**: [-1, +1]
- **의미**: +1 = 52주 최고가 근처, -1 = 52주 최저가 근처

#### 구성 요소 3: 거래량 급등 (Volume Spike) - 가중치 5%

```python
# trend_signal.py:151-153
vol_ma = df["V"].rolling(20, min_periods=5).mean()  # 20일 평균 거래량
vol_score = (df["V"] / vol_ma - 1).clip(-1, 1)
```

- **계산**: 현재 거래량 대비 20일 평균 거래량 비율
- **범위**: [-1, +1]
- **의미**: 양수 = 평균 이상 거래량 (관심 증가)

#### 구성 요소 4: 변동성 역전 (Volatility Reversal) - 가중치 5% (역전)

```python
# trend_signal.py:155-159
ret = df["C"].pct_change()
vol_recent = ret.rolling(5, min_periods=3).std()   # 최근 5일 변동성
vol_avg = ret.rolling(20, min_periods=10).std()    # 20일 평균 변동성
vol_spike = (vol_recent / vol_avg.replace(0, np.nan) - 1).clip(-1, 1) * -1  # 역전!
```

- **계산**: 최근 5일 변동성 vs 20일 평균 변동성
- **역전 적용**: 높은 변동성 → 공포 (음수), 낮은 변동성 → 탐욕 (양수)
- **범위**: [-1, +1]

### 3.2 데이터 수집 기간

| 구성 요소 | 필요 기간 | 최소 데이터 |
|-----------|----------|------------|
| 모멘텀 | 5일 | 5개 |
| 52주 위치 | 52주 (~260일) | 10개 (min_periods) |
| 거래량 급등 | 20일 | 5개 (min_periods) |
| 변동성 역전 | 20일 | 10개 (min_periods) |

**참고:** 52주 범위 계산을 위해 약 1년치 데이터가 필요하며, `min_periods=10`으로
최소 10개 데이터 이후부터 유효한 값이 생성됩니다.

### 3.3 차트 표시

- **라인 스타일**: 실선 (Cubic Bezier 곡선)
- **두께**: 1.5px
- **Y축**: 오른쪽 축 (범위: -1.2 ~ +1.2)
- **색상**: 보라색 (RGB: 156, 39, 176)

```kotlin
// TechnicalCharts.kt:373-385
LineDataSet(fearGreedEntries, "F&G").apply {
    axisDependency = YAxis.AxisDependency.RIGHT
    color = fearGreedColor  // 보라색
    lineWidth = 1.5f
    mode = LineDataSet.Mode.CUBIC_BEZIER
}
```

#### Y축 라벨 해석

| 값 범위 | 라벨 | 의미 |
|---------|------|------|
| > 0.6 | 탐욕 | 극단적 탐욕 |
| 0.2 ~ 0.6 | + | 탐욕 |
| -0.2 ~ 0.2 | 중립 | 중립 |
| -0.6 ~ -0.2 | - | 공포 |
| < -0.6 | 공포 | 극단적 공포 |

---

## 4. 매수/매도 시그널 마커

### 4.1 시그널 생성 로직

시그널은 3가지 조건의 조합으로 생성됩니다.

```python
# trend_signal.py:164-188
def _gen_signals(df: pd.DataFrame, ma_period: int, cmf_period: int) -> pd.DataFrame:
```

#### 4.1.1 Chaikin Money Flow (CMF) 계산

```python
# trend_signal.py:131-136
def _calc_cmf(df: pd.DataFrame, period: int = 4) -> pd.Series:
    hl = (df["H"] - df["L"]).replace(0, np.nan)
    mfm = ((df["C"] - df["L"]) - (df["H"] - df["C"])) / hl  # Money Flow Multiplier
    mfv = mfm * df["V"]  # Money Flow Volume
    return mfv.rolling(period).sum() / df["V"].rolling(period).sum()
```

**CMF 수식:**
```
MFM = [(C - L) - (H - C)] / (H - L)
MFV = MFM × Volume
CMF = Σ(MFV, period) / Σ(Volume, period)
```

- **CMF > 0**: 자금 유입 (매수 압력)
- **CMF < 0**: 자금 유출 (매도 압력)
- **기본 기간**: 4일

### 4.2 매수 시그널 조건

#### 강한 매수 (Buy) - 3개 조건 모두 충족

```python
# trend_signal.py:173-177
b1 = r["H"] > r["PH"]  # 고가 돌파: 당일 고가 > 전일 고가
b2 = r["C"] > r["MA"]  # MA 위: 종가 > 이동평균
b3 = r["CMF"] > 0      # 자금 유입: CMF 양수

r["Buy"] = (b_cnt == 3).astype(int)  # 3개 조건 모두 충족
```

| 조건 | 설명 | 의미 |
|------|------|------|
| b1 | 당일 고가 > 전일 고가 | 가격 돌파 |
| b2 | 종가 > MA | 상승 추세 |
| b3 | CMF > 0 | 자금 유입 |

#### 보조 매수 (AuxBuy) - 2개 조건 충족 (반드시 MA 조건 포함)

```python
# trend_signal.py:186
r["AuxBuy"] = ((b_cnt == 2) & b2).astype(int)  # 2개 조건 + 반드시 MA 위
```

- 3개 중 2개 조건 충족
- **필수 조건**: 종가 > MA (b2)

### 4.3 매도 시그널 조건

#### 강한 매도 (Sell) - 3개 조건 모두 충족

```python
# trend_signal.py:179-187
s1 = r["L"] < r["PL"]  # 저가 하향돌파: 당일 저가 < 전일 저가
s2 = r["C"] < r["MA"]  # MA 아래: 종가 < 이동평균
s3 = r["CMF"] < 0      # 자금 유출: CMF 음수

r["Sell"] = (s_cnt == 3).astype(int)  # 3개 조건 모두 충족
```

| 조건 | 설명 | 의미 |
|------|------|------|
| s1 | 당일 저가 < 전일 저가 | 가격 붕괴 |
| s2 | 종가 < MA | 하락 추세 |
| s3 | CMF < 0 | 자금 유출 |

#### 보조 매도 (AuxSell) - 2개 조건 충족 (반드시 MA 조건 포함)

```python
# trend_signal.py:188
r["AuxSell"] = ((s_cnt == 2) & s2).astype(int)  # 2개 조건 + 반드시 MA 아래
```

- 3개 중 2개 조건 충족
- **필수 조건**: 종가 < MA (s2)

### 4.4 차트 마커 표시

#### 마커 스타일

| 시그널 | 색상 | 모양 | 크기 | 의미 |
|--------|------|------|------|------|
| 매수 | 빨간색 (244, 67, 54) | ▲ 삼각형 | 24px | 강한 매수 신호 |
| 보조매수 | 연한 빨간색 (255, 138, 128) | ▲ 삼각형 | 18px | 약한 매수 신호 |
| 매도 | 파란색 (33, 150, 243) | ▼ 역삼각형 | 24px | 강한 매도 신호 |
| 보조매도 | 연한 파란색 (130, 177, 255) | ▼ 역삼각형 | 18px | 약한 매도 신호 |

**참고:** 한국 주식시장 관례에 따라 매수는 빨간색, 매도는 파란색으로 표시합니다.

#### 마커 위치

모든 시그널 마커는 **해당 시점의 종가(Close) 위치**에 표시됩니다.

```kotlin
// TechnicalCharts.kt:397-422
data.buySignal.forEachIndexed { index, signal ->
    if (signal == 1) {
        buyEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
    }
}
```

---

## 5. 파라미터 요약

| 파라미터 | 기본값 | 범위 | 설명 |
|----------|--------|------|------|
| days | 180 | 1-3650 | 분석 기간 (일) |
| interval | "w" | "d", "w", "m" | 일봉/주봉/월봉 |
| ma_period | 20 | 양의 정수 | 이동평균 기간 |
| cmf_period | 4 | 양의 정수 | CMF 계산 기간 |

---

## 6. API 호출 예시

### Python

```python
from trend_signal import get_trend_signal_analysis

# 삼성전자 주봉 180일 분석
result = get_trend_signal_analysis(
    ticker="005930",
    days=180,
    interval="w",
    ma_period=20,
    cmf_period=4
)
```

### Kotlin (OscillatorPyClient)

```kotlin
val trendSignalData = pyClient.getTrendSignalData(
    ticker = "005930",
    days = 365,
    interval = "w"
)
```

---

## 7. 반환 데이터 구조

```json
{
    "ticker": "005930",
    "name": "삼성전자",
    "interval": "w",
    "dates": ["2024-01-01", "2024-01-08", ...],
    "open": [71000, 72000, ...],
    "high": [73000, 74000, ...],
    "low": [70000, 71000, ...],
    "close": [72000, 73000, ...],
    "volume": [10000000, 12000000, ...],
    "ma": [71500, 71800, ...],
    "cmf": [0.15, 0.08, ...],
    "fear_greed": [0.35, 0.42, ...],
    "buy_signal": [0, 0, 1, 0, ...],
    "aux_buy_signal": [0, 1, 0, 0, ...],
    "sell_signal": [0, 0, 0, 1, ...],
    "aux_sell_signal": [1, 0, 0, 0, ...]
}
```

---

## 8. 데이터 흐름

```
┌─────────────────────────────────────────────────────────────────────┐
│  1. Python (trend_signal.py)                                        │
│     get_trend_signal_analysis()                                     │
│     ├─ _get_ohlcv() → pykrx 데이터 수집                              │
│     ├─ _gen_signals()                                               │
│     │   ├─ MA 계산 (rolling mean)                                   │
│     │   ├─ _calc_cmf() → CMF 계산                                   │
│     │   └─ _calc_fg() → Fear & Greed 계산                           │
│     └─ JSON 반환                                                    │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────────┐
│  2. Kotlin (OscillatorPyClient.kt)                                  │
│     getTrendSignalData()                                            │
│     └─ JSON → TrendSignalData 변환                                  │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────────┐
│  3. Kotlin (OscillatorViewModel.kt)                                 │
│     searchAndAnalyze()                                              │
│     └─ TrendSignalCalculator.analyze()                              │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────────┐
│  4. Compose UI (TechnicalCharts.kt)                                 │
│     TrendSignalChart()                                              │
│     ├─ 종가 라인 (LineDataSet)                                       │
│     ├─ MA 라인 (LineDataSet, dashed)                                │
│     ├─ Fear & Greed 라인 (LineDataSet, right axis)                  │
│     └─ 매수/매도 마커 (ScatterDataSet)                               │
└─────────────────────────────────────────────────────────────────────┘
```

---

**문서 버전:** 1.0
**최종 수정일:** 2026-01-22
**작성자:** Claude Code
