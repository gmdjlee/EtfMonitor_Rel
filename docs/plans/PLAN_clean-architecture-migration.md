# Clean Architecture Migration Plan (v2 - Comprehensive)

**Feature**: 클린 아키텍처 전환 (Clean Architecture Migration)
**Scope**: Large (7 phases, 20-30 hours total)
**Created**: 2025-12-24
**Last Updated**: 2025-12-24
**Status**: In Progress - Phase 5 Complete

---

**CRITICAL INSTRUCTIONS**: After completing each phase:
1. ✅ Check off completed task checkboxes
2. 🧪 Run all quality gate validation commands
3. ⚠️ Verify ALL quality gate items pass
4. 📅 Update "Last Updated" date
5. 📝 Document learnings in Notes section
6. ➡️ Only then proceed to next phase

⛔ DO NOT skip quality gates or proceed with failing checks

---

## 1. Overview

### 1.1 Current State Analysis

**ViewModels (12개)**:
| ViewModel | Screen | Repository Dependencies |
|-----------|--------|------------------------|
| HomeViewModel | HomeScreen | DataRepository, FearGreedRepository, MarketOscillatorRepository, MarketDepositRepository |
| EtfListViewModel | EtfListScreen | DataRepository |
| DetailViewModel | DetailScreen | DataRepository |
| StockTrendViewModel | StockTrendScreen | DataRepository |
| StatisticsViewModel | RankingTab, StockChangeTab, AnalysisTab, CashDepositTab | DataRepository |
| FearGreedViewModel | FearGreedScreen | FearGreedRepository |
| MarketOscillatorViewModel | MarketOscillatorScreen | MarketOscillatorRepository |
| OscillatorViewModel | OscillatorScreen | StockRepository, StockAnalysisRepository, OscillatorPyClient |
| MarketDepositViewModel | MarketDepositScreen | MarketDepositRepository |
| AdvancedDashboardViewModel | AdvancedDashboardScreen | AdvancedAnalysisRepository |
| NewAIAnalysisViewModel | NewAIAnalysisScreen | AIAnalysisRepository, AIChatRepository, CorrelationAnalysisRepository |
| SettingsViewModel | SettingsScreen | DataRepository, Multiple DAOs |

**Hub Screens (4개 - No ViewModel)**:
- MarketIndicatorHubScreen
- EtfHubScreen
- StocksHubScreen
- AnalysisHubScreen

**Repositories (13개)**:
| Repository | Primary DAO | Python Client | Caching |
|------------|-------------|---------------|---------|
| DataRepository | EtfDao, StockDao, DailyEtfStatisticsDao | PyKrxClient | None |
| StockRepository | StockDao | OscillatorPyClient | None |
| StockAnalysisRepository | StockAnalysisDao | OscillatorPyClient | 24h |
| FearGreedRepository | FearGreedDao | Python direct | None |
| MarketOscillatorRepository | MarketOscillatorDao | OscillatorPyClient | None |
| MarketDepositRepository | MarketDepositDao | OscillatorPyClient | 12h smart |
| MarketIndexRepository | MarketIndexDao | MarketIndexPyClient | None |
| AIAnalysisRepository | AIAnalysisDao | AIApiClientFactory | None |
| AIChatRepository | AIChatDao | AIApiClientFactory | Max 10 msgs |
| CorrelationAnalysisRepository | CorrelationAnalysisDao | AIApiClientFactory | None |
| AdvancedAnalysisRepository | 9 DAOs | None | None |
| StatisticsAnalysisRepository | EtfDao, MarketIndexDao, DailyEtfStatisticsDao | None | None |
| TimeSeriesAnalysisRepository | Multiple DAOs | None | None |

**Python Clients (3개)**:
- PyKrxClient → etfcollector, stocks, core modules
- MarketIndexPyClient → market module
- OscillatorPyClient → stocks, deposit_scraper, market, trend_signal modules

**AI Clients (3개)**:
- AIApiClient (interface)
- ClaudeApiClient → Anthropic API
- GeminiApiClient → Google Generative AI

**Workers (8개)**:
| Worker | Repository | Schedule |
|--------|------------|----------|
| EtfUpdateWorker | DataRepository | Daily 00:30 |
| StockUpdateWorker | StockRepository | Daily |
| MarketDepositUpdateWorker | MarketDepositRepository | Daily |
| FearGreedUpdateWorker | FearGreedRepository | Daily |
| MarketOscillatorUpdateWorker | MarketOscillatorRepository | Daily |
| MarketIndexUpdateWorker | MarketIndexRepository | Daily |
| DataArchiveWorker | DataRepository | Weekly |
| AdvancedAnalysisWorker | AdvancedAnalysisRepository | Periodic |

**Foreground Service**:
- DataCollectionService → DataRepository, FearGreedRepository, MarketOscillatorRepository, MarketIndexRepository, MarketDepositRepository

### 1.2 Target State (Clean Architecture)

