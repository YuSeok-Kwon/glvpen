"""
Topic 0: 세이버메트릭스 트렌드 - wRC+, FIP, xFIP로 보는 리그 판도

분석 내용:
- 리그 평균 기술통계 (wRC+, BABIP, ISO, K%, BB%, FIP, xFIP, K/9, BB/9)
- WAR 상위 5명 타자/투수

가설검정:
1. wRC+ 분포 정규성 (Shapiro-Wilk)
2. WAR 상위/하위 타자의 wRC+ 차이 (독립표본 t-test)
3. ERA와 FIP 상관성 (Pearson)
"""
import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    batters = db.get_batters(season)
    pitchers = db.get_pitchers(season)

    if batters.empty or pitchers.empty:
        return '{}', '[]', '데이터 없음', '{}'

    # ==================== 기술통계 ====================
    batter_metrics = ['WAR', 'AVG', 'OPS', 'wRC+', 'BABIP', 'ISO', 'K%', 'BB%']
    pitcher_metrics = ['WAR', 'ERA', 'FIP', 'xFIP', 'WHIP', 'K/9', 'BB/9']

    stats_dict = {}
    for m in batter_metrics:
        if m in batters.columns:
            stats_dict[f'타자_{m}'] = StatsUtils.descriptive(
                batters[m].dropna().values, f'타자 {m}'
            )
    for m in pitcher_metrics:
        if m in pitchers.columns:
            stats_dict[f'투수_{m}'] = StatsUtils.descriptive(
                pitchers[m].dropna().values, f'투수 {m}'
            )

    # WAR 상위 5명
    if 'WAR' in batters.columns:
        top5_batters = batters.nlargest(5, 'WAR')[['playerName', 'teamName', 'WAR', 'OPS', 'wRC+']].to_dict('records')
        stats_dict['WAR_상위5_타자'] = top5_batters
    if 'WAR' in pitchers.columns:
        top5_pitchers = pitchers.nlargest(5, 'WAR')[['playerName', 'teamName', 'WAR', 'ERA', 'FIP']].to_dict('records')
        stats_dict['WAR_상위5_투수'] = top5_pitchers

    # ==================== 가설검정 ====================
    hypothesis = {}

    # 1. wRC+ 정규성 검정
    if 'wRC+' in batters.columns:
        wrc = batters['wRC+'].dropna().values
        hypothesis['wRC+_정규성'] = StatsUtils.normality_test(wrc, 'wRC+ 분포')

    # 2. WAR 상위/하위 wRC+ 차이
    if 'WAR' in batters.columns and 'wRC+' in batters.columns:
        median_war = batters['WAR'].median()
        high_war = batters[batters['WAR'] > median_war]['wRC+'].dropna().values
        low_war = batters[batters['WAR'] <= median_war]['wRC+'].dropna().values
        hypothesis['WAR상위_하위_wRC+차이'] = StatsUtils.t_test_ind(
            high_war, low_war, 'WAR 상위 그룹', 'WAR 하위 그룹'
        )

    # 3. ERA-FIP 상관성
    if 'ERA' in pitchers.columns and 'FIP' in pitchers.columns:
        era = pitchers['ERA'].dropna().values
        fip = pitchers['FIP'].dropna().values
        min_len = min(len(era), len(fip))
        hypothesis['ERA_FIP_상관'] = StatsUtils.correlation(
            era[:min_len], fip[:min_len], 'ERA', 'FIP'
        )

    # ==================== 차트 생성 ====================
    charts = []

    # Bar: 리그 평균 타자 주요 지표
    batter_labels = [m for m in batter_metrics if f'타자_{m}' in stats_dict and m != 'WAR']
    batter_means = [stats_dict[f'타자_{m}']['mean'] for m in batter_labels]
    if batter_labels:
        charts.append(ChartBuilder.bar(
            '리그 평균 타자 주요 지표',
            batter_labels,
            [{'label': f'{season} 시즌', 'data': batter_means}]
        ))

    # Bar: WAR 상위 5명 타자
    if 'WAR_상위5_타자' in stats_dict:
        top5 = stats_dict['WAR_상위5_타자']
        charts.append(ChartBuilder.bar(
            'WAR 상위 5명 타자',
            [f"{p['playerName']}({p['teamName']})" for p in top5],
            [{'label': 'WAR', 'data': [p['WAR'] for p in top5]}]
        ))

    # Radar: 투수 주요 지표 (정규화)
    if all(f'투수_{m}' in stats_dict for m in ['ERA', 'FIP', 'WHIP', 'K/9', 'BB/9']):
        charts.append(ChartBuilder.radar(
            '투수 리그 평균 지표',
            ['ERA', 'FIP', 'WHIP', 'K/9', 'BB/9'],
            [{'label': f'{season} 평균', 'data': [
                stats_dict['투수_ERA']['mean'],
                stats_dict['투수_FIP']['mean'],
                stats_dict['투수_WHIP']['mean'],
                stats_dict['투수_K/9']['mean'],
                stats_dict['투수_BB/9']['mean'],
            ]}]
        ))

    # ==================== 인사이트 텍스트 ====================
    findings = []
    if '타자_wRC+' in stats_dict:
        findings.append(f"리그 평균 wRC+: {stats_dict['타자_wRC+']['mean']:.1f} (표준편차: {stats_dict['타자_wRC+']['std']:.1f})")
    if '투수_FIP' in stats_dict:
        findings.append(f"리그 평균 FIP: {stats_dict['투수_FIP']['mean']:.2f}")
    if 'wRC+_정규성' in hypothesis:
        h = hypothesis['wRC+_정규성']
        findings.append(f"wRC+ 분포: {h['interpretation']}")
    if 'WAR상위_하위_wRC+차이' in hypothesis:
        h = hypothesis['WAR상위_하위_wRC+차이']
        findings.append(f"WAR 상위 vs 하위 wRC+ 차이: {h['interpretation']} (평균 차이: {h['mean_diff']:.1f})")
    if 'ERA_FIP_상관' in hypothesis:
        h = hypothesis['ERA_FIP_상관']
        findings.append(f"ERA-FIP 상관관계: {h['interpretation']}")

    insight = StatsUtils.format_insight('세이버메트릭스 트렌드 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
