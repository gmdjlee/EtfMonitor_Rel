"""
주식 데이터 수집 모듈 (pykrx 사용)
"""
from datetime import datetime, timedelta
import pandas as pd
from pykrx import stock
import sys

# 공통 헬퍼 함수 import
try:
    from utils import get_market_date_with_fallback, get_all_market_tickers, get_ticker_name_safe
except ImportError:
    print("[stock_data_fetcher] Warning: utils module not found, using fallback functions", file=sys.stderr)
    # Fallback 구현 (utils.py가 없을 경우 대비)
    def get_market_date_with_fallback():
        try:
            today = datetime.now().strftime("%Y%m%d")
            _ = stock.get_market_ticker_list(today, market="KOSPI")
            return today
        except Exception:
            return (datetime.now() - timedelta(days=1)).strftime("%Y%m%d")

    def get_all_market_tickers(date_str=None):
        if date_str is None:
            date_str = get_market_date_with_fallback()
        try:
            return list(stock.get_market_ticker_list(date_str, market="KOSPI")) + \
                   list(stock.get_market_ticker_list(date_str, market="KOSDAQ"))
        except Exception:
            return []

    def get_ticker_name_safe(ticker):
        try:
            name = stock.get_market_ticker_name(ticker)
            return str(name) if name else ""
        except Exception:
            return ""


def search_stock(name):
    """
    종목명으로 코드 검색

    Args:
        name: 검색할 종목명

    Returns:
        list: [{"ticker": "...", "name": "..."}, ...]
    """
    try:
        # 공통 함수 사용
        tickers = get_all_market_tickers()
    except Exception as e:
        print(f"[stock_data_fetcher] search_stock ticker list error: {e}", file=sys.stderr)
        return []

    # 검색
    matches = []
    for t in tickers:
        try:
            ticker_name = get_ticker_name_safe(t)
            if ticker_name and (name.upper() in ticker_name.upper() or ticker_name.upper() in name.upper()):
                matches.append({"ticker": t, "name": ticker_name})
        except Exception as e:
            print(f"[stock_data_fetcher] Error processing ticker {t}: {e}", file=sys.stderr)
            continue

    return matches


def get_stock_data(ticker, days=180):
    """
    주식 데이터 수집

    Parameters:
    -----------
    ticker : str
        종목 코드
    days : int
        분석 기간 (일)

    Returns:
    --------
    dict
        {
            "dates": ["2024-01-01", ...],
            "market_cap": [100000000000, ...],
            "foreign_5d": [5000000000, ...],
            "institution_5d": [3000000000, ...]
        }
    """
    end = datetime.now()
    start = end - timedelta(days=days)

    start_str = start.strftime("%Y%m%d")
    end_str = end.strftime("%Y%m%d")

    try:
        # 시가총액 데이터
        mcap = stock.get_market_cap(start_str, end_str, ticker)

        # 투자자 거래 데이터
        inv = stock.get_market_trading_value_by_date(start_str, end_str, ticker)

        if mcap.empty or inv.empty:
            print(f"[stock_data_fetcher] No data for ticker {ticker}", file=sys.stderr)
            return None
    except Exception as e:
        print(f"[stock_data_fetcher] get_stock_data API error for {ticker}: {e}", file=sys.stderr)
        return None

    # 5일 누적 계산
    foreign_5d = inv["외국인합계"].rolling(5).sum()
    institution_5d = inv["기관합계"].rolling(5).sum()

    # NaN 제거
    df = pd.DataFrame({
        "market_cap": mcap["시가총액"],
        "foreign_5d": foreign_5d,
        "institution_5d": institution_5d
    }).dropna()

    # JSON 변환 가능한 형태로 반환
    result = {
        "dates": df.index.strftime("%Y-%m-%d").tolist(),
        "market_cap": df["market_cap"].tolist(),
        "foreign_5d": df["foreign_5d"].tolist(),
        "institution_5d": df["institution_5d"].tolist()
    }

    return result


def get_stock_name(ticker):
    """
    종목 코드로 이름 조회

    Args:
        ticker: 종목 티커

    Returns:
        str: 종목명 (None 반환 가능)
    """
    name = get_ticker_name_safe(ticker)
    return name if name else None


def get_all_stocks():
    """
    전체 종목 리스트 가져오기 (자동완성용)

    Returns:
        list: [{"ticker": "005930", "name": "삼성전자"}, ...]
    """
    try:
        # 공통 함수 사용
        tickers = get_all_market_tickers()
    except Exception as e:
        print(f"[stock_data_fetcher] get_all_stocks error: {e}", file=sys.stderr)
        return []

    stock_list = []
    for ticker in tickers:
        try:
            name = get_ticker_name_safe(ticker)
            if name:  # 이름이 있는 종목만 추가
                stock_list.append({"ticker": ticker, "name": name})
        except Exception as e:
            print(f"[stock_data_fetcher] Error processing ticker {ticker}: {e}", file=sys.stderr)
            continue

    return stock_list