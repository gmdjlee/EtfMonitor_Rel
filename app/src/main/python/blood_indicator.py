"""
Blood Indicator calculation module.
Uses Yahoo Finance REST API to fetch US Treasury and HYG data.

BLOOD = IRX / (HYG Dividend Yield - 10Y Treasury)
- Rising BLOOD = Risk On (Market healthy)
- Falling BLOOD = Risk Off (Market stress)

Reference formula (weekly data):
    BLOOD_PROXY = IRX / (HYG Dividend Yield - 10Y Treasury Yield)
    - Data resampled to weekly (Friday) intervals
    - Moving averages: 20, 60, 120 periods (weeks)
"""
import json
import time
from typing import Optional, Dict, List, Any
from datetime import datetime, timedelta
import pandas as pd
import numpy as np
import requests

from core import get_logger, to_json, err_json

log = get_logger(__name__)

# Yahoo Finance API endpoints
YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}"
YAHOO_QUOTE_URL = "https://query1.finance.yahoo.com/v7/finance/quote"
YAHOO_QUOTESUMMARY_URL = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/{symbol}"

# Tickers
TICKER_IRX = "^IRX"      # 13-Week Treasury Bill (3-Month T-Bill)
TICKER_TNX = "^TNX"      # 10-Year Treasury Note
TICKER_HYG = "HYG"       # iShares High Yield Corporate Bond ETF
TICKER_SPY = "SPY"       # S&P 500 ETF (for reference)

# Request headers (similar to browser)
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
}

# Default HYG dividend yield if API fails (typical range: 5-8%)
DEFAULT_HYG_YIELD = 6.0


def _fetch_yahoo_chart(symbol: str, start_ts: int, end_ts: int) -> Optional[pd.DataFrame]:
    """
    Fetch historical data from Yahoo Finance chart API.

    Args:
        symbol: Ticker symbol
        start_ts: Start timestamp (Unix)
        end_ts: End timestamp (Unix)

    Returns:
        DataFrame with OHLCV data or None on error
    """
    try:
        url = YAHOO_CHART_URL.format(symbol=symbol)
        params = {
            "period1": start_ts,
            "period2": end_ts,
            "interval": "1d",
            "events": "history"
        }

        resp = requests.get(url, params=params, headers=HEADERS, timeout=30)
        resp.raise_for_status()

        data = resp.json()

        if "chart" not in data or "result" not in data["chart"]:
            log.error("Invalid response format for %s", symbol)
            return None

        result = data["chart"]["result"]
        if not result:
            log.error("No data returned for %s", symbol)
            return None

        result = result[0]
        timestamps = result.get("timestamp", [])
        quote = result.get("indicators", {}).get("quote", [{}])[0]

        if not timestamps or not quote:
            log.error("Missing data for %s", symbol)
            return None

        df = pd.DataFrame({
            "Date": pd.to_datetime(timestamps, unit="s"),
            "Open": quote.get("open", []),
            "High": quote.get("high", []),
            "Low": quote.get("low", []),
            "Close": quote.get("close", []),
            "Volume": quote.get("volume", [])
        })

        df.set_index("Date", inplace=True)
        df = df.dropna(subset=["Close"])
        # Ensure data is sorted by date (ascending)
        df = df.sort_index(ascending=True)

        return df

    except requests.exceptions.RequestException as e:
        log.error("Request error fetching %s: %s", symbol, e)
        return None
    except Exception as e:
        log.error("Error fetching %s: %s", symbol, e)
        return None


def _fetch_hyg_dividend_yield() -> float:
    """
    Fetch current HYG dividend yield from Yahoo Finance.

    Returns:
        HYG dividend yield as percentage (e.g., 5.5 for 5.5%)
    """
    try:
        url = YAHOO_QUOTESUMMARY_URL.format(symbol=TICKER_HYG)
        params = {"modules": "summaryDetail"}

        resp = requests.get(url, params=params, headers=HEADERS, timeout=15)
        resp.raise_for_status()

        data = resp.json()

        if "quoteSummary" not in data or "result" not in data["quoteSummary"]:
            log.warning("Invalid response format for HYG yield")
            return DEFAULT_HYG_YIELD

        result = data["quoteSummary"]["result"]
        if not result:
            log.warning("No result in HYG yield response")
            return DEFAULT_HYG_YIELD

        summary = result[0].get("summaryDetail", {})
        dividend_yield = summary.get("dividendYield", {})

        # Try to get the raw value first, then fmt
        if isinstance(dividend_yield, dict):
            raw_yield = dividend_yield.get("raw")
            if raw_yield is not None:
                # Convert from decimal to percentage (0.055 -> 5.5)
                yield_pct = float(raw_yield) * 100
                log.info("Fetched HYG dividend yield: %.2f%%", yield_pct)
                return yield_pct

        log.warning("Could not parse HYG dividend yield, using default")
        return DEFAULT_HYG_YIELD

    except requests.exceptions.RequestException as e:
        log.error("Request error fetching HYG yield: %s", e)
        return DEFAULT_HYG_YIELD
    except Exception as e:
        log.error("Error fetching HYG yield: %s", e)
        return DEFAULT_HYG_YIELD


