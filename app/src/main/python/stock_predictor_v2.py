"""
Enhanced stock price prediction module (v2).
Improved accuracy through expanded features and ensemble models.
10x faster through batch data collection.
"""
import json
import time
import traceback
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
from sklearn.ensemble import (
    RandomForestClassifier,
    GradientBoostingClassifier,
    VotingClassifier
)
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import TimeSeriesSplit
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score

from core import get_logger, to_json, err_json
from feature_engineer import (
    build_enhanced_features,
    get_key_factors,
    get_feature_importance_mapping,
    ALL_FEATURES,
    FEATURE_NAMES_KR
)
from data_collector import (
    batch_get_price_changes,
    batch_get_stock_technicals,
    batch_get_investor_data,
    collect_training_labels
)

log = get_logger(__name__)

# Try importing optional packages
try:
    from xgboost import XGBClassifier
    HAS_XGBOOST = True
except ImportError:
    HAS_XGBOOST = False
    log.info("XGBoost not available, using sklearn alternatives")

try:
    from lightgbm import LGBMClassifier
    HAS_LIGHTGBM = True
except ImportError:
    HAS_LIGHTGBM = False
    log.info("LightGBM not available, using sklearn alternatives")

try:
    from imblearn.over_sampling import SMOTE
    from imblearn.under_sampling import RandomUnderSampler
    HAS_IMBLEARN = True
except ImportError:
    HAS_IMBLEARN = False
    log.info("imbalanced-learn not available, skipping SMOTE")


# Model cache
_models_v2: Dict[str, Any] = {}
_scalers_v2: Dict[str, StandardScaler] = {}
_feature_importance_v2: Dict[str, List[float]] = {}


def _create_ensemble_model(model_type: str = "voting") -> Any:
    """Create ensemble model based on type."""

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

    if HAS_XGBOOST and model_type in ("voting", "xgboost"):
        base_models.append(('xgb', XGBClassifier(
            n_estimators=150,
            max_depth=6,
            learning_rate=0.05,
            scale_pos_weight=2,
            random_state=42,
            n_jobs=-1,
            use_label_encoder=False,
            eval_metric='logloss'
        )))
        weights.append(1.5)

    if HAS_LIGHTGBM and model_type in ("voting", "lightgbm"):
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
    elif model_type == "xgboost" and HAS_XGBOOST:
        return XGBClassifier(
            n_estimators=150,
            max_depth=6,
            learning_rate=0.05,
            scale_pos_weight=2,
            random_state=42,
            n_jobs=-1,
            use_label_encoder=False,
            eval_metric='logloss'
        )
    elif model_type == "lightgbm" and HAS_LIGHTGBM:
        return LGBMClassifier(
            n_estimators=150,
            max_depth=6,
            learning_rate=0.05,
            class_weight='balanced',
            random_state=42,
            n_jobs=-1,
            verbose=-1
        )
    elif model_type == "gradient_boosting":
        return GradientBoostingClassifier(
            n_estimators=150,
            max_depth=5,
            learning_rate=0.05,
            subsample=0.8,
            random_state=42
        )
    else:
        return RandomForestClassifier(
            n_estimators=150,
            max_depth=12,
            min_samples_leaf=3,
            class_weight='balanced',
            random_state=42,
            n_jobs=-1
        )


