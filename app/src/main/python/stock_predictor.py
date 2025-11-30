"""
Stock price prediction module based on ETF composition changes.
Uses ML to predict rising stocks based on ETF weight changes.
"""
import json
import traceback
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
import pandas as pd
from pykrx import stock
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, precision_score, recall_score

from core import get_logger, to_json, err_json

log = get_logger(__name__)

# Model cache (single-threaded Android Chaquopy environment)
_models: Dict[str, Any] = {}
_scalers: Dict[str, StandardScaler] = {}

FEATURE_COLS = ['is_new', 'is_increased', 'is_decreased', 'is_removed',
                'weight_change', 'etf_count', 'amount_billion']
FEATURE_NAMES = ['신규편입', '비중증가', '비중감소', '제외', '비중변화율', 'ETF수', '편입금액']


def _get_price_change(ticker: str, date: str, days: int = 5) -> Optional[float]:
    """Get price change rate after N days."""
    try:
        start = datetime.strptime(date, "%Y-%m-%d")
        end = start + timedelta(days=days + 10)

        df = stock.get_market_ohlcv(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"), ticker)
        if df is None or len(df) < 2:
            return None

        p0 = df.iloc[0]['종가']
        p1 = df.iloc[min(days, len(df) - 1)]['종가']

        if p0 <= 0:
            return None
        return round(((p1 - p0) / p0) * 100, 2)

    except Exception:
        return None


def _build_features(changes: List[Dict]) -> pd.DataFrame:
    """Build feature DataFrame from stock changes."""
    rows = []
    for c in changes:
        status = c.get('status', '')
        rows.append({
            'is_new': int(status == 'NEW'),
            'is_increased': int(status == 'INCREASED'),
            'is_decreased': int(status == 'DECREASED'),
            'is_removed': int(status == 'REMOVED'),
            'weight_change': c.get('weight_change', 0),
            'etf_count': c.get('etf_count', 1),
            'amount_billion': c.get('total_amount', 0) / 1e9,
            'ticker': c.get('ticker', ''),
            'name': c.get('name', ''),
            'date': c.get('date', '')
        })
    return pd.DataFrame(rows)


def _collect_data(changes_json: str, days: int, threshold: float) -> Tuple[np.ndarray, np.ndarray, List[str]]:
    """Collect training data with labels."""
    changes = json.loads(changes_json)
    if not changes:
        return np.array([]), np.array([]), []

    df = _build_features(changes)
    labels, valid_idx, tickers = [], [], []

    for idx, row in df.iterrows():
        if not row['ticker'] or not row['date']:
            continue

        change = _get_price_change(row['ticker'], row['date'], days)
        if change is not None:
            labels.append(1 if change >= threshold else 0)
            valid_idx.append(idx)
            tickers.append(row['ticker'])

    if not valid_idx:
        return np.array([]), np.array([]), []

    return df.loc[valid_idx, FEATURE_COLS].values, np.array(labels), tickers


def train_model(changes_json: str, days: int = 5, threshold: float = 3.0,
                model_type: str = "random_forest") -> str:
    """
    Train prediction model.

    Returns: JSON with training results
    """
    global _models, _scalers

    try:
        X, y, _ = _collect_data(changes_json, days, threshold)

        if len(X) < 20:
            return to_json({"success": False, "error": f"데이터 부족: {len(X)}개 (최소 20개)", "sample_count": len(X)})

        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=0.2, random_state=42,
            stratify=y if len(set(y)) > 1 else None
        )

        scaler = StandardScaler()
        X_train_s = scaler.fit_transform(X_train)
        X_test_s = scaler.transform(X_test)

        if model_type == "gradient_boosting":
            model = GradientBoostingClassifier(n_estimators=100, max_depth=5, random_state=42)
        else:
            model = RandomForestClassifier(n_estimators=100, max_depth=10, random_state=42, class_weight='balanced')

        model.fit(X_train_s, y_train)

        y_pred = model.predict(X_test_s)
        acc = accuracy_score(y_test, y_pred)
        prec = precision_score(y_test, y_pred, zero_division=0)
        rec = recall_score(y_test, y_pred, zero_division=0)

        key = f"{model_type}_{days}_{threshold}"
        _models[key] = model
        _scalers[key] = scaler

        return to_json({
            "success": True,
            "model_type": model_type,
            "sample_count": len(X),
            "train_count": len(X_train),
            "test_count": len(X_test),
            "accuracy": round(acc, 4),
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "positive_ratio": round(sum(y) / len(y), 4),
            "feature_importance": dict(zip(FEATURE_NAMES, model.feature_importances_.tolist())),
            "days_after": days,
            "price_threshold": threshold
        })

    except Exception as e:
        return to_json({"success": False, "error": str(e), "traceback": traceback.format_exc()})


