"""
Korea Investment Securities Open API Client.
Replaces pykrx for all Korean market data needs.

Reference: https://github.com/koreainvestment/open-trading-api

Phase 2 of KIS API Migration:
- Token management with auto-refresh
- Rate limiting (20 requests/second)
- Retry logic with exponential backoff
- ETF holdings, investor trading, OHLCV data
- Stock master download from KIS server
"""
import requests
import json
import time
import zipfile
import io
from datetime import datetime, timedelta
from typing import Optional, Dict, List, Tuple, Any
import pandas as pd
from core import get_logger

log = get_logger("kis_client")


class KISAPIClient:
    """Korea Investment Securities Open API Client."""

    BASE_URL = "https://openapi.koreainvestment.com:9443"
    TOKEN_EXPIRY_HOURS = 23

    # Rate limiting: 20 requests per second
    RATE_LIMIT_PER_SEC = 20
    MIN_REQUEST_INTERVAL = 1.0 / RATE_LIMIT_PER_SEC  # 0.05 seconds

    # Retry configuration
    MAX_RETRIES = 3
    RETRY_DELAY_BASE = 1.0  # seconds (exponential backoff: 1, 2, 4)

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

    def _get_token(self) -> str:
        """Get or refresh OAuth access token."""
        if self._token and self._token_expiry and datetime.now() < self._token_expiry:
            return self._token

        url = f"{self.BASE_URL}/oauth2/tokenP"
        headers = {"content-type": "application/json"}
        body = {
            "grant_type": "client_credentials",
            "appkey": self.app_key,
            "appsecret": self.app_secret
        }

        response = requests.post(url, headers=headers, json=body, timeout=30)
        response.raise_for_status()

        data = response.json()
        self._token = data["access_token"]
        self._token_expiry = datetime.now() + timedelta(hours=self.TOKEN_EXPIRY_HOURS)

        log.info("KIS API token refreshed")
        return self._token

    def _rate_limit(self):
        """Enforce rate limiting (20 requests/second)."""
        elapsed = time.time() - self._last_request_time
        if elapsed < self.MIN_REQUEST_INTERVAL:
            time.sleep(self.MIN_REQUEST_INTERVAL - elapsed)
        self._last_request_time = time.time()

    def _request(self, endpoint: str, tr_id: str, params: Dict) -> Dict:
        """
        Make authenticated API request with rate limiting and retry.

        Args:
            endpoint: API endpoint path
            tr_id: Transaction ID for the API call
            params: Query parameters

        Returns:
            JSON response as dictionary

        Raises:
            requests.exceptions.RequestException: If all retries fail
        """
        token = self._get_token()
        url = f"{self.BASE_URL}{endpoint}"

        headers = {
            "content-type": "application/json; charset=utf-8",
            "authorization": f"Bearer {token}",
            "appkey": self.app_key,
            "appsecret": self.app_secret,
            "tr_id": tr_id
        }

        last_error = None
        for attempt in range(self.MAX_RETRIES):
            try:
                self._rate_limit()
                response = requests.get(url, headers=headers, params=params, timeout=30)
                response.raise_for_status()
                return response.json()
            except requests.exceptions.RequestException as e:
                last_error = e
                if attempt < self.MAX_RETRIES - 1:
                    delay = self.RETRY_DELAY_BASE * (2 ** attempt)
                    log.warning(f"Request failed (attempt {attempt + 1}), retrying in {delay}s: {e}")
                    time.sleep(delay)

        raise last_error

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
        """
        params = {
            "fid_cond_mrkt_div_code": "J",
            "fid_input_iscd": etf_ticker,
            "fid_cond_scr_div_code": "11216"
        }

        data = self._request(
            "/uapi/etfetn/v1/quotations/inquire-component-stock-price",
            "FHKST121600C0",
            params
        )

        if data.get("rt_cd") != "0":
            raise ValueError(f"API error: {data.get('msg1')}")

        output2 = data.get("output2", [])

        return pd.DataFrame([{
            "ticker": item.get("stck_shrn_iscd"),
            "name": item.get("stck_prpr_name"),
            "weight": float(item.get("hldg_wght", 0)),
            "amount": float(item.get("evlu_amt", 0)),
            "quantity": int(item.get("hldg_qty", 0))
        } for item in output2])

    # ========================================
    # Investor Trading (replaces pykrx get_market_trading_value_by_date)
    # ========================================

    def get_investor_trading(
        self,
        ticker: str,
        start_date: str
    ) -> pd.DataFrame:
        """
        Get daily investor trading data.

        Args:
            ticker: Stock ticker (e.g., "005930")
            start_date: Start date (YYYYMMDD)

        Returns:
            DataFrame with columns: date, foreign_net, institution_net, etc.
        """
        params = {
            "fid_cond_mrkt_div_code": "J",
            "fid_input_iscd": ticker,
            "fid_input_date_1": start_date,
            "fid_org_adj_prc": "",
            "fid_etc_cls_code": ""
        }

        data = self._request(
            "/uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily",
            "FHPTJ04160001",
            params
        )

        if data.get("rt_cd") != "0":
            raise ValueError(f"API error: {data.get('msg1')}")

        output2 = data.get("output2", [])

        return pd.DataFrame([{
            "date": item.get("stck_bsop_date"),
            "foreign_net": int(item.get("frgn_ntby_qty", 0)),
            "institution_net": int(item.get("orgn_ntby_qty", 0)),
            "individual_net": int(item.get("prsn_ntby_qty", 0)),
            "pension_net": int(item.get("pnsn_fnd_ntby_qty", 0))
        } for item in output2])

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
        Get stock daily OHLCV data.

        Args:
            ticker: Stock ticker (e.g., "005930")
            start_date: Start date (YYYYMMDD)
            end_date: End date (YYYYMMDD)

        Returns:
            DataFrame with OHLCV columns indexed by date
        """
        params = {
            "fid_cond_mrkt_div_code": "J",
            "fid_input_iscd": ticker,
            "fid_input_date_1": start_date,
            "fid_input_date_2": end_date,
            "fid_period_div_code": "D",
            "fid_org_adj_prc": "0"
        }

        data = self._request(
            "/uapi/domestic-stock/v1/quotations/inquire-daily-price",
            "FHKST01010400",
            params
        )

        if data.get("rt_cd") != "0":
            raise ValueError(f"API error: {data.get('msg1')}")

        output = data.get("output", [])

        df = pd.DataFrame([{
            "date": item.get("stck_bsop_date"),
            "open": int(item.get("stck_oprc", 0)),
            "high": int(item.get("stck_hgpr", 0)),
            "low": int(item.get("stck_lwpr", 0)),
            "close": int(item.get("stck_clpr", 0)),
            "volume": int(item.get("acml_vol", 0))
        } for item in output])

        if not df.empty:
            df["date"] = pd.to_datetime(df["date"])
            df.set_index("date", inplace=True)
            return df.sort_index()
        return df

    # ========================================
    # Index OHLCV (replaces pykrx get_index_ohlcv)
    # ========================================

    def get_index_ohlcv(
        self,
        index_code: str,
        start_date: str
    ) -> pd.DataFrame:
        """
        Get index daily OHLCV data.

        Args:
            index_code: Index code (e.g., "0001" for KOSPI, "1001" for KOSDAQ)
            start_date: Start date (YYYYMMDD)

        Returns:
            DataFrame with columns: date, open, high, low, close, volume
        """
        params = {
            "fid_period_div_code": "D",
            "fid_cond_mrkt_div_code": "U",
            "fid_input_iscd": index_code,
            "fid_input_date_1": start_date
        }

        data = self._request(
            "/uapi/domestic-stock/v1/quotations/inquire-index-daily-price",
            "FHPUP02120000",
            params
        )

        if data.get("rt_cd") != "0":
            raise ValueError(f"API error: {data.get('msg1')}")

        output2 = data.get("output2", [])

        df = pd.DataFrame([{
            "date": item.get("stck_bsop_date"),
            "open": float(item.get("bstp_nmix_oprc", 0)),
            "high": float(item.get("bstp_nmix_hgpr", 0)),
            "low": float(item.get("bstp_nmix_lwpr", 0)),
            "close": float(item.get("bstp_nmix_prpr", 0)),
            "volume": int(item.get("acml_vol", 0))
        } for item in output2])

        if not df.empty:
            df["date"] = pd.to_datetime(df["date"])
            df.set_index("date", inplace=True)
            return df.sort_index()
        return df

    # ========================================
    # Stock Info (replaces pykrx get_market_ticker_name)
    # ========================================

    def get_stock_info(self, ticker: str) -> Dict:
        """
        Get current stock info including name and price.

        Args:
            ticker: Stock ticker (e.g., "005930")

        Returns:
            Dict with keys: ticker, name, price, market_cap, etc.
        """
        params = {
            "fid_cond_mrkt_div_code": "J",
            "fid_input_iscd": ticker
        }

        data = self._request(
            "/uapi/domestic-stock/v1/quotations/inquire-price",
            "FHKST01010100",
            params
        )

        if data.get("rt_cd") != "0":
            raise ValueError(f"API error: {data.get('msg1')}")

        output = data.get("output", {})

        return {
            "ticker": ticker,
            "name": output.get("hts_kor_isnm", ""),
            "price": int(output.get("stck_prpr", 0)),
            "market_cap": int(output.get("hts_avls", 0)) * 100000000,  # 억원 → 원
            "volume": int(output.get("acml_vol", 0)),
            "per": float(output.get("per", 0) or 0),
            "pbr": float(output.get("pbr", 0) or 0)
        }

    def get_stock_name(self, ticker: str) -> str:
        """Get stock name by ticker."""
        try:
            info = self.get_stock_info(ticker)
            return info.get("name", "")
        except Exception as e:
            log.warning(f"Failed to get stock name for {ticker}: {e}")
            return ""

    # ========================================
    # Market Cap Ranking (replaces pykrx get_market_cap)
    # ========================================

    def get_market_cap_ranking(
        self,
        market: str = "0000",
        limit: int = 100
    ) -> pd.DataFrame:
        """
        Get market capitalization ranking.

        Args:
            market: Market code ("0000": all, "0001": KOSPI, "1001": KOSDAQ)
            limit: Maximum number of results

        Returns:
            DataFrame with columns: rank, ticker, name, price, market_cap
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

        data = self._request(
            "/uapi/domestic-stock/v1/ranking/market-cap",
            "FHPST01740000",
            params
        )

        if data.get("rt_cd") != "0":
            raise ValueError(f"API error: {data.get('msg1')}")

        output = data.get("output", [])[:limit]

        return pd.DataFrame([{
            "rank": int(item.get("data_rank", 0)),
            "ticker": item.get("stck_shrn_iscd"),
            "name": item.get("hts_kor_isnm"),
            "price": int(item.get("stck_prpr", 0)),
            "market_cap": int(item.get("stck_avls", 0)) * 100000000
        } for item in output])

    # ========================================
    # ETF List (replaces pykrx get_etf_ticker_list)
    # ========================================

    def get_etf_list(self) -> pd.DataFrame:
        """
        Get all ETF list.

        Uses stock master files and filters for ETF names.
        Korean ETFs have names containing 'ETF' suffix.

        Returns:
            DataFrame with columns: ticker, name
        """
        # Get all stocks from master files
        try:
            all_stocks = self.get_all_stocks()
        except Exception as e:
            log.error(f"Failed to get stock master: {e}")
            return pd.DataFrame(columns=["ticker", "name"])

        if all_stocks.empty:
            log.warning("Empty stock master data")
            return pd.DataFrame(columns=["ticker", "name"])

        # Filter for ETFs - Korean ETFs have names ending with 'ETF' or containing ETF patterns
        # Common patterns: "KODEX 200 ETF", "TIGER ETF", "KOSEF ETF", etc.
        etf_mask = all_stocks["name"].str.contains(r"ETF$|ETF\s|증권\s*상장지수|상장지수투자",
                                                    case=False, na=False, regex=True)
        etf_df = all_stocks[etf_mask][["ticker", "name"]].copy()

        log.info(f"Found {len(etf_df)} ETFs from stock master")
        return etf_df.reset_index(drop=True)

    # ========================================
    # Stock List (KOSPI/KOSDAQ master files)
    # ========================================

    def download_stock_master(self, market: str = "kospi") -> pd.DataFrame:
        """
        Download stock master list from KIS server.

        Args:
            market: "kospi" or "kosdaq"

        Returns:
            DataFrame with columns: ticker, name, market, listed_shares
            (listed_shares is in units of 1000)
        """
        if market.lower() == "kospi":
            url = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip"
        elif market.lower() == "kosdaq":
            url = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip"
        else:
            raise ValueError(f"Unknown market: {market}")

        response = requests.get(url, timeout=60)
        response.raise_for_status()

        with zipfile.ZipFile(io.BytesIO(response.content)) as zf:
            filename = zf.namelist()[0]
            with zf.open(filename) as f:
                content = f.read().decode("cp949")

        # Parse using fixed-width format based on official KIS spec
        # Part 2 field specs (last 228 bytes of each record)
        # Reference: https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kospi_code_mst.py
        field_specs = [
            2, 1, 4, 4, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 9, 5, 5, 1, 1, 1, 2, 1, 1, 1, 2, 2, 2, 3,
            1, 3, 12, 12, 8, 15, 21, 2, 7, 1, 1, 1, 1, 1, 9,
            9, 9, 5, 9, 8, 9, 3, 1, 1, 1
        ]
        # Index 50 is listed_shares (lstn_stcn), 15 bytes
        # Calculate offset: sum of specs[0:50]
        listed_shares_offset = sum(field_specs[:50])  # 108
        listed_shares_len = field_specs[50]  # 15

        stocks = []
        for line in content.strip().split("\n"):
            if len(line) < 228:
                continue

            # Part 1: ticker (9 bytes) + standard code (12 bytes) + name (variable)
            ticker = line[0:9].strip()

            # Skip if not a valid 6-digit ticker
            if not ticker or len(ticker) != 6 or not ticker.isdigit():
                continue

            # Name is between position 21 and the last 228 bytes
            part2_start = len(line) - 228
            name = line[21:part2_start].strip()

            # Part 2: Extract listed shares from fixed position
            part2 = line[part2_start:]
            listed_shares_str = part2[listed_shares_offset:listed_shares_offset + listed_shares_len].strip()

            try:
                # listed_shares is in units of 1000 (천)
                listed_shares = int(listed_shares_str) if listed_shares_str else 0
            except ValueError:
                listed_shares = 0

            stocks.append({
                "ticker": ticker,
                "name": name,
                "market": market.upper(),
                "listed_shares": listed_shares  # in units of 1000
            })

        return pd.DataFrame(stocks)

    def get_all_stocks(self) -> pd.DataFrame:
        """Get all KOSPI and KOSDAQ stocks."""
        kospi = self.download_stock_master("kospi")
        kosdaq = self.download_stock_master("kosdaq")
        return pd.concat([kospi, kosdaq], ignore_index=True)

    # ========================================
    # Market Ticker List (replaces pykrx get_market_ticker_list)
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
            raise ValueError(f"Unknown market: {market}")

        return df["ticker"].tolist()

    # ========================================
    # Listed Shares Cache (for market cap calculation)
    # ========================================

    _listed_shares_cache: Dict[str, int] = {}

    def get_listed_shares(self, ticker: str) -> int:
        """
        Get listed shares for a ticker (in units of 1000).

        Args:
            ticker: Stock ticker (e.g., "005930")

        Returns:
            Listed shares in units of 1000 (multiply by 1000 for actual shares)
        """
        # Check cache first
        if ticker in self._listed_shares_cache:
            return self._listed_shares_cache[ticker]

        # Load from master file if cache is empty
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
    # Index Components (replaces pykrx get_index_portfolio_deposit_file)
    # ========================================

    def get_index_components(self, market: str = "KOSPI", limit: int = 200) -> List[str]:
        """
        Get top N stocks by market cap as index components.

        This replaces pykrx.get_index_portfolio_deposit_file() with a better approach:
        - Gets the most liquid and impactful stocks
        - Automatically updates with market changes
        - No static list maintenance required

        Args:
            market: "KOSPI" or "KOSDAQ"
            limit: Number of stocks to return (default 200)

        Returns:
            List of ticker strings

        Raises:
            ValueError: If API returns error
        """
        market_code = "0001" if market.upper() == "KOSPI" else "1001"
        df = self.get_market_cap_ranking(market=market_code, limit=limit)
        return df["ticker"].tolist()

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
        """
        # Get OHLCV data
        df = self.get_stock_ohlcv(ticker, start_date, end_date)

        if df.empty:
            return df

        # Get listed shares (in units of 1000)
        listed_shares = self.get_listed_shares(ticker)

        if listed_shares > 0:
            # Calculate market cap: close * listed_shares * 1000
            df["market_cap"] = df["close"] * listed_shares * 1000
        else:
            df["market_cap"] = 0

        return df


