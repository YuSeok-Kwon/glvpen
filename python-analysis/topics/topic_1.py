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


def analyze(season: int, db: DBConnector) -> tuple:
    batters = db.get_batters(season)
    pitchers = db.get_pitchers(season)
    multi = db.get_batters_multi_season([season - 1, season])

    if batters.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    charts = []
    findings = []

    # ==================== WAR 상위 10명 ====================
    if 'WAR' in batters.columns:
        top10_bat = batters.nlargest(10, 'WAR')[['playerName', 'teamName', 'position', 'WAR', 'OPS']].to_dict('records')
        stats_dict['타자_WAR_Top10'] = top10_bat
        stats_dict['타자_WAR_기술통계'] = StatsUtils.descriptive(batters['WAR'].dropna().values, '타자 WAR')

        charts.append(ChartBuilder.bar(
            'WAR 상위 10명 타자',
            [f"{p['playerName']}({p['teamName']})" for p in top10_bat],
            [{'label': 'WAR', 'data': [p['WAR'] for p in top10_bat]}]
        ))

    if 'WAR' in pitchers.columns:
        top10_pit = pitchers.nlargest(10, 'WAR')[['playerName', 'teamName', 'WAR', 'ERA', 'FIP']].to_dict('records')
        stats_dict['투수_WAR_Top10'] = top10_pit

    # ==================== 전년 비교 (paired t-test) ====================
    if not multi.empty and 'WAR' in multi.columns:
        prev = multi[multi['season'] == season - 1][['playerId', 'WAR']].rename(columns={'WAR': 'prev_WAR'})
        curr = multi[multi['season'] == season][['playerId', 'WAR']].rename(columns={'WAR': 'curr_WAR'})
        merged = prev.merge(curr, on='playerId', how='inner').dropna()

        if len(merged) >= 5:
            hypothesis['전년_현년_WAR_비교'] = StatsUtils.t_test_paired(
                merged['prev_WAR'].values, merged['curr_WAR'].values,
                f'{season-1} vs {season} WAR'
            )
            findings.append(f"전년 대비 WAR 변화: {hypothesis['전년_현년_WAR_비교']['interpretation']}")

            # 전년 대비 변화 차트 (상위 5명)
            merged_with_name = merged.merge(
                batters[['playerId', 'playerName', 'teamName']].drop_duplicates(),
                on='playerId', how='left'
            )
            merged_with_name['war_diff'] = merged_with_name['curr_WAR'] - merged_with_name['prev_WAR']
            top5_change = merged_with_name.nlargest(5, 'war_diff')

            charts.append(ChartBuilder.bar(
                'WAR 상승폭 상위 5명',
                [f"{r['playerName']}" for _, r in top5_change.iterrows()],
                [
                    {'label': f'{season-1}', 'data': top5_change['prev_WAR'].tolist()},
                    {'label': f'{season}', 'data': top5_change['curr_WAR'].tolist()},
                ]
            ))

    # ==================== 포지션별 WAR ANOVA ====================
    if 'WAR' in batters.columns and 'position' in batters.columns:
        pos_groups = {}
        for pos, group in batters.groupby('position'):
            war_vals = group['WAR'].dropna().values
            if len(war_vals) >= 3:
                pos_groups[pos] = war_vals

        if len(pos_groups) >= 3:
            hypothesis['포지션별_WAR_ANOVA'] = StatsUtils.anova(pos_groups)
            findings.append(f"포지션별 WAR: {hypothesis['포지션별_WAR_ANOVA']['interpretation']}")

            charts.append(ChartBuilder.bar(
                '포지션별 평균 WAR',
                list(hypothesis['포지션별_WAR_ANOVA']['group_means'].keys()),
                [{'label': '평균 WAR', 'data': list(hypothesis['포지션별_WAR_ANOVA']['group_means'].values())}]
            ))

    # ==================== 포지션별 WAR 비중 (Doughnut) ====================
    if 'WAR' in batters.columns and 'position' in batters.columns:
        pos_war = batters.groupby('position')['WAR'].sum().sort_values(ascending=False)
        charts.append(ChartBuilder.doughnut(
            '포지션별 WAR 비중',
            pos_war.index.tolist(),
            [round(v, 1) for v in pos_war.values.tolist()]
        ))

    if 'WAR' in batters.columns:
        findings.insert(0, f"타자 평균 WAR: {stats_dict['타자_WAR_기술통계']['mean']:.2f} (최고: {stats_dict['타자_WAR_기술통계']['max']:.2f})")

    insight = StatsUtils.format_insight('WAR 스포트라이트', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
