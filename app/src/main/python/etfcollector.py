"""
ETF 데이터 수집 모듈
pykrx 라이브러리를 사용하여 ETF 정보 수집
"""
from pykrx import stock
from typing import Any, Dict, List
from datetime import datetime
import json
import traceback

from logger import setup_logger

logger = setup_logger(__name__)


def get_etf_list_with_names(date_str: str,
                            include_keywords_json: str = "[]",
                            exclude_keywords_json: str = "[]") -> str:
    """
    특정 날짜의 ETF 목록을 필터링하여 조회

    필터링 규칙:
    1. 이름에 '액티브'가 반드시 포함되어야 함 (필수)
    2. include_keywords 중 '액티브' 외에 최소 1개 이상 포함되어야 함
    3. exclude_keywords 중 하나라도 포함되면 제외

    Args:
        date_str: YYYYMMDD 형식의 날짜
        include_keywords_json: 포함되어야 할 키워드 JSON 배열 문자열
        exclude_keywords_json: 제외되어야 할 키워드 JSON 배열 문자열

    Returns:
        JSON 문자열 형태의 ETF 리스트
        [{"ticker": "...", "name": "..."}, ...]
    """
    try:
        logger.info("="*80)
        logger.info("ETF 목록 필터링 조회 시작")
        logger.info("="*80)

        # Validate date format
        datetime.strptime(date_str, '%Y%m%d')

        # JSON 파싱 with error handling
        try:
            include_keywords = json.loads(include_keywords_json) if include_keywords_json else []
            exclude_keywords = json.loads(exclude_keywords_json) if exclude_keywords_json else []
        except json.JSONDecodeError as e:
            logger.error("JSON 파싱 오류: %s", str(e))
            return json.dumps([])

        logger.info("입력 키워드:")
        logger.info("  포함 (%d개): %s", len(include_keywords), include_keywords)
        logger.info("  제외 (%d개): %s", len(exclude_keywords), exclude_keywords)

        # '액티브' 확인
        if '액티브' not in include_keywords:
            logger.warning("'액티브'가 포함 키워드에 없습니다!")
        else:
            logger.info("'액티브' 키워드 확인됨")

        # '액티브'를 제외한 다른 테마 키워드들
        other_keywords = [k for k in include_keywords if k != '액티브']
        logger.info(
            "기타 테마 키워드 (%d개): %s",
            len(other_keywords),
            other_keywords[:5] if len(other_keywords) > 5 else other_keywords
        )

        # ETF 티커 가져오기
        logger.info("ETF 티커 목록 조회 중 (날짜: %s)...", date_str)
        tickers = stock.get_etf_ticker_list(date_str)
        if tickers is None:
            logger.error("API가 None을 반환했습니다")
            return json.dumps([])

        logger.info("API에서 %d개 티커 조회 완료", len(tickers))

        # 필터링
        logger.info("-"*80)
        logger.info("필터링 프로세스 시작:")
        logger.info("-"*80)

        etf_list: List[Dict[str, str]] = []
        stats = {
            'total': 0,
            'no_active': 0,
            'excluded': 0,
            'no_theme': 0,
            'passed': 0
        }

        for ticker in tickers:
            try:
                stats['total'] += 1

                name = stock.get_etf_ticker_name(ticker)
                if not name:
                    continue

                name = str(name)

                # STEP 1: '액티브' 필수 확인
                if '액티브' not in name:
                    stats['no_active'] += 1
                    continue

                # STEP 2: 제외 키워드 확인
                excluded = False
                matched_exclude = None
                if exclude_keywords:
                    for keyword in exclude_keywords:
                        if keyword in name:
                            excluded = True
                            matched_exclude = keyword
                            stats['excluded'] += 1
                            break

                if excluded:
                    logger.debug("제외됨: %s (키워드: '%s')", name, matched_exclude)
                    continue

                # STEP 3: 다른 테마 키워드 확인
                has_theme_keyword = False
                matched_theme = None

                if other_keywords:
                    for keyword in other_keywords:
                        if keyword in name:
                            has_theme_keyword = True
                            matched_theme = keyword
                            break

                    if not has_theme_keyword:
                        stats['no_theme'] += 1
                        logger.debug(
                            "'액티브'는 있지만 테마 키워드 없음: %s",
                            name
                        )
                        continue
                else:
                    # 테마 키워드가 없으면 '액티브'만으로 통과
                    logger.warning(
                        "테마 키워드가 제공되지 않아 '액티브'만으로 허용: %s",
                        name
                    )
                    has_theme_keyword = True

                # 필터링 통과!
                stats['passed'] += 1
                logger.info("통과: %s (매칭: '액티브' + '%s')", name, matched_theme)

                etf_list.append({
                    'ticker': str(ticker),
                    'name': name
                })

            except Exception as e:
                logger.warning("티커 처리 중 오류 (%s): %s", ticker, str(e))
                continue

        # 결과 통계
        logger.info("="*80)
        logger.info("필터링 결과:")
        logger.info("="*80)
        logger.info("  총 ETF 처리: %d", stats['total'])
        logger.info("  └─ '액티브' 없음: %d", stats['no_active'])
        logger.info("  └─ '액티브' 있음: %d", stats['total'] - stats['no_active'])
        logger.info("     ├─ 제외 키워드로 제외: %d", stats['excluded'])
        logger.info("     ├─ 테마 키워드 없음: %d", stats['no_theme'])
        logger.info("     └─ 통과: %d", stats['passed'])
        logger.info("="*80)

        if etf_list:
            logger.info("최종 목록 (%d개 ETF):", len(etf_list))
            for i, etf in enumerate(etf_list, 1):
                logger.info("  %d. %s: %s", i, etf['ticker'], etf['name'])
        else:
            logger.warning("필터를 통과한 ETF가 없습니다!")
            logger.warning("가능한 원인:")
            logger.warning("  1. '액티브'가 포함된 ETF가 없음")
            logger.warning("  2. 모든 '액티브' ETF가 제외됨")
            logger.warning("  3. '액티브' ETF에 테마 키워드가 없음")

        logger.info("="*80)

        return json.dumps(etf_list)

    except ValueError as e:
        logger.error("날짜 형식 오류: %s", str(e))
        return json.dumps([])
    except Exception as e:
        logger.error("get_etf_list_with_names 예외 발생: %s", str(e))
        logger.error(traceback.format_exc())
        return json.dumps([])


