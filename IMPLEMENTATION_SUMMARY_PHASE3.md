# Phase 3 Implementation Summary
ETF Monitor - AI Signal Generation (Claude API Integration)

## 구현 개요
Statistical_Analysis.md에서 정의한 **Phase 3 (AI 신호 생성)**을 구현했습니다. Claude API를 활용하여 ETF 통계, 시장 지표를 분석하고 매수/매도 신호를 생성하는 시스템입니다.

## ✅ Phase 3: AI 신호 생성 (완료)

### 3.1 Claude API 통합 ✅

**생성된 파일**:
- `ai/MarketSignal.kt` - AI 분석 결과 데이터 모델
- `ai/MarketAnalysisPrompts.kt` - 프롬프트 엔지니어링
- `ai/ClaudeApiClient.kt` - Claude API 통신 클라이언트
- `repository/AIAnalysisRepository.kt` - AI 분석 비즈니스 로직
- `analysis/Backtester.kt` - 신호 백테스팅 엔진
- `di/AIModule.kt` - AI 컴포넌트 DI 설정

### 3.2 핵심 기능

#### 1) MarketSignal - AI 분석 결과
```kotlin
data class MarketSignal(
    val market: String,           // "KOSPI" or "KOSDAQ"
    val date: String,             // "2025-01-01"
    val signal: SignalType,       // STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL
    val confidence: Double,       // 신뢰도 (0.0 ~ 1.0)
    val upProbability: Double,    // 상승 확률 (%)
    val downProbability: Double,  // 하락 확률 (%)
    val reasoning: String,        // AI의 분석 이유
    val keyFactors: List<String>, // 주요 영향 요인
    val recommendation: String,   // 투자 권장사항
    val riskLevel: RiskLevel      // LOW, MEDIUM, HIGH
)
```

#### 2) MarketAnalysisData - AI 입력 데이터
모든 시장 데이터를 통합하여 Claude에 전달:
- **ETF 통계**: 신규/제외/증가/감소 종목 수 및 금액
- **원화예금**: 변화액 및 변화율
- **증시 자금 동향**: 고객예탁금 변화
- **Fear & Greed Index**: 시장 심리 지표
- **과매수/과매도**: 기술적 지표
- **상관관계 데이터**: 각 지표와 지수의 상관계수

#### 3) 프롬프트 엔지니어링
```kotlin
// 종합 분석 프롬프트
MarketAnalysisPrompts.createComprehensiveAnalysisPrompt(data)

// ETF 중심 분석
MarketAnalysisPrompts.createEtfFocusedAnalysisPrompt(data)

// 빠른 신호 생성
MarketAnalysisPrompts.createQuickSignalPrompt(data)

// 백테스트 결과 반영
MarketAnalysisPrompts.createBacktestEnhancedPrompt(data, backtestResult)
```

**프롬프트 특징**:
- 구조화된 데이터 제공 (지수, ETF 통계, 자금 동향 등)
- 한글 해석 포함 (Fear & Greed 수준, 과매수/과매도 상태)
- 상관관계 해석 자동 추가
- JSON 형식 응답 요청으로 파싱 용이
- 분석 시 고려사항 명시

#### 4) Claude API 클라이언트
```kotlin
// 시장 분석 수행
val result = claudeApiClient.analyzeMarket(prompt, temperature = 0.7)

// API 키 설정
apiKeyProvider.setApiKey("sk-ant-...")

// API 연결 테스트
val testResult = claudeApiClient.testApiKey()
```

**주요 기능**:
- OkHttp 기반 HTTP 통신
- 60초 타임아웃 설정
- JSON 응답 자동 파싱
- 응답에서 JSON 블록 추출 (```json ... ``` 또는 {...})
- 한글/영문 신호 타입 자동 변환
- SharedPreferences 기반 API 키 관리

**API 모델**: `claude-3-5-sonnet-20241022` (최신 Sonnet)

