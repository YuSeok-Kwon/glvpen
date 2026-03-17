# KBO 데이터 종합 분석 프로젝트 계획서

**프로젝트명**: glvpen 데이터 분석 파이프라인
**작성일**: 2026-02-28
**데이터 범위**: 2020\~2025 시즌 (1군), 2022~2025 (퓨처스)
**총 Entity**: 29개 (분석 대상: 약 20개)

---

## 프로젝트 개요

glvpen에 축적된 6시즌 분량의 KBO 데이터를 활용하여, 3가지 분석 전략을 병렬로 수행한다. 각 전략은 독립적인 노트북(ipynb)으로 구성하되, 공통 데이터 추출 레이어를 공유한다.

### 비즈니스 목표 → 데이터 마이닝 목표 변환

| 비즈니스 목표                    | 데이터 마이닝 목표                     | 분석 유형          |
| -------------------------------- | -------------------------------------- | ------------------ |
| 다음 시즌 브레이크아웃 선수 예측 | 시즌별 성과 지표 변화율 기반 성장 예측 | 시계열 회귀        |
| 경기 승패 핵심 요인 파악         | 라인업/매치업 변수와 승패의 상관 분석  | 분류 + 탐색적 분석 |
| 관중 동원 극대화 전략 도출       | 관중수에 영향을 미치는 변수 식별       | 회귀 + 요인 분석   |

---

## 공통 인프라: 데이터 추출 레이어

### 필요 테이블 및 추출 SQL

분석 시작 전, MySQL에서 아래 테이블들을 CSV로 추출해야 한다.

```sql
-- 실행 방법: MySQL CLI 또는 DBeaver에서 실행 후 CSV Export

-- [공통] 선수 기본 정보
SELECT * FROM player;

-- [공통] 팀 기본 정보
SELECT * FROM team;

-- [전략1] 타자 시즌 스탯 (정규시즌만)
SELECT * FROM batterStats WHERE series = '0';

-- [전략1] 투수 시즌 스탯 (정규시즌만)
SELECT * FROM pitcherStats WHERE series = '0';

-- [전략1] 수비 스탯
SELECT * FROM defenseStats WHERE series = '0';

-- [전략1] 주루 스탯
SELECT * FROM runnerStats WHERE series = '0';

-- [전략2] 경기 일정 (종료된 경기)
SELECT * FROM schedule WHERE status = '종료';

-- [전략2] 스코어보드
SELECT * FROM scoreBoard;

-- [전략2] 타자 라인업
SELECT * FROM batterLineup;

-- [전략2] 타자 경기 기록
SELECT * FROM batterRecord;

-- [전략2] 투수 경기 기록
SELECT * FROM pitcherRecord;

-- [전략2] 경기 하이라이트
SELECT * FROM gameHighlight;

-- [전략2] 팀 순위
SELECT * FROM teamRanking;

-- [전략2] 상대전적
SELECT * FROM team_head_to_head;

-- [전략2] 팀 스탯
SELECT * FROM teamStats;

-- [전략3] 관중현황
SELECT * FROM crowd_stats;

-- [전략3] 퓨처스 타자 스탯
SELECT * FROM futures_batter_stats;

-- [전략3] 퓨처스 투수 스탯
SELECT * FROM futures_pitcher_stats;

-- [전략3] 퓨처스 팀 스탯
SELECT * FROM futures_team_stats;
```

### 데이터 저장 구조

```
/data/
  ├── raw/                    # CSV 원본
  │   ├── player.csv
  │   ├── team.csv
  │   ├── batter_stats.csv
  │   ├── pitcher_stats.csv
  │   ├── defense_stats.csv
  │   ├── runner_stats.csv
  │   ├── schedule.csv
  │   ├── score_board.csv
  │   ├── batter_lineup.csv
  │   ├── batter_record.csv
  │   ├── pitcher_record.csv
  │   ├── game_highlight.csv
  │   ├── team_ranking.csv
  │   ├── team_head_to_head.csv
  │   ├── team_stats.csv
  │   ├── crowd_stats.csv
  │   ├── futures_batter_stats.csv
  │   ├── futures_pitcher_stats.csv
  │   └── futures_team_stats.csv
  └── processed/              # 전처리 완료 데이터
      ├── batter_pivot.parquet
      ├── pitcher_pivot.parquet
      └── game_features.parquet
```

### 공통 전처리: EAV → 피벗 변환

BatterStats/PitcherStats는 EAV(Entity-Attribute-Value) 패턴이므로, 분석 전 반드시 피벗 변환이 필요하다.

```python
# EAV → 피벗 변환 예시
batter_pivot = (
    batter_stats
    .pivot_table(
        index=['playerId', 'season'],
        columns='category',
        values='value',
        aggfunc='first'
    )
    .reset_index()
)
# 결과: playerId | season | AVG | HR | OBP | SLG | OPS | BABIP | ISO | wRC_PLUS | ...
```

---

## 전략 1: 선수 성과 예측 및 가치 평가

### 1.1 문제 정의

**핵심 질문**: "2026 시즌에 브레이크아웃할 가능성이 높은 선수는 누구인가? 반대로 하락이 예상되는 선수는?"

**성공 기준**: 과거 시즌 데이터로 다음 시즌 주요 지표(AVG, OPS, ERA 등) 예측 시 MAPE 15% 이내

### 1.2 분석 항목

#### A. 나이-성과 커브 (Aging Curve)

