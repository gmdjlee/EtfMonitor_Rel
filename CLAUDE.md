# CLAUDE.md - AI Assistant Guide for EtfMonitor

## Project Overview

**ETF Monitor** is a production-grade Android financial monitoring application for the Korean stock market (KRX). It provides ETF tracking, stock analysis, technical indicators, and market sentiment analysis.

### Key Facts
- **Language**: Kotlin 2.1.0
- **UI Framework**: Jetpack Compose with Material Design 3
- **Min SDK**: 26 (Android 8.0) | **Target SDK**: 35 (Android 15)
- **Architecture**: MVVM + Clean Architecture
- **Dependency Injection**: Hilt 2.54
- **Database**: Room 2.8.3 (19 entities, 16 DAOs, schema v14)
- **AI Integration**: Claude & Gemini API for market analysis
- **Unique Feature**: Embedded Python runtime (Chaquopy) for data collection & ML predictions

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

### Python Integration Critical Patterns

#### Timeout Requirements by Client
| Client | Default Timeout | Notes |
|--------|----------------|-------|
| PyKrxClient | 30s | 2 retries for holdings data |
| MarketIndexPyClient | 30s | Standard |
| OscillatorPyClient | **180s** | Market oscillator collects 200+ stocks |
| StockPredictorPyClient | **120s** | ML training is expensive |
| FearGreedRepository | No timeout | Direct Python, request 3x days due to MA loss |

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
| FearGreedRepository | No auto-expiry | Check latest date manually |

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
│   ├── java/com/example/etfmonitor/
│   │   ├── MainActivity.kt              # Entry point
│   │   ├── EtfMonitorApp.kt            # Hilt application
│   │   ├── database/                    # Room (19 entities, 16 DAOs)
│   │   │   ├── AppDatabase.kt
│   │   │   ├── entities/
│   │   │   ├── DAOs/
│   │   │   └── Migrations (v1→v14)
│   │   ├── di/                          # Hilt modules
│   │   │   ├── DatabaseModule.kt
│   │   │   ├── RepositoryModule.kt
│   │   │   ├── PythonModule.kt
│   │   │   ├── AIModule.kt
│   │   │   └── WorkerModule.kt
│   │   ├── repository/                  # Data layer (13 repos)
│   │   ├── python/                      # Python bridge (PyKrxClient)
│   │   ├── oscillator/                  # Technical analysis
│   │   ├── ai/                          # AI integration (Claude, Gemini)
│   │   │   ├── AIApiClient.kt
│   │   │   ├── ClaudeApiClient.kt
│   │   │   ├── GeminiApiClient.kt
│   │   │   ├── MarketAnalysisPrompts.kt
│   │   │   ├── AIApiClientFactory.kt
│   │   │   ├── AIResponseParser.kt
│   │   │   └── MarketSignal.kt
│   │   ├── analysis/                    # Market analysis
│   │   │   ├── CorrelationAnalyzer.kt
│   │   │   └── Backtester.kt
│   │   ├── ui/                          # Compose UI layer
│   │   │   ├── Navigation.kt
│   │   │   ├── screens/                 # 14 feature screens
│   │   │   ├── components/              # Reusable components
│   │   │   └── theme/                   # Material Design 3
│   │   ├── worker/                      # Background tasks
│   │   ├── service/                     # Foreground services
│   │   └── utils/                       # Formatters
│   ├── python/                          # Python scripts (10 files)
│   │   ├── etfcollector.py              # ETF data collection
│   │   ├── stocks.py                    # Stock data utilities
│   │   ├── market.py                    # Market data fetcher
│   │   ├── core.py                      # Core utilities
│   │   ├── feargreed.py                 # Fear & Greed calculation
│   │   ├── deposit_scraper.py           # Market deposit scraper
│   │   ├── stock_predictor.py           # ML predictions
│   │   ├── trend_signal.py              # Trend signal analysis
│   │   └── logger.py                    # Logging utilities
│   ├── res/                             # Android resources
│   └── AndroidManifest.xml
├── gradle/libs.versions.toml            # Version catalog
└── build.gradle.kts                     # Build config
```

**Key Package Organization:**
- `ui.*` - Screens, components, theme (Material Design 3)
- `database.*` - Room entities, DAOs, migrations
- `repository.*` - Data access abstraction
- `di.*` - Hilt dependency injection modules
- `python.*` - Python integration bridge
- `ai.*` - AI API clients (Claude, Gemini)
- `analysis.*` - Correlation analysis, backtesting
- `worker.*` - WorkManager background tasks
- `service.*` - Foreground services
- `oscillator.*` - Technical analysis calculations

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
│   DataRepository, StockRepository   │  Business logic
└──────────┬──────────────────────────┘
           │
┌──────────▼──────────────────────────┐
│   Data Sources                      │
│   Room DAOs | Python (PyKrx) | APIs │  IO operations
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
class DataRepository @Inject constructor(
    private val dao: EtfDao,
    private val pyKrx: PyKrxClient
) {
    // Reactive data stream
    fun getAllEtfs(): Flow<List<Etf>> = dao.getAllEtfs()
        .flowOn(Dispatchers.IO)

    // One-time suspend operation
    suspend fun hasData(): Boolean = withContext(Dispatchers.IO) {
        dao.getEtfCount() > 0
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
| **Chaquopy** | 15.0.1 | Python runtime |
| **OkHttp** | 4.12.0 | HTTP client (AI APIs) |
| **Security Crypto** | 1.1.0-alpha06 | Encrypted API key storage |

### Data Visualization
- **Vico 2.0.0-alpha.28**: Modern line/column charts (Material Design 3)

### AI Integration
- **Claude API**: Anthropic's Claude for market analysis
- **Gemini API**: Google's Gemini for market analysis
- Encrypted SharedPreferences for secure API key storage

### Python Integration
**Embedded Python** via Chaquopy includes:
- `pykrx`: Korean stock market API
- `pandas`: Data manipulation
- `scikit-learn`: Machine learning (stock predictions)
- `beautifulsoup4`: Web scraping
- `numpy`: Numerical computing
- `requests`: HTTP client

#### Python Scripts (app/src/main/python/)
| Script | Exposed Functions | Return Format | Notes |
|--------|------------------|---------------|-------|
| **etfcollector.py** | `get_etf_list_with_names()`, `get_etf_list()`, `get_etf_holdings()` | JSON array | Uses pykrx, filters by keywords |
| **stocks.py** | `search_stock()`, `get_stock_data()`, `get_stock_ohlcv()`, `get_all_stocks_list()` | JSON object | 5-day rolling sums for investor data |
| **market.py** | `fetch_all_markets()`, `fetch_recent_days()`, `get_market_oscillator()` | JSON object | Oscillator collects 200+ component stocks |
| **core.py** | `get_business_days()`, date/number utilities | Various | Shared utilities, not directly called |
| **feargreed.py** | `run_analysis()`, `combine()`, `analyze()` | DataFrame tuple | 5 indicators @ 20% each (Mom, PCR, VIX, Spread, RSI) |
| **deposit_scraper.py** | `scrape_deposit_data()`, `get_market_deposit_data()` | JSON string | Scrapes Naver Finance |
| **stock_predictor.py** | `train_and_predict()`, `train_model()`, `predict()` | JSON | RandomForest/GradientBoosting, min 20 samples |
| **trend_signal.py** | `get_trend_signal_analysis()`, `get_elder_impulse_analysis()`, `get_demark_td_analysis()` | JSON | Technical indicators with buy/sell signals |

#### Python JSON Return Patterns
```python
# stocks.py - get_stock_data()
{
  "ticker": "005930",
  "name": "삼성전자",
  "dates": ["2024-01-01", "2024-01-02"],
  "market_cap": [100000, 105000],
  "foreign_5d": [500, 600],      # 5-day cumulative
  "institution_5d": [300, 400]   # 5-day cumulative
}

