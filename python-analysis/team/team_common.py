"""
팀 분석 공통 헬퍼.
팀 정보 조회, 정규화, JSON 저장 등.
"""
import os
import sys
import json
import numpy as np

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'output')

from config import DATA_SEASON, PREDICT_SEASON, SEASONS as _CFG_SEASONS

SEASONS = _CFG_SEASONS
LATEST_SEASON = DATA_SEASON

TEAM_NAMES = {
    1: 'KIA', 2: '두산', 3: '삼성', 4: 'SSG', 5: 'LG',
    6: '한화', 7: 'NC', 8: 'KT', 9: '롯데', 10: '키움',
}


# ==================== 팀 정보 ====================

def get_team_name(team_id: int) -> str:
    """팀 ID → 팀명"""
    return TEAM_NAMES.get(team_id, f'ID:{team_id}')


def get_team_info(db, team_id: int) -> dict:
    """팀 기본 정보 조회"""
    sql = "SELECT id AS teamId, name AS teamName FROM team WHERE id = %s"
    df = db.query(sql, (team_id,))
    if df.empty:
        raise ValueError(f"팀 ID {team_id}에 해당하는 팀을 찾을 수 없습니다.")
    return df.iloc[0].to_dict()


# ==================== 출력 ====================

def save_chart_json(filename: str, charts: list):
    """output/ 디렉토리에 Chart.js JSON 저장"""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    filepath = os.path.join(OUTPUT_DIR, filename)
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(charts, f, ensure_ascii=False, indent=2, default=str)
    print(f"\n  -> 차트 JSON 저장: {filepath}")


def save_analysis_output(filename: str, charts: list,
                         findings: list = None, stats_dict: dict = None):
    """차트 + 인사이트를 통합 저장"""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    filepath = os.path.join(OUTPUT_DIR, filename)
    data = {
        'charts': charts,
        'findings': findings or [],
        'stats': stats_dict or {},
    }
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2, default=str)
    print(f"\n  -> 분석 JSON 저장: {filepath}")


def print_header(title: str, team_name: str):
    """콘솔 헤더 출력"""
    print("=" * 60)
    print(f"  {title}")
    print(f"  팀: {team_name}")
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


def normalize_for_radar(values: list, max_val=None) -> list:
    """레이더 차트용 0~100 정규화"""
    if max_val is None:
        max_val = max(abs(v) for v in values) if values else 1
    if max_val == 0:
        max_val = 1
    return [round(v / max_val * 100, 1) for v in values]


def normalize_series(values: list) -> list:
    """0~100 정규화 (퍼센타일 기반 + min-max 혼합).
    범위가 극히 좁은 지표(FPCT 등)에서 min-max가 0/100 극단값을 만드는 문제를 방지.
    상대순위 기반이므로 최소값도 적정 점수를 받는다."""
    if not values:
        return []
    n = len(values)
    min_v = min(values)
    max_v = max(values)
    rng = max_v - min_v

    if rng == 0:
        return [50.0] * n

    # 범위가 충분하면 min-max, 좁으면 퍼센타일 기반
    # 기준: 비율 범위(max/min - 1)가 5% 미만이면 좁은 범위로 판단
    ratio = rng / max(abs(min_v), 1e-9)
    if ratio < 0.05:
        # 퍼센타일 기반: 순위에 따라 10~100 분배 (최하위도 10점 보장)
        ranked = sorted(range(n), key=lambda i: values[i])
        result = [0.0] * n
        for rank_pos, idx in enumerate(ranked):
            result[idx] = round(10 + (rank_pos / max(n - 1, 1)) * 90, 1)
        return result
    else:
        return [round((v - min_v) / rng * 100, 1) for v in values]


def normalize_series_inverse(values: list) -> list:
    """0~100 정규화 역순 (낮을수록 높은 점수).
    ERA 등 낮을수록 좋은 지표용."""
    if not values:
        return []
    n = len(values)
    min_v = min(values)
    max_v = max(values)
    rng = max_v - min_v

    if rng == 0:
        return [50.0] * n

    ratio = rng / max(abs(min_v), 1e-9)
    if ratio < 0.05:
        ranked = sorted(range(n), key=lambda i: values[i], reverse=True)
        result = [0.0] * n
        for rank_pos, idx in enumerate(ranked):
            result[idx] = round(10 + (rank_pos / max(n - 1, 1)) * 90, 1)
        return result
    else:
        return [round((max_v - v) / rng * 100, 1) for v in values]
