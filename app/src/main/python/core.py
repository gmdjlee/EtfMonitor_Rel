"""
Core utilities for EtfMonitor Python modules.
HTTP client, date utilities, and common functions.

Requires KIS API credentials to be configured in Settings.
Uses KIS Open API as the sole data source.

Improvements in v2.1:
- Better market_date() with weekend handling
- Error vs empty result distinction
- Improved logging
"""
import json
import time
import logging
import sys
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Union, Tuple
from dataclasses import dataclass
from enum import Enum
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


# Result types for better error handling
class ResultStatus(Enum):
    SUCCESS = "success"
    ERROR = "error"
    EMPTY = "empty"
    API_NOT_CONFIGURED = "api_not_configured"


@dataclass
class DataResult:
    """데이터 조회 결과를 담는 데이터 클래스."""
    status: ResultStatus
    data: Any = None
    message: str = ""

    @classmethod
    def success(cls, data: Any) -> "DataResult":
        return cls(status=ResultStatus.SUCCESS, data=data)

    @classmethod
    def empty(cls, message: str = "No data") -> "DataResult":
        return cls(status=ResultStatus.EMPTY, message=message)

    @classmethod
    def error(cls, message: str) -> "DataResult":
        return cls(status=ResultStatus.ERROR, message=message)

    @classmethod
    def not_configured(cls) -> "DataResult":
        return cls(
            status=ResultStatus.API_NOT_CONFIGURED,
            message="KIS API not configured. Please configure KIS credentials in Settings."
        )

    @property
    def is_success(self) -> bool:
        return self.status == ResultStatus.SUCCESS

    @property
    def is_error(self) -> bool:
        return self.status in (ResultStatus.ERROR, ResultStatus.API_NOT_CONFIGURED)


# Logger setup
_loggers: Dict[str, logging.Logger] = {}


def get_logger(name: str) -> logging.Logger:
    """Get or create logger for module."""
    if name not in _loggers:
        logger = logging.getLogger(name)
        if not logger.handlers:
            logger.setLevel(logging.INFO)
            h = logging.StreamHandler(sys.stderr)
            h.setFormatter(logging.Formatter(
                '[%(asctime)s][%(name)s] %(levelname)s: %(message)s',
                datefmt='%H:%M:%S'
            ))
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

# 한국 공휴일 (매년 업데이트 필요)
# 2024-2025년 공휴일 기준
KOREAN_HOLIDAYS = {
    # 2024
    "20240101", "20240209", "20240210", "20240211", "20240212",  # 신정, 설연휴
    "20240301", "20240410", "20240505", "20240506", "20240515",  # 삼일절, 총선, 어린이날, 대체휴일, 부처님오신날
    "20240606", "20240815", "20240916", "20240917", "20240918",  # 현충일, 광복절, 추석연휴
    "20241003", "20241009", "20241225",  # 개천절, 한글날, 성탄절
    # 2025
    "20250101", "20250128", "20250129", "20250130",  # 신정, 설연휴
    "20250301", "20250505", "20250506", "20250606",  # 삼일절, 어린이날, 대체휴일, 현충일
    "20250815", "20251003", "20251005", "20251006", "20251007",  # 광복절, 개천절, 추석연휴
    "20251009", "20251225",  # 한글날, 성탄절
}


class HttpClient:
    """HTTP client with retry logic."""

    def __init__(self, base_headers: Optional[Dict] = None):
        self.session = requests.Session()
        self.headers = base_headers or {"User-Agent": USER_AGENT}
        self._logger = get_logger("HttpClient")

    def get(self, url: str, **kwargs) -> Optional[requests.Response]:
        return self._request("GET", url, **kwargs)

    def post(self, url: str, **kwargs) -> Optional[requests.Response]:
        return self._request("POST", url, **kwargs)

    def _request(self, method: str, url: str, retries: int = MAX_RETRIES,
                 timeout: int = TIMEOUT, **kwargs) -> Optional[requests.Response]:
        kwargs.setdefault("headers", self.headers)
        kwargs.setdefault("timeout", timeout)

        last_error = None
        for attempt in range(1, retries + 1):
            try:
                resp = self.session.request(method, url, **kwargs)
                resp.raise_for_status()
                return resp
            except requests.exceptions.Timeout as e:
                last_error = e
                self._logger.warning(f"Timeout (attempt {attempt}/{retries}): {url}")
                if attempt < retries:
                    time.sleep(RETRY_DELAY * attempt)
                    continue
            except requests.exceptions.HTTPError as e:
                last_error = e
                self._logger.warning(f"HTTP error {e.response.status_code} (attempt {attempt}/{retries}): {url}")
                # 5xx 에러는 재시도
                if e.response.status_code >= 500 and attempt < retries:
                    time.sleep(RETRY_DELAY * attempt)
                    continue
                return None
            except requests.exceptions.RequestException as e:
                last_error = e
                self._logger.warning(f"Request error (attempt {attempt}/{retries}): {e}")
                if attempt < retries:
                    time.sleep(RETRY_DELAY * attempt)
                    continue

        self._logger.error(f"Request failed after {retries} attempts: {last_error}")
        return None

    def get_json(self, url: str, **kwargs) -> Optional[Dict]:
        resp = self.get(url, **kwargs)
        if resp:
            try:
                return resp.json()
            except json.JSONDecodeError as e:
                self._logger.error(f"JSON decode error: {e}")
        return None

    def post_json(self, url: str, **kwargs) -> Optional[Dict]:
        resp = self.post(url, **kwargs)
        if resp:
            try:
                return resp.json()
            except json.JSONDecodeError as e:
                self._logger.error(f"JSON decode error: {e}")
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


