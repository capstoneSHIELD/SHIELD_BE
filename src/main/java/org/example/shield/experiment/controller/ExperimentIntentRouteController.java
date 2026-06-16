package org.example.shield.experiment.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.example.shield.ai.application.IntentClassificationService;
import org.example.shield.ai.dto.ExperimentIntentRouteResponse;
import org.example.shield.ai.dto.ExperimentSelectedLabel;
import org.example.shield.ai.provider.ChatMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 법률 분야 분류 benchmark runner 전용 내부 adapter.
 *
 * <p>운영 공개 API가 아니며 {@code local/test} profile에서만 등록된다.
 * Python runner가 provider별 raw classifier 호출 정보와 parser 결과를 함께 받아
 * 실험 지표를 산출하기 위한 얇은 HTTP boundary다.
 */
@RestController
@Profile({"local", "test"})
@RequestMapping("/internal/experiments/intent-route")
public class ExperimentIntentRouteController {

    private final IntentClassificationService intentClassificationService;

    public ExperimentIntentRouteController(IntentClassificationService intentClassificationService) {
        this.intentClassificationService = intentClassificationService;
    }

    @PostMapping("/preflight")
    public Map<String, Object> preflight(@RequestBody(required = false) ExperimentPreflightRequest request) {
        List<String> providers = request == null ? List.of() : request.providers();
        return Map.of("providers", intentClassificationService.availableExperimentProviders(providers));
    }

    @PostMapping
    public ExperimentIntentRouteResponse route(@Valid @RequestBody ExperimentIntentRouteRequest request) {
        return intentClassificationService.routeForExperiment(
                request.provider(),
                request.mode(),
                request.domain(),
                toChatMessages(request.messages()),
                clean(request.selectedNodeIds()),
                toSelectedLabels(request.selectedLabels()),
                request.historyWindowMessages(),
                request.includeRaw()
        );
    }

    private List<ChatMessage> toChatMessages(List<ExperimentMessageRequest> messages) {
        if (messages == null) {
            return List.of();
        }
        return messages.stream()
                .map(this::toChatMessage)
                .toList();
    }

    private ChatMessage toChatMessage(ExperimentMessageRequest message) {
        String role = message.role() == null ? "USER" : message.role().trim().toUpperCase();
        String content = message.content() == null ? "" : message.content();
        return switch (role) {
            case "ASSISTANT", "CHATBOT", "AI" -> ChatMessage.assistant(content);
            case "SYSTEM" -> ChatMessage.system(content);
            default -> ChatMessage.user(content);
        };
    }

    public record ExperimentPreflightRequest(List<String> providers) {
    }

    public record ExperimentIntentRouteRequest(
            @NotBlank String provider,
            @NotBlank String mode,
            String domain,
            List<ExperimentMessageRequest> messages,
            List<String> selectedNodeIds,
            List<ExperimentSelectedLabelRequest> selectedLabels,
            Integer historyWindowMessages,
            boolean includeRaw
    ) {
    }

    public record ExperimentMessageRequest(String role, String content) {
    }

    public record ExperimentSelectedLabelRequest(
            String nodeId,
            String l1,
            String l2,
            String l3
    ) {
    }

    private List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private List<ExperimentSelectedLabel> toSelectedLabels(List<ExperimentSelectedLabelRequest> labels) {
        if (labels == null) {
            return List.of();
        }
        return labels.stream()
                .filter(label -> label != null && label.nodeId() != null && !label.nodeId().isBlank())
                .map(label -> new ExperimentSelectedLabel(
                        cleanValue(label.nodeId()),
                        cleanValue(label.l1()),
                        cleanValue(label.l2()),
                        cleanValue(label.l3())
                ))
                .toList();
    }

    private String cleanValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
