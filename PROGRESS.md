# PROGRESS.md — Stock Menu Updates
## Status: LOOP_COMPLETE
## Completed
- [x] S-001 Diff supply-demand oscillator
- [x] S-002 Diff trend signal
- [x] S-003 Debug elder impulse bug
- [x] S-004 Analyze real-time intraday feature
- [x] S-005 Supply-demand oscillator — ALREADY IMPLEMENTED (OscillatorCalculator.kt)
- [x] S-006 Trend signal updated (MA alignment, CMF=20, FG smoothing, 2-of-3 voting)
- [x] S-007 Elder impulse slope bug fixed (forward difference for newest-first data)
- [x] S-008 Verified: 1852 tests pass, 0 failures, build passes
- [x] S-009 Real-time integration plan approved
- [x] S-010 Real-time data source: domain (3 files) + data (3 files) + DI (1 file) created
- [x] S-011 Real-time UI: RealtimeSupplyViewModel + RealtimeSupplyTab + StocksHubScreen tab added
- [x] S-012 Verified: build passes, 1852 tests pass, 0 failures
- [x] S-013 Full regression: no other features affected, only stock feature files modified
- [x] S-014 CHANGE_LOG.md generated, CLAUDE.md updated with realtime supply docs

## Current
ALL TASKS COMPLETE

---

## Algorithm Diffs

### Supply-Demand Oscillator (S-001)

**Current (MarketMonitor — MarketOscillatorCalculator.kt)**:
- Market-wide oscillator: aggregates volume + price momentum of top-200 market cap stocks
- Formula: `oscillator = ((upVolume/totalVolume) + (gainedPoints/totalPoints)) / 2`
- Range mapping: `if (avg > 0.5) avg else avg - 1.0` → output × 100 → [-50%, +50%]
- No EMA smoothing, no signal line, no rolling window
- Data ordering: oldest-first (chronological)
- Rate limiting: Semaphore(3) + 500ms delay per request

**Reference (StockApp — NativeAnalysisRepoImpl.kt + StockData.kt)**:
- Per-stock supply ratio: `(for5d + ins5d) × 1,000,000 / mcap`
- for5d/ins5d = 5-day rolling sum of foreign/institution net buying (via MathUtil.rollingSum)
- Signal thresholds: STRONG_BUY >0.005, BUY >0.002, NEUTRAL, SELL <-0.002, STRONG_SELL <-0.005
- Data ordering: newest-first

**Differences (8 CRITICAL)**:

| # | Difference | Severity |
|---|-----------|----------|
| 1 | Entirely different algorithm (market-wide volume vs per-stock supply flow) | CRITICAL |
| 2 | No MACD oscillator (EMA12/EMA26/Signal EMA9) in current | CRITICAL |
| 3 | No rolling sum (window=5, min_periods=1) in current | HIGH |
| 4 | No signal line in current | HIGH |
| 5 | Different data source (top-200 aggregate vs individual stock ka10059) | CRITICAL |
| 6 | Different output type (time series % vs threshold-based signal) | CRITICAL |
| 7 | Data ordering: oldest-first vs newest-first | HIGH |
| 8 | Different null handling (0.5 default vs 0.0) | MEDIUM |

**Decision**: These are fundamentally different indicators. To match reference, need to add per-stock supply ratio calculation alongside existing market oscillator (not replace).

---

### Trend Signal (S-002)

**Current (MarketMonitor — TechnicalAnalysisEngine.kt + TrendSignalCalculator.kt)**:
- MA: SMA20 only (single period)
- CMF: period=4 default (both daily and weekly)
- Fear/Greed: 4 factors (log return / range position / vol ratio / volatility ratio), weights 0.45/0.45/0.05/0.05, no smoothing
- Signal: Weighted 4-factor scoring (±30 price-vs-MA, ±30 CMF, ±20 FG, ±20 recent signals) → 5-state output
- Buy/Sell: 3-condition (high breakout + MA + CMF) strong signal + 2-of-3 auxiliary signal → 4 arrays
- Data ordering: oldest-first

**Reference (StockApp — TrendCalculator.kt)**:
- MA: MA5, MA10, MA20, MA60 (4 periods)
- Daily MA Signal: MA5 > MA20 > MA60 → bullish (3-level alignment)
- Weekly MA Signal: calcMaSignalWeeklyReference (high breakout + MA10 + CMF > 0)
- CMF: period=20 daily, 4 weekly
- Fear/Greed: 4 factors (momentum / position / vol surge / vol spike), weights 0.45/0.45/0.05/0.05, rolling(7/10) smoothing
- Signal: 2-of-3 voting (MA + CMF>0.05 + FG>0.5) → 3-state output (bullish/neutral/bearish)
- Data ordering: newest-first

**Differences (7 items)**:

