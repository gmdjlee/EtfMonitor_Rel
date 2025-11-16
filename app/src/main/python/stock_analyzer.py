"""
주식 분석 통합 모듈
deposit_scraper와 stock_data_fetcher를 통합하여 사용
"""

import json
import sys
import traceback
from typing import Optional, List, Dict, Any

from logger import setup_logger

logger = setup_logger(__name__)

# Constants
MAX_DAYS = 3650  # 최대 10년
MAX_PAGES = 50   # 최대 페이지 수

# 명시적으로 모듈 import - 실패 시 프로그램 종료
try:
    from deposit_scraper import scrape_deposit_data, get_latest_data
    from stock_data_fetcher import (
        search_stock,
        get_stock_data,
        get_stock_name,
        get_all_stocks
    )
    logger.info("모든 모듈 import 성공")
except ImportError as e:
    # 치명적 오류로 처리하고 조기 종료
    logger.critical("필수 모듈을 불러올 수 없습니다: %s", str(e))
    logger.error(traceback.format_exc())
    # Android에서 Python 프로세스 실패를 감지할 수 있도록 명확한 에러 출력
    print(json.dumps({"error": "모듈 import 실패", "details": str(e)}, ensure_ascii=False))
    sys.exit(1)
except Exception as e:
    logger.critical("예상치 못한 오류: %s", str(e))
    logger.error(traceback.format_exc())
    print(json.dumps({"error": "초기화 실패", "details": str(e)}, ensure_ascii=False))
    sys.exit(1)


def search_stock_wrapper(query: str) -> str:
    """
    종목 검색

    Args:
        query: 검색어 (종목명 또는 코드)

    Returns:
        JSON 문자열: {"ticker": "005930", "name": "삼성전자"} or {"error": "..."}
    """
    try:
        # 입력 검증
        if not query or not query.strip():
            logger.warning("검색어가 비어있습니다")
            return json.dumps({"error": "검색어를 입력해주세요"}, ensure_ascii=False)

        logger.info("종목 검색: %s", query)
        matches = search_stock(query)

        if not matches:
            logger.info("종목을 찾을 수 없습니다: %s", query)
            return json.dumps({"error": "종목을 찾을 수 없습니다"}, ensure_ascii=False)

        logger.info("검색 결과: %d개 종목 발견", len(matches))
        # 가장 관련성 높은 종목 반환
        return json.dumps(matches[0], ensure_ascii=False)

    except Exception as e:
        logger.error("검색 오류: %s", str(e))
        logger.debug(traceback.format_exc())
        return json.dumps({"error": f"검색 중 오류 발생: {str(e)}"}, ensure_ascii=False)


def get_stock_analysis(ticker: str, days: int = 180) -> str:
    """
    종목의 시가총액 및 투자자별 거래 데이터 수집

    Args:
        ticker: 종목 코드
        days: 분석 기간 (일)

    Returns:
        JSON 문자열
    """
    try:
        # 입력 검증
        if not ticker or not ticker.strip():
            logger.warning("종목 코드가 비어있습니다")
            return json.dumps({"error": "종목 코드가 필요합니다"}, ensure_ascii=False)

        if days <= 0 or days > MAX_DAYS:
            logger.warning("유효하지 않은 기간: %d일", days)
            return json.dumps(
                {"error": f"유효하지 않은 기간입니다 (1-{MAX_DAYS}일)"},
                ensure_ascii=False
            )

        logger.info("종목 분석 시작: %s, %d일", ticker, days)

        # 종목 데이터 수집
        data = get_stock_data(ticker, days)

        if data is None:
            logger.warning("종목 데이터를 가져올 수 없습니다: %s", ticker)
            return json.dumps(
                {"error": "데이터를 가져올 수 없습니다"},
                ensure_ascii=False
            )

        # 종목명 추가
        stock_name = get_stock_name(ticker)
        data["ticker"] = ticker
        data["name"] = stock_name or ticker

        logger.info(
            "종목 분석 완료: %s, %d개 데이터",
            data['name'], len(data.get('dates', []))
        )
        return json.dumps(data, ensure_ascii=False)

    except Exception as e:
        logger.error("분석 오류 (티커: %s): %s", ticker, str(e))
        logger.debug(traceback.format_exc())
        return json.dumps({"error": f"분석 중 오류 발생: {str(e)}"}, ensure_ascii=False)


