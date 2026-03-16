"""
2026 WBC 한국 대표팀 전력 분석

분석 항목:
1. 대표팀 선수 KBO 최근 시즌 성적 종합
2. 투수진 로테이션 분석 (선발/불펜 구성)
3. 타선 전력 분석 (타순 시뮬레이션용 데이터)
4. 팀별 대표 선발 비율 분석
5. 대표팀 vs KBO 리그 평균 비교 (t-test)
6. 투수진 유형 분석 (선발형 vs 불펜형)

투수 매칭 전략:
- 1차: pitcher_stats 테이블에서 이름 매칭
- 2차: batter_stats 테이블에서 이름 매칭 (투타겸업/분류 오류 대응)
- 3차: futures_pitcher_stats / futures_batter_stats 에서 매칭
- 미매칭 선수는 로스터 정보만으로 기록
"""
import json
import numpy as np
import pandas as pd
from common.db_connector import DBConnector
from common.chart_builder import ChartBuilder
from common.stats_utils import StatsUtils
from wbc.roster import (
    WBC_ROSTER, ALL_PLAYERS, PITCHER_NAMES, BATTER_NAMES,
    get_team_distribution
)


TOPIC_INDEX = 20  # WBC 특별 토픽


def _find_pitchers_extended(db: DBConnector, season: int, all_batters, all_pitchers):
    """
    투수 로스터를 여러 테이블에서 탐색.
    반환: (wbc_pitchers DataFrame, matched_names list, unmatched_names list)
    """
    matched_frames = []
    matched_names = []
    unmatched_names = list(PITCHER_NAMES)

    # 1차: pitcher_stats
    if not all_pitchers.empty:
        found = all_pitchers[all_pitchers['playerName'].isin(unmatched_names)]
        if not found.empty:
            matched_frames.append(found)
            found_names = found['playerName'].unique().tolist()
            matched_names.extend(found_names)
            unmatched_names = [n for n in unmatched_names if n not in found_names]

    # 2차: batter_stats (투타겸업 또는 DB 분류 오류)
    if unmatched_names and not all_batters.empty:
        found_in_bat = all_batters[all_batters['playerName'].isin(unmatched_names)]
        if not found_in_bat.empty:
            matched_frames.append(found_in_bat)
            found_names = found_in_bat['playerName'].unique().tolist()
            matched_names.extend(found_names)
            unmatched_names = [n for n in unmatched_names if n not in found_names]

    # 3차: futures 테이블
    if unmatched_names:
        try:
            futures_pit = db.get_futures_pitchers(season)
            if not futures_pit.empty:
                found = futures_pit[futures_pit['playerName'].isin(unmatched_names)]
                if not found.empty:
                    matched_frames.append(found)
                    found_names = found['playerName'].unique().tolist()
                    matched_names.extend(found_names)
                    unmatched_names = [n for n in unmatched_names if n not in found_names]
        except:
            pass

    if unmatched_names:
        try:
            futures_bat = db.get_futures_batters(season)
            if not futures_bat.empty:
                found = futures_bat[futures_bat['playerName'].isin(unmatched_names)]
                if not found.empty:
                    matched_frames.append(found)
                    found_names = found['playerName'].unique().tolist()
                    matched_names.extend(found_names)
                    unmatched_names = [n for n in unmatched_names if n not in found_names]
        except:
            pass

    # 합치기
    if matched_frames:
        combined = pd.concat(matched_frames, ignore_index=True)
        # 중복 제거 (같은 선수가 여러 테이블에 있을 수 있음)
        if 'playerId' in combined.columns:
            combined = combined.drop_duplicates(subset=['playerId'], keep='first')
        return combined, matched_names, unmatched_names
    else:
        return pd.DataFrame(), matched_names, unmatched_names