def _calc_signal(blood_value: float, prev_blood: Optional[float], ma_20: Optional[float] = None) -> str:
    """
    Determine signal type based on BLOOD value and trend.

    Args:
        blood_value: Current BLOOD value
        prev_blood: Previous BLOOD value
        ma_20: 20-day moving average (optional)
    """
    if prev_blood is None:
        return "NEUTRAL"

    # Percentage change
    pct_change = (blood_value - prev_blood) / abs(prev_blood) * 100 if prev_blood != 0 else 0

    # Additional MA signal if available
    ma_signal = 0
    if ma_20 is not None and ma_20 != 0:
        if blood_value > ma_20 * 1.05:
            ma_signal = 1
        elif blood_value < ma_20 * 0.95:
            ma_signal = -1

    # Combine signals
    if pct_change > 3 or ma_signal > 0:
        return "RISK_ON"
    elif pct_change < -3 or ma_signal < 0:
        return "RISK_OFF"

    return "NEUTRAL"


def fetch_blood_data(start_date: str, end_date: str) -> Optional[pd.DataFrame]:
    """
    Fetch and calculate Blood Indicator data.

    Uses the reference formula:
        BLOOD = IRX / (HYG Dividend Yield - 10Y Treasury Yield)

    Data is resampled to weekly (Friday) intervals for smoother trend analysis.
    Moving averages (20, 60, 120 weeks) are calculated for trend signals.

    Args:
        start_date: Start date (YYYYMMDD or YYYY-MM-DD)
        end_date: End date (YYYYMMDD or YYYY-MM-DD)

    Returns:
        DataFrame with Blood Indicator data or None on error
    """
    log.info("Fetching Blood Indicator data: %s ~ %s", start_date, end_date)

    try:
        # Parse dates
        for fmt in ("%Y%m%d", "%Y-%m-%d"):
            try:
                start_dt = datetime.strptime(start_date.replace("-", "")[:8], "%Y%m%d")
                end_dt = datetime.strptime(end_date.replace("-", "")[:8], "%Y%m%d")
                break
            except ValueError:
                continue
        else:
            log.error("Failed to parse dates")
            return None

        # Add buffer for rolling calculations (120 weeks * 7 days = 840 days, use 900 for safety)
        buffer_start = start_dt - timedelta(days=900)

        # Convert to Unix timestamps
        start_ts = int(buffer_start.timestamp())
        end_ts = int((end_dt + timedelta(days=1)).timestamp())

        # Fetch HYG dividend yield first
        log.info("Fetching HYG dividend yield...")
        hyg_dividend_yield = _fetch_hyg_dividend_yield()
        log.info("HYG Dividend Yield: %.2f%%", hyg_dividend_yield)
        time.sleep(0.3)

        # Fetch historical data with delay between requests
        log.info("Downloading IRX (3M T-Bill)...")
        irx_df = _fetch_yahoo_chart(TICKER_IRX, start_ts, end_ts)
        time.sleep(0.3)

        log.info("Downloading TNX (10Y Treasury)...")
        tnx_df = _fetch_yahoo_chart(TICKER_TNX, start_ts, end_ts)
        time.sleep(0.3)

        log.info("Downloading SPY (reference)...")
        spy_df = _fetch_yahoo_chart(TICKER_SPY, start_ts, end_ts)

        if irx_df is None or tnx_df is None:
            log.error("Failed to fetch required data (IRX or TNX)")
            return None

        # Resample IRX to weekly (Friday) - convert from percentage (4.5 = 4.5%)
        # IRX is already in percentage format (e.g., 4.5 means 4.5%)
        irx_weekly = irx_df["Close"].resample("W-FRI").last()
        # Convert to decimal for calculation (4.5% -> 0.045)
        irx_weekly = irx_weekly / 100

        # Resample 10Y Treasury to weekly (Friday)
        # TNX is in percentage format (e.g., 4.5 means 4.5%)
        tnx_weekly = tnx_df["Close"].resample("W-FRI").last()

        # Resample SPY to weekly (Friday)
        if spy_df is not None:
            spy_weekly = spy_df["Close"].resample("W-FRI").last()
        else:
            spy_weekly = pd.Series(index=irx_weekly.index, dtype=float)

        # Create combined DataFrame
        df = pd.DataFrame(index=irx_weekly.index)
        df["IRX"] = irx_weekly
        df["TNX"] = tnx_weekly.reindex(df.index, method="ffill")
        df["SPY_Close"] = spy_weekly.reindex(df.index, method="ffill")

        # Use constant HYG dividend yield (current yield applied to all periods)
        # This follows the reference code's approach
        df["HYG_Yield"] = hyg_dividend_yield

        # Forward fill missing values
        df = df.ffill()
        df = df.dropna()

        # Calculate High Yield Spread Proxy: HYG Yield - 10Y Treasury
        df["Spread"] = df["HYG_Yield"] - df["TNX"]

        # Calculate BLOOD Proxy: IRX / (HYG Yield - 10Y Treasury)
        # Handle division by zero/small numbers
        df["BLOOD"] = np.where(
            df["Spread"].abs() > 0.1,
            df["IRX"] / df["Spread"],
            np.nan
        )

        # Calculate moving averages (20, 60, 120 weeks)
        df["MA_20"] = df["BLOOD"].rolling(20).mean()
        df["MA_60"] = df["BLOOD"].rolling(60).mean()
        df["MA_120"] = df["BLOOD"].rolling(120).mean()

        # Previous BLOOD value for signal calculation
        df["BLOOD_Prev"] = df["BLOOD"].shift(1)

        # Calculate signal
        signals = []
        for idx, row in df.iterrows():
            if pd.notna(row["BLOOD"]):
                signal = _calc_signal(
                    row["BLOOD"],
                    row["BLOOD_Prev"] if pd.notna(row["BLOOD_Prev"]) else None,
                    row["MA_20"] if pd.notna(row["MA_20"]) else None
                )
                signals.append(signal)
            else:
                signals.append("NEUTRAL")
        df["Signal"] = signals

        # Filter to requested date range
        mask = (df.index >= pd.Timestamp(start_dt)) & (df.index <= pd.Timestamp(end_dt))
        df = df.loc[mask]
        df = df.dropna(subset=["BLOOD"])

        if df.empty:
            log.error("No valid BLOOD data calculated")
            return None

        # Ensure final data is sorted by date (ascending) for correct chart display
        df = df.sort_index(ascending=True)

        log.info("Calculated %d BLOOD indicator records (weekly)", len(df))
        return df

    except Exception as e:
        log.error("Error fetching Blood data: %s", e)
        import traceback
        log.error(traceback.format_exc())
        return None


