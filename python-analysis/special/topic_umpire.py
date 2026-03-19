"""
특집: 심판-선수/팀 궁합 분석

주의: 현재 DB에 심판(umpire) 테이블이 없으므로
이 모듈은 향후 크롤링 파이프라인 추가 후 활성화.
현재는 스캐폴드(구조)만 제공.

향후 필요 데이터:
- umpire 테이블: (id, name)
- game_umpire 테이블: (scheduleId, umpireId, position)
  position: 주심/1루심/2루심/3루심

분석 계획:
1. 특정 심판이 주심일 때 팀별 승률 (카이제곱)
2. 특정 심판-투수 조합별 삼진율 (ANOVA)
3. 특정 심판 경기의 평균 볼넷수 vs 전체 평균 (t-test)
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


TOPIC_INDEX = 16  # 특집 토픽 번호


def analyze(season: int, db: DBConnector) -> tuple:
    """
    심판 데이터가 DB에 존재하면 분석 수행.
    없으면 안내 메시지 반환.
    """
    # 심판 테이블 존재 여부 확인
    try:
        check = db.query("SHOW TABLES LIKE 'game_umpire'")
        if check.empty:
            return (
                '{}', '[]',
                '[심판 궁합 분석]\n- 아직 심판 데이터(game_umpire)가 DB에 없습니다.\n'
                '- 크롤링 파이프라인에 심판 데이터 수집을 추가하면 활성화됩니다.\n'
                '- 필요 테이블: umpire(id, name), game_umpire(scheduleId, umpireId, position)',
                '{}'
            )
    except:
        pass

    # 심판 데이터 존재 시 분석 로직
    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    try:
        # 심판별 경기 데이터 조회
        umpire_games = db.query("""
            SELECT gu.umpireId, u.name AS umpireName, gu.position,
                   s.homeTeamId, s.awayTeamId, s.homeScore, s.awayScore,
                   t1.name AS homeTeamName, t2.name AS awayTeamName,
                   s.gameDate
            FROM game_umpire gu
            JOIN umpire u ON gu.umpireId = u.id
            JOIN kbo_schedule s ON gu.scheduleId = s.id
            JOIN team t1 ON s.homeTeamId = t1.id
            JOIN team t2 ON s.awayTeamId = t2.id
            WHERE s.season = %s AND s.status = '완료'
              AND gu.position = '주심'
        """, (season,))

        if umpire_games.empty:
            return '{}', '[]', '심판 경기 데이터 없음', '{}'

        # 심판별 경기 수 / 홈팀 승률
        for umpire_name in umpire_games['umpireName'].unique():
            ump_data = umpire_games[umpire_games['umpireName'] == umpire_name]
            if len(ump_data) < 10:
                continue

            # 홈팀 승률 계산
            home_wins = (ump_data['homeScore'] > ump_data['awayScore']).sum()
            total = len(ump_data)
            home_wr = home_wins / total if total > 0 else 0

            stats_dict[f'{umpire_name}_홈승률'] = {
                'games': total,
                'homeWinRate': round(home_wr, 4),
            }

        # 팀별 특정 심판과의 궁합
        teams = umpire_games['homeTeamName'].unique()
        for team in teams:
            team_home = umpire_games[umpire_games['homeTeamName'] == team]
            if len(team_home) < 5:
                continue

            umpire_records = []
            for ump in team_home['umpireName'].unique():
                ump_games = team_home[team_home['umpireName'] == ump]
                if len(ump_games) >= 3:
                    wins = (ump_games['homeScore'] > ump_games['awayScore']).sum()
                    umpire_records.append({
                        'umpire': ump,
                        'games': len(ump_games),
                        'wins': int(wins),
                        'winRate': round(wins / len(ump_games), 4),
                    })

            if umpire_records:
                stats_dict[f'{team}_심판궁합'] = sorted(
                    umpire_records, key=lambda x: x['winRate'], reverse=True
                )

        # 가설검정: 심판별 홈팀 승률 차이 (ANOVA)
        umpire_groups = {}
        for ump in umpire_games['umpireName'].unique():
            ump_data = umpire_games[umpire_games['umpireName'] == ump]
            if len(ump_data) >= 10:
                home_results = (ump_data['homeScore'] > ump_data['awayScore']).astype(float).values
                umpire_groups[ump] = home_results

        if len(umpire_groups) >= 3:
            hypothesis['심판별_홈승률_ANOVA'] = StatsUtils.anova(
                {k: np.array(v) for k, v in umpire_groups.items()}
            )
            findings.append(f"심판별 홈팀 승률 차이: {hypothesis['심판별_홈승률_ANOVA']['interpretation']}")

        # 차트
        if stats_dict:
            ump_names = []
            ump_games_count = []
            for key, val in stats_dict.items():
                if key.endswith('_홈승률'):
                    ump_names.append(key.replace('_홈승률', ''))
                    ump_games_count.append(val['homeWinRate'])

            if ump_names:
                charts.append(ChartBuilder.bar(
                    '심판별 홈팀 승률',
                    ump_names[:15],
                    [{'label': '홈팀 승률', 'data': ump_games_count[:15]}]
                ))

        findings.insert(0, f"심판-팀 궁합 분석 ({season})")
        insight = StatsUtils.format_insight('심판 궁합 분석', findings)

    except Exception as e:
        return (
            '{}', '[]',
            f'[심판 궁합 분석]\n- 심판 데이터 조회 중 오류: {str(e)}\n'
            '- game_umpire/umpire 테이블 구조를 확인해주세요.',
            '{}'
        )

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
