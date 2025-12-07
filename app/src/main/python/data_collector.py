"""
Batch data collection module for ML prediction.
Optimized for speed - batch API calls instead of individual calls.
"""
import json
import time
import traceback
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
from pykrx import stock

from core import get_logger, to_json, err_json, to_ymd, to_iso, parse_date

log = get_logger(__name__)


def _batch_get_market_ohlcv(date: str, market: str = "ALL") -> pd.DataFrame:
    """
    Get OHLCV data for all stocks in market on a specific date.
    Much faster than individual calls.
    """
    try:
        d = to_ymd(date)
        if market == "ALL":
            # Get both KOSPI and KOSDAQ
            df_kospi = stock.get_market_ohlcv_by_ticker(d, market="KOSPI")
            df_kosdaq = stock.get_market_ohlcv_by_ticker(d, market="KOSDAQ")
            if df_kospi is not None and df_kosdaq is not None:
                return pd.concat([df_kospi, df_kosdaq])
            elif df_kospi is not None:
                return df_kospi
            elif df_kosdaq is not None:
                return df_kosdaq
        else:
            return stock.get_market_ohlcv_by_ticker(d, market=market)
    except Exception as e:
        log.error("Batch OHLCV fetch error for %s: %s", date, e)
    return pd.DataFrame()


def batch_get_price_changes(
    tickers_json: str,
    base_date: str,
    days_after: int = 5
) -> str:
    """
    Batch collect price changes for multiple tickers.

    Args:
        tickers_json: JSON array of ticker strings
        base_date: Base date (YYYY-MM-DD or YYYYMMDD)
        days_after: Days after base date to measure change

    Returns:
        JSON {
            "success": true,
            "data": {"005930": 3.5, "000660": -1.2, ...},
            "collected_count": 95,
            "failed_count": 5,
            "collection_time_ms": 2500
        }
    """
    start_time = time.time()

    try:
        tickers = json.loads(tickers_json)
        if not tickers:
            return to_json({
                "success": True,
                "data": {},
                "collected_count": 0,
                "failed_count": 0,
                "collection_time_ms": 0
            })

        # Parse dates
        base_dt = parse_date(base_date)
        if not base_dt:
            return to_json({"success": False, "error": f"Invalid date: {base_date}"})

        # Calculate end date with buffer for non-trading days
        end_dt = base_dt + timedelta(days=days_after + 10)

        base_str = to_ymd(base_dt)
        end_str = to_ymd(end_dt)

        # Batch fetch OHLCV for both dates
        log.info("Batch fetching prices for %d tickers from %s to %s", len(tickers), base_str, end_str)

        df_base = _batch_get_market_ohlcv(base_str)
        df_end = _batch_get_market_ohlcv(end_str)

        if df_base.empty:
            # Try next business day
            for i in range(1, 5):
                test_date = (base_dt + timedelta(days=i)).strftime("%Y%m%d")
                df_base = _batch_get_market_ohlcv(test_date)
                if not df_base.empty:
                    break

        if df_end.empty:
            # Try previous days
            for i in range(1, 5):
                test_date = (end_dt - timedelta(days=i)).strftime("%Y%m%d")
                df_end = _batch_get_market_ohlcv(test_date)
                if not df_end.empty:
                    break

        results = {}
        failed_count = 0

        for ticker in tickers:
            try:
                if ticker in df_base.index and ticker in df_end.index:
                    p0 = df_base.loc[ticker, '종가']
                    p1 = df_end.loc[ticker, '종가']
                    if p0 > 0:
                        change = round(((p1 - p0) / p0) * 100, 2)
                        results[ticker] = change
                    else:
                        failed_count += 1
                else:
                    failed_count += 1
            except Exception:
                failed_count += 1

        elapsed_ms = int((time.time() - start_time) * 1000)

        log.info("Batch price collection completed: %d success, %d failed, %dms",
                 len(results), failed_count, elapsed_ms)

        return to_json({
            "success": True,
            "data": results,
            "collected_count": len(results),
            "failed_count": failed_count,
            "collection_time_ms": elapsed_ms
        })

    except Exception as e:
        log.error("Batch price collection error: %s", e)
        return to_json({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc()
        })


