# pykrx → yfinance Migration Plan

**Date:** 2025-01-05
**Status:** Draft
**Author:** Claude Code

---

## Executive Summary

This document outlines the comprehensive plan for migrating the EtfMonitor Android app's data collection layer from **pykrx** to **yfinance** library.

### Key Finding

> **Full migration from pykrx to yfinance is NOT feasible** due to critical data gaps in yfinance for Korean market-specific data.

### Recommended Approach

A **hybrid architecture** that:
1. Migrates suitable features to yfinance (OHLCV, market cap, index data)
2. Retains pykrx for irreplaceable features (ETF holdings, investor trading data)
3. Adds fallback mechanisms using KRX OpenAPI and web scraping
4. Implements a multi-source data layer for reliability

---

## Table of Contents

1. [Current State Analysis](#1-current-state-analysis)
2. [yfinance Capability Assessment](#2-yfinance-capability-assessment)
3. [Gap Analysis](#3-gap-analysis)
4. [Hybrid Architecture Design](#4-hybrid-architecture-design)
5. [Implementation Phases](#5-implementation-phases)
6. [File Change Summary](#6-file-change-summary)
7. [Risk Assessment](#7-risk-assessment)
8. [Testing Strategy](#8-testing-strategy)
9. [Rollback Plan](#9-rollback-plan)

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

## 2. yfinance Capability Assessment

### 2.1 yfinance for Korean Markets

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

### 2.2 Korean Ticker Format in yfinance

| Market | Format | Example |
|--------|--------|---------|
| KOSPI Stocks | `XXXXXX.KS` | `005930.KS` (삼성전자) |
| KOSDAQ Stocks | `XXXXXX.KQ` | `035720.KQ` (카카오) |
| KOSPI Index | `^KS11` | - |
| KOSDAQ Index | `^KQ11` | - |
| KOSPI 200 | `^KS200` | - |

### 2.3 yfinance Key Methods

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

---

## 3. Gap Analysis

### 3.1 Critical Gaps (Cannot Migrate)

| Feature | pykrx Function | yfinance Alternative | Impact |
|---------|---------------|---------------------|--------|
| **ETF List** | `get_etf_ticker_list()` | None | App cannot discover Korean ETFs |
| **ETF Holdings** | `get_etf_portfolio_deposit_file()` | `funds_data.top_holdings` (Top 10 only) | Core feature broken |
| **Investor Trading** | `get_market_trading_value_by_date()` | None | Foreign/institutional analysis lost |
| **Index Components** | `get_index_portfolio_deposit_file()` | None | Market oscillator calculation broken |
| **Stock List** | `get_market_ticker_list()` | None | Cannot enumerate all stocks |
| **Business Day Check** | `get_market_ohlcv()` check | None | Date validation broken |

### 3.2 Migratable Features

| Feature | Current (pykrx) | Target (yfinance) | Benefit |
|---------|-----------------|-------------------|---------|
| Stock OHLCV | `get_market_ohlcv()` | `Ticker.history()` | More reliable, international standard |
| Index OHLCV | `get_index_ohlcv()` | `Ticker.history()` | Same benefits |
| Market Cap | `get_market_cap()` | `Ticker.info` | Faster single-point query |

### 3.3 Migration Verdict

```
┌─────────────────────────────────────────────────────────────┐
│                    MIGRATION VERDICT                         │
├─────────────────────────────────────────────────────────────┤
│  Full Migration:     ❌ NOT POSSIBLE (critical data gaps)   │
│  Partial Migration:  ✅ RECOMMENDED (OHLCV, index, cap)     │
│  Data Fallback:      ✅ REQUIRED (KRX API, web scraping)    │
│  Approach:           HYBRID MULTI-SOURCE ARCHITECTURE       │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Hybrid Architecture Design

### 4.1 Multi-Source Data Layer

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                  DataSourceManager                      │
                    │  (Priority-based fallback with caching)                 │
                    └────────────────────────┬────────────────────────────────┘
                                             │
           ┌─────────────────────────────────┼─────────────────────────────────┐
           │                                 │                                 │
    ┌──────▼──────┐                   ┌──────▼──────┐                   ┌──────▼──────┐
    │  yfinance   │                   │   pykrx    │                   │  KRX API /  │
    │  (Primary)  │                   │  (Primary) │                   │  Scraping   │
    │             │                   │            │                   │ (Fallback)  │
    └──────┬──────┘                   └──────┬─────┘                   └──────┬──────┘
           │                                  │                                │
           │  • Stock OHLCV                   │  • ETF list                   │  • ETF holdings
           │  • Market cap                    │  • ETF holdings               │  • Index components
           │  • Index OHLCV                   │  • Stock list                 │  • Fear & Greed data
           │                                  │  • Investor trading           │  • Business days
           │                                  │  • Index components           │
           └──────────────────────────────────┴────────────────────────────────┘
```

### 4.2 Data Source Assignment

| Feature | Primary Source | Fallback 1 | Fallback 2 |
|---------|---------------|------------|------------|
| Stock OHLCV | **yfinance** | pykrx | KRX scraping |
| Market Cap | **yfinance** | pykrx | - |
| Index OHLCV | **yfinance** | pykrx | KRX API |
| ETF List | **pykrx** | KRX scraping | - |
| ETF Holdings | **pykrx** | KRX ETF PDF | - |
| Stock List | **pykrx** | KRX API | - |
| Foreign/Institutional | **pykrx** | KRX API | - |
| Index Components | **pykrx** | KRX API | - |
| Business Days | **KRX calendar** | pykrx | - |

### 4.3 Fallback Flow

```python
def get_stock_ohlcv(ticker: str, start: str, end: str) -> DataFrame:
    """Get stock OHLCV with automatic fallback."""

    # Try yfinance first (primary for OHLCV)
    try:
        return yf_client.get_stock_ohlcv(ticker, start, end)
    except Exception as e:
        log.warning(f"yfinance failed: {e}")

    # Fallback to pykrx
    try:
        return pykrx_get_stock_ohlcv(ticker, start, end)
    except Exception as e:
        log.warning(f"pykrx failed: {e}")

    # Last resort: KRX scraping
    try:
        return krx_scrape_ohlcv(ticker, start, end)
    except Exception as e:
        log.error(f"All sources failed: {e}")
        raise DataUnavailableError(f"Cannot fetch OHLCV for {ticker}")
```

---

## 5. Implementation Phases

### Phase 1: Foundation Layer

**Duration:** Week 1-2
**Objective:** Create the multi-source data infrastructure

#### 1.1 Create yfinance Wrapper Module

**New File:** `app/src/main/python/yf_client.py`

```python
"""
yfinance wrapper for Korean stock market.
Handles ticker formatting and data normalization.
"""
import yfinance as yf
import pandas as pd
from typing import Optional
from logger import log

def format_krx_ticker(ticker: str, market: str = None) -> str:
    """Convert Korean ticker to yfinance format.

    Args:
        ticker: 6-digit Korean stock code (e.g., "005930")
        market: Optional market hint ("KOSPI" or "KOSDAQ")

    Returns:
        yfinance ticker (e.g., "005930.KS")
    """
    if ticker.startswith("^"):
        return ticker  # Already an index

    if "." in ticker:
        return ticker  # Already formatted

    # Infer market from ticker if not provided
    if market is None:
        market = infer_market(ticker)

    suffix = ".KS" if market == "KOSPI" else ".KQ"
    return f"{ticker}{suffix}"

def get_stock_ohlcv(ticker: str, start: str, end: str) -> pd.DataFrame:
    """Get stock OHLCV data via yfinance.

    Args:
        ticker: Korean stock ticker (6-digit)
        start: Start date (YYYYMMDD or YYYY-MM-DD)
        end: End date (YYYYMMDD or YYYY-MM-DD)

    Returns:
        DataFrame with columns: open, high, low, close, volume
    """
    yf_ticker = format_krx_ticker(ticker)

    # Normalize date format
    start_dt = normalize_date(start)
    end_dt = normalize_date(end)

    t = yf.Ticker(yf_ticker)
    df = t.history(start=start_dt, end=end_dt)

    if df.empty:
        raise ValueError(f"No data for {yf_ticker}")

    # Normalize column names to lowercase
    df = df.rename(columns={
        "Open": "open",
        "High": "high",
        "Low": "low",
        "Close": "close",
        "Volume": "volume"
    })

    return df[["open", "high", "low", "close", "volume"]]

def get_index_ohlcv(index_code: str, start: str, end: str) -> pd.DataFrame:
    """Get index OHLCV data via yfinance.

    Args:
        index_code: "KOSPI" or "KOSDAQ" (or pykrx codes "1001", "2001")
    """
    # Map to yfinance index symbols
    index_map = {
        "KOSPI": "^KS11",
        "KOSDAQ": "^KQ11",
        "1001": "^KS11",
        "2001": "^KQ11"
    }

    yf_symbol = index_map.get(index_code, index_code)
    return get_stock_ohlcv(yf_symbol, start, end)

def get_market_cap(ticker: str) -> Optional[float]:
    """Get current market cap via yfinance."""
    try:
        yf_ticker = format_krx_ticker(ticker)
        info = yf.Ticker(yf_ticker).info
        return info.get('marketCap')
    except Exception as e:
        log.warning(f"Failed to get market cap for {ticker}: {e}")
        return None
```

#### 1.2 Create Data Source Manager

**New File:** `app/src/main/python/data_source_manager.py`

```python
"""
Multi-source data manager with automatic fallback.
"""
from enum import Enum
from typing import Callable, Any, List
from logger import log

class DataSource(Enum):
    YFINANCE = "yfinance"
    PYKRX = "pykrx"
    KRX_API = "krx_api"
    SCRAPING = "scraping"

class DataSourceManager:
    """Priority-based data fetching with automatic fallback."""

    def __init__(self):
        self._cache = {}

    def with_fallback(
        self,
        sources: List[Callable],
        *args,
        **kwargs
    ) -> Any:
        """Try multiple data sources in order.

        Args:
            sources: List of callable functions to try in order
            *args, **kwargs: Arguments to pass to each function

        Returns:
            Result from first successful source

        Raises:
            Exception: If all sources fail
        """
        last_error = None

        for i, source_fn in enumerate(sources):
            try:
                result = source_fn(*args, **kwargs)
                if result is not None:
                    log.info(f"Data fetched from source {i+1}/{len(sources)}")
                    return result
            except Exception as e:
                log.warning(f"Source {i+1} failed: {e}")
                last_error = e
                continue

        raise last_error or Exception("All data sources failed")

# Global instance
data_manager = DataSourceManager()
```

#### 1.3 Create KRX API Client

**New File:** `app/src/main/python/krx_api_client.py`

```python
"""
Direct KRX OpenAPI/OpenDART integration for fallback data.
Similar pattern to existing feargreed.py implementation.
"""
import requests
import pandas as pd
from typing import List, Dict, Optional
from logger import log
from core import HttpClient

KRX_API_BASE = "http://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd"

def get_etf_list(date: str) -> List[Dict]:
    """Get all Korean ETFs from KRX directly.

    Args:
        date: Date in YYYYMMDD format

    Returns:
        List of dicts with 'ticker' and 'name' keys
    """
    params = {
        "bld": "dbms/MDC/STAT/standard/MDCSTAT04301",
        "trdDd": date,
        "share": "1",
        "money": "1"
    }

    try:
        response = HttpClient.get(KRX_API_BASE, params=params)
        data = response.json()

        return [
            {"ticker": item["ISU_SRT_CD"], "name": item["ISU_ABBRV"]}
            for item in data.get("OutBlock_1", [])
        ]
    except Exception as e:
        log.error(f"KRX ETF list failed: {e}")
        return []

def get_stock_list(date: str, market: str) -> List[str]:
    """Get all stock tickers from KRX directly."""
    market_code = "STK" if market == "KOSPI" else "KSQ"

    params = {
        "bld": "dbms/MDC/STAT/standard/MDCSTAT01901",
        "mktId": market_code,
        "trdDd": date
    }

    try:
        response = HttpClient.get(KRX_API_BASE, params=params)
        data = response.json()

        return [item["ISU_SRT_CD"] for item in data.get("OutBlock_1", [])]
    except Exception as e:
        log.error(f"KRX stock list failed: {e}")
        return []

def is_business_day(date: str) -> bool:
    """Check if date is a KRX business day."""
    # Try to fetch minimal data for the date
    stocks = get_stock_list(date, "KOSPI")
    return len(stocks) > 0
```

#### 1.4 Update build.gradle.kts

**File:** `app/build.gradle.kts`

```kotlin
chaquopy {
    defaultConfig {
        pip {
            install("yfinance")  // ADD THIS LINE
            install("pandas")
            install("pykrx")
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

### Phase 2: OHLCV Migration

**Duration:** Week 2-3
**Objective:** Migrate stock/index OHLCV to yfinance as primary source

#### 2.1 Modify stocks.py

**File:** `app/src/main/python/stocks.py`

```python
# Add imports at top
from yf_client import get_stock_ohlcv as yf_get_ohlcv
from data_source_manager import data_manager

# Modify get_stock_ohlcv function
def get_stock_ohlcv(ticker: str, days: int = 365, interval: str = "d") -> str:
    """Get stock OHLCV with yfinance primary, pykrx fallback."""

    def _yfinance_source():
        end = datetime.now()
        start = end - timedelta(days=days)
        return yf_get_ohlcv(ticker, start.strftime("%Y%m%d"), end.strftime("%Y%m%d"))

    def _pykrx_source():
        # Original pykrx implementation
        end = market_date()
        start = days_ago(days)
        return stock.get_market_ohlcv(start, end, ticker)

    try:
        df = data_manager.with_fallback([_yfinance_source, _pykrx_source])
        # ... rest of processing unchanged
    except Exception as e:
        return err_json(f"OHLCV fetch failed: {e}")
```

#### 2.2 Modify market.py

**File:** `app/src/main/python/market.py`

```python
# Add imports
from yf_client import get_index_ohlcv as yf_get_index_ohlcv

# Modify fetch_index function
def fetch_index(market: str, start: str, end: str) -> pd.DataFrame:
    """Fetch index OHLCV with yfinance primary."""

    def _yfinance_source():
        return yf_get_index_ohlcv(market, start, end)

    def _pykrx_source():
        idx_code = "1001" if market == "KOSPI" else "2001"
        return stock.get_index_ohlcv(start, end, idx_code)

    return data_manager.with_fallback([_yfinance_source, _pykrx_source])
```

---

### Phase 3: Market Cap Migration

**Duration:** Week 3
**Objective:** Use yfinance for market cap data

#### 3.1 Modify trend_signal.py

**File:** `app/src/main/python/trend_signal.py`

```python
# Add import
from yf_client import get_market_cap as yf_get_market_cap

# Modify _get_market_cap helper
def _get_market_cap(ticker: str, start: str, end: str) -> pd.DataFrame:
    """Get market cap with yfinance primary."""

    def _yfinance_source():
        # yfinance returns single point, need to adapt
        cap = yf_get_market_cap(ticker)
        if cap:
            # Create single-row DataFrame
            return pd.DataFrame({"시가총액": [cap]}, index=[pd.Timestamp.now()])
        raise ValueError("No market cap data")

    def _pykrx_source():
        return stock.get_market_cap(start, end, ticker)

    return data_manager.with_fallback([_pykrx_source, _yfinance_source])
    # Note: pykrx first here because it provides historical data
```

---

### Phase 4: Fallback Enhancement

**Duration:** Week 4
**Objective:** Add KRX API fallbacks for critical data

#### 4.1 Enhance etfcollector.py

**File:** `app/src/main/python/etfcollector.py`

```python
# Add import
from krx_api_client import get_etf_list as krx_get_etf_list

# Modify get_etf_list_with_names
def get_etf_list_with_names(keywords: List[str] = None) -> str:
    """Get ETF list with pykrx primary, KRX API fallback."""

    def _pykrx_source():
        d = market_date()
        tickers = stock.get_etf_ticker_list(d)
        return [{"ticker": t, "name": stock.get_etf_ticker_name(t)} for t in tickers]

    def _krx_source():
        d = market_date()
        return krx_get_etf_list(d)

    try:
        etfs = data_manager.with_fallback([_pykrx_source, _krx_source])

        # Apply keyword filtering if provided
        if keywords:
            etfs = [e for e in etfs if any(k in e["name"] for k in keywords)]

        return to_json(etfs)
    except Exception as e:
        return err_json(f"ETF list fetch failed: {e}")
```

#### 4.2 Enhance core.py

**File:** `app/src/main/python/core.py`

```python
# Add import
from krx_api_client import get_stock_list as krx_get_stock_list

# Modify get_tickers
def get_tickers(market: str = None, date: str = None) -> List[str]:
    """Get stock tickers with pykrx primary, KRX API fallback."""

    d = date or market_date()

    def _pykrx_source():
        if market:
            return list(stock.get_market_ticker_list(d, market=market))
        else:
            kospi = list(stock.get_market_ticker_list(d, market="KOSPI"))
            kosdaq = list(stock.get_market_ticker_list(d, market="KOSDAQ"))
            return kospi + kosdaq

    def _krx_source():
        if market:
            return krx_get_stock_list(d, market)
        else:
            return krx_get_stock_list(d, "KOSPI") + krx_get_stock_list(d, "KOSDAQ")

    return data_manager.with_fallback([_pykrx_source, _krx_source])
```

---

### Phase 5: Testing & Validation

**Duration:** Week 5-6
**Objective:** Comprehensive testing of the hybrid system

#### 5.1 Unit Tests

**New File:** `app/src/test/python/test_yf_client.py`

```python
import pytest
from yf_client import format_krx_ticker, get_stock_ohlcv, get_index_ohlcv

class TestYfClient:
    def test_format_krx_ticker_kospi(self):
        assert format_krx_ticker("005930", "KOSPI") == "005930.KS"

    def test_format_krx_ticker_kosdaq(self):
        assert format_krx_ticker("035720", "KOSDAQ") == "035720.KQ"

    def test_format_krx_ticker_already_formatted(self):
        assert format_krx_ticker("005930.KS") == "005930.KS"

    def test_format_krx_ticker_index(self):
        assert format_krx_ticker("^KS11") == "^KS11"

    def test_get_stock_ohlcv_valid(self):
        df = get_stock_ohlcv("005930", "20240101", "20240131")
        assert not df.empty
        assert "close" in df.columns

    def test_get_index_ohlcv_kospi(self):
        df = get_index_ohlcv("KOSPI", "20240101", "20240131")
        assert not df.empty
```

#### 5.2 Integration Tests

**New File:** `app/src/test/python/test_data_source_manager.py`

```python
import pytest
from data_source_manager import DataSourceManager

class TestDataSourceManager:
    def test_primary_success(self):
        manager = DataSourceManager()

        def success_fn():
            return "success"

        def fallback_fn():
            return "fallback"

        result = manager.with_fallback([success_fn, fallback_fn])
        assert result == "success"

    def test_fallback_on_primary_failure(self):
        manager = DataSourceManager()

        def fail_fn():
            raise Exception("Primary failed")

        def fallback_fn():
            return "fallback"

        result = manager.with_fallback([fail_fn, fallback_fn])
        assert result == "fallback"

    def test_all_sources_fail(self):
        manager = DataSourceManager()

        def fail_fn():
            raise Exception("Failed")

        with pytest.raises(Exception):
            manager.with_fallback([fail_fn, fail_fn])
```

#### 5.3 Comparison Validation

**New File:** `app/src/test/python/test_data_comparison.py`

```python
"""
Compare data from pykrx and yfinance to ensure consistency.
"""
import pandas as pd
from pykrx import stock
import yfinance as yf

def compare_ohlcv_sources(ticker: str, days: int = 30):
    """Compare OHLCV data from both sources."""

    # Get pykrx data
    end = pd.Timestamp.now().strftime("%Y%m%d")
    start = (pd.Timestamp.now() - pd.Timedelta(days=days)).strftime("%Y%m%d")
    pykrx_df = stock.get_market_ohlcv(start, end, ticker)

    # Get yfinance data
    yf_ticker = f"{ticker}.KS"
    yf_df = yf.Ticker(yf_ticker).history(period=f"{days}d")

    # Compare close prices
    # Allow 1% variance due to different data providers
    pykrx_close = pykrx_df["종가"].values[-10:]  # Last 10 days
    yf_close = yf_df["Close"].values[-10:]

    variance = abs(pykrx_close - yf_close) / pykrx_close * 100

    return {
        "max_variance_pct": variance.max(),
        "mean_variance_pct": variance.mean(),
        "pykrx_rows": len(pykrx_df),
        "yfinance_rows": len(yf_df),
        "acceptable": variance.max() < 5  # 5% threshold
    }
```

---

## 6. File Change Summary

### New Files (5 files)

| File | Purpose | Lines (est.) |
|------|---------|--------------|
| `app/src/main/python/yf_client.py` | yfinance wrapper for Korean stocks | ~200 |
| `app/src/main/python/data_source_manager.py` | Multi-source fallback manager | ~250 |
| `app/src/main/python/krx_api_client.py` | Direct KRX API client | ~300 |
| `app/src/test/python/test_yf_client.py` | Unit tests for yfinance wrapper | ~150 |
| `app/src/test/python/test_data_source_manager.py` | Unit tests for fallback manager | ~150 |

### Modified Files (6 files)

| File | Changes | Complexity |
|------|---------|------------|
| `app/build.gradle.kts` | Add `install("yfinance")` | Low |
| `app/src/main/python/stocks.py` | yfinance for OHLCV, keep pykrx for investor data | Medium |
| `app/src/main/python/market.py` | yfinance for index OHLCV, keep pykrx for components | Medium |
| `app/src/main/python/trend_signal.py` | yfinance for OHLCV/market cap | Medium |
| `app/src/main/python/core.py` | Add KRX API fallbacks for tickers | Medium |
| `app/src/main/python/etfcollector.py` | Add KRX scraping fallback for ETF list | High |

### Unchanged Files

| File | Reason |
|------|--------|
| `app/src/main/python/feargreed.py` | Already uses direct KRX API |
| `app/src/main/python/deposit_scraper.py` | Already uses Naver scraping |
| `app/src/main/python/stock_predictor_v2.py` | Uses processed data from other modules |
| Kotlin clients (`PyKrxClient.kt`, etc.) | Python layer handles fallbacks internally |

---

## 7. Risk Assessment

### High Risk

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| yfinance Korean data gaps | OHLCV missing for some stocks | Medium | Automatic pykrx fallback |
| pykrx API changes/deprecation | All Korean-specific data affected | Low | KRX API as secondary fallback |
| Rate limiting on yfinance | Data fetch failures | Medium | Request throttling + caching |

### Medium Risk

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Market cap currency mismatch | Wrong values in UI | Medium | Convert to KRW |
| Data timing differences | Slight data variance | High | Document acceptable thresholds |
| Build time increase | Slower CI/CD | Low | yfinance is lightweight |

### Low Risk

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Ticker format issues | Wrong data returned | Low | Robust ticker validation |
| Memory usage increase | App performance | Low | Proper cleanup |

---

## 8. Testing Strategy

### Unit Testing

- Test each yfinance function independently
- Test ticker format conversion
- Test fallback manager logic
- Mock external API calls

### Integration Testing

- Test full data flow from Python to Kotlin
- Verify JSON output format compatibility
- Test timeout handling

### Comparison Testing

- Compare pykrx vs yfinance data for same tickers
- Validate acceptable variance thresholds
- Document any systematic differences

### Performance Testing

- Measure latency for yfinance vs pykrx
- Test concurrent requests
- Validate caching effectiveness

---

## 9. Rollback Plan

### Immediate Rollback

If migration causes critical issues:

```python
# In data_source_manager.py
USE_YFINANCE = False  # Set to False to disable yfinance

def with_fallback(self, sources, *args, **kwargs):
    if not USE_YFINANCE:
        # Skip yfinance sources
        sources = [s for s in sources if "yfinance" not in s.__name__]
    # ... rest of logic
```

### Gradual Rollback

1. Monitor error rates per data source
2. Disable yfinance for problematic tickers only
3. Maintain pykrx as always-available fallback

### Full Rollback

1. Revert Python file changes
2. Remove `yfinance` from build.gradle.kts
3. No Kotlin changes needed (Python handles all logic)

---

## Appendix A: API Reference

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

### pykrx Key Methods (Retained)

```python
# ETF data (no yfinance equivalent)
stock.get_etf_ticker_list(date)
stock.get_etf_ticker_name(ticker)
stock.get_etf_portfolio_deposit_file(ticker, date)

# Investor data (no yfinance equivalent)
stock.get_market_trading_value_by_date(start, end, ticker)

# Index components (no yfinance equivalent)
stock.get_index_portfolio_deposit_file(index_code)
```

---

## Appendix B: Korean Ticker Mapping

| pykrx Ticker | yfinance Ticker | Name |
|--------------|-----------------|------|
| 005930 | 005930.KS | 삼성전자 |
| 000660 | 000660.KS | SK하이닉스 |
| 035720 | 035720.KQ | 카카오 |
| 035420 | 035420.KQ | NAVER |
| 1001 (KOSPI Index) | ^KS11 | KOSPI |
| 2001 (KOSDAQ Index) | ^KQ11 | KOSDAQ |

---

**Document Version:** 1.0
**Last Updated:** 2025-01-05
