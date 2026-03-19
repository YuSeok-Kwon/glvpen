"""
g4. 승리 요인 분석
- 안타 차이별 승률
- 실책 유무 경기 승률 차이
- 볼넷 차이별 승률
- R/H/E/B 프로필 vs 리그
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
    safe_float, filter_schedules_by_type, filter_score_boards,
    output_filename, TEAM_NAMES, LATEST_SEASON, GAME_TYPES,
)


def _build_game_data(schedules, score_boards, team_id):
    """팀 경기별 R/H/E/B 데이터 병합"""
    merged = score_boards.merge(
        schedules[['id', 'homeTeamId', 'awayTeamId', 'homeScore', 'awayScore']],
        left_on='scheduleId', right_on='id', how='inner', suffixes=('_sb', '')
    )

    rows = []
    for _, row in merged.iterrows():
        is_home = row['homeTeamId'] == team_id
        is_away = row['awayTeamId'] == team_id
        if not is_home and not is_away:
            continue

        if is_home:
            team_r = safe_float(row.get('homeR', 0))
            team_h = safe_float(row.get('homeH', 0))
            team_e = safe_float(row.get('homeE', 0))
            team_b = safe_float(row.get('homeB', 0))
            opp_r = safe_float(row.get('awayR', 0))
            opp_h = safe_float(row.get('awayH', 0))
            opp_e = safe_float(row.get('awayE', 0))
            opp_b = safe_float(row.get('awayB', 0))
        else:
            team_r = safe_float(row.get('awayR', 0))
            team_h = safe_float(row.get('awayH', 0))
            team_e = safe_float(row.get('awayE', 0))
            team_b = safe_float(row.get('awayB', 0))
            opp_r = safe_float(row.get('homeR', 0))
            opp_h = safe_float(row.get('homeH', 0))
            opp_e = safe_float(row.get('homeE', 0))
            opp_b = safe_float(row.get('homeB', 0))

        win = 1 if team_r > opp_r else (0 if team_r < opp_r else -1)
        rows.append({
            'team_r': team_r, 'team_h': team_h, 'team_e': team_e, 'team_b': team_b,
            'opp_r': opp_r, 'opp_h': opp_h, 'opp_e': opp_e, 'opp_b': opp_b,
            'h_diff': team_h - opp_h, 'r_diff': team_r - opp_r,
            'b_diff': team_b - opp_b, 'win': win,
        })
    return pd.DataFrame(rows)


def analyze(team_id: int, game_type: str = 'regular'):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('승리 요인 분석', team_name, game_type)

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

        game_data = _build_game_data(schedules, score_boards, team_id)
        if game_data.empty:
            print("  경기 데이터가 없습니다.")
            return

        # ── 1) Bar: 안타 차이 구간별 승률 ──
        print_section('안타 차이별 승률')
        bins = range(-6, 7)
        win_rates_by_h = {}
        for h_diff in bins:
            if h_diff <= -5:
                mask = game_data['h_diff'] <= -5
                label = '-5이하'
            elif h_diff >= 5:
                mask = game_data['h_diff'] >= 5
                label = '+5이상'
            else:
                mask = game_data['h_diff'] == h_diff
                label = f'{h_diff:+d}'
            subset = game_data[mask]
            if len(subset) > 0:
                wr = round((subset['win'] == 1).mean() * 100, 1)
                win_rates_by_h[label] = wr

        h_labels = sorted(win_rates_by_h.keys(), key=lambda x: int(x.replace('이하', '').replace('이상', '').replace('+', '')))
        h_data = [win_rates_by_h[l] for l in h_labels]

        charts.append(ChartBuilder.bar(
            f'{team_name} 안타 차이별 승률',
            h_labels,
            [{'label': '승률 (%)', 'data': h_data}]
        ))
        findings.append(f"안타 +3 이상 시 승률: {win_rates_by_h.get('+3', 0):.1f}%")

        # ── 2) Bar: 실책 유무 경기 승률 ──
        print_section('실책 유무 승률')
        error_games = game_data[game_data['team_e'] > 0]
        no_error_games = game_data[game_data['team_e'] == 0]
        error_wr = round((error_games['win'] == 1).mean() * 100, 1) if len(error_games) > 0 else 0
        no_error_wr = round((no_error_games['win'] == 1).mean() * 100, 1) if len(no_error_games) > 0 else 0

        # 리그 전체 실책 승률
        league_data = pd.DataFrame()
        for tid in TEAM_NAMES:
            td = _build_game_data(schedules, score_boards, tid)
            if not td.empty:
                league_data = pd.concat([league_data, td], ignore_index=True)

        if not league_data.empty:
            lg_error = league_data[league_data['team_e'] > 0]
            lg_no_error = league_data[league_data['team_e'] == 0]
            lg_error_wr = round((lg_error['win'] == 1).mean() * 100, 1) if len(lg_error) > 0 else 0
            lg_no_error_wr = round((lg_no_error['win'] == 1).mean() * 100, 1) if len(lg_no_error) > 0 else 0
        else:
            lg_error_wr = 0
            lg_no_error_wr = 0

        charts.append(ChartBuilder.bar(
            '실책 유무 경기 승률',
            ['실책 있음', '실책 없음'],
            [
                {'label': team_name, 'data': [error_wr, no_error_wr]},
                {'label': '리그 평균', 'data': [lg_error_wr, lg_no_error_wr]},
            ]
        ))
        findings.append(f"실책 있는 경기 승률: {error_wr}% vs 실책 없는 경기: {no_error_wr}%")
        print_stat('실책 있음 승률', f'{error_wr}% ({len(error_games)}경기)')
        print_stat('실책 없음 승률', f'{no_error_wr}% ({len(no_error_games)}경기)')

        # 실책 유무 카이제곱 검정
        if len(error_games) > 0 and len(no_error_games) > 0:
            e_win = int((error_games['win'] == 1).sum())
            e_loss = len(error_games) - e_win
            ne_win = int((no_error_games['win'] == 1).sum())
            ne_loss = len(no_error_games) - ne_win
            if min(e_win + e_loss, ne_win + ne_loss) > 0:
                observed = np.array([[e_win, e_loss], [ne_win, ne_loss]])
                chi = StatsUtils.chi_square(observed, '실책 유무 승률')
                hypothesis['실책_카이제곱'] = chi
                print_stat('카이제곱', chi['interpretation'])

        # ── 3) Scatter: 안타 차이 vs 득점 차이 ──
        print_section('안타 차이 vs 득점 차이')
        scatter_data = [{'x': float(row['h_diff']), 'y': float(row['r_diff'])}
                        for _, row in game_data.iterrows()]
        charts.append(ChartBuilder.scatter(
            f'{team_name} 안타 차이 vs 득점 차이',
            [{'label': team_name, 'data': scatter_data}]
        ))

        # 상관분석
        corr = StatsUtils.correlation(
            game_data['h_diff'].values.astype(float),
            game_data['r_diff'].values.astype(float),
            '안타 차이', '득점 차이'
        )
        hypothesis['안타_득점_상관'] = corr
        findings.append(f"안타 차이-득점 차이: {corr['interpretation']}")
        print_stat('상관분석', corr['interpretation'])

        # 인사이트
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 승리 요인 분석")
        insight = StatsUtils.format_insight('승리 요인 분석', findings)
        print_section('인사이트')
        print(f"  {insight}")

        fname = output_filename('g4', team_name, LATEST_SEASON, game_type)
        save_analysis_output(fname, charts, findings)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
