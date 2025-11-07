"""
주식 데이터 수집 모듈 (pykrx 사용)
"""
from datetime import datetime, timedelta
import pandas as pd
from pykrx import stock


def search_stock(name):
    """종목명으로 코드 검색"""
    try:
        today = datetime.now().strftime("%Y%m%d")
        tickers = stock.get_market_ticker_list(today, market="KOSPI") + \
                  stock.get_market_ticker_list(today, market="KOSDAQ")
    except:
        yesterday = (datetime.now() - timedelta(days=1)).strftime("%Y%m%d")
        tickers = stock.get_market_ticker_list(yesterday, market="KOSPI") + \
                  stock.get_market_ticker_list(yesterday, market="KOSDAQ")

    # 검색
    matches = []
    for t in tickers:
        ticker_name = stock.get_market_ticker_name(t)
        if name.upper() in ticker_name.upper() or ticker_name.upper() in name.upper():
            matches.append({"ticker": t, "name": ticker_name})

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

    # 시가총액 데이터
    mcap = stock.get_market_cap(start_str, end_str, ticker)

    # 투자자 거래 데이터
    inv = stock.get_market_trading_value_by_date(start_str, end_str, ticker)

    if mcap.empty or inv.empty:
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
    """종목 코드로 이름 조회"""
    try:
        return stock.get_market_ticker_name(ticker)
    except:
        return None


def get_all_stocks():
    """
    전체 종목 리스트 가져오기 (자동완성용)

    Returns:
    --------
    list
        [{"ticker": "005930", "name": "삼성전자"}, ...]
    """
    try:
        today = datetime.now().strftime("%Y%m%d")
        tickers = stock.get_market_ticker_list(today, market="KOSPI") + \
                  stock.get_market_ticker_list(today, market="KOSDAQ")
    except:
        yesterday = (datetime.now() - timedelta(days=1)).strftime("%Y%m%d")
        tickers = stock.get_market_ticker_list(yesterday, market="KOSPI") + \
                  stock.get_market_ticker_list(yesterday, market="KOSDAQ")

    stock_list = []
    for ticker in tickers:
        try:
            name = stock.get_market_ticker_name(ticker)
            stock_list.append({"ticker": ticker, "name": name})
        except:
            continue

    return stock_list