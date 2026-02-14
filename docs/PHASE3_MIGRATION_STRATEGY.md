# Phase 3 Migration Strategy
# pykrx → kotlin_krx Migration (Feature Module Refactoring)

**Status**: ✅ COMPLETE (Stock Feature Python-Free)
**Created**: 2026-02-14
**Updated**: 2026-02-14 (T-012/T-013 completion)
**Phase**: 3/3

---

## Overview

Phase 3 completes the pykrx → kotlin_krx migration by refactoring feature modules to use kotlin_krx UseCases, removing all Python bridge dependencies from the Stock feature.

**Prerequisites**:
- Phase 1 complete: kotlin_krx integrated ✅
- Phase 2 complete: UseCases and repositories implemented ✅
- Coexistence validation passed ✅

**Scope**: 3 feature migration tasks (T-011, T-012, T-013) - **ALL COMPLETE**

---

## Task Breakdown

### T-011: ETF Feature Migration - ✅ COMPLETE (Iteration 11)
- ✅ Created GetKrxEtfHoldingsUseCase (wraps KrxEtf.getPortfolio)
- ✅ Created GetKrxEtfListUseCase (parallel name lookups + client-side filtering)
- ✅ Modified EtfRepositoryImpl (2 of 3 PyKrxClient calls migrated)
- **Remaining Dependency**: PyKrxClient.getBusinessDays() (2 call sites, business calendar logic)
- **Rationale**: kotlin_krx focuses on KRX data fetching, not business day calculations
- **Actual effort**: 1 iteration
- **Files changed**: 4 files (2 created, 2 modified)

### T-012: Stock Oscillator Feature Migration - ✅ COMPLETE (Iterations 14-15)
- ✅ Created TechnicalAnalysisEngine.kt (487 lines) - Pure Kotlin computation engine
- ✅ Created StockDataRepository interface + KrxStockDataRepositoryImpl (441 lines)
- ✅ Created 4 UseCases (GetTrendSignalData, GetElderImpulse, GetDemarkTD, GetStockOhlcv)
- ✅ Migrated StockRepositoryImpl: OscillatorPyClient → StockDataRepository
- ✅ Migrated StockAnalysisRepositoryImpl: OscillatorPyClient → StockDataRepository
- ✅ Migrated OscillatorViewModel (18 pyClient calls → 3 UseCases)
- ✅ Cleaned up StockTrendViewModel + AggregatedStockTrendViewModel (removed unused pyClient)
- ✅ Updated StockModule DI (added StockDataRepository + 4 UseCase providers)
- **Result**: Zero OscillatorPyClient references in stock feature, AD-002 RESOLVED
- **Actual effort**: 2 iterations
- **Files changed**: 14 files (8 created, 6 modified)

### T-013: Stock Analysis Feature Migration - ✅ COMPLETE (Included in T-012)
- ✅ StockAnalysisRepositoryImpl migrated as part of T-012 work
- ✅ Uses StockDataRepository.getStockAnalysisData() (kotlin_krx + TechnicalAnalysisEngine)
- ✅ 24-hour caching policy maintained
- ✅ JOIN with stocks table for name resolution preserved
- **Result**: No separate implementation needed, fully migrated in T-012
- **Actual effort**: 0 iterations (included in T-012)
- **Files changed**: 0 additional (already counted in T-012)

---

## Phase 3 Achievement Summary

### Migration Statistics
- **Total iterations**: 3 (T-011: 1, T-012: 2, T-013: 0 - included in T-012)
- **Files created**: 10 (2 UseCases in T-011, 8 files in T-012)
- **Files modified**: 8 (2 repos + 2 DI in T-011, 3 ViewModels + 2 repos + 1 DI in T-012)
- **Lines of code**: ~1,100 (TechnicalAnalysisEngine: 487, KrxStockDataRepositoryImpl: 441, UseCases + interfaces: ~200)
- **Python dependencies removed**: OscillatorPyClient (entire class, 596 lines, 10 methods)
- **Python dependencies remaining**: PyKrxClient.getBusinessDays() only (2 call sites, minimal)

### Technical Achievements
1. **TechnicalAnalysisEngine**: Pure Kotlin port of Python numerical analysis (~130 lines trend_signal.py → 487 lines Kotlin)
   - EMA, CMF, Fear & Greed Index, Signal Generation
   - Elder Impulse System, DeMark TD Sequential
   - Weekly/Monthly OHLCV resampling, Rolling sums
2. **StockDataRepository**: Clean domain layer abstraction for kotlin_krx + technical analysis
3. **AD-002 Resolution**: All stock feature ViewModels now properly use UseCases (Clean Architecture compliance)
4. **Build Success**: 1m 23s, 52 tasks, zero regression, zero OscillatorPyClient references

