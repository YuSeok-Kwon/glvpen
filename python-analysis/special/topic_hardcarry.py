"""
특집: 선수 1~2명이 팀을 하드캐리 할 수 있는가?

분석 방법:
- 팀별 상위 1~2명의 WAR 합계 / 팀 전체 WAR 비율 계산
- 캐리 의존도(top1 WAR / team WAR) 분석
- 캐리 의존도와 팀 승률 간 상관분석
- 상위 의존도 팀 vs 분산형 팀 승률 비교 (t-test)

가설: '소수 핵심 선수 의존도가 높은 팀은 승률이 낮다(리스크 집중)'
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


TOPIC_INDEX = 15  # 특집 토픽 번호


def analyze(season: int, db: DBConnector) -> tuple:
    batters = db.get_batters(season)
    pitchers = db.get_pitchers(season)
    rankings = db.get_team_rankings(season)

    if batters.empty or rankings.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # ==================== 팀별 캐리 의존도 계산 ====================
    carry_data = []

    for _, team_row in rankings.iterrows():
        team_name = team_row['teamName']

        # 타자 WAR
        team_bat = batters[batters['teamName'] == team_name]
        bat_war = team_bat['WAR'].dropna() if 'WAR' in team_bat.columns else []

        # 투수 WAR
        team_pit = pitchers[pitchers['teamName'] == team_name] if not pitchers.empty else pd.DataFrame()
        pit_war = team_pit['WAR'].dropna() if not team_pit.empty and 'WAR' in team_pit.columns else []

        # 전체 WAR 합산
        all_wars = list(bat_war) + list(pit_war)
        if len(all_wars) < 3:
            continue

        all_wars_sorted = sorted(all_wars, reverse=True)
        total_war = sum(all_wars_sorted)
        if total_war <= 0:
            continue

        top1_war = all_wars_sorted[0]
        top2_war = sum(all_wars_sorted[:2])

        carry_data.append({
            'teamName': team_name,
            'totalWAR': round(total_war, 2),
            'top1WAR': round(top1_war, 2),
            'top2WAR': round(top2_war, 2),
            'top1Ratio': round(top1_war / total_war * 100, 2),
            'top2Ratio': round(top2_war / total_war * 100, 2),
            'winRate': float(team_row['winRate']) if 'winRate' in team_row else 0.0,
            'ranking': int(team_row['ranking']),
        })

    if not carry_data:
        return '{}', '[]', '캐리 분석 데이터 부족', '{}'

    carry_df = pd.DataFrame(carry_data)
    stats_dict['캐리_의존도'] = carry_data

    # 기술통계
    stats_dict['top1의존도_통계'] = StatsUtils.descriptive(
        carry_df['top1Ratio'].values, 'Top1 WAR 의존도(%)'
    )
    stats_dict['top2의존도_통계'] = StatsUtils.descriptive(
        carry_df['top2Ratio'].values, 'Top2 WAR 의존도(%)'
    )

    findings.append(f"Top1 의존도 평균: {stats_dict['top1의존도_통계']['mean']:.1f}%")
    findings.append(f"Top2 의존도 평균: {stats_dict['top2의존도_통계']['mean']:.1f}%")

    # ==================== 상관분석: 의존도 vs 승률 ====================
    if len(carry_df) >= 5:
        hypothesis['top1의존도_승률_상관'] = StatsUtils.correlation(
            carry_df['top1Ratio'].values,
            carry_df['winRate'].values,
            'Top1 의존도', '승률'
        )
        findings.append(f"Top1 의존도-승률: {hypothesis['top1의존도_승률_상관']['interpretation']}")

        hypothesis['top2의존도_승률_상관'] = StatsUtils.correlation(
            carry_df['top2Ratio'].values,
            carry_df['winRate'].values,
            'Top2 의존도', '승률'
        )
        findings.append(f"Top2 의존도-승률: {hypothesis['top2의존도_승률_상관']['interpretation']}")

    # ==================== 의존도 상위 vs 하위 팀 승률 비교 ====================
    if len(carry_df) >= 6:
        median_dep = carry_df['top1Ratio'].median()
        high_dep = carry_df[carry_df['top1Ratio'] >= median_dep]['winRate'].values
        low_dep = carry_df[carry_df['top1Ratio'] < median_dep]['winRate'].values

        if len(high_dep) >= 2 and len(low_dep) >= 2:
            hypothesis['의존도_상하위_승률'] = StatsUtils.t_test_ind(
                high_dep, low_dep,
                '높은 의존도 팀', '낮은 의존도 팀'
            )
            findings.append(f"의존도 상/하위 승률: {hypothesis['의존도_상하위_승률']['interpretation']}")

    # ==================== 차트 ====================
    # 팀별 Top1/Top2 의존도
    team_names = [d['teamName'] for d in carry_data]
    charts.append(ChartBuilder.bar(
        '팀별 핵심 선수 WAR 의존도(%)',
        team_names,
        [
            {'label': 'Top1 의존도', 'data': [d['top1Ratio'] for d in carry_data]},
            {'label': 'Top2 의존도', 'data': [d['top2Ratio'] for d in carry_data]},
        ]
    ))

    # 산점도: 의존도 vs 승률
    charts.append(ChartBuilder.scatter(
        'Top1 의존도 vs 팀 승률',
        [{'label': '팀', 'data': [{'x': d['top1Ratio'], 'y': d['winRate']} for d in carry_data]}]
    ))

    # 팀 총 WAR 분포
    charts.append(ChartBuilder.bar(
        '팀별 총 WAR',
        team_names,
        [{'label': '총 WAR', 'data': [d['totalWAR'] for d in carry_data]}]
    ))

    findings.insert(0, f"팀별 하드캐리 분석 ({season}시즌)")
    insight = StatsUtils.format_insight('하드캐리 분석: 소수 정예 vs 전력 분산', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
