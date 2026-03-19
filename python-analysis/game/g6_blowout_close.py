"""
g6. 접전/대패 분류
- 접전(1~2점차) / 중간(3~4점차) / 대파(5점+) 비율
- 유형별 승률
- 10팀 접전 비율 비교
- 접전/대파 경기 승률 카이제곱
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
    safe_float, filter_schedules_by_type, get_team_games,
    output_filename, TEAM_NAMES, LATEST_SEASON, GAME_TYPES,
)


def _classify_margin(diff):
    """점수차 기반 경기 유형 분류"""
    d = abs(diff)
    if d <= 2:
        return '접전'
    elif d <= 4:
        return '중간'
    else:
        return '대파'


def _calc_margin_stats(team_games):
    """팀 경기별 점수차 유형 분류 및 통계"""
    if team_games.empty:
        return {}

    results = {'접전': {'total': 0, 'win': 0}, '중간': {'total': 0, 'win': 0}, '대파': {'total': 0, 'win': 0}}
    for _, row in team_games.iterrows():
        ts = safe_float(row['teamScore'])
        os_val = safe_float(row['opponentScore'])
        diff = ts - os_val
        category = _classify_margin(diff)
        results[category]['total'] += 1
        if ts > os_val:
            results[category]['win'] += 1

    return results


def analyze(team_id: int, game_type: str = 'regular'):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('접전/대패 분류 분석', team_name, game_type)

        schedules = db.get_schedules(LATEST_SEASON)
        if schedules.empty:
            print("  데이터가 없습니다.")
            return

        schedules = filter_schedules_by_type(schedules, game_type)
        if schedules.empty:
            print(f"  {GAME_TYPES.get(game_type, game_type)} 경기 데이터가 없습니다.")
            return

        charts = []
        findings = []
        hypothesis = {}

        team_games = get_team_games(schedules, team_id)
        if team_games.empty:
            print("  경기 데이터가 없습니다.")
            return

        margin_stats = _calc_margin_stats(team_games)

        # ── 1) Doughnut: 접전/중간/대파 비율 ──
        print_section('접전/중간/대파 비율')
        categories = ['접전', '중간', '대파']
        cat_totals = [margin_stats[c]['total'] for c in categories]
        total_games = sum(cat_totals)

        charts.append(ChartBuilder.doughnut(
            f'{team_name} 경기 유형 비율',
            categories,
            cat_totals,
        ))

        for c, cnt in zip(categories, cat_totals):
            pct = round(cnt / total_games * 100, 1) if total_games > 0 else 0
            findings.append(f"{c} 경기: {cnt}경기 ({pct}%)")
            print_stat(c, f'{cnt}경기 ({pct}%)')

        # ── 2) Bar: 유형별 승률 ──
        print_section('유형별 승률')
        cat_wr = []
        for c in categories:
            total = margin_stats[c]['total']
            win = margin_stats[c]['win']
            wr = round(win / total * 100, 1) if total > 0 else 0
            cat_wr.append(wr)
            print_stat(f'{c} 승률', f'{wr}% ({win}/{total})')

        charts.append(ChartBuilder.bar(
            f'{team_name} 유형별 승률',
            categories,
            [{'label': '승률 (%)', 'data': cat_wr}]
        ))
        findings.append(f"접전 승률: {cat_wr[0]}%, 대파 승률: {cat_wr[2]}%")

        # ── 3) Bar: 10팀 접전 승률 비교 ──
        print_section('10팀 접전 승률 비교')
        close_win_rates = {}
        for tid in TEAM_NAMES:
            tg = get_team_games(schedules, tid)
            ms = _calc_margin_stats(tg)
            close = ms.get('접전', {'total': 0, 'win': 0})
            wr = round(close['win'] / close['total'] * 100, 1) if close['total'] > 0 else 0
            close_win_rates[tid] = wr

        sorted_teams = sorted(close_win_rates.items(), key=lambda x: x[1], reverse=True)
        bar_labels = [get_team_name(t) for t, _ in sorted_teams]
        bar_data = [r for _, r in sorted_teams]

        charts.append(ChartBuilder.bar(
            '10팀 접전 경기(1~2점차) 승률',
            bar_labels,
            [{'label': '접전 승률 (%)', 'data': bar_data}]
        ))

        rank = next(i + 1 for i, (t, _) in enumerate(sorted_teams) if t == team_id)
        findings.append(f"{team_name} 접전 승률 {rank}위")

        # ── 4) 통계검정: 접전/대파 승률 카이제곱 ──
        print_section('통계검정')
        close = margin_stats.get('접전', {'total': 0, 'win': 0})
        blowout = margin_stats.get('대파', {'total': 0, 'win': 0})
        if close['total'] > 0 and blowout['total'] > 0:
            c_win = close['win']
            c_loss = close['total'] - c_win
            b_win = blowout['win']
            b_loss = blowout['total'] - b_win
            observed = np.array([[c_win, c_loss], [b_win, b_loss]])
            chi = StatsUtils.chi_square(observed, '접전 vs 대파 승률')
            hypothesis['접전대파_카이제곱'] = chi
            findings.append(f"접전 vs 대파 승률: {chi['interpretation']}")
            print_stat('카이제곱', chi['interpretation'])

        # 인사이트
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 접전/대패 분류 분석")
        insight = StatsUtils.format_insight('접전/대패 분류', findings)
        print_section('인사이트')
        print(f"  {insight}")

        fname = output_filename('g6', team_name, LATEST_SEASON, game_type)
        save_analysis_output(fname, charts, findings)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