**가설**: KBO에서도 MLB와 유사하게 타자 27~29세, 투수 26~28세에 피크를 찍는 패턴이 존재할 것이다.

**방법론**:

- Player.birthDate + season으로 시즌별 나이 산출
- 나이별 OPS/ERA 평균 곡선 그리기 (Delta Method)
- 포지션별 aging curve 차이 비교

**활용 데이터**: player (birthDate, position) + batterStats (AVG, OPS, wRC_PLUS) + pitcherStats (ERA, FIP)

**산출물**: 포지션별 aging curve 그래프, 현재 피크 전 선수 리스트

#### B. 행운 보정 분석 (Luck Adjustment)

**가설**: BABIP이 리그 평균(.300 내외)에서 크게 벗어난 선수는 다음 시즌 평균 회귀가 발생할 것이다.

**방법론**:

- 타자: BABIP vs 리그 평균 편차 계산 → 다음 시즌 AVG 변화와 상관 분석
- 투수: FIP-ERA 갭 분석 → 갭이 클수록 다음 시즌 ERA 변화 예측
- LOB%(잔루율) 극단값 선수 필터링

**활용 데이터**: batterStats (BABIP, AVG, OPS) + pitcherStats (ERA, FIP, xFIP)

**산출물**: 과대평가/과소평가 선수 리스트, FIP-ERA 갭 히스토그램

#### C. 다시즌 성장 추이 모델

**가설**: 최근 2~3시즌의 성적 추이(기울기)가 다음 시즌 성적의 유의미한 예측 변수이다.

**방법론**:

- 선수별 6시즌 주요 지표 시계열 구성
- 선형 회귀로 트렌드 기울기(slope) 산출
- Marcel Projection 방식 적용 (최근 시즌에 가중치 부여: 5/4/3)
- 나이 보정 계수 적용

**활용 데이터**: batterStats/pitcherStats (6시즌 전체), player (birthDate, debutYear)

**산출물**: 2026 시즌 예측 성적표, 브레이크아웃 후보 TOP 10

#### D. 포지션별 대체 가능 수준(Replacement Level) 분석

**가설**: 포지션별로 대체 가능 수준이 다르며, 이를 고려해야 실질적 선수 가치를 평가할 수 있다.

**방법론**:

- 포지션별 wRC+ 분포 비교
- 포지션별 상위/하위 분위수 차이 분석
- 포지션 희소성 지수 산출

**활용 데이터**: batterStats (wRC_PLUS, position), defenseStats

**산출물**: 포지션별 가치 매트릭스, 포지션 희소성 순위

### 1.3 노트북 구조

```
notebooks/
  01_선수성과예측/
    ├── 01_데이터_전처리_및_EDA.ipynb
    │     - CSV 로딩 + EAV 피벗 변환
    │     - 결측치/이상치 탐색
    │     - 기초 통계량 확인
    │
    ├── 02_나이성과커브_분석.ipynb
    │     - Aging curve 산출
    │     - 포지션별 비교
    │     - 피크 전 선수 필터링
    │
    ├── 03_행운보정_분석.ipynb
    │     - BABIP 편차 분석
    │     - FIP-ERA 갭 분석
    │     - 평균 회귀 검증
    │
    ├── 04_성장추이_예측모델.ipynb
    │     - Marcel Projection 구현
    │     - 시계열 트렌드 분석
    │     - 2026 시즌 예측
    │
    └── 05_포지션별_가치평가.ipynb
          - Replacement Level 산출
          - 포지션 희소성 분석
```

---

## 전략 2: 경기 전략 및 매치업 분석

### 2.1 문제 정의

**핵심 질문**: "팀 간 상대전적 편차가 발생하는 구조적 원인은 무엇인가? 라인업 구성이 경기 결과에 미치는 실질적 영향은?"

**성공 기준**: 경기 승패 예측 모델 정확도 60% 이상 (야구 특성상 이 수준이면 유의미)

### 2.2 분석 항목

#### A. 상대전적 패턴 분석

**가설**: 특정 팀 조합에서 시즌을 넘어 지속적으로 편향된 승패가 존재하며, 이는 투수진 구성이나 홈 구장 특성과 관련이 있을 것이다.

**방법론**:

- TeamHeadToHead 데이터로 시즌별 상대전적 매트릭스 구성
- 카이제곱 검정으로 기대 승률 대비 유의미한 편차 팀 조합 식별
- 편향된 조합의 경기 상세 분석 (어떤 투수가 어떤 팀에 강한가)

**활용 데이터**: team_head_to_head (6시즌), pitcherRecord + schedule (팀별 투수 성적)

**산출물**: 상대전적 히트맵, 유의미한 편향 조합 리스트

#### B. 이닝별 득점 패턴 분석

**가설**: 팀마다 고유한 득점 시간대 패턴이 존재하며(초반형 vs 후반형), 이 패턴은 팀의 라인업 구성 철학을 반영한다.

**방법론**:

- ScoreBoard.homeInningScores / awayInningScores 파싱 (CSV 형태)
- 팀별 이닝별 평균 득점 히트맵 생성
- 클러스터링으로 팀 유형 분류 (초반 폭발형, 꾸준형, 후반 역전형)
- 이닝별 득점 패턴과 최종 승패 상관관계

**활용 데이터**: scoreBoard (이닝별 점수), schedule (홈/원정, 결과)

**산출물**: 팀별 이닝별 득점 히트맵, 팀 유형 분류 결과

