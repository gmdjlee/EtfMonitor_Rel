# ML 주가 예측 성능 및 속도 향상 개발 명세서

## 문서 정보
- **버전**: 1.0
- **작성일**: 2025-12-07
- **대상 시스템**: EtfMonitor Android App
- **목표**: ML 예측 정확도 +15~25%, 실행 속도 10배 향상

---

## 1. 현재 시스템 분석

### 1.1 현재 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    StockPredictionRepository                │
│  collectHistoricalChanges() → StockChangeData List          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    StockPredictorPyClient                   │
│  trainAndPredict() → Python 호출 (120초 타임아웃)            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    stock_predictor.py                       │
│  _build_features() → 7개 Feature                            │
│  _get_price_change() → 개별 종목 API 호출 (병목!)            │
│  train_model() → RandomForest/GradientBoosting              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 현재 문제점

| 영역 | 문제 | 영향 |
|------|------|------|
| **속도** | `_get_price_change()` 개별 API 호출 | 100종목 = 100회 API 호출, ~60초 |
| **속도** | 학습 시 레이블 수집이 순차적 | 전체 120초 중 90% 소요 |
| **성능** | Feature 7개 (ETF 변화만) | 시장 컨텍스트 부재 |
| **성능** | 단일 모델 (RF/GB) | 앙상블 효과 없음 |
| **성능** | 랜덤 train/test split | 시계열 특성 무시 |

### 1.3 현재 Feature 구성

```python
# 현재: 7개 Feature
FEATURE_COLS = [
    'is_new',           # 신규 편입 여부 (binary)
    'is_increased',     # 비중 증가 여부 (binary)
    'is_decreased',     # 비중 감소 여부 (binary)
    'is_removed',       # 제외 여부 (binary)
    'weight_change',    # 비중 변화율 (%)
    'etf_count',        # 포함 ETF 수
    'amount_billion'    # 편입 금액 (10억)
]
```

---

## 2. 개선 목표

### 2.1 정량적 목표

| 지표 | 현재 | 목표 | 개선율 |
|------|------|------|--------|
| **실행 시간** | ~120초 | ~10-15초 | **8-10배** |
| **정확도 (Accuracy)** | ~65% | ~80% | **+15%p** |
| **정밀도 (Precision)** | ~60% | ~75% | **+15%p** |
| **Feature 수** | 7개 | 25-30개 | **4배** |

### 2.2 정성적 목표

- 앱 내 기존 데이터 소스 최대 활용
- Chaquopy 호환성 유지
- 기존 API 인터페이스 하위 호환
- Snapdragon NPU 활용 준비 (옵션)

---

## 3. 속도 향상 설계

### 3.1 데이터 수집 최적화

#### 3.1.1 배치 가격 데이터 수집

**현재 문제:**
```python
# 현재: N개 종목 = N회 API 호출
for ticker in tickers:
    price_change = _get_price_change(ticker, date)  # 개별 호출
```

**개선안:**
```python
# 개선: 1회 배치 호출
def _batch_get_price_changes(tickers: List[str], date: str, days: int) -> Dict[str, float]:
    """배치로 여러 종목의 가격 변화율 수집"""
    start = datetime.strptime(date, "%Y-%m-%d")
    end = start + timedelta(days=days + 10)

    results = {}

    # 방법 1: 전체 시장 OHLCV 한번에 조회 (pykrx 지원)
    try:
        # KOSPI + KOSDAQ 전체 종가 데이터
        df_start = stock.get_market_ohlcv_by_ticker(start.strftime("%Y%m%d"))
        df_end = stock.get_market_ohlcv_by_ticker(end.strftime("%Y%m%d"))

        for ticker in tickers:
            if ticker in df_start.index and ticker in df_end.index:
                p0 = df_start.loc[ticker, '종가']
                p1 = df_end.loc[ticker, '종가']
                if p0 > 0:
                    results[ticker] = round(((p1 - p0) / p0) * 100, 2)
    except Exception as e:
        log.error("Batch price fetch error: %s", e)

    return results
```

**예상 효과:** API 호출 100회 → 2회, 시간 90초 → 5초

