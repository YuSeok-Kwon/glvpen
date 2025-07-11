# BaseBall LOCK 게임 모드 구조 정리

## 🎮 게임 모드별 설명

### 1. Simulation (시뮬레이션 게임) - 메인 게임

- **경로**: `/game/*`
- **설명**: 카드 기반 야구 시뮬레이션 게임
- **특징**:
  - 선수 카드 수집 및 덱 구성
  - 라인업 설정 및 전략적 게임플레이
  - 봇과의 대전
  - 게임 결과 기록 및 통계

### 2. Classic Mode (클래식 모드)

- **경로**: `/classic/*`
- **설명**: 난이도 기반 빠른 게임
- **특징**:
  - Easy, Normal, Hard 난이도 선택
  - 빠른 게임 진행
  - 경험치 및 보상 시스템
  - 간단한 승부 결과

### 3. Real Match (실제 경기 예측)

- **경로**: `/realmatch/*`
- **설명**: 실제 KBO 경기 결과 예측 게임
- **특징**:
  - 실제 KBO 일정 연동
  - 경기 결과 예측 및 베팅
  - 예측 정확도 통계
  - 리얼타임 경기 정보

### 4. Custom Player (커스텀 선수)

- **경로**: `/customplayer/*`
- **설명**: RPG 스타일 선수 육성 시스템
- **특징**:
  - 커스텀 선수 생성 및 편집
  - 능력치 성장 시스템
  - 레벨업 및 경험치
  - 특성 시스템

## 🔄 리팩토링 제안

### 옵션 1: 모듈명 변경 (더 명확한 이름)

```
simulation → cardgame (카드 게임)
classic → quickgame (빠른 게임)
realmatch → prediction (경기 예측)
customplayer → playerrpg (선수 RPG)
```

### 옵션 2: 게임 모드 통합

```
gamemode/
├── card/          (기존 simulation)
├── quick/         (기존 classic)
├── prediction/    (기존 realmatch)
└── rpg/          (기존 customplayer)
```

### 옵션 3: 현재 구조 유지하되 문서화 강화

- README 파일에 명확한 모듈 설명 추가
- 각 컨트롤러에 상세한 주석 추가
- API 문서화 개선
