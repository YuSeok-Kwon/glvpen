"""
MySQL 접속 및 데이터 조회 유틸.
Stats 테이블의 피봇 형태(category=지표명, value=수치)를
선수별 한 행에 모든 지표가 컬럼으로 들어간 DataFrame으로 변환.
"""
import mysql.connector
import pandas as pd
import json
from config import DB_CONFIG


class DBConnector:
    def __init__(self):
        self.conn = mysql.connector.connect(**DB_CONFIG)

    def query(self, sql: str, params: tuple = None) -> pd.DataFrame:
        """SQL 쿼리 → DataFrame 변환"""
        return pd.read_sql(sql, self.conn, params=params)

    # ==================== 타자 데이터 ====================

    def get_batters(self, season: int) -> pd.DataFrame:
        """
        시즌 전체 타자 스탯을 피봇 변환하여 반환.
        결과: playerId | position | WAR | AVG | OPS | HR | ... 형태의 DataFrame
        """
        sql = """
            SELECT bs.playerId, bs.category, bs.value, bs.position,
                   p.name AS playerName, t.name AS teamName, t.id AS teamId
            FROM player_batter_stats bs
            JOIN player p ON bs.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE bs.season = %s
              AND bs.series = '0'
              AND (bs.situationType = '' OR bs.situationType IS NULL)
        """
        raw = self.query(sql, (season,))
        if raw.empty:
            return pd.DataFrame()

        # 선수 메타 정보 추출 (피봇 전)
        meta = raw.groupby('playerId').first()[['playerName', 'teamName', 'teamId', 'position']].reset_index()

        # 피봇: category → 컬럼, value → 값
        pivoted = raw.pivot_table(
            index='playerId', columns='category', values='value', aggfunc='first'
        ).reset_index()

        # 메타 정보 병합
        result = meta.merge(pivoted, on='playerId', how='left')
        return result

    def get_batters_multi_season(self, seasons: list) -> pd.DataFrame:
        """다시즌 타자 데이터 (전년 비교용)"""
        placeholders = ','.join(['%s'] * len(seasons))
        sql = f"""
            SELECT bs.playerId, bs.season, bs.category, bs.value, bs.position,
                   p.name AS playerName, t.name AS teamName
            FROM player_batter_stats bs
            JOIN player p ON bs.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE bs.season IN ({placeholders})
              AND bs.series = '0'
              AND (bs.situationType = '' OR bs.situationType IS NULL)
        """
        raw = self.query(sql, tuple(seasons))
        if raw.empty:
            return pd.DataFrame()

        meta = raw.groupby(['playerId', 'season']).first()[['playerName', 'teamName', 'position']].reset_index()
        pivoted = raw.pivot_table(
            index=['playerId', 'season'], columns='category', values='value', aggfunc='first'
        ).reset_index()

        return meta.merge(pivoted, on=['playerId', 'season'], how='left')

    # ==================== 투수 데이터 ====================

    def get_pitchers(self, season: int) -> pd.DataFrame:
        """시즌 전체 투수 스탯을 피봇 변환하여 반환."""
        sql = """
            SELECT ps.playerId, ps.category, ps.value, ps.position,
                   p.name AS playerName, t.name AS teamName, t.id AS teamId
            FROM player_pitcher_stats ps
            JOIN player p ON ps.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE ps.season = %s
              AND ps.series = '0'
              AND (ps.situationType = '' OR ps.situationType IS NULL)
        """
        raw = self.query(sql, (season,))
        if raw.empty:
            return pd.DataFrame()

        meta = raw.groupby('playerId').first()[['playerName', 'teamName', 'teamId', 'position']].reset_index()
        pivoted = raw.pivot_table(
            index='playerId', columns='category', values='value', aggfunc='first'
        ).reset_index()

        return meta.merge(pivoted, on='playerId', how='left')

    def get_pitchers_multi_season(self, seasons: list) -> pd.DataFrame:
        """다시즌 투수 데이터"""
        placeholders = ','.join(['%s'] * len(seasons))
        sql = f"""
            SELECT ps.playerId, ps.season, ps.category, ps.value,
                   p.name AS playerName, t.name AS teamName
            FROM player_pitcher_stats ps
            JOIN player p ON ps.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE ps.season IN ({placeholders})
              AND ps.series = '0'
              AND (ps.situationType = '' OR ps.situationType IS NULL)
        """
        raw = self.query(sql, tuple(seasons))
        if raw.empty:
            return pd.DataFrame()

        meta = raw.groupby(['playerId', 'season']).first()[['playerName', 'teamName']].reset_index()
        pivoted = raw.pivot_table(
            index=['playerId', 'season'], columns='category', values='value', aggfunc='first'
        ).reset_index()

        return meta.merge(pivoted, on=['playerId', 'season'], how='left')

    # ==================== 기타 데이터 ====================

    def get_players_with_birth(self, season: int) -> pd.DataFrame:
        """선수 생년월일 포함 데이터 (나이 분석용)"""
        sql = """
            SELECT p.id AS playerId, p.name, p.birthDate, p.position, t.name AS teamName
            FROM player p
            JOIN team t ON p.teamId = t.id
            WHERE p.id IN (
                SELECT DISTINCT playerId FROM player_batter_stats WHERE season = %s AND series = '0'
                UNION
                SELECT DISTINCT playerId FROM player_pitcher_stats WHERE season = %s AND series = '0'
            )
        """
        return self.query(sql, (season, season))

    def get_team_rankings(self, season: int) -> pd.DataFrame:
        """팀 순위 데이터"""
        sql = """
            SELECT tr.teamId, t.name AS teamName, tr.ranking, tr.wins, tr.losses,
                   tr.draws, tr.winRate, tr.gamesBehind
            FROM team_ranking tr
            JOIN team t ON tr.teamId = t.id
            WHERE tr.season = %s
            ORDER BY tr.ranking ASC
        """
        return self.query(sql, (season,))

    def get_head_to_head(self, season: int) -> pd.DataFrame:
        """팀 간 상대전적"""
        sql = """
            SELECT h.teamId, h.opponentTeamId, h.wins, h.losses, h.draws,
                   t1.name AS teamName, t2.name AS opponentName
            FROM team_head_to_head h
            JOIN team t1 ON h.teamId = t1.id
            JOIN team t2 ON h.opponentTeamId = t2.id
            WHERE h.season = %s
        """
        return self.query(sql, (season,))

    def get_schedules(self, season: int) -> pd.DataFrame:
        """완료된 경기 일정"""
        sql = """
            SELECT s.id, s.gameDate, s.homeTeamId, s.awayTeamId,
                   s.homeScore, s.awayScore, s.stadium, s.status,
                   t1.name AS homeTeamName, t2.name AS awayTeamName
            FROM schedule s
            JOIN team t1 ON s.homeTeamId = t1.id
            JOIN team t2 ON s.awayTeamId = t2.id
            WHERE s.season = %s AND s.status = '완료'
        """
        return self.query(sql, (season,))

    def get_team_stats(self, season: int) -> pd.DataFrame:
        """팀 시즌 스탯을 피봇 변환하여 반환 (team_stats EAV → 넓은 형태)"""
        sql = """
            SELECT ts.teamId, t.name AS teamName, ts.category, ts.value
            FROM team_stats ts
            JOIN team t ON ts.teamId = t.id
            WHERE ts.season = %s
        """
        raw = self.query(sql, (season,))
        if raw.empty:
            return pd.DataFrame()
        meta = raw.groupby('teamId').first()[['teamName']].reset_index()
        pivoted = raw.pivot_table(
            index='teamId', columns='category', values='value', aggfunc='first'
        ).reset_index()
        return meta.merge(pivoted, on='teamId', how='left')

    def get_team_stats_multi_season(self, seasons: list) -> pd.DataFrame:
        """다시즌 팀 스탯 (피봇 변환)"""
        placeholders = ','.join(['%s'] * len(seasons))
        sql = f"""
            SELECT ts.teamId, t.name AS teamName, ts.season, ts.category, ts.value
            FROM team_stats ts
            JOIN team t ON ts.teamId = t.id
            WHERE ts.season IN ({placeholders})
        """
        raw = self.query(sql, tuple(seasons))
        if raw.empty:
            return pd.DataFrame()
        meta = raw.groupby(['teamId', 'season']).first()[['teamName']].reset_index()
        pivoted = raw.pivot_table(
            index=['teamId', 'season'], columns='category', values='value', aggfunc='first'
        ).reset_index()
        return meta.merge(pivoted, on=['teamId', 'season'], how='left')

    def get_score_boards(self, season: int) -> pd.DataFrame:
        """이닝별 스코어보드"""
        sql = """
            SELECT sb.*
            FROM score_board sb
            JOIN schedule s ON sb.scheduleId = s.id
            WHERE s.season = %s AND s.status = '완료'
        """
        return self.query(sql, (season,))

    def get_crowd_stats(self, season: int) -> pd.DataFrame:
        """관중 데이터"""
        sql = """
            SELECT cs.*, t.name AS teamName
            FROM crowd_stats cs
            JOIN team t ON cs.homeTeamId = t.id
            WHERE cs.season = %s
        """
        return self.query(sql, (season,))

    # ==================== 경기 기록 데이터 ====================

    def get_batter_records(self, season: int) -> pd.DataFrame:
        """경기별 타자 기록"""
        sql = """
            SELECT br.scheduleId, br.playerId, br.teamId,
                   br.pa, br.ab, br.hits, br.rbi, br.hr, br.sb, br.so, br.bb, br.runs,
                   p.name AS playerName, t.name AS teamName
            FROM kbo_batter_record br
            JOIN player p ON br.playerId = p.id
            JOIN team t ON br.teamId = t.id
            JOIN schedule s ON br.scheduleId = s.id
            WHERE s.season = %s AND s.status = '완료'
        """
        return self.query(sql, (season,))

    def get_pitcher_records(self, season: int) -> pd.DataFrame:
        """경기별 투수 기록"""
        sql = """
            SELECT pr.scheduleId, pr.playerId, pr.teamId,
                   pr.innings, pr.strikeouts, pr.bb, pr.hbp,
                   pr.runs, pr.earnedRuns, pr.hits, pr.hr,
                   pr.decision, pr.pitchCount, pr.battersFaced,
                   p.name AS playerName, t.name AS teamName
            FROM kbo_pitcher_record pr
            JOIN player p ON pr.playerId = p.id
            JOIN team t ON pr.teamId = t.id
            JOIN schedule s ON pr.scheduleId = s.id
            WHERE s.season = %s AND s.status = '완료'
        """
        return self.query(sql, (season,))

    def get_batter_lineups(self, season: int) -> pd.DataFrame:
        """경기별 타순"""
        sql = """
            SELECT bl.scheduleId, bl.teamId, bl.playerId,
                   bl.`order`, bl.position,
                   p.name AS playerName, t.name AS teamName
            FROM kbo_batter_lineup bl
            JOIN player p ON bl.playerId = p.id
            JOIN team t ON bl.teamId = t.id
            JOIN schedule s ON bl.scheduleId = s.id
            WHERE s.season = %s AND s.status = '완료'
        """
        return self.query(sql, (season,))

    def get_game_key_players(self, season: int) -> pd.DataFrame:
        """경기별 키플레이어"""
        sql = """
            SELECT gkp.scheduleId, gkp.season, gkp.playerType,
                   gkp.metric, gkp.ranking, gkp.playerName,
                   gkp.teamId, gkp.recordInfo,
                   t.name AS teamName
            FROM kbo_game_key_player gkp
            JOIN team t ON gkp.teamId = t.id
            WHERE gkp.season = %s
        """
        return self.query(sql, (season,))

    def get_futures_batters(self, season: int) -> pd.DataFrame:
        """퓨처스 리그 타자 데이터"""
        sql = """
            SELECT fb.playerId, fb.category, fb.value,
                   p.name AS playerName, t.name AS teamName
            FROM futures_batter_stats fb
            JOIN player p ON fb.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE fb.season = %s
        """
        raw = self.query(sql, (season,))
        if raw.empty:
            return pd.DataFrame()

        meta = raw.groupby('playerId').first()[['playerName', 'teamName']].reset_index()
        pivoted = raw.pivot_table(
            index='playerId', columns='category', values='value', aggfunc='first'
        ).reset_index()

        return meta.merge(pivoted, on='playerId', how='left')

    def get_futures_pitchers(self, season: int) -> pd.DataFrame:
        """퓨처스 리그 투수 데이터"""
        sql = """
            SELECT fp.playerId, fp.category, fp.value,
                   p.name AS playerName, t.name AS teamName
            FROM futures_pitcher_stats fp
            JOIN player p ON fp.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE fp.season = %s
        """
        raw = self.query(sql, (season,))
        if raw.empty:
            return pd.DataFrame()

        meta = raw.groupby('playerId').first()[['playerName', 'teamName']].reset_index()
        pivoted = raw.pivot_table(
            index='playerId', columns='category', values='value', aggfunc='first'
        ).reset_index()

        return meta.merge(pivoted, on='playerId', how='left')

    # ==================== 분석 결과 저장 ====================

    def _ensure_table_exists(self):
        """analysis_result 테이블 스키마 확인 및 자동 생성/재생성"""
        if hasattr(self, '_table_ensured'):
            return
        cursor = self.conn.cursor()

        # 테이블 존재 여부 및 컬럼 확인
        needs_recreate = False
        cursor.execute("SHOW TABLES LIKE 'analysis_result'")
        if cursor.fetchone():
            # 테이블은 존재 → stats_json 컬럼 확인
            cursor.execute("SHOW COLUMNS FROM analysis_result LIKE 'stats_json'")
            if not cursor.fetchone():
                # 잘못된 스키마 → 백업 후 재생성
                print("    [DB] analysis_result 테이블 스키마 불일치 - 재생성합니다...")
                cursor.execute("RENAME TABLE analysis_result TO analysis_result_backup")
                self.conn.commit()
                needs_recreate = True
        else:
            needs_recreate = True

        if needs_recreate or True:
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS analysis_result (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    topic INT NOT NULL COMMENT '주제 인덱스',
                    sub_topic VARCHAR(50) NOT NULL DEFAULT 'default' COMMENT '서브토픽',
                    season INT NOT NULL COMMENT '분석 대상 시즌',
                    comparison_type VARCHAR(20) NOT NULL DEFAULT 'none' COMMENT '비교 유형',
                    anchor_value VARCHAR(20) NOT NULL DEFAULT '' COMMENT '비교 앵커',
                    stats_json LONGTEXT NOT NULL COMMENT '기술통계 결과 (JSON)',
                    chart_json LONGTEXT NOT NULL COMMENT 'Chart.js 차트 데이터 (JSON)',
                    insight_text LONGTEXT NOT NULL COMMENT '통계 인사이트 텍스트',
                    hypothesis_results LONGTEXT COMMENT '가설검정 결과 (JSON)',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_topic_sub_season_comp (topic, sub_topic, season, comparison_type, anchor_value),
                    INDEX idx_season (season),
                    INDEX idx_topic_sub (topic, sub_topic)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """)
            self.conn.commit()

        cursor.close()
        self._table_ensured = True

    def _has_v7_columns(self) -> bool:
        """V7 마이그레이션(sub_topic 등) 적용 여부 확인"""
        if not hasattr(self, '_v7_checked'):
            try:
                cursor = self.conn.cursor()
                cursor.execute("SHOW COLUMNS FROM analysis_result LIKE 'sub_topic'")
                self._v7_checked = cursor.fetchone() is not None
                cursor.close()
            except:
                self._v7_checked = False
        return self._v7_checked

    def save_analysis_result(self, topic: int, season: int,
                             stats_json: str, chart_json: str,
                             insight_text: str, hypothesis_json: str,
                             sub_topic: str = 'default',
                             comparison_type: str = 'none',
                             anchor_value: str = ''):
        """
        분석 결과를 analysis_result 테이블에 UPSERT.
        테이블 미존재 시 자동 생성, V6/V7 스키마 자동 분기.
        """
        self._ensure_table_exists()
        cursor = self.conn.cursor()

        if self._has_v7_columns():
            # V7 스키마: sub_topic, comparison_type, anchor_value 포함
            sql = """
                INSERT INTO analysis_result
                    (topic, sub_topic, season, comparison_type, anchor_value,
                     stats_json, chart_json, insight_text, hypothesis_results)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    stats_json = VALUES(stats_json),
                    chart_json = VALUES(chart_json),
                    insight_text = VALUES(insight_text),
                    hypothesis_results = VALUES(hypothesis_results),
                    updated_at = NOW()
            """
            cursor.execute(sql, (topic, sub_topic, season, comparison_type, anchor_value,
                                 stats_json, chart_json, insight_text, hypothesis_json))
        else:
            # V6 스키마: topic + season만 UNIQUE KEY
            sql = """
                INSERT INTO analysis_result
                    (topic, season, stats_json, chart_json, insight_text, hypothesis_results)
                VALUES (%s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    stats_json = VALUES(stats_json),
                    chart_json = VALUES(chart_json),
                    insight_text = VALUES(insight_text),
                    hypothesis_results = VALUES(hypothesis_results),
                    updated_at = NOW()
            """
            cursor.execute(sql, (topic, season, stats_json, chart_json,
                                 insight_text, hypothesis_json))

        self.conn.commit()
        cursor.close()

    # ==================== 시리즈별 데이터 (포스트시즌, 시범경기) ====================

    def get_batters_by_series(self, season: int, series: str) -> pd.DataFrame:
        """시리즈별 타자 스탯 (series: '0'=정규, '1'=포스트, '9'=시범 등)"""
        sql = """
            SELECT bs.playerId, bs.category, bs.value, bs.position,
                   p.name AS playerName, t.name AS teamName, t.id AS teamId
            FROM player_batter_stats bs
            JOIN player p ON bs.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE bs.season = %s AND bs.series = %s
              AND (bs.situationType = '' OR bs.situationType IS NULL)
        """
        raw = self.query(sql, (season, series))
        if raw.empty:
            return pd.DataFrame()
        meta = raw.groupby('playerId').first()[['playerName', 'teamName', 'teamId', 'position']].reset_index()
        pivoted = raw.pivot_table(index='playerId', columns='category', values='value', aggfunc='first').reset_index()
        return meta.merge(pivoted, on='playerId', how='left')

    def get_pitchers_by_series(self, season: int, series: str) -> pd.DataFrame:
        """시리즈별 투수 스탯"""
        sql = """
            SELECT ps.playerId, ps.category, ps.value, ps.position,
                   p.name AS playerName, t.name AS teamName, t.id AS teamId
            FROM player_pitcher_stats ps
            JOIN player p ON ps.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE ps.season = %s AND ps.series = %s
              AND (ps.situationType = '' OR ps.situationType IS NULL)
        """
        raw = self.query(sql, (season, series))
        if raw.empty:
            return pd.DataFrame()
        meta = raw.groupby('playerId').first()[['playerName', 'teamName', 'teamId', 'position']].reset_index()
        pivoted = raw.pivot_table(index='playerId', columns='category', values='value', aggfunc='first').reset_index()
        return meta.merge(pivoted, on='playerId', how='left')

    def get_schedules_with_month(self, season: int) -> pd.DataFrame:
        """월 정보 포함 경기 일정"""
        sql = """
            SELECT s.id, s.gameDate, MONTH(s.gameDate) AS gameMonth,
                   s.homeTeamId, s.awayTeamId,
                   s.homeScore, s.awayScore, s.stadium, s.status,
                   t1.name AS homeTeamName, t2.name AS awayTeamName
            FROM schedule s
            JOIN team t1 ON s.homeTeamId = t1.id
            JOIN team t2 ON s.awayTeamId = t2.id
            WHERE s.season = %s AND s.status = '완료'
        """
        return self.query(sql, (season,))

    def get_available_seasons(self) -> list:
        """데이터가 존재하는 시즌 목록 조회 (내림차순)"""
        sql = """
            SELECT DISTINCT season FROM player_batter_stats
            WHERE series = '0'
            ORDER BY season DESC
        """
        df = self.query(sql)
        return df['season'].tolist() if not df.empty else []

    def get_available_months(self, season: int) -> list:
        """해당 시즌에 경기가 있는 월 목록 조회 (오름차순)"""
        sql = """
            SELECT DISTINCT MONTH(gameDate) AS m
            FROM schedule
            WHERE season = %s AND status = '완료'
            ORDER BY m ASC
        """
        df = self.query(sql, (season,))
        return df['m'].tolist() if not df.empty else []

    def get_batters_by_month(self, season: int, month: int) -> pd.DataFrame:
        """특정 월 경기의 타자 성적 (월별 분석용) - schedule 기반"""
        sql = """
            SELECT bs.playerId, bs.category, bs.value, bs.position,
                   p.name AS playerName, t.name AS teamName
            FROM player_batter_stats bs
            JOIN player p ON bs.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE bs.season = %s AND bs.series = '0'
              AND (bs.situationType = '' OR bs.situationType IS NULL)
        """
        raw = self.query(sql, (season,))
        if raw.empty:
            return pd.DataFrame()
        meta = raw.groupby('playerId').first()[['playerName', 'teamName', 'position']].reset_index()
        pivoted = raw.pivot_table(index='playerId', columns='category', values='value', aggfunc='first').reset_index()
        return meta.merge(pivoted, on='playerId', how='left')

    def get_player_by_name(self, name: str) -> pd.DataFrame:
        """선수명으로 선수 정보 조회"""
        sql = """
            SELECT p.id AS playerId, p.name, p.position, p.birthDate,
                   t.name AS teamName, t.id AS teamId
            FROM player p
            JOIN team t ON p.teamId = t.id
            WHERE p.name = %s
        """
        return self.query(sql, (name,))

    def get_players_by_names(self, names: list) -> pd.DataFrame:
        """선수명 리스트로 다수 선수 정보 조회"""
        if not names:
            return pd.DataFrame()
        placeholders = ','.join(['%s'] * len(names))
        sql = f"""
            SELECT p.id AS playerId, p.name, p.position, p.birthDate,
                   t.name AS teamName, t.id AS teamId
            FROM player p
            JOIN team t ON p.teamId = t.id
            WHERE p.name IN ({placeholders})
        """
        return self.query(sql, tuple(names))

    def close(self):
        """DB 연결 종료"""
        if self.conn and self.conn.is_connected():
            self.conn.close()