def _find_batters_extended(db: DBConnector, season: int, all_batters):
    """야수 로스터를 여러 테이블에서 탐색."""
    matched_frames = []
    matched_names = []
    unmatched_names = list(BATTER_NAMES)

    # 1차: batter_stats
    if not all_batters.empty:
        found = all_batters[all_batters['playerName'].isin(unmatched_names)]
        if not found.empty:
            matched_frames.append(found)
            found_names = found['playerName'].unique().tolist()
            matched_names.extend(found_names)
            unmatched_names = [n for n in unmatched_names if n not in found_names]

    # 2차: futures
    if unmatched_names:
        try:
            futures_bat = db.get_futures_batters(season)
            if not futures_bat.empty:
                found = futures_bat[futures_bat['playerName'].isin(unmatched_names)]
                if not found.empty:
                    matched_frames.append(found)
                    found_names = found['playerName'].unique().tolist()
                    matched_names.extend(found_names)
                    unmatched_names = [n for n in unmatched_names if n not in found_names]
        except:
            pass

    if matched_frames:
        combined = pd.concat(matched_frames, ignore_index=True)
        if 'playerId' in combined.columns:
            combined = combined.drop_duplicates(subset=['playerId'], keep='first')
        return combined, matched_names, unmatched_names
    else:
        return pd.DataFrame(), matched_names, unmatched_names


