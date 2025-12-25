# Clean Architecture Cleanup Plan

**Feature**: 클린 아키텍처 정리 (Clean Architecture Cleanup)
**Scope**: Medium-Large (8 phases, 15-20 hours estimated)
**Created**: 2025-12-25
**Status**: In Progress (Phase 1-6 Complete, Phase 7.1-7.5 Complete, Phase 8 Pending)

---

## 1. Current State Analysis

### 1.1 Codebase Statistics

| Category | Count | Location |
|----------|-------|----------|
| Total Kotlin Files | 267 | app/src/main/java/com/etfmonitor/ |
| Core Module Files | 59 | core/ |
| Feature Module Files | 121 | feature/ |
| Legacy Repository Files | 13 | repository/ |
| Legacy UI Files | 33 | ui/screens/ |
| Database Files | 34 | database/ (entities + DAOs) |
| Oscillator (Legacy) | 3 | oscillator/ |
| DI Files (Split) | 5 | di/ + core/di/ |
| Root Level | 2 | MainActivity.kt, EtfMonitorApp.kt |
| Navigation | 1 | navigation/ |

### 1.2 Identified Issues

#### Issue 1: Legacy Repository Layer ✅ RESOLVED
**Location**: `repository/` folder has been completely deleted
**Resolution**: Phase 7.1-7.5 eliminated all legacy repositories:
- Phase 7.1: DataRepository
- Phase 7.2: StockRepository, StockAnalysisRepository
- Phase 7.3: FearGreedRepository, MarketDepositRepository, MarketOscillatorRepository, MarketIndexRepository
- Phase 7.4: AIAnalysisRepository, AIChatRepository, CorrelationAnalysisRepository, AdvancedAnalysisRepository, StatisticsAnalysisRepository, TimeSeriesAnalysisRepository (→ feature/analysis/data/internal/TimeSeriesAnalysisHelper)
- Phase 7.5: RepositoryModule.kt deleted, repository/ folder deleted

#### Issue 2: Legacy UI Layer Not Fully Migrated
**Location**: `ui/screens/` (33 files)
```
ui/screens/
├── hub/                    # 4 files - NOT migrated
│   ├── AnalysisHubScreen.kt
│   ├── EtfHubScreen.kt
│   ├── HubComponents.kt
│   └── StocksHubScreen.kt
├── aianalysis/            # 3 files - NOT migrated
│   ├── AnalysisComponents.kt
│   ├── NewAIAnalysisScreen.kt
│   └── NewAIAnalysisViewModel.kt
├── advanced/              # 9 files - NOT migrated
│   ├── AdvancedDashboardScreen.kt
│   ├── AdvancedDashboardViewModel.kt
│   ├── CommonComponents.kt
│   ├── EtfCorrelationTab.kt
│   ├── HistoryCharts.kt
│   ├── LiquidityTab.kt
│   ├── MarketCapFlowTab.kt
│   ├── PredictionAccuracyUI.kt
│   └── SectorFearGreedTab.kt
├── oscillator/            # 2 files - NOT migrated
│   ├── OscillatorScreen.kt
│   └── OscillatorViewModel.kt
├── settings/              # 10 files - NOT migrated
│   ├── SettingsScreen.kt
│   ├── SettingsViewModel.kt
│   └── components/ (8 files)
└── statistics/            # 6 files - NOT migrated
    ├── AggregatedStockTrendScreen.kt
    ├── AnalysisTab.kt
    ├── CashDepositTab.kt
    ├── RankingTab.kt
    ├── StatisticsViewModel.kt
    └── StockChangeTab.kt
```

#### Issue 3: Oscillator Package in Wrong Location
**Location**: `oscillator/` (3 files)
```
oscillator/
├── calculator/
│   ├── OscillatorCalculator.kt
│   └── TrendSignalCalculator.kt
└── model/
    └── StockData.kt
```
**Problem**: Should be in `core/` or `feature/stock/` for consistency.

#### Issue 4: Database/DI Structure Split
**Database**:
- `database/` - DAOs (16) + entities/ (18 files)
- `core/database/` - AppDatabase.kt, Converters.kt

**DI**:
- `di/` - RepositoryModule.kt only
- `core/di/` - DatabaseModule, WorkerModule, PythonModule, AIModule

**Problem**: Inconsistent organization causes confusion.