#### C. 라인업 효율성 분석

**가설**: 1~2번 타자의 출루율이 팀 득점에 미치는 영향이 클린업(3~5번)의 장타율보다 크다.

**방법론**:

- BatterLineup으로 타순별 선수 식별
- BatterRecord로 해당 경기 성적 매칭
- 타순 그룹별(상위-1,2번 / 클린업-3,4,5 / 하위-6,7,8,9) 기여도 비교
- 경기별 1-2번 OBP와 팀 총 득점의 상관계수 vs 3-5번 SLG와 팀 총 득점의 상관계수

**활용 데이터**: batterLineup (타순) + batterRecord (경기 기록) + schedule (결과)

**산출물**: 타순별 기여도 차트, 상관 분석 결과

#### D. 홈/원정 어드밴티지 분석

**가설**: KBO에서 홈 어드밴티지는 실제로 존재하며, 그 크기는 구장/팀에 따라 다르다.

**방법론**:

- Schedule 데이터에서 홈/원정 승률 비교
- 구장별 득점 환경 분석 (파크 팩터 산출)
- 팀별 홈/원정 성적 차이 비교
- 관중수(CrowdStats)와 홈 승률의 관계

**활용 데이터**: schedule, scoreBoard, crowd_stats, teamRanking

**산출물**: 구장별 파크 팩터 표, 홈 어드밴티지 크기 비교 차트

### 2.3 노트북 구조

```
notebooks/
  02_경기전략분석/
    ├── 01_데이터_전처리_및_EDA.ipynb
    │     - 경기 데이터 병합 (Schedule + ScoreBoard + Record)
    │     - 이닝별 점수 파싱
    │
    ├── 02_상대전적_패턴분석.ipynb
    │     - 상대전적 매트릭스 구성
    │     - 카이제곱 검정
    │     - 편향 원인 탐색
    │
    ├── 03_이닝별_득점패턴.ipynb
    │     - 이닝별 득점 히트맵
    │     - 팀 유형 클러스터링
    │
    ├── 04_라인업_효율성분석.ipynb
    │     - 타순별 기여도 분석
    │     - OBP vs SLG 영향력 비교
    │
    └── 05_홈원정_어드밴티지.ipynb
          - 파크 팩터 산출
          - 홈 어드밴티지 정량화
```

---

## 전략 3: 관중/흥행 요인 + 퓨처스 인재 발굴

### 3.1 문제 정의

**핵심 질문 (관중)**: "경기 관중 동원에 가장 큰 영향을 미치는 변수는 무엇이며, 구단이 통제 가능한 변수는 어떤 것인가?"

**핵심 질문 (퓨처스)**: "퓨처스 리그 성적 중 1군 성공을 예측하는 핵심 지표는 무엇인가?"

**성공 기준 (관중)**: 관중수 예측 모델 R² > 0.5
**성공 기준 (퓨처스)**: 퓨처스→1군 승격 선수의 1군 성공 여부 예측 정확도 65% 이상

### 3.2 분석 항목

#### A. 관중 동원 요인 분석

**가설**: 요일(주말), 대진 카드(인기팀 조합), 팀 순위가 관중수의 주요 결정 요인이며, 구장 수용 인원이 상한선으로 작용한다.

**방법론**:

- CrowdStats를 기반으로 피처 엔지니어링:
  - 요일 (dayOfWeek) → 주중/주말 이진 변수
  - 대진 카드 (homeTeamId, awayTeamId) → 팀 인기도 지수
  - 팀 순위 (TeamRanking 조인) → 홈팀/원정팀 순위
  - 월별 시즌 진행도 (gameDate → month)
  - 구장별 수용 인원 대비 점유율
- 다중 회귀 분석 + 랜덤 포레스트로 변수 중요도 산출
- 잔차 분석으로 설명되지 않는 요인 탐색 (날씨, 프로모션 등 외부 요인 추정)

**활용 데이터**: crowd_stats, teamRanking, schedule, team

**산출물**: 변수 중요도 차트, 요일/대진별 관중수 히트맵, 구장별 점유율 추이

#### B. 관중 트렌드 시계열 분석

**가설**: KBO 관중수는 코로나(2020~2021) 이후 회복 중이며, 회복 속도는 구단별로 차이가 있다.

**방법론**:

- 시즌별 월별 평균 관중수 시계열 구성
- 구단별 관중 회복률 비교 (2019 대비, 2019 데이터 없으면 2022 기준)
- 시즌 성적과 관중 변화의 시차 상관 분석 (Granger Causality)

**활용 데이터**: crowd_stats (2020~2025), teamRanking

**산출물**: 구단별 관중 추이 라인 차트, 성적-관중 시차 상관 분석 결과

#### C. 퓨처스→1군 성과 전이 분석

**가설**: 퓨처스 리그에서 특정 지표(타자: OPS/BB%, 투수: K/BB)가 높은 선수가 1군에서도 성공할 확률이 높다.

**방법론**:

- 퓨처스 스탯이 있는 선수 중 이후 1군 등장 기록이 있는 선수 식별
- 퓨처스 성적과 1군 첫 시즌/2시즌 성적 비교
- 상관 분석: 퓨처스 어떤 지표가 1군 성공(wRC+ > 100 또는 ERA < 4.50)과 가장 강한 상관?
- 로지스틱 회귀로 "1군 성공 확률" 예측 모델 구축