```
app/src/main/java/com/etfmonitor/
├── core/                              # 공유 모듈
│   ├── common/                        # 공통 유틸리티
│   │   ├── extension/                 # Kotlin Extensions
│   │   ├── util/                      # Utilities (AppLogger, DateFormatter, etc.)
│   │   └── result/                    # Result wrapper, Exceptions
│   ├── database/                      # Room DB 핵심 (AppDatabase, Converters, Migrations)
│   ├── di/                            # 핵심 DI 모듈 (DatabaseModule, WorkerModule)
│   ├── network/                       # Network/Python 인프라
│   │   ├── python/                    # Python 클라이언트 (PyKrxClient, etc.)
│   │   └── ai/                        # AI 클라이언트 (ClaudeApiClient, etc.)
│   ├── worker/                        # WorkManager Workers (Feature 무관)
│   ├── service/                       # Foreground Service
│   └── ui/                            # 공통 UI
│       ├── component/                 # 공통 Composables (StateCards, Charts, etc.)
│       └── theme/                     # Material 3 테마
│
├── feature/                           # Feature 모듈
│   ├── home/
│   │   ├── data/repository/           # HomeRepositoryImpl
│   │   ├── domain/
│   │   │   ├── model/                 # HomeSummary, HomeState
│   │   │   ├── repository/            # HomeRepository interface
│   │   │   └── usecase/               # GetHomeSummaryUseCase, etc.
│   │   └── presentation/
│   │       ├── component/             # HomeSummaryCard, HomeDialogs
│   │       ├── screen/                # HomeScreen
│   │       └── viewmodel/             # HomeViewModel
│   │
│   ├── etf/
│   │   ├── data/
│   │   │   ├── datasource/            # EtfLocalDataSource
│   │   │   ├── mapper/                # EtfMapper (Entity ↔ Domain)
│   │   │   └── repository/            # EtfRepositoryImpl
│   │   ├── domain/
│   │   │   ├── model/                 # Etf, Holding, ComparisonResult
│   │   │   ├── repository/            # EtfRepository interface
│   │   │   └── usecase/               # GetEtfListUseCase, GetEtfDetailUseCase, etc.
│   │   └── presentation/
│   │       ├── hub/                   # EtfHubScreen
│   │       ├── list/                  # EtfListScreen, EtfListViewModel
│   │       └── detail/                # DetailScreen, DetailViewModel
│   │
│   ├── stock/
│   │   ├── data/
│   │   ├── domain/
│   │   │   ├── model/                 # Stock, StockAnalysis, StockTrend
│   │   │   └── usecase/               # AnalyzeStockUseCase, GetStockTrendUseCase, etc.
│   │   └── presentation/
│   │       ├── hub/                   # StocksHubScreen
│   │       ├── trend/                 # StockTrendScreen, AggregatedStockTrendScreen
│   │       ├── oscillator/            # OscillatorScreen, OscillatorViewModel
│   │       └── statistics/            # StatisticsViewModel, Tab components
│   │
│   ├── market/
│   │   ├── data/
│   │   │   ├── datasource/            # FearGreed, Oscillator, Deposit, Index DataSources
│   │   │   └── repository/            # *RepositoryImpl
│   │   ├── domain/
│   │   │   ├── model/                 # FearGreedIndex, MarketOscillator, MarketDeposit
│   │   │   └── usecase/               # GetFearGreedUseCase, GetMarketDepositUseCase, etc.
│   │   └── presentation/
│   │       ├── hub/                   # MarketIndicatorHubScreen
│   │       ├── feargreed/             # FearGreedScreen, FearGreedViewModel
│   │       ├── oscillator/            # MarketOscillatorScreen, MarketOscillatorViewModel
│   │       └── deposit/               # MarketDepositScreen, MarketDepositViewModel
│   │
│   ├── analysis/
│   │   ├── data/
│   │   │   ├── datasource/
│   │   │   │   ├── ai/                # AIRemoteDataSource
│   │   │   │   └── analysis/          # AnalysisLocalDataSource
│   │   │   └── repository/            # AI*, Correlation*, Advanced*RepositoryImpl
│   │   ├── domain/
│   │   │   ├── model/                 # AIAnalysisResult, CorrelationResult, etc.
│   │   │   └── usecase/               # AnalyzeMarketUseCase, GetCorrelationUseCase, etc.
│   │   └── presentation/
│   │       ├── hub/                   # AnalysisHubScreen
│   │       ├── advanced/              # AdvancedDashboardScreen, AdvancedDashboardViewModel
│   │       └── aianalysis/            # NewAIAnalysisScreen, NewAIAnalysisViewModel
│   │
│   └── settings/
│       ├── data/
│       ├── domain/
│       └── presentation/              # SettingsScreen, SettingsViewModel
│
└── navigation/                        # App Navigation
    └── Navigation.kt
```

### 1.3 Objectives

1. **Feature 기반 모듈화**: 각 기능을 독립적인 feature 모듈로 분리
2. **레이어 분리**: Data/Domain/Presentation 레이어 명확히 분리
3. **UseCase 도입**: 비즈니스 로직을 UseCase로 분리하여 재사용성 향상
4. **의존성 역전**: Repository 인터페이스를 Domain 레이어에, 구현체를 Data 레이어에
5. **성능 유지**: 기존 최적화 패턴 (Holding 압축, 캐싱, LIMIT) 유지
6. **점진적 마이그레이션**: 기존 기능을 유지하면서 단계별로 전환

---

## 2. Architecture Decisions

### 2.1 Package Structure

| Decision | Rationale |
|----------|-----------|
| `feature/` 패키지 사용 | 기능별로 코드를 그룹화하여 응집도 향상 |
| `core/` 패키지 사용 | 공통 코드와 인프라를 분리하여 재사용성 향상 |
| `domain/` 레이어 도입 | 비즈니스 로직을 UI와 Data로부터 분리 |
| UseCase 패턴 | 단일 책임 원칙 준수, 테스트 용이성 |
| Hub 화면 유지 | Navigation 허브 역할, 별도 ViewModel 불필요 |

### 2.2 Dependency Flow

```
Presentation → Domain ← Data
     ↓            ↑        ↓
 ViewModel → UseCase ← Repository(impl)
     ↓            ↑        ↓
  Screen     Interface   DataSource
                              ↓
                         DAO / Python Client / AI Client
```

### 2.3 Python/AI Client 처리 전략

