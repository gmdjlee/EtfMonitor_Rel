"""
시장 과매수/과매도 지표 분석 모듈
KOSPI/KOSDAQ 구성종목 데이터를 기반으로 시장 심리 지표 계산
"""
import json
import time
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
from pykrx import stock

from logger import setup_logger
from utils import get_market_date_with_fallback

logger = setup_logger(__name__)

# 시장 지수 정의
MARKET_CONFIG = {
    "KOSPI": {
        "index_code": "1001",
        "component_code": "1028",  # KOSPI200
        "name_kr": "코스피"
    },
    "KOSDAQ": {
        "index_code": "2001",
        "component_code": "2203",  # KOSDAQ150
        "name_kr": "코스닥"
    }
}

# API 설정
API_DELAY = 0.1
BATCH_SIZE = 50  # 배치 처리 크기


class MarketOscillator:
    """시장 과매수/과매도 분석기"""

    def __init__(self, start_date: str, end_date: str):
        """
        Args:
            start_date: YYYYMMDD 형식
            end_date: YYYYMMDD 형식
        """
        self.start_date = start_date
        self.end_date = end_date
        self._validate_dates()

    def _validate_dates(self):
        """날짜 유효성 검증"""
        try:
            start = datetime.strptime(self.start_date, '%Y%m%d')
            end = datetime.strptime(self.end_date, '%Y%m%d')

            if start > end:
                raise ValueError(f"시작일({self.start_date})이 종료일({self.end_date})보다 늦습니다")

            # 최대 1년 제한
            if (end - start).days > 365:
                logger.warning("1년 이상 데이터는 성능 저하 가능")

        except ValueError as e:
            logger.error("날짜 형식 오류: %s", str(e))
            raise

    def get_index_data(self, market: str) -> Optional[pd.DataFrame]:
        """지수 데이터 조회"""
        try:
            config = MARKET_CONFIG.get(market)
            if not config:
                logger.error("잘못된 시장: %s", market)
                return None

            df = stock.get_index_ohlcv(
                self.start_date,
                self.end_date,
                config["index_code"]
            )

            if df.empty:
                logger.warning("%s 지수 데이터 없음", market)
                return None

            # 필요 컬럼만 추출
            result = pd.DataFrame({
                "날짜": df.index,
                "종가": df["종가"].values
            })

            return result

        except Exception as e:
            logger.error("%s 지수 데이터 조회 실패: %s", market, str(e))
            return None

    def get_component_data(self, market: str) -> Tuple[pd.DataFrame, pd.DataFrame]:
        """구성종목 데이터 수집 (배치 처리)"""
        try:
            config = MARKET_CONFIG.get(market)
            if not config:
                return pd.DataFrame(), pd.DataFrame()

            # 구성종목 조회
            tickers = stock.get_index_portfolio_deposit_file(
                config["component_code"]
            )

            if not tickers:
                logger.error("%s 구성종목 없음", market)
                return pd.DataFrame(), pd.DataFrame()

            logger.info("%s: %d개 종목 수집 시작", market, len(tickers))

            # 날짜 인덱스 생성
            dates = stock.get_index_ohlcv(
                self.start_date,
                self.end_date,
                config["index_code"]
            ).index

            # 초기화
            close_dict = {}
            volume_dict = {}

            # 배치 처리로 메모리 최적화
            ticker_list = list(tickers)
            for i in range(0, len(ticker_list), BATCH_SIZE):
                batch = ticker_list[i:i+BATCH_SIZE]

                for ticker in batch:
                    try:
                        df = stock.get_market_ohlcv(
                            self.start_date,
                            self.end_date,
                            ticker
                        )

                        if not df.empty:
                            # reindex로 날짜 정렬
                            df_aligned = df.reindex(dates)

                            # 종목명 조회
                            name = stock.get_market_ticker_name(ticker)
                            col_name = f"{name}({ticker})" if name else ticker

                            close_dict[col_name] = df_aligned["종가"]
                            volume_dict[col_name] = df_aligned["거래량"].fillna(0)

                        time.sleep(API_DELAY)

                    except Exception as e:
                        logger.debug("종목 %s 수집 실패: %s", ticker, str(e))
                        continue

                logger.info("  진행: %d/%d", min(i+BATCH_SIZE, len(ticker_list)), len(ticker_list))

            # DataFrame 생성
            close_df = pd.DataFrame(close_dict, index=dates)
            volume_df = pd.DataFrame(volume_dict, index=dates)

            # 날짜 컬럼 추가
            close_df.reset_index(names="날짜", inplace=True)
            volume_df.reset_index(names="날짜", inplace=True)

            logger.info("%s: %d개 종목 수집 완료", market, len(close_dict))

            return close_df, volume_df

        except Exception as e:
            logger.error("%s 구성종목 데이터 수집 실패: %s", market, str(e))
            return pd.DataFrame(), pd.DataFrame()

    def calculate_oscillator(
            self,
            close_df: pd.DataFrame,
            volume_df: pd.DataFrame
    ) -> np.ndarray:
        """과매수/과매도 지표 계산 (벡터화 연산)"""
        try:
            # 종목 컬럼만 선택
            cols = [c for c in close_df.columns if c != "날짜"]

            if not cols:
                logger.warning("계산할 종목이 없습니다")
                return np.array([])

            # 변화율 계산 (벡터화)
            change = close_df[cols].pct_change().fillna(0)

            # 상승/하락 마스크 생성
            up_mask = change > 0
            down_mask = change < 0

            # 거래량 분리 (벡터 연산)
            up_vol = volume_df[cols].where(up_mask, 0).sum(axis=1)
            down_vol = volume_df[cols].where(down_mask, 0).sum(axis=1)

            # 포인트 계산 (벡터 연산)
            gained = change.where(up_mask, 0).sum(axis=1)
            lost = change.where(down_mask, 0).sum(axis=1).abs()

            # 비율 계산
            total_vol = up_vol + down_vol
            total_pts = gained + lost

            # 0 나누기 방지
            vol_ratio = np.where(total_vol > 0, up_vol / total_vol, 0.5)
            pts_ratio = np.where(total_pts > 0, gained / total_pts, 0.5)

            # 과매수/과매도 지표 (평균)
            avg_ratio = (vol_ratio + pts_ratio) / 2

            # -1 ~ 1 범위로 변환 (0.5 기준)
            oscillator = np.where(avg_ratio > 0.5, avg_ratio, avg_ratio - 1)

            return oscillator

        except Exception as e:
            logger.error("지표 계산 오류: %s", str(e))
            return np.array([])

    def analyze_market(self, market: str) -> Optional[Dict]:
        """시장 분석 실행"""
        try:
            logger.info("%s 분석 시작", market)

            # 1. 지수 데이터
            index_df = self.get_index_data(market)
            if index_df is None or index_df.empty:
                return None

            # 2. 구성종목 데이터
            close_df, volume_df = self.get_component_data(market)
            if close_df.empty or volume_df.empty:
                return None

            # 3. 지표 계산
            oscillator = self.calculate_oscillator(close_df, volume_df)
            if len(oscillator) == 0:
                return None

            # 4. 결과 생성
            result = {
                "market": market,
                "dates": index_df["날짜"].dt.strftime("%Y-%m-%d").tolist(),
                "index": index_df["종가"].tolist(),
                "oscillator": (oscillator * 100).tolist(),  # 퍼센트로 변환
                "stats": {
                    "mean": float(np.mean(oscillator * 100)),
                    "max": float(np.max(oscillator * 100)),
                    "min": float(np.min(oscillator * 100)),
                    "latest": float(oscillator[-1] * 100) if len(oscillator) > 0 else 0
                }
            }

            logger.info("%s 분석 완료: %d일 데이터", market, len(result["dates"]))
            return result

        except Exception as e:
            logger.error("%s 분석 실패: %s", market, str(e))
            return None