**활용 데이터**: futures_batter_stats, futures_pitcher_stats, batterStats, pitcherStats, player

**산출물**: 퓨처스-1군 성적 상관 매트릭스, 유망주 스카우팅 리포트

#### D. 퓨처스 유망주 발굴 모델

**가설**: 퓨처스 성적 + 나이 + 포지션을 조합하면 향후 1군 기여 가능성을 정량화할 수 있다.

**방법론**:

- 과거 퓨처스→1군 승격 사례를 학습 데이터로 구성
- 피처: 퓨처스 주요 지표, 나이, 포지션, 성장률(시즌간 변화)
- 랜덤 포레스트/XGBoost로 "1군 기여 확률" 예측
- 현재 퓨처스 선수에 적용하여 유망주 랭킹 생성

**활용 데이터**: futures_batter_stats, futures_pitcher_stats, player, batterStats/pitcherStats (1군 실적)

**산출물**: 유망주 랭킹 TOP 20, 피처 중요도 분석

### 3.3 노트북 구조

```
notebooks/
  03_관중퓨처스분석/
    ├── 01_관중_데이터_전처리.ipynb
    │     - CrowdStats 로딩 + 피처 엔지니어링
    │     - TeamRanking 조인
    │
    ├── 02_관중_동원요인_분석.ipynb
    │     - 다중 회귀 분석
    │     - 변수 중요도 산출
    │     - 잔차 분석
    │
    ├── 03_관중_트렌드_시계열.ipynb
    │     - 구단별 관중 추이
    │     - 성적-관중 시차 상관
    │
    ├── 04_퓨처스_1군_전이분석.ipynb
    │     - 승격 선수 식별
    │     - 퓨처스-1군 성적 상관
    │
    └── 05_유망주_발굴모델.ipynb
          - ML 모델 학습
          - 유망주 랭킹 생성
```

---

## 전체 프로젝트 일정

### Phase 0: 데이터 준비 (1일)

| 작업           | 설명                       | 산출물                  |
| -------------- | -------------------------- | ----------------------- |
| DB → CSV 추출 | 위 SQL 스크립트 실행       | raw/ 폴더 CSV 18개      |
| 공통 전처리    | EAV 피벗 변환, 결측치 처리 | processed/ 폴더 parquet |

### Phase 1: 탐색적 분석 - EDA (2~3일)

| 전략  | 핵심 EDA                                 | 검증 포인트                             |
| ----- | ---------------------------------------- | --------------------------------------- |
| 전략1 | 시즌별 선수 스탯 분포, 카테고리별 결측률 | EAV 피벗 후 데이터 완전성               |
| 전략2 | 경기 데이터 병합 정합성, 이닝 점수 파싱  | Schedule-ScoreBoard-Record 조인 키 일치 |
| 전략3 | 관중 분포, 퓨처스-1군 선수 매칭률        | 퓨처스 선수 중 1군 등장 비율            |

### Phase 2: 본 분석 (3~5일)

| 전략  | 분석 항목                                        | 핵심 방법론                          |
| ----- | ------------------------------------------------ | ------------------------------------ |
| 전략1 | Aging Curve, 행운 보정, 예측 모델, 포지션 가치   | 시계열 회귀, Marcel Projection       |
| 전략2 | 상대전적, 이닝 패턴, 라인업 효율, 홈 어드밴티지  | 카이제곱, 클러스터링, 상관 분석      |
| 전략3 | 관중 요인, 관중 트렌드, 퓨처스 전이, 유망주 모델 | 다중 회귀, 로지스틱 회귀, RF/XGBoost |

### Phase 3: 검증 및 문서화 (1~2일)

| 작업        | 방법                              |
| ----------- | --------------------------------- |
| 방법론 검증 | 교차 검증, 잔차 분석, 과적합 확인 |
| 결과 해석   | 도메인 지식 기반 sanity check     |
| 시각화 정리 | 핵심 차트 선별 + 대시보드 구성    |

---

## 기술 스택 (분석용)

| 분류        | 도구                                         |
| ----------- | -------------------------------------------- |
| 언어        | Python 3.10+                                 |
| 데이터 처리 | pandas, numpy                                |
| 시각화      | matplotlib, seaborn, plotly                  |
| 통계 분석   | scipy, statsmodels                           |
| 머신러닝    | scikit-learn, xgboost                        |
| 노트북      | Jupyter Notebook (ipynb)                     |
| 대시보드    | Chart.js (기존 플랫폼 연동) 또는 Plotly Dash |

---

## 리스크 및 대응

| 리스크                          | 영향                                  | 대응                                                      |
| ------------------------------- | ------------------------------------- | --------------------------------------------------------- |
| EAV 데이터 결측                 | 특정 시즌/선수의 카테고리 누락        | 결측 패턴 분석 후 보간 또는 해당 레코드 제외              |
| 퓨처스 데이터 부족              | 2022~2025 4시즌으로 ML 학습 표본 부족 | 단순 통계 분석 위주로 전환, ML은 보조적 사용              |
| 관중 외부 요인                  | 날씨/프로모션 데이터 부재             | 잔차 분석에서 '설명 불가 분산'으로 명시, 해석에 한계 기술 |
| 투수-타자 직접 대결 데이터 부재 | 매치업 분석 깊이 제한                 | 팀 단위 분석으로 대체, 하이라이트 데이터 보조 활용        |

---

## 핵심 인사이트 기대 방향

### 전략 1에서 기대하는 인사이트

