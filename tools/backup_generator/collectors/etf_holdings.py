"""
ETF Holdings Data Collector
Collects ETF list and their holdings data from pykrx
"""
import time
from datetime import datetime, timedelta
from typing import Optional, Set

from pykrx import stock
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TaskProgressColumn, TimeElapsedColumn

from config import Config
from .base import BaseCollector, CollectorResult


class EtfHoldingsCollector(BaseCollector):
    """Collector for ETF and Holdings data (2022~2025)"""

    # Cash ticker (원화예금)
    CASH_TICKER = "010010"

    def __init__(self, config: Config):
        super().__init__(config, "etf_holdings")

    @property
    def collector_type(self) -> str:
        return "etf_holdings"

    def collect(self, resume: bool = True) -> CollectorResult:
        """Collect ETF and Holdings data"""
        start_time = time.time()

        try:
            # Load checkpoint if resuming
            checkpoint = self.load_checkpoint() if resume else None
            existing_etfs = []
            existing_holdings = []
            processed_keys: Set[str] = set()

            if resume:
                saved_data = self.load_data()
                if saved_data:
                    existing_etfs = saved_data.get("etfs", [])
                    existing_holdings = saved_data.get("holdings", [])
                    processed_keys = set(saved_data.get("processed_keys", []))

            start_date = self.parse_date(self.config.date_range.etf_holdings_start)
            end_date = self.parse_date(self.config.date_range.etf_holdings_end)

            # Resume from checkpoint
            last_processed_date = None
            last_processed_etf = None
            if checkpoint:
                if checkpoint.get("last_date"):
                    last_processed_date = self.parse_date(checkpoint["last_date"])
                last_processed_etf = checkpoint.get("last_etf")

            self.logger.info(
                f"Collecting ETF Holdings: {self.format_date_iso(start_date)} ~ "
                f"{self.format_date_iso(end_date)}"
            )

            # Get list of business days
            business_days = self._get_business_days(start_date, end_date)

            if last_processed_date:
                business_days = [d for d in business_days if d > last_processed_date]
                self.logger.info(f"Resuming from {self.format_date_iso(last_processed_date)}")

            if not business_days:
                self.logger.info("No more days to process")
                return CollectorResult(
                    success=True,
                    data={"etfs": existing_etfs, "holdings": existing_holdings},
                    record_count=len(existing_holdings),
                    elapsed_seconds=time.time() - start_time
                )

            all_etfs = {e["ticker"]: e for e in existing_etfs}
            all_holdings = existing_holdings.copy()

            total_days = len(business_days)

            with Progress(
                SpinnerColumn(),
                TextColumn("[progress.description]{task.description}"),
                BarColumn(),
                TaskProgressColumn(),
                TimeElapsedColumn(),
            ) as progress:
                task = progress.add_task(
                    f"[cyan]ETF Holdings ({len(all_holdings)} records)...",
                    total=total_days
                )

                for day_idx, current_date in enumerate(business_days):
                    date_ymd = self.format_date_ymd(current_date)
                    date_iso = self.format_date_iso(current_date)

                    progress.update(
                        task,
                        description=f"[cyan]ETF Holdings: {date_iso} ({day_idx + 1}/{total_days})"
                    )

                    try:
                        # Get filtered ETF list for this date
                        self.rate_limit(self.config.rate_limit.pykrx_delay)
                        etf_list = self._get_filtered_etf_list(date_ymd)

                        if not etf_list:
                            progress.update(task, advance=1)
                            continue

                        # If resuming within a day, skip already processed ETFs
                        etfs_to_process = etf_list
                        if last_processed_date and current_date == last_processed_date and last_processed_etf:
                            skip_until_found = True
                            filtered = []
                            for etf in etf_list:
                                if skip_until_found:
                                    if etf["ticker"] == last_processed_etf:
                                        skip_until_found = False
                                    continue
                                filtered.append(etf)
                            etfs_to_process = filtered

                        # Process each ETF
                        for etf in etfs_to_process:
                            ticker = etf["ticker"]
                            name = etf["name"]

                            # Add to ETF list
                            if ticker not in all_etfs:
                                all_etfs[ticker] = {
                                    "ticker": ticker,
                                    "name": name
                                }

                            # Get holdings
                            key = f"{ticker}-{date_iso}"
                            if key in processed_keys:
                                continue

                            self.rate_limit(self.config.rate_limit.etf_holdings_delay)
                            holdings = self._get_etf_holdings(ticker, date_ymd, date_iso)

                            all_holdings.extend(holdings)
                            processed_keys.add(key)

                            # Save checkpoint periodically
                            if len(all_holdings) % 100 == 0:
                                self._save_progress(
                                    all_etfs, all_holdings, processed_keys,
                                    date_iso, ticker
                                )

                        # Update checkpoint after each day
                        self._save_progress(
                            all_etfs, all_holdings, processed_keys,
                            date_iso, None
                        )

                    except Exception as e:
                        self.logger.error(f"Error processing {date_iso}: {e}")
                        self._save_progress(
                            all_etfs, all_holdings, processed_keys,
                            date_iso, None
                        )
                        raise

                    progress.update(task, advance=1)

                    # Clear last_processed_etf after first day
                    last_processed_etf = None

            # Convert ETF dict to list
            etf_list = list(all_etfs.values())

            # Sort holdings by date and ETF
            all_holdings.sort(key=lambda x: (x["date"], x["etfTicker"], x["stockTicker"]))

            # Clear checkpoint on success
            self.clear_checkpoint()
            self.clear_data()

            elapsed = time.time() - start_time
            self.logger.info(
                f"Collected {len(etf_list)} ETFs, {len(all_holdings)} holdings in {elapsed:.1f}s"
            )

            return CollectorResult(
                success=True,
                data={"etfs": etf_list, "holdings": all_holdings},
                record_count=len(all_holdings),
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

    def _save_progress(
        self,
        etfs: dict,
        holdings: list,
        processed_keys: Set[str],
        last_date: str,
        last_etf: Optional[str]
    ):
        """Save progress to checkpoint and data files"""
        self.save_checkpoint({
            "last_date": last_date,
            "last_etf": last_etf
        })
        self.save_data({
            "etfs": list(etfs.values()),
            "holdings": holdings,
            "processed_keys": list(processed_keys)
        })

    def _get_business_days(
        self,
        start_date: datetime,
        end_date: datetime
    ) -> list[datetime]:
        """Get list of trading days between dates"""
        business_days = []

        try:
            start_ymd = self.format_date_ymd(start_date)
            end_ymd = self.format_date_ymd(end_date)

            # Use pykrx to get business days
            df = stock.get_index_ohlcv_by_date(start_ymd, end_ymd, "1001")

            if df is not None and not df.empty:
                for date_idx in df.index:
                    business_days.append(date_idx.to_pydatetime())

        except Exception as e:
            self.logger.warning(f"Error getting business days: {e}")
            # Fallback to simple weekday filter
            current = start_date
            while current <= end_date:
                if current.weekday() < 5:  # Monday = 0, Friday = 4
                    business_days.append(current)
                current += timedelta(days=1)

        return sorted(business_days)

    def _get_filtered_etf_list(self, date_ymd: str) -> list[dict]:
        """Get filtered ETF list based on config keywords"""
        etf_list = []

        try:
            # Get all ETFs with names
            tickers = stock.get_etf_ticker_list(date_ymd)

            for ticker in tickers:
                try:
                    name = stock.get_etf_ticker_name(ticker)

                    # Check include keywords (at least one must match)
                    include_match = any(
                        kw in name for kw in self.config.etf.include_keywords
                    )
                    if not include_match:
                        continue

                    # Check exclude keywords (none should match)
                    exclude_match = any(
                        kw in name for kw in self.config.etf.exclude_keywords
                    )
                    if exclude_match:
                        continue

                    etf_list.append({
                        "ticker": ticker,
                        "name": name
                    })

                except Exception:
                    continue

        except Exception as e:
            self.logger.error(f"Error getting ETF list: {e}")

        return etf_list

    def _get_etf_holdings(
        self,
        ticker: str,
        date_ymd: str,
        date_iso: str
    ) -> list[dict]:
        """Get holdings for a single ETF"""
        holdings = []

        try:
            # Don't use retry_with_backoff here because pykrx internal errors
            # (like uint64 overflow for negative values) won't be fixed by retrying
            df = stock.get_etf_portfolio_deposit_file(ticker, date_ymd)

            if df is None or df.empty:
                return holdings

            total_nav = df["평가금액"].sum() if "평가금액" in df.columns else 0

            for stock_ticker, row in df.iterrows():
                # Skip cash
                if str(stock_ticker) == self.CASH_TICKER:
                    continue

                stock_name = row.get("종목명", "")
                amount = float(row.get("평가금액", 0))

                # Calculate weight
                weight = 0.0
                if total_nav > 0:
                    weight = (amount / total_nav) * 100

                holdings.append({
                    "etfTicker": ticker,
                    "stockTicker": str(stock_ticker),
                    "stockName": stock_name,
                    "date": date_iso,
                    "weight": round(weight, 4),
                    "amount": amount,
                    "snapshotType": self.config.etf.snapshot_type
                })

        except OverflowError as e:
            # pykrx internal error with negative values and uint64
            self.logger.debug(f"Skipping {ticker} on {date_iso}: pykrx overflow error")
        except Exception as e:
            self.logger.debug(f"Error getting holdings for {ticker} on {date_iso}: {e}")

        return holdings