#### Issue 5: Feature Module Presentation Inconsistency
| Feature | Domain | Data | Presentation |
|---------|--------|------|--------------|
| home | ✅ | ✅ | ✅ Complete |
| etf | ✅ | ✅ | ✅ Complete |
| stock | ✅ | ✅ | ⚠️ Partial (StockTrendScreen only) |
| market | ✅ | ✅ | ✅ Complete |
| analysis | ✅ | ✅ | ⚠️ State classes only |
| settings | ✅ | ✅ | ❌ Still in ui/screens/ |

#### Issue 6: Duplicate/Similar Repository Names ✅ RESOLVED
| Legacy Repository | Feature Repository | Status |
|------------------|-------------------|--------|
| ~~DataRepository~~ | EtfRepositoryImpl | ✅ Eliminated (Phase 7.1) |
| ~~StockRepository~~ | StockRepositoryImpl | ✅ Eliminated (Phase 7.2) |
| ~~StockAnalysisRepository~~ | StockAnalysisRepositoryImpl | ✅ Eliminated (Phase 7.2) |
| ~~FearGreedRepository~~ | FearGreedRepositoryImpl | ✅ Eliminated (Phase 7.3) |
| ~~MarketOscillatorRepository~~ | MarketOscillatorRepositoryImpl | ✅ Eliminated (Phase 7.3) |
| ~~MarketDepositRepository~~ | MarketDepositRepositoryImpl | ✅ Eliminated (Phase 7.3) |
| ~~MarketIndexRepository~~ | MarketIndexRepositoryImpl | ✅ Eliminated (Phase 7.3) |
| ~~AIAnalysisRepository~~ | AIAnalysisRepositoryImpl | ✅ Eliminated (Phase 7.4) |
| ~~AIChatRepository~~ | ChatRepositoryImpl | ✅ Eliminated (Phase 7.4) |
| ~~CorrelationAnalysisRepository~~ | CorrelationAnalysisRepositoryImpl | ✅ Eliminated (Phase 7.4) |
| ~~AdvancedAnalysisRepository~~ | AdvancedAnalysisRepositoryImpl | ✅ Eliminated (Phase 7.4) |
| ~~StatisticsAnalysisRepository~~ | StatisticsAnalysisRepositoryImpl | ✅ Eliminated (Phase 7.4) |
| ~~TimeSeriesAnalysisRepository~~ | TimeSeriesAnalysisHelper (internal) | ✅ Eliminated (Phase 7.5) |

---

## 2. Cleanup Strategy

### 2.1 Architecture Principles

1. **Single Source of Truth**: One implementation per functionality
2. **Clean Separation**: Feature modules are self-contained
3. **Gradual Migration**: Maintain backward compatibility during transition
4. **No Duplicate Wrappers**: Remove wrapper repositories, migrate consumers directly

### 2.2 Target State

```
app/src/main/java/com/etfmonitor/
├── core/                              # Shared infrastructure
│   ├── common/util/                   # Utilities
│   ├── analysis/                      # Analysis utilities (Backtester, etc.)
│   ├── database/                      # AppDatabase, Converters, entities/, DAOs
│   ├── di/                            # All DI modules
│   ├── network/                       # Python, AI clients
│   ├── ui/                            # Shared UI (theme, components)
│   ├── worker/                        # All workers
│   └── service/                       # Foreground services
│
├── feature/                           # Feature modules
│   ├── home/                          # ✅ Complete
│   ├── etf/                           # ✅ Complete
│   ├── stock/                         # + presentation migrated
│   ├── market/                        # ✅ Complete
│   ├── analysis/                      # + presentation migrated
│   └── settings/                      # + presentation migrated
│
├── navigation/                        # App navigation
│
├── MainActivity.kt
└── EtfMonitorApp.kt
```

**To be deleted**:
- ~~`repository/` (13 files)~~ → ✅ Deleted (Phase 7.4-7.5)
- `ui/screens/` (33 files) → Moved to feature modules
- `oscillator/` (3 files) → Moved to core/analysis/
- ~~`di/RepositoryModule.kt`~~ → ✅ Deleted (Phase 7.5)

---

## 3. Phase Breakdown

### Phase 1: Database & DI Consolidation (2-3 hours)

**Goal**: Consolidate database files and DI modules to core/

**Tasks**:
- [ ] Move `database/entities/` → `core/database/entities/`
- [ ] Move all DAOs from `database/` → `core/database/`
- [ ] Move `di/RepositoryModule.kt` → `core/di/RepositoryModule.kt`
- [ ] Update all imports across the codebase
- [ ] Delete empty `database/` and `di/` folders
- [ ] Verify build succeeds

**Files to Move**: ~35 files
**Impact**: Foundation for all subsequent phases

