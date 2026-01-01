"""
Stocks Master Data Collector
Collects stock master data from pykrx
"""
import time
from datetime import datetime
from typing import Optional, Set

from pykrx import stock
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TaskProgressColumn

from config import Config
from .base import BaseCollector, CollectorResult


class StocksCollector(BaseCollector):
    """Collector for stock master data"""

    def __init__(self, config: Config):
        super().__init__(config, "stocks")

    @property
    def collector_type(self) -> str:
        return "stocks"

    def collect(self, resume: bool = True) -> CollectorResult:
        """Collect stock master data"""
        start_time = time.time()

        try:
            self.logger.info("Collecting stock master data...")

            all_stocks = []
            timestamp = self.get_timestamp_ms()

            with Progress(
                SpinnerColumn(),
                TextColumn("[progress.description]{task.description}"),
                BarColumn(),
                TaskProgressColumn(),
            ) as progress:
                task = progress.add_task("[cyan]Stocks...", total=2)

                for market in self.config.markets:
                    progress.update(
                        task,
                        description=f"[cyan]Stocks: {market}"
                    )

                    self.rate_limit(self.config.rate_limit.pykrx_delay)
                    market_stocks = self._get_market_stocks(market, timestamp)
                    all_stocks.extend(market_stocks)

                    progress.update(task, advance=1)

            # Remove duplicates by ticker
            unique_stocks = {s["ticker"]: s for s in all_stocks}
            all_stocks = list(unique_stocks.values())

            # Sort by ticker
            all_stocks.sort(key=lambda x: x["ticker"])

            elapsed = time.time() - start_time
            self.logger.info(
                f"Collected {len(all_stocks)} stocks in {elapsed:.1f}s"
            )

            return CollectorResult(
                success=True,
                data=all_stocks,
                record_count=len(all_stocks),
                elapsed_seconds=elapsed
            )

        except Exception as e:
            self.logger.error(f"Collection failed: {e}")
            return CollectorResult(
                success=False,
                error_message=str(e),
                elapsed_seconds=time.time() - start_time
            )

    def _get_market_stocks(self, market: str, timestamp: int) -> list[dict]:
        """Get all stocks for a market"""
        stocks = []

        try:
            tickers = self.retry_with_backoff(
                stock.get_market_ticker_list,
                market=market
            )

            for ticker in tickers:
                try:
                    name = stock.get_market_ticker_name(ticker)

                    stocks.append({
                        "ticker": ticker,
                        "name": name,
                        "market": market,
                        "sector": "",  # Sector info not available from pykrx
                        "isEtfHolding": False,  # Will be updated later
                        "lastUpdated": timestamp
                    })

                except Exception:
                    continue

        except Exception as e:
            self.logger.error(f"Error getting {market} stocks: {e}")

        return stocks

    def mark_etf_holdings(
        self,
        stocks: list[dict],
        holdings: list[dict]
    ) -> list[dict]:
        """
        Mark stocks that are held by ETFs.
        Call this after collecting both stocks and holdings.
        """
        # Get unique stock tickers from holdings
        held_tickers: Set[str] = set()
        for holding in holdings:
            held_tickers.add(holding["stockTicker"])

        # Mark stocks
        for stock_data in stocks:
            if stock_data["ticker"] in held_tickers:
                stock_data["isEtfHolding"] = True

        return stocks
