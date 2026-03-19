"""
g3. 키플레이어 분석
- WPA 1위 선수 빈도 (누가 자주 키플레이어?)
- HITTER vs PITCHER 키플레이어 비율
- 키플레이어 등장 경기 승률
- 10팀 키플레이어 집중도
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


def analyze(team_id: int, game_type: str = 'regular'):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('키플레이어 분석', team_name, game_type)

        schedules = db.get_schedules(LATEST_SEASON)
        key_players = db.get_game_key_players(LATEST_SEASON)
        if schedules.empty or key_players.empty:
            print("  데이터가 없습니다.")
            return

        schedules = filter_schedules_by_type(schedules, game_type)
        if schedules.empty:
            print(f"  {GAME_TYPES.get(game_type, game_type)} 경기 데이터가 없습니다.")
            return

        valid_ids = set(schedules['id'])
        key_players = key_players[key_players['scheduleId'].isin(valid_ids)].copy()

        # WPA 1위 필터
        wpa_top = key_players[
            (key_players['metric'] == 'GAME_WPA_RT') & (key_players['ranking'] == 1)
        ].copy()

        charts = []
        findings = []
        hypothesis = {}

        # 대상 팀 키플레이어
        team_wpa = wpa_top[wpa_top['teamId'] == team_id]

        # ── 1) Bar: 대상 팀 키플레이어 등장 빈도 (상위 10명) ──
        print_section('키플레이어 등장 빈도')
        if not team_wpa.empty:
            player_counts = team_wpa['playerName'].value_counts().head(10)
            charts.append(ChartBuilder.bar(
                f'{team_name} 키플레이어 등장 빈도 (상위 10명)',
                player_counts.index.tolist(),
                [{'label': '등장 횟수', 'data': player_counts.values.tolist()}]
            ))
            top_player = player_counts.index[0]
            findings.append(f"최다 키플레이어: {top_player} ({player_counts.iloc[0]}회)")
            print_stat('최다 키플레이어', f'{top_player} ({player_counts.iloc[0]}회)')
        else:
            print("  팀 키플레이어 데이터가 없습니다.")

        # ── 2) Doughnut: HITTER vs PITCHER 비율 ──
        print_section('HITTER vs PITCHER 비율')
        if not team_wpa.empty:
            type_counts = team_wpa['playerType'].value_counts()
            type_labels = type_counts.index.tolist()
            type_data = type_counts.values.tolist()

            charts.append(ChartBuilder.doughnut(
                f'{team_name} 키플레이어 유형 비율',
                type_labels,
                type_data,
            ))

            for lbl, cnt in zip(type_labels, type_data):
                pct = round(cnt / sum(type_data) * 100, 1)
                findings.append(f"{lbl} 키플레이어 비율: {pct}%")
                print_stat(lbl, f'{cnt}회 ({pct}%)')

        # ── 키플레이어 등장 경기 승률 ──
        print_section('키플레이어 등장 경기 승률')
        team_games = get_team_games(schedules, team_id)
        if not team_games.empty and not team_wpa.empty:
            kp_schedule_ids = set(team_wpa['scheduleId'])
            kp_games = team_games[team_games['id'].isin(kp_schedule_ids)]
            non_kp_games = team_games[~team_games['id'].isin(kp_schedule_ids)]

            kp_wr = (kp_games['result'] == '승').mean() if len(kp_games) > 0 else 0
            non_kp_wr = (non_kp_games['result'] == '승').mean() if len(non_kp_games) > 0 else 0

            findings.append(f"키플레이어 등장 승률: {kp_wr:.3f} vs 비등장: {non_kp_wr:.3f}")
            print_stat('등장 경기 승률', f'{kp_wr:.3f} ({len(kp_games)}경기)')
            print_stat('비등장 경기 승률', f'{non_kp_wr:.3f} ({len(non_kp_games)}경기)')

            # 카이제곱 검정
            if len(kp_games) > 0 and len(non_kp_games) > 0:
                kp_win = int((kp_games['result'] == '승').sum())
                kp_loss = len(kp_games) - kp_win
                nkp_win = int((non_kp_games['result'] == '승').sum())
                nkp_loss = len(non_kp_games) - nkp_win
                if min(kp_win, kp_loss, nkp_win, nkp_loss) >= 0:
                    observed = np.array([[kp_win, kp_loss], [nkp_win, nkp_loss]])
                    chi_test = StatsUtils.chi_square(observed, '키플레이어 유무 승률')
                    hypothesis['키플레이어_승률_카이제곱'] = chi_test
                    findings.append(f"키플레이어 유무 승률: {chi_test['interpretation']}")
                    print_stat('카이제곱', chi_test['interpretation'])

        # ── 3) Bar: 10팀 키플레이어 집중도 ──
        print_section('10팀 키플레이어 집중도')
        concentration = {}
        for tid in TEAM_NAMES:
            t_wpa = wpa_top[wpa_top['teamId'] == tid]
            if t_wpa.empty:
                concentration[tid] = 0
                continue
            counts = t_wpa['playerName'].value_counts()
            top3_count = counts.head(3).sum()
            total_count = counts.sum()
            concentration[tid] = round(top3_count / total_count * 100, 1) if total_count > 0 else 0

        sorted_conc = sorted(concentration.items(), key=lambda x: x[1], reverse=True)
        bar_labels = [get_team_name(t) for t, _ in sorted_conc]
        bar_data = [c for _, c in sorted_conc]

        charts.append(ChartBuilder.bar(
            '10팀 키플레이어 집중도 (상위 3명 비중)',
            bar_labels,
            [{'label': '상위 3명 비중 (%)', 'data': bar_data}]
        ))

        team_conc = concentration.get(team_id, 0)
        findings.append(f"{team_name} 키플레이어 집중도: {team_conc:.1f}%")
        print_stat('집중도', f'{team_conc:.1f}%')

        # 인사이트
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 키플레이어 분석")
        insight = StatsUtils.format_insight('키플레이어 분석', findings)
        print_section('인사이트')
        print(f"  {insight}")

        fname = output_filename('g3', team_name, LATEST_SEASON, game_type)
        save_analysis_output(fname, charts, findings)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