#### 5) AIAnalysisRepository
```kotlin
// 최신 시장 분석
val response = aiAnalysisRepository.analyzeLatestMarket("KOSPI")

// 특정 날짜 분석
val response = aiAnalysisRepository.analyzeMarket("KOSPI", "2025-01-01")

// 빠른 신호 생성
val signal = aiAnalysisRepository.generateQuickSignal("KOSPI", "2025-01-01")

// 배치 신호 생성 (백테스팅용)
val signals = aiAnalysisRepository.generateBatchSignals(
    market = "KOSPI",
    startDate = "2025-01-01",
    endDate = "2025-01-31"
)
```

**데이터 수집 로직**:
1. MarketIndex (지수 데이터)
2. DailyEtfStatistics (ETF 통계)
3. FearGreedIndex (선택)
4. MarketOscillatorData (선택)
5. MarketDeposit (선택)

모든 데이터를 `MarketAnalysisData`로 통합하여 Claude에 전달

### 3.3 백테스팅 시스템 ✅

#### Backtester - 신호 정확도 검증
```kotlin
// 신호 백테스트
val result = backtester.backtest(
    market = "KOSPI",
    signals = signalRecords,
    holdingPeriod = 5 // 5일 보유
)
```

**백테스트 결과**:
```kotlin
data class BacktestResult(
    val totalSignals: Int,        // 총 신호 수
    val correctSignals: Int,      // 정확한 신호 수
    val accuracy: Double,         // 정확도 (%)
    val averageReturn: Double,    // 평균 수익률 (%)
    val winRate: Double,          // 승률 (%)
    val maxDrawdown: Double,      // 최대 낙폭 (%)
    val sharpeRatio: Double?,     // 샤프 비율
    val period: String            // 분석 기간
)
```

**분석 기능**:
1. **미래 수익률 계산**: 신호 발생 후 1일/5일/10일 수익률
2. **신호 정확도 판단**:
   - 매수 신호 → 양의 수익률이면 정확
   - 매도 신호 → 음의 수익률이면 정확
   - 중립 신호 → ±1% 이내면 정확
3. **최대 낙폭 (MDD)**: 누적 수익률 기준 최대 손실폭
4. **샤프 비율**: 리스크 대비 수익률 (변동성 고려)

**추가 분석**:
```kotlin
// 신호 타입별 성과
val performance = backtester.analyzeBySignalType(signals)
// STRONG_BUY: 정확도 75%, 평균 수익률 3.2%
// BUY: 정확도 68%, 평균 수익률 1.8%
// ...

// 신뢰도별 성과
val confidenceAnalysis = backtester.analyzeByConfidence(signals)
// 0.9~1.0: 정확도 82%, 평균 수익률 2.5%
// 0.7~0.9: 정확도 71%, 평균 수익률 1.9%
// ...
```

### 3.4 Dependency Injection (Hilt)

**AIModule.kt** - 새로운 DI 모듈 생성:
```kotlin
@Provides @Singleton
fun provideApiKeyProvider(context: Context): ApiKeyProvider

@Provides @Singleton
fun provideClaudeApiClient(apiKeyProvider: ApiKeyProvider): ClaudeApiClient

@Provides @Singleton
fun provideAIAnalysisRepository(...): AIAnalysisRepository

@Provides @Singleton
fun provideBacktester(marketIndexDao: MarketIndexDao): Backtester
```

모든 AI 컴포넌트가 Singleton으로 관리됩니다.

## 📊 데이터 흐름

```
1. 데이터 수집
   ↓
   AIAnalysisRepository.collectAnalysisData()
   - MarketIndex (지수)
   - DailyEtfStatistics (ETF 통계)
   - FearGreedIndex (심리 지표)
   - MarketOscillatorData (기술 지표)
   - MarketDeposit (자금 동향)
   ↓
   MarketAnalysisData 통합

2. 프롬프트 생성
   ↓
   MarketAnalysisPrompts.createComprehensiveAnalysisPrompt(data)
   - 구조화된 데이터 포맷
   - 해석 자동 추가
   - JSON 응답 요청
   ↓
   프롬프트 문자열

3. AI 분석
   ↓
   ClaudeApiClient.analyzeMarket(prompt)
   - Claude API 호출 (claude-3-5-sonnet-20241022)
   - JSON 응답 파싱
   ↓
   MarketSignal

4. 응답 포장
   ↓
   AIAnalysisResponse
   - signal: MarketSignal
   - alternativeScenarios: List<Scenario> (향후 구현)
   - historicalAccuracy: BacktestResult? (백테스트 결과)
   - processingTime: Long

5. (선택) 백테스팅
   ↓
   Backtester.backtest(signals, holdingPeriod)
   - 미래 수익률 계산
   - 정확도 검증
   - 성과 지표 산출
   ↓
   BacktestResult
```

