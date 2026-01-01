"""
Fear & Greed Index Data Collector
Collects 5 indicators from KRX API to calculate Fear & Greed index
"""
import time
from datetime import datetime, timedelta
from typing import Optional, Tuple

import numpy as np
import pandas as pd
import requests
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TaskProgressColumn

from config import Config
from .base import BaseCollector, CollectorResult


class FearGreedCollector(BaseCollector):
    """Collector for Fear & Greed Index data (2020~2025)"""

    KRX_API_URL = "https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd"

    # Market codes for KRX API
    MARKET_CODES = {
        "KOSPI": {"idx": "1", "otp_idx": "001", "otp_opt": "12001"},
        "KOSDAQ": {"idx": "2", "otp_idx": "001", "otp_opt": "12002"},
        "VKOSPI": {"idx": "1", "otp_idx": "1", "otp_opt": "1"},
        "5년국채": {"idx": "", "otp_idx": "", "otp_opt": ""},
        "10년국채": {"idx": "", "otp_idx": "", "otp_opt": ""},
    }

    def __init__(self, config: Config):
        super().__init__(config, "fear_greed")
        self._session = None

    @property
    def collector_type(self) -> str:
        return "fear_greed"

    def _get_session(self) -> requests.Session:
        """Get or create requests session with KRX cookies"""
        if self._session is None:
            self._session = requests.Session()
            self._session.headers.update({
                "User-Agent": self.config.api.user_agent,
                "Referer": "https://data.krx.co.kr/",
            })
            # Initialize session by visiting main page
            try:
                self._session.get("https://data.krx.co.kr/", timeout=10)
            except Exception:
                pass
        return self._session

    def collect(self, resume: bool = True) -> CollectorResult:
        """Collect Fear & Greed Index data"""
        start_time = time.time()

        try:
            # Load checkpoint if resuming
            checkpoint = self.load_checkpoint() if resume else None
            existing_data = self.load_data() if resume else []

            # Calculate date range (request 3x for MA calculation loss)
            target_start = self.parse_date(self.config.date_range.fear_greed_start)
            target_end = self.parse_date(self.config.date_range.fear_greed_end)

            # Extend start date by 2 years for MA calculation
            actual_start = target_start - timedelta(days=365 * 2)

            # Resume from checkpoint
            if checkpoint and checkpoint.get("last_date"):
                last_date = self.parse_date(checkpoint["last_date"])
                actual_start = last_date + timedelta(days=1)
                self.logger.info(f"Resuming from {self.format_date_iso(actual_start)}")

            if actual_start > target_end:
                self.logger.info("Already completed")
                return CollectorResult(
                    success=True,
                    data=existing_data,
                    record_count=len(existing_data),
                    elapsed_seconds=time.time() - start_time
                )

            self.logger.info(
                f"Collecting Fear & Greed: {self.format_date_iso(actual_start)} ~ "
                f"{self.format_date_iso(target_end)} (target: {self.format_date_iso(target_start)})"
            )

            # Calculate batches
            total_days = (target_end - actual_start).days + 1
            batch_size = self.config.rate_limit.fear_greed_batch_days
            total_batches = (total_days + batch_size - 1) // batch_size

            all_raw_data = []
            existing_dates = {d["date"] for d in existing_data}

            with Progress(
                SpinnerColumn(),
                TextColumn("[progress.description]{task.description}"),
                BarColumn(),
                TaskProgressColumn(),
            ) as progress:
                task = progress.add_task(
                    f"[cyan]Fear & Greed ({len(existing_data)} records)...",
                    total=total_batches
                )

                current_start = actual_start

                while current_start <= target_end:
                    current_end = min(
                        current_start + timedelta(days=batch_size - 1),
                        target_end
                    )

                    progress.update(
                        task,
                        description=f"[cyan]Fear & Greed: {self.format_date_iso(current_start)}"
                    )

                    try:
                        # Fetch raw data for this period
                        self.rate_limit(self.config.rate_limit.krx_delay)
                        raw_df = self._fetch_raw_data(current_start, current_end)

                        if raw_df is not None and not raw_df.empty:
                            all_raw_data.append(raw_df)

                    except Exception as e:
                        self.logger.error(f"Error fetching data: {e}")
                        self.save_checkpoint({"last_date": self.format_date_iso(current_start)})
                        raise

                    # Update checkpoint
                    self.save_checkpoint({"last_date": self.format_date_iso(current_end)})

                    progress.update(task, advance=1)
                    current_start = current_end + timedelta(days=1)

            # Combine all raw data
            if all_raw_data:
                combined_df = pd.concat(all_raw_data, ignore_index=False)
                combined_df = combined_df[~combined_df.index.duplicated(keep="last")]
                combined_df = combined_df.sort_index()

                # Calculate Fear & Greed for each market
                kospi_data = self._calculate_fear_greed(combined_df, "KOSPI", target_start, target_end)
                kosdaq_data = self._calculate_fear_greed(combined_df, "KOSDAQ", target_start, target_end)

                all_data = existing_data + kospi_data + kosdaq_data
            else:
                all_data = existing_data

            # Remove duplicates by id
            unique_data = {d["id"]: d for d in all_data}
            all_data = list(unique_data.values())

            # Sort by date and market
            all_data.sort(key=lambda x: (x["date"], x["market"]))

            # Filter to target date range
            all_data = [
                d for d in all_data
                if self.parse_date(d["date"]) >= target_start
                and self.parse_date(d["date"]) <= target_end
            ]

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

    def _fetch_raw_data(
        self,
        start_date: datetime,
        end_date: datetime
    ) -> Optional[pd.DataFrame]:
        """Fetch raw market data from KRX API"""
        session = self._get_session()

        start_ymd = self.format_date_ymd(start_date)
        end_ymd = self.format_date_ymd(end_date)

        # Fetch index data
        indices = {}
        for market in ["KOSPI", "KOSDAQ"]:
            self.rate_limit(self.config.rate_limit.krx_delay)
            df = self._fetch_index_data(session, market, start_ymd, end_ymd)
            if df is not None:
                indices[market] = df

        # Fetch VKOSPI (volatility)
        self.rate_limit(self.config.rate_limit.krx_delay)
        vkospi_df = self._fetch_vkospi(session, start_ymd, end_ymd)

        # Fetch bond yields
        self.rate_limit(self.config.rate_limit.krx_delay)
        bond_5y = self._fetch_bond_yield(session, "5년", start_ymd, end_ymd)

        self.rate_limit(self.config.rate_limit.krx_delay)
        bond_10y = self._fetch_bond_yield(session, "10년", start_ymd, end_ymd)

        # Fetch options data (for Put-Call ratio)
        self.rate_limit(self.config.rate_limit.krx_delay)
        options_df = self._fetch_options_data(session, start_ymd, end_ymd)

        # Combine all data
        combined = pd.DataFrame()

        for market, df in indices.items():
            if combined.empty:
                combined = df.rename(columns={"close": f"{market}_close"})
            else:
                combined = combined.join(df.rename(columns={"close": f"{market}_close"}), how="outer")

        if vkospi_df is not None and not vkospi_df.empty:
            combined = combined.join(vkospi_df.rename(columns={"close": "VKOSPI"}), how="outer")

        if bond_5y is not None and not bond_5y.empty:
            combined = combined.join(bond_5y.rename(columns={"yield": "BOND_5Y"}), how="outer")

        if bond_10y is not None and not bond_10y.empty:
            combined = combined.join(bond_10y.rename(columns={"yield": "BOND_10Y"}), how="outer")

        if options_df is not None and not options_df.empty:
            combined = combined.join(options_df, how="outer")

        return combined if not combined.empty else None

    def _fetch_index_data(
        self,
        session: requests.Session,
        market: str,
        start_ymd: str,
        end_ymd: str
    ) -> Optional[pd.DataFrame]:
        """Fetch market index data"""
        try:
            # Use pykrx for simpler implementation
            from pykrx import stock

            ticker = "1001" if market == "KOSPI" else "2001"
            df = stock.get_index_ohlcv_by_date(start_ymd, end_ymd, ticker)

            if df is None or df.empty:
                return None

            result = pd.DataFrame({
                "close": df["종가"]
            })
            return result

        except Exception as e:
            self.logger.debug(f"Error fetching {market} index: {e}")
            return None

    def _fetch_vkospi(
        self,
        session: requests.Session,
        start_ymd: str,
        end_ymd: str
    ) -> Optional[pd.DataFrame]:
        """Fetch VKOSPI (volatility index) data"""
        try:
            from pykrx import stock

            df = stock.get_index_ohlcv_by_date(start_ymd, end_ymd, "1004")  # VKOSPI

            if df is None or df.empty:
                return None

            result = pd.DataFrame({
                "close": df["종가"]
            })
            return result

        except Exception as e:
            self.logger.debug(f"Error fetching VKOSPI: {e}")
            return None

    def _fetch_bond_yield(
        self,
        session: requests.Session,
        bond_type: str,
        start_ymd: str,
        end_ymd: str
    ) -> Optional[pd.DataFrame]:
        """Fetch bond yield data from KRX"""
        # This is simplified - actual implementation would need KRX bond API
        # For now, return None and handle missing data gracefully
        return None

    def _fetch_options_data(
        self,
        session: requests.Session,
        start_ymd: str,
        end_ymd: str
    ) -> Optional[pd.DataFrame]:
        """Fetch options data for Put-Call ratio"""
        # This is simplified - actual implementation would need KRX options API
        # For now, return None and handle missing data gracefully
        return None

    def _calculate_fear_greed(
        self,
        df: pd.DataFrame,
        market: str,
        start_date: datetime,
        end_date: datetime
    ) -> list[dict]:
        """Calculate Fear & Greed index for a market"""
        results = []

        if df is None or df.empty:
            return results

        close_col = f"{market}_close"
        if close_col not in df.columns:
            return results

        # Calculate MA period
        n = len(df)
        ma_period = min(125, max(10, int(n * 0.9)))

        # Calculate indicators
        close = df[close_col].dropna()

        if close.empty:
            return results

        # 1. Momentum: (Index - MA) / MA * 100
        ma = close.rolling(window=ma_period).mean()
        momentum = (close - ma) / ma * 100

        # 2. RSI (10-period)
        rsi = self._calculate_rsi(close, 10)

        # 3. Volatility (VKOSPI) - inverted
        volatility = None
        if "VKOSPI" in df.columns:
            volatility = df["VKOSPI"]

        # 4. Spread (10Y - 5Y bond)
        spread = None
        if "BOND_10Y" in df.columns and "BOND_5Y" in df.columns:
            spread = df["BOND_10Y"] - df["BOND_5Y"]

        # 5. Put-Call Ratio - inverted
        pcr = None
        if "PUT_VOL" in df.columns and "CALL_VOL" in df.columns:
            pcr = 1 - (df["PUT_VOL"] / df["CALL_VOL"])

        # Normalize each indicator to 0-100 scale
        def normalize(series):
            if series is None or series.empty:
                return None
            min_val = series.min()
            max_val = series.max()
            if max_val == min_val:
                return series * 0 + 50
            return (series - min_val) / (max_val - min_val) * 100

        mom_norm = normalize(momentum)
        rsi_norm = rsi  # Already 0-100
        vol_norm = normalize(100 - volatility) if volatility is not None else None
        spread_norm = normalize(spread) if spread is not None else None
        pcr_norm = normalize(pcr) if pcr is not None else None

        # Calculate Fear & Greed (equal weight 20% each)
        timestamp = self.get_timestamp_ms()

        for date_idx in close.index:
            if date_idx < start_date or date_idx > end_date:
                continue

            date_str = date_idx.strftime("%Y-%m-%d")
            record_id = f"{market}-{date_str}"

            # Get values
            idx_value = float(close.loc[date_idx]) if date_idx in close.index else 0
            mom_val = float(mom_norm.loc[date_idx]) if mom_norm is not None and date_idx in mom_norm.index else 50
            rsi_val = float(rsi_norm.loc[date_idx]) if rsi_norm is not None and date_idx in rsi_norm.index else 50
            vol_val = float(vol_norm.loc[date_idx]) if vol_norm is not None and date_idx in vol_norm.index else 50
            spread_val = float(spread_norm.loc[date_idx]) if spread_norm is not None and date_idx in spread_norm.index else 50
            pcr_val = float(pcr_norm.loc[date_idx]) if pcr_norm is not None and date_idx in pcr_norm.index else 50

            # Count available indicators
            available = [mom_val, rsi_val, vol_val, spread_val, pcr_val]
            valid_count = sum(1 for v in available if not pd.isna(v))

            if valid_count == 0:
                continue

            # Calculate Fear & Greed value
            fg_value = sum(v for v in available if not pd.isna(v)) / valid_count

            # Calculate oscillator (deviation from 50)
            oscillator = fg_value - 50

            results.append({
                "id": record_id,
                "market": market,
                "date": date_str,
                "indexValue": round(idx_value, 2),
                "fearGreedValue": round(fg_value, 2),
                "oscillator": round(oscillator, 2),
                "rsi": round(rsi_val, 2),
                "momentum": round(mom_val, 2),
                "putCallRatio": round(pcr_val, 2),
                "volatility": round(vol_val, 2),
                "spread": round(spread_val, 2),
                "lastUpdated": timestamp
            })

        return results

    def _calculate_rsi(self, prices: pd.Series, period: int = 14) -> pd.Series:
        """Calculate RSI indicator"""
        delta = prices.diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()

        rs = gain / loss
        rsi = 100 - (100 / (1 + rs))

        return rsi
