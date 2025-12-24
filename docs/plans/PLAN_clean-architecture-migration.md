# Clean Architecture Migration Plan

**Feature**: 클린 아키텍처 전환 (Clean Architecture Migration)
**Scope**: Large (6-7 phases, 15-25 hours total)
**Created**: 2025-12-24
**Last Updated**: 2025-12-24
**Status**: Draft - Awaiting Approval

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

### 1.1 Current State

현재 ETF Monitor 앱은 **레이어 기반 구조**로 구성되어 있습니다:

```
app/src/main/java/com/etfmonitor/
├── ai/                    # AI 클라이언트 (11 files)
├── analysis/              # 분석 로직 (3 files)
├── database/              # Room DB (entities 18, DAOs 16)
│   └── entities/          # Entity 클래스
├── di/                    # Hilt 모듈 (5 modules)
├── oscillator/            # 오실레이터 관련
│   ├── calculator/
│   ├── model/
│   └── python/
├── python/                # Python 브릿지 (2 files)
├── repository/            # Repository (13 files)
├── service/               # Foreground Service
├── ui/                    # UI Layer
│   ├── components/        # 공통 컴포넌트
│   ├── screens/           # Feature별 화면 (14 screens)
│   │   ├── home/
│   │   ├── detail/
│   │   ├── list/
│   │   ├── settings/
│   │   ├── statistics/
│   │   ├── advanced/
│   │   ├── aianalysis/
│   │   ├── feargreed/
│   │   ├── hub/
│   │   ├── marketoscillator/
│   │   ├── oscillator/
│   │   └── trend/
│   └── theme/             # Material 3 테마
├── utils/                 # 유틸리티
└── worker/                # WorkManager Workers
```

### 1.2 Target State (Clean Architecture)

```
app/src/main/java/com/etfmonitor/
├── core/                              # 공유 모듈
│   ├── common/                        # 공통 유틸리티, 확장 함수
│   │   ├── extension/                 # Kotlin Extensions
│   │   ├── util/                      # Utilities
│   │   └── result/                    # Result wrapper
│   ├── database/                      # Room DB 설정 (AppDatabase, Converters)
│   ├── di/                            # 핵심 DI 모듈
│   ├── network/                       # Network 설정 (Python clients)
│   └── ui/                            # 공통 UI
│       ├── component/                 # 공통 Composables
│       └── theme/                     # Material 3 테마
│
├── feature/                           # Feature 모듈
│   ├── home/
│   │   ├── data/                      # Data Layer
│   │   │   ├── datasource/            # Local/Remote DataSources
│   │   │   └── repository/            # Repository Implementation
│   │   ├── domain/                    # Domain Layer
│   │   │   ├── model/                 # Domain Models
│   │   │   ├── repository/            # Repository Interface
│   │   │   └── usecase/               # Use Cases
│   │   └── presentation/              # Presentation Layer
│   │       ├── component/             # Feature-specific Composables
│   │       ├── screen/                # Screen Composables
│   │       └── viewmodel/             # ViewModels
│   │
│   ├── etf/                           # ETF List & Detail
│   ├── stock/                         # Stock Analysis & Trends
│   ├── market/                        # Market Indicators (FearGreed, Oscillator, Deposit)
│   ├── analysis/                      # Advanced Analysis & AI
│   └── settings/                      # Settings
│
└── navigation/                        # App Navigation
```

### 1.3 Objectives

1. **Feature 기반 모듈화**: 각 기능을 독립적인 feature 모듈로 분리
2. **레이어 분리**: Data/Domain/Presentation 레이어 명확히 분리
3. **UseCase 도입**: 비즈니스 로직을 UseCase로 분리하여 재사용성 향상
4. **의존성 역전**: Repository 인터페이스를 Domain 레이어에, 구현체를 Data 레이어에
5. **점진적 마이그레이션**: 기존 기능을 유지하면서 단계별로 전환

---

## 2. Architecture Decisions