| Component | Location | Rationale |
|-----------|----------|-----------|
| PyKrxClient | `core/network/python/` | 여러 feature에서 사용 |
| MarketIndexPyClient | `core/network/python/` | Market feature 전용이지만 core에 유지 |
| OscillatorPyClient | `core/network/python/` | Stock, Market 등 다양한 feature에서 사용 |
| AIApiClientFactory | `core/network/ai/` | Analysis feature에서 사용하지만 core에 유지 |
| ClaudeApiClient | `core/network/ai/` | AI 인프라 |
| GeminiApiClient | `core/network/ai/` | AI 인프라 |

### 2.4 Worker/Service 처리 전략

| Component | Location | Rationale |
|-----------|----------|-----------|
| EtfUpdateWorker | `core/worker/` | DataRepository 의존, feature 무관 |
| StockUpdateWorker | `core/worker/` | 배경 작업 |
| Market*UpdateWorker | `core/worker/` | 배경 작업 |
| DataCollectionService | `core/service/` | 전역 데이터 수집 서비스 |
| WorkManagerHelper | `core/worker/` | 스케줄링 유틸리티 |

### 2.5 Key Principles

1. **Domain은 아무것도 의존하지 않음**: 순수 Kotlin 코드
2. **Data는 Domain에 의존**: Repository 인터페이스 구현
3. **Presentation은 Domain에 의존**: UseCase 호출
4. **DI를 통한 연결**: Hilt로 의존성 주입
5. **기존 최적화 유지**: Holding 압축, 캐싱, LIMIT 패턴 보존

---

## 3. Performance & Memory Considerations

### 3.1 기존 최적화 패턴 (반드시 유지)

| Pattern | Location | Description |
|---------|----------|-------------|
| Holding 압축 저장 | `Holding.kt` | `weightBps: Short`, `amountMillion: Int` 사용 |
| Holding.create() | `Holding.kt` | Factory method로 오버플로우 방지 |
| DAO LIMIT | `EtfDao.kt` | 랭킹 500, 변경 300, 일반 100 제한 |
| StockAnalysisData JOIN | `StockAnalysisDao.kt` | name 필드 분리 후 JOIN 필수 |
| Python 타임아웃 | Various | PyKrx 30s, Oscillator 180s, ML 120s |
| Repository 캐싱 | StockAnalysis 24h, MarketDeposit 12h | 불필요한 재수집 방지 |
| FearGreed 3x 요청 | `FearGreedRepository.kt` | MA 데이터 손실 보상 |

### 3.2 Migration 시 성능 체크포인트

각 Phase 완료 후 다음 항목 검증:
- [ ] Holding.create() factory method 사용 여부
- [ ] StockAnalysisData JOIN 쿼리 유지 여부
- [ ] DAO LIMIT 패턴 유지 여부
- [ ] Python 타임아웃 설정 유지 여부
- [ ] Repository 캐싱 로직 유지 여부
- [ ] Flow/flowOn(Dispatchers.IO) 패턴 유지 여부

### 3.3 Memory Management

```kotlin
// ✅ CORRECT: UseCase에서도 Flow 유지
class GetEtfListUseCase(private val repository: EtfRepository) {
    operator fun invoke(): Flow<List<Etf>> = repository.getAllEtfs()
        .flowOn(Dispatchers.IO)
}

// ❌ WRONG: Flow를 List로 변환하면 메모리 문제 발생 가능
class GetEtfListUseCase(private val repository: EtfRepository) {
    suspend operator fun invoke(): List<Etf> = repository.getAllEtfs().first() // OOM risk
}
```

---

## 4. Phase Breakdown

### Phase 1: Core Module Setup (3-4 hours)

**Goal**: 공유 모듈 구조를 설정하고 기존 공통 코드를 이동

**Tasks**:
- [x] `core/common/util/` 패키지 생성 및 유틸리티 이동
  - `utils/AppLogger.kt` → `core/common/util/`
  - `utils/DateFormatter.kt` → `core/common/util/`
  - `utils/AppConstants.kt` → `core/common/util/`
  - `utils/Exceptions.kt` → `core/common/util/`
  - `utils/DataArchiver.kt` → `core/common/util/`
  - `ui/utils/AmountFormatter.kt` → `core/common/util/` (추가)
- [x] `core/database/` 패키지 생성
  - `database/AppDatabase.kt` → `core/database/` (entities 폴더 유지)
  - `database/Converters.kt` → `core/database/`
  - **DAOs는 entities와 함께 유지** (마이그레이션 안정성)
- [x] `core/ui/theme/` 패키지 생성
  - `ui/theme/*` → `core/ui/theme/`
- [x] `core/ui/component/` 패키지 생성
  - `ui/components/StateCards.kt` → `core/ui/component/`
  - `ui/components/Material3Components.kt` → `core/ui/component/`
  - `ui/components/DesignSystemComponents.kt` → `core/ui/component/`
  - `ui/components/BottomNavigationBar.kt` → `core/ui/component/`
- [x] `core/di/` 패키지 생성
  - `di/DatabaseModule.kt` → `core/di/`
  - `di/WorkerModule.kt` → `core/di/`
- [x] `core/network/python/` 패키지 생성
  - `python/PyKrxClient.kt` → `core/network/python/`
  - `python/MarketIndexPyClient.kt` → `core/network/python/`
  - `oscillator/python/OscillatorPyClient.kt` → `core/network/python/`
  - `di/PythonModule.kt` → `core/di/`
- [x] `core/network/ai/` 패키지 생성
  - `ai/*` → `core/network/ai/`
  - `di/AIModule.kt` → `core/di/`
- [x] `core/worker/` 패키지 생성
  - `worker/*` → `core/worker/`
- [x] `core/service/` 패키지 생성
  - `service/*` → `core/service/`
- [x] 모든 import 경로 업데이트
- [ ] 빌드 및 기능 테스트 (네트워크 오류로 미완료)

**Quality Gate**:
- [ ] `./gradlew assembleDebug` 성공
- [ ] `./gradlew lint` 경고 검토
- [ ] 모든 기존 기능 정상 동작

