package org.example.shield.ai.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * HyperCLOVA X Chat Completions 요청 (Phase P5.5 Commit 1).
 *
 * <p>Naver Clova Studio v3 chat-completions API 스키마.
 * <pre>
 * {
 *   "messages": [{"role": "system", "content": "..."}, {"role": "user", "content": "..."}],
 *   "topP": 0.8,
 *   "temperature": 0.3,
 *   "maxTokens": 512,
 *   "repetitionPenalty": 1.1,
 *   "stop": [],
 *   "includeAiFilters": true
 * }
 * </pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HyperClovaChatRequest {

    private List<Message> messages;
    private Double topP;
    private Double temperature;
    private Integer maxTokens;
    private Double repetitionPenalty;
    private List<String> stop;
    private Boolean includeAiFilters;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        private String role;
        private String content;

        public static Message system(String text) {
            return Message.builder().role("system").content(text).build();
        }

        public static Message user(String text) {
            return Message.builder().role("user").content(text).build();
        }

        public static Message assistant(String text) {
            return Message.builder().role("assistant").content(text).build();
        }
    }

    /**
     * Judge용 저온도 short response 기본값.
     */
    public static HyperClovaChatRequest forJudge(List<Message> messages, int maxTokens) {
        return HyperClovaChatRequest.builder()
                .messages(messages)
                .topP(0.5)
                .temperature(0.1)
                .maxTokens(maxTokens)
                .repetitionPenalty(1.1)
                .includeAiFilters(false)   // judge는 자체 평가가 목적, 추가 필터 불필요
                .build();
    }
}
