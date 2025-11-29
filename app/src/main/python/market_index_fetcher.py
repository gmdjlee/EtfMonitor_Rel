#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KOSPI/KOSDAQ 지수 데이터 수집 모듈
ETF 통계와의 상관관계 분석을 위한 시장 지수 데이터 제공
"""

from pykrx import stock
import pandas as pd
from datetime import datetime, timedelta
import json
from typing import List, Dict, Optional


def fetch_market_index(
    market: str,
    start_date: str,
    end_date: str
) -> List[Dict]:
    """
    특정 시장의 지수 데이터 수집

    Args:
        market: "KOSPI" 또는 "KOSDAQ"
        start_date: 시작 날짜 (YYYYMMDD 형식)
        end_date: 종료 날짜 (YYYYMMDD 형식)

    Returns:
        지수 데이터 리스트 (JSON 형식)
        [{
            "date": "2025-01-01",
            "market": "KOSPI",
            "closePrice": 2500.0,
            "openPrice": 2480.0,
            "highPrice": 2510.0,
            "lowPrice": 2470.0,
            "volume": 500000,
            "changeRate": 0.5
        }, ...]
    """
    try:
        # pykrx에서 사용하는 티커 코드
        ticker_map = {
            "KOSPI": "1001",  # KOSPI 지수
            "KOSDAQ": "2001"  # KOSDAQ 지수
        }

        if market not in ticker_map:
            raise ValueError(f"Invalid market: {market}. Must be 'KOSPI' or 'KOSDAQ'")

        ticker = ticker_map[market]

        # 날짜 형식 변환 (YYYYMMDD -> YYYY-MM-DD)
        start_formatted = f"{start_date[:4]}-{start_date[4:6]}-{start_date[6:]}"
        end_formatted = f"{end_date[:4]}-{end_date[4:6]}-{end_date[6:]}"

        # pykrx로 지수 데이터 가져오기
        df = stock.get_index_ohlcv(start_date, end_date, ticker)

        if df is None or df.empty:
            print(f"No data found for {market} from {start_formatted} to {end_formatted}")
            return []

        # 데이터 변환
        result = []
        prev_close = None

        for idx, row in df.iterrows():
            date_str = idx.strftime("%Y-%m-%d")
            close_price = float(row['종가'])

            # 등락률 계산
            change_rate = 0.0
            if prev_close is not None and prev_close > 0:
                change_rate = ((close_price - prev_close) / prev_close) * 100

            data = {
                "date": date_str,
                "market": market,
                "closePrice": close_price,
                "openPrice": float(row['시가']),
                "highPrice": float(row['고가']),
                "lowPrice": float(row['저가']),
                "volume": int(row['거래량']),
                "changeRate": round(change_rate, 2)
            }

            result.append(data)
            prev_close = close_price

        print(f"Fetched {len(result)} records for {market}")
        return result

    except Exception as e:
        print(f"Error fetching market index for {market}: {e}")
        import traceback
        traceback.print_exc()
        return []


def fetch_all_markets(
    start_date: str,
    end_date: str,
    markets = None
) -> str:
    """
    여러 시장의 지수 데이터를 한번에 수집

    Args:
        start_date: 시작 날짜 (YYYYMMDD 형식)
        end_date: 종료 날짜 (YYYYMMDD 형식)
        markets: 수집할 시장 리스트 (기본값: ["KOSPI", "KOSDAQ"])

    Returns:
        JSON 문자열 형태의 지수 데이터
    """
    if markets is None:
        markets = ["KOSPI", "KOSDAQ"]
    else:
        # Convert Java ArrayList to Python list if passed from Kotlin/Java
        markets = list(markets)

    all_data = []

    for market in markets:
        data = fetch_market_index(market, start_date, end_date)
        all_data.extend(data)

    return json.dumps(all_data, ensure_ascii=False)


def fetch_recent_days(days: int = 30, markets = None) -> str:
    """
    최근 N일의 지수 데이터 수집

    Args:
        days: 수집할 일수
        markets: 수집할 시장 리스트

    Returns:
        JSON 문자열 형태의 지수 데이터
    """
    # Convert Java ArrayList to Python list if passed from Kotlin/Java
    if markets is not None:
        markets = list(markets)

    end_date = datetime.now()
    start_date = end_date - timedelta(days=days + 30)  # 주말/공휴일 고려하여 여유있게

    start_str = start_date.strftime("%Y%m%d")
    end_str = end_date.strftime("%Y%m%d")

    return fetch_all_markets(start_str, end_str, markets)


def get_latest_index(market: str) -> Optional[Dict]:
    """
    특정 시장의 최신 지수 데이터 조회

    Args:
        market: "KOSPI" 또는 "KOSDAQ"

    Returns:
        최신 지수 데이터 (Dict) 또는 None
    """
    try:
        # 최근 10일 데이터 조회 (주말/공휴일 고려)
        end_date = datetime.now()
        start_date = end_date - timedelta(days=10)

        start_str = start_date.strftime("%Y%m%d")
        end_str = end_date.strftime("%Y%m%d")

        data = fetch_market_index(market, start_str, end_str)

        if data:
            return data[-1]  # 최신 데이터 반환

        return None

    except Exception as e:
        print(f"Error getting latest index for {market}: {e}")
        return None


# 테스트 코드
if __name__ == "__main__":
    print("=== Market Index Fetcher Test ===")

    # 최근 30일 데이터 테스트
    print("\n1. Testing recent 30 days data fetch...")
    result = fetch_recent_days(30)
    data = json.loads(result)
    print(f"Total records: {len(data)}")

    if data:
        print(f"First record: {data[0]}")
        print(f"Last record: {data[-1]}")

    # 최신 지수 데이터 테스트
    print("\n2. Testing latest index fetch...")
    kospi_latest = get_latest_index("KOSPI")
    kosdaq_latest = get_latest_index("KOSDAQ")

    if kospi_latest:
        print(f"KOSPI latest: {kospi_latest}")

    if kosdaq_latest:
        print(f"KOSDAQ latest: {kosdaq_latest}")

    print("\nTest completed!")
