"""
고급 분석 기반 팀별 컬럼 기사 생성.
5가지 고급 분석(클러치/Marcel/유사도/승리예측/피로도)을 팀별로 기사화.
분석당 10팀 = 총 50건.

실행:
  python3 advanced/write_columns.py                  # 전체
  python3 advanced/write_columns.py --analysis a1,a3 # 특정 분석만
  python3 advanced/write_columns.py --dry-run        # 미리보기
"""
import sys
import os
import json
import argparse
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.db_connector import DBConnector
from common.article_generator import generate_article
from common.chart_builder import ChartBuilder
from config import DATA_SEASON, PREDICT_SEASON, SEASONS

SEASON = DATA_SEASON

# ==================== 분석별 메타데이터 ====================

ANALYSIS_META = {
    'a1': {
        'title_template': '클러치 지수 분석: {teams}',
        'subtitle': 'RBI·HR·OPS Z-score 기반 결정적 순간의 강자',
        'category': 'player',
        'description': 'RBI, HR, OPS의 Z-score 합성 지수로 결정적 순간에 강한 타자를 분석합니다.',
    },
    'a2': {
        'title_template': 'Marcel 성적 예측: {teams}',
        'subtitle': '3년 가중평균 기반 주요 타자 성적 전망',
        'category': 'player',
        'description': 'Marcel 시스템(3년 가중평균 + 리그 회귀 + 노화 곡선)으로 주요 선수의 성적을 예측합니다.',
    },
    'a3': {
        'title_template': '선수 유사도 분석: {teams}',
        'subtitle': '세이버 지표 기반 유사 선수 탐색',
        'category': 'player',
        'description': 'ISO, K%, BB%, wOBA 등 세이버 지표의 코사인 유사도로 비슷한 유형의 선수를 찾습니다.',
    },
    'a4': {
        'title_template': '팀 승리 예측 분석: {teams}',
        'subtitle': '피타고리안 승률과 핵심 지표 기반 전력 분석',
        'category': 'team',
        'description': '득실점 기반 피타고리안 승률과 OPS/ERA 등 핵심 지표로 팀의 전력을 분석합니다.',
    },
    'a5': {
        'title_template': '투수 피로도 분석: {teams}',
        'subtitle': '등판 간격과 이닝 부하로 본 투수 건강 분석',
        'category': 'player',
        'description': '등판 간격별 ERA 변화와 이닝 부하량으로 투수의 피로도를 분석합니다.',
    },
}


# ==================== 공용 함수 ====================

def _safe(val, fmt='.3f'):
    """NaN-safe 포맷팅"""
    try:
        if val is None or (isinstance(val, float) and np.isnan(val)):
            return '-'
        return format(val, fmt)
    except (ValueError, TypeError):
        return str(val)


def _zscore(series):
    """시리즈의 Z-score 계산"""
    mean = series.mean()
    std = series.std()
    if std == 0 or np.isnan(std):
        return series * 0
    return (series - mean) / std


# ==================== a1: 클러치 지수 ====================

