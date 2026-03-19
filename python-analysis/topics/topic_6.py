"""
Topic 6: 홈/원정 & 구장 환경 분석 - 파크팩터와 숨겨진 변수

가설검정:
1. 홈/원정 승패 비율 (카이제곱)
2. 홈 vs 원정 득점 차이 (t-test)
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    schedules = db.get_schedules(season)
    if schedules.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # ==================== 홈/원정 승률 분석 ====================
    schedules['homeWin'] = (schedules['homeScore'] > schedules['awayScore']).astype(int)
    schedules['awayWin'] = (schedules['awayScore'] > schedules['homeScore']).astype(int)

    home_wins = schedules['homeWin'].sum()
    away_wins = schedules['awayWin'].sum()
    total = len(schedules)
    draws = total - home_wins - away_wins

    stats_dict['홈_승률'] = round(home_wins / total, 3)
    stats_dict['원정_승률'] = round(away_wins / total, 3)
    stats_dict['홈승_수'] = int(home_wins)
    stats_dict['원정승_수'] = int(away_wins)

    # 팀별 홈/원정 승률
    team_home = schedules.groupby('homeTeamName').agg(
        홈승=('homeWin', 'sum'), 홈경기=('homeWin', 'count')
    ).reset_index()
    team_home['홈승률'] = round(team_home['홈승'] / team_home['홈경기'], 3)

    team_away = schedules.groupby('awayTeamName').agg(
        원정승=('awayWin', 'sum'), 원정경기=('awayWin', 'count')
    ).reset_index()
    team_away['원정승률'] = round(team_away['원정승'] / team_away['원정경기'], 3)

    stats_dict['팀별_홈승률'] = team_home.set_index('homeTeamName')['홈승률'].to_dict()
    stats_dict['팀별_원정승률'] = team_away.set_index('awayTeamName')['원정승률'].to_dict()

    # ==================== 구장별 파크팩터 ====================
    stadium_stats = schedules.groupby('stadium').agg(
        avg_total_score=('homeScore', lambda x: x.values.sum()),
        games=('stadium', 'count')
    ).reset_index()
    # 실제 파크팩터: 해당 구장 평균 득점 / 리그 평균 득점
    league_avg_score = (schedules['homeScore'].mean() + schedules['awayScore'].mean())
    stadium_stats['park_factor'] = np.round(
        (schedules.groupby('stadium')['homeScore'].mean() +
         schedules.groupby('stadium')['awayScore'].mean()).values / league_avg_score, 3
    )
    stats_dict['구장별_파크팩터'] = stadium_stats[['stadium', 'park_factor', 'games']].to_dict('records')

    # ==================== 가설검정 ====================
    # 1. 홈/원정 승패 카이제곱
    observed = np.array([[home_wins, away_wins], [away_wins, home_wins]])
    hypothesis['홈원정_승패_균등성'] = StatsUtils.chi_square(observed, '홈/원정 승패')
    findings.append(f"홈/원정 승패: {hypothesis['홈원정_승패_균등성']['interpretation']}")

    # 2. 홈/원정 득점 차이
    hypothesis['홈원정_득점_차이'] = StatsUtils.t_test_ind(
        schedules['homeScore'].values, schedules['awayScore'].values,
        '홈팀 득점', '원정팀 득점'
    )
    findings.append(f"홈/원정 득점 차이: {hypothesis['홈원정_득점_차이']['interpretation']}")

    # ==================== 차트 ====================
    charts.append(ChartBuilder.doughnut(
        '홈/원정 승률',
        ['홈 승리', '원정 승리', '무승부'],
        [home_wins, away_wins, draws]
    ))

    # 팀별 홈/원정 승률 비교
    all_teams = sorted(set(team_home['homeTeamName']) & set(team_away['awayTeamName']))
    if all_teams:
        charts.append(ChartBuilder.bar(
            '팀별 홈/원정 승률 비교',
            all_teams,
            [
                {'label': '홈 승률', 'data': [stats_dict['팀별_홈승률'].get(t, 0) for t in all_teams]},
                {'label': '원정 승률', 'data': [stats_dict['팀별_원정승률'].get(t, 0) for t in all_teams]},
            ]
        ))

    findings.insert(0, f"전체 홈 승률: {stats_dict['홈_승률']:.3f}, 원정 승률: {stats_dict['원정_승률']:.3f}")
    insight = StatsUtils.format_insight('홈/원정 & 파크팩터 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False, default=str),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
