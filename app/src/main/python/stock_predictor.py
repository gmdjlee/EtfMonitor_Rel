#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ETF 구성 변화 기반 주가 상승 예측 모듈
- 신규/제외/비중증감 데이터와 이후 주가 변동을 학습
- 향후 주가 상승 가능성이 높은 종목 예측
"""

import json
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
import pandas as pd
import numpy as np
from pykrx import stock

# scikit-learn imports
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, precision_score, recall_score

# 전역 모델 저장소 (메모리 캐시)
_model_cache: Dict[str, any] = {}
_scaler_cache: Dict[str, StandardScaler] = {}


def get_stock_price_change(ticker: str, start_date: str, days_after: int = 5) -> Optional[float]:
    """
    특정 날짜 이후 N일간의 주가 변화율 계산

    Args:
        ticker: 종목 코드
        start_date: 시작 날짜 (YYYY-MM-DD 형식)
        days_after: 이후 확인할 일수

    Returns:
        주가 변화율 (%) 또는 None
    """
    try:
        # 날짜 형식 변환
        start_dt = datetime.strptime(start_date, "%Y-%m-%d")
        end_dt = start_dt + timedelta(days=days_after + 10)  # 영업일 고려

        start_str = start_dt.strftime("%Y%m%d")
        end_str = end_dt.strftime("%Y%m%d")

        # 주가 데이터 조회
        df = stock.get_market_ohlcv(start_str, end_str, ticker)

        if df is None or len(df) < 2:
            return None

        # 시작가와 N일 후 종가 비교
        start_price = df.iloc[0]['종가']

        # days_after 영업일 후 또는 가능한 마지막 날짜
        target_idx = min(days_after, len(df) - 1)
        end_price = df.iloc[target_idx]['종가']

        if start_price <= 0:
            return None

        change_rate = ((end_price - start_price) / start_price) * 100
        return round(change_rate, 2)

    except Exception as e:
        print(f"Error getting price change for {ticker}: {e}")
        return None


def create_training_features(stock_changes: List[Dict]) -> pd.DataFrame:
    """
    학습용 피처 생성

    Args:
        stock_changes: 종목 변화 데이터 리스트
        [{
            "ticker": "005930",
            "name": "삼성전자",
            "status": "NEW/INCREASED/DECREASED/REMOVED",
            "weight_change": 0.5,  # 비중 변화 (%)
            "etf_count": 3,        # 포함된 ETF 수
            "total_amount": 1000000000,  # 총 편입 금액
            "date": "2025-01-01"
        }, ...]

    Returns:
        피처 DataFrame
    """
    features = []

    for item in stock_changes:
        feature = {
            'is_new': 1 if item.get('status') == 'NEW' else 0,
            'is_increased': 1 if item.get('status') == 'INCREASED' else 0,
            'is_decreased': 1 if item.get('status') == 'DECREASED' else 0,
            'is_removed': 1 if item.get('status') == 'REMOVED' else 0,
            'weight_change': item.get('weight_change', 0),
            'etf_count': item.get('etf_count', 1),
            'amount_billion': item.get('total_amount', 0) / 1_000_000_000,  # 10억 단위
            'ticker': item.get('ticker', ''),
            'name': item.get('name', ''),
            'date': item.get('date', '')
        }
        features.append(feature)

    return pd.DataFrame(features)


def collect_training_data(
    stock_changes_json: str,
    days_after: int = 5,
    price_threshold: float = 3.0
) -> Tuple[np.ndarray, np.ndarray, List[str]]:
    """
    학습 데이터 수집 (ETF 변화 → 주가 변화 라벨링)

    Args:
        stock_changes_json: 종목 변화 데이터 JSON 문자열
        days_after: 주가 변화 확인 기간
        price_threshold: 상승 판단 기준 (%)

    Returns:
        (features, labels, tickers) 튜플
    """
    stock_changes = json.loads(stock_changes_json)

    if not stock_changes:
        return np.array([]), np.array([]), []

    df = create_training_features(stock_changes)

    # 주가 변화 라벨 수집
    labels = []
    valid_indices = []
    tickers = []

    for idx, row in df.iterrows():
        ticker = row['ticker']
        date = row['date']

        if not ticker or not date:
            continue

        price_change = get_stock_price_change(ticker, date, days_after)

        if price_change is not None:
            # 상승: 1, 보합/하락: 0
            label = 1 if price_change >= price_threshold else 0
            labels.append(label)
            valid_indices.append(idx)
            tickers.append(ticker)

    if not valid_indices:
        return np.array([]), np.array([]), []

    # 피처 추출 (ticker, name, date 제외)
    feature_cols = ['is_new', 'is_increased', 'is_decreased', 'is_removed',
                    'weight_change', 'etf_count', 'amount_billion']
    X = df.loc[valid_indices, feature_cols].values
    y = np.array(labels)

    return X, y, tickers


def train_model(
    stock_changes_json: str,
    days_after: int = 5,
    price_threshold: float = 3.0,
    model_type: str = "random_forest"
) -> str:
    """
    예측 모델 학습

    Args:
        stock_changes_json: 학습용 종목 변화 데이터 JSON
        days_after: 주가 변화 확인 기간
        price_threshold: 상승 판단 기준 (%)
        model_type: "random_forest" 또는 "gradient_boosting"

    Returns:
        학습 결과 JSON (정확도, 정밀도, 재현율 등)
    """
    global _model_cache, _scaler_cache

    try:
        X, y, tickers = collect_training_data(
            stock_changes_json, days_after, price_threshold
        )

        if len(X) < 20:
            return json.dumps({
                "success": False,
                "error": f"학습 데이터 부족: {len(X)}개 (최소 20개 필요)",
                "sample_count": len(X)
            }, ensure_ascii=False)

        # 데이터 분할
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=0.2, random_state=42, stratify=y if len(set(y)) > 1 else None
        )

        # 스케일링
        scaler = StandardScaler()
        X_train_scaled = scaler.fit_transform(X_train)
        X_test_scaled = scaler.transform(X_test)

        # 모델 생성 및 학습
        if model_type == "gradient_boosting":
            model = GradientBoostingClassifier(
                n_estimators=100,
                max_depth=5,
                random_state=42
            )
        else:
            model = RandomForestClassifier(
                n_estimators=100,
                max_depth=10,
                random_state=42,
                class_weight='balanced'
            )

        model.fit(X_train_scaled, y_train)

        # 평가
        y_pred = model.predict(X_test_scaled)
        accuracy = accuracy_score(y_test, y_pred)
        precision = precision_score(y_test, y_pred, zero_division=0)
        recall = recall_score(y_test, y_pred, zero_division=0)

        # 피처 중요도
        feature_names = ['신규편입', '비중증가', '비중감소', '제외',
                        '비중변화율', 'ETF수', '편입금액']
        feature_importance = dict(zip(
            feature_names,
            model.feature_importances_.tolist()
        ))

        # 모델 캐시 저장
        cache_key = f"{model_type}_{days_after}_{price_threshold}"
        _model_cache[cache_key] = model
        _scaler_cache[cache_key] = scaler

        return json.dumps({
            "success": True,
            "model_type": model_type,
            "sample_count": len(X),
            "train_count": len(X_train),
            "test_count": len(X_test),
            "accuracy": round(accuracy, 4),
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "positive_ratio": round(sum(y) / len(y), 4),
            "feature_importance": feature_importance,
            "days_after": days_after,
            "price_threshold": price_threshold
        }, ensure_ascii=False)

    except Exception as e:
        import traceback
        return json.dumps({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc()
        }, ensure_ascii=False)


def predict_rising_stocks(
    current_changes_json: str,
    days_after: int = 5,
    price_threshold: float = 3.0,
    model_type: str = "random_forest",
    min_confidence: float = 0.6
) -> str:
    """
    현재 ETF 변화 데이터로 상승 예상 종목 예측

    Args:
        current_changes_json: 현재 종목 변화 데이터 JSON
        days_after: 예측 기간
        price_threshold: 상승 판단 기준
        model_type: 사용할 모델 타입
        min_confidence: 최소 신뢰도 (확률)

    Returns:
        예측 결과 JSON
    """
    global _model_cache, _scaler_cache

    try:
        cache_key = f"{model_type}_{days_after}_{price_threshold}"

        if cache_key not in _model_cache:
            return json.dumps({
                "success": False,
                "error": "학습된 모델이 없습니다. 먼저 train_model을 호출하세요.",
                "predictions": []
            }, ensure_ascii=False)

        model = _model_cache[cache_key]
        scaler = _scaler_cache[cache_key]

        current_changes = json.loads(current_changes_json)

        if not current_changes:
            return json.dumps({
                "success": True,
                "predictions": [],
                "message": "예측할 종목이 없습니다."
            }, ensure_ascii=False)

        df = create_training_features(current_changes)

        # 피처 추출
        feature_cols = ['is_new', 'is_increased', 'is_decreased', 'is_removed',
                        'weight_change', 'etf_count', 'amount_billion']
        X = df[feature_cols].values

        # 스케일링
        X_scaled = scaler.transform(X)

        # 예측 (확률)
        probabilities = model.predict_proba(X_scaled)[:, 1]  # 상승 확률

        # 결과 생성
        predictions = []
        for idx, prob in enumerate(probabilities):
            if prob >= min_confidence:
                predictions.append({
                    "ticker": df.iloc[idx]['ticker'],
                    "name": df.iloc[idx]['name'],
                    "status": current_changes[idx].get('status', 'UNKNOWN'),
                    "confidence": round(float(prob), 4),
                    "weight_change": df.iloc[idx]['weight_change'],
                    "etf_count": int(df.iloc[idx]['etf_count']),
                    "amount_billion": round(df.iloc[idx]['amount_billion'], 2)
                })

        # 신뢰도 순 정렬
        predictions.sort(key=lambda x: x['confidence'], reverse=True)

        return json.dumps({
            "success": True,
            "total_analyzed": len(current_changes),
            "predicted_rising_count": len(predictions),
            "min_confidence": min_confidence,
            "predictions": predictions[:30]  # 상위 30개만 반환
        }, ensure_ascii=False)

    except Exception as e:
        import traceback
        return json.dumps({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc(),
            "predictions": []
        }, ensure_ascii=False)


def train_and_predict(
    historical_changes_json: str,
    current_changes_json: str,
    days_after: int = 5,
    price_threshold: float = 3.0,
    model_type: str = "random_forest",
    min_confidence: float = 0.6
) -> str:
    """
    학습과 예측을 한번에 수행

    Args:
        historical_changes_json: 과거 종목 변화 데이터 (학습용)
        current_changes_json: 현재 종목 변화 데이터 (예측용)
        days_after: 예측 기간
        price_threshold: 상승 판단 기준 (%)
        model_type: 모델 타입
        min_confidence: 최소 신뢰도

    Returns:
        학습 및 예측 결과 JSON
    """
    try:
        # 1. 모델 학습
        train_result_json = train_model(
            historical_changes_json,
            days_after,
            price_threshold,
            model_type
        )
        train_result = json.loads(train_result_json)

        if not train_result.get('success'):
            return json.dumps({
                "success": False,
                "error": train_result.get('error', '모델 학습 실패'),
                "training": train_result,
                "predictions": []
            }, ensure_ascii=False)

        # 2. 예측 수행
        predict_result_json = predict_rising_stocks(
            current_changes_json,
            days_after,
            price_threshold,
            model_type,
            min_confidence
        )
        predict_result = json.loads(predict_result_json)

        return json.dumps({
            "success": True,
            "training": {
                "accuracy": train_result.get('accuracy'),
                "precision": train_result.get('precision'),
                "recall": train_result.get('recall'),
                "sample_count": train_result.get('sample_count'),
                "feature_importance": train_result.get('feature_importance')
            },
            "prediction": {
                "total_analyzed": predict_result.get('total_analyzed', 0),
                "predicted_rising_count": predict_result.get('predicted_rising_count', 0),
                "min_confidence": min_confidence,
                "days_after": days_after,
                "price_threshold": price_threshold
            },
            "predictions": predict_result.get('predictions', [])
        }, ensure_ascii=False)

    except Exception as e:
        import traceback
        return json.dumps({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc(),
            "predictions": []
        }, ensure_ascii=False)


def get_model_status() -> str:
    """
    현재 캐시된 모델 상태 확인

    Returns:
        모델 상태 JSON
    """
    global _model_cache

    return json.dumps({
        "cached_models": list(_model_cache.keys()),
        "model_count": len(_model_cache)
    }, ensure_ascii=False)


def clear_model_cache() -> str:
    """
    모델 캐시 초기화

    Returns:
        결과 JSON
    """
    global _model_cache, _scaler_cache

    _model_cache.clear()
    _scaler_cache.clear()

    return json.dumps({
        "success": True,
        "message": "모델 캐시가 초기화되었습니다."
    }, ensure_ascii=False)


# 테스트 코드
if __name__ == "__main__":
    print("=== Stock Predictor Test ===")

    # 테스트 데이터
    test_historical = json.dumps([
        {"ticker": "005930", "name": "삼성전자", "status": "INCREASED",
         "weight_change": 0.5, "etf_count": 10, "total_amount": 5000000000000,
         "date": "2025-01-01"},
        {"ticker": "000660", "name": "SK하이닉스", "status": "NEW",
         "weight_change": 1.0, "etf_count": 5, "total_amount": 2000000000000,
         "date": "2025-01-01"},
    ])

    test_current = json.dumps([
        {"ticker": "035420", "name": "NAVER", "status": "INCREASED",
         "weight_change": 0.3, "etf_count": 8, "total_amount": 1000000000000,
         "date": "2025-01-15"},
    ])

    print("\n1. Model Status:")
    print(get_model_status())

    print("\n2. Training (would need real data):")
    # result = train_and_predict(test_historical, test_current)
    # print(result)

    print("\nTest completed!")
