"""
경기 분석 공통 헬퍼.
경기유형 분류, 이닝 파싱, 승패 판정 등.
"""
import os
import sys
import json
import numpy as np
import pandas as pd

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'output')

# team_common 재사용
from team.team_common import (
    TEAM_NAMES, get_team_name, safe_float,
    print_section, print_stat, LATEST_SEASON,
    normalize_series, normalize_series_inverse,
)

# ==================== 경기유형 ====================

GAME_TYPES = {
    'all': '전체',
    'regular': '정규시즌',
    'preseason': '시범경기',
    'postseason': '포스트시즌',
}


def get_game_type_label(game_type: str) -> str:
    """경기유형 한글 라벨"""
    return GAME_TYPES.get(game_type, game_type)


def classify_game_type(game_date) -> str:
    """날짜 기반 경기유형 분류.
    - 3월 22일 이전: preseason
    - 10월 11일 이후: postseason
    - 나머지: regular
    """
    if isinstance(game_date, str):
        game_date = pd.to_datetime(game_date)
    month = game_date.month
    day = game_date.day

    if month < 3:
        return 'preseason'
    elif month == 3 and day <= 22:
        return 'preseason'
    elif month >= 11:
        return 'postseason'
    elif month == 10 and day >= 11:
        return 'postseason'
    else:
        return 'regular'


def filter_schedules_by_type(schedules: pd.DataFrame, game_type: str = 'all') -> pd.DataFrame:
    """경기유형으로 schedule DataFrame 필터"""
    if schedules.empty:
        return schedules
    df = schedules.copy()
    df['gameType'] = df['gameDate'].apply(classify_game_type)
    if game_type == 'all':
        return df
    return df[df['gameType'] == game_type].reset_index(drop=True)


# ==================== 파싱 ====================

def parse_inning_scores(inning_str) -> list:
    """'1,0,2,0,0,1,0,0,0' → [1, 0, 2, 0, 0, 1, 0, 0, 0]"""
    if not inning_str or pd.isna(inning_str):
        return []
    try:
        return [int(x.strip()) for x in str(inning_str).split(',') if x.strip()]
    except (ValueError, AttributeError):
        return []


def parse_game_time(time_str) -> int:
    """'2:51' → 171 (분)"""
    if not time_str or pd.isna(time_str):
        return 0
    try:
        parts = str(time_str).split(':')
        if len(parts) == 2:
            return int(parts[0]) * 60 + int(parts[1])
    except (ValueError, IndexError):
        pass
    return 0


# ==================== 팀 경기 필터 ====================

def get_team_games(schedules: pd.DataFrame, team_id: int) -> pd.DataFrame:
    """특정 팀의 홈+원정 경기 필터 + 승/패/무/teamScore/opponentScore 컬럼 추가"""
    if schedules.empty:
        return schedules

    home = schedules[schedules['homeTeamId'] == team_id].copy()
    home['isHome'] = True
    home['teamScore'] = home['homeScore']
    home['opponentScore'] = home['awayScore']
    home['opponentTeamId'] = home['awayTeamId']

    away = schedules[schedules['awayTeamId'] == team_id].copy()
    away['isHome'] = False
    away['teamScore'] = away['awayScore']
    away['opponentScore'] = away['homeScore']
    away['opponentTeamId'] = away['homeTeamId']

    combined = pd.concat([home, away], ignore_index=True)
    if combined.empty:
        return combined

    def calc_result(row):
        ts = safe_float(row['teamScore'])
        os_val = safe_float(row['opponentScore'])
        if ts > os_val:
            return '승'
        elif ts < os_val:
            return '패'
        else:
            return '무'

    combined['result'] = combined.apply(calc_result, axis=1)
    combined = combined.sort_values('gameDate').reset_index(drop=True)
    return combined


# ==================== 출력 ====================

def save_chart_json(filename: str, charts: list):
    """output/ 디렉토리에 Chart.js JSON 저장"""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    filepath = os.path.join(OUTPUT_DIR, filename)
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(charts, f, ensure_ascii=False, indent=2, default=str)
    print(f"\n  -> 차트 JSON 저장: {filepath}")


def print_header(title: str, team_name: str, game_type: str = 'regular'):
    """콘솔 헤더 출력 (경기유형 포함)"""
    type_label = get_game_type_label(game_type)
    print("=" * 60)
    print(f"  {title}")
    print(f"  팀: {team_name} | 경기유형: {type_label}")
    print("=" * 60)


def output_filename(prefix: str, team_name: str, season: int, game_type: str) -> str:
    """출력 파일명 생성"""
    return f"{prefix}_{team_name}_{season}_{game_type}.json"


def filter_score_boards(score_boards: pd.DataFrame, schedule_ids: set) -> pd.DataFrame:
    """유효한 scheduleId로 score_boards 필터"""
    if score_boards.empty:
        return score_boards
    return score_boards[score_boards['scheduleId'].isin(schedule_ids)].copy()
