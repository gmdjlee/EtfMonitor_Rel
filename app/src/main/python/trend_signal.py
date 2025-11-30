"""
주간 추세 전략 분석 모듈 (trend_signal)

pykrx를 통해 OHLCV 데이터를 수집하고 기술적 지표 계산:
- MA (Moving Average): 추세 방향
- CMF (Chaikin Money Flow): 자금 유입/유출
- Fear & Greed Index: 시장 심리 지표
- 매수/매도 시그널 생성
"""

import json
import traceback
from datetime import datetime, timedelta
from typing import Optional, Dict, List, Any
import numpy as np
import pandas as pd
from pykrx import stock

from logger import setup_logger

logger = setup_logger(__name__)


def get_ohlcv_data(ticker: str, days: int = 180, interval: str = "d") -> Optional[Dict[str, List[Any]]]:
    """
    OHLCV 데이터 수집

    Args:
        ticker: 종목 코드
        days: 분석 기간 (일)
        interval: 주기 ("d"=일별, "w"=주별)

    Returns:
        dict: OHLCV 데이터 + 기술 지표
    """
    if not ticker or not isinstance(ticker, str):
        logger.warning("잘못된 티커: %s", ticker)
        return None

    if days <= 0:
        logger.warning("잘못된 기간: %d일", days)
        return None

    # 주간 데이터를 위해 더 많은 일 데이터 수집
    extra_days = days * 2 if interval == "w" else days
    end = datetime.now()
    start = end - timedelta(days=extra_days)

    start_str = start.strftime("%Y%m%d")
    end_str = end.strftime("%Y%m%d")

    try:
        logger.info("OHLCV 데이터 수집: %s (%s ~ %s, 주기: %s)", ticker, start_str, end_str, interval)

        # pykrx에서 OHLCV 데이터 가져오기
        df = stock.get_market_ohlcv(start_str, end_str, ticker)

        if df.empty:
            logger.warning("데이터가 없습니다 (티커: %s)", ticker)
            return None

        # 컬럼명 정리
        df = df.rename(columns={
            "시가": "Open",
            "고가": "High",
            "저가": "Low",
            "종가": "Close",
            "거래량": "Volume"
        })

        # 필요한 컬럼만 선택
        df = df[["Open", "High", "Low", "Close", "Volume"]]

        # 주간 데이터로 리샘플링
        if interval == "w":
            df = df.resample("W").agg({
                "Open": "first",
                "High": "max",
                "Low": "min",
                "Close": "last",
                "Volume": "sum"
            }).dropna()

        if df.empty:
            logger.warning("리샘플링 후 데이터가 비어있습니다 (티커: %s)", ticker)
            return None

        # 결과 반환
        result = {
            "dates": df.index.strftime("%Y-%m-%d").tolist(),
            "open": df["Open"].tolist(),
            "high": df["High"].tolist(),
            "low": df["Low"].tolist(),
            "close": df["Close"].tolist(),
            "volume": [int(v) for v in df["Volume"].tolist()]
        }

        logger.info("OHLCV 수집 완료 (티커: %s): %d개 데이터", ticker, len(result["dates"]))
        return result

    except Exception as e:
        logger.error("OHLCV 수집 오류 (티커: %s): %s", ticker, str(e))
        logger.debug(traceback.format_exc())
        return None


def calc_ma(series: pd.Series, period: int) -> pd.Series:
    """이동평균(MA) 계산"""
    return series.rolling(period).mean()


def calc_cmf(df: pd.DataFrame, period: int = 4) -> pd.Series:
    """
    Chaikin Money Flow (CMF) 계산

    공식:
    1. Money Flow Multiplier = ((Close - Low) - (High - Close)) / (High - Low)
    2. Money Flow Volume = MFM * Volume
    3. CMF = Sum(MFV, n) / Sum(Volume, n)
    """
    high_low = df["High"] - df["Low"]
    # 0 나눗셈 방지
    high_low = high_low.replace(0, np.nan)

    mf_mult = ((df["Close"] - df["Low"]) - (df["High"] - df["Close"])) / high_low
    mf_vol = mf_mult * df["Volume"]

    cmf = mf_vol.rolling(period).sum() / df["Volume"].rolling(period).sum()
    return cmf


def calc_fear_greed(df: pd.DataFrame, momentum_period: int = 5, position_period: int = 52) -> pd.Series:
    """
    Fear & Greed Index 계산

    구성 요소:
    1. Momentum (45%): 로그 수익률 기반 모멘텀
    2. 52주 포지션 (45%): 가격이 52주 범위 내 어디에 있는지
    3. 거래량 급증 (5%): 최근 거래량 대비 과거 평균
    4. 변동성 (5%): 최근 변동성 대비 과거 평균 (반전)

    결과: -1 (극도의 공포) ~ +1 (극도의 탐욕)
    """
    # 1. 모멘텀 (로그 수익률 기반)
    log_returns = np.log(df["Close"] / df["Close"].shift(momentum_period))
    momentum = log_returns / 0.1  # 스케일링 (10% 수익률 = 1.0)
    momentum = momentum.clip(-1, 1)  # -1 ~ 1로 제한

    # 2. 52주 포지션 (0=최저, 1=최고 -> -1 ~ +1로 변환)
    rolling_high = df["Close"].rolling(position_period, min_periods=10).max()
    rolling_low = df["Close"].rolling(position_period, min_periods=10).min()
    high_low_range = rolling_high - rolling_low
    high_low_range = high_low_range.replace(0, np.nan)
    position = (df["Close"] - rolling_low) / high_low_range
    position = (position * 2) - 1  # 0~1 -> -1~+1

    # 3. 거래량 급증 (최근 거래량 / 과거 평균)
    vol_ma = df["Volume"].rolling(20, min_periods=5).mean()
    vol_ratio = df["Volume"] / vol_ma
    vol_score = (vol_ratio - 1).clip(-1, 1)

    # 4. 변동성 (최근 변동성 / 과거 평균) - 반전
    returns = df["Close"].pct_change()
    vol_recent = returns.rolling(5, min_periods=3).std()
    vol_avg = returns.rolling(20, min_periods=10).std()
    vol_spike = (vol_recent / vol_avg.replace(0, np.nan) - 1).clip(-1, 1) * -1  # 변동성 증가 = 공포

    # 가중 평균
    fear_greed = (
        momentum * 0.45 +
        position * 0.45 +
        vol_score * 0.05 +
        vol_spike * 0.05
    )

    return fear_greed


