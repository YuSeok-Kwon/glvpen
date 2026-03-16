"""
선수 개인 분석 공통 헬퍼.
선수 조회, 타자/투수 판별, 시즌 스탯 피봇, 경기별 기록 조회, JSON 저장 등.
"""
import os
import sys
import json
import numpy as np
import pandas as pd

# python-analysis/ 를 sys.path에 추가
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from common.db_connector import DBConnector

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'output')

SEASONS = [2020, 2021, 2022, 2023, 2024, 2025]


# ==================== 선수 정보 ====================

def get_player_info(db: DBConnector, player_id: int) -> dict:
    """선수 기본 정보 조회 (player + team JOIN)"""
    sql = """
        SELECT p.id AS playerId, p.name, p.position, p.birthDate,
               p.throwBat, p.backNumber, p.debutYear,
               t.name AS teamName, t.id AS teamId
        FROM player p
        JOIN team t ON p.teamId = t.id
        WHERE p.id = %s
    """
    df = db.query(sql, (player_id,))
    if df.empty:
        raise ValueError(f"선수 ID {player_id}에 해당하는 선수를 찾을 수 없습니다.")
    return df.iloc[0].to_dict()


def is_pitcher(info: dict) -> bool:
    """투수 여부 판별"""
    return info.get('position', '') == '투수'


# ==================== 데이터 조회 ====================

def get_player_season_stats(db: DBConnector, player_id: int,
                            seasons: list, table: str) -> pd.DataFrame:
    """
    특정 선수의 다시즌 스탯을 피봇 변환하여 반환.
    table: 'player_batter_stats' 또는 'player_pitcher_stats'
    반환: season | AVG | OPS | HR | ... 형태의 DataFrame
    """
    placeholders = ','.join(['%s'] * len(seasons))
    sql = f"""
        SELECT season, category, value
        FROM {table}
        WHERE playerId = %s
          AND season IN ({placeholders})
          AND series = '0'
          AND (situationType = '' OR situationType IS NULL)
    """
    params = (player_id, *seasons)
    raw = db.query(sql, params)
    if raw.empty:
        return pd.DataFrame()

    pivoted = raw.pivot_table(
        index='season', columns='category', values='value', aggfunc='first'
    ).reset_index()
    return pivoted


def get_player_game_records(db: DBConnector, player_id: int,
                            table: str) -> pd.DataFrame:
    """
    특정 선수의 경기별 기록 조회 (kbo_schedule JOIN).
    table: 'kbo_batter_record' 또는 'kbo_pitcher_record'
    """
    sql = f"""
        SELECT r.*, s.matchDate, s.homeTeamId, s.awayTeamId,
               s.homeTeamScore, s.awayTeamScore, s.stadium
        FROM {table} r
        JOIN kbo_schedule s ON r.scheduleId = s.id
        WHERE r.playerId = %s
        ORDER BY s.matchDate ASC
    """
    return db.query(sql, (player_id,))


def get_runner_stats(db: DBConnector, player_id: int,
                     season: int) -> pd.DataFrame:
    """주루 기록 조회 (player_runner_stats)"""
    sql = """
        SELECT category, value
        FROM player_runner_stats
        WHERE playerId = %s AND season = %s AND series = '0'
    """
    return db.query(sql, (player_id, season))


# ==================== 출력 ====================

def save_chart_json(filename: str, charts: list):
    """output/ 디렉토리에 Chart.js JSON 저장"""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    filepath = os.path.join(OUTPUT_DIR, filename)
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(charts, f, ensure_ascii=False, indent=2, default=str)
    print(f"\n  -> 차트 JSON 저장: {filepath}")


def print_header(title: str, info: dict):
    """콘솔 헤더 출력"""
    print("=" * 60)
    print(f"  {title}")
    print(f"  선수: {info.get('name', '?')} ({info.get('teamName', '?')}) "
          f"#{info.get('backNumber', '?')}")
    print(f"  포지션: {info.get('position', '?')} | "
          f"투타: {info.get('throwBat', '?')}")
    print("=" * 60)