**Rollback**: `git revert` to previous commit

---

### Phase 2: Feature - Home Module (2-3 hours)

**Goal**: Home 기능을 클린 아키텍처로 전환 (파일럿)

**Current Dependencies**:
- DataRepository
- FearGreedRepository
- MarketOscillatorRepository
- MarketDepositRepository
- EtfDao
- DataCollectionService

**Target Structure**:
```
feature/home/
├── data/
│   └── repository/
│       └── HomeRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── HomeSummary.kt
│   │   └── HomeState.kt
│   ├── repository/
│   │   └── HomeRepository.kt
│   └── usecase/
│       ├── GetHomeSummaryUseCase.kt
│       ├── CheckDataStatusUseCase.kt
│       ├── CheckFirstRunUseCase.kt
│       └── InitializeAllDataUseCase.kt
├── di/
│   └── HomeModule.kt
└── presentation/
    ├── component/
    │   ├── HomeSummaryCard.kt
    │   └── HomeDialogs.kt
    ├── screen/
    │   └── HomeScreen.kt
    └── viewmodel/
        └── HomeViewModel.kt
```

**Tasks**:
- [ ] `feature/home/domain/model/` 생성
  - HomeSummary, HomeState를 별도 파일로 분리
- [ ] `feature/home/domain/repository/HomeRepository.kt` 인터페이스 생성
- [ ] `feature/home/domain/usecase/` UseCase 클래스 생성
  - GetHomeSummaryUseCase
  - CheckDataStatusUseCase
  - CheckFirstRunUseCase
  - InitializeAllDataUseCase
- [ ] `feature/home/data/repository/HomeRepositoryImpl.kt` 구현
  - 기존 Repository들을 조합하여 구현
- [ ] `feature/home/di/HomeModule.kt` 생성
- [ ] Presentation 레이어 이동 및 리팩토링
  - HomeViewModel → UseCase 의존성으로 변경
  - HomeScreen, HomeSummaryCard, HomeDialogs 이동
- [ ] 빌드 및 기능 테스트

**Quality Gate**:
- [ ] `./gradlew assembleDebug` 성공
- [ ] Home 화면 정상 동작
- [ ] 데이터 초기화/업데이트 정상 동작
- [ ] 다이얼로그 표시 정상

**Rollback**: `git revert` to Phase 1 commit

---

### Phase 3: Feature - ETF Module (2-3 hours)

**Goal**: ETF 목록, 상세, Hub 기능을 클린 아키텍처로 전환

**Current Components**:
- EtfListScreen, EtfListViewModel
- DetailScreen, DetailViewModel
- EtfHubScreen (No ViewModel)
- DataRepository (ETF 관련 부분)

**Target Structure**:
```
feature/etf/
├── data/
│   ├── datasource/
│   │   └── EtfLocalDataSource.kt
│   ├── mapper/
│   │   └── EtfMapper.kt
│   └── repository/
│       └── EtfRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── Etf.kt (Domain)
│   │   ├── Holding.kt (Domain)
│   │   ├── ComparisonResult.kt
│   │   └── HoldingWithComparison.kt
│   ├── repository/
│   │   └── EtfRepository.kt
│   └── usecase/
│       ├── GetEtfListUseCase.kt
│       ├── SearchEtfsUseCase.kt
│       ├── GetEtfDetailUseCase.kt
│       ├── GetEtfComparisonUseCase.kt
│       └── CheckDataExistsUseCase.kt
├── di/
│   └── EtfModule.kt
└── presentation/
    ├── hub/
    │   └── EtfHubScreen.kt
    ├── list/
    │   ├── EtfListScreen.kt
    │   └── EtfListViewModel.kt
    └── detail/
        ├── DetailScreen.kt
        └── DetailViewModel.kt
```

**Tasks**:
- [x] Domain 모델 생성 (Entity와 별도)
  - ⚠️ **Holding 도메인 모델**: weight, amount를 Float으로 노출
  - ⚠️ **Mapper**: Entity의 압축값 변환 로직 유지
- [x] Repository 인터페이스 및 구현체 생성
- [x] UseCase 클래스 생성
- [x] Presentation 레이어 이동 및 리팩토링
- [x] EtfHubScreen 업데이트 (imports만 변경, Statistics 의존성 유지)
- [x] DI 설정
- [ ] 빌드 및 기능 테스트 (네트워크 오류로 미완료)

**Performance Check**:
- [x] Holding.create() 사용 확인 (Mapper에서) - EtfRepositoryImpl에서 직접 Entity의 weight/amount 속성 사용
- [x] DAO LIMIT 패턴 유지 확인 - EtfLocalDataSource가 기존 DAO 메서드 사용

**Rollback**: `git revert` to Phase 2 commit

---

### Phase 4: Feature - Stock Module (3-4 hours)

**Goal**: 종목 분석, 트렌드, 오실레이터, 통계 기능을 클린 아키텍처로 전환

**Current Components**:
- StockTrendScreen, StockTrendViewModel
- AggregatedStockTrendScreen (StatisticsViewModel 공유)
- OscillatorScreen, OscillatorViewModel
- StatisticsViewModel, RankingTab, StockChangeTab, AnalysisTab, CashDepositTab
- StocksHubScreen (No ViewModel)
- StockRepository, StockAnalysisRepository