#### 3.1.2 가격 데이터 캐싱 (Room DB 활용)

**새 Entity:**
```kotlin
// database/entities/PriceCache.kt
@Entity(
    tableName = "price_cache",
    primaryKeys = ["ticker", "date"]
)
data class PriceCache(
    val ticker: String,
    val date: String,
    val closePrice: Double,
    val priceChange5d: Double?,  // 5일 후 변화율
    val priceChange10d: Double?, // 10일 후 변화율
    val updatedAt: Long = System.currentTimeMillis()
)
```

**DAO:**
```kotlin
@Dao
interface PriceCacheDao {
    @Query("SELECT * FROM price_cache WHERE ticker IN (:tickers) AND date = :date")
    suspend fun getPrices(tickers: List<String>, date: String): List<PriceCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<PriceCache>)

    @Query("DELETE FROM price_cache WHERE updatedAt < :cutoff")
    suspend fun deleteOldCache(cutoff: Long)
}
```

#### 3.1.3 병렬 처리

```python
from concurrent.futures import ThreadPoolExecutor, as_completed
import time

def _parallel_collect_labels(df: pd.DataFrame, days: int, threshold: float) -> Tuple[np.ndarray, List[int]]:
    """병렬로 레이블 수집"""
    tickers = df['ticker'].tolist()
    dates = df['date'].tolist()

    # 먼저 배치 조회 시도
    unique_dates = list(set(dates))
    batch_results = {}

    for date in unique_dates:
        batch_results[date] = _batch_get_price_changes(tickers, date, days)

    # 배치에서 누락된 종목만 개별 조회 (병렬)
    missing = []
    for idx, (ticker, date) in enumerate(zip(tickers, dates)):
        if ticker not in batch_results.get(date, {}):
            missing.append((idx, ticker, date))

    if missing:
        with ThreadPoolExecutor(max_workers=10) as executor:
            futures = {
                executor.submit(_get_price_change, t, d, days): (i, t, d)
                for i, t, d in missing
            }
            for future in as_completed(futures):
                idx, ticker, date = futures[future]
                try:
                    result = future.result()
                    if date not in batch_results:
                        batch_results[date] = {}
                    batch_results[date][ticker] = result
                except Exception:
                    pass

    # 결과 조합
    labels = []
    valid_indices = []
    for idx, (ticker, date) in enumerate(zip(tickers, dates)):
        change = batch_results.get(date, {}).get(ticker)
        if change is not None:
            labels.append(1 if change >= threshold else 0)
            valid_indices.append(idx)

    return np.array(labels), valid_indices
```

### 3.2 모델 캐싱 최적화

```python
# 모델 직렬화/역직렬화
import joblib
import os

MODEL_CACHE_DIR = "/data/user/0/com.etfmonitor/files/ml_models"

def save_model(model, scaler, key: str):
    """모델을 파일로 저장"""
    os.makedirs(MODEL_CACHE_DIR, exist_ok=True)
    joblib.dump(model, f"{MODEL_CACHE_DIR}/{key}_model.joblib")
    joblib.dump(scaler, f"{MODEL_CACHE_DIR}/{key}_scaler.joblib")

def load_model(key: str):
    """저장된 모델 로드"""
    model_path = f"{MODEL_CACHE_DIR}/{key}_model.joblib"
    scaler_path = f"{MODEL_CACHE_DIR}/{key}_scaler.joblib"

    if os.path.exists(model_path) and os.path.exists(scaler_path):
        return joblib.load(model_path), joblib.load(scaler_path)
    return None, None
```

---

## 4. 성능 향상 설계

### 4.1 Feature 확장

#### 4.1.1 새로운 Feature 구조