**Quality Gate**:
- [ ] `./gradlew assembleDebug` succeeds
- [ ] All entity/DAO imports updated
- [ ] No orphan files in old locations

---

### Phase 2: Oscillator Package Migration (1-2 hours)

**Goal**: Move oscillator utilities to appropriate location

**Tasks**:
- [ ] Move `oscillator/calculator/OscillatorCalculator.kt` → `core/analysis/OscillatorCalculator.kt`
- [ ] Move `oscillator/calculator/TrendSignalCalculator.kt` → `core/analysis/TrendSignalCalculator.kt`
- [ ] Move `oscillator/model/StockData.kt` → `core/analysis/model/StockData.kt`
- [ ] Update all imports (OscillatorPyClient, ViewModels, etc.)
- [ ] Delete empty `oscillator/` folder
- [ ] Verify build succeeds

**Files to Move**: 3 files
**Dependencies to Update**: ~10 files

**Quality Gate**:
- [ ] `./gradlew assembleDebug` succeeds
- [ ] OscillatorViewModel still works correctly
- [ ] StockTrendScreen still works correctly

---

### Phase 3: Stock Feature Presentation Migration (2-3 hours)

**Goal**: Complete Stock feature module presentation layer

**Tasks**:
- [ ] Move `ui/screens/oscillator/` → `feature/stock/presentation/oscillator/`
  - OscillatorScreen.kt
  - OscillatorViewModel.kt
- [ ] Move `ui/screens/statistics/` → `feature/stock/presentation/statistics/`
  - AggregatedStockTrendScreen.kt
  - AnalysisTab.kt
  - CashDepositTab.kt
  - RankingTab.kt
  - StatisticsViewModel.kt
  - StockChangeTab.kt
- [ ] Move `ui/screens/hub/StocksHubScreen.kt` → `feature/stock/presentation/hub/`
- [ ] Update Navigation.kt imports
- [ ] Update all internal imports
- [ ] Delete migrated files from `ui/screens/`

**Files to Move**: 9 files
**Dependencies**: StockModule.kt update for new ViewModels

**Quality Gate**:
- [ ] `./gradlew assembleDebug` succeeds
- [ ] OscillatorScreen navigates correctly
- [ ] Statistics tabs display correctly
- [ ] StocksHubScreen works

---

### Phase 4: Analysis Feature Presentation Migration (2-3 hours)

**Goal**: Complete Analysis feature module presentation layer

**Tasks**:
- [ ] Move `ui/screens/aianalysis/` → `feature/analysis/presentation/aianalysis/`
  - AnalysisComponents.kt
  - NewAIAnalysisScreen.kt
  - NewAIAnalysisViewModel.kt
- [ ] Move `ui/screens/advanced/` → `feature/analysis/presentation/advanced/`
  - AdvancedDashboardScreen.kt
  - AdvancedDashboardViewModel.kt
  - CommonComponents.kt
  - EtfCorrelationTab.kt
  - HistoryCharts.kt
  - LiquidityTab.kt
  - MarketCapFlowTab.kt
  - PredictionAccuracyUI.kt
  - SectorFearGreedTab.kt
- [ ] Move `ui/screens/hub/AnalysisHubScreen.kt` → `feature/analysis/presentation/hub/`
- [ ] Update Navigation.kt imports
- [ ] Update AnalysisModule.kt for new ViewModels
- [ ] Delete migrated files from `ui/screens/`

**Files to Move**: 13 files

**Quality Gate**:
- [ ] `./gradlew assembleDebug` succeeds
- [ ] AI Analysis screen works
- [ ] Advanced Dashboard tabs work
- [ ] AnalysisHubScreen navigates correctly

---

### Phase 5: Settings Feature Presentation Migration (2 hours)

**Goal**: Complete Settings feature module presentation layer

**Tasks**:
- [ ] Move `ui/screens/settings/SettingsScreen.kt` → `feature/settings/presentation/`
- [ ] Move `ui/screens/settings/SettingsViewModel.kt` → `feature/settings/presentation/`
- [ ] Move `ui/screens/settings/components/` → `feature/settings/presentation/component/`
  - ChartColorCards.kt
  - ColorPickerComponents.kt
  - DataCards.kt
  - GeneralCards.kt
  - KeywordCards.kt
  - PeriodCards.kt
  - UpdateCards.kt
- [ ] Update Navigation.kt imports
- [ ] Update SettingsModule.kt
- [ ] Delete `ui/screens/settings/`

**Files to Move**: 10 files

