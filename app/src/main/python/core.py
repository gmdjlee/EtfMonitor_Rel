"""
Core utilities for EtfMonitor Python modules.
HTTP client, date utilities, and common functions.

Requires KIS API credentials to be configured in Settings.
Uses KIS Open API as the sole data source.
"""
import json
import time
import logging
import sys
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Union
import requests

# KIS API client reference
_kis_client = None


def set_kis_client(client):
    """Set the global KIS client instance for use in core functions."""
    global _kis_client
    _kis_client = client


def get_kis_client():
    """Get the global KIS client if available."""
    return _kis_client


def is_kis_available() -> bool:
    """Check if KIS client is available and initialized."""
    return _kis_client is not None


# Logger setup
_loggers: Dict[str, logging.Logger] = {}


def get_logger(name: str) -> logging.Logger:
    """Get or create logger for module."""
    if name not in _loggers:
        logger = logging.getLogger(name)
        if not logger.handlers:
            logger.setLevel(logging.INFO)
            h = logging.StreamHandler(sys.stderr)
            h.setFormatter(logging.Formatter('[%(name)s] %(levelname)s: %(message)s'))
            logger.addHandler(h)
        _loggers[name] = logger
    return _loggers[name]


# Constants
TIMEOUT = 15
MAX_RETRIES = 3
RETRY_DELAY = 2
REQ_DELAY = 0.5
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
CASH_TICKER = "010010"  # 원화예금
REF_TICKER = "005930"   # 삼성전자 (영업일 판단용)

# Market config
MARKETS = {
    "KOSPI": {"idx": "1001", "comp": "1028", "name": "코스피"},
    "KOSDAQ": {"idx": "2001", "comp": "2203", "name": "코스닥"}
}


class HttpClient:
    """HTTP client with retry logic."""

    def __init__(self, base_headers: Optional[Dict] = None):
        self.session = requests.Session()
        self.headers = base_headers or {"User-Agent": USER_AGENT}

    def get(self, url: str, **kwargs) -> Optional[requests.Response]:
        return self._request("GET", url, **kwargs)

    def post(self, url: str, **kwargs) -> Optional[requests.Response]:
        return self._request("POST", url, **kwargs)

    def _request(self, method: str, url: str, retries: int = MAX_RETRIES,
                 timeout: int = TIMEOUT, **kwargs) -> Optional[requests.Response]:
        kwargs.setdefault("headers", self.headers)
        kwargs.setdefault("timeout", timeout)

        for attempt in range(1, retries + 1):
            try:
                resp = self.session.request(method, url, **kwargs)
                resp.raise_for_status()
                return resp
            except requests.exceptions.Timeout:
                if attempt < retries:
                    time.sleep(RETRY_DELAY * attempt)
                    continue
            except requests.exceptions.HTTPError:
                return None
            except requests.exceptions.RequestException:
                if attempt < retries:
                    time.sleep(RETRY_DELAY * attempt)
                    continue
        return None

    def get_json(self, url: str, **kwargs) -> Optional[Dict]:
        resp = self.get(url, **kwargs)
        if resp:
            try:
                return resp.json()
            except json.JSONDecodeError:
                pass
        return None

    def post_json(self, url: str, **kwargs) -> Optional[Dict]:
        resp = self.post(url, **kwargs)
        if resp:
            try:
                return resp.json()
            except json.JSONDecodeError:
                pass
        return None


# Date utilities
def parse_date(s: str) -> Optional[datetime]:
    """Parse date string to datetime."""
    for fmt in ("%Y%m%d", "%Y-%m-%d", "%Y.%m.%d", "%Y/%m/%d"):
        try:
            return datetime.strptime(s.strip(), fmt)
        except ValueError:
            continue
    return None


def fmt_date(d: Union[str, datetime], fmt: str = "%Y%m%d") -> str:
    """Format date to string."""
    if isinstance(d, str):
        dt = parse_date(d)
        return dt.strftime(fmt) if dt else d
    return d.strftime(fmt)


def to_iso(d: Union[str, datetime]) -> str:
    """Convert to YYYY-MM-DD format."""
    return fmt_date(d, "%Y-%m-%d")


def to_ymd(d: Union[str, datetime]) -> str:
    """Convert to YYYYMMDD format."""
    return fmt_date(d, "%Y%m%d")


def today() -> str:
    """Get today as YYYYMMDD."""
    return datetime.now().strftime("%Y%m%d")


def days_ago(n: int) -> str:
    """Get date n days ago as YYYYMMDD."""
    return (datetime.now() - timedelta(days=n)).strftime("%Y%m%d")


# Cache for business days to avoid repeated API calls
_business_days_cache: Dict[str, List[str]] = {}


def _fetch_business_days_from_ohlcv(start: str, end: str) -> List[str]:
    """
    Fetch business days by getting OHLCV data for a date range.
    This is more efficient than checking each date individually.
    """
    if not is_kis_available():
        return []

    try:
        client = get_kis_client()
        # Fetch stock data for the entire range (will only have data for trading days)
        df = client.get_stock_ohlcv(REF_TICKER, start, end)
        if df is not None and not df.empty:
            # Extract dates from the index (which are the actual trading days)
            return [d.strftime("%Y%m%d") for d in df.index]
    except Exception as e:
        get_logger("core").warning(f"Failed to fetch business days via OHLCV: {e}")

    return []


def market_date() -> str:
    """Get latest market date (most recent business day).

    Uses KIS API to check for valid trading days.
    """
    if not is_kis_available():
        # If KIS not available, return yesterday as fallback
        return days_ago(1)

    # Try to get OHLCV data for the past 7 days
    start = days_ago(7)
    end = today()

    try:
        days = _fetch_business_days_from_ohlcv(start, end)
        if days:
            return days[-1]  # Return the most recent trading day
    except Exception:
        pass

    # Fallback to yesterday if nothing found
    return days_ago(1)


