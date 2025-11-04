"""
유틸리티 함수 모듈
날짜 처리 및 기타 헬퍼 함수
"""
from pykrx import stock
from datetime import datetime, timedelta
import json


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
    except:
        return False