### 2.1 Package Structure

| Decision | Rationale |
|----------|-----------|
| `feature/` 패키지 사용 | 기능별로 코드를 그룹화하여 응집도 향상 |
| `core/` 패키지 사용 | 공통 코드와 인프라를 분리하여 재사용성 향상 |
| `domain/` 레이어 도입 | 비즈니스 로직을 UI와 Data로부터 분리 |
| UseCase 패턴 | 단일 책임 원칙 준수, 테스트 용이성 |

### 2.2 Dependency Flow

```
Presentation → Domain ← Data
     ↓            ↑        ↓
 ViewModel → UseCase ← Repository(impl)
     ↓            ↑        ↓
  Screen     Interface   DataSource
```

### 2.3 Key Principles

1. **Domain은 아무것도 의존하지 않음**: 순수 Kotlin 코드
2. **Data는 Domain에 의존**: Repository 인터페이스 구현
3. **Presentation은 Domain에 의존**: UseCase 호출
4. **DI를 통한 연결**: Hilt로 의존성 주입

---

## 3. Phase Breakdown

### Phase 1: Core Module Setup (2-3 hours)

**Goal**: 공유 모듈 구조를 설정하고 기존 공통 코드를 이동

**Tasks**:
- [ ] `core/common/` 패키지 생성 및 유틸리티 이동
  - `utils/AppLogger.kt` → `core/common/util/AppLogger.kt`
  - `utils/DateFormatter.kt` → `core/common/util/DateFormatter.kt`
  - `utils/AppConstants.kt` → `core/common/util/AppConstants.kt`
  - `utils/Exceptions.kt` → `core/common/util/Exceptions.kt`
  - `utils/DataArchiver.kt` → `core/common/util/DataArchiver.kt`
- [ ] `core/common/result/` 패키지 생성 및 Result wrapper 추가
- [ ] `core/common/extension/` 패키지 생성
- [ ] `core/database/` 패키지 생성 및 DB 설정 이동
  - `database/AppDatabase.kt` → `core/database/AppDatabase.kt`
  - `database/Converters.kt` → `core/database/Converters.kt`
- [ ] `core/ui/theme/` 패키지 생성 및 테마 이동
  - `ui/theme/*` → `core/ui/theme/*`
- [ ] `core/ui/component/` 패키지 생성 및 공통 컴포넌트 이동
  - `ui/components/StateCards.kt` → `core/ui/component/StateCards.kt`
  - `ui/components/Material3Components.kt` → `core/ui/component/Material3Components.kt`
  - `ui/components/DesignSystemComponents.kt` → `core/ui/component/DesignSystemComponents.kt`
- [ ] `core/di/` 패키지 생성 및 DI 모듈 이동
  - `di/DatabaseModule.kt` → `core/di/DatabaseModule.kt`
  - `di/WorkerModule.kt` → `core/di/WorkerModule.kt`
- [ ] 모든 import 경로 업데이트
- [ ] 빌드 및 테스트 확인

**Quality Gate**:
- [ ] `./gradlew assembleDebug` 성공
- [ ] 모든 기존 기능 정상 동작
- [ ] Import 경로 충돌 없음

**Dependencies**: None

**Rollback**: `git revert` to previous commit

---

### Phase 2: Feature - Home Module (2-3 hours)

**Goal**: Home 기능을 클린 아키텍처로 전환 (파일럿)

**Current Structure**:
```
ui/screens/home/
├── HomeScreen.kt
├── HomeViewModel.kt
├── HomeDialogs.kt
└── HomeSummaryCard.kt
```

