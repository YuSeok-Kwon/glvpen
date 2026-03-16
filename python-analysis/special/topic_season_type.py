"""
특집: 정규시즌 vs 포스트시즌 vs 시범경기 상관관계 분석

분석 방법:
- 정규시즌(series='0')과 포스트시즌(series 다양), 시범경기 성적 비교
- 동일 선수 기준 정규 vs 포스트 성적 쌍표본 t-test
- 정규시즌 성적과 포스트시즌 성적 간 상관분석 (Pearson)
- 시범경기 성적의 정규시즌 예측력 분석
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils


TOPIC_INDEX = 17  # 특집 토픽 번호


def analyze(season: int, db: DBConnector) -> tuple:
    # 정규시즌 데이터
    regular_bat = db.get_batters_by_series(season, '0')
    regular_pit = db.get_pitchers_by_series(season, '0')

    # 포스트시즌 데이터 (series 코드: KBO는 다양 - '3'=와일드카드, '4'=준플, '5'=플오, '7'=한시 등)
    post_series_codes = ['3', '4', '5', '7', '9']
    post_bat_list = []
    post_pit_list = []
    for code in post_series_codes:
        pb = db.get_batters_by_series(season, code)
        if not pb.empty:
            post_bat_list.append(pb)
        pp = db.get_pitchers_by_series(season, code)
        if not pp.empty:
            post_pit_list.append(pp)

    post_bat = pd.concat(post_bat_list) if post_bat_list else pd.DataFrame()
    post_pit = pd.concat(post_pit_list) if post_pit_list else pd.DataFrame()

    if regular_bat.empty:
        return '{}', '[]', '정규시즌 데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # ==================== 타자: 정규 vs 포스트 ====================
    if not post_bat.empty:
        for metric in ['AVG', 'OPS', 'HR']:
            if metric not in regular_bat.columns or metric not in post_bat.columns:
                continue

            reg_vals = regular_bat.set_index('playerId')[metric]
            post_vals = post_bat.groupby('playerId')[metric].mean()
            common = reg_vals.index.intersection(post_vals.index)

            if len(common) >= 5:
                stats_dict[f'정규_{metric}'] = StatsUtils.descriptive(
                    reg_vals.loc[common].dropna().values, f'정규 {metric}'
                )
                stats_dict[f'포스트_{metric}'] = StatsUtils.descriptive(
                    post_vals.loc[common].dropna().values, f'포스트 {metric}'
                )

                # 쌍표본 t-test
                hypothesis[f'정규vs포스트_타자_{metric}'] = StatsUtils.t_test_paired(
                    reg_vals.loc[common].dropna().values,
                    post_vals.loc[common].dropna().values,
                    f'타자 {metric} 정규 vs 포스트'
                )
                findings.append(f"타자 {metric} 정규vs포스트: {hypothesis[f'정규vs포스트_타자_{metric}']['interpretation']}")

                # 상관분석
                hypothesis[f'정규포스트_상관_{metric}'] = StatsUtils.correlation(
                    reg_vals.loc[common].dropna().values,
                    post_vals.loc[common].dropna().values,
                    f'정규 {metric}', f'포스트 {metric}'
                )
                findings.append(f"정규-포스트 {metric} 상관: {hypothesis[f'정규포스트_상관_{metric}']['interpretation']}")

        # 차트: 정규 vs 포스트 평균 비교
        compare_labels = []
        reg_means = []
        post_means = []
        for m in ['AVG', 'OPS']:
            if f'정규_{m}' in stats_dict and f'포스트_{m}' in stats_dict:
                compare_labels.append(m)
                reg_means.append(stats_dict[f'정규_{m}']['mean'])
                post_means.append(stats_dict[f'포스트_{m}']['mean'])
        if compare_labels:
            charts.append(ChartBuilder.bar(
                '정규시즌 vs 포스트시즌 타자 성적',
                compare_labels,
                [
                    {'label': '정규시즌', 'data': reg_means},
                    {'label': '포스트시즌', 'data': post_means},
                ]
            ))
    else:
        findings.append("포스트시즌 타자 데이터 없음 - 정규시즌만 분석")

    # ==================== 투수: 정규 vs 포스트 ====================
    if not regular_pit.empty and not post_pit.empty:
        for metric in ['ERA', 'WHIP', 'K/9']:
            if metric not in regular_pit.columns or metric not in post_pit.columns:
                continue

            reg_vals = regular_pit.set_index('playerId')[metric]
            post_vals = post_pit.groupby('playerId')[metric].mean()
            common = reg_vals.index.intersection(post_vals.index)

            if len(common) >= 5:
                hypothesis[f'정규vs포스트_투수_{metric}'] = StatsUtils.t_test_paired(
                    reg_vals.loc[common].dropna().values,
                    post_vals.loc[common].dropna().values,
                    f'투수 {metric} 정규 vs 포스트'
                )
                findings.append(f"투수 {metric} 정규vs포스트: {hypothesis[f'정규vs포스트_투수_{metric}']['interpretation']}")

    # ==================== 총평 차트 ====================
    if not post_bat.empty:
        # 포스트시즌 성적 분포 도넛
        if 'AVG' in post_bat.columns:
            avg_bins = [0, 0.200, 0.250, 0.300, 0.350, 1.0]
            avg_labels_chart = ['~.200', '.200~.250', '.250~.300', '.300~.350', '.350~']
            hist, _ = np.histogram(post_bat['AVG'].dropna().values, bins=avg_bins)
            charts.append(ChartBuilder.doughnut(
                '포스트시즌 타자 타율 분포',
                avg_labels_chart,
                hist.tolist()
            ))

    findings.insert(0, f"정규 vs 포스트시즌 비교 분석 ({season})")
    insight = StatsUtils.format_insight('정규시즌 vs 포스트시즌 상관관계', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