## 🔧 사용 방법

### 1. API 키 설정
```kotlin
val apiKeyProvider = SharedPreferencesApiKeyProvider(context)
apiKeyProvider.setApiKey("sk-ant-api03-...")
```

### 2. 최신 시장 분석
```kotlin
val aiRepository = AIAnalysisRepository(...)

// 최신 데이터로 KOSPI 분석
val result = aiRepository.analyzeLatestMarket("KOSPI")

if (result.isSuccess) {
    val response = result.getOrThrow()
    val signal = response.signal

    println("신호: ${signal.signal.toKorean()} ${signal.signal.toEmoji()}")
    println("신뢰도: ${signal.confidence * 100}%")
    println("상승 확률: ${signal.upProbability}%")
    println("분석 이유: ${signal.reasoning}")
    println("주요 요인: ${signal.keyFactors.joinToString(", ")}")
    println("권장사항: ${signal.recommendation}")
    println("위험 수준: ${signal.riskLevel.toKorean()}")
} else {
    println("분석 실패: ${result.exceptionOrNull()?.message}")
}
```

### 3. 백테스팅
```kotlin
val backtester = Backtester(marketIndexDao)

// 배치 신호 생성
val signalsResult = aiRepository.generateBatchSignals(
    market = "KOSPI",
    startDate = "2025-01-01",
    endDate = "2025-01-31"
)

if (signalsResult.isSuccess) {
    val signals = signalsResult.getOrThrow()

    // 백테스트 수행
    val backtestResult = backtester.backtest("KOSPI", signals, holdingPeriod = 5)

    if (backtestResult.isSuccess) {
        val result = backtestResult.getOrThrow()
        println("총 신호: ${result.totalSignals}개")
        println("정확도: ${result.accuracy}%")
        println("평균 수익률: ${result.averageReturn}%")
        println("승률: ${result.winRate}%")
        println("최대 낙폭: ${result.maxDrawdown}%")
        println("샤프 비율: ${result.sharpeRatio}")
    }
}
```

### 4. 신호 타입별 성과 분석
```kotlin
val performance = backtester.analyzeBySignalType(signals)

performance.forEach { (signalType, perf) ->
    println("${signalType.toKorean()}: " +
            "정확도 ${perf.accuracy}%, " +
            "평균 수익률 ${perf.averageReturn}%, " +
            "신호 수 ${perf.count}개")
}
```

## 💰 비용 추정

**Claude API 비용** (claude-3-5-sonnet-20241022):
- 입력: $3.00 / 1M tokens
- 출력: $15.00 / 1M tokens

**예상 사용량**:
- 1회 분석당 입력: ~1,500 tokens (프롬프트)
- 1회 분석당 출력: ~500 tokens (JSON 응답)
- 1회 비용: $0.0045 + $0.0075 = **$0.012**

**월 사용 예시**:
- 일 1회 분석 (30일): $0.36
- 일 5회 분석 (30일): $1.80
- 일 10회 분석 (30일): $3.60
- 백테스팅 (100회): $1.20

**실제 비용은 프롬프트 길이와 응답 길이에 따라 변동**

## 🎯 주요 특징

### 1. 종합 분석
- ETF 편입/편출 패턴
- 원화예금 변화 추이
- 증시 자금 동향
- Fear & Greed Index
- 과매수/과매도 지표
- 상관관계 데이터

모든 데이터를 통합하여 Claude가 종합적으로 판단

### 2. 프롬프트 엔지니어링
- 구조화된 데이터 포맷
- 자동 해석 추가 (Fear & Greed 수준, Oscillator 상태)
- 상관관계 강도 해석
- 분석 시 고려사항 명시
- JSON 형식 응답 요청

### 3. 백테스팅
- 과거 신호의 정확도 검증
- 실제 수익률 계산
- 리스크 지표 (MDD, Sharpe Ratio)
- 신호 타입별/신뢰도별 성과 분석

