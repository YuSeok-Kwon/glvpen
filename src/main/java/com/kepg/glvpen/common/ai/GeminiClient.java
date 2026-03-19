package com.kepg.glvpen.common.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class GeminiClient {

    @Value("${gemini.api-key}")
    private String apiKey;

    private static final String ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent";
    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    public String callGemini(String prompt) {
        int maxRetries = 2;
        int retryDelay = 15000; // 15초
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Gemini API 호출 시작 (시도 {}/{}) - prompt 길이: {}", attempt, maxRetries, prompt.length());
                
                Map<String, Object> message = Map.of("parts", List.of(Map.of("text", prompt)));
                Map<String, Object> request = Map.of("contents", List.of(message));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<String> entity = new HttpEntity<>(gson.toJson(request), headers);

                String url = ENDPOINT + "?key=" + apiKey;
                log.info("Gemini API URL: {}", ENDPOINT);
                log.info("API 키 앞 10글자: {}", apiKey.substring(0, Math.min(10, apiKey.length())));
                
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                log.info("Gemini API 응답 상태: {}", response.getStatusCode());
                log.info("Gemini API 응답 본문: {}", response.getBody());

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Gemini API 응답 내용 길이: {}", response.getBody().length());
                    JsonObject root = JsonParser.parseString(response.getBody()).getAsJsonObject();
                    log.info("파싱된 JSON: {}", root.toString());
                    
                    JsonArray candidates = root.getAsJsonArray("candidates");
                    if (candidates != null && !candidates.isEmpty()) {
                        log.info("candidates 배열 크기: {}", candidates.size());
                        JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                        log.info("첫 번째 candidate: {}", firstCandidate.toString());
                        
                        JsonObject content = firstCandidate.getAsJsonObject("content");
                        if (content != null) {
                            JsonArray parts = content.getAsJsonArray("parts");
                            if (parts != null && !parts.isEmpty()) {
                                String result = parts.get(0).getAsJsonObject().get("text").getAsString().trim();
                                log.info("Gemini API 성공 - 응답 길이: {}", result.length());
                                return result;
                            } else {
                                log.error("parts 배열이 비어있음");
                            }
                        } else {
                            log.error("content 객체가 null");
                        }
                    } else {
                        log.error("candidates 배열이 비어있음 또는 null");
                    }
                } else {
                    log.error("Gemini API 실패 - 응답 코드: {}, 응답 내용: {}", response.getStatusCode(), response.getBody());
                }

            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                log.warn("할당량 초과 (시도 {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    log.info("{}ms 후 재시도...", retryDelay);
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Gemini 호출 실패 (시도 {}/{})", attempt, maxRetries, e);
                if (attempt >= maxRetries) {
                    break;
                }
            }
        }

        log.error("Gemini API 호출 실패 - 기본 메시지 반환");
        return "Gemini 응답을 불러오지 못했습니다.";
    }
}