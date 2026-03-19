# 투수 피로도 분석 (Fatigue Analysis)

## 개요

투수의 **등판 간 휴식일 수** 에 따른 성적 변화와 **전반기/후반기 ERA 비교** 를 통해 피로도를 정량적으로 분석한다.
KBO 경기 기록(`kbo_pitcher_record`)과 경기 일정(`kbo_schedule`)을 결합하여 등판 간격을 계산하고, 구간별 ERA를 그룹핑한다.

## 알고리즘

### 1. 등판 간 휴식일 계산

투수의 시즌 내 등판 기록을 날짜순으로 정렬한 뒤, 연속 등판 간의 일수 차이를 계산한다.

```
restDays = ChronoUnit.DAYS.between(이전_등판일, 현재_등판일)
```

### 2. 휴식일별 ERA 그룹핑

등판을 3개 그룹으로 분류하고 그룹별 ERA, 평균 투구수, 평균 이닝을 산출한다.

| 그룹 | 휴식일 | 의미 |
|------|--------|------|
| 0~2일 | 0 ~ 2일 | 연투/불펜 단기 등판 |
| 3~4일 | 3 ~ 4일 | 선발 정상 로테이션 |
| 5일+ | 5일 이상 | 충분한 휴식 후 등판 |

> **시즌 첫 등판**: 이전 등판이 없으므로 자동으로 "5일+" 그룹에 배정한다.

**그룹별 ERA 계산:**

```
그룹 ERA = (그룹 내 총 자책점 / 그룹 내 총 이닝) × 9
```

### 3. 이닝 변환 (KBO 표기법)

KBO는 이닝을 `2.1` (2와 1/3이닝)으로 표기한다. 이를 실제 이닝 수로 변환한다.

```
실제 이닝 = 정수부 + (소수부 × 10) / 3

예시:
  2.0 → 2.000 이닝
  2.1 → 2.333 이닝 (2 + 1/3)
  2.2 → 2.667 이닝 (2 + 2/3)
  5.1 → 5.333 이닝
```

```java
double whole = Math.floor(ip);        // 2.1 → 2
double fraction = ip - whole;          // 2.1 → 0.1
return whole + (fraction * 10) / 3.0;  // 2 + (0.1 * 10) / 3 = 2.333
```

### 4. 전반기/후반기 ERA 비교

올스타 브레이크를 기준으로 시즌을 양분하여 ERA를 비교한다.

| 구간 | 기준일 | 기간 |
|------|--------|------|
| 전반기 | ~ 7월 14일 | 개막 ~ 올스타 직전 |
| 후반기 | 7월 15일 ~ | 올스타 이후 ~ 시즌 종료 |

```java
LocalDate midPoint = LocalDate.of(season, 7, 15);
```

### 5. 피로도 지수 (Fatigue Index)

후반기 ERA를 전반기 ERA로 나누어 시즌 후반의 성적 변화를 측정한다.

```
Fatigue Index = ERA(후반기) / ERA(전반기)
```

| 피로도 지수 | 해석 |
|------------|------|
| < 0.8 | 후반기 대폭 개선 (적응/컨디션 상승) |
| 0.8 ~ 1.0 | 후반기 소폭 개선 또는 유지 |
| 1.0 | 전후반 동일 |
| 1.0 ~ 1.3 | 후반기 소폭 하락 (경미한 피로) |
| 1.3 ~ 1.5 | 후반기 뚜렷한 하락 (피로 누적) |
| > 1.5 | 후반기 급격한 하락 (심각한 피로) |

> 전반기 ERA가 0인 경우(전반기 등판 없음), 피로도 지수는 1.0으로 설정한다.

## API

### 개별 투수 피로도 분석

```http
GET /api/analysis/advanced/fatigue/{playerId}?season=2025
```

**응답:**

