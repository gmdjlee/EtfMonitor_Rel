# CLAUDE.md - AI Assistant Guide for EtfMonitor

## Project Overview

**ETF Monitor** is a production-grade Android financial monitoring application for the Korean stock market (KRX). It provides ETF tracking, stock analysis, technical indicators, and market sentiment analysis.

### Key Facts
- **Language**: Kotlin 2.1.0
- **UI Framework**: Jetpack Compose with Material Design 3
- **Min SDK**: 26 (Android 8.0) | **Target SDK**: 35 (Android 15)
- **Architecture**: MVVM + Clean Architecture
- **Dependency Injection**: Hilt 2.54
- **Database**: Room 2.8.3 (22 entities, 20 DAOs, schema v19)
- **AI Integration**: Claude & Gemini API for market analysis
- **KRX Data**: kotlin_krx native Kotlin library for Korean stock market data

### Project Purpose
Monitor Korean ETFs with features including:
- Real-time ETF holdings and composition tracking
- Stock-level foreign/institutional investment analysis
- Technical oscillators (EMA, MACD) for market timing
- Fear & Greed Index for market sentiment
- Market deposit trends
- AI-powered market analysis (Claude, Gemini)
- ML-based stock price predictions
- Correlation analysis between ETF flows and market indices
- Background data synchronization

---

## ⚠️ Critical Implementation Notes

> **Read this section before making any changes to the codebase.**

### Database Critical Patterns

#### 1. Holding Entity Memory Optimization
The `Holding` entity uses compressed storage to minimize memory footprint:
```kotlin
// STORAGE: Uses Short/Int instead of Float
@Entity
data class Holding(
    val etfTicker: String,
    val stockTicker: String,
    val date: String,
    val weightBps: Short,        // basis points: 5.25% → 525
    val amountMillion: Int,      // millions: 1,234,567,890 → 1234
    val snapshotType: String     // DAILY, WEEKLY, MONTHLY
)

// CONVERSION HELPERS (built into entity):
val weight: Float = weightBps / 10000f
val amount: Float = amountMillion * 1_000_000f
```

**⚠️ ALWAYS use the factory method to create Holdings:**
```kotlin
// ✅ CORRECT: Use factory method
val holding = Holding.create(etfTicker, stockTicker, name, date, weight, amount)

// ❌ WRONG: Direct construction may cause overflow/underflow
val holding = Holding(etfTicker, stockTicker, date, 525, 1234, "DAILY")
```

#### 2. StockAnalysisData JOIN Requirement
After Migration 12→13, the `name` field was removed from `stock_analysis_data`. **Always use JOIN with stocks table:**
```kotlin
// ✅ CORRECT: Use DTO with JOIN
val data = stockAnalysisDao.getAnalysisDataWithName(ticker)  // Returns StockAnalysisWithName

// ❌ WRONG: Direct query will miss name field
val data = stockAnalysisDao.getAnalysisData(ticker)  // name is null
```

#### 3. DAO Query Memory Limits
All ranking/list queries use LIMIT to prevent OOM on Android:
```kotlin
// Built-in limits in EtfDao:
// - Stock rankings: LIMIT 500
// - Stock changes: LIMIT 300
// - General lists: LIMIT 100
```

#### 4. Type Conversion in Queries
When querying Holding data, convert compressed values:
```sql
SELECT CAST(weightBps AS REAL) / 10000.0 as weight,
       CAST(amountMillion AS REAL) * 1000000.0 as amount
FROM holdings
```

### KRX Data Client Critical Patterns

#### Timeout Requirements by Client
| Client | Default Timeout | Notes |
|--------|----------------|-------|
| KrxDataClient | 30s | 2 retries for holdings data |
| StockDataClient | 30s | Standard |
| MarketIndexClient | 30s | Standard |
| MarketOscillatorCalculator | **180s** | Collects 200+ component stocks |
| BloodIndicatorClient | **90s** | Yahoo Finance + FRED API dual fetch |
| FearGreedClient | No explicit timeout | Direct KRX HTTP calls, request 3x days due to MA loss |
| DepositScraper | 30s | Jsoup HTML parsing |

#### FearGreed Data Collection
Fear & Greed calculation loses data due to moving averages. **Always request 3x the needed days:**
```kotlin
// To get 30 days of Fear & Greed data:
fearGreedRepository.initializeFearGreed(days = 90)  // Request 3x days
```

### Repository Caching Strategies
| Repository | Cache Expiry | Invalidation Logic |
|------------|-------------|-------------------|
| StockAnalysisRepository | 24 hours | OR missing today's data OR <80% requested days |
| MarketDepositRepository | 12 hours | AND latest date == today |
| FearGreedRepository | 12 hours | OR latest date != today |

### ViewModel State Patterns
**Note:** Not all ViewModels use sealed classes. Two ViewModels use individual StateFlows for granular control:
- **SettingsViewModel**: 25+ individual StateFlows (themes, API keys, schedules, etc.)
- **StatisticsViewModel**: 12+ individual StateFlows (sorting, search, analysis results)

This is intentional for these complex configuration screens. New ViewModels should still prefer sealed classes.

---

## Codebase Structure

```
EtfMonitor_Rel/
├── app/src/main/
│   ├── java/com/etfmonitor/
│   │   ├── MainActivity.kt              # Entry point
│   │   ├── EtfMonitorApp.kt            # Hilt application
│   │   │
│   │   ├── core/                        # Core module (97 files)
│   │   │   ├── common/util/             # AppLogger, DateFormatter, Exceptions, etc. (6 files)
│   │   │   ├── analysis/                # Market analysis utilities (6 files)
│   │   │   │   ├── CorrelationAnalyzer.kt
│   │   │   │   ├── Backtester.kt
│   │   │   │   ├── TimeSeriesData.kt
│   │   │   │   ├── OscillatorCalculator.kt
│   │   │   │   ├── TrendSignalCalculator.kt
│   │   │   │   └── model/StockData.kt
│   │   │   ├── database/                # Room (40 files: 20 entities, 18 DAOs, AppDatabase, Converters)
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── Converters.kt
│   │   │   │   ├── entities/            # 20 entity files (21 entities)
│   │   │   │   └── *Dao.kt              # 18 DAO interfaces
│   │   │   ├── network/
│   │   │   │   ├── ai/                  # AI API clients (14 files)
│   │   │   │   │   ├── AIApiClient.kt
│   │   │   │   │   ├── ClaudeApiClient.kt
│   │   │   │   │   ├── GeminiApiClient.kt
│   │   │   │   │   └── MarketSignal.kt
│   │   │   │   └── krx/                 # Native KRX data clients (6 files)
│   │   │   │       ├── KrxDataClient.kt
│   │   │   │       ├── StockDataClient.kt
│   │   │   │       ├── MarketIndexClient.kt
│   │   │   │       ├── FearGreedClient.kt
│   │   │   │       ├── DepositScraper.kt
│   │   │   │       └── BloodIndicatorClient.kt
│   │   │   ├── ui/                      # Shared UI (20 files)
│   │   │   │   ├── theme/               # Material 3 theme
│   │   │   │   └── component/           # StateCards, BottomNav, HubComponents, etc.
│   │   │   ├── worker/                  # Background workers (9 files)
│   │   │   ├── service/                 # Foreground services (2 files)
│   │   │   └── di/                      # Core DI modules (4 files)
│   │   │
│   │   ├── feature/                     # Feature modules (155 files)
│   │   │   ├── home/                    # Home feature (15 files)
│   │   │   │   ├── domain/              # Domain layer
│   │   │   │   │   ├── model/           # HomeState, HomeSummary
│   │   │   │   │   ├── repository/      # HomeRepository interface
│   │   │   │   │   └── usecase/         # GetHomeSummary, CheckDataStatus, etc.
│   │   │   │   ├── data/repository/     # HomeRepositoryImpl
│   │   │   │   ├── presentation/        # HomeScreen, HomeViewModel
│   │   │   │   └── di/                  # HomeModule.kt
│   │   │   │
│   │   │   ├── etf/                     # ETF feature (23 files)
│   │   │   │   ├── domain/              # Domain layer
│   │   │   │   │   ├── model/           # Etf, Holding, ComparisonResult, DataProgress
│   │   │   │   │   ├── repository/      # EtfRepository interface
│   │   │   │   │   └── usecase/         # Use cases
│   │   │   │   ├── data/                # Data layer
│   │   │   │   │   ├── mapper/          # EtfMapper
│   │   │   │   │   └── repository/      # EtfRepositoryImpl
│   │   │   │   ├── presentation/        # EtfListScreen, EtfDetailScreen, EtfHubScreen
│   │   │   │   └── di/                  # EtfModule.kt
│   │   │   │
│   │   │   ├── stock/                   # Stock feature (38 files)
│   │   │   │   ├── domain/              # Stock, StockAnalysis, StockTrend models
│   │   │   │   ├── data/                # LocalDataSources, Mapper, Repositories
│   │   │   │   ├── presentation/        # StockTrendScreen, OscillatorScreen, StatisticsScreen
│   │   │   │   └── di/                  # StockModule.kt
│   │   │   │
│   │   │   ├── market/                  # Market indicators feature (22 files)
│   │   │   │   ├── domain/              # FearGreed, Deposit, Oscillator, Index
│   │   │   │   │   ├── model/           # MarketModels.kt
│   │   │   │   │   ├── repository/      # 4 repository interfaces
│   │   │   │   │   └── usecase/         # 37 UseCases
│   │   │   │   ├── data/
│   │   │   │   │   ├── mapper/          # MarketMapper.kt
│   │   │   │   │   └── repository/      # 4 repository implementations
│   │   │   │   ├── presentation/
│   │   │   │   │   ├── feargreed/       # FearGreedScreen, ViewModel
│   │   │   │   │   ├── oscillator/      # MarketOscillatorScreen, ViewModel
│   │   │   │   │   ├── deposit/         # MarketDepositScreen, ViewModel
│   │   │   │   │   └── hub/             # MarketIndicatorHubScreen
│   │   │   │   └── di/                  # MarketModule.kt
│   │   │   │
│   │   │   ├── analysis/                # Analysis feature (37 files)
│   │   │   │   ├── domain/              # AI, Correlation, Dashboard models
│   │   │   │   ├── data/                # Repository implementations
│   │   │   │   ├── presentation/        # AI Analysis, Advanced Dashboard screens
│   │   │   │   └── di/                  # AnalysisModule.kt
│   │   │   │
│   │   │   └── settings/                # Settings feature (20 files)
│   │   │       ├── domain/
│   │   │       │   ├── model/           # SettingsModels.kt
│   │   │       │   ├── repository/      # SettingsRepository.kt
│   │   │       │   └── usecase/         # Theme, AI, Update UseCases
│   │   │       ├── data/
│   │   │       │   ├── mapper/
│   │   │       │   └── repository/
│   │   │       ├── presentation/        # SettingsScreen, SettingsViewModel, components/
│   │   │       └── di/                  # SettingsModule.kt
│   │   │
│   │   └── navigation/                  # App navigation (1 file)
│   │       └── Navigation.kt            # NavHost, Screen routes
│   │
│   ├── res/                             # Android resources
│   └── AndroidManifest.xml
├── gradle/libs.versions.toml            # Version catalog
└── build.gradle.kts                     # Build config
```

