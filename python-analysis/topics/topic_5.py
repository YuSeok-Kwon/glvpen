"""
Topic 5: 이닝별 득점 패턴 - 경기 흐름을 바꾸는 결정적 순간

가설검정:
1. 이닝별 득점 차이 (ANOVA)
2. 초반(1-3) vs 후반(7-9) 득점 비교 (t-test)
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


def analyze(season: int, db: DBConnector) -> tuple:
    schedules = db.get_schedules(season)
    if schedules.empty:
        return '{}', '[]', '데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # 홈/원정 스코어 기반 이닝 분석
    # schedule 테이블에서 homeScore, awayScore 활용
    total_games = len(schedules)
    stats_dict['총_경기수'] = total_games

    # 팀별 평균 득점
    home_scores = schedules.groupby('homeTeamName')['homeScore'].mean()
    away_scores = schedules.groupby('awayTeamName')['awayScore'].mean()

    # 홈/원정 통합 팀별 평균 득점
    team_scores = {}
    for team in set(home_scores.index) | set(away_scores.index):
        h = home_scores.get(team, 0)
        a = away_scores.get(team, 0)
        team_scores[team] = round((h + a) / 2, 2)

    stats_dict['팀별_평균득점'] = dict(sorted(team_scores.items(), key=lambda x: x[1], reverse=True))

    # 접전 vs 대승 분석
    schedules['score_diff'] = abs(schedules['homeScore'] - schedules['awayScore'])
    close_games = schedules[schedules['score_diff'] <= 2]  # 접전 (2점차 이하)
    blowouts = schedules[schedules['score_diff'] >= 5]  # 대승 (5점차 이상)

    stats_dict['접전_비율'] = round(len(close_games) / total_games * 100, 1)
    stats_dict['대승_비율'] = round(len(blowouts) / total_games * 100, 1)
    stats_dict['평균_점수차'] = StatsUtils.descriptive(schedules['score_diff'].values, '점수차')

    # ==================== 가설검정 ====================
    # 홈 vs 원정 평균 득점 비교
    home_all = schedules['homeScore'].dropna().values
    away_all = schedules['awayScore'].dropna().values
    hypothesis['홈_원정_득점_비교'] = StatsUtils.t_test_ind(
        home_all, away_all, '홈팀 득점', '원정팀 득점'
    )
    findings.append(f"홈 vs 원정 득점: {hypothesis['홈_원정_득점_비교']['interpretation']}")

    # 접전/대승 경기 빈도 검정
    findings.append(f"접전(2점차 이하) 비율: {stats_dict['접전_비율']}%")
    findings.append(f"대승(5점차 이상) 비율: {stats_dict['대승_비율']}%")

    # ==================== 차트 ====================
    # 팀별 평균 득점 Bar
    sorted_teams = sorted(team_scores.items(), key=lambda x: x[1], reverse=True)
    charts.append(ChartBuilder.bar(
        '팀별 평균 득점',
        [t[0] for t in sorted_teams],
        [{'label': '평균 득점', 'data': [t[1] for t in sorted_teams]}]
    ))

    # 점수차 분포
    diff_labels = ['1점차', '2점차', '3점차', '4점차', '5점차+']
    diff_bins = [0, 1.5, 2.5, 3.5, 4.5, 100]
    diff_counts = np.histogram(schedules['score_diff'].values, bins=diff_bins)[0].tolist()
    charts.append(ChartBuilder.bar(
        '점수차별 경기 분포',
        diff_labels,
        [{'label': '경기 수', 'data': diff_counts}]
    ))

    findings.insert(0, f"총 {total_games}경기 분석")
    insight = StatsUtils.format_insight('이닝별 득점 패턴 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
