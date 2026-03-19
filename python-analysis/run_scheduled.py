"""
자동 분석 스케줄링 오케스트레이터.
Java ScheduledAnalysisService에서 ProcessBuilder로 호출.

runCount 1개로 4개 카테고리 독립 로테이션:
  team(6)  → 3주 순환    | game(7)  → 3.5주 순환
  player(9) → 4.5주 순환  | advanced(5) → 2.5주 순환

  runCount=0 → t1, g1, p1, a1
  runCount=5 → t6, g6, p6, a1
  runCount=6 → t1, g7, p7, a2

사용법:
  python3 run_scheduled.py                              # 자동 (상태 파일에서 runCount)
  python3 run_scheduled.py --run-count 0                # runCount 수동 지정
  python3 run_scheduled.py --run-count 0 --dry-run      # 미리보기
  python3 run_scheduled.py --category team --key t3     # 단일 카테고리 수동 실행
"""
import sys
import os
import json
import argparse
import subprocess
import time

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATE_FILE = os.path.join(BASE_DIR, 'output', 'scheduled_run_count.json')

# 카테고리별 분석 키
CATEGORIES = {
    'team':     ['t1', 't2', 't3', 't4', 't5', 't6'],
    'game':     ['g1', 'g2', 'g3', 'g4', 'g5', 'g6', 'g7'],
    'player':   ['p1', 'p2', 'p3', 'p4', 'p5', 'p6', 'p7', 'p8', 'p9'],
    'advanced': ['a1', 'a2', 'a3', 'a4', 'a5'],
}

EXECUTION_TIMEOUT = 600  # 10분


# ==================== 상태 관리 ====================

def load_run_count() -> int:
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, 'r') as f:
                data = json.load(f)
                return data.get('run_count', 0)
        except (json.JSONDecodeError, IOError):
            return 0
    return 0


def save_run_count(count: int):
    os.makedirs(os.path.dirname(STATE_FILE), exist_ok=True)
    with open(STATE_FILE, 'w') as f:
        json.dump({
            'run_count': count,
            'last_run': time.strftime('%Y-%m-%d %H:%M:%S'),
        }, f, indent=2)


def get_analysis_key(category: str, run_count: int) -> str:
    keys = CATEGORIES[category]
    return keys[run_count % len(keys)]


# ==================== 실행 ====================

def run_command(cmd: list, timeout: int = EXECUTION_TIMEOUT) -> bool:
    print(f"  $ {' '.join(cmd)}")
    try:
        result = subprocess.run(
            cmd, cwd=BASE_DIR, timeout=timeout,
        )
        return result.returncode == 0
    except subprocess.TimeoutExpired:
        print(f"  [타임아웃] {timeout}초 초과")
        return False
    except Exception as e:
        print(f"  [오류] {e}")
        return False


def execute_category(category: str, key: str, dry_run: bool = False) -> bool:
    print(f"\n{'=' * 60}")
    print(f"  [{category.upper()}] {key} 실행")
    print(f"{'=' * 60}")

    if dry_run:
        print(f"  [dry-run] 건너뜀")
        return True

    python = sys.executable or 'python3'

    if category == 'team':
        # run_all.py --analysis {key} --all → write_columns.py --analysis {key}
        ok = run_command([python, 'team/run_all.py', '--analysis', key, '--all'])
        if not ok:
            print(f"  [실패] team/run_all.py")
            return False
        ok = run_command([python, 'team/write_columns.py', '--analysis', key])
        if not ok:
            print(f"  [실패] team/write_columns.py")
            return False

    elif category == 'game':
        # run_all.py --analysis {key} --all → write_columns.py --analysis {key}
        ok = run_command([python, 'game/run_all.py', '--analysis', key, '--all'])
        if not ok:
            print(f"  [실패] game/run_all.py")
            return False
        ok = run_command([python, 'game/write_columns.py', '--analysis', key])
        if not ok:
            print(f"  [실패] game/write_columns.py")
            return False

    elif category == 'player':
        # run_all.py --analysis {key} (추천 3명) → write_columns.py --analysis {key}
        ok = run_command([python, 'player/run_all.py', '--analysis', key])
        if not ok:
            print(f"  [실패] player/run_all.py")
            return False
        ok = run_command([python, 'player/write_columns.py', '--analysis', key])
        if not ok:
            print(f"  [실패] player/write_columns.py")
            return False

    elif category == 'advanced':
        # write_columns.py --analysis {key} 만 (run_all.py 없음)
        ok = run_command([python, 'advanced/write_columns.py', '--analysis', key])
        if not ok:
            print(f"  [실패] advanced/write_columns.py")
            return False

    print(f"  [{category.upper()}] {key} 완료")
    return True