def generate_signals(df: pd.DataFrame, ma_period: int = 20, cmf_period: int = 4) -> pd.DataFrame:
    """
    매수/매도 시그널 생성

    매수 조건:
    1. 고가 > 전일 고가 (돌파)
    2. 종가 > MA (상승 추세)
    3. CMF > 0 (자금 유입)

    매도 조건:
    1. 저가 < 전일 저가 (이탈)
    2. 종가 < MA (하락 추세)
    3. CMF < 0 (자금 유출)
    """
    result = df.copy()

    # 지표 계산
    result["MA"] = calc_ma(result["Close"], ma_period)
    result["CMF"] = calc_cmf(result, cmf_period)
    result["FearGreed"] = calc_fear_greed(result)

    # 전일 고가/저가
    result["PrevHigh"] = result["High"].shift(1)
    result["PrevLow"] = result["Low"].shift(1)

    # 매수 조건
    buy_cond = (
        (result["High"] > result["PrevHigh"]) &
        (result["Close"] > result["MA"]) &
        (result["CMF"] > 0)
    )

    # 매도 조건
    sell_cond = (
        (result["Low"] < result["PrevLow"]) &
        (result["Close"] < result["MA"]) &
        (result["CMF"] < 0)
    )

    # 시그널 설정
    result["BuySignal"] = buy_cond.astype(int)
    result["SellSignal"] = sell_cond.astype(int)

    return result


def get_trend_signal_analysis(ticker: str, days: int = 180, interval: str = "w",
                               ma_period: int = 20, cmf_period: int = 4) -> str:
    """
    종목의 추세 시그널 분석

    Args:
        ticker: 종목 코드
        days: 분석 기간 (일)
        interval: 주기 ("d"=일별, "w"=주별)
        ma_period: 이동평균 기간
        cmf_period: CMF 기간

    Returns:
        JSON 문자열
    """
    try:
        # 입력 검증
        if not ticker or not ticker.strip():
            return json.dumps({"error": "종목 코드가 필요합니다"}, ensure_ascii=False)

        if days <= 0 or days > 3650:
            return json.dumps({"error": "유효하지 않은 기간입니다 (1-3650일)"}, ensure_ascii=False)

        logger.info("추세 시그널 분석 시작: %s, %d일, 주기: %s", ticker, days, interval)

        # OHLCV 데이터 수집
        ohlcv = get_ohlcv_data(ticker, days, interval)

        if ohlcv is None:
            return json.dumps({"error": "데이터를 가져올 수 없습니다"}, ensure_ascii=False)

        # DataFrame 생성
        df = pd.DataFrame({
            "Open": ohlcv["open"],
            "High": ohlcv["high"],
            "Low": ohlcv["low"],
            "Close": ohlcv["close"],
            "Volume": ohlcv["volume"]
        }, index=pd.to_datetime(ohlcv["dates"]))

        # 시그널 생성
        result_df = generate_signals(df, ma_period, cmf_period)

        # NaN 제거
        result_df = result_df.dropna()

        if result_df.empty:
            return json.dumps({"error": "지표 계산 후 데이터가 없습니다"}, ensure_ascii=False)

        # 종목명 조회
        try:
            from stock_data_fetcher import get_stock_name
            name = get_stock_name(ticker) or ticker
        except Exception:
            name = ticker

        # JSON 반환
        data = {
            "ticker": ticker,
            "name": name,
            "interval": interval,
            "dates": result_df.index.strftime("%Y-%m-%d").tolist(),
            "open": result_df["Open"].tolist(),
            "high": result_df["High"].tolist(),
            "low": result_df["Low"].tolist(),
            "close": result_df["Close"].tolist(),
            "volume": [int(v) for v in result_df["Volume"].tolist()],
            "ma": result_df["MA"].tolist(),
            "cmf": result_df["CMF"].tolist(),
            "fear_greed": result_df["FearGreed"].tolist(),
            "buy_signal": result_df["BuySignal"].tolist(),
            "sell_signal": result_df["SellSignal"].tolist()
        }

        logger.info("추세 시그널 분석 완료: %s, %d개 데이터", name, len(data["dates"]))
        return json.dumps(data, ensure_ascii=False)

    except Exception as e:
        logger.error("추세 시그널 분석 오류 (티커: %s): %s", ticker, str(e))
        logger.debug(traceback.format_exc())
        return json.dumps({"error": f"분석 중 오류 발생: {str(e)}"}, ensure_ascii=False)


# API 테스트용
if __name__ == "__main__":
    # 삼성전자 주간 추세 분석
    result = get_trend_signal_analysis("005930", days=365, interval="w")
    print(result)
