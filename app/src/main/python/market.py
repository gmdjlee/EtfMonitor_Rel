"""
Market index and oscillator module.
Uses KIS API exclusively - no pykrx dependency.

Requires KIS API credentials to be configured in Settings.
"""
import time
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional
import numpy as np
import pandas as pd

from core import get_logger, get_name, to_json, err_json, MARKETS, REQ_DELAY, get_kis_client, is_kis_available

log = get_logger(__name__)

BATCH_SIZE = 50


def _ensure_kis_client():
    """Ensure KIS client is available, raise error if not."""
    if not is_kis_available():
        raise RuntimeError("KIS API not configured. Please configure KIS credentials in Settings.")
    return get_kis_client()


def fetch_index(market: str, start: str, end: str) -> List[Dict[str, Any]]:
    """
    Fetch market index data via KIS API.

    Returns: [{
        "date": "YYYY-MM-DD",
        "market": "KOSPI",
        "closePrice": 2500.0,
        "openPrice": 2480.0,
        "highPrice": 2510.0,
        "lowPrice": 2470.0,
        "volume": 500000,
        "changeRate": 0.5
    }, ...]
    """
    cfg = MARKETS.get(market)
    if not cfg:
        return []

    try:
        client = _ensure_kis_client()
        # KIS API uses index codes: "0001" for KOSPI, "1001" for KOSDAQ
        # Our MARKETS config uses "1001"/"2001" - map accordingly
        index_code = "0001" if market == "KOSPI" else "1001"
        df = client.get_index_ohlcv(index_code, start)

        if df is None or df.empty:
            log.warning(f"No index data for {market}")
            return []

        result = []
        prev_close = None

        for idx, row in df.iterrows():
            close = float(row["close"])
            change = 0.0
            if prev_close and prev_close > 0:
                change = ((close - prev_close) / prev_close) * 100

            result.append({
                "date": idx.strftime("%Y-%m-%d"),
                "market": market,
                "closePrice": close,
                "openPrice": float(row["open"]),
                "highPrice": float(row["high"]),
                "lowPrice": float(row["low"]),
                "volume": int(row["volume"]),
                "changeRate": round(change, 2)
            })
            prev_close = close

        log.info("Index %s: %d records", market, len(result))
        return result

    except Exception as e:
        log.error("fetch_index error (%s): %s", market, e)
        return []


def fetch_all_markets(start: str, end: str, markets: Optional[List[str]] = None) -> str:
    """Fetch index data for multiple markets."""
    mkts = list(markets) if markets else ["KOSPI", "KOSDAQ"]
    data = []
    for m in mkts:
        data.extend(fetch_index(m, start, end))
    return to_json(data)


