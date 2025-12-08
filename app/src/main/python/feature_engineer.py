"""
Feature engineering module for ML prediction.
Expands features from 7 to 28 for improved prediction accuracy.
"""
import json
import traceback
from typing import Any, Dict, List, Optional

import numpy as np
import pandas as pd

from core import get_logger, to_json

log = get_logger(__name__)


# Feature groups definition
FEATURE_GROUPS = {
    # === Basic ETF Features (existing 7) ===
    'etf_basic': [
        'is_new',           # New inclusion
        'is_increased',     # Weight increased
        'is_decreased',     # Weight decreased
        'is_removed',       # Removed
        'weight_change',    # Weight change rate
        'etf_count',        # Number of ETFs
        'amount_billion',   # Inclusion amount
    ],

    # === Supply/Demand Features (new 5) ===
    'supply_demand': [
        'foreign_5d_net',       # Foreign 5-day net buy (100M KRW)
        'institution_5d_net',   # Institution 5-day net buy (100M KRW)
        'foreign_ratio',        # Foreign net buy / market cap
        'institution_ratio',    # Institution net buy / market cap
        'supply_score',         # Combined supply score (-1 ~ +1)
    ],

    # === Technical Indicators (new 6) ===
    'technical': [
        'price_vs_ma20',    # Current price / 20-day MA
        'price_vs_ma60',    # Current price / 60-day MA
        'rsi_14',           # RSI(14)
        'macd_signal',      # MACD signal (1: golden cross, -1: dead cross, 0: neutral)
        'volume_ratio',     # Volume / 20-day average
        'volatility_20d',   # 20-day volatility
    ],

    # === Momentum Features (new 4) ===
    'momentum': [
        'return_5d',        # 5-day return
        'return_20d',       # 20-day return
        'return_60d',       # 60-day return
        'momentum_score',   # Combined momentum score
    ],

    # === Market Context (new 4) ===
    'market_context': [
        'market_oscillator',    # Market overbought/oversold index
        'fear_greed_index',     # Fear & Greed index
        'market_return_5d',     # Market (KOSPI/KOSDAQ) 5-day return
        'market_type',          # Market type (KOSPI=1, KOSDAQ=0)
    ],

    # === Derived Features (new 2) ===
    'derived': [
        'log_market_cap',       # Log market cap
        'weight_zscore',        # Weight change z-score
    ],
}

# All features list
ALL_FEATURES = []
for group in FEATURE_GROUPS.values():
    ALL_FEATURES.extend(group)

# Feature names (Korean)
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


def _calc_supply_score(foreign_ratio: float, institution_ratio: float) -> float:
    """Calculate supply score (-1 ~ +1)."""
    combined = foreign_ratio * 0.6 + institution_ratio * 0.4
    return float(np.clip(combined * 100, -1, 1))


def _infer_market(ticker: str) -> str:
    """Infer market from ticker code."""
    if ticker.startswith(('0', '1', '2', '3')):
        return 'KOSPI'
    return 'KOSDAQ'


def _calc_momentum_score(r5: float, r20: float, r60: float) -> float:
    """Calculate combined momentum score."""
    return r5 * 0.5 + r20 * 0.3 + r60 * 0.2


