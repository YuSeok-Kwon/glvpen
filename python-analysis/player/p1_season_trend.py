"""
p1. 시즌 성장 트렌드 분석
- 시즌별 주요 지표 추이
- 전년 대비 변화율
- 피크 시즌 식별
- 성장 방향 상관분석
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import json
import numpy as np
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from player.player_common import (
    get_player_info, is_pitcher, get_player_season_stats,
    save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, pick_available, normalize_for_radar, SEASONS
)

TARGET_PLAYER_ID = 1

BATTER_METRICS = ['AVG', 'OPS', 'HR', 'RBI', 'wOBA', 'OBP', 'SLG']
BATTER_RATE_METRICS = {'AVG', 'OPS', 'wOBA', 'OBP', 'SLG'}
PITCHER_METRICS = ['ERA', 'WHIP', 'K/9', 'BB/9', 'W', 'SV', 'IP']


def analyze(player_id):
    db = DBConnector()
    try:
        info = get_player_info(db, player_id)
        print_header('시즌 성장 트렌드 분석', info)

        pitcher = is_pitcher(info)
        table = 'player_pitcher_stats' if pitcher else 'player_batter_stats'
        candidates = PITCHER_METRICS if pitcher else BATTER_METRICS

        df = get_player_season_stats(db, player_id, SEASONS, table)
        if df.empty or len(df) < 2:
            print("  데이터가 부족합니다 (최소 2시즌 필요)")
            return

        metrics = pick_available(df, candidates)
        if not metrics:
            print(f"  사용 가능한 지표가 없습니다. 컬럼: {list(df.columns)}")
            return

        df = df.sort_values('season')
        seasons_str = [str(int(s)) for s in df['season'].values]

        stats_dict = {}
        findings = []
        hypothesis = {}
        charts = []

        # ── 1) 시즌별 주요 지표 추이 (Line) ──
        # 타자: 비율 지표(AVG, OPS 등)와 카운팅 지표(HR, RBI)를 별도 차트로 분리
        print_section('시즌별 주요 지표 추이')
        if not pitcher:
            rate_metrics = [m for m in metrics if m in BATTER_RATE_METRICS]
            count_metrics = [m for m in metrics if m not in BATTER_RATE_METRICS]

            # 비율 지표 차트 (AVG, OPS 등)
            if rate_metrics:
                rate_datasets = []
                for m in rate_metrics[:3]:
                    vals = [safe_float(v) for v in df[m].values]
                    rate_datasets.append({'label': m, 'data': vals})
                    desc = StatsUtils.descriptive(np.array(vals), f'{m} 추이')
                    stats_dict[f'{m}_기술통계'] = desc
                    print_stat(f'{m} 평균', desc['mean'])
                charts.append(ChartBuilder.line(
                    f'{info["name"]} 시즌별 비율 지표 추이 (AVG·OPS)',
                    seasons_str, rate_datasets
                ))

            # 카운팅 지표 차트 (HR, RBI 등)
            if count_metrics:
                count_datasets = []
                for m in count_metrics[:3]:
                    vals = [safe_float(v) for v in df[m].values]
                    count_datasets.append({'label': m, 'data': vals})
                    desc = StatsUtils.descriptive(np.array(vals), f'{m} 추이')
                    stats_dict[f'{m}_기술통계'] = desc
                    print_stat(f'{m} 평균', desc['mean'])
                charts.append(ChartBuilder.line(
                    f'{info["name"]} 시즌별 카운팅 지표 추이 (HR·RBI)',
                    seasons_str, count_datasets
                ))
        else:
            # 투수: 스케일이 비슷하므로 기존 방식 유지
            line_datasets = []
            for m in metrics[:4]:
                vals = [safe_float(v) for v in df[m].values]
                line_datasets.append({'label': m, 'data': vals})
                desc = StatsUtils.descriptive(np.array(vals), f'{m} 추이')
                stats_dict[f'{m}_기술통계'] = desc
                print_stat(f'{m} 평균', desc['mean'])
            charts.append(ChartBuilder.line(
                f'{info["name"]} 시즌별 주요 지표 추이',
                seasons_str, line_datasets
            ))

        # ── 2) 전년 대비 변화율 (Bar) ──
        print_section('전년 대비 변화율')
        primary = metrics[0]
        vals = [safe_float(v) for v in df[primary].values]
        changes = []
        change_labels = []
        for i in range(1, len(vals)):
            prev, curr = vals[i - 1], vals[i]
            pct = ((curr - prev) / abs(prev)) * 100 if prev != 0 else 0
            changes.append(round(pct, 1))
            change_labels.append(f'{seasons_str[i - 1]}->{seasons_str[i]}')
            print_stat(f'{change_labels[-1]}', f'{changes[-1]:+.1f}%')

        if changes:
            charts.append(ChartBuilder.bar(
                f'{info["name"]} {primary} 전년 대비 변화율 (%)',
                change_labels,
                [{'label': f'{primary} 변화율', 'data': changes}]
            ))
            stats_dict[f'{primary}_변화율'] = changes

        # 피크 시즌 식별 (타자: 높을수록 좋음 / 투수 ERA: 낮을수록 좋음)
        if pitcher and primary == 'ERA':
            peak_idx = int(np.argmin(vals))
        else:
            peak_idx = int(np.argmax(vals))
        peak_season = seasons_str[peak_idx]
        peak_val = vals[peak_idx]
        findings.append(f"피크 시즌: {peak_season}년 ({primary}={peak_val:.3f})")
        print_stat('피크 시즌', f'{peak_season}년 ({primary}={peak_val:.3f})')

        # ── 3) 첫 시즌 vs 최근 시즌 (Radar) ──
        print_section('첫 시즌 vs 최근 시즌 비교')
        first_row = df.iloc[0]
        last_row = df.iloc[-1]
        radar_labels = []
        first_vals = []
        last_vals = []

        for m in metrics:
            fv = safe_float(first_row.get(m, 0))
            lv = safe_float(last_row.get(m, 0))
            max_v = max(abs(fv), abs(lv), 0.001)
            first_vals.append(round(fv / max_v * 100, 1))
            last_vals.append(round(lv / max_v * 100, 1))
            radar_labels.append(m)

        first_season = str(int(first_row['season']))
        last_season = str(int(last_row['season']))
        charts.append(ChartBuilder.radar(
            f'{info["name"]} {first_season} vs {last_season} 비교',
            radar_labels,
            [
                {'label': first_season, 'data': first_vals},
                {'label': last_season, 'data': last_vals},
            ]
        ))

        # ── 4) 성장 방향 상관분석 ──
        print_section('성장 방향 분석')
        season_nums = np.arange(len(df), dtype=float)
        for m in metrics[:3]:
            vals_arr = np.array([safe_float(v) for v in df[m].values])
            corr = StatsUtils.correlation(season_nums, vals_arr, '시즌', m)
            hypothesis[f'시즌_{m}_상관'] = corr
            findings.append(f"{m} 성장 방향: {corr['interpretation']}")
            print_stat(f'{m} 상관계수', corr['correlation'])

        # ── 인사이트 요약 ──
        findings.insert(0, f"{info['name']}({info['teamName']}) "
                           f"시즌 성장 트렌드 ({seasons_str[0]}~{seasons_str[-1]})")
        insight = StatsUtils.format_insight('시즌 성장 트렌드', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f'p1_{info["name"]}_{SEASONS[-1]}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(TARGET_PLAYER_ID)