```python
# 확장된 Feature 구성 (7 → 28개)

FEATURE_GROUPS = {
    # === 기본 ETF Feature (기존 7개) ===
    'etf_basic': [
        'is_new',           # 신규 편입
        'is_increased',     # 비중 증가
        'is_decreased',     # 비중 감소
        'is_removed',       # 제외
        'weight_change',    # 비중 변화율
        'etf_count',        # ETF 수
        'amount_billion',   # 편입 금액
    ],

    # === 수급 Feature (신규 5개) ===
    'supply_demand': [
        'foreign_5d_net',       # 외국인 5일 순매수 (억원)
        'institution_5d_net',   # 기관 5일 순매수 (억원)
        'foreign_ratio',        # 외국인 순매수 / 시가총액
        'institution_ratio',    # 기관 순매수 / 시가총액
        'supply_score',         # 종합 수급 점수 (-1 ~ +1)
    ],

    # === 기술적 지표 (신규 6개) ===
    'technical': [
        'price_vs_ma20',    # 현재가 / 20일 이평
        'price_vs_ma60',    # 현재가 / 60일 이평
        'rsi_14',           # RSI(14)
        'macd_signal',      # MACD 시그널 (1: 골든크로스, -1: 데드크로스, 0: 중립)
        'volume_ratio',     # 거래량 / 20일 평균
        'volatility_20d',   # 20일 변동성
    ],

    # === 모멘텀 Feature (신규 4개) ===
    'momentum': [
        'return_5d',        # 5일 수익률
        'return_20d',       # 20일 수익률
        'return_60d',       # 60일 수익률
        'momentum_score',   # 종합 모멘텀 점수
    ],

    # === 시장 컨텍스트 (신규 4개) ===
    'market_context': [
        'market_oscillator',    # 시장 과매수/과매도 지수
        'fear_greed_index',     # Fear & Greed 지수
        'market_return_5d',     # 시장(KOSPI/KOSDAQ) 5일 수익률
        'market_type',          # 시장 구분 (KOSPI=1, KOSDAQ=0)
    ],

    # === 파생 Feature (신규 2개) ===
    'derived': [
        'log_market_cap',       # 로그 시가총액
        'weight_zscore',        # 비중변화 z-score
    ],
}

# 전체 Feature 리스트
ALL_FEATURES = []
for group in FEATURE_GROUPS.values():
    ALL_FEATURES.extend(group)

# Feature 이름 (한글)
FEATURE_NAMES_KR = {
    'is_new': '신규편입',
    'is_increased': '비중증가',
    'is_decreased': '비중감소',
    'is_removed': '제외',
    'weight_change': '비중변화율',
    'etf_count': 'ETF수',
    'amount_billion': '편입금액',
    'foreign_5d_net': '외국인5일순매수',
    'institution_5d_net': '기관5일순매수',
    'foreign_ratio': '외국인비율',
    'institution_ratio': '기관비율',
    'supply_score': '수급점수',
    'price_vs_ma20': '20일이평대비',
    'price_vs_ma60': '60일이평대비',
    'rsi_14': 'RSI',
    'macd_signal': 'MACD시그널',
    'volume_ratio': '거래량비율',
    'volatility_20d': '변동성',
    'return_5d': '5일수익률',
    'return_20d': '20일수익률',
    'return_60d': '60일수익률',
    'momentum_score': '모멘텀점수',
    'market_oscillator': '시장오실레이터',
    'fear_greed_index': '공포탐욕지수',
    'market_return_5d': '시장5일수익률',
    'market_type': '시장구분',
    'log_market_cap': '로그시가총액',
    'weight_zscore': '비중Z점수',
}
```

#### 4.1.2 Feature 수집 함수

