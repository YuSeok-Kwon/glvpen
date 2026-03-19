"""
선수 분석 일괄 실행기.
DB 기반 자동 추천 선수로 p1~p9 분석을 일괄 실행.

사용법:
  python3 player/run_all.py                    # 전체 분석 (9 x 3명)
  python3 player/run_all.py --analysis p1,p3   # 특정 분석만
  python3 player/run_all.py --dry-run          # 추천만 출력, 실행 안함
  python3 player/run_all.py --player p1:42     # 특정 분석에 선수 수동 지정
  python3 player/run_all.py --history          # 이전 실행 이력 조회
  python3 player/run_all.py --reset-history    # 이력 초기화
"""
import sys
import os
import argparse
import time
import traceback

# python-analysis/ 를 sys.path에 추가
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from common.db_connector import DBConnector
from player.recommend import PlayerRecommender, HISTORY_PATH
from player import (
    p1_season_trend,
    p2_sabermetrics_profile,
    p3_batter_type,
    p4_pitcher_type,
    p5_age_performance,
    p6_game_volatility,
    p7_position_offense,
    p8_throw_bat_split,
    p9_babip_luck,
)

# 분석 모듈 매핑
MODULES = {
    'p1': p1_season_trend,
    'p2': p2_sabermetrics_profile,
    'p3': p3_batter_type,
    'p4': p4_pitcher_type,
    'p5': p5_age_performance,
    'p6': p6_game_volatility,
    'p7': p7_position_offense,
    'p8': p8_throw_bat_split,
    'p9': p9_babip_luck,
}

ANALYSIS_NAMES = {
    'p1': '시즌 트렌드',
    'p2': '세이버메트릭스 프로필',
    'p3': '타자 유형 분류',
    'p4': '투수 유형 분류',
    'p5': '나이-성과 커브',
    'p6': '경기별 변동성',
    'p7': '포지션별 공격력',
    'p8': '투타 유형별 성적',
    'p9': 'BABIP 행운 분석',
}