def is_business_day(date_str: str) -> bool:
    """Check if date is a business day using cached data."""
    # Check cache first
    for cache_key, days in _business_days_cache.items():
        if date_str in days:
            return True

    if not is_kis_available():
        # Cannot determine without KIS API, assume weekdays are business days
        dt = parse_date(date_str)
        if dt:
            return dt.weekday() < 5  # Monday-Friday
        return True

    # Fetch a small range around the date to cache nearby dates
    dt = parse_date(date_str)
    if not dt:
        return False

    start = (dt - timedelta(days=7)).strftime("%Y%m%d")
    end = (dt + timedelta(days=7)).strftime("%Y%m%d")

    try:
        days = _fetch_business_days_from_ohlcv(start, end)
        if days:
            cache_key = f"{start}-{end}"
            _business_days_cache[cache_key] = days
            return date_str in days
    except Exception:
        pass

    return False


def get_business_days(start: str, end: str) -> str:
    """Get business days in range as JSON string."""
    try:
        s, e = parse_date(start), parse_date(end)
        if not s or not e or s > e:
            return to_json([])

        start_str = s.strftime("%Y%m%d")
        end_str = e.strftime("%Y%m%d")

        # Try to get business days efficiently via OHLCV data
        if is_kis_available():
            days = _fetch_business_days_from_ohlcv(start_str, end_str)
            if days:
                # Cache for future use
                cache_key = f"{start_str}-{end_str}"
                _business_days_cache[cache_key] = days
                return to_json(days)

        # Fallback: return weekdays (Mon-Fri) - less accurate but works offline
        days = []
        cur = s
        while cur <= e:
            if cur.weekday() < 5:  # Monday=0, Sunday=6
                days.append(cur.strftime("%Y%m%d"))
            cur += timedelta(days=1)
        return to_json(days)

    except Exception:
        return to_json([])


# Stock name cache (populated from KIS stock master)
_stock_name_cache: Dict[str, str] = {}


# Stock utilities
def get_tickers(market: Optional[str] = None, date: Optional[str] = None) -> List[str]:
    """
    Get stock tickers for market(s) via KIS API.

    Args:
        market: "KOSPI", "KOSDAQ", or None for all
        date: Ignored (for backward compatibility)

    Returns:
        List of ticker strings
    """
    global _stock_name_cache

    if not is_kis_available():
        get_logger("core").error("KIS API not configured")
        return []

    try:
        client = get_kis_client()
        if market and market.upper() in ["KOSPI", "KOSDAQ"]:
            df = client.download_stock_master(market.lower())
            # Cache names for later use
            for _, row in df.iterrows():
                _stock_name_cache[row["ticker"]] = row["name"]
            return df["ticker"].tolist()
        else:
            # All markets
            df = client.get_all_stocks()
            for _, row in df.iterrows():
                _stock_name_cache[row["ticker"]] = row["name"]
            return df["ticker"].tolist()
    except Exception as e:
        get_logger("core").error(f"Failed to get tickers: {e}")
        return []


def get_name(ticker: str) -> str:
    """
    Get stock name by ticker via KIS API.

    Args:
        ticker: Stock ticker (e.g., "005930")

    Returns:
        Stock name or empty string if not found
    """
    if not ticker:
        return ""
    if ticker == CASH_TICKER:
        return "원화예금"

    # Check cache first
    if ticker in _stock_name_cache:
        return _stock_name_cache[ticker]

    if not is_kis_available():
        return ""

    # Try KIS API
    try:
        client = get_kis_client()
        name = client.get_stock_name(ticker)
        if name:
            _stock_name_cache[ticker] = name
            return name
    except Exception as e:
        get_logger("core").warning(f"Failed to get stock name for {ticker}: {e}")

    return ""


def get_etf_tickers(date: Optional[str] = None) -> List[str]:
    """
    Get ETF tickers via KIS API.

    Args:
        date: Ignored (for backward compatibility)

    Returns:
        List of ETF ticker strings
    """
    if not is_kis_available():
        get_logger("core").error("KIS API not configured")
        return []

    try:
        client = get_kis_client()
        df = client.get_etf_list()
        return df["ticker"].tolist() if not df.empty else []
    except Exception as e:
        get_logger("core").error(f"Failed to get ETF list: {e}")
        return []


def get_etf_name(ticker: str) -> str:
    """
    Get ETF name by ticker via KIS API.

    Args:
        ticker: ETF ticker

    Returns:
        ETF name or empty string if not found
    """
    if not is_kis_available():
        return ""

    try:
        client = get_kis_client()
        # Try to get from ETF list first (may be cached)
        df = client.get_etf_list()
        match = df[df["ticker"] == ticker]
        if not match.empty:
            return match.iloc[0]["name"]
        # Try stock info as fallback
        return client.get_stock_name(ticker)
    except Exception as e:
        get_logger("core").warning(f"Failed to get ETF name for {ticker}: {e}")
        return ""


# JSON helpers
def to_json(data: Any, **kwargs) -> str:
    """Convert to JSON string."""
    kwargs.setdefault("ensure_ascii", False)
    return json.dumps(data, **kwargs)


def err_json(msg: str) -> str:
    """Create error JSON response."""
    return to_json({"error": msg})


# Number parsing
def parse_num(text: str) -> float:
    """Parse number from text (handles Korean format)."""
    try:
        cleaned = text.replace(",", "").replace("억원", "").replace("억", "").strip()
        return float(cleaned) if cleaned and cleaned != "-" else 0.0
    except (ValueError, AttributeError):
        return 0.0
