"""
Market index and oscillator module.

Requires KIS API credentials to be configured in Settings.
Uses KIS Open API as the sole data source.

Improvements in v2.1:
- Performance optimization with caching
- Better error handling and logging
- Fixed end date parameter usage
- Batch processing for component stocks
"""
import time
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple
from dataclasses import dataclass
from functools import lru_cache
import numpy as np
import pandas as pd

from core import (
    get_logger, get_name, to_json, err_json, MARKETS, REQ_DELAY,
    get_kis_client, is_kis_available, DataResult, ResultStatus
)

log = get_logger(__name__)

BATCH_SIZE = 50

# 캐시 설정
CACHE_TTL_SECONDS = 300  # 5분


@dataclass
class CachedData:
    """캐시된 데이터를 담는 클래스."""
    data: Any
    timestamp: datetime

    def is_valid(self, ttl_seconds: int = CACHE_TTL_SECONDS) -> bool:
        return (datetime.now() - self.timestamp).total_seconds() < ttl_seconds


class DataCache:
    """간단한 메모리 캐시."""

    def __init__(self):
        self._cache: Dict[str, CachedData] = {}

    def get(self, key: str, ttl_seconds: int = CACHE_TTL_SECONDS) -> Optional[Any]:
        """캐시에서 데이터 조회."""
        if key in self._cache:
            cached = self._cache[key]
            if cached.is_valid(ttl_seconds):
                log.debug(f"Cache hit: {key}")
                return cached.data
            else:
                del self._cache[key]
        return None

    def set(self, key: str, data: Any):
        """캐시에 데이터 저장."""
        self._cache[key] = CachedData(data=data, timestamp=datetime.now())

    def clear(self):
        """캐시 초기화."""
        self._cache.clear()


# 글로벌 캐시 인스턴스
_cache = DataCache()


def _ensure_kis_client():
    """Ensure KIS client is available, raise error if not."""
    if not is_kis_available():
        raise RuntimeError("KIS API not configured. Please configure KIS credentials in Settings.")
    return get_kis_client()


