-- kbo_schedule 테이블에 KBO 원본 시리즈 코드 컬럼 추가
-- 게임센터 API(박스스코어, 키플레이어)에서 srId 파라미터로 사용
-- 0: 정규시즌, 1: 시범경기, 3: 와일드카드, 4: 준PO, 5: PO, 6: 특별전, 7: 한국시리즈
ALTER TABLE kbo_schedule ADD COLUMN kboSeriesCode VARCHAR(5) DEFAULT NULL;

-- 기존 데이터 보강: seriesType으로부터 유추 가능한 값 설정
UPDATE kbo_schedule SET kboSeriesCode = '0' WHERE seriesType = '0' AND kboSeriesCode IS NULL;
UPDATE kbo_schedule SET kboSeriesCode = '1' WHERE seriesType = '1' AND kboSeriesCode IS NULL;
