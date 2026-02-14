# kotlin_krx Coverage Map Catalog

**Generated**: 2026-02-14
**Source**: D:\android_2025\kotlin_krx\docs\USER_MANUAL.md (1095 lines)
**Purpose**: Comprehensive API function inventory for Code-Reviewer verification

---

## Section 3: KrxStock - 주식 데이터 (11 Functions)

| Section | Function | Parameters | Return Type | pykrx Equivalent | Status |
|---------|----------|------------|-------------|------------------|--------|
| 3.1 | getMarketOhlcv | date: String, market: Market = Market.ALL | List\<MarketOhlcv\> | stock.get_market_ohlcv("20210122") | TBD |
| 3.2 | getOhlcvByTicker | startDate: String, endDate: String, ticker: String | List\<StockOhlcvHistory\> | stock.get_market_ohlcv("20210101", "20210131", "005930") | TBD |
| 3.3 | getMarketCap | date: String, market: Market = Market.ALL | List\<MarketCap\> | stock.get_market_cap("20210122") | TBD |
| 3.4 | getMarketFundamental | date: String, market: Market = Market.ALL | List\<StockFundamental\> | stock.get_market_fundamental("20210122") | TBD |
| 3.5 | getTickerList | date: String, market: Market = Market.ALL | List\<TickerInfo\> | stock.get_market_ticker_list("20210122") | TBD |
| 3.6 | getMarketTradingByInvestor | startDate: String, endDate: String, market: Market = Market.ALL, valueType: TradingValueType = VALUE, askBidType: AskBidType = NET_BUY | List\<InvestorTrading\> | stock.get_market_trading_value_and_volume_on_market_by_date() | TBD |
| 3.7 | getTradingByInvestor | startDate: String, endDate: String, ticker: String, valueType: TradingValueType = VALUE, askBidType: AskBidType = NET_BUY | List\<InvestorTrading\> | stock.get_market_trading_value_and_volume_on_ticker_by_date() | TBD |
| 3.8 | getShortSellingAll | date: String, market: Market = Market.KOSPI | List\<ShortSelling\> | stock.get_shorting_volume_by_ticker() | TBD |
| 3.9 | getShortSellingByTicker | startDate: String, endDate: String, ticker: String | List\<ShortSellingHistory\> | stock.get_shorting_volume_by_date() | TBD |
| 3.10 | getShortBalanceAll | date: String, market: Market = Market.KOSPI | List\<ShortBalance\> | stock.get_shorting_balance_by_ticker() | TBD |
| 3.11 | getShortBalanceByTicker | startDate: String, endDate: String, ticker: String | List\<ShortBalanceHistory\> | stock.get_shorting_balance_by_date() | TBD |

---

## Section 4: KrxEtf - ETF 데이터 (5 Functions)

| Section | Function | Parameters | Return Type | pykrx Equivalent | Status |
|---------|----------|------------|-------------|------------------|--------|
| 4.1 | getEtfPrice | date: String | List\<EtfPrice\> | etf.get_etf_ohlcv_by_ticker("20210122") | TBD |
| 4.2 | getOhlcvByTicker | startDate: String, endDate: String, ticker: String | List\<EtfOhlcvHistory\> | etf.get_etf_ohlcv_by_date("20210101", "20210131", "069500") | TBD |
| 4.3 | getEtfTickerList | date: String | List\<EtfInfo\> | etf.get_etf_ticker_list("20210122") | TBD |
| 4.4 | getEtfName | ticker: String, date: String | String? | (helper - no direct pykrx equivalent) | TBD |
| 4.5 | getPortfolio | date: String, ticker: String | List\<EtfPortfolio\> | etf.get_etf_portfolio_deposit_file("20210122", "069500") | TBD |

---

## Section 5: KrxIndex - 지수 데이터 (10 Functions + 4 Helpers)