**Quality Gate**:
- [ ] `./gradlew assembleDebug` succeeds
- [ ] Settings screen displays correctly
- [ ] All settings components work

---

### Phase 6: Hub Screens & UI Cleanup (1-2 hours)

**Goal**: Move remaining hub screens and clean ui/screens/

**Tasks**:
- [ ] Move `ui/screens/hub/EtfHubScreen.kt` → `feature/etf/presentation/hub/`
- [ ] Move `ui/screens/hub/HubComponents.kt` → `core/ui/component/`
- [ ] Update Navigation.kt imports
- [ ] Delete empty `ui/screens/` folder
- [ ] Verify no orphan files remain in `ui/`

**Files to Move**: 2 files

**Quality Gate**:
- [ ] `./gradlew assembleDebug` succeeds
- [ ] All hub screens work correctly
- [ ] `ui/` folder only contains expected files (none remaining)

---

### Phase 7: Legacy Repository Elimination (3-4 hours)

**Goal**: Migrate consumers from legacy repositories to feature repositories

**Approach**:
- Update consumers (Workers, Services, ViewModels) to use feature repositories
- Remove wrapper pattern - feature repositories directly implement logic
- Delete legacy repository files

**Tasks**:

**7.1 DataRepository Elimination** ✅ COMPLETE (2025-12-25):
- [x] Identify all DataRepository consumers
- [x] Migrate EtfUpdateWorker to EtfRepository
- [x] Migrate DataCollectionService to feature repositories
- [x] Migrate remaining ViewModels
- [x] Delete `repository/DataRepository.kt`

**7.2 Stock Repositories Elimination** ✅ COMPLETE (2025-12-25):
- [x] Migrate StockUpdateWorker to feature StockRepository
- [x] Migrate MainActivity to feature StockRepository
- [x] Migrate SettingsViewModel to feature StockRepository
- [x] Migrate OscillatorViewModel to feature repositories
- [x] Update StockAnalysisRepositoryImpl to use core StockData model
- [x] Delete `repository/StockRepository.kt`
- [x] Delete `repository/StockAnalysisRepository.kt`
- [x] Delete unused domain StockAnalysis model

**7.3 Market Repositories Elimination** ✅ COMPLETE (2025-12-25):
- [x] Migrate Market workers to feature repositories
- [x] Update FearGreedRepositoryImpl to directly implement logic
- [x] Update MarketDepositRepositoryImpl to directly implement logic
- [x] Update MarketOscillatorRepositoryImpl to directly implement logic
- [x] Update MarketIndexRepositoryImpl to directly implement logic
- [x] Delete `repository/FearGreedRepository.kt`
- [x] Delete `repository/MarketOscillatorRepository.kt`
- [x] Delete `repository/MarketDepositRepository.kt`
- [x] Delete `repository/MarketIndexRepository.kt`
- [x] Update RepositoryModule.kt to remove legacy market providers
- [x] Update all consumers (ViewModels, Workers, Services) to use feature interfaces

**7.4 Analysis Repositories Elimination** ✅ COMPLETE (2025-12-25):
- [x] Migrate analysis repositories to feature implementations
- [x] Delete `repository/AIAnalysisRepository.kt` (previously deleted)
- [x] Delete `repository/AIChatRepository.kt` (previously deleted)
- [x] Delete `repository/CorrelationAnalysisRepository.kt` (previously deleted)
- [x] Delete `repository/AdvancedAnalysisRepository.kt` (previously deleted)
- [x] Delete `repository/StatisticsAnalysisRepository.kt` (previously deleted)
- [x] Delete `repository/TimeSeriesAnalysisRepository.kt`
- [x] Move TimeSeriesAnalysisRepository to feature/analysis/data/internal/TimeSeriesAnalysisHelper

**7.5 RepositoryModule Cleanup** ✅ COMPLETE (2025-12-25):
- [x] All providers moved to feature modules
- [x] Delete `core/di/RepositoryModule.kt`
- [x] Delete `repository/` folder entirely
- [x] Remove dead code from EtfMapper.kt (reference to deleted ComparisonResult)

**Quality Gate**:
- [x] No references to `com.etfmonitor.repository` package
- [ ] `./gradlew assembleDebug` succeeds (network unavailable for testing)
- [ ] All workers execute correctly
- [ ] DataCollectionService works
- [ ] No runtime crashes

---

### Phase 8: Final Cleanup & Documentation (1-2 hours)

**Goal**: Final verification and documentation update

