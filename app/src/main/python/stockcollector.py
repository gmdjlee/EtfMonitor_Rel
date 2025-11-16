"""
주식 데이터 수집 모듈
pykrx 라이브러리를 사용하여 주식 정보 수집
"""
from pykrx import stock
from typing import List, Dict
from datetime import datetime
import json

from logger import setup_logger

logger = setup_logger(__name__)

# Constants
CASH_DEPOSIT_TICKER = "010010"  # 원화예금 특수 티커


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
        # Validate date format
        datetime.strptime(date_str, '%Y%m%d')

        # Validate market
        if market not in ("KOSPI", "KOSDAQ"):
            logger.error("잘못된 시장 구분: %s (KOSPI 또는 KOSDAQ만 가능)", market)
            return json.dumps([])

        tickers = stock.get_market_ticker_list(date_str, market=market)

        stocks: List[Dict[str, str]] = []
        for ticker in tickers:
            try:
                name = stock.get_market_ticker_name(ticker)
                stocks.append({
                    'ticker': str(ticker),
                    'name': str(name) if name else ""
                })
            except Exception as e:
                logger.warning(
                    "종목명 조회 실패 (티커: %s): %s",
                    ticker, str(e)
                )
                stocks.append({
                    'ticker': str(ticker),
                    'name': ""
                })

        logger.info(
            "%s 시장 종목 조회 완료 (%s): %d개",
            market, date_str, len(stocks)
        )
        return json.dumps(stocks)

    except ValueError as e:
        logger.error("날짜 형식 오류: %s", str(e))
        return json.dumps([])
    except Exception as e:
        logger.error("get_stock_list 오류 (%s): %s", market, str(e))
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
        # Validate date format
        datetime.strptime(date_str, '%Y%m%d')

        kospi_json = get_stock_list(date_str, "KOSPI")
        kosdaq_json = get_stock_list(date_str, "KOSDAQ")

        kospi_list = json.loads(kospi_json)
        kosdaq_list = json.loads(kosdaq_json)

        all_stocks = kospi_list + kosdaq_list

        logger.info(
            "전체 주식 목록 조회 완료: KOSPI %d, KOSDAQ %d, 총 %d",
            len(kospi_list), len(kosdaq_list), len(all_stocks)
        )
        return json.dumps(all_stocks)

    except ValueError as e:
        logger.error("날짜 형식 오류: %s", str(e))
        return json.dumps([])
    except json.JSONDecodeError as e:
        logger.error("JSON 파싱 오류: %s", str(e))
        return json.dumps([])
    except Exception as e:
        logger.error("get_all_stocks 오류: %s", str(e))
        return json.dumps([])


def get_stock_name(ticker: str) -> str:
    """
    주식 이름 조회

    Args:
        ticker: 주식 티커

    Returns:
        주식 이름 (조회 실패 시 빈 문자열)
    """
    try:
        # Validate ticker
        if not ticker or not isinstance(ticker, str):
            logger.warning("잘못된 티커 형식: %s", ticker)
            return ""

        ticker = ticker.strip()

        # Special case: 원화예금
        if ticker == CASH_DEPOSIT_TICKER:
            logger.debug("특수 티커: %s (원화예금)", ticker)
            return "원화예금"

        name = stock.get_market_ticker_name(ticker)

        # 이름이 None이거나 빈 문자열이면 티커 반환
        if name and str(name).strip():
            return str(name)
        else:
            logger.warning(
                "종목명을 찾을 수 없어 티커를 반환합니다: %s",
                ticker
            )
            return str(ticker)

    except Exception as e:
        logger.error("get_stock_name 오류 (티커: %s): %s", ticker, str(e))
        return ""