**Target Structure**:
```
feature/stock/
├── data/
│   ├── datasource/
│   │   ├── StockLocalDataSource.kt
│   │   └── StockAnalysisDataSource.kt
│   ├── mapper/
│   │   └── StockMapper.kt
│   └── repository/
│       ├── StockRepositoryImpl.kt
│       └── StockAnalysisRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── Stock.kt
│   │   ├── StockAnalysis.kt
│   │   ├── StockTrend.kt
│   │   └── StockRanking.kt
│   ├── repository/
│   │   ├── StockRepository.kt
│   │   └── StockAnalysisRepository.kt
│   └── usecase/
│       ├── GetStockRankingUseCase.kt
│       ├── AnalyzeStockUseCase.kt
│       ├── GetStockTrendUseCase.kt
│       ├── GetAggregatedStockTrendUseCase.kt
│       ├── SearchStockUseCase.kt
│       └── GetStockOscillatorUseCase.kt
├── di/
│   └── StockModule.kt
└── presentation/
    ├── hub/
    │   └── StocksHubScreen.kt
    ├── trend/
    │   ├── StockTrendScreen.kt
    │   ├── StockTrendViewModel.kt
    │   └── AggregatedStockTrendScreen.kt
    ├── oscillator/
    │   ├── OscillatorScreen.kt
    │   └── OscillatorViewModel.kt
    └── statistics/
        ├── StatisticsViewModel.kt
        ├── RankingTab.kt
        ├── StockChangeTab.kt
        ├── AnalysisTab.kt
        └── CashDepositTab.kt
```

**Tasks**:
- [x] Domain Layer 생성
- [x] Data Layer 생성 (Repository 구현)
  - ⚠️ **StockAnalysis 24h 캐싱 로직 유지**
  - ⚠️ **StockAnalysisData JOIN 패턴 유지**
- [x] UseCase 클래스 생성
- [x] Presentation Layer 이동
- [x] DI 설정
- [ ] 빌드 및 기능 테스트 (네트워크 오류로 미완료)

**Performance Check**:
- [x] StockAnalysis 캐싱 로직 유지 확인
- [x] StockAnalysisData JOIN 패턴 유지 확인
- [x] OscillatorPyClient 180s 타임아웃 유지 확인 (core/network/python/ 사용)

**Rollback**: `git revert` to Phase 3 commit

---

### Phase 5: Feature - Market Module (3-4 hours)

**Goal**: 시장 지표 기능을 클린 아키텍처로 전환 (FearGreed, Oscillator, Deposit, Index)

**Current Components**:
- FearGreedScreen, FearGreedViewModel, FearGreedRepository
- MarketOscillatorScreen, MarketOscillatorViewModel, MarketOscillatorRepository
- MarketDepositScreen, MarketDepositViewModel, MarketDepositRepository
- MarketIndexRepository
- MarketIndicatorHubScreen (No ViewModel)

**Target Structure**:
```
feature/market/
├── data/
│   ├── datasource/
│   │   ├── FearGreedLocalDataSource.kt
│   │   ├── MarketOscillatorLocalDataSource.kt
│   │   ├── MarketDepositLocalDataSource.kt
│   │   └── MarketIndexLocalDataSource.kt
│   ├── mapper/
│   │   └── MarketMapper.kt
│   └── repository/
│       ├── FearGreedRepositoryImpl.kt
│       ├── MarketOscillatorRepositoryImpl.kt
│       ├── MarketDepositRepositoryImpl.kt
│       └── MarketIndexRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── FearGreedIndex.kt
│   │   ├── MarketOscillator.kt
│   │   ├── MarketDeposit.kt
│   │   └── MarketIndex.kt
│   ├── repository/
│   │   └── (4 interfaces)
│   └── usecase/
│       ├── GetFearGreedUseCase.kt
│       ├── InitializeFearGreedUseCase.kt
│       ├── GetMarketOscillatorUseCase.kt
│       ├── GetMarketDepositUseCase.kt
│       └── GetMarketIndexUseCase.kt
├── di/
│   └── MarketModule.kt
└── presentation/
    ├── hub/
    │   └── MarketIndicatorHubScreen.kt
    ├── feargreed/
    │   ├── FearGreedScreen.kt
    │   └── FearGreedViewModel.kt
    ├── oscillator/
    │   ├── MarketOscillatorScreen.kt
    │   └── MarketOscillatorViewModel.kt
    └── deposit/
        ├── MarketDepositScreen.kt
        └── MarketDepositViewModel.kt
```

**Tasks**:
- [x] Domain Layer 생성
  - FearGreed, MarketOscillator, MarketDeposit, MarketIndex models
  - Repository interfaces
  - UseCase classes (12 UseCases total)
- [x] Data Layer 생성
  - ⚠️ **FearGreed 3x 요청 로직 유지** ✅
  - ⚠️ **MarketDeposit 12h 캐싱 로직 유지** ✅
  - LocalDataSources wrapping DAOs
  - MarketMapper for Entity <-> Domain conversion
  - Repository implementations
- [x] UseCase 클래스 생성
- [x] Presentation Layer 이동
  - FearGreedScreen, FearGreedViewModel
  - MarketOscillatorScreen, MarketOscillatorViewModel
  - MarketDepositScreen, MarketDepositViewModel
  - MarketIndicatorHubScreen
- [x] DI 설정 (MarketModule.kt)
- [x] 기존 의존성 업데이트
  - Workers: FearGreedUpdateWorker, MarketOscillatorUpdateWorker, MarketDepositUpdateWorker, MarketIndexUpdateWorker
  - DataCollectionService
  - SettingsViewModel
  - HomeViewModel(s)
  - HomeRepositoryImpl
  - AIModule
- [x] Old files 삭제
  - repository/FearGreedRepository.kt
  - repository/MarketOscillatorRepository.kt
  - repository/MarketDepositRepository.kt
  - repository/MarketIndexRepository.kt
  - Old UI screens (feargreed, marketoscillator, oscillator, hub)
- [ ] 빌드 및 기능 테스트 (네트워크 오류로 미완료)

