"""
선수 자동 추천 엔진.
각 분석(p1~p9)에 최적인 선수 3명을 DB 데이터 기반으로 추천.
- 분석별 선정 기준에 따라 최적 후보 선별
- 이전 실행 이력 기반 중복 선수 제외
- 분석 간 동일 선수 중복 사용 최소화
"""
import os
import json
import numpy as np
import pandas as pd
from datetime import datetime

HISTORY_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), 'output', 'run_history.json'
)

from config import SEASONS, DATA_SEASON

LATEST_SEASON = DATA_SEASON


class PlayerRecommender:
    def __init__(self, db):
        self.db = db
        self.season = LATEST_SEASON
        self._cache = {}
        self._history = self._load_history()
        # 현재 세션에서 이미 추천된 선수 ID 추적 (분석 간 중복 최소화)
        self._used_ids = set()

    # ==================== 히스토리 관리 ====================

    def _load_history(self) -> dict:
        """이전 실행 이력 로드 (분석별 선수 ID 목록)"""
        if os.path.exists(HISTORY_PATH):
            try:
                with open(HISTORY_PATH, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except (json.JSONDecodeError, IOError):
                return {}
        return {}

    def save_history(self, key: str, player_id: int, player_name: str):
        """실행 완료된 (분석키, 선수) 조합을 이력에 기록"""
        if key not in self._history:
            self._history[key] = []
        entry = {
            'player_id': player_id,
            'player_name': player_name,
            'run_date': datetime.now().strftime('%Y-%m-%d'),
        }
        # 동일 선수 중복 기록 방지
        existing_ids = [e['player_id'] for e in self._history[key]]
        if player_id not in existing_ids:
            self._history[key].append(entry)

        os.makedirs(os.path.dirname(HISTORY_PATH), exist_ok=True)
        with open(HISTORY_PATH, 'w', encoding='utf-8') as f:
            json.dump(self._history, f, ensure_ascii=False, indent=2)

    def _get_analyzed_ids(self, key: str) -> set:
        """특정 분석에서 이미 분석 완료된 선수 ID 집합"""
        entries = self._history.get(key, [])
        return {e['player_id'] for e in entries}

    # ==================== 데이터 캐싱 ====================

    def _load_all(self):
        """8개 쿼리로 전체 데이터 1회 캐싱"""
        if self._cache:
            return

        print("  [추천] 데이터 로딩 중...")

        # 1. 최신 시즌 타자/투수
        self._cache['batters'] = self.db.get_batters(self.season)
        self._cache['pitchers'] = self.db.get_pitchers(self.season)

        # 2. 다시즌 보유 선수 (4시즌 이상)
        self._cache['multi_batter'] = self.db.query("""
            SELECT playerId, COUNT(DISTINCT season) AS season_count
            FROM player_batter_stats
            WHERE series = '0'
              AND (situationType = '' OR situationType IS NULL)
            GROUP BY playerId
            HAVING season_count >= 4
        """)
        self._cache['multi_pitcher'] = self.db.query("""
            SELECT playerId, COUNT(DISTINCT season) AS season_count
            FROM player_pitcher_stats
            WHERE series = '0'
              AND (situationType = '' OR situationType IS NULL)
            GROUP BY playerId
            HAVING season_count >= 4
        """)

        # 3. 선수 기본 정보
        self._cache['players_info'] = self.db.query("""
            SELECT p.id AS playerId, p.name, p.position, p.birthDate,
                   p.throwBat, t.name AS teamName
            FROM player p
            JOIN team t ON p.teamId = t.id
        """)

        # 4. 경기 기록 수
        self._cache['batter_game_counts'] = self.db.query("""
            SELECT playerId, COUNT(*) AS game_count
            FROM kbo_batter_record
            GROUP BY playerId
        """)
        self._cache['pitcher_game_counts'] = self.db.query("""
            SELECT playerId, COUNT(*) AS game_count
            FROM kbo_pitcher_record
            GROUP BY playerId
        """)

        # 5. 도루 기록
        self._cache['runners'] = self.db.query(f"""
            SELECT playerId, value AS sb_count
            FROM player_runner_stats
            WHERE season = %s AND series = '0' AND category = 'SB'
        """, (self.season,))

        print(f"  [추천] 타자 {len(self._cache['batters'])}명, "
              f"투수 {len(self._cache['pitchers'])}명 로딩 완료")

    # ==================== 유틸 ====================

    def _safe_float(self, val, default=0.0):
        try:
            v = float(val)
            return v if not np.isnan(v) else default
        except (TypeError, ValueError):
            return default

    def _exclude_and_pick(self, candidates: pd.DataFrame, key: str,
                          n: int = 3, id_col: str = 'playerId') -> pd.DataFrame:
        """
        후보에서 이미 분석된 선수 + 현재 세션 중복 선수를 제외하고 상위 n명 선택.
        제외 후 후보가 부족하면 세션 중복만 해제하여 재시도.
        """
        analyzed_ids = self._get_analyzed_ids(key)
        # 1차: 이력 + 세션 중복 모두 제외
        mask = ~candidates[id_col].isin(analyzed_ids | self._used_ids)
        filtered = candidates[mask]

        if len(filtered) >= n:
            picked = filtered.head(n)
        else:
            # 2차: 이력만 제외 (세션 중복은 허용)
            mask2 = ~candidates[id_col].isin(analyzed_ids)
            filtered2 = candidates[mask2]
            if len(filtered2) >= n:
                picked = filtered2.head(n)
            else:
                # 3차: 제외 없이 상위 선택 (후보 부족 시)
                picked = candidates.head(n)

        # 선택된 선수를 세션 추적에 등록
        for pid in picked[id_col].tolist():
            self._used_ids.add(pid)

        return picked

    def _get_name(self, player_id: int) -> str:
        """선수 ID → 이름"""
        info = self._cache.get('players_info', pd.DataFrame())
        if info.empty:
            return f'ID:{player_id}'
        row = info[info['playerId'] == player_id]
        return row.iloc[0]['name'] if not row.empty else f'ID:{player_id}'

    # ==================== 분석별 추천 ====================

    def _recommend_p1(self) -> list:
        """p1 시즌 트렌드: 다시즌(>=4) 보유 AND 최근 OPS/ERA 상위 → 타자2 + 투수1"""
        batters = self._cache['batters'].copy()
        pitchers = self._cache['pitchers'].copy()
        multi_bat = self._cache['multi_batter']
        multi_pit = self._cache['multi_pitcher']

        # 다시즌 타자 중 OPS 상위
        bat_multi = batters[batters['playerId'].isin(multi_bat['playerId'])]
        if 'OPS' in bat_multi.columns:
            bat_multi = bat_multi.copy()
            bat_multi['OPS_f'] = bat_multi['OPS'].apply(self._safe_float)
            bat_multi = bat_multi.sort_values('OPS_f', ascending=False)
        bat_picked = self._exclude_and_pick(bat_multi, 'p1', n=2)

        # 다시즌 투수 중 ERA 하위 (낮을수록 좋음)
        pit_multi = pitchers[pitchers['playerId'].isin(multi_pit['playerId'])]
        if 'ERA' in pit_multi.columns:
            pit_multi = pit_multi.copy()
            pit_multi['ERA_f'] = pit_multi['ERA'].apply(self._safe_float)
            pit_multi = pit_multi[pit_multi['ERA_f'] > 0]
            pit_multi = pit_multi.sort_values('ERA_f', ascending=True)
        pit_picked = self._exclude_and_pick(pit_multi, 'p1', n=1)

        results = []
        for _, row in bat_picked.iterrows():
            results.append((int(row['playerId']),
                            f"다시즌 보유 타자, OPS {self._safe_float(row.get('OPS', 0)):.3f}"))
        for _, row in pit_picked.iterrows():
            results.append((int(row['playerId']),
                            f"다시즌 보유 투수, ERA {self._safe_float(row.get('ERA', 0)):.2f}"))
        return results

    def _recommend_p2(self) -> list:
        """p2 세이버메트릭스: 규정타석 ISO/BB% 최고 타자 2명 + 규정이닝 K/9 최고 투수 1명"""
        batters = self._cache['batters'].copy()
        pitchers = self._cache['pitchers'].copy()

        # 규정타석(PA>=300) 타자
        if 'PA' in batters.columns:
            batters['PA_f'] = batters['PA'].apply(self._safe_float)
            qual_bat = batters[batters['PA_f'] >= 300].copy()
        else:
            qual_bat = batters.copy()

        results = []
        # ISO 최고 타자
        if 'ISO' in qual_bat.columns:
            qual_bat['ISO_f'] = qual_bat['ISO'].apply(self._safe_float)
            iso_sorted = qual_bat.sort_values('ISO_f', ascending=False)
            iso_pick = self._exclude_and_pick(iso_sorted, 'p2', n=1)
            for _, row in iso_pick.iterrows():
                results.append((int(row['playerId']),
                                f"규정타석 ISO {self._safe_float(row.get('ISO', 0)):.3f} (장타력 최고)"))

        # BB% 최고 타자
        bb_col = 'BB%' if 'BB%' in qual_bat.columns else 'BB_pct'
        if bb_col in qual_bat.columns:
            qual_bat['BB_f'] = qual_bat[bb_col].apply(self._safe_float)
            bb_sorted = qual_bat.sort_values('BB_f', ascending=False)
            bb_pick = self._exclude_and_pick(bb_sorted, 'p2', n=1)
            for _, row in bb_pick.iterrows():
                results.append((int(row['playerId']),
                                f"규정타석 BB% {self._safe_float(row.get(bb_col, 0)):.1f} (선구안 최고)"))

        # 규정이닝(IP>=100) K/9 최고 투수
        if 'IP' in pitchers.columns:
            pitchers['IP_f'] = pitchers['IP'].apply(self._safe_float)
            qual_pit = pitchers[pitchers['IP_f'] >= 100].copy()
        else:
            qual_pit = pitchers.copy()

        k9_col = 'K/9' if 'K/9' in qual_pit.columns else 'SO9'
        if k9_col in qual_pit.columns:
            qual_pit['K9_f'] = qual_pit[k9_col].apply(self._safe_float)
            k9_sorted = qual_pit.sort_values('K9_f', ascending=False)
            k9_pick = self._exclude_and_pick(k9_sorted, 'p2', n=1)
            for _, row in k9_pick.iterrows():
                results.append((int(row['playerId']),
                                f"규정이닝 K/9 {self._safe_float(row.get(k9_col, 0)):.2f} (탈삼진 최고)"))

        return results[:3]

    def _recommend_p3(self) -> list:
        """p3 타자 유형: 파워(HR 1위), 컨택(K% 최저), 스피드(SB 1위) 타자 3명"""
        batters = self._cache['batters'].copy()
        runners = self._cache['runners']
        results = []

        # 파워: HR 1위
        if 'HR' in batters.columns:
            batters['HR_f'] = batters['HR'].apply(self._safe_float)
            hr_sorted = batters.sort_values('HR_f', ascending=False)
            hr_pick = self._exclude_and_pick(hr_sorted, 'p3', n=1)
            for _, row in hr_pick.iterrows():
                results.append((int(row['playerId']),
                                f"파워형: HR {int(self._safe_float(row.get('HR', 0)))}개"))

        # 컨택: K% 최저 (규정타석)
        k_col = 'K%' if 'K%' in batters.columns else 'SO_pct'
        if k_col in batters.columns and 'PA' in batters.columns:
            batters['PA_f'] = batters['PA'].apply(self._safe_float)
            qual = batters[batters['PA_f'] >= 300].copy()
            qual['K_f'] = qual[k_col].apply(self._safe_float)
            qual = qual[qual['K_f'] > 0]
            k_sorted = qual.sort_values('K_f', ascending=True)
            k_pick = self._exclude_and_pick(k_sorted, 'p3', n=1)
            for _, row in k_pick.iterrows():
                results.append((int(row['playerId']),
                                f"컨택형: K% {self._safe_float(row.get(k_col, 0)):.1f}%"))

        # 스피드: SB 1위
        if not runners.empty:
            runners_c = runners.copy()
            runners_c['sb_f'] = runners_c['sb_count'].apply(self._safe_float)
            sb_sorted = runners_c.sort_values('sb_f', ascending=False)
            sb_pick = self._exclude_and_pick(sb_sorted, 'p3', n=1)
            for _, row in sb_pick.iterrows():
                results.append((int(row['playerId']),
                                f"스피드형: SB {int(self._safe_float(row.get('sb_count', 0)))}개"))

        return results[:3]

    def _recommend_p4(self) -> list:
        """p4 투수 유형: 탈삼진(K/9 1위), 땅볼(GO/AO 1위), 제구(BB/9 최저) 투수 3명"""
        pitchers = self._cache['pitchers'].copy()
        if 'IP' in pitchers.columns:
            pitchers['IP_f'] = pitchers['IP'].apply(self._safe_float)
            qual = pitchers[pitchers['IP_f'] >= 100].copy()
        else:
            qual = pitchers.copy()

        results = []

        # 탈삼진: K/9 1위
        k9_col = 'K/9' if 'K/9' in qual.columns else 'SO9'
        if k9_col in qual.columns:
            qual['K9_f'] = qual[k9_col].apply(self._safe_float)
            k9_sorted = qual.sort_values('K9_f', ascending=False)
            k9_pick = self._exclude_and_pick(k9_sorted, 'p4', n=1)
            for _, row in k9_pick.iterrows():
                results.append((int(row['playerId']),
                                f"탈삼진형: K/9 {self._safe_float(row.get(k9_col, 0)):.2f}"))

        # 땅볼: GO/AO 1위
        goao_col = 'GO/AO' if 'GO/AO' in qual.columns else 'GOAO'
        if goao_col in qual.columns:
            qual['GOAO_f'] = qual[goao_col].apply(self._safe_float)
            goao_sorted = qual.sort_values('GOAO_f', ascending=False)
            goao_pick = self._exclude_and_pick(goao_sorted, 'p4', n=1)
            for _, row in goao_pick.iterrows():
                results.append((int(row['playerId']),
                                f"땅볼형: GO/AO {self._safe_float(row.get(goao_col, 0)):.2f}"))

        # 제구: BB/9 최저
        bb9_col = 'BB/9' if 'BB/9' in qual.columns else 'BB9'
        if bb9_col in qual.columns:
            qual['BB9_f'] = qual[bb9_col].apply(self._safe_float)
            bb9_valid = qual[qual['BB9_f'] > 0]
            bb9_sorted = bb9_valid.sort_values('BB9_f', ascending=True)
            bb9_pick = self._exclude_and_pick(bb9_sorted, 'p4', n=1)
            for _, row in bb9_pick.iterrows():
                results.append((int(row['playerId']),
                                f"제구형: BB/9 {self._safe_float(row.get(bb9_col, 0)):.2f}"))

        return results[:3]

    def _recommend_p5(self) -> list:
        """p5 나이 커브: 베테랑(birthYear<=1993) AND 다시즌(>=4) AND 상위 → 타자2 + 투수1"""
        batters = self._cache['batters'].copy()
        pitchers = self._cache['pitchers'].copy()
        multi_bat = self._cache['multi_batter']
        multi_pit = self._cache['multi_pitcher']
        info = self._cache['players_info']

        # 베테랑 선수 필터 (1993년 이전 출생)
        if 'birthDate' in info.columns:
            info_c = info.copy()
            info_c['birthDate'] = pd.to_datetime(info_c['birthDate'], errors='coerce')
            veterans = info_c[info_c['birthDate'].dt.year <= 1993]['playerId']
        else:
            veterans = info['playerId']

        results = []

        # 베테랑 + 다시즌 타자 → OPS 상위
        vet_multi_bat = batters[
            batters['playerId'].isin(veterans) &
            batters['playerId'].isin(multi_bat['playerId'])
        ].copy()
        if 'OPS' in vet_multi_bat.columns:
            vet_multi_bat['OPS_f'] = vet_multi_bat['OPS'].apply(self._safe_float)
            vet_multi_bat = vet_multi_bat.sort_values('OPS_f', ascending=False)
        bat_picked = self._exclude_and_pick(vet_multi_bat, 'p5', n=2)
        for _, row in bat_picked.iterrows():
            results.append((int(row['playerId']),
                            f"베테랑 다시즌 타자, OPS {self._safe_float(row.get('OPS', 0)):.3f}"))

        # 베테랑 + 다시즌 투수 → ERA 하위
        vet_multi_pit = pitchers[
            pitchers['playerId'].isin(veterans) &
            pitchers['playerId'].isin(multi_pit['playerId'])
        ].copy()
        if 'ERA' in vet_multi_pit.columns:
            vet_multi_pit['ERA_f'] = vet_multi_pit['ERA'].apply(self._safe_float)
            vet_multi_pit = vet_multi_pit[vet_multi_pit['ERA_f'] > 0]
            vet_multi_pit = vet_multi_pit.sort_values('ERA_f', ascending=True)
        pit_picked = self._exclude_and_pick(vet_multi_pit, 'p5', n=1)
        for _, row in pit_picked.iterrows():
            results.append((int(row['playerId']),
                            f"베테랑 다시즌 투수, ERA {self._safe_float(row.get('ERA', 0)):.2f}"))

        return results[:3]

    def _recommend_p6(self) -> list:
        """p6 변동성: 경기 기록 수 최다 → 타자2 + 투수1"""
        bat_counts = self._cache['batter_game_counts'].copy()
        pit_counts = self._cache['pitcher_game_counts'].copy()

        results = []

        # 타자 경기 기록 수 최다
        bat_sorted = bat_counts.sort_values('game_count', ascending=False)
        bat_picked = self._exclude_and_pick(bat_sorted, 'p6', n=2)
        for _, row in bat_picked.iterrows():
            results.append((int(row['playerId']),
                            f"타자 경기기록 {int(row['game_count'])}건 (변동성 분석 최적)"))

        # 투수 경기 기록 수 최다
        pit_sorted = pit_counts.sort_values('game_count', ascending=False)
        pit_picked = self._exclude_and_pick(pit_sorted, 'p6', n=1)
        for _, row in pit_picked.iterrows():
            results.append((int(row['playerId']),
                            f"투수 경기기록 {int(row['game_count'])}건 (변동성 분석 최적)"))

        return results[:3]

    def _recommend_p7(self) -> list:
        """p7 포지션별 공격력: 포수/내야수/외야수 각 OPS 1위 타자 3명"""
        batters = self._cache['batters'].copy()
        info = self._cache['players_info']

        # 규정타석 필터 (PA>=200)
        if 'PA' in batters.columns:
            batters['PA_f'] = batters['PA'].apply(self._safe_float)
            batters = batters[batters['PA_f'] >= 200].copy()

        # 실제 포지션 매핑
        pos_map = info[['playerId', 'position']].drop_duplicates()
        merged = batters.merge(pos_map, on='playerId', how='left', suffixes=('', '_real'))
        if 'position_real' in merged.columns:
            merged['real_pos'] = merged['position_real']
        else:
            merged['real_pos'] = merged['position']

        # 포지션 그룹
        pos_groups = {
            '포수': ['포수'],
            '내야수': ['내야수', '1루수', '2루수', '3루수', '유격수'],
            '외야수': ['외야수', '좌익수', '중견수', '우익수'],
        }

        results = []
        if 'OPS' in merged.columns:
            merged['OPS_f'] = merged['OPS'].apply(self._safe_float)

            for group_name, positions in pos_groups.items():
                group = merged[merged['real_pos'].isin(positions)].copy()
                group = group.sort_values('OPS_f', ascending=False)
                pick = self._exclude_and_pick(group, 'p7', n=1)
                for _, row in pick.iterrows():
                    results.append((int(row['playerId']),
                                    f"{group_name} OPS {self._safe_float(row.get('OPS', 0)):.3f} (포지션 1위)"))

        return results[:3]

    def _recommend_p8(self) -> list:
        """p8 투타 유형: 좌타/우타/양타 각 OPS 1위 타자 3명"""
        batters = self._cache['batters'].copy()
        info = self._cache['players_info']

        # 규정타석 필터 (PA>=200)
        if 'PA' in batters.columns:
            batters['PA_f'] = batters['PA'].apply(self._safe_float)
            batters = batters[batters['PA_f'] >= 200].copy()

        # throwBat → 타석 유형 파싱
        tb_map = info[['playerId', 'throwBat']].drop_duplicates()
        merged = batters.merge(tb_map, on='playerId', how='left')

        def parse_bat_hand(tb):
            if not tb or not isinstance(tb, str):
                return '미상'
            tb = tb.strip()
            if len(tb) >= 4:
                return tb[2:]
            if len(tb) == 2:
                return tb
            return tb

        merged['bat_hand'] = merged['throwBat'].apply(parse_bat_hand)

        results = []
        if 'OPS' in merged.columns:
            merged['OPS_f'] = merged['OPS'].apply(self._safe_float)

            for bat_type in ['좌타', '우타', '양타']:
                group = merged[merged['bat_hand'] == bat_type].copy()
                group = group.sort_values('OPS_f', ascending=False)
                pick = self._exclude_and_pick(group, 'p8', n=1)
                for _, row in pick.iterrows():
                    results.append((int(row['playerId']),
                                    f"{bat_type} OPS {self._safe_float(row.get('OPS', 0)):.3f} (유형 1위)"))

        return results[:3]

    def _recommend_p9(self) -> list:
        """p9 BABIP 행운: BABIP 최고(행운) + 최저(불운) + 다시즌 보유(추이)"""
        batters = self._cache['batters'].copy()
        multi_bat = self._cache['multi_batter']

        results = []

        if 'BABIP' in batters.columns:
            batters['BABIP_f'] = batters['BABIP'].apply(self._safe_float)
            # 규정타석 필터
            if 'PA' in batters.columns:
                batters['PA_f'] = batters['PA'].apply(self._safe_float)
                qual = batters[batters['PA_f'] >= 300].copy()
            else:
                qual = batters.copy()

            valid = qual[qual['BABIP_f'] > 0]

            # BABIP 최고 (행운)
            lucky = valid.sort_values('BABIP_f', ascending=False)
            lucky_pick = self._exclude_and_pick(lucky, 'p9', n=1)
            for _, row in lucky_pick.iterrows():
                results.append((int(row['playerId']),
                                f"BABIP {self._safe_float(row.get('BABIP', 0)):.3f} (행운 최고)"))

            # BABIP 최저 (불운)
            unlucky = valid.sort_values('BABIP_f', ascending=True)
            unlucky_pick = self._exclude_and_pick(unlucky, 'p9', n=1)
            for _, row in unlucky_pick.iterrows():
                results.append((int(row['playerId']),
                                f"BABIP {self._safe_float(row.get('BABIP', 0)):.3f} (불운 최저)"))

            # 다시즌 보유 (BABIP 추이 분석용)
            multi_babip = valid[valid['playerId'].isin(multi_bat['playerId'])]
            multi_babip = multi_babip.sort_values('BABIP_f', ascending=False)
            multi_pick = self._exclude_and_pick(multi_babip, 'p9', n=1)
            for _, row in multi_pick.iterrows():
                results.append((int(row['playerId']),
                                f"다시즌 BABIP {self._safe_float(row.get('BABIP', 0)):.3f} (추이 분석용)"))

        return results[:3]

    # ==================== 공개 API ====================

    def recommend_for(self, key: str) -> list:
        """
        특정 분석에 대한 추천 선수 반환.
        반환: [(player_id, reason), ...]
        """
        self._load_all()
        method = getattr(self, f'_recommend_{key}', None)
        if method is None:
            raise ValueError(f"알 수 없는 분석 키: {key}")
        recs = method()
        # 선수 이름 보강
        enriched = []
        for pid, reason in recs:
            name = self._get_name(pid)
            enriched.append((pid, name, reason))
        return enriched

    def recommend_all(self) -> dict:
        """
        전체 분석(p1~p9)에 대한 추천 결과 반환.
        반환: {key: [(player_id, name, reason), ...]}

        분석 순서대로 추천하며, 이전 분석에서 추천된 선수는
        다음 분석에서 가능한 한 제외 (중복 최소화).
        """
        self._load_all()
        self._used_ids.clear()  # 세션 추적 초기화

        results = {}
        for key in ['p1', 'p2', 'p3', 'p4', 'p5', 'p6', 'p7', 'p8', 'p9']:
            results[key] = self.recommend_for(key)
        return results

    def get_history_summary(self) -> dict:
        """이력 요약 (분석별 분석 완료 선수 수)"""
        return {k: len(v) for k, v in self._history.items()}
