"""
g5. 경기 시간 분석
- 대상 팀 평균 경기 시간 vs 리그 평균
- 10팀 평균 경기 시간 비교
- 경기 시간과 총 득점/안타의 상관
- 월별 평균 경기 시간 추이
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
    get_team_name, save_chart_json, print_header, print_section, print_stat,
    safe_float, filter_schedules_by_type, filter_score_boards, parse_game_time,
    output_filename, TEAM_NAMES, LATEST_SEASON, GAME_TYPES,
)


def _build_time_data(schedules, score_boards):
    """경기 시간 + R/H 데이터 병합"""
    merged = score_boards.merge(
        schedules[['id', 'homeTeamId', 'awayTeamId', 'gameDate']],
        left_on='scheduleId', right_on='id', how='inner', suffixes=('_sb', '')
    )

    rows = []
    for _, row in merged.iterrows():
        gt = parse_game_time(row.get('gameTime'))
        if gt <= 0:
            continue
        total_r = safe_float(row.get('homeR', 0)) + safe_float(row.get('awayR', 0))
        total_h = safe_float(row.get('homeH', 0)) + safe_float(row.get('awayH', 0))
        game_date = pd.to_datetime(row['gameDate'])
        rows.append({
            'scheduleId': row['scheduleId'],
            'homeTeamId': row['homeTeamId'],
            'awayTeamId': row['awayTeamId'],
            'gameTime': gt,
            'totalR': total_r,
            'totalH': total_h,
            'month': game_date.month,
        })
    return pd.DataFrame(rows)


def analyze(team_id: int, game_type: str = 'regular'):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('경기 시간 분석', team_name, game_type)

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

        time_data = _build_time_data(schedules, score_boards)
        if time_data.empty:
            print("  경기 시간 데이터가 없습니다.")
            return

        charts = []
        findings = []
        hypothesis = {}

        # 팀별 평균 경기 시간
        team_avg_times = {}
        for tid in TEAM_NAMES:
            mask = (time_data['homeTeamId'] == tid) | (time_data['awayTeamId'] == tid)
            team_games = time_data[mask]
            if len(team_games) > 0:
                team_avg_times[tid] = round(team_games['gameTime'].mean(), 1)
            else:
                team_avg_times[tid] = 0

        league_avg = round(time_data['gameTime'].mean(), 1)
        team_avg = team_avg_times.get(team_id, 0)

        # ── 1) Bar: 10팀 평균 경기 시간 ──
        print_section('10팀 평균 경기 시간')
        sorted_teams = sorted(team_avg_times.items(), key=lambda x: x[1], reverse=True)
        bar_labels = [get_team_name(t) for t, _ in sorted_teams]
        bar_data = [v for _, v in sorted_teams]

        charts.append(ChartBuilder.bar(
            '10팀 평균 경기 시간 (분)',
            bar_labels,
            [{'label': '평균 시간 (분)', 'data': bar_data}]
        ))

        rank = next(i + 1 for i, (t, _) in enumerate(sorted_teams) if t == team_id)
        findings.append(f"{team_name} 평균 경기 시간: {team_avg:.1f}분 ({rank}위, 리그 평균 {league_avg:.1f}분)")
        print_stat('평균 경기 시간', f'{team_avg:.1f}분 (리그 평균 {league_avg:.1f}분)')

        # ── 2) Scatter: 경기 시간 vs 총 득점 ──
        print_section('경기 시간 vs 총 득점')
        scatter_data = [{'x': float(row['gameTime']), 'y': float(row['totalR'])}
                        for _, row in time_data.iterrows()]
        charts.append(ChartBuilder.scatter(
            '경기 시간 vs 총 득점',
            [{'label': '전체 경기', 'data': scatter_data}]
        ))

        corr_r = StatsUtils.correlation(
            time_data['gameTime'].values.astype(float),
            time_data['totalR'].values.astype(float),
            '경기 시간', '총 득점'
        )
        hypothesis['시간_득점_상관'] = corr_r
        findings.append(f"경기 시간-총 득점: {corr_r['interpretation']}")
        print_stat('시간-득점 상관', corr_r['interpretation'])

        # 경기 시간 vs 안타 상관
        corr_h = StatsUtils.correlation(
            time_data['gameTime'].values.astype(float),
            time_data['totalH'].values.astype(float),
            '경기 시간', '총 안타'
        )
        hypothesis['시간_안타_상관'] = corr_h
        findings.append(f"경기 시간-총 안타: {corr_h['interpretation']}")
        print_stat('시간-안타 상관', corr_h['interpretation'])

        # ── 3) Line: 월별 평균 경기 시간 추이 ──
        print_section('월별 평균 경기 시간')
        monthly = time_data.groupby('month')['gameTime'].mean()
        month_labels = [f'{m}월' for m in sorted(monthly.index)]
        month_data = [round(monthly[m], 1) for m in sorted(monthly.index)]

        charts.append(ChartBuilder.line(
            '월별 평균 경기 시간 추이',
            month_labels,
            [{'label': '평균 시간 (분)', 'data': month_data}]
        ))

        if len(month_data) >= 2:
            diff = month_data[-1] - month_data[0]
            direction = '증가' if diff > 0 else '감소'
            findings.append(f"월별 경기 시간 추이: {month_labels[0]} → {month_labels[-1]} {abs(diff):.1f}분 {direction}")
        print_stat('월별 추이', f'{month_labels[0]} {month_data[0]}분 → {month_labels[-1]} {month_data[-1]}분')

        # 인사이트
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 경기 시간 분석")
        insight = StatsUtils.format_insight('경기 시간 분석', findings)
        print_section('인사이트')
        print(f"  {insight}")

        fname = output_filename('g5', team_name, LATEST_SEASON, game_type)
        save_chart_json(fname, charts)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
