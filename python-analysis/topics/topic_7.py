"""
Topic 7: 관중 동원 트렌드 - 구단 인기와 성적의 상관관계

가설검정:
1. 승률-관중 상관 (Pearson)
2. 요일별 관중 차이 (ANOVA)
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    crowd = db.get_crowd_stats(season)
    rankings = db.get_team_rankings(season)

    if crowd.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # ==================== 팀별 평균 관중 ====================
    team_crowd = crowd.groupby('teamName')['crowd'].agg(['mean', 'sum', 'count']).reset_index()
    team_crowd.columns = ['teamName', 'avg_crowd', 'total_crowd', 'games']
    team_crowd['avg_crowd'] = team_crowd['avg_crowd'].round(0).astype(int)
    team_crowd = team_crowd.sort_values('avg_crowd', ascending=False)

    stats_dict['팀별_평균관중'] = team_crowd.to_dict('records')
    stats_dict['관중_기술통계'] = StatsUtils.descriptive(crowd['crowd'].dropna().values, '관중수')

    charts.append(ChartBuilder.bar(
        '팀별 평균 관중',
        team_crowd['teamName'].tolist(),
        [{'label': '평균 관중', 'data': team_crowd['avg_crowd'].tolist()}]
    ))

    # ==================== 요일별 관중 ====================
    if 'gameDate' in crowd.columns:
        crowd['gameDate'] = pd.to_datetime(crowd['gameDate'], errors='coerce')
        crowd['dayOfWeek'] = crowd['gameDate'].dt.dayofweek  # 0=월 ~ 6=일
        day_labels = ['월', '화', '수', '목', '금', '토', '일']

        day_crowd = crowd.groupby('dayOfWeek')['crowd'].mean().round(0)
        stats_dict['요일별_평균관중'] = {day_labels[int(k)]: int(v) for k, v in day_crowd.items() if not np.isnan(k)}

        charts.append(ChartBuilder.line(
            '요일별 평균 관중',
            [day_labels[int(k)] for k in sorted(day_crowd.index)],
            [{'label': '평균 관중', 'data': [int(day_crowd[k]) for k in sorted(day_crowd.index)]}]
        ))

        # ANOVA: 요일별 관중 차이
        day_groups = {}
        for day_idx, group in crowd.groupby('dayOfWeek'):
            vals = group['crowd'].dropna().values
            if len(vals) >= 3:
                day_groups[day_labels[int(day_idx)]] = vals

        if len(day_groups) >= 3:
            hypothesis['요일별_관중_ANOVA'] = StatsUtils.anova(day_groups)
            findings.append(f"요일별 관중 차이: {hypothesis['요일별_관중_ANOVA']['interpretation']}")

    # ==================== 승률-관중 상관 ====================
    if not rankings.empty:
        merged = team_crowd.merge(rankings[['teamName', 'winRate']], on='teamName', how='inner')
        if len(merged) >= 5:
            hypothesis['승률_관중_상관'] = StatsUtils.correlation(
                merged['winRate'].values, merged['avg_crowd'].values.astype(float),
                '승률', '평균 관중'
            )
            findings.append(f"승률-관중 상관: {hypothesis['승률_관중_상관']['interpretation']}")

    findings.insert(0, f"리그 평균 관중: {stats_dict['관중_기술통계']['mean']:.0f}명")
    insight = StatsUtils.format_insight('관중 동원 트렌드', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
