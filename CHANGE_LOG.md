# CHANGE_LOG.md — Feature Updates

## Summary

Updated 5 items:
1. **Market oscillator pykrx algorithm port (v3)** — NEW (2026-02-24)
2. Supply-demand oscillator algorithm sync
3. Trend signal algorithm sync
4. Elder Impulse slope bug fix
5. Real-time intraday supply feature migration

**Build**: `assembleDebug` SUCCESSFUL
**Tests**: 1861 tests, 0 failures (oscillator-related)
**Scope**: Market oscillator (feature/market/) — individual stock oscillator (feature/stock/) NOT affected

---

## M-001: Market Oscillator pykrx Algorithm Port (v3) — 2026-02-24

**Goal**: Port Python pykrx `_calc()` algorithm to Kotlin, replacing the v2 advance/decline + EMA approach.

**Files Modified (4 source + 2 test)**:
- `core/analysis/MarketOscillatorCalculator.kt` — Algorithm fully rewritten
- `feature/market/domain/model/MarketModels.kt` — Status thresholds updated
- `feature/market/presentation/oscillator/MarketOscillatorViewModel.kt` — Default thresholds changed
- `feature/market/presentation/oscillator/MarketOscillatorScreen.kt` — Dialog text updated
- `core/analysis/MarketOscillatorCalculatorTest.kt` — 19 tests fully rewritten
- `feature/market/presentation/oscillator/MarketOscillatorViewModelTest.kt` — Threshold assertions updated

| Aspect | Before (v2) | After (v3) |
|--------|-------------|------------|
| Component scope | All market tickers (~900/~1600) | KOSPI200 ("1028") / KOSDAQ150 ("2203") only |
| Weighting | Simple advance count `advances/total` | `(vol_ratio + pts_ratio) / 2` dual weighting |
| Nonlinear transform | None | `if (avg > 0.5) avg else (avg - 1.0)` — 102pt jump at boundary |
| Output range | [0, 100] | [-100, -50] ∪ (50, 100] |
| Smoothing | 5-day EMA | None (raw daily values) |
| Concurrency | Semaphore(3) parallel | Sequential with 500ms delay |
| Overbought threshold | 70 | 80 |
| Oversold threshold | 30 | -80 |
| API calls (200 days) | ~200 getMarketOhlcv | 1 getPortfolioTickers + ~200 getMarketOhlcv |

**Key Algorithm** (pykrx `_calc()` direct port):
```kotlin
val volRatio = if (totalVol > 0L) upVol.toDouble() / totalVol else 0.5
val ptsRatio = if (totalPts > 0.0) gained / totalPts else 0.5
val avg = (volRatio + ptsRatio) / 2.0
val oscillatorRaw = if (avg > 0.5) avg else (avg - 1.0)  // nonlinear transform
// ×100 → [-100,-50] ∪ (50,100]
```

**NOT changed**: `OscillatorCalculator` (feature/stock/) — individual stock MACD-based supply/demand oscillator is a completely separate system.

---

## S-005: Supply-Demand Oscillator Algorithm

**Decision**: No code change needed. MarketOscillatorCalculator.kt already implements the correct algorithm (per-stock supply ratio via market-cap-weighted aggregation). The reference StockApp uses a different approach (per-stock ka10059 API) which is not applicable to the market-wide oscillator pattern.
**Note**: This was subsequently superseded by M-001 (pykrx algorithm port).

| Aspect | Before | After |
|--------|--------|-------|
| Algorithm | Market-wide top-200 oscillator | Same (already correct) |
| Code changes | - | None |

---

## S-006: Trend Signal Algorithm

**Files Modified**:
- `core/analysis/TechnicalAnalysisEngine.kt` — Added calculateMa(), calcMaSignal(), calcMaSignalWeeklyReference(), calcTrend(), calculateFearGreed()
- `core/analysis/TrendSignalCalculator.kt` — Rewritten determineSignal() to use 2-of-3 voting
- `core/data/repository/krx/KrxStockDataRepositoryImpl.kt` — CMF period by interval (daily=20, weekly=4)
- `core/analysis/TechnicalAnalysisEngineTest.kt` — 36 new tests for MA, trend, FG smoothing
- `core/analysis/TrendSignalCalculatorTest.kt` — Rewritten for voting logic

