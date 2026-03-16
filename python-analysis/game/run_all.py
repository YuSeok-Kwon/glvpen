"""
경기 분석 일괄 실행기.
DB 기반 자동 추천 팀으로 g1~g7 분석을 일괄 실행.

사용법:
  python3 game/run_all.py                            # 전체 (7분석 × 3팀, 정규시즌)
  python3 game/run_all.py --game-type regular         # 정규시즌만 (기본값)
  python3 game/run_all.py --game-type postseason      # 포스트시즌
  python3 game/run_all.py --game-type all             # 전체 경기
  python3 game/run_all.py --analysis g1,g3            # 특정 분석만
  python3 game/run_all.py --dry-run                   # 추천만 출력
  python3 game/run_all.py --team g1:1                 # 수동 팀 지정
  python3 game/run_all.py --season 2024               # 시즌 지정
  python3 game/run_all.py --history                   # 이전 실행 이력 조회
  python3 game/run_all.py --reset-history             # 이력 초기화
"""
import sys
import os
import json
import argparse
import time
import traceback

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

import numpy as np
from common.db_connector import DBConnector
from game.game_common import (
    get_team_name, safe_float, filter_schedules_by_type, get_team_games,
    parse_inning_scores, parse_game_time, filter_score_boards,
    TEAM_NAMES, LATEST_SEASON, GAME_TYPES, get_game_type_label,
)
from game import (
    g1_inning_scoring,
    g2_comeback_pattern,
    g3_key_player,
    g4_win_factor,
    g5_game_time,
    g6_blowout_close,
    g7_lineup_effect,
)

MODULES = {
    'g1': g1_inning_scoring,
    'g2': g2_comeback_pattern,
    'g3': g3_key_player,
    'g4': g4_win_factor,
    'g5': g5_game_time,
    'g6': g6_blowout_close,
    'g7': g7_lineup_effect,
}

ANALYSIS_NAMES = {
    'g1': '이닝별 득점 패턴',
    'g2': '역전/추격 패턴',
    'g3': '키플레이어 분석',
    'g4': '승리 요인 분석',
    'g5': '경기 시간 분석',
    'g6': '접전/대패 분류',
    'g7': '라인업 효과',
}

HISTORY_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), 'output', 'run_history.json'
)


# ==================== 팀 추천 엔진 ====================

