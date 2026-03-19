"""
p9. BABIP 운 분석
- BABIP vs AVG 시계열
- 괴리도 (BABIP - AVG)
- 행운 지수 (z-score)
- 평균 회귀 예측
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
    get_player_info, is_pitcher, get_player_season_stats,
    filter_qualified_batters,
    save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, SEASONS
)

TARGET_PLAYER_ID = 1


def analyze(player_id):
    db = DBConnector()
    try:
        info = get_player_info(db, player_id)
        if is_pitcher(info):
            print(f"  {info['name']}은(는) 투수입니다. BABIP 운 분석은 타자 전용입니다.")
            return

        print_header('BABIP 운 분석', info)

        # 선수 다시즌 BABIP, AVG
        df = get_player_season_stats(db, player_id, SEASONS,
                                     'player_batter_stats')
        if df.empty:
            print("  시즌 데이터가 없습니다.")
            return

        needed = ['BABIP', 'AVG']
        missing = [c for c in needed if c not in df.columns]
        if missing:
            print(f"  필수 컬럼 누락: {missing}. 컬럼: {list(df.columns)}")
            return

        df = df.sort_values('season')
        for c in needed:
            df[c] = pd.to_numeric(df[c], errors='coerce')
        df = df.dropna(subset=needed)

        if len(df) < 2:
            print("  유효 데이터가 부족합니다 (최소 2시즌 필요)")
            return

        seasons_str = [str(int(s)) for s in df['season'].values]
        babip_vals = [round(safe_float(v), 3) for v in df['BABIP'].values]
        avg_vals = [round(safe_float(v), 3) for v in df['AVG'].values]

        stats_dict = {}
        findings = []
        hypothesis = {}
        charts = []

        # ── 리그 평균 BABIP/AVG 산출 ──
        latest_season = SEASONS[-1]
        league = db.get_batters(latest_season)
        league = filter_qualified_batters(league)
        league_babip_mean = 0.300  # 기본값
        league_avg_mean = 0.260

        if not league.empty:
            if 'BABIP' in league.columns:
                league_babip_mean = round(
                    pd.to_numeric(league['BABIP'], errors='coerce').mean(), 3
                )
            if 'AVG' in league.columns:
                league_avg_mean = round(
                    pd.to_numeric(league['AVG'], errors='coerce').mean(), 3
                )

        # ── 1) Line: BABIP vs AVG 시계열 + 리그 평균선 ──
        print_section('BABIP vs AVG 시계열')
        charts.append(ChartBuilder.line(
            f'{info["name"]} BABIP vs AVG 추이',
            seasons_str,
            [
                {'label': 'BABIP', 'data': babip_vals},
                {'label': 'AVG', 'data': avg_vals},
                {'label': f'리그 BABIP 평균 ({league_babip_mean})',
                 'data': [league_babip_mean] * len(seasons_str),
                 'borderDash': [5, 5]},
                {'label': f'리그 AVG 평균 ({league_avg_mean})',
                 'data': [league_avg_mean] * len(seasons_str),
                 'borderDash': [5, 5]},
            ]
        ))

        for s, b, a in zip(seasons_str, babip_vals, avg_vals):
            print_stat(f'{s} BABIP', b)
            print_stat(f'{s} AVG', a)

        # ── 2) Bar: 시즌별 괴리도 (BABIP - AVG) ──
        print_section('시즌별 괴리도 (BABIP - AVG)')
        divergence = [round(b - a, 3) for b, a in zip(babip_vals, avg_vals)]
        stats_dict['괴리도'] = dict(zip(seasons_str, divergence))

        charts.append(ChartBuilder.bar(
            f'{info["name"]} BABIP-AVG 괴리도',
            seasons_str,
            [{'label': 'BABIP - AVG', 'data': divergence,
              'backgroundColor': ['#EF4444' if d > 0 else '#3B82F6'
                                  for d in divergence]}]
        ))

        for s, d in zip(seasons_str, divergence):
            print_stat(f'{s} 괴리도', f'{d:+.3f}')

        # 행운 지수 (BABIP 기준 z-score)
        babip_arr = np.array(babip_vals)
        babip_mean = babip_arr.mean()
        babip_std = babip_arr.std() if len(babip_arr) > 1 else 0.030
        if babip_std == 0:
            babip_std = 0.030

        latest_babip = babip_vals[-1]
        luck_z = (latest_babip - league_babip_mean) / babip_std
        findings.append(f"최근 시즌 행운 지수(z): {luck_z:.2f} "
                        f"({'행운' if luck_z > 0.5 else '불운' if luck_z < -0.5 else '보통'})")
        print_stat('행운 지수(z)', f'{luck_z:.2f}')

        # 평균 회귀 예측
        regression_target = league_babip_mean
        expected_change = regression_target - latest_babip
        findings.append(f"평균 회귀 예측: BABIP {expected_change:+.3f} 변동 예상 "
                        f"(현재 {latest_babip:.3f} → 리그평균 {regression_target:.3f})")

        # ── 3) Scatter: 리그 BABIP vs AVG + 해당 선수 ──
        print_section('리그 BABIP vs AVG 분포')
        if not league.empty and 'BABIP' in league.columns and 'AVG' in league.columns:
            league_scatter = league.dropna(subset=['BABIP', 'AVG']).copy()
            league_scatter['BABIP'] = pd.to_numeric(league_scatter['BABIP'],
                                                     errors='coerce')
            league_scatter['AVG'] = pd.to_numeric(league_scatter['AVG'],
                                                   errors='coerce')
            league_scatter = league_scatter.dropna(subset=['BABIP', 'AVG'])

            others = league_scatter[league_scatter['playerId'] != player_id]
            target = league_scatter[league_scatter['playerId'] == player_id]

            scatter_datasets = [
                {
                    'label': '리그 선수',
                    'data': [{'x': round(safe_float(r['BABIP']), 3),
                              'y': round(safe_float(r['AVG']), 3)}
                             for _, r in others.iterrows()],
                    'backgroundColor': '#3B82F6',
                },
            ]

            if not target.empty:
                tr = target.iloc[0]
                scatter_datasets.append({
                    'label': info['name'],
                    'data': [{'x': round(safe_float(tr['BABIP']), 3),
                              'y': round(safe_float(tr['AVG']), 3)}],
                    'backgroundColor': '#EF4444',
                    'pointRadius': 8,
                })

            charts.append(ChartBuilder.scatter(
                f'리그 BABIP vs AVG ({latest_season}) - {info["name"]} 위치',
                scatter_datasets
            ))

        # ── 통계 ──
        babip_desc = StatsUtils.descriptive(babip_arr, 'BABIP')
        avg_desc = StatsUtils.descriptive(np.array(avg_vals), 'AVG')
        stats_dict['BABIP_기술통계'] = babip_desc
        stats_dict['AVG_기술통계'] = avg_desc

        # 상관분석: BABIP vs AVG
        corr = StatsUtils.correlation(babip_arr, np.array(avg_vals),
                                       'BABIP', 'AVG')
        hypothesis['BABIP_AVG_상관'] = corr
        interp = corr.get('interpretation', corr.get('note', '분석 불가'))
        findings.append(f"BABIP-AVG 상관: {interp}")
        print_stat('BABIP-AVG 상관계수', corr.get('correlation', 0.0))

        # ── 인사이트 ──
        findings.insert(0, f"{info['name']}({info['teamName']}) "
                           f"BABIP 운 분석")
        insight = StatsUtils.format_insight('BABIP 운 분석', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f'p9_{info["name"]}_{SEASONS[-1]}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(TARGET_PLAYER_ID)
