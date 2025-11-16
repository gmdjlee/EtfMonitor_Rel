"""
KOSPI/KOSDAQ Fear & Greed Index 분석 모듈
KRX 데이터를 활용한 투자심리 지표 계산
"""

import json
import sys
from datetime import datetime
from functools import reduce
from typing import Optional, Dict, Any, Tuple, Union
import time

import pandas as pd
import requests
from sklearn.preprocessing import MinMaxScaler

from logger import setup_logger

logger = setup_logger(__name__)

# Constants
REQUEST_TIMEOUT = 10
MAX_RETRIES = 3
RETRY_DELAY = 2

# 한글 출력 설정
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

# 헤더
OPTION_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept": "*/*",
    "Accept-Language": "ko-KR,ko;q=0.9",
    "Content-Type": "application/x-www-form-urlencoded",
    "Origin": "https://data.krx.co.kr",
    "Referer": "https://data.krx.co.kr/contents/MMC/ISIF/isif/MMCISIF013.cmd",
}

INDEX_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "Accept-Language": "ko-KR,ko;q=0.9",
    "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
    "Origin": "https://data.krx.co.kr",
    "Referer": "https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201010301",
}

# 페이로드
OPTION_PAYLOAD = {
    "inqTpCd": "2",
    "prtType": "QTY",
    "prtCheck": "SU",
    "isuCd02": "KR___OPK2I",
    "isuCd": "KR___OPK2I",
    "aggBasTpCd": "",
    "prodId": "KR___OPK2I",
    "bld": "dbms/MDC/STAT/standard/MDCSTAT13102",
}

INDEX_PAYLOAD = {
    "bld": "dbms/MDC/STAT/standard/MDCSTAT01201",
    "locale": "ko_KR",
    "param1idxCd_finder_drvetcidx0_1": "",
    "csvxls_isNo": "false",
}

# 지수 매핑
INDEX_MAP = {
    "5년국채": {"type": "derivative", "indTpCd": "D", "idxIndCd": "896", "idxCd": "D", "idxCd2": "896"},
    "10년국채": {"type": "derivative", "indTpCd": "1", "idxIndCd": "309", "idxCd": "1", "idxCd2": "309"},
    "VKOSPI": {"type": "derivative", "indTpCd": "1", "idxIndCd": "300", "idxCd": "1", "idxCd2": "300"},
    "KOSPI": {"type": "market", "indIdx": "1", "indIdx2": "001"},
    "KOSDAQ": {"type": "market", "indIdx": "2", "indIdx2": "001"},
}

INDEX_NAMES = {
    "5년국채": "5년 국채선물 추종 지수",
    "10년국채": "10년국채선물지수",
    "VKOSPI": "코스피 200 변동성지수",
    "KOSPI": "코스피",
    "KOSDAQ": "코스닥",
}


def to_date(val: Union[str, datetime]) -> str:
    """날짜를 YYYY-MM-DD 형식으로 변환"""
    if isinstance(val, str):
        if "/" in val:
            return val.replace("/", "-")
        try:
            return datetime.strptime(val, "%Y%m%d").strftime("%Y-%m-%d")
        except ValueError:
            return val
    return val.strftime("%Y-%m-%d") if hasattr(val, "strftime") else val


def fetch(
    session: requests.Session,
    url: str,
    headers: Dict[str, str],
    payload: Dict[str, str]
) -> Optional[Dict[str, Any]]:
    """데이터 조회 (재시도 로직 포함)"""
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            res = session.post(url, headers=headers, data=payload, timeout=REQUEST_TIMEOUT)
            res.raise_for_status()
            return res.json() if res.text else None

        except requests.exceptions.Timeout:
            logger.warning(
                "타임아웃 (시도 %d/%d): 서버 응답 시간 초과",
                attempt, MAX_RETRIES
            )
            if attempt < MAX_RETRIES:
                time.sleep(RETRY_DELAY * attempt)
                continue
            return None

        except requests.exceptions.ConnectionError:
            logger.warning(
                "연결 오류 (시도 %d/%d): 네트워크 연결 확인 필요",
                attempt, MAX_RETRIES
            )
            if attempt < MAX_RETRIES:
                time.sleep(RETRY_DELAY * attempt)
                continue
            return None

        except requests.exceptions.HTTPError as e:
            logger.error("HTTP 오류: %d", e.response.status_code)
            return None

        except json.JSONDecodeError:
            logger.error("JSON 파싱 오류: 잘못된 응답 형식")
            return None

        except Exception as e:
            logger.error("예상치 못한 오류: %s", str(e))
            return None

    return None


def to_num(x: Any) -> float:
    """문자열을 숫자로 변환 (쉼표 제거)"""
    if isinstance(x, str) and x:
        return float(x.replace(",", ""))
    return x


class BaseFetcher:
    """기본 데이터 수집 클래스"""

    def __init__(self, init_url: str, headers: Dict[str, str]):
        self.url = "https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd"
        self.session = requests.Session()
        self.headers = headers
        try:
            self.session.get(init_url, headers=headers, timeout=REQUEST_TIMEOUT)
        except Exception as e:
            logger.debug("초기화 URL 요청 실패: %s", str(e))


class OptionData(BaseFetcher):
    """옵션 데이터 수집"""

    def __init__(self):
        super().__init__(
            "https://data.krx.co.kr/contents/MMC/ISIF/isif/MMCISIF013.cmd",
            OPTION_HEADERS
        )

    def get(self, start: str, end: str, opt_type: str = "C") -> Optional[Dict[str, Any]]:
        if opt_type not in ["C", "P"]:
            logger.error("잘못된 옵션 타입: %s", opt_type)
            raise ValueError(f"Invalid opt_type: {opt_type}")

        payload = OPTION_PAYLOAD.copy()
        payload.update({"strtDd": start, "endDd": end, "isuOpt": opt_type})
        return fetch(self.session, self.url, self.headers, payload)

    def parse(self, data: Optional[Dict[str, Any]]) -> Optional[pd.DataFrame]:
        try:
            if not data:
                logger.warning("옵션 데이터가 비어있습니다")
                return None

            df = pd.DataFrame(data.get("block1") or data.get("output", []))
            if df.empty:
                logger.warning("옵션 DataFrame이 비어있습니다")
                return None

            df.rename(columns={
                "TRD_DD": "거래일",
                "A07": "기관",
                "A08": "법인",
                "A09": "개인",
                "A12": "외국인",
                "AMT_OR_QTY": "전체",
            }, inplace=True)

            df["거래일"] = df["거래일"].apply(to_date)
            for col in ["기관", "법인", "개인", "외국인", "전체"]:
                if col in df.columns:
                    df[col] = df[col].apply(to_num).astype(int)

            logger.debug("옵션 데이터 파싱 완료: %d행", len(df))
            return df

        except KeyError as e:
            logger.error("옵션 데이터 파싱 오류: 필수 컬럼 누락 %s", str(e))
            return None
        except ValueError as e:
            logger.error("옵션 데이터 변환 오류: %s", str(e))
            return None
        except Exception as e:
            logger.error("옵션 데이터 처리 오류: %s", str(e))
            return None


class IndexData(BaseFetcher):
    """지수 데이터 수집"""

    def __init__(self):
        super().__init__(
            "https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201010301",
            INDEX_HEADERS,
        )

    def get(self, start: str, end: str, key: str) -> Optional[Dict[str, Any]]:
        if key not in INDEX_MAP:
            logger.error("잘못된 지수 키: %s", key)
            raise ValueError(f"Invalid key: {key}")

        info = INDEX_MAP[key]
        name = INDEX_NAMES[key]

        if info["type"] == "market":
            payload = {
                "bld": "dbms/MDC/STAT/standard/MDCSTAT00301",
                "locale": "ko_KR",
                "tboxindIdx_finder_equidx0_4": name,
                "indIdx": info["indIdx"],
                "indIdx2": info["indIdx2"],
                "codeNmindIdx_finder_equidx0_4": name,
                "param1indIdx_finder_equidx0_4": "",
                "strtDd": start,
                "endDd": end,
                "share": "2",
                "money": "3",
                "csvxls_isNo": "false",
            }
        else:
            payload = INDEX_PAYLOAD.copy()
            payload.update({
                "strtDd": start,
                "endDd": end,
                "indTpCd": info["indTpCd"],
                "idxIndCd": info["idxIndCd"],
                "idxCd": info["idxCd"],
                "idxCd2": info["idxCd2"],
                "tboxidxCd_finder_drvetcidx0_1": name,
                "codeNmidxCd_finder_drvetcidx0_1": name,
            })

        return fetch(self.session, self.url, self.headers, payload)

    def parse(self, data: Optional[Dict[str, Any]]) -> Optional[pd.DataFrame]:
        try:
            if not data:
                logger.warning("지수 데이터가 비어있습니다")
                return None

            df = pd.DataFrame(data.get("block1") or data.get("output", []))
            if df.empty:
                logger.warning("지수 DataFrame이 비어있습니다")
                return None

            df.rename(columns={
                "TRD_DD": "거래일",
                "CLSPRC_IDX": "종가",
                "CMPPREVDD_IDX": "대비",
                "FLUC_RT": "등락률",
                "OPNPRC_IDX": "시가",
                "HGPRC_IDX": "고가",
                "LWPRC_IDX": "저가",
            }, inplace=True)

            df["거래일"] = df["거래일"].apply(to_date)
            for col in ["종가", "대비", "등락률", "시가", "고가", "저가"]:
                if col in df.columns:
                    df[col] = df[col].apply(to_num)
                    df[col] = pd.to_numeric(df[col], errors='coerce')

            # 존재하는 컬럼만 반환
            cols = ["거래일", "종가", "대비", "등락률", "시가", "고가", "저가"]
            result = df[[c for c in cols if c in df.columns]]

            logger.debug("지수 데이터 파싱 완료: %d행", len(result))
            return result

        except KeyError as e:
            logger.error("지수 데이터 파싱 오류: 필수 컬럼 누락 %s", str(e))
            return None
        except ValueError as e:
            logger.error("지수 데이터 변환 오류: %s", str(e))
            return None
        except Exception as e:
            logger.error("지수 데이터 처리 오류: %s", str(e))
            return None


def combine(start: str, end: str) -> Optional[pd.DataFrame]:
    """모든 데이터를 조합"""
    try:
        logger.info("데이터 수집 시작: %s ~ %s", start, end)

        opt = OptionData()
        call = opt.parse(opt.get(start, end, "C"))
        put = opt.parse(opt.get(start, end, "P"))

        idx = IndexData()
        b5y = idx.parse(idx.get(start, end, "5년국채"))
        b10y = idx.parse(idx.get(start, end, "10년국채"))
        vix = idx.parse(idx.get(start, end, "VKOSPI"))
        kp = idx.parse(idx.get(start, end, "KOSPI"))
        kq = idx.parse(idx.get(start, end, "KOSDAQ"))

        if any(df is None or df.empty for df in [call, put, b5y, b10y, vix]):
            logger.error("필수 데이터 수집 실패 (Call/Put 옵션, 5년국채, 10년국채, VKOSPI)")
            return None

        # 옵션 5일 이동평균
        for df, col in [(call, "Call"), (put, "Put")]:
            df.sort_values("거래일", inplace=True)
            df.reset_index(drop=True, inplace=True)
            df[col] = df["전체"].rolling(5).mean()

        # 병합
        dfs = [
            b5y[["거래일", "종가"]].rename(columns={"종가": "5년국채"}),
            b10y[["거래일", "종가"]].rename(columns={"종가": "10년국채"}),
            vix[["거래일", "종가"]].rename(columns={"종가": "VIX"}),
            call[["거래일", "Call"]],
            put[["거래일", "Put"]],
        ]

        if kp is not None and not kp.empty:
            dfs.append(kp[["거래일", "종가"]].rename(columns={"종가": "KOSPI"}))
        if kq is not None and not kq.empty:
            dfs.append(kq[["거래일", "종가"]].rename(columns={"종가": "KOSDAQ"}))

        result = reduce(lambda l, r: l.merge(r, on="거래일", how="outer"), dfs)
        result = result.sort_values("거래일").reset_index(drop=True)

        logger.info("데이터 조합 완료: %d행", len(result))
        return result

    except KeyError as e:
        logger.error("데이터 병합 오류: 컬럼 누락 %s", str(e))
        return None
    except ValueError as e:
        logger.error("데이터 병합 오류: 값 변환 실패 %s", str(e))
        return None
    except Exception as e:
        logger.error("데이터 조합 오류: %s", str(e))
        return None


def calc_rsi(df: pd.DataFrame, col: str, window: int = 10) -> pd.DataFrame:
    """RSI 계산"""
    try:
        delta = df[col].diff(1)
        gain = delta.mask(delta < 0, 0).rolling(window).mean()
        loss = delta.mask(delta > 0, 0).abs().rolling(window).mean()

        # 0으로 나누기 방지
        rs = gain / loss.replace(0, float('nan'))
        df['RSI'] = 100 - (100 / (1 + rs))
        return df

    except Exception as e:
        logger.error("RSI 계산 오류: %s", str(e))
        df['RSI'] = float('nan')
        return df


def calc_fg(
    df: pd.DataFrame,
    idx_col: str,
    vix_col: str,
    call_col: str,
    put_col: str,
    b5_col: str,
    b10_col: str
) -> pd.DataFrame:
    """Fear & Greed 지수 계산"""
    try:
        df['MA125'] = df[idx_col].rolling(125).mean()
        df['Mom'] = (df[idx_col] - df['MA125']) / df['MA125'].replace(0, float('nan')) * 100
        df['PCR'] = df[put_col] / df[call_col].replace(0, float('nan'))
        df['Vol'] = df[vix_col]
        df['Spread'] = df[b10_col] - df[b5_col]

        scaler = MinMaxScaler()
        df[['Mom', 'PCR', 'Vol', 'Spread', 'RSI']] = scaler.fit_transform(
            df[['Mom', 'PCR', 'Vol', 'Spread', 'RSI']]
        )

        df['FG'] = (df['Mom'] * 0.2 + (1 - df['PCR']) * 0.2 +
                    (1 - df['Vol']) * 0.2 + df['Spread'] * 0.2 + df['RSI'] * 0.2)
        return df

    except Exception as e:
        logger.error("Fear & Greed 지수 계산 오류: %s", str(e))
        df['FG'] = float('nan')
        return df


def calc_macd(
    df: pd.DataFrame,
    col: str,
    short: int = 12,
    long: int = 26,
    signal: int = 9
) -> pd.DataFrame:
    """MACD 계산"""
    try:
        df['EMA_S'] = df[col].ewm(span=short, adjust=False).mean()
        df['EMA_L'] = df[col].ewm(span=long, adjust=False).mean()
        df['MACD'] = df['EMA_S'] - df['EMA_L']
        df['Signal'] = df['MACD'].ewm(span=signal, adjust=False).mean()
        df['Osc'] = df['MACD'] - df['Signal']
        return df

    except Exception as e:
        logger.error("MACD 계산 오류: %s", str(e))
        df['Osc'] = float('nan')
        return df


def analyze(df: pd.DataFrame) -> Tuple[Optional[pd.DataFrame], Optional[pd.DataFrame]]:
    """Fear & Greed 분석"""
    try:
        df['거래일'] = pd.to_datetime(df['거래일'])

        # 수치 변환
        for col in ['5년국채', '10년국채', 'VIX', 'KOSPI', 'KOSDAQ', 'Call', 'Put']:
            if col in df.columns:
                df[col] = pd.to_numeric(df[col], errors='coerce')

        # NaN 제거 (필수 컬럼만) - 불필요한 copy() 제거
        req = ['5년국채', '10년국채', 'VIX', 'Call', 'Put']
        df = df.dropna(subset=req)

        if len(df) == 0:
            logger.error("분석 가능한 데이터 없음")
            return None, None

        if len(df) < 125:
            logger.warning("데이터 %d일 (권장: 125일 이상)", len(df))

        kp_df, kq_df = None, None

        # KOSPI 분석
        if 'KOSPI' in df.columns and df['KOSPI'].notna().any():
            try:
                # 불필요한 copy() 제거 - 뷰로 작업
                kp_df = df.copy()  # 여기서만 복사
                kp_df = calc_rsi(kp_df, 'KOSPI')
                kp_df = calc_fg(kp_df, 'KOSPI', 'VIX', 'Call', 'Put', '5년국채', '10년국채')
                kp_df = calc_macd(kp_df, 'FG')
                kp_df = kp_df.dropna()  # copy() 제거

                if len(kp_df) > 0:
                    logger.info("="*80)
                    logger.info("KOSPI Fear & Greed Index")
                    logger.info("="*80)
                    logger.info("\n%s", kp_df[['거래일', 'KOSPI', 'FG', 'Osc']].tail(20).to_string(index=False))
                else:
                    logger.warning("KOSPI: 계산 후 유효 데이터 없음")
                    kp_df = None

            except Exception as e:
                logger.error("KOSPI 분석 오류: %s", str(e))
                kp_df = None

        # KOSDAQ 분석
        if 'KOSDAQ' in df.columns and df['KOSDAQ'].notna().any():
            try:
                # 불필요한 copy() 제거
                kq_df = df.copy()  # 여기서만 복사
                kq_df = calc_rsi(kq_df, 'KOSDAQ')
                kq_df = calc_fg(kq_df, 'KOSDAQ', 'VIX', 'Call', 'Put', '5년국채', '10년국채')
                kq_df = calc_macd(kq_df, 'FG')
                kq_df = kq_df.dropna()  # copy() 제거

                if len(kq_df) > 0:
                    logger.info("="*80)
                    logger.info("KOSDAQ Fear & Greed Index")
                    logger.info("="*80)
                    logger.info("\n%s", kq_df[['거래일', 'KOSDAQ', 'FG', 'Osc']].tail(20).to_string(index=False))
                else:
                    logger.warning("KOSDAQ: 계산 후 유효 데이터 없음")
                    kq_df = None

            except Exception as e:
                logger.error("KOSDAQ 분석 오류: %s", str(e))
                kq_df = None

        if kp_df is None and kq_df is None:
            logger.error("KOSPI/KOSDAQ 분석 모두 실패")
            return None, None

        return kp_df, kq_df

    except Exception as e:
        logger.error("분석 처리 오류: %s", str(e))
        return None, None


def run_analysis(start: str, end: str) -> Tuple[Optional[pd.DataFrame], Optional[pd.DataFrame]]:
    """
    Fear & Greed Index 분석 실행

    Args:
        start: 시작일 (YYYYMMDD)
        end: 종료일 (YYYYMMDD)

    Returns:
        Tuple[Optional[pd.DataFrame], Optional[pd.DataFrame]]: (KOSPI 결과, KOSDAQ 결과)
    """
    try:
        logger.info("="*80)
        logger.info("Fear & Greed Index 분석: %s ~ %s", start, end)
        logger.info("="*80)

        # 데이터 수집
        df = combine(start, end)
        if df is None or df.empty:
            logger.error("데이터 수집 실패")
            return None, None

        logger.info("조합 데이터: %d행", len(df))
        logger.debug("\n%s", df.to_string(index=False))

        # 분석
        kp_df, kq_df = analyze(df)

        logger.info("="*80)
        logger.info("분석 완료")
        logger.info("="*80)

        return kp_df, kq_df

    except KeyboardInterrupt:
        logger.warning("사용자에 의해 중단되었습니다")
        return None, None
    except Exception as e:
        logger.critical("치명적 오류: %s", str(e))
        import traceback
        logger.error(traceback.format_exc())
        return None, None