class GameRecommender:
    def __init__(self, db, game_type='regular', season=LATEST_SEASON):
        self.db = db
        self.season = season
        self.game_type = game_type
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
        history_key = f"{key}_{self.game_type}"
        if history_key not in self._history:
            self._history[history_key] = []
        entry = {
            'team_id': team_id,
            'team_name': team_name,
            'game_type': self.game_type,
            'run_date': time.strftime('%Y-%m-%d'),
        }
        existing_ids = [e['team_id'] for e in self._history[history_key]]
        if team_id not in existing_ids:
            self._history[history_key].append(entry)

        os.makedirs(os.path.dirname(HISTORY_PATH), exist_ok=True)
        with open(HISTORY_PATH, 'w', encoding='utf-8') as f:
            json.dump(self._history, f, ensure_ascii=False, indent=2)

    def _get_analyzed_ids(self, key: str) -> set:
        history_key = f"{key}_{self.game_type}"
        entries = self._history.get(history_key, [])
        return {e['team_id'] for e in entries}

    def _exclude_and_pick(self, candidates: list, key: str, n: int = 3) -> list:
        """후보 [(team_id, reason)] 에서 이력/세션 중복 제외 후 n개 선택"""
        analyzed = self._get_analyzed_ids(key)
        filtered = [(t, r) for t, r in candidates if t not in analyzed and t not in self._used_ids]
        if len(filtered) >= n:
            picked = filtered[:n]
        else:
            filtered2 = [(t, r) for t, r in candidates if t not in analyzed]
            if len(filtered2) >= n:
                picked = filtered2[:n]
            else:
                picked = candidates[:n]

        for tid, _ in picked:
            self._used_ids.add(tid)
        return picked

    def _load_all(self):
        if self._cache:
            return
        print("  [추천] 데이터 로딩 중...")

        schedules = self.db.get_schedules(self.season)
        self._cache['schedules'] = filter_schedules_by_type(schedules, self.game_type)
        self._cache['score_boards'] = self.db.get_score_boards(self.season)
        self._cache['rankings'] = self.db.get_team_rankings(self.season)

        # score_boards 필터
        if not self._cache['schedules'].empty:
            valid_ids = set(self._cache['schedules']['id'])
            self._cache['score_boards'] = filter_score_boards(
                self._cache['score_boards'], valid_ids
            )

        try:
            self._cache['key_players'] = self.db.get_game_key_players(self.season)
            if not self._cache['schedules'].empty:
                valid_ids = set(self._cache['schedules']['id'])
                self._cache['key_players'] = self._cache['key_players'][
                    self._cache['key_players']['scheduleId'].isin(valid_ids)
                ]
        except Exception:
            self._cache['key_players'] = None

        try:
            lineups = self.db.get_batter_lineups(self.season)
            if not self._cache['schedules'].empty:
                valid_ids = set(self._cache['schedules']['id'])
                lineups = lineups[lineups['scheduleId'].isin(valid_ids)]
            self._cache['lineups'] = lineups
        except Exception:
            self._cache['lineups'] = None

        sched_count = len(self._cache['schedules'])
        sb_count = len(self._cache['score_boards'])
        gt_label = get_game_type_label(self.game_type)
        print(f"  [추천] {gt_label}: 일정 {sched_count}건, 스코어보드 {sb_count}건 로딩 완료")

    # ==================== 분석별 추천 ====================

    def _recommend_g1(self) -> list:
        """g1 이닝 득점: 후반 집중 + 전반 집중 + 균형형"""
        schedules = self._cache['schedules']
        score_boards = self._cache['score_boards']
        if schedules.empty or score_boards.empty:
            return []

        merged = score_boards.merge(
            schedules[['id', 'homeTeamId', 'awayTeamId']],
            left_on='scheduleId', right_on='id', how='inner', suffixes=('', '_sched')
        )

        team_ratios = {}
        for tid in TEAM_NAMES:
            fh_scores = []
            sh_scores = []
            for _, row in merged.iterrows():
                if row['homeTeamId'] == tid:
                    scores = parse_inning_scores(row.get('homeInningScores'))
                elif row['awayTeamId'] == tid:
                    scores = parse_inning_scores(row.get('awayInningScores'))
                else:
                    continue
                if scores and len(scores) >= 6:
                    fh_scores.append(sum(scores[:5]))
                    sh_scores.append(sum(scores[5:9]))

            fh = np.mean(fh_scores) if fh_scores else 0
            sh = np.mean(sh_scores) if sh_scores else 0
            total = fh + sh
            ratio = sh / total if total > 0 else 0.5
            team_ratios[tid] = (ratio, fh, sh)

        if not team_ratios:
            return []

        result = []
        # 후반 집중
        sorted_sh = sorted(team_ratios.items(), key=lambda x: x[1][0], reverse=True)
        t = sorted_sh[0]
        result.append((t[0], f"후반 득점 비중 {t[1][0]:.1%} (후반 집중)"))
        # 전반 집중
        t = sorted_sh[-1]
        result.append((t[0], f"후반 득점 비중 {t[1][0]:.1%} (전반 집중)"))
        # 균형형
        balanced = sorted(team_ratios.items(), key=lambda x: abs(x[1][0] - 0.5))
        t = balanced[0]
        result.append((t[0], f"후반 비중 {t[1][0]:.1%} (균형형)"))

        return self._exclude_and_pick(result, 'g1')

    def _recommend_g2(self) -> list:
        """g2 역전: 역전승 최다 + 역전패 최다 + 선행승 최다"""
        schedules = self._cache['schedules']
        score_boards = self._cache['score_boards']
        if schedules.empty or score_boards.empty:
            return []

        from game.g2_comeback_pattern import _analyze_comeback
        team_comebacks = {}
        for tid in TEAM_NAMES:
            r = _analyze_comeback(schedules, score_boards, tid)
            team_comebacks[tid] = r

        result = []
        # 역전승 최다
        sorted_cw = sorted(team_comebacks.items(), key=lambda x: x[1]['comeback_win'], reverse=True)
        t = sorted_cw[0]
        result.append((t[0], f"역전승 {t[1]['comeback_win']}회 (최다)"))
        # 선행승 최다
        sorted_lw = sorted(team_comebacks.items(), key=lambda x: x[1]['lead_win'], reverse=True)
        t = sorted_lw[0]
        result.append((t[0], f"선행승 {t[1]['lead_win']}회 (최다)"))
        # 역전승 비율 최고
        comeback_rates = {}
        for tid, r in team_comebacks.items():
            total_w = r['lead_win'] + r['comeback_win']
            comeback_rates[tid] = r['comeback_win'] / total_w if total_w > 0 else 0
        sorted_cr = sorted(comeback_rates.items(), key=lambda x: x[1], reverse=True)
        t = sorted_cr[0]
        result.append((t[0], f"역전승 비율 {t[1]:.1%} (최고)"))

        return self._exclude_and_pick(result, 'g2')

    def _recommend_g3(self) -> list:
        """g3 키플레이어: WPA 등장 최다 + 분산 팀 + 투수 비중 최고"""
        key_players = self._cache.get('key_players')
        if key_players is None or key_players.empty:
            return [(1, 'KIA (데이터 미확인)')]

        wpa_top = key_players[
            (key_players['metric'] == 'GAME_WPA_RT') & (key_players['ranking'] == 1)
        ]

        result = []
        # 등장 횟수 최다
        team_counts = wpa_top.groupby('teamId').size()
        if not team_counts.empty:
            top = team_counts.idxmax()
            result.append((int(top), f"WPA 1위 {team_counts[top]}회 (최다)"))

        # 키플레이어 분산 (고유 선수 수 최다)
        team_unique = wpa_top.groupby('teamId')['playerName'].nunique()
        if not team_unique.empty:
            top = team_unique.idxmax()
            result.append((int(top), f"키플레이어 {team_unique[top]}명 (가장 분산)"))

        # 투수 키플레이어 비중 최고
        pitcher_ratios = {}
        for tid in TEAM_NAMES:
            t_wpa = wpa_top[wpa_top['teamId'] == tid]
            if len(t_wpa) > 0:
                p_count = len(t_wpa[t_wpa['playerType'] == 'PITCHER'])
                pitcher_ratios[tid] = p_count / len(t_wpa)
        if pitcher_ratios:
            top = max(pitcher_ratios, key=pitcher_ratios.get)
            result.append((top, f"투수 키플레이어 비중 {pitcher_ratios[top]:.1%}"))

        return self._exclude_and_pick(result, 'g3')

    def _recommend_g4(self) -> list:
        """g4 승리 요인: 안타 효율 최고 + 실책 최다 + 볼넷 최다"""
        schedules = self._cache['schedules']
        score_boards = self._cache['score_boards']
        if schedules.empty or score_boards.empty:
            return []

        from game.g4_win_factor import _build_game_data
        efficiencies = {}
        error_counts = {}
        bb_counts = {}

        for tid in TEAM_NAMES:
            gd = _build_game_data(schedules, score_boards, tid)
            if gd.empty:
                continue
            # 안타 대비 득점 효율
            total_h = gd['team_h'].sum()
            total_r = gd['team_r'].sum()
            efficiencies[tid] = total_r / total_h if total_h > 0 else 0
            error_counts[tid] = gd['team_e'].sum()
            bb_counts[tid] = gd['team_b'].sum()

        result = []
        if efficiencies:
            top = max(efficiencies, key=efficiencies.get)
            result.append((top, f"안타당 득점 {efficiencies[top]:.3f} (최고 효율)"))
        if error_counts:
            top = max(error_counts, key=error_counts.get)
            result.append((top, f"실책 {int(error_counts[top])}개 (최다)"))
        if bb_counts:
            top = max(bb_counts, key=bb_counts.get)
            result.append((top, f"볼넷 {int(bb_counts[top])}개 (최다)"))

        return self._exclude_and_pick(result, 'g4')

    def _recommend_g5(self) -> list:
        """g5 경기 시간: 최장 + 최단 + 시간-득점 상관 최고"""
        schedules = self._cache['schedules']
        score_boards = self._cache['score_boards']
        if schedules.empty or score_boards.empty:
            return []

        from game.g5_game_time import _build_time_data
        time_data = _build_time_data(schedules, score_boards)
        if time_data.empty:
            return []

        team_times = {}
        for tid in TEAM_NAMES:
            mask = (time_data['homeTeamId'] == tid) | (time_data['awayTeamId'] == tid)
            tg = time_data[mask]
            if len(tg) > 0:
                team_times[tid] = tg['gameTime'].mean()

        result = []
        if team_times:
            longest = max(team_times, key=team_times.get)
            result.append((longest, f"평균 {team_times[longest]:.1f}분 (최장)"))
            shortest = min(team_times, key=team_times.get)
            result.append((shortest, f"평균 {team_times[shortest]:.1f}분 (최단)"))
            # 중간
            sorted_t = sorted(team_times.items(), key=lambda x: x[1])
            mid = sorted_t[len(sorted_t) // 2]
            result.append((mid[0], f"평균 {mid[1]:.1f}분 (중간)"))

        return self._exclude_and_pick(result, 'g5')

    def _recommend_g6(self) -> list:
        """g6 접전/대파: 접전 비율 최고 + 대파 비율 최고 + 접전 승률 최고"""
        schedules = self._cache['schedules']
        if schedules.empty:
            return []

        from game.g6_blowout_close import _calc_margin_stats
        close_rates = {}
        blowout_rates = {}
        close_wr = {}

        for tid in TEAM_NAMES:
            tg = get_team_games(schedules, tid)
            ms = _calc_margin_stats(tg)
            total = sum(ms[c]['total'] for c in ['접전', '중간', '대파'])
            if total == 0:
                continue
            close_rates[tid] = ms['접전']['total'] / total
            blowout_rates[tid] = ms['대파']['total'] / total
            c = ms['접전']
            close_wr[tid] = c['win'] / c['total'] if c['total'] > 0 else 0

        result = []
        if close_rates:
            top = max(close_rates, key=close_rates.get)
            result.append((top, f"접전 비율 {close_rates[top]:.1%} (최고)"))
        if blowout_rates:
            top = max(blowout_rates, key=blowout_rates.get)
            result.append((top, f"대파 비율 {blowout_rates[top]:.1%} (최고)"))
        if close_wr:
            top = max(close_wr, key=close_wr.get)
            result.append((top, f"접전 승률 {close_wr[top]:.1%} (최고)"))

        return self._exclude_and_pick(result, 'g6')

    def _recommend_g7(self) -> list:
        """g7 라인업: 안정성 최고 + 최저 + 1번 타자 변경 최다"""
        lineups = self._cache.get('lineups')
        if lineups is None or lineups.empty:
            return [(1, '데이터 미확인')]

        from game.g7_lineup_effect import _calc_lineup_stability
        stabilities = {}
        leadoff_changes = {}

        for tid in TEAM_NAMES:
            s = _calc_lineup_stability(lineups, tid)
            stabilities[tid] = s['stability']
            # 1번 타자 변경 빈도
            t_lo = lineups[(lineups['teamId'] == tid) & (lineups['order'] == 1)]
            leadoff_changes[tid] = t_lo['playerName'].nunique() if not t_lo.empty else 0

        result = []
        if stabilities:
            top = max(stabilities, key=stabilities.get)
            result.append((top, f"안정성 {stabilities[top]:.1f}% (최고)"))
            bot = min(stabilities, key=stabilities.get)
            result.append((bot, f"안정성 {stabilities[bot]:.1f}% (최저)"))
        if leadoff_changes:
            top = max(leadoff_changes, key=leadoff_changes.get)
            result.append((top, f"1번 타자 {leadoff_changes[top]}명 (최다 변경)"))

        return self._exclude_and_pick(result, 'g7')

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
        description='경기 분석 일괄 실행기',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예시:
  python3 game/run_all.py --dry-run                   추천만 확인
  python3 game/run_all.py --analysis g1,g3             특정 분석만 실행
  python3 game/run_all.py --team g1:1                  g1에 팀 ID 1 (KIA) 수동 지정
  python3 game/run_all.py --game-type postseason       포스트시즌 분석
  python3 game/run_all.py --season 2024                2024 시즌
  python3 game/run_all.py --history                    실행 이력 조회
  python3 game/run_all.py --reset-history              이력 초기화 후 실행
        """
    )
    parser.add_argument('--analysis', type=str, default=None,
                        help='실행할 분석 (쉼표 구분, 예: g1,g3,g6)')
    parser.add_argument('--game-type', type=str, default='regular',
                        choices=list(GAME_TYPES.keys()),
                        help='경기유형 (기본: regular)')
    parser.add_argument('--season', type=int, default=LATEST_SEASON,
                        help=f'분석 대상 시즌 (기본: {LATEST_SEASON})')
    parser.add_argument('--dry-run', action='store_true',
                        help='추천만 출력하고 분석 실행하지 않음')
    parser.add_argument('--team', action='append', default=[],
                        help='특정 분석에 팀 수동 지정 (예: g1:1)')
    parser.add_argument('--history', action='store_true',
                        help='이전 실행 이력 조회')
    parser.add_argument('--reset-history', action='store_true',
                        help='이력 초기화')
    return parser.parse_args()


def parse_manual_teams(team_args: list) -> dict:
    """--team g1:1 형태의 인자를 파싱 → {분석키: [팀ID]}"""
    manual = {}
    for arg in team_args:
        if ':' not in arg:
            print(f"  [경고] 잘못된 형식: {arg} (예: g1:1)")
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


def print_recommendation_table(recommendations: dict, game_type: str):
    gt_label = get_game_type_label(game_type)
    print("\n" + "=" * 80)
    print(f"  팀 자동 추천 결과 ({gt_label})")
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


def print_history(recommender: GameRecommender):
    history = recommender._history
    if not history:
        print("\n  실행 이력이 없습니다.")
        return

    print("\n" + "=" * 80)
    print("  실행 이력")
    print("=" * 80)

    for key in sorted(history.keys()):
        entries = history[key]
        parts = key.rsplit('_', 1)
        analysis_key = parts[0] if len(parts) > 1 else key
        gt = parts[1] if len(parts) > 1 else 'regular'
        name = ANALYSIS_NAMES.get(analysis_key, analysis_key)
        gt_label = get_game_type_label(gt)
        print(f"\n  [{analysis_key}] {name} ({gt_label}) — {len(entries)}팀 분석 완료")
        for e in entries:
            print(f"    - [{e['team_id']:>2}] {e['team_name']:<6} ({e['run_date']})")

    total = sum(len(v) for v in history.values())
    print(f"\n  총 {total}건 완료")
    print("=" * 80)


def run_analysis(key: str, team_id: int, team_name: str,
                 game_type: str, recommender: GameRecommender) -> bool:
    module = MODULES[key]
    analysis_name = ANALYSIS_NAMES.get(key, key)
    gt_label = get_game_type_label(game_type)

    print(f"\n{'─' * 60}")
    print(f"  실행: [{key}] {analysis_name} — {team_name} (ID: {team_id}) [{gt_label}]")
    print(f"{'─' * 60}")

    start = time.time()
    try:
        module.analyze(team_id, game_type)
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
    recommender = GameRecommender(db, game_type=args.game_type, season=args.season)

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
        gt_label = get_game_type_label(args.game_type)
        print("\n" + "=" * 80)
        print(f"  경기 분석 자동 추천 시스템 ({gt_label}, {args.season} 시즌)")
        print("=" * 80)

        recommender._load_all()

        history_summary = recommender.get_history_summary()
        if history_summary:
            print(f"\n  [이력] 이전 실행: "
                  + ", ".join(f"{k}={v}팀" for k, v in sorted(history_summary.items())))

        recommendations = {}
        for key in target_keys:
            if key in manual:
                recs = []
                for tid in manual[key]:
                    recs.append((tid, get_team_name(tid), "수동 지정"))
                recommendations[key] = recs
            else:
                recommendations[key] = recommender.recommend_for(key)

        print_recommendation_table(recommendations, args.game_type)

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
                ok = run_analysis(key, tid, tname, args.game_type, recommender)
                if ok:
                    success_count += 1
                else:
                    fail_count += 1

        # 결과 요약
        total_elapsed = time.time() - total_start
        print("\n" + "=" * 80)
        print(f"  실행 결과 요약 ({gt_label})")
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