```python
def _build_enhanced_features(
    changes: List[Dict],
    market_data: Optional[Dict] = None,
    stock_data: Optional[Dict] = None
) -> pd.DataFrame:
    """확장된 Feature DataFrame 생성"""
    rows = []

    for c in changes:
        ticker = c.get('ticker', '')
        status = c.get('status', '')

        row = {
            # 기본 ETF Feature
            'is_new': int(status == 'NEW'),
            'is_increased': int(status == 'INCREASED'),
            'is_decreased': int(status == 'DECREASED'),
            'is_removed': int(status == 'REMOVED'),
            'weight_change': c.get('weight_change', 0),
            'etf_count': c.get('etf_count', 1),
            'amount_billion': c.get('total_amount', 0) / 1e9,

            # 메타 정보 (feature 아님)
            'ticker': ticker,
            'name': c.get('name', ''),
            'date': c.get('date', ''),
        }

        # 수급 Feature (stock_data에서 가져옴)
        if stock_data and ticker in stock_data:
            sd = stock_data[ticker]
            row['foreign_5d_net'] = sd.get('foreign_5d', 0) / 1e8  # 억원
            row['institution_5d_net'] = sd.get('institution_5d', 0) / 1e8
            mcap = sd.get('market_cap', 1)
            row['foreign_ratio'] = sd.get('foreign_5d', 0) / mcap if mcap > 0 else 0
            row['institution_ratio'] = sd.get('institution_5d', 0) / mcap if mcap > 0 else 0
            row['supply_score'] = _calc_supply_score(row['foreign_ratio'], row['institution_ratio'])
            row['log_market_cap'] = np.log10(mcap) if mcap > 0 else 0
        else:
            row.update({
                'foreign_5d_net': 0, 'institution_5d_net': 0,
                'foreign_ratio': 0, 'institution_ratio': 0,
                'supply_score': 0, 'log_market_cap': 0
            })

        # 기술적 지표 (stock_data에서 가져옴)
        if stock_data and ticker in stock_data:
            sd = stock_data[ticker]
            row['price_vs_ma20'] = sd.get('price_vs_ma20', 1.0)
            row['price_vs_ma60'] = sd.get('price_vs_ma60', 1.0)
            row['rsi_14'] = sd.get('rsi', 50) / 100  # 0~1 정규화
            row['macd_signal'] = sd.get('macd_signal', 0)
            row['volume_ratio'] = sd.get('volume_ratio', 1.0)
            row['volatility_20d'] = sd.get('volatility', 0)
        else:
            row.update({
                'price_vs_ma20': 1.0, 'price_vs_ma60': 1.0,
                'rsi_14': 0.5, 'macd_signal': 0,
                'volume_ratio': 1.0, 'volatility_20d': 0
            })

        # 모멘텀 Feature
        if stock_data and ticker in stock_data:
            sd = stock_data[ticker]
            row['return_5d'] = sd.get('return_5d', 0)
            row['return_20d'] = sd.get('return_20d', 0)
            row['return_60d'] = sd.get('return_60d', 0)
            row['momentum_score'] = (row['return_5d'] * 0.5 +
                                      row['return_20d'] * 0.3 +
                                      row['return_60d'] * 0.2)
        else:
            row.update({
                'return_5d': 0, 'return_20d': 0, 'return_60d': 0, 'momentum_score': 0
            })

        # 시장 컨텍스트 Feature
        if market_data:
            row['market_oscillator'] = market_data.get('oscillator', 50) / 100
            row['fear_greed_index'] = market_data.get('fear_greed', 0.5)
            row['market_return_5d'] = market_data.get('market_return_5d', 0)
            # 시장 구분 추론
            row['market_type'] = 1 if _infer_market(ticker) == 'KOSPI' else 0
        else:
            row.update({
                'market_oscillator': 0.5, 'fear_greed_index': 0.5,
                'market_return_5d': 0, 'market_type': 1
            })

        # 파생 Feature
        row['weight_zscore'] = 0  # 나중에 전체 데이터로 계산

        rows.append(row)

    df = pd.DataFrame(rows)

    # z-score 계산 (전체 데이터 기준)
    if len(df) > 1 and 'weight_change' in df.columns:
        mean_wc = df['weight_change'].mean()
        std_wc = df['weight_change'].std()
        if std_wc > 0:
            df['weight_zscore'] = (df['weight_change'] - mean_wc) / std_wc

    return df


def _calc_supply_score(foreign_ratio: float, institution_ratio: float) -> float:
    """수급 점수 계산 (-1 ~ +1)"""
    combined = foreign_ratio * 0.6 + institution_ratio * 0.4
    return np.clip(combined * 100, -1, 1)


def _infer_market(ticker: str) -> str:
    """종목 코드로 시장 추론"""
    # 간단한 규칙: 6자리 숫자 중 첫 글자로 판단
    if ticker.startswith(('0', '1', '2', '3')):
        return 'KOSPI'
    return 'KOSDAQ'
```

### 4.2 모델 아키텍처 개선

#### 4.2.1 앙상블 모델 구조

