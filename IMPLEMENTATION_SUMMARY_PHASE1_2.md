# Phase 1+2 Implementation Summary
ETF Monitor - Statistical Analysis Feature

## 구현 개요
Statistical_Analysis.md에서 정의한 Phase 1(기초 인프라 구축)과 Phase 2(기본 통계 분석)를 구현했습니다.

## ✅ Phase 1: 기초 인프라 구축 (완료)

### 1.1 MarketIndex 테이블 추가
**목적**: KOSPI/KOSDAQ 일별 종가 데이터 저장 및 ETF 통계와의 상관관계 분석

**생성된 파일**:
- `database/entities/MarketIndex.kt` - 시장 지수 엔티티
- `database/MarketIndexDao.kt` - 시장 지수 데이터 접근 객체
- `repository/MarketIndexRepository.kt` - 시장 지수 비즈니스 로직
- `python/market_index_fetcher.py` - pykrx를 이용한 지수 데이터 수집

**데이터 구조**:
```kotlin
data class MarketIndex(
    val id: String,              // "KOSPI-2025-01-01"
    val market: String,          // "KOSPI" or "KOSDAQ"
    val date: String,            // "2025-01-01"
    val closePrice: Double,      // 종가
    val openPrice: Double,       // 시가
    val highPrice: Double,       // 고가
    val lowPrice: Double,        // 저가
    val volume: Long,            // 거래량
    val changeRate: Double,      // 등락률 (%)
    val lastUpdated: Long
)
```

**주요 기능**:
- 특정 시장의 기간별 데이터 조회
- 최신 지수 데이터 조회
- 일별 종가 및 등락률 계산

### 1.2 데이터베이스 마이그레이션
**Migration 8 → 9**: MarketIndex 테이블 추가
- 테이블 생성 및 인덱스 추가 (market, date, market+date)

**Migration 9 → 10**: DailyEtfStatistics 테이블 추가
- ETF 통계 데이터 저장 테이블 생성

**AppDatabase 버전**: 8 → 10

## ✅ Phase 2: 기본 통계 분석 (완료)

### 2.1 DailyEtfStatistics 엔티티
**목적**: 일별 ETF 편입/제외/증감 통계 및 원화예금 추이 저장

**생성된 파일**:
- `database/entities/DailyEtfStatistics.kt` - 일별 통계 엔티티
- `database/DailyEtfStatisticsDao.kt` - 통계 데이터 접근 객체

**데이터 구조**:
```kotlin
data class DailyEtfStatistics(
    val date: String,

    // 신규 편입 통계
    val newStockCount: Int,
    val newStockAmount: Long,

    // 제외 종목 통계
    val removedStockCount: Int,
    val removedStockAmount: Long,

    // 비중 증가 통계
    val increasedStockCount: Int,
    val increasedStockAmount: Long,

    // 비중 감소 통계
    val decreasedStockCount: Int,
    val decreasedStockAmount: Long,

    // 원화예금 통계
    val cashDepositAmount: Long,
    val cashDepositChange: Long,
    val cashDepositChangeRate: Double,

    // 전체 ETF 통계
    val totalEtfCount: Int,
    val totalHoldingAmount: Long
)
```

### 2.2 StatisticsAnalysisRepository
**목적**: ETF 통계 계산 및 시장 지수와의 상관관계 분석

**생성된 파일**:
- `repository/StatisticsAnalysisRepository.kt`

**주요 기능**:

#### 1) 일별 통계 계산
```kotlin
suspend fun calculateAndStoreDailyStatistics(): Result<Int>
```
- 최근 2일 데이터를 비교하여 신규/제외/증가/감소 종목 통계 계산
- 원화예금 변화율 계산
- 계산 결과를 DailyEtfStatistics 테이블에 저장