def fetch_index(market: str, start: str, end: str) -> List[Dict[str, Any]]:
    """
    Fetch market index data via KIS API.

    Args:
        market: "KOSPI" or "KOSDAQ"
        start: Start date (YYYYMMDD)
        end: End date (YYYYMMDD)

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
        log.error(f"Unknown market: {market}")
        return []

    # 캐시 확인
    cache_key = f"index_{market}_{start}_{end}"
    cached = _cache.get(cache_key)
    if cached is not None:
        return cached

    try:
        client = _ensure_kis_client()
        # KIS API uses index codes: "0001" for KOSPI, "1001" for KOSDAQ
        index_code = "0001" if market == "KOSPI" else "1001"

        # end_date도 전달
        df = client.get_index_ohlcv(index_code, start, end)

        if df is None or df.empty:
            log.warning(f"No index data for {market}")
            return []

        # 날짜 범위 필터링
        start_dt = pd.to_datetime(start)
        end_dt = pd.to_datetime(end)
        df = df[(df.index >= start_dt) & (df.index <= end_dt)]

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

        log.info("Index %s: %d records (%s ~ %s)", market, len(result), start, end)

        # 캐시 저장
        _cache.set(cache_key, result)

        return result

    except RuntimeError as e:
        log.error("KIS API error (%s): %s", market, e)
        return []
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
    start = end - timedelta(days=days + 30)  # 여유분 추가
    return fetch_all_markets(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"), markets)


def get_latest_index(market: str) -> Optional[Dict]:
    """Get latest index data for a market."""
    end = datetime.now()
    start = end - timedelta(days=10)
    data = fetch_index(market, start.strftime("%Y%m%d"), end.strftime("%Y%m%d"))
    return data[-1] if data else None


class Oscillator:
    """
    Market overbought/oversold oscillator calculator using KIS API.

    Improvements:
    - Caching for component data
    - Better error handling with detailed logging
    - Zero division protection
    """

    def __init__(self, start: str, end: str):
        self.start = start
        self.end = end
        self._validate()
        self._client = _ensure_kis_client()
        self._component_cache: Dict[str, Tuple[pd.DataFrame, pd.DataFrame]] = {}

    def _validate(self):
        """Validate date range."""
        try:
            s = datetime.strptime(self.start, '%Y%m%d')
            e = datetime.strptime(self.end, '%Y%m%d')
            if s > e:
                raise ValueError(f"Invalid date range: {self.start} > {self.end}")
        except ValueError as e:
            raise ValueError(f"Date validation error: {e}")

    def _get_index(self, market: str) -> Optional[pd.DataFrame]:
        """Get index OHLCV data via KIS API."""
        cfg = MARKETS.get(market)
        if not cfg:
            return None

        try:
            # Map market to KIS index code
            index_code = "0001" if market == "KOSPI" else "1001"
            df = self._client.get_index_ohlcv(index_code, self.start, self.end)

            if df is None or df.empty:
                log.warning(f"No index data for {market}")
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

    def _get_components(self, market: str) -> Tuple[pd.DataFrame, pd.DataFrame]:
        """
        Get component stock data via KIS API with caching.

        Returns:
            Tuple of (close_df, volume_df)
        """
        # 캐시 확인
        cache_key = f"{market}_{self.start}_{self.end}"
        if cache_key in self._component_cache:
            log.debug(f"Using cached component data for {market}")
            return self._component_cache[cache_key]

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
            index_df = self._client.get_index_ohlcv(index_code, self.start, self.end)
            if index_df is None or index_df.empty:
                return pd.DataFrame(), pd.DataFrame()

            dates = index_df.index
            close_dict, vol_dict = {}, {}
            success_count = 0
            error_count = 0

            for i in range(0, len(tickers), BATCH_SIZE):
                batch = tickers[i:i + BATCH_SIZE]
                batch_start_time = time.time()

                for t in batch:
                    try:
                        df = self._client.get_stock_ohlcv(t, self.start, self.end)
                        if df is not None and not df.empty:
                            aligned = df.reindex(dates)
                            name = get_name(t)
                            col = f"{name}({t})" if name else t
                            close_dict[col] = aligned["close"]
                            vol_dict[col] = aligned["volume"].fillna(0)
                            success_count += 1
                        time.sleep(REQ_DELAY)  # Rate limit compliance: 1.0초 (was 0.25초)
                    except Exception as e:
                        error_count += 1
                        log.warning(f"Error fetching {t}: {e}")
                        # Rate limit 에러 시 추가 대기
                        if "500" in str(e) or "429" in str(e) or "rate" in str(e).lower():
                            log.info(f"Rate limit detected, waiting 3 seconds...")
                            time.sleep(3)
                        continue

                batch_elapsed = time.time() - batch_start_time
                log.info(f"Batch {i//BATCH_SIZE + 1}: {len(batch)} tickers in {batch_elapsed:.1f}s")

                # 배치 간 추가 딜레이 (rate limit 회복)
                if i + BATCH_SIZE < len(tickers):
                    time.sleep(2.0)  # 배치 간 2초 대기

            if error_count > 0:
                log.warning(f"{market}: {error_count} errors, {success_count} successes")

            close_df = pd.DataFrame(close_dict, index=dates)
            vol_df = pd.DataFrame(vol_dict, index=dates)
            close_df.index.name = '날짜'
            vol_df.index.name = '날짜'
            close_df.reset_index(inplace=True)
            vol_df.reset_index(inplace=True)

            log.info("%s: collected %d components successfully", market, len(close_dict))

            # 캐시 저장
            self._component_cache[cache_key] = (close_df, vol_df)

            return close_df, vol_df

        except Exception as e:
            log.error("Component fetch error (%s): %s", market, e)
            return pd.DataFrame(), pd.DataFrame()

    def _calc(self, close_df: pd.DataFrame, vol_df: pd.DataFrame) -> np.ndarray:
        """
        Calculate oscillator values with zero-division protection.

        Returns:
            numpy array of oscillator values (-1 to 1)
        """
        cols = [c for c in close_df.columns if c != "날짜"]
        if not cols:
            log.warning("No columns for oscillator calculation")
            return np.array([])

        try:
            change = close_df[cols].pct_change().fillna(0)
            up_mask = change > 0
            down_mask = change < 0

            up_vol = vol_df[cols].where(up_mask, 0).sum(axis=1)
            down_vol = vol_df[cols].where(down_mask, 0).sum(axis=1)
            gained = change.where(up_mask, 0).sum(axis=1)
            lost = change.where(down_mask, 0).sum(axis=1).abs()

            total_vol = up_vol + down_vol
            total_pts = gained + lost

            # Zero division protection
            vol_ratio = np.where(total_vol > 0, up_vol / total_vol, 0.5)
            pts_ratio = np.where(total_pts > 0, gained / total_pts, 0.5)
            avg = (vol_ratio + pts_ratio) / 2

            result = np.where(avg > 0.5, avg, avg - 1)

            # NaN 체크
            nan_count = np.isnan(result).sum()
            if nan_count > 0:
                log.warning(f"Oscillator has {nan_count} NaN values, replacing with 0")
                result = np.nan_to_num(result, nan=0.0)

            return result

        except Exception as e:
            log.error(f"Oscillator calculation error: {e}")
            return np.array([])

    def analyze(self, market: str) -> Optional[Dict]:
        """
        Run oscillator analysis for a market.

        Returns:
            Dict with analysis results or None on error
        """
        log.info("Analyzing %s oscillator (%s ~ %s)", market, self.start, self.end)

        idx_df = self._get_index(market)
        if idx_df is None or idx_df.empty:
            log.error(f"Failed to get index data for {market}")
            return None

        close_df, vol_df = self._get_components(market)
        if close_df.empty or vol_df.empty:
            log.error(f"Failed to get component data for {market}")
            return None

        osc = self._calc(close_df, vol_df)
        if len(osc) == 0:
            log.error(f"Oscillator calculation failed for {market}")
            return None

        osc_pct = osc * 100

        return {
            "market": market,
            "dates": idx_df["날짜"].dt.strftime("%Y-%m-%d").tolist(),
            "index": idx_df["종가"].tolist(),
            "oscillator": osc_pct.tolist(),
            "stats": {
                "mean": float(np.nanmean(osc_pct)),
                "max": float(np.nanmax(osc_pct)),
                "min": float(np.nanmin(osc_pct)),
                "latest": float(osc_pct[-1]) if len(osc_pct) > 0 else 0,
                "count": int(len(osc_pct))
            }
        }


def get_market_oscillator(market: str, start: str, end: str) -> str:
    """
    Get market overbought/oversold oscillator.

    Args:
        market: "KOSPI" or "KOSDAQ"
        start: Start date (YYYYMMDD)
        end: End date (YYYYMMDD)

    Returns: JSON {
        "market": "KOSPI",
        "dates": [...],
        "index": [...],
        "oscillator": [...],
        "stats": {...}
    }
    """
    if market not in MARKETS:
        return err_json(f"Invalid market: {market}. Use KOSPI or KOSDAQ")

    try:
        osc = Oscillator(start, end)
        result = osc.analyze(market)

        if result:
            return to_json(result)
        else:
            return err_json(f"Analysis failed for {market}")

    except RuntimeError as e:
        # KIS API not configured
        log.error("KIS API error: %s", e)
        return err_json(str(e), error_type="api_not_configured")
    except ValueError as e:
        log.error("Validation error: %s", e)
        return err_json(str(e), error_type="validation_error")
    except Exception as e:
        log.error("get_market_oscillator error: %s", e)
        return err_json(str(e))


def clear_cache():
    """Clear the data cache."""
    _cache.clear()
    log.info("Market data cache cleared")


# Backward compatibility alias (used by MarketIndexPyClient)
fetch_recent_days = fetch_recent