# stock_predictor.py - train_and_predict()
{
  "success": true,
  "accuracy": 0.85,
  "precision": 0.82,
  "predictions": [{"ticker": "...", "confidence": 0.92, ...}]
}
```

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
// In database/DAOs/NewEntityDao.kt
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
// In database/Migrations/Migrations.kt
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

### Adding a Python Data Source

1. **Create Python Script**
```python
# In app/src/main/python/new_collector.py
import json
from typing import List, Dict

def collect_data(param: str) -> str:
    """Collect data and return as JSON string"""
    data = []
    # ... data collection logic
    return json.dumps(data, ensure_ascii=False)
```

2. **Create Kotlin Client**
```kotlin
// In python/NewPyClient.kt
@Singleton
class NewPyClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val python = Python.getInstance()
    private val module = python.getModule("new_collector")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun collectData(param: String): List<DataItem> = withContext(Dispatchers.IO) {
        try {
            withTimeout(30_000L) {
                val result = module.callAttr("collect_data", param).toString()
                json.decodeFromString<List<DataItem>>(result)
            }
        } catch (e: Exception) {
            Log.e("NewPyClient", "Error collecting data", e)
            emptyList()
        }
    }
}
```

3. **Provide in PythonModule**
```kotlin
@Provides
@Singleton
fun provideNewPyClient(@ApplicationContext context: Context): NewPyClient {
    return NewPyClient(context)
}
```

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

#### Python Integration

**ALWAYS:**
- Set 30-second timeout for Python calls
- Use `withContext(Dispatchers.IO)` for Python operations
- Use kotlinx.serialization for JSON parsing
- Handle exceptions and log errors

```kotlin
// ✅ CORRECT
suspend fun fetchData(): List<Item> = withContext(Dispatchers.IO) {
    try {
        withTimeout(30_000L) {
            val result = python.callAttr("function").toString()
            json.decodeFromString<List<Item>>(result)
        }
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Python call timeout", e)
        emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "Python call failed", e)
        emptyList()
    }
}