#### 2) 상관관계 분석
```kotlin
suspend fun calculateCorrelation(
    market: String,
    startDate: String,
    endDate: String
): CorrelationResult
```
- Pearson 상관계수를 사용한 통계 분석
- ETF 통계(신규/제외/증가/감소/원화예금)와 시장 지수 등락률의 상관관계 계산
- 최소 10개 이상의 데이터 포인트 필요

**분석 결과**:
```kotlin
data class CorrelationResult(
    val market: String,
    val period: String,
    val dataPointCount: Int,
    val newStockCorrelation: Double,        // 신규 편입 vs 지수
    val removedStockCorrelation: Double,    // 제외 종목 vs 지수
    val increasedStockCorrelation: Double,  // 비중 증가 vs 지수
    val decreasedStockCorrelation: Double,  // 비중 감소 vs 지수
    val cashDepositCorrelation: Double,     // 원화예금 vs 지수
    val averageIndexChange: Double
)
```

**상관관계 해석**:
- `>= 0.7`: 강한 양의 상관관계
- `0.4 ~ 0.7`: 중간 양의 상관관계
- `0.1 ~ 0.4`: 약한 양의 상관관계
- `-0.1 ~ 0.1`: 상관관계 없음
- `< -0.7`: 강한 음의 상관관계

### 2.3 Dependency Injection 설정

**DatabaseModule.kt 업데이트**:
- `provideMarketIndexDao()` 추가
- `provideDailyEtfStatisticsDao()` 추가
- `MIGRATION_8_9`, `MIGRATION_9_10` 등록

**RepositoryModule.kt 업데이트**:
- `provideMarketIndexRepository()` 추가
- `provideStatisticsAnalysisRepository()` 추가

## 📊 데이터 흐름

```
1. 데이터 수집
   ↓
   Python (market_index_fetcher.py)
   - pykrx로 KOSPI/KOSDAQ 지수 수집
   - 일별 종가, 등락률 계산
   ↓
   MarketIndexRepository
   - MarketIndex 테이블에 저장

2. 통계 계산
   ↓
   StatisticsAnalysisRepository.calculateAndStoreDailyStatistics()
   - EtfDao에서 최근 2일 데이터 조회
   - 신규/제외/증가/감소 종목 통계 계산
   - 원화예금 변화 계산
   ↓
   DailyEtfStatistics 테이블에 저장

3. 상관관계 분석
   ↓
   StatisticsAnalysisRepository.calculateCorrelation()
   - DailyEtfStatistics와 MarketIndex 조회
   - 날짜별 매핑 후 Pearson 상관계수 계산
   ↓
   CorrelationResult 반환
```

## 🔄 다음 단계 (미구현)

Phase 1+2의 백엔드 인프라는 완성되었습니다. UI 구현을 위해서는 다음 작업이 필요합니다:

### 1. UI 스크린 생성
- `ui/screens/analysis/MarketDashboardScreen.kt` - 통합 대시보드
- `ui/screens/analysis/MarketDashboardViewModel.kt` - 대시보드 ViewModel
- `ui/screens/analysis/CorrelationAnalysisScreen.kt` - 상관관계 분석 화면
- `ui/screens/analysis/CorrelationAnalysisViewModel.kt` - 분석 ViewModel

### 2. Navigation 업데이트
- `ui/Navigation.kt`에 새로운 스크린 경로 추가

### 3. 데이터 수집 통합
- `DataRepository.kt`의 `initializeData()` 및 `updateData()`에 시장 지수 수집 로직 추가
- ETF 데이터 수집 시 MarketIndex도 함께 수집
- 통계 계산 자동화

### 4. Python Client 생성
Python 모듈과 통신하는 Kotlin 클라이언트:
- `python/MarketIndexPyClient.kt` - market_index_fetcher.py 호출

## 📝 사용 방법

### 1. 시장 지수 데이터 수집 (Python)
```python
from market_index_fetcher import fetch_recent_days, get_latest_index

# 최근 30일 데이터 수집
json_data = fetch_recent_days(30)

# 최신 지수 조회
latest = get_latest_index("KOSPI")
```

