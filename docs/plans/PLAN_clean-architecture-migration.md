# Implementation Plan: Clean Architecture Migration

**Status**: 🔄 In Progress
**Started**: 2024-12-24
**Last Updated**: 2024-12-24
**Target Module**: `app`
**Min SDK**: 26 | **Target SDK**: 35

---

**CRITICAL INSTRUCTIONS**: After completing each phase:
1. Check off completed task checkboxes
2. Run all quality gate validation commands
3. Verify ALL quality gate items pass
4. Update "Last Updated" date above
5. Document learnings in Notes section
6. Only then proceed to next phase

**DO NOT skip quality gates or proceed with failing checks**

---

## Overview

### Feature Description
Migrate EtfMonitor codebase from current flat package structure to Clean Architecture with feature-based modularization. This enables better separation of concerns, testability, and maintainability.

### Current vs Target Structure

**Current Structure (Flat):**
```
com.etfmonitor/
├── ui/screens/           # 14 screens mixed
├── repository/           # 13 repositories
├── database/             # 19 entities, 16 DAOs
├── di/                   # 5 Hilt modules
├── python/               # Python clients
├── ai/                   # AI integration
├── analysis/             # Analysis utilities
├── worker/               # Workers
└── service/              # Services
```

**Target Structure (Clean Architecture):**
```
com.etfmonitor/
├── core/                           # Shared infrastructure
│   ├── common/                     # Extensions, utilities
│   ├── database/                   # Room database, migrations
│   │   ├── entity/                 # All entities
│   │   ├── dao/                    # All DAOs
│   │   └── AppDatabase.kt
│   ├── di/                         # Core DI modules
│   ├── network/                    # API clients (AI)
│   ├── python/                     # Python bridge
│   └── ui/                         # Common UI components
│
├── feature/                        # Feature modules
│   ├── home/
│   │   ├── data/                   # Repository implementations
│   │   ├── domain/                 # Use cases, models, interfaces
│   │   └── presentation/           # Screen, ViewModel, State
│   │
│   ├── etf/                        # ETF list, detail, comparison
│   │   ├── data/
│   │   ├── domain/
│   │   └── presentation/
│   │
│   ├── stock/                      # Stock trend, oscillator
│   │   ├── data/
│   │   ├── domain/
│   │   └── presentation/
│   │
│   ├── market/                     # Market deposit, oscillator, F&G
│   │   ├── data/
│   │   ├── domain/
│   │   └── presentation/
│   │
│   ├── analysis/                   # Advanced analysis, correlation
│   │   ├── data/
│   │   ├── domain/
│   │   └── presentation/
│   │
│   ├── ai/                         # AI analysis, chat
│   │   ├── data/
│   │   ├── domain/
│   │   └── presentation/
│   │
│   └── settings/                   # Settings, statistics
│       ├── data/
│       ├── domain/
│       └── presentation/
│
├── worker/                         # Background workers
├── service/                        # Foreground services
└── MainActivity.kt
```

### Success Criteria
- [ ] All 14 screens migrated to feature modules
- [ ] All 13 repositories refactored with domain interfaces
- [ ] Use cases created for complex business logic
- [ ] Core module contains shared infrastructure
- [ ] All existing functionality preserved
- [ ] Build succeeds without errors
- [ ] No regressions in app behavior

### User Impact
- No visible changes to users
- Improved code maintainability for developers
- Better testability for future development
- Clearer boundaries between features

### Affected Components (162 Kotlin files)
- 14 Screens with ViewModels
- 13 Repositories
- 19 Database Entities
- 16 DAOs
- 5 DI Modules
- 3 Python Clients
- 11 AI Integration Files
- 7 Workers

---

## Architecture Decisions

| Decision | Rationale | Trade-offs |
|----------|-----------|------------|
| Feature-based modules (not layer-based) | Features are cohesive units; easier navigation | Cross-feature dependencies need careful management |
| Core module for shared code | Avoids duplication; single source of truth | Core becomes a dependency for all features |
| Keep database centralized in core | Entities are shared across features | Feature modules depend on core for data access |
| Domain layer with use cases | Encapsulates business logic; testable | Additional abstraction layer |
| Repository interfaces in domain | Dependency inversion principle | More interfaces to maintain |