// ❌ WRONG
fun fetchData(): List<Item> {
    val result = python.callAttr("function").toString()  // No timeout, blocking
    return json.decodeFromString(result)
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

### Task: Debug Python Integration Issues

**Steps:**
1. Check LogCat for Python errors (tag: "PyKrxClient" or module name)
2. Test Python script directly in Android Studio Python console
3. Verify JSON serialization with `@Serializable` annotations
4. Check timeout settings (30s may be insufficient for large datasets)
5. Verify Python packages are installed in `build.gradle.kts`

```kotlin
// Add debug logging
private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true  // For debugging
}

Log.d(TAG, "Python result: $result")  // Log raw Python output
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
- **`ui/Navigation.kt`**: All screen routes (14 screens), NavHost setup

### ViewModels (13 total)

#### Sealed State Class ViewModels (11)
| ViewModel | State Class | States | Key Dependencies |
|-----------|-------------|--------|------------------|
| **HomeViewModel** | HomeState | Loading, Idle, Initializing, Updating, Success, Error + HomeSummary | 4 Repos, EtfDao, Context |
| **EtfListViewModel** | ListState | Loading, Success, Empty, Error | DataRepository |
| **DetailViewModel** | DetailState | Loading, Success, Error | DataRepository, SavedStateHandle |
| **StockTrendViewModel** | TrendState | Loading, Success, Error | DataRepository, SavedStateHandle |
| **PredictionViewModel** | PredictionState | Initial, NoPredictions, HasPredictions, Loading, Success, Error | StockPredictionRepository |
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
- **`database/AppDatabase.kt`**: Room database (19 entities, 16 DAOs, 13 migrations v1→v14)
- **`database/Migrations`**: Schema evolution (inline in AppDatabase.kt)
- **`database/Converters.kt`**: TypeConverters for `List<String>` and `List<Long>` (uses org.json.JSONArray)

#### Database Entities (19 total)
| Entity | Table | Primary Key | Critical Notes |
|--------|-------|-------------|----------------|
| Etf | etfs | ticker (String) | Minimal: ticker + name |
| **Holding** | holdings | (etfTicker, stockTicker, date) | **Uses Short/Int compression** - see Critical Notes |
| Stock | stocks | ticker (String) | Added in v13, has inferMarket() helper |
| StockAnalysisData | stock_analysis_data | ticker (String) | **name removed in v13** - requires JOIN |
| Setting | settings | key (String) | Simple KV store |
| SearchHistory | search_history | id (Int, auto) | User search tracking |
| MarketDeposit | market_deposits | date (String) | Daily deposit/credit |
| FearGreedIndex | fear_greed_index | id (String: "KOSPI-2024-01-01") | 12 indicator columns |
| MarketOscillatorData | market_oscillator | id (String: "KOSPI-2025-01-01") | Overbought/oversold |
| MarketIndex | market_index | id (String: "KOSPI-2025-01-01") | OHLCV + changeRate |
| DailyEtfStatistics | daily_etf_statistics | date (String) | 14-column aggregates |
| CorrelationAnalysisResult | correlation_analysis_result | id (String) | 12+ correlation metrics |
| AIAnalysisResult | ai_analysis_result | id (UUID String) | AI interpretation |
| AIChatSession | ai_chat_session | id (UUID String) | Chat session |
| AIChatMessage | ai_chat_message | id (UUID String) | Chat messages |
| StockPrediction | stock_predictions | id ("{ticker}-{date}") | ML predictions |
| SectorAnalysis | sector_analysis | id ("{sector}-{date}") | Sector Fear/Greed |
| EtfCorrelationCache | etf_correlation_cache | id ("{etf1}-{etf2}-{date}") | ETF pair correlation |
| LiquidityAnalysis | liquidity_analysis | date (String) | Market liquidity |

#### Critical Migrations
| Migration | Impact | Action Required |
|-----------|--------|-----------------|
| **7→8** | Holding restructure | Float → Short/Int, added snapshotType, 7 indices |
| **12→13** | Stock expansion | name removed from StockAnalysisData → use JOIN |
| **13→14** | Advanced analysis | Added SectorAnalysis, EtfCorrelationCache, LiquidityAnalysis |

### Repositories (13 total)

#### Core Data Repositories
| Repository | Dependencies | Key Methods | Error Pattern |
|------------|--------------|-------------|---------------|
| **DataRepository** | EtfDao, DailyEtfStatisticsDao, StockDao, PyKrxClient | `initializeData()`, `updateData()`, `getComparison()` | Flow<DataProgress> |
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
| **StockPredictionRepository** | StockPredictionDao, EtfDao, StockPredictorPyClient | `runPrediction()` | Min 20 training samples |
| **AdvancedAnalysisRepository** | 9 DAOs | 5 analysis types: MarketCapFlow, Divergence, Liquidity, Sector, ETF Correlation | Complex multi-factor analysis |

### AI Integration (11 files)

#### API Client Configuration
| Client | API Endpoint | Default Model | Timeout |
|--------|-------------|---------------|---------|
| **ClaudeApiClient** | `api.anthropic.com/v1/messages` | `claude-3-5-sonnet-20241022` | 60s |
| **GeminiApiClient** | `generativelanguage.googleapis.com/v1beta/models` | `gemini-2.0-flash-exp` | 60s |

#### AI Files
- **`ai/AIApiClient.kt`**: Interface with `analyzeMarket()`, `chat()`, `isApiAvailable()`, `testApiKey()`, `listModels()`
- **`ai/ClaudeApiClient.kt`**: Anthropic API (headers: `x-api-key`, `anthropic-version: 2023-06-01`)
- **`ai/GeminiApiClient.kt`**: Google API with `validateAndFixModelName()`, SAFETY/RECITATION block handling
- **`ai/AIApiClientFactory.kt`**: Factory pattern for client selection based on `ApiKeyProvider.getSelectedProvider()`
- **`ai/AIResponseParser.kt`**: Extracts JSON from `\`\`\`json...\`\`\`` blocks or raw `{...}`, parses Korean signal names
- **`ai/MarketAnalysisPrompts.kt`**: Templates for `COMPREHENSIVE`, `ETF_ONLY`, `TECHNICAL_ONLY`, `SENTIMENT_ONLY` analysis
- **`ai/ApiKeyProvider.kt`**: Interface for API key management
- **`ai/SharedPreferencesApiKeyProvider.kt`**: **AES256-GCM encrypted** storage via Android Keystore
- **`ai/AIModel.kt`**: Model definitions with id, name, provider, contextWindow, maxOutputTokens
- **`ai/AIProvider.kt`**: Enum (CLAUDE, GEMINI) with `toDisplayName()`, `fromString()`
- **`ai/MarketSignal.kt`**: Signal data class with `SignalType` (STRONG_BUY→STRONG_SELL), `RiskLevel` (LOW/MEDIUM/HIGH)

### Analysis
- **`analysis/CorrelationAnalyzer.kt`**: ETF flow vs market correlation
- **`analysis/Backtester.kt`**: Strategy backtesting

### Python Bridge (4 Kotlin Clients)
- **`python/PyKrxClient.kt`**: Main Python integration (ETF/stock data)
  - `getFilteredEtfList()`, `getEtfList()`, `getHoldings()`, `getBusinessDays()`, `getStockName()`
  - Uses: `etfcollector`, `stocks`, `core` modules
  - Retry: 2 retries for holdings data with exponential backoff
- **`python/MarketIndexPyClient.kt`**: Market index data fetcher
  - `fetchMarketIndices()`, `fetchRecentDays()`, `getLatestIndex()`
  - Uses: `market` module
- **`python/StockPredictorPyClient.kt`**: ML stock prediction client
  - `trainAndPredict()`, `trainModel()`, `predict()`, `getModelStatus()`, `clearModelCache()`
  - Uses: `stock_predictor` module
  - Timeout: 120s (ML training is expensive)
- **`oscillator/python/OscillatorPyClient.kt`**: Technical analysis client
  - `searchStock()`, `getStockAnalysis()`, `getMarketDepositData()`, `getAllStocksList()`
  - `getMarketOscillator()` (180s timeout), `getTrendSignalData()`, `getElderImpulseData()`, `getDemarkTDData()`
  - Uses: `stocks`, `deposit_scraper`, `market`, `trend_signal` modules

### UI Theme
- **`ui/theme/Theme.kt`**: Material Design 3 color schemes, typography
- **`ui/theme/ThemeManager.kt`**: Global theme state (dark mode, font, colors)

### Background Tasks (7 workers)
- **`worker/StockUpdateWorker.kt`**: Daily stock data refresh
- **`worker/DataArchiveWorker.kt`**: Data archiving
- **`worker/AdvancedAnalysisWorker.kt`**: Advanced analysis tasks
- **`worker/MarketOscillatorUpdateWorker.kt`**: Market oscillator updates
- **`worker/MarketDepositUpdateWorker.kt`**: Market deposit updates
- **`worker/FearGreedUpdateWorker.kt`**: Fear & Greed index updates
- **`worker/WorkManagerHelper.kt`**: WorkManager scheduling utilities

### Services
- **`service/DataCollectionService.kt`**: Foreground ETF sync service
- **`service/CollectionState.kt`**: Collection state management

### Dependency Injection (5 Modules, 43 Singleton Providers)

#### Module Summary
| Module | Manual Providers | Provides |
|--------|-----------------|----------|
| **DatabaseModule** | 17 | AppDatabase + 16 DAOs |
| **RepositoryModule** | 6 | 6 Repositories (7 more auto-injected via @Inject) |
| **PythonModule** | 3 | Python instance + 2 clients (2 more auto-injected) |
| **AIModule** | 9 | ApiKeyProvider, 2 API clients, Factory, 2 Analyzers, 3 Repositories |
| **WorkerModule** | 1 | WorkManager |

#### Auto-Injected Components (via @Inject constructor)
- **Repositories**: StockRepository, StockAnalysisRepository, MarketDepositRepository, AdvancedAnalysisRepository
- **Python Clients**: PyKrxClient, OscillatorPyClient

#### DI Dependency Flow
```
AppDatabase → DAOs → Repositories → ViewModels
                ↓
Python Instance → Python Clients → Repositories
                ↓
ApiKeyProvider → AI Clients → AIApiClientFactory → AI Repositories
```

#### Database Name
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "etf_monitor.db")
```

### Build Configuration
- **`gradle/libs.versions.toml`**: Version catalog for all dependencies
- **`app/build.gradle.kts`**: App module config, Chaquopy setup

---

## Gotchas & Known Issues

### 1. Chaquopy Build Times
**Issue**: Initial builds take 10-15 minutes due to Python runtime extraction.

**Solution**: Enable Gradle daemon and build cache in `gradle.properties`:
```properties
org.gradle.daemon=true
org.gradle.caching=true
org.gradle.parallel=true
```

### 2. Python Timeout on Large Datasets
**Issue**: 30-second timeout may be insufficient for initial ETF data collection.

**Solution**: Increase timeout for specific operations:
```kotlin
withTimeout(60_000L) {  // 60 seconds for initial load
    pyKrx.getAllEtfs()
}
```

### 3. Room Migration Testing
**Issue**: Migrations are not automatically tested.

**Recommendation**: Test migrations manually or add migration tests:
```kotlin
@Test
fun migrate7To8() {
    val db = helper.createDatabase(TEST_DB, 7)
    // Insert test data
    db.close()

    helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)
}
```

### 4. WorkManager Not Running
**Issue**: Workers not executing on schedule.

**Debug Steps:**
1. Check battery optimization settings (may kill WorkManager)
2. Verify constraints (network, charging)
3. Check LogCat for WorkManager errors
4. Use `adb shell dumpsys jobscheduler` to inspect scheduled jobs

### 5. Material Design 3 Dark Mode Shadows
**Issue**: Shadows not visible in dark mode.

**Solution**: Use white/light shadows for dark theme (already implemented):
```kotlin
val shadowColor = if (isSystemInDarkTheme()) Color.White else Color.Black
```

### 6. Compose State Hoisting
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

# Run tests
./gradlew test
```

