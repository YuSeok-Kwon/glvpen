-- 중복 데이터 확인 및 정리 스크립트

-- 1. Schedule 테이블의 중복 데이터 확인
SELECT statizId, COUNT(*) as count
FROM schedule 
WHERE statizId IS NOT NULL
GROUP BY statizId 
HAVING COUNT(*) > 1
ORDER BY count DESC;

-- 2. 같은 날짜, 같은 팀 매치업의 중복 확인
SELECT DATE(matchDate) as match_date, homeTeamId, awayTeamId, COUNT(*) as count
FROM schedule 
GROUP BY DATE(matchDate), homeTeamId, awayTeamId 
HAVING COUNT(*) > 1
ORDER BY count DESC;

-- 3. User 테이블의 중복 데이터 확인 (로그인 오류 관련)
SELECT username, COUNT(*) as count
FROM user 
GROUP BY username 
HAVING COUNT(*) > 1
ORDER BY count DESC;

-- 4. Email 중복 확인
SELECT email, COUNT(*) as count
FROM user 
WHERE email IS NOT NULL
GROUP BY email 
HAVING COUNT(*) > 1
ORDER BY count DESC;

-- 중복 제거 방법 (실행 전 백업 필수!)
-- 
-- Schedule 테이블 중복 제거 (같은 날짜, 같은 팀 매치업 기준)
-- 먼저 중복된 스케줄들의 상세 정보를 확인
SELECT s1.id, DATE(s1.matchDate) as match_date, s1.homeTeamId, s1.awayTeamId, s1.statizId
FROM schedule s1
INNER JOIN schedule s2 ON DATE(s1.matchDate) = DATE(s2.matchDate) 
    AND s1.homeTeamId = s2.homeTeamId 
    AND s1.awayTeamId = s2.awayTeamId
WHERE s1.id != s2.id
ORDER BY DATE(s1.matchDate), s1.homeTeamId, s1.awayTeamId, s1.id;

-- 중복 제거 실행 (더 작은 ID를 가진 것을 삭제하여 최신 데이터 유지)
DELETE s1 FROM schedule s1
INNER JOIN schedule s2 
WHERE s1.id < s2.id  -- 더 작은 ID (더 오래된 것)를 삭제
AND DATE(s1.matchDate) = DATE(s2.matchDate) 
AND s1.homeTeamId = s2.homeTeamId 
AND s1.awayTeamId = s2.awayTeamId;

-- statizId 기준 중복 제거 (참고용)
-- DELETE s1 FROM schedule s1
-- INNER JOIN schedule s2 
-- WHERE s1.id > s2.id 
-- AND s1.statizId = s2.statizId 
-- AND s1.statizId IS NOT NULL;
--
-- User 테이블 중복 제거 (username 기준)
-- 먼저 중복된 사용자들의 상세 정보를 확인
SELECT u1.id, u1.username, u1.email, u1.createdDate 
FROM user u1
INNER JOIN user u2 ON u1.username = u2.username
WHERE u1.id != u2.id
ORDER BY u1.username, u1.id;

-- 중복 제거 실행 (가장 최근에 생성된 것을 남기고 이전 것들 삭제)
DELETE u1 FROM user u1
INNER JOIN user u2
WHERE u1.id < u2.id  -- 더 작은 ID (더 오래된 것)를 삭제
AND u1.username = u2.username;

-- ========================================
-- 모든 중복 데이터 한 번에 삭제 (주의: 실행 전 반드시 백업!)
-- ========================================

-- 1. Schedule 테이블의 모든 중복 제거
DELETE s1 FROM schedule s1
INNER JOIN schedule s2 
WHERE s1.id < s2.id
AND DATE(s1.matchDate) = DATE(s2.matchDate) 
AND s1.homeTeamId = s2.homeTeamId 
AND s1.awayTeamId = s2.awayTeamId;

-- 2. User 테이블의 모든 중복 제거
DELETE u1 FROM user u1
INNER JOIN user u2
WHERE u1.id < u2.id
AND u1.username = u2.username;

-- 3. Email 중복도 있다면 제거 (선택사항)
DELETE u1 FROM user u1
INNER JOIN user u2
WHERE u1.id < u2.id
AND u1.email = u2.email 
AND u1.email IS NOT NULL;

-- ========================================
-- 특정 날짜 데이터 완전 삭제 (2025-07-20)
-- ========================================

-- 2025-07-20 날짜의 모든 스케줄 데이터 삭제
DELETE FROM schedule 
WHERE DATE(matchDate) = '2025-07-20';

-- 연관된 데이터도 함께 삭제 (외래키 제약이 있는 경우)
-- ScoreBoard 삭제 (schedule과 연관)
DELETE sb FROM scoreboard sb
INNER JOIN schedule s ON sb.scheduleId = s.id
WHERE DATE(s.matchDate) = '2025-07-20';

-- GameHighlight 삭제 (schedule과 연관)
DELETE gh FROM gamehighlight gh
INNER JOIN schedule s ON gh.scheduleId = s.id
WHERE DATE(s.matchDate) = '2025-07-20';

-- BatterRecord 삭제 (schedule과 연관)
DELETE br FROM batterrecord br
INNER JOIN schedule s ON br.scheduleId = s.id
WHERE DATE(s.matchDate) = '2025-07-20';

-- PitcherRecord 삭제 (schedule과 연관)
DELETE pr FROM pitcherrecord pr
INNER JOIN schedule s ON pr.scheduleId = s.id
WHERE DATE(s.matchDate) = '2025-07-20';

-- Lineup 삭제 (schedule과 연관)
DELETE l FROM lineup l
INNER JOIN schedule s ON l.scheduleId = s.id
WHERE DATE(s.matchDate) = '2025-07-20';

-- 마지막으로 Schedule 삭제
DELETE FROM schedule 
WHERE DATE(matchDate) = '2025-07-20';

-- ========================================
-- 확인 쿼리 (삭제 후 검증용)
-- ========================================

-- 삭제 후 2025-07-20 데이터가 남아있는지 확인
SELECT COUNT(*) as remaining_schedules
FROM schedule 
WHERE DATE(matchDate) = '2025-07-20';

-- 전체 테이블 데이터 삭제 (극단적인 경우만 사용)
-- ========================================

-- 모든 스케줄 데이터 삭제 (주의!)
-- DELETE FROM schedule;

-- 모든 사용자 데이터 삭제 (주의!)
-- DELETE FROM user;
