"""
p6. 경기별 성적 변동성 분석
- 경기별 성적 시계열 + 이동평균
- 변동계수(CV)
- 안타수 분포 히스토그램 / 월별 추이
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
    get_player_info, is_pitcher, get_player_game_records,
    save_chart_json, save_analysis_output, print_header, print_section, print_stat,
    safe_float, SEASONS
)

TARGET_PLAYER_ID = 1
MOVING_AVG_WINDOW = 10


def analyze(player_id):
    db = DBConnector()
    try:
        info = get_player_info(db, player_id)
        print_header('경기별 성적 변동성 분석', info)

        pitcher = is_pitcher(info)
        table = 'kbo_pitcher_record' if pitcher else 'kbo_batter_record'

        records = get_player_game_records(db, player_id, table)
        if records.empty or len(records) < 5:
            print("  경기 기록이 부족합니다 (최소 5경기 필요)")
            return

        records['matchDate'] = pd.to_datetime(records['matchDate'])
        records = records.sort_values('matchDate')

        stats_dict = {}
        findings = []
        hypothesis = {}
        charts = []

        if pitcher:
            # 투수: 경기별 ERA 근사(er/ip*9), 삼진, WHIP 근사
            records['innings'] = pd.to_numeric(records['innings'], errors='coerce')
            records['earnedRuns'] = pd.to_numeric(records['earnedRuns'], errors='coerce')
            records['strikeouts'] = pd.to_numeric(records['strikeouts'], errors='coerce')
            records['hits'] = pd.to_numeric(records['hits'], errors='coerce')
            records['bb'] = pd.to_numeric(records['bb'], errors='coerce')

            # IP 변환: 7.1 → 7.333... (1/3 이닝)
            def convert_ip(ip_val):
                if pd.isna(ip_val):
                    return 0
                integer_part = int(ip_val)
                fraction = ip_val - integer_part
                return integer_part + (fraction * 10 / 3)

            records['ip_real'] = records['innings'].apply(convert_ip)
            valid = records[records['ip_real'] > 0].copy()

            if valid.empty:
                print("  유효한 투구 기록이 없습니다.")
                return

            valid['game_era'] = (valid['earnedRuns'] / valid['ip_real']) * 9
            valid['game_whip'] = (valid['hits'] + valid['bb']) / valid['ip_real']
            metric_col = 'game_era'
            metric_label = 'ERA (경기별)'
            sub_col = 'strikeouts'
            sub_label = '삼진'
        else:
            # 타자: 경기별 타율(hits/ab), 안타, 홈런, 타점
            records['ab'] = pd.to_numeric(records['ab'], errors='coerce')
            records['hits'] = pd.to_numeric(records['hits'], errors='coerce')
            records['hr'] = pd.to_numeric(records['hr'], errors='coerce')
            records['rbi'] = pd.to_numeric(records['rbi'], errors='coerce')

            valid = records[records['ab'] > 0].copy()
            if valid.empty:
                print("  유효한 타석 기록이 없습니다.")
                return

            valid['game_avg'] = valid['hits'] / valid['ab']
            metric_col = 'game_avg'
            metric_label = '타율 (경기별)'
            sub_col = 'hits'
            sub_label = '안타'

        metric_vals = valid[metric_col].values.astype(float)
        game_labels = [d.strftime('%m/%d') for d in valid['matchDate']]

        # ── 1) Line: 경기별 성적 + 이동평균 ──
        print_section(f'경기별 {metric_label}')
        ma = pd.Series(metric_vals).rolling(
            window=MOVING_AVG_WINDOW, min_periods=1
        ).mean().values

        charts.append(ChartBuilder.line(
            f'{info["name"]} 경기별 {metric_label}',
            game_labels,
            [
                {'label': metric_label,
                 'data': [round(v, 3) for v in metric_vals],
                 'pointRadius': 1,
                 'pointHoverRadius': 4,
                 'borderWidth': 1.5,
                 'borderColor': '#93C5FD'},
                {'label': f'{MOVING_AVG_WINDOW}경기 이동평균',
                 'data': [round(v, 3) for v in ma],
                 'borderWidth': 3,
                 'pointRadius': 0,
                 'borderColor': '#DC2626'},
            ]
        ))

        # 변동계수(CV)
        desc = StatsUtils.descriptive(metric_vals, metric_label)
        stats_dict[f'{metric_label}_기술통계'] = desc
        cv = (desc['std'] / desc['mean'] * 100) if desc['mean'] != 0 else 0
        stats_dict['변동계수_CV'] = round(cv, 2)
        findings.append(f"변동계수(CV): {cv:.1f}%")
        print_stat('평균', desc['mean'])
        print_stat('표준편차', desc['std'])
        print_stat('변동계수(CV)', f'{cv:.1f}%')

        # 정규성 검정
        norm = StatsUtils.normality_test(metric_vals, metric_label)
        hypothesis['정규성_검정'] = norm
        findings.append(f"정규성: {norm['interpretation']}")

        # ── 2) Bar: 히스토그램 (안타수/삼진 분포) ──
        print_section(f'{sub_label} 분포')
        sub_vals = valid[sub_col].dropna().astype(int).values

        if len(sub_vals) > 0:
            value_counts = pd.Series(sub_vals).value_counts().sort_index()
            hist_labels = [str(v) for v in value_counts.index]
            hist_data = value_counts.values.tolist()

            charts.append(ChartBuilder.bar(
                f'{info["name"]} {sub_label} 분포',
                hist_labels,
                [{'label': '경기 수', 'data': hist_data,
                  'backgroundColor': '#3B82F6'}]
            ))

            sub_desc = StatsUtils.descriptive(sub_vals.astype(float), sub_label)
            stats_dict[f'{sub_label}_분포'] = sub_desc
            findings.append(f"{sub_label} 평균: {sub_desc['mean']:.2f}, "
                            f"최다: {sub_desc['max']:.0f}")

        # ── 3) Line: 월별 추이 ──
        print_section('월별 추이')
        valid['month'] = valid['matchDate'].dt.month
        monthly = valid.groupby('month')[metric_col].mean()

        if len(monthly) >= 2:
            month_labels = [f'{int(m)}월' for m in monthly.index]
            month_vals = [round(v, 3) for v in monthly.values]

            charts.append(ChartBuilder.line(
                f'{info["name"]} 월별 {metric_label} 추이',
                month_labels,
                [{'label': f'월별 평균 {metric_label}', 'data': month_vals}]
            ))

            for ml, mv in zip(month_labels, month_vals):
                print_stat(ml, mv)

            stats_dict['월별_추이'] = dict(zip(month_labels, month_vals))

        # ── 인사이트 ──
        findings.insert(0, f"{info['name']}({info['teamName']}) "
                           f"경기별 성적 변동성 ({len(valid)}경기)")
        insight = StatsUtils.format_insight('경기별 성적 변동성', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_analysis_output(f'p6_{info["name"]}_{SEASONS[-1]}.json', charts, findings, stats_dict)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(TARGET_PLAYER_ID)