### Build Variants

- **Debug**: Development builds with logging
- **Release**: Production builds (minification disabled currently)

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
- [ ] Python dependencies added to `build.gradle.kts` if needed
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
- **Chaquopy**: https://chaquo.com/chaquopy/
- **PyKrx**: https://github.com/sharebook-kr/pykrx

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
7. **Test Python Locally**: Test Python scripts before integrating
8. **Material Design 3**: Follow M3 guidelines for new UI components
9. **State Hoisting**: Keep Composables stateless
10. **Document Complex Logic**: Add KDoc for non-obvious functions

### Common Pitfalls to Avoid

#### General
- ❌ Exposing `MutableStateFlow` publicly
- ❌ Running database operations without `Dispatchers.IO`
- ❌ Creating ViewModels manually (use `hiltViewModel()`)
- ❌ Forgetting Python timeout (always use `withTimeout`)
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

#### Python-Specific
- ❌ Using 30s timeout for `getMarketOscillator()` (needs 180s)
- ❌ Using 30s timeout for ML training (needs 120s)
- ❌ Requesting exact days for FearGreed (request 3x due to MA data loss)
- ❌ Not handling JSON parsing errors from Python (use `ignoreUnknownKeys = true`)

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

**Last Updated**: 2025-12-06
**Codebase Version**: Schema v14, ~39,900 LOC
**Maintainer**: gmdjlee

---

## Change History

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
