"""
Topic 9: ABS 도입 영향 분석 - 스트라이크존 변화가 바꾼 것들

가설검정:
1. ABS 도입 전후 K% 쌍표본 t-test
2. ABS 도입 전후 BB% 쌍표본 t-test
"""
import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    # ABS 도입 전(season-1)과 후(season) 비교
    prev_season = season - 1
    batters_multi = db.get_batters_multi_season([prev_season, season])
    pitchers_multi = db.get_pitchers_multi_season([prev_season, season])

    if batters_multi.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # ==================== 타자 K%, BB% 변화 ====================
    for metric in ['K%', 'BB%', 'AVG', 'OPS']:
        if metric not in batters_multi.columns:
            continue

        prev = batters_multi[batters_multi['season'] == prev_season]
        curr = batters_multi[batters_multi['season'] == season]

        prev_vals = prev.set_index('playerId')[metric]
        curr_vals = curr.set_index('playerId')[metric]
        common = prev_vals.index.intersection(curr_vals.index)

        if len(common) >= 10:
            stats_dict[f'타자_{metric}_{prev_season}'] = StatsUtils.descriptive(
                prev_vals.loc[common].dropna().values, f'{prev_season} {metric}'
            )
            stats_dict[f'타자_{metric}_{season}'] = StatsUtils.descriptive(
                curr_vals.loc[common].dropna().values, f'{season} {metric}'
            )

            hypothesis[f'타자_{metric}_전후비교'] = StatsUtils.t_test_paired(
                prev_vals.loc[common].dropna().values,
                curr_vals.loc[common].dropna().values,
                f'타자 {metric} ({prev_season} vs {season})'
            )
            findings.append(f"타자 {metric} 변화: {hypothesis[f'타자_{metric}_전후비교']['interpretation']}")

    # ==================== 투수 K/9, BB/9 변화 ====================
    for metric in ['K/9', 'BB/9', 'ERA']:
        if pitchers_multi.empty or metric not in pitchers_multi.columns:
            continue

        prev = pitchers_multi[pitchers_multi['season'] == prev_season]
        curr = pitchers_multi[pitchers_multi['season'] == season]

        prev_vals = prev.set_index('playerId')[metric]
        curr_vals = curr.set_index('playerId')[metric]
        common = prev_vals.index.intersection(curr_vals.index)

        if len(common) >= 10:
            hypothesis[f'투수_{metric}_전후비교'] = StatsUtils.t_test_paired(
                prev_vals.loc[common].dropna().values,
                curr_vals.loc[common].dropna().values,
                f'투수 {metric} ({prev_season} vs {season})'
            )
            findings.append(f"투수 {metric} 변화: {hypothesis[f'투수_{metric}_전후비교']['interpretation']}")

    # ==================== 차트 ====================
    # 전후 비교 Bar
    metrics_to_chart = ['K%', 'BB%']
    for m in metrics_to_chart:
        prev_key = f'타자_{m}_{prev_season}'
        curr_key = f'타자_{m}_{season}'
        if prev_key in stats_dict and curr_key in stats_dict:
            charts.append(ChartBuilder.bar(
                f'타자 {m} 전후 비교',
                [f'{prev_season}', f'{season}'],
                [{'label': m, 'data': [stats_dict[prev_key]['mean'], stats_dict[curr_key]['mean']]}]
            ))

    # 종합 변화 Line
    change_metrics = []
    change_prev = []
    change_curr = []
    for m in ['K%', 'BB%', 'AVG']:
        prev_key = f'타자_{m}_{prev_season}'
        curr_key = f'타자_{m}_{season}'
        if prev_key in stats_dict and curr_key in stats_dict:
            change_metrics.append(m)
            change_prev.append(stats_dict[prev_key]['mean'])
            change_curr.append(stats_dict[curr_key]['mean'])

    if change_metrics:
        charts.append(ChartBuilder.bar(
            'ABS 도입 전후 주요 지표 변화',
            change_metrics,
            [
                {'label': f'{prev_season}', 'data': change_prev},
                {'label': f'{season}', 'data': change_curr},
            ]
        ))

    findings.insert(0, f"ABS 도입 전후 비교: {prev_season} vs {season}")
    insight = StatsUtils.format_insight('ABS 도입 영향 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