def print_section(title: str):
    """섹션 구분선"""
    print(f"\n  --- {title} ---")


def print_stat(label: str, value):
    """지표 출력"""
    if isinstance(value, float):
        print(f"    {label}: {value:.4f}")
    else:
        print(f"    {label}: {value}")


# ==================== 유틸 ====================

def safe_float(val, default=0.0):
    """안전한 float 변환"""
    try:
        v = float(val)
        return v if not np.isnan(v) else default
    except (TypeError, ValueError):
        return default


def pick_available(df: pd.DataFrame, candidates: list) -> list:
    """DataFrame 컬럼 중 존재하는 지표만 반환"""
    return [c for c in candidates if c in df.columns]


def normalize_for_radar(values: list) -> list:
    """레이더 차트용 0~100 정규화"""
    max_val = max(abs(v) for v in values) if values else 1
    if max_val == 0:
        max_val = 1
    return [round(v / max_val * 100, 1) for v in values]


# ==================== 규정 필터 ====================

# KBO 144경기 기준
MIN_PA = 223       # 규정타석 50% (144 × 3.1 × 0.5)


def filter_qualified_batters(df: pd.DataFrame, min_pa: int = MIN_PA) -> pd.DataFrame:
    """규정타석 50% 충족 타자만 필터. PA 컬럼 필요."""
    if df.empty or 'PA' not in df.columns:
        return df
    df = df.copy()
    df['PA'] = pd.to_numeric(df['PA'], errors='coerce').fillna(0)
    before = len(df)
    filtered = df[df['PA'] >= min_pa].copy()
    print(f"    [필터] 규정타석50%: {before}명 → {len(filtered)}명 (PA >= {min_pa})")
    return filtered


def filter_qualified_pitchers(df: pd.DataFrame) -> pd.DataFrame:
    """
    선발투수: IP 상위 50%, 불펜투수: G 상위 50% 필터.
    GS, IP, G 컬럼 필요.
    """
    if df.empty:
        return df
    df = df.copy()

    for c in ['GS', 'IP', 'G']:
        if c in df.columns:
            df[c] = pd.to_numeric(df[c], errors='coerce').fillna(0)
        else:
            df[c] = 0

    before = len(df)

    # 선발(GS > 0) / 불펜(GS == 0) 분리
    starters = df[df['GS'] > 0].copy()
    relievers = df[df['GS'] == 0].copy()

    # 선발: IP 상위 50%
    if not starters.empty:
        ip_median = starters['IP'].median()
        starters = starters[starters['IP'] >= ip_median]

    # 불펜: G 상위 50%
    if not relievers.empty:
        g_median = relievers['G'].median()
        relievers = relievers[relievers['G'] >= g_median]

    filtered = pd.concat([starters, relievers], ignore_index=True)
    s_cnt = len(starters)
    r_cnt = len(relievers)
    print(f"    [필터] 투수: {before}명 → {len(filtered)}명 (선발 {s_cnt} + 불펜 {r_cnt})")
    return filtered


def merge_real_position(db: DBConnector, df: pd.DataFrame) -> pd.DataFrame:
    """
    batter_stats의 position(전부 'DH')을 player 테이블의 실제 포지션으로 교체.
    df에 playerId 컬럼이 있어야 한다.
    """
    if df.empty or 'playerId' not in df.columns:
        return df
    pos_sql = "SELECT id AS playerId, position AS real_position FROM player"
    pos_df = db.query(pos_sql)
    merged = df.merge(pos_df, on='playerId', how='left')
    # 기존 position 컬럼을 실제 포지션으로 교체
    if 'position' in merged.columns:
        merged['position'] = merged['real_position']
    else:
        merged['position'] = merged['real_position']
    merged = merged.drop(columns=['real_position'], errors='ignore')
    return merged
