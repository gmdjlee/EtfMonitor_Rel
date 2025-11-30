"""
Trend signal analysis module.
Technical indicators: MA, CMF, Fear & Greed Index with buy/sell signals.
"""
import json
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional
import numpy as np
import pandas as pd
from pykrx import stock

from core import get_logger, get_name, to_json, err_json

log = get_logger(__name__)


def _get_ohlcv(ticker: str, days: int, interval: str = "d") -> Optional[pd.DataFrame]:
    """Get OHLCV data."""
    extra = days * 2 if interval == "w" else days
    end = datetime.now()
    start = end - timedelta(days=extra)

    try:
        df = stock.get_market_ohlcv(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"), ticker)
        if df.empty:
            return None

        df = df.rename(columns={"시가": "O", "고가": "H", "저가": "L", "종가": "C", "거래량": "V"})
        df = df[["O", "H", "L", "C", "V"]]

        if interval == "w":
            df = df.resample("W").agg({"O": "first", "H": "max", "L": "min", "C": "last", "V": "sum"}).dropna()

        return df if not df.empty else None

    except Exception as e:
        log.error("OHLCV error (%s): %s", ticker, e)
        return None


def _calc_cmf(df: pd.DataFrame, period: int = 4) -> pd.Series:
    """Calculate Chaikin Money Flow."""
    hl = (df["H"] - df["L"]).replace(0, np.nan)
    mfm = ((df["C"] - df["L"]) - (df["H"] - df["C"])) / hl
    mfv = mfm * df["V"]
    return mfv.rolling(period).sum() / df["V"].rolling(period).sum()


def _calc_fg(df: pd.DataFrame, mom_period: int = 5, pos_period: int = 52) -> pd.Series:
    """Calculate Fear & Greed Index (-1 to +1)."""
    # Momentum (45%)
    log_ret = np.log(df["C"] / df["C"].shift(mom_period))
    mom = (log_ret / 0.1).clip(-1, 1)

    # Position in 52-week range (45%)
    hi = df["C"].rolling(pos_period, min_periods=10).max()
    lo = df["C"].rolling(pos_period, min_periods=10).min()
    rng = (hi - lo).replace(0, np.nan)
    pos = ((df["C"] - lo) / rng * 2) - 1

    # Volume spike (5%)
    vol_ma = df["V"].rolling(20, min_periods=5).mean()
    vol_score = (df["V"] / vol_ma - 1).clip(-1, 1)

    # Volatility (5%, inverted)
    ret = df["C"].pct_change()
    vol_recent = ret.rolling(5, min_periods=3).std()
    vol_avg = ret.rolling(20, min_periods=10).std()
    vol_spike = (vol_recent / vol_avg.replace(0, np.nan) - 1).clip(-1, 1) * -1

    return mom * 0.45 + pos * 0.45 + vol_score * 0.05 + vol_spike * 0.05


def _gen_signals(df: pd.DataFrame, ma_period: int, cmf_period: int) -> pd.DataFrame:
    """Generate buy/sell signals."""
    r = df.copy()
    r["MA"] = r["C"].rolling(ma_period).mean()
    r["CMF"] = _calc_cmf(r, cmf_period)
    r["FG"] = _calc_fg(r)
    r["PH"] = r["H"].shift(1)
    r["PL"] = r["L"].shift(1)

    # Buy conditions
    b1 = r["H"] > r["PH"]  # High breakout
    b2 = r["C"] > r["MA"]  # Above MA
    b3 = r["CMF"] > 0      # Money inflow
    b_cnt = b1.astype(int) + b2.astype(int) + b3.astype(int)

    # Sell conditions
    s1 = r["L"] < r["PL"]  # Low breakdown
    s2 = r["C"] < r["MA"]  # Below MA
    s3 = r["CMF"] < 0      # Money outflow
    s_cnt = s1.astype(int) + s2.astype(int) + s3.astype(int)

    r["Buy"] = (b_cnt == 3).astype(int)
    r["AuxBuy"] = ((b_cnt == 2) & b2).astype(int)
    r["Sell"] = (s_cnt == 3).astype(int)
    r["AuxSell"] = ((s_cnt == 2) & s2).astype(int)

    return r


def get_trend_signal_analysis(ticker: str, days: int = 180, interval: str = "w",
                               ma_period: int = 20, cmf_period: int = 4) -> str:
    """
    Analyze trend signals for a stock.

    Args:
        ticker: Stock code
        days: Analysis period
        interval: "d" (daily) or "w" (weekly)
        ma_period: MA period
        cmf_period: CMF period

    Returns: JSON with OHLCV, MA, CMF, Fear&Greed, signals
    """
    if not ticker or not ticker.strip():
        return err_json("종목 코드가 필요합니다")

    if days <= 0 or days > 3650:
        return err_json("유효하지 않은 기간입니다 (1-3650일)")

    log.info("Trend analysis: %s, %d days, %s", ticker, days, interval)

    df = _get_ohlcv(ticker, days, interval)
    if df is None:
        return err_json("데이터를 가져올 수 없습니다")

    r = _gen_signals(df, ma_period, cmf_period).dropna()
    if r.empty:
        return err_json("지표 계산 후 데이터가 없습니다")

    name = get_name(ticker) or ticker

    data = {
        "ticker": ticker,
        "name": name,
        "interval": interval,
        "dates": r.index.strftime("%Y-%m-%d").tolist(),
        "open": r["O"].tolist(),
        "high": r["H"].tolist(),
        "low": r["L"].tolist(),
        "close": r["C"].tolist(),
        "volume": [int(v) for v in r["V"]],
        "ma": r["MA"].tolist(),
        "cmf": r["CMF"].tolist(),
        "fear_greed": r["FG"].tolist(),
        "buy_signal": r["Buy"].tolist(),
        "aux_buy_signal": r["AuxBuy"].tolist(),
        "sell_signal": r["Sell"].tolist(),
        "aux_sell_signal": r["AuxSell"].tolist()
    }

    log.info("Trend analysis complete: %s, %d records", name, len(data["dates"]))
    return to_json(data)
