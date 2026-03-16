-- V8: 기존 ddl-auto:update 환경에서 실행되지 않은 DROP TABLE 정리
-- V3, V4 마이그레이션이 Flyway 없이 실행되지 않아 잔존할 수 있는 테이블 제거

-- V3 대상: Custom Player Mode, Real Match Mode 테이블
DROP TABLE IF EXISTS custom_teams;
DROP TABLE IF EXISTS custom_players;
DROP TABLE IF EXISTS real_match_result;

-- V4 대상: Review 모듈 테이블
DROP TABLE IF EXISTS review_summary;
DROP TABLE IF EXISTS review;
