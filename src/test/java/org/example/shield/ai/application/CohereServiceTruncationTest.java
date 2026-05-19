package org.example.shield.ai.application;

import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.infrastructure.SanitizeService;
import org.example.shield.common.enums.MessageRole;
import org.example.shield.consultation.application.ClassificationCandidate;
import org.example.shield.consultation.application.ClassificationResolver;
import org.example.shield.consultation.domain.Consultation;
import org.example.shield.consultation.domain.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CohereServiceTruncationTest {

    @Test
    @DisplayName("truncateMessages — 메시지가 maxMessages+1 이하면 그대로 반환")
    void truncateMessages_withinLimit() throws Exception {
        List<CohereChatRequest.Message> messages = List.of(
                CohereChatRequest.Message.system("system prompt"),
                CohereChatRequest.Message.user("msg1"),
                CohereChatRequest.Message.assistant("reply1")
        );

        List<CohereChatRequest.Message> result = invokeTruncate(messages, 20);
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("truncateMessages — 메시지가 maxMessages+1 초과 시 시스템 프롬프트 + 최근 N개 유지")
    void truncateMessages_exceedsLimit() throws Exception {
        List<CohereChatRequest.Message> messages = new ArrayList<>();
        messages.add(CohereChatRequest.Message.system("system prompt"));
        for (int i = 1; i <= 10; i++) {
            messages.add(CohereChatRequest.Message.user("user" + i));
            messages.add(CohereChatRequest.Message.assistant("reply" + i));
        }
        // 21 messages total: 1 system + 20 conversation

        List<CohereChatRequest.Message> result = invokeTruncate(messages, 4);

        assertThat(result).hasSize(5); // system + last 4
        assertThat(result.get(0).getRole()).isEqualTo("system");
        assertThat(result.get(0).getContent()).isEqualTo("system prompt");
        // Last 4 messages should end with reply10
        assertThat(result.get(result.size() - 1).getContent()).isEqualTo("reply10");
    }

    @Test
    @DisplayName("truncateMessages — maxMessages=0이면 시스템 프롬프트만 유지")
    void truncateMessages_zeroMax() throws Exception {
        List<CohereChatRequest.Message> messages = List.of(
                CohereChatRequest.Message.system("system prompt"),
                CohereChatRequest.Message.user("msg1")
        );

        List<CohereChatRequest.Message> result = invokeTruncate(messages, 0);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo("system");
    }

    @Test
    @DisplayName("buildChatMessages keeps Q/A memory in system prompt even when raw history is truncated")
    void buildChatMessages_truncatesRawHistoryButKeepsMemorySummary() throws Exception {
        CohereService service = createMinimalService();

        CohereApiConfig config = mock(CohereApiConfig.class);
        when(config.getMaxHistoryMessages()).thenReturn(2);
        PromptService promptService = mock(PromptService.class);
        when(promptService.loadRouterChatPrompt()).thenReturn("BASE RULES");
        SanitizeService sanitizeService = mock(SanitizeService.class);
        when(sanitizeService.sanitizeUserText(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ClassificationResolver classificationResolver = mock(ClassificationResolver.class);
        when(classificationResolver.candidateForCollection(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ClassificationCandidate.empty());

        setField(service, "config", config);
        setField(service, "promptService", promptService);
        setField(service, "sanitizeService", sanitizeService);
        setField(service, "classificationResolver", classificationResolver);

        Consultation consultation = mock(Consultation.class);
        List<Message> history = List.of(
                message(MessageRole.CHATBOT, "Question 1?"),
                message(MessageRole.USER, "Answer 1"),
                message(MessageRole.CHATBOT, "Question 2?"),
                message(MessageRole.USER, "Answer 2"),
                message(MessageRole.CHATBOT, "Question 3?"),
                message(MessageRole.USER, "Answer 3")
        );

        Method buildChatMessages = CohereService.class.getDeclaredMethod(
                "buildChatMessages", Consultation.class, String.class, String.class, List.class);
        buildChatMessages.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<CohereChatRequest.Message> messages = (List<CohereChatRequest.Message>) buildChatMessages.invoke(
                service, consultation, "latest", "", history);

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getContent()).contains("=== RECENT Q/A MEMORY ===");
        assertThat(messages.get(0).getContent()).contains("Question 3?");
        assertThat(messages.get(0).getContent()).contains("Answer 3");
        assertThat(messages.get(1).getContent()).isEqualTo("Answer 3");
        assertThat(messages.get(2).getContent()).isEqualTo("latest");
    }

    /**
     * 리플렉션으로 private truncateMessages 호출.
     */
    private List<CohereChatRequest.Message> invokeTruncate(List<CohereChatRequest.Message> messages, int max)
            throws Exception {
        Method method = CohereService.class.getDeclaredMethod(
                "truncateMessages", List.class, int.class);
        method.setAccessible(true);
        CohereService service = createMinimalService();
        @SuppressWarnings("unchecked")
        List<CohereChatRequest.Message> result = (List<CohereChatRequest.Message>) method.invoke(service, messages, max);
        return result;
    }

    private CohereService createMinimalService() throws Exception {
        var constructor = CohereService.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        // CohereService 생성자 파라미터 수만큼 null 을 채워 넣는다 (truncateMessages 만 테스트하므로 의존성 불필요).
        int paramCount = constructor.getParameterCount();
        Object[] args = new Object[paramCount];
        return (CohereService) constructor.newInstance(args);
    }

    private void setField(CohereService service, String fieldName, Object value) throws Exception {
        Field field = CohereService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private Message message(MessageRole role, String content) {
        return role == MessageRole.USER
                ? Message.createUserMessage(UUID.randomUUID(), content)
                : Message.createAiMessage(UUID.randomUUID(), content, null, null, null, null);
    }
}
