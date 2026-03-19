# 선수 유사도 분석 (Player Similarity)

## 개요

**코사인 유사도(Cosine Similarity)** 를 활용하여 KBO 타자 간 성적 패턴의 유사성을 측정한다.
8개 핵심 세이버메트릭스 지표를 벡터로 표현하고, Z-score 정규화 후 벡터 간 각도를 비교하여 "스타일이 비슷한 선수"를 찾아낸다.

## 알고리즘

### 비교 지표 (8차원 벡터)

| 인덱스 | 지표 | 분류 | 설명 |
|--------|------|------|------|
| 0 | WAR | 종합 | 대체 선수 대비 승리 기여도 |
| 1 | AVG | 타격 | 타율 |
| 2 | OPS | 타격 | 출루율 + 장타율 |
| 3 | HR | 파워 | 홈런 수 |
| 4 | SB | 스피드 | 도루 수 |
| 5 | K% | 선구안 | 삼진 비율 |
| 6 | BB% | 선구안 | 볼넷 비율 |
| 7 | ISO | 파워 | 순수 장타력 (SLG - AVG) |

이 8개 지표를 통해 선수의 타격, 파워, 스피드, 선구안, 종합 가치를 다차원적으로 평가한다.

### Step 1: 데이터 수집

`findAllBattersForAnalysis(season)` 쿼리로 해당 시즌 30경기 이상 출전 타자의 전체 스탯을 가져온다.

### Step 2: Z-score 정규화

각 지표를 동일한 스케일로 변환하여 HR(0~50)과 AVG(0.200~0.350) 같은 스케일 차이를 해소한다.

```
Z(x) = (x - μ) / σ

μ = 해당 시즌 전체 선수의 지표 평균
σ = 해당 시즌 전체 선수의 지표 표준편차
```

### Step 3: 코사인 유사도 계산

정규화된 벡터 간의 각도를 측정한다. 값이 1에 가까울수록 성적 패턴이 유사하다.

```
Cosine Similarity = (A · B) / (||A|| × ||B||)

A · B = Σ(A[i] × B[i])          (내적)
||A|| = √(Σ(A[i]²))            (벡터 크기)

결과 범위: -1 ~ 1
  1.0 = 완전 동일 패턴
  0.0 = 무관
 -1.0 = 완전 반대 패턴
```

### 유사도 탐색 vs 1:1 비교

| 모드 | 메서드 | 정규화 | 설명 |
|------|--------|--------|------|
| 유사 선수 탐색 | `findSimilarBatters()` | Z-score 정규화 후 비교 | 전체 선수 대비 상대적 위치 기반 |
| 1:1 직접 비교 | `compareTwoPlayers()` | 원본 값으로 비교 | 두 선수의 절대적 성적 직접 비교 |

## API

### 유사 선수 탐색

```http
GET /api/analysis/advanced/similarity/{playerId}?season=2025&topN=10
```

**응답:**

```json
{
  "season": 2025,
  "basePlayer": {
    "playerId": 123,
    "playerName": "양의지",
    "teamName": "NC 다이노스",
    "logoName": "nc",
    "stats": {
      "WAR": 3.5,
      "AVG": 0.301,
      "OPS": 0.845,
      "HR": 18,
      "SB": 2,
      "K%": 11.5,
      "BB%": 7.7,
      "ISO": 0.178
    }
  },
  "similarPlayers": [
    {
      "playerId": 456,
      "playerName": "강민호",
      "teamName": "롯데 자이언츠",
      "logoName": "lotte",
      "similarity": 0.978,
      "stats": {
        "WAR": 2.8,
        "AVG": 0.289,
        "OPS": 0.821,
        "HR": 15,
        "SB": 1,
        "K%": 12.3,
        "BB%": 8.1,
        "ISO": 0.165
      }
    }
  ],
  "radarLabels": ["WAR", "AVG", "OPS", "HR", "SB", "K%", "BB%", "ISO"]
}
```

### 두 선수 직접 비교

```http
GET /api/analysis/advanced/similarity/compare?player1=123&player2=456&season=2025
```

## 데이터 흐름

```
1. BatterStatsRepository.findAllBattersForAnalysis(season)
   → 30경기 이상 출전 타자 전체 스탯 (pivot 쿼리)
   → 컬럼: playerId, playerName, teamName, logoName, teamId,
            WAR, AVG, OPS, HR, SB, K%, BB%, ISO, G, PA, ...

2. 8개 지표 벡터 추출 (인덱스 5~12)

3. Z-score 정규화 (전체 선수 기준)

4. 기준 선수 벡터 vs 전체 선수 벡터 → 코사인 유사도

5. 유사도 내림차순 정렬 → 상위 N명 반환
```

## 파일 구조

| 파일 | 역할 |
|------|------|
| `PlayerSimilarityService.java` | Z-score 정규화 + 코사인 유사도 계산 |
| `PlayerSimilarityDTO.java` | PlayerInfo + SimilarPlayer + radarLabels |
| `BatterStatsRepository.java` | `findAllBattersForAnalysis()` — pivot 쿼리 |

## 활용 예시

### 트레이드/FA 분석
- "이 선수와 성적 패턴이 비슷한 선수가 과거에 어떤 계약을 했는가?"

### 대체 선수 탐색
- "부상 선수 대신 비슷한 스타일의 선수는 누구인가?"

### 레이더 차트 시각화
- `radarLabels` 필드를 Chart.js Radar Chart의 라벨로 사용
- 기준 선수와 유사 선수의 stats를 겹쳐서 시각적으로 비교

## 한계 및 참고사항

- **타자만 지원**: 현재 투수 유사도는 미구현 (향후 확장 가능)
- **단일 시즌 기반**: 커리어 전체가 아닌 해당 시즌 성적만으로 비교
- **최소 출전**: 30경기 미만 선수는 분석 대상에서 제외 (`findAllBattersForAnalysis` 내부 필터)
- **유효 데이터 검증**: 모든 지표가 0인 선수는 제외 (`hasValidData()`)
- **소수점 정밀도**: 유사도 값은 소수점 3자리로 반올림
