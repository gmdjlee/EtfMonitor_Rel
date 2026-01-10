# EtfMonitor UI 최적화 가이드

## 프로젝트 구조

```
ui-optimization/
├── all-ui-code.kt          # 통합 UI 코드 (스크린 + 컴포넌트)
├── extract-ui.sh           # 추출 스크립트
├── UI_OPTIMIZATION_GUIDE.md # 이 파일
└── optimized/              # 최적화된 결과물
```

## 1단계: 분석 (Claude Code 사용)

### 명령어 예시
```
이 파일(all-ui-code.kt)을 분석해서:
1. 중복 코드 패턴 식별
2. 리컴포지션 최적화 기회 찾기
3. 성능 안티패턴 탐지
4. 공통 컴포넌트 추출 후보 제안
```

## 2단계: 최적화 영역

### A. 리컴포지션 최적화
- `remember` 누락된 계산
- `derivedStateOf` 미사용
- Lambda 안정성 문제
- List 아이템 key 누락

### B. 메모리 최적화
- 이미지 로딩 개선
- LazyColumn/LazyRow 최적화
- 상태 호이스팅 정리

### C. 코드 중복 제거
- 공통 카드 레이아웃
- 공통 차트 설정
- 반복 스타일링 패턴

## 3단계: 병합 전략

### Git 기반 병합
```bash
# 최적화 브랜치에서 작업
git checkout -b ui-optimization

# 변경사항 커밋
git add .
git commit -m "refactor: UI optimization"

# 원본에 병합
git checkout main
git merge ui-optimization
```

### 파일별 병합 (안전)
1. 최적화된 파일을 `optimized/` 폴더에 저장
2. diff로 변경사항 검토
3. 파일별로 수동 병합
