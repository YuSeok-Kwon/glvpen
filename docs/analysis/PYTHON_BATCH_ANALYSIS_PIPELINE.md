# Python 배치 분석 파이프라인 설계

> **전략 3**: Python 스크립트를 배치 작업으로 실행 → 통계 분석 결과를 DB에 저장 → Java가 읽어서 Gemini 프롬프트에 포함

---

## 1. 배경 및 목적

### 기존 시스템의 한계 (전략 B: Java-only)

| 문제 | 설명 |
|------|------|
| **통계 분석 부재** | Java `ColumnDataCalculator`는 기술통계(평균, 정렬)만 제공. 가설검정 불가 |
| **차트 데이터 없음** | Gemini에게 텍스트 데이터만 전달. 기사에 시각화 첨부 불가 |
| **분석 깊이 한계** | Shapiro-Wilk, t-test, ANOVA, 상관분석 등 고급 통계는 Java에서 구현 비용 높음 |

### 전략 3 해결 방향

```
[전략 B]  DB → Java(ColumnDataCalculator) → 텍스트 데이터 → Gemini → 기사

[전략 3]  DB → Python(scipy/pandas) → 통계분석 + 가설검정 + Chart.js JSON
                                              │
                                              ▼
                                      analysis_result 테이블
                                              │
                                              ▼
                             Java(AiColumnGeneratorService) → Gemini → 기사 + 차트
```

**핵심 원칙**: Python이 통계 분석의 **모든 수치를 사전 계산**하고, Java는 이를 **그대로 Gemini에 전달**. ColumnDataCalculator는 **fallback**으로만 동작.

---

## 2. 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                    Python 배치 분석 (main.py)                    │
│                                                                 │
│  main.py --topic 0 --season 2025                                │
│       │                                                         │
│       ├─ config.py (DB설정, 15개 주제 정의, 색상 팔레트)         │
│       │                                                         │
│       ├─ common/                                                │
│       │   ├─ db_connector.py  (MySQL → DataFrame 피봇 변환)     │
│       │   ├─ stats_utils.py   (기술통계 + 가설검정 래퍼)        │
│       │   └─ chart_builder.py (Chart.js 4.4.1 호환 JSON)        │
│       │                                                         │
│       └─ topics/topic_0.py ~ topic_14.py (15개 분석 모듈)       │
│               │                                                 │
│               └─ analyze(season, db) → (stats, chart, insight,  │
│                                          hypothesis)            │
│                       │                                         │
│                       ▼                                         │
│               db.save_analysis_result()  [UPSERT]               │
│                                                                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
                ┌───────────────────────┐
                │   analysis_result     │
                │   (MySQL 중간 테이블)   │
                │                       │
                │  topic (0~14)         │
                │  season               │
                │  stats_json           │
                │  chart_json           │
                │  insight_text         │
                │  hypothesis_results   │
                └───────────┬───────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              Java (AiColumnGeneratorService)                     │
│                                                                 │
│  @Scheduled(cron = "0 0 6 * * THU")                             │
│  generateRotatingTopicColumn()                                  │
│       │                                                         │
│       ├─ analysisResultRepository.findByTopicAndSeason()        │
│       │                                                         │
│       ├─ [있으면] Python 분석 결과 기반 프롬프트 구성             │
│       │   ├─ stats_json    → "=== 통계 분석 데이터 ==="          │
│       │   ├─ insight_text  → "=== 통계적 인사이트 ==="           │
│       │   ├─ hypothesis    → "=== 가설검정 결과 ==="             │
│       │   └─ chart_json    → "=== 차트 참조 데이터 ==="          │
│       │                                                         │
│       ├─ [없으면] ColumnDataCalculator fallback                  │
│       │                                                         │
│       └─ GeminiClient.callGemini(prompt)                        │
│               │                                                 │
│               └─ parseAndSaveColumn() → AnalysisColumn 저장     │
│                                          + chartData 저장        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 디렉토리 구조