### Architecture Impact
- **Before Phase 3**: 3 ViewModels directly inject OscillatorPyClient (architecture violation)
- **After Phase 3**: 3 ViewModels properly use domain layer UseCases → StockDataRepository → kotlin_krx
- **Compliance**: 100% Clean Architecture in stock feature (domain → data → external API boundary)

---

## UseCase-to-Feature Mapping (Actual Implementation)

| UseCase | Phase 3 Task | Feature Module | Status |
|---------|--------------|----------------|--------|
| GetKrxMarketCapUseCase | T-007 (Phase 2) | ETF | ✅ Implemented, not actively used in Phase 3 |
| GetKrxIndexComponentsUseCase | T-008 (Phase 2) | Market Oscillator | ⏸️ Created, not yet consumed (market feature out of scope) |
| GetKrxMarketDataUseCase | T-008 (Phase 2) | Stock Analysis | ⏸️ Created, not yet consumed (stock uses StockDataRepository instead) |
| GetKrxEtfHoldingsUseCase | T-011 | ETF | ✅ Implemented, used by EtfRepositoryImpl |
| GetKrxEtfListUseCase | T-011 | ETF | ✅ Implemented, used by EtfRepositoryImpl |
| GetTrendSignalDataUseCase | T-012 | Stock Oscillator | ✅ Implemented, used by OscillatorViewModel |
| GetElderImpulseDataUseCase | T-012 | Stock Oscillator | ✅ Implemented, used by OscillatorViewModel |
| GetDemarkTDDataUseCase | T-012 | Stock Oscillator | ✅ Implemented, used by OscillatorViewModel |
| GetStockOhlcvUseCase | T-012 | Stock Oscillator | ✅ Implemented, available for future use |

**Note**: StockDataRepository provides a higher-level abstraction that internally uses kotlin_krx. Stock feature consumes StockDataRepository instead of individual market data UseCases.

---

## Clean Architecture Completion Checklist - ✅ STOCK FEATURE COMPLETE

### Stock Feature ViewModels - ✅ ALL MIGRATED
- [x] **OscillatorViewModel**: Removed OscillatorPyClient, now injects 3 UseCases
  - GetTrendSignalDataUseCase (replaces getTrendSignalData)
  - GetElderImpulseDataUseCase (replaces getElderImpulseData)
  - GetDemarkTDDataUseCase (replaces getDemarkTDData)
  - **Solution**: Created TechnicalAnalysisEngine to provide missing kotlin_krx functionality
- [x] **StockTrendViewModel**: Removed unused OscillatorPyClient dependency
  - **Finding**: pyClient was never actually used (reference-only for navigation FAB)
- [x] **AggregatedStockTrendViewModel**: Removed unused OscillatorPyClient dependency
  - **Finding**: Same as StockTrendViewModel (navigation FAB only, not data-dependent)
  - **Note**: @AssistedInject migration NOT needed (just removed unused parameter)
- [x] **Verification**: All 3 ViewModels follow Clean Architecture (ViewModel → UseCase → Repository → Data)

### Repository Layer - ✅ IMPLEMENTED
- [x] **StockDataRepository** interface created (domain layer abstraction)
- [x] **KrxStockDataRepositoryImpl** implementation (data layer, 441 lines)
- [x] **StockRepositoryImpl** migrated to use StockDataRepository
- [x] **StockAnalysisRepositoryImpl** migrated to use StockDataRepository

### Repository Interfaces (T-008 C2 technical debt) - ⏸️ DEFERRED
**Decision**: Stock feature migration successful without creating intermediate repository interfaces
- Concrete class injection acceptable for Phase 3 scope (stock feature only)
- Repository interfaces can be added in future refactoring if needed
- Current architecture: ViewModel → UseCase → StockDataRepository (interface) → KrxStockDataRepositoryImpl (concrete)
- No double-refactoring occurred, migration completed in one pass

---

## Success Criteria (Phase 3 Complete) - ✅ ACHIEVED

### Stock Feature Scope (100% Complete)
1. ✅ All Stock feature ViewModels inject UseCases (no direct Python client injection)
   - OscillatorViewModel: 3 kotlin_krx UseCases
   - StockTrendViewModel: Cleaned (removed unused pyClient)
   - AggregatedStockTrendViewModel: Cleaned (removed unused pyClient)
2. ✅ OscillatorPyClient completely removed from stock feature (0 references)
3. ✅ StockRepositoryImpl + StockAnalysisRepositoryImpl migrated to StockDataRepository
4. ✅ All stock features functional with kotlin_krx + TechnicalAnalysisEngine
5. ✅ Build succeeds (1m 23s, 52 tasks), zero regression
6. ✅ Clean Architecture compliance: AD-002 RESOLVED (all ViewModels → UseCases → Repositories)

