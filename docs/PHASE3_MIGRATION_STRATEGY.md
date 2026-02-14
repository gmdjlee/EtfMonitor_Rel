# Phase 3 Migration Strategy
# pykrx → kotlin_krx Migration (Feature Module Refactoring)

**Status**: BLOCKED (Pending Phase 2 completion)
**Created**: 2026-02-14
**Phase**: 3/3

---

## Overview

Phase 3 completes the pykrx → kotlin_krx migration by refactoring feature modules to use kotlin_krx UseCases, removing all Python bridge dependencies.

**Prerequisites**:
- Phase 1 complete: kotlin_krx integrated ✅
- Phase 2 complete: UseCases and repositories implemented ✅
- Coexistence validation passed ✅

**Scope**: 3 feature migration tasks (T-011, T-012, T-013)

---

## Task Breakdown

### T-011: ETF Feature Migration
- Redesign ETF feature to use GetKrxMarketCapUseCase
- Create GetKrxEtfHoldingsUseCase (wraps KrxEtfRepositoryImpl.getEtfHoldings)
  - **W2**: Wraps KrxEtf.getEtfComponents() (maps from pykrx get_etf_portfolio_deposit_file)
- Remove PyKrxClient dependency from EtfRepositoryImpl
- Estimated effort: 1 iteration

### T-012: Supply-Demand Oscillator Feature Migration - ✅ DEFERRED (Iteration 14)
- **Status**: DEFERRED - OscillatorPyClient accepted as permanent Python dependency
- **Rationale**: kotlin_krx lacks OHLCV/market cap time series APIs + numerical analysis functions
- **Budget Constraint**: 4 iterations for 9 tasks, T-012 full migration requires 2+ iterations (infeasible)
- **API Gaps** (would require 3-4 iteration standalone migration):
  - Data source gaps: get_market_ohlcv(), get_market_cap() time series, stock search
  - Computation gaps: Trend signal, Elder Impulse, DeMark TD algorithms (~300 lines Python → Kotlin)
- **Python Dependency Retained**: OscillatorPyClient (596 lines, 10 methods, 4 modules, 7 consumers)
- **ViewModel Status**: No migration (OscillatorViewModel heavy dependency, StockTrendViewModel/AggregatedStockTrendViewModel light navigation-only)
- **Future Path**: Requires kotlin_krx enhancements OR custom Kotlin numerical analysis library
- **Actual effort**: 0.25 iterations (documentation only)

### T-013: Stock Analysis Feature Migration
- Redesign stock analysis to use GetKrxMarketDataUseCase
- Replace Python-based trend signals with kotlin_krx data
- Create additional UseCases as needed
- Estimated effort: 1 iteration

---

## UseCase-to-Feature Mapping

| UseCase | Phase 3 Task | Feature Module |
|---------|--------------|----------------|
| GetKrxMarketCapUseCase | T-011, T-013 | ETF, Stock Analysis |
| GetKrxIndexComponentsUseCase | T-012 | Oscillator |
| GetKrxMarketDataUseCase | T-013 | Stock Analysis |
| GetKrxEtfHoldingsUseCase (new) | T-011 | ETF |

---

## Clean Architecture Completion Checklist

**S3 NOTE**: Repository interfaces should be created BEFORE ViewModel refactoring to avoid double-refactoring.

### Repository Interfaces (deferred from T-008 C2 technical debt - PRIORITY 1)
- [ ] Create KrxStockRepository interface
- [ ] Create KrxMarketRepository interface
- [ ] Create KrxEtfRepository interface
- [ ] Refactor UseCases to inject interfaces instead of *Impl classes
- [ ] Add @Binds methods to KrxModule for interface → implementation mapping

### ViewModel Refactoring (TBD pending feature gap analysis - W3)
- [ ] StockTrendViewModel: Remove OscillatorPyClient, inject GetKrxMarketCapUseCase
  - **NOTE**: Currently uses getTrendSignalData/getElderImpulseData/getDemarkTDData - no kotlin_krx equivalents. May require feature removal or custom Kotlin analysis implementation
- [ ] OscillatorViewModel: Remove OscillatorPyClient, inject GetKrxIndexComponentsUseCase
- [ ] AggregatedStockTrendViewModel: Remove OscillatorPyClient, inject GetKrxMarketDataUseCase
  - **MIGRATION COMPLEXITY**: @AssistedInject migration required
- [ ] Verify all 3 ViewModels follow Clean Architecture (UseCase → Repository → Data)

---

## Success Criteria (Phase 3 Complete)

1. All ViewModels inject UseCases (no direct Python client injection)
2. All Python bridge clients removed (PyKrxClient, OscillatorPyClient, etc.)
3. Python dependencies removed from build.gradle (Chaquopy, pykrx)
4. All features functional with kotlin_krx only
5. Build succeeds, tests pass, no performance regression
6. Clean Architecture compliance: 100% (no technical debt)

---

## Risk Assessment

### High Risk
**T-012 (Oscillator)**: Most complex, 3 ViewModels affected, trend signal functions don't exist in kotlin_krx
- **Mitigation**: May need to remove unsupported features or implement custom trend analysis

### Medium Risk
**T-011 (ETF)**: Holding data migration, requires careful testing of Holding.create() factory
- **Mitigation**: HoldingMapper already tested in T-007

### Low Risk
**T-013 (Stock Analysis)**: Straightforward market cap data, GetKrxMarketDataUseCase already implemented
- **Mitigation**: None needed

---

## Rollback Strategy

If Phase 3 migration fails:
1. Revert feature module changes
2. Keep Python bridge code functional
3. UseCases remain available for future attempts
4. Coexistence mode continues (Python + kotlin_krx dual paths)

---

## Notes

**W1 (AggregatedStockTrendViewModel)**:
Uses @AssistedInject + @AssistedFactory pattern. Factory interface must be updated during migration (cannot simply swap constructor parameters).

**W2 (ETF Holdings Data Source)**:
KrxEtf.getEtfComponents() maps from pykrx's get_etf_portfolio_deposit_file. Holdings data structure compatibility already verified in T-007.

**W3 (Feature Gap Risk)**:
Trend signal functions (getTrendSignalData, getElderImpulseData, getDemarkTDData) do not exist in kotlin_krx. Phase 3 may require feature removal or custom Kotlin analysis implementation.

**S1 (FearGreedRepositoryImpl Exclusion)**:
FearGreedRepositoryImpl is NOT in scope for migration. It uses BloodIndicatorPyClient, which has no kotlin_krx equivalent. Remains Python-based permanently.

**S2 (Documentation)**:
This document created during T-009 validation. Should be updated as Phase 3 tasks progress.

**S3 (Repository Interface Priority)**:
Repository interfaces should be created BEFORE ViewModel refactoring to avoid double-refactoring (first to UseCases with *Impl, then to UseCases with interfaces).
