"""
Blood Indicator calculation module.
Replicates TradingView Pine Script Blood Indicator.

BLOOD = US03MY (3M T-Bill) / BAMLH0A0HYM2 (High Yield Spread)
- Above 100-week SMA = Risk On (Green)
- Below 100-week SMA = Risk Off (Red)

Data Sources:
- US03MY: Yahoo Finance (^IRX) - No API key required
- BAMLH0A0HYM2: FRED API - API key required (free)
  Get key: https://fred.stlouisfed.org/docs/api/api_key.html

Usage:
    # Set API key via environment variable
    export FRED_API_KEY=your_api_key

    # Or set programmatically
    from blood_indicator_v2 import set_fred_api_key
    set_fred_api_key("your_api_key")

Version: 2.0 (FRED API only)
"""
import json
import time
import os
from typing import Optional, Dict, List, Any
from datetime import datetime, timedelta
import pandas as pd
import numpy as np
import requests

from core import get_logger, to_json, err_json

log = get_logger(__name__)

# Yahoo Finance API endpoint
YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}"

# FRED API endpoint
FRED_API_URL = "https://api.stlouisfed.org/fred/series/observations"

# FRED API Key (get free key from https://fred.stlouisfed.org/docs/api/api_key.html)
# Set via environment variable or replace with your key
FRED_API_KEY = os.environ.get("FRED_API_KEY", "YOUR_FRED_API_KEY")


def set_fred_api_key(api_key: str) -> None:
    """
    Set FRED API key programmatically.

    Args:
        api_key: FRED API key string
    """
    global FRED_API_KEY
    FRED_API_KEY = api_key
    log.info("FRED API key updated")

# Tickers
TICKER_IRX = "^IRX"           # 13-Week Treasury Bill (proxy for US03MY)
TICKER_SPY = "SPY"            # S&P 500 ETF (for reference)

# FRED Series
FRED_HIGH_YIELD_SPREAD = "BAMLH0A0HYM2"  # ICE BofA US High Yield Spread

# Request headers
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
}

# SMA Length (100 weeks as per Pine Script)
SMA_LENGTH = 100


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

        return df

    except requests.exceptions.RequestException as e:
        log.error("Request error fetching %s: %s", symbol, e)
        return None
    except Exception as e:
        log.error("Error fetching %s: %s", symbol, e)
        return None


def _fetch_fred_series(series_id: str, start_date: str, end_date: str) -> Optional[pd.DataFrame]:
    """
    Fetch data from FRED API.

    Args:
        series_id: FRED series ID (e.g., BAMLH0A0HYM2)
        start_date: Start date (YYYY-MM-DD)
        end_date: End date (YYYY-MM-DD)

    Returns:
        DataFrame with series data or None on error
    """
    # Check if API key is available
    if FRED_API_KEY == "YOUR_FRED_API_KEY" or not FRED_API_KEY:
        log.error("FRED API key not set.")
        log.error("Get free key from https://fred.stlouisfed.org/docs/api/api_key.html")
        log.error("Set via: export FRED_API_KEY=your_key or call set_fred_api_key('your_key')")
        return None

    try:
        params = {
            "series_id": series_id,
            "api_key": FRED_API_KEY,
            "file_type": "json",
            "observation_start": start_date,
            "observation_end": end_date,
            "sort_order": "asc"
        }

        resp = requests.get(FRED_API_URL, params=params, headers=HEADERS, timeout=30)
        resp.raise_for_status()

        data = resp.json()

        if "observations" not in data:
            log.error("Invalid response format from FRED API for %s", series_id)
            return None

        observations = data["observations"]

        if not observations:
            log.error("No data returned from FRED API for %s", series_id)
            return None

        # Parse observations into DataFrame
        records = []
        for obs in observations:
            date = obs.get("date")
            value = obs.get("value")

            # FRED uses "." for missing values
            if value and value != ".":
                try:
                    records.append({
                        "date": pd.to_datetime(date),
                        series_id: float(value)
                    })
                except (ValueError, TypeError):
                    continue

        if not records:
            log.error("No valid data parsed from FRED API for %s", series_id)
            return None

        df = pd.DataFrame(records)
        df.set_index("date", inplace=True)

        log.info("Fetched %d records from FRED API for %s", len(df), series_id)
        return df

    except requests.exceptions.RequestException as e:
        log.error("Request error fetching FRED API %s: %s", series_id, e)
        return None
    except Exception as e:
        log.error("Error fetching FRED API %s: %s", series_id, e)
        return None


