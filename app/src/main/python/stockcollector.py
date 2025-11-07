"""
주식 데이터 수집 모듈
pykrx 라이브러리를 사용하여 주식 정보 수집
"""
from pykrx import stock
from typing import List
import json


def get_stock_list(date_str: str, market: str = "KOSPI") -> str:
    """
    특정 날짜의 주식 목록 조회

    Args:
        date_str: YYYYMMDD 형식의 날짜
        market: "KOSPI" 또는 "KOSDAQ"

    Returns:
        JSON 문자열 형태의 주식 리스트
        [{"ticker": "...", "name": "..."}, ...]
    """
    try:
        tickers = stock.get_market_ticker_list(date_str, market=market)

        stocks = []
        for ticker in tickers:
            name = stock.get_market_ticker_name(ticker)
            stocks.append({
                'ticker': str(ticker),
                'name': str(name) if name else ""
            })

        return json.dumps(stocks)

    except Exception as e:
        print(f"get_stock_list error for {market}: {e}")
        return json.dumps([])


def get_all_stocks(date_str: str) -> str:
    """
    KOSPI와 KOSDAQ 전체 주식 목록 조회

    Args:
        date_str: YYYYMMDD 형식의 날짜

    Returns:
        JSON 문자열 형태의 주식 리스트
    """
    try:
        kospi_json = get_stock_list(date_str, "KOSPI")
        kosdaq_json = get_stock_list(date_str, "KOSDAQ")

        kospi_list = json.loads(kospi_json)
        kosdaq_list = json.loads(kosdaq_json)

        all_stocks = kospi_list + kosdaq_list

        return json.dumps(all_stocks)

    except Exception as e:
        print(f"get_all_stocks error: {e}")
        return json.dumps([])


def get_stock_name(ticker: str) -> str:
    """
    주식 이름 조회

    Args:
        ticker: 주식 티커

    Returns:
        주식 이름
    """
    try:
        if ticker == "010010":
            return "원화예금"
        else:
            name = stock.get_market_ticker_name(ticker)
            # 이름이 None이거나 빈 문자열이면 티커 반환
            if name and str(name).strip():
                return str(name)
            else:
                print(f"No name found for {ticker}, using ticker as name")
                return str(ticker)
    except Exception as e:
        print(f"get_stock_name error for {ticker}: {e}")
        return ""