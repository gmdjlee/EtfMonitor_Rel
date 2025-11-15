"""
유틸리티 함수 모듈
날짜 처리 및 기타 헬퍼 함수
"""
from pykrx import stock
from datetime import datetime, timedelta
import json
import sys


def get_business_days(start_date: str, end_date: str) -> str:
    """
    특정 기간의 영업일 목록 조회
    삼성전자(005930) 시세 데이터로 영업일 판단

    Args:
        start_date: YYYYMMDD 형식의 시작일
        end_date: YYYYMMDD 형식의 종료일

    Returns:
        JSON 문자열 형태의 영업일 리스트 (YYYYMMDD 형식)
    """
    try:
        start = datetime.strptime(start_date, '%Y%m%d')
        end = datetime.strptime(end_date, '%Y%m%d')

        business_days = []
        current = start

        while current <= end:
            date_str = current.strftime('%Y%m%d')

            try:
                df = stock.get_market_ohlcv(date_str, date_str, '005930')
                if not df.empty:
                    business_days.append(date_str)
            except:
                pass

            current += timedelta(days=1)

        return json.dumps(business_days)

    except Exception as e:
        print(f"get_business_days error: {e}")
        return json.dumps([])


def is_business_day(date_str: str) -> bool:
    """
    특정 날짜가 영업일인지 확인

    Args:
        date_str: YYYYMMDD 형식의 날짜

    Returns:
        영업일 여부
    """
    try:
        df = stock.get_market_ohlcv(date_str, date_str, '005930')
        return not df.empty
    except Exception as e:
        print(f"[utils] is_business_day error: {e}", file=sys.stderr)
        return False


def get_market_date_with_fallback():
    """
    마켓 데이터 조회용 날짜 반환 (오늘 또는 어제)

    Returns:
        str: YYYYMMDD 형식의 날짜
    """
    try:
        today = datetime.now().strftime("%Y%m%d")
        # 오늘 날짜로 시도
        _ = stock.get_market_ticker_list(today, market="KOSPI")
        return today
    except Exception:
        # 실패하면 어제 날짜 반환
        yesterday = (datetime.now() - timedelta(days=1)).strftime("%Y%m%d")
        return yesterday


def get_all_market_tickers(date_str=None):
    """
    KOSPI + KOSDAQ 전체 종목 티커 조회

    Args:
        date_str: YYYYMMDD 형식의 날짜 (None이면 자동으로 최근 날짜 사용)

    Returns:
        list: 종목 티커 리스트
    """
    try:
        if date_str is None:
            date_str = get_market_date_with_fallback()

        kospi_tickers = stock.get_market_ticker_list(date_str, market="KOSPI")
        kosdaq_tickers = stock.get_market_ticker_list(date_str, market="KOSDAQ")

        return list(kospi_tickers) + list(kosdaq_tickers)
    except Exception as e:
        print(f"[utils] get_all_market_tickers error: {e}", file=sys.stderr)
        return []


def get_ticker_name_safe(ticker):
    """
    안전한 종목명 조회 (에러 처리 포함)

    Args:
        ticker: 종목 티커

    Returns:
        str: 종목명 (조회 실패시 빈 문자열)
    """
    try:
        name = stock.get_market_ticker_name(ticker)
        return str(name) if name and str(name).strip() else ""
    except Exception as e:
        print(f"[utils] get_ticker_name_safe error for {ticker}: {e}", file=sys.stderr)
        return ""