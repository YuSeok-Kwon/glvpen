"""
Topic 4: 상대전적 패턴 - 팀 간 맞대결 숨은 데이터

가설검정:
1. 팀별 상대전적 불균형 유의성 (카이제곱)
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    h2h = db.get_head_to_head(season)
    if h2h.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # 총 경기 수 계산
    h2h['total'] = h2h['wins'] + h2h['losses'] + h2h['draws']
    h2h['winRate'] = h2h.apply(
        lambda r: round(r['wins'] / r['total'], 3) if r['total'] > 0 else 0, axis=1
    )

    # 압도적 상대전적 (승률 0.7 이상, 5경기 이상)
    dominant = h2h[(h2h['winRate'] >= 0.7) & (h2h['total'] >= 5)].sort_values('winRate', ascending=False)
    stats_dict['압도적_상대전적'] = dominant[
        ['teamName', 'opponentName', 'wins', 'losses', 'draws', 'winRate']
    ].head(5).to_dict('records')

    # 기술통계
    stats_dict['상대전적_승률_기술통계'] = StatsUtils.descriptive(
        h2h['winRate'].values, '상대전적 승률'
    )

    # ==================== 카이제곱 검정 ====================
    # 각 팀의 홈/원정 승패가 균등한지 검정
    teams = h2h['teamName'].unique()
    if len(teams) >= 2:
        # 팀별 총 승/패 집계
        team_records = h2h.groupby('teamName').agg({'wins': 'sum', 'losses': 'sum'}).reset_index()
        if len(team_records) >= 2:
            observed = team_records[['wins', 'losses']].values
            if observed.min() > 0:
                hypothesis['팀별_승패_균등성'] = StatsUtils.chi_square(observed, '팀별 승패 균등성')
                findings.append(f"팀별 승패 균등성: {hypothesis['팀별_승패_균등성']['interpretation']}")

    # ==================== 차트 ====================
    # Bar: 팀별 상대전적 총 승률
    team_overall = h2h.groupby('teamName').agg({
        'wins': 'sum', 'losses': 'sum', 'draws': 'sum'
    }).reset_index()
    team_overall['total'] = team_overall['wins'] + team_overall['losses'] + team_overall['draws']
    team_overall['winRate'] = team_overall['wins'] / team_overall['total']
    team_overall = team_overall.sort_values('winRate', ascending=False)

    charts.append(ChartBuilder.bar(
        '팀별 상대전적 종합 승률',
        team_overall['teamName'].tolist(),
        [{'label': '승률', 'data': [round(v, 3) for v in team_overall['winRate'].tolist()]}]
    ))

    # 압도적 매치업 차트
    if len(stats_dict['압도적_상대전적']) > 0:
        matchups = stats_dict['압도적_상대전적']
        charts.append(ChartBuilder.bar(
            '압도적 상대전적 Top 5',
            [f"{m['teamName']} vs {m['opponentName']}" for m in matchups],
            [{'label': '승률', 'data': [m['winRate'] for m in matchups]}]
        ))

    findings.insert(0, f"분석 대상 팀: {len(teams)}개, 총 매치업: {len(h2h)}건")
    insight = StatsUtils.format_insight('상대전적 패턴 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