def _calc_signal(blood_value: float, sma_value: Optional[float]) -> str:
    """
    Determine signal based on Blood Indicator vs 100-week SMA.
    Matches Pine Script logic.

    Args:
        blood_value: Current Blood Indicator value
        sma_value: 100-week SMA value

    Returns:
        Signal string: RISK_ON (green) or RISK_OFF (red)
    """
    if sma_value is None or pd.isna(sma_value):
        return "NEUTRAL"

    if blood_value > sma_value:
        return "RISK_ON"   # Green in Pine Script
    else:
        return "RISK_OFF"  # Red in Pine Script


def fetch_blood_data(start_date: str, end_date: str) -> Optional[pd.DataFrame]:
    """
    Fetch and calculate Blood Indicator data.
    Blood Indicator = US03MY / BAMLH0A0HYM2

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

        # Extend start date to have enough data for 100-week SMA
        extended_start = start_dt - timedelta(weeks=SMA_LENGTH + 10)

        # Convert to Unix timestamps for Yahoo
        start_ts = int(extended_start.timestamp())
        end_ts = int((end_dt + timedelta(days=1)).timestamp())

        # Date strings for FRED
        fred_start = extended_start.strftime("%Y-%m-%d")
        fred_end = end_dt.strftime("%Y-%m-%d")

        # Fetch IRX (3M T-Bill) from Yahoo Finance
        log.info("Downloading IRX (3M T-Bill) from Yahoo Finance...")
        irx_df = _fetch_yahoo_chart(TICKER_IRX, start_ts, end_ts)
        time.sleep(0.3)

        # Fetch High Yield Spread from FRED
        log.info("Downloading BAMLH0A0HYM2 (High Yield Spread) from FRED...")
        spread_df = _fetch_fred_series(FRED_HIGH_YIELD_SPREAD, fred_start, fred_end)
        time.sleep(0.3)

        # Fetch SPY for reference (optional)
        log.info("Downloading SPY (reference)...")
        spy_df = _fetch_yahoo_chart(TICKER_SPY, start_ts, end_ts)

        if irx_df is None:
            log.error("Failed to fetch IRX data")
            return None

        if spread_df is None:
            log.error("Failed to fetch High Yield Spread data")
            return None

        # === Resample to weekly (W-FRI) ===
        irx_weekly = irx_df["Close"].resample("W-FRI").last()
        spread_weekly = spread_df[FRED_HIGH_YIELD_SPREAD].resample("W-FRI").last()
        spy_weekly = spy_df["Close"].resample("W-FRI").last() if spy_df is not None else None

        # Create combined DataFrame
        df = pd.DataFrame(index=irx_weekly.index)
        df["US03MY"] = irx_weekly
        df["HighYieldSpread"] = spread_weekly.reindex(df.index, method="ffill")

        if spy_weekly is not None:
            df["SPY_Close"] = spy_weekly.reindex(df.index, method="ffill")
        else:
            df["SPY_Close"] = np.nan

        # Forward fill missing values
        df = df.ffill()
        df = df.dropna(subset=["US03MY", "HighYieldSpread"])

        # === Calculate Blood Indicator ===
        # Blood = US03MY / HighYieldSpread (as per Pine Script)
        df["BLOOD"] = np.where(
            df["HighYieldSpread"].abs() > 0.01,
            df["US03MY"] / df["HighYieldSpread"],
            np.nan
        )

        # === Calculate 100-week SMA ===
        df["BLOOD_SMA"] = df["BLOOD"].rolling(window=SMA_LENGTH, min_periods=1).mean()

        # Calculate signal (above/below SMA)
        signals = []
        signal_colors = []
        for idx, row in df.iterrows():
            if pd.notna(row["BLOOD"]):
                signal = _calc_signal(row["BLOOD"], row["BLOOD_SMA"])
                signals.append(signal)
                signal_colors.append("green" if signal == "RISK_ON" else "red")
            else:
                signals.append("NEUTRAL")
                signal_colors.append("gray")

        df["Signal"] = signals
        df["SignalColor"] = signal_colors

        # Filter to requested date range
        mask = (df.index >= pd.Timestamp(start_dt)) & (df.index <= pd.Timestamp(end_dt))
        df = df.loc[mask]
        df = df.dropna(subset=["BLOOD"])

        if df.empty:
            log.error("No valid Blood Indicator data calculated")
            return None

        log.info("Calculated %d Blood Indicator records", len(df))
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
        JSON string with Blood Indicator data
    """
    df = fetch_blood_data(start_date, end_date)

    if df is None or df.empty:
        return err_json("Failed to fetch Blood Indicator data")

    records = []
    for idx, row in df.iterrows():
        date_str = idx.strftime("%Y-%m-%d")

        blood_val = row["BLOOD"]
        blood_sma = row["BLOOD_SMA"]
        us03my_val = row["US03MY"]
        spread_val = row["HighYieldSpread"]
        spy_val = row["SPY_Close"]
        signal = row["Signal"]
        signal_color = row["SignalColor"]

        records.append({
            "id": f"BLOOD-{date_str}",
            "date": date_str,
            "bloodValue": round(float(blood_val), 6) if pd.notna(blood_val) else 0.0,
            "bloodSma": round(float(blood_sma), 6) if pd.notna(blood_sma) else 0.0,
            "us03my": round(float(us03my_val), 4) if pd.notna(us03my_val) else 0.0,
            "highYieldSpread": round(float(spread_val), 4) if pd.notna(spread_val) else 0.0,
            "spyClose": round(float(spy_val), 2) if pd.notna(spy_val) else None,
            "signalType": signal,
            "signalColor": signal_color
        })

    return to_json(records)


