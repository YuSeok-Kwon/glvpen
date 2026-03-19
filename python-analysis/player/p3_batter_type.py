"""
p3. 타자 유형 분류
- ISO/K%/BB%/SB 4축 z-score
- 파워/컨택/스피드/올라운더 분류
- 규정타석 50%(223 PA) 충족 타자만 분석
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
    get_player_info, is_pitcher,
    save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, SEASONS
)

TARGET_PLAYER_ID = 1

# 규정타석 50% (KBO 144경기 × 3.1 × 0.5 ≈ 223)
MIN_PA = 223

# 유형 분류 4축
AXES = ['ISO', 'K%', 'BB%', 'SB']
SB_CANDIDATES = ['SB', 'SB_count', 'stolen_bases']

TYPE_COLORS = {
    '파워형': '#DC2626',
    '컨택형': '#2563EB',
    '스피드형': '#059669',
    '올라운더': '#D97706',
    '미분류': '#CBD5E1',
}


def classify_batter(iso_z, k_z, bb_z, sb_z):
    """z-score 기반 타자 유형 분류"""
    if sb_z > 1.0:
        return '스피드형'
    if iso_z > 0.5 and k_z > 0.0:
        return '파워형'
    if k_z < 0.0 and bb_z > 0.0:
        return '컨택형'
    positives = sum(1 for z in [iso_z, -k_z, bb_z, sb_z] if z > 0.3)
    if positives >= 2:
        return '올라운더'
    return '미분류'


def analyze(player_id):
    db = DBConnector()
    try:
        info = get_player_info(db, player_id)
        if is_pitcher(info):
            print(f"  {info['name']}은(는) 투수입니다. 타자 유형 분류는 타자 전용입니다.")
            return

        print_header('타자 유형 분류', info)
        season = SEASONS[-1]

        league = db.get_batters(season)
        if league.empty:
            print(f"  {season} 시즌 데이터가 없습니다.")
            return

        # ── 규정타석 50% 필터 ──
        if 'PA' in league.columns:
            league['PA'] = pd.to_numeric(league['PA'], errors='coerce').fillna(0)
            before = len(league)
            league = league[league['PA'] >= MIN_PA].copy()
            print_stat('규정타석 필터', f'{before}명 → {len(league)}명 (PA >= {MIN_PA})')
        else:
            print("  PA 컬럼 없음 — 필터 생략")

        # SB 컬럼 확인
        sb_col = None
        for c in SB_CANDIDATES + ['SB']:
            if c in league.columns:
                sb_col = c
                break

        if sb_col is None:
            runner_sql = """
                SELECT playerId, value AS SB
                FROM player_runner_stats
                WHERE season = %s AND series = '0' AND category = 'SB'
            """
            runners = db.query(runner_sql, (season,))
            if not runners.empty:
                league = league.merge(runners, on='playerId', how='left')
                sb_col = 'SB'

        needed = ['ISO', 'K%', 'BB%']
        missing = [c for c in needed if c not in league.columns]
        if missing:
            print(f"  필수 컬럼 누락: {missing}")
            return

        for c in needed:
            league[c] = pd.to_numeric(league[c], errors='coerce')
        if sb_col and sb_col in league.columns:
            league[sb_col] = pd.to_numeric(league[sb_col], errors='coerce').fillna(0)
        else:
            league['SB'] = 0
            sb_col = 'SB'

        # z-score 계산
        axes_cols = ['ISO', 'K%', 'BB%', sb_col]
        for c in axes_cols:
            mean = league[c].mean()
            std = league[c].std()
            league[f'{c}_z'] = (league[c] - mean) / (std if std > 0 else 1)

        # 유형 분류
        league['batter_type'] = league.apply(
            lambda r: classify_batter(
                r['ISO_z'], r['K%_z'], r['BB%_z'], r[f'{sb_col}_z']
            ), axis=1
        )

        # 해당 선수 확인
        player_row = league[league['playerId'] == player_id]
        if player_row.empty:
            print(f"  {season} 시즌 해당 선수가 규정타석 미달이거나 데이터가 없습니다.")
            return
        pr = player_row.iloc[0]
        player_type = pr['batter_type']

        stats_dict = {}
        findings = []
        charts = []

        print_section(f'분류 결과: {player_type}')
        findings.append(f"{info['name']}의 타자 유형: {player_type}")
        for c in axes_cols:
            print_stat(f'{c}', f'{safe_float(pr[c]):.3f} (z={safe_float(pr[f"{c}_z"]):.2f})')

        # ── 1) 산점도: ISO vs SB (유형별 색상, 해당 선수 강조) ──
        print_section(f'{info["name"]} ISO vs SB 분포')
        scatter_datasets = []
        for btype, color in TYPE_COLORS.items():
            subset = league[(league['batter_type'] == btype) & (league['playerId'] != player_id)]
            if subset.empty:
                continue
            points = [{'x': round(safe_float(r['ISO']), 3),
                        'y': round(safe_float(r[sb_col]), 1)}
                       for _, r in subset.iterrows()]
            scatter_datasets.append({
                'label': btype, 'data': points,
                'backgroundColor': color, 'pointRadius': 5,
            })

        # 해당 선수 포인트 강조
        player_color = TYPE_COLORS.get(player_type, '#2563EB')
        scatter_datasets.append({
            'label': f'★ {info["name"]}',
            'data': [{'x': round(safe_float(pr['ISO']), 3),
                       'y': round(safe_float(pr[sb_col]), 1)}],
            'backgroundColor': player_color,
            'pointRadius': 10, 'pointStyle': 'star',
            'borderColor': '#000000', 'borderWidth': 2,
        })

        charts.append(ChartBuilder.scatter(
            f'{info["name"]} 위치: ISO vs SB ({season}, 규정타석50%)',
            scatter_datasets
        ))

        # ── 2) 도넛: 유형별 비율 ──
        print_section('리그 유형 비율')
        type_counts = league['batter_type'].value_counts()
        labels = type_counts.index.tolist()
        values = type_counts.values.tolist()
        colors = [TYPE_COLORS.get(t, '#CBD5E1') for t in labels]

        for t, cnt in zip(labels, values):
            print_stat(t, f'{cnt}명 ({cnt/len(league)*100:.1f}%)')
            findings.append(f"리그 {t}: {cnt}명 ({cnt/len(league)*100:.1f}%)")

        charts.append(ChartBuilder.doughnut(
            f'{info["name"]} 소속 유형 비율 ({season})', labels, values, colors
        ))

        # ── 3) 레이더: 해당 선수 4축 프로파일 ──
        print_section('선수 4축 프로파일')
        radar_vals = [round(50 + 10 * safe_float(pr[f'{c}_z']), 1) for c in axes_cols]
        league_avg = [50.0] * len(axes_cols)

        charts.append(ChartBuilder.radar(
            f'{info["name"]} 타자 프로파일 ({season})',
            ['장타력(ISO)', '삼진율(K%)', '선구안(BB%)', '주루(SB)'],
            [
                {'label': info['name'], 'data': radar_vals},
                {'label': '리그 평균', 'data': league_avg},
            ]
        ))

        # ── 인사이트 ──
        findings.insert(0, f"{info['name']}({info['teamName']}) "
                           f"타자 유형 분류 ({season})")
        insight = StatsUtils.format_insight('타자 유형 분류', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f'p3_{info["name"]}_{season}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(TARGET_PLAYER_ID)
