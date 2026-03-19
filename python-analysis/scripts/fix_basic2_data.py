"""
KBO 타자 Basic2/Detail1 시즌 선택 버그 — 손상 데이터 삭제 스크립트

문제: KboPlayerStatsCrawler의 selectSeasonDropdown()에서 ASP.NET PostBack 후
드롭다운 값이 실제로 반영되지 않아, Basic2/Detail1 카테고리가 모든 시즌에
2025 시즌 데이터로 동일하게 저장됨.

동작:
1. 영향받은 선수 자동 탐지 (3시즌 이상 동일값 + 비율 지표)
2. 해당 선수들의 Basic2+Detail1 카테고리 중 2020~2024 시즌분 삭제
3. 삭제 건수 리포트 출력

사용법:
    cd python-analysis
    python scripts/fix_basic2_data.py              # dry-run (삭제 안 함)
    python scripts/fix_basic2_data.py --execute    # 실제 삭제
"""
import sys
import os

# python-analysis 디렉토리를 모듈 경로에 추가
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

import mysql.connector
from config import DB_CONFIG

# Basic2 전용 카테고리 (Basic1과 겹치지 않는 것들)
BASIC2_CATEGORIES = ['BB', 'IBB', 'HBP', 'SO', 'GDP', 'SLG', 'OBP', 'OPS', 'MH', 'RISP', 'PH-BA']

# Detail1 전용 카테고리
DETAIL1_CATEGORIES = ['XBH', 'GO', 'AO', 'GO/AO', 'GW RBI', 'BB/K', 'P/PA', 'ISOP', 'XR', 'GPA']

# 비율 지표 (시즌마다 달라야 정상인 지표) — 탐지용
RATIO_CATEGORIES = ['SLG', 'OBP', 'OPS', 'ISOP', 'GPA', 'BB/K', 'P/PA', 'GO/AO']

# 손상 가능성이 높은 시즌 범위
AFFECTED_SEASONS = [2020, 2021, 2022, 2023, 2024]

# 정상 시즌 (비교 기준)
REFERENCE_SEASON = 2025


def detect_affected_players(cursor):
    """3시즌 이상 동일한 비율 지표 값을 가진 선수 탐지"""
    affected_players = set()

    for cat in RATIO_CATEGORIES:
        sql = """
            SELECT playerId, COUNT(DISTINCT season) AS season_count,
                   COUNT(DISTINCT value) AS distinct_values,
                   GROUP_CONCAT(DISTINCT CONCAT(season, ':', value) ORDER BY season) AS detail
            FROM player_batter_stats
            WHERE category = %s
              AND series = '0'
              AND (situationType = '' OR situationType IS NULL)
              AND season BETWEEN 2020 AND 2025
            GROUP BY playerId
            HAVING season_count >= 3 AND distinct_values = 1
        """
        cursor.execute(sql, (cat,))
        rows = cursor.fetchall()

        for row in rows:
            player_id = row[0]
            season_count = row[1]
            detail = row[3]
            affected_players.add(player_id)
            print(f"  [탐지] playerId={player_id}, category={cat}, "
                  f"{season_count}시즌 동일값: {detail}")

    return affected_players


def count_affected_rows(cursor, player_ids):
    """삭제 대상 행 수 카운트"""
    if not player_ids:
        return 0

    all_categories = BASIC2_CATEGORIES + DETAIL1_CATEGORIES
    placeholders_players = ','.join(['%s'] * len(player_ids))
    placeholders_cats = ','.join(['%s'] * len(all_categories))
    placeholders_seasons = ','.join(['%s'] * len(AFFECTED_SEASONS))

    sql = f"""
        SELECT COUNT(*) FROM player_batter_stats
        WHERE playerId IN ({placeholders_players})
          AND category IN ({placeholders_cats})
          AND season IN ({placeholders_seasons})
          AND series = '0'
          AND (situationType = '' OR situationType IS NULL)
    """
    params = list(player_ids) + all_categories + AFFECTED_SEASONS
    cursor.execute(sql, params)
    return cursor.fetchone()[0]


