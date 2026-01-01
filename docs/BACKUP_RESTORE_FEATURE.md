# 백업 및 복구 기능 개발 명세서

## 문서 정보
- **버전**: 1.0
- **작성일**: 2026-01-01
- **대상 시스템**: EtfMonitor Android App
- **기능**: 데이터 백업/복구, 선택적 복원, 병합 모드

---

## 1. 기능 개요

### 1.1 목적
앱 데이터의 안전한 백업 및 복구 기능을 제공하여 데이터 손실 방지 및 기기 간 데이터 이동을 지원합니다.

### 1.2 주요 기능
| 기능 | 설명 |
|------|------|
| **전체/선택적 백업** | 22개 엔티티 중 원하는 항목만 선택하여 백업 |
| **날짜 범위 필터링** | 시계열 데이터의 특정 기간만 백업 |
| **병합 모드 복구** | 기존 데이터 유지, 누락된 데이터만 추가 |
| **압축 지원** | GZIP 압축으로 백업 파일 크기 최소화 |
| **파일 내보내기/가져오기** | 외부 저장소로 백업 파일 관리 |

---

## 2. 아키텍처

### 2.1 모듈 구조

```
feature/backup/
├── di/
│   └── BackupModule.kt              # Hilt DI 모듈
├── domain/
│   ├── model/
│   │   └── BackupModels.kt          # 도메인 모델
│   └── repository/
│       └── BackupRepository.kt      # Repository 인터페이스
├── data/
│   ├── dto/
│   │   └── BackupDtos.kt            # 22개 엔티티 DTO
│   └── repository/
│       └── BackupRepositoryImpl.kt  # Repository 구현
└── presentation/
    ├── state/
    │   └── BackupState.kt           # UI 상태
    ├── viewmodel/
    │   └── BackupViewModel.kt       # ViewModel
    └── screen/
        └── BackupScreen.kt          # UI 컴포저블

core/database/
└── BackupDao.kt                     # 백업 전용 DAO
```

### 2.2 데이터 흐름