# ==================== CLI ====================

def parse_args():
    parser = argparse.ArgumentParser(
        description='자동 분석 스케줄링 오케스트레이터',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예시:
  python3 run_scheduled.py                              자동 실행
  python3 run_scheduled.py --run-count 0 --dry-run      미리보기
  python3 run_scheduled.py --category team --key t3     단일 수동 실행
        """
    )
    parser.add_argument('--run-count', type=int, default=None,
                        help='runCount 수동 지정 (미지정 시 상태 파일에서 자동 읽기)')
    parser.add_argument('--dry-run', action='store_true',
                        help='실행할 분석 미리보기')
    parser.add_argument('--category', type=str, default=None,
                        choices=['team', 'game', 'player', 'advanced'],
                        help='단일 카테고리 수동 실행')
    parser.add_argument('--key', type=str, default=None,
                        help='단일 분석 키 (--category와 함께 사용)')
    return parser.parse_args()


def main():
    args = parse_args()
    total_start = time.time()

    print("\n" + "=" * 60)
    print("  자동 분석 스케줄링 시스템")
    print("=" * 60)

    # runCount 결정
    if args.run_count is not None:
        run_count = args.run_count
    else:
        run_count = load_run_count()

    # 실행 대상 결정
    schedule = {}
    if args.category and args.key:
        # 단일 카테고리 수동 실행
        all_keys = CATEGORIES.get(args.category, [])
        if args.key not in all_keys:
            print(f"  [오류] {args.category}에 {args.key}는 없습니다.")
            print(f"  가능한 키: {', '.join(all_keys)}")
            sys.exit(1)
        schedule[args.category] = args.key
    elif args.category:
        # 카테고리만 지정 → runCount 기반 키 선택
        schedule[args.category] = get_analysis_key(args.category, run_count)
    else:
        # 전체 카테고리 로테이션
        for cat in ['team', 'game', 'player', 'advanced']:
            schedule[cat] = get_analysis_key(cat, run_count)

    print(f"\n  runCount: {run_count}")
    print(f"  실행 예정:")
    for cat, key in schedule.items():
        print(f"    {cat:>10}: {key}")

    if args.dry_run:
        print(f"\n  [dry-run] 실행하지 않습니다.\n")
        return

    # 순차 실행 (카테고리별 독립 try/except)
    results = {}
    for cat, key in schedule.items():
        try:
            ok = execute_category(cat, key)
            results[cat] = 'success' if ok else 'fail'
        except Exception as e:
            print(f"\n  [{cat.upper()}] 예외 발생: {e}")
            results[cat] = 'error'

    # runCount 증가 (단일 카테고리 수동 실행이 아닌 경우만)
    if not (args.category and args.key):
        save_run_count(run_count + 1)
        print(f"\n  runCount {run_count} -> {run_count + 1} 저장")

    # 결과 요약
    total_elapsed = time.time() - total_start
    print(f"\n{'=' * 60}")
    print(f"  실행 결과 요약")
    print(f"{'=' * 60}")
    for cat, status in results.items():
        icon = 'O' if status == 'success' else 'X'
        key = schedule[cat]
        print(f"    [{icon}] {cat:>10}: {key} -> {status}")
    print(f"    총 소요시간: {total_elapsed:.1f}초")
    print(f"{'=' * 60}\n")

    # 실패 있으면 exit code 1
    if any(s != 'success' for s in results.values()):
        sys.exit(1)


if __name__ == '__main__':
    main()
