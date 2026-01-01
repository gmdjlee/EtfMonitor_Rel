# ETF Monitor Backup Data Generator

PC 환경에서 ETF Monitor 앱의 백업 데이터를 생성하는 도구입니다.

## 기능

- **시장 지수 데이터** (2000~2025): KOSPI/KOSDAQ 일별 OHLCV
- **Blood Indicator** (2007~2025): 미국 T-Bill 및 High Yield Spread 기반 Risk On/Off 지표
- **증시 자금 데이터** (2020~2025): 고객예탁금 및 신용잔고
- **Fear & Greed 지수** (2020~2025): 5개 지표 기반 공포/탐욕 지수
- **ETF 보유 현황** (2022~2025): 액티브 ETF 목록 및 포트폴리오 구성
- **종목 마스터**: KOSPI/KOSDAQ 전체 종목 정보

## 설치

```bash
cd tools/backup_generator
pip install -r requirements.txt
```

## 환경 설정

### FRED API 키 (Blood Indicator용)

Blood Indicator 수집을 위해 FRED API 키가 필요합니다:

1. https://fred.stlouisfed.org/docs/api/api_key.html 에서 무료 API 키 발급
2. 환경 변수 설정:
   ```bash
   export FRED_API_KEY="your_api_key_here"
   ```

## 사용법

### 전체 데이터 수집

```bash
python main.py
```

### 특정 데이터만 수집

```bash
# 시장 지수만
python main.py --market-index

# Blood Indicator만
python main.py --blood-indicator

# 증시 자금만
python main.py --market-deposit

# Fear & Greed만
python main.py --fear-greed

# ETF 보유현황만
python main.py --etf-holdings

# 종목 마스터만
python main.py --stocks
```

### 중단된 수집 재개

```bash
python main.py --resume
```

### 옵션

```
--all, -a           모든 collector 실행 (기본값)
--resume, -r        체크포인트에서 재개
--no-backup         데이터만 수집, 백업 파일 생성하지 않음
--force, -f         일부 collector 실패해도 백업 파일 생성
--output, -o        출력 파일명 지정 (확장자 제외)
--no-compress       GZIP 압축 비활성화
--verbose, -v       상세 로그 출력
```

## 출력 파일

### 백업 파일

- 위치: `output/etfmonitor_backup_YYYYMMDD_HHMMSS.etfbackup.gz`
- 형식: GZIP 압축된 JSON
- 앱의 "설정 > 백업 > 외부 백업 파일 복구"에서 사용 가능

### 체크포인트

- 위치: `checkpoints/`
- 수집이 중단된 경우 진행 상황 저장
- `--resume` 옵션으로 재개 가능

### 로그

- 위치: `logs/backup_YYYYMMDD_HHMMSS.log`

## 예상 소요 시간

| 데이터 | 예상 시간 | 비고 |
|--------|----------|------|
| 시장 지수 | ~10분 | 26년간 데이터 |
| Blood Indicator | ~5분 | FRED API 제한 |
| 증시 자금 | ~30분 | Naver 스크래핑 |
| Fear & Greed | ~1시간 | KRX API |
| ETF 보유현황 | ~10시간 | 가장 오래 걸림 |
| 종목 마스터 | ~1분 | 단일 요청 |
| **전체** | **~12시간** | |

## Rate Limiting

API 제한을 피하기 위해 각 데이터 소스별 딜레이가 적용됩니다:

- pykrx: 0.5초/요청
- Naver Finance: 0.5초/페이지
- FRED API: 0.3초/요청
- ETF Holdings: 2초/ETF (더 보수적)

## 설정 커스터마이징

`config.py` 파일에서 다음을 수정할 수 있습니다:

- 날짜 범위
- Rate limiting 설정
- ETF 필터링 키워드
- 출력 디렉토리

## 문제 해결

### FRED API 오류

```
Error: FRED_API_KEY not configured
```

→ FRED API 키를 환경 변수로 설정하세요.

### 수집 중단됨

```
Interrupted. Progress has been saved to checkpoints.
```

→ `python main.py --resume`로 재개하세요.

### 메모리 부족

ETF Holdings 수집 중 메모리 부족 시:
- `config.py`에서 `etf_holdings_batch_days`를 줄이세요
- 또는 날짜 범위를 나누어 수집하세요

## 앱에서 복구

1. 생성된 `.etfbackup.gz` 파일을 Android 기기로 전송
2. 앱 실행 → 설정 → 백업 탭
3. "외부 백업 파일 복구" 선택
4. 파일 선택 후 복구

## 라이선스

ETF Monitor 프로젝트의 일부입니다.
