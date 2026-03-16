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


def analyze(season: int, db: DBConnector) -> tuple:
    multi = db.get_batters_multi_season([season - 1, season])
    if multi.empty or 'WAR' not in multi.columns:
        return '{}', '[]', '데이터 없음', '{}'

    prev = multi[multi['season'] == season - 1].set_index('playerId')
    curr = multi[multi['season'] == season].set_index('playerId')
    common = prev.index.intersection(curr.index)

    if len(common) < 10:
        return '{}', '[]', '비교 대상 선수 부족', '{}'

    # WAR 변화 계산
    comparison = curr.loc[common, ['playerName', 'teamName', 'WAR', 'OPS']].copy()
    comparison['prev_WAR'] = prev.loc[common, 'WAR']
    comparison['war_diff'] = comparison['WAR'] - comparison['prev_WAR']
    if 'wRC+' in curr.columns:
        comparison['wRC+'] = curr.loc[common, 'wRC+']
    if 'ISO' in curr.columns:
        comparison['ISO'] = curr.loc[common, 'ISO']
        comparison['prev_ISO'] = prev.loc[common, 'ISO']

    comparison = comparison.dropna(subset=['war_diff'])
    top5 = comparison.nlargest(5, 'war_diff')

    stats_dict = {
        '브레이크아웃_Top5': top5[['playerName', 'teamName', 'WAR', 'prev_WAR', 'war_diff']].reset_index().to_dict('records'),
        'WAR변화_기술통계': StatsUtils.descriptive(comparison['war_diff'].values, 'WAR 변화량'),
    }

    hypothesis = {}
    findings = []
    charts = []

    # 가설1: 브레이크아웃 vs 비브레이크아웃 wRC+
    if 'wRC+' in comparison.columns:
        threshold = comparison['war_diff'].quantile(0.75)
        breakout = comparison[comparison['war_diff'] >= threshold]['wRC+'].dropna().values
        non_breakout = comparison[comparison['war_diff'] < threshold]['wRC+'].dropna().values
        hypothesis['브레이크아웃_wRC+_차이'] = StatsUtils.t_test_ind(
            breakout, non_breakout, '브레이크아웃 그룹', '일반 그룹'
        )
        findings.append(f"브레이크아웃 vs 일반 wRC+: {hypothesis['브레이크아웃_wRC+_차이']['interpretation']}")

    # 가설2: ISO와 WAR 상승 상관
    if 'ISO' in comparison.columns:
        iso_vals = comparison['ISO'].dropna().values
        war_diff_vals = comparison.loc[comparison['ISO'].notna(), 'war_diff'].values
        hypothesis['ISO_WAR상승_상관'] = StatsUtils.correlation(
            iso_vals, war_diff_vals, 'ISO', 'WAR 상승폭'
        )
        findings.append(f"ISO와 WAR 상승 상관: {hypothesis['ISO_WAR상승_상관']['interpretation']}")

    # 차트: WAR 상승폭 Top 5
    charts.append(ChartBuilder.bar(
        'WAR 상승폭 Top 5 (브레이크아웃)',
        [f"{r['playerName']}({r['teamName']})" for r in stats_dict['브레이크아웃_Top5']],
        [
            {'label': f'{season-1} WAR', 'data': [r['prev_WAR'] for r in stats_dict['브레이크아웃_Top5']]},
            {'label': f'{season} WAR', 'data': [r['WAR'] for r in stats_dict['브레이크아웃_Top5']]},
        ]
    ))

    # Radar: 브레이크아웃 선수 능력치
    if 'wRC+' in top5.columns and 'OPS' in top5.columns:
        for _, row in top5.head(3).iterrows():
            radar_data = []
            radar_labels = []
            for m in ['WAR', 'OPS', 'wRC+', 'ISO']:
                if m in top5.columns and not np.isnan(row.get(m, np.nan)):
                    radar_labels.append(m)
                    radar_data.append(round(float(row[m]), 3))
            if radar_labels:
                charts.append(ChartBuilder.radar(
                    f"{row['playerName']} 능력치",
                    radar_labels,
                    [{'label': row['playerName'], 'data': radar_data}]
                ))
                break  # 1명만

    findings.insert(0, f"WAR 상승폭 1위: {top5.iloc[0]['playerName']} (+{top5.iloc[0]['war_diff']:.2f})")
    insight = StatsUtils.format_insight('브레이크아웃 후보 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
