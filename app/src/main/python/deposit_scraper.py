"""
Market deposit data scraper from Naver Finance.
Collects customer deposits and credit balance data.
"""
import time
from typing import Any, Dict, List, Optional
import pandas as pd
from bs4 import BeautifulSoup

from core import get_logger, HttpClient, parse_num, to_iso, REQ_DELAY

log = get_logger(__name__)

BASE_URL = "https://finance.naver.com/sise/sise_deposit.naver"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept": "text/html,application/xhtml+xml",
    "Accept-Language": "ko-KR,ko;q=0.9",
    "Referer": "https://finance.naver.com/"
}


def _parse_date(s: str) -> str:
    """Parse date to YYYY-MM-DD format."""
    s = s.strip()
    if not s:
        return ""

    # Already YYYY-MM-DD
    if len(s) == 10 and s[4] == '-':
        return s

    # YYYY.MM.DD or YY.MM.DD
    if '.' in s:
        parts = s.split('.')
        if len(parts) == 3:
            y, m, d = parts[0].strip(), parts[1].strip().zfill(2), parts[2].strip().zfill(2)
            if len(y) == 2:
                y = '20' + y
            return f"{y}-{m}-{d}"

    # YYYYMMDD
    if len(s) == 8 and s.isdigit():
        return f"{s[:4]}-{s[4:6]}-{s[6:]}"

    # YYYY/MM/DD
    if '/' in s:
        parts = s.split('/')
        if len(parts) == 3:
            y, m, d = parts[0].strip(), parts[1].strip().zfill(2), parts[2].strip().zfill(2)
            if len(y) == 2:
                y = '20' + y
            return f"{y}-{m}-{d}"

    return s


def _scrape_page(client: HttpClient, page: int) -> List[Dict[str, Any]]:
    """Scrape a single page."""
    resp = client.get(f"{BASE_URL}?page={page}")
    if not resp:
        return []

    resp.encoding = 'euc-kr'
    soup = BeautifulSoup(resp.text, 'html.parser')
    table = soup.find('table', {'class': 'type_1'})

    if not table:
        return []

    data = []
    for row in table.find_all('tr')[2:]:  # Skip header rows
        cols = row.find_all('td')
        if len(cols) < 5:
            continue

        raw_date = cols[0].get_text(strip=True)
        if not raw_date:
            continue

        date = _parse_date(raw_date)
        if not date:
            continue

        data.append({
            'date': date,
            'deposit_amount': parse_num(cols[1].get_text(strip=True)),
            'deposit_change': parse_num(cols[2].get_text(strip=True)),
            'credit_amount': parse_num(cols[3].get_text(strip=True)),
            'credit_change': parse_num(cols[4].get_text(strip=True))
        })

    return data


def scrape_deposit_data(num_pages: int = 5) -> Optional[Dict[str, List[Any]]]:
    """
    Scrape market deposit data from Naver Finance.

    Returns: {
        'dates': [...],
        'deposit_amounts': [...],
        'deposit_changes': [...],
        'credit_amounts': [...],
        'credit_changes': [...]
    }
    """
    if num_pages <= 0:
        return None

    log.info("Scraping deposit data: %d pages", num_pages)
    client = HttpClient(HEADERS)
    all_data = []

    for page in range(1, num_pages + 1):
        page_data = _scrape_page(client, page)
        if page_data:
            all_data.extend(page_data)
            log.info("Page %d: %d records", page, len(page_data))

        if page < num_pages:
            time.sleep(REQ_DELAY)

    if not all_data:
        log.warning("No data collected")
        return None

    # Deduplicate and sort
    df = pd.DataFrame(all_data)
    df = df.drop_duplicates(subset=['date'], keep='first')
    df = df.sort_values('date', ascending=True)

    log.info("Total: %d records (deduped)", len(df))

    return {
        'dates': df['date'].tolist(),
        'deposit_amounts': df['deposit_amount'].tolist(),
        'deposit_changes': df['deposit_change'].tolist(),
        'credit_amounts': df['credit_amount'].tolist(),
        'credit_changes': df['credit_change'].tolist()
    }


def get_latest_data() -> Optional[Dict[str, List[Any]]]:
    """Get latest data (1 page)."""
    return scrape_deposit_data(1)


def get_extended_data() -> Optional[Dict[str, List[Any]]]:
    """Get extended data (10 pages)."""
    return scrape_deposit_data(10)
