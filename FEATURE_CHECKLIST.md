# FEATURE_CHECKLIST.md — MarketMonitor Feature Catalog

## Navigation Routes (14)

| Route | Screen | Status |
|---|---|---|
| `home` | HomeScreen | ACTIVE |
| `market_indicator` | MarketIndicatorHubScreen | ACTIVE |
| `etf_hub` | EtfHubScreen | ACTIVE |
| `stocks` | StocksHubScreen | ACTIVE |
| `analysis` | AnalysisHubScreen | ACTIVE |
| `list` | EtfListScreen | ACTIVE |
| `detail/{ticker}` | EtfDetailScreen | ACTIVE |
| `settings` | SettingsScreen | ACTIVE |
| `backup` | BackupScreen | ACTIVE |
| `trend/{etfTicker}/{stockTicker}` | StockTrendScreen | ACTIVE |
| `aggregated_trend/{stockTicker}` | AggregatedStockTrendScreen | ACTIVE |
| `oscillator` | OscillatorScreen | ACTIVE |
| `market_deposit` | MarketDepositScreen | ACTIVE |
| `fear_greed` | FearGreedScreen | ACTIVE |
| `market_oscillator` | MarketOscillatorScreen | ACTIVE |
| `ai_analysis` | NewAIAnalysisScreen | ACTIVE |
| `advanced_dashboard` | AdvancedDashboardScreen | ACTIVE |

## Feature Modules (7)

### 1. analysis (35 files)
- **Screens**: AnalysisHubScreen, NewAIAnalysisScreen, AdvancedDashboardScreen
- **ViewModels**: NewAIAnalysisViewModel, AdvancedDashboardViewModel
- **Repositories**: AIAnalysisRepository, ChatRepository, StatisticsAnalysisRepository, StockIndicatorRepository, AdvancedAnalysisRepository, CorrelationAnalysisRepository
- **DI**: AnalysisModule
- **Data Sources**: Claude/Gemini AI APIs, Room

### 2. backup (9 files)
- **Screens**: BackupScreen
- **ViewModels**: BackupViewModel
- **Repositories**: BackupRepository
- **DI**: BackupModule
- **Data Sources**: Google Drive, Room

### 3. etf (25 files)
- **Screens**: EtfHubScreen, EtfListScreen, EtfDetailScreen
- **ViewModels**: EtfListViewModel, EtfDetailViewModel
- **UseCases**: CheckDataStatusUseCase, GetEtfComparisonUseCase, GetEtfDetailUseCase, GetEtfListUseCase, SearchEtfsUseCase, GetAvailableDatesUseCase, GetComparisonInRangeUseCase
- **Repositories**: EtfRepository
- **DI**: EtfModule
- **Data Sources**: kotlin_krx (KrxEtf), Room

### 4. home (15 files)
- **Screens**: HomeScreen
- **ViewModels**: HomeViewModel
- **UseCases**: CheckDataStatusUseCase, CheckFirstRunUseCase, GetDefaultDaysUseCase, GetHomeSummaryUseCase, SaveDialogDismissedUseCase
- **Repositories**: HomeRepository
- **DI**: HomeModule
- **Data Sources**: Room, SharedPreferences

### 5. market (22 files)
- **Screens**: MarketIndicatorHubScreen, FearGreedScreen, MarketDepositScreen, BloodIndicatorScreen, MarketOscillatorScreen
- **ViewModels**: FearGreedViewModel, MarketDepositViewModel, BloodIndicatorViewModel, MarketOscillatorViewModel
- **Repositories**: FearGreedRepository, MarketDepositRepository, MarketIndexRepository, MarketOscillatorRepository, BloodIndicatorRepository
- **DI**: MarketModule
- **Data Sources**: kotlin_krx (KrxIndex + FearGreedCalculator, MarketOscillatorCalculator), Python (blood_indicator.py), NaverFinanceScraper, Room

### 6. settings (11 files)
- **Screens**: SettingsScreen
- **ViewModels**: SettingsViewModel (25+ StateFlows, intentional exception)
- **DI**: SettingsModule
- **Data Sources**: SharedPreferences, Room