**Target Structure**:
```
feature/home/
├── data/
│   ├── datasource/
│   │   └── HomeLocalDataSource.kt
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
│       └── InitializeDataUseCase.kt
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
- [ ] `feature/home/` 디렉토리 구조 생성
- [ ] Domain Layer 생성
  - [ ] `domain/model/HomeSummary.kt` - 도메인 모델 정의
  - [ ] `domain/model/HomeState.kt` - UI 상태 sealed class
  - [ ] `domain/repository/HomeRepository.kt` - Repository 인터페이스
  - [ ] `domain/usecase/GetHomeSummaryUseCase.kt` - 요약 정보 조회
  - [ ] `domain/usecase/CheckDataStatusUseCase.kt` - 데이터 상태 확인
  - [ ] `domain/usecase/InitializeDataUseCase.kt` - 데이터 초기화
- [ ] Data Layer 생성
  - [ ] `data/datasource/HomeLocalDataSource.kt` - 로컬 데이터 소스
  - [ ] `data/repository/HomeRepositoryImpl.kt` - Repository 구현
- [ ] Presentation Layer 이동 및 수정
  - [ ] `HomeViewModel.kt` → UseCase 의존성으로 변경
  - [ ] `HomeScreen.kt`, `HomeSummaryCard.kt`, `HomeDialogs.kt` 이동
- [ ] DI 설정
  - [ ] `feature/home/di/HomeModule.kt` 생성
- [ ] 빌드 및 테스트 확인

**Quality Gate**:
- [ ] `./gradlew assembleDebug` 성공
- [ ] Home 화면 정상 동작
- [ ] 데이터 초기화/업데이트 정상 동작
- [ ] 다이얼로그 표시 정상

**Dependencies**: Phase 1 완료

**Rollback**: `git revert` to Phase 1 commit

---

### Phase 3: Feature - ETF Module (2-3 hours)

**Goal**: ETF 목록 및 상세 기능을 클린 아키텍처로 전환

**Current Structure**:
```
ui/screens/list/
├── EtfListScreen.kt
└── EtfListViewModel.kt
ui/screens/detail/
├── DetailScreen.kt
└── DetailViewModel.kt
repository/DataRepository.kt (ETF 관련 부분)
database/entities/Etf.kt, Holding.kt
database/EtfDao.kt
```

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
│   │   ├── Etf.kt
│   │   ├── Holding.kt
│   │   └── ComparisonResult.kt
│   ├── repository/
│   │   └── EtfRepository.kt
│   └── usecase/
│       ├── GetEtfListUseCase.kt
│       ├── SearchEtfsUseCase.kt
│       ├── GetEtfDetailUseCase.kt
│       └── GetEtfComparisonUseCase.kt
└── presentation/
    ├── list/
    │   ├── EtfListScreen.kt
    │   └── EtfListViewModel.kt
    └── detail/
        ├── DetailScreen.kt
        └── DetailViewModel.kt
```

**Tasks**:
- [ ] `feature/etf/` 디렉토리 구조 생성
- [ ] Domain Layer 생성
  - [ ] `domain/model/` - Etf, Holding, ComparisonResult 도메인 모델
  - [ ] `domain/repository/EtfRepository.kt` - Repository 인터페이스
  - [ ] `domain/usecase/*` - UseCase 클래스들
- [ ] Data Layer 생성
  - [ ] `data/datasource/EtfLocalDataSource.kt` - 로컬 데이터 소스
  - [ ] `data/mapper/EtfMapper.kt` - Entity ↔ Domain 변환
  - [ ] `data/repository/EtfRepositoryImpl.kt` - Repository 구현
- [ ] Presentation Layer 이동
  - [ ] List와 Detail 화면 분리하여 이동
  - [ ] ViewModel UseCase 의존성으로 변경
- [ ] DI 설정
  - [ ] `feature/etf/di/EtfModule.kt` 생성
- [ ] 빌드 및 테스트 확인

**Quality Gate**:
- [ ] `./gradlew assembleDebug` 성공
- [ ] ETF 목록 화면 정상 동작
- [ ] ETF 상세 화면 정상 동작
- [ ] 검색 기능 정상 동작

**Dependencies**: Phase 2 완료

**Rollback**: `git revert` to Phase 2 commit

---

### Phase 4: Feature - Stock Module (2-3 hours)