def delete_affected_rows(cursor, player_ids):
    """손상 데이터 삭제 실행"""
    if not player_ids:
        return 0

    all_categories = BASIC2_CATEGORIES + DETAIL1_CATEGORIES
    placeholders_players = ','.join(['%s'] * len(player_ids))
    placeholders_cats = ','.join(['%s'] * len(all_categories))
    placeholders_seasons = ','.join(['%s'] * len(AFFECTED_SEASONS))

    sql = f"""
        DELETE FROM player_batter_stats
        WHERE playerId IN ({placeholders_players})
          AND category IN ({placeholders_cats})
          AND season IN ({placeholders_seasons})
          AND series = '0'
          AND (situationType = '' OR situationType IS NULL)
    """
    params = list(player_ids) + all_categories + AFFECTED_SEASONS
    cursor.execute(sql, params)
    return cursor.rowcount


def print_verification(cursor, sample_player_name='구자욱'):
    """수정 후 검증: 특정 선수의 시즌별 OBP/SLG/OPS 확인"""
    sql = """
        SELECT bs.season, bs.category, bs.value
        FROM player_batter_stats bs
        JOIN player p ON bs.playerId = p.id
        WHERE p.name = %s
          AND bs.category IN ('OBP', 'SLG', 'OPS')
          AND bs.series = '0'
          AND (bs.situationType = '' OR bs.situationType IS NULL)
        ORDER BY bs.season, bs.category
    """
    cursor.execute(sql, (sample_player_name,))
    rows = cursor.fetchall()

    if not rows:
        print(f"\n  [{sample_player_name}] 데이터 없음")
        return

    print(f"\n  [{sample_player_name}] 시즌별 OBP/SLG/OPS:")
    current_season = None
    for season, cat, value in rows:
        if season != current_season:
            current_season = season
            print(f"    {season}시즌:", end='')
        print(f" {cat}={value}", end='')
    print()


def main():
    execute = '--execute' in sys.argv

    print("=" * 60)
    print("KBO 타자 Basic2/Detail1 손상 데이터 수정 스크립트")
    print("=" * 60)
    print(f"모드: {'실제 삭제' if execute else 'DRY-RUN (삭제 안 함)'}")
    print(f"대상 카테고리: {len(BASIC2_CATEGORIES)}개 (Basic2) + {len(DETAIL1_CATEGORIES)}개 (Detail1)")
    print(f"대상 시즌: {AFFECTED_SEASONS}")
    print()

    conn = mysql.connector.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 1단계: 영향받은 선수 탐지
        print("[1단계] 손상 데이터 자동 탐지 (비율 지표 기준)")
        affected = detect_affected_players(cursor)

        if not affected:
            print("\n  손상 데이터가 탐지되지 않았습니다.")
            return

        print(f"\n  영향받은 선수: {len(affected)}명")
        print(f"  Player IDs: {sorted(affected)}")

        # 2단계: 삭제 대상 카운트
        row_count = count_affected_rows(cursor, affected)
        print(f"\n[2단계] 삭제 대상: {row_count}건")

        # 수정 전 검증
        print("\n[검증] 삭제 전 상태:")
        print_verification(cursor)

        # 3단계: 삭제 실행
        if execute:
            deleted = delete_affected_rows(cursor, affected)
            conn.commit()
            print(f"\n[3단계] 삭제 완료: {deleted}건")

            # 수정 후 검증
            print("\n[검증] 삭제 후 상태:")
            print_verification(cursor)
        else:
            print(f"\n[3단계] DRY-RUN — 실제 삭제하려면 --execute 옵션 추가")
            print(f"  python scripts/fix_basic2_data.py --execute")

        # 리포트
        print("\n" + "=" * 60)
        print("리포트 요약")
        print("=" * 60)
        print(f"  영향받은 선수: {len(affected)}명")
        print(f"  삭제 대상 행: {row_count}건")
        print(f"  대상 카테고리: {BASIC2_CATEGORIES + DETAIL1_CATEGORIES}")
        print(f"  대상 시즌: {AFFECTED_SEASONS}")
        if execute:
            print(f"  실제 삭제: {deleted}건")
        print("\n  다음 단계: crawl.batterOnly=true 로 재크롤링 실행")

    finally:
        cursor.close()
        conn.close()


if __name__ == '__main__':
    main()