def analyze(season: int, db: DBConnector) -> tuple:
    """WBC 한국 대표팀 전력 분석"""
    # KBO 전체 데이터
    all_batters = db.get_batters(season)
    all_pitchers = db.get_pitchers(season)

    if all_batters.empty and all_pitchers.empty:
        return '{}', '[]', 'KBO 데이터 없음', '{}'

    stats_dict = {}
    hypothesis = {}
    findings = []
    charts = []

    # ==================== 확장 매칭 ====================
    wbc_pitchers, pit_matched, pit_unmatched = _find_pitchers_extended(
        db, season, all_batters, all_pitchers
    )
    wbc_batters, bat_matched, bat_unmatched = _find_batters_extended(
        db, season, all_batters
    )

    stats_dict['매칭_현황'] = {
        '투수_매칭': f"{len(pit_matched)}/{len(PITCHER_NAMES)}명",
        '야수_매칭': f"{len(bat_matched)}/{len(BATTER_NAMES)}명",
        '투수_미매칭': pit_unmatched,
        '야수_미매칭': bat_unmatched,
    }
    findings.append(f"DB 매칭: 투수 {len(pit_matched)}/{len(PITCHER_NAMES)}명, "
                    f"야수 {len(bat_matched)}/{len(BATTER_NAMES)}명")
    if pit_unmatched:
        findings.append(f"미매칭 투수: {', '.join(pit_unmatched)} (2025 시즌 데이터 미보유)")

    # ==================== 1. 타자 분석 ====================
    if not wbc_batters.empty:
        bat_cols = [c for c in ['playerName', 'teamName', 'position', 'WAR', 'AVG', 'OPS', 'HR', 'RBI', 'SB', 'wRC+']
                    if c in wbc_batters.columns]
        stats_dict['대표팀_타자_성적'] = wbc_batters[bat_cols].sort_values(
            'WAR', ascending=False
        ).to_dict('records') if 'WAR' in wbc_batters.columns else wbc_batters[bat_cols].to_dict('records')

        # 기술통계
        for metric in ['WAR', 'AVG', 'OPS', 'HR']:
            if metric in wbc_batters.columns:
                stats_dict[f'대표팀_타자_{metric}'] = StatsUtils.descriptive(
                    wbc_batters[metric].dropna().values, f'대표팀 타자 {metric}'
                )

        # 대표팀 vs KBO 평균 비교
        for metric in ['AVG', 'OPS', 'WAR']:
            if metric in wbc_batters.columns and not all_batters.empty and metric in all_batters.columns:
                non_wbc = all_batters[~all_batters['playerName'].isin(BATTER_NAMES)]
                if len(non_wbc) >= 10:
                    hypothesis[f'대표팀vs리그_타자_{metric}'] = StatsUtils.t_test_ind(
                        wbc_batters[metric].dropna().values,
                        non_wbc[metric].dropna().values,
                        '대표팀 타자', 'KBO 평균'
                    )
                    findings.append(
                        f"타자 {metric} 대표팀 vs KBO: "
                        f"{hypothesis[f'대표팀vs리그_타자_{metric}']['interpretation']}"
                    )

        # 차트: 대표팀 타자 WAR 랭킹
        if 'WAR' in wbc_batters.columns:
            sorted_bat = wbc_batters.sort_values('WAR', ascending=False)
            charts.append(ChartBuilder.bar(
                'WBC 대표팀 타자 WAR',
                [f"{r['playerName']}({r['teamName']})" for _, r in sorted_bat.iterrows()],
                [{'label': 'WAR', 'data': sorted_bat['WAR'].tolist()}]
            ))

        # 차트: 대표팀 vs KBO 평균 비교
        compare_metrics = []
        wbc_means = []
        kbo_means = []
        for m in ['AVG', 'OPS']:
            if m in wbc_batters.columns and not all_batters.empty and m in all_batters.columns:
                compare_metrics.append(m)
                wbc_means.append(round(wbc_batters[m].dropna().mean(), 4))
                kbo_means.append(round(all_batters[m].dropna().mean(), 4))
        if compare_metrics:
            charts.append(ChartBuilder.bar(
                '대표팀 타자 vs KBO 리그 평균',
                compare_metrics,
                [
                    {'label': '대표팀', 'data': wbc_means},
                    {'label': 'KBO 평균', 'data': kbo_means},
                ]
            ))

        # 레이더: 대표팀 1위 타자
        if stats_dict.get('대표팀_타자_성적') and len(stats_dict['대표팀_타자_성적']) > 0:
            top_batter = stats_dict['대표팀_타자_성적'][0]
            radar_labels = [k for k in ['WAR', 'AVG', 'OPS', 'HR'] if k in top_batter]
            radar_data = [top_batter[k] for k in radar_labels]
            if radar_labels:
                charts.append(ChartBuilder.radar(
                    f"대표팀 핵심 타자: {top_batter['playerName']}",
                    radar_labels,
                    [{'label': top_batter['playerName'], 'data': radar_data}]
                ))

    # ==================== 2. 투수 분석 ====================
    if not wbc_pitchers.empty:
        pit_cols = [c for c in ['playerName', 'teamName', 'WAR', 'ERA', 'WHIP', 'SO', 'IP', 'K/9', 'BB/9', 'FIP']
                    if c in wbc_pitchers.columns]
        stats_dict['대표팀_투수_성적'] = wbc_pitchers[pit_cols].sort_values(
            'WAR', ascending=False
        ).to_dict('records') if 'WAR' in wbc_pitchers.columns else wbc_pitchers[pit_cols].to_dict('records')

        # 기술통계
        for metric in ['WAR', 'ERA', 'K/9', 'WHIP']:
            if metric in wbc_pitchers.columns:
                stats_dict[f'대표팀_투수_{metric}'] = StatsUtils.descriptive(
                    wbc_pitchers[metric].dropna().values, f'대표팀 투수 {metric}'
                )

        # 대표팀 vs KBO 평균 비교 (pitcher_stats 테이블 기준)
        for metric in ['ERA', 'K/9', 'WAR']:
            if metric in wbc_pitchers.columns and not all_pitchers.empty and metric in all_pitchers.columns:
                # 투수 테이블에 있는 대표팀 투수만으로 비교
                pit_in_pit_table = wbc_pitchers[wbc_pitchers['playerName'].isin(
                    all_pitchers['playerName'].tolist()
                )]
                non_wbc = all_pitchers[~all_pitchers['playerName'].isin(PITCHER_NAMES)]
                if len(pit_in_pit_table) >= 3 and len(non_wbc) >= 10:
                    hypothesis[f'대표팀vs리그_투수_{metric}'] = StatsUtils.t_test_ind(
                        pit_in_pit_table[metric].dropna().values,
                        non_wbc[metric].dropna().values,
                        '대표팀 투수', 'KBO 평균'
                    )
                    findings.append(
                        f"투수 {metric} 대표팀 vs KBO: "
                        f"{hypothesis[f'대표팀vs리그_투수_{metric}']['interpretation']}"
                    )

        # 선발/불펜 분류
        roster_info = {p['name']: p['role'] for p in WBC_ROSTER['투수']}
        starters = wbc_pitchers[wbc_pitchers['playerName'].apply(
            lambda x: roster_info.get(x, '') == '선발'
        )]
        relievers = wbc_pitchers[wbc_pitchers['playerName'].apply(
            lambda x: roster_info.get(x, '') == '불펜'
        )]

        if not starters.empty and not relievers.empty and 'ERA' in wbc_pitchers.columns:
            stats_dict['선발_ERA'] = StatsUtils.descriptive(
                starters['ERA'].dropna().values, '선발 ERA'
            )
            stats_dict['불펜_ERA'] = StatsUtils.descriptive(
                relievers['ERA'].dropna().values, '불펜 ERA'
            )

            if len(starters) >= 2 and len(relievers) >= 2:
                hypothesis['선발vs불펜_ERA'] = StatsUtils.t_test_ind(
                    starters['ERA'].dropna().values,
                    relievers['ERA'].dropna().values,
                    '선발', '불펜'
                )
                findings.append(f"선발 vs 불펜 ERA: {hypothesis['선발vs불펜_ERA']['interpretation']}")

        # 차트: 투수 WAR 랭킹
        if 'WAR' in wbc_pitchers.columns:
            sorted_pit = wbc_pitchers.sort_values('WAR', ascending=False)
            charts.append(ChartBuilder.bar(
                'WBC 대표팀 투수 WAR',
                [f"{r['playerName']}({r['teamName']})" for _, r in sorted_pit.iterrows()],
                [{'label': 'WAR', 'data': sorted_pit['WAR'].tolist()}]
            ))

        # 차트: 선발/불펜 ERA 비교
        if 'ERA' in wbc_pitchers.columns and not starters.empty and not relievers.empty:
            charts.append(ChartBuilder.bar(
                '대표팀 선발 vs 불펜 평균 ERA',
                ['선발', '불펜'],
                [{'label': 'ERA', 'data': [
                    round(starters['ERA'].dropna().mean(), 2),
                    round(relievers['ERA'].dropna().mean(), 2)
                ]}]
            ))

    # ==================== 3. 팀별 선발 분포 ====================
    team_dist = get_team_distribution()
    stats_dict['팀별_선발_분포'] = team_dist

    charts.append(ChartBuilder.doughnut(
        '팀별 WBC 대표팀 선발 인원',
        list(team_dist.keys()),
        list(team_dist.values()),
    ))

    # ==================== 4. 포지션 밸런스 ====================
    position_counts = {pos: len(players) for pos, players in WBC_ROSTER.items()}
    stats_dict['포지션_구성'] = position_counts

    charts.append(ChartBuilder.doughnut(
        '대표팀 포지션 구성',
        list(position_counts.keys()),
        list(position_counts.values()),
    ))

    # ==================== 5. 다시즌 성적 추이 (핵심 선수) ====================
    key_batters = ['김도영', '노시환', '구자욱']
    try:
        multi_bat = db.get_batters_multi_season([season-2, season-1, season])
        if not multi_bat.empty and 'WAR' in multi_bat.columns:
            for batter_name in key_batters:
                player_data = multi_bat[multi_bat['playerName'] == batter_name]
                if not player_data.empty and len(player_data) >= 2:
                    seasons_list = sorted(player_data['season'].unique())
                    war_values = []
                    for s in seasons_list:
                        s_data = player_data[player_data['season'] == s]
                        war_values.append(round(float(s_data['WAR'].iloc[0]), 2) if not s_data.empty else 0)

                    stats_dict[f'{batter_name}_성장추이'] = {
                        'seasons': [int(s) for s in seasons_list],
                        'WAR': war_values,
                    }

            # 성장 추이 차트
            growth_datasets = []
            growth_seasons = []
            for batter_name in key_batters:
                key = f'{batter_name}_성장추이'
                if key in stats_dict:
                    if not growth_seasons:
                        growth_seasons = [str(s) for s in stats_dict[key]['seasons']]
                    growth_datasets.append({
                        'label': batter_name,
                        'data': stats_dict[key]['WAR'],
                    })

            if growth_datasets:
                charts.append(ChartBuilder.line(
                    '핵심 타자 WAR 성장 추이',
                    growth_seasons,
                    growth_datasets,
                ))
    except Exception:
        pass

    # ==================== 종합 ====================
    total_matched = len(pit_matched) + len(bat_matched)
    findings.insert(0, f"2026 WBC 한국 대표팀 전력 분석 (KBO {season} 시즌 기준)")
    findings.insert(1, f"로스터 {len(ALL_PLAYERS)}명 중 DB 매칭 {total_matched}명 "
                       f"(투수 {len(pit_matched)}/{len(PITCHER_NAMES)}명, "
                       f"야수 {len(bat_matched)}/{len(BATTER_NAMES)}명)")

    insight = StatsUtils.format_insight('2026 WBC 한국 대표팀 분석', findings)

    return (json.dumps(stats_dict, ensure_ascii=False, default=str),
            json.dumps(charts, ensure_ascii=False),
            insight,
            json.dumps(hypothesis, ensure_ascii=False, default=str))
