"""
g1. 이닝별 득점 패턴
- 이닝별 평균 득점 (대상 팀 vs 리그 평균)
- 전반(1~5회) vs 후반(6~9회) 득점 비중
- 누적 득점 곡선
- 쌍표본 t-test: 전반 vs 후반 득점
"""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from game.game_common import (
    get_team_name, save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, filter_schedules_by_type, parse_inning_scores,
    filter_score_boards, output_filename, TEAM_NAMES, LATEST_SEASON, GAME_TYPES,
)


def _calc_inning_scores(schedules, score_boards, team_id):
    """팀의 경기별 이닝 득점 리스트 계산"""
    merged = score_boards.merge(
        schedules[['id', 'homeTeamId', 'awayTeamId']],
        left_on='scheduleId', right_on='id', how='inner', suffixes=('', '_sched')
    )

    team_innings = []
    for _, row in merged.iterrows():
        if row['homeTeamId'] == team_id:
            scores = parse_inning_scores(row.get('homeInningScores'))
        elif row['awayTeamId'] == team_id:
            scores = parse_inning_scores(row.get('awayInningScores'))
        else:
            continue
        if scores:
            team_innings.append(scores)

    return team_innings


def _calc_all_teams_inning_scores(schedules, score_boards):
    """전체 팀의 이닝별 득점 데이터"""
    merged = score_boards.merge(
        schedules[['id', 'homeTeamId', 'awayTeamId']],
        left_on='scheduleId', right_on='id', how='inner', suffixes=('', '_sched')
    )

    all_innings = {}
    for tid in TEAM_NAMES:
        team_innings = []
        for _, row in merged.iterrows():
            if row['homeTeamId'] == tid:
                scores = parse_inning_scores(row.get('homeInningScores'))
            elif row['awayTeamId'] == tid:
                scores = parse_inning_scores(row.get('awayInningScores'))
            else:
                continue
            if scores:
                team_innings.append(scores)
        all_innings[tid] = team_innings

    return all_innings