**Goal**: 종목 분석 및 트렌드 기능을 클린 아키텍처로 전환

**Current Structure**:
```
ui/screens/trend/
├── StockTrendScreen.kt
└── StockTrendViewModel.kt
ui/screens/statistics/
├── StatisticsViewModel.kt
├── RankingTab.kt
├── StockChangeTab.kt
├── AnalysisTab.kt
├── CashDepositTab.kt
└── AggregatedStockTrendScreen.kt
repository/StockRepository.kt
repository/StockAnalysisRepository.kt
database/entities/Stock.kt, StockAnalysisData.kt
```

**Target Structure**:
```
feature/stock/
├── data/
│   ├── datasource/
│   │   └── StockLocalDataSource.kt
│   ├── mapper/
│   │   └── StockMapper.kt
│   └── repository/
│       ├── StockRepositoryImpl.kt
│       └── StockAnalysisRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── Stock.kt
│   │   ├── StockAnalysis.kt
│   │   └── StockTrend.kt
│   ├── repository/
│   │   ├── StockRepository.kt
│   │   └── StockAnalysisRepository.kt
│   └── usecase/
│       ├── GetStockRankingUseCase.kt
│       ├── AnalyzeStockUseCase.kt
│       └── GetStockTrendUseCase.kt
└── presentation/
    ├── trend/
    │   ├── StockTrendScreen.kt
    │   └── StockTrendViewModel.kt
    └── statistics/
        ├── StatisticsViewModel.kt
        └── tabs/
```

**Tasks**:
- [ ] `feature/stock/` 디렉토리 구조 생성
- [ ] Domain Layer 생성
- [ ] Data Layer 생성
- [ ] Presentation Layer 이동
- [ ] DI 설정
- [ ] 빌드 및 테스트 확인

**Quality Gate**:
- [ ] `./gradlew assembleDebug` 성공
- [ ] 종목 트렌드 화면 정상 동작
- [ ] 통계 화면 정상 동작

**Dependencies**: Phase 3 완료

**Rollback**: `git revert` to Phase 3 commit

---

### Phase 5: Feature - Market Module (2-3 hours)

**Goal**: 시장 지표 기능을 클린 아키텍처로 전환 (FearGreed, Oscillator, Deposit, MarketIndex)

**Current Structure**:
```
ui/screens/feargreed/
ui/screens/marketoscillator/
ui/screens/oscillator/
repository/FearGreedRepository.kt
repository/MarketOscillatorRepository.kt
repository/MarketDepositRepository.kt
repository/MarketIndexRepository.kt
```

**Target Structure**:
```
feature/market/
├── data/
│   ├── datasource/
│   │   ├── FearGreedDataSource.kt
│   │   ├── MarketOscillatorDataSource.kt
│   │   ├── MarketDepositDataSource.kt
│   │   └── MarketIndexDataSource.kt
│   └── repository/
│       └── *Impl.kt
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
└── presentation/
    ├── feargreed/
    ├── oscillator/
    └── deposit/
```

**Tasks**:
- [ ] `feature/market/` 디렉토리 구조 생성
- [ ] Domain Layer 생성
- [ ] Data Layer 생성
- [ ] Presentation Layer 이동
- [ ] DI 설정
- [ ] 빌드 및 테스트 확인

**Quality Gate**:
- [ ] `./gradlew assembleDebug` 성공
- [ ] Fear & Greed 화면 정상 동작
- [ ] Market Oscillator 화면 정상 동작
- [ ] Market Deposit 화면 정상 동작

**Dependencies**: Phase 4 완료

**Rollback**: `git revert` to Phase 4 commit

---

### Phase 6: Feature - Analysis & AI Module (2-3 hours)

**Goal**: 분석 및 AI 기능을 클린 아키텍처로 전환

**Current Structure**:
```
ui/screens/advanced/
ui/screens/aianalysis/
ai/
analysis/
repository/AIAnalysisRepository.kt
repository/CorrelationAnalysisRepository.kt
repository/AdvancedAnalysisRepository.kt
```

