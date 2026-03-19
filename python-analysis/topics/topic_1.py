"""
Topic 1: WAR 스포트라이트 - WAR 상위 선수 심층 분석

가설검정:
1. 전년/현년 WAR 쌍표본 t-test
2. 포지션별 WAR 차이 ANOVA
"""
import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from common.filters import filter_qualified_batters, filter_qualified_pitchers, filter_batters_multi_season


def analyze(season: int, db: DBConnector) -> tuple:
    batters = filter_qualified_batters(db.get_batters(season))
    pitchers = filter_qualified_pitchers(db.get_pitchers(season))
    multi = filter_batters_multi_season(db.get_batters_multi_season([season - 1, season]))

    if batters.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    charts = []
    findings = []

    # 가치 지표: WAR 우선, 없으면 OPS
    val = 'WAR' if 'WAR' in batters.columns else 'OPS'
    val_label = val

    # ==================== 상위 10명 ====================
    top10_cols = ['playerName', 'teamName', 'position']
    for c in ['WAR', 'OPS', 'AVG']:
        if c in batters.columns:
            top10_cols.append(c)
    top10_bat = batters.nlargest(10, val)[top10_cols].to_dict('records')
    stats_dict[f'타자_{val}_Top10'] = top10_bat
    stats_dict[f'타자_{val}_기술통계'] = StatsUtils.descriptive(batters[val].dropna().values, f'타자 {val}')

    charts.append(ChartBuilder.bar(
        f'{val} 상위 10명 타자',
        [f"{p['playerName']}({p['teamName']})" for p in top10_bat],
        [{'label': val_label, 'data': [p[val] for p in top10_bat]}]
    ))

    pit_val = 'WAR' if 'WAR' in pitchers.columns else 'ERA'
    if not pitchers.empty:
        pit_cols = ['playerName', 'teamName']
        for c in ['WAR', 'ERA', 'FIP', 'WHIP']:
            if c in pitchers.columns:
                pit_cols.append(c)
        if pit_val == 'ERA':
            top10_pit = pitchers.nsmallest(10, pit_val)[pit_cols].to_dict('records')
        else:
            top10_pit = pitchers.nlargest(10, pit_val)[pit_cols].to_dict('records')
        stats_dict['투수_Top10'] = top10_pit

    # ==================== 전년 비교 (paired t-test) ====================
    multi_val = 'WAR' if (not multi.empty and 'WAR' in multi.columns) else ('OPS' if not multi.empty and 'OPS' in multi.columns else None)
    if multi_val:
        prev = multi[multi['season'] == season - 1][['playerId', multi_val]].rename(columns={multi_val: 'prev'})
        curr = multi[multi['season'] == season][['playerId', multi_val]].rename(columns={multi_val: 'curr'})
        merged = prev.merge(curr, on='playerId', how='inner').dropna()

        if len(merged) >= 5:
            hypothesis[f'전년_현년_{multi_val}_비교'] = StatsUtils.t_test_paired(
                merged['prev'].values, merged['curr'].values,
                f'{season-1} vs {season} {multi_val}'
            )
            findings.append(f"전년 대비 {multi_val} 변화: {hypothesis[f'전년_현년_{multi_val}_비교']['interpretation']}")

            merged_with_name = merged.merge(
                batters[['playerId', 'playerName', 'teamName']].drop_duplicates(),
                on='playerId', how='left'
            )
            merged_with_name['diff'] = merged_with_name['curr'] - merged_with_name['prev']
            top5_change = merged_with_name.nlargest(5, 'diff')

            charts.append(ChartBuilder.bar(
                f'{multi_val} 상승폭 상위 5명',
                [f"{r['playerName']}" for _, r in top5_change.iterrows()],
                [
                    {'label': f'{season-1}', 'data': top5_change['prev'].tolist()},
                    {'label': f'{season}', 'data': top5_change['curr'].tolist()},
                ]
            ))

    # ==================== 포지션별 ANOVA ====================
    if 'position' in batters.columns:
        pos_groups = {}
        for pos, group in batters.groupby('position'):
            vals = group[val].dropna().values
            if len(vals) >= 3:
                pos_groups[pos] = vals

        if len(pos_groups) >= 3:
            hypothesis[f'포지션별_{val}_ANOVA'] = StatsUtils.anova(pos_groups)
            findings.append(f"포지션별 {val}: {hypothesis[f'포지션별_{val}_ANOVA']['interpretation']}")

            charts.append(ChartBuilder.bar(
                f'포지션별 평균 {val}',
                list(hypothesis[f'포지션별_{val}_ANOVA']['group_means'].keys()),
                [{'label': f'평균 {val}', 'data': list(hypothesis[f'포지션별_{val}_ANOVA']['group_means'].values())}]
            ))

    # ==================== 포지션별 비중 (Doughnut) ====================
    if 'position' in batters.columns:
        pos_sum = batters.groupby('position')[val].sum().sort_values(ascending=False)
        charts.append(ChartBuilder.doughnut(
            f'포지션별 {val} 비중',
            pos_sum.index.tolist(),
            [round(v, 1) for v in pos_sum.values.tolist()]
        ))

    findings.insert(0, f"타자 평균 {val}: {stats_dict[f'타자_{val}_기술통계']['mean']:.3f} (최고: {stats_dict[f'타자_{val}_기술통계']['max']:.3f})")

    insight = StatsUtils.format_insight('WAR 스포트라이트', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
