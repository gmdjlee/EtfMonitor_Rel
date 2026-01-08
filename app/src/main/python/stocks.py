"""
Stock data collection and analysis module.
Unified module merging stockcollector, stock_data_fetcher, and stock_analyzer.

Requires KIS API credentials to be configured in Settings.
Uses KIS Open API as the sole data source.

Improvements in v2.1:
- Consistent return types (always list or dict with error field)
- Better error messages
- Input validation
- Date alignment fix for investor data
"""
import json
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Union
import pandas as pd

from core import (
    get_logger, get_tickers, get_name, market_date,
    to_json, err_json, success_json, MARKETS,
    is_kis_available, get_kis_client,
    DataResult, ResultStatus
)

log = get_logger(__name__)


def _ensure_kis_client():
    """Ensure KIS client is available, raise error if not."""
    if not is_kis_available():
        raise RuntimeError("KIS API not configured. Please configure KIS credentials in Settings.")
    return get_kis_client()


def _validate_ticker(ticker: str) -> Optional[str]:
    """Validate and normalize ticker."""
    if not ticker or not isinstance(ticker, str):
        return None
    ticker = ticker.strip()
    if not ticker:
        return None
    # 6자리 숫자 형식 확인 (선택적)
    # if not ticker.isdigit() or len(ticker) != 6:
    #     return None
    return ticker


def _validate_date(date_str: str) -> bool:
    """Validate date string format (YYYYMMDD)."""
    try:
        datetime.strptime(date_str, '%Y%m%d')
        return True
    except (ValueError, TypeError):
        return False


def get_stock_list(date: str, market: str = "KOSPI") -> str:
    """
    Get stock list for a market via KIS API.

    Args:
        date: Date string (YYYYMMDD)
        market: "KOSPI" or "KOSDAQ"

    Returns: JSON [{"ticker": "...", "name": "..."}, ...]
             or {"error": true, "message": "..."} on error
    """
    # 입력 검증
    if not _validate_date(date):
        return err_json("Invalid date format. Use YYYYMMDD", "validation_error")

    if market not in MARKETS:
        return err_json(f"Invalid market: {market}. Use KOSPI or KOSDAQ", "validation_error")

    try:
        client = _ensure_kis_client()
        df = client.download_stock_master(market.lower())

        if df.empty:
            log.warning(f"No stocks found for {market}")
            return to_json([])

        result = [
            {"ticker": row["ticker"], "name": row["name"]}
            for _, row in df.iterrows()
        ]
        log.info("%s: %d stocks via KIS API (%s)", market, len(result), date)
        return to_json(result)

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e), "api_not_configured")
    except Exception as e:
        log.error("get_stock_list error: %s", e)
        return err_json(f"Failed to get stock list: {e}")


def get_all_stocks(date: Optional[str] = None) -> str:
    """
    Get all stocks from KOSPI + KOSDAQ via KIS API.

    Args:
        date: Optional date string (YYYYMMDD), defaults to latest market date

    Returns: JSON [{"ticker": "...", "name": "..."}, ...]
             or {"error": true, "message": "...", "error_type": "..."} on error
    """
    try:
        # Check KIS API availability first
        if not is_kis_available():
            log.error("KIS API not configured")
            return to_json({
                "error": True,
                "message": "KIS API가 설정되지 않았습니다. 설정 화면에서 KIS API 키를 입력해주세요.",
                "error_type": "api_not_configured"
            })

        d = date or market_date()
        tickers = get_tickers(date=d)

        if not tickers:
            return to_json({
                "error": True,
                "message": "종목 목록을 가져올 수 없습니다.",
                "error_type": "no_data"
            })

        result = []
        for t in tickers:
            name = get_name(t)
            if name:
                result.append({"ticker": t, "name": name})

        log.info("All stocks: %d", len(result))
        return to_json(result)

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return to_json({
            "error": True,
            "message": str(e),
            "error_type": "api_error"
        })
    except Exception as e:
        log.error("get_all_stocks error: %s", e)
        return to_json({
            "error": True,
            "message": f"종목 데이터를 가져오는 중 오류가 발생했습니다: {e}",
            "error_type": "unknown_error"
        })


