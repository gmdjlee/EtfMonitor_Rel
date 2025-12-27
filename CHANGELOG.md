# Changelog

All notable changes to ETF Monitor will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- Python package versions now pinned for reproducible builds:
  - pandas==2.1.4
  - pykrx==1.0.47
  - scikit-learn==1.3.2
  - requests==2.31.0
  - beautifulsoup4==4.12.2
  - joblib==1.3.2

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
| 1.0.1 | 2025-12-27 | v17 | Testing, quality fixes, 3 new entities |
| 1.0.0 | 2025-12-25 | v14 | Initial release, Clean Architecture |
