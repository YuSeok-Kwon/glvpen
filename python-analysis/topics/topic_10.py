"""
Topic 10: 클러치/초커 분석 - 위기 상황에 강한 선수, 약한 선수

가설검정:
1. RISP vs 평상시 AVG 쌍표본 t-test
2. 일반 AVG와 RISP AVG 상관성 (Pearson)
"""
import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    batters = db.get_batters(season)
    if batters.empty or 'AVG' not in batters.columns:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # RISP(득점권 타율)와 일반 AVG 비교
    if 'RISP' in batters.columns and 'AVG' in batters.columns:
        data = batters[['playerName', 'teamName', 'AVG', 'RISP', 'WAR']].dropna()
        data['clutch_gap'] = data['RISP'] - data['AVG']

        stats_dict['clutch_갭_기술통계'] = StatsUtils.descriptive(
            data['clutch_gap'].values, '클러치 갭 (RISP - AVG)'
        )

        # 클러치 선수 (RISP > AVG + 0.030)
        clutch = data[data['clutch_gap'] > 0.030].nlargest(5, 'clutch_gap')
        choker = data[data['clutch_gap'] < -0.030].nsmallest(5, 'clutch_gap')

        stats_dict['클러치_Top5'] = clutch[['playerName', 'teamName', 'AVG', 'RISP', 'clutch_gap']].to_dict('records')
        stats_dict['초커_Top5'] = choker[['playerName', 'teamName', 'AVG', 'RISP', 'clutch_gap']].to_dict('records')

        # 가설1: RISP vs AVG 쌍표본 t-test
        hypothesis['RISP_AVG_비교'] = StatsUtils.t_test_paired(
            data['AVG'].values, data['RISP'].values,
            'AVG vs RISP'
        )
        findings.append(f"RISP vs AVG: {hypothesis['RISP_AVG_비교']['interpretation']}")

        # 가설2: AVG와 RISP 상관
        hypothesis['AVG_RISP_상관'] = StatsUtils.correlation(
            data['AVG'].values, data['RISP'].values, 'AVG', 'RISP'
        )
        findings.append(f"AVG-RISP 상관: {hypothesis['AVG_RISP_상관']['interpretation']}")

        # 차트: 클러치 갭 Top 5 / Bottom 5
        all_notable = list(clutch.itertuples()) + list(choker.itertuples())
        charts.append(ChartBuilder.bar(
            '클러치 지수 (RISP - AVG)',
            [f"{r.playerName}" for r in all_notable],
            [{'label': '클러치 갭', 'data': [round(r.clutch_gap, 3) for r in all_notable]}]
        ))

        # Scatter: AVG vs RISP
        scatter_data = [{'x': round(float(r['AVG']), 3), 'y': round(float(r['RISP']), 3)}
                        for _, r in data.head(50).iterrows()]
        charts.append(ChartBuilder.scatter(
            'AVG vs RISP 관계',
            [{'label': '선수', 'data': scatter_data}]
        ))

        findings.insert(0, f"평균 클러치 갭: {stats_dict['clutch_갭_기술통계']['mean']:.3f}")
    else:
        findings.append("RISP 데이터 미보유 - AVG 기반 분석만 수행")

    insight = StatsUtils.format_insight('클러치/초커 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
