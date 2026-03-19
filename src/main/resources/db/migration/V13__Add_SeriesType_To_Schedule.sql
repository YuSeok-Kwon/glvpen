-- kbo_schedule 테이블에 시리즈 타입 컬럼 추가
-- 0: 정규시즌, 1: 시범경기, 9: 포스트시즌, 6: 한국시리즈
ALTER TABLE kbo_schedule ADD COLUMN seriesType VARCHAR(10) DEFAULT '0';