def get_market_oscillator(
        market: str,
        start_date: str,
        end_date: str
) -> str:
    """
    시장 과매수/과매도 지표 조회 (Android 앱 인터페이스)

    Args:
        market: "KOSPI" 또는 "KOSDAQ"
        start_date: YYYYMMDD
        end_date: YYYYMMDD

    Returns:
        JSON 문자열: {
            "market": "KOSPI",
            "dates": ["2025-01-01", ...],
            "index": [3000.5, ...],
            "oscillator": [65.3, ...],  # 퍼센트
            "stats": {
                "mean": 50.2,
                "max": 85.1,
                "min": -72.3,
                "latest": 62.5
            }
        }
    """
    try:
        # 입력 검증
        if market not in ["KOSPI", "KOSDAQ"]:
            return json.dumps({"error": "Invalid market"}, ensure_ascii=False)

        # 분석 실행
        analyzer = MarketOscillator(start_date, end_date)
        result = analyzer.analyze_market(market)

        if result is None:
            return json.dumps({"error": "Analysis failed"}, ensure_ascii=False)

        return json.dumps(result, ensure_ascii=False)

    except Exception as e:
        logger.error("get_market_oscillator 오류: %s", str(e))
        return json.dumps({"error": str(e)}, ensure_ascii=False)


def get_realtime_oscillator(market: str = "KOSPI") -> str:
    """
    실시간 과매수/과매도 지표 (최근 30일)

    Args:
        market: "KOSPI" 또는 "KOSDAQ"

    Returns:
        JSON 문자열 (간소화된 데이터)
    """
    try:
        end_date = datetime.now().strftime("%Y%m%d")
        start_date = (datetime.now() - timedelta(days=30)).strftime("%Y%m%d")

        return get_market_oscillator(market, start_date, end_date)

    except Exception as e:
        logger.error("get_realtime_oscillator 오류: %s", str(e))
        return json.dumps({"error": str(e)}, ensure_ascii=False)