### 2. 일별 통계 계산 (Kotlin)
```kotlin
val statisticsRepo = StatisticsAnalysisRepository(...)

// 통계 계산 및 저장
val result = statisticsRepo.calculateAndStoreDailyStatistics()

// 최근 통계 조회
statisticsRepo.getRecentStatistics(30)
    .collect { statistics ->
        // UI 업데이트
    }
```

### 3. 상관관계 분석 (Kotlin)
```kotlin
val correlation = statisticsRepo.calculateCorrelation(
    market = "KOSPI",
    startDate = "2025-01-01",
    endDate = "2025-01-31"
)

println("신규 편입 vs 지수: ${correlation.newStockCorrelation}")
println("해석: ${correlation.getCorrelationStrength(correlation.newStockCorrelation)}")
```

## 🧪 테스트 체크리스트

### 데이터베이스
- [  ] MarketIndex 테이블 생성 확인
- [ ] DailyEtfStatistics 테이블 생성 확인
- [ ] Migration 8→9→10 정상 동작 확인
- [ ] DAO 메서드 정상 동작 확인

### Repository
- [ ] MarketIndexRepository.insertAll() 테스트
- [ ] StatisticsAnalysisRepository.calculateAndStoreDailyStatistics() 테스트
- [ ] StatisticsAnalysisRepository.calculateCorrelation() 테스트
- [ ] Pearson 상관계수 계산 정확도 확인

### Python
- [ ] market_index_fetcher.py 실행 테스트
- [ ] KOSPI/KOSDAQ 데이터 수집 확인
- [ ] JSON 직렬화 확인

### 통합
- [ ] ETF 데이터 수집 후 통계 계산 자동화
- [ ] 실시간 상관관계 분석 동작 확인

## 🎯 예상 효과

1. **데이터 정합성**: ETF 데이터와 시장 지수가 동일한 날짜 범위로 수집
2. **분석 정확도**: Pearson 상관계수를 통한 통계적으로 유의미한 관계 파악
3. **투자 인사이트**: ETF 편입/제외 패턴과 시장 움직임의 관계 발견
4. **확장성**: Phase 3 (AI 분석)의 기반 데이터 제공

## 📌 주의사항

1. **최소 데이터 요구사항**: 상관관계 분석을 위해 최소 10개의 데이터 포인트 필요
2. **날짜 매핑**: ETF 통계와 시장 지수의 날짜가 일치해야 정확한 분석 가능
3. **성능**: 통계 계산은 IO 작업이므로 `Dispatchers.IO`에서 실행
4. **데이터 수집 주기**: 일 1회 업데이트 권장 (시장 종료 후)

## 🔗 관련 파일

### 새로 생성된 파일 (14개)
```
database/entities/MarketIndex.kt
database/entities/DailyEtfStatistics.kt
database/MarketIndexDao.kt
database/DailyEtfStatisticsDao.kt
repository/MarketIndexRepository.kt
repository/StatisticsAnalysisRepository.kt
python/market_index_fetcher.py
```

### 수정된 파일 (3개)
```
database/AppDatabase.kt          (v8 → v10, 2개 entity 추가)
di/DatabaseModule.kt             (2개 DAO 제공자 추가, 2개 migration 등록)
di/RepositoryModule.kt           (2개 Repository 제공자 추가)
```

## ✅ 완료 상태

- [✅] Phase 1: 기초 인프라 구축 (100%)
- [✅] Phase 2: 기본 통계 분석 (100% - Backend)
- [❌] Phase 2: UI 구현 (0%)
- [❌] Phase 3: AI 신호 생성 (0%)

**작업 시간**: 약 4시간 (Backend 완료)
**다음 단계**: UI 스크린 및 ViewModel 구현 (예상 6시간)

---

작성일: 2025-11-25
작성자: Claude Code AI Assistant