- "25세 이하 + BABIP 저평가 + 최근 2시즌 wRC+ 상승 추세"인 선수가 2026 브레이크아웃 1순위
- 포지션별 대체 수준 차이를 감안한 실질 가치 순위 (유격수 OPS .750 > 1루수 OPS .800)

### 전략 2에서 기대하는 인사이트

- 특정 팀 조합에서 통계적으로 유의미한 상성 존재 여부 확인
- "1-2번 출루율 > .360인 날 팀 승률 X%"와 같은 실행 가능한(actionable) 인사이트

### 전략 3에서 기대하는 인사이트

- 관중 동원의 70%는 "요일 + 대진 카드 + 팀 순위"로 설명 가능 (나머지는 외부 요인)
- 퓨처스 BB%/K% 비율이 1군 성공의 가장 강력한 선행 지표

---

## 전략 4: 환경/상황 변수와 선수 퍼포먼스 상관 분석

### 4.1 문제 정의

**핵심 질문**: "선수 퍼포먼스에 영향을 미치는 외부/환경 변수(날씨, 구장, 계절, 관중, ABS 도입)는 무엇이며, 그 영향의 크기는?"

### 4.2 분석 항목

#### A. 날씨 × 선수 퍼포먼스

**가설**: 기온/습도가 타구 비거리와 투수 그립에 영향을 미쳐, 여름철(고온다습) 타자 유리/투수 불리 경향이 존재할 것이다.

**방법론**:

- 외부 데이터 필요: 기상청 API 또는 Open-Meteo API에서 경기일/경기장 소재지별 날씨 수집
  - 수집 항목: 기온, 습도, 풍속, 강수 여부
- Schedule.matchDate + Team.stadium(구장 소재지)로 날씨 매칭
- 기온 구간별(10도 미만/10~20/20~30/30도 이상) 타격 지표(AVG, HR, SLG) 비교
- 투수 지표(ERA, WHIP)와 기온의 상관 분석
- 강수 후 경기(습한 날)의 성적 차이 분석

**활용 데이터**: schedule + batterRecord + pitcherRecord + **외부: 기상 데이터**

**외부 데이터 수집 방법**:

```python
# Open-Meteo API (무료, API 키 불필요)
import requests

def get_weather(date, lat, lon):
    url = f"https://archive-api.open-meteo.com/v1/archive"
    params = {
        "latitude": lat, "longitude": lon,
        "start_date": date, "end_date": date,
        "hourly": "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation"
    }
    return requests.get(url, params=params).json()

# KBO 구장 좌표 매핑
STADIUM_COORDS = {
    "잠실": (37.5122, 127.0719),
    "고척": (37.4982, 126.8671),
    "수원": (37.2997, 127.0096),
    "대전": (36.3171, 127.4292),
    "대구": (35.8411, 128.6816),
    "사직": (35.1940, 129.0616),
    "광주": (35.1681, 126.8889),
    "창원": (35.2225, 128.5822),
    "인천": (37.4370, 126.6932),
    "문학": (37.4370, 126.6932),
}
```

**산출물**: 기온-타격지표 산점도, 날씨 조건별 성적 비교 박스플롯

#### B. 구장(파크 팩터) × 선수 퍼포먼스

**가설**: 구장마다 홈런/안타 발생률이 다르며, 특정 선수는 특정 구장에서 유독 강하거나 약한 패턴이 존재한다.

**방법론**:

- 구장별 파크 팩터 산출 (경기당 평균 득점/안타/홈런 비율)
  - Park Factor = (홈 경기 평균 득점) / (원정 경기 평균 득점)
- 선수별 구장별 성적 매트릭스 (BatterRecord에 scheduleId → schedule.stadium 조인)
- 특정 구장에서 유의미하게 다른 성적을 보이는 선수 식별 (Z-score 기반)
- 좌타/우타별 구장 영향 차이 분석 (Player.throwBat 활용)

**활용 데이터**: schedule (stadium) + batterRecord + pitcherRecord + player (throwBat)

**산출물**: 구장별 파크 팩터 표, 선수별 구장 친화도 히트맵

#### C. 월(계절) × 선수 퍼포먼스

**가설**: 선수마다 시즌 초반(4~5월)과 후반(8~9월)의 성적 편차가 존재하며, "상반기형/하반기형" 선수를 구분할 수 있다.

**방법론**:

- Schedule.matchDate에서 월 추출
- 월별 선수 성적 집계 (BatterRecord/PitcherRecord를 월별 그룹핑)
- 선수별 월별 성적 변동 계수(CV) 산출 → 안정성 지수
- 상반기(3~6월) vs 하반기(7~10월) 성적 차이 paired t-test
- 클러스터링으로 선수 유형 분류 (꾸준형, 상반기형, 하반기형, 롤러코스터형)

**활용 데이터**: schedule (matchDate) + batterRecord + pitcherRecord + player

**산출물**: 선수별 월별 성적 라인 차트, 선수 유형 분류표

#### D. 관중 × 선수 퍼포먼스 (클러치/초커 분석)

**가설**: 관중이 많은 경기(주말/빅매치)에서 유독 잘하는 선수(클러치)와 못하는 선수(초커)가 존재한다.

**방법론**:

- CrowdStats.crowd를 경기에 매칭 (gameDate + homeTeamId로 schedule 조인)
- 관중수 분위별 그룹 (하위 25% / 중간 50% / 상위 25%)
- 선수별 관중 그룹에 따른 성적 차이 분석
- 관중 상위 25% 경기에서의 OPS - 전체 OPS = "클러치 지수"
- 통계적 유의성 검정 (부트스트래핑으로 신뢰구간 산출)