```python
from sklearn.ensemble import (
    RandomForestClassifier,
    GradientBoostingClassifier,
    VotingClassifier
)

# XGBoost/LightGBM 추가 (Chaquopy 설치 필요)
try:
    from xgboost import XGBClassifier
    HAS_XGBOOST = True
except ImportError:
    HAS_XGBOOST = False

try:
    from lightgbm import LGBMClassifier
    HAS_LIGHTGBM = True
except ImportError:
    HAS_LIGHTGBM = False


def create_ensemble_model(model_type: str = "voting") -> Any:
    """앙상블 모델 생성"""

    base_models = [
        ('rf', RandomForestClassifier(
            n_estimators=150,
            max_depth=12,
            min_samples_leaf=3,
            class_weight='balanced',
            random_state=42,
            n_jobs=-1
        )),
        ('gb', GradientBoostingClassifier(
            n_estimators=150,
            max_depth=5,
            learning_rate=0.05,
            subsample=0.8,
            random_state=42
        ))
    ]

    weights = [1.0, 1.0]

    if HAS_XGBOOST:
        base_models.append(('xgb', XGBClassifier(
            n_estimators=150,
            max_depth=6,
            learning_rate=0.05,
            scale_pos_weight=2,
            random_state=42,
            n_jobs=-1
        )))
        weights.append(1.5)

    if HAS_LIGHTGBM:
        base_models.append(('lgbm', LGBMClassifier(
            n_estimators=150,
            max_depth=6,
            learning_rate=0.05,
            class_weight='balanced',
            random_state=42,
            n_jobs=-1,
            verbose=-1
        )))
        weights.append(1.5)

    if model_type == "voting":
        return VotingClassifier(
            estimators=base_models,
            voting='soft',
            weights=weights
        )
    elif model_type == "best_single":
        # XGBoost가 있으면 단일 최고 성능 모델 반환
        if HAS_XGBOOST:
            return base_models[2][1]  # XGBClassifier
        return base_models[0][1]  # RandomForest
    else:
        return base_models[0][1]  # RandomForest (기본)
```

#### 4.2.2 시계열 교차검증

```python
from sklearn.model_selection import TimeSeriesSplit

def train_with_cv(
    X: np.ndarray,
    y: np.ndarray,
    model,
    n_splits: int = 5
) -> Dict[str, Any]:
    """시계열 교차검증으로 학습"""

    tscv = TimeSeriesSplit(n_splits=n_splits)

    cv_scores = {
        'accuracy': [],
        'precision': [],
        'recall': [],
        'f1': []
    }

    for train_idx, val_idx in tscv.split(X):
        X_train, X_val = X[train_idx], X[val_idx]
        y_train, y_val = y[train_idx], y[val_idx]

        model.fit(X_train, y_train)
        y_pred = model.predict(X_val)

        cv_scores['accuracy'].append(accuracy_score(y_val, y_pred))
        cv_scores['precision'].append(precision_score(y_val, y_pred, zero_division=0))
        cv_scores['recall'].append(recall_score(y_val, y_pred, zero_division=0))
        cv_scores['f1'].append(f1_score(y_val, y_pred, zero_division=0))

    # 전체 데이터로 최종 학습
    model.fit(X, y)

    return {
        'model': model,
        'cv_accuracy': np.mean(cv_scores['accuracy']),
        'cv_precision': np.mean(cv_scores['precision']),
        'cv_recall': np.mean(cv_scores['recall']),
        'cv_f1': np.mean(cv_scores['f1']),
        'cv_std': np.std(cv_scores['accuracy'])
    }
```

### 4.3 클래스 불균형 처리

```python
from imblearn.over_sampling import SMOTE
from imblearn.under_sampling import RandomUnderSampler
from imblearn.pipeline import Pipeline as ImbPipeline

def create_balanced_pipeline(model) -> ImbPipeline:
    """SMOTE + 언더샘플링 파이프라인"""
    return ImbPipeline([
        ('oversample', SMOTE(sampling_strategy=0.5, random_state=42)),
        ('undersample', RandomUnderSampler(sampling_strategy=0.8, random_state=42)),
        ('model', model)
    ])
```

---

## 5. 파일 구조