def get_etf_list(date_str: str) -> str:
    """
    특정 날짜의 ETF 티커 목록 조회 (필터링 없음)

    Args:
        date_str: YYYYMMDD 형식의 날짜

    Returns:
        JSON 문자열 형태의 ETF 티커 리스트
    """
    try:
        # Validate date format
        datetime.strptime(date_str, '%Y%m%d')

        tickers = stock.get_etf_ticker_list(date_str)
        if tickers is None:
            logger.warning("ETF 티커 목록이 None입니다 (날짜: %s)", date_str)
            return json.dumps([])

        ticker_list = [str(ticker) for ticker in tickers]
        logger.info("ETF 티커 목록 조회 완료: %d개 (날짜: %s)", len(ticker_list), date_str)
        return json.dumps(ticker_list)

    except ValueError as e:
        logger.error("날짜 형식 오류: %s", str(e))
        return json.dumps([])
    except Exception as e:
        logger.error("get_etf_list 오류: %s", str(e))
        return json.dumps([])


def get_etf_name(ticker: str) -> str:
    """
    ETF 이름 조회

    Args:
        ticker: ETF 티커

    Returns:
        ETF 이름 (조회 실패 시 빈 문자열)
    """
    try:
        if not ticker or not isinstance(ticker, str):
            logger.warning("잘못된 티커 형식: %s", ticker)
            return ""

        name = stock.get_etf_ticker_name(ticker)
        result = str(name) if name else ""

        if not result:
            logger.debug("ETF 이름 조회 실패: %s", ticker)

        return result

    except Exception as e:
        logger.error("get_etf_name 오류 (티커: %s): %s", ticker, str(e))
        return ""


def get_etf_holdings(ticker: str, date_str: str) -> str:
    """
    ETF 구성 종목 정보 조회

    Args:
        ticker: ETF 티커
        date_str: YYYYMMDD 형식의 날짜

    Returns:
        JSON 문자열 형태의 구성 종목 리스트
        [{"ticker": "...", "weight": ..., "amount": ...}, ...]
    """
    try:
        # Validate date format
        datetime.strptime(date_str, '%Y%m%d')

        df = stock.get_etf_portfolio_deposit_file(ticker, date_str)

        if df is None or df.empty or '비중' not in df.columns:
            logger.debug(
                "ETF 구성 종목 데이터 없음 (티커: %s, 날짜: %s)",
                ticker, date_str
            )
            return json.dumps([])

        holdings: List[Dict[str, Any]] = []
        for stock_ticker, row in df.iterrows():
            amount = float(row.get('금액', 0)) if '금액' in df.columns else 0.0

            holdings.append({
                'ticker': str(stock_ticker),
                'weight': float(row['비중']),
                'amount': amount
            })

        logger.info(
            "ETF 구성 종목 조회 완료 (티커: %s, 날짜: %s): %d개",
            ticker, date_str, len(holdings)
        )
        return json.dumps(holdings)

    except ValueError as e:
        logger.error("날짜 형식 오류: %s", str(e))
        return json.dumps([])
    except Exception as e:
        logger.error(
            "get_etf_holdings 오류 (티커: %s, 날짜: %s): %s",
            ticker, date_str, str(e)
        )
        return json.dumps([])