def get_blood_indicator_json(start_date: str, end_date: str) -> str:
    """
    Get Blood Indicator data as JSON string for Kotlin consumption.

    Args:
        start_date: Start date (YYYYMMDD)
        end_date: End date (YYYYMMDD)

    Returns:
        JSON string with Blood Indicator data (weekly intervals)
    """
    df = fetch_blood_data(start_date, end_date)

    if df is None or df.empty:
        return err_json("Failed to fetch Blood Indicator data")

    records = []
    for idx, row in df.iterrows():
        date_str = idx.strftime("%Y-%m-%d")

        blood_val = row["BLOOD"]
        # Convert IRX from decimal back to percentage for display (0.045 -> 4.5)
        irx_val = row["IRX"] * 100 if pd.notna(row["IRX"]) else 0.0
        hyg_yield_val = row["HYG_Yield"]
        tnx_val = row["TNX"]
        spread_val = row["Spread"]
        spy_val = row["SPY_Close"]
        signal = row["Signal"]
        ma_20 = row.get("MA_20")
        ma_60 = row.get("MA_60")
        ma_120 = row.get("MA_120")

        records.append({
            "id": f"BLOOD-{date_str}",
            "date": date_str,
            "bloodValue": round(float(blood_val), 4) if pd.notna(blood_val) else 0.0,
            "irx": round(float(irx_val), 4),
            "hygYield": round(float(hyg_yield_val), 4) if pd.notna(hyg_yield_val) else 0.0,
            "tenYearYield": round(float(tnx_val), 4) if pd.notna(tnx_val) else 0.0,
            "spreadValue": round(float(spread_val), 4) if pd.notna(spread_val) else 0.0,
            "spyClose": round(float(spy_val), 2) if pd.notna(spy_val) else None,
            "signalType": signal,
            "ma20": round(float(ma_20), 4) if pd.notna(ma_20) else None,
            "ma60": round(float(ma_60), 4) if pd.notna(ma_60) else None,
            "ma120": round(float(ma_120), 4) if pd.notna(ma_120) else None
        })

    return to_json(records)


def get_latest_blood_value() -> str:
    """
    Get latest Blood Indicator value.

    Returns:
        JSON string with latest Blood data
    """
    end = datetime.now()
    # Need more days since we're using weekly data now
    start = end - timedelta(days=60)

    df = fetch_blood_data(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"))

    if df is None or df.empty:
        return err_json("No data available")

    latest = df.iloc[-1]
    date_str = df.index[-1].strftime("%Y-%m-%d")

    # Convert IRX from decimal back to percentage for display (0.045 -> 4.5)
    irx_pct = latest["IRX"] * 100 if pd.notna(latest["IRX"]) else 0.0

    return to_json({
        "date": date_str,
        "bloodValue": round(float(latest["BLOOD"]), 4),
        "signal": latest["Signal"],
        "irx": round(float(irx_pct), 4),
        "hygYield": round(float(latest["HYG_Yield"]), 4),
        "tenYearYield": round(float(latest["TNX"]), 4),
        "spreadValue": round(float(latest["Spread"]), 4)
    })


# For testing
if __name__ == "__main__":
    end = datetime.now()
    start = end - timedelta(days=90)
    result = get_blood_indicator_json(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"))
    print(result[:500])  # Print first 500 chars