def analyze_clutch(db, team_id, team_name, batters_all):
    """팀별 클러치 지수 분석. 리그 전체 기준 Z-score 후 팀 필터."""
    findings = []
    charts = []

    # 리그 전체 Z-score 계산
    qualified = batters_all[batters_all.get('G', batters_all.get('PA', 0)) >= 50].copy()
    if qualified.empty or 'RBI' not in qualified.columns:
        return findings, charts

    for col in ['RBI', 'HR', 'OPS']:
        if col in qualified.columns:
            qualified[f'z_{col}'] = _zscore(qualified[col].fillna(0))

    qualified['clutch_index'] = (
        qualified.get('z_RBI', 0) +
        qualified.get('z_HR', 0) +
        qualified.get('z_OPS', 0)
    ) / 3

    # 팀 필터
    team_df = qualified[qualified['teamId'] == team_id].copy()
    if team_df.empty:
        findings.append(f'{team_name}에 해당 시즌 규정 타석 이상 타자가 없습니다.')
        return findings, charts

    team_df = team_df.sort_values('clutch_index', ascending=False)
    top5 = team_df.head(5)

    # 리그 내 순위
    league_rank = qualified.sort_values('clutch_index', ascending=False).reset_index(drop=True)
    league_rank['league_rank'] = range(1, len(league_rank) + 1)

    findings.append(f'{team_name} 클러치 지수 TOP 5 (리그 전체 {len(qualified)}명 중):')
    for i, (_, row) in enumerate(top5.iterrows(), 1):
        name = row.get('playerName', '?')
        ci = row.get('clutch_index', 0)
        rbi = int(row.get('RBI', 0))
        hr = int(row.get('HR', 0))
        ops = _safe(row.get('OPS', 0))
        lr = league_rank[league_rank['playerId'] == row['playerId']]['league_rank'].values
        lr_str = f'리그 {lr[0]}위' if len(lr) > 0 else ''
        findings.append(f'  {i}. {name} — 클러치지수 {_safe(ci, ".2f")} ({lr_str}) | RBI {rbi} HR {hr} OPS {ops}')

    # 팀 평균 vs 리그 평균
    team_avg = team_df['clutch_index'].mean()
    league_avg = qualified['clutch_index'].mean()
    diff = team_avg - league_avg
    label = '리그 평균 대비 강함' if diff > 0 else '리그 평균 대비 약함'
    findings.append(f'{team_name} 클러치 평균: {_safe(team_avg, ".2f")} (리그 평균 {_safe(league_avg, ".2f")}, {label})')

    # 차트 1: TOP 5 클러치 지수 바 차트
    top5_names = [row.get('playerName', '?') for _, row in top5.iterrows()]
    top5_ci = [round(row.get('clutch_index', 0), 2) for _, row in top5.iterrows()]
    charts.append(ChartBuilder.bar(
        f'{team_name} 클러치 지수 TOP 5',
        top5_names,
        [{'label': '클러치 지수', 'data': top5_ci}]
    ))

    # 차트 2: TOP 5 RBI/HR/OPS 레이더
    top3 = list(top5.head(3).iterrows())
    if len(top3) >= 2:
        radar_labels = ['RBI', 'HR', 'OPS', 'z_RBI', 'z_HR', 'z_OPS']
        radar_display = ['RBI', 'HR', 'OPS', 'Z-RBI', 'Z-HR', 'Z-OPS']
        radar_ds = []
        for _, row in top3:
            pname = row.get('playerName', '?')
            data = [round(float(row.get(c, 0)), 2) for c in radar_labels]
            radar_ds.append({'label': pname, 'data': data})
        charts.append(ChartBuilder.radar(
            f'{team_name} 상위 3명 클러치 지표 비교', radar_display, radar_ds
        ))

    return findings, charts


# ==================== a2: Marcel 성적 예측 ====================

