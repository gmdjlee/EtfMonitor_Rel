"""
ETF data collection module.

Requires KIS API credentials to be configured in Settings.
Uses KIS Open API as the sole data source.

Improvements in v2.1:
- Better error handling with detailed logging
- Consistent return types
- Input validation
"""
import json
from datetime import datetime
from typing import Any, Dict, List, Optional

from core import (
    get_logger,
    get_etf_tickers,
    get_etf_name as core_get_etf_name,
    to_json,
    err_json,
    is_kis_available,
    get_kis_client
)

log = get_logger(__name__)


def _ensure_kis_client():
    """Ensure KIS client is available, raise error if not."""
    if not is_kis_available():
        raise RuntimeError("KIS API not configured. Please configure KIS credentials in Settings.")
    return get_kis_client()


def _validate_date(date_str: str) -> bool:
    """Validate date string format (YYYYMMDD)."""
    try:
        datetime.strptime(date_str, '%Y%m%d')
        return True
    except (ValueError, TypeError):
        return False


def get_etf_list_with_names(date: str, include_json: str = "[]", exclude_json: str = "[]") -> str:
    """
    Get filtered ETF list via KIS API.

    Filtering rules:
    1. Must contain '액티브' (required)
    2. Must contain at least one theme keyword from include_json
    3. Must not contain any keyword from exclude_json

    Args:
        date: Date string (YYYYMMDD)
        include_json: JSON array of keywords to include
        exclude_json: JSON array of keywords to exclude

    Returns: JSON [{"ticker": "...", "name": "..."}, ...]
             or {"error": true, "message": "..."} on error
    """
    # 입력 검증
    if not _validate_date(date):
        return err_json("Invalid date format. Use YYYYMMDD", "validation_error")

    try:
        include = json.loads(include_json) if include_json else []
        exclude = json.loads(exclude_json) if exclude_json else []
    except json.JSONDecodeError as e:
        log.error("JSON parse error: %s", e)
        return err_json(f"Invalid JSON format: {e}", "validation_error")

    log.info("ETF filter: include=%d, exclude=%d", len(include), len(exclude))

    # Theme keywords (excluding '액티브')
    themes = [k for k in include if k != '액티브']

    try:
        # Get ETF list via KIS API
        etf_list = _get_etf_list_internal(date)
        if not etf_list:
            log.warning("No ETF list returned")
            return to_json([])

        result: List[Dict[str, str]] = []
        filtered_count = {"no_active": 0, "excluded": 0, "no_theme": 0}

        for etf in etf_list:
            ticker = etf.get("ticker")
            name = etf.get("name")

            if not ticker or not name:
                continue

            # Step 1: Must have '액티브'
            if '액티브' not in name:
                filtered_count["no_active"] += 1
                continue

            # Step 2: Exclude keywords check
            if any(kw in name for kw in exclude):
                filtered_count["excluded"] += 1
                continue

            # Step 3: Must have theme keyword (or none required)
            if themes and not any(kw in name for kw in themes):
                filtered_count["no_theme"] += 1
                continue

            result.append({"ticker": ticker, "name": name})

        log.info("ETF filter result: %d/%d passed (no_active=%d, excluded=%d, no_theme=%d)",
                 len(result), len(etf_list),
                 filtered_count["no_active"], filtered_count["excluded"], filtered_count["no_theme"])
        return to_json(result)

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e), "api_not_configured")
    except Exception as e:
        log.error("get_etf_list_with_names error: %s", e)
        return err_json(f"ETF 목록 조회 오류: {e}")


