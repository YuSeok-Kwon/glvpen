"""
Topic 9B: ABS 도입이 투수에게 미친 영향

가설검정:
1. ABS 전후 K/9 변화 (쌍표본 t-test)
2. ABS 전후 BB/9 변화 (쌍표본 t-test)
3. ABS 전후 ERA 변화 (쌍표본 t-test)
4. ABS 전후 WHIP 변화 (쌍표본 t-test)
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from common.filters import filter_pitchers_multi_season


def analyze(season: int, db: DBConnector) -> tuple:
    prev_season = season - 1
    pitchers_multi = filter_pitchers_multi_season(db.get_pitchers_multi_season([prev_season, season]))

    if pitchers_multi.empty:
        return '{}', '[]', '투수 데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    metrics = ['K/9', 'BB/9', 'ERA', 'WHIP', 'FIP', 'SO']
    tested_metrics = []

    for metric in metrics:
        if metric not in pitchers_multi.columns:
            continue

        prev = pitchers_multi[pitchers_multi['season'] == prev_season]
        curr = pitchers_multi[pitchers_multi['season'] == season]

        prev_vals = prev.groupby('playerId')[metric].first()
        curr_vals = curr.groupby('playerId')[metric].first()
        common = prev_vals.index.intersection(curr_vals.index)

        if len(common) >= 10:
            import pandas as pd
            paired = pd.DataFrame({'prev': prev_vals.loc[common], 'curr': curr_vals.loc[common]}).dropna()
            if len(paired) < 10:
                continue
            stats_dict[f'투수_{metric}_{prev_season}'] = StatsUtils.descriptive(
                paired['prev'].values, f'{prev_season} {metric}'
            )
            stats_dict[f'투수_{metric}_{season}'] = StatsUtils.descriptive(
                paired['curr'].values, f'{season} {metric}'
            )

            hypothesis[f'투수_{metric}_전후비교'] = StatsUtils.t_test_paired(
                paired['prev'].values,
                paired['curr'].values,
                f'투수 {metric} ({prev_season} vs {season})'
            )
            findings.append(f"투수 {metric}: {hypothesis[f'투수_{metric}_전후비교']['interpretation']}")
            tested_metrics.append(metric)

    # 차트: 각 지표별 전후 비교
    for m in tested_metrics[:4]:
        prev_key = f'투수_{m}_{prev_season}'
        curr_key = f'투수_{m}_{season}'
        if prev_key in stats_dict and curr_key in stats_dict:
            charts.append(ChartBuilder.bar(
                f'투수 {m} ABS 전후 비교',
                [str(prev_season), str(season)],
                [{'label': m, 'data': [stats_dict[prev_key]['mean'], stats_dict[curr_key]['mean']]}]
            ))

    # 종합 변화량
    if len(tested_metrics) >= 2:
        change_labels = []
        change_values = []
        for m in tested_metrics:
            prev_key = f'투수_{m}_{prev_season}'
            curr_key = f'투수_{m}_{season}'
            if prev_key in stats_dict and curr_key in stats_dict:
                change_labels.append(m)
                change_values.append(round(stats_dict[curr_key]['mean'] - stats_dict[prev_key]['mean'], 4))

        charts.append(ChartBuilder.bar(
            f'투수 지표 변화량 ({prev_season}→{season})',
            change_labels,
            [{'label': '변화량', 'data': change_values}]
        ))

    # ERA 가장 개선된 투수 Top 5
    if 'ERA' in pitchers_multi.columns:
        prev = pitchers_multi[pitchers_multi['season'] == prev_season].drop_duplicates('playerId').set_index('playerId')
        curr = pitchers_multi[pitchers_multi['season'] == season].drop_duplicates('playerId').set_index('playerId')
        common_ids = prev.index.intersection(curr.index)
        if len(common_ids) >= 5:
            diff = (prev.loc[common_ids, 'ERA'] - curr.loc[common_ids, 'ERA']).dropna()
            top5_improve = diff.nlargest(5)
            improve_names = []
            improve_vals = []
            for pid in top5_improve.index:
                if pid in curr.index and 'playerName' in curr.columns:
                    improve_names.append(curr.loc[pid, 'playerName'])
                    improve_vals.append(round(float(top5_improve[pid]), 4))
            if improve_names:
                charts.append(ChartBuilder.bar(
                    'ABS 이후 ERA 개선 Top 5',
                    improve_names,
                    [{'label': 'ERA 감소폭', 'data': improve_vals}]
                ))
                stats_dict['ERA_개선_Top5'] = [
                    {'name': n, 'change': v} for n, v in zip(improve_names, improve_vals)
                ]

    findings.insert(0, f"ABS 투수 영향 분석: {prev_season} vs {season}")
    insight = StatsUtils.format_insight('ABS 도입 - 투수 영향', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