**Clean Architecture Organization (~255 Kotlin files):**
- `core/` (97 files) - Shared infrastructure: database, network, UI theme, workers, services, DI
- `feature/` (155 files) - 6 feature modules with domain/data/presentation layers
- `navigation/` (1 file) - App-wide navigation
- `MainActivity.kt`, `EtfMonitorApp.kt` - Entry points

**Feature Module Structure (per feature):**
- `domain/model/` - Business models
- `domain/repository/` - Repository interfaces
- `domain/usecase/` - Use cases
- `data/mapper/` - Entity ↔ Domain mappers
- `data/repository/` - Repository implementations
- `presentation/` - Screens, ViewModels, components
- `di/` - Feature-specific DI module

---

## Architecture & Design Patterns

### MVVM with Clean Architecture

```
┌─────────────────────────────────────┐
│   UI Layer (Jetpack Compose)        │  Stateless screens
│   Screens ◄── ViewModels (State)    │  @HiltViewModel
└──────────┬──────────────────────────┘
           │
┌──────────▼──────────────────────────┐
│   Repository Layer                  │  @Singleton
│   EtfRepository, StockRepository    │  Business logic
└──────────┬──────────────────────────┘
           │
┌──────────▼──────────────────────────┐
│   Data Sources                      │
│   Room DAOs | kotlin_krx | AI APIs  │  IO operations
└─────────────────────────────────────┘
```

### Core Design Patterns

#### 1. State-Driven UI (Sealed Classes)
```kotlin
// Pattern: Sealed class for type-safe state (HomeViewModel actual implementation)
sealed class HomeState {
    object Loading : HomeState()
    data class Idle(val hasData: Boolean, val lastDate: String?, val summary: HomeSummary? = null) : HomeState()
    data class Initializing(val message: String, val progress: Int) : HomeState()
    data class Updating(val message: String, val progress: Int) : HomeState()
    data class Success(val message: String) : HomeState()
    data class Error(val message: String) : HomeState()
}

// Supporting data class for complex state
data class HomeSummary(
    val depositChange: Double?,
    val creditChange: Double?,
    val kospiFearGreed: Double?,
    val kosdaqFearGreed: Double?,
    val kospiOscillator: Double?,
    val kospiStatus: String?,
    val kosdaqOscillator: Double?,
    val kosdaqStatus: String?
)

// ViewModels expose immutable StateFlow
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DataRepository
) : ViewModel() {
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()
}

// Screens consume state reactively
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    when (state) {
        is HomeState.Loading -> LoadingIndicator()
        is HomeState.Idle -> IdleContent()
        is HomeState.Error -> ErrorCard((state as HomeState.Error).message)
    }
}
```

#### 2. Repository Pattern with Flow
```kotlin
@Singleton
class EtfRepositoryImpl @Inject constructor(
    private val localDataSource: EtfLocalDataSource,
    private val etfDao: EtfDao,
    private val pyKrxClient: PyKrxClient
) : EtfRepository {
    // Reactive data stream
    override fun getAllEtfs(): Flow<List<Etf>> = localDataSource.getAllEtfs()
        .map { it.toDomain() }
        .flowOn(Dispatchers.IO)

    // One-time suspend operation
    override suspend fun hasData(): Boolean = withContext(Dispatchers.IO) {
        etfDao.getEtfCount() > 0
    }
}
```

#### 3. Dependency Injection (Hilt)
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "etf_db")
            .addMigrations(/* migrations */)
            .build()
    }
}
```

#### 4. Type-Safe Navigation
```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Detail : Screen("detail/{ticker}") {
        fun createRoute(ticker: String) = "detail/$ticker"
    }
}

NavHost(navController, startDestination = Screen.Home.route) {
    composable(Screen.Home.route) { HomeScreen() }
    composable(Screen.Detail.route) { backStackEntry ->
        val ticker = backStackEntry.arguments?.getString("ticker")
        DetailScreen(ticker)
    }
}
```

#### 5. Coroutine Dispatcher Strategy
```kotlin
// Rule: Explicit dispatcher per operation type
suspend fun loadFromDatabase() = withContext(Dispatchers.IO) {
    dao.query()
}

fun observeData() {
    repository.getDataFlow()
        .flowOn(Dispatchers.IO)      // Upstream on IO
        .onEach { updateUI(it) }      // Downstream on Main
        .launchIn(viewModelScope)
}
```

---

## Technology Stack

### Core Dependencies
| Component | Version | Purpose |
|-----------|---------|---------|
| **Kotlin** | 2.1.0 | Primary language |
| **Compose BOM** | 2024.12.01 | UI framework |
| **Material3** | Latest | Design system |
| **Hilt** | 2.54 | Dependency injection |
| **Room** | 2.8.3 | Local database |
| **Coroutines** | 1.10.2 | Async/concurrency |
| **WorkManager** | 2.9.1 | Background tasks |
| **Navigation Compose** | 2.8.5 | Type-safe navigation |
| **kotlin_krx** | local module | KRX stock market data API |
| **Jsoup** | 1.17.2 | HTML parsing (deposit scraper) |
| **OkHttp** | 4.12.0 | HTTP client (AI APIs, KRX) |
| **Security Crypto** | 1.1.0-alpha06 | Encrypted API key storage |

### Data Visualization
- **Vico 2.0.0-alpha.28**: Modern line/column charts (Material Design 3)

### AI Integration
- **Claude API**: Anthropic's Claude for market analysis
- **Gemini API**: Google's Gemini for market analysis
- Encrypted SharedPreferences for secure API key storage

### krxkt Integration
**Native Kotlin KRX API** (internal module at `krxkt/`, package `com.krxkt`):
- Direct KRX HTTP API access (no API key required)
- `KrxEtf`: ETF ticker lists, portfolios, names, ISIN codes
- `KrxStock`: Stock data, OHLCV, market cap, investor trading data
- `KrxIndex`: KOSPI/KOSDAQ index data

#### Native KRX Client Architecture
| Client | Replaces | Key Methods |
|--------|----------|-------------|
| **KrxDataClient** | PyKrxClient | `getFilteredEtfList()`, `getHoldings()`, `getBusinessDays()`, `getStockName()` |
| **StockDataClient** | OscillatorPyClient (data) | `searchStock()`, `getStockAnalysis()`, `getStockOhlcv()`, `getAllStocksList()` |
| **MarketIndexClient** | MarketIndexPyClient | `fetchMarketIndices()`, `fetchRecentDays()`, `getLatestIndex()` |
| **FearGreedClient** | feargreed.py | `runAnalysis()` - 5 indicators @ 20% each (Momentum, PCR, VIX, Spread, RSI) |
| **DepositScraper** | deposit_scraper.py | `getMarketDepositData()` - Jsoup HTML parsing from Naver Finance |
| **BloodIndicatorClient** | blood_indicator.py | `fetchBloodIndicator()` - Yahoo Finance + FRED API |

#### Technical Indicator Calculators (in `core/analysis/`)
| Calculator | Purpose |
|-----------|---------|
| **TechnicalIndicators** | EMA, MACD, RSI, CMF calculations |
| **MarketOscillatorCalculator** | Market overbought/oversold (200+ stocks, 180s timeout) |
| **TrendSignalNativeCalculator** | Elder Impulse, DeMark TD Sequential |
| **OscillatorCalculator** | Stock-level supply/demand oscillator |

---

## Development Workflows

### Adding a New Feature Screen

1. **Create UI State**
```kotlin
// In ui/screens/newfeature/NewFeatureScreen.kt
sealed class NewFeatureState {
    object Loading : NewFeatureState()
    data class Success(val data: List<Item>) : NewFeatureState()
    data class Error(val message: String) : NewFeatureState()
}
```

2. **Create ViewModel**
```kotlin
@HiltViewModel
class NewFeatureViewModel @Inject constructor(
    private val repository: DataRepository
) : ViewModel() {
    private val _state = MutableStateFlow<NewFeatureState>(NewFeatureState.Loading)
    val state: StateFlow<NewFeatureState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getData()
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    _state.value = NewFeatureState.Error(e.message ?: "Unknown error")
                }
                .collect { data ->
                    _state.value = NewFeatureState.Success(data)
                }
        }
    }
}
```

3. **Create Screen Composable**
```kotlin
@Composable
fun NewFeatureScreen(
    navController: NavHostController,
    viewModel: NewFeatureViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Feature") }) }
    ) { paddingValues ->
        when (state) {
            is NewFeatureState.Loading -> LoadingIndicator()
            is NewFeatureState.Success -> SuccessContent((state as NewFeatureState.Success).data)
            is NewFeatureState.Error -> ErrorCard((state as NewFeatureState.Error).message)
        }
    }
}
```

4. **Add Navigation Route**
```kotlin
// In ui/Navigation.kt
sealed class Screen(val route: String) {
    // ... existing routes
    object NewFeature : Screen("newfeature")
}