**Tasks**:
- [ ] Run full build verification: `./gradlew clean assembleDebug`
- [ ] Run lint: `./gradlew lint`
- [ ] Verify all screens work:
  - [ ] Home
  - [ ] ETF List/Detail/Hub
  - [ ] Stock Trend/Oscillator/Statistics/Hub
  - [ ] Market FearGreed/Oscillator/Deposit/Hub
  - [ ] Analysis Advanced/AI/Hub
  - [ ] Settings
- [ ] Update CLAUDE.md with new structure
- [ ] Delete this plan file or mark as complete
- [ ] Create final commit

**Documentation Updates**:
- [ ] Codebase structure section
- [ ] File references
- [ ] DI modules section
- [ ] Remove references to deleted folders

**Quality Gate**:
- [ ] All screens manually tested
- [ ] No lint errors
- [ ] CLAUDE.md accurately reflects new structure

---

## 4. Migration Dependency Graph

```
Phase 1 (Database/DI)
    │
    ├── Phase 2 (Oscillator) ─────────────────────┐
    │                                              │
    ├── Phase 3 (Stock Presentation) ─────────────┤
    │                                              │
    ├── Phase 4 (Analysis Presentation) ──────────┤
    │                                              │
    ├── Phase 5 (Settings Presentation) ──────────┤
    │                                              │
    └── Phase 6 (Hub & UI Cleanup) ───────────────┤
                                                   │
                                          Phase 7 (Repository Elimination)
                                                   │
                                          Phase 8 (Final Cleanup)
```

**Note**: Phases 2-6 can run in parallel after Phase 1 completes.
Phase 7 requires all presentation migrations to be complete.

---

## 5. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Import path conflicts | High | Medium | IDE refactoring tools, staged commits |
| DI binding failures | Medium | High | Test after each phase |
| Runtime crashes | Medium | High | Manual testing checklist |
| Worker failures | Medium | High | Test workers individually |
| Performance regression | Low | Medium | Compare before/after metrics |
| Merge conflicts | Low | Medium | Complete before other branches merge |

---

## 6. Rollback Strategy

Each phase should be committed separately for easy rollback:

```bash
# Rollback single phase
git revert <phase-commit-hash>

# Rollback to before cleanup started
git reset --hard <pre-cleanup-commit>
```

---

## 7. File Count Summary

| Phase | Files Moved | Files Deleted | Net Change |
|-------|-------------|---------------|------------|
| Phase 1 | ~35 | 0 | 0 |
| Phase 2 | 3 | 0 | 0 |
| Phase 3 | 9 | 0 | 0 |
| Phase 4 | 13 | 0 | 0 |
| Phase 5 | 10 | 0 | 0 |
| Phase 6 | 2 | 0 | 0 |
| Phase 7 | 0 | 14 | -14 |
| Phase 8 | 0 | 0 | 0 |
| **Total** | **72** | **14** | **-14** |

---

## 8. Post-Cleanup Structure

```
app/src/main/java/com/etfmonitor/
├── core/                              # 72 files
│   ├── analysis/                      # 6 files (added 3 from oscillator)
│   ├── common/util/                   # 6 files
│   ├── database/                      # 36 files (moved from database/)
│   │   ├── entities/
│   │   └── *.Dao.kt
│   ├── di/                            # 5 files (consolidated)
│   ├── network/ai/                    # 11 files
│   ├── network/python/                # 3 files
│   ├── service/                       # 2 files
│   ├── ui/component/                  # 12 files (added HubComponents)
│   ├── ui/theme/                      # 8 files
│   └── worker/                        # 9 files
│
├── feature/                           # 155 files (added ~35 from ui/screens)
│   ├── home/                          # Complete
│   ├── etf/                           # Complete + hub
│   ├── stock/                         # Complete + oscillator, statistics, hub
│   ├── market/                        # Complete
│   ├── analysis/                      # Complete + aianalysis, advanced, hub
│   └── settings/                      # Complete + presentation
│
├── navigation/                        # 1 file
├── MainActivity.kt
└── EtfMonitorApp.kt

Total: ~230 files (down from 267)
```

---

## 9. Approval

**이 정리 계획을 검토하고 승인해 주세요.**

### Questions:
1. Phase 순서와 의존성이 적절한가요?
2. 레거시 Repository 제거 시 점진적 vs 일괄 제거 중 어떤 방식을 선호하시나요?
3. 추가로 정리해야 할 영역이 있나요?
4. Phase 1부터 진행해도 될까요?

---

**Created**: 2025-12-25
**Author**: Claude Code Assistant
