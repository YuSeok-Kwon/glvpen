package com.kepg.glvpen.modules.analysis.service;

import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.kepg.glvpen.common.ai.GeminiClient;
import com.kepg.glvpen.modules.analysis.dto.AiInsightRequestDTO;
import com.kepg.glvpen.modules.analysis.dto.AiInsightResponseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInsightService {

    private final GeminiClient geminiClient;

    public AiInsightResponseDTO generateInsight(AiInsightRequestDTO request) {
        try {
            String prompt = buildPrompt(request);
            String result = geminiClient.callGemini(prompt);

            if (result == null || result.contains("Gemini 응답을 불러오지 못했습니다")) {
                return AiInsightResponseDTO.builder()
                        .chartType(request.getChartType())
                        .success(false)
                        .errorMessage("AI 응답을 생성하지 못했습니다.")
                        .build();
            }

            // 마크다운 코드블록 제거
            String cleaned = result.replaceAll("```html\\s*", "").replaceAll("```\\s*", "").trim();

            return AiInsightResponseDTO.builder()
                    .chartType(request.getChartType())
                    .insight(cleaned)
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("AI 인사이트 생성 실패 (chartType={}): {}", request.getChartType(), e.getMessage());
            return AiInsightResponseDTO.builder()
                    .chartType(request.getChartType())
                    .success(false)
                    .errorMessage("AI 분석 중 오류가 발생했습니다.")
                    .build();
        }
    }

    private String buildPrompt(AiInsightRequestDTO request) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 KBO 야구 데이터 분석 전문가입니다.\n");
        sb.append("아래 차트 데이터를 분석하여 핵심 인사이트를 설명해주세요.\n\n");

        sb.append("=== 차트 정보 ===\n");
        sb.append("차트 제목: ").append(request.getTitle()).append("\n");
        sb.append("차트 유형: ").append(request.getChartType()).append("\n");

        if (request.getLabels() != null) {
            sb.append("라벨: ").append(String.join(", ", request.getLabels())).append("\n");
        }

        if (request.getDatasets() != null) {
            for (AiInsightRequestDTO.DatasetSummary ds : request.getDatasets()) {
                sb.append("데이터셋 [").append(ds.getLabel()).append("]: ");
                if (ds.getData() != null) {
                    sb.append(ds.getData().stream()
                            .map(d -> String.format("%.2f", d))
                            .collect(Collectors.joining(", ")));
                }
                sb.append("\n");
            }
        }

        if (request.getExtra() != null && !request.getExtra().isEmpty()) {
            sb.append("추가 컨텍스트: ");
            request.getExtra().forEach((k, v) -> sb.append(k).append("=").append(v).append(", "));
            sb.append("\n");
        }

        sb.append("\n=== 분석 관점 ===\n");
        sb.append(getAnalysisPerspective(request.getChartType())).append("\n");

        sb.append("\n=== 응답 규칙 ===\n");
        sb.append("- 3~5문장으로 간결하게 작성\n");
        sb.append("- HTML <p> 태그로 감싸서 작성\n");
        sb.append("- 핵심 수치는 <strong> 태그로 강조\n");
        sb.append("- 한국어로 작성\n");
        sb.append("- 존댓말 사용\n");
        sb.append("- 데이터에 있는 수치만 사용하고, 없는 수치는 만들지 마세요\n");

        return sb.toString();
    }

    private String getAnalysisPerspective(String chartType) {
        return switch (chartType) {
            case "woba-distribution" -> "포지션별 타격 생산성(wOBA)을 비교 분석하세요. 어떤 포지션이 가장 높은/낮은 생산성을 보이는지, 그 이유를 추론해주세요.";
            case "team-balance" -> "팀별 공격력(타자 wOBA)과 투수력(FIP)의 밸런스를 분석하세요. 공격형/수비형 팀을 분류하고, 밸런스가 좋은 팀을 식별해주세요.";
            case "league-trend" -> "시즌별 리그 평균 ERA와 OPS 추이를 분석하세요. 타고투저 또는 투고타저 흐름이 있는지 설명해주세요.";
            case "homerun-trend" -> "시즌별 리그 총 홈런 수 변화를 분석하세요. 증감 추세와 가능한 원인(반발계수, 규정 변화 등)을 추론해주세요.";
            case "series-comparison" -> "정규시즌과 포스트시즌의 주요 성적 차이를 비교 분석하세요. 포스트시즌에서 특별히 달라지는 지표가 있는지 설명해주세요.";
            case "rookie-impact" -> "신인 선수들의 즉시 전력 기여도를 분석하세요. 주목할 만한 신인의 성적과 팀에 미친 영향을 설명해주세요.";
            case "school-type" -> "고졸/대졸/해외 출신 선수 비율 트렌드를 분석하세요. 시간에 따른 변화 패턴과 의미를 설명해주세요.";
            case "game-event-trend" -> "경기 이벤트(안타, 홈런, 삼진, 볼넷 등) 발생 추이를 분석하세요. 리그 전체적인 경기 양상의 변화를 설명해주세요.";
            case "win-prediction" -> "승리 확률 예측 결과를 분석하세요. 어떤 지표가 승리에 가장 큰 영향을 미치는지 설명해주세요.";
            case "marcel-projection" -> "Marcel 방식 성적 예측 결과를 분석하세요. 예측 성적의 상승/하락 원인과 주요 변화 지표를 설명해주세요.";
            case "similarity" -> "유사 선수 분석 결과를 설명하세요. 기준 선수와 유사 선수들의 공통 특성과 플레이 스타일을 분석해주세요.";
            case "clutch-index" -> "클러치 지수 상위 선수들을 분석하세요. 위기 상황에서 강한 선수들의 공통점과 핵심 지표를 설명해주세요.";
            case "fatigue-analysis" -> "투수의 등판 간격별 ERA 패턴과 전후반기 성적 변화를 분석하세요. 피로도가 성적에 미치는 영향을 설명해주세요.";
            default -> "차트 데이터의 핵심 패턴과 인사이트를 분석해주세요.";
        };
    }
}