def analyze_marcel(db, team_id, team_name, batters_multi, player_births):
    """Marcel 시스템으로 팀 주요 타자 성적 예측."""
    findings = []
    charts = []

    s0, s1, s2 = SEASON, SEASON - 1, SEASON - 2
    weights = {s0: 5, s1: 4, s2: 3}
    total_weight = 12

    # 팀 소속 선수 (현재 시즌)
    current = batters_multi[(batters_multi.get('season', -1) == s0)]
    if 'teamId' in current.columns:
        team_players = current[current['teamId'] == team_id]['playerId'].unique()
    else:
        team_players = current[current['playerName'].isin(
            current[current.get('teamName', '') == team_name]['playerName']
        )]['playerId'].unique()

    if len(team_players) == 0:
        findings.append(f'{team_name}에 다시즌 데이터가 있는 타자가 없습니다.')
        return findings, charts

    categories = ['AVG', 'OPS', 'HR', 'RBI', 'wOBA']
    projections = []

    for pid in team_players:
        p_data = batters_multi[batters_multi['playerId'] == pid]
        if len(p_data[p_data.get('season', -1) == s0]) == 0:
            continue
        pname = p_data.iloc[0].get('playerName', '?')

        # 규정 타석 체크 (G >= 50 어느 한 시즌이라도)
        if p_data[p_data.get('G', 0) >= 50].empty:
            continue

        proj = {'name': pname, 'playerId': pid}
        for cat in categories:
            if cat not in p_data.columns:
                continue
            weighted_sum = 0
            w_sum = 0
            for szn, w in weights.items():
                row = p_data[p_data['season'] == szn]
                if not row.empty and not np.isnan(row.iloc[0].get(cat, float('nan'))):
                    weighted_sum += row.iloc[0][cat] * w
                    w_sum += w
            if w_sum == 0:
                continue
            weighted_avg = weighted_sum / w_sum

            # 리그 평균 (현재 시즌)
            league_avg = batters_multi[batters_multi['season'] == s0][cat].mean()
            if np.isnan(league_avg):
                league_avg = weighted_avg

            # 리그 회귀 (20%)
            projected = weighted_avg * 0.8 + league_avg * 0.2

            # 노화 곡선 (간이)
            if player_births is not None and pid in player_births.index:
                birth = player_births.loc[pid]
                if birth and not (isinstance(birth, float) and np.isnan(birth)):
                    try:
                        age = PREDICT_SEASON - int(str(birth)[:4])
                        if age > 30:
                            decay = 1 - (age - 30) * 0.005
                            if cat in ['AVG', 'OPS', 'wOBA']:
                                projected *= max(decay, 0.9)
                    except (ValueError, TypeError):
                        pass

            last_val = p_data[p_data['season'] == s0].iloc[0].get(cat, 0)
            proj[cat] = projected
            proj[f'{cat}_last'] = last_val
            proj[f'{cat}_change'] = ((projected - last_val) / max(abs(last_val), 0.001)) * 100 if last_val != 0 else 0

        if 'OPS' in proj:
            projections.append(proj)

    if not projections:
        findings.append(f'{team_name}에 Marcel 예측 대상 타자가 없습니다.')
        return findings, charts

    # OPS 기준 정렬
    projections.sort(key=lambda x: x.get('OPS', 0), reverse=True)

    findings.append(f'{team_name} {PREDICT_SEASON} 시즌 Marcel 예측 (상위 5명):')
    for i, p in enumerate(projections[:5], 1):
        ops_proj = _safe(p.get('OPS', 0))
        ops_last = _safe(p.get('OPS_last', 0))
        ops_chg = _safe(p.get('OPS_change', 0), '+.1f') if p.get('OPS_change', 0) >= 0 else _safe(p.get('OPS_change', 0), '.1f')
        avg_proj = _safe(p.get('AVG', 0))
        hr_proj = _safe(p.get('HR', 0), '.0f')
        findings.append(f'  {i}. {p["name"]} — 예상 OPS {ops_proj} (전년 {ops_last}, {ops_chg}%) | AVG {avg_proj} HR {hr_proj}')

    # 팀 전체 전망
    team_ops_proj = np.mean([p.get('OPS', 0) for p in projections])
    team_ops_last = np.mean([p.get('OPS_last', 0) for p in projections])
    direction = '상승' if team_ops_proj > team_ops_last else '하락'
    findings.append(f'{team_name} 팀 평균 예상 OPS: {_safe(team_ops_proj)} (전년 {_safe(team_ops_last)}, {direction} 전망)')

    # 차트: 상위 5명 전년 OPS vs 예상 OPS
    top5 = projections[:5]
    names = [p['name'] for p in top5]
    last_ops = [round(p.get('OPS_last', 0), 3) for p in top5]
    proj_ops = [round(p.get('OPS', 0), 3) for p in top5]
    charts.append(ChartBuilder.bar(
        f'{team_name} Marcel OPS 예측 ({SEASON} → {PREDICT_SEASON})',
        names,
        [
            {'label': f'{SEASON} 실제', 'data': last_ops, 'backgroundColor': '#94A3B8'},
            {'label': f'{PREDICT_SEASON} 예측', 'data': proj_ops, 'backgroundColor': '#2563EB'},
        ]
    ))

    return findings, charts


# ==================== a3: 선수 유사도 ====================

