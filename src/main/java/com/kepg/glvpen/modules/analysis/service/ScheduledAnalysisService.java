package com.kepg.glvpen.modules.analysis.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 주 2회 자동 분석 서비스 (배포 전용)
 * analysis.scheduled.enabled=true 일 때만 Bean 생성
 *
 * 매주 월/목 01:00 실행:
 * - Python run_scheduled.py를 ProcessBuilder로 호출
 * - runCount 기반 4개 카테고리(team/game/player/advanced) 로테이션
 * - 크롤링(00:00) 이후 1시간 뒤 실행하여 최신 데이터 기반 분석
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "analysis.scheduled.enabled", havingValue = "true")
public class ScheduledAnalysisService {

    @Value("${analysis.python.path:python3}")
    private String pythonPath;

    @Value("${analysis.python.base-dir:python-analysis}")
    private String baseDir;

    private static final long TIMEOUT_MINUTES = 30;
    private static final String STATE_FILE = "output/scheduled_run_count.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(cron = "0 0 1 * * MON,THU", zone = "Asia/Seoul")
    public void runScheduledAnalysis() {
        log.info("[자동 분석] 스케줄 분석 파이프라인 시작");

        try {
            int runCount = readRunCount();
            log.info("[자동 분석] runCount={}", runCount);

            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, "run_scheduled.py", "--run-count", String.valueOf(runCount)
            );
            pb.directory(Paths.get(baseDir).toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[자동 분석] {}", line);
                }
            }

            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.error("[자동 분석] 타임아웃 ({}분 초과)", TIMEOUT_MINUTES);
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("[자동 분석] 파이프라인 성공 (runCount={})", runCount);
            } else {
                log.error("[자동 분석] 파이프라인 실패 (exitCode={})", exitCode);
            }

        } catch (Exception e) {
            log.error("[자동 분석] 파이프라인 실행 중 오류", e);
        }
    }

    private int readRunCount() {
        Path statePath = Paths.get(baseDir, STATE_FILE);
        if (!Files.exists(statePath)) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(statePath.toFile());
            return node.has("run_count") ? node.get("run_count").asInt() : 0;
        } catch (Exception e) {
            log.warn("[자동 분석] 상태 파일 읽기 실패, runCount=0 사용", e);
            return 0;
        }
    }
}
