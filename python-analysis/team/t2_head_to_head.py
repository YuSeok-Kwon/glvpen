"""
t2. 상대전적 히트맵
- 대상 팀 vs 각 상대: 승/패/무 (Stacked Bar)
- 대상 팀 상대별 승률 (Bar)
- 전체 승/패/무 비율 (Doughnut)
- 상대전적 균등성 카이제곱 검정
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from team.team_common import (
    get_team_name, save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, LATEST_SEASON
)


def analyze(team_id: int):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('상대전적 히트맵 분석', team_name)

        h2h = db.get_head_to_head(LATEST_SEASON)
        if h2h.empty:
            print("  상대전적 데이터가 없습니다.")
            return

        # 대상 팀 상대전적만 필터
        team_h2h = h2h[h2h['teamId'] == team_id].copy()
        if team_h2h.empty:
            print(f"  팀 ID {team_id}의 상대전적 데이터가 없습니다.")
            return

        charts = []
        stats_dict = {}
        findings = []
        hypothesis = {}

        # 승률 계산
        team_h2h['wins_f'] = team_h2h['wins'].apply(safe_float)
        team_h2h['losses_f'] = team_h2h['losses'].apply(safe_float)
        team_h2h['draws_f'] = team_h2h['draws'].apply(safe_float)
        team_h2h['total'] = team_h2h['wins_f'] + team_h2h['losses_f'] + team_h2h['draws_f']
        team_h2h['winRate'] = team_h2h.apply(
            lambda r: round(r['wins_f'] / r['total'], 3) if r['total'] > 0 else 0, axis=1
        )

        # 승률 순 정렬
        team_h2h = team_h2h.sort_values('winRate', ascending=False)
        opp_names = team_h2h['opponentName'].tolist()

        # ── 1) 대상 팀 vs 각 상대 승/패/무 (Stacked Bar) ──
        print_section('대상 팀 상대별 승/패/무')
        wins_data = team_h2h['wins_f'].astype(int).tolist()
        losses_data = team_h2h['losses_f'].astype(int).tolist()
        draws_data = team_h2h['draws_f'].astype(int).tolist()

        charts.append(ChartBuilder.stacked_bar(
            f'{team_name} 상대별 승/패/무',
            opp_names,
            [
                {'label': '승', 'data': wins_data, 'backgroundColor': '#3B82F6'},
                {'label': '패', 'data': losses_data, 'backgroundColor': '#EF4444'},
                {'label': '무', 'data': draws_data, 'backgroundColor': '#E7E9ED'},
            ]
        ))

        for _, row in team_h2h.iterrows():
            print_stat(f'vs {row["opponentName"]}',
                       f'{int(row["wins_f"])}승 {int(row["losses_f"])}패 {int(row["draws_f"])}무 '
                       f'(승률 {row["winRate"]:.3f})')

        # ── 2) 대상 팀 상대별 승률 (Bar) ──
        print_section('상대별 승률')
        wr_data = [round(r, 3) for r in team_h2h['winRate'].tolist()]

        charts.append(ChartBuilder.bar(
            f'{team_name} 상대별 승률',
            opp_names,
            [{'label': '승률', 'data': wr_data}]
        ))

        # 호구/천적 분석
        nemesis = team_h2h[team_h2h['winRate'] < 0.4]
        prey = team_h2h[team_h2h['winRate'] >= 0.6]
        if not prey.empty:
            prey_names = ', '.join(prey['opponentName'].tolist())
            findings.append(f"호구 (승률 0.6+): {prey_names}")
        if not nemesis.empty:
            nem_names = ', '.join(nemesis['opponentName'].tolist())
            findings.append(f"천적 (승률 0.4-): {nem_names}")

        desc = StatsUtils.descriptive(np.array(wr_data), '상대별 승률')
        stats_dict['승률_기술통계'] = desc

        # ── 3) 전체 승/패/무 비율 (Doughnut) ──
        print_section('전체 승/패/무 비율')
        total_w = int(team_h2h['wins_f'].sum())
        total_l = int(team_h2h['losses_f'].sum())
        total_d = int(team_h2h['draws_f'].sum())

        charts.append(ChartBuilder.doughnut(
            f'{team_name} 전체 상대전적',
            ['승', '패', '무'],
            [total_w, total_l, total_d],
            ['#3B82F6', '#EF4444', '#E7E9ED']
        ))

        total_games = total_w + total_l + total_d
        overall_wr = round(total_w / total_games, 3) if total_games > 0 else 0
        findings.append(f"총 상대전적: {total_w}승 {total_l}패 {total_d}무 (승률 {overall_wr:.3f})")
        print_stat('총 전적', f'{total_w}승 {total_l}패 {total_d}무 (승률 {overall_wr:.3f})')

        # ── 4) 카이제곱 검정: 상대전적 균등성 ──
        print_section('상대전적 균등성 검정')
        observed = np.array([team_h2h['wins_f'].values,
                             team_h2h['losses_f'].values])
        # 0 값이 있으면 검정 불가하므로 최소 1로 보정
        observed = np.maximum(observed, 0.5)
        chi_result = StatsUtils.chi_square(observed, '상대전적 균등성')
        hypothesis['상대전적_카이제곱'] = chi_result
        findings.append(f"상대전적 균등성: {chi_result['interpretation']}")
        print_stat('카이제곱 결과', chi_result['interpretation'])

        # ── 인사이트 ──
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 상대전적 분석")
        insight = StatsUtils.format_insight('상대전적 히트맵', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f't2_{team_name}_{LATEST_SEASON}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
