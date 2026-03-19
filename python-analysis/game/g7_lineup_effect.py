"""
g7. 라인업 효과
- 1번 타자 유형별 팀 평균 득점
- 라인업 다양성 (변경 빈도)
- 주요 타순(1,3,4번) 고정 비율
- 10팀 라인업 안정성 비교
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


def _calc_lineup_stability(lineups, team_id):
    """라인업 안정성 지표 계산 (주요 타순 고정 비율)"""
    team_lineups = lineups[lineups['teamId'] == team_id].copy()
    if team_lineups.empty:
        return {'stability': 0, 'unique_lineups': 0, 'key_order_fixed': 0}

    # 경기별 1,3,4번 타자 조합
    key_orders = [1, 3, 4]
    key_combos = {}
    schedule_ids = team_lineups['scheduleId'].unique()

    for sid in schedule_ids:
        game_lineup = team_lineups[team_lineups['scheduleId'] == sid]
        combo = []
        for order in key_orders:
            player = game_lineup[game_lineup['order'] == order]
            if not player.empty:
                combo.append(int(player.iloc[0]['playerId']))
            else:
                combo.append(0)
        combo_key = tuple(combo)
        key_combos[combo_key] = key_combos.get(combo_key, 0) + 1

    total_games = len(schedule_ids)
    unique_combos = len(key_combos)
    most_common = max(key_combos.values()) if key_combos else 0
    fixed_rate = round(most_common / total_games * 100, 1) if total_games > 0 else 0

    return {
        'stability': fixed_rate,
        'unique_lineups': unique_combos,
        'key_order_fixed': most_common,
        'total_games': total_games,
    }


def _get_leadoff_stats(lineups, schedules, team_id):
    """1번 타자별 팀 평균 득점"""
    team_lineups = lineups[(lineups['teamId'] == team_id) & (lineups['order'] == 1)]
    if team_lineups.empty:
        return {}

    team_games = get_team_games(schedules, team_id)
    if team_games.empty:
        return {}

    leadoff_scores = {}
    for _, row in team_lineups.iterrows():
        player_name = row['playerName']
        sid = row['scheduleId']
        game = team_games[team_games['id'] == sid]
        if game.empty:
            continue
        score = safe_float(game.iloc[0]['teamScore'])
        if player_name not in leadoff_scores:
            leadoff_scores[player_name] = []
        leadoff_scores[player_name].append(score)

    result = {}
    for name, scores in leadoff_scores.items():
        if len(scores) >= 3:  # 최소 3경기 이상
            result[name] = {
                'avg_score': round(np.mean(scores), 2),
                'games': len(scores),
            }
    return result


def analyze(team_id: int, game_type: str = 'regular'):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('라인업 효과 분석', team_name, game_type)

        schedules = db.get_schedules(LATEST_SEASON)
        lineups = db.get_batter_lineups(LATEST_SEASON)
        if schedules.empty or lineups.empty:
            print("  데이터가 없습니다.")
            return

        schedules = filter_schedules_by_type(schedules, game_type)
        if schedules.empty:
            print(f"  {GAME_TYPES.get(game_type, game_type)} 경기 데이터가 없습니다.")
            return

        valid_ids = set(schedules['id'])
        lineups = lineups[lineups['scheduleId'].isin(valid_ids)].copy()

        charts = []
        findings = []
        hypothesis = {}

        # ── 1) Bar: 1번 타자별 평균 득점 ──
        print_section('1번 타자별 팀 평균 득점')
        leadoff = _get_leadoff_stats(lineups, schedules, team_id)
        if leadoff:
            sorted_lo = sorted(leadoff.items(), key=lambda x: x[1]['avg_score'], reverse=True)
            lo_labels = [f"{name} ({info['games']}경기)" for name, info in sorted_lo]
            lo_data = [info['avg_score'] for _, info in sorted_lo]

            charts.append(ChartBuilder.bar(
                f'{team_name} 1번 타자별 팀 평균 득점',
                lo_labels,
                [{'label': '팀 평균 득점', 'data': lo_data}]
            ))

            best = sorted_lo[0]
            findings.append(f"최고 1번 타자: {best[0]} (평균 {best[1]['avg_score']}점, {best[1]['games']}경기)")
            for name, info in sorted_lo[:3]:
                print_stat(name, f"평균 {info['avg_score']}점 ({info['games']}경기)")
        else:
            print("  1번 타자 데이터가 부족합니다.")

        # ── 라인업 안정성 ──
        print_section('라인업 안정성')
        stability = _calc_lineup_stability(lineups, team_id)
        findings.append(
            f"라인업 안정성: {stability['stability']}% "
            f"(1,3,4번 고유 조합 {stability['unique_lineups']}개)"
        )
        print_stat('1,3,4번 최다 동일 조합', f"{stability['key_order_fixed']}회")
        print_stat('고유 조합 수', f"{stability['unique_lineups']}개")
        print_stat('고정 비율', f"{stability['stability']}%")

        # ── 2) Bar: 10팀 라인업 안정성 ──
        print_section('10팀 라인업 안정성')
        team_stabilities = {}
        for tid in TEAM_NAMES:
            s = _calc_lineup_stability(lineups, tid)
            team_stabilities[tid] = s['stability']

        sorted_teams = sorted(team_stabilities.items(), key=lambda x: x[1], reverse=True)
        bar_labels = [get_team_name(t) for t, _ in sorted_teams]
        bar_data = [v for _, v in sorted_teams]

        charts.append(ChartBuilder.bar(
            '10팀 라인업 안정성 (주요 타순 고정 비율)',
            bar_labels,
            [{'label': '고정 비율 (%)', 'data': bar_data}]
        ))

        rank = next(i + 1 for i, (t, _) in enumerate(sorted_teams) if t == team_id)
        findings.append(f"{team_name} 라인업 안정성 {rank}위")

        # ── 3) Scatter: 라인업 안정성 vs 팀 승률 ──
        print_section('라인업 안정성 vs 팀 승률')
        rankings = db.get_team_rankings(LATEST_SEASON)
        if not rankings.empty:
            scatter_data = []
            stab_vals = []
            wr_vals = []
            for tid in TEAM_NAMES:
                r = rankings[rankings['teamId'] == tid]
                if r.empty:
                    continue
                wr = safe_float(r.iloc[0].get('winRate', 0))
                stab = team_stabilities.get(tid, 0)
                scatter_data.append({'x': stab, 'y': round(wr, 3)})
                stab_vals.append(stab)
                wr_vals.append(wr)

            charts.append(ChartBuilder.scatter(
                '라인업 안정성 vs 팀 승률',
                [{'label': '10팀', 'data': scatter_data}]
            ))

            if len(stab_vals) >= 3:
                corr = StatsUtils.correlation(
                    np.array(stab_vals), np.array(wr_vals),
                    '라인업 안정성', '승률'
                )
                hypothesis['안정성_승률_상관'] = corr
                findings.append(f"안정성-승률: {corr['interpretation']}")
                print_stat('상관분석', corr['interpretation'])

        # 인사이트
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 라인업 효과 분석")
        insight = StatsUtils.format_insight('라인업 효과', findings)
        print_section('인사이트')
        print(f"  {insight}")

        fname = output_filename('g7', team_name, LATEST_SEASON, game_type)
        save_analysis_output(fname, charts, findings)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
