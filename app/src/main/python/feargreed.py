"""
Fear & Greed Index analysis module for KOSPI/KOSDAQ.
Uses KRX data for market sentiment indicators.
"""
from functools import reduce
from typing import Any, Dict, Optional, Tuple
import pandas as pd
import numpy as np
from sklearn.preprocessing import MinMaxScaler

from core import get_logger, HttpClient, to_iso, parse_num

log = get_logger(__name__)

KRX_URL = "https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
    "Origin": "https://data.krx.co.kr",
    "Referer": "https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201",
}

# Index configuration
INDICES = {
    "5년국채": {"type": "D", "indTpCd": "D", "idxIndCd": "896"},
    "10년국채": {"type": "D", "indTpCd": "1", "idxIndCd": "309"},
    "VKOSPI": {"type": "D", "indTpCd": "1", "idxIndCd": "300"},
    "KOSPI": {"type": "M", "indIdx": "1", "indIdx2": "001"},
    "KOSDAQ": {"type": "M", "indIdx": "2", "indIdx2": "001"},
}


class KRXFetcher:
    """KRX data fetcher."""

    def __init__(self):
        self.client = HttpClient(HEADERS)
        # Init session - retry if first attempt fails
        init_resp = self.client.get(
            "https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201"
        )
        if init_resp is None:
            log.warning("Session init failed, retrying...")
            self.client.get(
                "https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201"
            )

    def _post(self, payload: Dict) -> Optional[Dict]:
        resp = self.client.post(KRX_URL, data=payload)
        if resp is None:
            log.warning("POST request failed for bld=%s", payload.get("bld", "unknown"))
            return None
        try:
            data = resp.json()
            if not data:
                log.warning("Empty JSON response for bld=%s", payload.get("bld", "unknown"))
            return data
        except Exception as e:
            log.error("JSON parse error: %s", e)
            return None

    def get_option(self, start: str, end: str, opt_type: str) -> Optional[pd.DataFrame]:
        """Get option data (C=Call, P=Put)."""
        log.info("Fetching option data: %s ~ %s, type=%s", start, end, opt_type)
        payload = {
            "bld": "dbms/MDC/STAT/standard/MDCSTAT13102",
            "inqTpCd": "2", "prtType": "QTY", "prtCheck": "SU",
            "isuCd02": "KR___OPK2I", "isuCd": "KR___OPK2I",
            "prodId": "KR___OPK2I", "aggBasTpCd": "",
            "strtDd": start, "endDd": end, "isuOpt": opt_type
        }
        data = self._post(payload)
        if not data:
            log.warning("No data returned for option type %s", opt_type)
            return None

        rows = data.get("block1") or data.get("output", [])
        if not rows:
            log.warning("Empty rows for option type %s (keys: %s)", opt_type, list(data.keys()))
            return None

        df = pd.DataFrame(rows)
        df = df.rename(columns={"TRD_DD": "거래일", "AMT_OR_QTY": "전체"})
        df["거래일"] = df["거래일"].apply(to_iso)
        df["전체"] = df["전체"].apply(parse_num).astype(int)
        return df

    def get_index(self, start: str, end: str, key: str) -> Optional[pd.DataFrame]:
        """Get index data."""
        cfg = INDICES.get(key)
        if not cfg:
            return None

        if cfg["type"] == "M":  # Market index
            payload = {
                "bld": "dbms/MDC/STAT/standard/MDCSTAT00301",
                "locale": "ko_KR",
                "indIdx": cfg["indIdx"], "indIdx2": cfg["indIdx2"],
                "strtDd": start, "endDd": end,
                "share": "2", "money": "3", "csvxls_isNo": "false"
            }
        else:  # Derivative index
            payload = {
                "bld": "dbms/MDC/STAT/standard/MDCSTAT01201",
                "locale": "ko_KR",
                "indTpCd": cfg["indTpCd"], "idxIndCd": cfg["idxIndCd"],
                "idxCd": cfg["indTpCd"], "idxCd2": cfg["idxIndCd"],
                "strtDd": start, "endDd": end, "csvxls_isNo": "false"
            }

        data = self._post(payload)
        if not data:
            return None

        rows = data.get("block1") or data.get("output", [])
        if not rows:
            return None

        df = pd.DataFrame(rows)
        df = df.rename(columns={"TRD_DD": "거래일", "CLSPRC_IDX": "종가"})
        df["거래일"] = df["거래일"].apply(to_iso)
        df["종가"] = df["종가"].apply(parse_num)
        return df[["거래일", "종가"]]


