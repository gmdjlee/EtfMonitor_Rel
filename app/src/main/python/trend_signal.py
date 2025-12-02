"""
Trend signal analysis module.
Technical indicators: MA, CMF, Fear & Greed Index, DeMark TD Setup, Elder Impulse System.
"""
import json
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional
import numpy as np
import pandas as pd
from pykrx import stock

from core import get_logger, get_name, to_json, err_json

log = get_logger(__name__)


# ============================================================
# OHLCV Data Fetching with Monthly Resampling
# ============================================================

def _resample_monthly(df: pd.DataFrame) -> pd.DataFrame:
    """월봉 리샘플링."""
    if df.empty:
        return df
    return df.resample("ME").agg({
        "O": "first", "H": "max", "L": "min", "C": "last", "V": "sum"
    }).dropna()


# ============================================================
# DeMark TD Setup
# ============================================================

def _calc_td_setup(df: pd.DataFrame, col: str = "C") -> pd.DataFrame:
    """DeMark TD Setup 카운트 계산.

    - Sell Setup: Close(t) > Close(t-4) 연속 시 +1
    - Buy Setup: Close(t) < Close(t-4) 연속 시 +1
    """
    df = df.copy()
    n = len(df)
    sell = np.zeros(n)
    buy = np.zeros(n)
    prices = df[col].values

    for i in range(4, n):
        if prices[i] > prices[i - 4]:
            sell[i] = sell[i - 1] + 1
        else:
            sell[i] = 0

        if prices[i] < prices[i - 4]:
            buy[i] = buy[i - 1] + 1
        else:
            buy[i] = 0

    df["TD_Sell"] = sell.astype(int)
    df["TD_Buy"] = buy.astype(int)
    return df


# ============================================================
# Elder Impulse System
# ============================================================

def _calc_ema(s: pd.Series, n: int) -> pd.Series:
    """지수이동평균."""
    return s.ewm(span=n, adjust=False).mean()


def _calc_elder_impulse(df: pd.DataFrame, ema_period: int = 13) -> pd.DataFrame:
    """Elder Impulse System 계산.

    - EMA 기울기와 MACD 히스토그램 기울기로 추세 판별
    - bull: 둘 다 상승, bear: 둘 다 하락, neutral: 혼조
    """
    df = df.copy()
    close = df["C"]

    df["EMA"] = _calc_ema(close, ema_period)

    ema12 = _calc_ema(close, 12)
    ema26 = _calc_ema(close, 26)
    df["MACD"] = ema12 - ema26
    df["MACD_Signal"] = _calc_ema(df["MACD"], 9)
    df["MACD_Hist"] = df["MACD"] - df["MACD_Signal"]

    ema_slope = df["EMA"].diff()
    hist_slope = df["MACD_Hist"].diff()

    impulse = pd.Series(0, index=df.index)  # 0=neutral
    impulse[(ema_slope > 0) & (hist_slope > 0)] = 1   # bull
    impulse[(ema_slope < 0) & (hist_slope < 0)] = -1  # bear
    df["Impulse"] = impulse

    return df


def _get_ohlcv(ticker: str, days: int, interval: str = "d") -> Optional[pd.DataFrame]:
    """Get OHLCV data.

    Args:
        ticker: Stock code
        days: Analysis period
        interval: "d" (daily), "w" (weekly), "m" (monthly)
    """
    extra = days * 3 if interval == "m" else (days * 2 if interval == "w" else days)
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
        elif interval == "m":
            df = _resample_monthly(df)

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


# ============================================================
# Elder Impulse API
# ============================================================