### 5.1 Python 모듈 (신규/수정)

```
app/src/main/python/
├── stock_predictor.py          # 기존 (하위 호환 유지)
├── stock_predictor_v2.py       # 신규: 개선된 예측 모듈
├── feature_engineer.py         # 신규: Feature 엔지니어링
├── model_manager.py            # 신규: 모델 관리
└── data_collector.py           # 신규: 배치 데이터 수집
```

### 5.2 Kotlin 클래스 (신규/수정)

```
app/src/main/java/com/etfmonitor/
├── database/
│   ├── entities/
│   │   ├── PriceCache.kt              # 신규: 가격 캐시 Entity
│   │   └── EnhancedPrediction.kt      # 신규: 확장 예측 결과
│   ├── PriceCacheDao.kt               # 신규: 가격 캐시 DAO
│   └── AppDatabase.kt                 # 수정: 새 Entity 추가
├── python/
│   ├── StockPredictorPyClient.kt      # 기존 (하위 호환)
│   └── EnhancedPredictorClient.kt     # 신규: 개선된 클라이언트
└── repository/
    ├── StockPredictionRepository.kt   # 기존 (하위 호환)
    └── EnhancedPredictionRepository.kt # 신규: 개선된 Repository
```

---

## 6. API 명세

### 6.1 Python API (stock_predictor_v2.py)

#### 6.1.1 train_enhanced_model

```python
def train_enhanced_model(
    changes_json: str,
    market_data_json: str,
    stock_data_json: str,
    days: int = 5,
    threshold: float = 3.0,
    model_type: str = "voting",
    use_cv: bool = True
) -> str:
    """
    확장된 Feature로 모델 학습

    Args:
        changes_json: ETF 변화 데이터 JSON
        market_data_json: 시장 컨텍스트 데이터 JSON
        stock_data_json: 종목별 수급/기술 데이터 JSON
        days: 예측 기간 (일)
        threshold: 상승 판단 기준 (%)
        model_type: "voting", "xgboost", "lightgbm", "random_forest"
        use_cv: 시계열 교차검증 사용 여부

    Returns:
        JSON {
            "success": true,
            "model_type": "voting",
            "feature_count": 28,
            "sample_count": 500,
            "cv_accuracy": 0.78,
            "cv_precision": 0.72,
            "cv_recall": 0.68,
            "cv_f1": 0.70,
            "feature_importance": {...},
            "training_time_ms": 3500
        }
    """
```

#### 6.1.2 predict_enhanced

```python
def predict_enhanced(
    changes_json: str,
    market_data_json: str,
    stock_data_json: str,
    days: int = 5,
    threshold: float = 3.0,
    model_type: str = "voting",
    min_confidence: float = 0.6
) -> str:
    """
    확장된 Feature로 예측 수행

    Returns:
        JSON {
            "success": true,
            "total_analyzed": 120,
            "predicted_rising_count": 25,
            "predictions": [
                {
                    "ticker": "005930",
                    "name": "삼성전자",
                    "confidence": 0.85,
                    "status": "INCREASED",
                    "key_factors": ["외국인순매수", "RSI상승", "비중증가"],
                    "risk_score": 0.3
                },
                ...
            ],
            "inference_time_ms": 150
        }
    """
```

#### 6.1.3 batch_collect_prices

```python
def batch_collect_prices(
    tickers_json: str,
    start_date: str,
    end_date: str
) -> str:
    """
    배치로 가격 데이터 수집

    Returns:
        JSON {
            "success": true,
            "collected_count": 95,
            "failed_count": 5,
            "data": {
                "005930": {"2025-01-01": 58000, "2025-01-02": 58500, ...},
                ...
            },
            "collection_time_ms": 5000
        }
    """
```

### 6.2 Kotlin API (EnhancedPredictorClient.kt)