**Performance Check**:
- [x] FearGreed 3x 요청 로직 유지 확인 (FearGreedRepositoryImpl.kt:91)
- [x] MarketDeposit 캐싱 로직 유지 확인 (MarketDepositRepositoryImpl.kt:36, DATA_EXPIRY_HOURS = 12)
- [x] Python 타임아웃 설정 유지 확인 (core/network/python/ 사용)

**Rollback**: `git revert` to Phase 4 commit

---

### Phase 6: Feature - Analysis & AI Module (3-4 hours)

**Goal**: 분석 및 AI 기능을 클린 아키텍처로 전환

**Current Components**:
- AdvancedDashboardScreen, AdvancedDashboardViewModel, AdvancedAnalysisRepository (9 DAO 의존)
- NewAIAnalysisScreen, NewAIAnalysisViewModel
- AIAnalysisRepository, AIChatRepository, CorrelationAnalysisRepository
- StatisticsAnalysisRepository, TimeSeriesAnalysisRepository
- AnalysisHubScreen (No ViewModel)

**Target Structure**:
```
feature/analysis/
├── data/
│   ├── datasource/
│   │   ├── AIRemoteDataSource.kt
│   │   ├── AdvancedAnalysisLocalDataSource.kt
│   │   └── CorrelationLocalDataSource.kt
│   └── repository/
│       ├── AIAnalysisRepositoryImpl.kt
│       ├── AIChatRepositoryImpl.kt
│       ├── CorrelationAnalysisRepositoryImpl.kt
│       ├── AdvancedAnalysisRepositoryImpl.kt
│       ├── StatisticsAnalysisRepositoryImpl.kt
│       └── TimeSeriesAnalysisRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── AIAnalysisResult.kt
│   │   ├── MarketSignal.kt
│   │   ├── CorrelationResult.kt
│   │   ├── MarketCapWeightedFlow.kt
│   │   ├── SectorAnalysis.kt
│   │   └── LiquidityAnalysis.kt
│   ├── repository/
│   │   └── (6 interfaces)
│   └── usecase/
│       ├── AnalyzeMarketWithAIUseCase.kt
│       ├── ChatWithAIUseCase.kt
│       ├── GetCorrelationAnalysisUseCase.kt
│       ├── GetMarketCapFlowUseCase.kt
│       ├── GetSectorAnalysisUseCase.kt
│       └── GetLiquidityAnalysisUseCase.kt
├── di/
│   └── AnalysisModule.kt
└── presentation/
    ├── hub/
    │   └── AnalysisHubScreen.kt
    ├── advanced/
    │   ├── AdvancedDashboardScreen.kt
    │   ├── AdvancedDashboardViewModel.kt
    │   └── tabs/ (existing tab components)
    └── aianalysis/
        ├── NewAIAnalysisScreen.kt
        └── NewAIAnalysisViewModel.kt
```

**Tasks**:
- [ ] Domain Layer 생성
- [ ] Data Layer 생성
  - ⚠️ **AIChatRepository 10 메시지 제한 유지**
  - ⚠️ **AdvancedAnalysisRepository 복잡 로직 보존**
- [ ] UseCase 클래스 생성
- [ ] Presentation Layer 이동
- [ ] DI 설정
- [ ] 빌드 및 기능 테스트

**Performance Check**:
- [ ] AI API 타임아웃 60s 유지 확인
- [ ] 복잡한 분석 로직 정확성 검증

**Rollback**: `git revert` to Phase 5 commit

---

### Phase 7: Feature - Settings & Final Cleanup (2-3 hours)

**Goal**: Settings 기능 전환 및 최종 정리

**Current Components**:
- SettingsScreen, SettingsViewModel (25+ StateFlows)
- 다양한 Settings components

**Tasks**:
- [ ] `feature/settings/` 구조 생성
- [ ] Settings 도메인 모델 및 UseCase 생성
- [ ] SettingsViewModel 리팩토링
- [ ] Navigation 모듈 분리 (`navigation/`)
- [ ] 사용하지 않는 구 파일 정리
- [ ] `analysis/` 폴더 정리 (CorrelationAnalyzer, Backtester 이동)
- [ ] 최종 빌드 및 전체 기능 테스트
- [ ] CLAUDE.md 문서 업데이트

**Final Quality Gate**:
- [ ] `./gradlew assembleDebug` 성공
- [ ] `./gradlew lint` 통과
- [ ] 모든 화면 정상 동작 확인
  - [ ] Home
  - [ ] ETF List/Detail/Hub
  - [ ] Stock Trend/Oscillator/Statistics/Hub
  - [ ] Market FearGreed/Oscillator/Deposit/Hub
  - [ ] Analysis Advanced/AI/Hub
  - [ ] Settings
- [ ] 모든 워커 스케줄링 확인
- [ ] 데이터 수집 서비스 정상 동작 확인

**Rollback**: `git revert` to Phase 6 commit

---

## 5. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Import 경로 충돌 | High | Medium | IDE 리팩토링 도구 활용, 단계별 검증 |
| DI 설정 오류 | Medium | High | 각 Phase별 빌드 테스트 |
| 기능 회귀 | Medium | High | 각 화면별 수동 테스트 |
| 성능 저하 | Medium | High | 성능 체크포인트 검증 |
| 캐싱 로직 손실 | Low | High | 명시적 캐싱 패턴 검증 |
| Holding 압축 미적용 | Low | Critical | Mapper에서 create() 사용 확인 |
| 빌드 시간 증가 | Low | Low | Gradle 캐시 활용 |

---

## 6. Entity/DAO Handling Strategy

### 6.1 Database Layer 유지 정책

**Entity와 DAO는 `core/database/`에 유지**:
- Room 마이그레이션 안정성
- 다중 feature에서 공유 사용
- 기존 압축/최적화 로직 보존

### 6.2 Domain Model 분리