def batch_get_stock_technicals(
    tickers_json: str,
    date: str,
    lookback_days: int = 60
) -> str:
    """
    Batch collect technical indicators for multiple tickers.

    Args:
        tickers_json: JSON array of ticker strings
        date: Analysis date
        lookback_days: Days of history for indicator calculation

    Returns:
        JSON with technical data for each ticker
    """
    start_time = time.time()

    try:
        tickers = json.loads(tickers_json)
        if not tickers:
            return to_json({"success": True, "data": {}})

        end_dt = parse_date(date)
        if not end_dt:
            return to_json({"success": False, "error": f"Invalid date: {date}"})

        start_dt = end_dt - timedelta(days=lookback_days + 30)  # Buffer

        results = {}

        def fetch_ticker_data(ticker: str) -> Tuple[str, Optional[Dict]]:
            """Fetch data for single ticker."""
            try:
                df = stock.get_market_ohlcv(
                    start_dt.strftime("%Y%m%d"),
                    end_dt.strftime("%Y%m%d"),
                    ticker
                )

                if df is None or len(df) < 20:
                    return ticker, None

                close = df['종가']
                volume = df['거래량']

                # Calculate technical indicators
                data = {}

                # Moving averages
                ma20 = close.rolling(20).mean().iloc[-1]
                ma60 = close.rolling(60).mean().iloc[-1] if len(close) >= 60 else ma20
                current_price = close.iloc[-1]

                data['price_vs_ma20'] = round(current_price / ma20, 4) if ma20 > 0 else 1.0
                data['price_vs_ma60'] = round(current_price / ma60, 4) if ma60 > 0 else 1.0

                # RSI
                delta = close.diff()
                gain = delta.where(delta > 0, 0).rolling(14).mean()
                loss = (-delta.where(delta < 0, 0)).rolling(14).mean()
                rs = gain / loss
                rsi = 100 - (100 / (1 + rs))
                data['rsi'] = round(rsi.iloc[-1], 2) if not np.isnan(rsi.iloc[-1]) else 50

                # MACD
                ema12 = close.ewm(span=12).mean()
                ema26 = close.ewm(span=26).mean()
                macd = ema12 - ema26
                signal = macd.ewm(span=9).mean()

                macd_val = macd.iloc[-1]
                signal_val = signal.iloc[-1]
                prev_macd = macd.iloc[-2] if len(macd) > 1 else macd_val
                prev_signal = signal.iloc[-2] if len(signal) > 1 else signal_val

                # MACD signal: 1 = golden cross, -1 = dead cross, 0 = neutral
                if prev_macd < prev_signal and macd_val >= signal_val:
                    data['macd_signal'] = 1
                elif prev_macd > prev_signal and macd_val <= signal_val:
                    data['macd_signal'] = -1
                else:
                    data['macd_signal'] = 0

                # Volume ratio
                avg_volume = volume.rolling(20).mean().iloc[-1]
                current_volume = volume.iloc[-1]
                data['volume_ratio'] = round(current_volume / avg_volume, 2) if avg_volume > 0 else 1.0

                # Volatility (20-day standard deviation of returns)
                returns = close.pct_change()
                data['volatility'] = round(returns.rolling(20).std().iloc[-1] * 100, 2)

                # Returns
                if len(close) >= 5:
                    data['return_5d'] = round((close.iloc[-1] / close.iloc[-5] - 1) * 100, 2)
                else:
                    data['return_5d'] = 0

                if len(close) >= 20:
                    data['return_20d'] = round((close.iloc[-1] / close.iloc[-20] - 1) * 100, 2)
                else:
                    data['return_20d'] = 0

                if len(close) >= 60:
                    data['return_60d'] = round((close.iloc[-1] / close.iloc[-60] - 1) * 100, 2)
                else:
                    data['return_60d'] = 0

                return ticker, data

            except Exception as e:
                log.warning("Technical fetch error for %s: %s", ticker, e)
                return ticker, None

        # Parallel fetch
        with ThreadPoolExecutor(max_workers=10) as executor:
            futures = {executor.submit(fetch_ticker_data, t): t for t in tickers}
            for future in as_completed(futures):
                ticker, data = future.result()
                if data:
                    results[ticker] = data

        elapsed_ms = int((time.time() - start_time) * 1000)

        return to_json({
            "success": True,
            "data": results,
            "collected_count": len(results),
            "collection_time_ms": elapsed_ms
        })

    except Exception as e:
        log.error("Batch technical collection error: %s", e)
        return to_json({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc()
        })


