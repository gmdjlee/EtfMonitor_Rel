"""
증시자금동향 데이터 수집 모듈 (개선 버전)
네이버 증권에서 고객예탁금과 신용잔고 데이터를 수집합니다.
"""

import requests
from bs4 import BeautifulSoup
import pandas as pd
from datetime import datetime
from typing import Optional, List, Dict, Any
import time

from logger import setup_logger

logger = setup_logger(__name__)

# Constants
DEFAULT_NUM_PAGES = 5
EXTENDED_NUM_PAGES = 10
REQUEST_TIMEOUT = 15
REQUEST_DELAY = 0.5
MAX_RETRIES = 3
RETRY_DELAY = 2


def scrape_deposit_data(num_pages: int = DEFAULT_NUM_PAGES) -> Optional[Dict[str, List[Any]]]:
    """
    네이버 증권에서 증시자금동향 데이터를 수집합니다.

    Args:
        num_pages: 수집할 페이지 수 (기본값: 5)

    Returns:
        dict: {
            'dates': [...],
            'deposit_amounts': [...],
            'deposit_changes': [...],
            'credit_amounts': [...],
            'credit_changes': [...]
        }
        수집 실패 시 None
    """
    if num_pages <= 0:
        logger.warning("페이지 수가 0 이하입니다: %d", num_pages)
        return None

    logger.info("데이터 수집 시작: %d페이지", num_pages)

    all_data: List[Dict[str, Any]] = []

    for page_num in range(1, num_pages + 1):
        try:
            logger.info("페이지 %d 수집 중...", page_num)
            page_data = scrape_page(page_num)

            if page_data:
                all_data.extend(page_data)
                logger.info("페이지 %d: %d개 수집", page_num, len(page_data))
            else:
                logger.warning("페이지 %d: 데이터 없음", page_num)

            # 요청 간 딜레이 (서버 부하 방지)
            if page_num < num_pages:
                time.sleep(REQUEST_DELAY)

        except Exception as e:
            logger.error("페이지 %d 수집 실패: %s", page_num, str(e))
            continue

    if not all_data:
        logger.warning("수집된 데이터가 없습니다")
        return None

    logger.info("총 %d개 데이터 수집", len(all_data))

    # 데이터프레임 생성
    try:
        df = pd.DataFrame(all_data)

        # 중복 제거 및 정렬
        df = df.drop_duplicates(subset=['date'], keep='first')
        df = df.sort_values('date', ascending=True)

        logger.info("중복 제거 후: %d개", len(df))

        # 딕셔너리로 변환
        result = {
            'dates': df['date'].tolist(),
            'deposit_amounts': df['deposit_amount'].tolist(),
            'deposit_changes': df['deposit_change'].tolist(),
            'credit_amounts': df['credit_amount'].tolist(),
            'credit_changes': df['credit_change'].tolist()
        }

        logger.info("데이터 변환 완료")
        return result

    except Exception as e:
        logger.error("데이터 변환 실패: %s", str(e))
        return None


def scrape_page(page_num: int) -> Optional[List[Dict[str, Any]]]:
    """
    특정 페이지의 데이터를 수집합니다 (재시도 로직 포함).

    Args:
        page_num: 페이지 번호

    Returns:
        list: 데이터 리스트 (실패 시 None)
    """
    url = f"https://finance.naver.com/sise/sise_deposit.naver?page={page_num}"

    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7',
        'Referer': 'https://finance.naver.com/'
    }

    # Retry logic
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            logger.debug("URL 요청 (시도 %d/%d): %s", attempt, MAX_RETRIES, url)

            response = requests.get(url, headers=headers, timeout=REQUEST_TIMEOUT)
            response.raise_for_status()
            response.encoding = 'euc-kr'

            soup = BeautifulSoup(response.text, 'html.parser')

            # 테이블 찾기
            table = soup.find('table', {'class': 'type_1'})

            if not table:
                logger.warning("페이지 %d: 테이블을 찾을 수 없습니다", page_num)
                return None

            data_list: List[Dict[str, Any]] = []
            rows = table.find_all('tr')

            logger.debug("페이지 %d: %d개 행 발견", page_num, len(rows))

            for idx, row in enumerate(rows[2:], start=2):  # 헤더 2행 제외
                try:
                    cols = row.find_all('td')

                    if len(cols) < 5:
                        continue

                    raw_date = cols[0].get_text(strip=True)

                    if not raw_date:
                        continue

                    # 날짜 형식 변환 (YYYY-MM-DD)
                    date = convert_date_format(raw_date)

                    if not date:
                        continue

                    # 데이터 추출
                    deposit_amount = parse_number(cols[1].get_text(strip=True))
                    deposit_change = parse_number(cols[2].get_text(strip=True))
                    credit_amount = parse_number(cols[3].get_text(strip=True))
                    credit_change = parse_number(cols[4].get_text(strip=True))

                    data_list.append({
                        'date': date,
                        'deposit_amount': deposit_amount,
                        'deposit_change': deposit_change,
                        'credit_amount': credit_amount,
                        'credit_change': credit_change
                    })

                except Exception as e:
                    logger.debug("행 %d 파싱 실패: %s", idx, str(e))
                    continue

            logger.info("페이지 %d: %d개 데이터 추출", page_num, len(data_list))
            return data_list

        except requests.exceptions.Timeout as e:
            logger.warning(
                "페이지 %d 타임아웃 (시도 %d/%d): %s",
                page_num, attempt, MAX_RETRIES, str(e)
            )
            if attempt < MAX_RETRIES:
                time.sleep(RETRY_DELAY * attempt)
                continue
            else:
                logger.error("페이지 %d 최대 재시도 횟수 초과 (타임아웃)", page_num)
                return None

        except requests.exceptions.RequestException as e:
            logger.warning(
                "페이지 %d 요청 실패 (시도 %d/%d): %s",
                page_num, attempt, MAX_RETRIES, str(e)
            )
            if attempt < MAX_RETRIES:
                time.sleep(RETRY_DELAY * attempt)
                continue
            else:
                logger.error("페이지 %d 최대 재시도 횟수 초과", page_num)
                return None

        except Exception as e:
            logger.error("페이지 %d 스크래핑 실패: %s", page_num, str(e))
            return None

    return None


