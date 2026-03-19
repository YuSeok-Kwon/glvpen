# 승리 예측 모델 (Win Prediction)

## 개요

KBO 팀 간 경기 승리 확률을 예측하는 **로지스틱 회귀(Logistic Regression)** 기반 머신러닝 모델이다.
외부 라이브러리 없이 순수 Java로 구현하였으며, 팀 스탯 차이(ERA, OPS, WAR)와 홈 이점을 Feature로 사용한다.

## 알고리즘

### 로지스틱 회귀 (Logistic Regression)

이진 분류 문제(홈팀 승리/패배)를 확률로 모델링하는 통계적 학습 알고리즘이다.

**시그모이드 함수:**

```
P(홈팀 승리) = σ(z) = 1 / (1 + e^(-z))
z = w₁x₁ + w₂x₂ + ... + w₅x₅ + b
```

### Feature 설계 (5개)

| # | Feature | 산출 방식 | 의미 |
|---|---------|----------|------|
| 1 | deltaERA | 원정팀 ERA - 홈팀 ERA | 홈팀 투수력 우위 (양수 = 유리) |
| 2 | deltaOPS | 홈팀 OPS - 원정팀 OPS | 홈팀 타격력 우위 |
| 3 | deltaBatterWAR | 홈팀 타자WAR - 원정팀 타자WAR | 홈팀 타자 전력 우위 |
| 4 | deltaPitcherWAR | 홈팀 투수WAR - 원정팀 투수WAR | 홈팀 투수 전력 우위 |
| 5 | homeAdvantage | 항상 1.0 | 홈 이점 상수 |

> **ERA는 반전 처리**: ERA는 낮을수록 좋으므로, `원정 - 홈`으로 계산하여 홈팀이 낮으면 양수가 되도록 설계하였다.

### Feature 정규화 (Z-score)

ERA(3~6), OPS(0.6~0.9), WAR(-2~6) 등 스케일이 다른 Feature를 동일 스케일로 변환한다.

```
Z(x) = (x - μ) / σ

μ = 학습 데이터의 평균
σ = 학습 데이터의 표준편차
```

정규화 파라미터(평균, 표준편차)는 학습 시 계산되어 예측 시에도 동일하게 적용된다.

### 학습 (Gradient Descent)

| 하이퍼파라미터 | 값 | 설명 |
|--------------|-----|------|
| Learning Rate | 0.01 | 가중치 업데이트 속도 |
| Epochs | 1,000 | 전체 데이터 반복 횟수 |
| Loss Function | Binary Cross Entropy | 이진 분류 손실 함수 |

```
# 매 Epoch마다:
for 각 학습 샘플 (features, label):
    pred = σ(w · x + b)
    error = pred - label

    # 가중치 기울기 누적
    ∇w[i] += error × x[i]
    ∇b += error

# 가중치 업데이트
w[i] = w[i] - α × ∇w[i] / n
b = b - α × ∇b / n
```

### 예측 범위 제한

모델 예측값을 **0.25 ~ 0.75** 범위로 제한한다.
야구는 불확실성이 높은 스포츠이므로 극단적 확률(90% 등)은 비현실적이다.

```java
homeWinProb = Math.max(0.25, Math.min(0.75, homeWinProb));
```

## 학습 데이터

| 항목 | 설명 |
|------|------|
| 데이터 소스 | `kbo_schedule` (경기 결과) + `team_stats` (팀 스탯) |
| 학습 시즌 | 2020 ~ 직전 시즌 (lazy 학습) |
| 필터 | 종료된 경기만, 동점 경기 제외 |
| 학습 샘플 | 약 2,700+ 경기 (5시즌 기준) |

### Lazy 학습

최초 예측 요청 시 모델이 없으면 자동으로 학습을 시작한다 (`volatile` + `synchronized`로 Thread-safe).

```java
if (weights == null) {
    trainModel(2020, season - 1);
}
```

## API

### 승리 확률 예측

```http
GET /api/analysis/advanced/win-prediction?homeTeamId={id}&awayTeamId={id}&season={year}
```

**응답:**

```json
{
  "homeTeamId": 1,
  "homeTeamName": "KIA 타이거즈",
  "homeTeamLogo": "kia",
  "awayTeamId": 5,
  "awayTeamName": "두산 베어스",
  "awayTeamLogo": "doosan",
  "homeWinProbability": 0.529,
  "awayWinProbability": 0.471,
  "modelAccuracy": 0.52,
  "season": 2025,
  "featureDeltas": {
    "ERA차": 0.15,
    "OPS차": 0.022,
    "타자WAR차": 2.1,
    "투수WAR차": -0.5
  }
}
```

### 모델 재학습

```http
POST /api/analysis/advanced/win-prediction/train?startSeason=2020&endSeason=2024
```

**응답:**

```json
{
  "trainingSamples": 2793,
  "accuracy": 0.52,
  "trainedSeasons": "2020~2024"
}
```

## 데이터 흐름

```
1. TeamStatsRepository.findAllTeamStats(season) → 팀별 ERA, OPS, WAR 조회
2. Feature 생성: 홈팀 - 원정팀 차이 계산
3. Z-score 정규화 (학습 시 저장된 μ, σ 사용)
4. σ(w · x + b) → 홈팀 승리 확률
5. 범위 제한 (0.25 ~ 0.75)
```

## 파일 구조

| 파일 | 역할 |
|------|------|
| `WinPredictionService.java` | 모델 학습 + 예측 로직 |
| `WinPredictionDTO.java` | 예측 결과 + 학습 결과 DTO |
| `AdvancedAnalysisRestController.java` | REST API 엔드포인트 |
| `ScheduleRepository.java` | `findAllFinishedBySeason()` — 학습 데이터 |
| `TeamStatsRepository.java` | `findAllTeamStats()` — 팀 스탯 |

## 한계 및 참고사항

- **정확도**: 약 52% (KBO 홈 승률 자체가 ~54%이므로 단순 홈 이점 수준)
- **시즌 스탯 기반**: 개별 경기의 선발 투수, 당일 컨디션 등은 반영하지 않음
- **데이터 부족 시**: 팀 스탯이 없으면 기본값 0.54 (홈 이점) 반환
- **Thread Safety**: `volatile` 필드 + `synchronized` 학습 블록으로 동시성 보장