def get_latest_blood_value() -> str:
    """
    Get latest Blood Indicator value.

    Returns:
        JSON string with latest Blood data
    """
    end = datetime.now()
    start = end - timedelta(days=60)  # 60 days to ensure we have recent data

    df = fetch_blood_data(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"))

    if df is None or df.empty:
        return err_json("No data available")

    latest = df.iloc[-1]
    date_str = df.index[-1].strftime("%Y-%m-%d")

    return to_json({
        "date": date_str,
        "bloodValue": round(float(latest["BLOOD"]), 6),
        "bloodSma": round(float(latest["BLOOD_SMA"]), 6),
        "signal": latest["Signal"],
        "signalColor": latest["SignalColor"],
        "us03my": round(float(latest["US03MY"]), 4),
        "highYieldSpread": round(float(latest["HighYieldSpread"]), 4)
    })


def get_blood_summary() -> str:
    """
    Get Blood Indicator summary statistics.

    Returns:
        JSON string with summary data
    """
    end = datetime.now()
    start = end - timedelta(weeks=SMA_LENGTH + 52)  # ~3 years of data

    df = fetch_blood_data(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"))

    if df is None or df.empty:
        return err_json("No data available")

    latest = df.iloc[-1]

    # Count signals
    risk_on_count = (df["Signal"] == "RISK_ON").sum()
    risk_off_count = (df["Signal"] == "RISK_OFF").sum()

    return to_json({
        "period": {
            "start": df.index[0].strftime("%Y-%m-%d"),
            "end": df.index[-1].strftime("%Y-%m-%d"),
            "weeks": len(df)
        },
        "latest": {
            "date": df.index[-1].strftime("%Y-%m-%d"),
            "bloodValue": round(float(latest["BLOOD"]), 6),
            "bloodSma": round(float(latest["BLOOD_SMA"]), 6),
            "signal": latest["Signal"],
            "signalColor": latest["SignalColor"]
        },
        "statistics": {
            "mean": round(float(df["BLOOD"].mean()), 4),
            "std": round(float(df["BLOOD"].std()), 4),
            "min": round(float(df["BLOOD"].min()), 4),
            "max": round(float(df["BLOOD"].max()), 4)
        },
        "signals": {
            "riskOnWeeks": int(risk_on_count),
            "riskOffWeeks": int(risk_off_count),
            "riskOnPct": round(risk_on_count / len(df) * 100, 1)
        }
    })


# For testing
if __name__ == "__main__":
    import sys

    # FRED API key is required
    # Set via environment variable before running:
    #   export FRED_API_KEY=your_fred_api_key_here
    #
    # Or set programmatically:
    #   set_fred_api_key("your_fred_api_key_here")

    print("=" * 60)
    print("Blood Indicator Test")
    print("=" * 60)

    if FRED_API_KEY == "YOUR_FRED_API_KEY" or not FRED_API_KEY:
        print("\n[ERROR] FRED API key is required.")
        print("\nSetup instructions:")
        print("  1. Get free key: https://fred.stlouisfed.org/docs/api/api_key.html")
        print("  2. Set environment: export FRED_API_KEY=your_key")
        print("  3. Or call: set_fred_api_key('your_key')")
        sys.exit(1)

    end = datetime.now()
    start = end - timedelta(days=180)  # 6 months

    print(f"\nFetching data: {start.strftime('%Y-%m-%d')} to {end.strftime('%Y-%m-%d')}")
    print()

    result = get_blood_indicator_json(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"))

    # Pretty print result
    import json
    data = json.loads(result)

    if "error" in data and data["error"]:
        print(f"Error: {data.get('message', 'Unknown error')}")
        sys.exit(1)

    print(f"Total records: {len(data)}")
    print("\nLatest 3 records:")
    for record in data[-3:]:
        print(f"  {record['date']}: BLOOD={record['bloodValue']:.4f}, "
              f"SMA={record['bloodSma']:.4f}, Signal={record['signalType']}")
