package org.example.shield.ai.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HyperCLOVA X Chat Completions 응답 (Phase P5.5 Commit 1).
 *
 * <pre>
 * {
 *   "status": {"code": "20000", "message": "OK"},
 *   "result": {
 *     "message": {"role": "assistant", "content": "..."},
 *     "stopReason": "stop",
 *     "inputLength": 100,
 *     "outputLength": 50,
 *     "seed": 12345,
 *     "aiFilter": [...]
 *   }
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HyperClovaChatResponse {

    private Status status;
    private Result result;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        private String code;
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Message message;
        private String stopReason;
        private Integer inputLength;
        private Integer outputLength;
        private Integer seed;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private String content;
    }

    /**
     * 응답 본문 텍스트 추출 (없으면 빈 문자열).
     */
    public String extractContent() {
        if (result == null || result.getMessage() == null) {
            return "";
        }
        String content = result.getMessage().getContent();
        return content == null ? "" : content;
    }

    /**
     * 성공 응답 여부 (status code "20000" = HTTP 200 OK).
     */
    public boolean isSuccess() {
        return status != null && "20000".equals(status.getCode());
    }
}
