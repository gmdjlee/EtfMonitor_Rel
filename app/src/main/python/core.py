"""
Core utilities for EtfMonitor Python modules.
HTTP client, date utilities, and common functions.
"""
import json
import time
import logging
import sys
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Union
import requests
from pykrx import stock

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


def market_date() -> str:
    """Get latest market date (most recent business day).

    Uses pykrx's get_nearest_business_day_in_a_week for efficient lookup.
    Falls back to manual search if the new API is unavailable.
    """
    try:
        # Use pykrx's built-in business day function (v1.1.1+)
        result = stock.get_nearest_business_day_in_a_week(prev=True)
        if result:
            return result
    except (AttributeError, Exception):
        pass

    # Fallback: Try up to 7 days back to find a valid market date
    for i in range(7):
        d = days_ago(i)
        try:
            tickers = stock.get_market_ticker_list(d, market="KOSPI")
            if tickers is not None and len(list(tickers)) > 0:
                return d
        except Exception:
            continue
    # Fallback to yesterday if nothing found
    return days_ago(1)


def is_business_day(date_str: str) -> bool:
    """Check if date is a business day."""
    try:
        df = stock.get_market_ohlcv(date_str, date_str, REF_TICKER)
        return not df.empty
    except Exception:
        return False


def get_business_days(start: str, end: str) -> str:
    """Get business days in range as JSON string."""
    try:
        s, e = parse_date(start), parse_date(end)
        if not s or not e or s > e:
            return to_json([])

        days = []
        cur = s
        while cur <= e:
            d = cur.strftime("%Y%m%d")
            if is_business_day(d):
                days.append(d)
            cur += timedelta(days=1)
        return to_json(days)
    except Exception:
        return to_json([])


# Stock utilities
def get_tickers(market: Optional[str] = None, date: Optional[str] = None) -> List[str]:
    """Get stock tickers for market(s)."""
    d = date or market_date()
    try:
        if market and market in MARKETS:
            return list(stock.get_market_ticker_list(d, market=market))
        # All markets
        kospi = list(stock.get_market_ticker_list(d, market="KOSPI"))
        kosdaq = list(stock.get_market_ticker_list(d, market="KOSDAQ"))
        return kospi + kosdaq
    except Exception:
        return []


def get_name(ticker: str) -> str:
    """Get stock name by ticker."""
    if not ticker:
        return ""
    if ticker == CASH_TICKER:
        return "원화예금"
    try:
        name = stock.get_market_ticker_name(ticker)
        return str(name).strip() if name else ""
    except Exception:
        return ""


def get_etf_tickers(date: Optional[str] = None) -> List[str]:
    """Get ETF tickers."""
    d = date or market_date()
    try:
        tickers = stock.get_etf_ticker_list(d)
        return [str(t) for t in tickers] if tickers else []
    except Exception:
        return []


def get_etf_name(ticker: str) -> str:
    """Get ETF name by ticker."""
    try:
        name = stock.get_etf_ticker_name(ticker)
        return str(name).strip() if name else ""
    except Exception:
        return ""


# Sector classification (pykrx 1.1.1+)
def get_sector_classifications(date: Optional[str] = None, market: str = "KOSPI") -> Dict[str, str]:
    """
    Get sector classifications for stocks.

    Args:
        date: Date in YYYYMMDD format (default: latest market date)
        market: "KOSPI" or "KOSDAQ"

    Returns: Dict mapping ticker to sector name
    """
    d = date or market_date()
    try:
        df = stock.get_market_sector_classifications(d, market)
        if df is None or df.empty:
            return {}
        # DataFrame index is ticker, columns include sector info
        return {str(ticker): str(row.get("업종명", "")) for ticker, row in df.iterrows()}
    except (AttributeError, Exception) as e:
        get_logger(__name__).warning("get_sector_classifications error: %s", e)
        return {}


def get_sector_list(date: Optional[str] = None, market: str = "KOSPI") -> List[str]:
    """
    Get unique sector names for a market.

    Args:
        date: Date in YYYYMMDD format (default: latest market date)
        market: "KOSPI" or "KOSDAQ"

    Returns: List of unique sector names
    """
    classifications = get_sector_classifications(date, market)
    return list(set(classifications.values())) if classifications else []


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