| Section | Function | Parameters | Return Type | pykrx Equivalent | Status |
|---------|----------|------------|-------------|------------------|--------|
| 5.1 | getOhlcvByTicker | startDate: String, endDate: String, ticker: String | List\<IndexOhlcv\> | index.get_index_ohlcv("20210101", "20210131", "1028") | TBD |
| 5.2 | getKospi | startDate: String, endDate: String | List\<IndexOhlcv\> | (helper - wraps getOhlcvByTicker with TICKER_KOSPI) | TBD |
| 5.2 | getKospi200 | startDate: String, endDate: String | List\<IndexOhlcv\> | (helper - wraps getOhlcvByTicker with TICKER_KOSPI_200) | TBD |
| 5.2 | getKosdaq | startDate: String, endDate: String | List\<IndexOhlcv\> | (helper - wraps getOhlcvByTicker with TICKER_KOSDAQ) | TBD |
| 5.2 | getKosdaq150 | startDate: String, endDate: String | List\<IndexOhlcv\> | (helper - wraps getOhlcvByTicker with TICKER_KOSDAQ_150) | TBD |
| 5.3 | getIndexOhlcv | date: String, market: IndexMarket = IndexMarket.KOSPI | List\<IndexOhlcvByTicker\> | index.get_index_ohlcv_by_ticker("20210122", "KOSPI") | TBD |
| 5.4 | getIndexList | date: String, market: IndexMarket = IndexMarket.ALL | List\<IndexInfo\> | (no direct pykrx equivalent) | TBD |
| 5.5 | getIndexName | ticker: String, date: String | String? | (helper - no direct pykrx equivalent) | TBD |
| 5.6 | getIndexPortfolio | date: String, ticker: String | List\<IndexPortfolio\> | index.get_index_portfolio_deposit_file("1028", "20210122") | TBD |
| 5.7 | getIndexPortfolioTickers | date: String, ticker: String | List\<String\> | index.get_index_portfolio_deposit_file("1028") (tickers only) | TBD |
| 5.8 | getNearestBusinessDay | date: String, prev: Boolean = true | String | get_nearest_business_day_in_a_week(date, prev) | TBD |
| 5.9 | getBusinessDays | startDate: String, endDate: String | List\<String\> | get_previous_business_days(fromdate, todate) | TBD |
| 5.10 | getBusinessDaysByMonth | year: Int, month: Int | List\<String\> | get_previous_business_days(year=2021, month=1) | TBD |

---

## Summary Statistics

| Category | Count |
|----------|-------|
| **Total Functions** | **30** |
| KrxStock Functions | 11 |
| KrxEtf Functions | 5 |
| KrxIndex Functions | 10 |
| Helper Functions | 4 (getKospi, getKospi200, getKosdaq, getKosdaq150) |
| **Total Data Models** | **25+** |
| Stock Models | 10 |
| ETF Models | 4 |
| Index Models | 4 |
| Shared Models | 7+ (TickerInfo, InvestorTrading, etc.) |

---

## Additional Context

### Constructors

| Class | Parameters | Notes |
|-------|-----------|-------|
| KrxStock | client: KrxClient = KrxClient(), tickerCache: TickerCache = TickerCache() | Section 3 |
| KrxEtf | client: KrxClient = KrxClient(), tickerCache: TickerCache = TickerCache() | Section 4 |
| KrxIndex | client: KrxClient = KrxClient() | Section 5 |

### Resource Management

All API classes implement `close()` method for OkHttpClient cleanup. Client sharing is recommended for efficiency.

### Date Format

All date parameters use `yyyyMMdd` format (e.g., "20210122").

### Index Ticker Constants (Section 5 - Lines 471-479)

| Constant | Value | Description |
|----------|-------|-------------|
| TICKER_KOSPI | "1001" | KOSPI |
| TICKER_KOSPI_200 | "1028" | KOSPI 200 |
| TICKER_KOSPI_LARGE | "1002" | KOSPI 대형주 |
| TICKER_KOSPI_MID | "1003" | KOSPI 중형주 |
| TICKER_KOSPI_SMALL | "1004" | KOSPI 소형주 |
| TICKER_KOSDAQ | "2001" | KOSDAQ |
| TICKER_KOSDAQ_150 | "2203" | KOSDAQ 150 |

### Deprecated/Legacy Functions

**None identified** in USER_MANUAL.md sections 3-5.

---

## Next Steps for Code-Reviewer

1. Verify each function exists in actual kotlin_krx source code
2. Confirm parameter types match documentation
3. Validate return types match data models
4. Check for undocumented public API functions
5. Identify any deprecated methods not mentioned in manual
6. Cross-reference with pykrx compatibility map (Section 10, lines 844-892)

---

**Catalog Status**: COMPLETE
**Functions Extracted**: 30 API functions + 4 helper methods
**Verification Required**: 34 total entries
**Code-Reviewer Task**: Cross-reference with D:\android_2025\kotlin_krx\src\main\kotlin\com\krxkt\
