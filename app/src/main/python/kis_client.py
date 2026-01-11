"""
Korea Investment Securities Open API Client.
Replaces pykrx for all Korean market data needs.

Reference: https://github.com/koreainvestment/open-trading-api

Version: 3.0 - Standalone (no pykrx dependency)
- Circuit breaker pattern for continuous failures
- HTTPError retry with exponential backoff
- Token refresh error handling
- Consistent error response parsing
- Better logging for debugging
- Standalone logger (no core.py dependency)
"""
import requests
import json
import time
import zipfile
import io
import logging
import sys
from datetime import datetime, timedelta
from typing import Optional, Dict, List, Tuple, Any, Union
from dataclasses import dataclass
from enum import Enum
import pandas as pd


# Standalone logger setup (no dependency on core.py)
def _get_logger(name: str) -> logging.Logger:
    """Get or create logger."""
    logger = logging.getLogger(name)
    if not logger.handlers:
        handler = logging.StreamHandler(sys.stderr)
        handler.setFormatter(logging.Formatter(
            '[%(name)s] %(levelname)s: %(message)s'
        ))
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
    return logger

log = _get_logger("kis_client")


class APIErrorCode(Enum):
    """KIS API 에러 코드."""
    SUCCESS = "0"
    TOKEN_EXPIRED = "EGW00123"
    RATE_LIMIT = "EGW00201"
    INVALID_TOKEN = "EGW00121"
    SERVICE_ERROR = "EGW00999"


@dataclass
class APIResult:
    """API 호출 결과를 담는 데이터 클래스."""
    success: bool
    data: Optional[Dict] = None
    error_code: Optional[str] = None
    error_message: Optional[str] = None

    @classmethod
    def ok(cls, data: Dict) -> "APIResult":
        return cls(success=True, data=data)

    @classmethod
    def fail(cls, code: str, message: str) -> "APIResult":
        return cls(success=False, error_code=code, error_message=message)


class CircuitBreaker:
    """Circuit Breaker 패턴 구현."""

    def __init__(self, failure_threshold: int = 5, recovery_timeout: int = 120):
        """
        Args:
            failure_threshold: 연속 실패 횟수 임계값
            recovery_timeout: 회로 차단 후 복구 대기 시간 (초) - 증가됨 (60→120)
        """
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        self.failure_count = 0
        self.last_failure_time: Optional[float] = None
        self.is_open = False

    def record_success(self):
        """성공 기록 - 실패 카운트 리셋."""
        self.failure_count = 0
        self.is_open = False

    def record_failure(self):
        """실패 기록 - 임계값 초과 시 회로 차단."""
        self.failure_count += 1
        self.last_failure_time = time.time()

        if self.failure_count >= self.failure_threshold:
            self.is_open = True
            log.warning(f"Circuit breaker OPEN after {self.failure_count} failures")

    def can_execute(self) -> bool:
        """실행 가능 여부 확인."""
        if not self.is_open:
            return True

        # 복구 타임아웃 체크
        if self.last_failure_time and \
                (time.time() - self.last_failure_time) > self.recovery_timeout:
            log.info("Circuit breaker attempting recovery (half-open)")
            return True

        return False

    def reset(self):
        """수동 리셋."""
        self.failure_count = 0
        self.is_open = False
        self.last_failure_time = None


