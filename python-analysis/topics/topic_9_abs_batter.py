"""
Topic 9A: ABS 도입이 타자에게 미친 영향

가설검정:
1. ABS 전후 K% 변화 (쌍표본 t-test)
2. ABS 전후 BB% 변화 (쌍표본 t-test)
3. ABS 전후 AVG 변화 (쌍표본 t-test)
4. ABS 전후 OPS 변화 (쌍표본 t-test)
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    prev_season = season - 1
    batters_multi = db.get_batters_multi_season([prev_season, season])

    if batters_multi.empty:
        return '{}', '[]', '타자 데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    metrics = ['K%', 'BB%', 'AVG', 'OPS', 'HR', 'ISO']
    tested_metrics = []

    for metric in metrics:
        if metric not in batters_multi.columns:
            continue

        prev = batters_multi[batters_multi['season'] == prev_season]
        curr = batters_multi[batters_multi['season'] == season]

        prev_vals = prev.groupby('playerId')[metric].first()
        curr_vals = curr.groupby('playerId')[metric].first()
        common = prev_vals.index.intersection(curr_vals.index)

        if len(common) >= 10:
            paired = pd.DataFrame({'prev': prev_vals.loc[common], 'curr': curr_vals.loc[common]}).dropna()
            if len(paired) < 10:
                continue
            stats_dict[f'타자_{metric}_{prev_season}'] = StatsUtils.descriptive(
                paired['prev'].values, f'{prev_season} {metric}'
            )
            stats_dict[f'타자_{metric}_{season}'] = StatsUtils.descriptive(
                paired['curr'].values, f'{season} {metric}'
            )

            hypothesis[f'타자_{metric}_전후비교'] = StatsUtils.t_test_paired(
                paired['prev'].values,
                paired['curr'].values,
                f'타자 {metric} ({prev_season} vs {season})'
            )
            findings.append(f"타자 {metric}: {hypothesis[f'타자_{metric}_전후비교']['interpretation']}")
            tested_metrics.append(metric)

    # 차트: 각 지표별 전후 비교
    for m in tested_metrics[:4]:
        prev_key = f'타자_{m}_{prev_season}'
        curr_key = f'타자_{m}_{season}'
        if prev_key in stats_dict and curr_key in stats_dict:
            charts.append(ChartBuilder.bar(
                f'타자 {m} ABS 전후 비교',
                [str(prev_season), str(season)],
                [{'label': m, 'data': [stats_dict[prev_key]['mean'], stats_dict[curr_key]['mean']]}]
            ))

    # 종합 변화량 차트
    if len(tested_metrics) >= 2:
        change_labels = []
        change_values = []
        for m in tested_metrics:
            prev_key = f'타자_{m}_{prev_season}'
            curr_key = f'타자_{m}_{season}'
            if prev_key in stats_dict and curr_key in stats_dict:
                change_labels.append(m)
                change_values.append(round(stats_dict[curr_key]['mean'] - stats_dict[prev_key]['mean'], 4))

        charts.append(ChartBuilder.bar(
            f'타자 지표 변화량 ({prev_season}→{season})',
            change_labels,
            [{'label': '변화량', 'data': change_values}]
        ))

    # 가장 크게 변한 선수 Top 5
    if 'AVG' in batters_multi.columns:
        prev = batters_multi[batters_multi['season'] == prev_season].drop_duplicates('playerId').set_index('playerId')
        curr = batters_multi[batters_multi['season'] == season].drop_duplicates('playerId').set_index('playerId')
        common_ids = prev.index.intersection(curr.index)
        if len(common_ids) >= 5:
            diff = (curr.loc[common_ids, 'AVG'] - prev.loc[common_ids, 'AVG']).dropna()
            top5_improve = diff.nlargest(5)
            improve_names = []
            improve_vals = []
            for pid in top5_improve.index:
                if pid in curr.index and 'playerName' in curr.columns:
                    improve_names.append(curr.loc[pid, 'playerName'])
                    improve_vals.append(round(float(top5_improve[pid]), 4))
            if improve_names:
                charts.append(ChartBuilder.bar(
                    'ABS 이후 타율 상승 Top 5',
                    improve_names,
                    [{'label': 'AVG 변화', 'data': improve_vals}]
                ))
                stats_dict['타율_상승_Top5'] = [
                    {'name': n, 'change': v} for n, v in zip(improve_names, improve_vals)
                ]

    findings.insert(0, f"ABS 타자 영향 분석: {prev_season} vs {season}")
    insight = StatsUtils.format_insight('ABS 도입 - 타자 영향', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