```json
{
  "playerId": 101,
  "playerName": "류현진",
  "teamName": "한화 이글스",
  "logoName": "hanwha",
  "season": 2025,
  "totalAppearances": 27,
  "seasonEra": 3.82,
  "fatigueIndex": 1.14,
  "restGroups": [
    {
      "label": "0~2일",
      "appearances": 0,
      "era": 0.0,
      "avgPitchCount": 0.0,
      "avgInnings": 0.0
    },
    {
      "label": "3~4일",
      "appearances": 15,
      "era": 3.45,
      "avgPitchCount": 95.2,
      "avgInnings": 6.33
    },
    {
      "label": "5일+",
      "appearances": 12,
      "era": 4.21,
      "avgPitchCount": 88.7,
      "avgInnings": 5.67
    }
  ],
  "halfSeason": {
    "firstHalfEra": 3.2,
    "firstHalfAppearances": 14,
    "secondHalfEra": 4.5,
    "secondHalfAppearances": 13
  }
}
```

### 피로도 랭킹 (등판 수 20 이상)

```http
GET /api/analysis/advanced/fatigue/ranking?season=2025&limit=20
```

**응답:**

```json
[
  {
    "rank": 1,
    "playerId": 101,
    "playerName": "류현진",
    "teamName": "한화 이글스",
    "logoName": "hanwha",
    "totalAppearances": 27,
    "seasonEra": 3.82,
    "fatigueIndex": 1.45
  }
]
```

### 차트 데이터 (개별 투수)

```http
GET /api/analysis/advanced/fatigue/{playerId}/chart?season=2025
```

> `analyzePitcherFatigue()`와 동일한 응답. 프론트엔드에서 `restGroups`로 Bar Chart, `halfSeason`으로 Comparison Chart를 렌더링한다.

## 데이터 흐름

```
1. PitcherRecordRepository.findAllPitcherAppearancesBySeason(season)
   → 전체 투수 등판 기록 (playerId, playerName, teamName, logoName,
                           innings, earnedRuns, pitchCount, entryType, gameDate)

2. 해당 투수 필터 (playerId 기준)

3. 등판 날짜순 정렬 → 연속 등판 간 휴식일 계산

4. 3개 그룹 분류 → 그룹별 ERA/평균투구수/평균이닝 산출

5. 전반기(~7/14)/후반기(7/15~) 분류 → 각 ERA 계산

6. 피로도 지수 = 후반기 ERA / 전반기 ERA
```

## 파일 구조

| 파일 | 역할 |
|------|------|
| `FatigueAnalysisService.java` | 등판 간격 분석 + 피로도 계산 |
| `FatigueAnalysisDTO.java` | RestGroupStats + HalfSeasonComparison + FatigueRanking |
| `PitcherRecordRepository.java` | `findAllPitcherAppearancesBySeason()` — 등판 기록 쿼리 |

## 활용 시나리오

### 선발 로테이션 관리
- 4일 휴식 vs 5일 휴식 시 ERA 차이를 확인하여 최적 로테이션 간격을 파악

### 불펜 피로 관리
- 0~2일 연투 시 ERA 급등 여부로 불펜 투수 연투 한계를 판단

### 시즌 후반 체력 관리
- 피로도 지수 > 1.3인 투수는 후반기 이닝 제한 검토 필요

### 포스트시즌 전력 분석
- 시즌 후반 ERA가 안정적인(피로도 지수 ≤ 1.0) 투수가 포스트시즌 신뢰도 높음

## 한계 및 참고사항

- **최소 등판 수**: 랭킹은 20등판 이상 투수만 포함 (데이터 신뢰성)
- **개별 분석은 제한 없음**: 1등판이라도 개별 API 호출 가능
- **올스타 기준일**: 매년 7월 15일로 고정 (실제 올스타 일정과 차이 가능)
- **entryType 미활용**: 선발/중계/마무리 구분 데이터는 수집하나 현재 분석에 미반영
- **투구수 제한**: 투구수(pitchCount)는 그룹별 평균으로만 제공 (누적 피로 모델 미적용)
- **소수점 정밀도**: ERA 등은 소수점 2자리로 반올림 (`round(val * 100.0) / 100.0`)
