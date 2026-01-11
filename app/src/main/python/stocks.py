"""
Stock data collection and analysis module.
Unified module merging stockcollector, stock_data_fetcher, and stock_analyzer.

Version: 2.0 - Migrated from pykrx to KIS API
"""
import json
import logging
import sys
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional
import pandas as pd

from kis_client import get_client, is_client_initialized

# ========================================
# Logger setup (standalone)
# ========================================

_loggers: Dict[str, logging.Logger] = {}

def get_logger(name: str) -> logging.Logger:
    """Get or create logger."""
    if name not in _loggers:
        logger = logging.getLogger(name)
        if not logger.handlers:
            handler = logging.StreamHandler(sys.stderr)
            handler.setFormatter(logging.Formatter(
                '[%(name)s] %(levelname)s: %(message)s'
            ))
            logger.addHandler(handler)
            logger.setLevel(logging.INFO)
        _loggers[name] = logger
    return _loggers[name]

log = get_logger(__name__)

# Constants
MARKETS = ["KOSPI", "KOSDAQ", "ALL"]


def to_json(data: Any) -> str:
    """Convert data to JSON string."""
    return json.dumps(data, ensure_ascii=False, default=str)


def err_json(message: str) -> str:
    """Create error JSON response."""
    return json.dumps({"error": message}, ensure_ascii=False)


def market_date() -> str:
    """Get current market date (YYYYMMDD)."""
    return datetime.now().strftime("%Y%m%d")


# ========================================
# Stock List Functions
# ========================================

def get_stock_list(date: str, market: str = "KOSPI") -> str:
    """
    Get stock list for a market.

    Args:
        date: Date string (YYYYMMDD) - Note: KIS uses latest master, date is for compatibility
        market: Market name ("KOSPI" or "KOSDAQ")

    Returns:
        JSON [{"ticker": "...", "name": "..."}, ...]
    """
    try:
        # Validate date format
        datetime.strptime(date, '%Y%m%d')

        if market not in MARKETS:
            return to_json([])

        if not is_client_initialized():
            return err_json("KIS API 클라이언트가 초기화되지 않았습니다")

        client = get_client()

        # Get stocks for market
        if market == "ALL":
            df = client.get_all_stocks()
        else:
            df = client.download_stock_master(market.lower())

        if df.empty:
            return to_json([])

        result = []
        for _, row in df.iterrows():
            name = row.get("name", "")
            if name:
                result.append({"ticker": row["ticker"], "name": name})

        log.info("%s: %d stocks (%s)", market, len(result), date)
        return to_json(result)

    except ValueError as e:
        log.error("Invalid date format: %s", e)
        return to_json([])
    except RuntimeError as e:
        log.error("KIS client error: %s", e)
        return err_json(f"KIS API 오류: {e}")
    except Exception as e:
        log.error("get_stock_list error: %s", e)
        return to_json([])


def get_all_stocks(date: Optional[str] = None) -> str:
    """
    Get all stocks from KOSPI + KOSDAQ.

    Args:
        date: Optional date string (YYYYMMDD) - for compatibility, KIS uses latest master

    Returns:
        JSON [{"ticker": "...", "name": "..."}, ...]
    """
    try:
        if not is_client_initialized():
            return err_json("KIS API 클라이언트가 초기화되지 않았습니다")

        client = get_client()

        # Get all stocks from master
        df = client.get_all_stocks()

        if df.empty:
            return to_json([])

        result = []
        for _, row in df.iterrows():
            name = row.get("name", "")
            if name:
                result.append({
                    "ticker": row["ticker"],
                    "name": name
                })

        log.info("All stocks: %d", len(result))
        return to_json(result)

    except RuntimeError as e:
        log.error("KIS client error: %s", e)
        return err_json(f"KIS API 오류: {e}")
    except Exception as e:
        log.error("get_all_stocks error: %s", e)
        return to_json([])


def search_stock(query: str) -> str:
    """
    Search stocks by name.

    Args:
        query: Search keyword

    Returns:
        JSON [{"ticker": "...", "name": "..."}, ...]
    """
    if not query or not query.strip():
        return err_json("검색어를 입력해주세요")

    try:
        if not is_client_initialized():
            return err_json("KIS API 클라이언트가 초기화되지 않았습니다")

        client = get_client()

        # Get all stocks
        df = client.get_all_stocks()

        if df.empty:
            return to_json([])

        q = query.upper().strip()
        matches = []

        for _, row in df.iterrows():
            name = row.get("name", "")
            ticker = row.get("ticker", "")

            if name and (q in name.upper() or name.upper() in q or q in ticker):
                matches.append({"ticker": ticker, "name": name})

        log.info("Search '%s': %d found", query, len(matches))
        return to_json(matches)

    except RuntimeError as e:
        log.error("KIS client error: %s", e)
        return err_json(f"검색 오류: {e}")
    except Exception as e:
        log.error("search_stock error: %s", e)
        return err_json(f"검색 오류: {e}")


# ========================================
# Stock Data Functions
# ========================================