def get_market_deposit_data(num_pages: int = 5) -> str:
    """
    증시 자금 동향 데이터 수집 (고객예탁금, 신용잔고)

    Args:
        num_pages: 수집할 페이지 수

    Returns:
        JSON 문자열
    """
    try:
        # 입력 검증
        if num_pages <= 0 or num_pages > MAX_PAGES:
            logger.warning("유효하지 않은 페이지 수: %d", num_pages)
            return json.dumps(
                {"error": f"유효하지 않은 페이지 수입니다 (1-{MAX_PAGES})"},
                ensure_ascii=False
            )

        logger.info("증시 자금 동향 수집 시작: %d페이지", num_pages)

        # deposit_scraper 함수 호출
        data = scrape_deposit_data(num_pages)

        logger.debug("데이터 수집 결과 타입: %s", type(data))

        if data is None or not data:
            logger.warning("시장 데이터가 비어있습니다")
            return json.dumps(
                {"error": "시장 데이터를 가져올 수 없습니다 (데이터 없음)"},
                ensure_ascii=False
            )

        # 데이터 검증
        if not isinstance(data, dict):
            logger.error("잘못된 데이터 형식: %s", type(data))
            return json.dumps(
                {"error": f"잘못된 데이터 형식: {type(data)}"},
                ensure_ascii=False
            )

        required_keys = ['dates', 'deposit_amounts', 'deposit_changes',
                         'credit_amounts', 'credit_changes']
        missing_keys = [key for key in required_keys if key not in data]

        if missing_keys:
            logger.error("필수 키 누락: %s", missing_keys)
            return json.dumps(
                {"error": f"필수 키 누락: {missing_keys}"},
                ensure_ascii=False
            )

        # 데이터가 비어있는지 확인
        if not data.get('dates') or len(data['dates']) == 0:
            logger.warning("수집된 날짜 데이터가 비어있습니다")
            return json.dumps(
                {"error": "수집된 데이터가 비어있습니다"},
                ensure_ascii=False
            )

        logger.info("데이터 수집 성공: %d개", len(data['dates']))
        return json.dumps(data, ensure_ascii=False)

    except Exception as e:
        logger.error("증시 데이터 수집 오류: %s", str(e))
        logger.debug(traceback.format_exc())
        return json.dumps(
            {"error": f"증시 데이터 수집 중 오류 발생: {str(e)}"},
            ensure_ascii=False
        )


def get_latest_market_data() -> str:
    """
    최신 증시 자금 동향 (1페이지)

    Returns:
        JSON 문자열
    """
    try:
        logger.info("최신 증시 자금 동향 수집 시작")

        data = get_latest_data()

        if data is None or not data:
            logger.warning("최신 데이터를 가져올 수 없습니다")
            return json.dumps(
                {"error": "최신 데이터를 가져올 수 없습니다"},
                ensure_ascii=False
            )

        logger.info("최신 데이터 수집 성공: %d개", len(data.get('dates', [])))
        return json.dumps(data, ensure_ascii=False)

    except Exception as e:
        logger.error("최신 데이터 오류: %s", str(e))
        logger.debug(traceback.format_exc())
        return json.dumps(
            {"error": f"최신 데이터 수집 중 오류 발생: {str(e)}"},
            ensure_ascii=False
        )


def get_all_stocks_list() -> str:
    """
    전체 종목 리스트 가져오기 (자동완성용)

    Returns:
        JSON 문자열: [{"ticker": "005930", "name": "삼성전자"}, ...]
    """
    try:
        logger.info("전체 종목 리스트 수집 시작")

        stocks = get_all_stocks()

        logger.info("종목 리스트 수집 완료: %d개", len(stocks))
        return json.dumps(stocks, ensure_ascii=False)

    except Exception as e:
        logger.error("종목 리스트 오류: %s", str(e))
        logger.debug(traceback.format_exc())
        return json.dumps(
            {"error": f"종목 리스트 수집 중 오류 발생: {str(e)}"},
            ensure_ascii=False
        )
