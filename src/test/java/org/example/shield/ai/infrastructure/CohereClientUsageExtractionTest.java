package org.example.shield.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.dto.CohereChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CohereClientUsageExtractionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CohereClient client;

    @BeforeEach
    void setUp() throws Exception {
        var ctor = CohereClient.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object[] args = new Object[ctor.getParameterCount()];
        client = (CohereClient) ctor.newInstance(args);
    }

    @Test
    void usageBilledUnitsAreUsedForTokenExtraction() throws Exception {
        String rawResponse = """
                {
                  "id": "resp-1",
                  "message": {"role": "assistant", "content": [{"type": "text", "text": "{\\"ok\\":true}"}]},
                  "finish_reason": "COMPLETE",
                  "usage": {
                    "billed_units": {"input_tokens": 10, "output_tokens": 15},
                    "tokens": {"input_tokens": 570, "output_tokens": 17},
                    "cached_tokens": 512
                  }
                }
                """;
        CohereChatResponse response = objectMapper.readValue(rawResponse, CohereChatResponse.class);

        assertThat(invokeTokenExtractor("extractInputTokens", response)).isEqualTo(10);
        assertThat(invokeTokenExtractor("extractOutputTokens", response)).isEqualTo(15);
    }

    private Integer invokeTokenExtractor(String methodName, CohereChatResponse response) throws Exception {
        Method method = CohereClient.class.getDeclaredMethod(methodName, CohereChatResponse.class);
        method.setAccessible(true);
        return (Integer) method.invoke(client, response);
    }
}
