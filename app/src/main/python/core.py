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
