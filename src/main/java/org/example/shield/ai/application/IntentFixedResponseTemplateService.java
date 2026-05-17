package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class IntentFixedResponseTemplateService {

    private static final String TEMPLATE_PATH = "ai/templates/intent-router-fixed-responses.yaml";

    private final YAMLMapper yamlMapper = new YAMLMapper();
    private final Map<String, String> templates = new ConcurrentHashMap<>();

    @PostConstruct
    void load() {
        templates.put("greeting", "안녕하세요. 상담에 필요한 사실관계를 차근차근 정리해보겠습니다.");
        templates.put("irrelevant", "법률 상담과 관련된 사실관계를 알려주시면 필요한 정보를 정리해드리겠습니다.");
        templates.put("ask_legal_advice", "승소 가능성이나 법적 결론은 단정할 수 없습니다. 판단에 필요한 사실관계를 먼저 정리해드리겠습니다.");
        templates.put("confirm_affirmative", "확인했습니다. 다음으로 필요한 사실관계를 이어서 확인하겠습니다.");
        templates.put("confirm_negative", "알겠습니다. 올바른 내용을 다시 알려주세요.");

        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
        if (!resource.exists()) {
            log.warn("Intent fixed response template not found: {}", TEMPLATE_PATH);
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = yamlMapper.readTree(in);
            root.fields().forEachRemaining(entry -> {
                String ko = entry.getValue().path("ko").asText(null);
                if (ko != null && !ko.isBlank()) {
                    templates.put(entry.getKey(), ko);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to load intent fixed response templates, using defaults: {}", e.getMessage());
        }
    }

    public String get(String key) {
        return templates.getOrDefault(key, templates.get("irrelevant"));
    }
}