```
python-analysis/
├── main.py                 # CLI 진입점 (--topic, --season 인자)
├── config.py               # DB 설정, 15개 주제 매핑, 색상 팔레트
├── requirements.txt        # 의존성 (pandas, scipy, mysql-connector-python, python-dotenv)
├── .env.example            # 환경변수 템플릿
├── common/
│   ├── __init__.py
│   ├── db_connector.py     # MySQL 커넥터 + DataFrame 피봇 + UPSERT
│   ├── stats_utils.py      # 통계 분석 유틸 (기술통계, 가설검정)
│   └── chart_builder.py    # Chart.js 4.4.1 호환 JSON 빌더
└── topics/
    ├── __init__.py
    ├── topic_0.py           # 세이버메트릭스 트렌드
    ├── topic_1.py           # WAR 스포트라이트
    ├── topic_2.py           # 브레이크아웃 후보
    ├── topic_3.py           # 행운 보정 분석
    ├── topic_4.py           # 상대전적 패턴
    ├── topic_5.py           # 이닝별 득점 패턴
    ├── topic_6.py           # 홈/원정 & 파크팩터
    ├── topic_7.py           # 관중 동원 트렌드
    ├── topic_8.py           # 퓨처스 유망주 스카우팅
    ├── topic_9.py           # ABS 도입 영향 분석
    ├── topic_10.py          # 클러치/초커 분석
    ├── topic_11.py          # 나이-성과 커브
    ├── topic_12.py          # 라인업 효율성
    ├── topic_13.py          # 월별/계절별 선수 유형
    └── topic_14.py          # 포지션별 가치 분석
```

---

## 4. 핵심 컴포넌트 상세

### 4.1 main.py - CLI 진입점

**실행 방법**:
```bash
# 전체 15개 주제 분석 (기본 시즌: 2025)
python main.py

# 특정 주제만 분석
python main.py --topic 0

# 특정 시즌 분석
python main.py --season 2024

# 조합
python main.py --topic 3 --season 2024
```

**실행 흐름**:
1. CLI 인자 파싱 (`argparse`)
2. `DBConnector` 인스턴스 생성
3. `run_topic()` 루프 (전체 또는 단일)
4. 각 토픽 모듈의 `analyze(season, db)` 호출
5. 반환값 4-tuple → `db.save_analysis_result()` UPSERT
6. 성공/실패 카운트 출력, 실패 시 `exit(1)`

**반환값 계약**: 모든 토픽 모듈은 반드시 4-tuple을 반환해야 함:
```python
(stats_json: str, chart_json: str, insight_text: str, hypothesis_json: str)
```

### 4.2 config.py - 설정

| 설정 | 값 | 설명 |
|------|-----|------|
| `DB_CONFIG` | `.env`에서 로드 | MySQL 접속 정보 (host, port, user, password, database) |
| `CURRENT_SEASON` | `2025` (기본값) | 분석 대상 시즌 |
| `ANALYSIS_TOPICS` | 15개 한국어 제목 | Java `ROTATING_TOPICS` 배열과 인덱스 매핑 |
| `SIGNIFICANCE_LEVEL` | `0.05` | 가설검정 유의수준 |
| `CHART_COLORS` | 10색 팔레트 | Chart.js 호환 HEX 색상 |
| `TEAM_COLORS` | KBO 10개 구단 | 구단별 대표 색상 매핑 |

**주제 인덱스 매핑** (Java `ROTATING_TOPICS`와 1:1 대응):