```
┌─────────────────────────────────────────────────────────────┐
│                      BackupScreen                           │
│  UI 상태 표시 / 사용자 입력 처리                              │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    BackupViewModel                          │
│  상태 관리 / 진행 상태 추적 / 에러 핸들링                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  BackupRepositoryImpl                       │
│  백업 생성 / 복구 로직 / 파일 I/O / 압축                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                      BackupDao                              │
│  22개 엔티티 조회 / INSERT IGNORE 삽입                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 엔티티 분류

### 3.1 카테고리별 엔티티

| 카테고리 | 엔티티 | 설명 |
|----------|--------|------|
| **MASTER** | Etf, Stock, Setting | 기준 데이터, 날짜 필터 없음 |
| **TIME_SERIES** | Holding, StockAnalysisData, MarketDeposit, FearGreedIndex, MarketOscillatorData, MarketIndex, DailyEtfStatistics, PriceCache, EnhancedPrediction | 시계열 데이터, 날짜 범위 필터 지원 |
| **ANALYSIS** | CorrelationAnalysisResult, AIAnalysisResult, SectorAnalysis, EtfCorrelationCache, LiquidityAnalysis, StockIndicatorAIResult | 분석 결과 |
| **USER_DATA** | SearchHistory, AIChatSession, AIChatMessage, StockPrediction | 사용자 생성 데이터 |

### 3.2 엔티티별 키 구조 (병합 모드용)

```kotlin
// 각 엔티티의 고유 키 조합
Etf                    → ticker
Stock                  → ticker
Holding                → (etfTicker, stockTicker, date)
StockAnalysisData      → ticker
MarketDeposit          → date
FearGreedIndex         → id (형식: "KOSPI-2024-01-01")
MarketOscillatorData   → id (형식: "KOSPI-2025-01-01")
MarketIndex            → id (형식: "KOSPI-2025-01-01")
Setting                → key
SearchHistory          → id
...
```

---

## 4. 핵심 로직

### 4.1 백업 생성 프로세스

```kotlin
fun createBackup(options: BackupOptions): Flow<BackupProgress> = flow {
    emit(BackupProgress.Starting(totalEntities))

    // 1. 선택된 엔티티별 데이터 수집
    options.entities.forEach { entityType ->
        val data = collectEntityData(entityType, options.startDate, options.endDate)
        emit(BackupProgress.Processing(entityType, progress, processed, total))
    }

    // 2. JSON 직렬화
    val json = Json.encodeToString(backupData)

    // 3. 압축 (옵션, 1MB 이상 권장)
    val content = if (options.compress && json.length > 1_000_000) {
        gzipCompress(json)
    } else {
        json.toByteArray()
    }

    // 4. 파일 저장
    saveToInternalStorage(backupId, content, isCompressed)

    emit(BackupProgress.Completed(backupInfo))
}
```

### 4.2 병합 모드 복구 프로세스

```kotlin
private suspend fun restoreEntitiesWithMerge(data: BackupData): ImportResult {
    var inserted = 0
    var skipped = 0

    // ETF 복구 예시
    data.etfs?.let { etfs ->
        // 1. 기존 키 조회
        val existingKeys = backupDao.getAllEtfKeys().toSet()

        // 2. 신규 항목만 필터링
        val newItems = etfs.filter { it.ticker !in existingKeys }

        // 3. INSERT IGNORE로 삽입 (배치 처리)
        newItems.chunked(BATCH_SIZE).forEach { batch ->
            val entities = batch.map { it.toEntity() }
            val results = backupDao.insertEtfsIgnore(entities)
            inserted += results.count { it != -1L }
            skipped += results.count { it == -1L }
        }
    }

    return ImportResult(inserted, skipped, 0, emptyList())
}
```

### 4.3 INSERT IGNORE 전략

Room에서 `OnConflictStrategy.IGNORE`를 사용하여 중복 키 충돌 시 삽입을 건너뜁니다:

```kotlin
@Dao
interface BackupDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEtfsIgnore(etfs: List<Etf>): List<Long>
    // 반환값: 삽입 성공 시 rowId, 충돌로 건너뛴 경우 -1
}
```

---

## 5. 백업 파일 형식

### 5.1 파일 구조

```
backups/
├── {uuid}.etfbackup       # 비압축 백업
└── {uuid}.etfbackup.gz    # GZIP 압축 백업
```

### 5.2 JSON 스키마

```json
{
  "metadata": {
    "appVersion": "1.0.0",
    "schemaVersion": 19,
    "createdAt": "2026-01-01T12:00:00",
    "entityCounts": {
      "ETF": 50,
      "HOLDING": 10000,
      "STOCK": 500,
      ...
    },
    "dateRange": {
      "startDate": "2024-01-01",
      "endDate": "2025-12-31"
    }
  },
  "etfs": [...],
  "holdings": [...],
  "stocks": [...],
  ...
}
```

### 5.3 DTO 변환 예시

Holding 엔티티는 압축 저장 방식을 사용하므로 DTO 변환 시 주의:

```kotlin
@Serializable
data class HoldingDto(
    val etfTicker: String,
    val stockTicker: String,
    val stockName: String,
    val date: String,
    val weight: Float,      // weightBps → Float 변환
    val amount: Float,      // amountMillion → Float 변환
    val snapshotType: String
) {
    fun toEntity() = Holding.create(
        etfTicker, stockTicker, stockName, date, weight, amount, snapshotType
    )

    companion object {
        fun fromEntity(entity: Holding) = HoldingDto(
            etfTicker = entity.etfTicker,
            stockTicker = entity.stockTicker,
            stockName = entity.stockName,
            date = entity.date,
            weight = entity.weight,        // 자동 변환 (weightBps / 10000f)
            amount = entity.amount,        // 자동 변환 (amountMillion * 1_000_000f)
            snapshotType = entity.snapshotType
        )
    }
}
```

---

## 6. UI 구성

### 6.1 화면 구조

```
BackupScreen
├── TopAppBar (백업 및 복구)
├── DatabaseStatusCard
│   ├── 총 레코드 수
│   ├── 예상 백업 크기
│   └── 데이터 기간
├── QuickActionsCard
│   └── 파일에서 복구 버튼
├── LocalBackupsList
│   └── BackupCard (각 백업)
│       ├── 생성일시
│       ├── 스키마 버전
│       ├── 파일 크기
│       └── 메뉴 (복구, 내보내기, 삭제, Google Drive 업로드)
└── FAB (백업 생성)
```

### 6.2 다이얼로그

| 다이얼로그 | 용도 |
|-----------|------|
| CreateBackupConfigDialog | 백업 옵션 설정 (엔티티 선택, 압축, 날짜 범위) |
| RestoreConfigDialog | 복구 옵션 설정 (엔티티 선택) |
| ProgressDialog | 백업/복구 진행 상태 표시 |
| BackupDetailDialog | 백업 상세 정보 표시 |
| DeleteConfirmDialog | 삭제 확인 |

### 6.3 상태 관리

```kotlin
// 메인 화면 상태
sealed class BackupState {
    object Loading : BackupState()
    data class Idle(
        val localBackups: List<BackupInfo>,
        val entityCounts: Map<EntityType, Int>,
        val dateRange: DateRange?,
        val estimatedSize: Long
    ) : BackupState()
    data class Error(val message: String) : BackupState()
}

// 백업 생성 상태
sealed class CreateBackupState {
    object Hidden : CreateBackupState()
    data class Visible(options...) : CreateBackupState()
    data class InProgress(progress...) : CreateBackupState()
    data class Success(backupInfo) : CreateBackupState()
    data class Error(message) : CreateBackupState()
}
```

---

## 7. 설정 연동

### 7.1 진입점

설정 화면(SettingsScreen)의 일반 탭에서 백업 카드를 통해 접근:

```kotlin
// GeneralCards.kt
@Composable
fun BackupCard(onNavigateToBackup: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onNavigateToBackup)) {
        // 백업 및 복구 아이콘 + 설명
    }
}
```

### 7.2 네비게이션

```kotlin
// Navigation.kt
sealed class Screen(val route: String) {
    object Backup : Screen("backup")
}

