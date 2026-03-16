-- V9: 데이터 무결성을 위한 Unique Constraint 추가
-- 기존 중복 데이터가 있을 수 있으므로 중복 제거 후 제약 추가

-- ============================
-- 1. player_batter_stats: (playerId, season, category, series, situationType, situationValue)
-- ============================
DELETE b1 FROM player_batter_stats b1
INNER JOIN player_batter_stats b2
ON b1.playerId = b2.playerId
   AND b1.season = b2.season
   AND b1.category = b2.category
   AND b1.series = b2.series
   AND b1.situationType = b2.situationType
   AND b1.situationValue = b2.situationValue
   AND b1.id < b2.id;

ALTER TABLE player_batter_stats
    ADD UNIQUE KEY uk_batter_stats_full (playerId, season, category, series, situationType, situationValue);

-- ============================
-- 2. player_pitcher_stats: (playerId, season, category, series, situationType, situationValue)
-- ============================
DELETE p1 FROM player_pitcher_stats p1
INNER JOIN player_pitcher_stats p2
ON p1.playerId = p2.playerId
   AND p1.season = p2.season
   AND p1.category = p2.category
   AND p1.series = p2.series
   AND p1.situationType = p2.situationType
   AND p1.situationValue = p2.situationValue
   AND p1.id < p2.id;

ALTER TABLE player_pitcher_stats
    ADD UNIQUE KEY uk_pitcher_stats_full (playerId, season, category, series, situationType, situationValue);

-- ============================
-- 3. player_defense_stats: (playerId, season, series, position, category)
-- ============================
DELETE d1 FROM player_defense_stats d1
INNER JOIN player_defense_stats d2
ON d1.playerId = d2.playerId
   AND d1.season = d2.season
   AND d1.series = d2.series
   AND d1.position = d2.position
   AND d1.category = d2.category
   AND d1.id < d2.id;

ALTER TABLE player_defense_stats
    ADD UNIQUE KEY uk_defense_stats_full (playerId, season, series, position, category);

-- ============================
-- 4. player_runner_stats: (playerId, season, series, category)
-- ============================
DELETE r1 FROM player_runner_stats r1
INNER JOIN player_runner_stats r2
ON r1.playerId = r2.playerId
   AND r1.season = r2.season
   AND r1.series = r2.series
   AND r1.category = r2.category
   AND r1.id < r2.id;

ALTER TABLE player_runner_stats
    ADD UNIQUE KEY uk_runner_stats_full (playerId, season, series, category);

-- ============================
-- 5. team_ranking: (season, teamId)
-- ============================
DELETE t1 FROM team_ranking t1
INNER JOIN team_ranking t2
ON t1.season = t2.season
   AND t1.teamId = t2.teamId
   AND t1.id < t2.id;

ALTER TABLE team_ranking
    ADD UNIQUE KEY uk_team_ranking_season (season, teamId);

-- ============================
-- 6. kbo_score_board: (scheduleId) - 1:1 관계
-- ============================
DELETE s1 FROM kbo_score_board s1
INNER JOIN kbo_score_board s2
ON s1.scheduleId = s2.scheduleId
   AND s1.id < s2.id;

ALTER TABLE kbo_score_board
    ADD UNIQUE KEY uk_scoreboard_schedule (scheduleId);

-- ============================
-- 7. kbo_batter_record: (scheduleId, playerId)
-- ============================
DELETE br1 FROM kbo_batter_record br1
INNER JOIN kbo_batter_record br2
ON br1.scheduleId = br2.scheduleId
   AND br1.playerId = br2.playerId
   AND br1.id < br2.id;

ALTER TABLE kbo_batter_record
    ADD UNIQUE KEY uk_batter_record_game (scheduleId, playerId);

-- ============================
-- 8. kbo_pitcher_record: (scheduleId, playerId)
-- ============================
DELETE pr1 FROM kbo_pitcher_record pr1
INNER JOIN kbo_pitcher_record pr2
ON pr1.scheduleId = pr2.scheduleId
   AND pr1.playerId = pr2.playerId
   AND pr1.id < pr2.id;

ALTER TABLE kbo_pitcher_record
    ADD UNIQUE KEY uk_pitcher_record_game (scheduleId, playerId);

-- ============================
-- 9. player: kboPlayerId (자연키)
-- ============================
-- kboPlayerId가 NULL인 경우가 있을 수 있으므로 NULL 허용 UNIQUE로 처리
ALTER TABLE player
    ADD UNIQUE KEY uk_player_kbo_id (kboPlayerId);
