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


def analyze(season: int, db: DBConnector) -> tuple:
    batters = db.get_batters(season)
    rankings = db.get_team_rankings(season)

    if batters.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # ==================== 포지션별 WAR 분석 ====================
    if 'WAR' in batters.columns and 'position' in batters.columns:
        pos_war = batters.groupby('position').agg(
            총WAR=('WAR', 'sum'),
            평균WAR=('WAR', 'mean'),
            선수수=('WAR', 'count'),
            최고WAR=('WAR', 'max'),
        ).round(2).reset_index()
        pos_war = pos_war.sort_values('총WAR', ascending=False)

        stats_dict['포지션별_WAR'] = pos_war.to_dict('records')

        # 포지션별 WAR 비중 (Doughnut)
        charts.append(ChartBuilder.doughnut(
            '포지션별 WAR 비중',
            pos_war['position'].tolist(),
            pos_war['총WAR'].tolist()
        ))

        # 포지션별 평균 WAR (Bar)
        charts.append(ChartBuilder.bar(
            '포지션별 평균 WAR',
            pos_war['position'].tolist(),
            [{'label': '평균 WAR', 'data': pos_war['평균WAR'].tolist()}]
        ))

        # ANOVA
        pos_groups = {}
        for pos, group in batters.groupby('position'):
            vals = group['WAR'].dropna().values
            if len(vals) >= 3:
                pos_groups[pos] = vals

        if len(pos_groups) >= 3:
            hypothesis['포지션별_WAR_ANOVA'] = StatsUtils.anova(pos_groups)
            findings.append(f"포지션별 WAR: {hypothesis['포지션별_WAR_ANOVA']['interpretation']}")

        # 포지션별 상위 3명
        top3_by_pos = {}
        for pos, group in batters.groupby('position'):
            top3 = group.nlargest(3, 'WAR')[['playerName', 'teamName', 'WAR']].to_dict('records')
            top3_by_pos[pos] = top3
        stats_dict['포지션별_Top3'] = top3_by_pos

    # ==================== 팀별 포지션 WAR과 승수 상관 ====================
    if not rankings.empty and 'WAR' in batters.columns and 'teamId' in batters.columns:
        team_war = batters.groupby('teamId')['WAR'].sum().reset_index()
        team_war.columns = ['teamId', 'total_WAR']

        merged = team_war.merge(rankings[['teamId', 'wins', 'teamName']], on='teamId', how='inner')

        if len(merged) >= 5:
            hypothesis['팀WAR_승수_상관'] = StatsUtils.correlation(
                merged['total_WAR'].values,
                merged['wins'].values.astype(float),
                '팀 총 WAR', '승수'
            )
            findings.append(f"팀 WAR-승수 상관: {hypothesis['팀WAR_승수_상관']['interpretation']}")

            stats_dict['팀별_총WAR'] = merged[['teamName', 'total_WAR', 'wins']].to_dict('records')

    # Radar: 종합 포지션 가치
    if 'WAR' in batters.columns and 'OPS' in batters.columns:
        pos_combined = batters.groupby('position').agg({
            'WAR': 'mean', 'OPS': 'mean'
        }).round(3)

        top_positions = pos_combined.nlargest(6, 'WAR')
        charts.append(ChartBuilder.radar(
            '포지션별 종합 가치',
            top_positions.index.tolist(),
            [
                {'label': '평균 WAR', 'data': top_positions['WAR'].tolist()},
                {'label': '평균 OPS', 'data': top_positions['OPS'].tolist()},
            ]
        ))

    if stats_dict.get('포지션별_WAR'):
        top_pos = stats_dict['포지션별_WAR'][0]
        findings.insert(0, f"가장 높은 총 WAR 포지션: {top_pos['position']} ({top_pos['총WAR']})")

    insight = StatsUtils.format_insight('포지션별 가치 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