### 7. stock (50 files)
- **Screens**: StocksHubScreen (TabRow: 차트 분석/재무정보), OscillatorScreen, StockTrendScreen, AggregatedStockTrendScreen
- **ViewModels**: OscillatorViewModel, StockTrendViewModel, StatisticsViewModel (12+ StateFlows, intentional exception), FinancialInfoViewModel
- **UseCases**: AnalyzeStockUseCase, GetCashDepositTrendUseCase, GetStatisticsDatesUseCase, GetStockAnalysisUseCase, GetStockChangesUseCase, GetStockRankingUseCase, GetStockTrendUseCase, InitializeStocksUseCase, SearchStocksUseCase, GetFinancialSummaryUseCase
- **Repositories**: StockRepository, StockAnalysisRepository, StockTrendRepository, StockStatisticsRepository, FinancialRepository
- **Financial UI**: FinancialInfoContent, ProfitabilityContent (3 MPAndroidChart charts), StabilityContent (4 charts + evaluation badges)
- **DI**: StockModule (@KisOkHttp qualifier, KIS Financial providers)
- **Data Sources**: kotlin_krx (TechnicalAnalysisEngine, KrxStockDataRepository), KIS Open API (5 financial endpoints, OAuth2), AI APIs, Room

## Core Infrastructure

### DAOs (21)
AIAnalysisDao, AIChatDao, BackupDao, BloodIndicatorDao, CorrelationAnalysisDao, DailyEtfStatisticsDao, EnhancedPredictionDao, EtfCorrelationDao, EtfDao, FearGreedDao, FinancialCacheDao, LiquidityAnalysisDao, MarketDepositDao, MarketIndexDao, MarketOscillatorDao, PriceCacheDao, SearchHistoryDao, SectorAnalysisDao, StockAnalysisDao, StockDao, StockIndicatorAIResultDao

### Entities (22)
AIAnalysisResult, AIChatMessage, AIChatSession, BloodIndicator, CorrelationAnalysisResult, DailyEtfStatistics, EnhancedPrediction, Etf, EtfCorrelationCache, FearGreedIndex, FinancialCache, Holding, LiquidityAnalysis, MarketDeposit, MarketIndex, MarketOscillatorData, PriceCache, SearchHistory, SectorAnalysis, Setting, Stock, StockAnalysisData, StockIndicatorAIResult

### Workers (9)
AdvancedAnalysisWorker, BloodIndicatorUpdateWorker, DataArchiveWorker, EtfUpdateWorker, FearGreedUpdateWorker, MarketDepositUpdateWorker, MarketIndexUpdateWorker, MarketOscillatorUpdateWorker, StockUpdateWorker

### Python Clients (1 active)
- BloodIndicatorPyClient (90s timeout) — blood_indicator.py (Yahoo/FRED)
- FearGreedRepositoryImpl (60s timeout) — feargreed.py (직접 PyObject 조작, PyClient 아님)

### KIS API (Financial Info)
- KisApiKeyProvider — EncryptedSharedPreferences (AES256-GCM) for KIS API key storage
- KisApiKeyConfig — appKey, appSecret, InvestmentMode enum (MOCK/PRODUCTION)
- FinancialRepositoryImpl — OAuth2 token management, 5 parallel API endpoints, 24h Room cache

### AI Clients (11 files)
AIApiClient, AIApiClientFactory, AIModel, AIProvider, AIResponseParser, ClaudeApiClient, GeminiApiClient, ApiKeyProvider, SharedPreferencesApiKeyProvider, MarketAnalysisPrompts, MarketSignal

### kotlin_krx UseCases (11)
GetKrxMarketCapUseCase, GetKrxIndexComponentsUseCase, GetKrxMarketDataUseCase, GetKrxEtfHoldingsUseCase, GetKrxEtfListUseCase, GetKrxBusinessDaysUseCase, GetKrxIndexDataUseCase, GetTrendSignalDataUseCase, GetElderImpulseDataUseCase, GetDemarkTDDataUseCase, GetStockOhlcvUseCase

### kotlin_krx Repositories (5)
KrxEtfRepositoryImpl, KrxStockRepositoryImpl, KrxMarketRepositoryImpl, KrxIndexRepositoryImpl, KrxStockDataRepositoryImpl

### Core DI Modules (5)
AIModule, DatabaseModule, KrxModule, PythonModule, WorkerModule

### Python Scripts (4 active)
blood_indicator.py, feargreed.py, kis_client.py, core.py

## Summary Counts

| Category | Count |
|---|---|
| Screen routes | 17 (14 declared + 3 extra) |
| Feature modules | 7 |
| ViewModels | 15 (+FinancialInfoViewModel) |
| Feature UseCases | 22 (+GetFinancialSummaryUseCase) |
| Core krx UseCases | 11 |
| Repositories (feature) | 19 (+FinancialRepository) |
| Repositories (core krx) | 5 |
| DAOs | 21 (+FinancialCacheDao) |
| Entities | 22 (+FinancialCache) |
| Workers | 9 |
| Python clients | 1 |
| KIS API clients | 1 (FinancialRepositoryImpl) |
| AI client files | 11 |
| DI modules | 12 (5 core + 7 feature) |
