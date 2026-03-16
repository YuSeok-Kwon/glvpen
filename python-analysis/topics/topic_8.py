"""
Topic 8: 퓨처스 유망주 스카우팅 - 내일의 KBO 스타

가설검정:
1. 퓨처스 vs 1군 주요 지표 비교 (t-test)
"""
import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    futures_bat = db.get_futures_batters(season)
    futures_pit = db.get_futures_pitchers(season)
    first_bat = db.get_batters(season)
    first_pit = db.get_pitchers(season)

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    has_data = False

    # ==================== 퓨처스 타자 분석 ====================
    if not futures_bat.empty and 'WAR' in futures_bat.columns:
        has_data = True
        # PA 50 이상 필터
        if 'PA' in futures_bat.columns:
            futures_bat = futures_bat[futures_bat['PA'] >= 50]

        top5_bat = futures_bat.nlargest(5, 'WAR')
        cols = [c for c in ['playerName', 'teamName', 'WAR', 'AVG', 'OPS'] if c in top5_bat.columns]
        stats_dict['퓨처스_타자_Top5'] = top5_bat[cols].to_dict('records')

        if 'WAR' in futures_bat.columns:
            stats_dict['퓨처스_타자_WAR'] = StatsUtils.descriptive(
                futures_bat['WAR'].dropna().values, '퓨처스 타자 WAR'
            )

        # 차트: 유망주 타자 WAR
        charts.append(ChartBuilder.bar(
            '퓨처스 유망 타자 Top 5',
            [f"{r['playerName']}({r['teamName']})" for r in stats_dict['퓨처스_타자_Top5']],
            [{'label': 'WAR', 'data': [r['WAR'] for r in stats_dict['퓨처스_타자_Top5']]}]
        ))

        # 1군 비교 t-test
        if not first_bat.empty and 'AVG' in futures_bat.columns and 'AVG' in first_bat.columns:
            hypothesis['퓨처스_1군_AVG_비교'] = StatsUtils.t_test_ind(
                futures_bat['AVG'].dropna().values,
                first_bat['AVG'].dropna().values,
                '퓨처스 타자', '1군 타자'
            )
            findings.append(f"퓨처스 vs 1군 AVG: {hypothesis['퓨처스_1군_AVG_비교']['interpretation']}")

    # ==================== 퓨처스 투수 분석 ====================
    if not futures_pit.empty and 'WAR' in futures_pit.columns:
        has_data = True
        if 'IP' in futures_pit.columns:
            futures_pit = futures_pit[futures_pit['IP'] >= 20]

        top5_pit = futures_pit.nlargest(5, 'WAR')
        cols = [c for c in ['playerName', 'teamName', 'WAR', 'ERA', 'SO'] if c in top5_pit.columns]
        stats_dict['퓨처스_투수_Top5'] = top5_pit[cols].to_dict('records')

        charts.append(ChartBuilder.bar(
            '퓨처스 유망 투수 Top 5',
            [f"{r['playerName']}({r['teamName']})" for r in stats_dict['퓨처스_투수_Top5']],
            [{'label': 'WAR', 'data': [r['WAR'] for r in stats_dict['퓨처스_투수_Top5']]}]
        ))

    if not has_data:
        return '{}', '[]', '퓨처스 데이터 없음', '{}'

    # Radar: 1위 유망주 능력치
    if '퓨처스_타자_Top5' in stats_dict and len(stats_dict['퓨처스_타자_Top5']) > 0:
        top_prospect = stats_dict['퓨처스_타자_Top5'][0]
        radar_labels = [k for k in ['WAR', 'AVG', 'OPS'] if k in top_prospect]
        radar_data = [top_prospect[k] for k in radar_labels]
        if radar_labels:
            charts.append(ChartBuilder.radar(
                f"{top_prospect['playerName']} 능력치",
                radar_labels,
                [{'label': top_prospect['playerName'], 'data': radar_data}]
            ))

    findings.insert(0, f"퓨처스 타자 분석 대상: {len(futures_bat) if not futures_bat.empty else 0}명")
    insight = StatsUtils.format_insight('퓨처스 유망주 스카우팅', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