| # | Python (`ANALYSIS_TOPICS`) | Java (`ROTATING_TOPICS`) | 카테고리 |
|---|---------------------------|--------------------------|---------|
| 0 | 세이버메트릭스 트렌드 | 세이버메트릭스 트렌드: wRC+, FIP, xFIP로 보는 리그 판도 | trend |
| 1 | WAR 스포트라이트 | 선수 스포트라이트: WAR 상위 선수 심층 분석 | player |
| 2 | 브레이크아웃 후보 | 브레이크아웃 후보: 올 시즌 급성장 선수 분석 | player |
| 3 | 행운 보정 분석 | 행운 보정 분석: BABIP과 FIP-ERA 갭으로 보는 진짜 실력 | trend |
| 4 | 상대전적 패턴 | 상대전적 패턴: 팀 간 맞대결 숨은 데이터 | game |
| 5 | 이닝별 득점 패턴 | 이닝별 득점 패턴: 경기 흐름을 바꾸는 결정적 순간 | game |
| 6 | 홈/원정 & 파크팩터 | 홈/원정 & 구장 환경 분석: 파크팩터와 숨겨진 변수 | game |
| 7 | 관중 동원 트렌드 | 관중 동원 트렌드: 구단 인기와 성적의 상관관계 | trend |
| 8 | 퓨처스 유망주 스카우팅 | 퓨처스 유망주 스카우팅: 내일의 KBO 스타 | player |
| 9 | ABS 도입 영향 분석 | ABS 도입 영향 분석: 스트라이크존 변화가 바꾼 것들 | trend |
| 10 | 클러치/초커 분석 | 클러치/초커 분석: 위기 상황에 강한 선수, 약한 선수 | player |
| 11 | 나이-성과 커브 | 나이-성과 커브: KBO 선수들의 피크 시즌은 언제인가 | player |
| 12 | 라인업 효율성 분석 | 라인업 효율성 분석: 타순별 기대 생산량과 실제 성적 | game |
| 13 | 월별/계절별 선수 유형 | 월별/계절별 선수 유형: 시즌 구간별 강한 선수는 누구인가 | player |
| 14 | 포지션별 가치 분석 | 포지션별 가치 분석: 어떤 포지션이 팀 승리에 가장 기여하는가 | player |

### 4.3 common/db_connector.py - 데이터 액세스

**피봇 변환 패턴**: Stats 테이블은 EAV(Entity-Attribute-Value) 구조로 저장됨. `DBConnector`는 이를 피봇 변환하여 분석에 적합한 wide-format DataFrame으로 반환.

```
[DB 원본 - Long Format]             [피봇 결과 - Wide Format]
playerId | category | value         playerId | WAR | AVG | OPS | HR | ...
    1    |  WAR     |  3.2    →        1     | 3.2 | .312| .850|  25 | ...
    1    |  AVG     |  .312            2     | 4.1 | .298| .920|  30 | ...
    1    |  OPS     |  .850
    2    |  WAR     |  4.1
```

**제공 쿼리 메서드**:

| 메서드 | 반환 | 용도 |
|--------|------|------|
| `get_batters(season)` | 시즌 전체 타자 피봇 DataFrame | 주제 0~3, 10~14 |
| `get_batters_multi_season(seasons)` | 다시즌 타자 DataFrame | 주제 1, 2, 9 (전년 비교) |
| `get_pitchers(season)` | 시즌 전체 투수 피봇 DataFrame | 주제 0~3, 14 |
| `get_pitchers_multi_season(seasons)` | 다시즌 투수 DataFrame | 주제 1, 9 |
| `get_players_with_birth(season)` | 선수 생년월일 DataFrame | 주제 11 (나이 분석) |
| `get_team_rankings(season)` | 팀 순위 DataFrame | 주제 6, 7 |
| `get_head_to_head(season)` | 상대전적 DataFrame | 주제 4 |
| `get_schedules(season)` | 완료 경기 일정 DataFrame | 주제 5, 6, 13 |
| `get_score_boards(season)` | 이닝별 스코어보드 DataFrame | 주제 5 |
| `get_crowd_stats(season)` | 관중 데이터 DataFrame | 주제 7 |
| `get_futures_batters(season)` | 퓨처스 타자 피봇 DataFrame | 주제 8 |
| `get_futures_pitchers(season)` | 퓨처스 투수 피봇 DataFrame | 주제 8 |
| `save_analysis_result(...)` | - | UPSERT (topic+season 유니크) |

**UPSERT 로직**: `INSERT ... ON DUPLICATE KEY UPDATE` — topic+season 조합이 이미 존재하면 갱신, 없으면 삽입.

