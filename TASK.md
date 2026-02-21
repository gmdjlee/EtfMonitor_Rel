# TASK.md — Ranking Feature Migration

## Phase 1: Source Analysis (iterations 1-3)
- [ ] R-001 Read RANKING_FEATURE_SPEC.md and RANKING_FEATURE_UI_SPEC.md completely
- [ ] R-002 Scan StockApp source: identify ranking data models, API calls, ViewModels, UI components
- [ ] R-003 Create IMPLEMENTATION_PLAN.md: StockApp component to current project module mapping. Architect approves.

## Phase 2: Data Layer (iterations 4-6)
- [ ] R-004 Create data models: Entity, DTOs, response mappers for ranking data
- [ ] R-005 Create API service interface for ranking endpoints
- [ ] R-006 Create Repository implementation: fetch ranking data, handle pagination if applicable

## Phase 3: Domain Layer (iterations 7-8)
- [ ] R-007 Create domain Entity, Repository interface
- [ ] R-008 Create UseCases for each ranking type (per spec document)

## Phase 4: Presentation Layer (iterations 9-11)
- [ ] R-009 Create RankingViewModel with StateFlow (loading, data, error, filter states)
- [ ] R-010 Create ranking UI: list, tabs, filters per RANKING_FEATURE_UI_SPEC.md
- [ ] R-011 Wire navigation: add ranking screen to app navigation graph

## Phase 5: Integration and Verification (iterations 12-15)
- [ ] R-012 Create Hilt DI module for ranking feature dependencies
- [ ] R-013 Test each ranking type: data loads, displays correctly, matches StockApp behavior
- [ ] R-014 Build verification: assembleDebug passes, all tests pass
- [ ] R-015 Generate IMPLEMENTATION_REPORT.md, update CLAUDE.md