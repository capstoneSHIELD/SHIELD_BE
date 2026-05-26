package org.example.shield.ai.provider.cohere;

import org.example.shield.ai.application.CohereService;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.provider.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CohereClassificationClientAdapter} 위임 + ChatMessage 변환 검증.
 */
class CohereClassificationClientAdapterTest {

    private CohereService cohereService;
    private CohereClassificationClientAdapter adapter;

    @BeforeEach
    void setUp() {
        cohereService = mock(CohereService.class);
        adapter = new CohereClassificationClientAdapter(cohereService);
    }

    @Test
    @DisplayName("providerKey는 'cohere'")
    void providerKey() {
        assertThat(adapter.providerKey()).isEqualTo("cohere");
    }

    @Test
    @DisplayName("classify — CohereService.callClassify에 위임 + role 변환")
    @SuppressWarnings("unchecked")
    void classify_delegatesAndConvertsRoles() {
        AiCallResult<String> expected = new AiCallResult<>("id-1", "{}", 10, 20, 100);
        when(cohereService.callClassify(any())).thenReturn(expected);

        List<ChatMessage> input = List.of(
                ChatMessage.system("you are a classifier"),
                ChatMessage.user("what is this about?"),
                ChatMessage.assistant("legal advice")
        );

        AiCallResult<String> actual = adapter.classify(input);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<List<CohereChatRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(cohereService).callClassify(captor.capture());
        List<CohereChatRequest.Message> converted = captor.getValue();

        assertThat(converted).hasSize(3);
        assertThat(converted.get(0).getRole()).isEqualTo("system");
        assertThat(converted.get(1).getRole()).isEqualTo("user");
        assertThat(converted.get(2).getRole()).isEqualTo("assistant");
        assertThat(converted.get(0).getContent()).isEqualTo("you are a classifier");
        assertThat(converted.get(1).getContent()).isEqualTo("what is this about?");
        assertThat(converted.get(2).getContent()).isEqualTo("legal advice");
    }
}