def search_stock(query: str) -> str:
    """
    Search stocks by name via KIS API.

    Args:
        query: Search keyword

    Returns: JSON [{"ticker": "...", "name": "..."}, ...]
             or {"error": true, "message": "..."} on error
    """
    if not query or not query.strip():
        return err_json("검색어를 입력해주세요", "validation_error")

    try:
        if not is_kis_available():
            return err_json("KIS API가 설정되지 않았습니다", "api_not_configured")

        tickers = get_tickers()
        if not tickers:
            return err_json("종목 목록을 가져올 수 없습니다", "no_data")

        q = query.upper().strip()
        matches = []

        for t in tickers:
            name = get_name(t)
            if name and (q in name.upper() or name.upper() in q or q in t):
                matches.append({"ticker": t, "name": name})

        log.info("Search '%s': %d found", query, len(matches))
        return to_json(matches)

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e), "api_error")
    except Exception as e:
        log.error("search_stock error: %s", e)
        return err_json(f"검색 오류: {e}")


def get_stock_data(ticker: str, days: int = 180) -> str:
    """
    Get stock market cap and investor trading data via KIS API.

    Market cap is calculated from close price × listed shares.

    Args:
        ticker: Stock code (6 digits)
        days: Period in days (1-3650)

    Returns: JSON {
        "ticker": "...",
        "name": "...",
        "dates": [...],
        "market_cap": [...],
        "foreign_5d": [...],
        "institution_5d": [...]
    } or {"error": true, "message": "..."} on error
    """
    # 입력 검증
    ticker = _validate_ticker(ticker)
    if not ticker:
        return err_json("종목 코드가 필요합니다", "validation_error")

    if not isinstance(days, int) or days <= 0 or days > 3650:
        return err_json("유효하지 않은 기간입니다 (1-3650일)", "validation_error")

    try:
        client = _ensure_kis_client()

        end = datetime.now()
        start = end - timedelta(days=days)
        s, e = start.strftime("%Y%m%d"), end.strftime("%Y%m%d")

        # Get investor trading data
        log.info(f"Fetching investor trading data for {ticker}...")
        inv_df = client.get_investor_trading(ticker, s)
        if inv_df is None or inv_df.empty:
            return err_json("투자자 거래 데이터를 가져올 수 없습니다", "no_data")

        inv_df["date"] = pd.to_datetime(inv_df["date"])
        inv_df.set_index("date", inplace=True)
        inv_df = inv_df.sort_index()

        # Get market cap data (calculated from OHLCV + listed shares)
        log.info(f"Fetching OHLCV data for {ticker}...")
        ohlcv_df = client.get_stock_ohlcv_with_market_cap(ticker, s, e)
        if ohlcv_df is None or ohlcv_df.empty:
            return err_json("시가총액 데이터를 가져올 수 없습니다", "no_data")

        if "market_cap" not in ohlcv_df.columns:
            return err_json("시가총액 계산에 실패했습니다", "calculation_error")

        mcap_df = ohlcv_df[["market_cap"]]

        # 5-day rolling sum
        f5d = inv_df["foreign_net"].rolling(5, min_periods=1).sum()
        i5d = inv_df["institution_net"].rolling(5, min_periods=1).sum()

        # 날짜 정렬 - 공통 날짜만 사용
        common_dates = mcap_df.index.intersection(inv_df.index)

        if len(common_dates) == 0:
            log.warning(f"No common dates between OHLCV and investor data for {ticker}")
            # OHLCV 데이터만 사용
            df = pd.DataFrame({
                "market_cap": mcap_df["market_cap"],
                "foreign_5d": 0,
                "institution_5d": 0
            })
        else:
            df = pd.DataFrame({
                "market_cap": mcap_df.loc[common_dates, "market_cap"],
                "foreign_5d": f5d.reindex(common_dates).fillna(0),
                "institution_5d": i5d.reindex(common_dates).fillna(0)
            })

        df = df.dropna(subset=["market_cap"])

        if df.empty:
            return err_json("데이터 처리 후 결과가 없습니다", "no_data")

        result = {
            "error": False,
            "ticker": ticker,
            "name": get_name(ticker) or ticker,
            "dates": df.index.strftime("%Y-%m-%d").tolist(),
            "market_cap": df["market_cap"].tolist(),
            "foreign_5d": df["foreign_5d"].tolist(),
            "institution_5d": df["institution_5d"].tolist()
        }

        log.info("Stock data %s: %d records", ticker, len(result["dates"]))
        return to_json(result)

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e), "api_not_configured")
    except Exception as e:
        log.error("get_stock_data error (%s): %s", ticker, e)
        return err_json(f"분석 오류: {e}")


