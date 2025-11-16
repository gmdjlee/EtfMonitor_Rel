"""
유틸리티 함수 모듈
날짜 처리 및 기타 헬퍼 함수
"""
from pykrx import stock
from datetime import datetime, timedelta
from typing import List, Optional
import json

from logger import setup_logger

logger = setup_logger(__name__)


def get_business_days(start_date: str, end_date: str) -> str:
    """
    특정 기간의 영업일 목록 조회
    삼성전자(005930) 시세 데이터로 영업일 판단

    Args:
        start_date: YYYYMMDD 형식의 시작일
        end_date: YYYYMMDD 형식의 종료일

    Returns:
        JSON 문자열 형태의 영업일 리스트 (YYYYMMDD 형식)

    Raises:
        ValueError: 날짜 형식이 잘못된 경우
    """
    try:
        # Input validation
        start = datetime.strptime(start_date, '%Y%m%d')
        end = datetime.strptime(end_date, '%Y%m%d')

        if start > end:
            logger.warning(
                "시작일(%s)이 종료일(%s)보다 늦습니다. 빈 목록을 반환합니다.",
                start_date, end_date
            )
            return json.dumps([])

        # Validate reasonable date range (e.g., not more than 10 years)
        if (end - start).days > 3650:
            logger.warning(
                "날짜 범위가 너무 큽니다 (%d일). 최대 10년까지 지원합니다.",
                (end - start).days
            )
            return json.dumps([])

        business_days: List[str] = []
        current = start

        while current <= end:
            date_str = current.strftime('%Y%m%d')

            try:
                df = stock.get_market_ohlcv(date_str, date_str, '005930')
                if not df.empty:
                    business_days.append(date_str)
            except Exception as e:
                # Specific exception handling instead of bare except
                logger.debug(
                    "날짜 %s의 시장 데이터 조회 실패: %s",
                    date_str, str(e)
                )

            current += timedelta(days=1)

        logger.info(
            "영업일 조회 완료: %s ~ %s (%d일)",
            start_date, end_date, len(business_days)
        )
        return json.dumps(business_days)

    except ValueError as e:
        logger.error("날짜 형식 오류: %s", str(e))
        return json.dumps([])
    except Exception as e:
        logger.error("get_business_days 오류: %s", str(e))
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
        # Validate date format
        datetime.strptime(date_str, '%Y%m%d')

        df = stock.get_market_ohlcv(date_str, date_str, '005930')
        return not df.empty
    except ValueError as e:
        logger.error("날짜 형식 오류: %s", str(e))
        return False
    except Exception as e:
        logger.error("is_business_day 오류: %s", str(e))
        return False


def get_market_date_with_fallback() -> str:
    """
    마켓 데이터 조회용 날짜 반환 (오늘 또는 어제)

    Returns:
        str: YYYYMMDD 형식의 날짜
    """
    try:
        today = datetime.now().strftime("%Y%m%d")
        # 오늘 날짜로 시도
        _ = stock.get_market_ticker_list(today, market="KOSPI")
        logger.debug("마켓 날짜: %s (오늘)", today)
        return today
    except Exception as e:
        # 실패하면 어제 날짜 반환
        yesterday = (datetime.now() - timedelta(days=1)).strftime("%Y%m%d")
        logger.debug("마켓 날짜: %s (어제, 오늘 조회 실패: %s)", yesterday, str(e))
        return yesterday


def get_all_market_tickers(date_str: Optional[str] = None) -> List[str]:
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
        else:
            # Validate date format
            datetime.strptime(date_str, '%Y%m%d')

        kospi_tickers = stock.get_market_ticker_list(date_str, market="KOSPI")
        kosdaq_tickers = stock.get_market_ticker_list(date_str, market="KOSDAQ")

        all_tickers = list(kospi_tickers) + list(kosdaq_tickers)
        logger.info(
            "전체 종목 조회 완료: KOSPI %d, KOSDAQ %d, 총 %d",
            len(kospi_tickers), len(kosdaq_tickers), len(all_tickers)
        )
        return all_tickers
    except ValueError as e:
        logger.error("날짜 형식 오류: %s", str(e))
        return []
    except Exception as e:
        logger.error("get_all_market_tickers 오류: %s", str(e))
        return []


def get_ticker_name_safe(ticker: str) -> str:
    """
    안전한 종목명 조회 (에러 처리 포함)

    Args:
        ticker: 종목 티커

    Returns:
        str: 종목명 (조회 실패시 빈 문자열)
    """
    try:
        if not ticker or not isinstance(ticker, str):
            logger.warning("잘못된 티커 형식: %s", ticker)
            return ""

        name = stock.get_market_ticker_name(ticker)
        result = str(name) if name and str(name).strip() else ""

        if not result:
            logger.debug("종목명 조회 실패: %s", ticker)

        return result
    except Exception as e:
        logger.error("get_ticker_name_safe 오류 (티커: %s): %s", ticker, str(e))
        return ""
