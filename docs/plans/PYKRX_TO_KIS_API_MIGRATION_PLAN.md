# pykrx → KIS API Migration Plan

**Date:** 2025-01-05
**Status:** In Progress (Phase 4 Complete)
**Author:** Claude Code

## Migration Progress

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 1 | Research & Planning | ✅ Complete |
| Phase 2 | Create KIS API Client | ✅ Complete |
| Phase 3 | Migrate etfcollector.py | ✅ Complete |
| Phase 4 | Migrate stocks.py | ✅ Complete |
| Phase 5 | Kotlin Integration | ⬜ Pending |
| Phase 6 | Testing & Validation | ⬜ Pending |
| Phase 7 | Cleanup & Finalization | ⬜ Pending |

---

## Executive Summary

This document outlines the comprehensive plan for migrating the EtfMonitor Android app's data collection layer from **pykrx** to alternative data sources.

### Key Finding

> **✅ Full migration from pykrx is NOW FEASIBLE** using **Korea Investment Securities (KIS) Open API** as the single-source solution.

### Alternative Sources Evaluated

| Library/API | ETF List | ETF Holdings | Investor Trading | OHLCV | Verdict |
|-------------|----------|--------------|------------------|-------|---------|
| **yfinance** | ❌ | ⚠️ Top 10 only | ❌ | ✅ | Partial |
| **FinanceDataReader** | ✅ | ❌ | ❌ | ✅ | Partial |
| **Daum Finance** | ❌ | ❌ | ❌ | ⚠️ | Not viable |
| **KIS Open API** | ✅ | ✅ | ✅ | ✅ | **Complete** |

### Recommended Approach

**Option A (Recommended): KIS API Single-Source Architecture**
- Uses Korea Investment Securities Open API as the sole data source
- Requires: Korea Investment Securities brokerage account
- Provides: All data types currently used by the app
- Benefits: Single API, official support, reliable data

**Option B (Fallback): Hybrid Multi-Source Architecture**
- Uses yfinance for OHLCV + FinanceDataReader for lists + pykrx for holdings/investor data
- Requires: No account needed
- Limitation: Still depends on pykrx for critical features

---

## Table of Contents

