"""
BaseBall LOCK 3.0 - Python 배치 분석 진입점

정규 토픽(0~14), 특집 토픽(15~), WBC 분석(20), 연도/월 비교를 지원.

실행 예시:
    python main.py                          # 정규 전체 (0~14) 기본 분석
    python main.py --topic 8                # 특정 주제 (서브토픽 포함)
    python main.py --special                # 특집 분석 (15~17)
    python main.py --wbc                    # WBC 분석
    python main.py --compare-yearly         # 전체 주제 연도별 비교
    python main.py --compare-monthly        # 전체 주제 월별 비교
    python main.py --all                    # 정규 + 특집 + 비교 모두 실행
    python main.py --season 2024            # 특정 시즌
"""
import sys
import argparse
import traceback
from datetime import datetime

from config import CURRENT_SEASON, ANALYSIS_TOPICS, SPECIAL_TOPICS, WBC_TOPICS, get_topic_name
from common.db_connector import DBConnector
from common.comparison import ComparisonAnalyzer

# ==================== 정규 토픽 모듈 ====================
from topics import (
    topic_0, topic_1, topic_2, topic_3, topic_4,
    topic_5, topic_6, topic_7, topic_8, topic_9,
    topic_10, topic_11, topic_12, topic_13, topic_14,
)
from topics import topic_8_futures_batter, topic_8_futures_pitcher
from topics import topic_9_abs_batter, topic_9_abs_pitcher

# ==================== 특집 토픽 모듈 ====================
from special import topic_hardcarry, topic_umpire, topic_season_type

# ==================== WBC 모듈 ====================
from wbc import wbc_analysis

# 기존 호환용 (default sub_topic)
TOPIC_MODULES = {
    0: topic_0, 1: topic_1, 2: topic_2, 3: topic_3, 4: topic_4,
    5: topic_5, 6: topic_6, 7: topic_7,
    10: topic_10, 11: topic_11, 12: topic_12, 13: topic_13, 14: topic_14,
}

# 서브토픽 분리된 모듈
SUB_TOPIC_MODULES = {
    8: {
        'batter': topic_8_futures_batter,
        'pitcher': topic_8_futures_pitcher,
    },
    9: {
        'batter': topic_9_abs_batter,
        'pitcher': topic_9_abs_pitcher,
    },
}

# 특집 모듈
SPECIAL_MODULES = {
    15: topic_hardcarry,
    16: topic_umpire,
    17: topic_season_type,
}


def run_topic(db: DBConnector, topic_index: int, season: int,
              sub_topic: str = 'default',
              comparison_type: str = 'none',
              anchor_value: str = '') -> bool:
    """단일 주제 분석 실행 및 결과 저장. 성공 시 True 반환."""
    topic_name = get_topic_name(topic_index)
    label = f"[{topic_index:2d}] {topic_name}"
    if sub_topic != 'default':
        label += f" ({sub_topic})"
    if comparison_type != 'none':
        label += f" [{comparison_type}:{anchor_value}]"

    print(f"  {label} 분석 시작...")

    try:
        # 모듈 결정
        if topic_index in SUB_TOPIC_MODULES and sub_topic in SUB_TOPIC_MODULES[topic_index]:
            module = SUB_TOPIC_MODULES[topic_index][sub_topic]
        elif topic_index in TOPIC_MODULES:
            module = TOPIC_MODULES[topic_index]
        elif topic_index in SPECIAL_MODULES:
            module = SPECIAL_MODULES[topic_index]
        elif topic_index == 20:
            module = wbc_analysis
        else:
            print(f"  {label} 모듈 없음 - 건너뜀")
            return False

        stats_json, chart_json, insight_text, hypothesis_json = module.analyze(season, db)

        db.save_analysis_result(
            topic=topic_index,
            season=season,
            stats_json=stats_json,
            chart_json=chart_json,
            insight_text=insight_text,
            hypothesis_json=hypothesis_json,
            sub_topic=sub_topic,
            comparison_type=comparison_type,
            anchor_value=anchor_value,
        )
        print(f"  {label} 완료")
        return True

    except Exception as e:
        print(f"  {label} 실패: {e}")
        traceback.print_exc()
        return False


