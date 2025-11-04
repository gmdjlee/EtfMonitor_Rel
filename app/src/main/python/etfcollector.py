"""
ETF 데이터 수집 모듈
pykrx 라이브러리를 사용하여 ETF 정보 수집
"""
from pykrx import stock
from typing import List, Dict, Set
import json


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
        print("\n" + "="*80)
        print("PYTHON: get_etf_list_with_names() called")
        print("="*80)

        # JSON 파싱
        include_keywords = json.loads(include_keywords_json) if include_keywords_json else []
        exclude_keywords = json.loads(exclude_keywords_json) if exclude_keywords_json else []

        print(f"\nInput keywords:")
        print(f"  Include ({len(include_keywords)}): {include_keywords}")
        print(f"  Exclude ({len(exclude_keywords)}): {exclude_keywords}")

        # '액티브' 확인
        if '액티브' not in include_keywords:
            print("\n⚠️ WARNING: '액티브' is not in include_keywords!")
        else:
            print("\n✓ '액티브' keyword found")

        # '액티브'를 제외한 다른 테마 키워드들
        other_keywords = [k for k in include_keywords if k != '액티브']
        print(f"  Other theme keywords ({len(other_keywords)}): {other_keywords[:5]}...")

        # ETF 티커 가져오기
        print(f"\nFetching ETF tickers for {date_str}...")
        tickers = stock.get_etf_ticker_list(date_str)
        if tickers is None:
            print("  ❌ ERROR: API returned None")
            return json.dumps([])

        print(f"  ✓ Got {len(tickers)} tickers from API")

        # 필터링
        print("\n" + "-"*80)
        print("FILTERING PROCESS:")
        print("-"*80)

        etf_list = []
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

                # ========================================
                # STEP 1: '액티브' 필수 확인
                # ========================================
                if '액티브' not in name:
                    stats['no_active'] += 1
                    continue

                # ========================================
                # STEP 2: 제외 키워드 확인
                # ========================================
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
                    # print(f"  ✗ {name} - excluded by '{matched_exclude}'")
                    continue

                # ========================================
                # STEP 3: 다른 테마 키워드 확인
                # ========================================
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
                        # print(f"  ✗ {name} - has '액티브' but no theme keyword")
                        continue
                else:
                    # 테마 키워드가 없으면 '액티브'만으로 통과
                    print(f"  ⚠️ No theme keywords provided, allowing: {name}")
                    has_theme_keyword = True

                # ========================================
                # 필터링 통과!
                # ========================================
                stats['passed'] += 1
                print(f"  ✓ PASS: {name}")
                print(f"      Matched: '액티브' + '{matched_theme}'")

                etf_list.append({
                    'ticker': str(ticker),
                    'name': name
                })

            except Exception as e:
                print(f"  Error processing {ticker}: {e}")
                continue

        # 결과 통계
        print("\n" + "="*80)
        print("FILTERING RESULTS:")
        print("="*80)
        print(f"  Total ETFs processed: {stats['total']}")
        print(f"  └─ No '액티브': {stats['no_active']}")
        print(f"  └─ Has '액티브': {stats['total'] - stats['no_active']}")
        print(f"     ├─ Excluded by keywords: {stats['excluded']}")
        print(f"     ├─ No theme keyword: {stats['no_theme']}")
        print(f"     └─ ✓ PASSED: {stats['passed']}")
        print("="*80)

        if etf_list:
            print(f"\nFinal list ({len(etf_list)} ETFs):")
            for i, etf in enumerate(etf_list, 1):
                print(f"  {i}. {etf['ticker']}: {etf['name']}")
        else:
            print("\n⚠️ WARNING: NO ETFs passed the filter!")
            print("Possible reasons:")
            print("  1. No ETFs with '액티브' in name")
            print("  2. All '액티브' ETFs were excluded")
            print("  3. No '액티브' ETFs have theme keywords")

        print("="*80 + "\n")

        return json.dumps(etf_list)

    except Exception as e:
        print(f"\n❌ EXCEPTION in get_etf_list_with_names: {e}")
        import traceback
        traceback.print_exc()
        return json.dumps([])


def get_etf_list(date_str: str) -> str:
    """
    특정 날짜의 ETF 티커 목록 조회 (필터링 없음)
    """
    try:
        tickers = stock.get_etf_ticker_list(date_str)
        if tickers is None:
            return json.dumps([])

        ticker_list = [str(ticker) for ticker in tickers]
        return json.dumps(ticker_list)
    except Exception as e:
        print(f"get_etf_list error: {e}")
        return json.dumps([])


def get_etf_name(ticker: str) -> str:
    """
    ETF 이름 조회
    """
    try:
        name = stock.get_etf_ticker_name(ticker)
        return str(name) if name else ""
    except Exception as e:
        print(f"get_etf_name error for {ticker}: {e}")
        return ""


def get_etf_holdings(ticker: str, date_str: str) -> str:
    """
    ETF 구성 종목 정보 조회
    """
    try:
        df = stock.get_etf_portfolio_deposit_file(ticker, date_str)

        if df is None or df.empty or '비중' not in df.columns:
            return json.dumps([])

        holdings = []
        for stock_ticker, row in df.iterrows():
            amount = float(row.get('금액', 0)) if '금액' in df.columns else 0.0

            holdings.append({
                'ticker': str(stock_ticker),
                'weight': float(row['비중']),
                'amount': amount
            })

        return json.dumps(holdings)

    except Exception as e:
        print(f"get_etf_holdings error for {ticker} on {date_str}: {e}")
        return json.dumps([])