def fetch_recent(days: int = 30, markets: Optional[List[str]] = None) -> str:
    """Fetch recent N days of index data."""
    end = datetime.now()
    start = end - timedelta(days=days + 30)
    return fetch_all_markets(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"), markets)


def get_latest_index(market: str) -> Optional[Dict]:
    """Get latest index data for a market."""
    end = datetime.now()
    start = end - timedelta(days=10)
    data = fetch_index(market, start.strftime("%Y%m%d"), end.strftime("%Y%m%d"))
    return data[-1] if data else None


class Oscillator:
    """Market overbought/oversold oscillator calculator using KIS API."""

    def __init__(self, start: str, end: str):
        self.start = start
        self.end = end
        self._validate()
        self._client = _ensure_kis_client()

    def _validate(self):
        s = datetime.strptime(self.start, '%Y%m%d')
        e = datetime.strptime(self.end, '%Y%m%d')
        if s > e:
            raise ValueError(f"Invalid date range: {self.start} > {self.end}")

    def _get_index(self, market: str) -> Optional[pd.DataFrame]:
        """Get index OHLCV data via KIS API."""
        cfg = MARKETS.get(market)
        if not cfg:
            return None

        try:
            # Map market to KIS index code
            index_code = "0001" if market == "KOSPI" else "1001"
            df = self._client.get_index_ohlcv(index_code, self.start)

            if df is None or df.empty:
                return None

            # Return DataFrame with date and close columns
            result = pd.DataFrame({
                "날짜": df.index,
                "종가": df["close"].values
            })
            return result

        except Exception as e:
            log.error("Index fetch error (%s): %s", market, e)
            return None

    def _get_components(self, market: str) -> tuple:
        """Get component stock data via KIS API."""
        cfg = MARKETS.get(market)
        if not cfg:
            return pd.DataFrame(), pd.DataFrame()

        try:
            # Get top 200 stocks by market cap as index components
            tickers = self._client.get_index_components(market, limit=200)

            if not tickers:
                log.warning(f"No component stocks for {market}")
                return pd.DataFrame(), pd.DataFrame()

            log.info("%s: collecting %d components", market, len(tickers))

            # Get index dates for alignment
            index_code = "0001" if market == "KOSPI" else "1001"
            index_df = self._client.get_index_ohlcv(index_code, self.start)
            if index_df is None or index_df.empty:
                return pd.DataFrame(), pd.DataFrame()

            dates = index_df.index
            close_dict, vol_dict = {}, {}

            for i in range(0, len(tickers), BATCH_SIZE):
                batch = tickers[i:i + BATCH_SIZE]
                for t in batch:
                    try:
                        df = self._client.get_stock_ohlcv(t, self.start, self.end)
                        if df is not None and not df.empty:
                            aligned = df.reindex(dates)
                            name = get_name(t)
                            col = f"{name}({t})" if name else t
                            close_dict[col] = aligned["close"]
                            vol_dict[col] = aligned["volume"].fillna(0)
                        time.sleep(REQ_DELAY)
                    except Exception as e:
                        log.debug(f"Error fetching {t}: {e}")
                        continue

            close_df = pd.DataFrame(close_dict, index=dates)
            vol_df = pd.DataFrame(vol_dict, index=dates)
            close_df.index.name = '날짜'
            vol_df.index.name = '날짜'
            close_df.reset_index(inplace=True)
            vol_df.reset_index(inplace=True)

            log.info("%s: collected %d components", market, len(close_dict))
            return close_df, vol_df

        except Exception as e:
            log.error("Component fetch error (%s): %s", market, e)
            return pd.DataFrame(), pd.DataFrame()

    def _calc(self, close_df: pd.DataFrame, vol_df: pd.DataFrame) -> np.ndarray:
        """Calculate oscillator values."""
        cols = [c for c in close_df.columns if c != "날짜"]
        if not cols:
            return np.array([])

        change = close_df[cols].pct_change().fillna(0)
        up_mask = change > 0
        down_mask = change < 0

        up_vol = vol_df[cols].where(up_mask, 0).sum(axis=1)
        down_vol = vol_df[cols].where(down_mask, 0).sum(axis=1)
        gained = change.where(up_mask, 0).sum(axis=1)
        lost = change.where(down_mask, 0).sum(axis=1).abs()

        total_vol = up_vol + down_vol
        total_pts = gained + lost

        vol_ratio = np.where(total_vol > 0, up_vol / total_vol, 0.5)
        pts_ratio = np.where(total_pts > 0, gained / total_pts, 0.5)
        avg = (vol_ratio + pts_ratio) / 2

        return np.where(avg > 0.5, avg, avg - 1)

    def analyze(self, market: str) -> Optional[Dict]:
        """Run oscillator analysis for a market."""
        log.info("Analyzing %s oscillator", market)

        idx_df = self._get_index(market)
        if idx_df is None or idx_df.empty:
            return None

        close_df, vol_df = self._get_components(market)
        if close_df.empty or vol_df.empty:
            return None

        osc = self._calc(close_df, vol_df)
        if len(osc) == 0:
            return None

        osc_pct = osc * 100
        return {
            "market": market,
            "dates": idx_df["날짜"].dt.strftime("%Y-%m-%d").tolist(),
            "index": idx_df["종가"].tolist(),
            "oscillator": osc_pct.tolist(),
            "stats": {
                "mean": float(np.mean(osc_pct)),
                "max": float(np.max(osc_pct)),
                "min": float(np.min(osc_pct)),
                "latest": float(osc_pct[-1]) if len(osc_pct) > 0 else 0
            }
        }


def get_market_oscillator(market: str, start: str, end: str) -> str:
    """
    Get market overbought/oversold oscillator.

    Returns: JSON {
        "market": "KOSPI",
        "dates": [...],
        "index": [...],
        "oscillator": [...],
        "stats": {...}
    }
    """
    if market not in MARKETS:
        return err_json("Invalid market")

    try:
        osc = Oscillator(start, end)
        result = osc.analyze(market)
        return to_json(result) if result else err_json("Analysis failed")
    except RuntimeError as e:
        # KIS API not configured
        log.error("KIS API error: %s", e)
        return err_json(str(e))
    except Exception as e:
        log.error("get_market_oscillator error: %s", e)
        return err_json(str(e))


# Backward compatibility alias (used by MarketIndexPyClient)
fetch_recent_days = fetch_recent