**활용 데이터**: crowd_stats + schedule + batterRecord + pitcherRecord

**산출물**: 클러치/초커 선수 랭킹, 관중수-성적 상관 산점도

#### E. 2025 ABS(자동 볼-스트라이크 시스템) 도입 영향 분석

**가설**: ABS 도입으로 스트라이크 존이 정밀해지면서 BB%/K% 비율이 변화하고, 특히 프레이밍이 뛰어난 포수와 코너 활용 투수의 가치가 재편되었을 것이다.

**방법론**:

- ABS 도입 전(2020~2024) vs 도입 후(2025) 리그 전체 지표 비교
  - 리그 평균: BB%, K%, AVG, OBP, BABIP, FIP
  - 경기당 평균: 득점, 볼넷, 삼진, 사구(HBP)
- Difference-in-Differences 접근: 각 선수의 2024→2025 변화를 이전 시즌간 변화와 비교
- ABS 수혜자/피해자 식별
  - 투수: 2024 대비 K% 증가 상위 → ABS 적응 성공
  - 타자: 2024 대비 BB% 증가 상위 → 선구안 좋은 타자가 수혜
- 포수별 피패스볼/도루 허용 변화 (DefenseStats의 PB, SB, CS)

**활용 데이터**: batterStats + pitcherStats + defenseStats (2020~2025 전체)

**산출물**: ABS 전후 리그 지표 변화 대시보드, ABS 수혜/피해 선수 TOP 10

### 4.3 노트북 구조

```
notebooks/
  04_환경변수분석/
    ├── 01_날씨_데이터_수집_및_전처리.ipynb
    │     - Open-Meteo API로 날씨 데이터 수집
    │     - 경기별 날씨 매칭
    │
    ├── 02_날씨_선수퍼포먼스.ipynb
    │     - 기온/습도별 성적 비교
    │     - 강수 영향 분석
    │
    ├── 03_구장_파크팩터.ipynb
    │     - 구장별 파크 팩터 산출
    │     - 선수별 구장 친화도
    │
    ├── 04_월별_계절별_분석.ipynb
    │     - 월별 성적 추이
    │     - 상반기/하반기 유형 분류
    │
    ├── 05_관중_클러치분석.ipynb
    │     - 관중수별 성적 차이
    │     - 클러치/초커 지수 산출
    │
    └── 06_ABS_도입_영향분석.ipynb
          - ABS 전후 리그 지표 비교
          - DiD 분석
          - 수혜/피해 선수 식별
```

---

## 전략 5: 메이저리그식 세이버메트릭스 심층 분석

### 5.1 문제 정의

**핵심 질문**: "KBO에서도 MLB 수준의 세이버메트릭스 분석이 유효한가? KBO만의 특수성을 반영한 보정이 필요한 지표는?"

### 5.2 분석 항목

#### A. 기존 지표 검증 및 KBO 보정

**방법론**:

- 현재 SabermetricsCalculator로 계산 중인 지표 재검증
  - 타자: BABIP, ISO, K%, BB%, wRC+
  - 투수: FIP, xFIP, K/9, BB/9
- MLB FIP 상수(cFIP ≈ 3.10)를 KBO 리그 환경에 맞게 재산출
  - KBO cFIP = 리그 평균 ERA - (리그 평균 FIP without constant)
- wRC+ 산출 시 파크 팩터 보정 적용 여부 검토

**산출물**: KBO 보정 상수 표, 보정 전/후 순위 변화 비교

#### B. 추가 세이버메트릭스 지표 산출

**현재 없는 지표 중 산출 가능한 것**:

| 지표                        | 의미                | 산출 방법                       | 필요 데이터                         |
| --------------------------- | ------------------- | ------------------------------- | ----------------------------------- |
| RC (Runs Created)           | 득점 기여도         | (H+BB) × TB / (AB+BB)          | batterStats                         |
| RC/27                       | 27아웃당 득점 창출  | RC / (AB - H + GIDP + SF) × 27 | batterStats                         |
| SecA (Secondary Average)    | 순수 장타+출루 기여 | (TB - H + BB + SB) / AB         | batterStats + runnerStats           |
| SIERA                       | 투수 실력 지표      | K%, BB%, GB% 기반 복합 산식     | pitcherStats (GB% 필요 - 추정 가능) |
| WPA (Win Probability Added) | 승리 기여도         | 매 타석 승리 확률 변화 합산     | gameHighlight + 상황별 데이터       |
| Leverage Index              | 상황 중요도         | 이닝/주자/점수차 기반           | gameHighlight (beforeSituation)     |

**WPA 근사 산출 방법** (GameHighlight 활용):

```python
# GameHighlight의 beforeSituation/afterSituation으로 승리 확률 변화 추정
# 예: "0:0, 5회초, 1사 1,3루" → "2:0, 5회초, 2사 1루"
# → 사전 정의된 Win Expectancy 테이블에서 확률 차이 계산
```

**산출물**: 확장 세이버메트릭스 리더보드

#### C. 선수 가치 종합 평가 체계 (WAR 근사)

**가설**: DB에 있는 데이터만으로도 WAR에 근접한 종합 가치 지표를 산출할 수 있다.

**방법론**:

- Offensive WAR 근사: wRC+ 기반 타격 기여 + 주루 기여(RunnerStats SB/CS) + 수비 기여(DefenseStats FPCT, RF)
- Pitching WAR 근사: FIP 기반 투구 기여
- Replacement Level 설정: 포지션별 하위 20% 수준
- 산출 공식:
  ```
  Batting Runs = (wRC+ - 100) / 100 × PA × 리그평균R/PA
  Baserunning Runs = (SB × 0.2) - (CS × 0.4)  # 간이 산출
  Fielding Runs = (FPCT - 리그평균FPCT) × 이닝 × 보정계수
  WAR_approx = (Batting + Baserunning + Fielding + Positional Adj) / RPW
  ```

**활용 데이터**: batterStats (wRC_PLUS, OPS 등) + runnerStats (SB, CS) + defenseStats (FPCT, A, E) + pitcherStats (FIP, IP)

**산출물**: KBO WAR 근사 리더보드, 포지션별 WAR 분포

#### D. 팀 전력 세이버메트릭스 대시보드

**방법론**:

- 팀별 주요 세이버메트릭스 집계
  - 공격: 팀 wRC+, 팀 BABIP, 팀 ISO, 팀 BB%/K%
  - 투수: 팀 FIP, 팀 xFIP, 팀 K/9, 팀 BB/9
  - 수비: 팀 FPCT, 팀 DER(방어효율)
- 레이더 차트로 팀 전력 비교
- 시즌별 팀 전력 변화 추이

**산출물**: 10구단 전력 레이더 차트, 시즌별 전력 추이 대시보드

### 5.3 노트북 구조

```
notebooks/
  05_세이버메트릭스/
    ├── 01_기존지표_검증_KBO보정.ipynb
    │     - FIP 상수 재산출
    │     - wRC+ 파크 팩터 보정
    │
    ├── 02_추가지표_산출.ipynb
    │     - RC, RC/27, SecA 산출
    │     - WPA 근사 산출 (가능 범위)
    │
    ├── 03_WAR_근사_산출.ipynb
    │     - 타격/주루/수비 기여 분리
    │     - WAR 근사 리더보드
    │
    └── 04_팀전력_세이버_대시보드.ipynb
          - 팀별 세이버 집계
          - 레이더 차트 시각화
```

---

## 전략 6: 퓨처스-1군 연계 심층 분석

### 6.1 분석 항목

-> 단, 1군 선수가 못해서 기량회복, 혹은 부상 등의 이유로 퓨처스에 다녀오는 경우도 고려해야함.

#### A. 퓨처스→1군 성과 전이율 (선수 단위)

**방법론**:

- Player 테이블에서 퓨처스 스탯이 있는 선수 식별
- 해당 선수의 1군 스탯 존재 여부 확인 → 승격 선수 목록
- 퓨처스 각 지표와 1군 첫해 성적의 피어슨/스피어먼 상관계수
- 전이 지표 우선순위: 어떤 퓨처스 지표가 1군 성공을 가장 잘 예측하는가?

#### B. 퓨처스→1군 성과 전이율 (팀 단위)

**방법론**:

- 팀별 퓨처스 팀 성적(FuturesTeamStats)과 1군 팀 성적(TeamStats/TeamRanking) 비교
- 퓨처스 우승팀의 1군 기여도 분석
- 팀별 퓨처스 육성 효율 지수: (1군 승격 후 기여 선수 수) / (퓨처스 등록 선수 수)

**활용 데이터**: futures_batter_stats, futures_pitcher_stats, futures_team_stats, batterStats, pitcherStats, teamRanking, player

### 6.2 노트북 구조

```
notebooks/
  06_퓨처스1군연계/
    ├── 01_승격선수_식별_및_매칭.ipynb
    ├── 02_선수단위_전이분석.ipynb
    └── 03_팀단위_육성효율.ipynb
```

---

## 업데이트된 전체 프로젝트 구조

```
notebooks/
  ├── 00_공통_데이터로딩.ipynb        # CSV 로딩 + EAV 피벗 + 공통 전처리
  ├── 01_선수성과예측/                # 전략 1
  ├── 02_경기전략분석/                # 전략 2
  ├── 03_관중분석/                    # 전략 3 (관중 부분)
  ├── 04_환경변수분석/                # 전략 4 (신규)
  ├── 05_세이버메트릭스/              # 전략 5 (신규)
  └── 06_퓨처스1군연계/              # 전략 6 (전략3 퓨처스 분리 + 확장)
```

## 외부 데이터 소스 종합

### Tier 1: 즉시 사용 가능 (무료, API 키 불필요)

