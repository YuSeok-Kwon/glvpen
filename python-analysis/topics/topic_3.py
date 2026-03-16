"""
Topic 3: 행운 보정 분석 - BABIP과 FIP-ERA 갭으로 보는 진짜 실력

가설검정:
1. BABIP과 AVG의 상관성 (Pearson)
2. 고BABIP/저BABIP 그룹의 AVG 차이 (t-test)
3. ERA > FIP vs ERA < FIP 투수 비교
"""
import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    batters = db.get_batters(season)
    pitchers = db.get_pitchers(season)

    if batters.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # ==================== 타자 BABIP 분석 ====================
    if 'BABIP' in batters.columns and 'AVG' in batters.columns:
        babip_data = batters[['playerName', 'teamName', 'BABIP', 'AVG', 'WAR']].dropna()
        stats_dict['BABIP_기술통계'] = StatsUtils.descriptive(babip_data['BABIP'].values, 'BABIP')
        league_babip = babip_data['BABIP'].mean()

        # 행운 수혜자 (BABIP > 리그평균 + 1std)
        babip_std = babip_data['BABIP'].std()
        lucky = babip_data[babip_data['BABIP'] > league_babip + babip_std].nlargest(5, 'BABIP')
        unlucky = babip_data[babip_data['BABIP'] < league_babip - babip_std].nsmallest(5, 'BABIP')
        stats_dict['행운_수혜자'] = lucky[['playerName', 'teamName', 'BABIP', 'AVG']].to_dict('records')
        stats_dict['불운_선수'] = unlucky[['playerName', 'teamName', 'BABIP', 'AVG']].to_dict('records')

        # 가설1: BABIP-AVG 상관
        hypothesis['BABIP_AVG_상관'] = StatsUtils.correlation(
            babip_data['BABIP'].values, babip_data['AVG'].values, 'BABIP', 'AVG'
        )
        findings.append(f"BABIP-AVG 상관: {hypothesis['BABIP_AVG_상관']['interpretation']}")

        # 가설2: 고BABIP vs 저BABIP AVG 차이
        median_babip = babip_data['BABIP'].median()
        high_babip_avg = babip_data[babip_data['BABIP'] > median_babip]['AVG'].values
        low_babip_avg = babip_data[babip_data['BABIP'] <= median_babip]['AVG'].values
        hypothesis['고저BABIP_AVG_차이'] = StatsUtils.t_test_ind(
            high_babip_avg, low_babip_avg, '고BABIP 그룹', '저BABIP 그룹'
        )
        findings.append(f"고/저 BABIP 그룹 AVG 차이: {hypothesis['고저BABIP_AVG_차이']['interpretation']}")

        # 차트: BABIP 산점도 데이터
        scatter_data = [{'x': round(float(r['BABIP']), 3), 'y': round(float(r['AVG']), 3)}
                        for _, r in babip_data.iterrows()]
        charts.append(ChartBuilder.scatter(
            'BABIP vs AVG 관계',
            [{'label': '선수', 'data': scatter_data[:50]}]  # 50명 제한
        ))

    # ==================== 투수 ERA-FIP 갭 분석 ====================
    if not pitchers.empty and 'ERA' in pitchers.columns and 'FIP' in pitchers.columns:
        pit_data = pitchers[['playerName', 'teamName', 'ERA', 'FIP', 'WAR']].dropna()
        pit_data['ERA_FIP_gap'] = pit_data['ERA'] - pit_data['FIP']

        stats_dict['ERA_FIP_갭_기술통계'] = StatsUtils.descriptive(
            pit_data['ERA_FIP_gap'].values, 'ERA-FIP 갭'
        )

        # 행운 투수 (ERA < FIP, 실력보다 좋은 결과)
        lucky_pitchers = pit_data[pit_data['ERA_FIP_gap'] < -0.5].nsmallest(5, 'ERA_FIP_gap')
        unlucky_pitchers = pit_data[pit_data['ERA_FIP_gap'] > 0.5].nlargest(5, 'ERA_FIP_gap')
        stats_dict['행운_투수'] = lucky_pitchers[['playerName', 'teamName', 'ERA', 'FIP', 'ERA_FIP_gap']].to_dict('records')
        stats_dict['불운_투수'] = unlucky_pitchers[['playerName', 'teamName', 'ERA', 'FIP', 'ERA_FIP_gap']].to_dict('records')

        # 차트: ERA-FIP 갭 분포
        gap_labels = ['< -1.0', '-1.0~-0.5', '-0.5~0', '0~0.5', '0.5~1.0', '> 1.0']
        bins = [-np.inf, -1.0, -0.5, 0, 0.5, 1.0, np.inf]
        gap_counts = np.histogram(pit_data['ERA_FIP_gap'].values, bins=bins)[0].tolist()
        charts.append(ChartBuilder.bar(
            'ERA-FIP 갭 분포 (투수)',
            gap_labels,
            [{'label': '투수 수', 'data': gap_counts}]
        ))

        findings.append(f"ERA-FIP 갭 평균: {stats_dict['ERA_FIP_갭_기술통계']['mean']:.3f}")

    findings.insert(0, f"리그 평균 BABIP: {stats_dict.get('BABIP_기술통계', {}).get('mean', 'N/A')}")
    insight = StatsUtils.format_insight('행운 보정 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
