# CLAUDE.md - AI Assistant Guide for EtfMonitor

## Project Overview

**ETF Monitor** is a production-grade Android financial monitoring application for the Korean stock market (KRX). It provides ETF tracking, stock analysis, technical indicators, and market sentiment analysis.

### Key Facts
- **Language**: Kotlin 2.1.0
- **UI Framework**: Jetpack Compose with Material Design 3
- **Min SDK**: 26 (Android 8.0) | **Target SDK**: 35 (Android 15)
- **Architecture**: MVVM + Clean Architecture
- **Dependency Injection**: Hilt 2.54
- **Database**: Room 2.8.3 (7 schemas with migrations)
- **Unique Feature**: Embedded Python runtime (Chaquopy) for data collection

### Project Purpose
Monitor Korean ETFs with features including:
- Real-time ETF holdings and composition tracking
- Stock-level foreign/institutional investment analysis
- Technical oscillators (EMA, MACD) for market timing
- Fear & Greed Index for market sentiment
- Market deposit trends
- Background data synchronization

---

## Codebase Structure

```
EtfMonitor_Rel/
├── app/src/main/
│   ├── java/com/example/etfmonitor/
│   │   ├── MainActivity.kt              # Entry point
│   │   ├── EtfMonitorApp.kt            # Hilt application
│   │   ├── database/                    # Room (9 entities, 8 DAOs)
│   │   │   ├── AppDatabase.kt
│   │   │   ├── entities/
│   │   │   ├── DAOs/
│   │   │   └── Migrations/
│   │   ├── di/                          # Hilt modules
│   │   │   ├── DatabaseModule.kt
│   │   │   ├── RepositoryModule.kt
│   │   │   ├── PythonModule.kt
│   │   │   └── WorkerModule.kt
│   │   ├── repository/                  # Data layer (6 repos)
│   │   ├── python/                      # Python bridge (PyKrxClient)
│   │   ├── oscillator/                  # Technical analysis
│   │   ├── ui/                          # Compose UI layer
│   │   │   ├── Navigation.kt
│   │   │   ├── screens/                 # 8 feature screens
│   │   │   ├── components/              # Reusable components
│   │   │   └── theme/                   # Material Design 3
│   │   ├── worker/                      # Background tasks
│   │   ├── service/                     # Foreground services
│   │   └── utils/                       # Formatters
│   ├── python/                          # Python scripts
│   │   ├── etfcollector.py
│   │   ├── stockcollector.py
│   │   ├── stock_analyzer.py
│   │   └── ...
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
// Pattern: Sealed class for type-safe state
sealed class HomeState {
    object Loading : HomeState()
    data class Idle(val hasData: Boolean, val lastDate: String?) : HomeState()
    data class Success(val message: String) : HomeState()
    data class Error(val message: String) : HomeState()
}

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
| **Chaquopy** | 15.0.1 | Python runtime |

### Data Visualization
- **Vico 2.0.0-alpha.28**: Modern line/column charts (Material Design 3)
- **MPAndroidChart 3.1.0**: Advanced technical charts (oscillators)

### Python Integration
**Embedded Python** via Chaquopy includes:
- `pykrx`: Korean stock market API
- `pandas`: Data manipulation
- `scikit-learn`: Machine learning
- `beautifulsoup4`: Web scraping

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
- **`ui/Navigation.kt`**: All screen routes (8 screens), NavHost setup

### Database
- **`database/AppDatabase.kt`**: Room database (9 entities, 6 migrations v1→v7)
- **`database/Migrations/Migrations.kt`**: Schema evolution

### Repositories
- **`repository/DataRepository.kt`**: ETF data, holdings, comparisons
- **`repository/StockRepository.kt`**: Stock ticker initialization
- **`repository/StockAnalysisRepository.kt`**: Foreign/institutional analysis

### Python Bridge
- **`python/PyKrxClient.kt`**: Main Python integration (ETF/stock data)
- **`python/OscillatorPyClient.kt`**: Technical oscillator calculations

### UI Theme
- **`ui/theme/Theme.kt`**: Material Design 3 color schemes, typography
- **`ui/theme/ThemeManager.kt`**: Global theme state (dark mode, font, colors)

### Background Tasks
- **`worker/StockUpdateWorker.kt`**: Daily stock data refresh
- **`service/DataCollectionService.kt`**: Foreground ETF sync service

### Dependency Injection
- **`di/DatabaseModule.kt`**: Database and DAO providers
- **`di/RepositoryModule.kt`**: Repository providers
- **`di/PythonModule.kt`**: Python engine provider

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

### Active Branch
- **Development**: `claude/claude-md-mia712l47enwbphf-01QrSFjXRk52kqnoAADPawKM`

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

- ❌ Exposing `MutableStateFlow` publicly
- ❌ Running database operations without `Dispatchers.IO`
- ❌ Creating ViewModels manually (use `hiltViewModel()`)
- ❌ Forgetting Python timeout (always use `withTimeout`)
- ❌ Changing database schema without migration
- ❌ Using LiveData (this project uses StateFlow)
- ❌ Hardcoding strings (use string resources)
- ❌ Blocking main thread with suspend functions

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

**Last Updated**: 2025-11-22
**Codebase Version**: Schema v7, ~4,200 LOC
**Maintainer**: gmdjlee