1. [Current State Analysis](#1-current-state-analysis)
2. [Alternative Data Sources Assessment](#2-alternative-data-sources-assessment)
   - 2.1 [yfinance](#21-yfinance)
   - 2.2 [FinanceDataReader](#22-financedatareader)
   - 2.3 [Daum Finance](#23-daum-finance)
   - 2.4 [KIS Open API (Recommended)](#24-kis-open-api-recommended)
3. [Gap Analysis](#3-gap-analysis)
4. [Architecture Design](#4-architecture-design)
   - 4.1 [Option A: KIS API Single-Source](#41-option-a-kis-api-single-source)
   - 4.2 [Option B: Hybrid Multi-Source](#42-option-b-hybrid-multi-source)
5. [Implementation Phases (KIS API)](#5-implementation-phases-kis-api)
6. [File Change Summary](#6-file-change-summary)
7. [Risk Assessment](#7-risk-assessment)
8. [Testing Strategy](#8-testing-strategy)
9. [Rollback Plan](#9-rollback-plan)
10. [Appendix: KIS API Reference](#appendix-a-kis-api-reference)

---

## 1. Current State Analysis

### 1.1 pykrx Usage by Python File

| File | Functions Used | Data Type | Critical? |
|------|---------------|-----------|-----------|
| **etfcollector.py** | `get_etf_ticker_list`, `get_etf_ticker_name`, `get_etf_portfolio_deposit_file` | ETF list, ETF holdings | **CRITICAL** |
| **stocks.py** | `get_market_ticker_list`, `get_market_ohlcv`, `get_market_cap`, `get_market_trading_value_by_date`, `get_market_ticker_name` | Stock list, OHLCV, investor data | Mixed |
| **market.py** | `get_index_ohlcv`, `get_index_portfolio_deposit_file`, `get_market_ohlcv` | Index data, oscillator | Mixed |
| **trend_signal.py** | `get_market_ohlcv`, `get_market_cap` | Technical analysis | Migratable |
| **core.py** | Various utility functions | Common utilities | Partially migratable |
| **feargreed.py** | None (uses KRX API directly) | Fear & Greed Index | N/A |
| **deposit_scraper.py** | None (uses Naver scraping) | Market deposits | N/A |

### 1.2 pykrx Function Details

#### ETF Data (etfcollector.py)

```python
# Get all ETF tickers on market
stock.get_etf_ticker_list(date)  # Returns: List[str]

# Get ETF name by ticker
stock.get_etf_ticker_name(ticker)  # Returns: str

# Get ETF portfolio holdings with weights and amounts
stock.get_etf_portfolio_deposit_file(ticker, date)  # Returns: DataFrame
# Columns: "비중" (weight %), "금액" (amount in KRW)
```

#### Stock Data (stocks.py)

```python
# Get all stock tickers in a market
stock.get_market_ticker_list(date, market)  # market: "KOSPI" | "KOSDAQ"

# Get OHLCV price data
stock.get_market_ohlcv(start, end, ticker)  # Returns: DataFrame
# Columns: "시가", "고가", "저가", "종가", "거래량"

# Get market capitalization over time
stock.get_market_cap(start, end, ticker)  # Returns: DataFrame

# Get investor trading volumes by type (CRITICAL for app functionality)
stock.get_market_trading_value_by_date(start, end, ticker)  # Returns: DataFrame
# Columns: "외국인합계", "기관합계" (used for 5-day rolling sums)

# Get stock name
stock.get_market_ticker_name(ticker)  # Returns: str
```

#### Market Index Data (market.py)

```python
# Get index OHLCV data
stock.get_index_ohlcv(start, end, index_code)
# index_code: "1001" (KOSPI), "2001" (KOSDAQ)

# Get index component stocks (CRITICAL for oscillator calculation)
stock.get_index_portfolio_deposit_file(index_code)
# Returns: List of 200+ component tickers
```

---

## 2. Alternative Data Sources Assessment

### 2.1 yfinance

**GitHub:** https://github.com/ranaroussi/yfinance

#### Capability for Korean Markets

| Feature | yfinance Method | Korean Support | Notes |
|---------|----------------|----------------|-------|
| Stock OHLCV | `Ticker.history()` | ✅ YES | Format: `005930.KS` |
| Market Cap | `Ticker.info['marketCap']` | ⚠️ Partial | May return USD |
| Index OHLCV | `Ticker.history()` | ✅ YES | `^KS11`, `^KQ11` |
| ETF Top Holdings | `funds_data.top_holdings` | ⚠️ Limited | Top 10 only |
| Stock List | N/A | ❌ NO | **Not available** |
| ETF List | N/A | ❌ NO | **Not available** |
| Investor Trading | N/A | ❌ NO | **Not available** |
| Index Components | N/A | ❌ NO | **Not available** |

#### Korean Ticker Format

| Market | Format | Example |
|--------|--------|---------|
| KOSPI Stocks | `XXXXXX.KS` | `005930.KS` (삼성전자) |
| KOSDAQ Stocks | `XXXXXX.KQ` | `035720.KQ` (카카오) |
| KOSPI Index | `^KS11` | - |
| KOSDAQ Index | `^KQ11` | - |
| KOSPI 200 | `^KS200` | - |

#### Key Methods

```python
import yfinance as yf

# Single ticker
ticker = yf.Ticker("005930.KS")

# Historical data
hist = ticker.history(period="1mo")  # or start="2024-01-01", end="2024-12-31"
# Returns DataFrame with: Open, High, Low, Close, Volume, Dividends, Stock Splits

# Stock info (includes market cap)
info = ticker.info
market_cap = info.get('marketCap')

# Multiple tickers
data = yf.download(["005930.KS", "000660.KS"], start="2024-01-01", end="2024-12-31")

# ETF data (limited for Korean ETFs)
spy = yf.Ticker("SPY")
holdings = spy.funds_data.top_holdings  # Only top 10
```

**Verdict:** ⚠️ **Partial** - Can replace OHLCV/market cap, but cannot replace ETF holdings or investor trading data.

---

### 2.2 FinanceDataReader

**GitHub:** https://github.com/FinanceData/FinanceDataReader

#### Capability for Korean Markets

| Feature | Method | Support | Notes |
|---------|--------|---------|-------|
| Stock List | `StockListing('KRX')` | ✅ YES | All KRX stocks |
| ETF List | `StockListing('ETF/KR')` | ✅ YES | All Korean ETFs |
| Stock OHLCV | `DataReader(ticker, start, end)` | ✅ YES | Standard format |
| Index OHLCV | `DataReader('^KS11', start, end)` | ✅ YES | Same as yfinance |
| Index Components | `SnapDataReader('KRX/INDEX/STOCK/1001')` | ✅ YES | KOSPI components |
| ETF Holdings | N/A | ❌ NO | **Not available** |
| Investor Trading | N/A | ❌ NO | **Not available** |

#### Key Methods

```python
import FinanceDataReader as fdr

# Stock listing (all KRX stocks)
stocks = fdr.StockListing('KRX')  # Returns DataFrame with Symbol, Name, Market

# ETF listing
etfs = fdr.StockListing('ETF/KR')  # All Korean ETFs

# Stock OHLCV
df = fdr.DataReader('005930', '2024-01-01', '2024-12-31')

# Index OHLCV
kospi = fdr.DataReader('KS11', '2024-01-01', '2024-12-31')

# Index components
components = fdr.SnapDataReader('KRX/INDEX/STOCK/1001')  # KOSPI components
```

**Verdict:** ⚠️ **Partial** - Can provide stock/ETF lists and index components, but cannot replace ETF holdings or investor trading data.

---

### 2.3 Daum Finance

**Website:** https://finance.daum.net/domestic

#### Analysis Result

| Feature | Availability | Notes |
|---------|-------------|-------|
| ETF Holdings | ❌ NO | Not available on public pages |
| Investor Trading | ⚠️ Limited | Data shown but provided by third-party (AXG) |
| OHLCV | ✅ YES | Available via scraping |
| Public API | ❌ NO | No documented public API |

**Key Findings:**
- The "외국인/기관 매매동향" (Foreign/Institutional trading) data at `finance.daum.net/domestic/influential_investors` is provided by **AXG**, not Daum's own data
- No official public API documentation found
- Web scraping would be unreliable for production use

**Verdict:** ❌ **Not Viable** - No public API, data from third-party sources, not suitable for production.

---

### 2.4 KIS Open API (Recommended)

**Website:** https://apiportal.koreainvestment.com/intro
**GitHub Examples:** https://github.com/koreainvestment/open-trading-api

#### Requirements

> ⚠️ **Korea Investment Securities (한국투자증권) brokerage account required**

To use the KIS Open API:
1. Open a brokerage account at Korea Investment Securities
2. Register for API access at https://apiportal.koreainvestment.com
3. Obtain APP_KEY and APP_SECRET credentials
4. Use OAuth token authentication

#### Capability for Korean Markets

| Feature | API Endpoint | TR ID | Support |
|---------|-------------|-------|---------|
| ETF Holdings (비중/금액) | `/uapi/etfetn/v1/quotations/inquire-component-stock-price` | FHKST121600C0 | ✅ **YES** |
| Investor Trading (일별) | `/uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily` | FHPTJ04160001 | ✅ **YES** |
| Stock OHLCV | `/uapi/domestic-stock/v1/quotations/inquire-daily-price` | FHKST01010400 | ✅ YES |
| Stock List | 종목정보 마스터파일 다운로드 | - | ✅ YES |
| Index OHLCV | `/uapi/domestic-stock/v1/quotations/inquire-index-daily-price` | - | ✅ YES |
| Market Cap | `/uapi/domestic-stock/v1/quotations/inquire-price` | - | ✅ YES |

#### Critical APIs for Migration

**1. ETF 구성종목시세 (ETF Component Stock Price)**

```python
# Endpoint: /uapi/etfetn/v1/quotations/inquire-component-stock-price
# TR ID: FHKST121600C0

def inquire_component_stock_price(
    fid_cond_mrkt_div_code: str,  # "J": 주식/ETF/ETN
    fid_input_iscd: str,          # ETF ticker (e.g., "069500" for KODEX 200)
    fid_cond_scr_div_code: str    # "11216"
) -> Tuple[pd.DataFrame, pd.DataFrame]:
    """
    ETF 구성종목시세 - 비중(weight), 금액(amount) 포함

    Returns:
        Tuple of (ETF basic info DataFrame, ETF component stocks DataFrame)

    Component DataFrame columns:
        - 종목코드 (Stock ticker)
        - 종목명 (Stock name)
        - 비중 (Weight %)
        - 평가금액 (Evaluation amount)
        - 보유수량 (Holding quantity)
        - 전일대비 (Change from previous day)
    """
```

**Replaces pykrx:** `stock.get_etf_portfolio_deposit_file(ticker, date)`

**2. 종목별 투자자매매동향 일별 (Investor Trade by Stock Daily)**

```python
# Endpoint: /uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily
# TR ID: FHPTJ04160001

def investor_trade_by_stock_daily(
    fid_cond_mrkt_div_code: str,  # "J": 전체, "S": 코스피, "Q": 코스닥
    fid_input_iscd: str,          # Stock ticker (e.g., "005930")
    fid_input_date_1: str,        # Start date (YYYYMMDD)
    fid_org_adj_prc: str,         # "" (empty)
    fid_etc_cls_code: str         # "" (empty)
) -> Tuple[pd.DataFrame, pd.DataFrame]:
    """
    종목별 투자자 매매동향 일별 - 외국인/기관 순매수 데이터

    Returns:
        Tuple of (summary DataFrame, daily details DataFrame)

    Daily DataFrame columns:
        - 일자 (Date)
        - 개인순매수 (Individual net buy)
        - 외국인순매수 (Foreign net buy)
        - 기관순매수 (Institutional net buy)
        - 금융투자순매수 (Financial investment net buy)
        - 보험순매수 (Insurance net buy)
        - 투신순매수 (Investment trust net buy)
        - 은행순매수 (Bank net buy)
        - 기타금융순매수 (Other financial net buy)
        - 연기금순매수 (Pension fund net buy)
    """
```

**Replaces pykrx:** `stock.get_market_trading_value_by_date(start, end, ticker)`

#### Authentication Flow

```python
import requests

# Step 1: Get OAuth access token
def get_access_token(app_key: str, app_secret: str) -> str:
    url = "https://openapi.koreainvestment.com:9443/oauth2/tokenP"

    headers = {"content-type": "application/json"}
    body = {
        "grant_type": "client_credentials",
        "appkey": app_key,
        "appsecret": app_secret
    }

    response = requests.post(url, headers=headers, json=body)
    return response.json()["access_token"]

# Step 2: Use token in API requests
def call_kis_api(endpoint: str, params: dict, token: str, app_key: str) -> dict:
    url = f"https://openapi.koreainvestment.com:9443{endpoint}"

    headers = {
        "content-type": "application/json; charset=utf-8",
        "authorization": f"Bearer {token}",
        "appkey": app_key,
        "appsecret": app_secret,
        "tr_id": params.get("tr_id")
    }

    response = requests.get(url, headers=headers, params=params)
    return response.json()
```

**Verdict:** ✅ **Complete Solution** - Can fully replace all pykrx functionality. Requires brokerage account but provides official, reliable data.

---

## 3. Gap Analysis

### 3.1 pykrx Functions and Alternatives

| Feature | pykrx Function | yfinance | FDR | KIS API |
|---------|---------------|----------|-----|---------|
| **ETF List** | `get_etf_ticker_list()` | ❌ | ✅ | ✅ |
| **ETF Holdings** | `get_etf_portfolio_deposit_file()` | ⚠️ Top 10 | ❌ | ✅ `inquire-component-stock-price` |
| **Investor Trading** | `get_market_trading_value_by_date()` | ❌ | ❌ | ✅ `investor-trade-by-stock-daily` |
| **Index Components** | `get_index_portfolio_deposit_file()` | ❌ | ✅ | ✅ |
| **Stock List** | `get_market_ticker_list()` | ❌ | ✅ | ✅ |
| **Stock OHLCV** | `get_market_ohlcv()` | ✅ | ✅ | ✅ |
| **Index OHLCV** | `get_index_ohlcv()` | ✅ | ✅ | ✅ |
| **Market Cap** | `get_market_cap()` | ⚠️ USD | ✅ | ✅ |
| **Stock Name** | `get_market_ticker_name()` | ⚠️ | ✅ | ✅ |

### 3.2 Critical Features Comparison

| Critical Feature | Can yfinance Replace? | Can FDR Replace? | Can KIS Replace? |
|-----------------|----------------------|------------------|------------------|
| ETF Holdings (비중/금액) | ❌ No (Top 10 only) | ❌ No | ✅ **Yes** |
| Investor Trading (외국인/기관) | ❌ No | ❌ No | ✅ **Yes** |

### 3.3 Migration Verdict

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         MIGRATION VERDICT                                  │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  Option A: KIS API (Recommended)                                          │
│  ────────────────────────────────                                         │
│  Full Migration:     ✅ POSSIBLE                                          │
│  Single Source:      ✅ YES (KIS API only)                                │
│  Requirement:        ⚠️ Korea Investment Securities account               │
│  Reliability:        ✅ Official API with support                         │
│                                                                           │
│  Option B: Hybrid Multi-Source                                            │
│  ─────────────────────────────                                            │
│  Full Migration:     ❌ NOT POSSIBLE                                      │
│  Sources:            yfinance (OHLCV) + FDR (lists) + pykrx (holdings)   │
│  Requirement:        None                                                 │
│  Reliability:        ⚠️ Multiple failure points                          │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Architecture Design

### 4.1 Option A: KIS API Single-Source (Recommended)

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         KIS API Architecture                                │
│                    (Single-Source, Official API)                           │
└────────────────────────────────────┬───────────────────────────────────────┘
                                     │
                    ┌────────────────▼────────────────┐
                    │        KIS API Client           │
                    │   (OAuth2 Token Management)     │
                    └────────────────┬────────────────┘
                                     │
     ┌───────────────────────────────┼───────────────────────────────┐
     │                               │                               │
┌────▼─────┐                   ┌─────▼────┐                   ┌──────▼─────┐
│  ETF     │                   │ Investor │                   │   Stock    │
│ Holdings │                   │ Trading  │                   │   Data     │
└────┬─────┘                   └─────┬────┘                   └──────┬─────┘
     │                               │                               │
     │ inquire-component-           │ investor-trade-by-            │ inquire-daily-
     │ stock-price                  │ stock-daily                   │ price, etc.
     │ (FHKST121600C0)              │ (FHPTJ04160001)              │
     │                               │                               │
     │ • ETF 구성종목                │ • 외국인 순매수              │ • OHLCV
     │ • 비중 (weight %)            │ • 기관 순매수                │ • 시가총액
     │ • 금액 (amount)              │ • 개인 순매수                │ • 거래량
     └───────────────────────────────┴───────────────────────────────┘
```

**Benefits:**
- ✅ Single API source - simplified error handling
- ✅ Official API with documentation and support
- ✅ Reliable data directly from Korea Investment Securities
- ✅ Complete coverage of all required data types
- ✅ No web scraping dependencies

**Requirements:**
- ⚠️ Korea Investment Securities brokerage account required
- APP_KEY and APP_SECRET from API portal
- OAuth token refresh management

### 4.2 Option B: Hybrid Multi-Source (Alternative)

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                  DataSourceManager                      │
                    │  (Priority-based fallback with caching)                 │
                    └────────────────────────┬────────────────────────────────┘
                                             │
           ┌─────────────────────────────────┼─────────────────────────────────┐
           │                                 │                                 │
    ┌──────▼──────┐                   ┌──────▼──────┐                   ┌──────▼──────┐
    │  yfinance   │                   │   pykrx    │                   │    FDR      │
    │             │                   │            │                   │             │
    └──────┬──────┘                   └──────┬─────┘                   └──────┬──────┘
           │                                  │                                │
           │  • Stock OHLCV                   │  • ETF holdings               │  • ETF list
           │  • Market cap                    │  • Investor trading           │  • Stock list
           │  • Index OHLCV                   │  • Index components           │  • Index components
           └──────────────────────────────────┴────────────────────────────────┘
```

**Limitations:**
- ❌ Still requires pykrx for ETF holdings and investor trading
- ❌ Multiple points of failure
- ❌ More complex error handling

### 4.3 Data Source Assignment Comparison

| Feature | Option A (KIS) | Option B (Hybrid) |
|---------|---------------|-------------------|
| Stock OHLCV | KIS API | yfinance → pykrx |
| Market Cap | KIS API | yfinance → pykrx |
| Index OHLCV | KIS API | yfinance → pykrx |
| ETF List | KIS 종목파일 | FDR → pykrx |
| ETF Holdings | **KIS API** ✅ | pykrx (no alternative) |
| Stock List | KIS 종목파일 | FDR → pykrx |
| Investor Trading | **KIS API** ✅ | pykrx (no alternative) |
| Index Components | KIS API | FDR → pykrx |

### 4.4 KIS API Client Implementation

```python
"""
KIS API Client for Korean stock market data.
Requires Korea Investment Securities account.
"""
import requests
import json
from datetime import datetime, timedelta
from typing import Optional, Dict, Tuple
import pandas as pd
from logger import log

class KISAPIClient:
    """Korea Investment Securities Open API Client."""

    BASE_URL = "https://openapi.koreainvestment.com:9443"
    TOKEN_EXPIRY_HOURS = 23  # Token valid for ~24 hours

    def __init__(self, app_key: str, app_secret: str):
        self.app_key = app_key
        self.app_secret = app_secret
        self._token: Optional[str] = None
        self._token_expiry: Optional[datetime] = None

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

        response = requests.post(url, headers=headers, json=body)
        response.raise_for_status()

        data = response.json()
        self._token = data["access_token"]
        self._token_expiry = datetime.now() + timedelta(hours=self.TOKEN_EXPIRY_HOURS)

        log.info("KIS API token refreshed")
        return self._token

    def _request(self, endpoint: str, tr_id: str, params: Dict) -> Dict:
        """Make authenticated API request."""
        token = self._get_token()
        url = f"{self.BASE_URL}{endpoint}"

        headers = {
            "content-type": "application/json; charset=utf-8",
            "authorization": f"Bearer {token}",
            "appkey": self.app_key,
            "appsecret": self.app_secret,
            "tr_id": tr_id
        }

        response = requests.get(url, headers=headers, params=params)
        response.raise_for_status()
        return response.json()

    def get_etf_holdings(self, etf_ticker: str) -> pd.DataFrame:
        """
        Get ETF component stocks with weights and amounts.
        Replaces: pykrx stock.get_etf_portfolio_deposit_file()

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

    def get_investor_trading(
        self,
        ticker: str,
        start_date: str,
        end_date: Optional[str] = None
    ) -> pd.DataFrame:
        """
        Get daily investor trading data (foreign/institutional).
        Replaces: pykrx stock.get_market_trading_value_by_date()

        Args:
            ticker: Stock ticker (e.g., "005930")
            start_date: Start date (YYYYMMDD)
            end_date: End date (YYYYMMDD), defaults to today

        Returns:
            DataFrame with columns: date, foreign_net, institution_net, individual_net
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
```

---

## 5. Implementation Phases (KIS API)

> **Note:** This section describes the recommended KIS API implementation. For the hybrid approach (Option B), see the legacy phase documentation in Appendix B.

### Phase 1: Prerequisites & Setup

**Objective:** Obtain KIS API access and set up credentials

---

#### 1.1 한국투자증권 계좌 개설 (Account Setup)

> **소요 시간:** 약 10분 (비대면 개설)
> **필요 조건:** 만 19세 이상, 본인 명의 휴대폰, 신분증 (주민등록증 또는 운전면허증)

##### 방법 1: 한국투자증권 앱으로 직접 개설 (권장)

1. **앱 다운로드**
   - Google Play Store에서 "한국투자" 앱 설치
   - 또는 https://www.truefriend.com 접속

2. **비대면 계좌개설 시작**
   - 앱 실행 → "계좌개설" 메뉴 선택
   - 개설 가능 시간: 24시간 (23:00~00:30 제외)

3. **본인 인증**
   - 신분증 촬영 (주민등록증 또는 운전면허증)
   - 휴대폰 본인 인증
   - 영상통화 또는 ARS 인증

4. **계좌 유형 선택**
   - **위탁(종합계좌)**: 주식/ETF 거래용 → **이 계좌 선택**
   - 금융상품(CMA): 현금 관리용
   - 연금저축: 연금용

5. **개설 완료**
   - 계좌번호 발급 (8자리-2자리 형식: 12345678-01)
   - 계좌 비밀번호 설정

##### 방법 2: 카카오뱅크 제휴 개설 (간편)

1. **카카오뱅크 앱 실행**
2. **메뉴 → 제휴 → 증권사 주식계좌**
3. **한국투자증권 주식계좌 → 개설하기**
4. **카카오뱅크 계좌 연결**
5. **동의 및 인증 후 개설 완료**

---

#### 1.2 KIS Developers API 서비스 신청

> **소요 시간:** 약 5분
> **필요 조건:** 한국투자증권 계좌, 한국투자증권 홈페이지 로그인 ID

##### Step 1: KIS Developers 접속

**경로 A (홈페이지)**
```
한국투자증권 홈페이지 (https://www.truefriend.com)
→ 트레이딩
→ Open API
→ KIS Developers
→ KIS Developers 서비스 신청하기
```

**경로 B (직접 접속)**
```
https://apiportal.koreainvestment.com
→ 오른쪽 상단 "API신청" 클릭
→ 로그인 (본인인증)
```

##### Step 2: API 서비스 신청

1. **로그인** (공동인증서 또는 간편인증)
2. **계좌 선택**
   - 실전투자: 실제 계좌번호 선택
   - 모의투자: 모의투자 계좌번호 입력 (별도 신청 필요)
3. **본인 인증** (문자 인증번호)
4. **신청 완료**

##### Step 3: APP KEY / APP SECRET 확인

1. **신청정보 화면**에서 확인
2. **신청현황 테이블**에서 해당 계좌의 키 확인
3. **복사 방법**: 클립보드에 복사 후 사용 (화면에 직접 노출되지 않음)

```
┌─────────────────────────────────────────────────────────────────┐
│                    KIS Developers 신청현황                       │
├──────────────┬─────────────────┬─────────────────┬──────────────┤
│ 계좌번호      │ APP KEY         │ APP SECRET      │ 상태         │
├──────────────┼─────────────────┼─────────────────┼──────────────┤
│ 12345678-01  │ PSxxx... [복사] │ xxx... [복사]   │ 사용중       │
└──────────────┴─────────────────┴─────────────────┴──────────────┘
```

##### ⚠️ 보안 주의사항

```
┌─────────────────────────────────────────────────────────────────┐
│ 🔐 APP KEY / APP SECRET 보안 관리                               │
├─────────────────────────────────────────────────────────────────┤
│ • 절대 타인에게 공유하지 마세요                                  │
│ • 소스코드에 하드코딩하지 마세요                                 │
│ • 유출 시 즉시 홈페이지에서 재발급 하세요                        │
│ • Git 저장소에 커밋하지 마세요 (.gitignore 활용)                │
└─────────────────────────────────────────────────────────────────┘
```

##### API 이용 기간

| 항목 | 내용 |
|-----|------|
| **이용 기간** | 신청일로부터 **1년** |
| **갱신 시점** | 만료 30일 전부터 가능 |
| **갱신 시** | APP KEY, APP SECRET 재발급 |
| **토큰 발급** | 1분당 1회 제한 |

---

#### 1.3 실전투자 vs 모의투자 선택

| 구분 | 실전투자 | 모의투자 |
|-----|---------|---------|
| **용도** | 실제 데이터 조회/거래 | 개발/테스트용 |
| **API URL** | `openapi.koreainvestment.com:9443` | `openapivts.koreainvestment.com:29443` |
| **데이터** | 실시간 실제 데이터 | 가상 데이터 |
| **거래** | 실제 매매 가능 | 가상 매매 |
| **권장** | **프로덕션용** | 개발 초기 테스트용 |

> **EtfMonitor 앱에서는 실전투자 API를 사용합니다** (데이터 조회만 수행, 거래 기능 없음)

---

#### 1.4 OAuth 접속 토큰 발급

##### 토큰 발급 흐름

```
┌─────────────┐    APP KEY + APP SECRET    ┌─────────────────┐
│   Client    │ ─────────────────────────► │  KIS OAuth API  │
│  (Python)   │                            │  /oauth2/tokenP │
└─────────────┘                            └────────┬────────┘
       ▲                                            │
       │            ACCESS TOKEN (24시간 유효)       │
       └────────────────────────────────────────────┘
```

##### API 엔드포인트

| 환경 | URL |
|-----|-----|
| **실전투자** | `https://openapi.koreainvestment.com:9443/oauth2/tokenP` |
| **모의투자** | `https://openapivts.koreainvestment.com:29443/oauth2/tokenP` |

##### 토큰 발급 요청

```python
import requests
import json

def get_access_token(app_key: str, app_secret: str) -> str:
    """OAuth 접속 토큰 발급"""

    url = "https://openapi.koreainvestment.com:9443/oauth2/tokenP"

    headers = {
        "content-type": "application/json"
    }

    body = {
        "grant_type": "client_credentials",
        "appkey": app_key,
        "appsecret": app_secret
    }

    response = requests.post(url, headers=headers, json=body)
    response.raise_for_status()

    data = response.json()
    access_token = data["access_token"]

    # 토큰 유효 시간: 약 24시간 (86400초)
    # expires_in: 86400

    return access_token
```

##### 토큰 응답 예시

```json
{
    "access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9...",
    "token_type": "Bearer",
    "expires_in": 86400,
    "access_token_token_expired": "2025-01-06 15:30:00"
}
```

##### ⚠️ 토큰 관리 주의사항

| 항목 | 제한 | 권장 사항 |
|-----|------|----------|
| **발급 빈도** | 1분당 1회 | 하루에 1번만 발급 |
| **유효 기간** | 24시간 | 만료 5분 전 갱신 |
| **재발급** | 잦은 발급 시 이용 제한 | 기존 토큰 재사용 |
| **저장** | 메모리 또는 안전한 저장소 | 앱 재시작 시 재발급 |

---

#### 1.5 Android 앱 설정 (Settings Screen)

EtfMonitor 앱에서 KIS API 자격 증명을 저장하기 위한 설정 화면 구성:

```kotlin
// Settings screen에 추가할 UI 컴포넌트
@Composable
fun KISApiSettings(
    appKey: String,
    appSecret: String,
    onCredentialsChange: (String, String) -> Unit
) {
    Column {
        Text(
            text = "한국투자증권 Open API",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = appKey,
            onValueChange = { onCredentialsChange(it, appSecret) },
            label = { Text("APP KEY") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = appSecret,
            onValueChange = { onCredentialsChange(appKey, it) },
            label = { Text("APP SECRET") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "* KIS Developers에서 발급받은 키를 입력하세요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

##### 자격 증명 저장 (암호화)

기존 Claude/Gemini API 키 저장 패턴과 동일하게 **AES256-GCM 암호화** 사용:

```kotlin
// ApiKeyProvider 인터페이스에 추가
interface ApiKeyProvider {
    // 기존
    fun getClaudeApiKey(): String?
    fun getGeminiApiKey(): String?

    // KIS API 추가
    fun getKisAppKey(): String?
    fun getKisAppSecret(): String?
    fun setKisAppKey(appKey: String)
    fun setKisAppSecret(appSecret: String)
    fun isKisApiConfigured(): Boolean
}
```

#### 1.6 Update build.gradle.kts

**File:** `app/build.gradle.kts`

```kotlin
chaquopy {
    defaultConfig {
        pip {
            // Remove pykrx after migration complete
            // install("pykrx")
            install("pandas")
            install("numpy")
            install("beautifulsoup4")
            install("requests")
            install("scikit-learn")
            install("xgboost")
            install("lightgbm")
        }
    }
}
```

---

### Phase 2: KIS API Client Module

**Objective:** Create Python module for KIS API integration

#### 2.1 Create KIS API Client

**New File:** `app/src/main/python/kis_client.py`

```python
"""
Korea Investment Securities Open API Client.
Replaces pykrx for all Korean market data needs.

Reference: https://github.com/koreainvestment/open-trading-api
"""
import requests
import json
import time
import zipfile
import io
from datetime import datetime, timedelta
from typing import Optional, Dict, List, Tuple
import pandas as pd
from logger import log

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
        """Make authenticated API request with rate limiting and retry."""
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
        """Get stock daily OHLCV data."""
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

        df["date"] = pd.to_datetime(df["date"])
        df.set_index("date", inplace=True)
        return df.sort_index()

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
            "per": float(output.get("per", 0)),
            "pbr": float(output.get("pbr", 0))
        }

    def get_stock_name(self, ticker: str) -> str:
        """Get stock name by ticker."""
        info = self.get_stock_info(ticker)
        return info.get("name", "")

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
    # Stock List (KOSPI/KOSDAQ master files)
    # ========================================

    def download_stock_master(self, market: str = "kospi") -> pd.DataFrame:
        """
        Download stock master list from KIS server.

        Args:
            market: "kospi" or "kosdaq"

        Returns:
            DataFrame with columns: ticker, name, market
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

        # Parse fixed-width format
        stocks = []
        for line in content.strip().split("\n"):
            if len(line) >= 20:
                ticker = line[0:9].strip()
                name = line[21:].split("|")[0].strip() if "|" in line else line[21:].strip()
                if ticker and len(ticker) == 6 and ticker.isdigit():
                    stocks.append({
                        "ticker": ticker,
                        "name": name,
                        "market": market.upper()
                    })

        return pd.DataFrame(stocks)

    def get_all_stocks(self) -> pd.DataFrame:
        """Get all KOSPI and KOSDAQ stocks."""
        kospi = self.download_stock_master("kospi")
        kosdaq = self.download_stock_master("kosdaq")
        return pd.concat([kospi, kosdaq], ignore_index=True)

# ========================================
# Global instance management
# ========================================

_client: Optional[KISAPIClient] = None

def init_kis_client(app_key: str, app_secret: str):
    """Initialize global KIS API client."""
    global _client
    _client = KISAPIClient(app_key, app_secret)
    log.info("KIS API client initialized")

def get_client() -> KISAPIClient:
    """Get global KIS API client instance."""
    if _client is None:
        raise RuntimeError("KIS client not initialized. Call init_kis_client first.")
    return _client
```

---

### Phase 3: Migrate etfcollector.py

**Objective:** Replace pykrx ETF functions with KIS API

#### 3.1 Modify etfcollector.py

**File:** `app/src/main/python/etfcollector.py`

```python
# BEFORE (pykrx)
from pykrx import stock

def get_etf_holdings(ticker: str, date: str) -> str:
    df = stock.get_etf_portfolio_deposit_file(ticker, date)
    # ...

# AFTER (KIS API)
from kis_client import get_client

def get_etf_holdings(ticker: str, date: str = None) -> str:
    """Get ETF holdings via KIS API."""
    try:
        client = get_client()
        df = client.get_etf_holdings(ticker)

        # Convert to existing JSON format for compatibility
        holdings = []
        for _, row in df.iterrows():
            holdings.append({
                "ticker": row["ticker"],
                "name": row["name"],
                "weight": row["weight"],
                "amount": row["amount"]
            })

        return json.dumps(holdings, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})
```

---

### Phase 4: Migrate stocks.py

**Objective:** Replace pykrx investor trading functions with KIS API

#### 4.1 Modify stocks.py

**File:** `app/src/main/python/stocks.py`

```python
# BEFORE (pykrx)
from pykrx import stock

def get_stock_data(ticker: str, days: int = 30) -> str:
    df = stock.get_market_trading_value_by_date(start, end, ticker)
    # Calculate 5-day rolling sums for foreign/institution

# AFTER (KIS API)
from kis_client import get_client

def get_stock_data(ticker: str, days: int = 30) -> str:
    """Get stock data including investor trading via KIS API."""
    try:
        client = get_client()

        # Get investor trading data
        start_date = (datetime.now() - timedelta(days=days)).strftime("%Y%m%d")
        df = client.get_investor_trading(ticker, start_date)

        # Calculate 5-day rolling sums (same logic as before)
        df["foreign_5d"] = df["foreign_net"].rolling(5).sum()
        df["institution_5d"] = df["institution_net"].rolling(5).sum()

        # Convert to JSON
        result = {
            "ticker": ticker,
            "dates": df["date"].tolist(),
            "foreign_5d": df["foreign_5d"].tolist(),
            "institution_5d": df["institution_5d"].tolist()
        }

        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})
```

---

### Phase 5: Kotlin Integration

**Objective:** Add KIS API credential management to Android app

#### 5.1 Add Settings UI

**File:** `app/src/main/java/com/etfmonitor/feature/settings/presentation/SettingsViewModel.kt`

```kotlin
// Add KIS API key StateFlows (similar to Claude/Gemini pattern)
private val _kisAppKey = MutableStateFlow("")
val kisAppKey: StateFlow<String> = _kisAppKey.asStateFlow()

private val _kisAppSecret = MutableStateFlow("")
val kisAppSecret: StateFlow<String> = _kisAppSecret.asStateFlow()

fun updateKisCredentials(appKey: String, appSecret: String) {
    viewModelScope.launch {
        apiKeyProvider.setKisAppKey(appKey)
        apiKeyProvider.setKisAppSecret(appSecret)
        _kisAppKey.value = appKey
        _kisAppSecret.value = appSecret
    }
}
```

#### 5.2 Initialize KIS Client in Python Bridge

**File:** `app/src/main/java/com/etfmonitor/core/network/python/PyKrxClient.kt`

```kotlin
// Add KIS initialization
suspend fun initializeKisClient(appKey: String, appSecret: String) = withContext(Dispatchers.IO) {
    try {
        val kisModule = python.getModule("kis_client")
        kisModule.callAttr("init_kis_client", appKey, appSecret)
        Log.i(TAG, "KIS API client initialized")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize KIS client", e)
    }
}
```

---

### Phase 6: Testing & Validation

**Objective:** Ensure KIS API data matches pykrx data quality

#### 6.1 Unit Tests

**New File:** `app/src/test/python/test_kis_client.py`

```python
import pytest
from kis_client import KISAPIClient

class TestKISClient:
    @pytest.fixture
    def client(self):
        # Use test credentials or mock
        return KISAPIClient("test_app_key", "test_app_secret")

    def test_get_etf_holdings(self, client):
        df = client.get_etf_holdings("069500")  # KODEX 200
        assert not df.empty
        assert "ticker" in df.columns
        assert "weight" in df.columns
        assert "amount" in df.columns

    def test_get_investor_trading(self, client):
        df = client.get_investor_trading("005930", "20240101")  # 삼성전자
        assert not df.empty
        assert "foreign_net" in df.columns
        assert "institution_net" in df.columns

    def test_get_stock_ohlcv(self, client):
        df = client.get_stock_ohlcv("005930", "20240101", "20240131")
        assert not df.empty
        assert "close" in df.columns
```

#### 6.2 Data Comparison Tests

```python
"""Compare KIS API data with pykrx to validate accuracy."""
from pykrx import stock as pykrx
from kis_client import get_client

def compare_etf_holdings(etf_ticker: str):
    """Compare ETF holdings from both sources."""
    # Get pykrx data
    pykrx_df = pykrx.get_etf_portfolio_deposit_file(etf_ticker)

    # Get KIS data
    kis_df = get_client().get_etf_holdings(etf_ticker)

    # Compare top holdings
    assert len(kis_df) >= len(pykrx_df) * 0.9  # At least 90% coverage

    # Compare weights (allow 1% variance)
    for ticker in pykrx_df.index[:10]:  # Top 10
        pykrx_weight = pykrx_df.loc[ticker, "비중"]
        kis_row = kis_df[kis_df["ticker"] == ticker]
        if not kis_row.empty:
            kis_weight = kis_row["weight"].values[0]
            assert abs(pykrx_weight - kis_weight) < 1.0
```

---

### Phase 7: Cleanup & Finalization

**Objective:** Remove pykrx dependency after successful migration

#### 7.1 Remove pykrx from Dependencies

**File:** `app/build.gradle.kts`

```kotlin
chaquopy {
    defaultConfig {
        pip {
            // REMOVED: install("pykrx")
            install("pandas")
            install("numpy")
            install("requests")
            // ...
        }
    }
}
```

#### 7.2 Remove pykrx Imports

Search and remove all `from pykrx import stock` statements from:
- `etfcollector.py`
- `stocks.py`
- `market.py`
- `trend_signal.py`
- `core.py`

#### 7.3 Update Documentation

- Update CLAUDE.md to reflect KIS API usage
- Update Python script docstrings
- Add KIS API setup instructions to README

---

## 6. File Change Summary

### Option A: KIS API Migration (Recommended)

#### New Files (2 files)

| File | Purpose | Lines (est.) |
|------|---------|--------------|
| `app/src/main/python/kis_client.py` | KIS Open API client | ~300 |
| `app/src/test/python/test_kis_client.py` | Unit tests for KIS client | ~150 |

#### Modified Files (7 files)

| File | Changes | Complexity |
|------|---------|------------|
| `app/build.gradle.kts` | Remove `install("pykrx")` | Low |
| `app/src/main/python/etfcollector.py` | Replace pykrx with KIS API | Medium |
| `app/src/main/python/stocks.py` | Replace pykrx with KIS API | Medium |
| `app/src/main/python/market.py` | Replace pykrx with KIS API | Medium |
| `app/src/main/python/trend_signal.py` | Replace pykrx with KIS API | Medium |
| `app/src/main/python/core.py` | Replace pykrx with KIS API | Low |
| `SettingsViewModel.kt` | Add KIS credential management | Low |

#### Unchanged Files

| File | Reason |
|------|--------|
| `app/src/main/python/feargreed.py` | Already uses direct KRX API |
| `app/src/main/python/deposit_scraper.py` | Already uses Naver scraping |
| `app/src/main/python/stock_predictor_v2.py` | Uses processed data from other modules |

---

### Option B: Hybrid Migration (Alternative)

#### New Files (5 files)

| File | Purpose | Lines (est.) |
|------|---------|--------------|
| `app/src/main/python/yf_client.py` | yfinance wrapper for Korean stocks | ~200 |
| `app/src/main/python/fdr_client.py` | FinanceDataReader wrapper | ~150 |
| `app/src/main/python/data_source_manager.py` | Multi-source fallback manager | ~250 |
| `app/src/test/python/test_yf_client.py` | Unit tests for yfinance wrapper | ~150 |
| `app/src/test/python/test_data_source_manager.py` | Unit tests for fallback manager | ~150 |

#### Modified Files (6 files)

| File | Changes | Complexity |
|------|---------|------------|
| `app/build.gradle.kts` | Add `install("yfinance")`, `install("financedatareader")` | Low |
| `app/src/main/python/stocks.py` | yfinance for OHLCV, keep pykrx for investor data | Medium |
| `app/src/main/python/market.py` | yfinance for index OHLCV, keep pykrx for components | Medium |
| `app/src/main/python/trend_signal.py` | yfinance for OHLCV/market cap | Medium |
| `app/src/main/python/core.py` | FDR for stock list, pykrx fallback | Medium |
| `app/src/main/python/etfcollector.py` | FDR for ETF list, **pykrx still required for holdings** | High |

#### Limitations (Option B)

| Feature | Status |
|---------|--------|
| ETF Holdings | ⚠️ **Still requires pykrx** |
| Investor Trading | ⚠️ **Still requires pykrx** |

---

## 7. Risk Assessment

### Option A: KIS API Risks

#### High Risk

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| **Account requirement** | Users need brokerage account | High | Clear documentation, consider Option B for users without account |
| API rate limiting | Data fetch failures | Medium | Request throttling, caching, respect API limits |
| Token expiration | Authentication failures | Low | Automatic token refresh (23-hour expiry) |

#### Medium Risk

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| API endpoint changes | Breaking changes | Low | Version API calls, monitor KIS announcements |
| Network timeouts | Slow responses | Medium | 30s timeout, retry logic |
| Response format changes | Parsing errors | Low | Schema validation, graceful fallbacks |

#### Low Risk

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Credential storage security | API key exposure | Low | Use encrypted SharedPreferences (existing pattern) |
| Data consistency | Slight variance from pykrx | Low | Comparison testing during migration |

### Option B: Hybrid Architecture Risks

#### High Risk

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| **pykrx still required** | Cannot fully remove dependency | High | None - this is inherent limitation |
| Multiple failure points | Complex error handling | High | DataSourceManager with fallback |
| pykrx deprecation | Critical features break | Low | No alternative for ETF holdings/investor trading |

#### Medium Risk

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| yfinance Korean data gaps | OHLCV missing for some stocks | Medium | Automatic pykrx fallback |
| Rate limiting (yfinance) | Data fetch failures | Medium | Request throttling + caching |
| Data timing differences | Slight variance between sources | High | Document acceptable thresholds |

### Risk Comparison Summary

| Factor | Option A (KIS) | Option B (Hybrid) |
|--------|---------------|-------------------|
| **Single Point of Failure** | ✅ Better - one API | ⚠️ Worse - multiple sources |
| **Account Requirement** | ⚠️ Required | ✅ None |
| **Complete pykrx Removal** | ✅ Yes | ❌ No |
| **Error Handling Complexity** | ✅ Simple | ⚠️ Complex |
| **Official Support** | ✅ KIS support | ⚠️ Community only |

---

## 8. Testing Strategy

### 8.1 Unit Testing (KIS API)

**New File:** `app/src/test/python/test_kis_client.py`

```python
import pytest
from unittest.mock import Mock, patch
from kis_client import KISAPIClient

class TestKISClient:
    @pytest.fixture
    def mock_client(self):
        """Create client with mocked requests."""
        with patch('kis_client.requests') as mock_requests:
            client = KISAPIClient("test_key", "test_secret")
            yield client, mock_requests

    def test_token_refresh(self, mock_client):
        """Test OAuth token acquisition."""
        client, mock_requests = mock_client
        mock_requests.post.return_value.json.return_value = {
            "access_token": "test_token",
            "expires_in": 86400
        }
        token = client._get_token()
        assert token == "test_token"

    def test_rate_limiting(self, mock_client):
        """Test rate limiting enforces 20 req/sec."""
        client, _ = mock_client
        import time
        start = time.time()
        for _ in range(5):
            client._rate_limit()
        elapsed = time.time() - start
        assert elapsed >= 0.2  # 5 requests * 0.05s minimum interval

    def test_retry_logic(self, mock_client):
        """Test exponential backoff on failures."""
        client, mock_requests = mock_client
        mock_requests.get.side_effect = [
            Exception("Network error"),
            Exception("Network error"),
            Mock(json=lambda: {"rt_cd": "0", "output": []})
        ]
        # Should succeed on 3rd attempt
        client._token = "test"
        client._token_expiry = datetime.now() + timedelta(hours=1)
        result = client._request("/test", "TEST", {})
        assert result["rt_cd"] == "0"

    def test_get_etf_holdings(self, mock_client):
        """Test ETF holdings retrieval."""
        client, mock_requests = mock_client
        mock_requests.get.return_value.json.return_value = {
            "rt_cd": "0",
            "output2": [{
                "stck_shrn_iscd": "005930",
                "stck_prpr_name": "삼성전자",
                "hldg_wght": "15.23",
                "evlu_amt": "1234567890",
                "hldg_qty": "1000"
            }]
        }
        client._token = "test"
        client._token_expiry = datetime.now() + timedelta(hours=1)
        df = client.get_etf_holdings("069500")
        assert len(df) == 1
        assert df.iloc[0]["ticker"] == "005930"
        assert df.iloc[0]["weight"] == 15.23

    def test_get_investor_trading(self, mock_client):
        """Test investor trading data retrieval."""
        client, mock_requests = mock_client
        mock_requests.get.return_value.json.return_value = {
            "rt_cd": "0",
            "output2": [{
                "stck_bsop_date": "20250105",
                "frgn_ntby_qty": "12345",
                "orgn_ntby_qty": "6789",
                "prsn_ntby_qty": "-19134"
            }]
        }
        client._token = "test"
        client._token_expiry = datetime.now() + timedelta(hours=1)
        df = client.get_investor_trading("005930", "20250101")
        assert len(df) == 1
        assert df.iloc[0]["foreign_net"] == 12345

    def test_get_index_ohlcv(self, mock_client):
        """Test index OHLCV retrieval."""
        client, mock_requests = mock_client
        mock_requests.get.return_value.json.return_value = {
            "rt_cd": "0",
            "output2": [{
                "stck_bsop_date": "20250105",
                "bstp_nmix_oprc": "2500.00",
                "bstp_nmix_hgpr": "2520.00",
                "bstp_nmix_lwpr": "2480.00",
                "bstp_nmix_prpr": "2510.00",
                "acml_vol": "123456789"
            }]
        }
        client._token = "test"
        client._token_expiry = datetime.now() + timedelta(hours=1)
        df = client.get_index_ohlcv("0001", "20250101")
        assert len(df) == 1
        assert df.iloc[0]["close"] == 2510.00
```

### 8.2 Integration Testing

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| Token lifecycle | Obtain token → Use for requests → Auto-refresh before expiry | Token refreshed within 23 hours |
| ETF holdings flow | Python → JSON → Kotlin parsing | Matching data in Android Room DB |
| Investor trading flow | Multiple days data → 5-day rolling sum | Correct cumulative values |
| Rate limit compliance | Burst 50 requests | All succeed within 2.5 seconds |

### 8.3 Comparison Testing (Validation Phase)

During migration, run both pykrx and KIS API in parallel:

```python
def compare_etf_holdings(etf_ticker: str) -> dict:
    """Compare ETF holdings from both sources."""
    from pykrx import stock as pykrx
    from kis_client import get_client

    # Get pykrx data
    pykrx_df = pykrx.get_etf_portfolio_deposit_file(etf_ticker)

    # Get KIS data
    kis_df = get_client().get_etf_holdings(etf_ticker)

    # Compare
    comparison = {
        "pykrx_count": len(pykrx_df),
        "kis_count": len(kis_df),
        "coverage": len(kis_df) / len(pykrx_df) if len(pykrx_df) > 0 else 0,
        "weight_diff_avg": 0  # Calculate average weight difference
    }

    return comparison
```

**Acceptance Criteria:**
- ETF holdings coverage ≥ 95%
- Weight variance ≤ 1%
- Investor trading data match ≥ 99%

### 8.4 Performance Testing

| Metric | Target | Method |
|--------|--------|--------|
| Single request latency | < 500ms | Measure 100 requests |
| Token refresh time | < 2s | Measure 10 refreshes |
| Batch ETF holdings (10 ETFs) | < 10s | Sequential with rate limiting |
| Error recovery time | < 5s | Simulate network failure |

---

## 9. Rollback Plan

### 9.1 Feature Flag (Recommended)

Implement a feature flag to switch between pykrx and KIS API:

```python
# In kis_client.py
import os

USE_KIS_API = os.environ.get("USE_KIS_API", "true").lower() == "true"

def get_data_source():
    """Get appropriate data source based on feature flag."""
    if USE_KIS_API:
        try:
            return get_client()
        except RuntimeError:
            log.warning("KIS client not initialized, falling back to pykrx")
            return None
    return None  # Use pykrx
```

```kotlin
// In SettingsViewModel.kt
private val _useKisApi = MutableStateFlow(true)

fun toggleKisApi(enabled: Boolean) {
    viewModelScope.launch {
        repository.setSetting("use_kis_api", enabled.toString())
        _useKisApi.value = enabled
        // Reinitialize Python client
        pyKrxClient.setKisApiEnabled(enabled)
    }
}
```

### 9.2 Gradual Rollout Strategy

| Phase | Coverage | Duration | Rollback Trigger |
|-------|----------|----------|------------------|
| Alpha | Developer only | 1 week | Any critical bug |
| Beta | 10% users (opt-in) | 2 weeks | Error rate > 5% |
| GA | 100% users | Permanent | Error rate > 1% |

### 9.3 Immediate Rollback Steps

If KIS API causes critical issues:

1. **Settings Toggle (User-level)**
   ```
   Settings → Data Source → Disable "KIS API 사용"
   ```

2. **Environment Variable (Developer)**
   ```bash
   export USE_KIS_API=false
   ```

3. **Code Rollback (Emergency)**
   ```bash
   git revert <kis-migration-commit>
   git push origin main
   ```

### 9.4 Full Rollback Procedure

1. **Revert Python files** to pykrx versions
   ```bash
   git checkout HEAD~1 -- app/src/main/python/etfcollector.py
   git checkout HEAD~1 -- app/src/main/python/stocks.py
   git checkout HEAD~1 -- app/src/main/python/market.py
   ```

2. **Keep kis_client.py** for future use (no harm if unused)

3. **Update build.gradle.kts** to restore pykrx
   ```kotlin
   pip {
       install("pykrx")  // Restore
       // ... other packages
   }
   ```

4. **Clear Settings cache** to reset API configuration
   ```kotlin
   apiKeyProvider.clearKisCredentials()
   ```

### 9.5 Data Continuity

- **No database migration needed** - same data schema
- **Historical data preserved** - pykrx and KIS use same ticker format
- **Cache invalidation** - Clear Python data cache on rollback

---

## Appendix A: KIS API Reference

### Authentication

```python
# OAuth2 Token Request
POST https://openapi.koreainvestment.com:9443/oauth2/tokenP

Headers:
  content-type: application/json

Body:
{
    "grant_type": "client_credentials",
    "appkey": "{APP_KEY}",
    "appsecret": "{APP_SECRET}"
}

Response:
{
    "access_token": "eyJ0eXAiOiJKV1...",
    "token_type": "Bearer",
    "expires_in": 86400
}
```

### Critical APIs

#### 1. ETF 구성종목시세 (ETF Component Stock Price)

```
GET /uapi/etfetn/v1/quotations/inquire-component-stock-price
TR ID: FHKST121600C0

Parameters:
  fid_cond_mrkt_div_code: "J" (주식/ETF/ETN)
  fid_input_iscd: ETF ticker (e.g., "069500")
  fid_cond_scr_div_code: "11216"

Response (output2):
[
  {
    "stck_shrn_iscd": "005930",      // 종목코드
    "stck_prpr_name": "삼성전자",     // 종목명
    "hldg_wght": "15.23",            // 비중 (%)
    "evlu_amt": "1234567890",        // 평가금액
    "hldg_qty": "1000"               // 보유수량
  }
]
```

#### 2. 종목별 투자자매매동향 일별 (Investor Trade by Stock Daily)

```
GET /uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily
TR ID: FHPTJ04160001

Parameters:
  fid_cond_mrkt_div_code: "J" (전체)
  fid_input_iscd: Stock ticker (e.g., "005930")
  fid_input_date_1: Start date (YYYYMMDD)
  fid_org_adj_prc: ""
  fid_etc_cls_code: ""

Response (output2):
[
  {
    "stck_bsop_date": "20250105",    // 일자
    "frgn_ntby_qty": "12345",        // 외국인 순매수
    "orgn_ntby_qty": "6789",         // 기관 순매수
    "prsn_ntby_qty": "-19134",       // 개인 순매수
    "pnsn_fnd_ntby_qty": "1000"      // 연기금 순매수
  }
]
```

#### 3. 주식현재가 일자별 (Stock Daily Price)

```
GET /uapi/domestic-stock/v1/quotations/inquire-daily-price
TR ID: FHKST01010400

Parameters:
  fid_cond_mrkt_div_code: "J"
  fid_input_iscd: Stock ticker
  fid_input_date_1: Start date
  fid_input_date_2: End date
  fid_period_div_code: "D" (일)
  fid_org_adj_prc: "0" (수정주가)

Response (output):
[
  {
    "stck_bsop_date": "20250105",    // 일자
    "stck_oprc": "55000",            // 시가
    "stck_hgpr": "56000",            // 고가
    "stck_lwpr": "54000",            // 저가
    "stck_clpr": "55500",            // 종가
    "acml_vol": "1234567"            // 거래량
  }
]
```

### API Request Headers

```
content-type: application/json; charset=utf-8
authorization: Bearer {access_token}
appkey: {APP_KEY}
appsecret: {APP_SECRET}
tr_id: {TR_ID}
```

### Rate Limits

- 초당 20회 제한 (일반)
- 일일 호출 한도 확인 필요

---

## Appendix B: yfinance Reference (Option B)

### yfinance Key Methods

```python
# Ticker object
ticker = yf.Ticker("005930.KS")

# Historical data
ticker.history(period="1mo")  # 1d, 5d, 1mo, 3mo, 6mo, 1y, 2y, 5y, 10y, ytd, max
ticker.history(start="2024-01-01", end="2024-12-31")
ticker.history(interval="1d")  # 1m, 2m, 5m, 15m, 30m, 60m, 90m, 1h, 1d, 5d, 1wk, 1mo, 3mo

# Stock info
ticker.info  # Dict with ~150 fields
ticker.info['marketCap']
ticker.info['sector']
ticker.info['industry']

# Multiple tickers
yf.download(["005930.KS", "000660.KS"], period="1mo", group_by="ticker")
```

### Korean Ticker Format

| pykrx Ticker | yfinance Ticker | Name |
|--------------|-----------------|------|
| 005930 | 005930.KS | 삼성전자 |
| 000660 | 000660.KS | SK하이닉스 |
| 035720 | 035720.KQ | 카카오 |
| 035420 | 035420.KQ | NAVER |
| 1001 (KOSPI Index) | ^KS11 | KOSPI |
| 2001 (KOSDAQ Index) | ^KQ11 | KOSDAQ |

---

## Appendix C: pykrx to KIS API Function Mapping

| pykrx Function | KIS API Endpoint | TR ID | KISAPIClient Method |
|----------------|------------------|-------|---------------------|
| `get_etf_portfolio_deposit_file()` | `/uapi/etfetn/v1/quotations/inquire-component-stock-price` | FHKST121600C0 | `get_etf_holdings()` |
| `get_market_trading_value_by_date()` | `/uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily` | FHPTJ04160001 | `get_investor_trading()` |
| `get_market_ohlcv()` | `/uapi/domestic-stock/v1/quotations/inquire-daily-price` | FHKST01010400 | `get_stock_ohlcv()` |
| `get_index_ohlcv()` | `/uapi/domestic-stock/v1/quotations/inquire-index-daily-price` | FHPUP02120000 | `get_index_ohlcv()` |
| `get_market_cap()` | `/uapi/domestic-stock/v1/ranking/market-cap` | FHPST01740000 | `get_market_cap_ranking()` |
| `get_market_ticker_name()` | `/uapi/domestic-stock/v1/quotations/inquire-price` | FHKST01010100 | `get_stock_info()` / `get_stock_name()` |
| `get_etf_ticker_list()` | 종목정보 마스터파일 다운로드 | N/A | `download_stock_master()` |
| `get_market_ticker_list()` | 종목정보 마스터파일 다운로드 | N/A | `get_all_stocks()` |

### Index Codes Reference

| pykrx Code | KIS Code | Index Name |
|------------|----------|------------|
| 1001 | 0001 | KOSPI |
| 2001 | 1001 | KOSDAQ |
| 1028 | 0028 | KOSPI 200 |

### Market Codes Reference

| Market | fid_cond_mrkt_div_code | Notes |
|--------|------------------------|-------|
| 전체 | J | KRX (KOSPI + KOSDAQ) |
| KOSPI | S | KOSPI only |
| KOSDAQ | Q | KOSDAQ only |
| NXT | NX | K-OTC market |

---

## Appendix D: Decision Summary

### Recommendation: Option A (KIS API)

**Primary Reasons:**
1. ✅ **Complete pykrx replacement** - All critical functions available
2. ✅ **Single source** - Simplified architecture and error handling
3. ✅ **Official support** - Korea Investment Securities provides documentation and support
4. ✅ **Reliable data** - Direct from securities firm, not web scraping

**Trade-off:**
- ⚠️ Requires Korea Investment Securities brokerage account

### When to Use Option B (Hybrid)

Option B is appropriate when:
- Users cannot open a brokerage account
- Need to support users without KIS credentials
- Want to keep pykrx as fallback during transition

---

**Document Version:** 3.0
**Last Updated:** 2025-01-05
**Change Log:**
- v1.0 (2025-01-05): Initial yfinance migration plan
- v2.0 (2025-01-05): Added KIS API as recommended solution, FinanceDataReader and Daum Finance analysis
- v2.1 (2025-01-05): Added detailed account setup guide, API credential acquisition process, OAuth token management
- v3.0 (2025-01-05): Complete revision based on official KIS GitHub repository
  - Added missing API methods: Index OHLCV, Market Cap, Stock Info, Stock List
  - Added rate limiting (20 req/sec) and retry logic with exponential backoff
  - Updated Testing Strategy for KIS API (unit tests, integration tests, comparison tests)
  - Updated Rollback Plan with feature flag and gradual rollout strategy
  - Expanded function mapping table with KISAPIClient method references
  - Added Index/Market code reference tables
  - Renamed file from `PYKRX_TO_YFINANCE_MIGRATION_PLAN.md` to `PYKRX_TO_KIS_API_MIGRATION_PLAN.md`

---

## References

### Official Documentation
- [한국투자증권 홈페이지](https://www.truefriend.com)
- [KIS Developers Portal](https://apiportal.koreainvestment.com/intro)
- [KIS Open Trading API GitHub](https://github.com/koreainvestment/open-trading-api) ⭐ **Primary Reference**

### GitHub Repository Structure (koreainvestment/open-trading-api)

```
open-trading-api/
├── examples_llm/           # LLM-optimized API examples
│   ├── domestic_stock/     # 156 subdirectories (OHLCV, investor trading, etc.)
│   └── etfetn/             # ETF/ETN APIs (holdings, NAV, etc.)
├── examples_user/          # User-friendly integrated examples
├── stocks_info/            # Stock master files (KOSPI, KOSDAQ)
│   ├── kis_kospi_code_mst.py
│   └── kis_kosdaq_code_mst.py
├── kis_auth.py             # Authentication helpers
└── kis_devlp.yaml          # Credential configuration template
```

### Key API Examples from GitHub

| API Category | GitHub Path | Key Files |
|-------------|-------------|-----------|
| ETF Holdings | `examples_llm/etfetn/inquire_component_stock_price/` | `inquire_component_stock_price.py` |
| Investor Trading | `examples_llm/domestic_stock/investor_trade_by_stock_daily/` | `investor_trade_by_stock_daily.py` |
| Index OHLCV | `examples_llm/domestic_stock/inquire_index_daily_price/` | `inquire_index_daily_price.py` |
| Stock Price | `examples_llm/domestic_stock/inquire_price/` | `inquire_price.py` |
| Market Cap | `examples_llm/domestic_stock/market_cap/` | `market_cap.py` |
| Stock List | `stocks_info/` | `kis_kospi_code_mst.py`, `kis_kosdaq_code_mst.py` |

### Account Setup Guides
- [스마트폰 계좌개설 안내](https://www.truefriend.com/main/customer/guide/_static/TF04aa090000.jsp)
- [비대면 계좌 개설 방법](https://moneytime.co.kr/entry/📈-한국투자증권-비대면-계좌-개설-방법-2025년-완전-가이드)

### API Development Resources
- [오픈API 서비스 신청 가이드 (WikiDocs)](https://wikidocs.net/164056)
- [API 접속 토큰 발급 가이드 (WikiDocs)](https://wikidocs.net/230259)
- [한국투자증권 Open API 신청 가이드 (Velog)](https://velog.io/@refinedstone/1-Open-API-신청)

### API Rate Limits

| Limit Type | Value | Notes |
|------------|-------|-------|
| Requests per second | 20 | General API calls |
| Token issuance | 1 per minute | OAuth token refresh |
| Token validity | 24 hours | Auto-refresh recommended at 23 hours |
| API key validity | 1 year | Renewal required 30 days before expiry |