### 4.4 common/stats_utils.py - 통계 분석 엔진

scipy.stats 기반 가설검정 래퍼. 모든 결과는 **JSON 직렬화 가능한 딕셔너리**로 반환.

| 메서드 | 통계 기법 | 사용 목적 | 반환 키 |
|--------|----------|----------|---------|
| `descriptive(data, label)` | 기술통계 | 평균, 중앙값, 표준편차, 사분위수 | n, mean, median, std, min, max, q1, q3 |
| `normality_test(data, label)` | Shapiro-Wilk | 정규분포 여부 판정 (n<5000) | statistic, p_value, is_normal |
| `t_test_ind(g1, g2, ...)` | Welch's t-test | 두 독립 그룹 평균 비교 | t_statistic, p_value, significant, mean_diff |
| `t_test_paired(before, after, ...)` | 쌍표본 t-검정 | 동일 대상 전후 비교 | t_statistic, p_value, significant, n_pairs |
| `anova(groups)` | One-way ANOVA | 3개+ 그룹 평균 비교 | f_statistic, p_value, significant, group_means |
| `correlation(x, y, ...)` | Pearson 상관분석 | 두 연속 변수 선형 관계 | correlation, p_value, significant, strength |
| `chi_square(observed, label)` | 카이제곱 독립성 | 범주형 변수 연관성 | chi2_statistic, p_value, significant, dof |
| `format_insight(title, findings)` | - | 인사이트 포맷팅 | 텍스트 문자열 |

**유의수준**: 모든 검정에서 `p < 0.05`를 기준으로 `significant: True/False` 판정.

**자동 해석**: 각 검정 결과에 `interpretation` 필드로 한국어 해석 포함.
- t-검정: "유의미한 차이 있음 (p=0.0023)" / "유의미한 차이 없음 (p=0.3421)"
- 상관분석: "강한 양의 상관관계 (r=0.7234, p=0.0001)"
- ANOVA: "그룹 간 유의미한 차이 있음 (F=5.23, p=0.0012)"

### 4.5 common/chart_builder.py - 시각화 JSON 빌더

Chart.js 4.4.1 호환 딕셔너리 생성. `column-detail.html`에서 다중 차트 배열(`[{}, {}, ...]`)로 렌더링.

| 메서드 | 차트 타입 | chartType 값 | 색상 자동 할당 |
|--------|----------|-------------|---------------|
| `bar(title, labels, datasets)` | 막대 차트 | `bar` | `backgroundColor` |
| `line(title, labels, datasets)` | 선형 차트 | `line` | `borderColor` + tension 0.4 |
| `radar(title, labels, datasets)` | 레이더 차트 | `radar` | `borderColor` + 20% 투명 배경 |
| `doughnut(title, labels, data, colors)` | 도넛 차트 | `doughnut` | 커스텀 또는 팔레트 |
| `stacked_bar(title, labels, datasets)` | 누적 막대 차트 | `bar` | + `scales.x/y.stacked: true` |
| `scatter(title, datasets)` | 산점도 | `scatter` | `backgroundColor` |

**출력 형식 예시**:
```json
{
    "chartType": "bar",
    "title": "리그 평균 세이버메트릭스 지표",
    "labels": ["wRC+", "BABIP", "ISO", "K%", "BB%"],
    "datasets": [{
        "label": "2025 시즌",
        "data": [100.23, 0.298, 0.145, 22.1, 8.5],
        "backgroundColor": "#FF6384"
    }]
}
```

---

## 5. 토픽 모듈 상세

### 공통 인터페이스

모든 `topic_N.py`는 동일한 함수 시그니처를 구현:

```python
def analyze(season: int, db: DBConnector) -> tuple[str, str, str, str]:
    """
    Returns:
        stats_json: 기술통계 결과 (JSON 문자열)
        chart_json: Chart.js 호환 차트 배열 (JSON 문자열)
        insight_text: 통계적 인사이트 (텍스트)
        hypothesis_json: 가설검정 결과 (JSON 문자열)
    """
```

