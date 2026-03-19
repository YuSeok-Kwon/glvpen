# Marcel 성적 예측 (Marcel Projection)

## 개요

**Marcel Projection System**은 야구 통계학자 Tom Tango가 개발한 성적 예측 시스템이다.
최근 3시즌의 가중평균을 기반으로 다음 시즌 성적을 예측하며, 리그 평균으로의 회귀(Regression to the Mean)와 노화 곡선(Aging Curve)을 반영한다.

"Marcel"이라는 이름은 프랑스의 마임 아티스트 Marcel Marceau에서 따왔으며, "가장 기본적인(바닥) 예측 시스템"이라는 의미를 담고 있다.

## 알고리즘

### Step 1: 3시즌 가중평균

최근 시즌에 더 높은 가중치를 부여하여 평균을 계산한다.

```
가중 평균 = (Year-1 × 5 + Year-2 × 4 + Year-3 × 3) / 12
```

| 시즌 | 가중치 | 비율 |
|------|--------|------|
| 직전 시즌 (Year-1) | 5 | 41.7% |
| 2시즌 전 (Year-2) | 4 | 33.3% |
| 3시즌 전 (Year-3) | 3 | 25.0% |

### Step 2: 리그 평균 회귀 (Regression to the Mean)

개별 선수의 성적은 표본 크기에 따른 불확실성이 있다. 출전 기회(PA/IP)가 적을수록 리그 평균에 가깝게 보정한다.

**타자 (비율 지표만 적용):**
```
예측값 = (가중평균 × 유효PA + 리그평균 × 1200) / (유효PA + 1200)
```

**투수 (비율 지표만 적용):**
```
예측값 = (가중평균 × 유효IP + 리그평균 × 130) / (유효IP + 130)
```

| 구분 | 회귀 기준량 | 설명 |
|------|-----------|------|
| 타자 | PA 1,200 | 약 3시즌 풀타임 분량 |
| 투수 | IP 130 | 약 1시즌 선발 분량 |

> **비율 지표(Rate Stat)**: AVG, OPS, OBP, SLG, BB%, K%, ERA, WHIP, FIP 등
> **횟수 지표(Count Stat)**: HR, RBI, SB, W, SO 등 — 회귀 미적용

### Step 3: 노화 곡선 (Aging Curve)

나이에 따른 성적 변화를 반영한다. KBO 선수의 평균 피크 연령을 기준으로 보정한다.

| 구분 | 피크 연령 | 비율 지표 감소 | 횟수 지표 감소 |
|------|----------|--------------|--------------|
| 타자 | 27세 | 0.6%/년 | 0.3%/년 |
| 투수 | 28세 | 0.6%/년 | 0.3%/년 |

```
# 피크 이후 (노화)
projected *= (1.0 - agingFactor × (나이 - 피크))

# 피크 이전 (성장) — 절반 속도
projected *= (1.0 + agingFactor × |나이 - 피크| × 0.5)
```

**투수 역방향 지표**: ERA, WHIP, FIP, BB/9는 낮을수록 좋으므로 노화 시 증가(×1.006), 성장 시 감소(×0.994)로 반전 적용한다.

### 신뢰도 (Confidence)

데이터가 충분할수록 예측의 신뢰도가 높아진다.

```
confidence = min(1.0, 보유시즌수 / 3)

- 3시즌 데이터: 1.0 (100%)
- 2시즌 데이터: 0.667 (66.7%)
- 1시즌 데이터: 0.333 (33.3%)
```

## 예측 카테고리

### 타자

| 카테고리 | 유형 | 설명 |
|---------|------|------|
| AVG | Rate | 타율 |
| OPS | Rate | 출루율+장타율 |
| OBP | Rate | 출루율 |
| SLG | Rate | 장타율 |
| BB% | Rate | 볼넷 비율 |
| K% | Rate | 삼진 비율 |
| HR | Count | 홈런 수 |
| RBI | Count | 타점 수 |
| SB | Count | 도루 수 |
| WAR | Count | 대체 선수 대비 승리 기여도 |

### 투수

| 카테고리 | 유형 | 역방향 | 설명 |
|---------|------|--------|------|
| ERA | Rate | O | 평균자책점 |
| WHIP | Rate | O | (안타+볼넷)/이닝 |
| FIP | Rate | O | 수비 무관 평균자책점 |
| K/9 | Rate | | 9이닝당 삼진 |
| BB/9 | Rate | O | 9이닝당 볼넷 |
| WAR | Count | | 대체 선수 대비 승리 기여도 |
| W | Count | | 승수 |
| SO | Count | | 총 삼진 |

## API

### 타자 개별 예측

```http
GET /api/analysis/advanced/projection/batter/{playerId}?targetSeason=2026
```

**응답:**

```json
{
  "playerId": 123,
  "playerName": "김도영",
  "teamName": "KIA 타이거즈",
  "logoName": "kia",
  "playerType": "batter",
  "targetSeason": 2026,
  "confidence": 1.0,
  "projections": [
    {
      "category": "AVG",
      "projected": 0.285,
      "lastSeason": 0.347,
      "leagueAvg": 0.267,
      "changeRate": -17.9
    },
    {
      "category": "HR",
      "projected": 22.5,
      "lastSeason": 38,
      "leagueAvg": null,
      "changeRate": -40.8
    }
  ]
}
```

### 투수 개별 예측

```http
GET /api/analysis/advanced/projection/pitcher/{playerId}?targetSeason=2026
```

### 전체 타자 WAR 예측 랭킹

```http
GET /api/analysis/advanced/projection/batters?targetSeason=2026&limit=20
```

**응답:**

```json
[
  {
    "rank": 1,
    "playerId": 123,
    "playerName": "김도영",
    "teamName": "KIA 타이거즈",
    "logoName": "kia",
    "projectedWar": 5.2,
    "lastSeasonWar": 7.8,
    "confidence": 1.0
  }
]
```

## 데이터 흐름

```
1. BatterStatsRepository.findStatsRawByPlayerIdAndSeason()
   → 최근 3시즌 카테고리별 성적 조회

2. BatterStatsRepository.findLeagueAvgBySeason()
   → 리그 평균 조회

3. 3시즌 가중평균 계산 (5/4/3)

4. Rate Stat → 리그 평균 회귀 적용

5. Player.birthDate → 노화 곡선 보정

6. BatterStatsRepository.findTeamAndPosition()
   → 팀/로고 정보 조회 (최근 3시즌 중 첫 발견)
```

## 파일 구조

| 파일 | 역할 |
|------|------|
| `MarcelProjectionService.java` | Marcel 알고리즘 구현 |
| `MarcelProjectionDTO.java` | ProjectionDetail + ProjectionRanking DTO |
| `BatterStatsRepository.java` | 타자 시즌 스탯, 리그 평균, 팀 정보 쿼리 |
| `PitcherStatsRepository.java` | 투수 시즌 스탯, 리그 평균 쿼리 |
| `PlayerRepository.java` | 선수 기본 정보 (birthDate) |

## 한계 및 참고사항

- **리그 평균**: 전체 선수 포함이므로 소규모 PA 선수가 평균을 낮출 수 있음
- **birthDate null**: 생년월일이 없는 선수는 노화 보정을 건너뜀
- **팀 정보**: WAR 데이터가 없는 선수는 teamName/logoName이 빈 값일 수 있음 (최대 3시즌 탐색 후 fallback)
- **신규 선수**: 1시즌 미만 데이터만 있으면 `null` 반환 (예측 불가)
- **소수점 정밀도**: 모든 값은 소수점 3자리로 반올림 (`round(val * 1000.0) / 1000.0`)
