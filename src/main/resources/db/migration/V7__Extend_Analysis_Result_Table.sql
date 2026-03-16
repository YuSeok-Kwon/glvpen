-- analysis_result 테이블 확장: 서브토픽 + 비교 분석 지원
-- 기존 UNIQUE KEY (topic, season) → (topic, sub_topic, season, comparison_type, anchor_value)

ALTER TABLE analysis_result
    ADD COLUMN sub_topic VARCHAR(50) NOT NULL DEFAULT 'default' COMMENT '서브토픽 (batter/pitcher/default)' AFTER topic,
    ADD COLUMN comparison_type VARCHAR(20) NOT NULL DEFAULT 'none' COMMENT '비교 유형 (none/yearly/monthly)' AFTER season,
    ADD COLUMN anchor_value VARCHAR(20) NOT NULL DEFAULT '' COMMENT '비교 앵커 (2025, 06 등)' AFTER comparison_type;

-- 기존 UNIQUE KEY 삭제 후 새 UNIQUE KEY 생성
ALTER TABLE analysis_result
    DROP INDEX uk_topic_season,
    ADD UNIQUE KEY uk_topic_sub_season_comp (topic, sub_topic, season, comparison_type, anchor_value);

-- 추가 인덱스
ALTER TABLE analysis_result
    ADD INDEX idx_topic_sub (topic, sub_topic),
    ADD INDEX idx_comparison (comparison_type, anchor_value);