def _calc_rsi(series: pd.Series, window: int = 10) -> pd.Series:
    """Calculate RSI."""
    delta = series.diff()
    gain = delta.mask(delta < 0, 0).rolling(window).mean()
    loss = delta.mask(delta > 0, 0).abs().rolling(window).mean()
    rs = gain / loss.replace(0, np.nan)
    return 100 - (100 / (1 + rs))


def _calc_macd(series: pd.Series, short: int = 12, long: int = 26, sig: int = 9) -> pd.Series:
    """Calculate MACD oscillator."""
    ema_s = series.ewm(span=short, adjust=False).mean()
    ema_l = series.ewm(span=long, adjust=False).mean()
    macd = ema_s - ema_l
    signal = macd.ewm(span=sig, adjust=False).mean()
    return macd - signal


def _calc_fg(df: pd.DataFrame, idx_col: str) -> pd.DataFrame:
    """Calculate Fear & Greed index."""
    n = len(df)
    ma_period = min(125, max(10, int(n * 0.9)))

    df = df.copy()
    df["MA"] = df[idx_col].rolling(ma_period).mean()
    df["Mom"] = (df[idx_col] - df["MA"]) / df["MA"].replace(0, np.nan) * 100
    df["PCR"] = df["Put"] / df["Call"].replace(0, np.nan)
    df["Vol"] = df["VIX"]
    df["Spread"] = df["10년국채"] - df["5년국채"]
    df["RSI"] = _calc_rsi(df[idx_col])

    feats = ["Mom", "PCR", "Vol", "Spread", "RSI"]
    valid = df[feats].notna().all(axis=1)

    if not valid.any():
        df["FG"] = np.nan
        return df

    scaler = MinMaxScaler()
    df.loc[valid, feats] = scaler.fit_transform(df.loc[valid, feats])

    df["FG"] = (df["Mom"] * 0.2 + (1 - df["PCR"]) * 0.2 +
                (1 - df["Vol"]) * 0.2 + df["Spread"] * 0.2 + df["RSI"] * 0.2)
    df["Osc"] = _calc_macd(df["FG"])

    return df


def run_analysis(start: str, end: str) -> Tuple[Optional[pd.DataFrame], Optional[pd.DataFrame]]:
    """
    Run Fear & Greed Index analysis.

    Args:
        start: Start date (YYYYMMDD)
        end: End date (YYYYMMDD)

    Returns:
        Tuple of (KOSPI result, KOSDAQ result) DataFrames
    """
    log.info("Fear & Greed analysis: %s ~ %s", start, end)

    try:
        fetcher = KRXFetcher()

        # Fetch option data
        call = fetcher.get_option(start, end, "C")
        put = fetcher.get_option(start, end, "P")
        if call is None or put is None:
            log.error("Failed to fetch option data")
            return None, None

        # Calculate 5-day MA for options
        call = call.sort_values("거래일").reset_index(drop=True)
        put = put.sort_values("거래일").reset_index(drop=True)
        call["Call"] = call["전체"].rolling(5).mean()
        put["Put"] = put["전체"].rolling(5).mean()

        # Fetch index data
        b5y = fetcher.get_index(start, end, "5년국채")
        b10y = fetcher.get_index(start, end, "10년국채")
        vix = fetcher.get_index(start, end, "VKOSPI")
        kospi = fetcher.get_index(start, end, "KOSPI")
        kosdaq = fetcher.get_index(start, end, "KOSDAQ")

        if any(d is None for d in [b5y, b10y, vix]):
            log.error("Failed to fetch required index data")
            return None, None

        # Merge data
        dfs = [
            b5y.rename(columns={"종가": "5년국채"}),
            b10y.rename(columns={"종가": "10년국채"}),
            vix.rename(columns={"종가": "VIX"}),
            call[["거래일", "Call"]],
            put[["거래일", "Put"]],
        ]
        if kospi is not None:
            dfs.append(kospi.rename(columns={"종가": "KOSPI"}))
        if kosdaq is not None:
            dfs.append(kosdaq.rename(columns={"종가": "KOSDAQ"}))

        df = reduce(lambda l, r: l.merge(r, on="거래일", how="outer"), dfs)
        df = df.sort_values("거래일").reset_index(drop=True)
        df["거래일"] = pd.to_datetime(df["거래일"])

        # Drop rows with missing required data
        req = ["5년국채", "10년국채", "VIX", "Call", "Put"]
        df = df.dropna(subset=req)

        if len(df) < 15:
            log.error("Insufficient data: %d rows (min 15 required)", len(df))
            return None, None

        log.info("Combined data: %d rows", len(df))

        # Analyze KOSPI
        kp_df = None
        if "KOSPI" in df.columns and df["KOSPI"].notna().any():
            kp_df = _calc_fg(df, "KOSPI").dropna()
            if len(kp_df) > 0:
                log.info("KOSPI FG: %d rows", len(kp_df))
            else:
                kp_df = None

        # Analyze KOSDAQ
        kq_df = None
        if "KOSDAQ" in df.columns and df["KOSDAQ"].notna().any():
            kq_df = _calc_fg(df, "KOSDAQ").dropna()
            if len(kq_df) > 0:
                log.info("KOSDAQ FG: %d rows", len(kq_df))
            else:
                kq_df = None

        return kp_df, kq_df

    except Exception as e:
        log.error("Analysis error: %s", e)
        return None, None


