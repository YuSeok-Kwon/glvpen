-- V12: team_stats 테이블에 Unique Constraint 추가
-- ON DUPLICATE KEY UPDATE 배치 저장을 위해 필요

-- 기존 중복 데이터 제거
DELETE t1 FROM team_stats t1
INNER JOIN team_stats t2
ON t1.teamId = t2.teamId
   AND t1.season = t2.season
   AND t1.category = t2.category
   AND t1.id < t2.id;

-- Unique Constraint 추가
ALTER TABLE team_stats
    ADD UNIQUE KEY uk_team_stats_full (teamId, season, category);
