"""
Topic 8B: 퓨처스 유망 투수 스카우팅

가설검정:
1. 퓨처스 vs 1군 투수 ERA 비교 (독립 t-test)
2. 퓨처스 vs 1군 투수 K/9 비교 (독립 t-test)
3. 퓨처스 투수 WAR 정규성 검정
"""
import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    futures_pit = db.get_futures_pitchers(season)
    first_pit = db.get_pitchers(season)

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    if futures_pit.empty or 'WAR' not in futures_pit.columns:
        return '{}', '[]', '퓨처스 투수 데이터 없음', '{}'

    # IP 필터
    if 'IP' in futures_pit.columns:
        futures_pit = futures_pit[futures_pit['IP'] >= 20]

    if futures_pit.empty:
        return '{}', '[]', '퓨처스 투수 IP 20 이상 데이터 없음', '{}'

    # Top 10 유망 투수
    top10 = futures_pit.nlargest(10, 'WAR')
    cols = [c for c in ['playerName', 'teamName', 'WAR', 'ERA', 'SO', 'IP', 'WHIP', 'K/9'] if c in top10.columns]
    stats_dict['퓨처스_투수_Top10'] = top10[cols].to_dict('records')

    # 기술통계
    for metric in ['WAR', 'ERA', 'SO', 'K/9']:
        if metric in futures_pit.columns:
            stats_dict[f'퓨처스_투수_{metric}'] = StatsUtils.descriptive(
                futures_pit[metric].dropna().values, f'퓨처스 투수 {metric}'
            )

    # 정규성 검정
    if len(futures_pit) >= 3:
        hypothesis['퓨처스_투수_WAR_정규성'] = StatsUtils.normality_test(
            futures_pit['WAR'].dropna().values, '퓨처스 투수 WAR'
        )
        findings.append(f"퓨처스 투수 WAR 분포: {hypothesis['퓨처스_투수_WAR_정규성']['interpretation']}")

    # 1군 비교
    if not first_pit.empty:
        for metric in ['ERA', 'K/9']:
            if metric in futures_pit.columns and metric in first_pit.columns:
                hypothesis[f'퓨처스vs1군_{metric}'] = StatsUtils.t_test_ind(
                    futures_pit[metric].dropna().values,
                    first_pit[metric].dropna().values,
                    '퓨처스 투수', '1군 투수'
                )
                findings.append(f"퓨처스 vs 1군 {metric}: {hypothesis[f'퓨처스vs1군_{metric}']['interpretation']}")

    # ERA-FIP 상관분석
    if 'ERA' in futures_pit.columns and 'FIP' in futures_pit.columns:
        hypothesis['퓨처스_ERA_FIP_상관'] = StatsUtils.correlation(
            futures_pit['ERA'].dropna().values,
            futures_pit['FIP'].dropna().values,
            'ERA', 'FIP'
        )
        findings.append(f"퓨처스 투수 ERA-FIP: {hypothesis['퓨처스_ERA_FIP_상관']['interpretation']}")

    # 차트: Top 10 WAR
    charts.append(ChartBuilder.bar(
        '퓨처스 유망 투수 Top 10 (WAR)',
        [f"{r['playerName']}({r['teamName']})" for r in stats_dict['퓨처스_투수_Top10']],
        [{'label': 'WAR', 'data': [r['WAR'] for r in stats_dict['퓨처스_투수_Top10']]}]
    ))

    # 1군 vs 퓨처스 비교
    if not first_pit.empty:
        compare_metrics = []
        futures_means = []
        first_means = []
        for m in ['ERA', 'K/9']:
            if m in futures_pit.columns and m in first_pit.columns:
                compare_metrics.append(m)
                futures_means.append(round(futures_pit[m].dropna().mean(), 4))
                first_means.append(round(first_pit[m].dropna().mean(), 4))
        if compare_metrics:
            charts.append(ChartBuilder.bar(
                '퓨처스 vs 1군 투수 평균 비교',
                compare_metrics,
                [
                    {'label': '퓨처스', 'data': futures_means},
                    {'label': '1군', 'data': first_means},
                ]
            ))

    # 레이더: 1위 유망주
    if stats_dict['퓨처스_투수_Top10']:
        top = stats_dict['퓨처스_투수_Top10'][0]
        radar_labels = [k for k in ['WAR', 'ERA', 'SO', 'K/9'] if k in top]
        radar_data = [top[k] for k in radar_labels]
        if radar_labels:
            charts.append(ChartBuilder.radar(
                f"{top['playerName']} 능력치 레이더",
                radar_labels,
                [{'label': top['playerName'], 'data': radar_data}]
            ))

    findings.insert(0, f"퓨처스 투수 분석 대상: {len(futures_pit)}명 (IP >= 20)")
    insight = StatsUtils.format_insight('퓨처스 유망 투수 스카우팅', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