def analyze_similarity(db, team_id, team_name, batters_all):
    """팀 대표 타자의 유사 선수 분석."""
    findings = []
    charts = []

    saber_cols = ['ISO', 'K%', 'BB%', 'wOBA', 'SLG', 'OBP', 'AVG', 'BABIP']
    available = [c for c in saber_cols if c in batters_all.columns]
    if len(available) < 4:
        findings.append('세이버 지표 데이터가 부족합니다.')
        return findings, charts

    qualified = batters_all[batters_all.get('G', 0) >= 50].copy()
    if qualified.empty:
        return findings, charts

    # 결측치 제거 후 표준화
    data = qualified[available].fillna(0).values
    norms = np.linalg.norm(data, axis=1, keepdims=True)
    norms[norms == 0] = 1
    normalized = data / norms

    # 팀 대표 타자 (OPS 최고)
    team_df = qualified[qualified['teamId'] == team_id]
    if team_df.empty:
        findings.append(f'{team_name}에 규정 타석 이상 타자가 없습니다.')
        return findings, charts

    top_batter_idx = team_df['OPS'].idxmax() if 'OPS' in team_df.columns else team_df.index[0]
    top_batter = qualified.loc[top_batter_idx]
    top_name = top_batter.get('playerName', '?')

    # 코사인 유사도 계산
    base_vec = normalized[qualified.index.get_loc(top_batter_idx)]
    similarities = np.dot(normalized, base_vec)

    qualified = qualified.copy()
    qualified['similarity'] = similarities

    # 타팀 선수만 (자기 자신 제외)
    others = qualified[(qualified['teamId'] != team_id) & (qualified.index != top_batter_idx)]
    others = others.sort_values('similarity', ascending=False)

    findings.append(f'{team_name} 대표 타자 {top_name}과 가장 유사한 선수 TOP 5:')
    findings.append(f'  (기준: {", ".join(available)})')
    findings.append(f'  {top_name} 지표 — OPS {_safe(top_batter.get("OPS", 0))} wOBA {_safe(top_batter.get("wOBA", 0))} ISO {_safe(top_batter.get("ISO", 0))}')

    top5_similar = list(others.head(5).iterrows())
    for i, (_, row) in enumerate(top5_similar, 1):
        sim = row.get('similarity', 0) * 100
        name = row.get('playerName', '?')
        team = row.get('teamName', '?')
        ops = _safe(row.get('OPS', 0))
        woba = _safe(row.get('wOBA', 0))
        findings.append(f'  {i}. {name} ({team}) — 유사도 {_safe(sim, ".1f")}% | OPS {ops} wOBA {woba}')

    # 차트 1: 유사도 바 차트
    sim_names = [row.get('playerName', '?') for _, row in top5_similar]
    sim_scores = [round(row.get('similarity', 0) * 100, 1) for _, row in top5_similar]
    charts.append(ChartBuilder.bar(
        f'{top_name}과 유사한 선수 TOP 5 (유사도 %)',
        sim_names,
        [{'label': '유사도', 'data': sim_scores}]
    ))

    # 차트 2: 기준 선수 vs 1위 유사 선수 레이더
    if top5_similar:
        _, most_sim = top5_similar[0]
        radar_labels = [c for c in available if c in ('OPS', 'SLG', 'OBP', 'AVG', 'ISO', 'wOBA')][:6]
        if len(radar_labels) >= 4:
            base_data = [round(float(top_batter.get(c, 0)), 3) for c in radar_labels]
            sim_data = [round(float(most_sim.get(c, 0)), 3) for c in radar_labels]
            charts.append(ChartBuilder.radar(
                f'{top_name} vs {most_sim.get("playerName", "?")} 지표 비교',
                radar_labels,
                [
                    {'label': top_name, 'data': base_data},
                    {'label': most_sim.get('playerName', '?'), 'data': sim_data},
                ]
            ))

    return findings, charts


# ==================== a4: 승리 예측 ====================

