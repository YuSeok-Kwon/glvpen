"""
t3. 시즌별 팀 순위 변동
- 대상 팀 순위 추이 (Line, y축 역전)
- 전체 10팀 순위 추이 (Line)
- 대상 팀 시즌별 승률 변화 (Bar)
- 순위-승률 상관분석
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from team.team_common import (
    get_team_name, save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, SEASONS, LATEST_SEASON
)


def analyze(team_id: int):
    db = DBConnector()
    try:
        team_name = get_team_name(team_id)
        print_header('시즌별 순위 변동 분석', team_name)

        # 6시즌 순위 데이터 수집
        all_rankings = {}  # {season: DataFrame}
        for s in SEASONS:
            r = db.get_team_rankings(s)
            if not r.empty:
                all_rankings[s] = r

        if len(all_rankings) < 2:
            print("  순위 데이터가 부족합니다 (최소 2시즌 필요)")
            return

        available_seasons = sorted(all_rankings.keys())
        seasons_str = [str(s) for s in available_seasons]

        charts = []
        stats_dict = {}
        findings = []
        hypothesis = {}

        # 대상 팀의 순위/승률 시계열 추출
        target_rankings = []
        target_winrates = []
        for s in available_seasons:
            df = all_rankings[s]
            row = df[df['teamId'] == team_id]
            if not row.empty:
                target_rankings.append(safe_float(row.iloc[0].get('ranking', 0)))
                target_winrates.append(safe_float(row.iloc[0].get('winRate', 0)))
            else:
                target_rankings.append(None)
                target_winrates.append(None)

        # ── 1) 대상 팀 순위 추이 (Line, y축 역전) ──
        print_section('대상 팀 순위 추이')
        charts.append({
            **ChartBuilder.line(
                f'{team_name} 순위 추이 ({seasons_str[0]}~{seasons_str[-1]})',
                seasons_str,
                [{'label': f'{team_name} 순위', 'data': target_rankings}]
            ),
            'options': {
                'scales': {
                    'y': {'reverse': True, 'min': 1, 'max': 10,
                           'title': {'display': True, 'text': '순위'}},
                }
            }
        })

        valid_rankings = [r for r in target_rankings if r is not None]
        if valid_rankings:
            desc = StatsUtils.descriptive(np.array(valid_rankings), '순위')
            stats_dict['순위_기술통계'] = desc
            print_stat('평균 순위', desc['mean'])
            print_stat('최고 순위', int(desc['min']))
            print_stat('최저 순위', int(desc['max']))

        # 전년 대비 순위 변화
        for i in range(1, len(target_rankings)):
            if target_rankings[i] is not None and target_rankings[i - 1] is not None:
                change = int(target_rankings[i - 1] - target_rankings[i])
                direction = '상승' if change > 0 else ('하락' if change < 0 else '유지')
                print_stat(f'{available_seasons[i]}', f'{int(target_rankings[i])}위 ({direction} {abs(change)}단계)')

        # ── 2) 전체 10팀 순위 추이 ──
        print_section('전체 10팀 순위 추이')
        # 모든 팀 ID 수집
        all_team_ids = set()
        for df in all_rankings.values():
            all_team_ids.update(df['teamId'].tolist())

        multi_datasets = []
        for tid in sorted(all_team_ids):
            tid = int(tid)
            tname = get_team_name(tid)
            data = []
            for s in available_seasons:
                df = all_rankings[s]
                row = df[df['teamId'] == tid]
                data.append(safe_float(row.iloc[0]['ranking']) if not row.empty else None)
            ds = {'label': tname, 'data': data, 'tension': 0.3}
            if tid == team_id:
                ds['borderWidth'] = 4
            multi_datasets.append(ds)

        charts.append({
            **ChartBuilder.line(
                f'{LATEST_SEASON} 전체 10팀 순위 추이',
                seasons_str,
                multi_datasets
            ),
            'options': {
                'scales': {
                    'y': {'reverse': True, 'min': 1, 'max': 10,
                           'title': {'display': True, 'text': '순위'}},
                }
            }
        })

        # ── 3) 대상 팀 시즌별 승률 변화 (Bar) ──
        print_section('시즌별 승률 변화')
        valid_wr = [w for w in target_winrates if w is not None]
        valid_wr_seasons = [s for s, w in zip(seasons_str, target_winrates) if w is not None]

        charts.append(ChartBuilder.bar(
            f'{team_name} 시즌별 승률',
            valid_wr_seasons,
            [{'label': '승률', 'data': valid_wr}]
        ))

        for s, w in zip(valid_wr_seasons, valid_wr):
            print_stat(f'{s} 승률', f'{w:.3f}')

        if valid_wr:
            wr_desc = StatsUtils.descriptive(np.array(valid_wr), '승률')
            stats_dict['승률_기술통계'] = wr_desc

        # ── 4) 순위-승률 상관분석 ──
        print_section('순위-승률 상관분석')
        paired_r = []
        paired_w = []
        for r, w in zip(target_rankings, target_winrates):
            if r is not None and w is not None:
                paired_r.append(r)
                paired_w.append(w)

        if len(paired_r) >= 3:
            corr = StatsUtils.correlation(
                np.array(paired_r), np.array(paired_w), '순위', '승률'
            )
            hypothesis['순위_승률_상관'] = corr
            findings.append(f"순위-승률 상관: {corr['interpretation']}")
            print_stat('상관분석', corr['interpretation'])

        # 최대 상승/하락 분석
        if len(valid_rankings) >= 2:
            changes = []
            for i in range(1, len(target_rankings)):
                if target_rankings[i] is not None and target_rankings[i-1] is not None:
                    changes.append(target_rankings[i-1] - target_rankings[i])
            if changes:
                max_rise = max(changes)
                max_drop = min(changes)
                if max_rise > 0:
                    findings.append(f"최대 상승: {int(max_rise)}단계")
                if max_drop < 0:
                    findings.append(f"최대 하락: {int(abs(max_drop))}단계")

        # ── 인사이트 ──
        findings.insert(0, f"{team_name} {seasons_str[0]}~{seasons_str[-1]} 순위 변동 분석")
        insight = StatsUtils.format_insight('시즌별 순위 변동', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f't3_{team_name}_{LATEST_SEASON}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(1)