def run_regular_topics(db: DBConnector, season: int, topic_filter: int = -1):
    """정규 토픽 (0~14) 실행. 서브토픽 있으면 각각 실행."""
    success, fail = 0, 0

    topics_to_run = [topic_filter] if topic_filter >= 0 else range(15)

    for idx in topics_to_run:
        if idx not in ANALYSIS_TOPICS:
            continue

        topic_config = ANALYSIS_TOPICS[idx]
        sub_topics = topic_config['sub_topics']

        for sub in sub_topics:
            ok = run_topic(db, idx, season, sub_topic=sub)
            success += 1 if ok else 0
            fail += 0 if ok else 1

    return success, fail


def run_special_topics(db: DBConnector, season: int):
    """특집 토픽 (15~) 실행"""
    success, fail = 0, 0

    for idx in SPECIAL_TOPICS:
        topic_config = SPECIAL_TOPICS[idx]
        for sub in topic_config['sub_topics']:
            ok = run_topic(db, idx, season, sub_topic=sub)
            success += 1 if ok else 0
            fail += 0 if ok else 1

    return success, fail


def run_wbc(db: DBConnector, season: int):
    """WBC 분석 실행"""
    ok = run_topic(db, 20, season)
    return (1 if ok else 0), (0 if ok else 1)


def run_yearly_comparisons(db: DBConnector, season: int):
    """전체 토픽 연도별 비교 실행"""
    print("\n  === 연도별 비교 분석 ===")
    comparator = ComparisonAnalyzer(db)
    success, fail = 0, 0

    for idx in ANALYSIS_TOPICS:
        topic_config = ANALYSIS_TOPICS[idx]

        for sub in topic_config['sub_topics']:
            # 해당 토픽의 analyze 함수 결정
            if idx in SUB_TOPIC_MODULES and sub in SUB_TOPIC_MODULES[idx]:
                analyze_fn = SUB_TOPIC_MODULES[idx][sub].analyze
            elif idx in TOPIC_MODULES:
                analyze_fn = TOPIC_MODULES[idx].analyze
            else:
                continue

            try:
                results = comparator.run_yearly_comparisons(
                    topic=idx, sub_topic=sub,
                    analyze_fn=analyze_fn,
                    anchor_season=season
                )

                for r in results:
                    try:
                        db.save_analysis_result(
                            topic=r['topic'],
                            season=r['season'],
                            stats_json=r['stats_json'],
                            chart_json=r['chart_json'],
                            insight_text=r['insight_text'],
                            hypothesis_json=r['hypothesis_json'],
                            sub_topic=r['sub_topic'],
                            comparison_type=r['comparison_type'],
                            anchor_value=r['anchor_value'],
                        )
                        print(f"    [{idx}] {get_topic_name(idx)} ({sub}) "
                              f"연도비교 {r['anchor_value']} vs {r['compare_with']} 저장 완료")
                        success += 1
                    except Exception as e:
                        print(f"    [{idx}] 연도비교 저장 실패: {e}")
                        fail += 1

            except Exception as e:
                print(f"    [{idx}] 연도비교 실행 실패: {e}")
                fail += 1

    return success, fail