### 토픽별 분석 내용

#### 주제 0: 세이버메트릭스 트렌드

| 항목 | 내용 |
|------|------|
| **기술통계** | 리그 평균 wRC+, BABIP, ISO, K%, BB% (타자) / FIP, xFIP, K/9, BB/9 (투수) |
| **가설검정** | wRC+ 정규성(Shapiro-Wilk), WAR 상위/하위 wRC+ 차이(t-test), ERA-FIP 상관(Pearson) |
| **차트** | Bar(리그 평균 지표), Bar(WAR 상위 5명), Radar(투수 지표) |

#### 주제 1: WAR 스포트라이트

| 항목 | 내용 |
|------|------|
| **기술통계** | WAR 상위 10명 타자/투수 |
| **가설검정** | 전년/현년 WAR 쌍표본 t-test (paired), 포지션별 WAR ANOVA |
| **차트** | Bar(상위 10명), Bar(상승폭 상위 5명), Bar(포지션별 평균 WAR), Doughnut(포지션별 WAR 비중) |

#### 주제 2: 브레이크아웃 후보

| 항목 | 내용 |
|------|------|
| **기술통계** | 전년 대비 WAR, wRC+, ISO 성장폭 상위 선수 |
| **가설검정** | 전년 대비 wRC+ 쌍표본 t-test |
| **차트** | Bar(성장폭 상위 5명), Line(WAR 변화 추이) |

#### 주제 3: 행운 보정 분석

| 항목 | 내용 |
|------|------|
| **기술통계** | BABIP 리그 평균 대비 편차, FIP-ERA 갭 |
| **가설검정** | BABIP 상위/하위 그룹 OPS 비교(t-test), FIP-ERA 상관(Pearson) |
| **차트** | Bar(BABIP 편차 상위/하위), Scatter(FIP vs ERA) |

#### 주제 4: 상대전적 패턴

| 항목 | 내용 |
|------|------|
| **기술통계** | 팀 간 승률 격차 매칭 |
| **가설검정** | 홈/원정 승률 차이 t-test, 상위/하위 상대전적 차이 분석 |
| **차트** | Bar(승률 격차 상위 매칭), Stacked Bar(팀별 상대전적) |

#### 주제 5: 이닝별 득점 패턴

| 항목 | 내용 |
|------|------|
| **기술통계** | 팀별 이닝별(1~9회) 평균 득점, 초반/중반/후반 구간 |
| **가설검정** | 초반 vs 후반 득점 t-test, 팀별 이닝 패턴 ANOVA |
| **차트** | Line(이닝별 득점 곡선), Stacked Bar(구간별 비교) |

#### 주제 6: 홈/원정 & 파크팩터

| 항목 | 내용 |
|------|------|
| **기술통계** | 팀별 홈/원정 승률, 구장별 평균 득점, 파크팩터 |
| **가설검정** | 홈/원정 승률 쌍표본 t-test, 파크팩터 구간 ANOVA |
| **차트** | Bar(홈/원정 승률 비교), Bar(파크팩터) |

#### 주제 7: 관중 동원 트렌드

| 항목 | 내용 |
|------|------|
| **기술통계** | 팀별 평균 관중, 요일별 평균 관중 |
| **가설검정** | 순위 vs 관중 Pearson 상관분석, 주중/주말 관중 t-test |
| **차트** | Bar(팀별 평균 관중), Line(요일별 관중 추이) |

#### 주제 8: 퓨처스 유망주 스카우팅

| 항목 | 내용 |
|------|------|
| **기술통계** | 퓨처스 타자 AVG/OPS 상위, 투수 ERA/WHIP 상위 |
| **가설검정** | 1군 vs 퓨처스 주요 지표 비교 t-test |
| **차트** | Bar(유망 타자 TOP5), Bar(유망 투수 TOP5), Radar(1군 vs 퓨처스 지표) |

#### 주제 9: ABS 도입 영향 분석