def analyze_win_prediction(db, team_id, team_name, team_stats, rankings):
    """피타고리안 승률 + 핵심 지표 기반 팀 전력 분석."""
    findings = []
    charts = []

    team = team_stats[team_stats['teamId'] == team_id]
    if team.empty:
        findings.append(f'{team_name}의 팀 스탯 데이터가 없습니다.')
        return findings, charts

    team = team.iloc[0]

    # 기본 지표
    rs = team.get('R', 0) or 0
    ra = team.get('실점', team.get('ER', 0)) or 0
    if ra == 0:
        ra = team.get('ERA', 4.0) * team.get('IP', 1296) / 9 if 'ERA' in team.index else 1
    actual_wpct = team.get('WPCT', 0) or 0
    games = team.get('G', 144) or 144

    # 피타고리안 승률 (RS^2 / (RS^2 + RA^2))
    if rs + ra > 0:
        pyth_wpct = rs ** 2 / (rs ** 2 + ra ** 2)
    else:
        pyth_wpct = 0.5

    pyth_wins = round(pyth_wpct * games)
    actual_wins = round(actual_wpct * games)
    luck = actual_wins - pyth_wins

    findings.append(f'{team_name} {SEASON} 시즌 전력 분석:')
    findings.append(f'  실제 승률: {_safe(actual_wpct)} ({actual_wins}승)')
    findings.append(f'  피타고리안 승률: {_safe(pyth_wpct)} (기대 {pyth_wins}승)')
    luck_label = f'+{luck}승 (행운)' if luck > 0 else f'{luck}승 (불운)'
    findings.append(f'  행운 지수: {luck_label}')

    # 핵심 지표 vs 리그 평균
    findings.append(f'\n  핵심 지표 (리그 평균 대비):')
    for cat, label, inv in [('OPS', '팀 OPS', False), ('ERA', '팀 ERA', True),
                             ('WHIP', 'WHIP', True), ('AVG', '팀 타율', False),
                             ('HR', '홈런', False), ('FPCT', '수비율', False)]:
        val = team.get(cat)
        if val is None or (isinstance(val, float) and np.isnan(val)):
            continue
        league_avg = team_stats[cat].mean() if cat in team_stats.columns else val
        diff = val - league_avg
        if inv:
            better = '우수' if diff < 0 else '열세'
        else:
            better = '우수' if diff > 0 else '열세'
        findings.append(f'    {label}: {_safe(val)} (리그 평균 {_safe(league_avg)}, {better})')

    # 순위 정보
    rank_row = rankings[rankings['teamId'] == team_id]
    if not rank_row.empty:
        rank = int(rank_row.iloc[0].get('ranking', 0))
        findings.append(f'\n  {SEASON} 시즌 최종 순위: {rank}위')

    # 다음 시즌 전망
    projected_wpct = pyth_wpct * 0.7 + actual_wpct * 0.3
    proj_wins = round(projected_wpct * 144)
    findings.append(f'\n  {PREDICT_SEASON} 시즌 전망:')
    findings.append(f'    예상 승률: {_safe(projected_wpct)} (약 {proj_wins}승)')

    # 차트 1: 실제 승수 vs 피타고리안 기대 승수
    charts.append(ChartBuilder.bar(
        f'{team_name} 실제 vs 기대 승수',
        ['실제 승수', '피타고리안 기대', f'{PREDICT_SEASON} 전망'],
        [{'label': '승수', 'data': [actual_wins, pyth_wins, proj_wins]}]
    ))

    # 차트 2: 핵심 지표 레이더 (팀 vs 리그 평균)
    radar_cats = []
    team_vals = []
    league_vals = []
    for cat, lbl, inv in [('OPS', 'OPS', False), ('AVG', '타율', False),
                           ('HR', 'HR', False), ('SLG', 'SLG', False),
                           ('OBP', 'OBP', False)]:
        val = team.get(cat)
        if val is not None and not (isinstance(val, float) and np.isnan(val)):
            lg = team_stats[cat].mean() if cat in team_stats.columns else val
            radar_cats.append(lbl)
            team_vals.append(round(float(val), 3))
            league_vals.append(round(float(lg), 3))
    if len(radar_cats) >= 3:
        charts.append(ChartBuilder.radar(
            f'{team_name} 핵심 지표 vs 리그 평균',
            radar_cats,
            [
                {'label': team_name, 'data': team_vals},
                {'label': '리그 평균', 'data': league_vals},
            ]
        ))

    return findings, charts


# ==================== a5: 피로도 분석 ====================