def predict_rising_stocks(changes_json: str, days: int = 5, threshold: float = 3.0,
                          model_type: str = "random_forest", min_conf: float = 0.6) -> str:
    """
    Predict rising stocks from current changes.

    Returns: JSON with predictions
    """
    global _models, _scalers

    try:
        key = f"{model_type}_{days}_{threshold}"

        if key not in _models:
            return to_json({"success": False, "error": "모델이 없습니다. train_model을 먼저 호출하세요.", "predictions": []})

        changes = json.loads(changes_json)
        if not changes:
            return to_json({"success": True, "predictions": [], "message": "예측할 종목이 없습니다."})

        df = _build_features(changes)
        X = df[FEATURE_COLS].values
        X_s = _scalers[key].transform(X)
        probs = _models[key].predict_proba(X_s)[:, 1]

        preds = []
        for idx, prob in enumerate(probs):
            if prob >= min_conf:
                preds.append({
                    "ticker": df.iloc[idx]['ticker'],
                    "name": df.iloc[idx]['name'],
                    "status": changes[idx].get('status', 'UNKNOWN'),
                    "confidence": round(float(prob), 4),
                    "weight_change": df.iloc[idx]['weight_change'],
                    "etf_count": int(df.iloc[idx]['etf_count']),
                    "amount_billion": round(df.iloc[idx]['amount_billion'], 2)
                })

        preds.sort(key=lambda x: x['confidence'], reverse=True)

        return to_json({
            "success": True,
            "total_analyzed": len(changes),
            "predicted_rising_count": len(preds),
            "min_confidence": min_conf,
            "predictions": preds[:30]
        })

    except Exception as e:
        return to_json({"success": False, "error": str(e), "traceback": traceback.format_exc(), "predictions": []})


def train_and_predict(hist_json: str, curr_json: str, days: int = 5, threshold: float = 3.0,
                      model_type: str = "random_forest", min_conf: float = 0.6) -> str:
    """Train and predict in one call."""
    try:
        train_res = json.loads(train_model(hist_json, days, threshold, model_type))

        if not train_res.get('success'):
            return to_json({"success": False, "error": train_res.get('error'), "training": train_res, "predictions": []})

        pred_res = json.loads(predict_rising_stocks(curr_json, days, threshold, model_type, min_conf))

        return to_json({
            "success": True,
            "training": {k: train_res.get(k) for k in ['accuracy', 'precision', 'recall', 'sample_count', 'feature_importance']},
            "prediction": {
                "total_analyzed": pred_res.get('total_analyzed', 0),
                "predicted_rising_count": pred_res.get('predicted_rising_count', 0),
                "min_confidence": min_conf,
                "days_after": days,
                "price_threshold": threshold
            },
            "predictions": pred_res.get('predictions', [])
        })

    except Exception as e:
        return to_json({"success": False, "error": str(e), "traceback": traceback.format_exc(), "predictions": []})


def get_model_status() -> str:
    """Get cached model status."""
    return to_json({"cached_models": list(_models.keys()), "model_count": len(_models)})


def clear_model_cache() -> str:
    """Clear model cache."""
    global _models, _scalers
    _models.clear()
    _scalers.clear()
    return to_json({"success": True, "message": "모델 캐시가 초기화되었습니다."})
