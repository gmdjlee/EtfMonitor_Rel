"""
Blood Indicator Data Collector
Risk On/Off indicator using US 3-Month T-Bill and High Yield Spread
"""
import time
from datetime import datetime, timedelta
from typing import Optional

import pandas as pd
import requests
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TaskProgressColumn

from config import Config, to_ymd, to_iso
from .base import BaseCollector, CollectorResult


class BloodIndicatorCollector(BaseCollector):
    """Collector for Blood Indicator data (2007~2025)"""

    # Data sources
    FRED_API_URL = "https://api.stlouisfed.org/fred/series/observations"
    YAHOO_API_URL = "https://query1.finance.yahoo.com/v8/finance/chart"

    # Series IDs
    HIGH_YIELD_SPREAD_ID = "BAMLH0A0HYM2"  # ICE BofA US High Yield Spread
    TBILL_SYMBOL = "^IRX"  # 13-week T-Bill

    # SMA period for signal
    SMA_PERIOD = 100  # weeks

    def __init__(self, config: Config):
        super().__init__(config, "blood_indicator")

    @property
    def collector_type(self) -> str:
        return "blood_indicator"

    def collect(self, resume: bool = True) -> CollectorResult:
        """Collect Blood Indicator data"""
        start_time = time.time()

        # Validate FRED API key
        if not self.config.api.fred_api_key:
            return CollectorResult(
                success=False,
                error_message="FRED_API_KEY not configured. Get free key from: "
                              "https://fred.stlouisfed.org/docs/api/api_key.html",
                elapsed_seconds=time.time() - start_time
            )

        try:
            start_date = self.parse_date(self.config.date_range.blood_indicator_start)
            end_date = self.parse_date(self.config.date_range.blood_indicator_end)

            self.logger.info(
                f"Collecting Blood Indicator: {self.format_date_iso(start_date)} ~ "
                f"{self.format_date_iso(end_date)}"
            )

            with Progress(
                SpinnerColumn(),
                TextColumn("[progress.description]{task.description}"),
                BarColumn(),
                TaskProgressColumn(),
            ) as progress:
                task = progress.add_task("[cyan]Blood Indicator...", total=4)

                # Step 1: Fetch High Yield Spread from FRED
                progress.update(task, description="[cyan]Fetching High Yield Spread (FRED)...")
                self.rate_limit(self.config.rate_limit.fred_delay)
                hy_spread_df = self._fetch_fred_data(
                    self.HIGH_YIELD_SPREAD_ID,
                    start_date,
                    end_date
                )
                progress.update(task, advance=1)

                # Step 2: Fetch T-Bill rate from Yahoo Finance
                progress.update(task, description="[cyan]Fetching T-Bill Rate (Yahoo)...")
                self.rate_limit(self.config.rate_limit.yahoo_delay)
                tbill_df = self._fetch_yahoo_data(
                    self.TBILL_SYMBOL,
                    start_date,
                    end_date
                )
                progress.update(task, advance=1)

                # Step 3: Fetch SPY close for reference
                progress.update(task, description="[cyan]Fetching SPY (Yahoo)...")
                self.rate_limit(self.config.rate_limit.yahoo_delay)
                spy_df = self._fetch_yahoo_data("SPY", start_date, end_date)
                progress.update(task, advance=1)

                # Step 4: Calculate Blood Indicator
                progress.update(task, description="[cyan]Calculating Blood Indicator...")
                all_data = self._calculate_blood_indicator(
                    hy_spread_df, tbill_df, spy_df
                )
                progress.update(task, advance=1)

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
                elapsed_seconds=time.time() - start_time
            )

    def _fetch_fred_data(
        self,
        series_id: str,
        start_date: datetime,
        end_date: datetime
    ) -> pd.DataFrame:
        """Fetch data from FRED API"""
        params = {
            "series_id": series_id,
            "api_key": self.config.api.fred_api_key,
            "file_type": "json",
            "observation_start": self.format_date_iso(start_date),
            "observation_end": self.format_date_iso(end_date),
            "frequency": "w",  # Weekly
        }

        response = self.retry_with_backoff(
            requests.get,
            self.FRED_API_URL,
            params=params,
            timeout=self.config.api.timeout
        )
        response.raise_for_status()

        data = response.json()
        observations = data.get("observations", [])

        records = []
        for obs in observations:
            if obs.get("value") and obs["value"] != ".":
                records.append({
                    "date": obs["date"],
                    "value": float(obs["value"])
                })

        df = pd.DataFrame(records)
        if not df.empty:
            df["date"] = pd.to_datetime(df["date"])
            df = df.set_index("date")

        return df

    def _fetch_yahoo_data(
        self,
        symbol: str,
        start_date: datetime,
        end_date: datetime
    ) -> pd.DataFrame:
        """Fetch data from Yahoo Finance API"""
        # Convert to Unix timestamp
        start_ts = int(start_date.timestamp())
        end_ts = int(end_date.timestamp())

        url = f"{self.YAHOO_API_URL}/{symbol}"
        params = {
            "period1": start_ts,
            "period2": end_ts,
            "interval": "1wk",  # Weekly
            "events": "history",
        }

        headers = {
            "User-Agent": self.config.api.user_agent
        }

        response = self.retry_with_backoff(
            requests.get,
            url,
            params=params,
            headers=headers,
            timeout=self.config.api.timeout
        )
        response.raise_for_status()

        data = response.json()
        chart = data.get("chart", {}).get("result", [{}])[0]

        timestamps = chart.get("timestamp", [])
        quotes = chart.get("indicators", {}).get("quote", [{}])[0]

        records = []
        closes = quotes.get("close", [])

        for i, ts in enumerate(timestamps):
            if i < len(closes) and closes[i] is not None:
                date = datetime.fromtimestamp(ts)
                records.append({
                    "date": date,
                    "close": closes[i]
                })

        df = pd.DataFrame(records)
        if not df.empty:
            df = df.set_index("date")

        return df

    def _calculate_blood_indicator(
        self,
        hy_spread_df: pd.DataFrame,
        tbill_df: pd.DataFrame,
        spy_df: pd.DataFrame
    ) -> list[dict]:
        """Calculate Blood Indicator from collected data"""
        results = []

        if hy_spread_df.empty or tbill_df.empty:
            self.logger.warning("Insufficient data for Blood Indicator calculation")
            return results

        # Resample to weekly and align dates
        hy_weekly = hy_spread_df.resample("W-FRI").last()
        tbill_weekly = tbill_df.resample("W-FRI").last()
        spy_weekly = spy_df.resample("W-FRI").last() if not spy_df.empty else None

        # Merge data
        merged = pd.merge(
            hy_weekly, tbill_weekly,
            left_index=True, right_index=True,
            how="inner",
            suffixes=("_hy", "_tbill")
        )

        if spy_weekly is not None and not spy_weekly.empty:
            merged = pd.merge(
                merged, spy_weekly,
                left_index=True, right_index=True,
                how="left"
            )

        if merged.empty:
            return results

        # Calculate Blood value: T-Bill Rate / High Yield Spread * 100
        merged["blood"] = merged["close"] / merged["value"] * 100

        # Calculate 100-week SMA
        merged["blood_sma"] = merged["blood"].rolling(window=self.SMA_PERIOD).mean()

        timestamp = self.get_timestamp_ms()

        for date_idx, row in merged.iterrows():
            date_str = date_idx.strftime("%Y-%m-%d")
            blood_value = row["blood"]
            blood_sma = row["blood_sma"] if pd.notna(row["blood_sma"]) else 0.0

            # Determine signal
            if pd.isna(blood_sma) or blood_sma == 0:
                signal_type = "NEUTRAL"
                signal_color = "gray"
            elif blood_value > blood_sma:
                signal_type = "RISK_ON"
                signal_color = "green"
            else:
                signal_type = "RISK_OFF"
                signal_color = "red"

            spy_close = None
            if "close" in row and pd.notna(row.get("close")):
                spy_close = float(row["close"])

            results.append({
                "id": f"BLOOD-{date_str}",
                "date": date_str,
                "bloodValue": round(blood_value, 4),
                "bloodSma": round(blood_sma, 4) if blood_sma else 0.0,
                "us03my": round(float(row.get("close", 0) if "close" in row.index else row.iloc[1]), 4),
                "highYieldSpread": round(float(row["value"]), 4),
                "spyClose": spy_close,
                "signalType": signal_type,
                "signalColor": signal_color,
                "lastUpdated": timestamp
            })

        return results
