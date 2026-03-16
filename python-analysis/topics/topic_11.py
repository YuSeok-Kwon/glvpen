"""
Topic 11: 나이-성과 커브 - KBO 선수들의 피크 시즌은 언제인가

가설검정:
1. 나이대별 WAR 차이 (ANOVA)
2. 각 나이대 WAR 정규성 검정
"""
import json
import numpy as np
import pandas as pd
from datetime import date
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    batters = db.get_batters(season)
    players = db.get_players_with_birth(season)

    if batters.empty or players.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # 나이 계산
    players['birthDate'] = pd.to_datetime(players['birthDate'], errors='coerce')
    players = players.dropna(subset=['birthDate'])
    players['age'] = players['birthDate'].apply(
        lambda bd: date(season, 7, 1).year - bd.year  # 시즌 중반(7월) 기준
    )

    # 타자 WAR과 나이 병합
    if 'WAR' in batters.columns:
        merged = batters[['playerId', 'playerName', 'teamName', 'WAR']].merge(
            players[['playerId', 'age']], on='playerId', how='inner'
        ).dropna()

        # 나이대 구분
        age_bins = [19, 23, 26, 29, 32, 45]
        age_labels = ['20-23', '24-26', '27-29', '30-32', '33+']
        merged['age_group'] = pd.cut(merged['age'], bins=age_bins, labels=age_labels, right=True)

        # 나이대별 기술통계
        age_stats = {}
        age_groups_for_anova = {}
        for label in age_labels:
            group = merged[merged['age_group'] == label]['WAR'].dropna()
            if len(group) >= 2:
                age_stats[label] = StatsUtils.descriptive(group.values, f'{label}세')
                age_groups_for_anova[label] = group.values

        stats_dict['나이대별_WAR'] = age_stats

        # 피크 나이대 식별
        if age_stats:
            peak_group = max(age_stats.items(), key=lambda x: x[1]['mean'])
            stats_dict['피크_나이대'] = peak_group[0]
            stats_dict['피크_평균WAR'] = peak_group[1]['mean']
            findings.append(f"피크 나이대: {peak_group[0]}세 (평균 WAR: {peak_group[1]['mean']:.2f})")

        # ANOVA: 나이대별 WAR 차이
        if len(age_groups_for_anova) >= 3:
            hypothesis['나이대별_WAR_ANOVA'] = StatsUtils.anova(age_groups_for_anova)
            findings.append(f"나이대별 WAR: {hypothesis['나이대별_WAR_ANOVA']['interpretation']}")

        # 나이대별 정규성 검정 (피크 나이대)
        if stats_dict.get('피크_나이대') and stats_dict['피크_나이대'] in age_groups_for_anova:
            peak_data = age_groups_for_anova[stats_dict['피크_나이대']]
            hypothesis['피크나이대_WAR_정규성'] = StatsUtils.normality_test(
                peak_data, f"{stats_dict['피크_나이대']}세 WAR"
            )

        # 차트: 나이대별 평균 WAR
        if age_stats:
            charts.append(ChartBuilder.line(
                '나이대별 평균 WAR (피크 커브)',
                list(age_stats.keys()),
                [{'label': '평균 WAR', 'data': [s['mean'] for s in age_stats.values()]}]
            ))

        # 나이대별 선수 수 분포
        age_counts = merged['age_group'].value_counts().sort_index()
        charts.append(ChartBuilder.bar(
            '나이대별 선수 수',
            age_counts.index.tolist(),
            [{'label': '선수 수', 'data': age_counts.values.tolist()}]
        ))

        # 나이별 WAR 상위 선수
        top_by_age = merged.loc[merged.groupby('age_group')['WAR'].idxmax()]
        stats_dict['나이대별_최고선수'] = top_by_age[
            ['playerName', 'teamName', 'age', 'age_group', 'WAR']
        ].to_dict('records')

    insight = StatsUtils.format_insight('나이-성과 커브 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