# ========================================
# Global instance management
# ========================================

_client: Optional[KISAPIClient] = None


def init_kis_client(app_key: str, app_secret: str):
    """
    Initialize global KIS API client.

    Also registers the client with core.py for use by other modules.
    """
    global _client
    _client = KISAPIClient(app_key, app_secret)

    # Register with core.py for other modules to use
    try:
        from core import set_kis_client
        set_kis_client(_client)
        log.info("KIS API client initialized and registered with core")
    except ImportError:
        log.warning("Could not register KIS client with core module")
        log.info("KIS API client initialized")


def get_client() -> KISAPIClient:
    """Get global KIS API client instance."""
    if _client is None:
        raise RuntimeError("KIS client not initialized. Call init_kis_client first.")
    return _client


def is_client_initialized() -> bool:
    """Check if KIS client is initialized."""
    return _client is not None


# ========================================
# Convenience functions (for direct use without client instance)
# ========================================

def get_etf_holdings(etf_ticker: str) -> str:
    """
    Get ETF holdings as JSON string.

    Args:
        etf_ticker: ETF ticker (e.g., "069500")

    Returns:
        JSON string with holdings data or error
    """
    try:
        client = get_client()
        df = client.get_etf_holdings(etf_ticker)
        return df.to_json(orient="records", force_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)


def get_stock_ohlcv(ticker: str, start_date: str, end_date: str) -> str:
    """
    Get stock OHLCV data as JSON string.

    Args:
        ticker: Stock ticker
        start_date: Start date (YYYYMMDD)
        end_date: End date (YYYYMMDD)

    Returns:
        JSON string with OHLCV data or error
    """
    try:
        client = get_client()
        df = client.get_stock_ohlcv(ticker, start_date, end_date)
        df = df.reset_index()
        df["date"] = df["date"].dt.strftime("%Y-%m-%d")
        return df.to_json(orient="records", force_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)


def get_investor_trading_data(ticker: str, start_date: str) -> str:
    """
    Get investor trading data as JSON string.

    Args:
        ticker: Stock ticker
        start_date: Start date (YYYYMMDD)

    Returns:
        JSON string with investor trading data or error
    """
    try:
        client = get_client()
        df = client.get_investor_trading(ticker, start_date)
        return df.to_json(orient="records", force_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)
