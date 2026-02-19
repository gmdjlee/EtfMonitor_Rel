# Changelog

All notable changes to ETF Monitor will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.0] - 2026-02-20

### Added

#### Blood Indicator Kotlin Migration (Chaquopy 제거)
- BloodIndicatorClient — native OkHttp client for Yahoo Finance (^IRX, SPY) and FRED API (BAMLH0A0HYM2)
- BloodIndicatorCalculator — pure Kotlin computation: weekly resampling (W-FRI), 100-week SMA, RISK_ON/RISK_OFF signals
- HTTP retry logic with exponential backoff (3 retries, 30s timeout per call)

### Removed
- **Chaquopy** embedded Python runtime — completely removed from project
- `blood_indicator.py` (527 lines) — replaced by BloodIndicatorClient + BloodIndicatorCalculator
- `core.py` (149 lines) — utility functions replaced by native Kotlin
- `BloodIndicatorPyClient.kt` — replaced by BloodIndicatorClient
- `PythonModule.kt` — Hilt module for Python singleton (no longer needed)
- `__init__.py` — Python package marker
- pip dependencies: `pandas`, `requests` (no longer bundled in APK)
- Chaquopy Gradle plugin, Maven repository, and ProGuard rules
- Python initialization in Android test (`KrxApiFunctionalityTest`)
- Configuration cache disabled workaround (`org.gradle.configuration-cache=false`)

### Changed
- BloodIndicatorRepositoryImpl — injects `BloodIndicatorClient` instead of `BloodIndicatorPyClient`
- KrxApiFunctionalityTest — removed Python/feargreed test, retained kotlin_krx tests only
- Configuration cache now enabled (`org.gradle.configuration-cache=true`)

---

## [1.2.0] - 2026-02-19

### Added

#### KIS Financial Information (재무정보)
- KIS Open API integration with OAuth2 client credentials (`/oauth2/tokenP`)
- 5 parallel financial data API endpoints (balance-sheet, income-statement, profit-ratio, stability-ratio, growth-ratio)
- FinancialInfoContent composable with TabRow (수익성/안정성)
- ProfitabilityContent with 3 MPAndroidChart charts (income bar, growth line, asset growth line)
- StabilityContent with 4 line charts + evaluation badges
- FinancialInfoViewModel with sealed FinancialState (NoStock, Loading, NoApiKey, Success, Error)
- GetFinancialSummaryUseCase (invoke + refresh)
- FinancialRepositoryImpl with cache-first 24h TTL + 5 async API calls
- KisApiKeyProvider using EncryptedSharedPreferences (AES256-GCM)
- KisApiKeyCard in Settings GeneralTab for API key management
- StocksHubScreen TabRow integration ["차트 분석" | "재무정보"]
- YTD-to-quarterly conversion for cumulative income statement values

#### Database (Schema v19→v20)
- Migration v19→v20: `financial_cache` table (ticker PK, name, data JSON, cachedAt)
- FinancialCache entity + FinancialCacheDao (7 queries)

---

## [1.1.0] - 2026-02-19

### Added

#### kotlin_krx Integration (pykrx Replacement)
- kotlin_krx native Kotlin library replacing pykrx Python dependency
- KrxModule Hilt DI (KrxClient, KrxStock, KrxEtf, KrxIndex, TickerCache singletons)
- 11 kotlin_krx UseCases for market data operations
- 5 kotlin_krx Repository implementations with KrxRepositoryBase
- DateAdapter, KrxErrorMapper, HoldingMapper adapter layer

#### Kotlin Native Computation Engines
- FearGreedCalculator — RSI, MACD, Fear & Greed index (replaces feargreed.py KRX calls)
- TechnicalAnalysisEngine — EMA, CMF, Elder Impulse, DeMark TD, trend signals (replaces trend_signal.py)
- MarketOscillatorCalculator — market overbought/oversold via top-200 market cap proxy

#### Database (Schema v17-v19)
- Migration v17→v18, v18→v19

### Removed
- **pykrx** Python dependency — 100% replaced by kotlin_krx
- **PyKrxClient** — all call sites migrated to kotlin_krx UseCases
- **OscillatorPyClient** — replaced by TechnicalAnalysisEngine + kotlin_krx
- Python packages: beautifulsoup4, scikit-learn, joblib, setuptools, wheel
- FearGreedRepositoryImpl Python/DataFrame dependency — now pure kotlin_krx + FearGreedCalculator

### Fixed
- Zero-data bug: kotlin_krx wrong API endpoint (MDCSTAT01602 → MDCSTAT01501) + reverse chronological order
- Investor trading data: 외국인/기관 수급 데이터 zero values
- Chart period selection: date format mismatch (yyyy-MM-dd → yyyyMMdd) across 5 charts
- Elder Impulse/DeMark TD market cap 0: single-call → OHLCV trade-day retry + sharesOutstanding priority
- BloodIndicatorPyClient crash: Python.getInstance() → Hilt constructor injection
- MarketOscillator empty display: yyyyMMdd → yyyy-MM-dd date conversion before DB storage
- MarketOscillator CancellationException swallowed in catch(Exception): added rethrow
- MarketOscillator job cancellation on navigation: NonCancellable context for data collection

