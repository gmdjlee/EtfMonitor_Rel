"""
Market Deposit Data Collector
Scrapes deposit and credit data from Naver Finance
"""
import re
import time
from datetime import datetime
from typing import Optional

import requests
from bs4 import BeautifulSoup
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TaskProgressColumn

from config import Config
from .base import BaseCollector, CollectorResult


class MarketDepositCollector(BaseCollector):
    """Collector for market deposit data (2020~2025)"""

    NAVER_URL = "https://finance.naver.com/sise/sise_deposit.naver"

    def __init__(self, config: Config):
        super().__init__(config, "market_deposit")

    @property
    def collector_type(self) -> str:
        return "market_deposit"

    def collect(self, resume: bool = True) -> CollectorResult:
        """Collect market deposit data"""
        start_time = time.time()

        try:
            # Load checkpoint if resuming
            checkpoint = self.load_checkpoint() if resume else None
            existing_data = self.load_data() if resume else []

            start_date = self.parse_date(self.config.date_range.market_deposit_start)
            end_date = self.parse_date(self.config.date_range.market_deposit_end)

            # Get start page from checkpoint
            start_page = 1
            if checkpoint and checkpoint.get("last_page"):
                start_page = checkpoint["last_page"] + 1
                self.logger.info(f"Resuming from page {start_page}")

            self.logger.info(
                f"Collecting market deposit: {self.format_date_iso(start_date)} ~ "
                f"{self.format_date_iso(end_date)}"
            )

            all_data = existing_data.copy()
            existing_dates = {d["date"] for d in all_data}

            # Estimate total pages (approximately 20 records per page, ~250 trading days per year)
            years = (end_date - start_date).days / 365
            estimated_records = int(years * 250)
            estimated_pages = (estimated_records // 20) + 10  # Add buffer

            with Progress(
                SpinnerColumn(),
                TextColumn("[progress.description]{task.description}"),
                BarColumn(),
                TaskProgressColumn(),
            ) as progress:
                task = progress.add_task(
                    f"[cyan]Market Deposit ({len(all_data)} records)...",
                    total=estimated_pages
                )

                page = start_page
                consecutive_old_pages = 0
                consecutive_empty_pages = 0

                while True:
                    progress.update(
                        task,
                        description=f"[cyan]Market Deposit: page {page}"
                    )

                    self.rate_limit(self.config.rate_limit.naver_delay)

                    try:
                        page_data = self._fetch_page(page)
                    except Exception as e:
                        self.logger.error(f"Error fetching page {page}: {e}")
                        self.save_checkpoint({"last_page": page - 1})
                        self.save_data(all_data)
                        raise

                    if not page_data:
                        consecutive_empty_pages += 1
                        if consecutive_empty_pages >= 3:
                            self.logger.info(f"No more data after page {page}")
                            break
                        page += 1
                        progress.update(task, advance=1)
                        continue

                    consecutive_empty_pages = 0

                    # Check date range
                    new_records = []
                    all_old = True

                    for record in page_data:
                        record_date = self.parse_date(record["date"])

                        if record_date < start_date:
                            # Past the date range, stop
                            all_old = True
                            break

                        if record_date > end_date:
                            # Future date, skip
                            continue

                        if record["date"] not in existing_dates:
                            all_old = False
                            new_records.append(record)
                            existing_dates.add(record["date"])

                    all_data.extend(new_records)

                    if all_old:
                        consecutive_old_pages += 1
                        if consecutive_old_pages >= 3:
                            self.logger.info(f"Reached start date at page {page}")
                            break
                    else:
                        consecutive_old_pages = 0

                    # Save checkpoint periodically
                    if page % 10 == 0:
                        self.save_checkpoint({"last_page": page})
                        self.save_data(all_data)

                    page += 1
                    progress.update(task, advance=1)

                    # Safety limit
                    if page > estimated_pages + 100:
                        self.logger.warning(f"Exceeded page limit at {page}")
                        break

            # Sort by date descending
            all_data.sort(key=lambda x: x["date"], reverse=True)

            # Clear checkpoint on success
            self.clear_checkpoint()
            self.clear_data()

            elapsed = time.time() - start_time
            self.logger.info(
                f"Collected {len(all_data)} records in {elapsed:.1f}s"
            )

            return CollectorResult(
                success=True,
                data=all_data,
                record_count=len(all_data),
                elapsed_seconds=elapsed
            )

        except Exception as e:
            self.logger.error(f"Collection failed: {e}")
            return CollectorResult(
                success=False,
                error_message=str(e),
                elapsed_seconds=time.time() - start_time,
                checkpoint_file=str(self.checkpoint_file) if self.checkpoint_file.exists() else None
            )

    def _fetch_page(self, page: int) -> list[dict]:
        """Fetch a single page from Naver Finance"""
        results = []

        params = {"page": page}
        headers = {
            "User-Agent": self.config.api.user_agent,
            "Accept-Language": "ko-KR,ko;q=0.9",
        }

        response = self.retry_with_backoff(
            requests.get,
            self.NAVER_URL,
            params=params,
            headers=headers,
            timeout=self.config.api.timeout
        )
        response.raise_for_status()

        # Naver uses EUC-KR encoding
        response.encoding = "euc-kr"
        soup = BeautifulSoup(response.text, "lxml")

        # Find the data table
        table = soup.find("table", class_="type_1")
        if not table:
            return results

        rows = table.find_all("tr")
        timestamp = self.get_timestamp_ms()

        for row in rows:
            cols = row.find_all("td")
            if len(cols) < 5:
                continue

            try:
                date_text = cols[0].get_text(strip=True)
                if not date_text:
                    continue

                date_str = self._parse_naver_date(date_text)
                if not date_str:
                    continue

                deposit_amount = self._parse_number(cols[1].get_text(strip=True))
                deposit_change = self._parse_number(cols[2].get_text(strip=True))
                credit_amount = self._parse_number(cols[3].get_text(strip=True))
                credit_change = self._parse_number(cols[4].get_text(strip=True))

                results.append({
                    "date": date_str,
                    "depositAmount": deposit_amount,
                    "depositChange": deposit_change,
                    "creditAmount": credit_amount,
                    "creditChange": credit_change,
                    "lastUpdated": timestamp
                })

            except Exception as e:
                self.logger.debug(f"Error parsing row: {e}")
                continue

        return results

    def _parse_naver_date(self, date_text: str) -> Optional[str]:
        """Parse date from Naver format to ISO format"""
        # Common formats: 2024.01.15, 2024-01-15, 24.01.15
        patterns = [
            (r"(\d{4})\.(\d{2})\.(\d{2})", lambda m: f"{m.group(1)}-{m.group(2)}-{m.group(3)}"),
            (r"(\d{4})-(\d{2})-(\d{2})", lambda m: f"{m.group(1)}-{m.group(2)}-{m.group(3)}"),
            (r"(\d{2})\.(\d{2})\.(\d{2})", lambda m: f"20{m.group(1)}-{m.group(2)}-{m.group(3)}"),
        ]

        for pattern, formatter in patterns:
            match = re.match(pattern, date_text)
            if match:
                return formatter(match)

        return None

    def _parse_number(self, text: str) -> float:
        """Parse Korean number format"""
        if not text:
            return 0.0

        # Remove whitespace and commas
        text = text.strip().replace(",", "").replace(" ", "")

        # Handle 억 (100 million)
        if "억" in text:
            text = text.replace("억", "")
            try:
                return float(text) * 100000000
            except ValueError:
                return 0.0

        # Handle negative numbers
        is_negative = False
        if text.startswith("-") or text.startswith("▼"):
            is_negative = True
            text = text.lstrip("-▼")
        elif text.startswith("+") or text.startswith("▲"):
            text = text.lstrip("+▲")

        try:
            value = float(text)
            return -value if is_negative else value
        except ValueError:
            return 0.0
