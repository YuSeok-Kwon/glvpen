"""
Topic 13: 월별/계절별 선수 유형 - 시즌 구간별 강한 선수는 누구인가

가설검정:
1. 월별 팀 승률 차이 (ANOVA)
2. 상반기 vs 하반기 승률 (t-test)
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

    # 월별 분석
    schedules['gameDate'] = pd.to_datetime(schedules['gameDate'], errors='coerce')
    schedules['month'] = schedules['gameDate'].dt.month

    # ==================== 팀별 월별 승률 ====================
    monthly_records = {}
    teams = set(schedules['homeTeamName']) | set(schedules['awayTeamName'])

    for team in teams:
        home_games = schedules[schedules['homeTeamName'] == team].copy()
        home_games['win'] = (home_games['homeScore'] > home_games['awayScore']).astype(int)
        away_games = schedules[schedules['awayTeamName'] == team].copy()
        away_games['win'] = (away_games['awayScore'] > away_games['homeScore']).astype(int)

        all_games = pd.concat([
            home_games[['month', 'win']],
            away_games[['month', 'win']],
        ])

        monthly_wr = all_games.groupby('month')['win'].mean().round(3)
        monthly_records[team] = monthly_wr.to_dict()

    stats_dict['팀별_월별_승률'] = monthly_records

    # ==================== 리그 월별 평균 ====================
    all_monthly = []
    for team, months in monthly_records.items():
        for m, wr in months.items():
            all_monthly.append({'month': m, 'winRate': wr, 'team': team})

    monthly_df = pd.DataFrame(all_monthly)
    league_monthly = monthly_df.groupby('month')['winRate'].mean().round(3)
    stats_dict['리그_월별_평균승률'] = league_monthly.to_dict()

    # ==================== 상반기 vs 하반기 ====================
    first_half = monthly_df[monthly_df['month'] <= 6]['winRate'].values
    second_half = monthly_df[monthly_df['month'] >= 7]['winRate'].values

    if len(first_half) >= 5 and len(second_half) >= 5:
        hypothesis['상반기_하반기_승률'] = StatsUtils.t_test_ind(
            first_half, second_half, '상반기(3-6월)', '하반기(7-10월)'
        )
        findings.append(f"상반기 vs 하반기: {hypothesis['상반기_하반기_승률']['interpretation']}")

    # ==================== 월별 승률 ANOVA ====================
    month_groups = {}
    for month, group in monthly_df.groupby('month'):
        vals = group['winRate'].values
        if len(vals) >= 3:
            month_groups[f'{int(month)}월'] = vals

    if len(month_groups) >= 3:
        hypothesis['월별_승률_ANOVA'] = StatsUtils.anova(month_groups)
        findings.append(f"월별 승률 차이: {hypothesis['월별_승률_ANOVA']['interpretation']}")

    # ==================== 차트 ====================
    # Line: 리그 월별 평균 승률
    months_sorted = sorted(league_monthly.index)
    charts.append(ChartBuilder.line(
        '리그 월별 평균 승률',
        [f'{int(m)}월' for m in months_sorted],
        [{'label': '평균 승률', 'data': [league_monthly[m] for m in months_sorted]}]
    ))

    # 상위 3팀 월별 추이
    team_total_wr = {team: np.mean(list(months.values()))
                     for team, months in monthly_records.items() if months}
    top3_teams = sorted(team_total_wr.items(), key=lambda x: x[1], reverse=True)[:3]

    datasets = []
    for team, _ in top3_teams:
        data = [monthly_records[team].get(m, 0) for m in months_sorted]
        datasets.append({'label': team, 'data': data})

    if datasets:
        charts.append(ChartBuilder.line(
            '상위 3팀 월별 승률 추이',
            [f'{int(m)}월' for m in months_sorted],
            datasets
        ))

    findings.insert(0, f"분석 대상: {len(teams)}팀, {len(schedules)}경기")
    insight = StatsUtils.format_insight('월별/계절별 패턴 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
