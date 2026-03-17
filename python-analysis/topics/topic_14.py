"""
Topic 14: 포지션별 가치 분석 - 어떤 포지션이 팀 승리에 가장 기여하는가

가설검정:
1. 포지션별 WAR 차이 (ANOVA)
2. 포지션별 WAR 합산과 팀 승수 상관 (Pearson)
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from common.filters import filter_qualified_batters


def analyze(season: int, db: DBConnector) -> tuple:
    batters = filter_qualified_batters(db.get_batters(season))
    rankings = db.get_team_rankings(season)

    if batters.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # 가치 지표: WAR 우선, 없으면 OPS
    val = 'WAR' if 'WAR' in batters.columns else 'OPS'

    # ==================== 포지션별 분석 ====================
    if val in batters.columns and 'position' in batters.columns:
        pos_agg = batters.groupby('position').agg(
            총합=(val, 'sum'),
            평균=(val, 'mean'),
            선수수=(val, 'count'),
            최고=(val, 'max'),
        ).round(3).reset_index()
        pos_agg = pos_agg.sort_values('총합', ascending=False)

        stats_dict[f'포지션별_{val}'] = pos_agg.to_dict('records')

        # 포지션별 비중 (Doughnut)
        charts.append(ChartBuilder.doughnut(
            f'포지션별 {val} 비중',
            pos_agg['position'].tolist(),
            pos_agg['총합'].tolist()
        ))

        # 포지션별 평균 (Bar)
        charts.append(ChartBuilder.bar(
            f'포지션별 평균 {val}',
            pos_agg['position'].tolist(),
            [{'label': f'평균 {val}', 'data': pos_agg['평균'].tolist()}]
        ))

        # ANOVA
        pos_groups = {}
        for pos, group in batters.groupby('position'):
            vals = group[val].dropna().values
            if len(vals) >= 3:
                pos_groups[pos] = vals

        if len(pos_groups) >= 3:
            hypothesis[f'포지션별_{val}_ANOVA'] = StatsUtils.anova(pos_groups)
            findings.append(f"포지션별 {val}: {hypothesis[f'포지션별_{val}_ANOVA']['interpretation']}")

        # 포지션별 상위 3명
        top3_by_pos = {}
        for pos, group in batters.groupby('position'):
            top3 = group.nlargest(3, val)[['playerName', 'teamName', val]].to_dict('records')
            top3_by_pos[pos] = top3
        stats_dict['포지션별_Top3'] = top3_by_pos

    # ==================== 팀별 포지션 가치와 승수 상관 ====================
    if not rankings.empty and val in batters.columns and 'teamId' in batters.columns:
        team_val = batters.groupby('teamId')[val].sum().reset_index()
        team_val.columns = ['teamId', f'total_{val}']

        merged = team_val.merge(rankings[['teamId', 'wins', 'teamName']], on='teamId', how='inner')

        if len(merged) >= 5:
            hypothesis[f'팀{val}_승수_상관'] = StatsUtils.correlation(
                merged[f'total_{val}'].values,
                merged['wins'].values.astype(float),
                f'팀 총 {val}', '승수'
            )
            findings.append(f"팀 {val}-승수 상관: {hypothesis[f'팀{val}_승수_상관']['interpretation']}")

            stats_dict[f'팀별_총{val}'] = merged[['teamName', f'total_{val}', 'wins']].to_dict('records')

    # Radar: 종합 포지션 가치
    if 'OPS' in batters.columns and 'position' in batters.columns:
        agg_cols = {val: 'mean', 'OPS': 'mean'} if val != 'OPS' else {'OPS': 'mean', 'AVG': 'mean'}
        pos_combined = batters.groupby('position').agg(agg_cols).round(3)

        sort_col = list(agg_cols.keys())[0]
        top_positions = pos_combined.nlargest(6, sort_col)
        charts.append(ChartBuilder.radar(
            '포지션별 종합 가치',
            top_positions.index.tolist(),
            [{'label': f'평균 {k}', 'data': top_positions[k].tolist()} for k in agg_cols]
        ))

    if stats_dict.get(f'포지션별_{val}'):
        top_pos = stats_dict[f'포지션별_{val}'][0]
        findings.insert(0, f"가장 높은 총 {val} 포지션: {top_pos['position']} ({top_pos['총합']:.3f})")

    insight = StatsUtils.format_insight('포지션별 가치 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