def parse_args():
    parser = argparse.ArgumentParser(
        description='선수 분석 일괄 실행기',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예시:
  python3 player/run_all.py --dry-run          추천만 확인
  python3 player/run_all.py --analysis p1,p3   특정 분석만 실행
  python3 player/run_all.py --player p1:42     p1에 선수 ID 42 수동 지정
  python3 player/run_all.py --history          실행 이력 조회
  python3 player/run_all.py --reset-history    이력 초기화 후 실행
        """
    )
    parser.add_argument('--analysis', type=str, default=None,
                        help='실행할 분석 (쉼표 구분, 예: p1,p3,p9)')
    parser.add_argument('--dry-run', action='store_true',
                        help='추천만 출력하고 분석 실행하지 않음')
    parser.add_argument('--player', action='append', default=[],
                        help='특정 분석에 선수 수동 지정 (예: p1:42)')
    parser.add_argument('--history', action='store_true',
                        help='이전 실행 이력 조회')
    parser.add_argument('--reset-history', action='store_true',
                        help='이력 초기화')
    return parser.parse_args()


def parse_manual_players(player_args: list) -> dict:
    """--player p1:42 형태의 인자를 파싱 → {분석키: [선수ID]}"""
    manual = {}
    for arg in player_args:
        if ':' not in arg:
            print(f"  [경고] 잘못된 형식: {arg} (예: p1:42)")
            continue
        key, pid_str = arg.split(':', 1)
        key = key.strip().lower()
        if key not in MODULES:
            print(f"  [경고] 알 수 없는 분석: {key}")
            continue
        try:
            pid = int(pid_str.strip())
            manual.setdefault(key, []).append(pid)
        except ValueError:
            print(f"  [경고] 잘못된 선수 ID: {pid_str}")
    return manual


def print_recommendation_table(recommendations: dict):
    """추천 결과를 테이블 형태로 출력"""
    print("\n" + "=" * 80)
    print("  선수 자동 추천 결과")
    print("=" * 80)

    total = 0
    unique_players = set()
    for key in sorted(recommendations.keys()):
        recs = recommendations[key]
        name = ANALYSIS_NAMES.get(key, key)
        print(f"\n  [{key}] {name}")
        print(f"  {'─' * 70}")
        if not recs:
            print(f"    (추천 선수 없음)")
            continue
        for i, (pid, pname, reason) in enumerate(recs, 1):
            print(f"    {i}. [{pid:>4}] {pname:<10} → {reason}")
            unique_players.add(pid)
            total += 1
        print()

    print(f"  {'─' * 70}")
    print(f"  총 {total}건 추천 (고유 선수 {len(unique_players)}명)")
    print("=" * 80)


def print_history(recommender: PlayerRecommender):
    """실행 이력 조회"""
    history = recommender._history
    if not history:
        print("\n  실행 이력이 없습니다.")
        return

    print("\n" + "=" * 80)
    print("  실행 이력")
    print("=" * 80)

    for key in sorted(history.keys()):
        entries = history[key]
        name = ANALYSIS_NAMES.get(key, key)
        print(f"\n  [{key}] {name} — {len(entries)}명 분석 완료")
        for e in entries:
            print(f"    - [{e['player_id']:>4}] {e['player_name']:<10} ({e['run_date']})")

    total = sum(len(v) for v in history.values())
    print(f"\n  총 {total}건 완료")
    print("=" * 80)


def run_analysis(key: str, player_id: int, player_name: str,
                 recommender: PlayerRecommender) -> bool:
    """단일 분석 실행. 성공 시 True, 실패 시 False"""
    module = MODULES[key]
    analysis_name = ANALYSIS_NAMES.get(key, key)

    print(f"\n{'─' * 60}")
    print(f"  실행: [{key}] {analysis_name} — {player_name} (ID: {player_id})")
    print(f"{'─' * 60}")

    start = time.time()
    try:
        module.analyze(player_id)
        elapsed = time.time() - start
        print(f"\n  ✓ 완료 ({elapsed:.1f}초)")
        # 성공 시 이력 기록
        recommender.save_history(key, player_id, player_name)
        return True
    except Exception as e:
        elapsed = time.time() - start
        print(f"\n  ✗ 실패 ({elapsed:.1f}초): {e}")
        traceback.print_exc()
        return False


def main():
    args = parse_args()
    total_start = time.time()

    # DB 연결
    db = DBConnector()
    recommender = PlayerRecommender(db)

    try:
        # --reset-history: 이력 초기화
        if args.reset_history:
            if os.path.exists(HISTORY_PATH):
                os.remove(HISTORY_PATH)
                print("  [이력] 초기화 완료")
            recommender._history = {}

        # --history: 이력만 조회하고 종료
        if args.history:
            print_history(recommender)
            return

        # 대상 분석 결정
        if args.analysis:
            target_keys = [k.strip().lower() for k in args.analysis.split(',')]
            invalid = [k for k in target_keys if k not in MODULES]
            if invalid:
                print(f"  [오류] 알 수 없는 분석: {', '.join(invalid)}")
                print(f"  사용 가능: {', '.join(sorted(MODULES.keys()))}")
                return
        else:
            target_keys = sorted(MODULES.keys())

        # 수동 지정 선수 파싱
        manual = parse_manual_players(args.player)

        # 추천 생성
        print("\n" + "=" * 80)
        print("  선수 자동 추천 시스템")
        print("=" * 80)

        # 데이터 미리 로드 (수동 지정 시에도 이름 조회에 필요)
        recommender._load_all()

        # 이력 요약
        history_summary = recommender.get_history_summary()
        if history_summary:
            print(f"\n  [이력] 이전 실행: "
                  + ", ".join(f"{k}={v}명" for k, v in sorted(history_summary.items())))

        recommendations = {}
        for key in target_keys:
            if key in manual:
                # 수동 지정 선수 처리
                recs = []
                for pid in manual[key]:
                    name = recommender._get_name(pid)
                    recs.append((pid, name, "수동 지정"))
                recommendations[key] = recs
            else:
                recommendations[key] = recommender.recommend_for(key)

        print_recommendation_table(recommendations)

        # --dry-run이면 여기서 종료
        if args.dry_run:
            print("\n  [dry-run] 분석을 실행하지 않습니다.\n")
            return

        # 실행
        success_count = 0
        fail_count = 0
        skip_count = 0

        for key in target_keys:
            recs = recommendations.get(key, [])
            if not recs:
                print(f"\n  [{key}] 추천 선수 없음 — 건너뜀")
                skip_count += 1
                continue
            for pid, pname, reason in recs:
                ok = run_analysis(key, pid, pname, recommender)
                if ok:
                    success_count += 1
                else:
                    fail_count += 1

        # 결과 요약
        total_elapsed = time.time() - total_start
        print("\n" + "=" * 80)
        print("  실행 결과 요약")
        print("=" * 80)
        print(f"    성공: {success_count}건")
        print(f"    실패: {fail_count}건")
        if skip_count:
            print(f"    건너뜀: {skip_count}건")
        print(f"    총 소요시간: {total_elapsed:.1f}초")
        print("=" * 80 + "\n")

    finally:
        db.close()


if __name__ == '__main__':
    main()
