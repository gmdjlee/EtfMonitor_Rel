"""
Market Index Data Collector (KOSPI, KOSDAQ)
Collects daily OHLCV data from pykrx
"""
import time
from datetime import datetime, timedelta
from typing import Optional

from pykrx import stock
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TaskProgressColumn

from config import Config, to_ymd, to_iso
from .base import BaseCollector, CollectorResult


class MarketIndexCollector(BaseCollector):
    """Collector for market index data (2000~2025)"""

    def __init__(self, config: Config):
        super().__init__(config, "market_index")

    @property
    def collector_type(self) -> str:
        return "market_index"

    def collect(self, resume: bool = True) -> CollectorResult:
        """Collect market index data"""
        start_time = time.time()

        try:
            # Load checkpoint if resuming
            checkpoint = self.load_checkpoint() if resume else None
            existing_data = self.load_data() if resume else []

            start_date = self.parse_date(self.config.date_range.market_index_start)
            end_date = self.parse_date(self.config.date_range.market_index_end)

            # Resume from last processed date
            if checkpoint and checkpoint.get("last_date"):
                last_date = self.parse_date(checkpoint["last_date"])
                start_date = last_date + timedelta(days=1)
                self.logger.info(f"Resuming from {self.format_date_iso(start_date)}")

            if start_date > end_date:
                self.logger.info("Already completed")
                return CollectorResult(
                    success=True,
                    data=existing_data,
                    record_count=len(existing_data),
                    elapsed_seconds=time.time() - start_time
                )

            # Calculate batches
            total_days = (end_date - start_date).days + 1
            batch_size = self.config.rate_limit.market_index_batch_days
            total_batches = (total_days + batch_size - 1) // batch_size

            self.logger.info(
                f"Collecting market index: {self.format_date_iso(start_date)} ~ "
                f"{self.format_date_iso(end_date)} ({total_batches} batches)"
            )

            all_data = existing_data.copy()

            with Progress(
                SpinnerColumn(),
                TextColumn("[progress.description]{task.description}"),
                BarColumn(),
                TaskProgressColumn(),
            ) as progress:
                task = progress.add_task(
                    f"[cyan]Market Index ({len(all_data)} records)...",
                    total=total_batches
                )

                current_start = start_date
                batch_num = 0

                while current_start <= end_date:
                    current_end = min(
                        current_start + timedelta(days=batch_size - 1),
                        end_date
                    )

                    batch_num += 1
                    progress.update(
                        task,
                        description=f"[cyan]Market Index: {self.format_date_iso(current_start)}"
                    )

                    # Collect for each market
                    for market in self.config.markets:
                        try:
                            self.rate_limit(self.config.rate_limit.pykrx_delay)
                            batch_data = self._fetch_market_data(
                                market,
                                self.format_date_ymd(current_start),
                                self.format_date_ymd(current_end)
                            )
                            all_data.extend(batch_data)
                        except Exception as e:
                            self.logger.error(f"Error fetching {market}: {e}")
                            # Save checkpoint and continue
                            self.save_checkpoint({"last_date": self.format_date_iso(current_start)})
                            self.save_data(all_data)
                            raise

                    # Update checkpoint
                    self.save_checkpoint({"last_date": self.format_date_iso(current_end)})
                    self.save_data(all_data)

                    progress.update(task, advance=1)
                    current_start = current_end + timedelta(days=1)

            # Remove duplicates by id
            unique_data = {d["id"]: d for d in all_data}
            all_data = list(unique_data.values())

            # Sort by date
            all_data.sort(key=lambda x: (x["date"], x["market"]))

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

    def _fetch_market_data(
        self,
        market: str,
        start_ymd: str,
        end_ymd: str
    ) -> list[dict]:
        """Fetch market index data using pykrx"""
        results = []

        try:
            # Get market index ticker
            ticker = "1001" if market == "KOSPI" else "2001"

            # Fetch OHLCV data
            df = self.retry_with_backoff(
                stock.get_index_ohlcv_by_date,
                start_ymd,
                end_ymd,
                ticker
            )

            if df is None or df.empty:
                return results

            timestamp = self.get_timestamp_ms()

            for date_idx, row in df.iterrows():
                date_str = date_idx.strftime("%Y-%m-%d")
                record_id = f"{market}-{date_str}"

                # Calculate change rate
                close_price = float(row.get("종가", 0))
                open_price = float(row.get("시가", 0))
                change_rate = 0.0
                if open_price > 0:
                    change_rate = round((close_price - open_price) / open_price * 100, 2)

                results.append({
                    "id": record_id,
                    "market": market,
                    "date": date_str,
                    "closePrice": close_price,
                    "openPrice": open_price,
                    "highPrice": float(row.get("고가", 0)),
                    "lowPrice": float(row.get("저가", 0)),
                    "volume": int(row.get("거래량", 0)),
                    "changeRate": change_rate,
                    "lastUpdated": timestamp
                })

        except Exception as e:
            self.logger.error(f"Error fetching {market} data: {e}")
            raise

        return results
