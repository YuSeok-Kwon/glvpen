"""
t4. 팀별 타투 밸런스
- 공격 점수(OPS, R, HR, AVG) vs 투수 점수(ERA역, WHIP역, SO, SV)
- 밸런스 지수: |공격-투수| → 작을수록 균형
- 팀 유형 분류: 타격형 / 투수형 / 균형형
- 공격-투수 상관분석
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from team.team_common import (
    get_team_name, save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, normalize_series, normalize_series_inverse, LATEST_SEASON
)

OFF_METRICS = ['OPS', 'R', 'HR', 'AVG']
PIT_METRICS = ['ERA', 'WHIP', 'SO', 'SV']


def _calc_balance(ts):
    """팀별 공격/투수 점수 및 밸런스 지수 계산"""
    results = {}
    team_ids = ts['teamId'].unique().tolist()

    # 공격 지표 정규화
    off_normed = {}
    for m in OFF_METRICS:
        vals = [safe_float(ts[ts['teamId'] == t].iloc[0].get(m, 0)) if not ts[ts['teamId'] == t].empty else 0
                for t in team_ids]
        normed = normalize_series(vals)
        off_normed[m] = dict(zip(team_ids, normed))

    # 투수 지표 정규화 (ERA, WHIP은 역순)
    pit_normed = {}
    for m in PIT_METRICS:
        vals = [safe_float(ts[ts['teamId'] == t].iloc[0].get(m, 0)) if not ts[ts['teamId'] == t].empty else 0
                for t in team_ids]
        if m in ('ERA', 'WHIP'):
            normed = normalize_series_inverse(vals)
        else:
            normed = normalize_series(vals)
        pit_normed[m] = dict(zip(team_ids, normed))

    for t in team_ids:
        t = int(t)
        row = ts[ts['teamId'] == t]
        name = row.iloc[0].get('teamName', get_team_name(t)) if not row.empty else get_team_name(t)

        off_score = round(np.mean([off_normed[m].get(t, 0) for m in OFF_METRICS]), 1)
        pit_score = round(np.mean([pit_normed[m].get(t, 0) for m in PIT_METRICS]), 1)
        balance = round(abs(off_score - pit_score), 1)

        # 유형 분류
        if balance <= 15:
            team_type = '균형형'
        elif off_score > pit_score:
            team_type = '타격형'
        else:
            team_type = '투수형'

        results[t] = {
            'name': name,
            'off_score': off_score,
            'pit_score': pit_score,
            'balance': balance,
            'type': team_type,
        }

    return results


def analyze(team_id: int):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('타투 밸런스 분석', team_name)

        ts = db.get_team_stats(LATEST_SEASON)
        if ts.empty:
            print("  팀 스탯 데이터가 없습니다.")
            return

        bal = _calc_balance(ts)
        if not bal or team_id not in bal:
            print(f"  팀 ID {team_id}의 데이터가 없습니다.")
            return

        charts = []
        stats_dict = {}
        findings = []
        hypothesis = {}

        target = bal[team_id]

        # ── 1) 산점도: x=공격점수, y=투수점수 ──
        print_section('공격-투수 점수 산포')
        scatter_datasets = []
        for t, info in bal.items():
            ds = {
                'label': info['name'],
                'data': [{'x': info['off_score'], 'y': info['pit_score']}],
            }
            if t == team_id:
                ds['pointRadius'] = 10
                ds['pointStyle'] = 'star'
            scatter_datasets.append(ds)

        charts.append({
            **ChartBuilder.scatter(
                f'{LATEST_SEASON} 공격력 vs 투수력 (팀별)',
                scatter_datasets
            ),
            'options': {
                'scales': {
                    'x': {'title': {'display': True, 'text': '공격 점수'}},
                    'y': {'title': {'display': True, 'text': '투수 점수'}},
                }
            }
        })

        print_stat(f'{team_name} 공격 점수', target['off_score'])
        print_stat(f'{team_name} 투수 점수', target['pit_score'])
        print_stat(f'{team_name} 유형', target['type'])
        findings.append(f"{team_name}: {target['type']} (공격 {target['off_score']}, 투수 {target['pit_score']})")

        # ── 2) 10팀 공격/투수 점수 비교 (Stacked Bar) ──
        print_section('10팀 공격/투수 점수')
        sorted_teams = sorted(bal.items(), key=lambda x: x[1]['off_score'] + x[1]['pit_score'], reverse=True)
        labels = [bal[t]['name'] for t, _ in sorted_teams]
        off_data = [bal[t]['off_score'] for t, _ in sorted_teams]
        pit_data = [bal[t]['pit_score'] for t, _ in sorted_teams]

        charts.append(ChartBuilder.stacked_bar(
            f'{LATEST_SEASON} 팀별 공격/투수 점수',
            labels,
            [
                {'label': '공격 점수', 'data': off_data, 'backgroundColor': '#EF4444'},
                {'label': '투수 점수', 'data': pit_data, 'backgroundColor': '#3B82F6'},
            ]
        ))

        for t, _ in sorted_teams:
            info = bal[t]
            print_stat(info['name'], f"공격 {info['off_score']}, 투수 {info['pit_score']} → {info['type']}")

        # 유형별 분포
        type_counts = {}
        for info in bal.values():
            type_counts[info['type']] = type_counts.get(info['type'], 0) + 1
        for t, c in type_counts.items():
            findings.append(f"{t}: {c}팀")

        # ── 3) 밸런스 지수 순위 (Bar) ──
        print_section('밸런스 지수 순위')
        balance_sorted = sorted(bal.items(), key=lambda x: x[1]['balance'])
        bal_labels = [bal[t]['name'] for t, _ in balance_sorted]
        bal_data = [bal[t]['balance'] for t, _ in balance_sorted]

        charts.append(ChartBuilder.bar(
            f'{LATEST_SEASON} 밸런스 지수 순위 (낮을수록 균형)',
            bal_labels,
            [{'label': '밸런스 지수 (|공격-투수|)', 'data': bal_data}]
        ))

        target_bal_rank = next(i + 1 for i, (t, _) in enumerate(balance_sorted) if t == team_id)
        findings.append(f"{team_name} 밸런스 {target_bal_rank}위 (지수: {target['balance']:.1f})")
        print_stat('밸런스 순위', f'{target_bal_rank}위 / 10팀')

        # ── 4) 상관분석: 공격-투수 점수 ──
        print_section('공격-투수 상관분석')
        off_arr = np.array([bal[t]['off_score'] for t in bal])
        pit_arr = np.array([bal[t]['pit_score'] for t in bal])
        corr = StatsUtils.correlation(off_arr, pit_arr, '공격 점수', '투수 점수')
        hypothesis['공격_투수_상관'] = corr
        findings.append(f"공격-투수 상관: {corr['interpretation']}")
        print_stat('상관분석', corr['interpretation'])

        # ── 인사이트 ──
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 타투 밸런스 분석")
        insight = StatsUtils.format_insight('타투 밸런스', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f't4_{team_name}_{LATEST_SEASON}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
