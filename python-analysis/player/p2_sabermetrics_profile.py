"""
p2. 세이버메트릭스 프로파일
- 리그 대비 세이버 지표 위치
- 백분위 랭킹
- 강점/약점 식별
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import json
import numpy as np
from scipy.stats import percentileofscore
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from player.player_common import (
    get_player_info, is_pitcher,
    filter_qualified_batters, filter_qualified_pitchers,
    save_chart_json, print_header, print_section, print_stat,
    safe_float, pick_available, SEASONS
)

TARGET_PLAYER_ID = 1

BATTER_SABER = ['BABIP', 'ISO', 'K%', 'BB%', 'wOBA', 'OPS', 'OBP', 'SLG']
PITCHER_SABER = ['K/9', 'BB/9', 'HR/9', 'WHIP', 'ERA', 'FIP']

# 값이 낮을수록 좋은 지표 (투수)
LOWER_IS_BETTER = {'ERA', 'WHIP', 'BB/9', 'HR/9', 'K%', 'FIP', 'xFIP'}


def analyze(player_id):
    db = DBConnector()
    try:
        info = get_player_info(db, player_id)
        print_header('세이버메트릭스 프로파일', info)

        pitcher = is_pitcher(info)
        season = SEASONS[-1]

        # 리그 전체 데이터
        if pitcher:
            league = db.get_pitchers(season)
            league = filter_qualified_pitchers(league)
            candidates = PITCHER_SABER
        else:
            league = db.get_batters(season)
            league = filter_qualified_batters(league)
            candidates = BATTER_SABER

        if league.empty:
            print(f"  {season} 시즌 리그 데이터가 없습니다.")
            return

        metrics = pick_available(league, candidates)
        if not metrics:
            print(f"  사용 가능한 세이버 지표가 없습니다. 컬럼: {list(league.columns)}")
            return

        # 해당 선수 행
        player_row = league[league['playerId'] == player_id]
        if player_row.empty:
            print(f"  {season} 시즌 해당 선수의 데이터가 없습니다.")
            return
        player_row = player_row.iloc[0]

        stats_dict = {}
        findings = []
        charts = []

        # ── 1) 레이더: 선수 vs 리그 평균 (정규화) ──
        print_section('선수 vs 리그 평균 (정규화)')
        player_vals_norm = []
        league_vals_norm = []
        radar_labels = []

        for m in metrics:
            pv = safe_float(player_row.get(m, 0))
            col = league[m].dropna().astype(float)
            lg_mean = col.mean() if len(col) > 0 else 0
            lg_std = col.std() if len(col) > 1 else 1
            if lg_std == 0:
                lg_std = 1

            # z-score → 50 + 10*z (T-score 스케일)
            z = (pv - lg_mean) / lg_std
            # 낮을수록 좋은 지표는 부호 반전
            if m in LOWER_IS_BETTER:
                z = -z
            player_vals_norm.append(round(50 + 10 * z, 1))
            league_vals_norm.append(50.0)
            radar_labels.append(m)
            print_stat(f'{m}', f'{pv:.3f} (리그평균 {lg_mean:.3f}, T={player_vals_norm[-1]})')

        charts.append(ChartBuilder.radar(
            f'{info["name"]} 세이버메트릭스 프로파일 ({season})',
            radar_labels,
            [
                {'label': info['name'], 'data': player_vals_norm},
                {'label': '리그 평균', 'data': league_vals_norm},
            ]
        ))

        # ── 2) 백분위 랭킹 (Bar) ──
        print_section('백분위 랭킹')
        percentiles = []
        pct_labels = []

        for m in metrics:
            pv = safe_float(player_row.get(m, 0))
            col = league[m].dropna().astype(float).values
            if len(col) == 0:
                continue
            pct = percentileofscore(col, pv, kind='rank')
            # 낮을수록 좋은 지표는 반전
            if m in LOWER_IS_BETTER:
                pct = 100 - pct
            percentiles.append(round(pct, 1))
            pct_labels.append(m)
            stats_dict[f'{m}_백분위'] = round(pct, 1)
            print_stat(f'{m} 백분위', f'{pct:.1f}%')

        if pct_labels:
            charts.append(ChartBuilder.bar(
                f'{info["name"]} 백분위 랭킹 ({season})',
                pct_labels,
                [{'label': '백분위 (%)', 'data': percentiles,
                  'backgroundColor': '#3B82F6'}]
            ))

        # 강점/약점 식별
        if percentiles:
            sorted_metrics = sorted(zip(pct_labels, percentiles),
                                    key=lambda x: x[1], reverse=True)
            strengths = [f"{m} ({p:.0f}%)" for m, p in sorted_metrics[:2]]
            weaknesses = [f"{m} ({p:.0f}%)" for m, p in sorted_metrics[-2:]]
            findings.append(f"강점: {', '.join(strengths)}")
            findings.append(f"약점: {', '.join(weaknesses)}")

        # ── 3) 리그 분포 내 위치 (Bar: 리그 분포 5분위 + 선수 위치) ──
        print_section('리그 분포 내 위치')
        primary = metrics[0]
        pv = safe_float(player_row.get(primary, 0))
        col = league[primary].dropna().astype(float)

        if len(col) >= 5:
            q_labels = ['하위 20%', '20~40%', '40~60%', '60~80%', '상위 20%']
            quantiles = [0, 0.2, 0.4, 0.6, 0.8, 1.0]
            counts = []
            for i in range(5):
                lo = col.quantile(quantiles[i])
                hi = col.quantile(quantiles[i + 1])
                if i == 4:
                    cnt = int(((col >= lo) & (col <= hi)).sum())
                else:
                    cnt = int(((col >= lo) & (col < hi)).sum())
                counts.append(cnt)

            # 선수가 속한 구간 식별
            pct_rank = percentileofscore(col.values, pv, kind='rank')
            player_bin = min(int(pct_rank / 20), 4)

            desc = StatsUtils.descriptive(col.values, f'{primary} 리그분포')
            stats_dict[f'{primary}_리그분포'] = desc
            findings.append(f"{primary} 리그 내 위치: "
                            f"{q_labels[player_bin]} (값={pv:.3f})")

            charts.append(ChartBuilder.bar(
                f'{primary} 리그 분포 ({season}) - {info["name"]}: {q_labels[player_bin]}',
                q_labels,
                [{'label': '선수 수', 'data': counts,
                  'backgroundColor': [
                      '#EF4444' if i == player_bin else '#3B82F6'
                      for i in range(5)
                  ]}]
            ))

        # ── 인사이트 ──
        findings.insert(0, f"{info['name']}({info['teamName']}) "
                           f"세이버메트릭스 프로파일 ({season})")
        insight = StatsUtils.format_insight('세이버메트릭스 프로파일', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_chart_json(f'p2_{info["name"]}_{season}.json', charts)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(TARGET_PLAYER_ID)