def build_enhanced_features(
    changes_json: str,
    market_data_json: Optional[str] = None,
    stock_data_json: Optional[str] = None
) -> str:
    """
    Build enhanced feature DataFrame from stock changes.

    Args:
        changes_json: JSON array of stock change data
        market_data_json: JSON with market context (oscillator, fear_greed, etc.)
        stock_data_json: JSON with stock-level data (technicals, investor data)

    Returns:
        JSON {
            "success": true,
            "feature_count": 28,
            "sample_count": 100,
            "features": [[...], [...], ...],  # 2D array
            "feature_names": ["is_new", "is_increased", ...],
            "tickers": ["005930", ...],
            "names": ["삼성전자", ...],
            "dates": ["2025-01-01", ...]
        }
    """
    try:
        changes = json.loads(changes_json)
        if not changes:
            return to_json({
                "success": True,
                "feature_count": 0,
                "sample_count": 0,
                "features": [],
                "feature_names": ALL_FEATURES,
                "tickers": [],
                "names": [],
                "dates": []
            })

        market_data = json.loads(market_data_json) if market_data_json else {}
        stock_data = json.loads(stock_data_json) if stock_data_json else {}

        rows = []
        tickers = []
        names = []
        dates = []

        for c in changes:
            ticker = c.get('ticker', '')
            status = c.get('status', '')

            row = {
                # Basic ETF Features
                'is_new': int(status == 'NEW'),
                'is_increased': int(status == 'INCREASED'),
                'is_decreased': int(status == 'DECREASED'),
                'is_removed': int(status == 'REMOVED'),
                'weight_change': c.get('weight_change', 0),
                'etf_count': c.get('etf_count', 1),
                'amount_billion': c.get('total_amount', 0) / 1e9,
            }

            # Supply/Demand Features (from stock_data)
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

            # Technical Indicators (from stock_data)
            if stock_data and ticker in stock_data:
                sd = stock_data[ticker]
                row['price_vs_ma20'] = sd.get('price_vs_ma20', 1.0)
                row['price_vs_ma60'] = sd.get('price_vs_ma60', 1.0)
                row['rsi_14'] = sd.get('rsi', 50) / 100  # Normalize to 0~1
                row['macd_signal'] = sd.get('macd_signal', 0)
                row['volume_ratio'] = min(sd.get('volume_ratio', 1.0), 10.0)  # Cap at 10
                row['volatility_20d'] = sd.get('volatility', 0)
            else:
                row.update({
                    'price_vs_ma20': 1.0, 'price_vs_ma60': 1.0,
                    'rsi_14': 0.5, 'macd_signal': 0,
                    'volume_ratio': 1.0, 'volatility_20d': 0
                })

            # Momentum Features
            if stock_data and ticker in stock_data:
                sd = stock_data[ticker]
                row['return_5d'] = sd.get('return_5d', 0)
                row['return_20d'] = sd.get('return_20d', 0)
                row['return_60d'] = sd.get('return_60d', 0)
                row['momentum_score'] = _calc_momentum_score(
                    row['return_5d'], row['return_20d'], row['return_60d']
                )
            else:
                row.update({
                    'return_5d': 0, 'return_20d': 0, 'return_60d': 0, 'momentum_score': 0
                })

            # Market Context Features
            if market_data:
                row['market_oscillator'] = market_data.get('oscillator', 50) / 100
                row['fear_greed_index'] = market_data.get('fear_greed', 0.5)
                row['market_return_5d'] = market_data.get('market_return_5d', 0)
                row['market_type'] = 1 if _infer_market(ticker) == 'KOSPI' else 0
            else:
                row.update({
                    'market_oscillator': 0.5, 'fear_greed_index': 0.5,
                    'market_return_5d': 0, 'market_type': 1
                })

            # weight_zscore will be calculated after all rows
            row['weight_zscore'] = 0

            rows.append(row)
            tickers.append(ticker)
            names.append(c.get('name', ''))
            dates.append(c.get('date', ''))

        df = pd.DataFrame(rows)

        # Calculate z-score for weight_change
        if len(df) > 1 and 'weight_change' in df.columns:
            mean_wc = df['weight_change'].mean()
            std_wc = df['weight_change'].std()
            if std_wc > 0:
                df['weight_zscore'] = (df['weight_change'] - mean_wc) / std_wc
            else:
                df['weight_zscore'] = 0

        # Extract features in correct order
        feature_array = df[ALL_FEATURES].values.tolist()

        return to_json({
            "success": True,
            "feature_count": len(ALL_FEATURES),
            "sample_count": len(rows),
            "features": feature_array,
            "feature_names": ALL_FEATURES,
            "tickers": tickers,
            "names": names,
            "dates": dates
        })

    except Exception as e:
        log.error("Feature engineering error: %s", e)
        return to_json({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc()
        })