def batch_get_investor_data(
    tickers_json: str,
    date: str,
    days: int = 5
) -> str:
    """
    Batch collect foreign/institutional investor data.

    Args:
        tickers_json: JSON array of ticker strings
        date: Analysis date
        days: Cumulative days for investor data

    Returns:
        JSON with investor data for each ticker
    """
    start_time = time.time()

    try:
        tickers = json.loads(tickers_json)
        if not tickers:
            return to_json({"success": True, "data": {}})

        end_dt = parse_date(date)
        if not end_dt:
            return to_json({"success": False, "error": f"Invalid date: {date}"})

        start_dt = end_dt - timedelta(days=days + 10)  # Buffer

        start_str = start_dt.strftime("%Y%m%d")
        end_str = end_dt.strftime("%Y%m%d")

        results = {}

        def fetch_investor_data(ticker: str) -> Tuple[str, Optional[Dict]]:
            """Fetch investor data for single ticker."""
            try:
                # Get investor trading data
                df = stock.get_market_trading_value_by_date(
                    start_str, end_str, ticker,
                    on="순매수", detail=True
                )

                if df is None or df.empty:
                    return ticker, None

                # Get market cap
                fund = stock.get_market_cap_by_ticker(end_str)
                market_cap = fund.loc[ticker, '시가총액'] if ticker in fund.index else 0

                # Calculate cumulative values (most recent 'days' rows)
                df = df.tail(days)

                foreign_5d = df['외국인'].sum() if '외국인' in df.columns else 0
                institution_5d = (
                    df['기관합계'].sum() if '기관합계' in df.columns
                    else (df['투신'].sum() + df['연기금'].sum()) if '투신' in df.columns else 0
                )

                return ticker, {
                    'foreign_5d': int(foreign_5d),
                    'institution_5d': int(institution_5d),
                    'market_cap': int(market_cap)
                }

            except Exception as e:
                log.warning("Investor data fetch error for %s: %s", ticker, e)
                return ticker, None

        # Parallel fetch with rate limiting
        with ThreadPoolExecutor(max_workers=5) as executor:
            futures = {executor.submit(fetch_investor_data, t): t for t in tickers}
            for future in as_completed(futures):
                ticker, data = future.result()
                if data:
                    results[ticker] = data

        elapsed_ms = int((time.time() - start_time) * 1000)

        return to_json({
            "success": True,
            "data": results,
            "collected_count": len(results),
            "collection_time_ms": elapsed_ms
        })

    except Exception as e:
        log.error("Batch investor data collection error: %s", e)
        return to_json({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc()
        })


def collect_training_labels(
    changes_json: str,
    days_after: int = 5,
    threshold: float = 3.0
) -> str:
    """
    Efficiently collect training labels using batch processing.

    Args:
        changes_json: JSON array of stock change data
        days_after: Days after to measure price change
        threshold: Price change threshold for positive label

    Returns:
        JSON {
            "success": true,
            "labels": [1, 0, 1, ...],
            "valid_indices": [0, 1, 3, ...],
            "total_samples": 100,
            "positive_count": 35,
            "collection_time_ms": 3000
        }
    """
    start_time = time.time()

    try:
        changes = json.loads(changes_json)
        if not changes:
            return to_json({
                "success": True,
                "labels": [],
                "valid_indices": [],
                "total_samples": 0,
                "positive_count": 0,
                "collection_time_ms": 0
            })

        # Group by date for batch processing
        date_groups = {}
        for idx, c in enumerate(changes):
            date = c.get('date', '')
            ticker = c.get('ticker', '')
            if date and ticker:
                if date not in date_groups:
                    date_groups[date] = []
                date_groups[date].append((idx, ticker))

        # Batch collect price changes for each date
        all_labels = {}  # idx -> label

        for date, items in date_groups.items():
            tickers = [t for _, t in items]
            result_json = batch_get_price_changes(
                json.dumps(tickers),
                date,
                days_after
            )
            result = json.loads(result_json)

            if result.get('success') and result.get('data'):
                price_data = result['data']
                for idx, ticker in items:
                    if ticker in price_data:
                        change = price_data[ticker]
                        all_labels[idx] = 1 if change >= threshold else 0

        # Build output
        valid_indices = sorted(all_labels.keys())
        labels = [all_labels[i] for i in valid_indices]
        positive_count = sum(labels)

        elapsed_ms = int((time.time() - start_time) * 1000)

        log.info("Label collection: %d valid samples, %d positive (%.1f%%), %dms",
                 len(labels), positive_count,
                 positive_count / len(labels) * 100 if labels else 0,
                 elapsed_ms)

        return to_json({
            "success": True,
            "labels": labels,
            "valid_indices": valid_indices,
            "total_samples": len(labels),
            "positive_count": positive_count,
            "collection_time_ms": elapsed_ms
        })

    except Exception as e:
        log.error("Label collection error: %s", e)
        return to_json({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc()
        })