| 항목 | 내용 |
|------|------|
| **기술통계** | 전년 대비 K%, BB%, K/9, BB/9 변화 |
| **가설검정** | K% 전후 쌍표본 t-test, BB% 전후 쌍표본 t-test |
| **차트** | Bar(전년 대비 지표 변화), Line(K%, BB% 시즌 간 추이) |

#### 주제 10: 클러치/초커 분석

| 항목 | 내용 |
|------|------|
| **기술통계** | 타율(AVG) vs 득점권 타율(RISP) 갭 |
| **가설검정** | AVG vs RISP 쌍표본 t-test, 클러치/초커 그룹 OPS 비교 |
| **차트** | Bar(클러치 상위 5명), Bar(초커 상위 5명), Scatter(AVG vs RISP) |

#### 주제 11: 나이-성과 커브

| 항목 | 내용 |
|------|------|
| **기술통계** | 나이 구간별(20-23, 24-26, 27-29, 30-32, 33+) 평균 WAR |
| **가설검정** | 나이 구간별 WAR ANOVA, 피크 구간 전후 비교 t-test |
| **차트** | Bar(나이 구간별 평균 WAR), Line(나이-WAR 커브) |

#### 주제 12: 라인업 효율성 분석

| 항목 | 내용 |
|------|------|
| **기술통계** | 포지션 그룹별(C/IF/OF/DH) 평균 wRC+, OPS, WAR |
| **가설검정** | 포지션 그룹 간 wRC+ ANOVA |
| **차트** | Bar(포지션별 평균 wRC+), Radar(포지션별 공격력 비교) |

#### 주제 13: 월별/계절별 선수 유형

| 항목 | 내용 |
|------|------|
| **기술통계** | 팀별 월별 승률, 상반기/하반기 변화 |
| **가설검정** | 상반기 vs 하반기 승률 쌍표본 t-test |
| **차트** | Line(월별 승률 추이), Bar(상반기/하반기 승률 비교) |

#### 주제 14: 포지션별 가치 분석

| 항목 | 내용 |
|------|------|
| **기술통계** | 포지션별 평균 WAR, wRC+, OPS, 대체 수준(하위 20%) |
| **가설검정** | 포지션 간 WAR ANOVA, 주요 포지션 쌍 비교 t-test |
| **차트** | Bar(포지션별 평균 WAR), Doughnut(포지션별 WAR 비중), Bar(상위 3명) |

---

## 6. 데이터 흐름

### 6.1 Python → DB (배치 실행)

```
[수동 실행: python main.py --season 2025]
        │
        ▼
    topic_N.analyze(season, db)
        │
        ├── db.get_batters(season)          → DataFrame (피봇 변환)
        ├── db.get_pitchers(season)         → DataFrame (피봇 변환)
        ├── StatsUtils.descriptive()        → 기술통계 딕셔너리
        ├── StatsUtils.t_test_ind()         → 가설검정 딕셔너리
        ├── StatsUtils.anova()              → ANOVA 결과 딕셔너리
        ├── StatsUtils.correlation()        → 상관분석 딕셔너리
        ├── ChartBuilder.bar()              → Chart.js JSON 딕셔너리
        └── StatsUtils.format_insight()     → 인사이트 텍스트
                │
                ▼
        json.dumps() → 4개 문자열
                │
                ▼
        db.save_analysis_result(topic, season, stats, chart, insight, hypothesis)
                │
                ▼
        INSERT ... ON DUPLICATE KEY UPDATE → analysis_result 테이블
```

### 6.2 DB → Java → Gemini (스케줄 실행)

