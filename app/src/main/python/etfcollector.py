"""
ETF data collection module.

Uses KIS Open API (preferred) with pykrx fallback.

Phase 3 of KIS API Migration:
- get_etf_list_with_names(): KIS API with pykrx fallback
- get_etf_holdings(): KIS API with pykrx fallback
"""
import json
from datetime import datetime
from typing import Any, Dict, List
from pykrx import stock

from core import (
    get_logger,
    get_etf_tickers,
    get_etf_name as core_get_etf_name,
    to_json,
    is_kis_available,
    get_kis_client
)

log = get_logger(__name__)


def get_etf_list_with_names(date: str, include_json: str = "[]", exclude_json: str = "[]") -> str:
    """
    Get filtered ETF list.

    Filtering rules:
    1. Must contain '액티브' (required)
    2. Must contain at least one theme keyword from include_json
    3. Must not contain any keyword from exclude_json

    Uses KIS API if available, falls back to pykrx.

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

    # Get ETF list - try KIS API first, fallback to pykrx
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


def _get_etf_list_internal(date: str) -> List[Dict[str, str]]:
    """
    Internal function to get ETF list with names.

    Uses KIS API if available, falls back to pykrx.

    Returns: List of {"ticker": ..., "name": ...}
    """
    # Try KIS API first
    if is_kis_available():
        try:
            client = get_kis_client()
            df = client.get_etf_list()
            if not df.empty:
                log.info("Using KIS API for ETF list")
                return [{"ticker": row["ticker"], "name": row["name"]}
                        for _, row in df.iterrows() if row["ticker"]]
        except Exception as e:
            log.warning(f"KIS API failed, falling back to pykrx: {e}")

    # Fallback to pykrx
    log.info("Using pykrx for ETF list")
    try:
        tickers = stock.get_etf_ticker_list(date)
        if not tickers:
            return []

        result = []
        for t in tickers:
            try:
                name = stock.get_etf_ticker_name(t)
                if name:
                    result.append({"ticker": str(t), "name": str(name)})
            except Exception:
                continue
        return result
    except Exception as e:
        log.error(f"pykrx ETF list failed: {e}")
        return []


def get_etf_list(date: str) -> str:
    """
    Get all ETF tickers for a date.

    Returns: JSON [ticker, ...]
    """
    try:
        datetime.strptime(date, '%Y%m%d')
        tickers = get_etf_tickers(date)
        log.info("ETF list: %d tickers", len(tickers))
        return to_json(tickers)
    except Exception as e:
        log.error("get_etf_list error: %s", e)
        return to_json([])


def get_etf_name(ticker: str) -> str:
    """Get ETF name by ticker."""
    return core_get_etf_name(ticker) if ticker else ""


def get_etf_holdings(ticker: str, date: str) -> str:
    """
    Get ETF portfolio holdings.

    Uses KIS API if available, falls back to pykrx.

    Returns: JSON [{"ticker": "...", "weight": ..., "amount": ...}, ...]
    """
    try:
        datetime.strptime(date, '%Y%m%d')
    except ValueError as e:
        log.error("Invalid date format: %s", e)
        return to_json([])

    # Try KIS API first
    if is_kis_available():
        try:
            client = get_kis_client()
            df = client.get_etf_holdings(ticker)

            if not df.empty:
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
        except Exception as e:
            log.warning(f"KIS API failed for holdings, falling back to pykrx: {e}")

    # Fallback to pykrx
    log.info("Using pykrx for ETF holdings: %s", ticker)
    try:
        df = stock.get_etf_portfolio_deposit_file(ticker, date)

        if df is None or df.empty or '비중' not in df.columns:
            return to_json([])

        holdings: List[Dict[str, Any]] = []
        for stk, row in df.iterrows():
            amt = float(row.get('금액', 0)) if '금액' in df.columns else 0.0
            holdings.append({
                "ticker": str(stk),
                "weight": float(row['비중']),
                "amount": amt
            })

        log.info("ETF holdings %s: %d items (pykrx)", ticker, len(holdings))
        return to_json(holdings)

    except Exception as e:
        log.error("get_etf_holdings error (%s): %s", ticker, e)
        return to_json([])
