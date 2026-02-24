# TASK.md — Stock Menu Feature Updates

## Phase 1: Analysis (iterations 1-3)
- [x] S-001 Diff supply-demand oscillator: current implementation vs reference. Document exact differences.
- [x] S-002 Diff trend signal: current implementation vs reference. Document exact differences.
- [x] S-003 Debug elder impulse bug: trace data flow, identify root cause, document in PROGRESS.md.
- [x] S-004 Analyze real-time intraday feature in reference: identify required APIs, data flow, UI updates.

## Phase 2: Algorithm Updates (iterations 4-6)
- [x] S-005 Update supply-demand oscillator algorithm to match reference. Architect approves diff before apply.
- [x] S-006 Update trend signal judgment method and algorithm to match reference. Architect approves.
- [x] S-007 Fix elder impulse bug based on root cause analysis.
- [x] S-008 Verify: all 3 fixes produce correct output. Build passes.

## Phase 3: Real-Time Feature (iterations 7-10)
- [x] S-009 Write real-time integration plan: which APIs needed, data flow, UI refresh strategy. Architect approves.
- [x] S-010 Implement real-time data source: add only the new API calls needed for intraday data.
- [x] S-011 Implement real-time UI updates: live data display in stock menu during market hours.
- [x] S-012 Verify: real-time data flows correctly, existing APIs unchanged, build passes.

## Phase 4: Verification and Report (iterations 11-12)
- [x] S-013 Full regression: confirm no other features are affected by changes.
- [x] S-014 Generate CHANGE_LOG.md (before/after for every change). Update CLAUDE.md.

## Constraints
- API: Keep current approach. Only add APIs specifically required for real-time.
- Architecture: MVVM + Clean Architecture + Feature modules.
- Scope: ONLY modify the 4 items listed. Do not touch anything else.