**Target Structure**:
```
feature/analysis/
├── data/
│   ├── datasource/
│   │   ├── ai/
│   │   │   ├── AIRemoteDataSource.kt
│   │   │   ├── ClaudeDataSource.kt
│   │   │   └── GeminiDataSource.kt
│   │   └── analysis/
│   │       └── AnalysisLocalDataSource.kt
│   └── repository/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
└── presentation/
    ├── advanced/
    └── aianalysis/
```

**Tasks**:
- [ ] `feature/analysis/` 디렉토리 구조 생성
- [ ] Domain Layer 생성
- [ ] Data Layer 생성 (AI 클라이언트 포함)
- [ ] Presentation Layer 이동
- [ ] DI 설정
- [ ] 빌드 및 테스트 확인

**Quality Gate**:
- [ ] `./gradlew assembleDebug` 성공
- [ ] Advanced Dashboard 화면 정상 동작
- [ ] AI Analysis 화면 정상 동작
- [ ] AI Chat 기능 정상 동작

**Dependencies**: Phase 5 완료

**Rollback**: `git revert` to Phase 5 commit

---

### Phase 7: Feature - Settings & Final Cleanup (2-3 hours)

**Goal**: Settings 기능 전환 및 최종 정리

**Current Structure**:
```
ui/screens/settings/
ui/screens/hub/
worker/
service/
```

**Target Structure**:
```
feature/settings/
├── data/
├── domain/
└── presentation/

core/worker/
core/service/
navigation/
```

**Tasks**:
- [ ] `feature/settings/` 디렉토리 구조 생성
- [ ] Settings 기능 클린 아키텍처로 전환
- [ ] Hub 화면들 정리 및 이동
- [ ] Worker 및 Service를 `core/` 또는 적절한 feature로 이동
- [ ] Navigation 모듈 분리
- [ ] 사용하지 않는 파일 정리
- [ ] 최종 빌드 및 테스트
- [ ] 문서 업데이트 (CLAUDE.md)

**Quality Gate**:
- [ ] `./gradlew assembleDebug` 성공
- [ ] `./gradlew lint` 통과
- [ ] 모든 화면 정상 동작
- [ ] 모든 워커 정상 동작
- [ ] 앱 전체 기능 테스트 통과

**Dependencies**: Phase 6 완료

**Rollback**: `git revert` to Phase 6 commit

---

## 4. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Import 경로 충돌 | High | Medium | IDE 리팩토링 도구 활용, 단계별 검증 |
| DI 설정 오류 | Medium | High | 각 Phase별 빌드 테스트 |
| 기능 회귀 | Medium | High | 각 화면별 수동 테스트 |
| 빌드 시간 증가 | Low | Low | Gradle 캐시 활용 |
| Git 충돌 | Low | Medium | 단일 브랜치에서 작업 |

---

## 5. Progress Tracking

| Phase | Status | Start Date | End Date | Notes |
|-------|--------|------------|----------|-------|
| Phase 1: Core Module | ⬜ Pending | - | - | - |
| Phase 2: Home Module | ⬜ Pending | - | - | - |
| Phase 3: ETF Module | ⬜ Pending | - | - | - |
| Phase 4: Stock Module | ⬜ Pending | - | - | - |
| Phase 5: Market Module | ⬜ Pending | - | - | - |
| Phase 6: Analysis Module | ⬜ Pending | - | - | - |
| Phase 7: Settings & Cleanup | ⬜ Pending | - | - | - |

---

## 6. Notes & Learnings

_이 섹션은 각 Phase 완료 후 업데이트됩니다._

---

## 7. Approval

**이 계획을 검토하고 승인해 주세요.**

질문사항:
1. 이 Phase 구분이 적절한가요?
2. 각 Phase의 범위가 적당한가요?
3. 추가로 고려해야 할 사항이 있나요?
4. 진행을 시작해도 될까요?