| 데이터                          | 출처                                                                            | 수집 방법                 | 관련 분석              | 비고                                         |
| ------------------------------- | ------------------------------------------------------------------------------- | ------------------------- | ---------------------- | -------------------------------------------- |
| 과거 날씨 (기온/습도/풍속/강수) | [Open-Meteo Historical API](https://open-meteo.com/en/docs/historical-weather-api) | Python requests           | 전략4-A 날씨×퍼포먼스 | 1940년~현재, 전 세계, 키 불필요, 비상업 무료 |
| KBO 구장 좌표                   | 수동 매핑                                                                       | 상수 딕셔너리 (10개 구장) | 전략4-A 날씨 매칭      | 한번만 정의하면 됨                           |
| ABS 도입 시점                   | 공식 발표                                                                       | 상수 (2025-03-22)         | 전략4-E ABS 분석       | 시즌 개막일 기준                             |

```python
# Open-Meteo 사용 예시 (API 키 불필요)
import requests

url = "https://archive-api.open-meteo.com/v1/archive"
params = {
    "latitude": 37.5122,    # 잠실구장
    "longitude": 127.0719,
    "start_date": "2025-04-01",
    "end_date": "2025-04-01",
    "hourly": "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation",
    "timezone": "Asia/Seoul"
}
response = requests.get(url, params=params).json()
```

### Tier 2: 가입 후 사용 가능 (무료, API 키 필요)

| 데이터                  | 출처                                                                                                     | 수집 방법          | 관련 분석              | 비고                              |
| ----------------------- | -------------------------------------------------------------------------------------------------------- | ------------------ | ---------------------- | --------------------------------- |
| 과거 날씨 (국내 상세)   | [기상청 기상자료개방포털](https://data.kma.go.kr/api/selectApiList.do)                                      | 공공데이터포털 API | 전략4-A 날씨×퍼포먼스 | 승인 1일 소요, 국내 관측소별 상세 |
| KBO 2018~2024 선수 성적 | [Kaggle KBO Dataset](https://www.kaggle.com/datasets/clementmsika/kbo-player-performance-dataset-2018-2024) | kaggle CLI         | 교차 검증용            | 기존 DB와 비교 검증 가능          |
| KBO 타자 데이터 (구형)  | [Kaggle KBO Baseball](https://www.kaggle.com/datasets/bluemumin/kbo-baseball-for-kaggle/data)               | kaggle CLI         | 전략1 OPS 예측 참고    | 2020년 게시, 다소 오래됨          |

```bash
# Kaggle 데이터 다운로드
pip install kaggle
kaggle datasets download -d clementmsika/kbo-player-performance-dataset-2018-2024
```

### Tier 3: 크롤링 필요 (추가 개발 필요)

| 데이터                    | 출처                                                                        | 수집 방법                   | 관련 분석                   | 비고                               |
| ------------------------- | --------------------------------------------------------------------------- | --------------------------- | --------------------------- | ---------------------------------- |
| 선수 연봉                 | 외부 KBO 데이터 사이트                                                         | Selenium 크롤링             | 전략1 가치평가, 전략5 WAR/$ | 이용약관 주의, 개인 학습 목적 사용 |
| FanGraphs KBO 세이버지표  | [FanGraphs International](https://www.fangraphs.com/leaders/international/kbo) | 회원 가입 후 CSV export     | 전략5 세이버 검증           | WAR 등 고급 지표 비교 검증 가능    |
| KBO 공식 선수 프로필      | [KBO 공식 사이트](https://www.koreabaseball.com/)                              | requests.post (동적 페이지) | 전략1 프로필 보강           | 기존 크롤러로 이미 수집 중         |
| MLB Win Expectancy 테이블 | 공개 참고자료                                                               | CSV 다운로드                | 전략5-B WPA 산출            | Fangraphs/Baseball Reference 참고  |

### Tier 4: 유료 또는 제한적

| 데이터                    | 출처                                                                 | 비용      | 관련 분석           | 비고                           |
| ------------------------- | -------------------------------------------------------------------- | --------- | ------------------- | ------------------------------ |
| 실시간 경기 데이터 피드   | [OddService](https://oddservice.com/sports-data-api/baseball-data-api/) | 유료      | 실시간 분석         | XML/JSON, MLB/KBO/NPB 지원     |
| Statcast 급 트래킹 데이터 | KBO 미공개                                                           | 접근 불가 | 타구 속도/각도 분석 | KBO는 현재 비공개 (MLB만 공개) |

### 외부 데이터 활용 시 분석 확장 가능성

| 추가 데이터   | 기존 분석 확장                     | 새로운 분석 가능                    |
| ------------- | ---------------------------------- | ----------------------------------- |
| 날씨 데이터   | 전략4-A 날씨×선수 퍼포먼스 완성   | 날씨 기반 경기 결과 예측 모델       |
| 연봉 데이터   | 전략1 가치평가에 "$/WAR" 산출 가능 | FA 시장 효율성 분석, 연봉 예측 모델 |
| Kaggle 데이터 | 기존 DB 교차 검증                  | 외부 데이터와 품질 비교             |
| FanGraphs WAR | 전략5 WAR 근사치 검증              | 자체 WAR vs FanGraphs WAR 오차 분석 |

### 우선순위 권장

```
Phase 0 (즉시): Open-Meteo 날씨 수집 → 전략4 바로 착수 가능
Phase 1 (1일):  기상청 API 신청 + Kaggle 다운로드
Phase 2 (2~3일): 연봉 크롤러 개발
Phase 3 (선택): FanGraphs 데이터 수동 수집
```

---

## 참고: 기존 플랫폼 연동 가능성

분석 결과는 glvpen 플랫폼의 기존 기능과 연동 가능하다.

| 분석 결과         | 연동 대상                  | 방법                     |
| ----------------- | -------------------------- | ------------------------ |
| 브레이크아웃 예측 | AnalysisColumn (분석 컬럼) | AI 자동 생성 기사로 발행 |
| 상대전적 패턴     | 대시보드 차트              | Chart.js Radar/Heatmap   |
| 관중 예측         | 경기 일정 페이지           | 예상 관중수 표시         |
| 유망주 랭킹       | 선수 프로필 페이지         | 퓨처스 잠재력 지수 표시  |