def get_stock_ohlcv(ticker: str, days: int = 180, interval: str = "d") -> str:
    """
    Get OHLCV data for stock via KIS API.

    Args:
        ticker: Stock code
        days: Period in days (1-3650)
        interval: "d" (daily) or "w" (weekly)

    Returns: JSON {
        "ticker": "...",
        "name": "...",
        "dates": [...],
        "open": [...],
        "high": [...],
        "low": [...],
        "close": [...],
        "volume": [...]
    } or {"error": true, "message": "..."} on error
    """
    # 입력 검증
    ticker = _validate_ticker(ticker)
    if not ticker:
        return err_json("종목 코드가 필요합니다", "validation_error")

    if interval not in ("d", "w"):
        return err_json("interval은 'd' 또는 'w'여야 합니다", "validation_error")

    try:
        client = _ensure_kis_client()

        extra = days * 2 if interval == "w" else days
        end = datetime.now()
        start = end - timedelta(days=extra)
        s, e = start.strftime("%Y%m%d"), end.strftime("%Y%m%d")

        df = client.get_stock_ohlcv(ticker, s, e)

        if df is None or df.empty:
            return err_json("데이터가 없습니다", "no_data")

        # Weekly resample
        if interval == "w":
            df = df.resample("W").agg({
                "open": "first", "high": "max", "low": "min",
                "close": "last", "volume": "sum"
            }).dropna()

        if df.empty:
            return err_json("리샘플링 후 데이터가 없습니다", "no_data")

        result = {
            "error": False,
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

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e), "api_not_configured")
    except Exception as e:
        log.error("get_stock_ohlcv error (%s): %s", ticker, e)
        return err_json(f"OHLCV 오류: {e}")


def get_stock_info(ticker: str) -> str:
    """
    Get current stock information.

    Args:
        ticker: Stock code

    Returns: JSON {
        "ticker": "...",
        "name": "...",
        "price": 50000,
        "market_cap": 300000000000000,
        "per": 10.5,
        "pbr": 1.2
    } or {"error": true, "message": "..."} on error
    """
    ticker = _validate_ticker(ticker)
    if not ticker:
        return err_json("종목 코드가 필요합니다", "validation_error")

    try:
        client = _ensure_kis_client()
        info = client.get_stock_info(ticker)

        if info is None:
            return err_json("종목 정보를 가져올 수 없습니다", "no_data")

        info["error"] = False
        return to_json(info)

    except RuntimeError as e:
        log.error("KIS API error: %s", e)
        return err_json(str(e), "api_not_configured")
    except Exception as e:
        log.error("get_stock_info error (%s): %s", ticker, e)
        return err_json(f"종목 정보 오류: {e}")


# Backward compatibility aliases
def get_stock_name(ticker: str) -> str:
    """Get stock name (for backward compatibility)."""
    return get_name(ticker)


def get_all_stocks_list() -> str:
    """Alias for get_all_stocks (backward compatibility)."""
    return get_all_stocks()


def search_stock_wrapper(query: str) -> str:
    """Search and return first match (backward compatibility)."""
    result_str = search_stock(query)
    try:
        result = json.loads(result_str)
        if isinstance(result, list) and result:
            return to_json(result[0])
        if isinstance(result, dict) and result.get("error"):
            return result_str
    except json.JSONDecodeError:
        pass
    return err_json("종목을 찾을 수 없습니다", "no_data")


def get_stock_analysis(ticker: str, days: int = 180) -> str:
    """Alias for get_stock_data (backward compatibility)."""
    return get_stock_data(ticker, days)
