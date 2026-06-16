package org.example.shield.experiment.application;

import lombok.RequiredArgsConstructor;
import org.example.shield.ai.application.IntentClassificationService;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.provider.AiClassificationClient;
import org.example.shield.ai.provider.ChatMessage;
import org.example.shield.common.enums.MessageRole;
import org.example.shield.consultation.domain.Message;
import org.example.shield.experiment.controller.dto.IntentRouteExperimentRequest;
import org.example.shield.experiment.controller.dto.IntentRouteExperimentResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile({"local", "test"})
@RequiredArgsConstructor
public class IntentRouteExperimentService {

    private final IntentClassificationService intentClassificationService;
    private final List<AiClassificationClient> classificationClients;

    public Map<String, Boolean> preflight(List<String> requestedProviders) {
        Map<String, AiClassificationClient> clients = clientsByProvider();
        Map<String, Boolean> result = new HashMap<>();
        for (String provider : requestedProviders == null ? List.<String>of() : requestedProviders) {
            result.put(normalizeProvider(provider), clients.containsKey(normalizeProvider(provider)));
        }
        return result;
    }

    public IntentRouteExperimentResponse route(IntentRouteExperimentRequest request) {
        String requestedProvider = normalizeProvider(request.provider());
        AiClassificationClient client = clientsByProvider().get(requestedProvider);
        if (client == null) {
            return IntentRouteExperimentResponse.error(
                    null,
                    request.provider(),
                    request.mode(),
                    request.domain(),
                    "config_error",
                    "No AiClassificationClient registered for provider: " + requestedProvider);
        }

        AiCallResult<String> raw;
        try {
            raw = callProvider(request, client);
        } catch (Exception e) {
            return IntentRouteExperimentResponse.error(
                    requestedProvider,
                    request.provider(),
                    request.mode(),
                    request.domain(),
                    "upstream_error",
                    e.getMessage());
        }

        try {
            IntentRouterResponse parsed = intentClassificationService.parseIntentRouterResponseForExperiment(raw.data());
            return IntentRouteExperimentResponse.success(
                    requestedProvider,
                    request.provider(),
                    request.mode(),
                    request.domain(),
                    raw,
                    parsed,
                    includeRaw(request));
        } catch (Exception e) {
            return IntentRouteExperimentResponse.parseFailure(
                    requestedProvider,
                    request.provider(),
                    request.mode(),
                    request.domain(),
                    raw,
                    e.getMessage());
        }
    }

    private AiCallResult<String> callProvider(IntentRouteExperimentRequest request, AiClassificationClient client) {
        List<Message> domainMessages = toDomainMessages(request.messages());
        List<ChatMessage> providerMessages = intentClassificationService.buildProviderMessagesForExperiment(
                domainMessages,
                request.domain());
        return client.classify(providerMessages);
    }

    private List<Message> toDomainMessages(List<IntentRouteExperimentRequest.MessagePayload> messages) {
        UUID consultationId = UUID.randomUUID();
        if (messages == null) {
            return List.of();
        }
        return messages.stream()
                .filter(message -> message != null && message.content() != null && !message.content().isBlank())
                .map(message -> toDomainMessage(consultationId, message))
                .toList();
    }

    private Message toDomainMessage(UUID consultationId, IntentRouteExperimentRequest.MessagePayload message) {
        MessageRole role = parseRole(message.role());
        if (role == MessageRole.CHATBOT) {
            return Message.createAiMessage(consultationId, message.content(), null, null, null, null);
        }
        return Message.createUserMessage(consultationId, message.content());
    }

    private MessageRole parseRole(String role) {
        if (role == null) {
            return MessageRole.USER;
        }
        String normalized = role.trim().toUpperCase();
        if ("ASSISTANT".equals(normalized) || "AI".equals(normalized) || "CHATBOT".equals(normalized)) {
            return MessageRole.CHATBOT;
        }
        return MessageRole.USER;
    }

    private Map<String, AiClassificationClient> clientsByProvider() {
        Map<String, AiClassificationClient> clients = new HashMap<>();
        for (AiClassificationClient client : classificationClients) {
            clients.put(normalizeProvider(client.providerKey()), client);
        }
        return clients;
    }

    private String normalizeProvider(String provider) {
        return provider == null || provider.isBlank() ? "" : provider.trim().toLowerCase();
    }

    private boolean includeRaw(IntentRouteExperimentRequest request) {
        return request.includeRaw() == null || request.includeRaw();
    }

}