| Aspect | Before | After |
|--------|--------|-------|
| MA periods | MA20 only | MA5, MA10, MA20, MA60 |
| Daily MA signal | Price vs single MA | 3-MA alignment (MA5>MA20>MA60) |
| CMF period (daily) | 4 | 20 |
| CMF period (weekly) | 4 | 4 (unchanged) |
| FG smoothing | None (direct) | rolling(7/10).mean() per component |
| FG momentum scaling | ×10, clipped [-1,1] | ×100 then rolling(7) smoothed |
| Trend classification | Weighted 4-factor scoring → 5-state | 2-of-3 voting (MA+CMF>0.05+FG>0.5) → 5-state |
| Signal output | STRONG_BUY/BUY/NEUTRAL/SELL/STRONG_SELL | Same enum (backward compatible) |

---

## S-007: Elder Impulse Slope Bug Fix

**File Modified**: `core/analysis/TechnicalAnalysisEngine.kt` (lines 730-735)

**Root Cause**: Slope calculation used backward difference `values[i] - values[i-1]` and set index 0 (newest bar) to 0.0. With newest-first data ordering, this made the newest bar always show 0.0 slope → BLUE (neutral).

| Aspect | Before | After |
|--------|--------|-------|
| EMA slope | `if (i == 0) 0.0 else e - ema[i - 1]` | `if (i + 1 >= ema.size) 0.0 else e - ema[i + 1]` |
| MACD hist slope | `if (i == 0) 0.0 else h - macdHist[i - 1]` | `if (i + 1 >= macdHist.size) 0.0 else h - macdHist[i + 1]` |
| Boundary | Index 0 (newest) gets 0.0 | Last index (oldest) gets 0.0 |
| Direction | Backward difference | Forward difference |
| Effect | Newest bar always BLUE | Newest bar shows correct signal |

**Test Changes**: `TechnicalAnalysisEngineTest.kt` — Elder Impulse tests now use newest-first exponential data:
- Bull: `40000 * exp(-0.06 * i)` (price rising when read newest-first)
- Bear: `100 * exp(0.06 * i)` (price falling when read newest-first)

---

## S-010/S-011: Real-Time Intraday Supply Feature

**New Files Created (9 files)**:

| File | Layer | Purpose |
|------|-------|---------|
| `feature/stock/domain/model/RealtimeSupplyModels.kt` | Domain | RealtimeSupplyData, RealtimeSupplySummary, RealtimeSupplySignal, TradingHours, CachedRealtimeSupplyData |
| `feature/stock/domain/repository/RealtimeSupplyRepository.kt` | Domain | Repository interface |
| `feature/stock/domain/usecase/GetRealtimeSupplyUseCase.kt` | Domain | UseCase with Korean signal descriptions |
| `feature/stock/data/dto/RealtimeSupplyDto.kt` | Data | RealtimeSupplyResponse, RealtimeSupplyItemDto DTOs |
| `feature/stock/data/repository/RealtimeSupplyRepositoryImpl.kt` | Data | API call (ka10063), 60s in-memory cache, JSON parsing |
| `feature/stock/di/RealtimeSupplyModule.kt` | DI | Hilt @Binds module |
| `feature/stock/presentation/realtime/RealtimeSupplyViewModel.kt` | Presentation | Sealed state, auto-refresh (60s during 09:00-15:30 KST), pull-to-refresh |
| `feature/stock/presentation/realtime/RealtimeSupplyTab.kt` | Presentation | Compose UI: signal card, supply/demand metrics, trading hours indicator |

**Modified Files (1 file)**:

| File | Change |
|------|--------|
| `feature/stock/presentation/hub/StocksHubScreen.kt` | Added "장중수급" tab (index 2) alongside "차트 분석" and "재무정보" |

**API**: Kiwoom `ka10063` — POST `/api/dostk/mrkcond` (장중 투자자별 매매)
**Cache**: ConcurrentHashMap, 60s TTL
**Signal thresholds**: netBuyRatio > 0.3 → STRONG_BUY, > 0.1 → BUY, < -0.1 → SELL, < -0.3 → STRONG_SELL
**Korean stock colors**: Red = Buy (매수), Blue = Sell (매도)

---

## Test Fixes (pre-existing, not part of feature scope)

**Files Modified**:
- `AdvancedDashboardViewModelTest.kt` — Fixed `forceRefresh()` tests: replaced virtual-time polling with real-time polling via `Dispatchers.Default` + `withTimeout`; added `awaitStateSettled()` for fast error paths; fixed EtfCorrelation test unconsumed events with `cancelAndIgnoreRemainingEvents()`

| Aspect | Before | After |
|--------|--------|-------|
| forceRefresh polling | `withTimeout(5000)` in virtual test time | `withContext(Dispatchers.Default) { withTimeout(5000) }` in real time |
| Error path await | Same as success path (waits for isRefreshing=true) | Separate `awaitStateSettled()` (waits for state != Loading) |
| EtfCorrelation test | Turbine unconsumed events error | Added `cancelAndIgnoreRemainingEvents()` |
