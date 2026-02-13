# CLAUDE.md — MarketMonitor (ETF Monitor)

## Project Identity

Korean stock market (KRX) ETF monitoring Android app.
Kotlin 2.1.0 | Jetpack Compose + M3 | MVVM + Clean Architecture | Hilt 2.54 | Room 2.8.3 (schema v19) | Chaquopy (embedded Python) | Claude & Gemini AI APIs

Package: `com.etfmonitor` | DB: `etf_monitor.db` | ~255 Kotlin files | 8 Python scripts
Structure: `core/` (97 files) shared infra, `feature/` (155 files) 6 modules, `navigation/` (1 file)
Each feature: `domain/{model,repository,usecase}` → `data/{mapper,repository}` → `presentation/` → `di/`

---

## Critical Rules

These are project-specific traps. Violating these causes bugs, crashes, or data corruption.

### 1. IMPORTANT: Holding Entity — Use Factory Only
`Holding` uses compressed storage (`weightBps: Short`, `amountMillion: Int`).
```kotlin
// ✅ ALWAYS use factory
Holding.create(etfTicker, stockTicker, name, date, weight, amount)
// ❌ NEVER construct directly — causes overflow/underflow
```
In SQL queries, convert: `CAST(weightBps AS REAL) / 10000.0`, `CAST(amountMillion AS REAL) * 1000000.0`

### 2. IMPORTANT: StockAnalysisData — JOIN Required
`name` was removed from `stock_analysis_data` in migration v12→13.
```kotlin
// ✅ stockAnalysisDao.getAnalysisDataWithName(ticker)  // JOIN with stocks table
// ❌ stockAnalysisDao.getAnalysisData(ticker)           // name will be null
```

### 3. IMPORTANT: Python Timeouts — Not All 30s
| Client | Timeout | Why |
|--------|---------|-----|
| PyKrxClient | 30s | Standard (2 retries for holdings) |
| MarketIndexPyClient | 30s | Standard |
| BloodIndicatorPyClient | **90s** | 100-week SMA calculation |
| EnhancedPredictorClient | **120s** | ML ensemble training (28 features) |
| OscillatorPyClient | **180s** | Collects 200+ component stocks |

### 4. IMPORTANT: FearGreed — Request 3x Days
Moving averages lose leading data. To get N days of Fear & Greed data:
```kotlin
fearGreedRepository.initializeFearGreed(days = N * 3)
```

### 5. DAO Queries — Always Use LIMIT
Ranking queries without LIMIT cause OOM on Android. Existing limits: rankings=500, changes=300, lists=100.

### 6. Database Migrations — Inline in AppDatabase.kt
Schema is v19 (18 migrations). All migrations defined inline in `AppDatabase.kt`.
IMPORTANT: Always add migration BEFORE changing schema. Never use `fallbackToDestructiveMigration()`.

### 7. Repository Caching
| Repository | Expiry | Invalidation |
|------------|--------|-------------|
| StockAnalysis | 24h | OR missing today OR <80% days |
| MarketDeposit | 12h | AND latest == today |
| FearGreed | 12h | OR latest != today |

### 8. ViewModel State Exceptions
Most ViewModels use sealed class state. Two exceptions use individual StateFlows (intentional):
- **SettingsViewModel**: 25+ StateFlows (complex config)
- **StatisticsViewModel**: 12+ StateFlows (multi-column sorting)
New ViewModels should use sealed classes.

### 9. AI Integration Rules
- Check `isApiKeyConfigured` before calling AI APIs
- AI parser handles Korean signals: 강력매수, 매수, 중립, 매도, 강력매도
- Handle Gemini `SAFETY`/`RECITATION` blocks (returns empty instead of error)
- API keys stored with AES256-GCM via Android Keystore (`SharedPreferencesApiKeyProvider`)

### 10. Python Calls — Always withContext(IO) + withTimeout
```kotlin
suspend fun fetch() = withContext(Dispatchers.IO) {
    withTimeout(30_000L) { module.callAttr("fn").toString() }
}
```
Use `Json { ignoreUnknownKeys = true }` for Python JSON parsing.

---

## Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install on device
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests (device required)
./gradlew clean                  # Clean build
```

Versions: `gradle/libs.versions.toml` | Build config: `app/build.gradle.kts`
ABI: arm64-v8a, x86_64 only (64-bit)

---

## Key Files

| Category | Path | Notes |
|----------|------|-------|
| Entry | `MainActivity.kt`, `EtfMonitorApp.kt` | Theme, permissions, Python init |
| Navigation | `navigation/Navigation.kt` | 14 screen routes |
| Database | `core/database/AppDatabase.kt` | 21 entities, 18 DAOs, 18 migrations (v19) |
| Database entities | `core/database/entities/` | 20 files (AIChatSession in AIChatMessage.kt) |
| Python scripts | `app/src/main/python/` | 8 files: etfcollector, stocks, market, feargreed, deposit_scraper, trend_signal, core, logger |
| Python bridge | `core/network/python/` | PyKrxClient, MarketIndexPyClient, OscillatorPyClient, BloodIndicatorPyClient |
| AI clients | `core/network/ai/` | ClaudeApiClient, GeminiApiClient, AIApiClientFactory (11 files) |
| Theme | `core/ui/theme/` | Theme.kt, ThemeManager.kt |
| Workers | `core/worker/` | 8 workers + WorkManagerHelper |
| DI | `core/di/` + `feature/*/di/` | 10 modules total (4 core + 6 feature) |
| Tests | `app/src/test/`, `app/src/androidTest/` | JUnit5, MockK, Turbine |

---

## Do NOT (project-specific mistakes)

| Do NOT | Do Instead |
|--------|-----------|
| Construct `Holding(...)` directly | Use `Holding.create()` factory method |
| Query `stock_analysis_data` without JOIN | Use `getAnalysisDataWithName()` (JOIN with stocks) |
| Use 30s timeout for `getMarketOscillator()` | Use 180s — collects 200+ stocks |
| Use 30s timeout for ML prediction | Use 120s — ensemble training is expensive |
| Request exact days for FearGreed | Request 3x days (MA data loss) |
| Write ranking queries without LIMIT | Add LIMIT clause (OOM on Android) |
| Expose `MutableStateFlow` publicly | Use `_state` private + `state: StateFlow` public via `.asStateFlow()` |
| Use `LiveData` | This project uses `StateFlow` exclusively |
| Omit dispatcher on DB/Python/network calls | Always `withContext(Dispatchers.IO)` |
| Call AI without checking API key | Check `isApiKeyConfigured` first |
| Assume English signal names only | Parser handles Korean: 강력매수, 매수, 중립, 매도, 강력매도 |
| Create ViewModels manually | Use `hiltViewModel()` in Composables |
| Change DB schema without migration | Add migration in AppDatabase.kt first |
| Add unrequested features or refactoring | Make ONLY the requested changes |
| Over-engineer with abstractions | Minimum complexity for current task |

---

## Model Routing Rules

Default model: **sonnet**. Use tiered agents in `.claude/agents/` for cost-efficient delegation.

### Agent Tier Map

| Agent | Model | Cost | Use When |
|-------|-------|------|----------|
| `explorer` | haiku | $ | "Where is X?", file search, dependency tracing, codebase questions |
| `doc-writer` | haiku | $ | CLAUDE.md updates, KDoc, CHANGELOG, documentation |
| `implementer` | sonnet | $$ | Feature implementation, bug fixes, refactoring |
| `test-writer` | sonnet | $$ | Unit/integration test generation |
| `reviewer` | opus | $$$$ | Architecture review, security audit, PR review |

### Escalation Criteria (→ opus reviewer)

- Changes touch **security-critical paths**: `SharedPreferencesApiKeyProvider`, `*ApiClient.kt`, `*PyClient.kt` callAttr()
- Database migration changes (schema integrity)
- Changes span **4+ feature modules** simultaneously
- Performance-sensitive code (DAO queries, Python bridge, caching logic)
- Pre-merge review for PRs with 10+ file changes

### De-escalation Criteria (→ haiku)

- Read-only operations: finding files, tracing imports, answering "where is X?"
- Documentation-only changes: KDoc, comments, CLAUDE.md, CHANGELOG
- Simple renames or string changes
- Counting files, listing dependencies, summarizing structure

### Always Sonnet+ (never haiku)

- Any code modification (Write/Edit operations)
- Kotlin/Python logic changes
- Build configuration changes (`build.gradle.kts`, `libs.versions.toml`)
- Test writing and execution
- Migration authoring

### Routing Decision Tree

```
Is it read-only exploration?
  YES → explorer (haiku)
  NO → Is it documentation-only?
    YES → doc-writer (haiku)
    NO → Is it test writing?
      YES → test-writer (sonnet)
      NO → Is it a review/audit?
        YES → Does it touch security paths or 4+ modules?
          YES → reviewer (opus)
          NO → reviewer (opus) — reviews always use opus
        NO → implementer (sonnet)
```

---

## Coding Philosophy

- **Minimal Engineering**: Only requested changes. No unrequested features, refactoring, or "improvements"
- **Trust Framework**: No error handling for impossible scenarios. Validate only at boundaries
- **No Premature Abstraction**: No helpers/utilities for one-time tasks. No hypothetical future design
- **Read Before Edit**: Always read the full file before making changes
- **Follow Existing Patterns**: Sealed state classes, StateFlow, Hilt injection, Clean Architecture layers

---

## Compaction Instructions

When context is compacted, PRESERVE:
- All 10 Critical Rules (especially Holding factory, StockAnalysisData JOIN, Python timeouts, FearGreed 3x)
- Do NOT table (project-specific mistakes)
- Project Identity (package name, DB name, structure)
- Commands section

DISCARD during compaction:
- Key Files table (can be re-discovered via Glob)
- Coding Philosophy (Claude retains behavioral context)
- This Compaction Instructions section itself

## Model Routing Rules
- Codebase exploration → use explorer subagent (haiku)
- kotlin_krx integration → Integrator teammate (sonnet)
- Test/performance/stability → QA-Engineer teammate (sonnet)
- Architecture decisions → Architect-Reviewer teammate (opus)
- Documentation updates → use doc-writer subagent (haiku)

## Migration Context
- Migrating from pykrx (Python) to kotlin_krx (native Kotlin)
- Architecture: MVVM + Clean Architecture + Feature modules
- DI framework: Hilt
- Key constraint: Maintain full API compatibility during migration