// In NavHost
composable(Screen.NewFeature.route) { NewFeatureScreen(navController) }
```

### Adding a Database Entity

1. **Create Entity**
```kotlin
// In database/entities/NewEntity.kt
@Entity(tableName = "new_table")
data class NewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "value") val value: Double
)
```

2. **Create DAO**
```kotlin
// In database/NewEntityDao.kt
@Dao
interface NewEntityDao {
    @Query("SELECT * FROM new_table")
    fun getAll(): Flow<List<NewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<NewEntity>)

    @Query("DELETE FROM new_table")
    suspend fun deleteAll()
}
```

3. **Add Migration**
```kotlin
// In database/AppDatabase.kt (migrations are inline)
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS new_table (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                value REAL NOT NULL
            )
        """.trimIndent())
    }
}
```

4. **Update AppDatabase**
```kotlin
@Database(
    entities = [
        // ... existing entities
        NewEntity::class
    ],
    version = 8,  // Increment version
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // ... existing DAOs
    abstract fun newEntityDao(): NewEntityDao
}
```

5. **Add to DatabaseModule**
```kotlin
@Provides
@Singleton
fun provideNewEntityDao(db: AppDatabase): NewEntityDao = db.newEntityDao()
```

### Adding a KRX Data Client

1. **Create Client** (in `core/network/krx/`)
```kotlin
@Singleton
class NewKrxClient @Inject constructor(
    private val krxStock: KrxStock  // or KrxEtf, KrxIndex
) {
    suspend fun fetchData(param: String): List<DataItem> = withContext(Dispatchers.IO) {
        try {
            withTimeout(30_000L) {
                val result = krxStock.getSomeData(param)
                result.map { DataItem(it.field1, it.field2) }
            }
        } catch (e: Exception) {
            AppLogger.getLogger("NewKrxClient").e("Error fetching data", e)
            emptyList()
        }
    }
}
```

2. **Inject via Hilt** (auto-injected via `@Inject constructor`)

### Adding a Background Worker

1. **Create Worker**
```kotlin
// In worker/NewUpdateWorker.kt
@HiltWorker
class NewUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: DataRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            repository.updateData()
            Result.success()
        } catch (e: Exception) {
            Log.e("NewUpdateWorker", "Update failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
```

2. **Schedule Worker**
```kotlin
// In WorkManagerHelper.kt or MainActivity.kt
fun scheduleNewUpdate(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = PeriodicWorkRequestBuilder<NewUpdateWorker>(1, TimeUnit.DAYS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            "new_update",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
}
```

---

## Key Conventions

### Code Style

#### Naming Conventions
- **Classes**: `PascalCase`
  - Activities: `*Activity` (MainActivity)
  - ViewModels: `*ViewModel` (HomeViewModel)
  - Screens: `*Screen` (HomeScreen)
  - Repositories: `*Repository` (DataRepository)
  - Workers: `*Worker` (StockUpdateWorker)
  - DAOs: `*Dao` (EtfDao)

- **Functions**:
  - Composables: `PascalCase` (HomeScreen, ErrorCard)
  - Suspend functions: `camelCase` (loadData, updateStocks)
  - Event handlers: `on*` (onSearchQueryChanged, onNavigateToList)

- **Variables**:
  - StateFlow backing: `_state` (private), `state` (public)
  - Constants: `UPPER_SNAKE_CASE`
  - Immutable collections: `List<>` (never `MutableList` in public APIs)

#### State Management Rules

**ALWAYS:**
- Use sealed classes for comprehensive state modeling
- Expose immutable StateFlow (never MutableStateFlow publicly)
- Update state through ViewModel methods only
- Use `collectAsState()` in Composables

**NEVER:**
- Expose mutable state directly
- Update state outside ViewModel
- Use LiveData (this project uses StateFlow exclusively)
- Mutate state fields directly (use `.value = ...`)

```kotlin
// ✅ CORRECT
private val _state = MutableStateFlow<UiState>(UiState.Loading)
val state: StateFlow<UiState> = _state.asStateFlow()

fun updateData() {
    _state.value = UiState.Success(data)
}

// ❌ WRONG
val state = MutableStateFlow<UiState>(UiState.Loading)  // Exposed mutable state
```

#### Coroutine Best Practices

**Dispatcher Rules:**
- **Main**: UI updates, StateFlow emissions
- **IO**: Database, network, Python calls, file I/O
- **Default**: CPU-intensive calculations (EMA, MACD)

**Scope Rules:**
- ViewModels: Use `viewModelScope` (auto-cancels on clear)
- Activities/Fragments: Use `lifecycleScope`
- Manual scopes: Always cancel when done

```kotlin
// ✅ CORRECT: Explicit dispatcher
suspend fun loadFromDb() = withContext(Dispatchers.IO) {
    dao.getAllEtfs()
}

fun observeData() {
    repository.getDataFlow()
        .flowOn(Dispatchers.IO)
        .onEach { _state.value = UiState.Success(it) }
        .launchIn(viewModelScope)
}

// ❌ WRONG: No dispatcher specified for blocking operation
suspend fun loadFromDb() {
    dao.getAllEtfs()  // May block caller's thread!
}
```

#### Database Access

**ALWAYS:**
- Use DAOs for all database operations
- Use suspend functions for one-time queries
- Use Flow for continuous observations
- Run on Dispatchers.IO

**NEVER:**
- Use raw SQL unless absolutely necessary
- Block the main thread with database operations
- Forget to add migrations when changing schema

```kotlin
// ✅ CORRECT
@Dao
interface EtfDao {
    @Query("SELECT * FROM etfs")
    fun getAllEtfs(): Flow<List<Etf>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(etfs: List<Etf>)
}

// ❌ WRONG
@Dao
interface EtfDao {
    @Query("SELECT * FROM etfs")
    fun getAllEtfs(): List<Etf>  // Blocking call, no Flow
}
```

#### KRX Client Integration

**ALWAYS:**
- Set appropriate timeout for KRX calls (30s standard, 180s for oscillator)
- Use `withContext(Dispatchers.IO)` for network operations
- Handle exceptions and log errors via `AppLogger`

```kotlin
// ✅ CORRECT
suspend fun fetchData(): List<Item> = withContext(Dispatchers.IO) {
    try {
        withTimeout(30_000L) {
            val result = krxStock.getData(param)
            result.map { it.toDomain() }
        }
    } catch (e: Exception) {
        AppLogger.getLogger(TAG).e("KRX call failed", e)
        emptyList()
    }
}
```

#### Compose Guidelines

**ALWAYS:**
- Keep screens stateless (state from ViewModel)
- Use lambda callbacks for events
- Extract sub-composables for reusability
- Use `remember` for expensive calculations
- Use `LaunchedEffect` for side effects

**NEVER:**
- Perform business logic in Composables
- Create ViewModels inside Composables manually
- Use global mutable state

```kotlin
// ✅ CORRECT
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToList: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Button(onClick = onNavigateToList) {
        Text("View List")
    }
}

// ❌ WRONG
@Composable
fun HomeScreen() {
    val viewModel = HomeViewModel(repository)  // Manual creation
    var localState by remember { mutableStateOf("") }

    // Business logic in composable
    LaunchedEffect(Unit) {
        repository.loadData()
    }
}
```

### File Organization

**Per Feature:**
```
screens/feature/
├── FeatureScreen.kt      # UI composable
├── FeatureViewModel.kt   # State management
└── FeatureState.kt       # State model (optional, can be in ViewModel file)
```

**Shared Components:**
```
ui/components/
├── ChartComponents.kt        # Chart-related composables
├── Material3Components.kt    # M3 themed components
└── StateCards.kt             # Status display cards
```

---

## Common Tasks & Solutions

### Task: Add a New Chart Type

**Location**: `ui/components/ChartComponents.kt`

```kotlin
@Composable
fun NewChart(
    data: List<DataPoint>,
    modifier: Modifier = Modifier
) {
    val chartEntryModel = entryModelOf(
        data.mapIndexed { index, point ->
            entryOf(index, point.value)
        }
    )

    Chart(
        chart = lineChart(),
        model = chartEntryModel,
        modifier = modifier
    )
}
```

### Task: Update Theme Colors

**Location**: `ui/theme/Color.kt` and `ui/theme/Theme.kt`

```kotlin
// 1. Define color in Color.kt
val NewPrimary = Color(0xFF6750A4)

// 2. Update color scheme in Theme.kt
private val LightColorScheme = lightColorScheme(
    primary = NewPrimary,
    // ... other colors
)
```

### Task: Add User Setting

**Location**: Database entity + ViewModel + Screen

```kotlin
// 1. Define setting key
const val SETTING_NEW_OPTION = "new_option"

// 2. Add to ViewModel
suspend fun updateSetting(value: String) {
    repository.setSetting(SETTING_NEW_OPTION, value)
}

fun getSetting(): Flow<String?> {
    return repository.getSetting(SETTING_NEW_OPTION)
}

// 3. Use in UI
@Composable
fun SettingsScreen() {
    val setting by viewModel.getSetting().collectAsState(initial = null)

    Switch(
        checked = setting == "true",
        onCheckedChange = { viewModel.updateSetting(it.toString()) }
    )
}
```

### Task: Handle Database Migration Errors

**Symptoms**: App crashes on upgrade with "Migration didn't properly handle..."

**Solution:**
```kotlin
// 1. Add migration in Migrations.kt
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Write SQL migration
        database.execSQL("ALTER TABLE table_name ADD COLUMN new_col TEXT")
    }
}

// 2. Add to AppDatabase
@Database(entities = [...], version = Y)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "etf_db")
                .addMigrations(MIGRATION_1_2, ..., MIGRATION_X_Y)
                .build()
        }
    }
}
```

### Task: Fix Compose Recomposition Issues

**Symptoms**: UI not updating when state changes

**Common Causes:**
1. Using mutable state instead of StateFlow
2. Not collecting state in Composable
3. Updating state on wrong thread

**Solution:**
```kotlin
// ✅ CORRECT
@HiltViewModel
class ViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(InitialState)
    val state: StateFlow<State> = _state.asStateFlow()

    fun update() {
        viewModelScope.launch {  // Correct scope
            _state.value = NewState  // Correct update
        }
    }
}

@Composable
fun Screen(viewModel: ViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()  // Collect state
    Text(state.toString())  // Will recompose
}
```

---

## Important Files Reference

### Entry Points
- **`MainActivity.kt`**: App entry, theme setup, permissions, initial data loading
- **`EtfMonitorApp.kt`**: Hilt application, Python engine initialization, WorkManager config

### Navigation
- **`navigation/Navigation.kt`**: All screen routes (14 screens), NavHost setup

### ViewModels (13 total)

#### Sealed State Class ViewModels (11)
| ViewModel | State Class | States | Key Dependencies |
|-----------|-------------|--------|------------------|
| **HomeViewModel** | HomeState | Loading, Idle, Initializing, Updating, Success, Error + HomeSummary | 4 Repos, EtfDao, Context |
| **EtfListViewModel** | ListState | Loading, Success, Empty, Error | DataRepository |
| **DetailViewModel** | DetailState | Loading, Success, Error | DataRepository, SavedStateHandle |
| **StockTrendViewModel** | TrendState | Loading, Success, Error | DataRepository, SavedStateHandle |
| **PredictionViewModel** | PredictionState | Initial, NoPredictions, HasPredictions, Loading, Success, Error | EnhancedPredictionRepository |
| **FearGreedViewModel** | FearGreedState | Loading, Idle, Initializing, Updating, Success, Error | FearGreedRepository, EtfDao |
| **OscillatorViewModel** | OscillatorState | Idle, Loading, Success(7 data items), Error | 4 Clients, StockRepository |
| **MarketDepositViewModel** | MarketDepositState | Idle, Loading, Success, Error | MarketDepositRepository |
| **MarketOscillatorViewModel** | MarketOscillatorState | Loading, Idle, Initializing, Updating, Success, Error | MarketOscillatorRepository |
| **AdvancedDashboardViewModel** | AdvancedDashboardState | Loading, Success(AdvancedDashboardData), Error | AdvancedAnalysisRepository, 8 DAOs |
| **NewAIAnalysisViewModel** | NewAIAnalysisState | 11 states including analysis + chat states | 4 Repositories |

#### Individual StateFlow ViewModels (2)
| ViewModel | StateFlows Count | Purpose |
|-----------|-----------------|---------|
| **SettingsViewModel** | 25+ | Themes, API keys, schedules, font scales, chart colors |
| **StatisticsViewModel** | 12+ | Multi-column sorting, search, analysis results |

#### Common ViewModel Patterns
```kotlin
// First-Run Dialog Pattern (used by HomeViewModel, FearGreedViewModel, MarketOscillatorViewModel)
private val _showFirstRunDialog = MutableStateFlow(false)
fun checkFirstRun() {
    viewModelScope.launch {
        val dismissed = etfDao.getSetting("${feature}_dialog_dismissed")
        if (!hasData && dismissed != "true") _showFirstRunDialog.value = true
    }
}

// Search Debounce Pattern (used by EtfListViewModel, OscillatorViewModel)
_searchQuery
    .debounce(300)
    .flatMapLatest { query -> repository.search(query) }
    .launchIn(viewModelScope)
```

### Database
- **`database/AppDatabase.kt`**: Room database (21 entities, 18 DAOs, 16 migrations v1→v17), migrations defined inline
- **`database/entities/`**: 20 entity files (21 entities - AIChatSession defined in AIChatMessage.kt)
- **`database/*Dao.kt`**: 18 DAO interfaces in database/ folder
- **`database/Converters.kt`**: TypeConverters for `List<String>` and `List<Long>` (uses org.json.JSONArray)

#### Database Entities (21 total)
| Entity | Table | Primary Key | Critical Notes |
|--------|-------|-------------|----------------|
| Etf | etfs | ticker (String) | Minimal: ticker + name |
| **Holding** | holdings | (etfTicker, stockTicker, date) | **Uses Short/Int compression** - see Critical Notes |
| Stock | stocks | ticker (String) | Added in v13, has inferMarket() helper |
| StockAnalysisData | stock_analysis_data | ticker (String) | **name removed in v13** - requires JOIN |
| Setting | settings | key (String) | Simple KV store |
| SearchHistory | search_history | id (Int, auto) | User search tracking, historyType added in v17 |
| MarketDeposit | market_deposits | date (String) | Daily deposit/credit |
| FearGreedIndex | fear_greed_index | id (String: "KOSPI-2024-01-01") | 12 indicator columns |
| MarketOscillatorData | market_oscillator | id (String: "KOSPI-2025-01-01") | Overbought/oversold |
| MarketIndex | market_index | id (String: "KOSPI-2025-01-01") | OHLCV + changeRate |
| DailyEtfStatistics | daily_etf_statistics | date (String) | 14-column aggregates |
| CorrelationAnalysisResult | correlation_analysis_result | id (String) | 12+ correlation metrics |
| AIAnalysisResult | ai_analysis_result | id (UUID String) | AI interpretation |
| AIChatSession | ai_chat_session | id (UUID String) | Chat session |
| AIChatMessage | ai_chat_message | id (UUID String) | Chat messages |
| StockPrediction | stock_predictions | id ("{ticker}-{date}") | ML predictions (legacy v1) |
| SectorAnalysis | sector_analysis | id ("{sector}-{date}") | Sector Fear/Greed |
| EtfCorrelationCache | etf_correlation_cache | id ("{etf1}-{etf2}-{date}") | ETF pair correlation |
| LiquidityAnalysis | liquidity_analysis | date (String) | Market liquidity |
| **PriceCache** | price_cache | (ticker, date) | ML prediction price cache, added in v15 |
| **EnhancedPrediction** | enhanced_predictions | id (UUID String) | 28-feature ensemble ML predictions, added in v15 |
| **StockIndicatorAIResult** | stock_indicator_ai_result | id (UUID String) | Stock-indicator AI analysis, added in v16 |

#### Critical Migrations
| Migration | Impact | Action Required |
|-----------|--------|-----------------|
| **7→8** | Holding restructure | Float → Short/Int, added snapshotType, 7 indices |
| **12→13** | Stock expansion | name removed from StockAnalysisData → use JOIN |
| **13→14** | Advanced analysis | Added SectorAnalysis, EtfCorrelationCache, LiquidityAnalysis |
| **14→15** | Enhanced ML predictions | Added PriceCache (price history), EnhancedPrediction (28 features, ensemble) |
| **15→16** | Stock-indicator AI | Added StockIndicatorAIResult for stock-specific AI analysis |
| **16→17** | Search history types | Added historyType to SearchHistory for menu-specific history |

### Repositories (13 total)

#### Core Data Repositories
| Repository | Dependencies | Key Methods | Error Pattern |
|------------|--------------|-------------|---------------|
| **EtfRepository** | EtfDao, DailyEtfStatisticsDao, StockDao, PyKrxClient | `initializeData()`, `updateData()`, `getComparison()` | Flow<DataProgress> |
| **StockRepository** | StockDao, OscillatorPyClient | `syncFromHoldings()`, `initializeStocks()` | Result<Int> |
| **StockAnalysisRepository** | StockAnalysisDao, StockDao, OscillatorPyClient | `getStockAnalysis()` (24h cache) | nullable return |
| **MarketIndexRepository** | MarketIndexDao, MarketIndexPyClient | `initializeMarketIndex()`, `updateMarketIndex()` | Result<Int> |
| **MarketDepositRepository** | MarketDepositDao, OscillatorPyClient | `getOrUpdateMarketData()` (12h smart cache) | Result<Int> |
| **FearGreedRepository** | FearGreedDao, Python (direct) | `initializeFearGreed(days)` → request 3x days | Result<Int> |
| **MarketOscillatorRepository** | MarketOscillatorDao, OscillatorPyClient | `initializeMarketData()` (365 days) | Result<Int> |

#### Analysis Repositories
| Repository | Dependencies | Key Methods | Notes |
|------------|--------------|-------------|-------|
| **AIAnalysisRepository** | AIApiClientFactory, 5 DAOs | `analyzeMarket()`, `generateQuickSignal()` | Temperature: 0.5 analysis, 0.3 quick |
| **AIChatRepository** | AIChatDao, AIApiClientFactory | `createSession()`, `sendMessage()` | Max 10 messages for context |
| **CorrelationAnalysisRepository** | CorrelationAnalyzer, 4 DAOs, AIApiClientFactory | `runCorrelationAnalysis()`, `interpretWithAI()` | 7+ correlation metrics |
| **StatisticsAnalysisRepository** | EtfDao, MarketIndexDao, DailyEtfStatisticsDao | `calculateCorrelation()` (Pearson) | Min 10 data points |
| **EnhancedPredictionRepository** | EnhancedPredictionDao, PriceCacheDao, EtfDao, EnhancedPredictorClient | `runEnhancedPrediction()` | 28 features, ensemble models |
| **AdvancedAnalysisRepository** | 9 DAOs | 5 analysis types: MarketCapFlow, Divergence, Liquidity, Sector, ETF Correlation | Complex multi-factor analysis |

### AI Integration (11 files)

#### API Client Configuration
| Client | API Endpoint | Default Model | Timeout |
|--------|-------------|---------------|---------|
| **ClaudeApiClient** | `api.anthropic.com/v1/messages` | `claude-3-5-sonnet-20241022` | 60s |
| **GeminiApiClient** | `generativelanguage.googleapis.com/v1beta/models` | `gemini-2.5-flash` | 60s |

#### AI Files (in `core/network/ai/`)
- **`AIApiClient.kt`**: Interface with `analyzeMarket()`, `chat()`, `isApiAvailable()`, `testApiKey()`, `listModels()`
- **`ClaudeApiClient.kt`**: Anthropic API (headers: `x-api-key`, `anthropic-version: 2023-06-01`)
- **`GeminiApiClient.kt`**: Google API with `validateAndFixModelName()`, SAFETY/RECITATION block handling
- **`AIApiClientFactory.kt`**: Factory pattern for client selection based on `ApiKeyProvider.getSelectedProvider()`
- **`AIResponseParser.kt`**: Extracts JSON from `\`\`\`json...\`\`\`` blocks or raw `{...}`, parses Korean signal names
- **`MarketAnalysisPrompts.kt`**: Templates for `COMPREHENSIVE`, `ETF_ONLY`, `TECHNICAL_ONLY`, `SENTIMENT_ONLY` analysis
- **`ApiKeyProvider.kt`**: Interface for API key management
- **`SharedPreferencesApiKeyProvider.kt`**: **AES256-GCM encrypted** storage via Android Keystore
- **`AIModel.kt`**: Model definitions with id, name, provider, contextWindow, maxOutputTokens
- **`AIProvider.kt`**: Enum (CLAUDE, GEMINI) with `toDisplayName()`, `fromString()`
- **`MarketSignal.kt`**: Signal data class with `SignalType` (STRONG_BUY→STRONG_SELL), `RiskLevel` (LOW/MEDIUM/HIGH)

### Analysis (in `core/analysis/`)
- **`CorrelationAnalyzer.kt`**: ETF flow vs market correlation
- **`Backtester.kt`**: Strategy backtesting
- **`TimeSeriesData.kt`**: Time series data structures

### KRX Data Clients (6 Kotlin Clients in `core/network/krx/`)
- **`KrxDataClient.kt`**: ETF/stock data (replaces PyKrxClient)
  - `getFilteredEtfList()`, `getEtfList()`, `getHoldings()`, `getBusinessDays()`, `getStockName()`
  - Uses: kotlin_krx `KrxEtf`, `KrxStock`, `KrxIndex`
  - Retry: 2 retries for holdings data
- **`StockDataClient.kt`**: Stock analysis data (replaces OscillatorPyClient data methods)
  - `searchStock()`, `getStockAnalysis()`, `getStockOhlcv()`, `getAllStocksList()`
  - Uses: kotlin_krx `KrxStock`
- **`MarketIndexClient.kt`**: Market index data (replaces MarketIndexPyClient)
  - `fetchMarketIndices()`, `fetchRecentDays()`, `getLatestIndex()`
  - Uses: kotlin_krx `KrxIndex`
- **`FearGreedClient.kt`**: Fear & Greed indicator (replaces feargreed.py)
  - `runAnalysis()` - Direct KRX HTTP calls for options + index data
  - 5 indicators @ 20% each (Momentum, PCR, VIX, Spread, RSI)
- **`DepositScraper.kt`**: Market deposit data (replaces deposit_scraper.py)
  - `getMarketDepositData()` - Jsoup HTML parsing from Naver Finance
- **`BloodIndicatorClient.kt`**: Blood indicator (replaces blood_indicator.py)
  - `fetchBloodIndicator()` - Yahoo Finance + FRED API

### UI Theme (in `core/ui/theme/`)
- **`Theme.kt`**: Material Design 3 color schemes, typography
- **`ThemeManager.kt`**: Global theme state (dark mode, font, colors)

### Background Tasks (8 workers + 1 utility in `core/worker/`)
- **`EtfUpdateWorker.kt`**: Daily ETF data refresh
- **`StockUpdateWorker.kt`**: Daily stock data refresh
- **`DataArchiveWorker.kt`**: Data archiving
- **`AdvancedAnalysisWorker.kt`**: Advanced analysis tasks
- **`MarketOscillatorUpdateWorker.kt`**: Market oscillator updates
- **`MarketDepositUpdateWorker.kt`**: Market deposit updates
- **`FearGreedUpdateWorker.kt`**: Fear & Greed index updates
- **`MarketIndexUpdateWorker.kt`**: Market index updates
- **`WorkManagerHelper.kt`**: WorkManager scheduling utilities (not a Worker)

### Services (in `core/service/`)
- **`DataCollectionService.kt`**: Foreground ETF sync service
- **`CollectionState.kt`**: Collection state management

### Dependency Injection (5+ Modules)

#### Module Summary
| Module | Manual Providers | Provides |
|--------|-----------------|----------|
| **DatabaseModule** | 19 | AppDatabase + 18 DAOs |
| **KrxModule** | 3 | KrxStock, KrxEtf, KrxIndex singletons |
| **AIModule** | 9 | ApiKeyProvider, 2 API clients, Factory, 2 Analyzers, 3 Repositories |
| **WorkerModule** | 1 | WorkManager |
| **Feature Modules** | varies | Feature-specific repository bindings |

#### Auto-Injected Components (via @Inject constructor)
- **KRX Clients**: KrxDataClient, StockDataClient, MarketIndexClient, FearGreedClient, DepositScraper, BloodIndicatorClient
- **Calculators**: MarketOscillatorCalculator, TrendSignalNativeCalculator
- **Repositories**: Most repositories auto-injected via `@Binds` in feature modules

#### DI Dependency Flow
```
AppDatabase → DAOs → Repositories → ViewModels
                ↓
KrxModule → KrxStock/KrxEtf/KrxIndex → KRX Clients → Repositories
                ↓
ApiKeyProvider → AI Clients → AIApiClientFactory → AI Repositories
```

#### Database Name
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "etf_monitor.db")
```

### Build Configuration
- **`gradle/libs.versions.toml`**: Version catalog for all dependencies
- **`app/build.gradle.kts`**: App module config

---

## Gotchas & Known Issues

### 1. Room Migration Testing
**Status**: ✅ Migration tests implemented in Phase 4.

**Location**: `app/src/androidTest/java/com/etfmonitor/core/database/MigrationTest.kt`

**Coverage**: All 18 migrations (v1→v19) with individual and full migration tests.

**Example**:
```kotlin
@Test
fun migrate7To8() {
    val db = helper.createDatabase(TEST_DB, 7)
    // Insert test data
    db.close()

    helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)
}
```

### 2. WorkManager Not Running
**Issue**: Workers not executing on schedule.

**Debug Steps:**
1. Check battery optimization settings (may kill WorkManager)
2. Verify constraints (network, charging)
3. Check LogCat for WorkManager errors
4. Use `adb shell dumpsys jobscheduler` to inspect scheduled jobs

### 3. Material Design 3 Dark Mode Shadows
**Issue**: Shadows not visible in dark mode.

**Solution**: Use white/light shadows for dark theme (already implemented):
```kotlin
val shadowColor = if (isSystemInDarkTheme()) Color.White else Color.Black
```

### 4. Compose State Hoisting
**Issue**: State updates not propagating correctly.

**Rule**: Always hoist state to the appropriate level:
- Screen-level state → ViewModel
- Component-level state → Parent composable
- Local UI state → `remember { mutableStateOf() }`

---

## Testing & Build

### Running the App

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install and run on device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run Android instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

### Test Structure

```
app/src/test/java/com/etfmonitor/              # Unit Tests
├── TestUtils.kt                               # Shared test utilities
├── core/
│   ├── analysis/
│   │   └── CorrelationAnalyzerTest.kt        # Pearson correlation, signal generation
│   └── network/python/
│       └── PyKrxClientTest.kt                # Python integration, retry logic, JSON parsing
├── feature/
│   ├── home/presentation/
│   │   └── HomeViewModelTest.kt              # State transitions, first-run dialog
│   ├── etf/data/repository/
│   │   └── EtfRepositoryImplTest.kt          # Holding comparison, settings management
│   └── market/data/repository/
│       └── FearGreedRepositoryImplTest.kt    # Data retrieval, cache logic

app/src/androidTest/java/com/etfmonitor/       # Instrumented Tests
└── core/database/
    └── MigrationTest.kt                       # All 16 migrations (v1→v17)
```

### Testing Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| JUnit5 | 5.10.2 | Test framework |
| MockK | 1.13.10 | Kotlin mocking |
| Turbine | 1.1.0 | Flow testing |
| Coroutines Test | 1.10.2 | Coroutine testing |
| Room Testing | 2.8.3 | Migration testing |
| AndroidX Test | 1.5.x | Instrumented tests |

### Writing Tests

**ViewModel Test Pattern**:
```kotlin
@ExtendWith(MainDispatcherExtension::class)
class MyViewModelTest {
    @Test
    fun `state transitions correctly on data load`() = runTest {
        val viewModel = MyViewModel(mockRepository)
        viewModel.state.test {
            assertEquals(State.Loading, awaitItem())
            assertEquals(State.Success(data), awaitItem())
        }
    }
}
```

**Repository Test Pattern**:
```kotlin
class MyRepositoryTest : RepositoryTest() {
    @Test
    fun `returns cached data when fresh`() = runRepoTest {
        coEvery { dao.getData() } returns flowOf(testData)
        val result = repository.getData().first()
        assertEquals(testData, result)
    }
}
```

### Build Variants

- **Debug**: Development builds with logging, no minification
- **Release**: Production builds with minification and resource shrinking enabled

### ABI Support

**Supported**: arm64-v8a, x86_64 (64-bit only)
**Not Supported**: armeabi-v7a, x86 (32-bit)

### Minimum Device Requirements

- Android 8.0 (API 26) or higher
- 64-bit processor
- Internet connection (for data sync)
- ~100MB storage for app + data

---

## Git Workflow

### Commit Guidelines

**Format**: `<type>: <description>`

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code restructuring
- `style`: UI/UX changes
- `chore`: Maintenance tasks
- `docs`: Documentation

**Examples**:
```
feat: Add market oscillator screen with MACD chart
fix: Fix BorderStroke null brush error in IdleCard
refactor: Extract chart components to ChartComponents.kt
style: Upgrade to Material Design 3 color system
```

### Pre-commit Checklist

- [ ] Code compiles without errors
- [ ] No new lint warnings
- [ ] Database migrations added if schema changed
- [ ] kotlin_krx API calls wrapped in `withContext(Dispatchers.IO)`
- [ ] StateFlow properly exposed (not MutableStateFlow)
- [ ] Coroutine dispatchers explicitly specified
- [ ] No hardcoded strings (use string resources)

---

## Quick Reference

### Adding Dependencies

**In `gradle/libs.versions.toml`:**
```toml
[versions]
newlib = "1.0.0"

[libraries]
newlib = { module = "com.example:newlib", version.ref = "newlib" }
```

**In `app/build.gradle.kts`:**
```kotlin
dependencies {
    implementation(libs.newlib)
}
```

### Hilt Annotations Quick Ref

| Annotation | Usage |
|------------|-------|
| `@HiltAndroidApp` | Application class |
| `@AndroidEntryPoint` | Activity, Fragment, Service |
| `@HiltViewModel` | ViewModel |
| `@HiltWorker` | Worker (with `@AssistedInject`) |
| `@Singleton` | Single instance app-wide |
| `@Inject` | Constructor injection |

### Compose Quick Ref

| Function | Usage |
|----------|-------|
| `remember { }` | Cache value across recompositions |
| `rememberCoroutineScope()` | Get coroutine scope in Composable |
| `LaunchedEffect(key) { }` | Side effect on key change |
| `collectAsState()` | Collect Flow as State |
| `derivedStateOf { }` | Compute derived state |

---

## Resources & Documentation

- **Jetpack Compose**: https://developer.android.com/jetpack/compose
- **Material Design 3**: https://m3.material.io/
- **Hilt**: https://dagger.dev/hilt/
- **Room**: https://developer.android.com/training/data-storage/room
- **Coroutines**: https://kotlinlang.org/docs/coroutines-overview.html
- **krxkt**: Internal module at `krxkt/` (com.krxkt, pure JVM library)

---

## Claude Code - Coding Guidelines

### Core Philosophy: Minimal Engineering

#### DO NOT Over-Engineer
- Make ONLY changes that are directly requested or clearly necessary
- Keep solutions simple and focused
- Do NOT add unrequested features, refactor code, or make "improvements"
- Bug fixes do NOT require cleaning up surrounding code
- Simple features do NOT need additional configurability

#### Trust the System
- Do NOT add error handling, fallbacks, or validation for scenarios that cannot occur
- Trust internal code and framework guarantees
- Validate ONLY at system boundaries (user input, external APIs)

#### Avoid Premature Abstraction
- Do NOT create helpers, utilities, or abstractions for one-time tasks
- Do NOT design for hypothetical future requirements
- The correct complexity level is the MINIMUM required for the current task
- Reuse existing abstractions when possible and follow DRY principles

#### Quality Standards
- Write high-quality, general-purpose solutions using available standard tools
- Focus on understanding problem requirements and implementing the correct algorithm
- Provide grounded, hallucination-free answers unless confident in the exact answer

---

### UI/UX Design Guidelines

#### Typography
- Choose beautiful, distinctive, and interesting fonts
- AVOID generic fonts like Arial, Inter, Roboto, system fonts
- Select unique choices that enhance aesthetics

#### Color & Theme
- Commit to a cohesive aesthetic
- Dominant colors with sharp accents perform better than timid, evenly-distributed palettes
- Draw inspiration from IDE themes and cultural aesthetics
- AVOID clichéd color schemes (especially purple gradients on white backgrounds)

#### Motion & Animation
- Use animations for effects and micro-interactions
- Focus on high-impact moments
- One well-orchestrated page load with staggered reveals (animation-delay) creates more delight than scattered micro-interactions

#### Backgrounds
- Create atmosphere and depth rather than defaulting to solid colors
- Layer gradients, use geometric patterns, or add contextual effects that match the overall aesthetic

#### Avoid Generic AI Aesthetics
- Overused font families (Inter, Roboto, Arial, system fonts)
- Clichéd color schemes (purple gradients on white)
- Predictable layouts and component patterns
- Cookie-cutter designs lacking contextual character

---

### Summary Rules

```
✓ ONLY requested changes
✓ Minimal complexity
✓ Trust framework guarantees
✓ Validate at boundaries only
✓ Unique, beautiful design choices

✗ NO unrequested features
✗ NO premature abstraction
✗ NO unnecessary error handling
✗ NO hypothetical future design
✗ NO generic AI aesthetics
```

---

## AI Assistant Guidelines

### When Working on This Project

1. **Read Before Editing**: Always read the file completely before making changes
2. **Follow Patterns**: Use existing patterns (sealed classes, StateFlow, etc.)
3. **Explicit Dispatchers**: Never omit coroutine dispatchers
4. **Type Safety**: Prefer sealed classes over enums for state
5. **Immutability**: Expose immutable types in public APIs
6. **Migration First**: Add database migrations before changing schema
7. **Test KRX Clients**: Verify KRX API responses match expected format
8. **Material Design 3**: Follow M3 guidelines for new UI components
9. **State Hoisting**: Keep Composables stateless
10. **Document Complex Logic**: Add KDoc for non-obvious functions

### Common Pitfalls to Avoid

#### General
- ❌ Exposing `MutableStateFlow` publicly
- ❌ Running database operations without `Dispatchers.IO`
- ❌ Creating ViewModels manually (use `hiltViewModel()`)
- ❌ Forgetting timeout for KRX network calls (always use `withTimeout`)
- ❌ Changing database schema without migration
- ❌ Using LiveData (this project uses StateFlow)
- ❌ Hardcoding strings (use string resources)
- ❌ Blocking main thread with suspend functions

#### Database-Specific (Critical)
- ❌ Direct `Holding` construction (use `Holding.create()` factory)
- ❌ Querying `StockAnalysisData` without JOIN to `stocks` table
- ❌ Forgetting type conversion for compressed Holding values in queries
- ❌ Not using LIMIT clauses in ranking/list queries (causes OOM)
- ❌ Assuming `name` field exists in `stock_analysis_data` (removed in v13)

#### KRX Client-Specific
- ❌ Using 30s timeout for `MarketOscillatorCalculator` (needs 180s)
- ❌ Requesting exact days for FearGreed (request 3x due to MA data loss)
- ❌ Not using `withContext(Dispatchers.IO)` for KRX network calls

#### AI-Specific
- ❌ Calling AI without checking `isApiKeyConfigured` first
- ❌ Assuming English-only signal names (parser handles Korean: 강력매수, 매수, 중립, 매도, 강력매도)
- ❌ Not handling Gemini SAFETY/RECITATION blocks

### Code Review Checklist

Before submitting changes, verify:
- [ ] Follows MVVM pattern
- [ ] StateFlow properly exposed
- [ ] Coroutine dispatchers specified
- [ ] Database migrations included
- [ ] Hilt annotations correct
- [ ] No memory leaks (proper scope usage)
- [ ] Material Design 3 compliance
- [ ] Error handling implemented
- [ ] Logging added for debugging

---

**Last Updated**: 2026-02-12
**Codebase Version**: Schema v19, ~270 Kotlin files, 0 Python scripts (fully native)
**Data Source**: krxkt/ internal module (native Kotlin KRX API, com.krxkt package)
**Review Score**: 84/100 (Security: 92, Performance: 88, Stability: 91, Test Coverage: 65)
**Maintainer**: gmdjlee

---

## Change History

### 2025-12-06 - Package Name & Structure Corrections
- Fixed package name from `com.example.etfmonitor` to `com.etfmonitor`
- Corrected database structure: DAOs are in `database/` directly, not `database/DAOs/`
- Clarified entity structure: 18 files containing 19 entities (AIChatSession in AIChatMessage.kt)
- Updated migrations location: inline in AppDatabase.kt
- Corrected workers count: 6 workers + 1 utility (WorkManagerHelper)
- Updated release build info: minification and resource shrinking now enabled

### 2025-12-06 - Critical Implementation Notes Added
- Added "Critical Implementation Notes" section at top of document
- Documented Holding entity memory optimization (Short/Int compression)
- Documented StockAnalysisData JOIN requirement after Migration 12→13
- Added Python client timeout requirements table (30s, 120s, 180s)
- Added repository caching strategies table
- Documented FearGreed 3x data collection requirement
- Added complete database entity reference table (19 entities)
- Added critical migrations summary (7→8, 12→13, 13→14)
- Expanded repository documentation with dependencies and error patterns
- Added ViewModels reference table (13 ViewModels, state patterns)
- Documented First-Run Dialog and Search Debounce patterns
- Added Python scripts reference table with return formats
- Expanded AI integration documentation (endpoints, models, timeouts)
- Added DI module summary (5 modules, 43 providers)
- Expanded Common Pitfalls with Database, Python, and AI-specific issues
- Fixed HomeState example to match actual implementation (7 states)

### 2025-12-15 - v1 Prediction System Removal & Codebase Optimization
- **Removed v1 prediction system** (~960 lines):
  - Deleted `stock_predictor.py` (basic 7-feature model)
  - Deleted `StockPredictorPyClient.kt`
  - Deleted `StockPredictionRepository.kt`
- **Migrated to v2 prediction system** (28 features, ensemble models):
  - `PredictionViewModel` now uses `EnhancedPredictionRepository`
  - `PredictionScreen` updated to use `EnhancedPrediction` entity
  - Added model selection (voting, xgboost, lightgbm, random_forest, gradient_boosting)
- **DI module cleanup**:
  - Removed `StockPredictorPyClient` provider from `PythonModule.kt`
  - Removed `StockPredictionRepository` provider from `RepositoryModule.kt`
  - Removed `StockPredictionDao` provider from `DatabaseModule.kt`
- **DAO optimization**:
  - Removed unused `getLatestPredictionsSuspend()` from `EnhancedPredictionDao`
  - Removed unused suspend variant from `EnhancedPredictionRepository`
- **Python cleanup**:
  - Removed unused functions from `market.py` (`get_realtime_oscillator`, `fetch_market_index`)
- **Documentation updated** to reflect new v2 prediction system

### 2025-12-24 - Clean Architecture Migration Started

**Phase 1-6: Feature Module Setup**
- Created `core/` package structure with common utilities, network clients, UI theme, workers, services, DI
- Created 6 feature modules: home, etf, stock, market, analysis, settings
- Each feature follows domain/data/presentation layer pattern

### 2025-12-25 - Clean Architecture Migration Complete (Phase 7-8)

**Phase 7: Legacy Repository Elimination (Complete)**

All legacy repositories migrated to feature modules:
- `DataRepository` → `EtfRepositoryImpl` (Phase 7.1)
- `StockRepository`, `StockAnalysisRepository` → `StockRepositoryImpl`, `StockAnalysisRepositoryImpl` (Phase 7.2)
- `FearGreedRepository`, `MarketDepositRepository`, `MarketOscillatorRepository`, `MarketIndexRepository` → Feature implementations (Phase 7.3)
- `AIAnalysisRepository`, `AIChatRepository`, `CorrelationAnalysisRepository`, `AdvancedAnalysisRepository`, `StatisticsAnalysisRepository` → Feature implementations (Phase 7.4)
- `TimeSeriesAnalysisRepository` → `TimeSeriesAnalysisHelper` (internal utility) (Phase 7.5)
- `RepositoryModule.kt` deleted, all bindings moved to feature modules

**Phase 8: Final Cleanup & Documentation**

Deleted legacy folders:
- `repository/` - All 13 legacy repository files
- `ui/screens/` - All screens migrated to feature modules
- `database/` - Moved to `core/database/`
- `oscillator/` - Moved to `core/analysis/`
- `di/` - Consolidated into `core/di/`

**Final Architecture** (~255 Kotlin files):
```
com/etfmonitor/
├── core/          (97 files) - Shared infrastructure
├── feature/       (155 files) - 6 feature modules
├── navigation/    (1 file) - App navigation
├── MainActivity.kt
└── EtfMonitorApp.kt
```

**DI Modules** (10 total, all in feature/*/di/ or core/di/):
- Core: DatabaseModule, WorkerModule, KrxModule, AIModule
- Features: HomeModule, EtfModule, StockModule, MarketModule, AnalysisModule, SettingsModule

### 2025-12-27 - Quality Plan Phase 5 (Documentation)

**Database Schema v14 → v17**
- Added 3 new entities: `PriceCache`, `EnhancedPrediction`, `StockIndicatorAIResult`
- Added 3 new DAOs: `PriceCacheDao`, `EnhancedPredictionDao`, `StockIndicatorAIResultDao`
- Added migrations 14→15, 15→16, 16→17

**Migration Details**:
- **v14→15**: ML prediction infrastructure (PriceCache for price history, EnhancedPrediction for 28-feature ensemble)
- **v15→16**: Stock-indicator AI analysis (StockIndicatorAIResult)
- **v16→17**: Search history separation (historyType field for menu-specific history)

**Testing Infrastructure** (Phase 4):
- Unit tests: 6 test files covering ViewModel, Repository, Analysis components
- Android tests: MigrationTest covering all 16 migrations (v1→v17)
- Testing dependencies: JUnit5, MockK, Turbine, Coroutines Test, Room Testing

**Documentation Updates**:
- Updated entity count: 19 → 21
- Updated DAO count: 16 → 18
- Updated migration count: 13 → 16
- Added Testing Guide section with test structure and patterns
- Created CHANGELOG.md for version tracking

### 2026-01-29 - pykrx Library Update to v1.1.1

**Version Pinning**:
- Pinned pykrx to v1.1.1 in `build.gradle.kts` for stability
- Previous: unpinned (latest), New: `pykrx==1.1.1`

**New Features Integrated**:
- **core.py**: Updated `market_date()` to use `get_nearest_business_day_in_a_week()` API
- **core.py**: Added `get_sector_classifications()` and `get_sector_list()` for sector data
- **etfcollector.py**: Added `get_etf_isin()` function for ISIN code retrieval

**pykrx v1.1.1 Highlights** (Jan 24, 2026):
- HTTPS upgrade for ETF info retrieval (security improvement)
- Fixed KRX login blocking via Referer header modification
- New APIs: business day lookup, sector classifications, ETF ISIN
- Gold price APIs available (not integrated: `get_item_gold_price`, `get_item_gold_ticker`)

**Documentation**:
- Updated Python Integration section with pykrx v1.1.1 features
- Added pykrx Key Features subsection
- Updated Python Scripts table with new functions

### 2026-02-11 - Comprehensive Project Review (4-Agent Team)

**Review Team**: Security, Performance, Stability, Test Coverage
**Codebase**: ~263 Kotlin files, 11 Python scripts, Schema v19

**Overall Score: 78/100**
- Security: 82/100
- Performance: 76/100
- Stability: 79/100
- Test Coverage: 65/100

**Critical Findings (Must Fix)**:
1. **ProGuard Rules Stale** (SEC-01, STAB-01): `proguard-rules.pro` lines 91-143 reference deleted legacy packages (`com.etfmonitor.ai`, `com.etfmonitor.repository`, `com.etfmonitor.ui.screens`, `com.etfmonitor.analysis`, `com.etfmonitor.oscillator`). These packages were moved during Clean Architecture migration but ProGuard rules were NOT updated. **Release builds may crash due to R8 stripping needed classes.**
2. **DataCollectionService Wrong Dispatcher** (PERF-01): `serviceScope = CoroutineScope(Dispatchers.Default + Job())` at line 60. I/O-heavy operations (DB writes, network, Python) run on CPU thread pool instead of IO.
3. **Test Coverage ~3%** (TEST-01): Only 8 unit test files + 1 instrumented test file for 263 source files. 10 of 14 ViewModels untested, 9 of 13 repositories untested, 0 workers tested.
4. **DataCollectionService Missing SupervisorJob** (STAB-04): Uses `Job()` instead of `SupervisorJob()` - one failed coroutine cancels all sibling coroutines.
5. **StatisticsViewModel Missing Dispatchers.IO** (PERF-02): `loadStatistics()` makes repository/DB calls without `withContext(Dispatchers.IO)`.
6. **Previous Review Findings Still Valid**: Converters.kt type safety, `.getOrThrow()` verification, `.first()` emptiness guards, bounded list access in repositories.

**Known Unnecessary Files/Folders**:
- `app/release/` - Build artifacts (should be in .gitignore)
- `app/schemas/` - Stale Room schema export (v17 only, schema is v19)
- `ui-optimization/` - Completed optimization audit artifacts (4 files)
- `TODO_CODE_QUALITY.md` - Outdated TODO list
- `QUALITY_PLAN.md` - Completed quality plan
- `docs/CODE_REVIEW_TODO.md` - Outdated checklist
- `docs/plans/PLAN_clean-architecture-migration.md` - Migration completed
- `docs/plans/PLAN_cleanup-architecture.md` - Cleanup completed
- `docs/ML_PREDICTION_ENHANCEMENT_SPEC.md` - v1 system removed, spec outdated

**ProGuard Rules Requiring Update** (prevents release crashes):
- Line 91-92: `com.etfmonitor.ai.**` -> `com.etfmonitor.core.network.ai.**`
- Line 133: `com.etfmonitor.repository.**` -> DELETE (Hilt auto-keeps)
- Line 134-137: `com.etfmonitor.ui.screens.**ViewModel` -> `com.etfmonitor.feature.**ViewModel`
- Line 142: `com.etfmonitor.analysis.**` -> `com.etfmonitor.core.analysis.**`
- Line 143: `com.etfmonitor.oscillator.**` -> DELETE (merged into core.analysis)

**Full Report**: `.claude/project-review-report.md`

### 2026-02-11 - Project Optimization (7-Engineer Team)

**Engineers**: Security, Performance, Stability, Bug Fix, Code Integration, Build, Test Coverage
**Constraint**: ZERO functional changes

**Fixes Applied**:
1. **ProGuard rules fixed** - All 5 stale package paths corrected (prevents release crashes)
2. **DataCollectionService** - `Dispatchers.Default + Job()` → `Dispatchers.IO + SupervisorJob()`
3. **StatisticsViewModel** - `loadStatistics()` wrapped in `withContext(Dispatchers.IO)`
4. **Converters.kt** - try-catch added to `toStringList()` and `toLongList()` (crash prevention)
5. **backup_rules.xml + data_extraction_rules.xml** - DB name fixed: `etf_db` → `etf_monitor.db`
6. **MarketDepositRepositoryImpl** - `minOf()` bounds validation for parallel list access
7. **8 files** - `.first()` emptiness guards added (prevents NoSuchElementException)
8. **getOrThrow() audit** - All 20 locations verified safe (inside try-catch or isSuccess checks)

**Cleanup**:
- Deleted `ErrorBoundary.kt` (dead code)
- Deleted `ui-optimization/` folder, `TODO_CODE_QUALITY.md`, `QUALITY_PLAN.md`
- Deleted 3 stale docs + 2 completed plan files
- Updated `.gitignore` with `app/release/`, `app/schemas/`
- Net: 155 lines added, 25,241 lines removed

**Estimated Score After Optimization**: 84/100 (Security: 92, Performance: 88, Stability: 91, Test: 65)
**Full Report**: `.claude/optimization-result-report.md`

### 2026-02-12 - Python → Native Kotlin Migration (Complete)

**Goal**: Remove Chaquopy + all Python dependencies, replace with native Kotlin using kotlin_krx
**Constraint**: ZERO functional/UI changes

**Architecture Change**:
```
Before: ViewModel → Repository → PyKrxClient/OscillatorPyClient → Python (Chaquopy) → pykrx/KIS API
After:  ViewModel → Repository → KrxDataClient/StockDataClient → kotlin_krx → KRX API (direct)
```

**Phase 1: Build Configuration**
- Added `include(":kotlin_krx")` to `settings.gradle.kts` with local module path
- Added `implementation(project(":kotlin_krx"))` and Jsoup 1.17.2 to `app/build.gradle.kts`
- Added jsoup version to `gradle/libs.versions.toml`

**Phase 2: Native KRX Clients Created (6 files)**
- `KrxDataClient.kt` - replaces PyKrxClient (ETF lists, holdings, business days)
- `StockDataClient.kt` - replaces OscillatorPyClient data methods (stock search, analysis, OHLCV)
- `MarketIndexClient.kt` - replaces MarketIndexPyClient (KOSPI/KOSDAQ indices)
- `FearGreedClient.kt` - replaces feargreed.py (5 indicators, direct KRX HTTP)
- `DepositScraper.kt` - replaces deposit_scraper.py (Jsoup HTML parsing)
- `BloodIndicatorClient.kt` - replaces blood_indicator.py (Yahoo Finance + FRED)

**Phase 3: Technical Indicator Calculators (3 files)**
- `TechnicalIndicators.kt` - EMA, MACD, RSI, CMF calculations
- `MarketOscillatorCalculator.kt` - market overbought/oversold (200+ stocks)
- `TrendSignalNativeCalculator.kt` - Elder Impulse, DeMark TD Sequential

**Phase 4-5: Repository + DI Updates**
- Updated 8 repositories to use new native clients
- Created `KrxModule.kt` providing KrxStock, KrxEtf, KrxIndex as @Singleton
- Deleted `PythonModule.kt`

**Phase 6: Chaquopy Removal**
- Deleted `app/src/main/python/` directory (11 Python scripts)
- Deleted Python bridge clients: `PyKrxClient.kt`, `MarketIndexPyClient.kt`, `OscillatorPyClient.kt`
- Removed Chaquopy plugin from `app/build.gradle.kts` and root `build.gradle.kts`
- Removed Chaquopy from `settings.gradle.kts` repositories
- Cleaned `EtfMonitorApp.kt` (removed Python engine initialization)

**Phase 7: Worker + Service Updates**
- Updated 7 workers + 1 service to use native Kotlin clients
- All timeout patterns preserved (30s standard, 180s oscillator, 90s blood indicator)

**Verification (4-Engineer Team)**:
- Build: 30/30 checks PASS
- Performance: 52/52 checks PASS (all dispatchers, timeouts, caching preserved)
- Stability: 25+ safe call sites verified, 3 issues found and fixed
- Bug Fix: 1 CRITICAL bug found and fixed (duplicate MergedRow ClassCastException)

**Post-Verification Fixes**:
1. `FearGreedClient.kt` - Removed duplicate local `MergedRow` class (prevents ClassCastException)
2. `OscillatorCalculator.kt` - Added emptiness guard for `depositAmounts`/`creditAmounts`
3. `MarketOscillatorRepositoryImpl.kt` - Added `minOf()` bounds check for parallel list access
4. `KrxDataClient.kt` - Added `KrxIndex` to constructor (DI-injected instead of ad-hoc instances)
5. `FearGreedClient.kt` - Removed dead `krxPost()` method and unused import
6. `gradle.properties` - Updated stale Chaquopy comment
7. `MarketIndexRepositoryImpl.kt` - Updated stale KDoc reference

**Files Summary**:
| Action | Count | Details |
|---|---|---|
| New Files | ~10 | 6 clients + 3 calculators + 1 DI module |
| Modified Files | ~18 | 8 repositories + 7 workers + 1 service + 2 build configs |
| Deleted Files | ~14 | 11 Python scripts + 3 Python bridge clients |

**Result**: Python dependency COMPLETELY REMOVED. Build time improvement (no Chaquopy extraction).

### 2026-02-12 - kotlin_krx → krxkt Internal Module Integration

**Goal**: Move external kotlin_krx (absolute path dependency) into internal `krxkt/` module
**Constraint**: ZERO functional/UI changes, ZERO import changes

**Phase 1-2: Module Creation (Pre-completed)**
- Copied 54 Kotlin files (23 main + 31 test) → `krxkt/src/`
- Created `krxkt/build.gradle.kts` (kotlin-jvm + java-library, com.krxkt package)
- Updated `settings.gradle.kts`: `include(":krxkt")`, removed external path reference
- Updated `app/build.gradle.kts`: `implementation(project(":krxkt"))`

**Phase 3: 6-Engineer Verification**
- Engineer 1 (Code Integration): 30/30 PASS — all imports resolve, ProGuard correct
- Engineer 2 (Performance): 9/9 PASS — dispatchers, timeouts, dependencies verified
- Engineer 3 (Stability): 5/5 PASS — error handling, retry, thread safety verified
- Engineer 4 (Test Coverage): krxkt 154/154 PASS, app 62/85 PASS (23 pre-existing)
- Engineer 5 (Build & Release): debug + release BUILD SUCCESSFUL
- Engineer 6 (Bug Fix): 7 fixes applied

**Fixes Applied**:
1. `IndexOhlcvTest.kt` — Fixed `CLPR_IDX` → `CLSPRC_IDX` (test field name mismatch, 2 test failures → 0)
2. `krxkt/build.gradle.kts` — Coroutines 1.7.3 → 1.10.2 (aligned with app)
3. `app/build.gradle.kts` — Added `testImplementation(kotlin("test"))` (fixes 6 test files)
4. `SettingsViewModelKisTest.kt` — Removed phantom `SettingsRepository` import + unused field
5. `HomeViewModelTest.kt` — Fixed `DataStatus` reference (not nested in CheckDataStatusUseCase)
6. `CorrelationAnalyzerTest.kt` — Fixed `assertTrue` trailing lambda syntax
7. `KrxDataClient.kt` — Removed unused `Market` import

**Architecture**:
```
krxkt/ (Pure JVM, com.krxkt) ← Infrastructure Layer
  ↓
core/network/krx/ (App wrappers) ← Data Layer
  ↓
feature/*/domain/ ← Domain Layer
```

**Benefits**:
- Portable builds (no absolute path dependency)
- Git-tracked source code
- CI/CD compatible
- Independent test suite (154 tests)
- Build caching via separate module

**Full Report**: `.claude/krxkt-integration-report.md`
