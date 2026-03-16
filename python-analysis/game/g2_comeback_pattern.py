"""
g2. 역전/추격 패턴
- 역전승/역전패/선행승 비율
- 역전 발생 이닝 분포
- 5회 이후 역전 승률
- 최대 점수차 역전 기록
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
    safe_float, filter_schedules_by_type, parse_inning_scores,
    filter_score_boards, output_filename, TEAM_NAMES, LATEST_SEASON, GAME_TYPES,
)


def _analyze_comeback(schedules, score_boards, team_id):
    """팀의 역전 패턴 분석"""
    merged = score_boards.merge(
        schedules[['id', 'homeTeamId', 'awayTeamId', 'homeScore', 'awayScore']],
        left_on='scheduleId', right_on='id', how='inner', suffixes=('_sb', '')
    )

    results = {
        'lead_win': 0,
        'comeback_win': 0,
        'draw': 0,
        'lead_loss': 0,
        'comeback_innings': [],
        'max_comeback_diff': 0,
        'after5_comeback_win': 0,
        'after5_total': 0,
        'total_games': 0,
    }

    for _, row in merged.iterrows():
        is_home = row['homeTeamId'] == team_id
        is_away = row['awayTeamId'] == team_id
        if not is_home and not is_away:
            continue

        home_innings = parse_inning_scores(row.get('homeInningScores'))
        away_innings = parse_inning_scores(row.get('awayInningScores'))
        if not home_innings or not away_innings:
            continue

        results['total_games'] += 1
        team_innings = home_innings if is_home else away_innings
        opp_innings = away_innings if is_home else home_innings

        max_len = min(len(team_innings), len(opp_innings))
        team_cum = np.cumsum(team_innings[:max_len])
        opp_cum = np.cumsum(opp_innings[:max_len])

        final_team = safe_float(row['homeScore'] if is_home else row['awayScore'])
        final_opp = safe_float(row['awayScore'] if is_home else row['homeScore'])

        if final_team == final_opp:
            results['draw'] += 1
            continue

        is_win = final_team > final_opp

        # 리드/역전 판정
        ever_trailed = False
        comeback_inning = None
        max_trail = 0

        for i in range(max_len):
            diff = float(team_cum[i] - opp_cum[i])
            if diff < 0:
                ever_trailed = True
                max_trail = max(max_trail, abs(diff))
            if i > 0:
                prev_diff = float(team_cum[i - 1] - opp_cum[i - 1])
                if prev_diff < 0 and diff >= 0:
                    comeback_inning = i + 1

        if is_win:
            if ever_trailed and comeback_inning:
                results['comeback_win'] += 1
                results['comeback_innings'].append(comeback_inning)
                results['max_comeback_diff'] = max(results['max_comeback_diff'], int(max_trail))
                if comeback_inning >= 6:
                    results['after5_comeback_win'] += 1
                results['after5_total'] += 1
            else:
                results['lead_win'] += 1
        else:
            results['lead_loss'] += 1

    return results


def analyze(team_id: int, game_type: str = 'regular'):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('역전/추격 패턴 분석', team_name, game_type)

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

        # 대상 팀 역전 분석
        result = _analyze_comeback(schedules, score_boards, team_id)
        if result['total_games'] == 0:
            print("  경기 데이터가 없습니다.")
            return

        # ── 1) Doughnut: 승리 유형 ──
        print_section('승리 유형 분포')
        win_types = ['선행승', '역전승', '무승부']
        win_data = [result['lead_win'], result['comeback_win'], result['draw']]

        charts.append(ChartBuilder.doughnut(
            f'{team_name} 승리 유형 분포',
            win_types,
            win_data,
        ))

        total_wins = result['lead_win'] + result['comeback_win']
        if total_wins > 0:
            comeback_pct = round(result['comeback_win'] / total_wins * 100, 1)
            findings.append(f"역전승 비율: {comeback_pct}% ({result['comeback_win']}/{total_wins})")
            print_stat('역전승', f"{result['comeback_win']}회 ({comeback_pct}%)")
        print_stat('선행승', f"{result['lead_win']}회")
        print_stat('무승부', f"{result['draw']}회")
        print_stat('최대 점수차 역전', f"{result['max_comeback_diff']}점")

        if result['max_comeback_diff'] > 0:
            findings.append(f"최대 {result['max_comeback_diff']}점 차 역전 기록")

        # ── 2) Bar: 역전 발생 이닝 분포 ──
        print_section('역전 발생 이닝 분포')
        if result['comeback_innings']:
            inning_counts = {}
            for inning in result['comeback_innings']:
                inning_counts[inning] = inning_counts.get(inning, 0) + 1
            all_innings = sorted(inning_counts.keys())
            inning_labels = [f'{i}회' for i in all_innings]
            inning_data = [inning_counts[i] for i in all_innings]

            charts.append(ChartBuilder.bar(
                f'{team_name} 역전 발생 이닝 분포',
                inning_labels,
                [{'label': '역전 횟수', 'data': inning_data}]
            ))

            most_inning = max(inning_counts, key=inning_counts.get)
            findings.append(f"가장 많은 역전 이닝: {most_inning}회 ({inning_counts[most_inning]}회)")
            print_stat('최빈 역전 이닝', f'{most_inning}회 ({inning_counts[most_inning]}회)')

        # ── 3) Bar: 10팀 역전승 비율 비교 ──
        print_section('10팀 역전승 비율 비교')
        team_comeback_rates = {}
        for tid in TEAM_NAMES:
            r = _analyze_comeback(schedules, score_boards, tid)
            total_w = r['lead_win'] + r['comeback_win']
            rate = round(r['comeback_win'] / total_w * 100, 1) if total_w > 0 else 0
            team_comeback_rates[tid] = rate

        sorted_teams = sorted(team_comeback_rates.items(), key=lambda x: x[1], reverse=True)
        bar_labels = [get_team_name(t) for t, _ in sorted_teams]
        bar_data = [r for _, r in sorted_teams]

        charts.append(ChartBuilder.bar(
            '10팀 역전승 비율 비교',
            bar_labels,
            [{'label': '역전승 비율 (%)', 'data': bar_data}]
        ))

        rank = next(i + 1 for i, (t, _) in enumerate(sorted_teams) if t == team_id)
        findings.append(f"{team_name} 역전승 비율 {rank}위")

        # ── 4) 통계검정: 역전승 vs 비역전승 최종 득점 t-test ──
        print_section('통계검정')
        merged = score_boards.merge(
            schedules[['id', 'homeTeamId', 'awayTeamId', 'homeScore', 'awayScore']],
            left_on='scheduleId', right_on='id', how='inner', suffixes=('_sb', '')
        )
        comeback_scores = []
        noncomeback_scores = []
        for _, row in merged.iterrows():
            is_home = row['homeTeamId'] == team_id
            is_away = row['awayTeamId'] == team_id
            if not is_home and not is_away:
                continue
            score = safe_float(row['homeScore'] if is_home else row['awayScore'])
            opp_score = safe_float(row['awayScore'] if is_home else row['homeScore'])
            if score <= opp_score:
                continue
            home_inn = parse_inning_scores(row.get('homeInningScores'))
            away_inn = parse_inning_scores(row.get('awayInningScores'))
            if not home_inn or not away_inn:
                continue
            team_inn = home_inn if is_home else away_inn
            opp_inn = away_inn if is_home else home_inn
            ever_trailed = False
            tcum = np.cumsum(team_inn[:min(len(team_inn), len(opp_inn))])
            ocum = np.cumsum(opp_inn[:min(len(team_inn), len(opp_inn))])
            for i in range(len(tcum)):
                if tcum[i] < ocum[i]:
                    ever_trailed = True
                    break
            if ever_trailed:
                comeback_scores.append(score)
            else:
                noncomeback_scores.append(score)

        if comeback_scores and noncomeback_scores:
            ttest = StatsUtils.t_test_ind(
                np.array(comeback_scores), np.array(noncomeback_scores),
                '역전승 경기', '선행승 경기'
            )
            hypothesis['역전승_득점_t검정'] = ttest
            findings.append(f"역전승 vs 선행승 득점: {ttest['interpretation']}")
            print_stat('t-검정', ttest['interpretation'])

        # 인사이트
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 역전/추격 패턴 분석")
        insight = StatsUtils.format_insight('역전/추격 패턴', findings)
        print_section('인사이트')
        print(f"  {insight}")

        fname = output_filename('g2', team_name, LATEST_SEASON, game_type)
        save_chart_json(fname, charts)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