class KISAPIClient:
    """Korea Investment Securities Open API Client."""

    BASE_URL = "https://openapi.koreainvestment.com:9443"
    TOKEN_EXPIRY_HOURS = 23

    # Rate limiting: 5 requests per second (200ms interval for stability)
    # KIS API has stricter rate limits - reduced from 10 to 5 requests/sec
    RATE_LIMIT_PER_SEC = 5
    MIN_REQUEST_INTERVAL = 0.2  # 200ms - increased for API stability (was 100ms)

    # Retry configuration - increased delays for rate limit recovery
    MAX_RETRIES = 3
    RETRY_DELAY_BASE = 2.0  # seconds (exponential backoff: 2, 4, 8) - was 1.0

    # HTTP status codes that should trigger retry
    RETRYABLE_STATUS_CODES = {500, 502, 503, 504, 429}

    def __init__(self, app_key: str, app_secret: str):
        """
        Initialize KIS API client.

        Args:
            app_key: KIS Open API app key
            app_secret: KIS Open API app secret
        """
        self.app_key = app_key
        self.app_secret = app_secret
        self._token: Optional[str] = None
        self._token_expiry: Optional[datetime] = None
        self._last_request_time: float = 0
        self._circuit_breaker = CircuitBreaker(failure_threshold=5, recovery_timeout=120)
        self._listed_shares_cache: Dict[str, int] = {}

    def _get_token(self, force_refresh: bool = False) -> str:
        """
        Get or refresh OAuth access token with error handling.

        Args:
            force_refresh: 강제로 새 토큰 발급

        Returns:
            Access token string

        Raises:
            RuntimeError: 토큰 발급 실패 시
        """
        # 기존 토큰이 유효하고 강제 갱신이 아니면 재사용
        if not force_refresh and self._token and self._token_expiry:
            if datetime.now() < self._token_expiry:
                return self._token

        url = f"{self.BASE_URL}/oauth2/tokenP"
        headers = {"content-type": "application/json"}
        body = {
            "grant_type": "client_credentials",
            "appkey": self.app_key,
            "appsecret": self.app_secret
        }

        last_error = None
        for attempt in range(self.MAX_RETRIES):
            try:
                response = requests.post(url, headers=headers, json=body, timeout=30)

                # 응답 파싱
                try:
                    data = response.json()
                except json.JSONDecodeError:
                    raise RuntimeError(f"Invalid JSON response from token endpoint: {response.text[:200]}")

                # HTTP 에러 체크
                if response.status_code != 200:
                    error_msg = data.get("error_description", data.get("msg1", "Unknown error"))

                    # 재시도 가능한 에러인지 확인
                    if response.status_code in self.RETRYABLE_STATUS_CODES:
                        if attempt < self.MAX_RETRIES - 1:
                            delay = self.RETRY_DELAY_BASE * (2 ** attempt)
                            log.warning(f"Token request failed (attempt {attempt + 1}), retrying in {delay}s: {error_msg}")
                            time.sleep(delay)
                            continue

                    raise RuntimeError(f"Token request failed ({response.status_code}): {error_msg}")

                # 토큰 추출
                access_token = data.get("access_token")
                if not access_token:
                    raise RuntimeError(f"No access_token in response: {data}")

                self._token = access_token
                self._token_expiry = datetime.now() + timedelta(hours=self.TOKEN_EXPIRY_HOURS)

                log.info("KIS API token refreshed successfully")
                return self._token

            except requests.exceptions.Timeout:
                last_error = "Token request timeout"
                if attempt < self.MAX_RETRIES - 1:
                    delay = self.RETRY_DELAY_BASE * (2 ** attempt)
                    log.warning(f"Token request timeout (attempt {attempt + 1}), retrying in {delay}s")
                    time.sleep(delay)
                    continue

            except requests.exceptions.RequestException as e:
                last_error = str(e)
                if attempt < self.MAX_RETRIES - 1:
                    delay = self.RETRY_DELAY_BASE * (2 ** attempt)
                    log.warning(f"Token request error (attempt {attempt + 1}), retrying in {delay}s: {e}")
                    time.sleep(delay)
                    continue

        raise RuntimeError(f"Failed to get token after {self.MAX_RETRIES} attempts: {last_error}")

    def _rate_limit(self):
        """Enforce rate limiting (20 requests/second)."""
        elapsed = time.time() - self._last_request_time
        if elapsed < self.MIN_REQUEST_INTERVAL:
            time.sleep(self.MIN_REQUEST_INTERVAL - elapsed)
        self._last_request_time = time.time()

    def _request(self, endpoint: str, tr_id: str, params: Dict) -> APIResult:
        """
        Make authenticated API request with rate limiting, retry, and circuit breaker.

        Args:
            endpoint: API endpoint path
            tr_id: Transaction ID for the API call
            params: Query parameters

        Returns:
            APIResult with success status and data or error info
        """
        # Circuit breaker 체크
        if not self._circuit_breaker.can_execute():
            return APIResult.fail("CIRCUIT_OPEN", "Circuit breaker is open. Too many recent failures.")

        url = f"{self.BASE_URL}{endpoint}"
        last_error = None

        for attempt in range(self.MAX_RETRIES):
            try:
                token = self._get_token()

                headers = {
                    "content-type": "application/json; charset=utf-8",
                    "authorization": f"Bearer {token}",
                    "appkey": self.app_key,
                    "appsecret": self.app_secret,
                    "tr_id": tr_id
                }

                self._rate_limit()
                response = requests.get(url, headers=headers, params=params, timeout=30)

                # HTTP 상태 코드 체크
                if response.status_code in self.RETRYABLE_STATUS_CODES:
                    last_error = f"HTTP {response.status_code}"
                    if attempt < self.MAX_RETRIES - 1:
                        delay = self.RETRY_DELAY_BASE * (2 ** attempt)
                        log.warning(f"Request failed with {response.status_code} (attempt {attempt + 1}), retrying in {delay}s")
                        time.sleep(delay)
                        continue
                    self._circuit_breaker.record_failure()
                    return APIResult.fail(str(response.status_code), f"HTTP error after {self.MAX_RETRIES} retries")

                # 응답 파싱
                try:
                    data = response.json()
                except json.JSONDecodeError:
                    # Log actual response content for debugging
                    response_preview = response.text[:500] if response.text else "(empty)"
                    log.warning(f"Invalid JSON response (status={response.status_code}): {response_preview}")
                    last_error = "Invalid JSON response"
                    if attempt < self.MAX_RETRIES - 1:
                        delay = self.RETRY_DELAY_BASE * (2 ** attempt)
                        log.warning(f"Invalid JSON response (attempt {attempt + 1}), retrying in {delay}s")
                        time.sleep(delay)
                        continue
                    self._circuit_breaker.record_failure()
                    return APIResult.fail("JSON_ERROR", f"Invalid JSON response: {response_preview[:100]}")

                # API 응답 코드 체크
                rt_cd = data.get("rt_cd", "")
                msg1 = data.get("msg1", "Unknown error")

                if rt_cd != "0":
                    # 토큰 만료 시 갱신 후 재시도
                    if rt_cd in [APIErrorCode.TOKEN_EXPIRED.value, APIErrorCode.INVALID_TOKEN.value]:
                        log.info("Token expired, refreshing...")
                        self._get_token(force_refresh=True)
                        if attempt < self.MAX_RETRIES - 1:
                            continue

                    # Rate limit 시 대기 후 재시도
                    if rt_cd == APIErrorCode.RATE_LIMIT.value:
                        if attempt < self.MAX_RETRIES - 1:
                            delay = self.RETRY_DELAY_BASE * (2 ** attempt) * 2  # 더 긴 대기
                            log.warning(f"Rate limited, waiting {delay}s before retry")
                            time.sleep(delay)
                            continue

                    log.warning(f"API error: [{rt_cd}] {msg1}")
                    return APIResult.fail(rt_cd, msg1)

                # 성공
                self._circuit_breaker.record_success()
                return APIResult.ok(data)

            except requests.exceptions.Timeout:
                last_error = "Request timeout"
                if attempt < self.MAX_RETRIES - 1:
                    delay = self.RETRY_DELAY_BASE * (2 ** attempt)
                    log.warning(f"Request timeout (attempt {attempt + 1}), retrying in {delay}s")
                    time.sleep(delay)
                    continue

            except requests.exceptions.RequestException as e:
                last_error = str(e)
                if attempt < self.MAX_RETRIES - 1:
                    delay = self.RETRY_DELAY_BASE * (2 ** attempt)
                    log.warning(f"Request error (attempt {attempt + 1}), retrying in {delay}s: {e}")
                    time.sleep(delay)
                    continue

        self._circuit_breaker.record_failure()
        return APIResult.fail("REQUEST_FAILED", f"Request failed after {self.MAX_RETRIES} attempts: {last_error}")

    def _request_paginated(
            self,
            endpoint: str,
            tr_id: str,
            params: Dict,
            output_keys: List[str] = None,
            max_pages: int = 10
    ) -> APIResult:
        """
        Make paginated API request following KIS API pagination pattern.

        KIS API uses tr_cont header for pagination:
        - "M" or "F": More data available
        - "" or "D" or "E": No more data

        Args:
            endpoint: API endpoint path
            tr_id: Transaction ID for the API call
            params: Query parameters
            output_keys: List of output keys to collect (default: ["output"])
            max_pages: Maximum number of pages to fetch

        Returns:
            APIResult with collected data for each output key
        """
        if output_keys is None:
            output_keys = ["output"]

        results = {key: [] for key in output_keys}
        tr_cont = ""

        for page in range(max_pages):
            # Circuit breaker 체크
            if not self._circuit_breaker.can_execute():
                return APIResult.fail("CIRCUIT_OPEN", "Circuit breaker is open")

            try:
                token = self._get_token()
            except RuntimeError as e:
                return APIResult.fail("TOKEN_ERROR", str(e))

            url = f"{self.BASE_URL}{endpoint}"

            headers = {
                "content-type": "application/json; charset=utf-8",
                "authorization": f"Bearer {token}",
                "appkey": self.app_key,
                "appsecret": self.app_secret,
                "tr_id": tr_id,
                "tr_cont": tr_cont
            }

            try:
                self._rate_limit()
                response = requests.get(url, headers=headers, params=params, timeout=30)

                if response.status_code in self.RETRYABLE_STATUS_CODES:
                    self._circuit_breaker.record_failure()
                    return APIResult.fail(str(response.status_code), f"HTTP error: {response.status_code}")

                data = response.json()

            except requests.exceptions.RequestException as e:
                self._circuit_breaker.record_failure()
                return APIResult.fail("REQUEST_ERROR", str(e))
            except json.JSONDecodeError:
                return APIResult.fail("JSON_ERROR", "Invalid JSON response")

            if data.get("rt_cd") != "0":
                if page == 0:
                    return APIResult.fail(data.get("rt_cd", "UNKNOWN"), data.get("msg1", "Unknown error"))
                break

            # Collect data from each output key
            for key in output_keys:
                output = data.get(key, [])
                if output:
                    if isinstance(output, dict):
                        output = [output]
                    results[key].extend(output)

            # Check continuation from response header
            resp_tr_cont = response.headers.get("tr_cont", "")

            if resp_tr_cont in ["M", "F"]:
                tr_cont = "N"  # Request next page
                log.debug(f"Fetching page {page + 2}...")
                time.sleep(0.3)  # Delay between pages (increased for rate limit)
            else:
                if page > 0:
                    log.info(f"Pagination complete. Total pages: {page + 1}")
                break
        else:
            # max_pages에 도달
            log.warning(f"Reached max_pages limit ({max_pages}). Data may be incomplete.")

        self._circuit_breaker.record_success()
        return APIResult.ok(results)

    # ========================================
    # ETF Holdings (replaces pykrx get_etf_portfolio_deposit_file)
    # ========================================

    def get_etf_holdings(self, etf_ticker: str) -> pd.DataFrame:
        """
        Get ETF component stocks with weights and amounts.

        Args:
            etf_ticker: ETF ticker (e.g., "069500" for KODEX 200)

        Returns:
            DataFrame with columns: ticker, name, weight, amount, quantity
            Empty DataFrame on error
        """
        params = {
            "fid_cond_mrkt_div_code": "J",
            "fid_input_iscd": etf_ticker,
            "fid_cond_scr_div_code": "11216"
        }

        result = self._request(
            "/uapi/etfetn/v1/quotations/inquire-component-stock-price",
            "FHKST121600C0",
            params
        )

        if not result.success:
            log.error(f"Failed to get ETF holdings for {etf_ticker}: {result.error_message}")
            return pd.DataFrame()

        output2 = result.data.get("output2", [])

        if output2:
            # Debug: log sample response to identify correct field names
            sample = output2[0] if output2 else {}
            log.debug(f"ETF holdings sample response keys: {list(sample.keys())}")
            log.debug(f"ETF holdings sample response: {sample}")

        holdings = []
        for item in output2:
            ticker = item.get("stck_shrn_iscd") or item.get("shrn_iscd") or ""
            if not ticker:
                continue

            # Try multiple possible field names for name
            name = (
                item.get("stck_prpr_name") or
                item.get("hts_kor_isnm") or
                item.get("prdt_name") or
                ""
            ).strip()

            # If name is still empty, try to get it from stock info API
            if not name or name.lower() == "none":
                try:
                    name = self.get_stock_name(ticker)
                except Exception:
                    pass
                if not name:
                    name = ticker  # Fallback to ticker as last resort

            # Try multiple possible field names for weight (percentage)
            # hldg_wght: 보유비중, mktc_wt: 시가총액비중
            weight_str = item.get("hldg_wght") or item.get("mktc_wt") or "0"
            try:
                weight = float(weight_str) if weight_str else 0.0
            except (ValueError, TypeError):
                weight = 0.0

            # Try multiple possible field names for amount
            # evlu_amt: 평가금액
            amount_str = item.get("evlu_amt") or item.get("evlu_pfls_amt") or "0"
            try:
                amount = float(amount_str) if amount_str else 0.0
            except (ValueError, TypeError):
                amount = 0.0

            # Quantity
            qty_str = item.get("hldg_qty") or item.get("cblc_qty") or "0"
            try:
                quantity = int(qty_str) if qty_str else 0
            except (ValueError, TypeError):
                quantity = 0

            holdings.append({
                "ticker": ticker,
                "name": name,
                "weight": weight,
                "amount": amount,
                "quantity": quantity
            })

        if holdings:
            log.debug(f"ETF {etf_ticker}: {len(holdings)} holdings, sample: {holdings[0]}")

        return pd.DataFrame(holdings)

    # ========================================
    # Investor Trading (replaces pykrx get_market_trading_value_by_date)
    # ========================================

    def get_investor_trading(
            self,
            ticker: str,
            start_date: str
    ) -> pd.DataFrame:
        """
        Get daily investor trading data with pagination support.

        Args:
            ticker: Stock ticker (e.g., "005930")
            start_date: Start date (YYYYMMDD)

        Returns:
            DataFrame with columns: date, foreign_net, institution_net, etc.
            Empty DataFrame on error
        """
        params = {
            "fid_cond_mrkt_div_code": "J",
            "fid_input_iscd": ticker,
            "fid_input_date_1": start_date,
            "fid_org_adj_prc": "",
            "fid_etc_cls_code": ""
        }

        result = self._request_paginated(
            "/uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily",
            "FHPTJ04160001",
            params,
            output_keys=["output2"]
        )

        if not result.success:
            log.error(f"Failed to get investor trading for {ticker}: {result.error_message}")
            return pd.DataFrame()

        output2 = result.data.get("output2", [])

        records = []
        for item in output2:
            date_str = item.get("stck_bsop_date")
            if not date_str:
                continue

            records.append({
                "date": date_str,
                # pykrx compatible column names (Korean)
                "외국인합계": int(item.get("frgn_ntby_qty", 0) or 0),
                "기관합계": int(item.get("orgn_ntby_qty", 0) or 0),
                "개인": int(item.get("prsn_ntby_qty", 0) or 0),
                # English column names (for convenience)
                "foreign_net": int(item.get("frgn_ntby_qty", 0) or 0),
                "institution_net": int(item.get("orgn_ntby_qty", 0) or 0),
                "individual_net": int(item.get("prsn_ntby_qty", 0) or 0),
                "pension_net": int(item.get("pnsn_fnd_ntby_qty", 0) or 0)
            })

        df = pd.DataFrame(records)
        if not df.empty:
            df["date"] = pd.to_datetime(df["date"])
            df.set_index("date", inplace=True)
            df = df.sort_index()

        log.debug(f"Investor trading {ticker}: {len(df)} records")
        return df

    # ========================================
    # Stock OHLCV (replaces pykrx get_market_ohlcv)
    # ========================================

    def get_stock_ohlcv(
            self,
            ticker: str,
            start_date: str,
            end_date: str
    ) -> pd.DataFrame:
        """
        Get stock daily OHLCV data with pagination for long periods.

        Args:
            ticker: Stock ticker (e.g., "005930")
            start_date: Start date (YYYYMMDD)
            end_date: End date (YYYYMMDD)

        Returns:
            DataFrame with OHLCV columns indexed by date
            Empty DataFrame on error
        """
        all_data = []
        current_end = end_date

        # 최대 100건씩 페이지네이션
        for _ in range(50):  # 최대 5000일 (약 20년)
            params = {
                "fid_cond_mrkt_div_code": "J",
                "fid_input_iscd": ticker,
                "fid_input_date_1": start_date,
                "fid_input_date_2": current_end,
                "fid_period_div_code": "D",
                "fid_org_adj_prc": "0"
            }

            result = self._request(
                "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice",
                "FHKST03010100",
                params
            )

            if not result.success:
                log.error(f"Failed to get OHLCV for {ticker}: {result.error_message}")
                break

            output2 = result.data.get("output2", [])
            if not output2:
                break

            all_data.extend(output2)

            # 100건 미만이면 더 이상 데이터 없음
            if len(output2) < 100:
                break

            # 다음 페이지를 위해 end_date 조정 (가장 오래된 날짜 - 1일)
            oldest_date = min(item.get("stck_bsop_date", "99999999") for item in output2)
            if oldest_date <= start_date:
                break

            # 하루 전으로 설정
            from datetime import datetime, timedelta
            try:
                oldest_dt = datetime.strptime(oldest_date, "%Y%m%d")
                current_end = (oldest_dt - timedelta(days=1)).strftime("%Y%m%d")
            except ValueError:
                break

            time.sleep(0.3)  # API 부하 방지 (increased for rate limit)

        if not all_data:
            return pd.DataFrame()

        df = pd.DataFrame([{
            "date": item.get("stck_bsop_date"),
            "open": int(item.get("stck_oprc", 0) or 0),
            "high": int(item.get("stck_hgpr", 0) or 0),
            "low": int(item.get("stck_lwpr", 0) or 0),
            "close": int(item.get("stck_clpr", 0) or 0),
            "volume": int(item.get("acml_vol", 0) or 0)
        } for item in all_data if item.get("stck_bsop_date")])

        if not df.empty:
            df["date"] = pd.to_datetime(df["date"])
            df = df.drop_duplicates(subset=["date"])
            df.set_index("date", inplace=True)
            return df.sort_index()
        return df

    # ========================================
    # Index OHLCV (replaces pykrx get_index_ohlcv)
    # ========================================

    def get_index_ohlcv(
            self,
            index_code: str,
            start_date: str,
            end_date: str = None
    ) -> pd.DataFrame:
        """
        Get index daily OHLCV data with pagination support.

        Args:
            index_code: Index code (e.g., "0001" for KOSPI, "1001" for KOSDAQ)
            start_date: Start date (YYYYMMDD)
            end_date: End date (YYYYMMDD, optional)

        Returns:
            DataFrame with columns: date, open, high, low, close, volume
            Empty DataFrame on error
        """
        params = {
            "fid_period_div_code": "D",
            "fid_cond_mrkt_div_code": "U",
            "fid_input_iscd": index_code,
            "fid_input_date_1": start_date
        }

        result = self._request_paginated(
            "/uapi/domestic-stock/v1/quotations/inquire-index-daily-price",
            "FHPUP02120000",
            params,
            output_keys=["output2"]
        )

        if not result.success:
            log.error(f"Failed to get index OHLCV for {index_code}: {result.error_message}")
            return pd.DataFrame()

        output2 = result.data.get("output2", [])

        df = pd.DataFrame([{
            "date": item.get("stck_bsop_date"),
            "open": float(item.get("bstp_nmix_oprc", 0) or 0),
            "high": float(item.get("bstp_nmix_hgpr", 0) or 0),
            "low": float(item.get("bstp_nmix_lwpr", 0) or 0),
            "close": float(item.get("bstp_nmix_prpr", 0) or 0),
            "volume": int(item.get("acml_vol", 0) or 0)
        } for item in output2 if item.get("stck_bsop_date")])

        if not df.empty:
            df["date"] = pd.to_datetime(df["date"])
            df.set_index("date", inplace=True)

            # end_date 필터링
            if end_date:
                end_dt = pd.to_datetime(end_date)
                df = df[df.index <= end_dt]

            return df.sort_index()
        return df

    # ========================================
    # Stock Info (replaces pykrx get_market_ticker_name)
    # ========================================

    def get_stock_info(self, ticker: str) -> Optional[Dict]:
        """
        Get current stock info including name and price.

        Args:
            ticker: Stock ticker (e.g., "005930")

        Returns:
            Dict with keys: ticker, name, price, market_cap, etc.
            None on error
        """
        params = {
            "fid_cond_mrkt_div_code": "J",
            "fid_input_iscd": ticker
        }

        result = self._request(
            "/uapi/domestic-stock/v1/quotations/inquire-price",
            "FHKST01010100",
            params
        )

        if not result.success:
            log.warning(f"Failed to get stock info for {ticker}: {result.error_message}")
            return None

        output = result.data.get("output", {})

        return {
            "ticker": ticker,
            "name": output.get("hts_kor_isnm", ""),
            "price": int(output.get("stck_prpr", 0) or 0),
            "market_cap": int(output.get("hts_avls", 0) or 0) * 100000000,  # 억원 → 원
            "volume": int(output.get("acml_vol", 0) or 0),
            "per": float(output.get("per", 0) or 0),
            "pbr": float(output.get("pbr", 0) or 0)
        }

    def get_stock_name(self, ticker: str) -> str:
        """Get stock name by ticker."""
        info = self.get_stock_info(ticker)
        return info.get("name", "") if info else ""

    # ========================================
    # Market Cap Ranking (replaces pykrx get_market_cap)
    # ========================================

    def get_market_cap_ranking(
            self,
            market: str = "0000",
            limit: int = 100
    ) -> pd.DataFrame:
        """
        Get market capitalization ranking with pagination support.

        Args:
            market: Market code ("0000": all, "0001": KOSPI, "1001": KOSDAQ)
            limit: Maximum number of results

        Returns:
            DataFrame with columns: rank, ticker, name, price, market_cap
            Empty DataFrame on error
        """
        params = {
            "fid_input_price_2": "",
            "fid_cond_mrkt_div_code": "J",
            "fid_cond_scr_div_code": "20174",
            "fid_div_cls_code": "0",
            "fid_input_iscd": market,
            "fid_trgt_cls_code": "0",
            "fid_trgt_exls_cls_code": "0",
            "fid_input_price_1": "",
            "fid_vol_cnt": ""
        }

        # Calculate max pages needed (assuming ~30 items per page)
        max_pages = (limit // 30) + 2

        result = self._request_paginated(
            "/uapi/domestic-stock/v1/ranking/market-cap",
            "FHPST01740000",
            params,
            output_keys=["output"],
            max_pages=max_pages
        )

        if not result.success:
            log.error(f"Failed to get market cap ranking: {result.error_message}")
            return pd.DataFrame()

        output = result.data.get("output", [])[:limit]

        return pd.DataFrame([{
            "rank": int(item.get("data_rank", 0) or 0),
            "ticker": item.get("stck_shrn_iscd"),
            "name": item.get("hts_kor_isnm"),
            "price": int(item.get("stck_prpr", 0) or 0),
            "market_cap": int(item.get("stck_avls", 0) or 0) * 100000000
        } for item in output if item.get("stck_shrn_iscd")])

    # ========================================
    # ETF List (replaces pykrx get_etf_ticker_list)
    # ========================================

    def download_etf_master(self) -> pd.DataFrame:
        """
        Download ETF master list from KIS server.

        Returns:
            DataFrame with columns: ticker, name
            Empty DataFrame on error
        """
        # Try multiple possible ETF master file URLs
        urls = [
            "https://new.real.download.dws.co.kr/common/master/etf_code.mst.zip",
            "https://new.real.download.dws.co.kr/common/master/etn_code.mst.zip",
        ]

        for url in urls:
            last_error = None
            response = None

            for attempt in range(self.MAX_RETRIES):
                try:
                    response = requests.get(url, timeout=60)
                    response.raise_for_status()
                    break
                except requests.exceptions.RequestException as e:
                    last_error = e
                    if attempt < self.MAX_RETRIES - 1:
                        delay = self.RETRY_DELAY_BASE * (2 ** attempt)
                        log.debug(f"ETF master download failed (attempt {attempt + 1}): {e}")
                        time.sleep(delay)
                        continue
                    response = None

            if response is None:
                log.debug(f"Could not download from {url}: {last_error}")
                continue

            try:
                with zipfile.ZipFile(io.BytesIO(response.content)) as zf:
                    filename = zf.namelist()[0]
                    with zf.open(filename) as f:
                        content = f.read().decode("cp949")

                # Parse master file - similar format to stock master
                etfs = self._parse_master_content(content, "ETF")
                if etfs:
                    log.info(f"Downloaded {len(etfs)} ETFs from master file ({url})")
                    # Reset circuit breaker since we got data successfully
                    self._circuit_breaker.reset()
                    return pd.DataFrame(etfs)

            except Exception as e:
                log.debug(f"Failed to parse {url}: {e}")
                continue

        log.warning("ETF master file download failed, using fallback method")
        return pd.DataFrame()

    def _parse_master_content(self, content: str, asset_type: str = "ETF") -> List[Dict]:
        """
        Parse master file content to extract ticker and name.

        Args:
            content: Raw content from master file
            asset_type: Type of asset (ETF/ETN/Stock)

        Returns:
            List of {"ticker": ..., "name": ...}
        """
        items = []
        for line in content.strip().split("\n"):
            if len(line) < 20:
                continue

            # Try to extract ticker (first 9 chars contain ticker)
            ticker_raw = line[0:9].strip()
            if not ticker_raw:
                continue

            # Get 6-digit ticker
            ticker = ticker_raw[:6] if len(ticker_raw) >= 6 else ticker_raw
            if not ticker.isdigit():
                continue

            # Extract name from remaining content
            # Name is typically after ticker, before numeric data
            try:
                # Find the part2 marker (228 bytes from end for stock format)
                part2_start = len(line) - 228 if len(line) > 250 else len(line) // 2
                name_part = line[9:part2_start] if part2_start > 9 else line[9:]
                name = name_part.strip()

                # Clean name - remove extra spaces and trailing data
                if "  " in name:
                    name = name.split("  ")[0].strip()

                # Fallback: just take first meaningful chunk
                if not name or len(name) < 2:
                    parts = line[9:].split()
                    name = parts[0] if parts else ""

                if ticker and name and len(name) >= 2:
                    items.append({"ticker": ticker, "name": name})

            except Exception:
                continue

        return items

    def get_etf_list_from_stocks(self) -> pd.DataFrame:
        """
        Get ETF list by filtering from stock master files.

        ETFs in Korea typically have tickers in specific ranges.

        Returns:
            DataFrame with columns: ticker, name
            Empty DataFrame on error
        """
        try:
            all_stocks = self.get_all_stocks()
            if all_stocks.empty:
                return pd.DataFrame()

            # Filter for ETF-like tickers
            # Most Korean ETFs have tickers starting with specific patterns
            # Common ETF prefixes: 069, 091, 102, 114, 117, 122, 130, 137, 139, etc.
            # Also, ETF names usually contain "ETF" or specific keywords
            etf_keywords = ["ETF", "KODEX", "TIGER", "KINDEX", "ARIRANG", "SOL",
                          "HANARO", "KOSEF", "TREX", "KBSTAR", "ACE", "TIMEFOLIO"]

            etfs = []
            for _, row in all_stocks.iterrows():
                name = row.get("name", "")
                ticker = row.get("ticker", "")

                # Check if name contains ETF keywords
                if any(kw in name.upper() for kw in etf_keywords):
                    etfs.append({"ticker": ticker, "name": name})

            if etfs:
                log.info(f"Found {len(etfs)} ETFs from stock master (keyword filter)")
                # Reset circuit breaker since we got data successfully via alternative method
                self._circuit_breaker.reset()
            return pd.DataFrame(etfs)

        except Exception as e:
            log.error(f"Failed to get ETF list from stocks: {e}")
            return pd.DataFrame()

    def get_etf_list(self) -> pd.DataFrame:
        """
        Get all ETF list.

        Tries multiple methods in order:
        1. ETF master file download
        2. Stock master filtering by ETF keywords
        3. API endpoint (fallback)

        Returns:
            DataFrame with columns: ticker, name
            Empty DataFrame on error
        """
        # Method 1: Try master file download first (most reliable)
        df = self.download_etf_master()
        if not df.empty:
            return df

        # Method 2: Try filtering from stock master
        log.info("Trying ETF list from stock master...")
        df = self.get_etf_list_from_stocks()
        if not df.empty:
            return df

        log.warning("Stock master fallback failed, trying API endpoint...")

        # Method 3: Fallback to API endpoint (least reliable)
        params = {
            "fid_cond_mrkt_div_code": "J",
            "fid_cond_scr_div_code": "13001",
            "fid_input_iscd": "0000",
            "fid_rank_sort_cls_code": "0",
            "fid_div_cls_code": "0",
            "fid_trgt_cls_code": "0",
            "fid_trgt_exls_cls_code": "0",
            "fid_input_price_1": "",
            "fid_input_price_2": "",
            "fid_vol_cnt": "",
            "fid_input_date_1": ""
        }

        result = self._request(
            "/uapi/domestic-stock/v1/quotations/inquire-search-stock-info",
            "CTPF1002R",
            params
        )

        if not result.success:
            log.error(f"Failed to get ETF list via API: {result.error_message}")
            return pd.DataFrame()

        output = result.data.get("output", [])

        return pd.DataFrame([{
            "ticker": item.get("stck_shrn_iscd"),
            "name": item.get("hts_kor_isnm", "")
        } for item in output if item.get("stck_shrn_iscd")])

    # ========================================
    # Stock List (KOSPI/KOSDAQ master files)
    # ========================================

    def download_stock_master(self, market: str = "kospi") -> pd.DataFrame:
        """
        Download stock master list from KIS server with retry.

        Args:
            market: "kospi" or "kosdaq"

        Returns:
            DataFrame with columns: ticker, name, market, listed_shares
            Empty DataFrame on error
        """
        if market.lower() == "kospi":
            url = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip"
        elif market.lower() == "kosdaq":
            url = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip"
        else:
            log.error(f"Unknown market: {market}")
            return pd.DataFrame()

        last_error = None
        for attempt in range(self.MAX_RETRIES):
            try:
                response = requests.get(url, timeout=60)
                response.raise_for_status()
                break
            except requests.exceptions.RequestException as e:
                last_error = e
                if attempt < self.MAX_RETRIES - 1:
                    delay = self.RETRY_DELAY_BASE * (2 ** attempt)
                    log.warning(f"Master download failed (attempt {attempt + 1}), retrying in {delay}s: {e}")
                    time.sleep(delay)
                    continue
        else:
            log.error(f"Failed to download stock master after {self.MAX_RETRIES} attempts: {last_error}")
            return pd.DataFrame()

        try:
            with zipfile.ZipFile(io.BytesIO(response.content)) as zf:
                filename = zf.namelist()[0]
                with zf.open(filename) as f:
                    content = f.read().decode("cp949")
        except Exception as e:
            log.error(f"Failed to parse stock master file: {e}")
            return pd.DataFrame()

        # Parse using fixed-width format
        field_specs = [
            2, 1, 4, 4, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 9, 5, 5, 1, 1, 1, 2, 1, 1, 1, 2, 2, 2, 3,
            1, 3, 12, 12, 8, 15, 21, 2, 7, 1, 1, 1, 1, 1, 9,
            9, 9, 5, 9, 8, 9, 3, 1, 1, 1
        ]
        listed_shares_offset = sum(field_specs[:50])
        listed_shares_len = field_specs[50]

        stocks = []
        for line in content.strip().split("\n"):
            if len(line) < 228:
                continue

            ticker = line[0:9].strip()
            if not ticker or len(ticker) != 6 or not ticker.isdigit():
                continue

            part2_start = len(line) - 228
            name = line[21:part2_start].strip()

            part2 = line[part2_start:]
            listed_shares_str = part2[listed_shares_offset:listed_shares_offset + listed_shares_len].strip()

            try:
                listed_shares = int(listed_shares_str) if listed_shares_str else 0
            except ValueError:
                listed_shares = 0

            stocks.append({
                "ticker": ticker,
                "name": name,
                "market": market.upper(),
                "listed_shares": listed_shares
            })

        log.info(f"Downloaded {len(stocks)} stocks from {market.upper()} master")
        return pd.DataFrame(stocks)

    def get_all_stocks(self) -> pd.DataFrame:
        """Get all KOSPI and KOSDAQ stocks."""
        kospi = self.download_stock_master("kospi")
        kosdaq = self.download_stock_master("kosdaq")
        return pd.concat([kospi, kosdaq], ignore_index=True)

    # ========================================
    # Market Ticker List
    # ========================================

    def get_market_ticker_list(self, market: str = "ALL") -> List[str]:
        """
        Get list of stock tickers for a market.

        Args:
            market: "KOSPI", "KOSDAQ", or "ALL"

        Returns:
            List of ticker strings
        """
        if market.upper() == "ALL":
            df = self.get_all_stocks()
        elif market.upper() == "KOSPI":
            df = self.download_stock_master("kospi")
        elif market.upper() == "KOSDAQ":
            df = self.download_stock_master("kosdaq")
        else:
            log.error(f"Unknown market: {market}")
            return []

        return df["ticker"].tolist() if not df.empty else []

    # ========================================
    # Listed Shares (for market cap calculation)
    # ========================================

    def get_listed_shares(self, ticker: str) -> int:
        """
        Get listed shares for a ticker (in units of 1000).

        Args:
            ticker: Stock ticker (e.g., "005930")

        Returns:
            Listed shares in units of 1000 (multiply by 1000 for actual shares)
        """
        if ticker in self._listed_shares_cache:
            return self._listed_shares_cache[ticker]

        if not self._listed_shares_cache:
            try:
                df = self.get_all_stocks()
                for _, row in df.iterrows():
                    self._listed_shares_cache[row["ticker"]] = row.get("listed_shares", 0)
            except Exception as e:
                log.warning(f"Failed to load listed shares from master: {e}")
                return 0

        return self._listed_shares_cache.get(ticker, 0)

    # ========================================
    # Index Components
    # ========================================

    def get_index_components(self, market: str = "KOSPI", limit: int = 200) -> List[str]:
        """
        Get top N stocks by market cap as index components.

        Args:
            market: "KOSPI" or "KOSDAQ"
            limit: Number of stocks to return (default 200)

        Returns:
            List of ticker strings
        """
        market_code = "0001" if market.upper() == "KOSPI" else "1001"
        df = self.get_market_cap_ranking(market=market_code, limit=limit)
        return df["ticker"].tolist() if not df.empty else []

    # ========================================
    # Stock OHLCV with Market Cap
    # ========================================

    def get_stock_ohlcv_with_market_cap(
            self,
            ticker: str,
            start_date: str,
            end_date: str
    ) -> pd.DataFrame:
        """
        Get stock daily OHLCV data with calculated market cap.

        Market cap = close price * listed_shares * 1000

        Args:
            ticker: Stock ticker (e.g., "005930")
            start_date: Start date (YYYYMMDD)
            end_date: End date (YYYYMMDD)

        Returns:
            DataFrame with OHLCV columns + market_cap, indexed by date
            Empty DataFrame on error
        """
        df = self.get_stock_ohlcv(ticker, start_date, end_date)

        if df.empty:
            return df

        listed_shares = self.get_listed_shares(ticker)

        if listed_shares > 0:
            df["market_cap"] = df["close"] * listed_shares * 1000
            # Debug: log sample market cap calculation
            if not df.empty:
                sample_close = df["close"].iloc[-1]
                sample_mcap = df["market_cap"].iloc[-1]
                log.debug(f"Market cap for {ticker}: close={sample_close}, listed_shares={listed_shares} (in 1000s), market_cap={sample_mcap/1e12:.2f}T")
        else:
            df["market_cap"] = 0
            log.warning(f"No listed shares found for {ticker}, market cap set to 0")

        return df

    def get_market_cap_daily(
            self,
            ticker: str,
            start_date: str,
            end_date: str
    ) -> pd.DataFrame:
        """
        Get daily market cap data (pykrx compatible format).

        Calculation: close price × listed_shares × 1000

        Args:
            ticker: Stock ticker (e.g., "005930")
            start_date: Start date (YYYYMMDD)
            end_date: End date (YYYYMMDD)

        Returns:
            DataFrame with column: 시가총액 (for pykrx compatibility)
            Index: DatetimeIndex
        """
        ohlcv = self.get_stock_ohlcv(ticker, start_date, end_date)
        if ohlcv.empty:
            return pd.DataFrame()

        listed_shares = self.get_listed_shares(ticker)
        if listed_shares == 0:
            log.warning(f"No listed shares found for {ticker}, loading from master")
            self.get_all_stocks()
            listed_shares = self.get_listed_shares(ticker)

        df = pd.DataFrame(index=ohlcv.index)
        df["시가총액"] = ohlcv["close"] * listed_shares * 1000

        if not df.empty:
            log.debug(f"Market cap {ticker}: close={ohlcv['close'].iloc[-1]}, "
                      f"listed_shares={listed_shares}k, "
                      f"market_cap={df['시가총액'].iloc[-1]/1e12:.2f}T")

        return df

    # ========================================
    # Health Check
    # ========================================

    def health_check(self) -> Dict[str, Any]:
        """
        API 연결 상태 확인.

        Returns:
            Dict with health status information
        """
        result = {
            "status": "unknown",
            "token_valid": False,
            "circuit_breaker_open": self._circuit_breaker.is_open,
            "failure_count": self._circuit_breaker.failure_count,
            "timestamp": datetime.now().isoformat()
        }

        try:
            # 토큰 테스트
            self._get_token()
            result["token_valid"] = True

            # 간단한 API 호출 테스트
            test_result = self._request(
                "/uapi/domestic-stock/v1/quotations/inquire-price",
                "FHKST01010100",
                {"fid_cond_mrkt_div_code": "J", "fid_input_iscd": "005930"}
            )

            if test_result.success:
                result["status"] = "healthy"
            else:
                result["status"] = "degraded"
                result["error"] = test_result.error_message

        except Exception as e:
            result["status"] = "unhealthy"
            result["error"] = str(e)

        return result


# ========================================
# Global instance management
# ========================================

_client: Optional[KISAPIClient] = None


def init_kis_client(app_key: str, app_secret: str) -> bool:
    """
    Initialize global KIS API client.

    Args:
        app_key: KIS Open API app key
        app_secret: KIS Open API app secret

    Returns:
        True if initialization successful, False otherwise
    """
    global _client
    if not app_key or not app_secret:
        log.error("KIS API key or secret is empty")
        return False
    try:
        _client = KISAPIClient(app_key, app_secret)
        log.info("KIS API client initialized successfully")
        return True
    except Exception as e:
        log.error(f"Failed to initialize KIS client: {e}")
        return False


def reset_client():
    """Reset the global client (for re-initialization)."""
    global _client
    _client = None


def get_client() -> KISAPIClient:
    """Get global KIS API client instance."""
    if _client is None:
        raise RuntimeError("KIS client not initialized. Call init_kis_client first.")
    return _client


def is_client_initialized() -> bool:
    """Check if KIS client is initialized."""
    return _client is not None


# ========================================
# Convenience functions
# ========================================

def get_etf_holdings(etf_ticker: str) -> str:
    """Get ETF holdings as JSON string."""
    try:
        client = get_client()
        df = client.get_etf_holdings(etf_ticker)
        if df.empty:
            return json.dumps({"error": "No data"}, ensure_ascii=False)
        return df.to_json(orient="records", force_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)


def get_stock_ohlcv(ticker: str, start_date: str, end_date: str) -> str:
    """Get stock OHLCV data as JSON string."""
    try:
        client = get_client()
        df = client.get_stock_ohlcv(ticker, start_date, end_date)
        if df.empty:
            return json.dumps({"error": "No data"}, ensure_ascii=False)
        df = df.reset_index()
        df["date"] = df["date"].dt.strftime("%Y-%m-%d")
        return df.to_json(orient="records", force_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)


def get_investor_trading_data(ticker: str, start_date: str) -> str:
    """Get investor trading data as JSON string."""
    try:
        client = get_client()
        df = client.get_investor_trading(ticker, start_date)
        if df.empty:
            return json.dumps({"error": "No data"}, ensure_ascii=False)
        return df.to_json(orient="records", force_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)
