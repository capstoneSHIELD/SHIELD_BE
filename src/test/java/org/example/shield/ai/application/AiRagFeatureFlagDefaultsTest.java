package org.example.shield.ai.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AiRagFeatureFlagDefaultsTest {

    @Test
    @DisplayName("AI/RAG risky features keep safe defaults for rollout")
    void riskyFeaturesKeepSafeDefaults() throws Exception {
        String yml = new String(
                new ClassPathResource("application.yml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(yml).contains("shadow-mode: ${AI_INTENT_ROUTER_SHADOW_MODE:true}");
        assertThat(yml).contains("enable-ask-legal-advice-skip: ${AI_INTENT_ROUTER_ENABLE_ASK_LEGAL_ADVICE_SKIP:false}");
        assertThat(yml).contains("enable-greeting-skip: ${AI_INTENT_ROUTER_ENABLE_GREETING_SKIP:false}");
        assertThat(yml).contains("enable-irrelevant-skip: ${AI_INTENT_ROUTER_ENABLE_IRRELEVANT_SKIP:false}");
        assertThat(yml).contains("enable-confirm: ${AI_INTENT_ROUTER_ENABLE_CONFIRM:false}");
        assertThat(yml).contains("enable-slot-auto-update: ${AI_INTENT_ROUTER_ENABLE_SLOT_AUTO_UPDATE:false}");
        assertThat(yml).contains("enabled: ${AI_DYNAMIC_PLAN_ENABLED:false}");
        assertThat(yml).contains("fusion-mode: ${AI_RAG_FUSION_MODE:weighted}");
        assertThat(yml).contains("enabled: ${AI_RAG_RETRIEVAL_GATE_ENABLED:false}");
        assertThat(yml).contains("enabled: ${AI_RAG_INTENT_AWARE_ENABLED:false}");
        assertThat(yml).contains("shadow-enabled: ${AI_OUTPUT_JUDGE_SHADOW_ENABLED:false}");
        assertThat(yml).contains("sampling-rate: ${AI_OUTPUT_JUDGE_SAMPLING_RATE:0.0}");
    }
}
