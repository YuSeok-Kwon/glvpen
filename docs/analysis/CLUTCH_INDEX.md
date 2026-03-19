# 클러치 지수 분석 (Clutch Index)

## 개요

**클러치 지수(Clutch Index)** 는 경기 결정적 상황에서 얼마나 큰 기여를 하는지 정량화한 지표이다.
RBI(타점), HR(홈런), OPS(출루율+장타율)의 Z-score 가중합으로 계산하며, 결정적 상황에서 강한 타자를 식별한다.

> **배경**: 원래 RISP(득점권 타율), GW RBI(결승타점), PH-BA(대타 타율) 기반으로 설계되었으나,
> KBO 공식 사이트의 BasicOld 페이지 전환으로 해당 데이터 수집이 불가능해져
> RBI, HR, OPS 기반 대체 지표로 전환하였다.

## 알고리즘

### 지표 선정 및 가중치

| 지표 | 가중치 | 의미 |
|------|--------|------|
| RBI (타점) | 0.5 (50%) | 직접적 득점 기여 — 가장 핵심적인 클러치 지표 |
| HR (홈런) | 0.3 (30%) | 결정적 한 방 능력 |
| OPS (출루+장타) | 0.2 (20%) | 전반적 타격 생산성 |

### 계산 과정

**Step 1: 해당 시즌 모든 타자 데이터 수집**

`findAllBattersForAnalysis(season)` 쿼리로 30경기 이상 출전한 타자의 RBI, HR, OPS를 가져온다.

**Step 2: Z-score 정규화**

각 지표를 전체 선수 기준 표준 점수로 변환한다.

```
Z(x) = (x - μ) / σ

μ = 전체 선수 평균
σ = 전체 선수 표준편차
```

예시:
```
선수 A: RBI=95, HR=28, OPS=0.850
리그 평균: RBI=52, HR=14, OPS=0.720
리그 표준편차: RBI=25, HR=10, OPS=0.085

Z(RBI) = (95 - 52) / 25 = 1.72
Z(HR) = (28 - 14) / 10 = 1.40
Z(OPS) = (0.850 - 0.720) / 0.085 = 1.53
```

**Step 3: 가중합**

```
Clutch Index = 0.5 × Z(RBI) + 0.3 × Z(HR) + 0.2 × Z(OPS)

선수 A: 0.5 × 1.72 + 0.3 × 1.40 + 0.2 × 1.53 = 1.586
```

**Step 4: 랭킹 및 백분위**

클러치 지수 내림차순으로 정렬 후 순위와 백분위를 부여한다.

```
rank = 정렬 순서 (1부터)
percentile = (1.0 - (rank - 1) / 전체선수수) × 100

예: 200명 중 1위 → 100.0%, 10위 → 95.5%
```

## API

### 클러치 랭킹 조회

```http
GET /api/analysis/advanced/clutch?season=2025&limit=20
```

**응답:**

```json
{
  "season": 2025,
  "rankings": [
    {
      "playerId": 789,
      "playerName": "데이비슨",
      "teamName": "NC 다이노스",
      "logoName": "nc",
      "rbi": 110,
      "hr": 35,
      "ops": 0.920,
      "clutchIndex": 2.838,
      "rank": 1,
      "percentile": 100.0
    },
    {
      "playerId": 456,
      "playerName": "최형우",
      "teamName": "KIA 타이거즈",
      "logoName": "kia",
      "rbi": 95,
      "hr": 25,
      "ops": 0.870,
      "clutchIndex": 2.145,
      "rank": 2,
      "percentile": 99.5
    }
  ]
}
```

### 특정 선수 클러치 지수

```http
GET /api/analysis/advanced/clutch/{playerId}?season=2025
```

### 전체 차트 데이터 (제한 없음)

```http
GET /api/analysis/advanced/clutch/chart?season=2025
```

## 데이터 흐름

```
1. BatterStatsRepository.findAllBattersForAnalysis(season)
   → pivot 쿼리로 선수별 RBI(인덱스 17), HR(인덱스 8), OPS(인덱스 7) 추출

2. 유효 데이터 필터 (RBI 또는 HR 중 하나 이상 존재)

3. Z-score 계산 (3개 지표 × 전체 선수)

4. 가중합 → 클러치 지수

5. 내림차순 정렬 → 순위/백분위 부여

6. limit 적용 → 결과 반환
```

## 파일 구조

| 파일 | 역할 |
|------|------|
| `ClutchIndexService.java` | Z-score 정규화 + 가중합 계산 |
| `ClutchIndexDTO.java` | ClutchPlayer (rank, percentile 포함) |
| `BatterStatsRepository.java` | `findAllBattersForAnalysis()` — pivot 쿼리 |

## 해석 가이드

| 클러치 지수 | 해석 |
|------------|------|
| 2.0+ | 리그 최상위 클러치 타자 (상위 ~3%) |
| 1.0 ~ 2.0 | 클러치 상황에 강한 선수 (상위 ~15%) |
| 0.0 ~ 1.0 | 리그 평균 수준 |
| -1.0 ~ 0.0 | 리그 평균 이하 |
| -1.0 미만 | 결정적 상황에 약한 선수 |

## 한계 및 참고사항

- **RISP 데이터 미수집**: KBO 사이트 페이지 전환으로 득점권 타율 데이터가 없어 RBI로 대체
- **상황 데이터 부재**: 실제 클러치 상황(주자 득점권, 접전, 후반 역전 등)의 세분화 불가
- **RBI 한계**: RBI는 타순, 앞 타자 출루율 등 외부 요인에 영향을 받는 지표
- **시즌 단위**: 월별/상황별 세분화 없이 시즌 전체 통계 기반
- **타자 전용**: 투수에 대한 클러치 분석은 미지원
- **개별 조회 비효율**: `getPlayerClutch()`는 내부적으로 전체 랭킹을 계산한 후 필터링
