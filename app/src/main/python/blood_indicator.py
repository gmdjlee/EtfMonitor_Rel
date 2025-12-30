"""
Blood Indicator calculation module.
Uses Yahoo Finance REST API to fetch US Treasury and HYG data.

BLOOD = IRX / (HYG Yield - 10Y Treasury)
- Rising BLOOD = Risk On (Market healthy)
- Falling BLOOD = Risk Off (Market stress)

Version: 2.0 (Unified with Colab code style)
Changes from v1.0:
- IRX divided by 100 (decimal format)
- Actual HYG dividend yield from Yahoo Finance (fixed value)
- Weekly resampling (W-FRI)
- No EMA smoothing (raw values)
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
YAHOO_QUOTE_URL = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/{symbol}"

# Tickers
TICKER_IRX = "^IRX"      # 13-Week Treasury Bill
TICKER_TNX = "^TNX"      # 10-Year Treasury Note
TICKER_HYG = "HYG"       # iShares High Yield Corporate Bond ETF
TICKER_SPY = "SPY"       # S&P 500 ETF (for reference)

# Request headers (similar to browser)
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
}

# Default HYG dividend yield (fallback value)
DEFAULT_HYG_YIELD = 5.72


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


def _get_hyg_dividend_yield() -> float:
    """
    Fetch actual HYG dividend yield from Yahoo Finance.

    Returns:
        Dividend yield as percentage (e.g., 5.72 for 5.72%)
    """
    try:
        url = YAHOO_QUOTE_URL.format(symbol=TICKER_HYG)
        params = {"modules": "summaryDetail"}

        resp = requests.get(url, params=params, headers=HEADERS, timeout=30)
        resp.raise_for_status()

        data = resp.json()

        if "quoteSummary" in data and "result" in data["quoteSummary"]:
            result = data["quoteSummary"]["result"]
            if result:
                summary = result[0].get("summaryDetail", {})
                dividend_yield = summary.get("dividendYield", {}).get("raw", None)

                if dividend_yield is not None:
                    # Yahoo returns as decimal (0.0572), convert to percentage (5.72)
                    yield_pct = dividend_yield * 100
                    log.info("HYG dividend yield fetched: %.4f%%", yield_pct)
                    return yield_pct

        log.warning("Could not fetch HYG dividend yield, using default %.2f%%", DEFAULT_HYG_YIELD)
        return DEFAULT_HYG_YIELD

    except Exception as e:
        log.error("Error fetching HYG dividend yield: %s", e)
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

        # Convert to Unix timestamps
        start_ts = int(start_dt.timestamp())
        end_ts = int((end_dt + timedelta(days=1)).timestamp())

        # Fetch data with delay between requests
        log.info("Downloading IRX (3M T-Bill)...")
        irx_df = _fetch_yahoo_chart(TICKER_IRX, start_ts, end_ts)
        time.sleep(0.3)

        log.info("Downloading TNX (10Y Treasury)...")
        tnx_df = _fetch_yahoo_chart(TICKER_TNX, start_ts, end_ts)
        time.sleep(0.3)

        log.info("Downloading HYG...")
        hyg_df = _fetch_yahoo_chart(TICKER_HYG, start_ts, end_ts)
        time.sleep(0.3)

        log.info("Downloading SPY (reference)...")
        spy_df = _fetch_yahoo_chart(TICKER_SPY, start_ts, end_ts)

        if irx_df is None or tnx_df is None or hyg_df is None:
            log.error("Failed to fetch required data")
            return None

        # Get actual HYG dividend yield (fixed value for entire period)
        hyg_dividend_yield = _get_hyg_dividend_yield()

        # === IRX: Divide by 100 (convert % to decimal) ===
        irx_df["Close"] = irx_df["Close"] / 100

        # === Resample to weekly (W-FRI) ===
        irx_weekly = irx_df["Close"].resample('W-FRI').last()
        tnx_weekly = tnx_df["Close"].resample('W-FRI').last()
        hyg_weekly = hyg_df["Close"].resample('W-FRI').last()
        spy_weekly = spy_df["Close"].resample('W-FRI').last() if spy_df is not None else None

        # Create combined DataFrame aligned by date
        df = pd.DataFrame(index=irx_weekly.index)
        df["IRX"] = irx_weekly
        df["TNX"] = tnx_weekly.reindex(df.index, method="ffill")
        df["HYG_Close"] = hyg_weekly.reindex(df.index, method="ffill")

        if spy_weekly is not None:
            df["SPY_Close"] = spy_weekly.reindex(df.index, method="ffill")
        else:
            df["SPY_Close"] = np.nan

        # Forward fill missing values
        df = df.ffill()

        # === Use actual HYG dividend yield (fixed value) ===
        df["HYG_Yield"] = hyg_dividend_yield

        # Calculate spread (denominator)
        df["Spread"] = df["HYG_Yield"] - df["TNX"]

        # === Calculate BLOOD indicator (no smoothing) ===
        df["BLOOD"] = np.where(
            df["Spread"].abs() > 0.1,
            df["IRX"] / df["Spread"],
            np.nan
        )

        # Calculate moving averages
        df["MA_20"] = df["BLOOD"].rolling(20).mean()
        df["MA_60"] = df["BLOOD"].rolling(60).mean()

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

        log.info("Calculated %d BLOOD indicator records", len(df))
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
        irx_val = row["IRX"]
        hyg_yield_val = row["HYG_Yield"]
        tnx_val = row["TNX"]
        spread_val = row["Spread"]
        spy_val = row["SPY_Close"]
        signal = row["Signal"]

        records.append({
            "id": f"BLOOD-{date_str}",
            "date": date_str,
            "bloodValue": round(float(blood_val), 6) if pd.notna(blood_val) else 0.0,
            "irx": round(float(irx_val), 6) if pd.notna(irx_val) else 0.0,
            "hygYield": round(float(hyg_yield_val), 4) if pd.notna(hyg_yield_val) else 0.0,
            "tenYearYield": round(float(tnx_val), 4) if pd.notna(tnx_val) else 0.0,
            "spreadValue": round(float(spread_val), 4) if pd.notna(spread_val) else 0.0,
            "spyClose": round(float(spy_val), 2) if pd.notna(spy_val) else None,
            "signalType": signal
        })

    return to_json(records)


def get_latest_blood_value() -> str:
    """
    Get latest Blood Indicator value.

    Returns:
        JSON string with latest Blood data
    """
    end = datetime.now()
    start = end - timedelta(days=30)

    df = fetch_blood_data(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"))

    if df is None or df.empty:
        return err_json("No data available")

    latest = df.iloc[-1]
    date_str = df.index[-1].strftime("%Y-%m-%d")

    return to_json({
        "date": date_str,
        "bloodValue": round(float(latest["BLOOD"]), 6),
        "signal": latest["Signal"],
        "irx": round(float(latest["IRX"]), 6),
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