def analyze_fatigue(db, team_id, team_name, pitcher_records, pitchers_season):
    """팀 주요 투수의 등판 간격별 ERA + 이닝 부하 분석."""
    findings = []
    charts = []

    # 팀 투수 경기 기록
    team_records = pitcher_records[pitcher_records['teamId'] == team_id].copy()
    if team_records.empty:
        findings.append(f'{team_name}의 투수 경기 기록이 없습니다.')
        return findings, charts

    # 경기별 날짜 매핑 (scheduleId → 날짜순 정렬)
    team_records = team_records.sort_values('scheduleId')

    # 시즌 스탯에서 팀 투수 필터
    team_pitchers = pitchers_season[pitchers_season['teamId'] == team_id] if 'teamId' in pitchers_season.columns else pitchers_season[pitchers_season['teamName'] == team_name]

    # 주요 투수 (이닝 상위 5명)
    if team_pitchers.empty:
        findings.append(f'{team_name}의 투수 시즌 스탯이 없습니다.')
        return findings, charts

    top_pitchers = team_pitchers.sort_values('IP', ascending=False).head(5) if 'IP' in team_pitchers.columns else team_pitchers.head(5)

    findings.append(f'{team_name} 주요 투수 피로도 분석 (상위 5명):')

    for _, pitcher in top_pitchers.iterrows():
        pid = pitcher['playerId']
        pname = pitcher.get('playerName', '?')
        season_ip = pitcher.get('IP', 0)
        season_era = pitcher.get('ERA', 0)
        season_g = int(pitcher.get('G', 0))
        gs = int(pitcher.get('GS', 0))

        # 해당 투수의 경기별 기록
        p_records = team_records[team_records['playerId'] == pid].copy()
        if p_records.empty:
            continue

        appearances = len(p_records)

        # 등판 간격 계산 (scheduleId 차이를 대리 지표로)
        schedule_ids = sorted(p_records['scheduleId'].unique())
        rest_gaps = []
        for i in range(1, len(schedule_ids)):
            gap = schedule_ids[i] - schedule_ids[i - 1]
            rest_gaps.append(gap)

        # 등판 간격 그룹별 ERA
        if len(rest_gaps) > 0:
            p_records = p_records.copy()
            p_records['rest'] = [0] + rest_gaps
            short_rest = p_records[p_records['rest'].between(1, 3)]
            normal_rest = p_records[p_records['rest'].between(4, 6)]
            long_rest = p_records[p_records['rest'] > 6]

            def _group_era(group):
                ip = group['innings'].sum()
                er = group['earnedRuns'].sum()
                return (er / ip * 9) if ip > 0 else 0

            findings.append(f'\n  {pname} (시즌 {_safe(season_ip, ".1f")}이닝, ERA {_safe(season_era)}):')
            findings.append(f'    등판 {appearances}회 (선발 {gs}회)')

            if not short_rest.empty:
                findings.append(f'    단기 휴식(1~3경기): ERA {_safe(_group_era(short_rest))} ({len(short_rest)}회)')
            if not normal_rest.empty:
                findings.append(f'    보통 휴식(4~6경기): ERA {_safe(_group_era(normal_rest))} ({len(normal_rest)}회)')
            if not long_rest.empty:
                findings.append(f'    장기 휴식(7+경기): ERA {_safe(_group_era(long_rest))} ({len(long_rest)}회)')

            avg_rest = np.mean(rest_gaps) if rest_gaps else 0
            findings.append(f'    평균 등판 간격: {_safe(avg_rest, ".1f")}경기')

            # 전후반기 비교
            mid = len(p_records) // 2
            if mid > 2:
                first_half = p_records.iloc[:mid]
                second_half = p_records.iloc[mid:]
                fh_era = _group_era(first_half)
                sh_era = _group_era(second_half)
                diff = sh_era - fh_era
                label = '상승(피로 징후)' if diff > 0.5 else '안정'
                findings.append(f'    전반기 ERA {_safe(fh_era)} → 후반기 ERA {_safe(sh_era)} ({label})')
        else:
            findings.append(f'\n  {pname} — 등판 {appearances}회, ERA {_safe(season_era)}')

    # 차트: 주요 투수 전후반기 ERA 비교
    fh_eras = []
    sh_eras = []
    pitcher_names = []
    for _, pitcher in top_pitchers.iterrows():
        pid = pitcher['playerId']
        pname = pitcher.get('playerName', '?')
        p_records = team_records[team_records['playerId'] == pid]
        if len(p_records) < 4:
            continue
        mid = len(p_records) // 2
        first_half = p_records.iloc[:mid]
        second_half = p_records.iloc[mid:]
        fh_ip = first_half['innings'].sum()
        fh_er = first_half['earnedRuns'].sum()
        sh_ip = second_half['innings'].sum()
        sh_er = second_half['earnedRuns'].sum()
        fh_era = (fh_er / fh_ip * 9) if fh_ip > 0 else 0
        sh_era = (sh_er / sh_ip * 9) if sh_ip > 0 else 0
        pitcher_names.append(pname)
        fh_eras.append(round(fh_era, 2))
        sh_eras.append(round(sh_era, 2))

    if pitcher_names:
        charts.append(ChartBuilder.bar(
            f'{team_name} 주요 투수 전후반기 ERA 비교',
            pitcher_names,
            [
                {'label': '전반기 ERA', 'data': fh_eras, 'backgroundColor': '#2563EB'},
                {'label': '후반기 ERA', 'data': sh_eras, 'backgroundColor': '#DC2626'},
            ]
        ))

    return findings, charts