def _get_etf_list_internal(date: str) -> List[Dict[str, str]]:
    """
    Internal function to get ETF list with names via KIS API.

    Args:
        date: Date string (ignored, for compatibility)

    Returns: List of {"ticker": ..., "name": ...}
    """
    client = _ensure_kis_client()
    df = client.get_etf_list()

    if df.empty:
        log.warning("No ETF list from KIS API")
        return []

    result = []
    active_count = 0
    for _, row in df.iterrows():
        ticker = row.get("ticker")
        name = row.get("name", "")
        if ticker:
            result.append({"ticker": ticker, "name": name})
            if '액티브' in name:
                active_count += 1

    log.info("Using KIS API for ETF list: %d total ETFs, %d with '액티브' keyword", len(result), active_count)

    # Log sample ETFs for debugging
    if result:
        samples = result[:5]
        log.debug("Sample ETFs: %s", [f"{e['ticker']}:{e['name'][:20]}" for e in samples])

    return result


def get_etf_list(date: str) -> str:
    """
    Get all ETF tickers for a date via KIS API.

    Args:
        date: Date string (YYYYMMDD)

    Returns: JSON [ticker, ...]
             or {"error": true, "message": "..."} on error
    """
    if not _validate_date(date):
        return err_json("Invalid date format. Use YYYYMMDD", "validation_error")

    try:
        tickers = get_etf_tickers(date)
        log.info("ETF list: %d tickers", len(tickers))
        return to_json(tickers)
    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e), "api_not_configured")
    except Exception as e:
        log.error("get_etf_list error: %s", e)
        return err_json(f"ETF 목록 조회 오류: {e}")


def get_etf_name(ticker: str) -> str:
    """
    Get ETF name by ticker via KIS API.

    Args:
        ticker: ETF ticker code

    Returns: ETF name or empty string
    """
    if not ticker or not isinstance(ticker, str):
        return ""
    return core_get_etf_name(ticker.strip())


def get_etf_holdings(ticker: str, date: str) -> str:
    """
    Get ETF portfolio holdings via KIS API.

    Args:
        ticker: ETF ticker code
        date: Date string (YYYYMMDD)

    Returns: JSON [{"ticker": "...", "weight": ..., "amount": ...}, ...]
             or {"error": true, "message": "..."} on error
    """
    # 입력 검증
    if not ticker or not isinstance(ticker, str):
        return err_json("ETF 코드가 필요합니다", "validation_error")

    ticker = ticker.strip()

    if not _validate_date(date):
        return err_json("Invalid date format. Use YYYYMMDD", "validation_error")

    try:
        client = _ensure_kis_client()
        df = client.get_etf_holdings(ticker)

        if df.empty:
            log.warning("No holdings data for %s", ticker)
            return to_json([])

        holdings: List[Dict[str, Any]] = []
        for _, row in df.iterrows():
            holdings.append({
                "ticker": str(row.get("ticker", "")),
                "name": str(row.get("name", "")),
                "weight": float(row.get("weight", 0) or 0),
                "amount": float(row.get("amount", 0) or 0),
                "quantity": int(row.get("quantity", 0) or 0)
            })

        log.info("ETF holdings %s: %d items (KIS API)", ticker, len(holdings))
        return to_json(holdings)

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e), "api_not_configured")
    except Exception as e:
        log.error("get_etf_holdings error (%s): %s", ticker, e)
        return err_json(f"ETF 구성종목 조회 오류: {e}")


def get_etf_info(ticker: str) -> str:
    """
    Get ETF information including name and current price.

    Args:
        ticker: ETF ticker code

    Returns: JSON {"ticker": ..., "name": ..., "price": ...}
             or {"error": true, "message": "..."} on error
    """
    if not ticker or not isinstance(ticker, str):
        return err_json("ETF 코드가 필요합니다", "validation_error")

    ticker = ticker.strip()

    try:
        client = _ensure_kis_client()
        info = client.get_stock_info(ticker)

        if info is None:
            return err_json(f"ETF 정보를 찾을 수 없습니다: {ticker}", "no_data")

        return to_json(info)

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e), "api_not_configured")
    except Exception as e:
        log.error("get_etf_info error (%s): %s", ticker, e)
        return err_json(f"ETF 정보 조회 오류: {e}")
