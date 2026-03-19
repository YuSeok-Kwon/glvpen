-- =====================================================
-- V11: 고아 레코드 정리
-- 1) Old Schedule ID 참조 레코드 삭제 (17,336건)
-- 2) 삭제된 Player ID 참조 레코드 삭제 (374건)
-- =====================================================

-- =====================================================
-- 1. Old Schedule ID 고아 레코드 정리
--    kbo_schedule ID가 4689부터 시작되어,
--    이전 크롤링 데이터(scheduleId < 4689)가 고아 상태
-- =====================================================

-- 1-1. kbo_batter_lineup (7,300건 - 전량 고아)
DELETE FROM kbo_batter_lineup
WHERE scheduleId NOT IN (SELECT id FROM kbo_schedule);

-- 1-2. kbo_batter_record (7,164건)
DELETE FROM kbo_batter_record
WHERE scheduleId NOT IN (SELECT id FROM kbo_schedule);

-- 1-3. kbo_pitcher_record (2,597건)
DELETE FROM kbo_pitcher_record
WHERE scheduleId NOT IN (SELECT id FROM kbo_schedule);

-- 1-4. kbo_score_board (275건)
DELETE FROM kbo_score_board
WHERE scheduleId NOT IN (SELECT id FROM kbo_schedule);

-- =====================================================
-- 2. 삭제된 Player ID 고아 레코드 정리
--    player 테이블 max ID = 1649, 이후 ID 참조 레코드 정리
-- =====================================================

-- 2-1. kbo_batter_record (130건, 1단계에서 일부 이미 삭제됨)
DELETE FROM kbo_batter_record
WHERE playerId NOT IN (SELECT id FROM player);

-- 2-2. kbo_pitcher_record (67건, 1단계에서 일부 이미 삭제됨)
DELETE FROM kbo_pitcher_record
WHERE playerId NOT IN (SELECT id FROM player);

-- 2-3. sim_player_card_overall (45건)
DELETE FROM sim_player_card_overall
WHERE playerId NOT IN (SELECT id FROM player);
