"""
p7. 포지션별 공격력 비교
- 동일 포지션 내 OPS 랭킹
- 전 포지션 평균 비교
- Top5 대비 분석
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from player.player_common import (
    get_player_info, is_pitcher, merge_real_position,
    filter_qualified_batters,
    save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, SEASONS
)

TARGET_PLAYER_ID = 1

OFFENSE_METRICS = ['OPS', 'AVG', 'OBP', 'SLG', 'HR', 'RBI', 'WAR']


def analyze(player_id):
    db = DBConnector()
    try:
        info = get_player_info(db, player_id)
        if is_pitcher(info):
            print(f"  {info['name']}은(는) 투수입니다. 포지션별 공격력 비교는 타자 전용입니다.")
            return

        print_header('포지션별 공격력 비교', info)
        season = SEASONS[-1]

        league = db.get_batters(season)
        league = filter_qualified_batters(league)
        if league.empty:
            print(f"  {season} 시즌 데이터가 없습니다.")
            return

        if 'OPS' not in league.columns:
            print(f"  OPS 컬럼이 없습니다. 컬럼: {list(league.columns)}")
            return

        # batter_stats의 position은 전부 'DH' → player 테이블의 실제 포지션으로 교체
        league = merge_real_position(db, league)
        league['OPS'] = pd.to_numeric(league['OPS'], errors='coerce')
        # 투수 제외, 포지션 null 제외
        league = league[
            league['position'].notna()
            & (league['position'] != '투수')
            & (league['position'] != '')
        ].copy()
        position = info.get('position', '')

        stats_dict = {}
        findings = []
        hypothesis = {}
        charts = []

        # ── 1) 포지션 내 Top10 (Bar) ──
        print_section(f'{position} 포지션 내 OPS Top10')
        same_pos = league[league['position'] == position].copy()
        same_pos = same_pos.dropna(subset=['OPS']).sort_values('OPS', ascending=False)

        top10 = same_pos.head(10)
        if top10.empty:
            print(f"  {position} 포지션에 데이터가 없습니다.")
        else:
            top_labels = [f"{r['playerName']}({r['teamName']})"
                          for _, r in top10.iterrows()]
            top_vals = [round(safe_float(r['OPS']), 3) for _, r in top10.iterrows()]

            # 해당 선수 하이라이트
            player_ids_in_top = top10['playerId'].tolist()
            colors = ['#EF4444' if pid == player_id else '#3B82F6'
                      for pid in player_ids_in_top]

            charts.append(ChartBuilder.bar(
                f'{position} OPS Top10 ({season})',
                top_labels,
                [{'label': 'OPS', 'data': top_vals,
                  'backgroundColor': colors}]
            ))

            # 선수 순위
            rank_list = same_pos['playerId'].tolist()
            if player_id in rank_list:
                rank = rank_list.index(player_id) + 1
                findings.append(f"{position} 내 OPS 순위: {rank}/{len(same_pos)}")
                print_stat(f'{position} 내 순위', f'{rank}/{len(same_pos)}')
            else:
                findings.append(f"{position} 내 순위 데이터 없음")

            for name, val in zip(top_labels[:5], top_vals[:5]):
                print_stat(name, val)

        # ── 2) 전 포지션 평균 OPS (Bar) ──
        print_section('전 포지션 평균 OPS')
        pos_avg = league.groupby('position')['OPS'].mean().sort_values(ascending=False)
        pos_labels = pos_avg.index.tolist()
        pos_vals = [round(v, 3) for v in pos_avg.values]

        # 해당 선수 포지션 하이라이트
        pos_colors = ['#EF4444' if p == position else '#3B82F6'
                      for p in pos_labels]

        charts.append(ChartBuilder.bar(
            f'포지션별 평균 OPS ({season})',
            pos_labels,
            [{'label': '평균 OPS', 'data': pos_vals,
              'backgroundColor': pos_colors}]
        ))

        for pl, pv in zip(pos_labels, pos_vals):
            print_stat(pl, pv)

        stats_dict['포지션별_평균_OPS'] = dict(zip(pos_labels, pos_vals))

        # t-검정: 해당 포지션 vs 나머지
        same_vals = same_pos['OPS'].dropna().values
        other_vals = league[league['position'] != position]['OPS'].dropna().values
        if len(same_vals) >= 2 and len(other_vals) >= 2:
            ttest = StatsUtils.t_test_ind(same_vals, other_vals,
                                          position, '나머지 포지션')
            hypothesis['포지션_OPS_ttest'] = ttest
            findings.append(f"{position} vs 나머지 OPS: {ttest['interpretation']}")

        # ── 3) 레이더: 해당 선수 vs 포지션 1위 (자기 자신이면 2위와 비교) ──
        print_section('선수 vs 포지션 상위 선수')
        player_row = league[league['playerId'] == player_id]
        if not player_row.empty and not top10.empty:
            pr = player_row.iloc[0]

            # 자기 자신이 1위면 2위와 비교, 아니면 1위와 비교
            compare_target = None
            for _, row in top10.iterrows():
                if int(row['playerId']) != player_id:
                    compare_target = row
                    break
            if compare_target is None:
                compare_target = top10.iloc[0]

            avail_metrics = [m for m in OFFENSE_METRICS if m in league.columns]
            radar_labels = []
            player_vals = []
            target_vals = []

            for m in avail_metrics:
                pv = safe_float(pr.get(m, 0))
                tv = safe_float(compare_target.get(m, 0))
                max_v = max(abs(pv), abs(tv), 0.001)
                player_vals.append(round(pv / max_v * 100, 1))
                target_vals.append(round(tv / max_v * 100, 1))
                radar_labels.append(m)

            target_name = compare_target.get('playerName', '비교 대상')
            target_rank_list = same_pos['playerId'].tolist()
            target_rank = target_rank_list.index(int(compare_target['playerId'])) + 1 if int(compare_target['playerId']) in target_rank_list else '?'
            charts.append(ChartBuilder.radar(
                f'{info["name"]} vs {target_name} ({position} {target_rank}위)',
                radar_labels,
                [
                    {'label': info['name'], 'data': player_vals},
                    {'label': f'{target_name} ({position} {target_rank}위)', 'data': target_vals},
                ]
            ))

        # ── 인사이트 ──
        findings.insert(0, f"{info['name']}({info['teamName']}) "
                           f"포지션별 공격력 비교 ({season})")
        insight = StatsUtils.format_insight('포지션별 공격력 비교', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f'p7_{info["name"]}_{season}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(TARGET_PLAYER_ID)
