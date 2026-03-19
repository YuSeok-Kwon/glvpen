"""
규정타석/규정이닝 필터 모듈.
극소 타석/이닝 선수의 이상값(BABIP 1.0, AVG 1.0 등)을 제거하기 위한 공통 필터.

기준:
- 타자: PA >= 223 (KBO 144경기 × 3.1 × 0.5 = 규정타석 50%)
- 투수 선발(GS>0): IP >= 72 (144경기 × 1.0 × 0.5)
- 투수 불펜(GS==0): G >= median (상위 50%)
"""
import pandas as pd
from common.db_connector import DBConnector

# KBO 144경기 기준 상수
MIN_PA = 223           # 규정타석 50%
MIN_IP_STARTER = 72    # 규정이닝 50% (선발)


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


def filter_qualified_pitchers(df: pd.DataFrame, min_ip: int = MIN_IP_STARTER) -> pd.DataFrame:
    """
    선발투수: IP >= min_ip(기본 72), 불펜투수: G >= median(상위 50%) 필터.
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

    # 선발: IP >= min_ip
    if not starters.empty:
        starters = starters[starters['IP'] >= min_ip]

    # 불펜: G 상위 50%
    if not relievers.empty:
        g_median = relievers['G'].median()
        relievers = relievers[relievers['G'] >= g_median]

    filtered = pd.concat([starters, relievers], ignore_index=True)
    s_cnt = len(starters)
    r_cnt = len(relievers)
    print(f"    [필터] 투수: {before}명 → {len(filtered)}명 (선발 {s_cnt}[IP>={min_ip}] + 불펜 {r_cnt}[G>=median])")
    return filtered


def filter_batters_multi_season(df: pd.DataFrame, min_pa: int = MIN_PA) -> pd.DataFrame:
    """
    멀티시즌 타자 데이터에서 각 행(시즌별)의 PA >= min_pa인 행만 필터.
    season, playerId, PA 컬럼 필요.
    """
    if df.empty or 'PA' not in df.columns:
        return df
    df = df.copy()
    df['PA'] = pd.to_numeric(df['PA'], errors='coerce').fillna(0)
    before = len(df)
    filtered = df[df['PA'] >= min_pa].copy()
    print(f"    [필터] 멀티시즌 규정타석50%: {before}행 → {len(filtered)}행 (PA >= {min_pa})")
    return filtered


def filter_pitchers_multi_season(df: pd.DataFrame, min_ip: int = MIN_IP_STARTER) -> pd.DataFrame:
    """
    멀티시즌 투수 데이터에서 시즌별 선발/불펜 분리 후 필터.
    season, playerId, GS, IP, G 컬럼 필요.
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
    results = []

    for season in df['season'].unique():
        season_df = df[df['season'] == season]
        starters = season_df[season_df['GS'] > 0].copy()
        relievers = season_df[season_df['GS'] == 0].copy()

        if not starters.empty:
            starters = starters[starters['IP'] >= min_ip]
        if not relievers.empty:
            g_median = relievers['G'].median()
            relievers = relievers[relievers['G'] >= g_median]

        results.append(pd.concat([starters, relievers], ignore_index=True))

    filtered = pd.concat(results, ignore_index=True) if results else pd.DataFrame()
    print(f"    [필터] 멀티시즌 투수: {before}행 → {len(filtered)}행")
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
    if 'position' in merged.columns:
        merged['position'] = merged['real_position']
    else:
        merged['position'] = merged['real_position']
    merged = merged.drop(columns=['real_position'], errors='ignore')
    return merged