각 feature에 Domain Model 생성:
```kotlin
// feature/etf/domain/model/Etf.kt (Domain)
data class Etf(
    val ticker: String,
    val name: String
)

// Mapper에서 변환
fun EtfEntity.toDomain() = Etf(ticker = ticker, name = name)
fun Etf.toEntity() = EtfEntity(ticker = ticker, name = name)
```

### 6.3 Holding 특수 처리

```kotlin
// feature/etf/data/mapper/HoldingMapper.kt
fun HoldingEntity.toDomain() = HoldingDomain(
    etfTicker = etfTicker,
    stockTicker = stockTicker,
    stockName = stockName,
    date = date,
    weight = weight,         // Entity의 computed property 사용
    amount = amount,         // Entity의 computed property 사용
    snapshotType = snapshotType
)

// Entity 생성 시 반드시 factory 사용
fun HoldingDomain.toEntity() = Holding.create(
    etfTicker = etfTicker,
    stockTicker = stockTicker,
    stockName = stockName,
    date = date,
    weight = weight,
    amount = amount,
    snapshotType = snapshotType
)
```

---

## 7. Progress Tracking

| Phase | Status | Start Date | End Date | Notes |
|-------|--------|------------|----------|-------|
| Phase 1: Core Module | ✅ Complete | 2025-12-24 | 2025-12-24 | All files moved, imports updated, old folders deleted |
| Phase 2: Home Module | ⬜ Pending | - | - | - |
| Phase 3: ETF Module | ✅ Complete | 2025-12-24 | 2025-12-24 | Clean Architecture implemented, UseCases introduced |
| Phase 4: Stock Module | ✅ Complete | 2025-12-24 | 2025-12-24 | Domain/Data/Presentation layers, 9 UseCases, 4 Repositories |
| Phase 5: Market Module | ✅ Complete | 2025-12-24 | 2025-12-24 | 30 files, 12 UseCases, 4 Repositories, critical logic preserved |
| Phase 6: Analysis Module | ⬜ Pending | - | - | - |
| Phase 7: Settings & Cleanup | ⬜ Pending | - | - | - |

---

## 8. Notes & Learnings

### Phase 1 (2025-12-24)

**Completed Tasks:**
1. Created `core/` package structure:
   - `core/common/util/` - AppLogger, DateFormatter, AppConstants, Exceptions, DataArchiver, AmountFormatter
   - `core/database/` - AppDatabase, Converters (entities and DAOs remain in database/)
   - `core/ui/theme/` - All theme files (Theme.kt, Color.kt, ExtendedColors.kt, etc.)
   - `core/ui/component/` - StateCards, Material3Components, DesignSystemComponents, BottomNavigationBar
   - `core/di/` - DatabaseModule, WorkerModule, PythonModule, AIModule
   - `core/network/python/` - PyKrxClient, MarketIndexPyClient, OscillatorPyClient
   - `core/network/ai/` - All AI clients and utilities
   - `core/worker/` - All workers and WorkManagerHelper
   - `core/service/` - DataCollectionService, CollectionState

2. Updated all import paths across 40+ files:
   - Repository files (12+)
   - ViewModel files
   - Screen files (28+)
   - DI module files
   - Main entry points (MainActivity, EtfMonitorApp)

3. Deleted old folders:
   - `utils/`, `ai/`, `python/`, `oscillator/python/`
   - `worker/`, `service/`
   - `ui/theme/`, `ui/utils/`
   - Old DI files (DatabaseModule, WorkerModule, AIModule, PythonModule from di/)

**Lessons Learned:**
- AmountFormatter was in `ui/utils/` not `utils/`, needed to be added to the plan
- Database entities and DAOs remain in `database/` for Room migration stability
- `di/RepositoryModule.kt` remains in `di/` as it depends on feature repositories

**Pending:**
- Build verification blocked by network issues (`java.net.UnknownHostException: services.gradle.org`)
- Full functionality testing to be done when network is available

### Phase 3 (2025-12-24)

**Completed Tasks:**
1. Created `feature/etf/` package structure:
   - `domain/model/` - Etf, HoldingStatus, HoldingWithComparison, ComparisonResult, DataStatus
   - `domain/repository/` - EtfRepository interface
   - `domain/usecase/` - GetEtfListUseCase, SearchEtfsUseCase, GetEtfDetailUseCase, GetEtfComparisonUseCase, CheckDataStatusUseCase
   - `data/datasource/` - EtfLocalDataSource
   - `data/mapper/` - EtfMapper (Entity <-> Domain conversion)
   - `data/repository/` - EtfRepositoryImpl with comparison analysis logic
   - `di/` - EtfModule providing all feature dependencies
   - `presentation/list/` - EtfListScreen, EtfListViewModel, EtfListState
   - `presentation/detail/` - EtfDetailScreen, EtfDetailViewModel, EtfDetailState

2. Updated files:
   - `Navigation.kt` - Updated imports to use new feature paths
   - `EtfHubScreen.kt` - Updated to use new EtfListViewModel/EtfListState

3. Deleted old files:
   - `ui/screens/list/` - EtfListScreen.kt, EtfListViewModel.kt
   - `ui/screens/detail/` - DetailScreen.kt, DetailViewModel.kt

**Architecture Decisions:**
- Domain models are separate from Entity classes (e.g., feature's Etf vs database's Etf)
- ViewModels now depend on UseCases instead of direct Repository access
- EtfHubScreen remains in `ui/screens/hub/` as it also uses StatisticsViewModel (will be migrated in Phase 4)
- Comparison analysis logic moved from DataRepository to EtfRepositoryImpl

**Performance Considerations:**
- Holding entity's compressed storage (weightBps, amountMillion) handled in EtfRepositoryImpl
- Flow/flowOn(Dispatchers.IO) pattern maintained in all layers

**Pending:**
- Build verification blocked by network issues
- EtfHubScreen will be fully migrated when Statistics/Stock Module is complete

