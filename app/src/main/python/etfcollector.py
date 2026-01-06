"""
ETF data collection module.

Uses KIS API exclusively - no pykrx dependency.
Requires KIS API credentials to be configured in Settings.
"""
import json
from datetime import datetime
from typing import Any, Dict, List

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


def get_etf_list_with_names(date: str, include_json: str = "[]", exclude_json: str = "[]") -> str:
    """
    Get filtered ETF list via KIS API.

    Filtering rules:
    1. Must contain '액티브' (required)
    2. Must contain at least one theme keyword from include_json
    3. Must not contain any keyword from exclude_json

    Returns: JSON [{"ticker": "...", "name": "..."}, ...]
    """
    try:
        datetime.strptime(date, '%Y%m%d')
        include = json.loads(include_json) if include_json else []
        exclude = json.loads(exclude_json) if exclude_json else []
    except (ValueError, json.JSONDecodeError) as e:
        log.error("Input error: %s", e)
        return to_json([])

    log.info("ETF filter: include=%d, exclude=%d", len(include), len(exclude))

    # Theme keywords (excluding '액티브')
    themes = [k for k in include if k != '액티브']

    try:
        # Get ETF list via KIS API
        etf_list = _get_etf_list_internal(date)
        if not etf_list:
            return to_json([])

        result: List[Dict[str, str]] = []

        for etf in etf_list:
            try:
                ticker = etf["ticker"]
                name = etf["name"]

                if not name:
                    continue

                # Step 1: Must have '액티브'
                if '액티브' not in name:
                    continue

                # Step 2: Exclude keywords check
                if any(kw in name for kw in exclude):
                    continue

                # Step 3: Must have theme keyword (or none required)
                if themes and not any(kw in name for kw in themes):
                    continue

                result.append({"ticker": ticker, "name": name})

            except Exception:
                continue

        log.info("ETF filter result: %d/%d passed", len(result), len(etf_list))
        return to_json(result)

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e))
    except Exception as e:
        log.error("get_etf_list_with_names error: %s", e)
        return to_json([])


def _get_etf_list_internal(date: str) -> List[Dict[str, str]]:
    """
    Internal function to get ETF list with names via KIS API.

    Returns: List of {"ticker": ..., "name": ...}
    """
    client = _ensure_kis_client()
    df = client.get_etf_list()

    if df.empty:
        log.warning("No ETF list from KIS API")
        return []

    log.info("Using KIS API for ETF list: %d ETFs", len(df))
    return [
        {"ticker": row["ticker"], "name": row["name"]}
        for _, row in df.iterrows() if row["ticker"]
    ]


def get_etf_list(date: str) -> str:
    """
    Get all ETF tickers for a date via KIS API.

    Returns: JSON [ticker, ...]
    """
    try:
        datetime.strptime(date, '%Y%m%d')
        tickers = get_etf_tickers(date)
        log.info("ETF list: %d tickers", len(tickers))
        return to_json(tickers)
    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e))
    except Exception as e:
        log.error("get_etf_list error: %s", e)
        return to_json([])


def get_etf_name(ticker: str) -> str:
    """Get ETF name by ticker via KIS API."""
    return core_get_etf_name(ticker) if ticker else ""


def get_etf_holdings(ticker: str, date: str) -> str:
    """
    Get ETF portfolio holdings via KIS API.

    Returns: JSON [{"ticker": "...", "weight": ..., "amount": ...}, ...]
    """
    try:
        datetime.strptime(date, '%Y%m%d')
    except ValueError as e:
        log.error("Invalid date format: %s", e)
        return to_json([])

    try:
        client = _ensure_kis_client()
        df = client.get_etf_holdings(ticker)

        if df.empty:
            log.warning("No holdings data for %s", ticker)
            return to_json([])

        log.info("Using KIS API for ETF holdings: %s", ticker)
        holdings: List[Dict[str, Any]] = []
        for _, row in df.iterrows():
            holdings.append({
                "ticker": str(row.get("ticker", "")),
                "weight": float(row.get("weight", 0)),
                "amount": float(row.get("amount", 0))
            })

        log.info("ETF holdings %s: %d items (KIS API)", ticker, len(holdings))
        return to_json(holdings)

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e))
    except Exception as e:
        log.error("get_etf_holdings error (%s): %s", ticker, e)
        return to_json([])