def get_stock_data(ticker: str, days: int = 180) -> str:
    """
    Get stock market cap and investor trading data.

    Args:
        ticker: Stock code (e.g., "005930")
        days: Number of days to fetch

    Returns:
        JSON {
            "ticker": "...",
            "name": "...",
            "dates": [...],
            "market_cap": [...],
            "foreign_5d": [...],
            "institution_5d": [...]
        }
    """
    if not ticker:
        return err_json("종목 코드가 필요합니다")
    if days <= 0 or days > 3650:
        return err_json("유효하지 않은 기간입니다 (1-3650일)")

    try:
        if not is_client_initialized():
            return err_json("KIS API 클라이언트가 초기화되지 않았습니다")

        client = get_client()

        end = datetime.now()
        start = end - timedelta(days=days)
        s = start.strftime("%Y%m%d")
        e = end.strftime("%Y%m%d")

        # Get market cap data
        mcap = client.get_market_cap_daily(ticker, s, e)

        # Get investor trading data
        inv = client.get_investor_trading(ticker, s)

        if mcap.empty:
            return err_json("시가총액 데이터를 가져올 수 없습니다")
        if inv.empty:
            return err_json("투자자 매매동향 데이터를 가져올 수 없습니다")

        # Calculate 5-day rolling sum for investor data
        # Use pykrx compatible column names
        f5d = inv["외국인합계"].rolling(5).sum()
        i5d = inv["기관합계"].rolling(5).sum()

        # Merge data
        df = pd.DataFrame({
            "market_cap": mcap["시가총액"],
            "foreign_5d": f5d,
            "institution_5d": i5d
        }).dropna()

        if df.empty:
            return err_json("데이터 처리 후 결과가 없습니다")

        # Get stock name
        name = client.get_stock_name(ticker) or ticker

        result = {
            "ticker": ticker,
            "name": name,
            "dates": df.index.strftime("%Y-%m-%d").tolist(),
            "market_cap": df["market_cap"].tolist(),
            "foreign_5d": df["foreign_5d"].tolist(),
            "institution_5d": df["institution_5d"].tolist()
        }

        log.info("Stock data %s: %d records", ticker, len(result["dates"]))
        return to_json(result)

    except RuntimeError as e:
        log.error("KIS client error (%s): %s", ticker, e)
        return err_json(f"분석 오류: {e}")
    except Exception as e:
        log.error("get_stock_data error (%s): %s", ticker, e)
        return err_json(f"분석 오류: {e}")


def get_stock_ohlcv(ticker: str, days: int = 180, interval: str = "d") -> str:
    """
    Get OHLCV data for stock.

    Args:
        ticker: Stock code (e.g., "005930")
        days: Period in days
        interval: "d" (daily) or "w" (weekly)

    Returns:
        JSON {
            "ticker": "...",
            "name": "...",
            "dates": [...],
            "open": [...],
            "high": [...],
            "low": [...],
            "close": [...],
            "volume": [...]
        }
    """
    if not ticker:
        return err_json("종목 코드가 필요합니다")

    try:
        if not is_client_initialized():
            return err_json("KIS API 클라이언트가 초기화되지 않았습니다")

        client = get_client()

        # For weekly data, fetch extra days
        extra = days * 2 if interval == "w" else days
        end = datetime.now()
        start = end - timedelta(days=extra)
        s = start.strftime("%Y%m%d")
        e = end.strftime("%Y%m%d")

        # Get OHLCV data from KIS API
        df = client.get_stock_ohlcv(ticker, s, e)

        if df.empty:
            return err_json("데이터가 없습니다")

        # KIS API already returns English column names: open, high, low, close, volume
        # No need to rename columns

        # Weekly resample if requested
        if interval == "w":
            df = df.resample("W").agg({
                "open": "first",
                "high": "max",
                "low": "min",
                "close": "last",
                "volume": "sum"
            }).dropna()

        if df.empty:
            return err_json("리샘플링 후 데이터가 없습니다")

        # Get stock name
        name = client.get_stock_name(ticker) or ticker

        result = {
            "ticker": ticker,
            "name": name,
            "dates": df.index.strftime("%Y-%m-%d").tolist(),
            "open": df["open"].tolist(),
            "high": df["high"].tolist(),
            "low": df["low"].tolist(),
            "close": df["close"].tolist(),
            "volume": [int(v) for v in df["volume"]]
        }

        log.info("OHLCV %s (%s): %d records", ticker, interval, len(result["dates"]))
        return to_json(result)

    except RuntimeError as e:
        log.error("KIS client error (%s): %s", ticker, e)
        return err_json(f"OHLCV 오류: {e}")
    except Exception as e:
        log.error("get_stock_ohlcv error (%s): %s", ticker, e)
        return err_json(f"OHLCV 오류: {e}")


# ========================================
# Backward Compatibility Aliases
# ========================================

def get_stock_name(ticker: str) -> str:
    """Get stock name (for backward compatibility)."""
    try:
        if not is_client_initialized():
            return ""
        client = get_client()
        return client.get_stock_name(ticker)
    except Exception:
        return ""


def get_all_stocks_list() -> str:
    """Alias for get_all_stocks (backward compatibility)."""
    return get_all_stocks()


def search_stock_wrapper(query: str) -> str:
    """Search and return first match (backward compatibility)."""
    result = json.loads(search_stock(query))
    if isinstance(result, list) and result:
        return to_json(result[0])
    if isinstance(result, dict) and "error" in result:
        return to_json(result)
    return err_json("종목을 찾을 수 없습니다")


def get_stock_analysis(ticker: str, days: int = 180) -> str:
    """Alias for get_stock_data (backward compatibility)."""
    return get_stock_data(ticker, days)
