"""
팀 분석 일괄 실행기.
DB 기반 자동 추천 팀으로 t1~t6 분석을 일괄 실행.

사용법:
  python3 team/run_all.py                    # 전체 분석 (6 x 3팀)
  python3 team/run_all.py --analysis t1,t3   # 특정 분석만
  python3 team/run_all.py --dry-run          # 추천만 출력, 실행 안함
  python3 team/run_all.py --team t1:1        # 특정 분석에 팀 수동 지정
  python3 team/run_all.py --history          # 이전 실행 이력 조회
  python3 team/run_all.py --reset-history    # 이력 초기화
"""
import sys
import os
import json
import argparse
import time
import traceback
import numpy as np

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from common.db_connector import DBConnector
from team.team_common import get_team_name, safe_float, SEASONS, LATEST_SEASON, TEAM_NAMES
from team import (
    t1_team_power_radar,
    t2_head_to_head,
    t3_ranking_trend,
    t4_bat_pitch_balance,
    t5_home_away_split,
    t6_investment_efficiency,
)

MODULES = {
    't1': t1_team_power_radar,
    't2': t2_head_to_head,
    't3': t3_ranking_trend,
    't4': t4_bat_pitch_balance,
    't5': t5_home_away_split,
    't6': t6_investment_efficiency,
}

ANALYSIS_NAMES = {
    't1': '팀 전력 레이더',
    't2': '상대전적 히트맵',
    't3': '순위 변동 추이',
    't4': '타투 밸런스',
    't5': '홈/원정 성적',
    't6': '구단 투자 효율',
}

HISTORY_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), 'output', 'run_history.json'
)


# ==================== 팀 추천 엔진 ====================