def convert_date_format(date_str: str) -> str:
    """
    다양한 날짜 형식을 YYYY-MM-DD 형식으로 변환합니다.

    Args:
        date_str: 원본 날짜 문자열 (예: "2025.01.29", "25.01.29", "2025-01-29")

    Returns:
        str: YYYY-MM-DD 형식의 날짜
    """
    try:
        # 공백 제거
        date_str = date_str.strip()

        if not date_str:
            return ""

        # 이미 YYYY-MM-DD 형식인 경우
        if len(date_str) == 10 and date_str[4] == '-' and date_str[7] == '-':
            return date_str

        # YYYY.MM.DD 형식 (네이버 증권에서 가장 흔한 형식)
        if '.' in date_str:
            parts = date_str.split('.')
            if len(parts) == 3:
                year = parts[0].strip()
                month = parts[1].strip().zfill(2)
                day = parts[2].strip().zfill(2)

                # YY.MM.DD 형식인 경우 (2자리 연도)
                if len(year) == 2:
                    year = '20' + year

                return f"{year}-{month}-{day}"

        # YYYYMMDD 형식
        if len(date_str) == 8 and date_str.isdigit():
            return f"{date_str[:4]}-{date_str[4:6]}-{date_str[6:]}"

        # YYYY/MM/DD 형식
        if '/' in date_str:
            parts = date_str.split('/')
            if len(parts) == 3:
                year = parts[0].strip()
                month = parts[1].strip().zfill(2)
                day = parts[2].strip().zfill(2)

                if len(year) == 2:
                    year = '20' + year

                return f"{year}-{month}-{day}"

        logger.warning("Unknown date format: %s", date_str)
        return date_str  # 변환 실패 시 원본 반환

    except Exception as e:
        logger.error("Date conversion error for '%s': %s", date_str, str(e))
        return date_str


def parse_number(text: str) -> float:
    """
    텍스트에서 숫자를 추출합니다.

    Args:
        text: 숫자가 포함된 텍스트

    Returns:
        float: 추출된 숫자 (실패 시 0.0)
    """
    try:
        # 쉼표와 "억원" 제거
        cleaned = text.replace(',', '').replace('억원', '').replace('억', '').strip()

        # 빈 문자열이나 "-" 처리
        if not cleaned or cleaned == '-':
            return 0.0

        return float(cleaned)

    except ValueError as e:
        logger.debug("숫자 변환 실패: '%s' -> %s", text, str(e))
        return 0.0
    except Exception as e:
        logger.error("파싱 오류: '%s' -> %s", text, str(e))
        return 0.0


def get_latest_data() -> Optional[Dict[str, List[Any]]]:
    """
    최신 데이터(1페이지)만 수집합니다.

    Returns:
        dict: 최신 데이터 (실패 시 None)
    """
    logger.info("최신 데이터 수집 시작")
    return scrape_deposit_data(num_pages=1)


def get_extended_data() -> Optional[Dict[str, List[Any]]]:
    """
    확장 데이터(10페이지)를 수집합니다.

    Returns:
        dict: 확장 데이터 (실패 시 None)
    """
    logger.info("확장 데이터 수집 시작")
    return scrape_deposit_data(num_pages=EXTENDED_NUM_PAGES)