# ==================== 기사 생성 & DB 저장 ====================

def build_team_card_html(team_name, rank=None):
    rank_str = f' | {rank}위' if rank else ''
    return (
        f'<div style="background:#f8f9fa;border-radius:8px;padding:12px 16px;'
        f'margin-bottom:12px;border-left:4px solid #0f3460;">'
        f'<strong>{team_name}</strong>'
        f'<span style="color:#6c757d;">{rank_str}</span>'
        f'</div>'
    )


def save_column(db, title, content, chart_json, summary,
                category, team_id=None, season=SEASON):
    cursor = db.conn.cursor()

    cursor.execute(
        "SELECT id FROM analysis_column WHERE title = %s AND season = %s",
        (title, season)
    )
    existing = cursor.fetchone()

    if existing:
        cursor.execute("""
            UPDATE analysis_column
            SET content = %s, chartData = %s, summary = %s,
                publishDate = NOW(), autoGenerated = TRUE
            WHERE id = %s
        """, (content, chart_json, summary, existing[0]))
        column_id = existing[0]
        action = "UPDATE"
    else:
        cursor.execute("""
            INSERT INTO analysis_column
                (title, content, category, relatedTeamId, season,
                 chartData, publishDate, viewCount, autoGenerated, summary, featured)
            VALUES (%s, %s, %s, %s, %s, %s, NOW(), 0, TRUE, %s, FALSE)
        """, (title, content, category, team_id, season, chart_json, summary))
        column_id = cursor.lastrowid
        action = "INSERT"

    db.conn.commit()
    cursor.close()
    return column_id, action


# ==================== 메인 ====================