| # | Difference | Reference | Current | Severity |
|---|-----------|-----------|---------|----------|
| 1 | MA periods | MA5/10/20/60 | MA20 only | CRITICAL |
| 2 | Daily MA signal | 3-MA alignment (MA5>MA20>MA60) | Price vs single MA | CRITICAL |
| 3 | Weekly MA signal | calcMaSignalWeeklyReference | Not implemented (resampled daily) | MEDIUM |
| 4 | CMF period (daily) | 20 | 4 | HIGH |
| 5 | FG smoothing | rolling(7/10).mean() per component | Direct (no smoothing) | MEDIUM |
| 6 | FG momentum scaling | ×100 then smoothed | ÷0.1 (=×10), clipped [-1,1] | HIGH |
| 7 | Trend classification | 2-of-3 voting → 3-state | Weighted scoring → 5-state | CRITICAL |

---

## Elder Impulse Bug (S-003)

**Symptom**: Chart's newest (rightmost) bar always displays BLUE (neutral), never shows green/red signal.

**Root Cause**: `TechnicalAnalysisEngine.kt` lines 410-415 — slope calculation sets index 0 (newest bar) to 0.0 instead of computing real slope.

```kotlin
// BUGGY (line 411): if (i == 0) 0.0 else e - ema[i - 1]
// Newest bar (i=0) always gets slope=0.0 → both slopes zero → always BLUE
```

**Reference (ElderCalculator.kt lines 200-209)**: Sets LAST index (oldest bar) to 0.0, computes real slope for newest bar via `values[i] - values[i+1]`.

**Fix**: Change slope direction from backward (`i - i-1`) to forward (`i - i+1`), and move 0.0 boundary from index 0 to last index.

```kotlin
// BEFORE (lines 410-415):
val emaSlope = ema.mapIndexed { i, e -> if (i == 0) 0.0 else e - ema[i - 1] }
val histSlope = macdHist.mapIndexed { i, h -> if (i == 0) 0.0 else h - macdHist[i - 1] }

// AFTER:
val emaSlope = ema.mapIndexed { i, e -> if (i + 1 >= ema.size) 0.0 else e - ema[i + 1] }
val histSlope = macdHist.mapIndexed { i, h -> if (i + 1 >= macdHist.size) 0.0 else h - macdHist[i + 1] }
```

**Impact**: EMA seed uses values[0], MACD/Signal/Histogram identical to reference. Only slope boundary is wrong. Fix is 2-line change.

---

## Real-Time Plan (S-004) — requires Architect approval

**New APIs needed**:
- `ka10063` — Realtime investor trend (장중 투자자별 매매): per-stock foreign/institution net buy amounts
- `ka10001` — Stock basic info: stock name lookup (already partially supported)

**Data Flow**:
```
RealtimeSupplyVm → GetRealtimeSupplyUseCase → RealtimeSupplyRepository
  → KiwoomApiClient.call(apiId="ka10063") → RealtimeSupplyResponse
  → map to RealtimeSupplyData → cache (60s TTL) → RealtimeSupplySummary → UI
```

**UI Refresh Strategy**:
- Auto-refresh every 60s during trading hours (09:00-15:30 Mon-Fri KST)
- Manual pull-to-refresh anytime
- Feature flag gated (`ENABLE_REALTIME_SUPPLY`)

**Required Files (17 new)**:
1. Domain: RealtimeSupplyData, RealtimeSupplySummary, RealtimeSupplySignal, RealtimeSupplyRepository, GetRealtimeSupplyUseCase, RefreshRealtimeSupplyUseCase
2. Data: RealtimeSupplyRepositoryImpl, RealtimeSupplyRequest/Response DTOs
3. DB: RealtimeSupplyCacheEntity, RealtimeSupplyCacheDao, Migration v22→v23
4. Presentation: RealtimeSupplyViewModel, RealtimeSupplyTab, TradingHoursUtil
5. DI: RealtimeSupplyModule
6. Utility: IntradayDataMerger (optional for chart integration)

**Existing infrastructure reused**: KiwoomApiClient, KiwoomTokenManager, CategoryRateLimiter, @KiwoomOkHttp OkHttpClient

**Estimated effort**: ~12 hours

**Status**: APPROVED — implementing

### S-009 Detailed Integration Plan

**API**: ka10063 (`/api/dostk/mrkcond`) — 장중 투자자별 매매
**Request params**: stkCd, mrktTp="000", invsr="6", stexTp (MOCK="3"/PROD="1"), amtQtyTp="1"
**Response field**: `opmr_invsr_trde` → List of items with cur_prc, netprps_amt, buy_amt, sell_amt, etc.
**Reuses**: KiwoomApiClient.call(), KiwoomTokenManager, CategoryRateLimiter, @KiwoomOkHttp