def get_elder_impulse_analysis(ticker: str, days: int = 365) -> str:
    """
    Elder Impulse System 분석 (주봉 기준).

    Args:
        ticker: Stock code
        days: Analysis period (default 1 year)

    Returns: JSON with market cap, EMA13, MACD, impulse signals
    """
    if not ticker or not ticker.strip():
        return err_json("종목 코드가 필요합니다")

    log.info("Elder Impulse analysis: %s, %d days", ticker, days)

    # 주봉 데이터 가져오기
    df = _get_ohlcv(ticker, days, "w")
    if df is None:
        return err_json("데이터를 가져올 수 없습니다")

    # 시가총액 데이터 가져오기
    end = datetime.now()
    start = end - timedelta(days=days * 2)
    try:
        cap_df = stock.get_market_cap(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"), ticker)
        if not cap_df.empty:
            cap_df = cap_df.resample("W").last().dropna()
            df = df.join(cap_df[["시가총액"]], how="left")
            df["MarketCap"] = df["시가총액"].fillna(method="ffill")
        else:
            df["MarketCap"] = 0
    except Exception as e:
        log.warning("Market cap error: %s", e)
        df["MarketCap"] = 0

    # Elder Impulse 계산
    df = _calc_elder_impulse(df)
    r = df.dropna(subset=["EMA", "MACD", "Impulse"])

    if r.empty:
        return err_json("지표 계산 후 데이터가 없습니다")

    name = get_name(ticker) or ticker

    data = {
        "ticker": ticker,
        "name": name,
        "interval": "w",
        "dates": r.index.strftime("%Y-%m-%d").tolist(),
        "close": r["C"].tolist(),
        "market_cap": [int(v) for v in r["MarketCap"]],
        "ema": r["EMA"].tolist(),
        "macd": r["MACD"].tolist(),
        "macd_signal": r["MACD_Signal"].tolist(),
        "macd_hist": r["MACD_Hist"].tolist(),
        "impulse": r["Impulse"].tolist()  # 1=bull, 0=neutral, -1=bear
    }

    log.info("Elder Impulse complete: %s, %d records", name, len(data["dates"]))
    return to_json(data)


# ============================================================
# DeMark TD Setup API
# ============================================================

def get_demark_td_analysis(ticker: str, days: int = 365, interval: str = "w") -> str:
    """
    DeMark TD Setup 분석.

    Args:
        ticker: Stock code
        days: Analysis period
        interval: "d" (daily), "w" (weekly), "m" (monthly)

    Returns: JSON with market cap, close prices, TD_Buy, TD_Sell counts
    """
    if not ticker or not ticker.strip():
        return err_json("종목 코드가 필요합니다")

    if interval not in ("d", "w", "m"):
        return err_json("interval은 'd', 'w', 'm' 중 하나여야 합니다")

    log.info("DeMark TD analysis: %s, %d days, %s", ticker, days, interval)

    df = _get_ohlcv(ticker, days, interval)
    if df is None:
        return err_json("데이터를 가져올 수 없습니다")

    # 시가총액 데이터 가져오기
    end = datetime.now()
    extra = days * 3 if interval == "m" else (days * 2 if interval == "w" else days)
    start = end - timedelta(days=extra)

    try:
        cap_df = stock.get_market_cap(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"), ticker)
        if not cap_df.empty:
            if interval == "w":
                cap_df = cap_df.resample("W").last().dropna()
            elif interval == "m":
                cap_df = cap_df.resample("ME").last().dropna()
            df = df.join(cap_df[["시가총액"]], how="left")
            df["MarketCap"] = df["시가총액"].fillna(method="ffill")
        else:
            df["MarketCap"] = 0
    except Exception as e:
        log.warning("Market cap error: %s", e)
        df["MarketCap"] = 0

    # DeMark TD Setup 계산
    df = _calc_td_setup(df)

    if len(df) < 5:
        return err_json("데이터가 부족합니다 (최소 5개 필요)")

    name = get_name(ticker) or ticker

    interval_name = {"d": "일봉", "w": "주봉", "m": "월봉"}.get(interval, interval)

    data = {
        "ticker": ticker,
        "name": name,
        "interval": interval,
        "interval_name": interval_name,
        "dates": df.index.strftime("%Y-%m-%d").tolist(),
        "close": df["C"].tolist(),
        "market_cap": [int(v) for v in df["MarketCap"]],
        "td_sell": df["TD_Sell"].tolist(),  # 매도 피로 카운트
        "td_buy": df["TD_Buy"].tolist()     # 매수 피로 카운트
    }

    log.info("DeMark TD complete: %s, %d records, %s", name, len(data["dates"]), interval_name)
    return to_json(data)
