# AGENTS.md — Claude Code Agent Teams Configuration

## Quick Start

### Using Agents via Task Tool
Each agent can be spawned as a sub-agent using the Task tool:
```
Task(subagent_type="feature-agent", prompt="Create a new sector analysis screen with...")
Task(subagent_type="domain-agent", prompt="Create GetSectorDataUseCase...")
Task(subagent_type="data-agent", prompt="Implement SectorRepositoryImpl...")
Task(subagent_type="test-agent", prompt="Write tests for SectorViewModel...")
```

### Using Cross-Agent Commands
```
/project:spawn-feature   — Orchestrate full feature creation (all 6 agents)
/project:swap-datasource — Swap data source implementation (data-agent only)
```

### Agent Files Location
All agent definitions: `.claude/agents/*.md`
Cross-agent commands: `.claude/commands/*.md`

---

## Agent: feature-agent
### Role: Feature UI + ViewModel development
### Allowed Paths (EtfMonitor)
- `app/src/main/java/com/etfmonitor/feature/*/presentation/`
- `app/src/main/java/com/etfmonitor/feature/*/di/` (ViewModel bindings only)
- `app/src/main/java/com/etfmonitor/navigation/`
### Forbidden: `core/database/**`, `core/network/**`, `feature/*/data/**`
### Rules:
- ONE ViewModel + ONE UiState + ONE UiEvent per screen
- ViewModel receives UseCases via @HiltViewModel constructor injection
- NEVER access Repository/DataSource directly
- Use collectAsStateWithLifecycle() in Composables
- One-time effects via SharedFlow<UiEffect>

## Agent: domain-agent
### Role: Business logic (UseCase) + domain models + repository interfaces
### Allowed Paths (EtfMonitor)
- `app/src/main/java/com/etfmonitor/feature/*/domain/model/`
- `app/src/main/java/com/etfmonitor/feature/*/domain/usecase/`
- `app/src/main/java/com/etfmonitor/feature/*/domain/repository/`
### Forbidden: ALL other packages
### Rules:
- Pure Kotlin only (no android.* imports except @Inject)
- UseCase = class with suspend operator fun invoke() returning Result<T>
- Repository = interface only (no implementation here)
- Domain models = plain data classes

## Agent: data-agent
### Role: Repository impl + DataSource + Mapper + DTO/Entity + Hilt modules
### Allowed Paths (EtfMonitor)
- `app/src/main/java/com/etfmonitor/feature/*/data/`
- `app/src/main/java/com/etfmonitor/core/database/`
- `app/src/main/java/com/etfmonitor/core/network/`
- `app/src/main/java/com/etfmonitor/core/di/`
- `app/src/main/java/com/etfmonitor/feature/*/di/`
- `app/src/main/python/`
### Forbidden: `feature/*/presentation/**`
### Read-Only: `feature/*/domain/**` (interfaces to implement)
### Rules:
- RepositoryImpl implements interface from domain layer
- DataSource swap = new impl + update Hilt binding (nothing else changes)
- Mappers bridge DTO <-> Domain <-> Entity
- All impl classes are `internal`
- Holding entity: ALWAYS use `Holding.create()` factory
- StockAnalysisData: ALWAYS use JOIN with stocks table

## Agent: ui-agent
### Role: Design system + shared UI components
### Allowed Paths (EtfMonitor)
- `app/src/main/java/com/etfmonitor/core/ui/theme/`
- `app/src/main/java/com/etfmonitor/core/ui/component/`
- `app/src/main/res/values/`, `app/src/main/res/values-night/`
### Forbidden: All other modules
### Rules:
- `core/ui/theme/` = Material 3 theme, colors, typography
- `core/ui/component/` = reusable composables that render domain models
- All composables must have @Preview

## Agent: integration-agent
### Role: App wiring, navigation host, build config
### Allowed Paths (EtfMonitor)
- `app/src/main/java/com/etfmonitor/MainActivity.kt`
- `app/src/main/java/com/etfmonitor/EtfMonitorApp.kt`
- `app/src/main/java/com/etfmonitor/navigation/`
- `app/src/main/java/com/etfmonitor/core/worker/`
- `app/src/main/java/com/etfmonitor/core/service/`
- `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`
### Forbidden: Implementation details of any module

## Agent: test-agent
### Role: Tests across all modules
### Allowed Paths (EtfMonitor)
- `app/src/test/java/com/etfmonitor/`
- `app/src/androidTest/java/com/etfmonitor/`
### Rules:
- Shared fakes in TestUtils.kt
- Domain: Pure JUnit + FakeRepository
- ViewModel: JUnit + Turbine + fake UseCases
- Repository: JUnit + fake DataSources
- Migration: Room MigrationTestHelper

## Cross-Agent Data Source Swap Protocol
> Command: `/project:swap-datasource`
1. data-agent creates new DataSourceImpl
2. data-agent updates Hilt module binding
3. NO other agents involved — zero cross-boundary impact

## Cross-Agent New Feature Protocol
> Command: `/project:spawn-feature`
1. feature-agent defines needed UseCase signature
2. domain-agent creates UseCase + repository interface
3. data-agent implements repository + data sources
4. feature-agent integrates UseCase into ViewModel
5. integration-agent registers navigation in AppNavHost
6. test-agent writes tests across all layers
