# /project:swap-datasource — Cross-Agent DataSource Swap Protocol

Orchestrate a data source swap using the data-agent only — zero cross-boundary impact.

## Protocol (from docs/AGENTS.md)

This is a **data-agent only** operation:

1. **data-agent** creates new DataSourceImpl
2. **data-agent** updates Hilt module binding
3. **NO other agents involved** — zero cross-boundary impact

## How It Works

The repository interface stays the same. Only the implementation changes.

### Example: Swap from Python to REST API

**Before:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class MarketModule {
    @Binds
    abstract fun bindRepository(impl: PythonMarketRepository): MarketRepository
}
```

**After:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class MarketModule {
    @Binds
    abstract fun bindRepository(impl: RestApiMarketRepository): MarketRepository
}
```

## Usage

Provide the datasource to swap:
```
/project:swap-datasource Replace PyKrxClient-based ETF data with direct REST API
```

## Validation
- Repository interface unchanged
- All existing tests pass
- No changes outside data layer
