# ETF Monitor App - Complete Specification

> **Version**: 1.0
> **Date**: 2026-01-25
> **Source**: EtfMonitor_Rel feature/etf module
> **Purpose**: Standalone ETF monitoring application for Korean market (KRX)

---

## Table of Contents

1. [App Overview](#1-app-overview)
2. [Architecture](#2-architecture)
3. [Data Collection Process](#3-data-collection-process)
4. [Data Storage](#4-data-storage)
5. [Data Loading & Processing](#5-data-loading--processing)
6. [UI Specification](#6-ui-specification)
7. [Design System](#7-design-system)
8. [Implementation Plan](#8-implementation-plan)
9. [Skills & Agents](#9-skills--agents)
10. [Prerequisites](#10-prerequisites)

---

## 1. App Overview

### 1.1 Purpose
Korean Active ETF portfolio tracking and analysis application that monitors:
- ETF holdings composition changes over time
- Stock weight changes within ETFs
- New/Removed stock detection
- Cash deposit trends across ETFs
- Stock-level aggregated analysis

### 1.2 Key Features
| Feature | Description |
|---------|-------------|
| **ETF List** | Searchable list of tracked Active ETFs with theme filtering |
| **ETF Detail** | Holdings comparison between periods with status indicators |
| **Statistics Hub** | 7-tab analysis: Amount Ranking, New/Removed/Increased/Decreased stocks, Cash Deposit, Stock Analysis |
| **Date Range Selection** | Week/Month/3M/6M/Year/All period comparison |
| **Theme Management** | Customizable ETF theme keywords for filtering |

### 1.3 Technical Stack
| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.1.0 |
| UI Framework | Jetpack Compose | BOM 2024.12.01 |
| Design System | Material Design 3 | Latest |
| Architecture | MVVM + Clean Architecture | - |
| DI | Hilt | 2.54 |
| Database | Room | 2.8.3 |
| Async | Coroutines + Flow | 1.10.2 |
| Python Runtime | Chaquopy | 15.0.1 |
| Charts | Vico | 2.0.0-alpha.28 |
| Min SDK | 26 (Android 8.0) | - |
| Target SDK | 35 (Android 15) | - |

---

## 2. Architecture

### 2.1 Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ EtfHubScreen│  │EtfListScreen│  │EtfDetailScr │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                 │
│  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐         │
│  │  HubState   │  │EtfListVM    │  │EtfDetailVM  │         │
│  │(Composable) │  │(StateFlow)  │  │(StateFlow)  │         │
│  └─────────────┘  └──────┬──────┘  └──────┬──────┘         │
└──────────────────────────┼────────────────┼─────────────────┘
                           │                │
┌──────────────────────────▼────────────────▼─────────────────┐
│                      DOMAIN LAYER                            │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    Use Cases                         │    │
│  │  GetEtfList | SearchEtfs | GetEtfDetail            │    │
│  │  GetComparison | GetComparisonInRange              │    │
│  │  GetAvailableDates | CheckDataStatus               │    │
│  └─────────────────────────┬───────────────────────────┘    │
│                            │                                 │
│  ┌─────────────────────────▼───────────────────────────┐    │
│  │              EtfRepository (Interface)               │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                   Domain Models                      │    │
│  │  Etf | HoldingWithComparison | ComparisonResult    │    │
│  │  DataProgress | DataStatus | HoldingStatus          │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│                       DATA LAYER                              │
│  ┌─────────────────────────────────────────────────────┐     │
│  │              EtfRepositoryImpl                       │     │
│  │  - Data collection with parallel processing         │     │
│  │  - Comparison logic with status determination       │     │
│  │  - Settings management (themes, exclusions)         │     │
│  │  - Daily statistics calculation                     │     │
│  └──────────────┬────────────────────┬─────────────────┘     │
│                 │                    │                        │
│  ┌──────────────▼──────┐  ┌──────────▼──────────┐            │
│  │  EtfLocalDataSource │  │    PyKrxClient      │            │
│  │  (Room DAOs)        │  │  (Python Bridge)    │            │
│  └──────────────┬──────┘  └──────────┬──────────┘            │
│                 │                    │                        │
│  ┌──────────────▼──────────────────▼─────────────────┐      │
│  │              Data Sources                          │      │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────────────┐   │      │
│  │  │ EtfDao  │  │StockDao │  │DailyStatsDao    │   │      │
│  │  └─────────┘  └─────────┘  └─────────────────┘   │      │
│  │                    │                              │      │
│  │              ┌─────▼─────┐                        │      │
│  │              │  Room DB  │                        │      │
│  │              └───────────┘                        │      │
│  └───────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│                    PYTHON LAYER                               │
│  ┌─────────────────────────────────────────────────────┐     │
│  │                  etfcollector.py                     │     │
│  │  get_etf_list_with_names() | get_etf_holdings()    │     │
│  └─────────────────────────────────────────────────────┘     │
│  ┌─────────────────────────────────────────────────────┐     │
│  │                     stocks.py                        │     │
│  │  get_stock_name() | search_stock()                  │     │
│  └─────────────────────────────────────────────────────┘     │
│  ┌─────────────────────────────────────────────────────┐     │
│  │                      core.py                         │     │
│  │  get_business_days()                                │     │
│  └─────────────────────────────────────────────────────┘     │
│                         │                                    │
│                   ┌─────▼─────┐                              │
│                   │   pykrx   │  (Korean Stock API)         │
│                   └───────────┘                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 Module Structure

```
app/src/main/java/com/etfapp/
├── core/
│   ├── common/
│   │   └── util/
│   │       ├── AppLogger.kt
│   │       ├── AmountFormatter.kt
│   │       └── DateFormatter.kt
│   ├── database/
│   │   ├── AppDatabase.kt
│   │   ├── entities/
│   │   │   ├── Etf.kt
│   │   │   ├── Holding.kt
│   │   │   ├── Stock.kt
│   │   │   ├── DailyEtfStatistics.kt
│   │   │   ├── Setting.kt
│   │   │   └── SearchHistory.kt
│   │   ├── EtfDao.kt
│   │   ├── StockDao.kt
│   │   ├── DailyEtfStatisticsDao.kt
│   │   └── SettingDao.kt
│   ├── network/
│   │   └── python/
│   │       └── PyKrxClient.kt
│   ├── ui/
│   │   ├── theme/
│   │   │   ├── Color.kt
│   │   │   ├── Theme.kt
│   │   │   ├── Type.kt
│   │   │   └── Shape.kt
│   │   └── component/
│   │       ├── DateRangeSelector.kt
│   │       ├── TabNavigationBar.kt
│   │       ├── HubHeader.kt
│   │       ├── LoadingIndicator.kt
│   │       └── ErrorCard.kt
│   └── di/
│       ├── DatabaseModule.kt
│       └── PythonModule.kt
├── feature/
│   └── etf/
│       ├── domain/
│       │   ├── model/
│       │   │   ├── Etf.kt
│       │   │   ├── HoldingWithComparison.kt
│       │   │   ├── ComparisonResult.kt
│       │   │   ├── DataProgress.kt
│       │   │   ├── DataStatus.kt
│       │   │   └── HoldingStatus.kt
│       │   ├── repository/
│       │   │   └── EtfRepository.kt
│       │   └── usecase/
│       │       ├── GetEtfListUseCase.kt
│       │       ├── SearchEtfsUseCase.kt
│       │       ├── GetEtfDetailUseCase.kt
│       │       ├── GetEtfComparisonUseCase.kt
│       │       ├── GetComparisonInRangeUseCase.kt
│       │       ├── GetAvailableDatesUseCase.kt
│       │       └── CheckDataStatusUseCase.kt
│       ├── data/
│       │   ├── datasource/
│       │   │   └── EtfLocalDataSource.kt
│       │   ├── mapper/
│       │   │   └── EtfMapper.kt
│       │   └── repository/
│       │       └── EtfRepositoryImpl.kt
│       ├── presentation/
│       │   ├── hub/
│       │   │   └── EtfHubScreen.kt
│       │   ├── list/
│       │   │   ├── EtfListScreen.kt
│       │   │   ├── EtfListViewModel.kt
│       │   │   └── EtfListState.kt
│       │   ├── detail/
│       │   │   ├── EtfDetailScreen.kt
│       │   │   ├── EtfDetailViewModel.kt
│       │   │   └── EtfDetailState.kt
│       │   └── statistics/
│       │       ├── StatisticsViewModel.kt
│       │       ├── AmountRankingTab.kt
│       │       ├── StockChangeTab.kt
│       │       ├── CashDepositTrendTab.kt
│       │       └── StockAnalysisTab.kt
│       └── di/
│           └── EtfModule.kt
├── navigation/
│   └── Navigation.kt
└── MainActivity.kt

app/src/main/python/
├── etfcollector.py
├── stocks.py
├── core.py
└── logger.py
```

---

## 3. Data Collection Process

### 3.1 Initialize Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    initializeData(days=25)                   │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. Initialize Default Settings                               │
│    - Default themes (28 keywords)                           │
│    - Default exclusions (16 keywords)                       │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Get Business Days                                         │
│    PyKrxClient.getBusinessDays(days)                        │
│    └─► core.py: get_business_days()                         │
│        └─► Returns: ["2025-01-20", "2025-01-21", ...]       │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. For Each Business Day:                                    │
│                                                              │
│    3.1 Get Filtered ETF List                                │
│        PyKrxClient.getFilteredEtfList(date, themes, excl)  │
│        └─► etfcollector.py: get_etf_list_with_names()      │
│            - Filter: name contains "액티브" (Active)         │
│            - Filter: name contains theme keywords           │
│            - Exclude: inverse/leverage keywords             │
│        └─► Returns: [{ticker, name}, ...]                   │
│                                                              │
│    3.2 Process ETFs in Parallel (5 concurrent)              │
│        For each ETF:                                         │
│        ├─ Check if already collected (skip if exists)       │
│        ├─ Insert ETF to database                            │
│        ├─ Get Holdings                                       │
│        │   PyKrxClient.getHoldings(ticker, date)            │
│        │   └─► etfcollector.py: get_etf_holdings()          │
│        │       └─► Returns: [{ticker, name, weight, amt}]   │
│        └─ Insert Holdings to database                       │
│                                                              │
│    3.3 Sync Stocks to Master Table                          │
│        StockDao.syncFromHoldings(stockList)                 │
│                                                              │
│    3.4 Calculate Daily Statistics                           │
│        - Compare with previous day                          │
│        - Count: new/removed/increased/decreased stocks      │
│        - Sum: amounts for each category                     │
│        - Track: cash deposit changes                        │
│        - Insert DailyEtfStatistics                          │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Emit Progress Updates                                     │
│    DataProgress.Loading(message, progress%)                 │
│    DataProgress.Success(message) or Error(message)          │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Update Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                       updateData()                           │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. Get Last Collection Date from DB                         │
│    EtfDao.getLatestDate()                                   │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Get Recent Business Days (10 days)                       │
│    Filter: days > lastDate                                  │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Process New Days Only                                     │
│    (Same as initializeData steps 3.1-3.4)                   │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 Theme Keywords (Default)

```kotlin
val defaultThemes = listOf(
    "반도체", "바이오", "혁신기술", "배당성장", "신재생",
    "2차전지", "AI", "조선", "테크", "수출", "로봇",
    "컬처", "밸류업", "친환경", "소비", "이노베이션",
    "메모리", "비메모리", "인공지능", "전기차", "배터리",
    "ESG", "탄소중립", "메타버스", "블록체인", "헬스케어",
    "IT", "성장"
)
```

### 3.4 Exclusion Keywords (Default)

```kotlin
val defaultExclusions = listOf(
    "인버스", "레버리지", "곱버스", "2X", "3X",
    "글로벌", "차이나", "채권", "달러", "China",
    "아시아", "미국", "일본", "금리", "금융채", "회사채"
)
```

---

## 4. Data Storage

### 4.1 Database Schema

#### 4.1.1 Etf Entity

```kotlin
@Entity(tableName = "etfs")
data class Etf(
    @PrimaryKey
    val ticker: String,      // e.g., "069500"
    val name: String         // e.g., "KODEX 200"
)
```

#### 4.1.2 Holding Entity (Memory Optimized)

```kotlin
@Entity(
    tableName = "holdings",
    primaryKeys = ["etfTicker", "stockTicker", "date"],
    indices = [
        Index(value = ["etfTicker"]),
        Index(value = ["stockTicker"]),
        Index(value = ["date"]),
        Index(value = ["etfTicker", "date"]),
        Index(value = ["stockTicker", "date"]),
        Index(value = ["weightBps"]),
        Index(value = ["amountMillion"])
    ]
)
data class Holding(
    val etfTicker: String,
    val stockTicker: String,
    val stockName: String,
    val date: String,            // "2025-01-20"
    val weightBps: Short,        // Weight in basis points (5.25% = 525)
    val amountMillion: Int,      // Amount in millions (1,234,567,890 = 1234)
    val snapshotType: String     // DAILY, WEEKLY, MONTHLY
) {
    // Conversion helpers
    val weight: Float get() = weightBps / 10000f
    val amount: Float get() = amountMillion * 1_000_000f

    companion object {
        // ALWAYS use this factory method
        fun create(
            etfTicker: String,
            stockTicker: String,
            stockName: String,
            date: String,
            weight: Float,
            amount: Float,
            snapshotType: String = "DAILY"
        ): Holding {
            return Holding(
                etfTicker = etfTicker,
                stockTicker = stockTicker,
                stockName = stockName,
                date = date,
                weightBps = (weight * 10000).toInt().coerceIn(-32768, 32767).toShort(),
                amountMillion = (amount / 1_000_000).toInt(),
                snapshotType = snapshotType
            )
        }
    }
}
```

#### 4.1.3 Stock Entity

```kotlin
@Entity(tableName = "stocks")
data class Stock(
    @PrimaryKey
    val ticker: String,
    val name: String,
    val market: String? = null   // KOSPI, KOSDAQ (inferred)
) {
    fun inferMarket(): String {
        return when {
            ticker.startsWith("0") || ticker.startsWith("1") ||
            ticker.startsWith("2") || ticker.startsWith("3") -> "KOSPI"
            else -> "KOSDAQ"
        }
    }
}
```

#### 4.1.4 DailyEtfStatistics Entity

```kotlin
@Entity(tableName = "daily_etf_statistics")
data class DailyEtfStatistics(
    @PrimaryKey
    val date: String,
    val newStockCount: Int,
    val newStockAmount: Long,
    val removedStockCount: Int,
    val removedStockAmount: Long,
    val increasedStockCount: Int,
    val increasedStockAmount: Long,
    val decreasedStockCount: Int,
    val decreasedStockAmount: Long,
    val cashDepositAmount: Long,
    val cashDepositChange: Long,
    val cashDepositChangeRate: Double,
    val totalEtfCount: Int,
    val totalHoldingAmount: Long
)
```

#### 4.1.5 Setting Entity

```kotlin
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey
    val key: String,
    val value: String
)
```

#### 4.1.6 SearchHistory Entity

```kotlin
@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val query: String,
    val timestamp: Long,
    val historyType: String = "ETF"  // ETF, STOCK
)
```

### 4.2 DAO Interfaces

#### 4.2.1 EtfDao (Key Methods)

```kotlin
@Dao
interface EtfDao {
    // ETF Operations
    @Query("SELECT * FROM etfs ORDER BY name")
    fun getAllEtfs(): Flow<List<Etf>>

    @Query("SELECT * FROM etfs WHERE name LIKE '%' || :query || '%' OR ticker LIKE '%' || :query || '%'")
    fun searchEtfs(query: String): Flow<List<Etf>>

    @Query("SELECT * FROM etfs WHERE ticker = :ticker")
    suspend fun getEtf(ticker: String): Etf?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEtf(etf: Etf)

    // Holdings Operations
    @Query("SELECT * FROM holdings WHERE etfTicker = :etfTicker AND date = :date ORDER BY weightBps DESC")
    suspend fun getHoldings(etfTicker: String, date: String): List<Holding>

    @Query("SELECT DISTINCT date FROM holdings WHERE etfTicker = :etfTicker ORDER BY date DESC")
    suspend fun getDates(etfTicker: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoldings(holdings: List<Holding>)

    // Statistics Queries (with LIMIT for memory safety)
    @Query("""
        SELECT stockTicker, stockName, SUM(amountMillion) as totalAmount, COUNT(DISTINCT etfTicker) as etfCount
        FROM holdings
        WHERE date = :date AND stockName NOT LIKE '%예금%' AND stockName NOT LIKE '%현금%'
        GROUP BY stockTicker
        ORDER BY totalAmount DESC
        LIMIT 500
    """)
    suspend fun getStockAmountRanking(date: String): List<StockAmountRanking>

    @Query("""
        SELECT h1.stockTicker, h1.stockName, h1.weightBps as currentWeight,
               COALESCE(h2.weightBps, 0) as previousWeight
        FROM holdings h1
        LEFT JOIN holdings h2 ON h1.stockTicker = h2.stockTicker
            AND h1.etfTicker = h2.etfTicker AND h2.date = :previousDate
        WHERE h1.date = :currentDate AND h2.stockTicker IS NULL
        ORDER BY h1.weightBps DESC
        LIMIT 300
    """)
    suspend fun getAllNewStocks(currentDate: String, previousDate: String): List<StockChangeInfo>

    // Settings
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: Setting)

    // Data Management
    @Query("SELECT MAX(date) FROM holdings")
    suspend fun getLatestDate(): String?

    @Query("SELECT DISTINCT date FROM holdings ORDER BY date DESC LIMIT :limit")
    suspend fun getAllDistinctDates(limit: Int): List<String>

    @Query("DELETE FROM holdings WHERE date < :date")
    suspend fun deleteHoldingsBeforeDate(date: String)
}
```

### 4.3 Database Configuration

```kotlin
@Database(
    entities = [
        Etf::class,
        Holding::class,
        Stock::class,
        DailyEtfStatistics::class,
        Setting::class,
        SearchHistory::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun etfDao(): EtfDao
    abstract fun stockDao(): StockDao
    abstract fun dailyEtfStatisticsDao(): DailyEtfStatisticsDao

    companion object {
        const val DATABASE_NAME = "etf_app.db"
    }
}
```

---

## 5. Data Loading & Processing

### 5.1 Comparison Logic

```kotlin
// Weight change threshold for status determination
private const val WEIGHT_CHANGE_THRESHOLD = 0.01f  // 0.01% = 1 basis point

fun determineStatus(currentWeight: Float, previousWeight: Float): HoldingStatus {
    return when {
        previousWeight == 0f && currentWeight > 0f -> HoldingStatus.NEW
        currentWeight == 0f && previousWeight > 0f -> HoldingStatus.REMOVED
        currentWeight - previousWeight > WEIGHT_CHANGE_THRESHOLD -> HoldingStatus.INCREASE
        previousWeight - currentWeight > WEIGHT_CHANGE_THRESHOLD -> HoldingStatus.DECREASE
        else -> HoldingStatus.MAINTAIN
    }
}
```

### 5.2 Comparison Result Sorting

```kotlin
// Sort order: NEW first, then REMOVED, then by currentWeight descending
val sortedItems = items.sortedWith(
    compareByDescending<HoldingWithComparison> { it.status == HoldingStatus.NEW }
        .thenByDescending { it.status == HoldingStatus.REMOVED }
        .thenByDescending { it.currentWeight }
)
```

### 5.3 Date Range Filtering

```kotlin
enum class DateRangeOption(val days: Int, val label: String) {
    DAY(1, "1일"),
    WEEK(7, "1주"),
    MONTH(30, "1개월"),
    THREE_MONTHS(90, "3개월"),
    SIX_MONTHS(180, "6개월"),
    YEAR(365, "1년"),
    ALL(-1, "전체")
}

fun filterDatesInRange(allDates: List<String>, option: DateRangeOption): List<String> {
    if (option == DateRangeOption.ALL) return allDates

    val cutoffDate = LocalDate.now().minusDays(option.days.toLong())
    val cutoffStr = cutoffDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    return allDates.filter { it >= cutoffStr }
}
```

### 5.4 Cash Deposit Detection

```kotlin
fun isCashDeposit(stockName: String): Boolean {
    val lowerName = stockName.lowercase()
    return lowerName.contains("원화예금") ||
           lowerName.contains("현금") ||
           lowerName.contains("cash") ||
           lowerName.contains("예금") ||
           lowerName.contains("krw")
}
```

### 5.5 Amount Formatting

```kotlin
object AmountFormatter {
    fun format(amount: Float): String {
        return when {
            amount >= 1_000_000_000_000 -> String.format("%.1f조", amount / 1_000_000_000_000)
            amount >= 100_000_000 -> String.format("%.0f억", amount / 100_000_000)
            amount >= 10_000 -> String.format("%.0f만", amount / 10_000)
            else -> String.format("%.0f", amount)
        }
    }

    fun formatLong(amount: Long): String = format(amount.toFloat())
}
```

---

## 6. UI Specification

### 6.1 Screen Hierarchy

```
EtfHubScreen (Main Tab)
├── Tab 0: "테마 목록" (ETF List)
│   └── EtfListHubContent
│       ├── EtfSearchField
│       └── LazyColumn[EtfListItemCompact]
│
└── Tab 1: "통계" (Statistics)
    └── StatisticsHubContent
        ├── ScrollableTabRow (7 tabs)
        │   ├── Tab 0: "보유량 순위"
        │   ├── Tab 1: "신규 편입"
        │   ├── Tab 2: "편출"
        │   ├── Tab 3: "비중 증가"
        │   ├── Tab 4: "비중 감소"
        │   ├── Tab 5: "예금 추이"
        │   └── Tab 6: "분석"
        ├── DateRangeSelector
        └── Tab Content
            ├── AmountRankingTab
            ├── StockChangeTab (x4)
            ├── CashDepositTrendTab
            └── StockAnalysisTab

EtfDetailScreen (Navigation)
├── TopAppBar (ETF name, date info)
├── DateRangeSelector
└── LazyColumn[HoldingComparisonItem]
    └── Status indicator colors
```

### 6.2 EtfHubScreen

```kotlin
@Composable
fun EtfHubScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onEtfClick: (String) -> Unit,
    onStockClick: (String) -> Unit,
    onNavigateToStocks: (String) -> Unit,
    initialStockTicker: String? = null,
    listViewModel: EtfListViewModel = hiltViewModel(),
    statisticsViewModel: StatisticsViewModel = hiltViewModel()
)

// Structure
Column {
    HubHeader(title = "ETF", ...)
    TabNavigationBar(tabs = ["테마 목록", "통계"], ...)
    HorizontalPager {
        page 0 -> EtfListHubContent
        page 1 -> StatisticsHubContent
    }
}
```

### 6.3 EtfListHubContent

```kotlin
@Composable
private fun EtfListHubContent(
    viewModel: EtfListViewModel,
    onEtfClick: (String) -> Unit
)

// Components
Column {
    // Search Field
    OutlinedTextField(
        placeholder = "ETF 검색...",
        leadingIcon = Icons.Default.Search,
        trailingIcon = if (query.isNotEmpty()) Icons.Default.Clear,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = surfaceVariant,
            unfocusedContainerColor = surfaceVariant,
            focusedBorderColor = Transparent,
            unfocusedBorderColor = Transparent
        )
    )

    // State-based content
    when (state) {
        Loading -> CircularProgressIndicator()
        Success -> LazyColumn { items(etfs) { EtfListItemCompact(...) } }
        Empty -> EmptyState(icon = SearchOff, message = "검색 결과가 없습니다")
        Error -> ErrorText(message)
    }
}
```

### 6.4 EtfListItemCompact

```kotlin
@Composable
private fun EtfListItemCompact(
    rank: Int,
    etf: Etf,
    onClick: () -> Unit
)

// Layout
Surface(shape = RoundedCornerShape(12.dp)) {
    Row(
        modifier = Modifier
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.02f),
                        surface
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Rank badge (circular, highlighted for top 3)
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = if (rank <= 3) primary.copy(alpha = 0.1f) else surfaceVariant
        ) {
            Text(rank.toString(), fontWeight = Bold)
        }

        // ETF info
        Column {
            Text(etf.name, style = bodyMedium, fontWeight = Medium)
            Text(etf.ticker, style = labelSmall, color = onSurfaceVariant)
        }

        // Chevron
        Icon(Icons.Default.ChevronRight)
    }
}
```

### 6.5 StatisticsHubContent

```kotlin
@Composable
private fun StatisticsHubContent(
    viewModel: StatisticsViewModel,
    onStockClick: (String) -> Unit,
    onNavigateToStocks: (String) -> Unit,
    initialStockTicker: String? = null
)

// 7 Tabs
val tabs = listOf(
    "보유량 순위",  // AmountRankingTab
    "신규 편입",    // StockChangeTab (NEW)
    "편출",         // StockChangeTab (REMOVED)
    "비중 증가",    // StockChangeTab (INCREASE)
    "비중 감소",    // StockChangeTab (DECREASE)
    "예금 추이",    // CashDepositTrendTab
    "분석"          // StockAnalysisTab
)

// Layout
Box {
    Column {
        ScrollableTabRow(selectedTabIndex, edgePadding = 16.dp)
        if (selectedTab != 6) {
            DateRangeSelector(...)
            Text("$previousDate ~ $currentDate")  // Date range display
        }
        // Tab content
        when (selectedTab) {
            0 -> AmountRankingTab(...)
            1 -> StockChangeTab(newStocks, NEW, ...)
            2 -> StockChangeTab(removedStocks, REMOVED, ...)
            3 -> StockChangeTab(increasedStocks, INCREASE, ...)
            4 -> StockChangeTab(decreasedStocks, DECREASE, ...)
            5 -> CashDepositTrendTab(...)
            6 -> StockAnalysisTab(...)
        }
    }

    // FAB for navigation (visible on Analysis tab with results)
    if (showFab) {
        ExtendedFloatingActionButton(
            onClick = { onNavigateToStocks(result.stockTicker) },
            icon = { Icon(Icons.Default.ShowChart) },
            text = { Text("종목 분석") }
        )
    }
}
```

### 6.6 EtfDetailScreen

```kotlin
@Composable
fun EtfDetailScreen(
    ticker: String,
    onBackClick: () -> Unit,
    onStockClick: (String) -> Unit,
    viewModel: EtfDetailViewModel = hiltViewModel()
)

// Layout
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(etfName) },
            subtitle = { Text("$previousDate ~ $currentDate") },
            navigationIcon = { BackButton(onBackClick) }
        )
    }
) {
    Column {
        // Date Range Selector
        DateRangeSelector(
            selectedRange = selectedRange,
            onRangeSelected = { viewModel.updateDateRange(it) },
            availableOptions = [DAY, WEEK, MONTH, THREE_MONTHS, SIX_MONTHS, YEAR, ALL]
        )

        // Holdings List
        LazyColumn {
            items(comparison.items) { holding ->
                HoldingComparisonItem(
                    holding = holding,
                    onClick = { onStockClick(holding.stockTicker) }
                )
            }
        }
    }
}
```

### 6.7 HoldingComparisonItem

```kotlin
@Composable
fun HoldingComparisonItem(
    holding: HoldingWithComparison,
    onClick: () -> Unit
)

// Status color mapping
val statusColor = when (holding.status) {
    HoldingStatus.NEW -> extendedColors.statusNew        // Green
    HoldingStatus.INCREASE -> extendedColors.statusIncrease  // Blue-green
    HoldingStatus.DECREASE -> extendedColors.statusDecrease  // Red
    HoldingStatus.REMOVED -> extendedColors.statusRemoved    // Gray
    HoldingStatus.MAINTAIN -> extendedColors.statusMaintain  // Olive
}

// Layout
Surface(shape = RoundedCornerShape(8.dp)) {
    Row {
        // Status indicator (vertical bar)
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(statusColor)
        )

        Column {
            // Stock info
            Row {
                Text(holding.stockName, fontWeight = Medium)
                Text(holding.stockTicker, color = onSurfaceVariant)
            }

            // Weight change
            Row {
                Text("${holding.previousWeight}% → ${holding.currentWeight}%")
                Text(
                    text = formatChange(holding.change),
                    color = if (holding.change >= 0) statusIncrease else statusDecrease
                )
            }

            // Amount
            Text(AmountFormatter.format(holding.currentAmount))
        }

        // Status badge
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = statusColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = holding.status.displayName,
                color = statusColor
            )
        }
    }
}
```

### 6.8 DateRangeSelector

```kotlin
@Composable
fun DateRangeSelector(
    selectedRange: DateRangeOption,
    onRangeSelected: (DateRangeOption) -> Unit,
    availableOptions: List<DateRangeOption> = DateRangeOption.values().toList()
)

// Layout
LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(availableOptions) { option ->
        FilterChip(
            selected = option == selectedRange,
            onClick = { onRangeSelected(option) },
            label = { Text(option.label) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = primary,
                selectedLabelColor = onPrimary
            )
        )
    }
}
```

### 6.9 State Classes

```kotlin
// EtfListState
sealed class EtfListState {
    object Loading : EtfListState()
    data class Success(val etfs: List<Etf>) : EtfListState()
    object Empty : EtfListState()
    data class Error(val message: String) : EtfListState()
}

// EtfDetailState
sealed class EtfDetailState {
    object Loading : EtfDetailState()
    data class Success(
        val etf: Etf,
        val comparison: ComparisonResult,
        val availableDates: List<String>
    ) : EtfDetailState()
    data class Error(val message: String) : EtfDetailState()
}

// DataProgress (for data collection)
sealed class DataProgress {
    data class Loading(val message: String, val progress: Int) : DataProgress()
    data class Success(val message: String) : DataProgress()
    data class Error(val message: String) : DataProgress()
}
```

---

## 7. Design System

### 7.1 Color Palette (Moss Green Nature Theme)

```kotlin
// ============ Light Theme ============
// Primary - Moss Green
val primaryLight = Color(0xFF4C6C43)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFCDEDA3)
val onPrimaryContainerLight = Color(0xFF102000)

// Secondary - Olive Green
val secondaryLight = Color(0xFF586249)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFDCE7C8)

// Tertiary - Teal Green
val tertiaryLight = Color(0xFF396663)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFBBEBEB)

// Surface - Warm Off-White
val backgroundLight = Color(0xFFFEFCF4)
val surfaceLight = Color(0xFFFEFCF4)
val surfaceVariantLight = Color(0xFFE1E4D5)

// ============ Dark Theme ============
// Primary - Light Moss Green
val primaryDark = Color(0xFFB1D18A)
val onPrimaryDark = Color(0xFF1F3701)
val primaryContainerDark = Color(0xFF354E16)

// Surface - Dark Greenish Gray
val backgroundDark = Color(0xFF1A1C18)
val surfaceDark = Color(0xFF1A1C18)
val surfaceVariantDark = Color(0xFF44483D)

// ============ Status Colors ============
val StatusNew = Color(0xFF4C6C43)       // Moss green - new
val StatusIncrease = Color(0xFF2E7D5A)  // Teal green - increase
val StatusDecrease = Color(0xFFBA1A1A)  // Red - decrease
val StatusRemoved = Color(0xFF8F9285)   // Gray - removed
val StatusMaintain = Color(0xFF586249)  // Olive - maintain

// ============ Chart Colors ============
val ChartPrimary = Color(0xFF4C6C43)
val ChartSecondary = Color(0xFF396663)
val ChartGreen = Color(0xFF2E7D5A)
val ChartRed = Color(0xFFBA1A1A)
```

### 7.2 Typography

```kotlin
val Typography = Typography(
    // Display
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp
    ),

    // Headlines
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),

    // Titles
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),

    // Body
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),

    // Labels
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
```

### 7.3 Spacing System

```kotlin
data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp
)
```

### 7.4 Shape System

```kotlin
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
```

### 7.5 Extended Theme Access

```kotlin
// Access extended colors
val MaterialTheme.extendedColors: ExtendedColors
    @Composable get() = LocalExtendedColors.current

// Usage
val statusColor = MaterialTheme.extendedColors.statusNew
```

---

## 8. Implementation Plan

### Phase 1: Project Setup (1-2 days)
**Prerequisites**: Android Studio, Kotlin 2.1.0, Gradle 8.x

| Step | Task | Details |
|------|------|---------|
| 1.1 | Create Android Project | New project with Jetpack Compose template |
| 1.2 | Configure build.gradle | Add all dependencies (Hilt, Room, Compose, Chaquopy) |
| 1.3 | Setup Chaquopy | Configure Python runtime with pykrx, pandas |
| 1.4 | Create folder structure | core/, feature/etf/, navigation/ |
| 1.5 | Configure Hilt | @HiltAndroidApp, modules |

**User Checkpoint**: Verify project builds and runs empty app

### Phase 2: Core Infrastructure (2-3 days)

| Step | Task | Details |
|------|------|---------|
| 2.1 | Implement AppDatabase | Room setup with all entities |
| 2.2 | Create DAOs | EtfDao, StockDao, DailyEtfStatisticsDao |
| 2.3 | Implement PyKrxClient | Python bridge for pykrx |
| 2.4 | Copy Python scripts | etfcollector.py, stocks.py, core.py |
| 2.5 | Create utility classes | AppLogger, AmountFormatter, DateFormatter |
| 2.6 | Setup DI modules | DatabaseModule, PythonModule |

**User Checkpoint**: Verify Python integration works (test getBusinessDays)

### Phase 3: Domain Layer (1-2 days)

| Step | Task | Details |
|------|------|---------|
| 3.1 | Create domain models | Etf, HoldingWithComparison, ComparisonResult, etc. |
| 3.2 | Define EtfRepository | Interface with all methods |
| 3.3 | Implement use cases | 7 use cases for ETF operations |
| 3.4 | Create EtfModule | DI bindings for domain layer |

**User Checkpoint**: Review domain model design

### Phase 4: Data Layer (2-3 days)

| Step | Task | Details |
|------|------|---------|
| 4.1 | Implement EtfLocalDataSource | Room DAO wrapper |
| 4.2 | Create EtfMapper | Entity ↔ Domain conversion |
| 4.3 | Implement EtfRepositoryImpl | Full data collection logic |
| 4.4 | Add parallel processing | Coroutine-based ETF processing |
| 4.5 | Implement statistics calculation | Daily statistics logic |
| 4.6 | Add settings management | Theme/exclusion keywords |

**User Checkpoint**: Test data collection (initialize 5 days of data)

### Phase 5: Design System (1-2 days)

| Step | Task | Details |
|------|------|---------|
| 5.1 | Create Color.kt | Moss Green Nature palette |
| 5.2 | Create Theme.kt | Light/Dark theme with extended colors |
| 5.3 | Create Type.kt | Typography scale |
| 5.4 | Create Shape.kt | Shape system |
| 5.5 | Create shared components | DateRangeSelector, TabNavigationBar, HubHeader |

**User Checkpoint**: Review design system in preview

### Phase 6: Presentation Layer - ETF List (2-3 days)

| Step | Task | Details |
|------|------|---------|
| 6.1 | Create EtfListState | Sealed class for UI state |
| 6.2 | Implement EtfListViewModel | State management with search debounce |
| 6.3 | Create EtfListScreen | Search field, list, empty/error states |
| 6.4 | Implement EtfListItemCompact | List item with gradient and rank |
| 6.5 | Add search functionality | 300ms debounce, clear button |

**User Checkpoint**: Verify ETF list displays and search works

### Phase 7: Presentation Layer - ETF Detail (2-3 days)

| Step | Task | Details |
|------|------|---------|
| 7.1 | Create EtfDetailState | Sealed class for detail state |
| 7.2 | Implement EtfDetailViewModel | Comparison loading with date range |
| 7.3 | Create EtfDetailScreen | TopAppBar, date selector, holdings list |
| 7.4 | Implement HoldingComparisonItem | Status colors, change indicators |
| 7.5 | Add DateRangeSelector integration | Period selection logic |

**User Checkpoint**: Verify detail screen shows comparison correctly

### Phase 8: Presentation Layer - Statistics (3-4 days)

| Step | Task | Details |
|------|------|---------|
| 8.1 | Create StatisticsViewModel | Multiple StateFlows for 7 tabs |
| 8.2 | Implement AmountRankingTab | Stock amount ranking list |
| 8.3 | Implement StockChangeTab | New/Removed/Increased/Decreased |
| 8.4 | Implement CashDepositTrendTab | Cash deposit chart and list |
| 8.5 | Implement StockAnalysisTab | Search and analysis UI |
| 8.6 | Create StatisticsHubContent | 7-tab container with date selector |

**User Checkpoint**: Verify all 7 statistics tabs work

### Phase 9: Hub Screen & Navigation (1-2 days)

| Step | Task | Details |
|------|------|---------|
| 9.1 | Create EtfHubScreen | HorizontalPager with 2 main tabs |
| 9.2 | Implement HubHeader | Title, theme toggle, settings |
| 9.3 | Setup Navigation | NavHost with all routes |
| 9.4 | Add FAB navigation | Stock analysis deep link |
| 9.5 | Handle initialStockTicker | Auto-navigate to analysis tab |

**User Checkpoint**: Test full navigation flow

### Phase 10: Polish & Testing (2-3 days)

| Step | Task | Details |
|------|------|---------|
| 10.1 | Add loading states | Progress indicators everywhere |
| 10.2 | Add error handling | Error cards with retry |
| 10.3 | Implement data refresh | Pull-to-refresh, auto-refresh |
| 10.4 | Add animations | Tab transitions, list animations |
| 10.5 | Write unit tests | ViewModel, Repository tests |
| 10.6 | Write UI tests | Screen navigation tests |
| 10.7 | Performance optimization | Memory limits, query optimization |

**User Checkpoint**: Final QA and acceptance

---

## 9. Skills & Agents

### 9.1 Available Skills

| Skill | Purpose | Usage |
|-------|---------|-------|
| **Explore** | Codebase exploration | Find files, understand structure |
| **Bash** | Command execution | Build, test, git operations |
| **Read** | File reading | Read source files |
| **Write** | File creation | Create new files |
| **Edit** | File modification | Modify existing files |
| **Glob** | File pattern matching | Find files by pattern |
| **Grep** | Content search | Search code content |

### 9.2 Recommended Agents

| Agent | Type | Usage |
|-------|------|-------|
| **Explore** | Codebase analysis | Research existing implementation patterns |
| **Plan** | Architecture planning | Design implementation strategy |
| **verify-app** | Quality assurance | Test app after changes |
| **code-simplifier** | Code optimization | Simplify complex code |
| **Bash** | Build & test | Run gradle commands |

### 9.3 Agent Usage Examples

```
# Explore existing implementation
Task(subagent_type="Explore", prompt="Find all files related to ETF feature...")

# Plan implementation
Task(subagent_type="Plan", prompt="Create implementation plan for ETF list screen...")

# Verify app works
Task(subagent_type="verify-app", prompt="Test ETF list screen navigation...")
```

---

## 10. Prerequisites

### 10.1 Required Skills/Agents to Implement First

Before implementing the ETF app, these supporting components must be created:

#### 10.1.1 Python Data Collection Agent

```
Name: etf-data-collector
Purpose: Manage Python-based ETF data collection
Tools: Bash, Read, Write
Functions:
  - Initialize pykrx environment
  - Test Python scripts
  - Debug data collection issues
```

#### 10.1.2 Database Migration Agent

```
Name: db-migration-helper
Purpose: Handle Room database migrations
Tools: Read, Write, Edit
Functions:
  - Generate migration scripts
  - Validate schema changes
  - Test migration paths
```

#### 10.1.3 UI Component Library

Before building screens, implement these shared components:

| Component | File | Priority |
|-----------|------|----------|
| DateRangeSelector | core/ui/component/DateRangeSelector.kt | High |
| TabNavigationBar | core/ui/component/TabNavigationBar.kt | High |
| HubHeader | core/ui/component/HubHeader.kt | High |
| LoadingIndicator | core/ui/component/LoadingIndicator.kt | Medium |
| ErrorCard | core/ui/component/ErrorCard.kt | Medium |
| EmptyStateCard | core/ui/component/EmptyStateCard.kt | Medium |
| FilterChipRow | core/ui/component/FilterChipRow.kt | Low |

### 10.2 Development Environment

| Requirement | Version | Notes |
|-------------|---------|-------|
| Android Studio | Ladybug (2024.2.1) | Latest stable |
| JDK | 17+ | Required for Gradle 8.x |
| Kotlin | 2.1.0 | Configured in libs.versions.toml |
| Gradle | 8.9+ | Wrapper included |
| Min SDK Device | API 26 (Android 8.0) | For testing |

### 10.3 Python Dependencies (Chaquopy)

```gradle
python {
    pip {
        install("pykrx")
        install("pandas")
        install("numpy")
        install("requests")
        install("beautifulsoup4")
    }
}
```

### 10.4 Kotlin Dependencies

```toml
# libs.versions.toml
[versions]
kotlin = "2.1.0"
compose-bom = "2024.12.01"
hilt = "2.54"
room = "2.8.3"
coroutines = "1.10.2"
navigation = "2.8.5"
chaquopy = "15.0.1"
vico = "2.0.0-alpha.28"

[libraries]
# Compose
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }

# Hilt
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version = "1.2.0" }

# Room
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }

# Coroutines
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }

# Charts
vico-compose = { module = "com.patrykandpatrick.vico:compose", version.ref = "vico" }
vico-compose-m3 = { module = "com.patrykandpatrick.vico:compose-m3", version.ref = "vico" }
```

---

## Appendix A: Key File Templates

### A.1 build.gradle.kts (app)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    id("com.chaquo.python")
}

android {
    namespace = "com.etfapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.etfapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
    }
}

chaquopy {
    defaultConfig {
        pip {
            install("pykrx")
            install("pandas")
            install("numpy")
            install("requests")
            install("beautifulsoup4")
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
}
```

### A.2 EtfRepository Interface

```kotlin
interface EtfRepository {
    // List
    fun getAllEtfs(): Flow<List<Etf>>
    fun searchEtfs(query: String): Flow<List<Etf>>

    // Status
    suspend fun hasData(): Boolean
    suspend fun getDataStatus(): DataStatus
    suspend fun getLatestDate(): String?

    // Detail
    suspend fun getEtf(ticker: String): Etf?
    suspend fun getComparison(etfTicker: String): ComparisonResult?
    suspend fun getComparisonInRange(etfTicker: String, startDate: String, endDate: String): ComparisonResult?
    suspend fun getAvailableDates(limit: Int = 100): List<String>

    // Collection
    fun initializeData(days: Int = 25): Flow<DataProgress>
    fun updateData(): Flow<DataProgress>
    suspend fun resetDatabase()
    suspend fun trimDataToPeriod(days: Int): Int

    // Settings
    suspend fun getDefaultDays(): Int
    suspend fun setDefaultDays(days: Int)
    suspend fun getThemes(): List<String>
    suspend fun addTheme(theme: String)
    suspend fun removeTheme(theme: String)
    suspend fun getExclusions(): List<String>
    suspend fun addExclusion(keyword: String)
    suspend fun removeExclusion(keyword: String)
}
```

---

## Appendix B: Navigation Routes

```kotlin
sealed class Screen(val route: String) {
    object EtfHub : Screen("etf_hub?stockTicker={stockTicker}") {
        fun createRoute(stockTicker: String? = null): String {
            return if (stockTicker != null) "etf_hub?stockTicker=$stockTicker" else "etf_hub"
        }
    }

    object EtfList : Screen("etf_list")

    object EtfDetail : Screen("etf_detail/{ticker}") {
        fun createRoute(ticker: String) = "etf_detail/$ticker"
    }

    object StockTrend : Screen("stock_trend/{etfTicker}/{stockTicker}") {
        fun createRoute(etfTicker: String, stockTicker: String) =
            "stock_trend/$etfTicker/$stockTicker"
    }

    object AggregatedTrend : Screen("aggregated_trend/{stockTicker}") {
        fun createRoute(stockTicker: String) = "aggregated_trend/$stockTicker"
    }
}
```

---

## Appendix C: String Resources (Korean)

```xml
<!-- strings.xml -->
<resources>
    <string name="app_name">ETF Monitor</string>

    <!-- Hub -->
    <string name="tab_theme_list">테마 목록</string>
    <string name="tab_statistics">통계</string>

    <!-- Search -->
    <string name="search_etf_hint">ETF 검색...</string>
    <string name="search_clear">지우기</string>
    <string name="search_no_results">검색 결과가 없습니다</string>
    <string name="no_etf_data">ETF 데이터가 없습니다</string>

    <!-- Statistics Tabs -->
    <string name="statistics_tab_amount_ranking">보유량 순위</string>
    <string name="statistics_tab_new">신규 편입</string>
    <string name="statistics_tab_removed">편출</string>
    <string name="statistics_tab_increased">비중 증가</string>
    <string name="statistics_tab_decreased">비중 감소</string>
    <string name="statistics_tab_cash_deposit">예금 추이</string>
    <string name="statistics_tab_analysis">분석</string>

    <!-- Date Range -->
    <string name="date_range_day">1일</string>
    <string name="date_range_week">1주</string>
    <string name="date_range_month">1개월</string>
    <string name="date_range_three_months">3개월</string>
    <string name="date_range_six_months">6개월</string>
    <string name="date_range_year">1년</string>
    <string name="date_range_all">전체</string>

    <!-- Status -->
    <string name="status_new">신규</string>
    <string name="status_increase">증가</string>
    <string name="status_decrease">감소</string>
    <string name="status_removed">편출</string>
    <string name="status_maintain">유지</string>

    <!-- Data Collection -->
    <string name="initializing">초기화 시작</string>
    <string name="calculating_business_days">영업일 계산 중</string>
    <string name="collecting_data">데이터 수집 중 (%1$d/%2$d) %3$s</string>
    <string name="initialization_complete">초기화 완료! ETF %1$d개 수집 (%2$d초 소요)</string>
    <string name="no_business_days">영업일을 찾을 수 없습니다</string>
    <string name="no_include_keywords">포함 키워드가 없습니다</string>

    <!-- FAB -->
    <string name="fab_stock_analysis">종목 분석</string>
</resources>
```

---

**Document End**

*This specification provides complete instructions for replicating the ETF feature as a standalone application. Follow the implementation plan in order, checking with the user at each checkpoint before proceeding.*
