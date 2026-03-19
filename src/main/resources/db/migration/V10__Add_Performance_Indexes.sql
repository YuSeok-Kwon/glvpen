-- V10: 대용량 테이블 조회 성능을 위한 인덱스 추가
-- MySQL은 CREATE INDEX IF NOT EXISTS를 지원하지 않으므로
-- information_schema 확인 후 조건부 생성

-- ============================
-- 1. player_batter_stats
-- ============================
SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player_batter_stats' AND index_name = 'idx_batter_player_season';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_batter_player_season ON player_batter_stats (playerId, season)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player_batter_stats' AND index_name = 'idx_batter_season_category';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_batter_season_category ON player_batter_stats (season, category)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player_batter_stats' AND index_name = 'idx_batter_season_series_sit';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_batter_season_series_sit ON player_batter_stats (season, series, situationType, situationValue)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================
-- 2. player_pitcher_stats
-- ============================
SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player_pitcher_stats' AND index_name = 'idx_pitcher_player_season';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_pitcher_player_season ON player_pitcher_stats (playerId, season)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player_pitcher_stats' AND index_name = 'idx_pitcher_season_category';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_pitcher_season_category ON player_pitcher_stats (season, category)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player_pitcher_stats' AND index_name = 'idx_pitcher_season_series_sit';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_pitcher_season_series_sit ON player_pitcher_stats (season, series, situationType, situationValue)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================
-- 3. player_defense_stats
-- ============================
SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player_defense_stats' AND index_name = 'idx_defense_player_season';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_defense_player_season ON player_defense_stats (playerId, season)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player_defense_stats' AND index_name = 'idx_defense_season_series';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_defense_season_series ON player_defense_stats (season, series)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================
-- 4. player_runner_stats
-- ============================
SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player_runner_stats' AND index_name = 'idx_runner_player_season';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_runner_player_season ON player_runner_stats (playerId, season)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================
-- 5. kbo_schedule
-- ============================
SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'kbo_schedule' AND index_name = 'idx_schedule_match_date';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_schedule_match_date ON kbo_schedule (matchDate)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'kbo_schedule' AND index_name = 'idx_schedule_home_team';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_schedule_home_team ON kbo_schedule (homeTeamId)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'kbo_schedule' AND index_name = 'idx_schedule_away_team';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_schedule_away_team ON kbo_schedule (awayTeamId)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- kbo_schedule에는 season 컬럼이 없으므로 idx_schedule_season_status 생략

-- ============================
-- 6. sim_game_result
-- ============================
SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'sim_game_result' AND index_name = 'idx_game_result_user';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_game_result_user ON sim_game_result (userId)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'sim_game_result' AND index_name = 'idx_game_result_schedule';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_game_result_schedule ON sim_game_result (scheduleId)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================
-- 7. sim_player_card_overall
-- ============================
SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'sim_player_card_overall' AND index_name = 'idx_card_player_season';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_card_player_season ON sim_player_card_overall (playerId, season)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'sim_player_card_overall' AND index_name = 'idx_card_overall_grade';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_card_overall_grade ON sim_player_card_overall (overall, grade)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================
-- 8. player
-- ============================
SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player' AND index_name = 'idx_player_team';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_player_team ON player (teamId)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @exists FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'player' AND index_name = 'idx_player_name_team';
SET @query = IF(@exists > 0, 'SELECT 1', 'CREATE INDEX idx_player_name_team ON player (name, teamId)');
PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