def analyze(team_id: int, game_type: str = 'regular'):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('이닝별 득점 패턴 분석', team_name, game_type)

        schedules = db.get_schedules(LATEST_SEASON)
        score_boards = db.get_score_boards(LATEST_SEASON)
        if schedules.empty or score_boards.empty:
            print("  데이터가 없습니다.")
            return

        schedules = filter_schedules_by_type(schedules, game_type)
        if schedules.empty:
            print(f"  {GAME_TYPES.get(game_type, game_type)} 경기 데이터가 없습니다.")
            return

        valid_ids = set(schedules['id'])
        score_boards = filter_score_boards(score_boards, valid_ids)

        charts = []
        findings = []
        hypothesis = {}

        # 대상 팀 이닝별 득점
        team_innings = _calc_inning_scores(schedules, score_boards, team_id)
        if not team_innings:
            print("  이닝 데이터가 없습니다.")
            return

        max_innings = max(len(g) for g in team_innings)
        max_innings = max(max_innings, 9)

        # 이닝별 평균 득점
        team_avg = []
        for i in range(max_innings):
            vals = [g[i] for g in team_innings if len(g) > i]
            team_avg.append(round(np.mean(vals), 3) if vals else 0)

        # 리그 전체 이닝 득점
        all_innings = _calc_all_teams_inning_scores(schedules, score_boards)
        league_all = []
        for tid_data in all_innings.values():
            league_all.extend(tid_data)

        league_avg = []
        for i in range(max_innings):
            vals = [g[i] for g in league_all if len(g) > i]
            league_avg.append(round(np.mean(vals), 3) if vals else 0)

        inning_labels = [f'{i+1}회' for i in range(min(max_innings, 12))]

        # ── 1) Bar: 대상 팀 이닝별 평균 득점 vs 리그 평균 ──
        print_section('이닝별 평균 득점')
        display_len = min(len(team_avg), len(inning_labels))
        charts.append(ChartBuilder.bar(
            f'{team_name} 이닝별 평균 득점 vs 리그 평균',
            inning_labels[:display_len],
            [
                {'label': team_name, 'data': team_avg[:display_len]},
                {'label': '리그 평균', 'data': league_avg[:display_len]},
            ]
        ))

        best_inning = int(np.argmax(team_avg[:9])) + 1
        findings.append(f"{team_name} 최다 득점 이닝: {best_inning}회 (평균 {team_avg[best_inning-1]:.3f}점)")
        print_stat('최다 득점 이닝', f'{best_inning}회 ({team_avg[best_inning-1]:.3f}점)')

        # ── 전반/후반 득점 비중 ──
        print_section('전반/후반 득점 비중')
        first_half_scores = {}
        second_half_scores = {}
        for tid in TEAM_NAMES:
            tid_innings = all_innings.get(tid, [])
            fh = []
            sh = []
            for game in tid_innings:
                fh.append(sum(game[:5]) if len(game) >= 5 else sum(game))
                sh.append(sum(game[5:9]) if len(game) >= 6 else 0)
            first_half_scores[tid] = round(np.mean(fh), 2) if fh else 0
            second_half_scores[tid] = round(np.mean(sh), 2) if sh else 0

        # ── 2) Stacked Bar: 전반/후반 득점 비중 (10팀) ──
        sorted_tids = sorted(TEAM_NAMES.keys())
        team_labels = [get_team_name(t) for t in sorted_tids]
        fh_data = [first_half_scores.get(t, 0) for t in sorted_tids]
        sh_data = [second_half_scores.get(t, 0) for t in sorted_tids]

        charts.append(ChartBuilder.stacked_bar(
            '10팀 전반/후반 평균 득점',
            team_labels,
            [
                {'label': '전반 (1~5회)', 'data': fh_data},
                {'label': '후반 (6~9회)', 'data': sh_data},
            ]
        ))

        team_fh = first_half_scores.get(team_id, 0)
        team_sh = second_half_scores.get(team_id, 0)
        total = team_fh + team_sh
        if total > 0:
            fh_pct = round(team_fh / total * 100, 1)
            findings.append(f"전반 득점 비중: {fh_pct}% (전반 {team_fh:.2f}, 후반 {team_sh:.2f})")
            print_stat('전반 비중', f'{fh_pct}%')
            print_stat('후반 비중', f'{100 - fh_pct:.1f}%')

        # ── 3) Line: 누적 득점 곡선 ──
        print_section('누적 득점 곡선')
        team_cumsum = np.cumsum(team_avg[:9]).tolist()
        league_cumsum = np.cumsum(league_avg[:9]).tolist()

        charts.append(ChartBuilder.line(
            f'{team_name} 누적 득점 곡선 vs 리그 평균',
            inning_labels[:9],
            [
                {'label': team_name, 'data': [round(v, 3) for v in team_cumsum]},
                {'label': '리그 평균', 'data': [round(v, 3) for v in league_cumsum]},
            ]
        ))

        # ── 4) 통계검정: 전반 vs 후반 paired t-test ──
        print_section('통계검정: 전반 vs 후반 득점')
        fh_game_scores = []
        sh_game_scores = []
        for game in team_innings:
            fh_game_scores.append(sum(game[:5]) if len(game) >= 5 else sum(game))
            sh_game_scores.append(sum(game[5:9]) if len(game) >= 6 else 0)

        ttest = StatsUtils.t_test_paired(
            np.array(fh_game_scores, dtype=float),
            np.array(sh_game_scores, dtype=float),
            '전반 vs 후반 득점'
        )
        hypothesis['전반후반_t검정'] = ttest
        findings.append(f"전반 vs 후반 득점: {ttest['interpretation']}")
        print_stat('t-검정', ttest['interpretation'])

        # 인사이트
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 이닝별 득점 패턴 분석")
        insight = StatsUtils.format_insight('이닝별 득점 패턴', findings)
        print_section('인사이트')
        print(f"  {insight}")

        fname = output_filename('g1', team_name, LATEST_SEASON, game_type)
        save_analysis_output(fname, charts, findings)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
