"""
Topic 8A: 퓨처스 유망 타자 스카우팅

가설검정:
1. 퓨처스 vs 1군 타자 AVG 비교 (독립 t-test)
2. 퓨처스 vs 1군 타자 OPS 비교 (독립 t-test)
3. 퓨처스 타자 WAR 정규성 검정
"""
import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    futures_bat = db.get_futures_batters(season)
    first_bat = db.get_batters(season)

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # 가치 지표: WAR 우선, 없으면 OPS
    val = 'WAR' if (not futures_bat.empty and 'WAR' in futures_bat.columns) else 'OPS'
    if futures_bat.empty or val not in futures_bat.columns:
        return '{}', '[]', '퓨처스 타자 데이터 없음', '{}'

    # PA 필터
    if 'PA' in futures_bat.columns:
        futures_bat = futures_bat[futures_bat['PA'] >= 50]

    if futures_bat.empty:
        return '{}', '[]', '퓨처스 타자 PA 50 이상 데이터 없음', '{}'

    # Top 10 유망 타자
    top10 = futures_bat.nlargest(10, val)
    cols = [c for c in ['playerName', 'teamName', 'AVG', 'OPS', 'HR', 'RBI', 'SB', 'SLG'] if c in top10.columns]
    stats_dict['퓨처스_타자_Top10'] = top10[cols].to_dict('records')

    # 기술통계
    for metric in ['AVG', 'OPS', 'HR', 'SLG']:
        if metric in futures_bat.columns:
            stats_dict[f'퓨처스_타자_{metric}'] = StatsUtils.descriptive(
                futures_bat[metric].dropna().values, f'퓨처스 타자 {metric}'
            )

    # OPS 정규성 검정
    if val in futures_bat.columns and len(futures_bat) >= 3:
        hypothesis[f'퓨처스_타자_{val}_정규성'] = StatsUtils.normality_test(
            futures_bat[val].dropna().values, f'퓨처스 타자 {val}'
        )
        findings.append(f"퓨처스 타자 {val} 분포: {hypothesis[f'퓨처스_타자_{val}_정규성']['interpretation']}")

    # 1군 비교
    if not first_bat.empty:
        for metric in ['AVG', 'OPS']:
            if metric in futures_bat.columns and metric in first_bat.columns:
                hypothesis[f'퓨처스vs1군_{metric}'] = StatsUtils.t_test_ind(
                    futures_bat[metric].dropna().values,
                    first_bat[metric].dropna().values,
                    '퓨처스 타자', '1군 타자'
                )
                findings.append(f"퓨처스 vs 1군 {metric}: {hypothesis[f'퓨처스vs1군_{metric}']['interpretation']}")

    # 차트: Top 10
    charts.append(ChartBuilder.bar(
        f'퓨처스 유망 타자 Top 10 ({val})',
        [f"{r['playerName']}({r['teamName']})" for r in stats_dict['퓨처스_타자_Top10']],
        [{'label': val, 'data': [r[val] for r in stats_dict['퓨처스_타자_Top10']]}]
    ))

    # 차트: 1군 vs 퓨처스 평균 비교
    if not first_bat.empty:
        compare_metrics = []
        futures_means = []
        first_means = []
        for m in ['AVG', 'OPS']:
            if m in futures_bat.columns and m in first_bat.columns:
                compare_metrics.append(m)
                futures_means.append(round(futures_bat[m].dropna().mean(), 4))
                first_means.append(round(first_bat[m].dropna().mean(), 4))
        if compare_metrics:
            charts.append(ChartBuilder.bar(
                '퓨처스 vs 1군 타자 평균 비교',
                compare_metrics,
                [
                    {'label': '퓨처스', 'data': futures_means},
                    {'label': '1군', 'data': first_means},
                ]
            ))

    # 레이더: 1위 유망주
    if stats_dict['퓨처스_타자_Top10']:
        top = stats_dict['퓨처스_타자_Top10'][0]
        radar_labels = [k for k in ['OPS', 'AVG', 'SLG', 'HR'] if k in top]
        radar_data = [top[k] for k in radar_labels]
        if radar_labels:
            charts.append(ChartBuilder.radar(
                f"{top['playerName']} 능력치 레이더",
                radar_labels,
                [{'label': top['playerName'], 'data': radar_data}]
            ))

    findings.insert(0, f"퓨처스 타자 분석 대상: {len(futures_bat)}명 (PA >= 50)")
    insight = StatsUtils.format_insight('퓨처스 유망 타자 스카우팅', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
