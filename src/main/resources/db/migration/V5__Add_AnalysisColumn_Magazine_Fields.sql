-- 분석 컬럼 매거진 재설계용 필드 추가
ALTER TABLE analysis_column ADD COLUMN summary VARCHAR(300) NULL;
ALTER TABLE analysis_column ADD COLUMN featured TINYINT(1) NOT NULL DEFAULT 0;

-- 기존 데이터의 summary를 content에서 HTML 태그 제거 후 첫 200자로 채움
UPDATE analysis_column
SET summary = LEFT(
    REGEXP_REPLACE(content, '<[^>]*>', ''),
    200
)
WHERE summary IS NULL AND content IS NOT NULL;
