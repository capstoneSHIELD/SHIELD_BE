package org.example.shield.consultation.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.consultation.domain.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SendMessageResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("empty checklist is serialized as a stable object")
    void emptyChecklist_isSerializedAsStableObject() throws Exception {
        UUID consultationId = UUID.randomUUID();
        Message message = Message.createAiMessage(consultationId, "ok", null, null, null, null);

        SendMessageResponse response = SendMessageResponse.from(
                message,
                false,
                SendMessageResponse.Progress.of(0, 10));

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(root.has("checklist")).isTrue();
        assertThat(root.path("checklist").path("caseType").isObject()).isTrue();
        assertThat(root.path("checklist").path("caseType").size()).isZero();
        assertThat(root.path("checklist").path("items").isArray()).isTrue();
        assertThat(root.path("checklist").path("items").size()).isZero();
        assertThat(root.path("checklist").path("warnings").isArray()).isTrue();
        assertThat(root.path("checklist").path("warnings").size()).isZero();
    }
}
