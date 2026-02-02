# ⚾ Custom Hitter Mode 기획서 (with 로직 설계)

## 🎯 개요
Custom Hitter Mode는 유저가 자신만의 타자를 성장시키며 매일 시뮬레이션 경기를 진행하는 간단한 게임 모드입니다. 경기는 결과만 제공되며, 성과에 따라 스탯과 경험치가 상승합니다.

---

## 📦 기본 시스템

### ✅ 선수 초기 상태
| 항목        | 설정값            |
|-------------|------------------|
| 초기 스탯   | 총합 30 (예: 파워 8, 정확 8, 속도 7, 수비 7) |
| 스탯 범위   | 각 항목 0 ~ 100   |
| 외형 커스터마이징 | 없음 |
| 경기 방식   | 텍스트 결과만 제공 |
| MVP 보상    | 경험치와 스탯 성장량 ×2 |

---

## 📊 스탯 구성 및 효과

| 스탯     | 설명                | 경기 결과에 미치는 영향 |
|----------|---------------------|--------------------------|
| **파워**  | 장타력               | 홈런 확률 증가           |
| **정확**  | 컨택 능력            | 안타 확률 증가           |
| **속도**  | 주루력               | 2루타 확률 증가          |
| **수비**  | 수비력               | MVP 선정 평가에 반영     |

---

## 🧠 경기 결과 생성 로직

### 🎲 성과 생성 방식
경기 결과는 아래와 같은 로직으로 생성됩니다. (랜덤 + 스탯 기반)

```java
// 예시 구조: 경기 결과 계산 알고리즘 (Service 내부)
int contact = hitter.getContact(); // 0 ~ 100
int power = hitter.getPower();
int speed = hitter.getSpeed();

double hitChance = 0.2 + (contact * 0.005); // 기본 20% + 스탯 보정 (max 70%)
double homeRunChance = 0.03 + (power * 0.002); // 기본 3% + 스탯 보정 (max 23%)
double doubleChance = 0.05 + (speed * 0.002); // 2루타 확률
```

- 타석 수는 고정 (ex: 4타석)
- 각 타석별로 확률 계산 → 결과 결정
- 결과 누적으로 안타 수, 홈런 수, MVP 판단

---

## 🏅 MVP 선정 로직

```java
boolean isMVP = myResultScore >= opponentResultScore;
```

- 경기 내 기록 기반 점수 산정
- 예: (안타 1 × 1) + (홈런 1 × 3) + (2루타 1 × 2)
- 높은 기록을 가진 유저 선수가 MVP로 선정
- MVP 보상:
  - XP ×2
  - 레벨업 포인트 ×2

---

## 📈 성장 시스템

### ✅ 경험치 기반 레벨업
```java
int xp = hits * 5 + homeRuns * 10 + walks * 2;
int nextLevelXp = level * 20 + 40;
```

- 경기 성과 → XP 획득 → XP 누적 시 레벨업
- 레벨업마다 스탯 포인트 3~5 지급

```java
if (hitter.getXp() >= nextLevelXp) {
  hitter.setLevel(hitter.getLevel() + 1);
  hitter.setXp(hitter.getXp() - nextLevelXp);
  hitter.setAvailablePoints(hitter.getAvailablePoints() + 4); // 예시
}
```

### ✅ 스탯 분배
유저가 직접 분배 (프론트에서 선택 → API로 전달)

```java
// DTO
public class StatAllocateDTO {
  private int contact;
  private int power;
  private int eye;
  private int speed;
}
```

---

## 💰 아이템 시스템

| 아이템명 | 효과           | 가격 (골드) |
|----------|----------------|-------------|
| 훈련북    | 무작위 스탯 +1 | 30G         |
| 배트 강화 | 파워 +1        | 50G         |

### 구매 로직

```java
if (hitter.getGold() >= 30) {
  hitter.setGold(hitter.getGold() - 30);
  int statType = random.nextInt(4); // 0~3 → 랜덤 스탯 증가
}
```

---

## 📋 API 설계 요약

| 기능             | Method | URL                        | 설명                   |
|------------------|--------|-----------------------------|------------------------|
| 경기 결과 적용      | POST   | `/api/custom-hitter/{id}/match-result` | 경기 결과 계산 및 저장 |
| 스탯 분배          | POST   | `/api/custom-hitter/{id}/allocate` | 레벨업 후 스탯 분배     |
| 아이템 사용         | POST   | `/api/custom-hitter/{id}/use-item` | 아이템 사용              |
| 상태 조회          | GET    | `/api/custom-hitter/{id}`         | 선수 상태 전체 조회     |

---

## 📝 경기 결과 예시 출력

```
[경기 결과]
- 타석: 4
- 안타: 2
- 홈런: 1
- 볼넷: 1
- 삼진: 0

[보상]
- 경험치: 22 XP
- 골드: 30G

🎖 MVP 수상! 보상 2배 적용
- 최종 XP: 44 XP
- 추가 포인트: +4

[현재 상태]
- 레벨: 4 (XP 20/80)
- 보유 포인트: 3
```

---

## 🔄 확장 고려 요소 (후순위)

- 시즌제 도입 (기간별 리셋 + 누적 기록)
- 커리어 기록 보관 (최다 안타, 최다 홈런 등)
- 친구 간 비교 기능 (PvP는 제외)
- 업적 시스템

---

## 🔧 기술 요약

- Java 17+
- Spring Boot + JPA
- DTO 중심 계층 분리 (Entity 직접 사용 금지)
- Service 레이어에서 모든 로직 처리
- 예외, validation, hard-coding 최소화

---