def run_monthly_comparisons(db: DBConnector, season: int):
    """전체 토픽 월별 비교 실행"""
    print("\n  === 월별 비교 분석 ===")
    comparator = ComparisonAnalyzer(db)
    success, fail = 0, 0

    # 월별 비교는 일부 토픽에만 의미 있음
    monthly_topics = [5, 7, 10, 13]  # 이닝득점, 관중, 클러치, 월별

    for idx in monthly_topics:
        if idx not in ANALYSIS_TOPICS:
            continue

        topic_config = ANALYSIS_TOPICS[idx]

        for sub in topic_config['sub_topics']:
            if idx in TOPIC_MODULES:
                analyze_fn = TOPIC_MODULES[idx].analyze
            else:
                continue

            # 월별 비교는 동일 함수를 시즌 단위로 호출 (월별 데이터 세분화는 향후 확장)
            try:
                results = comparator.run_monthly_comparisons(
                    topic=idx, sub_topic=sub,
                    season=season,
                    monthly_analyze_fn=lambda s, m, d: analyze_fn(s, d)
                )

                for r in results:
                    try:
                        db.save_analysis_result(
                            topic=r['topic'],
                            season=r['season'],
                            stats_json=r['stats_json'],
                            chart_json=r['chart_json'],
                            insight_text=r['insight_text'],
                            hypothesis_json=r['hypothesis_json'],
                            sub_topic=r['sub_topic'],
                            comparison_type=r['comparison_type'],
                            anchor_value=r['anchor_value'],
                        )
                        print(f"    [{idx}] {get_topic_name(idx)} 월비교 "
                              f"{r['anchor_value']}월 vs {r['compare_with']}월 저장 완료")
                        success += 1
                    except Exception as e:
                        print(f"    [{idx}] 월비교 저장 실패: {e}")
                        fail += 1

            except Exception as e:
                print(f"    [{idx}] 월비교 실행 실패: {e}")
                fail += 1

    return success, fail


def main():
    parser = argparse.ArgumentParser(description='BaseBall LOCK 3.0 배치 분석')
    parser.add_argument('--topic', type=int, default=-1, help='특정 주제 인덱스 (0~14)')
    parser.add_argument('--season', type=int, default=CURRENT_SEASON, help='분석 대상 시즌')
    parser.add_argument('--special', action='store_true', help='특집 분석 실행')
    parser.add_argument('--wbc', action='store_true', help='WBC 분석 실행')
    parser.add_argument('--compare-yearly', action='store_true', help='연도별 비교 분석')
    parser.add_argument('--compare-monthly', action='store_true', help='월별 비교 분석')
    parser.add_argument('--all', action='store_true', help='전체 실행 (정규 + 특집 + 비교)')
    args = parser.parse_args()

    season = args.season
    start_time = datetime.now()

    print(f"{'='*60}")
    print(f"  BaseBall LOCK 3.0 - Python 배치 분석")
    print(f"  시즌: {season}")
    print(f"  시작: {start_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'='*60}")

    db = DBConnector()
    total_success = 0
    total_fail = 0

    try:
        # 정규 토픽
        if args.all or args.topic >= 0 or (
            not args.special and not args.wbc
            and not args.compare_yearly and not args.compare_monthly
        ):
            print("\n  === 정규 분석 ===")
            s, f = run_regular_topics(db, season, args.topic)
            total_success += s
            total_fail += f

        # 특집 토픽
        if args.all or args.special:
            print("\n  === 특집 분석 ===")
            s, f = run_special_topics(db, season)
            total_success += s
            total_fail += f

        # WBC
        if args.all or args.wbc:
            print("\n  === WBC 분석 ===")
            s, f = run_wbc(db, season)
            total_success += s
            total_fail += f

        # 연도별 비교
        if args.all or args.compare_yearly:
            s, f = run_yearly_comparisons(db, season)
            total_success += s
            total_fail += f

        # 월별 비교
        if args.all or args.compare_monthly:
            s, f = run_monthly_comparisons(db, season)
            total_success += s
            total_fail += f

    finally:
        db.close()

    end_time = datetime.now()
    elapsed = (end_time - start_time).total_seconds()

    print(f"\n{'='*60}")
    print(f"  분석 완료")
    print(f"  성공: {total_success}건 / 실패: {total_fail}건")
    print(f"  소요시간: {elapsed:.1f}초")
    print(f"  종료: {end_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'='*60}")

    sys.exit(0 if total_fail == 0 else 1)


if __name__ == '__main__':
    main()
