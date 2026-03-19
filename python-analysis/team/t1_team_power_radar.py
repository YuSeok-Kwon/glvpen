"""
t1. 팀 전력 레이더
- 6축 레이더: 타격력(OPS), 장타력(HR), 투수력(ERA역), 수비력(FPCT), 기동력(SB), 출루력(OBP)
- 10팀 종합 전력 점수 비교
- 상위 3팀 레이더 비교
- 상위/하위 그룹 t-test
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

RADAR_AXES = ['타격력', '장타력', '투수력', '수비력', '기동력', '출루력']
RADAR_METRICS = ['OPS', 'HR', 'ERA', 'FPCT', 'SB', 'OBP']


def _build_team_scores(ts):
    """팀 스탯 DataFrame에서 6축 정규화 점수 계산"""
    scores = {}
    for _, row in ts.iterrows():
        tid = int(row['teamId'])
        name = row.get('teamName', get_team_name(tid))
        raw = {
            'OPS': safe_float(row.get('OPS', 0)),
            'HR': safe_float(row.get('HR', 0)),
            'ERA': safe_float(row.get('ERA', 0)),
            'FPCT': safe_float(row.get('FPCT', 0)),
            'SB': safe_float(row.get('SB', 0)),
            'OBP': safe_float(row.get('OBP', 0)),
        }
        scores[tid] = {'name': name, 'raw': raw}

    team_ids = list(scores.keys())
    if not team_ids:
        return {}

    # 정규화 (ERA는 역순)
    for metric in RADAR_METRICS:
        vals = [scores[t]['raw'][metric] for t in team_ids]
        if metric == 'ERA':
            normed = normalize_series_inverse(vals)
        else:
            normed = normalize_series(vals)
        for i, t in enumerate(team_ids):
            scores[t].setdefault('normed', {})[metric] = normed[i]

    # 종합 점수
    for t in team_ids:
        total = sum(scores[t]['normed'][m] for m in RADAR_METRICS)
        scores[t]['total'] = round(total, 1)

    return scores


def analyze(team_id: int):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('팀 전력 레이더 분석', team_name)

        ts = db.get_team_stats(LATEST_SEASON)
        if ts.empty:
            print("  팀 스탯 데이터가 없습니다.")
            return

        scores = _build_team_scores(ts)
        if not scores or team_id not in scores:
            print(f"  팀 ID {team_id}의 데이터가 없습니다.")
            return

        charts = []
        stats_dict = {}
        findings = []
        hypothesis = {}

        # ── 1) 대상 팀 vs 리그 평균 레이더 ──
        print_section('대상 팀 vs 리그 평균')
        target = scores[team_id]
        target_vals = [target['normed'][m] for m in RADAR_METRICS]

        league_avg = []
        for m in RADAR_METRICS:
            avg = np.mean([scores[t]['normed'][m] for t in scores])
            league_avg.append(round(avg, 1))

        charts.append(ChartBuilder.radar(
            f'{team_name} vs 리그 평균 전력 비교',
            RADAR_AXES,
            [
                {'label': team_name, 'data': target_vals},
                {'label': '리그 평균', 'data': league_avg},
            ]
        ))

        for i, axis in enumerate(RADAR_AXES):
            diff = target_vals[i] - league_avg[i]
            print_stat(axis, f'{target_vals[i]:.1f} (리그평균 {league_avg[i]:.1f}, 차이 {diff:+.1f})')
            if diff > 10:
                findings.append(f"{axis} 리그 평균 대비 우수 (+{diff:.1f})")
            elif diff < -10:
                findings.append(f"{axis} 리그 평균 대비 열세 ({diff:.1f})")

        # ── 2) 10팀 종합 전력 점수 (Bar) ──
        print_section('10팀 종합 전력 점수')
        sorted_teams = sorted(scores.items(), key=lambda x: x[1]['total'], reverse=True)
        bar_labels = [scores[t]['name'] for t, _ in sorted_teams]
        bar_data = [scores[t]['total'] for t, _ in sorted_teams]

        charts.append(ChartBuilder.bar(
            f'{LATEST_SEASON} 시즌 10팀 종합 전력 점수',
            bar_labels,
            [{'label': '종합 전력 점수', 'data': bar_data}]
        ))

        target_rank = next(i + 1 for i, (t, _) in enumerate(sorted_teams) if t == team_id)
        findings.append(f"{team_name} 종합 전력 {target_rank}위 (점수: {target['total']:.1f})")
        print_stat('종합 순위', f'{target_rank}위 / 10팀 (점수: {target["total"]:.1f})')

        desc = StatsUtils.descriptive(np.array(bar_data), '종합 전력 점수')
        stats_dict['종합전력_기술통계'] = desc

        # ── 3) 상위 3팀 레이더 비교 ──
        print_section('상위 3팀 레이더 비교')
        top3 = sorted_teams[:3]
        top3_datasets = []
        for t, _ in top3:
            vals = [scores[t]['normed'][m] for m in RADAR_METRICS]
            top3_datasets.append({'label': scores[t]['name'], 'data': vals})
            print_stat(scores[t]['name'], f"종합 {scores[t]['total']:.1f}")

        charts.append(ChartBuilder.radar(
            f'{LATEST_SEASON} 상위 3팀 전력 비교',
            RADAR_AXES,
            top3_datasets
        ))

        # ── 4) 통계검정: 상위/하위 5팀 t-test ──
        print_section('상위/하위 그룹 검정')
        top5_scores = np.array([s['total'] for _, s in sorted_teams[:5]])
        bot5_scores = np.array([s['total'] for _, s in sorted_teams[5:]])
        ttest = StatsUtils.t_test_ind(top5_scores, bot5_scores, '상위 5팀', '하위 5팀')
        hypothesis['상위하위_t검정'] = ttest
        findings.append(f"상위/하위 그룹 간 {ttest['interpretation']}")
        print_stat('t-검정 결과', ttest['interpretation'])

        # ── 인사이트 ──
        findings.insert(0, f"{team_name} {LATEST_SEASON} 시즌 전력 레이더 분석")
        insight = StatsUtils.format_insight('팀 전력 레이더', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f't1_{team_name}_{LATEST_SEASON}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