```
[매주 목요일 06:00 KST]
        │
        ▼
AiColumnGeneratorService.generateRotatingTopicColumn()
        │
        ├── weekOfYear % 15 → topicIndex 결정
        │
        ├── analysisResultRepository.findByTopicAndSeason(topicIndex, season)
        │       │
        │       ├── [존재] Python 결과 기반 프롬프트
        │       │     ├── stats_json     → 통계 데이터 블록
        │       │     ├── insight_text   → 인사이트 블록
        │       │     ├── hypothesis     → 가설검정 블록
        │       │     └── chart_json     → 차트 참조 블록
        │       │     + buildEnhancedWritingRules(true)
        │       │       └── "가설검정 결과를 기사에 자연스럽게 인용하세요"
        │       │
        │       └── [미존재] ColumnDataCalculator fallback
        │             └── calculator.calcXxx(season) → 텍스트 데이터
        │             + buildEnhancedWritingRules(false)
        │
        ├── GeminiClient.callGemini(prompt)
        │       └── Gemini API (gemini-flash-latest)
        │           재시도: 2회, 15초 대기 (429 Too Many Requests)
        │
        └── parseAndSaveColumn()
                ├── 제목 파싱 (## 헤더)
                ├── ##SUMMARY## 분리
                ├── AnalysisColumn 엔티티 저장
                └── chartData = chart_json (Python 결과 시)
```

---

## 7. analysis_result 테이블 스키마

```sql
CREATE TABLE analysis_result (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic       INT NOT NULL,                    -- 주제 인덱스 (0~14)
    season      INT NOT NULL,                    -- 분석 시즌
    stats_json  LONGTEXT NOT NULL,               -- 기술통계 JSON
    chart_json  LONGTEXT NOT NULL,               -- Chart.js 호환 차트 배열 JSON
    insight_text LONGTEXT NOT NULL,              -- 통계 인사이트 텍스트
    hypothesis_results LONGTEXT,                 -- 가설검정 결과 JSON
    created_at  DATETIME NOT NULL DEFAULT NOW(), -- 생성 시각
    updated_at  DATETIME DEFAULT NOW(),          -- 수정 시각
    UNIQUE KEY uk_topic_season (topic, season)   -- UPSERT 키
);
```

**Java 엔티티**: `AnalysisResult.java` (`modules/analysis/domain/`)

---

## 8. Java 연동 상세

### 8.1 AiColumnGeneratorService 분기 로직

```java
// 1. Python 분석 결과 우선 조회
Optional<AnalysisResult> analysisResultOpt =
    analysisResultRepository.findByTopicAndSeason(topicIndex, CURRENT_SEASON);

if (analysisResultOpt.isPresent()) {
    // Python 결과 기반: 통계 + 인사이트 + 가설검정 + 차트 → 프롬프트
    AnalysisResult result = analysisResultOpt.get();
    prompt.append("=== 통계 분석 데이터 ===\n").append(result.getStatsJson());
    prompt.append("=== 통계적 인사이트 ===\n").append(result.getInsightText());
    prompt.append("=== 가설검정 결과 ===\n").append(result.getHypothesisResults());
    prompt.append("=== 차트 참조 데이터 ===\n").append(result.getChartJson());
    chartDataForColumn = result.getChartJson();  // AnalysisColumn에도 저장
} else {
    // Fallback: ColumnDataCalculator 사용
    String preComputedData = switch (topicIndex) { ... };
    prompt.append("=== 사전 분석 데이터 ===\n").append(preComputedData);
}
```

### 8.2 향상된 작성 규칙

Python 분석 결과가 있을 때 추가되는 Gemini 작성 규칙:

```
- 가설검정 결과를 기사에 자연스럽게 인용하세요 (예: "통계적으로 유의미한 차이가 확인되었습니다 (p<0.05)")
- 통계적 인사이트를 독자가 이해할 수 있도록 쉬운 언어로 설명하세요
- 차트 데이터에 언급된 수치를 기사 본문에서 함께 다뤄주세요
- 기술통계(평균, 표준편차)와 가설검정 결과를 적절히 혼합하여 근거를 제시하세요
```

### 8.3 스케줄 정리

| 스케줄 | 주기 | 메서드 | 데이터 소스 |
|--------|------|--------|------------|
| 팀별 주간 분석 | 매주 월요일 06:00 | `generateTeamWeeklyColumn()` | 팀 순위 + 상세 데이터 (Python 미사용) |
| 로테이션 특집 | 매주 목요일 06:00 | `generateRotatingTopicColumn()` | **Python 우선** → Calculator fallback |
| 시즌 개요 | 수동 | `generateSeasonOverviewColumn()` | 기본 통계 (Python 미사용) |
| 주제별 생성 | 수동 | `generateColumnByTopic()` | 기본 통계 (Python 미사용) |

