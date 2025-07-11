# ⚾ BaseBall LOCK 2.0 - 모드 확장 구조 설계

---

## 🎮 도입: 3가지 게임 모드

### 1. **Real Match Mode**
- 실제 KBO 스케줄 및 라인업을 기반으로 시뮬레이션
- 어제 or 당일 경기 라인업 사용 (당일은 경기 1시간 전 공개)
- 크롤링 기반 자동 저장

### 2. **Custom Hitter Mode**
- 유저가 만든 타자를 실시간 시뮬로 키우는 RPG형 시스템
- 실시간 턴 구조, 경험치/스탯 성장 포함
- 선택 기반 행동으로 성장 분기

### 3. **Classic Sim Mode**
- 기존처럼 유저 라인업 vs 봇 라인업 자동 경기
- 대신 하이라이트만 출력, 난이도(Easy/Normal/Hard) 선택 가능

---

## 🧱 전체 설계 시 고려할 주요 구조

### ✅ 1. 모드 기반 도메인 분리

```
/modules
  ├── realmatch/
  ├── customplayer/
  ├── classic/
```

- 각 모드는 컨트롤러, 서비스, DTO, 결과 구조를 독립적으로 유지
- 공통 로직은 /common 또는 /core로 분리

---

### ✅ 2. 공통 요소와 전용 요소 분리

| 공통 요소        | 모드 전용 요소 |
|------------------|----------------|
| 유저/팀/선수 정보 | 성장 시스템 (customplayer) |
| 경기 결과 저장     | 실시간 세션 (customplayer) |
| MVP 계산          | 자동 라인업 생성 (realmatch) |
| 경기 로그 출력     | 난이도 설정 및 봇 전략 (classic) |

---

### ✅ 3. 실시간 경기 흐름 처리 (Custom Hitter Mode)

- WebSocket 기반 턴 단위 진행
- GameSession 저장 구조 (메모리 or Redis)
- 중간 상태 유지, 재접속 처리 필요

---

### ✅ 4. 스케줄 + 라인업 크롤링 (Real Match Mode)

- 전일 라인업: 새벽 자동 크롤링
- 당일 라인업: 경기 시작 1시간 전
- 테이블 설계: schedule + lineup

---

### ✅ 5. 커스텀 타자 성장 시스템

- custom_player 테이블 + 경험치, 레벨, 능력치 컬럼
- 성장 이벤트 로그 저장
- 경기 행동에 따라 분기 성장 or 특성 부여

---

### ✅ 6. 클라이언트 구조도 모드 분리

```
/pages
  ├── realmatch/
  ├── customplayer/
  ├── classic/
```

- 각 모드별 상태/라우트/페이지 독립 구성
- 공통 컴포넌트는 props로 분기 처리

---

### ✅ 7. 공통 결과 화면 통합

- /game/result-view?scheduleId=123&mode=classic
- GameResultDTO는 공통 + 모드별 필드 조합
- UI는 mode에 따라 분기 렌더링

---

### ✅ 8. Classic Mode 난이도 시스템

- Difficulty enum: EASY / NORMAL / HARD
- 봇 라인업 구성 로직을 난이도에 따라 가중치/전략 조절
- 하이라이트 출력만 (시간 압축)

---

## 📦 예시 서버 구조

```
/modules
  ├── realmatch/
  │   ├── realMatch.controller.ts
  │   ├── realMatch.service.ts
  ├── customplayer/
  │   ├── custom.controller.ts
  │   ├── growth.service.ts
  ├── classic/
  │   ├── classic.controller.ts
  │   ├── botDifficulty.service.ts
/common
  ├── mvpCalculator.ts
  ├── gameResultSaver.ts
  └── gameLogFormatter.ts
```

---

## ✅ 정리

> 각 모드는 독립된 도메인처럼 관리하되,  
> 공통 요소는 추상화/재사용하여 중복을 줄이고 확장성을 확보하는 구조로 간다.

---

**다음 단계 제안:**
- ERD 및 DB 설계
- 각 모드의 서비스 흐름 구체화
- WebSocket 구조 설계 (customplayer)