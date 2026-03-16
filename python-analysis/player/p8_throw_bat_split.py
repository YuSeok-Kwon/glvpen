"""
p8. 투타 유형별 성적 분석
- 좌타/우타/양타별 OPS, AVG, HR 분포
- 해당 선수 유형 내 위치
- throwBat 파싱: "우투우타" → 타석 = throwBat[2:] ("우타"/"좌타"/"양타")
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
    get_player_info, is_pitcher, filter_qualified_batters,
    save_chart_json, print_header, print_section, print_stat,
    safe_float, SEASONS
)

TARGET_PLAYER_ID = 1

BAT_TYPE_COLORS = {
    '우타': '#3B82F6',
    '좌타': '#EF4444',
    '양타': '#F59E0B',
}


def parse_bat_hand(throw_bat):
    """
    throwBat 파싱: "우투우타" → "우타", "좌투좌타" → "좌타"
    4글자 형식이 아닌 경우 전체 반환
    """
    if not throw_bat or not isinstance(throw_bat, str):
        return '미상'
    tb = throw_bat.strip()
    if len(tb) >= 4:
        return tb[2:]  # "우투우타" → "우타"
    if len(tb) == 2:
        return tb  # "우타" 그대로
    return tb


def analyze(player_id):
    db = DBConnector()
    try:
        info = get_player_info(db, player_id)
        if is_pitcher(info):
            print(f"  {info['name']}은(는) 투수입니다. 투타 유형 분석은 타자 전용입니다.")
            return

        print_header('투타 유형별 성적 분석', info)
        season = SEASONS[-1]

        league = db.get_batters(season)
        league = filter_qualified_batters(league)
        if league.empty:
            print(f"  {season} 시즌 데이터가 없습니다.")
            return

        # throwBat JOIN
        player_sql = """
            SELECT id AS playerId, throwBat
            FROM player
            WHERE id IN (
                SELECT DISTINCT playerId FROM player_batter_stats
                WHERE season = %s AND series = '0'
            )
        """
        tb_df = db.query(player_sql, (season,))
        if tb_df.empty:
            print("  throwBat 데이터가 없습니다.")
            return

        league = league.merge(tb_df, on='playerId', how='left')
        league['bat_hand'] = league['throwBat'].apply(parse_bat_hand)

        if 'OPS' not in league.columns:
            print(f"  OPS 컬럼이 없습니다. 컬럼: {list(league.columns)}")
            return

        for col in ['OPS', 'AVG', 'HR']:
            if col in league.columns:
                league[col] = pd.to_numeric(league[col], errors='coerce')

        # 해당 선수
        player_row = league[league['playerId'] == player_id]
        if player_row.empty:
            print(f"  {season} 시즌 해당 선수의 데이터가 없습니다.")
            return
        pr = player_row.iloc[0]
        player_bat = pr['bat_hand']

        stats_dict = {}
        findings = []
        hypothesis = {}
        charts = []

        print_section(f'타석: {player_bat}')
        findings.append(f"{info['name']}의 타석: {player_bat} "
                        f"(throwBat: {info.get('throwBat', '?')})")

        # ── 1) 유형별 평균 OPS (Bar) ──
        print_section('유형별 평균 OPS')
        type_ops = league.groupby('bat_hand')['OPS'].mean().sort_values(ascending=False)
        type_labels = type_ops.index.tolist()
        type_vals = [round(v, 3) for v in type_ops.values]
        type_colors = [BAT_TYPE_COLORS.get(t, '#E7E9ED') for t in type_labels]

        # 선수 OPS 값 추가
        player_ops = safe_float(pr.get('OPS', 0))

        charts.append(ChartBuilder.bar(
            f'투타 유형별 평균 OPS ({season})',
            type_labels,
            [{'label': '평균 OPS', 'data': type_vals,
              'backgroundColor': type_colors}]
        ))

        for tl, tv in zip(type_labels, type_vals):
            count = len(league[league['bat_hand'] == tl])
            print_stat(tl, f'{tv:.3f} ({count}명)')
            stats_dict[f'{tl}_평균_OPS'] = tv
            findings.append(f"{tl} 평균 OPS: {tv:.3f} ({count}명)")

        # ANOVA: 좌/우/양타 OPS 차이
        groups = {}
        for bt in league['bat_hand'].unique():
            vals = league[league['bat_hand'] == bt]['OPS'].dropna().values
            if len(vals) >= 2:
                groups[bt] = vals
        if len(groups) >= 2:
            anova = StatsUtils.anova(groups)
            hypothesis['투타유형_OPS_ANOVA'] = anova
            findings.append(f"투타유형별 OPS 차이: {anova['interpretation']}")

        # ── 2) 유형 내 Top10 (Bar) ──
        print_section(f'{player_bat} 내 OPS Top10')
        same_type = league[league['bat_hand'] == player_bat].copy()
        same_type = same_type.dropna(subset=['OPS']).sort_values(
            'OPS', ascending=False
        )
        top10 = same_type.head(10)

        if not top10.empty:
            top_labels = [f"{r['playerName']}({r['teamName']})"
                          for _, r in top10.iterrows()]
            top_vals = [round(safe_float(r['OPS']), 3) for _, r in top10.iterrows()]
            colors = ['#EF4444' if r['playerId'] == player_id else '#3B82F6'
                      for _, r in top10.iterrows()]

            charts.append(ChartBuilder.bar(
                f'{player_bat} OPS Top10 ({season})',
                top_labels,
                [{'label': 'OPS', 'data': top_vals,
                  'backgroundColor': colors}]
            ))

            rank_list = same_type['playerId'].tolist()
            if player_id in rank_list:
                rank = rank_list.index(player_id) + 1
                findings.append(f"{player_bat} 내 순위: {rank}/{len(same_type)}")
                print_stat(f'{player_bat} 내 순위', f'{rank}/{len(same_type)}')

        # ── 3) 레이더: 좌타 avg vs 우타 avg vs 해당 선수 ──
        print_section('유형별 비교 레이더')
        compare_metrics = [m for m in ['OPS', 'AVG', 'OBP', 'SLG', 'HR']
                           if m in league.columns]

        if compare_metrics:
            radar_labels = compare_metrics
            datasets = []

            # 좌타 평균
            for bt in ['좌타', '우타']:
                subset = league[league['bat_hand'] == bt]
                if subset.empty:
                    continue
                bt_vals = []
                for m in compare_metrics:
                    bt_vals.append(round(safe_float(subset[m].mean()), 3))
                datasets.append({'label': f'{bt} 평균', 'data': bt_vals})

            # 해당 선수
            player_vals = [round(safe_float(pr.get(m, 0)), 3)
                           for m in compare_metrics]
            datasets.append({'label': info['name'], 'data': player_vals})

            # 정규화
            for ds in datasets:
                max_per_metric = []
                for i in range(len(compare_metrics)):
                    all_vals_for_m = [d['data'][i] for d in datasets]
                    max_per_metric.append(max(abs(v) for v in all_vals_for_m) or 1)
                ds['data'] = [round(v / mx * 100, 1)
                              for v, mx in zip(ds['data'], max_per_metric)]

            charts.append(ChartBuilder.radar(
                f'{info["name"]} vs 좌타/우타 평균 ({season})',
                radar_labels, datasets
            ))

        # ── 인사이트 ──
        findings.insert(0, f"{info['name']}({info['teamName']}) "
                           f"투타 유형별 성적 ({season})")
        insight = StatsUtils.format_insight('투타 유형별 성적', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_chart_json(f'p8_{info["name"]}_{season}.json', charts)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(TARGET_PLAYER_ID)
