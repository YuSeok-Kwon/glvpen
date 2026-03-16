"""
t6. 구단 투자 효율
- 피타고리안 승률 vs 실제 승률 (Scatter)
- 과성과/저성과 지수 (Bar)
- 대상 팀 6시즌 효율 추이 (Line)
- 피타고리안 vs 실제 paired t-test
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from team.team_common import (
    get_team_name, save_chart_json, print_header, print_section, print_stat,
    safe_float, SEASONS, LATEST_SEASON, TEAM_NAMES
)


def _pythagorean(runs, runs_allowed, exp=2):
    """피타고리안 승률: R^exp / (R^exp + RA^exp)"""
    r = safe_float(runs)
    ra = safe_float(runs_allowed)
    if r + ra == 0:
        return 0.5
    return round(r ** exp / (r ** exp + ra ** exp), 4)


def analyze(team_id: int):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('구단 투자 효율 분석', team_name)

        # 최신 시즌 데이터
        ts = db.get_team_stats(LATEST_SEASON)
        rankings = db.get_team_rankings(LATEST_SEASON)

        if ts.empty or rankings.empty:
            print("  데이터가 부족합니다.")
            return

        charts = []
        stats_dict = {}
        findings = []
        hypothesis = {}

        # ── 팀별 피타고리안 승률 계산 ──
        team_data = {}
        for _, row in rankings.iterrows():
            tid = int(row['teamId'])
            actual_wr = safe_float(row.get('winRate', 0))
            ts_row = ts[ts['teamId'] == tid]
            if ts_row.empty:
                continue
            runs = safe_float(ts_row.iloc[0].get('R', 0))
            runs_allowed = safe_float(ts_row.iloc[0].get('RA', 0))
            pyth_wr = _pythagorean(runs, runs_allowed)
            team_data[tid] = {
                'name': get_team_name(tid),
                'actual_wr': actual_wr,
                'pyth_wr': pyth_wr,
                'diff': round(actual_wr - pyth_wr, 4),
                'runs': runs,
                'runs_allowed': runs_allowed,
            }

        if not team_data or team_id not in team_data:
            print(f"  팀 ID {team_id}의 데이터가 부족합니다.")
            return

        target = team_data[team_id]

        # ── 1) 피타고리안 vs 실제 승률 (Scatter) ──
        print_section('피타고리안 vs 실제 승률')
        scatter_datasets = []
        for tid, info in team_data.items():
            ds = {
                'label': info['name'],
                'data': [{'x': info['pyth_wr'], 'y': info['actual_wr']}],
            }
            if tid == team_id:
                ds['pointRadius'] = 10
                ds['pointStyle'] = 'star'
            scatter_datasets.append(ds)

        charts.append({
            **ChartBuilder.scatter(
                f'{LATEST_SEASON} 피타고리안 승률 vs 실제 승률',
                scatter_datasets
            ),
            'options': {
                'scales': {
                    'x': {'title': {'display': True, 'text': '피타고리안 승률'}},
                    'y': {'title': {'display': True, 'text': '실제 승률'}},
                }
            }
        })

        for tid in sorted(team_data.keys()):
            info = team_data[tid]
            marker = ' ★' if tid == team_id else ''
            print_stat(f'{info["name"]}{marker}',
                       f'실제 {info["actual_wr"]:.3f} / 피타 {info["pyth_wr"]:.3f} '
                       f'(차이 {info["diff"]:+.4f})')

        # ── 2) 과성과/저성과 지수 (Bar) ──
        print_section('과성과/저성과 지수')
        sorted_diff = sorted(team_data.items(), key=lambda x: x[1]['diff'], reverse=True)
        diff_labels = [team_data[t]['name'] for t, _ in sorted_diff]
        diff_data = [team_data[t]['diff'] for t, _ in sorted_diff]

        charts.append(ChartBuilder.bar(
            f'{LATEST_SEASON} 과성과/저성과 지수 (실제 - 피타고리안)',
            diff_labels,
            [{'label': '과성과 지수', 'data': diff_data}]
        ))

        target_eff_rank = next(i + 1 for i, (t, _) in enumerate(sorted_diff) if t == team_id)
        if target['diff'] > 0:
            findings.append(f"{team_name}: 과성과 ({target['diff']:+.4f}) — {target_eff_rank}위")
        else:
            findings.append(f"{team_name}: 저성과 ({target['diff']:+.4f}) — {target_eff_rank}위")

        # ── 3) 대상 팀 6시즌 효율 추이 (Line) ──
        print_section('대상 팀 6시즌 효율 추이')
        trend_seasons = []
        trend_actual = []
        trend_pyth = []
        trend_diff = []

        for s in SEASONS:
            s_ts = db.get_team_stats(s)
            s_rank = db.get_team_rankings(s)
            if s_ts.empty or s_rank.empty:
                continue
            s_ts_row = s_ts[s_ts['teamId'] == team_id]
            s_rank_row = s_rank[s_rank['teamId'] == team_id]
            if s_ts_row.empty or s_rank_row.empty:
                continue
            runs = safe_float(s_ts_row.iloc[0].get('R', 0))
            ra = safe_float(s_ts_row.iloc[0].get('RA', 0))
            actual = safe_float(s_rank_row.iloc[0].get('winRate', 0))
            pyth = _pythagorean(runs, ra)

            trend_seasons.append(str(s))
            trend_actual.append(actual)
            trend_pyth.append(pyth)
            trend_diff.append(round(actual - pyth, 4))

        if len(trend_seasons) >= 2:
            charts.append(ChartBuilder.line(
                f'{team_name} 6시즌 효율 추이 (실제 vs 피타고리안)',
                trend_seasons,
                [
                    {'label': '실제 승률', 'data': trend_actual},
                    {'label': '피타고리안 승률', 'data': trend_pyth},
                    {'label': '과성과 지수', 'data': trend_diff},
                ]
            ))

            for i, s in enumerate(trend_seasons):
                print_stat(f'{s}', f'실제 {trend_actual[i]:.3f} / 피타 {trend_pyth[i]:.3f} '
                                   f'(차이 {trend_diff[i]:+.4f})')

        # ── 4) paired t-test: 피타고리안 vs 실제 ──
        print_section('피타고리안 vs 실제 승률 검정')
        actual_arr = np.array([info['actual_wr'] for info in team_data.values()])
        pyth_arr = np.array([info['pyth_wr'] for info in team_data.values()])
        ttest = StatsUtils.t_test_paired(actual_arr, pyth_arr, '실제 vs 피타고리안')
        hypothesis['피타고리안_paired_t'] = ttest
        findings.append(f"피타고리안 vs 실제: {ttest['interpretation']}")
        print_stat('t-검정 결과', ttest['interpretation'])

        # 득점 효율
        print_section('득점 효율')
        for tid in sorted(team_data.keys()):
            info = team_data[tid]
            ts_row = ts[ts['teamId'] == tid]
            if not ts_row.empty:
                hits = safe_float(ts_row.iloc[0].get('H', 1))
                runs = info['runs']
                eff = round(runs / hits, 3) if hits > 0 else 0
                info['run_per_hit'] = eff
                marker = ' ★' if tid == team_id else ''
                print_stat(f'{info["name"]}{marker}', f'R/H = {eff:.3f}')

        # ── 인사이트 ──
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 구단 효율 분석")
        insight = StatsUtils.format_insight('구단 투자 효율', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_chart_json(f't6_{team_name}_{LATEST_SEASON}.json', charts)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