### 4. 확장성
- 다양한 분석 타입 지원 (종합/ETF만/기술적/심리적)
- 배치 신호 생성 (백테스팅용)
- Rate limiting (1초당 1 요청)
- API 키 관리 시스템

## 📝 설정 가이드

### Claude API 키 발급
1. https://console.anthropic.com/ 접속
2. 계정 생성 및 로그인
3. API Keys 메뉴에서 새 키 생성
4. `sk-ant-api03-...` 형식의 키 복사

### 앱에서 API 키 설정
```kotlin
// Settings에서 API 키 저장
val apiKeyProvider = SharedPreferencesApiKeyProvider(context)
apiKeyProvider.setApiKey("sk-ant-api03-your-key-here")

// API 연결 테스트
val aiRepository = AIAnalysisRepository(...)
val testResult = aiRepository.testApiConnection()

if (testResult.isSuccess) {
    println("API 키 유효 ✅")
} else {
    println("API 키 오류: ${testResult.exceptionOrNull()?.message}")
}
```

## ⚠️ 주의사항

### 1. API 키 보안
- API 키는 SharedPreferences에 암호화 없이 저장됩니다
- 프로덕션 환경에서는 EncryptedSharedPreferences 사용 권장
- API 키를 코드에 하드코딩하지 마세요
- .gitignore에 API 키 파일 추가

### 2. Rate Limiting
- Claude API는 rate limiting이 있습니다
- 배치 신호 생성 시 1초당 1 요청으로 제한
- 대량 분석 시 API 제한 고려

### 3. 비용 관리
- API 호출은 유료입니다
- 불필요한 반복 호출 방지
- 결과 캐싱 고려

### 4. 응답 파싱
- Claude 응답이 항상 올바른 JSON은 아닙니다
- 파싱 실패 시 기본 중립 신호 반환
- 로그 확인하여 응답 형식 검증

### 5. 백테스팅 제약
- 미래 데이터가 충분해야 백테스트 가능
- 최소 10개 이상의 신호 필요
- 거래일 기준으로 계산 (주말/공휴일 제외)

### 6. 투자 면책
**이 AI 분석은 참고용이며, 투자 결정은 개인의 판단과 책임입니다.**
- AI 신호가 항상 정확하지는 않습니다
- 과거 성과는 미래 수익을 보장하지 않습니다
- 리스크를 충분히 이해하고 투자하세요

## 🔗 관련 파일

### 새로 생성된 파일 (7개)
```
ai/MarketSignal.kt              - AI 분석 결과 데이터 모델
ai/MarketAnalysisPrompts.kt     - 프롬프트 엔지니어링
ai/ClaudeApiClient.kt           - Claude API 통신
repository/AIAnalysisRepository.kt - AI 분석 비즈니스 로직
analysis/Backtester.kt          - 신호 백테스팅
di/AIModule.kt                  - AI 컴포넌트 DI 설정
IMPLEMENTATION_SUMMARY_PHASE3.md - 본 문서
```

### Dependencies 추가 필요
```gradle
// build.gradle.kts (app)
dependencies {
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON serialization (이미 있을 수 있음)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

## ✅ 완료 상태

- [✅] Phase 1: 기초 인프라 구축 (100%)
- [✅] Phase 2: 기본 통계 분석 (100%)
- [✅] Phase 3: AI 신호 생성 (100% - Backend)
- [❌] Phase 3: UI 구현 (0%)
- [❌] Phase 4: 고급 분석 (0%)

**작업 시간**: 약 3시간 (Backend 완료)
**다음 단계**: AIAnalysisScreen UI 구현 (예상 4시간)

## 🚀 다음 단계 (UI 구현)

Phase 3 백엔드는 완성되었습니다. 사용자 인터페이스 구현이 필요합니다:

1. **AIAnalysisScreen.kt** - AI 분석 결과 화면
2. **AIAnalysisViewModel.kt** - 화면 상태 관리
3. **BacktestResultScreen.kt** - 백테스트 결과 화면
4. **Settings 화면에 API 키 설정 추가**
5. **Navigation 업데이트**

---

작성일: 2025-11-25
작성자: Claude Code AI Assistant
버전: Phase 3 Backend Complete
