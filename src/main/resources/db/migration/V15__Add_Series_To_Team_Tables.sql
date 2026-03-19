-- V15: 팀 테이블(team_ranking, team_stats, team_head_to_head)에 series 컬럼 추가
-- 선수 테이블(player_batter_stats, player_pitcher_stats)과 동일한 패턴: VARCHAR(10) DEFAULT '0'
-- 기존 데이터는 DEFAULT '0'(정규시즌)으로 자동 채워짐

-- ==================== team_ranking ====================
ALTER TABLE team_ranking ADD COLUMN series VARCHAR(10) DEFAULT '0';

-- 기존 UK 삭제 후 series 포함 UK 재생성
ALTER TABLE team_ranking DROP KEY uk_team_ranking_season;
ALTER TABLE team_ranking ADD UNIQUE KEY uk_team_ranking_full (season, teamId, series);

-- ==================== team_stats ====================
ALTER TABLE team_stats ADD COLUMN series VARCHAR(10) DEFAULT '0';

-- 기존 UK 삭제 후 series 포함 UK 재생성
ALTER TABLE team_stats DROP KEY uk_team_stats_full;
ALTER TABLE team_stats ADD UNIQUE KEY uk_team_stats_full (teamId, season, category, series);

-- ==================== team_head_to_head ====================
ALTER TABLE team_head_to_head ADD COLUMN series VARCHAR(10) DEFAULT '0';

-- JPA 자동생성 UK명 삭제 후 series 포함 UK 재생성
ALTER TABLE team_head_to_head DROP KEY UK6h1lxkg3ds7vhu0jboasm074f;
ALTER TABLE team_head_to_head ADD UNIQUE KEY uk_h2h_full (season, teamId, opponentTeamId, series);