def get_feature_importance_mapping(importance_values: List[float]) -> str:
    """
    Map feature importance values to feature names.

    Args:
        importance_values: List of importance values (same order as ALL_FEATURES)

    Returns:
        JSON with feature name -> importance mapping
    """
    try:
        if len(importance_values) != len(ALL_FEATURES):
            return to_json({
                "success": False,
                "error": f"Expected {len(ALL_FEATURES)} values, got {len(importance_values)}"
            })

        # Create mapping with Korean names
        importance_kr = {}
        importance_en = {}

        for feat, val in zip(ALL_FEATURES, importance_values):
            importance_en[feat] = round(val, 4)
            kr_name = FEATURE_NAMES_KR.get(feat, feat)
            importance_kr[kr_name] = round(val, 4)

        # Sort by importance
        sorted_kr = dict(sorted(importance_kr.items(), key=lambda x: x[1], reverse=True))
        sorted_en = dict(sorted(importance_en.items(), key=lambda x: x[1], reverse=True))

        return to_json({
            "success": True,
            "importance_kr": sorted_kr,
            "importance_en": sorted_en,
            "top_features": list(sorted_kr.keys())[:10]
        })

    except Exception as e:
        log.error("Feature importance mapping error: %s", e)
        return to_json({"success": False, "error": str(e)})


def get_key_factors(
    feature_values: List[float],
    importance_values: Optional[List[float]] = None,
    top_n: int = 3
) -> str:
    """
    Get key factors (most influential features) for a prediction.

    Args:
        feature_values: Feature values for a single sample
        importance_values: Model's feature importance (optional)
        top_n: Number of top factors to return

    Returns:
        JSON with key factors
    """
    try:
        if len(feature_values) != len(ALL_FEATURES):
            return to_json({
                "success": False,
                "error": f"Expected {len(ALL_FEATURES)} values, got {len(feature_values)}"
            })

        factors = []

        # Calculate contribution score for each feature
        for i, (feat, val) in enumerate(zip(ALL_FEATURES, feature_values)):
            importance = importance_values[i] if importance_values else 1.0

            # Determine if this feature contributes positively
            is_positive = False
            factor_name = FEATURE_NAMES_KR.get(feat, feat)

            if feat in ('is_new', 'is_increased'):
                is_positive = val > 0
            elif feat == 'is_decreased':
                is_positive = val == 0
            elif feat == 'is_removed':
                is_positive = val == 0
            elif feat == 'weight_change':
                is_positive = val > 0
            elif feat in ('foreign_5d_net', 'institution_5d_net', 'supply_score'):
                is_positive = val > 0
            elif feat in ('foreign_ratio', 'institution_ratio'):
                is_positive = val > 0
            elif feat == 'price_vs_ma20':
                is_positive = val > 1.0
            elif feat == 'rsi_14':
                is_positive = 0.3 < val < 0.7  # Not overbought/oversold
            elif feat == 'macd_signal':
                is_positive = val > 0
            elif feat == 'momentum_score':
                is_positive = val > 0
            elif feat == 'weight_zscore':
                is_positive = val > 0

            # Calculate contribution
            contribution = abs(val) * importance

            if is_positive and contribution > 0.01:
                factors.append({
                    'name': factor_name,
                    'value': round(val, 4),
                    'contribution': round(contribution, 4),
                    'direction': 'positive'
                })

        # Sort by contribution and get top N
        factors.sort(key=lambda x: x['contribution'], reverse=True)
        top_factors = factors[:top_n]

        return to_json({
            "success": True,
            "key_factors": [f['name'] for f in top_factors],
            "factor_details": top_factors
        })

    except Exception as e:
        log.error("Key factors extraction error: %s", e)
        return to_json({"success": False, "error": str(e)})


def get_feature_list() -> str:
    """Get list of all features with their groups and Korean names."""
    try:
        features = []
        for group_name, group_features in FEATURE_GROUPS.items():
            for feat in group_features:
                features.append({
                    'name': feat,
                    'name_kr': FEATURE_NAMES_KR.get(feat, feat),
                    'group': group_name
                })

        return to_json({
            "success": True,
            "feature_count": len(ALL_FEATURES),
            "features": features,
            "groups": list(FEATURE_GROUPS.keys())
        })

    except Exception as e:
        return to_json({"success": False, "error": str(e)})
