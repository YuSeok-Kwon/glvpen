"""
Topic 12: 라인업 효율성 분석 - 타순별 기대 생산량과 실제 성적

가설검정:
1. 포지션별 wRC+ 차이 (ANOVA)
2. wRC+와 WAR 상관 (Pearson)
"""
import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from common.filters import filter_qualified_batters


def analyze(season: int, db: DBConnector) -> tuple:
    batters = filter_qualified_batters(db.get_batters(season))
    if batters.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # ==================== 포지션별 생산성 분석 ====================
    pos_metrics = {}
    for metric in ['wRC+', 'OPS', 'WAR', 'AVG']:
        if metric in batters.columns and 'position' in batters.columns:
            pos_avg = batters.groupby('position')[metric].mean().round(3)
            pos_metrics[metric] = pos_avg.to_dict()

    stats_dict['포지션별_지표'] = pos_metrics

    # 포지션별 wRC+ ANOVA
    if 'wRC+' in batters.columns and 'position' in batters.columns:
        pos_groups = {}
        for pos, group in batters.groupby('position'):
            vals = group['wRC+'].dropna().values
            if len(vals) >= 3:
                pos_groups[pos] = vals

        if len(pos_groups) >= 3:
            hypothesis['포지션별_wRC+_ANOVA'] = StatsUtils.anova(pos_groups)
            findings.append(f"포지션별 wRC+ 차이: {hypothesis['포지션별_wRC+_ANOVA']['interpretation']}")

    # wRC+와 WAR 상관
    if 'wRC+' in batters.columns and 'WAR' in batters.columns:
        data = batters[['wRC+', 'WAR']].dropna()
        hypothesis['wRC+_WAR_상관'] = StatsUtils.correlation(
            data['wRC+'].values, data['WAR'].values, 'wRC+', 'WAR'
        )
        findings.append(f"wRC+-WAR 상관: {hypothesis['wRC+_WAR_상관']['interpretation']}")

    # ==================== 차트 ====================
    # Radar: 포지션별 종합 능력치
    if pos_metrics.get('wRC+') and pos_metrics.get('OPS') and pos_metrics.get('WAR'):
        positions = sorted(set(pos_metrics['wRC+'].keys()) &
                          set(pos_metrics['OPS'].keys()) &
                          set(pos_metrics['WAR'].keys()))
        if positions:
            charts.append(ChartBuilder.radar(
                '포지션별 종합 능력치',
                positions,
                [
                    {'label': 'wRC+ (정규화)', 'data': [pos_metrics['wRC+'].get(p, 0) / 100 for p in positions]},
                    {'label': 'OPS', 'data': [pos_metrics['OPS'].get(p, 0) for p in positions]},
                ]
            ))

    # Bar: 포지션별 평균 OPS
    if pos_metrics.get('OPS'):
        sorted_pos = sorted(pos_metrics['OPS'].items(), key=lambda x: x[1], reverse=True)
        charts.append(ChartBuilder.bar(
            '포지션별 평균 OPS',
            [p[0] for p in sorted_pos],
            [{'label': '평균 OPS', 'data': [p[1] for p in sorted_pos]}]
        ))

    # Bar: 포지션별 평균 WAR
    if pos_metrics.get('WAR'):
        sorted_war = sorted(pos_metrics['WAR'].items(), key=lambda x: x[1], reverse=True)
        charts.append(ChartBuilder.bar(
            '포지션별 평균 WAR',
            [p[0] for p in sorted_war],
            [{'label': '평균 WAR', 'data': [round(p[1], 2) for p in sorted_war]}]
        ))

    insight = StatsUtils.format_insight('라인업 효율성 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
