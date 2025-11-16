"""
주식 데이터 수집 모듈 (pykrx 사용)
"""
from datetime import datetime, timedelta
from typing import List, Dict, Optional, Any
import pandas as pd
from pykrx import stock

from logger import setup_logger
from utils import get_market_date_with_fallback, get_all_market_tickers, get_ticker_name_safe

logger = setup_logger(__name__)


def search_stock(name: str) -> List[Dict[str, str]]:
    """
    종목명으로 코드 검색

    Args:
        name: 검색할 종목명

    Returns:
        list: [{"ticker": "...", "name": "..."}, ...]
    """
    try:
        if not name or not isinstance(name, str):
            logger.warning("검색어가 비어있거나 잘못된 형식입니다: %s", name)
            return []

        # 공통 함수 사용
        tickers = get_all_market_tickers()

        if not tickers:
            logger.warning("티커 목록을 가져올 수 없습니다")
            return []

    except Exception as e:
        logger.error("티커 목록 조회 오류: %s", str(e))
        return []

    # 검색
    matches: List[Dict[str, str]] = []
    name_upper = name.upper()

    for t in tickers:
        try:
            ticker_name = get_ticker_name_safe(t)
            if ticker_name:
                ticker_name_upper = ticker_name.upper()
                if name_upper in ticker_name_upper or ticker_name_upper in name_upper:
                    matches.append({"ticker": t, "name": ticker_name})
        except Exception as e:
            logger.debug("티커 처리 중 오류 (%s): %s", t, str(e))
            continue

    logger.info("검색 완료 ('%s'): %d개 종목 발견", name, len(matches))
    return matches


def get_stock_data(ticker: str, days: int = 180) -> Optional[Dict[str, List[Any]]]:
    """
    주식 데이터 수집

    Args:
        ticker: 종목 코드
        days: 분석 기간 (일)

    Returns:
        dict: {
            "dates": ["2024-01-01", ...],
            "market_cap": [100000000000, ...],
            "foreign_5d": [5000000000, ...],
            "institution_5d": [3000000000, ...]
        }
        실패 시 None
    """
    if not ticker or not isinstance(ticker, str):
        logger.warning("잘못된 티커 형식: %s", ticker)
        return None

    if days <= 0:
        logger.warning("잘못된 기간: %d일", days)
        return None

    end = datetime.now()
    start = end - timedelta(days=days)

    start_str = start.strftime("%Y%m%d")
    end_str = end.strftime("%Y%m%d")

    try:
        logger.info("주식 데이터 수집: %s (%s ~ %s)", ticker, start_str, end_str)

        # 시가총액 데이터
        mcap = stock.get_market_cap(start_str, end_str, ticker)

        # 투자자 거래 데이터
        inv = stock.get_market_trading_value_by_date(start_str, end_str, ticker)

        if mcap.empty or inv.empty:
            logger.warning("데이터가 없습니다 (티커: %s)", ticker)
            return None

    except Exception as e:
        logger.error("API 오류 (티커: %s): %s", ticker, str(e))
        return None

    try:
        # 5일 누적 계산
        foreign_5d = inv["외국인합계"].rolling(5).sum()
        institution_5d = inv["기관합계"].rolling(5).sum()

        # NaN 제거
        df = pd.DataFrame({
            "market_cap": mcap["시가총액"],
            "foreign_5d": foreign_5d,
            "institution_5d": institution_5d
        }).dropna()

        if df.empty:
            logger.warning("데이터 처리 후 결과가 비어있습니다 (티커: %s)", ticker)
            return None

        # JSON 변환 가능한 형태로 반환
        result = {
            "dates": df.index.strftime("%Y-%m-%d").tolist(),
            "market_cap": df["market_cap"].tolist(),
            "foreign_5d": df["foreign_5d"].tolist(),
            "institution_5d": df["institution_5d"].tolist()
        }

        logger.info(
            "데이터 수집 완료 (티커: %s): %d개 데이터",
            ticker, len(result["dates"])
        )
        return result

    except Exception as e:
        logger.error("데이터 처리 오류 (티커: %s): %s", ticker, str(e))
        return None


def get_stock_name(ticker: str) -> Optional[str]:
    """
    종목 코드로 이름 조회

    Args:
        ticker: 종목 티커

    Returns:
        str: 종목명 (조회 실패 시 None)
    """
    if not ticker or not isinstance(ticker, str):
        logger.warning("잘못된 티커 형식: %s", ticker)
        return None

    name = get_ticker_name_safe(ticker)
    return name if name else None


def get_all_stocks() -> List[Dict[str, str]]:
    """
    전체 종목 리스트 가져오기 (자동완성용)

    Returns:
        list: [{"ticker": "005930", "name": "삼성전자"}, ...]
    """
    try:
        # 공통 함수 사용
        tickers = get_all_market_tickers()

        if not tickers:
            logger.warning("티커 목록을 가져올 수 없습니다")
            return []

    except Exception as e:
        logger.error("티커 목록 조회 오류: %s", str(e))
        return []

    stock_list: List[Dict[str, str]] = []

    for ticker in tickers:
        try:
            name = get_ticker_name_safe(ticker)
            if name:  # 이름이 있는 종목만 추가
                stock_list.append({"ticker": ticker, "name": name})
        except Exception as e:
            logger.debug("티커 처리 중 오류 (%s): %s", ticker, str(e))
            continue

    logger.info("전체 종목 리스트 조회 완료: %d개", len(stock_list))
    return stock_list