```kotlin
@Singleton
class EnhancedPredictorClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val marketOscillatorDao: MarketOscillatorDao,
    private val fearGreedDao: FearGreedDao,
    private val stockAnalysisDao: StockAnalysisDao
) {
    companion object {
        private const val TIMEOUT_MS = 30_000L  // 30초 (기존 120초에서 단축)
    }

    /**
     * 향상된 예측 실행
     */
    suspend fun trainAndPredict(
        changes: List<StockChangeData>,
        config: PredictionConfig = PredictionConfig()
    ): EnhancedPredictionResponse

    /**
     * 시장 컨텍스트 데이터 수집
     */
    private suspend fun collectMarketContext(date: String): MarketContextData

    /**
     * 종목별 수급/기술 데이터 수집
     */
    private suspend fun collectStockData(tickers: List<String>): Map<String, StockTechData>
}

data class PredictionConfig(
    val daysAfter: Int = 5,
    val priceThreshold: Double = 3.0,
    val minConfidence: Double = 0.6,
    val modelType: String = "voting",
    val useEnhancedFeatures: Boolean = true
)

data class EnhancedPredictionResponse(
    val success: Boolean,
    val predictions: List<EnhancedPrediction>,
    val trainingMetrics: TrainingMetrics?,
    val executionTimeMs: Long,
    val errorMessage: String? = null
)
```

---

## 7. 데이터베이스 마이그레이션

### 7.1 Migration 14 → 15

```kotlin
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 가격 캐시 테이블
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS price_cache (
                ticker TEXT NOT NULL,
                date TEXT NOT NULL,
                close_price REAL NOT NULL,
                price_change_5d REAL,
                price_change_10d REAL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (ticker, date)
            )
        """)

        // 인덱스
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_price_cache_date
            ON price_cache(date)
        """)

        // 확장 예측 결과 테이블
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS enhanced_predictions (
                id TEXT PRIMARY KEY NOT NULL,
                ticker TEXT NOT NULL,
                name TEXT NOT NULL,
                prediction_date TEXT NOT NULL,
                confidence REAL NOT NULL,
                status TEXT NOT NULL,
                key_factors TEXT NOT NULL,
                risk_score REAL NOT NULL,
                feature_values TEXT NOT NULL,
                model_type TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """)

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_enhanced_predictions_date
            ON enhanced_predictions(prediction_date DESC)
        """)
    }
}
```

---

## 8. Chaquopy 의존성 추가

### 8.1 build.gradle.kts 수정

```kotlin
chaquopy {
    defaultConfig {
        pip {
            // 기존
            install("pandas")
            install("pykrx")
            install("setuptools")
            install("wheel")
            install("requests")
            install("beautifulsoup4")
            install("scikit-learn")

            // 신규 추가
            install("xgboost")
            install("lightgbm")
            install("imbalanced-learn")
            install("joblib")
        }
    }
}
```

### 8.2 패키지 크기 영향

| 패키지 | 추가 크기 | 비고 |
|--------|----------|------|
| xgboost | ~15MB | 고성능 부스팅 |
| lightgbm | ~5MB | 경량 부스팅 |
| imbalanced-learn | ~3MB | SMOTE |
| joblib | ~1MB | 모델 직렬화 |
| **합계** | ~24MB | APK 증가량 |

---

## 9. 구현 일정

### 9.1 Phase 1: 속도 최적화 (1-2주)

| 작업 | 예상 소요 | 우선순위 |
|------|----------|----------|
| 배치 가격 데이터 수집 구현 | 3일 | P0 |
| 가격 캐시 DB 구현 | 2일 | P0 |
| 병렬 처리 적용 | 2일 | P1 |
| 모델 직렬화/캐싱 | 1일 | P1 |

**예상 성과:** 실행 시간 120초 → 15초

### 9.2 Phase 2: Feature 확장 (2-3주)

| 작업 | 예상 소요 | 우선순위 |
|------|----------|----------|
| feature_engineer.py 구현 | 3일 | P0 |
| 기존 데이터 소스 연동 | 3일 | P0 |
| EnhancedPredictorClient 구현 | 3일 | P0 |
| 테스트 및 검증 | 3일 | P0 |

**예상 성과:** Feature 7개 → 28개

### 9.3 Phase 3: 모델 개선 (1-2주)

| 작업 | 예상 소요 | 우선순위 |
|------|----------|----------|
| XGBoost/LightGBM 통합 | 2일 | P0 |
| 앙상블 모델 구현 | 2일 | P0 |
| 시계열 CV 적용 | 1일 | P1 |
| SMOTE 적용 | 1일 | P2 |

