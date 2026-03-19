-- Python 배치 분석 결과 저장 테이블
-- Python이 기술통계 + 가설검정 + Chart.js JSON을 생성하여 저장
-- Java(AiColumnGeneratorService)가 읽어서 Gemini 프롬프트에 포함
CREATE TABLE IF NOT EXISTS analysis_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    topic INT NOT NULL COMMENT '주제 인덱스 (0~14)',
    season INT NOT NULL COMMENT '분석 대상 시즌',
    stats_json LONGTEXT NOT NULL COMMENT '기술통계 결과 (JSON)',
    chart_json LONGTEXT NOT NULL COMMENT 'Chart.js 호환 차트 데이터 배열 (JSON)',
    insight_text LONGTEXT NOT NULL COMMENT '통계적 인사이트 텍스트',
    hypothesis_results LONGTEXT COMMENT '가설검정 결과 (JSON)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_topic_season (topic, season),
    INDEX idx_season (season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