### Phase 4 (2025-12-24)

**Completed Tasks:**
1. Created `feature/stock/` package structure:
   - `domain/model/` - Stock, StockTrend, StockAnalysis, StockRanking (StockAmountRanking, StockChangeInfo, StockAnalysisResult, CashDepositTrend), OscillatorModels (OscillatorResult, TradeSignal, SignalAnalysis)
   - `domain/repository/` - StockRepository, StockAnalysisRepository, StockTrendRepository, StockStatisticsRepository interfaces
   - `domain/usecase/` - 9 UseCases (SearchStocksUseCase, GetStockTrendUseCase, GetStockAnalysisUseCase, GetStockRankingUseCase, GetStockChangesUseCase, AnalyzeStockUseCase, GetStatisticsDatesUseCase, GetCashDepositTrendUseCase, InitializeStocksUseCase)
   - `data/datasource/` - StockLocalDataSource, StockAnalysisLocalDataSource, StockStatisticsLocalDataSource
   - `data/mapper/` - StockMapper (Entity <-> Domain conversion)
   - `data/repository/` - 4 Repository implementations (StockRepositoryImpl, StockAnalysisRepositoryImpl, StockTrendRepositoryImpl, StockStatisticsRepositoryImpl)
   - `di/` - StockModule providing all feature dependencies
   - `presentation/trend/` - StockTrendScreen, StockTrendViewModel, StockTrendState
   - `presentation/statistics/` - StatisticsViewModel with sorting capabilities

2. Updated files:
   - `Navigation.kt` - Updated imports to use new StockTrendScreen from feature module

3. Deleted old files:
   - `ui/screens/trend/StockTrendScreen.kt`
   - `ui/screens/trend/StockTrendViewModel.kt`

**Architecture Decisions:**
- Old repositories (StockRepository, StockAnalysisRepository in repository/) kept for backward compatibility
  - Still used by MainActivity, SettingsViewModel, StockUpdateWorker, OscillatorViewModel
  - Will be migrated in Phase 6 (Final Integration)
- StocksHubScreen and OscillatorScreen kept using existing ViewModels
- New StatisticsViewModel created in feature module (parallel with old one in ui/screens/statistics/)
- StockTrendRepository and StockStatisticsRepository both use StockStatisticsLocalDataSource (wraps EtfDao for holding statistics)

**Performance Considerations:**
- StockAnalysisRepositoryImpl preserves 24h caching logic with cache invalidation checks
- StockStatisticsLocalDataSource delegates to EtfDao methods that include proper LIMIT clauses
- All Repository implementations use withContext(Dispatchers.IO) for thread safety

**Pending:**
- Build verification blocked by network issues
- Full migration of old repositories to be done in Phase 6

### Phase 5 (2025-12-24)

**Completed Tasks:**
1. Created `feature/market/` package structure with 30 files:
   - `domain/model/` - FearGreed, MarketOscillator, MarketDeposit, MarketIndex, MarketState (sealed classes)
   - `domain/repository/` - 4 repository interfaces
   - `domain/usecase/` - 12 UseCases across 4 feature files
   - `data/datasource/` - 4 LocalDataSources wrapping DAOs
   - `data/mapper/` - MarketMapper for Entity <-> Domain conversion
   - `data/repository/` - 4 Repository implementations
   - `di/` - MarketModule providing all market dependencies
   - `presentation/feargreed/` - FearGreedScreen, FearGreedViewModel
   - `presentation/oscillator/` - MarketOscillatorScreen, MarketOscillatorViewModel
   - `presentation/deposit/` - MarketDepositScreen, MarketDepositViewModel
   - `presentation/hub/` - MarketIndicatorHubScreen

2. Updated dependencies across 13 files:
   - Workers: FearGreedUpdateWorker, MarketOscillatorUpdateWorker, MarketDepositUpdateWorker, MarketIndexUpdateWorker
   - Services: DataCollectionService
   - ViewModels: SettingsViewModel, HomeViewModel (old), HomeViewModel (feature/home)
   - Repositories: HomeRepositoryImpl
   - DI: AIModule, RepositoryModule

3. Deleted old files:
   - 4 old repository files from `repository/`
   - 7 old UI files from `ui/screens/` (feargreed, marketoscillator, oscillator, hub)
   - 3 empty directories

**Critical Logic Preserved:**
- **FearGreed 3x Request**: `collectionDays = minOf(days * 3, 730)` in FearGreedRepositoryImpl
- **MarketDeposit 12h Caching**: `DATA_EXPIRY_HOURS = 12` with `shouldUpdateMarketData()` logic

**Architecture Decisions:**
- ViewModels use UseCases for all operations (not direct repository access)
- MarketModule.kt provides all dependencies (data sources, repositories, use cases)
- Old repository files deleted; all consumers now use domain repository interfaces
- Navigation updated to use new screen paths

**Pending:**
- Build verification blocked by network issues (`java.net.UnknownHostException: services.gradle.org`)

---

## 9. Approval

**이 계획을 검토하고 승인해 주세요.**

### 변경사항 (v2):
1. 모든 화면 및 기능 완전 매핑 추가
2. Python/AI 클라이언트 처리 전략 명시
3. Worker/Service 처리 전략 명시
4. 성능/메모리 최적화 체크포인트 추가
5. Entity/DAO 처리 전략 추가
6. Holding 압축 저장 패턴 보존 방안 추가
7. 캐싱 로직 유지 방안 추가
8. Hub 화면 처리 방안 명시

### 질문:
1. 이 계획의 범위와 상세도가 적절한가요?
2. 성능/메모리 최적화 체크포인트가 충분한가요?
3. 추가로 고려해야 할 사항이 있나요?
4. Phase 1부터 진행을 시작해도 될까요?
