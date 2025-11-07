"""
증시자금동향 데이터 수집 모듈 (개선 버전)
네이버 증권에서 고객예탁금과 신용잔고 데이터를 수집합니다.
"""

import requests
from bs4 import BeautifulSoup
import pandas as pd
from datetime import datetime
import time
import sys


def scrape_deposit_data(num_pages=5):
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
    """
    print(f"[deposit_scraper] 데이터 수집 시작: {num_pages}페이지", file=sys.stderr)

    all_data = []

    for page_num in range(1, num_pages + 1):
        try:
            print(f"[deposit_scraper] 페이지 {page_num} 수집 중...", file=sys.stderr)
            page_data = scrape_page(page_num)

            if page_data:
                all_data.extend(page_data)
                print(f"[deposit_scraper] 페이지 {page_num}: {len(page_data)}개 수집", file=sys.stderr)
            else:
                print(f"[deposit_scraper] 페이지 {page_num}: 데이터 없음", file=sys.stderr)

            # 요청 간 딜레이 (서버 부하 방지)
            if page_num < num_pages:
                time.sleep(0.5)

        except Exception as e:
            print(f"[deposit_scraper] 페이지 {page_num} 수집 실패: {e}", file=sys.stderr)
            continue

    if not all_data:
        print("[deposit_scraper] 수집된 데이터가 없습니다", file=sys.stderr)
        return None

    print(f"[deposit_scraper] 총 {len(all_data)}개 데이터 수집", file=sys.stderr)

    # 데이터프레임 생성
    try:
        df = pd.DataFrame(all_data)

        # 중복 제거 및 정렬
        df = df.drop_duplicates(subset=['date'], keep='first')
        df = df.sort_values('date', ascending=True)

        print(f"[deposit_scraper] 중복 제거 후: {len(df)}개", file=sys.stderr)

        # 딕셔너리로 변환
        result = {
            'dates': df['date'].tolist(),
            'deposit_amounts': df['deposit_amount'].tolist(),
            'deposit_changes': df['deposit_change'].tolist(),
            'credit_amounts': df['credit_amount'].tolist(),
            'credit_changes': df['credit_change'].tolist()
        }

        print(f"[deposit_scraper] 데이터 변환 완료", file=sys.stderr)
        return result

    except Exception as e:
        print(f"[deposit_scraper] 데이터 변환 실패: {e}", file=sys.stderr)
        return None


def scrape_page(page_num):
    """
    특정 페이지의 데이터를 수집합니다.

    Args:
        page_num: 페이지 번호

    Returns:
        list: 데이터 리스트
    """

    url = f"https://finance.naver.com/sise/sise_deposit.naver?page={page_num}"

    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7',
        'Referer': 'https://finance.naver.com/'
    }

    try:
        print(f"[deposit_scraper] URL 요청: {url}", file=sys.stderr)

        response = requests.get(url, headers=headers, timeout=15)
        response.raise_for_status()
        response.encoding = 'euc-kr'

        soup = BeautifulSoup(response.text, 'html.parser')

        # 테이블 찾기
        table = soup.find('table', {'class': 'type_1'})

        if not table:
            print(f"[deposit_scraper] 페이지 {page_num}: 테이블을 찾을 수 없습니다", file=sys.stderr)
            return None

        data_list = []
        rows = table.find_all('tr')

        print(f"[deposit_scraper] 페이지 {page_num}: {len(rows)}개 행 발견", file=sys.stderr)

        for idx, row in enumerate(rows[2:], start=2):  # 헤더 2행 제외
            try:
                cols = row.find_all('td')

                if len(cols) < 5:
                    continue

                date = cols[0].get_text(strip=True)

                if not date or date == '':
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
                print(f"[deposit_scraper] 행 {idx} 파싱 실패: {e}", file=sys.stderr)
                continue

        print(f"[deposit_scraper] 페이지 {page_num}: {len(data_list)}개 데이터 추출", file=sys.stderr)
        return data_list

    except requests.exceptions.RequestException as e:
        print(f"[deposit_scraper] 페이지 {page_num} 요청 실패: {e}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"[deposit_scraper] 페이지 {page_num} 스크래핑 실패: {e}", file=sys.stderr)
        return None


def parse_number(text):
    """
    텍스트에서 숫자를 추출합니다.

    Args:
        text: 숫자가 포함된 텍스트

    Returns:
        float: 추출된 숫자
    """
    try:
        # 쉼표와 "억원" 제거
        cleaned = text.replace(',', '').replace('억원', '').replace('억', '').strip()

        # 빈 문자열이나 "-" 처리
        if not cleaned or cleaned == '-' or cleaned == '':
            return 0.0

        return float(cleaned)
    except ValueError as e:
        print(f"[deposit_scraper] 숫자 변환 실패: '{text}' -> {e}", file=sys.stderr)
        return 0.0
    except Exception as e:
        print(f"[deposit_scraper] 파싱 오류: '{text}' -> {e}", file=sys.stderr)
        return 0.0


def get_latest_data():
    """
    최신 데이터(1페이지)만 수집합니다.

    Returns:
        dict: 최신 데이터
    """
    print("[deposit_scraper] 최신 데이터 수집 시작", file=sys.stderr)
    return scrape_deposit_data(num_pages=1)


def get_extended_data():
    """
    확장 데이터(10페이지)를 수집합니다.

    Returns:
        dict: 확장 데이터
    """
    print("[deposit_scraper] 확장 데이터 수집 시작", file=sys.stderr)
    return scrape_deposit_data(num_pages=10)


# 테스트용
if __name__ == '__main__':
    print("증시자금동향 데이터 수집 테스트")
    data = scrape_deposit_data(num_pages=2)

    if data:
        print(f"수집된 데이터: {len(data['dates'])}개")
        print(f"날짜 범위: {data['dates'][0]} ~ {data['dates'][-1]}")
        print(f"최신 고객예탁금: {data['deposit_amounts'][-1]:,.0f}억원")
        print(f"최신 신용잔고: {data['credit_amounts'][-1]:,.0f}억원")
    else:
        print("데이터 수집 실패")