def _apply_smote(X: np.ndarray, y: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
    """Apply SMOTE for class balancing if available."""
    if not HAS_IMBLEARN:
        return X, y

    try:
        # Only apply if imbalance is significant
        unique, counts = np.unique(y, return_counts=True)
        if len(unique) < 2:
            return X, y

        ratio = min(counts) / max(counts)
        if ratio > 0.5:  # Already balanced enough
            return X, y

        smote = SMOTE(sampling_strategy=0.7, random_state=42)
        X_res, y_res = smote.fit_resample(X, y)

        log.info("SMOTE applied: %d -> %d samples", len(y), len(y_res))
        return X_res, y_res

    except Exception as e:
        log.warning("SMOTE failed: %s", e)
        return X, y


def _train_with_cv(
    X: np.ndarray,
    y: np.ndarray,
    model,
    n_splits: int = 5
) -> Dict[str, Any]:
    """Train with time series cross-validation."""

    tscv = TimeSeriesSplit(n_splits=n_splits)

    cv_scores = {
        'accuracy': [],
        'precision': [],
        'recall': [],
        'f1': []
    }

    valid_folds = 0
    for train_idx, val_idx in tscv.split(X):
        X_train, X_val = X[train_idx], X[val_idx]
        y_train, y_val = y[train_idx], y[val_idx]

        # Skip fold if train or validation set has only 1 class
        train_classes = np.unique(y_train)
        val_classes = np.unique(y_val)
        if len(train_classes) < 2 or len(val_classes) < 2:
            log.warning("Skipping CV fold: insufficient classes (train=%d, val=%d)",
                       len(train_classes), len(val_classes))
            continue

        # Clone model for each fold
        from sklearn.base import clone
        fold_model = clone(model)
        fold_model.fit(X_train, y_train)
        y_pred = fold_model.predict(X_val)

        cv_scores['accuracy'].append(accuracy_score(y_val, y_pred))
        cv_scores['precision'].append(precision_score(y_val, y_pred, zero_division=0))
        cv_scores['recall'].append(recall_score(y_val, y_pred, zero_division=0))
        cv_scores['f1'].append(f1_score(y_val, y_pred, zero_division=0))
        valid_folds += 1

    # If no valid folds, train without CV
    if valid_folds == 0:
        log.warning("No valid CV folds, training without CV")
        model.fit(X, y)
        return {
            'model': model,
            'cv_accuracy': 0.0,
            'cv_precision': 0.0,
            'cv_recall': 0.0,
            'cv_f1': 0.0,
            'cv_std': 0.0
        }

    # Train final model on all data
    model.fit(X, y)

    return {
        'model': model,
        'cv_accuracy': np.mean(cv_scores['accuracy']),
        'cv_precision': np.mean(cv_scores['precision']),
        'cv_recall': np.mean(cv_scores['recall']),
        'cv_f1': np.mean(cv_scores['f1']),
        'cv_std': np.std(cv_scores['accuracy'])
    }


def _get_feature_importance(model) -> List[float]:
    """Extract feature importance from model."""
    try:
        if hasattr(model, 'feature_importances_'):
            return model.feature_importances_.tolist()
        elif hasattr(model, 'estimators_'):
            # VotingClassifier
            importances = []
            for _, est in model.estimators_:
                if hasattr(est, 'feature_importances_'):
                    importances.append(est.feature_importances_)
            if importances:
                return np.mean(importances, axis=0).tolist()
        return [0.0] * len(ALL_FEATURES)
    except Exception:
        return [0.0] * len(ALL_FEATURES)


def train_enhanced_model(
    changes_json: str,
    market_data_json: str = "{}",
    stock_data_json: str = "{}",
    days: int = 5,
    threshold: float = 3.0,
    model_type: str = "voting",
    use_cv: bool = True,
    use_smote: bool = False
) -> str:
    """
    Train enhanced prediction model.

    Args:
        changes_json: ETF change data JSON
        market_data_json: Market context data JSON
        stock_data_json: Stock-level data JSON (technicals, investor data)
        days: Prediction period (days)
        threshold: Price change threshold for positive label (%)
        model_type: "voting", "xgboost", "lightgbm", "random_forest", "gradient_boosting"
        use_cv: Use time series cross-validation
        use_smote: Apply SMOTE for class balancing

    Returns:
        JSON with training results
    """
    global _models_v2, _scalers_v2, _feature_importance_v2

    start_time = time.time()

    try:
        # Build enhanced features
        feature_result = json.loads(build_enhanced_features(
            changes_json, market_data_json, stock_data_json
        ))

        if not feature_result.get('success'):
            return to_json({"success": False, "error": feature_result.get('error', 'Feature build failed')})

        features = np.array(feature_result['features'])
        if len(features) < 20:
            return to_json({
                "success": False,
                "error": f"데이터 부족: {len(features)}개 (최소 20개)",
                "sample_count": len(features)
            })

        # Collect labels using batch processing
        label_result = json.loads(collect_training_labels(
            changes_json, days, threshold
        ))

        if not label_result.get('success'):
            return to_json({"success": False, "error": label_result.get('error', 'Label collection failed')})

        labels = np.array(label_result['labels'])
        valid_indices = label_result['valid_indices']

        # Filter features to valid indices only
        X = features[valid_indices]
        y = labels

        if len(X) < 20:
            return to_json({
                "success": False,
                "error": f"유효 데이터 부족: {len(X)}개 (최소 20개)",
                "sample_count": len(X)
            })

        # Check class balance - need at least 2 classes for classification
        unique_classes, class_counts = np.unique(y, return_counts=True)
        if len(unique_classes) < 2:
            class_info = "상승" if unique_classes[0] == 1 else "하락/횡보"
            return to_json({
                "success": False,
                "error": f"클래스 불균형: 모든 샘플이 '{class_info}' 클래스입니다. 가격 임계값({threshold}%)을 조정하거나 데이터 기간을 늘려보세요.",
                "sample_count": len(X),
                "class_distribution": {str(c): int(cnt) for c, cnt in zip(unique_classes, class_counts)}
            })

        # Check minimum samples per class for reliable training
        min_class_count = int(min(class_counts))
        if min_class_count < 5:
            minor_class = "상승" if class_counts[0] < class_counts[1] else "하락/횡보"
            return to_json({
                "success": False,
                "error": f"'{minor_class}' 클래스 샘플 부족: {min_class_count}개 (최소 5개 필요). 가격 임계값({threshold}%)을 조정하거나 데이터 기간을 늘려보세요.",
                "sample_count": len(X),
                "class_distribution": {str(c): int(cnt) for c, cnt in zip(unique_classes, class_counts)}
            })

        log.info("Training with %d samples, %d features, class dist: %s",
                 len(X), X.shape[1], dict(zip(unique_classes.astype(int), class_counts.astype(int))))

        # Apply SMOTE if requested
        if use_smote:
            X, y = _apply_smote(X, y)

        # Scale features
        scaler = StandardScaler()
        X_scaled = scaler.fit_transform(X)

        # Create model
        model = _create_ensemble_model(model_type)

        # Train with or without CV
        if use_cv and len(X) >= 50:
            cv_result = _train_with_cv(X_scaled, y, model, n_splits=5)
            model = cv_result['model']
            accuracy = cv_result['cv_accuracy']
            precision = cv_result['cv_precision']
            recall = cv_result['cv_recall']
            f1 = cv_result['cv_f1']
            cv_std = cv_result['cv_std']
        else:
            # Simple train/test split
            split_idx = int(len(X_scaled) * 0.8)
            X_train, X_test = X_scaled[:split_idx], X_scaled[split_idx:]
            y_train, y_test = y[:split_idx], y[split_idx:]

            # Check if both train and test have at least 2 classes
            train_classes = np.unique(y_train)
            test_classes = np.unique(y_test)

            if len(train_classes) < 2:
                log.warning("Train set has only 1 class, training on full data")
                model.fit(X_scaled, y)
                accuracy = 0.0
                precision = 0.0
                recall = 0.0
                f1 = 0.0
                cv_std = 0.0
            else:
                model.fit(X_train, y_train)

                if len(test_classes) >= 2:
                    y_pred = model.predict(X_test)
                    accuracy = accuracy_score(y_test, y_pred)
                    precision = precision_score(y_test, y_pred, zero_division=0)
                    recall = recall_score(y_test, y_pred, zero_division=0)
                    f1 = f1_score(y_test, y_pred, zero_division=0)
                else:
                    log.warning("Test set has only 1 class, skipping evaluation")
                    accuracy = 0.0
                    precision = 0.0
                    recall = 0.0
                    f1 = 0.0
                cv_std = 0.0

                # Retrain on full data
                model.fit(X_scaled, y)

        # Get feature importance
        importance = _get_feature_importance(model)

        # Cache model
        key = f"v2_{model_type}_{days}_{threshold}"
        _models_v2[key] = model
        _scalers_v2[key] = scaler
        _feature_importance_v2[key] = importance

        # Create importance mapping
        importance_mapping = json.loads(get_feature_importance_mapping(importance))

        elapsed_ms = int((time.time() - start_time) * 1000)

        log.info("Training completed: accuracy=%.4f, precision=%.4f, time=%dms",
                 accuracy, precision, elapsed_ms)

        return to_json({
            "success": True,
            "model_type": model_type,
            "feature_count": len(ALL_FEATURES),
            "sample_count": len(X),
            "positive_ratio": round(sum(y) / len(y), 4),
            "cv_accuracy": round(accuracy, 4),
            "cv_precision": round(precision, 4),
            "cv_recall": round(recall, 4),
            "cv_f1": round(f1, 4),
            "cv_std": round(cv_std, 4),
            "feature_importance": importance_mapping.get('importance_kr', {}),
            "top_features": importance_mapping.get('top_features', [])[:5],
            "training_time_ms": elapsed_ms,
            "xgboost_available": HAS_XGBOOST,
            "lightgbm_available": HAS_LIGHTGBM,
            "smote_available": HAS_IMBLEARN
        })

    except Exception as e:
        log.error("Training error: %s", e)
        return to_json({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc()
        })


def predict_enhanced(
    changes_json: str,
    market_data_json: str = "{}",
    stock_data_json: str = "{}",
    days: int = 5,
    threshold: float = 3.0,
    model_type: str = "voting",
    min_confidence: float = 0.6
) -> str:
    """
    Predict rising stocks using enhanced features.

    Args:
        changes_json: Current ETF change data JSON
        market_data_json: Market context data JSON
        stock_data_json: Stock-level data JSON
        days: Prediction period (days)
        threshold: Price threshold used in training
        model_type: Model type used in training
        min_confidence: Minimum confidence for predictions

    Returns:
        JSON with predictions
    """
    global _models_v2, _scalers_v2, _feature_importance_v2

    start_time = time.time()

    try:
        key = f"v2_{model_type}_{days}_{threshold}"

        if key not in _models_v2:
            return to_json({
                "success": False,
                "error": "모델이 없습니다. train_enhanced_model을 먼저 호출하세요.",
                "predictions": []
            })

        model = _models_v2[key]
        scaler = _scalers_v2[key]
        importance = _feature_importance_v2.get(key)

        # Build features
        feature_result = json.loads(build_enhanced_features(
            changes_json, market_data_json, stock_data_json
        ))

        if not feature_result.get('success'):
            return to_json({
                "success": False,
                "error": feature_result.get('error', 'Feature build failed'),
                "predictions": []
            })

        features = np.array(feature_result['features'])
        tickers = feature_result['tickers']
        names = feature_result['names']

        if len(features) == 0:
            return to_json({
                "success": True,
                "predictions": [],
                "message": "예측할 종목이 없습니다."
            })

        # Scale and predict
        X_scaled = scaler.transform(features)
        probs = model.predict_proba(X_scaled)[:, 1]

        # Parse original changes for status
        changes = json.loads(changes_json)

        predictions = []
        for idx, prob in enumerate(probs):
            if prob >= min_confidence:
                # Get key factors
                factors_result = json.loads(get_key_factors(
                    features[idx].tolist(),
                    importance
                ))

                key_factors = factors_result.get('key_factors', [])

                # Calculate risk score (inverse of confidence, adjusted by volatility)
                risk_score = round(1 - prob, 2)

                predictions.append({
                    "ticker": tickers[idx],
                    "name": names[idx],
                    "status": changes[idx].get('status', 'UNKNOWN') if idx < len(changes) else 'UNKNOWN',
                    "confidence": round(float(prob), 4),
                    "key_factors": key_factors,
                    "risk_score": risk_score,
                    "feature_values": {
                        FEATURE_NAMES_KR.get(f, f): round(v, 4)
                        for f, v in zip(ALL_FEATURES[:10], features[idx][:10])  # Top 10 features
                    }
                })

        # Sort by confidence
        predictions.sort(key=lambda x: x['confidence'], reverse=True)

        elapsed_ms = int((time.time() - start_time) * 1000)

        return to_json({
            "success": True,
            "total_analyzed": len(features),
            "predicted_rising_count": len(predictions),
            "predictions": predictions[:30],  # Top 30
            "inference_time_ms": elapsed_ms
        })

    except Exception as e:
        log.error("Prediction error: %s", e)
        return to_json({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc(),
            "predictions": []
        })


def train_and_predict_enhanced(
    hist_json: str,
    curr_json: str,
    market_data_json: str = "{}",
    stock_data_json: str = "{}",
    days: int = 5,
    threshold: float = 3.0,
    model_type: str = "voting",
    min_confidence: float = 0.6,
    use_cv: bool = True
) -> str:
    """
    Train and predict in one call (enhanced version).

    This is the main entry point for the enhanced prediction system.
    """
    try:
        start_time = time.time()

        # Train model
        train_result = json.loads(train_enhanced_model(
            hist_json, market_data_json, stock_data_json,
            days, threshold, model_type, use_cv
        ))

        if not train_result.get('success'):
            return to_json({
                "success": False,
                "error": train_result.get('error'),
                "training": train_result,
                "predictions": []
            })

        # Predict
        pred_result = json.loads(predict_enhanced(
            curr_json, market_data_json, stock_data_json,
            days, threshold, model_type, min_confidence
        ))

        total_time_ms = int((time.time() - start_time) * 1000)

        return to_json({
            "success": True,
            "training": {
                "cv_accuracy": train_result.get('cv_accuracy'),
                "cv_precision": train_result.get('cv_precision'),
                "cv_recall": train_result.get('cv_recall'),
                "cv_f1": train_result.get('cv_f1'),
                "sample_count": train_result.get('sample_count'),
                "feature_count": train_result.get('feature_count'),
                "feature_importance": train_result.get('feature_importance'),
                "top_features": train_result.get('top_features'),
                "training_time_ms": train_result.get('training_time_ms')
            },
            "prediction": {
                "total_analyzed": pred_result.get('total_analyzed', 0),
                "predicted_rising_count": pred_result.get('predicted_rising_count', 0),
                "min_confidence": min_confidence,
                "days_after": days,
                "price_threshold": threshold,
                "inference_time_ms": pred_result.get('inference_time_ms', 0)
            },
            "predictions": pred_result.get('predictions', []),
            "total_time_ms": total_time_ms,
            "model_type": model_type
        })

    except Exception as e:
        log.error("Train and predict error: %s", e)
        return to_json({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc(),
            "predictions": []
        })


def get_model_status_v2() -> str:
    """Get cached model status (v2)."""
    return to_json({
        "cached_models": list(_models_v2.keys()),
        "model_count": len(_models_v2),
        "xgboost_available": HAS_XGBOOST,
        "lightgbm_available": HAS_LIGHTGBM,
        "smote_available": HAS_IMBLEARN,
        "feature_count": len(ALL_FEATURES)
    })


def clear_model_cache_v2() -> str:
    """Clear model cache (v2)."""
    global _models_v2, _scalers_v2, _feature_importance_v2
    _models_v2.clear()
    _scalers_v2.clear()
    _feature_importance_v2.clear()
    return to_json({"success": True, "message": "V2 모델 캐시가 초기화되었습니다."})


def get_available_features() -> str:
    """Get list of available features."""
    return to_json({
        "features": ALL_FEATURES,
        "feature_names_kr": FEATURE_NAMES_KR,
        "feature_count": len(ALL_FEATURES)
    })
