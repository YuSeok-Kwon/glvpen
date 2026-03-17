"""
Topic 2: 브레이크아웃 후보 - 올 시즌 급성장 선수 분석

가설검정:
1. 브레이크아웃 vs 비브레이크아웃 wRC+ 차이 (t-test)
2. ISO와 WAR 상승 간 상관성 (Pearson)
"""
import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from common.filters import filter_batters_multi_season


def analyze(season: int, db: DBConnector) -> tuple:
    multi = filter_batters_multi_season(db.get_batters_multi_season([season - 1, season]))
    # 가치 지표: WAR 우선, 없으면 OPS
    val = 'WAR' if (not multi.empty and 'WAR' in multi.columns) else 'OPS'
    if multi.empty or val not in multi.columns:
        return '{}', '[]', '데이터 없음', '{}'

    prev = multi[multi['season'] == season - 1].set_index('playerId')
    curr = multi[multi['season'] == season].set_index('playerId')
    common = prev.index.intersection(curr.index)

    if len(common) < 10:
        return '{}', '[]', '비교 대상 선수 부족', '{}'

    # 지표 변화 계산
    keep_cols = ['playerName', 'teamName']
    for c in ['WAR', 'OPS', 'AVG']:
        if c in curr.columns:
            keep_cols.append(c)
    comparison = curr.loc[common, keep_cols].copy()
    comparison[f'prev_{val}'] = prev.loc[common, val]
    comparison['val_diff'] = comparison[val] - comparison[f'prev_{val}']
    if 'ISO' in curr.columns:
        comparison['ISO'] = curr.loc[common, 'ISO']
        comparison['prev_ISO'] = prev.loc[common, 'ISO']

    comparison = comparison.dropna(subset=['val_diff'])
    top5 = comparison.nlargest(5, 'val_diff')

    stats_dict = {
        '브레이크아웃_Top5': top5[['playerName', 'teamName', val, f'prev_{val}', 'val_diff']].reset_index().to_dict('records'),
        f'{val}변화_기술통계': StatsUtils.descriptive(comparison['val_diff'].values, f'{val} 변화량'),
    }

    hypothesis = {}
    findings = []
    charts = []

    # 가설1: 브레이크아웃 vs 비브레이크아웃 OPS
    compare_metric = 'wRC+' if 'wRC+' in comparison.columns else 'OPS'
    if compare_metric in comparison.columns:
        threshold = comparison['val_diff'].quantile(0.75)
        breakout = comparison[comparison['val_diff'] >= threshold][compare_metric].dropna().values
        non_breakout = comparison[comparison['val_diff'] < threshold][compare_metric].dropna().values
        if len(breakout) >= 2 and len(non_breakout) >= 2:
            hypothesis[f'브레이크아웃_{compare_metric}_차이'] = StatsUtils.t_test_ind(
                breakout, non_breakout, '브레이크아웃 그룹', '일반 그룹'
            )
            findings.append(f"브레이크아웃 vs 일반 {compare_metric}: {hypothesis[f'브레이크아웃_{compare_metric}_차이']['interpretation']}")

    # 가설2: ISO와 성장 상관
    if 'ISO' in comparison.columns:
        iso_vals = comparison['ISO'].dropna().values
        diff_vals = comparison.loc[comparison['ISO'].notna(), 'val_diff'].values
        hypothesis[f'ISO_{val}상승_상관'] = StatsUtils.correlation(
            iso_vals, diff_vals, 'ISO', f'{val} 상승폭'
        )
        findings.append(f"ISO와 {val} 상승 상관: {hypothesis[f'ISO_{val}상승_상관']['interpretation']}")

    # 차트: 상승폭 Top 5
    charts.append(ChartBuilder.bar(
        f'{val} 상승폭 Top 5 (브레이크아웃)',
        [f"{r['playerName']}({r['teamName']})" for r in stats_dict['브레이크아웃_Top5']],
        [
            {'label': f'{season-1} {val}', 'data': [r[f'prev_{val}'] for r in stats_dict['브레이크아웃_Top5']]},
            {'label': f'{season} {val}', 'data': [r[val] for r in stats_dict['브레이크아웃_Top5']]},
        ]
    ))

    # Radar: 브레이크아웃 선수 능력치
    for _, row in top5.head(3).iterrows():
        radar_data = []
        radar_labels = []
        for m in [val, 'OPS', 'ISO', 'AVG']:
            if m in top5.columns and not np.isnan(row.get(m, np.nan)):
                radar_labels.append(m)
                radar_data.append(round(float(row[m]), 3))
        if len(radar_labels) >= 2:
            charts.append(ChartBuilder.radar(
                f"{row['playerName']} 능력치",
                radar_labels,
                [{'label': row['playerName'], 'data': radar_data}]
            ))
            break

    findings.insert(0, f"{val} 상승폭 1위: {top5.iloc[0]['playerName']} (+{top5.iloc[0]['val_diff']:.3f})")
    insight = StatsUtils.format_insight('브레이크아웃 후보 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