### Architecture Diagram
```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Presentation Layer                             │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  feature/home/    feature/etf/    feature/stock/    feature/ai/  │   │
│  │  presentation/    presentation/   presentation/     presentation/ │   │
│  │  ├─ Screen        ├─ Screen       ├─ Screen         ├─ Screen    │   │
│  │  ├─ ViewModel     ├─ ViewModel    ├─ ViewModel      ├─ ViewModel │   │
│  │  └─ State         └─ State        └─ State          └─ State     │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                    │                                     │
├────────────────────────────────────┼─────────────────────────────────────┤
│                           Domain Layer                                   │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  feature/*/domain/                                                │   │
│  │  ├─ usecase/          (GetEtfListUseCase, AnalyzeMarketUseCase)  │   │
│  │  ├─ model/            (Domain models - pure Kotlin)               │   │
│  │  └─ repository/       (Repository interfaces)                     │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                    │                                     │
├────────────────────────────────────┼─────────────────────────────────────┤
│                            Data Layer                                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  feature/*/data/                                                  │   │
│  │  └─ repository/       (Repository implementations)                │   │
│  │                                                                   │   │
│  │  core/database/                                                   │   │
│  │  ├─ entity/           (Room entities)                            │   │
│  │  ├─ dao/              (Room DAOs)                                │   │
│  │  └─ AppDatabase.kt    (Database singleton)                       │   │
│  │                                                                   │   │
│  │  core/python/         (Python clients)                           │   │
│  │  core/network/        (AI API clients)                           │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Dependencies

### Required Before Starting
- [ ] Git branch created: `claude/clean-architecture-migration-ZcuXV`
- [ ] Current build passes: `./gradlew assembleDebug`
- [ ] All tests pass (if any exist)

### External Dependencies (No changes required)
Current dependencies in `gradle/libs.versions.toml` are sufficient.

---

## Implementation Phases

### Phase 1: Core Module Structure (Foundation)
**Goal**: Create core package structure and migrate shared utilities
**Estimated Time**: 2-3 hours
**Status**: Pending

#### Tasks

**Task 1.1**: Create core package structure
- [ ] Create `core/common/` - extensions, utilities
- [ ] Create `core/database/` - database infrastructure
- [ ] Create `core/database/entity/` - all entities
- [ ] Create `core/database/dao/` - all DAOs
- [ ] Create `core/di/` - core DI modules
- [ ] Create `core/python/` - Python clients
- [ ] Create `core/network/` - AI API clients
- [ ] Create `core/ui/` - common UI components

**Task 1.2**: Migrate utilities to core
- [ ] Move `utils/` contents to `core/common/`
- [ ] Update package declarations
- [ ] Update imports in dependent files

**Task 1.3**: Migrate Python clients to core
- [ ] Move `python/PyKrxClient.kt` to `core/python/`
- [ ] Move `python/MarketIndexPyClient.kt` to `core/python/`
- [ ] Move `oscillator/python/OscillatorPyClient.kt` to `core/python/`
- [ ] Update package declarations and imports

**Task 1.4**: Migrate AI clients to core/network
- [ ] Move `ai/AIApiClient.kt` to `core/network/ai/`
- [ ] Move `ai/ClaudeApiClient.kt` to `core/network/ai/`
- [ ] Move `ai/GeminiApiClient.kt` to `core/network/ai/`
- [ ] Move `ai/AIApiClientFactory.kt` to `core/network/ai/`
- [ ] Move `ai/ApiKeyProvider.kt` to `core/network/ai/`
- [ ] Move `ai/SharedPreferencesApiKeyProvider.kt` to `core/network/ai/`
- [ ] Move `ai/AIModel.kt` to `core/network/ai/`
- [ ] Move `ai/AIProvider.kt` to `core/network/ai/`
- [ ] Move `ai/MarketAnalysisPrompts.kt` to `core/network/ai/`
- [ ] Move `ai/AIResponseParser.kt` to `core/network/ai/`
- [ ] Move `ai/MarketSignal.kt` to `core/network/ai/`
- [ ] Update all imports

#### Quality Gate
```bash
./gradlew assembleDebug
./gradlew lint
```
- [ ] Build succeeds
- [ ] No unresolved imports
- [ ] App launches and basic navigation works

---

### Phase 2: Database Layer Migration
**Goal**: Migrate all entities and DAOs to core/database
**Estimated Time**: 2-3 hours
**Status**: Pending

#### Tasks

**Task 2.1**: Migrate entities to core/database/entity
- [ ] Move all 18 entity files from `database/entities/` to `core/database/entity/`
- [ ] Update package declarations
- [ ] Preserve `Holding.create()` factory method

**Task 2.2**: Migrate DAOs to core/database/dao
- [ ] Move all 16 DAO files from `database/` to `core/database/dao/`
- [ ] Move `Converters.kt` to `core/database/`
- [ ] Update package declarations

**Task 2.3**: Migrate AppDatabase
- [ ] Move `AppDatabase.kt` to `core/database/`
- [ ] Update entity and DAO references
- [ ] Ensure all 13 migrations preserved

**Task 2.4**: Update DatabaseModule
- [ ] Move `di/DatabaseModule.kt` to `core/di/`
- [ ] Update all DAO provider paths
- [ ] Update database builder path

**Task 2.5**: Update all repository imports
- [ ] Update imports in all 13 repositories
- [ ] Update imports in ViewModels
- [ ] Update imports in workers

#### Quality Gate
```bash
./gradlew assembleDebug
./gradlew lint
```
- [ ] Build succeeds
- [ ] Database migrations still work
- [ ] App can read/write data correctly

---

### Phase 3: Feature Module Structure - Home & ETF
**Goal**: Create feature modules for Home and ETF features
**Estimated Time**: 3-4 hours
**Status**: Pending

#### Tasks

**Task 3.1**: Create Home feature structure
- [ ] Create `feature/home/data/repository/`
- [ ] Create `feature/home/domain/model/`
- [ ] Create `feature/home/domain/repository/`
- [ ] Create `feature/home/domain/usecase/`
- [ ] Create `feature/home/presentation/screen/`
- [ ] Create `feature/home/presentation/viewmodel/`
- [ ] Create `feature/home/presentation/state/`

**Task 3.2**: Migrate Home feature
- [ ] Move `HomeScreen.kt` to `feature/home/presentation/screen/`
- [ ] Move `HomeViewModel.kt` to `feature/home/presentation/viewmodel/`
- [ ] Extract `HomeState` to `feature/home/presentation/state/`
- [ ] Create `HomeRepository` interface in domain
- [ ] Move `DataRepository` to `feature/home/data/repository/` as implementation
- [ ] Update Navigation.kt with new import paths

**Task 3.3**: Create ETF feature structure
- [ ] Create `feature/etf/data/repository/`
- [ ] Create `feature/etf/domain/model/`
- [ ] Create `feature/etf/domain/repository/`
- [ ] Create `feature/etf/domain/usecase/`
- [ ] Create `feature/etf/presentation/screen/`
- [ ] Create `feature/etf/presentation/viewmodel/`
- [ ] Create `feature/etf/presentation/state/`
- [ ] Create `feature/etf/presentation/component/`

**Task 3.4**: Migrate ETF List feature
- [ ] Move `EtfListScreen.kt` to `feature/etf/presentation/screen/`
- [ ] Move `EtfListViewModel.kt` to `feature/etf/presentation/viewmodel/`
- [ ] Extract `ListState` to `feature/etf/presentation/state/`
- [ ] Move `EtfHubScreen.kt` to `feature/etf/presentation/screen/`

**Task 3.5**: Migrate ETF Detail feature
- [ ] Move `DetailScreen.kt` to `feature/etf/presentation/screen/`
- [ ] Move `DetailViewModel.kt` to `feature/etf/presentation/viewmodel/`
- [ ] Create `EtfRepository` interface in domain
- [ ] Update Navigation.kt

#### Quality Gate
```bash
./gradlew assembleDebug
./gradlew lint
```
- [ ] Build succeeds
- [ ] Home screen displays correctly
- [ ] ETF list and detail screens work
- [ ] Navigation between screens works

---

### Phase 4: Feature Module Structure - Stock & Market
**Goal**: Create feature modules for Stock and Market features
**Estimated Time**: 3-4 hours
**Status**: Pending

#### Tasks

**Task 4.1**: Create Stock feature structure
- [ ] Create `feature/stock/data/`
- [ ] Create `feature/stock/domain/`
- [ ] Create `feature/stock/presentation/`

**Task 4.2**: Migrate Stock feature
- [ ] Move `StockTrendScreen.kt` to `feature/stock/presentation/screen/`
- [ ] Move `StockTrendViewModel.kt` to `feature/stock/presentation/viewmodel/`
- [ ] Move `AggregatedStockTrendScreen.kt` to `feature/stock/presentation/screen/`
- [ ] Move `OscillatorScreen.kt` to `feature/stock/presentation/screen/`
- [ ] Move `OscillatorViewModel.kt` to `feature/stock/presentation/viewmodel/`
- [ ] Move `StocksHubScreen.kt` to `feature/stock/presentation/screen/`
- [ ] Create `StockRepository` interface in domain
- [ ] Move `StockRepository.kt` implementation to data
- [ ] Move `StockAnalysisRepository.kt` implementation to data

**Task 4.3**: Create Market feature structure
- [ ] Create `feature/market/data/`
- [ ] Create `feature/market/domain/`
- [ ] Create `feature/market/presentation/`

**Task 4.4**: Migrate Market feature
- [ ] Move `MarketDepositScreen.kt` to `feature/market/presentation/screen/`
- [ ] Move `MarketDepositViewModel.kt` to `feature/market/presentation/viewmodel/`
- [ ] Move `FearGreedScreen.kt` to `feature/market/presentation/screen/`
- [ ] Move `FearGreedViewModel.kt` to `feature/market/presentation/viewmodel/`
- [ ] Move `MarketOscillatorScreen.kt` to `feature/market/presentation/screen/`
- [ ] Move `MarketOscillatorViewModel.kt` to `feature/market/presentation/viewmodel/`
- [ ] Create market domain interfaces
- [ ] Move market repository implementations to data

#### Quality Gate
```bash
./gradlew assembleDebug
./gradlew lint
```
- [ ] Build succeeds
- [ ] Stock screens work correctly
- [ ] Market screens work correctly
- [ ] All data displays properly

---

### Phase 5: Feature Module Structure - Analysis & AI
**Goal**: Create feature modules for Analysis and AI features
**Estimated Time**: 3-4 hours
**Status**: Pending

#### Tasks

**Task 5.1**: Create Analysis feature structure
- [ ] Create `feature/analysis/data/`
- [ ] Create `feature/analysis/domain/`
- [ ] Create `feature/analysis/presentation/`

**Task 5.2**: Migrate Analysis feature
- [ ] Move `AdvancedDashboardScreen.kt` to `feature/analysis/presentation/screen/`
- [ ] Move `AdvancedDashboardViewModel.kt` to `feature/analysis/presentation/viewmodel/`
- [ ] Move `AnalysisHubScreen.kt` to `feature/analysis/presentation/screen/`
- [ ] Move `analysis/CorrelationAnalyzer.kt` to `feature/analysis/domain/`
- [ ] Move `analysis/Backtester.kt` to `feature/analysis/domain/`
- [ ] Create analysis domain interfaces
- [ ] Move `AdvancedAnalysisRepository.kt` to data
- [ ] Move `CorrelationAnalysisRepository.kt` to data
- [ ] Move `StatisticsAnalysisRepository.kt` to data

**Task 5.3**: Create AI feature structure
- [ ] Create `feature/ai/data/`
- [ ] Create `feature/ai/domain/`
- [ ] Create `feature/ai/presentation/`

**Task 5.4**: Migrate AI feature
- [ ] Move `NewAIAnalysisScreen.kt` to `feature/ai/presentation/screen/`
- [ ] Move `NewAIAnalysisViewModel.kt` to `feature/ai/presentation/viewmodel/`
- [ ] Create AI domain interfaces
- [ ] Move `AIAnalysisRepository.kt` to data
- [ ] Move `AIChatRepository.kt` to data
- [ ] Move `TimeSeriesAnalysisRepository.kt` to data

#### Quality Gate
```bash
./gradlew assembleDebug
./gradlew lint
```
- [ ] Build succeeds
- [ ] Advanced dashboard works
- [ ] AI analysis screen works
- [ ] Chat functionality works

---

### Phase 6: Feature Module Structure - Settings & Statistics
**Goal**: Create feature modules for Settings and Statistics
**Estimated Time**: 2-3 hours
**Status**: Pending

#### Tasks

**Task 6.1**: Create Settings feature structure
- [ ] Create `feature/settings/data/`
- [ ] Create `feature/settings/domain/`
- [ ] Create `feature/settings/presentation/`

**Task 6.2**: Migrate Settings feature
- [ ] Move `SettingsScreen.kt` to `feature/settings/presentation/screen/`
- [ ] Move `SettingsViewModel.kt` to `feature/settings/presentation/viewmodel/`
- [ ] Move `settings/components/` to `feature/settings/presentation/component/`
- [ ] Create settings domain interfaces

**Task 6.3**: Migrate Statistics feature
- [ ] Move all Statistics screens to `feature/settings/presentation/screen/`
- [ ] Move `StatisticsViewModel.kt` to `feature/settings/presentation/viewmodel/`
- [ ] Handle 25+ individual StateFlows pattern

#### Quality Gate
```bash
./gradlew assembleDebug
./gradlew lint
```
- [ ] Build succeeds
- [ ] Settings screen works with all toggles
- [ ] Statistics screens display correctly
- [ ] API key management works

---

### Phase 7: DI Migration & Final Cleanup
**Goal**: Consolidate DI modules and clean up legacy code
**Estimated Time**: 2-3 hours
**Status**: Pending

#### Tasks

**Task 7.1**: Reorganize DI modules
- [ ] Move `RepositoryModule.kt` to `core/di/`
- [ ] Move `PythonModule.kt` to `core/di/`
- [ ] Move `AIModule.kt` to `core/di/`
- [ ] Move `WorkerModule.kt` to `core/di/`
- [ ] Create feature-specific DI modules if needed

**Task 7.2**: Migrate Workers
- [ ] Keep workers in `worker/` package (shared infrastructure)
- [ ] Update all repository imports in workers
- [ ] Verify worker scheduling works

**Task 7.3**: Migrate Services
- [ ] Keep services in `service/` package
- [ ] Update all repository imports

**Task 7.4**: Update Navigation
- [ ] Update `Navigation.kt` with all new screen paths
- [ ] Verify all routes work correctly
- [ ] Test deep linking if applicable

**Task 7.5**: Remove legacy packages
- [ ] Remove empty `repository/` package
- [ ] Remove empty `database/entities/` package
- [ ] Remove empty `ai/` package
- [ ] Remove empty `analysis/` package
- [ ] Remove empty `oscillator/` package
- [ ] Clean up any orphaned files

**Task 7.6**: Update documentation
- [ ] Update CLAUDE.md with new structure
- [ ] Update package references
- [ ] Document new architecture

#### Quality Gate
```bash
./gradlew clean
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew lint
```
- [ ] Clean build succeeds
- [ ] Release build succeeds
- [ ] All 14 screens work correctly
- [ ] All background workers execute
- [ ] No lint errors

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation Strategy |
|------|-------------|--------|---------------------|
| Import resolution failures | High | Medium | Migrate incrementally; fix imports after each file move |
| Circular dependencies | Medium | High | Keep domain layer free of data layer dependencies |
| Hilt injection failures | Medium | High | Update module bindings immediately after moving |
| Database migration issues | Low | High | Don't modify AppDatabase entity definitions |
| Runtime crashes | Medium | Medium | Test each screen after migration |
| Merge conflicts | Low | Medium | Work on dedicated branch; commit frequently |

---

## Rollback Strategy

### If Phase 1-2 Fails
```bash
git reset --hard HEAD~N  # Reset to before migration
```

### If Phase 3-6 Fails
```bash
git revert HEAD~N..HEAD  # Revert specific phase commits
```

### General Rollback
- Each phase should be a separate commit
- Tag before major phases: `git tag pre-phase-N`
- Can revert to any phase independently

---

## Progress Tracking

### Completion Status
- **Phase 1 (Core Structure)**: Pending 0%
- **Phase 2 (Database)**: Pending 0%
- **Phase 3 (Home & ETF)**: Pending 0%
- **Phase 4 (Stock & Market)**: Pending 0%
- **Phase 5 (Analysis & AI)**: Pending 0%
- **Phase 6 (Settings)**: Pending 0%
- **Phase 7 (DI & Cleanup)**: Pending 0%

**Overall Progress**: 0% complete

### Files to Migrate (162 total)
- [ ] Core utilities: 5 files
- [ ] Python clients: 3 files
- [ ] AI clients: 11 files
- [ ] Database entities: 18 files
- [ ] DAOs: 16 files
- [ ] Repositories: 13 files
- [ ] Screens: 14 files
- [ ] ViewModels: 13 files
- [ ] DI modules: 5 files
- [ ] Analysis: 3 files
- [ ] Workers: 7 files
- [ ] Services: 2 files
- [ ] Other: ~52 files

---

## Notes & Learnings

### Implementation Notes
- [Add insights discovered during implementation]

### Blockers Encountered
- [Document any blockers and resolutions]

### Improvements for Future Plans
- [What would you do differently next time?]

---

## Final Checklist

**Before marking plan as COMPLETE**:
- [ ] All 7 phases completed with quality gates passed
- [ ] `./gradlew clean assembleDebug` passes
- [ ] `./gradlew assembleRelease` passes
- [ ] All 14 screens tested manually
- [ ] All background workers verified
- [ ] Navigation works correctly
- [ ] No lint errors or warnings
- [ ] CLAUDE.md updated with new structure
- [ ] Git history clean with meaningful commits

---

**Plan Status**: Ready for User Approval
**Next Action**: Get user approval to proceed with Phase 1
**Blocked By**: User approval required