class TeamRecommender:
    def __init__(self, db):
        self.db = db
        self.season = LATEST_SEASON
        self._cache = {}
        self._history = self._load_history()
        self._used_ids = set()

    def _load_history(self) -> dict:
        if os.path.exists(HISTORY_PATH):
            try:
                with open(HISTORY_PATH, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except (json.JSONDecodeError, IOError):
                return {}
        return {}

    def save_history(self, key: str, team_id: int, team_name: str):
        if key not in self._history:
            self._history[key] = []
        entry = {
            'team_id': team_id,
            'team_name': team_name,
            'run_date': time.strftime('%Y-%m-%d'),
        }
        existing_ids = [e['team_id'] for e in self._history[key]]
        if team_id not in existing_ids:
            self._history[key].append(entry)

        os.makedirs(os.path.dirname(HISTORY_PATH), exist_ok=True)
        with open(HISTORY_PATH, 'w', encoding='utf-8') as f:
            json.dump(self._history, f, ensure_ascii=False, indent=2)

    def _get_analyzed_ids(self, key: str) -> set:
        entries = self._history.get(key, [])
        return {e['team_id'] for e in entries}

    def _load_all(self):
        if self._cache:
            return
        print("  [추천] 데이터 로딩 중...")

        self._cache['team_stats'] = self.db.get_team_stats(self.season)
        self._cache['rankings'] = self.db.get_team_rankings(self.season)
        self._cache['head_to_head'] = self.db.get_head_to_head(self.season)
        self._cache['schedules'] = self.db.get_schedules(self.season)

        # 다시즌 순위 (t3, t6용)
        multi_rankings = {}
        for s in SEASONS:
            r = self.db.get_team_rankings(s)
            if not r.empty:
                multi_rankings[s] = r
        self._cache['multi_rankings'] = multi_rankings

        print(f"  [추천] 팀 스탯 {len(self._cache['team_stats'])}건, "
              f"순위 {len(self._cache['rankings'])}건 로딩 완료")

    def _exclude_and_pick(self, candidates: list, key: str, n: int = 3) -> list:
        """후보 [(team_id, reason)] 에서 이력 + 세션 중복 제외 후 n개 선택"""
        analyzed = self._get_analyzed_ids(key)
        # 1차: 이력 + 세션 중복 제외
        filtered = [(t, r) for t, r in candidates if t not in analyzed and t not in self._used_ids]
        if len(filtered) >= n:
            picked = filtered[:n]
        else:
            # 2차: 이력만 제외
            filtered2 = [(t, r) for t, r in candidates if t not in analyzed]
            if len(filtered2) >= n:
                picked = filtered2[:n]
            else:
                picked = candidates[:n]

        for tid, _ in picked:
            self._used_ids.add(tid)
        return picked

    # ==================== 분석별 추천 ====================

    def _recommend_t1(self) -> list:
        """t1 팀 전력: OPS 상위 + ERA 하위 + 중간"""
        ts = self._cache['team_stats']
        if ts.empty:
            return []

        candidates = []
        for _, row in ts.iterrows():
            tid = int(row['teamId'])
            ops = safe_float(row.get('OPS', 0))
            era = safe_float(row.get('ERA', 0))
            score = ops * 100 - era * 10  # 간단한 종합 점수
            candidates.append((tid, score, ops, era))

        candidates.sort(key=lambda x: x[1], reverse=True)
        result = []
        if candidates:
            # 1위 (최강)
            t = candidates[0]
            result.append((t[0], f"OPS {t[2]:.3f}, ERA {t[3]:.2f} (최강 전력)"))
            # 꼴찌 (최약)
            t = candidates[-1]
            result.append((t[0], f"OPS {t[2]:.3f}, ERA {t[3]:.2f} (약체 전력)"))
            # 중간
            mid = len(candidates) // 2
            t = candidates[mid]
            result.append((t[0], f"OPS {t[2]:.3f}, ERA {t[3]:.2f} (중간 전력)"))

        return self._exclude_and_pick(result, 't1')

    def _recommend_t2(self) -> list:
        """t2 상대전적: 최다승 + 최다패 + 치열한 팀"""
        h2h = self._cache['head_to_head']
        if h2h.empty:
            return []

        # 팀별 총 승수/패수 집계
        team_wins = {}
        team_losses = {}
        for _, row in h2h.iterrows():
            tid = int(row['teamId'])
            team_wins[tid] = team_wins.get(tid, 0) + safe_float(row['wins'])
            team_losses[tid] = team_losses.get(tid, 0) + safe_float(row['losses'])

        result = []
        # 최다승
        if team_wins:
            top_w = max(team_wins.items(), key=lambda x: x[1])
            result.append((top_w[0], f"총 {int(top_w[1])}승 (최다승)"))
        # 최다패
        if team_losses:
            top_l = max(team_losses.items(), key=lambda x: x[1])
            result.append((top_l[0], f"총 {int(top_l[1])}패 (최다패)"))
        # 치열한 팀 (승패 차이가 가장 작은 팀)
        close_teams = []
        for tid in team_wins:
            diff = abs(team_wins.get(tid, 0) - team_losses.get(tid, 0))
            close_teams.append((tid, diff))
        close_teams.sort(key=lambda x: x[1])
        if close_teams:
            t = close_teams[0]
            w, l = int(team_wins.get(t[0], 0)), int(team_losses.get(t[0], 0))
            result.append((t[0], f"{w}승 {l}패 (가장 치열한 전적)"))

        return self._exclude_and_pick(result, 't2')

    def _recommend_t3(self) -> list:
        """t3 순위 변동: 전년 대비 최대 상승 + 최대 하락 + 안정"""
        multi = self._cache['multi_rankings']
        recent_seasons = sorted(multi.keys())
        if len(recent_seasons) < 2:
            return []

        prev_s = recent_seasons[-2]
        curr_s = recent_seasons[-1]
        prev = multi[prev_s]
        curr = multi[curr_s]

        changes = []
        for _, row in curr.iterrows():
            tid = int(row['teamId'])
            curr_rank = safe_float(row['ranking'])
            prev_row = prev[prev['teamId'] == tid]
            if not prev_row.empty:
                prev_rank = safe_float(prev_row.iloc[0]['ranking'])
                change = prev_rank - curr_rank  # 양수=상승
                changes.append((tid, change, int(prev_rank), int(curr_rank)))

        if not changes:
            return []

        result = []
        # 최대 상승
        changes.sort(key=lambda x: x[1], reverse=True)
        t = changes[0]
        result.append((t[0], f"{t[2]}위→{t[3]}위 ({int(t[1])}단계 상승)"))
        # 최대 하락
        t = changes[-1]
        result.append((t[0], f"{t[2]}위→{t[3]}위 ({int(abs(t[1]))}단계 하락)"))
        # 가장 안정 (변동 최소)
        changes.sort(key=lambda x: abs(x[1]))
        t = changes[0]
        result.append((t[0], f"{t[2]}위→{t[3]}위 (변동 최소)"))

        return self._exclude_and_pick(result, 't3')

    def _recommend_t4(self) -> list:
        """t4 타투 밸런스: 타격 편중 + 투수 편중 + 균형형"""
        ts = self._cache['team_stats']
        if ts.empty:
            return []

        from team.t4_bat_pitch_balance import _calc_balance
        bal = _calc_balance(ts)
        if not bal:
            return []

        result = []
        # 타격 편중 (off > pit 최대)
        bat_heavy = sorted(bal.items(), key=lambda x: x[1]['off_score'] - x[1]['pit_score'], reverse=True)
        if bat_heavy:
            t = bat_heavy[0]
            result.append((t[0], f"타격 편중 (공격 {t[1]['off_score']}, 투수 {t[1]['pit_score']})"))
        # 투수 편중 (pit > off 최대)
        pit_heavy = sorted(bal.items(), key=lambda x: x[1]['pit_score'] - x[1]['off_score'], reverse=True)
        if pit_heavy:
            t = pit_heavy[0]
            result.append((t[0], f"투수 편중 (공격 {t[1]['off_score']}, 투수 {t[1]['pit_score']})"))
        # 균형형 (밸런스 지수 최소)
        balanced = sorted(bal.items(), key=lambda x: x[1]['balance'])
        if balanced:
            t = balanced[0]
            result.append((t[0], f"균형형 (밸런스 지수 {t[1]['balance']:.1f})"))

        return self._exclude_and_pick(result, 't4')

    def _recommend_t5(self) -> list:
        """t5 홈/원정: 홈 어드밴티지 최대 + 최소 + 원정 강팀"""
        schedules = self._cache['schedules']
        if schedules.empty:
            return []

        from team.t5_home_away_split import _calc_home_away
        ha_data = {}
        for tid in TEAM_NAMES:
            ha = _calc_home_away(schedules, tid)
            if ha['home_total'] + ha['away_total'] > 0:
                ha_data[tid] = ha

        if not ha_data:
            return []

        result = []
        # 홈 어드밴티지 최대
        sorted_ha = sorted(ha_data.items(), key=lambda x: x[1]['advantage'], reverse=True)
        t = sorted_ha[0]
        result.append((t[0], f"홈 어드밴티지 최대 ({t[1]['advantage']:+.3f})"))
        # 홈 어드밴티지 최소
        t = sorted_ha[-1]
        result.append((t[0], f"홈 어드밴티지 최소 ({t[1]['advantage']:+.3f})"))
        # 원정 강팀 (원정 승률 최고)
        sorted_away = sorted(ha_data.items(), key=lambda x: x[1]['away_wr'], reverse=True)
        t = sorted_away[0]
        result.append((t[0], f"원정 승률 최고 ({t[1]['away_wr']:.3f})"))

        return self._exclude_and_pick(result, 't5')

    def _recommend_t6(self) -> list:
        """t6 구단 효율: 과성과 1위 + 저성과 1위 + 중간"""
        ts = self._cache['team_stats']
        rankings = self._cache['rankings']
        if ts.empty or rankings.empty:
            return []

        from team.t6_investment_efficiency import _pythagorean

        efficiencies = []
        for _, row in rankings.iterrows():
            tid = int(row['teamId'])
            actual = safe_float(row.get('winRate', 0))
            ts_row = ts[ts['teamId'] == tid]
            if ts_row.empty:
                continue
            runs = safe_float(ts_row.iloc[0].get('R', 0))
            ra = safe_float(ts_row.iloc[0].get('RA', 0))
            pyth = _pythagorean(runs, ra)
            diff = round(actual - pyth, 4)
            efficiencies.append((tid, diff))

        if not efficiencies:
            return []

        efficiencies.sort(key=lambda x: x[1], reverse=True)
        result = []
        # 과성과 1위
        t = efficiencies[0]
        result.append((t[0], f"과성과 1위 (차이 {t[1]:+.4f})"))
        # 저성과 1위
        t = efficiencies[-1]
        result.append((t[0], f"저성과 1위 (차이 {t[1]:+.4f})"))
        # 중간
        mid = len(efficiencies) // 2
        t = efficiencies[mid]
        result.append((t[0], f"중간 효율 (차이 {t[1]:+.4f})"))

        return self._exclude_and_pick(result, 't6')

    # ==================== 공개 API ====================

    def recommend_for(self, key: str) -> list:
        """특정 분석에 대한 추천 팀 반환. 반환: [(team_id, team_name, reason)]"""
        self._load_all()
        method = getattr(self, f'_recommend_{key}', None)
        if method is None:
            raise ValueError(f"알 수 없는 분석 키: {key}")
        recs = method()
        return [(tid, get_team_name(tid), reason) for tid, reason in recs]

    def get_history_summary(self) -> dict:
        return {k: len(v) for k, v in self._history.items()}


# ==================== CLI ====================

def parse_args():
    parser = argparse.ArgumentParser(
        description='팀 분석 일괄 실행기',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예시:
  python3 team/run_all.py --dry-run          추천만 확인
  python3 team/run_all.py --analysis t1,t3   특정 분석만 실행
  python3 team/run_all.py --team t1:1        t1에 팀 ID 1 (KIA) 수동 지정
  python3 team/run_all.py --history          실행 이력 조회
  python3 team/run_all.py --reset-history    이력 초기화 후 실행
        """
    )
    parser.add_argument('--analysis', type=str, default=None,
                        help='실행할 분석 (쉼표 구분, 예: t1,t3,t6)')
    parser.add_argument('--dry-run', action='store_true',
                        help='추천만 출력하고 분석 실행하지 않음')
    parser.add_argument('--team', action='append', default=[],
                        help='특정 분석에 팀 수동 지정 (예: t1:1)')
    parser.add_argument('--history', action='store_true',
                        help='이전 실행 이력 조회')
    parser.add_argument('--reset-history', action='store_true',
                        help='이력 초기화')
    parser.add_argument('--all', action='store_true',
                        help='전체 10팀 분석 (추천 시스템 우회)')
    return parser.parse_args()


def parse_manual_teams(team_args: list) -> dict:
    """--team t1:1 형태의 인자를 파싱 → {분석키: [팀ID]}"""
    manual = {}
    for arg in team_args:
        if ':' not in arg:
            print(f"  [경고] 잘못된 형식: {arg} (예: t1:1)")
            continue
        key, tid_str = arg.split(':', 1)
        key = key.strip().lower()
        if key not in MODULES:
            print(f"  [경고] 알 수 없는 분석: {key}")
            continue
        try:
            tid = int(tid_str.strip())
            manual.setdefault(key, []).append(tid)
        except ValueError:
            print(f"  [경고] 잘못된 팀 ID: {tid_str}")
    return manual


def print_recommendation_table(recommendations: dict):
    print("\n" + "=" * 80)
    print("  팀 자동 추천 결과")
    print("=" * 80)

    total = 0
    unique_teams = set()
    for key in sorted(recommendations.keys()):
        recs = recommendations[key]
        name = ANALYSIS_NAMES.get(key, key)
        print(f"\n  [{key}] {name}")
        print(f"  {'─' * 70}")
        if not recs:
            print(f"    (추천 팀 없음)")
            continue
        for i, (tid, tname, reason) in enumerate(recs, 1):
            print(f"    {i}. [{tid:>2}] {tname:<6} → {reason}")
            unique_teams.add(tid)
            total += 1
        print()

    print(f"  {'─' * 70}")
    print(f"  총 {total}건 추천 (고유 팀 {len(unique_teams)}개)")
    print("=" * 80)


def print_history(recommender: TeamRecommender):
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
        print(f"\n  [{key}] {name} — {len(entries)}팀 분석 완료")
        for e in entries:
            print(f"    - [{e['team_id']:>2}] {e['team_name']:<6} ({e['run_date']})")

    total = sum(len(v) for v in history.values())
    print(f"\n  총 {total}건 완료")
    print("=" * 80)


def run_analysis(key: str, team_id: int, team_name: str,
                 recommender: TeamRecommender) -> bool:
    module = MODULES[key]
    analysis_name = ANALYSIS_NAMES.get(key, key)

    print(f"\n{'─' * 60}")
    print(f"  실행: [{key}] {analysis_name} — {team_name} (ID: {team_id})")
    print(f"{'─' * 60}")

    start = time.time()
    try:
        module.analyze(team_id)
        elapsed = time.time() - start
        print(f"\n  -> 완료 ({elapsed:.1f}초)")
        recommender.save_history(key, team_id, team_name)
        return True
    except Exception as e:
        elapsed = time.time() - start
        print(f"\n  -> 실패 ({elapsed:.1f}초): {e}")
        traceback.print_exc()
        return False


def main():
    args = parse_args()
    total_start = time.time()

    db = DBConnector()
    recommender = TeamRecommender(db)

    try:
        if args.reset_history:
            if os.path.exists(HISTORY_PATH):
                os.remove(HISTORY_PATH)
                print("  [이력] 초기화 완료")
            recommender._history = {}

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

        # 수동 지정 팀 파싱
        manual = parse_manual_teams(args.team)

        # 추천 생성
        print("\n" + "=" * 80)
        print("  팀 자동 추천 시스템")
        print("=" * 80)

        recommender._load_all()

        history_summary = recommender.get_history_summary()
        if history_summary:
            print(f"\n  [이력] 이전 실행: "
                  + ", ".join(f"{k}={v}팀" for k, v in sorted(history_summary.items())))

        recommendations = {}
        for key in target_keys:
            if args.all:
                # 전체 팀 (순위순)
                rankings = recommender._cache.get('rankings')
                if rankings is not None and not rankings.empty:
                    recs = [(int(r['teamId']), r['teamName'], "전체 분석")
                            for _, r in rankings.sort_values('ranking').iterrows()]
                else:
                    recs = [(tid, get_team_name(tid), "전체 분석")
                            for tid in sorted(TEAM_NAMES.keys())]
                recommendations[key] = recs
            elif key in manual:
                recs = []
                for tid in manual[key]:
                    recs.append((tid, get_team_name(tid), "수동 지정"))
                recommendations[key] = recs
            else:
                recommendations[key] = recommender.recommend_for(key)

        print_recommendation_table(recommendations)

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
                print(f"\n  [{key}] 추천 팀 없음 — 건너뜀")
                skip_count += 1
                continue
            for tid, tname, reason in recs:
                ok = run_analysis(key, tid, tname, recommender)
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
