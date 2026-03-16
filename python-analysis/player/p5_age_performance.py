"""
p5. 나이 vs 성적 커브
- 선수 나이별 성적 매핑
- 동일 포지션 리그 평균 커브 산출
- 피크 나이 식별
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
    merge_real_position, filter_qualified_batters, filter_qualified_pitchers,
    save_chart_json, print_header, print_section, print_stat,
    safe_float, SEASONS
)

TARGET_PLAYER_ID = 1


def _calc_age(birth_date, season_year):
    """생년월일 + 시즌 연도 → 만 나이 (시즌 중 7/1 기준)"""
    if pd.isna(birth_date):
        return None
    try:
        birth = pd.Timestamp(birth_date)
        return season_year - birth.year - (1 if birth.month > 6 else 0)
    except Exception:
        return None


def analyze(player_id):
    db = DBConnector()
    try:
        info = get_player_info(db, player_id)
        print_header('나이 vs 성적 커브', info)

        pitcher = is_pitcher(info)
        table = 'player_pitcher_stats' if pitcher else 'player_batter_stats'
        primary = 'ERA' if pitcher else 'OPS'

        # 선수 다시즌 스탯
        df = get_player_season_stats(db, player_id, SEASONS, table)
        if df.empty or len(df) < 2:
            print("  데이터가 부족합니다 (최소 2시즌 필요)")
            return

        if primary not in df.columns:
            print(f"  {primary} 지표가 없습니다. 컬럼: {list(df.columns)}")
            return

        birth_date = info.get('birthDate')
        if pd.isna(birth_date) or birth_date is None:
            print("  생년월일 정보가 없어 나이 분석이 불가합니다.")
            return

        df = df.sort_values('season')
        df['age'] = df['season'].apply(lambda s: _calc_age(birth_date, int(s)))
        df[primary] = pd.to_numeric(df[primary], errors='coerce')
        df = df.dropna(subset=['age', primary])

        if df.empty:
            print("  유효한 나이-성적 데이터가 없습니다.")
            return

        stats_dict = {}
        findings = []
        hypothesis = {}
        charts = []

        ages = df['age'].astype(int).tolist()
        vals = [round(safe_float(v), 3) for v in df[primary].values]

        # ── 동일 포지션 리그 평균 커브 산출 ──
        print_section('리그 평균 에이징 커브')
        position = info.get('position', '')

        # 모든 시즌 선수 birth 데이터
        league_ages = {}
        for season in SEASONS:
            players_birth = db.get_players_with_birth(season)
            if pitcher:
                season_data = db.get_pitchers(season)
                season_data = filter_qualified_pitchers(season_data)
            else:
                season_data = db.get_batters(season)
                season_data = filter_qualified_batters(season_data)

            if season_data.empty or players_birth.empty:
                continue

            # batter_stats의 position은 'DH' → player 테이블의 실제 포지션으로 교체
            season_data = merge_real_position(db, season_data)

            merged = season_data.merge(
                players_birth[['playerId', 'birthDate']],
                on='playerId', how='inner'
            )

            # 동일 포지션 필터 (player 테이블의 실제 포지션 기준)
            if position and position != '투수':
                merged = merged[merged['position'] == position]

            if primary not in merged.columns:
                continue

            merged[primary] = pd.to_numeric(merged[primary], errors='coerce')
            merged['age'] = merged['birthDate'].apply(
                lambda bd: _calc_age(bd, season)
            )
            merged = merged.dropna(subset=['age', primary])

            for _, row in merged.iterrows():
                a = int(row['age'])
                if 18 <= a <= 42:
                    league_ages.setdefault(a, []).append(safe_float(row[primary]))

        # 리그 평균 커브
        league_curve_ages = sorted(league_ages.keys())
        league_curve_vals = [round(np.mean(league_ages[a]), 3)
                             for a in league_curve_ages]

        # ── 1) Line: 선수 커브 vs 리그 평균 ──
        print_section(f'{info["name"]} 에이징 커브')
        for a, v in zip(ages, vals):
            print_stat(f'{a}세', v)

        # 합집합 나이 축
        all_ages = sorted(set(ages) | set(league_curve_ages))
        player_map = dict(zip(ages, vals))
        league_map = dict(zip(league_curve_ages, league_curve_vals))

        player_line = [player_map.get(a, None) for a in all_ages]
        league_line = [league_map.get(a, None) for a in all_ages]
        age_labels = [str(a) for a in all_ages]

        datasets = [
            {'label': info['name'], 'data': player_line},
            {'label': f'리그 평균 ({position or "전체"})', 'data': league_line},
        ]
        charts.append(ChartBuilder.line(
            f'{info["name"]} 에이징 커브 ({primary})', age_labels, datasets
        ))

        # ── 2) Bar: 피크 전후 비교 ──
        print_section('피크 전후 비교')
        if pitcher and primary == 'ERA':
            peak_idx = int(np.argmin(vals))
        else:
            peak_idx = int(np.argmax(vals))

        peak_age = ages[peak_idx]
        peak_val = vals[peak_idx]
        findings.append(f"피크 나이: {peak_age}세 ({primary}={peak_val:.3f})")
        print_stat('피크 나이', f'{peak_age}세 ({primary}={peak_val:.3f})')

        before = [v for a, v in zip(ages, vals) if a < peak_age]
        after = [v for a, v in zip(ages, vals) if a > peak_age]
        before_avg = round(np.mean(before), 3) if before else 0
        after_avg = round(np.mean(after), 3) if after else 0

        charts.append(ChartBuilder.bar(
            f'{info["name"]} 피크({peak_age}세) 전후 {primary} 비교',
            ['피크 이전', f'피크({peak_age}세)', '피크 이후'],
            [{'label': primary,
              'data': [before_avg, peak_val, after_avg],
              'backgroundColor': ['#3B82F6', '#EF4444', '#F59E0B']}]
        ))

        findings.append(f"피크 이전 평균 {primary}: {before_avg:.3f}")
        findings.append(f"피크 이후 평균 {primary}: {after_avg:.3f}")

        # ── 상관분석 ──
        age_arr = np.array(ages, dtype=float)
        val_arr = np.array(vals, dtype=float)
        corr = StatsUtils.correlation(age_arr, val_arr, '나이', primary)
        hypothesis['나이_성적_상관'] = corr
        findings.append(f"나이-{primary} 상관: {corr['interpretation']}")
        print_stat('상관계수', corr['correlation'])

        stats_dict['피크_나이'] = peak_age
        stats_dict['피크_값'] = peak_val
        stats_dict[f'{primary}_기술통계'] = StatsUtils.descriptive(val_arr, primary)

        # ── 인사이트 ──
        findings.insert(0, f"{info['name']}({info['teamName']}) "
                           f"나이 vs 성적 커브")
        insight = StatsUtils.format_insight('나이 vs 성적 커브', findings)
        print_section('인사이트')
        print(f"  {insight}")

        save_chart_json(f'p5_{info["name"]}_{SEASONS[-1]}.json', charts)
    finally:
        db.close()


if __name__ == '__main__':
    analyze(TARGET_PLAYER_ID)