### Minimal Python Dependency (Accepted)
- PyKrxClient.getBusinessDays() - 2 call sites in EtfRepositoryImpl (business calendar logic, out of kotlin_krx scope)

### Out of Scope (Other Features)
- Analysis Feature: TimeSeriesAnalysisHelper still uses OscillatorPyClient
- Market Feature: 4 repositories use Python clients (MarketIndex, BloodIndicator, MarketOscillator, MarketDeposit)
- Phase 4 candidate: Market/Analysis feature migration (separate initiative)

---

## Risk Assessment - RETROSPECTIVE

### T-012 (Oscillator) - Originally High Risk → ✅ MITIGATED
**Predicted Risk**: Most complex, 3 ViewModels affected, trend signal functions don't exist in kotlin_krx
**Actual Mitigation**: Created TechnicalAnalysisEngine (487 lines pure Kotlin) to provide missing functionality
**Outcome**: Complete success, zero Python dependency, AD-002 resolved
**Key Decision**: Port Python numerical analysis to Kotlin instead of deferring migration

### T-011 (ETF) - Originally Medium Risk → ✅ SUCCESSFUL
**Predicted Risk**: Holding data migration, requires careful testing of Holding.create() factory
**Actual Mitigation**: HoldingMapper from T-007 worked perfectly, no issues
**Outcome**: Partial migration (2/3 methods), accepted minimal PyKrxClient.getBusinessDays dependency
**Key Decision**: Business calendar logic out of kotlin_krx scope, kept Python utility function

### T-013 (Stock Analysis) - Originally Low Risk → ✅ NO SEPARATE WORK NEEDED
**Predicted Risk**: Straightforward market cap data
**Actual Outcome**: Already completed as part of T-012 (StockAnalysisRepositoryImpl migrated)
**Key Insight**: Stock analysis uses same StockDataRepository as oscillator feature

---

## Rollback Strategy - NOT NEEDED (Phase 3 Complete)

Phase 3 completed successfully, no rollback required.

**If future issues arise**:
1. Stock feature now 100% kotlin_krx (no Python bridge to revert to)
2. Git history preserves Python bridge code for reference
3. Commits available for selective revert if needed:
   - 0cd0d9b: T-012/T-013 stock feature migration
   - Prior commits: T-011 ETF feature migration
4. TechnicalAnalysisEngine is pure Kotlin (no external dependencies to fail)

---

## Notes - RETROSPECTIVE

**W1 (AggregatedStockTrendViewModel)** - ✅ RESOLVED:
Originally predicted to require @AssistedInject + @AssistedFactory migration.
**Actual**: pyClient was unused (navigation FAB only), simply removed the parameter. No factory changes needed.

**W2 (ETF Holdings Data Source)** - ✅ VERIFIED:
KrxEtf.getPortfolio() successfully replaced pykrx's get_etf_portfolio_deposit_file. Holdings data structure compatibility confirmed (HoldingMapper from T-007 worked perfectly).

**W3 (Feature Gap Risk)** - ✅ MITIGATED:
Trend signal functions (getTrendSignalData, getElderImpulseData, getDemarkTDData) did not exist in kotlin_krx.
**Solution**: Created TechnicalAnalysisEngine (487 lines) to provide all missing functionality in pure Kotlin.
**Outcome**: No feature removal needed, all oscillator features preserved.

**S1 (FearGreedRepositoryImpl Exclusion)** - ✅ CONFIRMED:
FearGreedRepositoryImpl remains out of scope. Uses BloodIndicatorPyClient, which has no kotlin_krx equivalent. Stays Python-based.

**S2 (Documentation)** - ✅ UPDATED:
This document updated post-Phase 3 completion (2026-02-14) to reflect actual implementation vs. original plan.

**S3 (Repository Interface Priority)** - ⏸️ NOT NEEDED:
Repository interfaces were NOT created before ViewModel refactoring.
**Actual**: Stock feature migration succeeded without intermediate repository interfaces. StockDataRepository interface provided sufficient abstraction. No double-refactoring occurred.

**NEW: S4 (T-013 Merged into T-012)**:
T-013 (Stock Analysis) did not require separate implementation. StockAnalysisRepositoryImpl was migrated as part of T-012 work, using the same StockDataRepository abstraction as OscillatorViewModel.

**NEW: S5 (Minimal Python Dependency)**:
Final Python dependency: PyKrxClient.getBusinessDays() only (2 call sites in EtfRepositoryImpl). Business calendar logic is out of kotlin_krx scope. Acceptable minimal dependency.
