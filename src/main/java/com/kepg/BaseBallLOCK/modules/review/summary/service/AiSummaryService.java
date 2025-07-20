package com.kepg.BaseBallLOCK.modules.review.summary.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.kepg.BaseBallLOCK.common.ai.GeminiClient;
import com.kepg.BaseBallLOCK.modules.review.domain.Review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiSummaryService {

    private final GeminiClient geminiClient;

    // 날짜별 감정 흐름을 GPT를 통해 요약
    public String summarizeFeelings(Map<LocalDate, List<String>> dailyFeelings) {
        log.info("감정 요약 시작 - 날짜 수: {}", dailyFeelings.size());
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음은 한 야구팬이 일주일 동안 남긴 감정 기록입니다.\n")
        		.append("각 날짜별 감정 흐름을 분석해서 아래 형식처럼 위트 있게 요약해주세요:\n")
        		.append("존재하지 않는 경기 상황(예: 만루홈런, 역전패 등)은 절대 만들어내지 마세요.\n")
        		.append("포맷은 '날짜 범위: 감정 키워드 → 짧고 인상적인 감정 설명' 형식이면 좋아.\n")
                .append("주의할 점은 다음과 같습니다:\n")
        		.append("- 감정 키워드는 실제 등장한 날짜에서만 분석해 주세요. 특정 표현이 하루에만 등장했다면 그 날로 한정해 주세요.\n")
        		.append("- 감정을 과도하게 확장하거나 일반화하지 마세요.\n")
        		.append("- 문장은 너무 길지 않게, 정돈된 느낌으로. 반말은 쓰지 마.\n")
        		.append("- 최대 5줄로 정리해주세요 \n\n");

        dailyFeelings.forEach((date, feelings) -> {
            prompt.append(date).append(": ");
            prompt.append(String.join(", ", feelings));
            prompt.append("\n");
        });

        log.info("감정 요약 프롬프트 길이: {}", prompt.length());
        String result = geminiClient.callGemini(prompt.toString());
        log.info("감정 요약 결과: {}", result);
        
        return result;
    }

    // 주간 리뷰 요약 생성 (summary 필드를 기반으로 요약문 생성)
    public String generateFullSummary(List<Review> reviews) {
        log.info("전체 요약 시작 - 리뷰 수: {}", reviews.size());
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음은 사용자가 일주일 동안 남긴 야구 리뷰 요약입니다.\n")
        .append("전체적인 감정 흐름과 전반적인 분위기를 분석해 주세요.\n")
        .append("문체는 마치 스포츠 캐스터처럼, 약간 위트 있고 흥미롭게 구성해 주세요.\n")
        .append("유머러스하면서도 팬의 진심도 느껴져야 해요.\n")
        .append("주의할 점은 다음과 같습니다:\n")
        .append("- 특정 날짜는 절대 언급하지 마세요.\n")
        .append("- 상투적이고, 올드한 표현은 지양해주세요.\n")
        .append("- 특히 '롤러코스터'라는 단어는 절대 사용하지 마세요. 어떤 비슷한 표현도 금지입니다.\n")
        .append("- 존재하지 않았던 경기 상황(예: 만루홈런, 역전패, 투수 등판, 대타 등)을 상상해서 만들지 마세요.\n")
        .append("- '초반~중반~후반~결론'과 같은 구조는 절대 쓰지 마세요.\n")
        .append("- 문장은 존댓말로 작성해주세요.\n")
        .append("- 각 문장은 끝에 <br> 태그를 붙여 주세요. HTML에서 줄바꿈으로 사용할 예정입니다.\n")
        .append("- 출력은 총 5줄 이상, 10줄 이하로 해주세요.\n")
        .append("- 비속어, 은어는 절대 쓰지 마세요.\n")
        .append("- 너무 장황하거나 늘어지는 말은 피하고, 팬 입장에서 간결하고 감정이 느껴지게 해주세요.\n");

        for (Review review : reviews) {
            if (StringUtils.hasText(review.getSummary())) {
                prompt.append("- ").append(review.getSummary().trim()).append("\n");
            }
        }

        log.info("전체 요약 프롬프트 길이: {}", prompt.length());
        String result = geminiClient.callGemini(prompt.toString());
        log.info("전체 요약 결과: {}", result);
        
        return result;
    }
}