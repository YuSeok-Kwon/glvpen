"""
t5. 홈/원정 성적 차이
- 대상 팀 홈/원정 승률 + 득점 비교 (Bar)
- 10팀 홈 어드밴티지 (Bar)
- 대상 팀 홈/원정 승패 분포 (Doughnut)
- 홈/원정 승률 차이 카이제곱 검정
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from team.team_common import (
    get_team_name, save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, LATEST_SEASON, TEAM_NAMES
)


def _calc_home_away(schedules, team_id):
    """팀별 홈/원정 성적 계산"""
    home = schedules[schedules['homeTeamId'] == team_id].copy()
    away = schedules[schedules['awayTeamId'] == team_id].copy()

    # 홈 성적
    home_wins = len(home[home['homeScore'] > home['awayScore']])
    home_losses = len(home[home['homeScore'] < home['awayScore']])
    home_draws = len(home[home['homeScore'] == home['awayScore']])
    home_total = home_wins + home_losses + home_draws
    home_wr = round(home_wins / home_total, 3) if home_total > 0 else 0
    home_avg_score = round(home['homeScore'].apply(safe_float).mean(), 2) if not home.empty else 0

    # 원정 성적
    away_wins = len(away[away['awayScore'] > away['homeScore']])
    away_losses = len(away[away['awayScore'] < away['homeScore']])
    away_draws = len(away[away['awayScore'] == away['homeScore']])
    away_total = away_wins + away_losses + away_draws
    away_wr = round(away_wins / away_total, 3) if away_total > 0 else 0
    away_avg_score = round(away['awayScore'].apply(safe_float).mean(), 2) if not away.empty else 0

    return {
        'home_wins': home_wins, 'home_losses': home_losses, 'home_draws': home_draws,
        'home_total': home_total, 'home_wr': home_wr, 'home_avg_score': home_avg_score,
        'away_wins': away_wins, 'away_losses': away_losses, 'away_draws': away_draws,
        'away_total': away_total, 'away_wr': away_wr, 'away_avg_score': away_avg_score,
        'advantage': round(home_wr - away_wr, 3),
    }


def analyze(team_id: int):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('홈/원정 성적 차이 분석', team_name)

        schedules = db.get_schedules(LATEST_SEASON)
        if schedules.empty:
            print("  경기 일정 데이터가 없습니다.")
            return

        charts = []
        stats_dict = {}
        findings = []
        hypothesis = {}

        # 대상 팀 홈/원정 성적
        target = _calc_home_away(schedules, team_id)

        # ── 1) 대상 팀 홈/원정 승률 + 득점 비교 ──
        print_section('홈/원정 승률 & 득점')
        charts.append(ChartBuilder.bar(
            f'{team_name} 홈/원정 성적 비교',
            ['승률', '평균 득점'],
            [
                {'label': '홈', 'data': [target['home_wr'], target['home_avg_score']],
                 'backgroundColor': '#3B82F6'},
                {'label': '원정', 'data': [target['away_wr'], target['away_avg_score']],
                 'backgroundColor': '#EF4444'},
            ]
        ))

        print_stat('홈 승률', f"{target['home_wr']:.3f} ({target['home_wins']}승 {target['home_losses']}패)")
        print_stat('원정 승률', f"{target['away_wr']:.3f} ({target['away_wins']}승 {target['away_losses']}패)")
        print_stat('홈 평균 득점', target['home_avg_score'])
        print_stat('원정 평균 득점', target['away_avg_score'])
        findings.append(f"홈 승률 {target['home_wr']:.3f}, 원정 승률 {target['away_wr']:.3f}")
        findings.append(f"홈 어드밴티지: {target['advantage']:+.3f}")

        # ── 2) 10팀 홈 어드밴티지 ──
        print_section('10팀 홈 어드밴티지')
        all_teams_ha = {}
        for tid in TEAM_NAMES:
            ha = _calc_home_away(schedules, tid)
            if ha['home_total'] + ha['away_total'] > 0:
                all_teams_ha[tid] = ha

        sorted_ha = sorted(all_teams_ha.items(), key=lambda x: x[1]['advantage'], reverse=True)
        ha_labels = [get_team_name(t) for t, _ in sorted_ha]
        ha_data = [h['advantage'] for _, h in sorted_ha]

        charts.append(ChartBuilder.bar(
            f'{LATEST_SEASON} 10팀 홈 어드밴티지 (홈승률 - 원정승률)',
            ha_labels,
            [{'label': '홈 어드밴티지', 'data': ha_data}]
        ))

        target_ha_rank = next(i + 1 for i, (t, _) in enumerate(sorted_ha) if t == team_id)
        findings.append(f"{team_name} 홈 어드밴티지 {target_ha_rank}위 ({target['advantage']:+.3f})")

        for t, h in sorted_ha:
            print_stat(get_team_name(t), f"홈 {h['home_wr']:.3f} - 원정 {h['away_wr']:.3f} = {h['advantage']:+.3f}")

        # ── 3) 대상 팀 홈/원정 승패 분포 (Doughnut) ──
        print_section('홈/원정 승패 분포')
        charts.append(ChartBuilder.doughnut(
            f'{team_name} 홈/원정 승패 분포',
            ['홈 승', '홈 패', '원정 승', '원정 패'],
            [target['home_wins'], target['home_losses'],
             target['away_wins'], target['away_losses']],
            ['#3B82F6', '#87CEEB', '#EF4444', '#FFB6C1']
        ))

        # ── 4) 카이제곱 검정: 홈/원정 승률 차이 ──
        print_section('홈/원정 승률 차이 검정')
        observed = np.array([
            [target['home_wins'], target['home_losses']],
            [target['away_wins'], target['away_losses']],
        ])
        if observed.min() >= 1:
            chi_result = StatsUtils.chi_square(observed, '홈/원정 승률')
            hypothesis['홈원정_카이제곱'] = chi_result
            findings.append(f"홈/원정 차이 검정: {chi_result['interpretation']}")
            print_stat('카이제곱 결과', chi_result['interpretation'])
        else:
            print("    표본이 부족하여 검정 생략")

        # ── 인사이트 ──
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 홈/원정 성적 분석")
        insight = StatsUtils.format_insight('홈/원정 성적 차이', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f't5_{team_name}_{LATEST_SEASON}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
