"""
glvpen - Python 배치 분석 설정
"""
import os
from dotenv import load_dotenv

load_dotenv()

# ==================== DB 설정 ====================
DB_CONFIG = {
    'host': os.getenv('DB_HOST', 'localhost'),
    'port': int(os.getenv('DB_PORT', 3306)),
    'user': os.getenv('DB_USER', 'root'),
    'password': os.getenv('DB_PASSWORD', ''),
    'database': os.getenv('DB_NAME', 'glvpen'),
}

# ==================== Gemini API 설정 ====================
GEMINI_API_KEY = os.getenv('GEMINI_API_KEY', 'AIzaSyDlwCYL2w94-NKMTnVenSYKEXnpQg3e2wA')

# ==================== 분석 설정 ====================

def _detect_data_season() -> int:
    """DB에서 최신 시즌을 자동 감지. 실패 시 환경변수 또는 2025 사용."""
    fallback = int(os.getenv('DATA_SEASON', 2025))
    try:
        import mysql.connector
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        cursor.execute("SELECT MAX(season) FROM team_stats")
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        if row and row[0]:
            return int(row[0])
    except Exception:
        pass
    return fallback


# DATA_SEASON: DB에 있는 최신 시즌 (자동 감지)
# PREDICT_SEASON: 기사에서 전망하는 다음 시즌
DATA_SEASON = _detect_data_season()
PREDICT_SEASON = DATA_SEASON + 1

# SEASONS: 분석에 사용할 시즌 범위 (DATA_SEASON 기준 최근 6시즌)
SEASONS = list(range(DATA_SEASON - 5, DATA_SEASON + 1))

# ==================== 정규 분석 토픽 (0~14) ====================
# AiColumnGeneratorService.ROTATING_TOPICS 배열과 인덱스 매핑
ANALYSIS_TOPICS = {
    0: {'name': '세이버메트릭스 트렌드', 'sub_topics': ['default']},
    1: {'name': 'WAR 스포트라이트', 'sub_topics': ['default']},
    2: {'name': '브레이크아웃 후보', 'sub_topics': ['default']},
    3: {'name': '행운 보정 분석', 'sub_topics': ['default']},
    4: {'name': '상대전적 패턴', 'sub_topics': ['default']},
    5: {'name': '이닝별 득점 패턴', 'sub_topics': ['default']},
    6: {'name': '홈/원정 & 파크팩터', 'sub_topics': ['default']},
    7: {'name': '관중 동원 트렌드', 'sub_topics': ['default']},
    8: {'name': '퓨처스 유망주 스카우팅', 'sub_topics': ['batter', 'pitcher']},
    9: {'name': 'ABS 도입 영향 분석', 'sub_topics': ['batter', 'pitcher']},
    10: {'name': '클러치/초커 분석', 'sub_topics': ['default']},
    11: {'name': '나이-성과 커브', 'sub_topics': ['default']},
    12: {'name': '라인업 효율성 분석', 'sub_topics': ['default']},
    13: {'name': '월별/계절별 선수 유형', 'sub_topics': ['default']},
    14: {'name': '포지션별 가치 분석', 'sub_topics': ['default']},
}

# ==================== 특집 분석 토픽 (15~) ====================
SPECIAL_TOPICS = {
    15: {'name': '하드캐리 분석', 'sub_topics': ['default']},
    16: {'name': '심판 궁합 분석', 'sub_topics': ['default']},
    17: {'name': '정규 vs 포스트시즌', 'sub_topics': ['default']},
}

# ==================== WBC 토픽 (20) ====================
WBC_TOPICS = {
    20: {'name': '2026 WBC 한국 대표팀 분석', 'sub_topics': ['default']},
}

# 전체 토픽명 조회 (역호환용)
def get_topic_name(topic_id: int) -> str:
    """토픽 ID로 이름 조회"""
    all_topics = {**ANALYSIS_TOPICS, **SPECIAL_TOPICS, **WBC_TOPICS}
    return all_topics.get(topic_id, {}).get('name', f'미정의 토픽 {topic_id}')

# 유의수준 (가설검정 기준)
SIGNIFICANCE_LEVEL = 0.05

# 차트 기본 팔레트 (팀 색상 매칭 안 될 때 사용)
CHART_COLORS = [
    '#2563EB', '#DC2626', '#059669', '#D97706', '#7C3AED',
    '#0891B2', '#BE185D', '#4F46E5', '#0D9488', '#EA580C',
]

# 리그 평균 등 기준선 색상
BASELINE_COLOR = '#94A3B8'

# KBO 10개 구단 공식 색상 (메인 + 서브)
TEAM_COLORS = {
    'LG':   {'main': '#C30452', 'sub': '#000000', 'name': 'LG 트윈스'},
    '삼성': {'main': '#074CA1', 'sub': '#FFFFFF', 'name': '삼성 라이온즈'},
    'KIA':  {'main': '#EA0029', 'sub': '#000000', 'name': 'KIA 타이거즈'},
    'KT':   {'main': '#000000', 'sub': '#EB1C24', 'name': 'KT 위즈'},
    'SSG':  {'main': '#CE0E2D', 'sub': '#004B87', 'name': 'SSG 랜더스'},
    '롯데': {'main': '#041E42', 'sub': '#D00F31', 'name': '롯데 자이언츠'},
    '한화': {'main': '#FF6600', 'sub': '#1D1D1B', 'name': '한화 이글스'},
    '두산': {'main': '#131230', 'sub': '#ED1C24', 'name': '두산 베어스'},
    'NC':   {'main': '#315288', 'sub': '#C19B6C', 'name': 'NC 다이노스'},
    '키움': {'main': '#570514', 'sub': '#D4A76A', 'name': '키움 히어로즈'},
}

# 팀 색상 순서 (10팀 비교 차트용) — 색상이 겹치지 않도록 배치
TEAM_COLOR_ORDER = [
    '#074CA1', '#C30452', '#EA0029', '#FF6600', '#315288',
    '#131230', '#CE0E2D', '#570514', '#000000', '#041E42',
]


def get_team_color(name: str, fallback_idx: int = 0) -> str:
    """팀명(약칭/풀네임)에서 메인 색상 반환"""
    if not name:
        return CHART_COLORS[fallback_idx % len(CHART_COLORS)]
    for key, val in TEAM_COLORS.items():
        if key in name or val['name'] in name:
            return val['main']
    return CHART_COLORS[fallback_idx % len(CHART_COLORS)]


def get_team_sub_color(name: str, fallback_idx: int = 0) -> str:
    """팀명에서 서브 색상 반환"""
    if not name:
        return CHART_COLORS[fallback_idx % len(CHART_COLORS)]
    for key, val in TEAM_COLORS.items():
        if key in name or val['name'] in name:
            return val['sub']
    return CHART_COLORS[fallback_idx % len(CHART_COLORS)]
