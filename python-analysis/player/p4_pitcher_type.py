"""
p4. 투수 유형 분류
- K/9, BB/9, HR/9, LOB%, GO/AO 5축 분석
- 탈삼진형/땅볼형/제구형 분류
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
    get_player_info, is_pitcher, filter_qualified_pitchers,
    save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, pick_available, SEASONS
)

TARGET_PLAYER_ID = 1

AXES = ['K/9', 'BB/9', 'HR/9', 'LOB%', 'GO/AO']
# 리그 중앙값 기준 분류 임계치
TYPE_COLORS = {
    '탈삼진형': '#EF4444',
    '땅볼형': '#3B82F6',
    '제구형': '#06B6D4',
    '밸런스형': '#F59E0B',
    '미분류': '#E7E9ED',
}


def classify_pitcher(k9_z, bb9_z, goao_z):
    """
    z-score 기반 투수 유형 분류.
    - 탈삼진형: K/9 상위
    - 땅볼형: GO/AO 상위
    - 제구형: BB/9 하위 (제구력 좋음)
    - 밸런스형: 여러 축 균형
    """
    if k9_z > 0.7:
        return '탈삼진형'
    if goao_z > 0.7:
        return '땅볼형'
    if bb9_z < -0.5:
        return '제구형'
    positives = sum(1 for z in [k9_z, -bb9_z, goao_z] if z > 0.3)
    if positives >= 2:
        return '밸런스형'
    return '미분류'


def analyze(player_id):
    db = DBConnector()
    try:
        info = get_player_info(db, player_id)
        if not is_pitcher(info):
            print(f"  {info['name']}은(는) 타자입니다. 투수 유형 분류는 투수 전용입니다.")
            return

        print_header('투수 유형 분류', info)
        season = SEASONS[-1]

        league = db.get_pitchers(season)
        league = filter_qualified_pitchers(league)
        if league.empty:
            print(f"  {season} 시즌 데이터가 없습니다.")
            return

        available = pick_available(league, AXES)
        # 최소 K/9, BB/9 필요
        core = ['K/9', 'BB/9']
        core_available = [c for c in core if c in league.columns]
        if len(core_available) < 2:
            print(f"  필수 컬럼(K/9, BB/9) 누락. 컬럼: {list(league.columns)}")
            return

        # 수치 변환
        for c in available:
            league[c] = pd.to_numeric(league[c], errors='coerce')

        # GO/AO 없으면 0으로 채움
        if 'GO/AO' not in league.columns:
            league['GO/AO'] = 0
            if 'GO/AO' not in available:
                available.append('GO/AO')

        # z-score 계산
        z_cols = ['K/9', 'BB/9', 'GO/AO']
        for c in z_cols:
            if c in league.columns:
                mean = league[c].mean()
                std = league[c].std()
                league[f'{c}_z'] = (league[c] - mean) / (std if std > 0 else 1)
            else:
                league[f'{c}_z'] = 0

        # 유형 분류
        league['pitcher_type'] = league.apply(
            lambda r: classify_pitcher(
                safe_float(r.get('K/9_z', 0)),
                safe_float(r.get('BB/9_z', 0)),
                safe_float(r.get('GO/AO_z', 0))
            ), axis=1
        )

        # 해당 선수 확인
        player_row = league[league['playerId'] == player_id]
        if player_row.empty:
            print(f"  {season} 시즌 해당 선수의 데이터가 없습니다.")
            return
        pr = player_row.iloc[0]
        player_type = pr['pitcher_type']

        stats_dict = {}
        findings = []
        hypothesis = {}
        charts = []

        print_section(f'분류 결과: {player_type}')
        findings.append(f"{info['name']}의 투수 유형: {player_type}")
        for c in available:
            val = safe_float(pr.get(c, 0))
            print_stat(c, f'{val:.3f}')

        # ── 1) 산점도: K/9 vs GO/AO ──
        print_section('K/9 vs GO/AO 분포')
        scatter_datasets = []
        for ptype, color in TYPE_COLORS.items():
            subset = league[league['pitcher_type'] == ptype]
            if subset.empty:
                continue
            points = [{'x': round(safe_float(r['K/9']), 2),
                        'y': round(safe_float(r.get('GO/AO', 0)), 2)}
                       for _, r in subset.iterrows()]
            scatter_datasets.append({
                'label': ptype, 'data': points, 'backgroundColor': color
            })

        if scatter_datasets:
            charts.append(ChartBuilder.scatter(
                f'투수 유형 분포: K/9 vs GO/AO ({season})',
                scatter_datasets
            ))

        # ── 2) 유형별 평균 ERA (Bar) ──
        print_section('유형별 평균 ERA')
        if 'ERA' in league.columns:
            league['ERA'] = pd.to_numeric(league['ERA'], errors='coerce')
            type_era = league.groupby('pitcher_type')['ERA'].mean()
            era_labels = type_era.index.tolist()
            era_values = [round(v, 3) for v in type_era.values]
            era_colors = [TYPE_COLORS.get(t, '#E7E9ED') for t in era_labels]

            for t, v in zip(era_labels, era_values):
                print_stat(f'{t} 평균 ERA', v)
                findings.append(f"{t} 평균 ERA: {v:.3f}")

            charts.append(ChartBuilder.bar(
                f'유형별 평균 ERA ({season})',
                era_labels,
                [{'label': '평균 ERA', 'data': era_values,
                  'backgroundColor': era_colors}]
            ))

            # ANOVA: 유형별 ERA 차이
            groups = {}
            for ptype in league['pitcher_type'].unique():
                subset = league[league['pitcher_type'] == ptype]['ERA'].dropna().values
                if len(subset) >= 2:
                    groups[ptype] = subset
            if len(groups) >= 2:
                hypothesis['유형별_ERA_ANOVA'] = StatsUtils.anova(groups)
                findings.append(f"유형별 ERA 차이: "
                                f"{hypothesis['유형별_ERA_ANOVA']['interpretation']}")

        # ── 3) 레이더: 해당 투수 5축 ──
        print_section('투수 5축 프로파일')
        radar_labels = []
        player_vals = []
        league_avg = []

        for c in available[:5]:
            pv = safe_float(pr.get(c, 0))
            col = league[c].dropna().astype(float)
            lg_mean = col.mean() if len(col) > 0 else 0
            lg_std = col.std() if len(col) > 1 else 1
            if lg_std == 0:
                lg_std = 1

            z = (pv - lg_mean) / lg_std
            # BB/9, HR/9는 낮을수록 좋으므로 반전
            if c in ('BB/9', 'HR/9'):
                z = -z
            player_vals.append(round(50 + 10 * z, 1))
            league_avg.append(50.0)
            radar_labels.append(c)

        charts.append(ChartBuilder.radar(
            f'{info["name"]} 투수 프로파일 ({season})',
            radar_labels,
            [
                {'label': info['name'], 'data': player_vals},
                {'label': '리그 평균', 'data': league_avg},
            ]
        ))

        # ── 인사이트 ──
        findings.insert(0, f"{info['name']}({info['teamName']}) "
                           f"투수 유형 분류 ({season})")
        insight = StatsUtils.format_insight('투수 유형 분류', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f'p4_{info["name"]}_{season}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(TARGET_PLAYER_ID)
