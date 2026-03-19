package com.kepg.glvpen.modules.analysis.service;

import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

import com.kepg.glvpen.common.ai.GeminiClient;
import com.kepg.glvpen.modules.analysis.domain.AnalysisColumn;
import com.kepg.glvpen.modules.analysis.domain.AnalysisResult;
import com.kepg.glvpen.modules.analysis.repository.AnalysisColumnRepository;
import com.kepg.glvpen.modules.analysis.repository.AnalysisResultRepository;
import com.kepg.glvpen.modules.player.repository.PlayerRepository;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.glvpen.modules.player.stats.repository.PitcherStatsRepository;
import com.kepg.glvpen.modules.team.repository.TeamRepository;
import com.kepg.glvpen.modules.team.teamRanking.domain.TeamRanking;
import com.kepg.glvpen.modules.team.teamRanking.repository.TeamRankingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiColumnGeneratorService {

    private final GeminiClient geminiClient;
    private final AnalysisColumnRepository columnRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final BatterStatsRepository batterStatsRepository;
    private final PitcherStatsRepository pitcherStatsRepository;
    private final TeamRankingRepository teamRankingRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final ColumnDataCalculator calculator;

    private static final int CURRENT_SEASON = LocalDateTime.now().getYear();

    // 로테이션 특집 주제 풀 (15개, 전략 B 확정)
    private static final String[][] ROTATING_TOPICS = {
        {"세이버메트릭스 트렌드: wOBA, FIP, OPS로 보는 리그 판도", "trend"},              // 0  전략5
        {"선수 스포트라이트: wOBA 상위 선수 심층 분석", "player"},                       // 1  전략1
        {"브레이크아웃 후보: 올 시즌 급성장 선수 분석", "player"},                       // 2  전략1-C
        {"행운 보정 분석: BABIP과 FIP-ERA 갭으로 보는 진짜 실력", "trend"},              // 3  전략1-B
        {"상대전적 패턴: 팀 간 맞대결 숨은 데이터", "game"},                             // 4  전략2-A
        {"이닝별 득점 패턴: 경기 흐름을 바꾸는 결정적 순간", "game"},                    // 5  전략2-B
        {"홈/원정 & 구장 환경 분석: 파크팩터와 숨겨진 변수", "game"},                    // 6  전략2-D + 4-B
        {"관중 동원 트렌드: 구단 인기와 성적의 상관관계", "trend"},                      // 7  전략3-A
        {"퓨처스 유망주 스카우팅: 내일의 KBO 스타", "player"},                           // 8  전략6
        {"ABS 도입 영향 분석: 스트라이크존 변화가 바꾼 것들", "trend"},                  // 9  전략4-E
        {"클러치/초커 분석: 위기 상황에 강한 선수, 약한 선수", "player"},                // 10 전략4-D
        {"나이-성과 커브: KBO 선수들의 피크 시즌은 언제인가", "player"},                 // 11 전략1-A
        {"라인업 효율성 분석: 타순별 기대 생산량과 실제 성적", "game"},                  // 12 전략2-C (신규)
        {"월별/계절별 선수 유형: 시즌 구간별 강한 선수는 누구인가", "player"},           // 13 전략4-C (신규)
        {"포지션별 가치 분석: 어떤 포지션이 팀 승리에 가장 기여하는가", "player"},       // 14 전략1-D (신규)
    };

    // ==================== 스케줄 메서드 ====================

    /**
     * 매주 월요일 06:00 - 팀별 주간 분석
     * weekOfYear % 10 → 순위 기준 팀 순환
     */
    @Scheduled(cron = "0 0 6 * * MON", zone = "Asia/Seoul")
    public void generateTeamWeeklyColumn() {
        log.info("=== [월요일] 팀별 주간 분석 컬럼 자동 생성 시작 ===");
        try {
            int weekOfYear = LocalDateTime.now().get(WeekFields.of(Locale.KOREA).weekOfYear());

            List<TeamRanking> rankings = teamRankingRepository.findBySeasonAndSeriesOrderByRankingAsc(CURRENT_SEASON, "0");
            if (rankings.isEmpty()) {
                log.warn("팀 순위 데이터가 없어 주간 분석을 생성할 수 없습니다.");
                return;
            }

            int teamIndex = weekOfYear % rankings.size();
            TeamRanking targetRanking = rankings.get(teamIndex);
            int teamId = targetRanking.getTeamId();

            // 팀 상세 데이터 수집
            String teamName = teamRepository.findTeamNameById(teamId);
            if (teamName == null) {
                teamName = "팀ID:" + teamId;
            }
            String teamDetailData = buildTeamDetailData(teamId, teamName, targetRanking, CURRENT_SEASON);

            // 프롬프트 구성
            StringBuilder prompt = new StringBuilder();
            prompt.append("당신은 KBO 야구 매거진 기사 작성 전문가입니다.\n")
                  .append("'").append(teamName).append("'의 이번 주 전력 분석 매거진 기사를 작성해주세요.\n\n")
                  .append("=== 실제 데이터 ===\n")
                  .append("시즌: ").append(CURRENT_SEASON).append("\n")
                  .append(teamDetailData).append("\n")
                  .append(buildWritingRules());

            String aiResponse = geminiClient.callGemini(prompt.toString());
            AnalysisColumn column = parseAndSaveColumn(aiResponse, "⚾ " + teamName + " 주간 전력 분석", "team", teamId, null);

            if (column == null) {
                log.warn("=== [월요일] AI 응답 검증 실패로 컬럼이 생성되지 않았습니다: {} ===", teamName);
                return;
            }

            log.info("=== [월요일] 팀별 주간 분석 컬럼 생성 완료: {} (주차: {}) ===", teamName, weekOfYear);
        } catch (Exception e) {
            log.error("[월요일] 팀별 주간 분석 컬럼 생성 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매주 목요일 06:00 - 로테이션 특집
     * weekOfYear % 15 → 주제 순환
     * Python 배치 분석 결과(analysis_result)를 우선 사용하고,
     * 없으면 기존 ColumnDataCalculator로 fallback
     */
    @Scheduled(cron = "0 0 6 * * THU", zone = "Asia/Seoul")
    public void generateRotatingTopicColumn() {
        log.info("=== [목요일] 로테이션 특집 컬럼 자동 생성 시작 ===");
        try {
            int weekOfYear = LocalDateTime.now().get(WeekFields.of(Locale.KOREA).weekOfYear());
            int topicIndex = weekOfYear % ROTATING_TOPICS.length;
            String topic = ROTATING_TOPICS[topicIndex][0];
            String category = ROTATING_TOPICS[topicIndex][1];

            // 1. Python 배치 분석 결과 조회 (analysis_result 테이블)
            Optional<AnalysisResult> analysisResultOpt =
                    analysisResultRepository.findByTopicAndSeason(topicIndex, CURRENT_SEASON);

            StringBuilder prompt = new StringBuilder();
            prompt.append("당신은 KBO 야구 매거진 기사 작성 전문가입니다.\n")
                  .append("주제: ").append(topic).append("\n")
                  .append("시즌: ").append(CURRENT_SEASON).append("\n\n");

            String chartDataForColumn = null;

            if (analysisResultOpt.isPresent()) {
                // Python 분석 결과 기반 프롬프트 (통계 분석 + 가설검정 + 차트)
                AnalysisResult result = analysisResultOpt.get();
                log.info("Python 분석 결과 사용 (topic={}, season={})", topicIndex, CURRENT_SEASON);

                prompt.append("=== 통계 분석 데이터 ===\n")
                      .append(result.getStatsJson()).append("\n\n");

                if (result.getInsightText() != null && !result.getInsightText().isBlank()) {
                    prompt.append("=== 통계적 인사이트 ===\n")
                          .append(result.getInsightText()).append("\n\n");
                }

                if (result.getHypothesisResults() != null && !result.getHypothesisResults().isBlank()) {
                    prompt.append("=== 가설검정 결과 ===\n")
                          .append(result.getHypothesisResults()).append("\n\n");
                }

                prompt.append("=== 차트 참조 데이터 (기사에서 차트 내용을 언급해주세요) ===\n")
                      .append(result.getChartJson()).append("\n\n");

                // 차트 데이터를 AnalysisColumn에 저장하기 위해 보관
                chartDataForColumn = result.getChartJson();

            } else {
                // Fallback: 기존 ColumnDataCalculator 사용
                log.info("Python 분석 결과 없음 → ColumnDataCalculator fallback (topic={}, season={})", topicIndex, CURRENT_SEASON);
                String preComputedData = switch (topicIndex) {
                    case 0  -> calculator.calcSabermetricsTrend(CURRENT_SEASON);
                    case 1  -> calculator.calcWarSpotlight(CURRENT_SEASON);
                    case 2  -> calculator.calcBreakoutCandidates(CURRENT_SEASON);
                    case 3  -> calculator.calcLuckAdjusted(CURRENT_SEASON);
                    case 4  -> calculator.calcHeadToHead(CURRENT_SEASON);
                    case 5  -> calculator.calcInningScoring(CURRENT_SEASON);
                    case 6  -> calculator.calcHomeAwayAdvantage(CURRENT_SEASON);
                    case 7  -> calculator.calcCrowdTrend(CURRENT_SEASON);
                    case 8  -> calculator.calcFuturesProspects(CURRENT_SEASON);
                    case 9  -> calculator.calcAbsImpact(CURRENT_SEASON);
                    case 10 -> calculator.calcClutchChoker(CURRENT_SEASON);
                    case 11 -> calculator.calcAgeCurve(CURRENT_SEASON);
                    case 12 -> calculator.calcLineupEfficiency(CURRENT_SEASON);
                    case 13 -> calculator.calcSeasonalPatterns(CURRENT_SEASON);
                    case 14 -> calculator.calcPositionValue(CURRENT_SEASON);
                    default -> "";
                };

                prompt.append("=== 사전 분석 데이터 (아래 수치를 그대로 사용하세요) ===\n")
                      .append(preComputedData).append("\n\n");
            }

            prompt.append(buildEnhancedWritingRules(analysisResultOpt.isPresent()));

            String aiResponse = geminiClient.callGemini(prompt.toString());
            AnalysisColumn column = parseAndSaveColumn(aiResponse, topic, category, null, null);

            if (column == null) {
                log.warn("=== [목요일] AI 응답 검증 실패로 컬럼이 생성되지 않았습니다: {} ===", topic);
                return;
            }

            // Python 분석 결과에서 생성된 차트 데이터를 컬럼에 저장
            if (chartDataForColumn != null) {
                column.setChartData(chartDataForColumn);
                columnRepository.save(column);
            }

            log.info("=== [목요일] 로테이션 특집 컬럼 생성 완료: {} (주차: {}, 인덱스: {}, Python분석: {}) ===",
                    topic, weekOfYear, topicIndex, analysisResultOpt.isPresent());
        } catch (Exception e) {
            log.error("[목요일] 로테이션 특집 컬럼 생성 실패: {}", e.getMessage(), e);
        }
    }

    // ==================== 수동 트리거 메서드 ====================

    /**
     * 시즌 개요 분석 컬럼 생성 (수동 호출용)
     */
    public AnalysisColumn generateSeasonOverviewColumn(int season) {
        log.info("시즌 {} 개요 분석 컬럼 생성 시작", season);

        List<Integer> batterIds = batterStatsRepository.findDistinctPlayerIdsBySeason(season);
        List<Integer> pitcherIds = pitcherStatsRepository.findDistinctPlayerIdsBySeason(season);
        String teamRankingData = buildTeamRankingData(season);

        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 KBO 야구 매거진 기사 작성 전문가입니다.\n")
              .append("독자가 흥미를 느낄 수 있는 데이터 분석 매거진 기사를 작성해주세요.\n\n")
              .append("=== 실제 데이터 ===\n")
              .append("시즌: ").append(season).append("\n")
              .append("등록 타자 수: ").append(batterIds.size()).append("명\n")
              .append("등록 투수 수: ").append(pitcherIds.size()).append("명\n")
              .append("KBO 10개 구단\n");

        if (!teamRankingData.isEmpty()) {
            prompt.append("\n팀 순위:\n").append(teamRankingData);
        }

        prompt.append("\n").append(buildWritingRules());

        String aiResponse = geminiClient.callGemini(prompt.toString());
        return parseAndSaveColumn(aiResponse, "📊 " + season + " 시즌 주간 분석", "trend", null, null);
    }

    /**
     * 수동 트리거: 특정 주제로 AI 컬럼 생성
     * 카테고리 기반 사전 분석 데이터 + 차트 JSON 포함
     */
    public AnalysisColumn generateColumnByTopic(String topic, String category, int season) {
        log.info("AI 컬럼 생성 - 주제: {}, 카테고리: {}, 시즌: {}", topic, category, season);

        String teamRankingData = buildTeamRankingData(season);
        String analysisData = getAnalysisDataByCategory(category, season);
        String chartJson = calculator.buildChartJson(category, season);

        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 KBO 야구 매거진 기사 작성 전문가입니다.\n")
              .append("주제: ").append(topic).append("\n")
              .append("시즌: ").append(season).append("\n\n");

        // 시즌 상황 컨텍스트
        String seasonContext = buildSeasonContext(season);
        if (!seasonContext.isEmpty()) {
            prompt.append(seasonContext).append("\n");
        }

        if (!teamRankingData.isEmpty()) {
            prompt.append("=== 팀 순위 (").append(season - 1).append(" 정규시즌 최종) ===\n")
                  .append(teamRankingData).append("\n\n");
        }

        if (analysisData != null && !analysisData.isEmpty() && !analysisData.startsWith("[")) {
            prompt.append("=== 사전 분석 데이터 (").append(season - 1).append(" 정규시즌 기록, 수치를 그대로 사용하세요) ===\n")
                  .append(analysisData).append("\n\n");
        }

        if (chartJson != null) {
            prompt.append("=== 차트 참조 데이터 (기사에서 차트 내용을 언급해주세요) ===\n")
                  .append(chartJson).append("\n\n");
        }

        prompt.append(buildEnhancedWritingRules(false));

        String aiResponse = geminiClient.callGemini(prompt.toString());
        AnalysisColumn column = parseAndSaveColumn(aiResponse, topic, category, null, null);

        if (column != null && chartJson != null) {
            column.setChartData(chartJson);
            columnRepository.save(column);
        }

        return column;
    }

    /**
     * 카테고리 기반 사전 분석 데이터 조회 (현재 시즌 → 전년 시즌 폴백)
     */
    private String getAnalysisDataByCategory(String category, int season) {
        String result = tryAnalysisDataForSeason(category, season);
        if ((result == null || result.isEmpty() || result.startsWith("[데이터") || result.startsWith("[계산") || result.startsWith("[타자")) && season > 2020) {
            result = tryAnalysisDataForSeason(category, season - 1);
        }
        return result;
    }

    private String tryAnalysisDataForSeason(String category, int season) {
        try {
            return switch (category) {
                case "player" -> calculator.calcWarSpotlight(season);
                case "team" -> calculator.calcPositionValue(season);
                case "game" -> calculator.calcInningScoring(season);
                case "trend" -> calculator.calcSabermetricsTrend(season);
                default -> "";
            };
        } catch (Exception e) {
            log.warn("tryAnalysisDataForSeason 실패 (category={}, season={}): {}", category, season, e.getMessage());
            return "";
        }
    }

    /**
     * 시즌 상황 컨텍스트 (시범경기/정규시즌 구분)
     */
    private String buildSeasonContext(int season) {
        int currentYear = LocalDateTime.now().getYear();
        int month = LocalDateTime.now().getMonthValue();
        int day = LocalDateTime.now().getDayOfMonth();

        if (season != currentYear) return "";

        StringBuilder ctx = new StringBuilder();
        ctx.append("=== 시즌 상황 ===\n");
        ctx.append("현재 날짜: ").append(LocalDateTime.now().toLocalDate()).append("\n");
        ctx.append("KBO ").append(season).append(" 시즌 일정: 시범경기 3/12~3/24, 정규시즌 개막 3/28\n");

        if (month < 3 || (month == 3 && day < 12)) {
            ctx.append("현재 상태: 시즌 시작 전 (스프링캠프)\n");
            ctx.append("분석 기반: ").append(season - 1).append(" 정규시즌 확정 기록\n");
            ctx.append("기사 관점: ").append(season).append(" 시즌 프리뷰/전망\n");
        } else if (month == 3 && day < 28) {
            ctx.append("현재 상태: 시범경기 기간 (정규시즌 개막 전)\n");
            ctx.append("분석 기반: ").append(season - 1).append(" 정규시즌 확정 기록\n");
            ctx.append("기사 관점: ").append(season).append(" 정규시즌 프리뷰/전망\n");
            ctx.append("주의: 시범경기 성적은 참고 수준이며, 정규시즌 성적과 직결되지 않습니다.\n");
            ctx.append("주의: '올 시즌 성적'이라고 표현하지 말고, '지난 시즌(").append(season - 1).append(") 기록 기반'임을 명시하세요.\n");
        } else {
            ctx.append("현재 상태: ").append(season).append(" 정규시즌 진행 중\n");
        }

        return ctx.toString();
    }

    // ==================== 데이터 수집 헬퍼 ====================

    /**
     * 팀 상세 데이터 (주간 분석용)
     */
    private String buildTeamDetailData(int teamId, String teamName, TeamRanking ranking, int season) {
        StringBuilder sb = new StringBuilder();
        sb.append("팀: ").append(teamName).append("\n")
          .append("순위: ").append(ranking.getRanking()).append("위\n")
          .append("성적: ").append(ranking.getWins()).append("승 ")
          .append(ranking.getLosses()).append("패 ")
          .append(ranking.getDraws()).append("무\n")
          .append("승률: ").append(String.format("%.3f", ranking.getWinRate())).append("\n");

        // 팀 최고 타자
        try {
            List<Object[]> topHitter = playerRepository.findTopHitterByTeamIdAndSeason(teamId, season);
            if (!topHitter.isEmpty()) {
                Object[] h = topHitter.get(0);
                sb.append("팀 최고 타자: ").append(h[0]).append(" (WAR: ").append(h[2]).append(")\n");
            }
        } catch (Exception e) {
            log.warn("팀 최고 타자 조회 실패 (teamId={}): {}", teamId, e.getMessage());
        }

        // 팀 최고 투수
        try {
            List<Object[]> topPitcher = playerRepository.findTopPitcherByTeamIdAndSeason(teamId, season);
            if (!topPitcher.isEmpty()) {
                Object[] p = topPitcher.get(0);
                sb.append("팀 최고 투수: ").append(p[0]).append(" (WAR: ").append(p[2]).append(")\n");
            }
        } catch (Exception e) {
            log.warn("팀 최고 투수 조회 실패 (teamId={}): {}", teamId, e.getMessage());
        }

        return sb.toString();
    }

    /**
     * 팀 순위 데이터를 프롬프트용 문자열로 구성
     */
    private String buildTeamRankingData(int season) {
        try {
            List<TeamRanking> rankings = teamRankingRepository.findBySeasonAndSeriesOrderByRankingAsc(season, "0");
            if (rankings.isEmpty()) {
                return "";
            }
            return rankings.stream()
                    .map(r -> {
                        String name = teamRepository.findTeamNameById(r.getTeamId());
                        if (name == null) name = "팀ID:" + r.getTeamId();
                        return r.getRanking() + "위 " + name
                                + " " + r.getWins() + "승 " + r.getLosses() + "패 " + r.getDraws() + "무"
                                + " 승률 " + String.format("%.3f", r.getWinRate());
                    })
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("팀 순위 데이터 조회 실패: {}", e.getMessage());
            return "";
        }
    }

    // ==================== 공통 유틸 ====================

    /**
     * 공통 작성 규칙
     */
    private String buildWritingRules() {
        return """
                === 작성 규칙 ===
                - 위 데이터의 수치를 정확히 인용하세요. 존재하지 않는 수치를 만들지 마세요.
                - 제목은 첫 줄에 작성 (##으로 시작), 매거진답게 흥미로운 제목
                - 본문은 HTML 형식으로 작성 (<p>, <strong>, <br> 태그 사용)
                - 800~1200자 분량으로 작성
                - 도입-분석-전망 구성으로 매거진 기사처럼 작성
                - 존댓말 사용
                - 마지막 줄에 ##SUMMARY## 구분자 후 2줄 요약 (100자 이내) 작성
                """;
    }

    /**
     * 향상된 작성 규칙 (Python 분석 결과 포함 시 가설검정 인용 규칙 추가)
     */
    private String buildEnhancedWritingRules(boolean hasPythonAnalysis) {
        StringBuilder rules = new StringBuilder();
        rules.append("""
                === 작성 규칙 ===
                - 위 데이터의 수치를 정확히 인용하세요. 존재하지 않는 수치를 만들지 마세요.
                - 제목은 첫 줄에 작성 (##으로 시작), 매거진답게 흥미로운 제목
                - 본문은 HTML 형식으로 작성 (<p>, <strong>, <br> 태그 사용)
                - 800~1200자 분량으로 작성
                - 도입-분석-전망 구성으로 매거진 기사처럼 작성
                - 존댓말 사용
                - 마지막 줄에 ##SUMMARY## 구분자 후 2줄 요약 (100자 이내) 작성
                """);

        if (hasPythonAnalysis) {
            rules.append("""
                - 가설검정 결과를 기사에 자연스럽게 인용하세요 (예: "통계적으로 유의미한 차이가 확인되었습니다 (p<0.05)")
                - 통계적 인사이트를 독자가 이해할 수 있도록 쉬운 언어로 설명하세요
                - 차트 데이터에 언급된 수치를 기사 본문에서 함께 다뤄주세요
                - 기술통계(평균, 표준편차)와 가설검정 결과를 적절히 혼합하여 근거를 제시하세요
                """);
        }

        return rules.toString();
    }

    private static final String GEMINI_ERROR_MESSAGE = "Gemini 응답을 불러오지 못했습니다.";
    private static final int MIN_CONTENT_LENGTH = 200;

    /**
     * AI 응답 파싱 → AnalysisColumn 저장
     * AI 응답이 유효하지 않으면 null 반환 (저장하지 않음)
     */
    private AnalysisColumn parseAndSaveColumn(String aiResponse, String defaultTitle, String category,
                                               Integer relatedTeamId, Integer relatedPlayerId) {
        // 1. null/빈 응답 검증
        if (aiResponse == null || aiResponse.isBlank()) {
            log.warn("AI 응답이 비어있어 컬럼을 생성하지 않습니다. 제목: {}", defaultTitle);
            return null;
        }

        // 2. Gemini API 에러 메시지 검증
        if (aiResponse.contains(GEMINI_ERROR_MESSAGE)) {
            log.warn("Gemini API 실패 응답 감지, 컬럼을 생성하지 않습니다. 제목: {}", defaultTitle);
            return null;
        }

        // 3. 최소 길이 검증
        if (aiResponse.length() < MIN_CONTENT_LENGTH) {
            log.warn("AI 응답이 너무 짧습니다 ({}자 < {}자). 제목: {}", aiResponse.length(), MIN_CONTENT_LENGTH, defaultTitle);
            return null;
        }

        String title = defaultTitle;
        String content = aiResponse;
        String summary = null;

        if (aiResponse.startsWith("##")) {
            int newlineIdx = aiResponse.indexOf("\n");
            if (newlineIdx > 0) {
                title = aiResponse.substring(2, newlineIdx).trim();
                content = aiResponse.substring(newlineIdx + 1).trim();
            }
        }

        int summaryIdx = content.indexOf("##SUMMARY##");
        if (summaryIdx > 0) {
            summary = content.substring(summaryIdx + 11).trim();
            content = content.substring(0, summaryIdx).trim();
        }

        if (summary == null || summary.isBlank()) {
            summary = ColumnService.extractSummary(content);
        }

        // 4. 파싱 후 본문 최소 길이 재검증
        if (content.isBlank() || content.length() < MIN_CONTENT_LENGTH) {
            log.warn("파싱 후 본문이 너무 짧습니다 ({}자). 제목: {}", content.length(), title);
            return null;
        }

        AnalysisColumn column = AnalysisColumn.builder()
                .title(title)
                .content(content)
                .summary(summary)
                .category(category)
                .relatedTeamId(relatedTeamId)
                .relatedPlayerId(relatedPlayerId)
                .season(CURRENT_SEASON)
                .publishDate(LocalDateTime.now())
                .autoGenerated(true)
                .viewCount(0)
                .build();

        return columnRepository.save(column);
    }

    private double parseDouble(Object value) {
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