// 라우트 등록
composable(Screen.Backup.route) {
    BackupScreen(onNavigateBack = { navController.popBackStack() })
}
```

---

## 8. 성능 최적화

### 8.1 배치 처리

대용량 데이터 삽입 시 1000개 단위로 배치 처리:

```kotlin
private const val BATCH_SIZE = 1000

items.chunked(BATCH_SIZE).forEach { batch ->
    backupDao.insertBatch(batch)
}
```

### 8.2 메모리 관리

- Flow를 사용한 스트리밍 처리로 메모리 효율 개선
- 대용량 JSON 파싱 시 순차적 처리

### 8.3 압축 임계값

1MB 이상의 백업 데이터는 GZIP 압축 권장:

```kotlin
val shouldCompress = options.compress && jsonContent.length > 1_000_000
```

---

## 9. 미구현 기능

### 9.1 Google Drive 연동 (예정)

현재 placeholder 상태로, 향후 구현 예정:

```kotlin
// 현재 상태: 에러 반환
override suspend fun uploadToGoogleDrive(backupId: String): Flow<BackupProgress> = flow {
    emit(BackupProgress.Failed(BackupError.GoogleDriveNotConfigured("Google Drive 연동이 구현되지 않았습니다")))
}
```

구현 시 필요 사항:
- Google Play Services 의존성 추가
- OAuth 2.0 인증 플로우
- Google Drive API v3 연동
- 파일 업로드/다운로드 구현

---

## 10. 사용 가이드

### 10.1 백업 생성

1. 설정 > 일반 > 백업 및 복구 진입
2. 우측 하단 FAB(+) 버튼 클릭
3. 백업할 데이터 선택
4. (선택) 날짜 범위 설정
5. (선택) 압축 옵션 설정
6. "백업 시작" 클릭

### 10.2 복구

1. 로컬 백업 목록에서 복구할 백업 선택
2. 메뉴에서 "복구" 선택
3. 복구할 데이터 선택
4. "복구 시작" 클릭
5. ※ 기존 데이터는 유지되며, 없는 데이터만 추가됨

### 10.3 외부 파일에서 복구

1. "파일에서 복구" 버튼 클릭
2. .etfbackup 또는 .etfbackup.gz 파일 선택
3. 파일 유효성 검증 후 복구 옵션 설정
4. "복구 시작" 클릭

### 10.4 백업 내보내기

1. 백업 목록에서 메뉴 > "내보내기" 선택
2. 저장 위치 및 파일명 지정
3. 외부 저장소에 백업 파일 저장

---

## 11. 파일 목록

### 11.1 생성된 파일

| 파일 | 설명 | 라인 수 |
|------|------|---------|
| `core/database/BackupDao.kt` | 백업 전용 DAO | ~500 |
| `feature/backup/domain/model/BackupModels.kt` | 도메인 모델 | ~150 |
| `feature/backup/domain/repository/BackupRepository.kt` | Repository 인터페이스 | ~50 |
| `feature/backup/data/dto/BackupDtos.kt` | 22개 엔티티 DTO | ~800 |
| `feature/backup/data/repository/BackupRepositoryImpl.kt` | Repository 구현 | ~600 |
| `feature/backup/di/BackupModule.kt` | Hilt DI 모듈 | ~20 |
| `feature/backup/presentation/state/BackupState.kt` | UI 상태 | ~80 |
| `feature/backup/presentation/viewmodel/BackupViewModel.kt` | ViewModel | ~300 |
| `feature/backup/presentation/screen/BackupScreen.kt` | UI 컴포저블 | ~700 |

### 11.2 수정된 파일

| 파일 | 변경 내용 |
|------|----------|
| `core/database/AppDatabase.kt` | `backupDao()` 추가 |
| `core/di/DatabaseModule.kt` | BackupDao Provider 추가 |
| `feature/settings/presentation/SettingsScreen.kt` | 백업 카드 연동 |
| `feature/settings/presentation/component/GeneralCards.kt` | BackupCard 추가 |
| `navigation/Navigation.kt` | Backup 라우트 추가 |
| `res/values/strings.xml` | 백업 관련 문자열 추가 |

---

## 12. 향후 개선 사항

1. **Google Drive 연동**: OAuth 인증 및 클라우드 백업/복구
2. **자동 백업**: WorkManager를 통한 주기적 자동 백업
3. **증분 백업**: 변경된 데이터만 백업하여 효율성 개선
4. **암호화**: 민감 데이터 보호를 위한 AES 암호화 지원
5. **백업 비교**: 현재 데이터와 백업 데이터 비교 기능
