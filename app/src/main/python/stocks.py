"""
Stock data collection and analysis module.
Unified module merging stockcollector, stock_data_fetcher, and stock_analyzer.
"""
import json
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional
import pandas as pd
from pykrx import stock

from core import (
    get_logger, get_tickers, get_name, market_date,
    to_json, err_json, MARKETS
)

log = get_logger(__name__)


def get_stock_list(date: str, market: str = "KOSPI") -> str:
    """
    Get stock list for a market.

    Returns: JSON [{"ticker": "...", "name": "..."}, ...]
    """
    try:
        datetime.strptime(date, '%Y%m%d')
        if market not in MARKETS:
            return to_json([])

        tickers = stock.get_market_ticker_list(date, market=market)
        result = [{"ticker": str(t), "name": get_name(t)} for t in tickers]
        log.info("%s: %d stocks (%s)", market, len(result), date)
        return to_json(result)

    except Exception as e:
        log.error("get_stock_list error: %s", e)
        return to_json([])


def get_all_stocks(date: Optional[str] = None) -> str:
    """
    Get all stocks from KOSPI + KOSDAQ.

    Returns: JSON [{"ticker": "...", "name": "..."}, ...]
    """
    try:
        d = date or market_date()
        tickers = get_tickers(date=d)

        result = []
        for t in tickers:
            name = get_name(t)
            if name:
                result.append({"ticker": t, "name": name})

        log.info("All stocks: %d", len(result))
        return to_json(result)

    except Exception as e:
        log.error("get_all_stocks error: %s", e)
        return to_json([])


def search_stock(query: str) -> str:
    """
    Search stocks by name.

    Returns: JSON [{"ticker": "...", "name": "..."}, ...]
    """
    if not query or not query.strip():
        return err_json("검색어를 입력해주세요")

    try:
        tickers = get_tickers()
        q = query.upper().strip()
        matches = []

        for t in tickers:
            name = get_name(t)
            if name and (q in name.upper() or name.upper() in q):
                matches.append({"ticker": t, "name": name})

        log.info("Search '%s': %d found", query, len(matches))
        return to_json(matches)

    except Exception as e:
        log.error("search_stock error: %s", e)
        return err_json(f"검색 오류: {e}")


def get_stock_data(ticker: str, days: int = 180) -> str:
    """
    Get stock market cap and investor trading data.

    Returns: JSON {
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
        end = datetime.now()
        start = end - timedelta(days=days)
        s, e = start.strftime("%Y%m%d"), end.strftime("%Y%m%d")

        mcap = stock.get_market_cap(s, e, ticker)
        inv = stock.get_market_trading_value_by_date(s, e, ticker)

        if mcap.empty or inv.empty:
            return err_json("데이터를 가져올 수 없습니다")

        # 5-day rolling sum
        f5d = inv["외국인합계"].rolling(5).sum()
        i5d = inv["기관합계"].rolling(5).sum()

        df = pd.DataFrame({
            "market_cap": mcap["시가총액"],
            "foreign_5d": f5d,
            "institution_5d": i5d
        }).dropna()

        if df.empty:
            return err_json("데이터 처리 후 결과가 없습니다")

        result = {
            "ticker": ticker,
            "name": get_name(ticker) or ticker,
            "dates": df.index.strftime("%Y-%m-%d").tolist(),
            "market_cap": [int(v) for v in df["market_cap"]],
            "foreign_5d": [int(v) for v in df["foreign_5d"]],
            "institution_5d": [int(v) for v in df["institution_5d"]]
        }

        log.info("Stock data %s: %d records", ticker, len(result["dates"]))
        return to_json(result)

    except Exception as e:
        log.error("get_stock_data error (%s): %s", ticker, e)
        return err_json(f"분석 오류: {e}")


def get_stock_ohlcv(ticker: str, days: int = 180, interval: str = "d") -> str:
    """
    Get OHLCV data for stock.

    Args:
        ticker: Stock code
        days: Period in days
        interval: "d" (daily) or "w" (weekly)

    Returns: JSON {
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
        extra = days * 2 if interval == "w" else days
        end = datetime.now()
        start = end - timedelta(days=extra)
        s, e = start.strftime("%Y%m%d"), end.strftime("%Y%m%d")

        df = stock.get_market_ohlcv(s, e, ticker)
        if df.empty:
            return err_json("데이터가 없습니다")

        df = df.rename(columns={
            "시가": "open", "고가": "high", "저가": "low",
            "종가": "close", "거래량": "volume"
        })[["open", "high", "low", "close", "volume"]]

        # Weekly resample
        if interval == "w":
            df = df.resample("W").agg({
                "open": "first", "high": "max", "low": "min",
                "close": "last", "volume": "sum"
            }).dropna()

        if df.empty:
            return err_json("리샘플링 후 데이터가 없습니다")

        result = {
            "ticker": ticker,
            "name": get_name(ticker) or ticker,
            "dates": df.index.strftime("%Y-%m-%d").tolist(),
            "open": df["open"].tolist(),
            "high": df["high"].tolist(),
            "low": df["low"].tolist(),
            "close": df["close"].tolist(),
            "volume": [int(v) for v in df["volume"]]
        }

        log.info("OHLCV %s (%s): %d records", ticker, interval, len(result["dates"]))
        return to_json(result)

    except Exception as e:
        log.error("get_stock_ohlcv error (%s): %s", ticker, e)
        return err_json(f"OHLCV 오류: {e}")


# Backward compatibility aliases
def get_stock_name(ticker: str) -> str:
    """Get stock name (for backward compatibility)."""
    return get_name(ticker)


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