**예상 성과:** 정확도 +10~15%p

### 9.4 Phase 4: 통합 및 최적화 (1주)

| 작업 | 예상 소요 | 우선순위 |
|------|----------|----------|
| 기존 API 하위 호환 유지 | 2일 | P0 |
| UI 통합 | 2일 | P1 |
| 성능 프로파일링 및 최적화 | 1일 | P1 |

---

## 10. 테스트 계획

### 10.1 단위 테스트

```python
# test_stock_predictor_v2.py

def test_batch_price_collection():
    """배치 가격 수집 테스트"""
    tickers = ["005930", "000660", "035420"]
    result = batch_collect_prices(json.dumps(tickers), "20250101", "20250110")
    data = json.loads(result)
    assert data["success"]
    assert data["collected_count"] >= 2

def test_enhanced_features():
    """확장 Feature 생성 테스트"""
    changes = [{"ticker": "005930", "status": "NEW", "weight_change": 0.5}]
    df = _build_enhanced_features(changes)
    assert len(df.columns) >= 28

def test_ensemble_model():
    """앙상블 모델 테스트"""
    model = create_ensemble_model("voting")
    X = np.random.randn(100, 28)
    y = np.random.randint(0, 2, 100)
    model.fit(X, y)
    assert model.predict(X[:5]).shape[0] == 5
```

### 10.2 통합 테스트

```kotlin
@Test
fun testEnhancedPrediction() = runTest {
    val client = EnhancedPredictorClient(context, ...)
    val changes = listOf(StockChangeData(...))

    val result = client.trainAndPredict(changes)

    assertTrue(result.success)
    assertTrue(result.executionTimeMs < 30_000)
    assertTrue(result.predictions.isNotEmpty())
}
```

### 10.3 성능 벤치마크

| 테스트 케이스 | 기준 | 목표 |
|--------------|------|------|
| 100종목 학습+예측 | 120초 | <15초 |
| Feature 생성 (100종목) | N/A | <2초 |
| 모델 추론 (100종목) | N/A | <1초 |
| 메모리 사용량 | N/A | <200MB |

---

## 11. 롤백 계획

### 11.1 하위 호환성 유지

- 기존 `stock_predictor.py` 유지
- 기존 `StockPredictorPyClient.kt` 유지
- 설정에서 v1/v2 전환 가능

### 11.2 Feature Flag

```kotlin
object PredictionFeatureFlags {
    var useEnhancedPredictor: Boolean = true
    var useXGBoost: Boolean = true
    var useBatchCollection: Boolean = true
}
```

---

## 12. 모니터링

### 12.1 로깅

```python
# 성능 메트릭 로깅
log.info("Training completed: accuracy=%.4f, time=%dms, features=%d",
         accuracy, training_time, feature_count)

log.info("Prediction completed: count=%d, avg_confidence=%.4f, time=%dms",
         prediction_count, avg_confidence, inference_time)
```

### 12.2 성능 대시보드 (향후)

- 예측 정확도 추이
- 실행 시간 추이
- Feature 중요도 변화

---

## 13. 위험 요소 및 대응

| 위험 | 영향 | 대응 |
|------|------|------|
| XGBoost/LightGBM Chaquopy 호환 문제 | 모델 성능 저하 | scikit-learn 앙상블로 대체 |
| 배치 API 제한 | 속도 향상 제한 | 캐싱 강화로 보완 |
| APK 크기 증가 (~24MB) | 설치 거부 | 선택적 다운로드 |
| 메모리 부족 | 앱 크래시 | Feature 수 동적 조정 |

---

## 14. 참고 자료

- [XGBoost Documentation](https://xgboost.readthedocs.io/)
- [LightGBM Documentation](https://lightgbm.readthedocs.io/)
- [imbalanced-learn Documentation](https://imbalanced-learn.org/)
- [Feature Engineering for Stock Prediction](https://alphascientist.com/feature_engineering.html)
- [Time Series Cross-Validation](https://scikit-learn.org/stable/modules/cross_validation.html#time-series-split)

---

**문서 종료**