def _is_weekend(date_str: str) -> bool:
    """Check if date is weekend (Saturday=5, Sunday=6)."""
    try:
        dt = datetime.strptime(date_str, "%Y%m%d")
        return dt.weekday() >= 5
    except ValueError:
        return False


def _is_holiday(date_str: str) -> bool:
    """Check if date is a known Korean holiday."""
    return date_str in KOREAN_HOLIDAYS


def _get_previous_business_day(date_str: str) -> str:
    """Get the previous business day (not weekend, not holiday)."""
    try:
        dt = datetime.strptime(date_str, "%Y%m%d")

        # 최대 10일 전까지 탐색 (연휴 고려)
        for _ in range(10):
            dt = dt - timedelta(days=1)
            candidate = dt.strftime("%Y%m%d")

            if not _is_weekend(candidate) and not _is_holiday(candidate):
                return candidate

        # 10일 내에 영업일을 못 찾으면 그냥 반환
        return dt.strftime("%Y%m%d")
    except ValueError:
        return date_str


def market_date() -> str:
    """
    Get latest market date (most recent business day).

    Uses weekend/holiday check first, then KIS API for validation.
    Handles weekends properly even without KIS API.
    """
    now = datetime.now()
    current_date = now.strftime("%Y%m%d")

    # 오늘이 주말이면 금요일로
    if now.weekday() >= 5:  # Saturday or Sunday
        days_to_subtract = now.weekday() - 4  # 토요일=1, 일요일=2
        current_date = (now - timedelta(days=days_to_subtract)).strftime("%Y%m%d")

    # 공휴일이면 이전 영업일로
    if _is_holiday(current_date):
        current_date = _get_previous_business_day(current_date)

    # KIS API로 검증 (가능한 경우)
    if is_kis_available():
        client = get_kis_client()

        # 최대 7일 전까지 탐색
        for i in range(7):
            d = (datetime.strptime(current_date, "%Y%m%d") - timedelta(days=i)).strftime("%Y%m%d")
            try:
                df = client.get_stock_ohlcv(REF_TICKER, d, d)
                if df is not None and not df.empty:
                    return d
            except Exception:
                continue

    return current_date


def is_business_day(date_str: str) -> bool:
    """
    Check if date is a business day.

    Uses weekend/holiday check first, then KIS API if available.
    """
    # 주말 체크
    if _is_weekend(date_str):
        return False

    # 공휴일 체크
    if _is_holiday(date_str):
        return False

    # KIS API로 추가 검증
    if is_kis_available():
        try:
            client = get_kis_client()
            df = client.get_stock_ohlcv(REF_TICKER, date_str, date_str)
            return df is not None and not df.empty
        except Exception:
            pass

    # KIS 없으면 주말/공휴일 아닌 날은 영업일로 간주
    return True


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
        List of ticker strings (empty list on error)
    """
    global _stock_name_cache

    if not is_kis_available():
        get_logger("core").error("KIS API not configured")
        return []

    try:
        client = get_kis_client()
        if market and market.upper() in ["KOSPI", "KOSDAQ"]:
            df = client.download_stock_master(market.lower())
            if df.empty:
                return []
            # Cache names for later use
            for _, row in df.iterrows():
                _stock_name_cache[row["ticker"]] = row["name"]
            return df["ticker"].tolist()
        else:
            # All markets
            df = client.get_all_stocks()
            if df.empty:
                return []
            for _, row in df.iterrows():
                _stock_name_cache[row["ticker"]] = row["name"]
            return df["ticker"].tolist()
    except Exception as e:
        get_logger("core").error(f"Failed to get tickers: {e}")
        return []


def get_tickers_with_result(market: Optional[str] = None) -> DataResult:
    """
    Get stock tickers with detailed result status.

    Args:
        market: "KOSPI", "KOSDAQ", or None for all

    Returns:
        DataResult with tickers list or error info
    """
    global _stock_name_cache

    if not is_kis_available():
        return DataResult.not_configured()

    try:
        client = get_kis_client()
        if market and market.upper() in ["KOSPI", "KOSDAQ"]:
            df = client.download_stock_master(market.lower())
        else:
            df = client.get_all_stocks()

        if df.empty:
            return DataResult.empty(f"No tickers found for market: {market or 'ALL'}")

        # Cache names
        for _, row in df.iterrows():
            _stock_name_cache[row["ticker"]] = row["name"]

        return DataResult.success(df["ticker"].tolist())

    except Exception as e:
        return DataResult.error(f"Failed to get tickers: {e}")


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
        if not df.empty:
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


def err_json(msg: str, error_type: str = "error") -> str:
    """Create error JSON response with type."""
    return to_json({"error": True, "message": msg, "error_type": error_type})


def success_json(data: Any, message: str = "") -> str:
    """Create success JSON response."""
    return to_json({"error": False, "data": data, "message": message})


# Number parsing
def parse_num(text: str) -> float:
    """Parse number from text (handles Korean format)."""
    try:
        cleaned = text.replace(",", "").replace("억원", "").replace("억", "").strip()
        return float(cleaned) if cleaned and cleaned != "-" else 0.0
    except (ValueError, AttributeError):
        return 0.0


# Safe value extraction
def safe_int(value: Any, default: int = 0) -> int:
    """Safely convert value to int."""
    try:
        if value is None or value == "":
            return default
        return int(value)
    except (ValueError, TypeError):
        return default


def safe_float(value: Any, default: float = 0.0) -> float:
    """Safely convert value to float."""
    try:
        if value is None or value == "":
            return default
        return float(value)
    except (ValueError, TypeError):
        return default