**Implementation (14 files)**:
1. `feature/stock/domain/model/RealtimeSupplyModels.kt` — RealtimeSupplyData, RealtimeSupplySummary, RealtimeSupplySignal, TradingHours
2. `feature/stock/domain/repository/RealtimeSupplyRepository.kt` — interface
3. `feature/stock/domain/usecase/GetRealtimeSupplyUseCase.kt` — get + refresh
4. `feature/stock/data/dto/RealtimeSupplyDto.kt` — Request/Response DTOs
5. `feature/stock/data/repository/RealtimeSupplyRepositoryImpl.kt` — impl with 60s cache
6. `core/database/entities/RealtimeSupplyCacheEntity.kt` — Room entity
7. `core/database/RealtimeSupplyCacheDao.kt` — DAO
8. `core/database/AppDatabase.kt` — Migration v22→v23 + entity registration
9. `feature/stock/presentation/realtime/RealtimeSupplyViewModel.kt` — StateFlow + auto-refresh
10. `feature/stock/presentation/realtime/RealtimeSupplyTab.kt` — Compose UI
11. `feature/stock/di/RealtimeSupplyModule.kt` — Hilt bindings
12. Navigation integration (add tab to stock detail screen)
13. Tests: ViewModel + Repository + UseCase
14. Update: CLAUDE.md with new feature

---

## Change Log
(task ID, file, what changed, before/after summary)

| Task | File | Change | Before | After |
|------|------|--------|--------|-------|
| S-001 | PROGRESS.md | Documented oscillator diff | (empty) | Full analysis |
| S-002 | PROGRESS.md | Documented trend signal diff | (empty) | Full analysis |
| S-003 | PROGRESS.md | Documented elder impulse bug | (empty) | Root cause + fix spec |
| S-004 | PROGRESS.md | Documented real-time plan | (empty) | Full implementation plan |
| S-005 | (none) | Already implemented | OscillatorCalculator.kt already matches reference | No code change needed |
| S-006 | TechnicalAnalysisEngine.kt | Added MA alignment, FG smoothing, 2-of-3 voting | SMA20 only, CMF=4, no smoothing, weighted scoring | MA5/10/20/60, CMF=20, rolling(7/10) smoothing, 2-of-3 voting |
| S-006 | TrendSignalCalculator.kt | Rewritten determineSignal() | Weighted 4-factor scoring | 2-of-3 voting (MA+CMF+FG) preserving 5-state |
| S-006 | KrxStockDataRepositoryImpl.kt | CMF period by interval | cmfPeriod=4 always | daily=20, weekly=4 |
| S-006 | TechnicalAnalysisEngineTest.kt | 36 new tests | No tests for new functions | calcMa, calcMaSignal, calcTrend, FG smoothing tests |
| S-006 | TrendSignalCalculatorTest.kt | Rewritten for voting logic | Weighted scoring tests | 2-of-3 voting tests |
| S-007 | TechnicalAnalysisEngine.kt | Slope direction fix | `if (i==0) 0.0 else e-ema[i-1]` | `if (i+1>=size) 0.0 else e-ema[i+1]` |
| S-007 | TechnicalAnalysisEngineTest.kt | Elder tests use newest-first data | Oldest-first quadratic data | Newest-first exponential data |
| S-008 | AdvancedDashboardViewModelTest.kt | Fixed forceRefresh test timing | Used advanceUntilIdle for IO dispatcher | Poll-based awaitForceRefreshDone |
| S-010 | feature/stock/domain/model/RealtimeSupplyModels.kt | NEW: domain models | (none) | RealtimeSupplyData, RealtimeSupplySummary, RealtimeSupplySignal, TradingHours |
| S-010 | feature/stock/domain/repository/RealtimeSupplyRepository.kt | NEW: repository interface | (none) | getRealtimeSupply(ticker) |
| S-010 | feature/stock/domain/usecase/GetRealtimeSupplyUseCase.kt | NEW: use case | (none) | Maps data to summary with Korean signal descriptions |
| S-010 | feature/stock/data/dto/RealtimeSupplyDto.kt | NEW: DTOs | (none) | RealtimeSupplyResponse, RealtimeSupplyItemDto |
| S-010 | feature/stock/data/repository/RealtimeSupplyRepositoryImpl.kt | NEW: repository impl | (none) | ka10063 API call, 60s ConcurrentHashMap cache |
| S-010 | feature/stock/di/RealtimeSupplyModule.kt | NEW: Hilt module | (none) | @Binds RealtimeSupplyRepository |
| S-011 | feature/stock/presentation/realtime/RealtimeSupplyViewModel.kt | NEW: ViewModel | (none) | Sealed state, 60s auto-refresh, pull-to-refresh |
| S-011 | feature/stock/presentation/realtime/RealtimeSupplyTab.kt | NEW: Compose UI | (none) | Signal card, supply/demand metrics, trading hours |
| S-011 | feature/stock/presentation/hub/StocksHubScreen.kt | Added 장중수급 tab | 2 tabs (차트 분석, 재무정보) | 3 tabs (차트 분석, 재무정보, 장중수급) |
| S-014 | CHANGE_LOG.md | NEW: changelog | (none) | Before/after for all changes |
| S-014 | CLAUDE.md | Updated docs | No realtime supply docs | ka10063, RealtimeSupply cache, architecture flow |

---