### Changed
- FearGreedRepositoryImpl constructor: `Python` → `KrxIndex`
- OscillatorViewModel: 18 pyClient calls → 3 kotlin_krx UseCases
- EtfRepositoryImpl: PyKrxClient → 3 kotlin_krx UseCases
- All ViewModels now inject domain UseCases (Clean Architecture AD-002 resolved)
- Python packages pinned: pandas, requests only (reduced from 6 packages)
- Project cleanup: 27 dead files removed, 13 unused dependencies removed

---

## [1.0.1] - 2025-12-27

### Added

#### Testing Infrastructure
- Unit test framework with JUnit5, MockK, Turbine, and Coroutines Test
- `HomeViewModelTest` - State transitions and first-run dialog logic
- `EtfRepositoryImplTest` - Holding comparison and settings management
- `FearGreedRepositoryImplTest` - Data retrieval and cache logic
- `CorrelationAnalyzerTest` - Pearson correlation and signal generation
- `PyKrxClientTest` - Python integration, retry logic, JSON parsing
- `MigrationTest` - All 16 database migrations (v1→v17)

#### Database (Schema v15-v17)
- `PriceCache` entity - ML prediction price history cache
- `EnhancedPrediction` entity - 28-feature ensemble ML predictions
- `StockIndicatorAIResult` entity - Stock-indicator AI analysis results
- `PriceCacheDao`, `EnhancedPredictionDao`, `StockIndicatorAIResultDao`
- `historyType` field in SearchHistory for menu-specific history

#### Quality Improvements
- Network retry logic with exponential backoff (`RetryHelper`)
- Compose error boundary component (`ErrorBoundary`)
- Certificate pinning for AI API endpoints (Claude, Gemini)
- 20+ accessibility strings for interactive elements

### Fixed

#### Critical Fixes
- Database schema mismatch - All entities now properly registered in AppDatabase
- Null safety improvements - Removed 7 critical `!!` operator usages
- Silent failure handling - Repository failures now propagate to UI

#### High Priority Fixes
- Release build debug logging - ProGuard rules strip Log.d/v calls
- SharedPreferences blocking - Changed `.commit()` to `.apply()`
- Architecture violations - Feature module dependencies via interface abstraction

#### Medium Priority Fixes
- Unnecessary coroutine scope in CashDepositTab removed
- LaunchedEffect properly utilized for chart calculations

### Changed

- Version code: 1 → 2
- Version name: "1.0" → "1.0.1"
- Python package versions pinned for reproducible builds (pandas, pykrx, scikit-learn, requests, beautifulsoup4, joblib)

### Security

- Certificate pinning for `api.anthropic.com` (Claude API)
- Certificate pinning for `generativelanguage.googleapis.com` (Gemini API)
- Backup pins configured for CA rotation

---

## [1.0.0] - 2025-12-25

### Added

#### Core Features
- ETF tracking with real-time holdings composition
- Stock-level foreign/institutional investment analysis
- Fear & Greed Index for KOSPI/KOSDAQ market sentiment
- Market Oscillator with overbought/oversold signals
- Market Deposit trends and credit balance tracking
- Advanced Dashboard with correlation analysis

#### AI Integration
- Claude API (claude-3-5-sonnet) for market analysis
- Gemini API (gemini-2.0-flash) as alternative provider
- AI chat sessions with context-aware conversations
- Market signal generation (STRONG_BUY to STRONG_SELL)

#### ML Predictions
- 28-feature ensemble model (XGBoost, LightGBM, Random Forest, Gradient Boosting)
- Stock price prediction with confidence scores
- Risk assessment and feature importance analysis

#### Clean Architecture
- MVVM + Clean Architecture with 6 feature modules
- Domain/Data/Presentation layer separation
- Hilt dependency injection throughout
- 10 DI modules (4 core + 6 feature)

#### Database (Schema v1-v14)
- Room database with 19 entities
- 14 migrations for schema evolution
- Holding entity with memory-optimized storage (Short/Int compression)
- Comprehensive DAO layer with Flow support

#### Background Processing
- WorkManager-based scheduled updates
- Foreground service for ETF data collection
- 8 specialized workers for different data types

#### Python Integration
- Chaquopy embedded Python runtime
- pykrx for Korean stock market data
- pandas for data manipulation
- scikit-learn for ML predictions
- beautifulsoup4 for web scraping

### Technical Details

- **Language**: Kotlin 2.1.0
- **UI**: Jetpack Compose with Material Design 3
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)
- **Database**: Room 2.8.3
- **DI**: Hilt 2.54
- **Charts**: Vico 2.0.0-alpha.28

---

## Version History Summary

| Version | Date | Schema | Key Changes |
|---------|------|--------|-------------|
| 1.3.0 | 2026-02-20 | v20 | blood_indicator.py → Kotlin, Chaquopy removed |
| 1.2.0 | 2026-02-19 | v20 | KIS Financial Info feature (재무정보), 13 new files |
| 1.1.0 | 2026-02-19 | v19 | pykrx → kotlin_krx migration, 3 Kotlin engines, bug fixes |
| 1.0.1 | 2025-12-27 | v17 | Testing, quality fixes, 3 new entities |
| 1.0.0 | 2025-12-25 | v14 | Initial release, Clean Architecture |