---

## 9. 전략 B vs 전략 3 비교

| 항목 | 전략 B (Java-only) | 전략 3 (Python 배치) |
|------|-------------------|---------------------|
| **분석 엔진** | `ColumnDataCalculator` (Java) | `topics/topic_N.py` (Python + scipy) |
| **통계 기법** | 평균, 정렬, 비교 | + Shapiro-Wilk, t-test, ANOVA, Pearson, chi-square |
| **출력 형식** | 텍스트 데이터 블록 (String) | JSON (stats + chart + insight + hypothesis) |
| **차트 지원** | 없음 | Chart.js 4.4.1 호환 JSON |
| **데이터 저장** | 없음 (매번 재계산) | `analysis_result` 테이블 (UPSERT) |
| **실행 방식** | Java 내부 호출 | CLI 수동 실행 (`python main.py`) |
| **Gemini 프롬프트** | 데이터 블록 + 기본 규칙 | 통계 + 인사이트 + 가설검정 + 차트 + 향상된 규칙 |
| **역할** | Fallback (Python 결과 없을 때) | 기본 데이터 소스 |

---

## 10. 확장 가이드

### 새 주제 추가 (16번째 이상)

1. **Python**: `topics/topic_15.py` 생성 — `analyze(season, db)` 구현
2. **Python**: `config.py`의 `ANALYSIS_TOPICS`에 제목 추가
3. **Python**: `main.py`의 `TOPIC_MODULES` 리스트에 import 추가
4. **Java**: `AiColumnGeneratorService.ROTATING_TOPICS` 배열에 `{"제목", "카테고리"}` 추가
5. **Java**: `ColumnDataCalculator`에 fallback 메서드 추가 (선택)
6. **Java**: `generateRotatingTopicColumn()`의 switch에 `case 15 ->` 추가 (fallback용)

### 새 통계 기법 추가

`stats_utils.py`에 `@staticmethod` 메서드 추가:
- Mann-Whitney U 검정 (비모수)
- 회귀분석 (scipy.stats.linregress)
- 효과 크기 (Cohen's d)

### 새 차트 타입 추가

`chart_builder.py`에 `@staticmethod` 메서드 추가:
- 히트맵 (행렬 데이터 → Chart.js matrix 플러그인)
- 박스 플롯 (Chart.js box 플러그인)

---

## 11. 운영 가이드

### 환경 설정

```bash
cd python-analysis
cp .env.example .env
# .env 파일에 DB 접속 정보 설정

pip install -r requirements.txt
```

### 실행 예시

```bash
# 2025 시즌 전체 분석 (약 2~5분 소요)
python main.py --season 2025

# 특정 주제만 재분석
python main.py --topic 0 --season 2025

# 과거 시즌 분석
python main.py --season 2024
python main.py --season 2023
```

### 결과 확인

```sql
-- 저장된 분석 결과 확인
SELECT topic, season, LENGTH(stats_json), LENGTH(chart_json), updated_at
FROM analysis_result
ORDER BY season DESC, topic ASC;

-- 특정 주제 인사이트 확인
SELECT insight_text FROM analysis_result WHERE topic = 0 AND season = 2025;
```

### 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| `mysql.connector.errors.ProgrammingError` | DB 접속 정보 오류 | `.env` 파일 확인 |
| 특정 토픽 실패 (나머지 성공) | 해당 시즌 데이터 부족 | `--topic N`으로 개별 재실행, DB 데이터 확인 |
| `ValueError: not enough values to unpack` | 피봇 변환 실패 | Stats 테이블에 해당 시즌 데이터 존재 여부 확인 |
| Java에서 Python 결과 미사용 | `findByTopicAndSeason` 조회 실패 | topic 인덱스 매핑 확인 (0~14) |