def main():
    parser = argparse.ArgumentParser(description='고급 분석 컬럼 생성기')
    parser.add_argument('--analysis', type=str, default=None,
                        help='분석 지정 (쉼표 구분, 예: a1,a3)')
    parser.add_argument('--dry-run', action='store_true',
                        help='미리보기만 (DB 저장 안함)')
    args = parser.parse_args()

    if args.analysis:
        target_keys = [k.strip().lower() for k in args.analysis.split(',')]
    else:
        target_keys = sorted(ANALYSIS_META.keys())

    db = DBConnector()
    try:
        # 순위 로드
        rankings = db.get_team_rankings(SEASON)
        ranking_map = {}
        team_list = []
        if not rankings.empty:
            ranking_map = {int(r['teamId']): int(r['ranking']) for _, r in rankings.iterrows()}
            team_list = [(int(r['teamId']), r['teamName'], int(r['ranking']))
                         for _, r in rankings.sort_values('ranking').iterrows()]
            print(f"\n  [순위] {SEASON} 시즌 {len(team_list)}팀 로딩 완료")
        else:
            print(f"\n  [경고] 순위 데이터 없음")
            return

        # 데이터 사전 로드 (분석에 필요한 것들)
        print("  [데이터] 로딩 중...")
        batters_all = db.get_batters(SEASON)
        batters_multi = db.get_batters_multi_season(SEASONS[-3:])
        pitchers_season = db.get_pitchers(SEASON)
        pitcher_records = db.get_pitcher_records(SEASON)
        team_stats = db.get_team_stats(SEASON)

        # 생년월일 (Marcel 노화 곡선용)
        birth_df = db.get_players_with_birth(SEASON)
        player_births = None
        if not birth_df.empty and 'birthDate' in birth_df.columns:
            player_births = birth_df.set_index('playerId')['birthDate']

        print(f"  [데이터] 타자 {len(batters_all)}명 | 투수 {len(pitchers_season)}명 | 팀 {len(team_stats)}개\n")

        # 분석 함수 매핑 → (findings, charts) 튜플 반환
        def run_analysis(key, tid, tname):
            if key == 'a1':
                return analyze_clutch(db, tid, tname, batters_all)
            elif key == 'a2':
                return analyze_marcel(db, tid, tname, batters_multi, player_births)
            elif key == 'a3':
                return analyze_similarity(db, tid, tname, batters_all)
            elif key == 'a4':
                return analyze_win_prediction(db, tid, tname, team_stats, rankings)
            elif key == 'a5':
                return analyze_fatigue(db, tid, tname, pitcher_records, pitchers_season)
            return [], []

        print("=" * 70)
        print("  고급 분석 컬럼 생성 (팀별 개별 기사)")
        print("=" * 70)

        results = []
        for key in target_keys:
            meta = ANALYSIS_META.get(key, {})
            print(f"\n  [{key}] {meta.get('title_template', key).format(teams='...')} ({len(team_list)}팀)")

            for tid, tname, rank in team_list:
                print(f"\n    [{tname} ({rank}위)]")

                # 분석 실행
                findings, analysis_charts = run_analysis(key, tid, tname)
                if not findings:
                    print(f"      -> 데이터 부족, 건너뜀")
                    continue

                # 기사 생성용 데이터 구성
                base_title = meta.get('title_template', '{teams} 분석').format(teams=tname)
                title = f"[{PREDICT_SEASON} 시즌 전망] {base_title}"

                entries_data = [{
                    'name': tname,
                    'card_html': build_team_card_html(tname, rank),
                    'findings': findings,
                    'charts': analysis_charts,
                }]

                content_html, all_charts, ai_summary = generate_article(
                    key, entries_data, meta,
                    data_season=SEASON, predict_season=PREDICT_SEASON
                )

                chart_json_str = json.dumps(all_charts, ensure_ascii=False) if all_charts else None

                summary = ai_summary or \
                    f'{SEASON} 시즌 데이터 기반 {tname}의 {PREDICT_SEASON} 시즌 {meta.get("subtitle", "전망")}.'

                chart_count = 0
                if chart_json_str:
                    try:
                        chart_count = len(json.loads(chart_json_str))
                    except Exception:
                        pass

                print(f"      제목: {title}")
                print(f"      본문: {len(content_html)}자 | 차트: {chart_count}개")

                if args.dry_run:
                    print(f"      [dry-run] DB 저장 건너뜀")
                    results.append((key, tname, 'dry-run', 0))
                else:
                    column_id, action = save_column(
                        db, title, content_html, chart_json_str, summary,
                        meta.get('category', 'player'), tid
                    )
                    print(f"      -> {action} (column_id: {column_id})")
                    results.append((key, tname, action, column_id))

        # 결과 요약
        print("\n" + "=" * 70)
        print("  고급 분석 컬럼 생성 결과")
        print("=" * 70)
        for key, name, action, cid in results:
            status = action if action == 'dry-run' else f'{action} (ID:{cid})'
            print(f"    [{key}] {name}: {status}")
        print(f"\n  총 {len(results)}건 {'미리보기' if args.dry_run else '저장'} 완료")
        print("=" * 70 + "\n")

    finally:
        db.close()


if __name__ == '__main__':
    main()