# Backward compatibility functions for Kotlin FearGreedRepository
def combine(start: str, end: str) -> Optional[pd.DataFrame]:
    """
    Fetch and combine raw data (backward compatibility).

    Args:
        start: Start date (YYYYMMDD)
        end: End date (YYYYMMDD)

    Returns:
        Combined DataFrame with all raw data
    """
    log.info("combine: %s ~ %s", start, end)

    try:
        fetcher = KRXFetcher()

        # Fetch option data
        call = fetcher.get_option(start, end, "C")
        put = fetcher.get_option(start, end, "P")
        if call is None or put is None:
            log.error("Failed to fetch option data")
            return None

        # Calculate 5-day MA for options
        call = call.sort_values("거래일").reset_index(drop=True)
        put = put.sort_values("거래일").reset_index(drop=True)
        call["Call"] = call["전체"].rolling(5).mean()
        put["Put"] = put["전체"].rolling(5).mean()

        # Fetch index data
        b5y = fetcher.get_index(start, end, "5년국채")
        b10y = fetcher.get_index(start, end, "10년국채")
        vix = fetcher.get_index(start, end, "VKOSPI")
        kospi = fetcher.get_index(start, end, "KOSPI")
        kosdaq = fetcher.get_index(start, end, "KOSDAQ")

        if any(d is None for d in [b5y, b10y, vix]):
            log.error("Failed to fetch required index data")
            return None

        # Merge data
        dfs = [
            b5y.rename(columns={"종가": "5년국채"}),
            b10y.rename(columns={"종가": "10년국채"}),
            vix.rename(columns={"종가": "VIX"}),
            call[["거래일", "Call"]],
            put[["거래일", "Put"]],
        ]
        if kospi is not None:
            dfs.append(kospi.rename(columns={"종가": "KOSPI"}))
        if kosdaq is not None:
            dfs.append(kosdaq.rename(columns={"종가": "KOSDAQ"}))

        df = reduce(lambda l, r: l.merge(r, on="거래일", how="outer"), dfs)
        df = df.sort_values("거래일").reset_index(drop=True)
        df["거래일"] = pd.to_datetime(df["거래일"])

        # Drop rows with missing required data
        req = ["5년국채", "10년국채", "VIX", "Call", "Put"]
        df = df.dropna(subset=req)

        if len(df) < 15:
            log.error("Insufficient data: %d rows", len(df))
            return None

        log.info("Combined data: %d rows", len(df))
        return df

    except Exception as e:
        log.error("combine error: %s", e)
        return None


def analyze(df: pd.DataFrame) -> Tuple[Optional[pd.DataFrame], Optional[pd.DataFrame]]:
    """
    Analyze combined data and calculate Fear & Greed index (backward compatibility).

    Args:
        df: Combined DataFrame from combine()

    Returns:
        Tuple of (KOSPI result, KOSDAQ result) DataFrames
    """
    if df is None or df.empty:
        return None, None

    try:
        # Analyze KOSPI
        kp_df = None
        if "KOSPI" in df.columns and df["KOSPI"].notna().any():
            kp_df = _calc_fg(df, "KOSPI").dropna()
            if len(kp_df) > 0:
                log.info("KOSPI FG: %d rows", len(kp_df))
            else:
                kp_df = None

        # Analyze KOSDAQ
        kq_df = None
        if "KOSDAQ" in df.columns and df["KOSDAQ"].notna().any():
            kq_df = _calc_fg(df, "KOSDAQ").dropna()
            if len(kq_df) > 0:
                log.info("KOSDAQ FG: %d rows", len(kq_df))
            else:
                kq_df = None

        return kp_df, kq_df

    except Exception as e:
        log.error("analyze error: %s", e)
        return None, None
