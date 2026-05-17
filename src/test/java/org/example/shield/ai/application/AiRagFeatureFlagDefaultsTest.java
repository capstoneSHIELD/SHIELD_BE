package org.example.shield.ai.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AiRagFeatureFlagDefaultsTest {

    @Test
    @DisplayName("AI/RAG risky features resolve to safe defaults for rollout")
    void riskyFeaturesKeepSafeDefaults() throws Exception {
        MockEnvironment env = loadApplicationYamlWithoutExternalOverrides();

        assertThat(env.getProperty("app.ai.intent-router.shadow-mode", Boolean.class)).isTrue();
        assertThat(env.getProperty("app.ai.intent-router.shadow-export.enabled", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.intent-router.shadow-export.include-raw-text", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.intent-router.enable-ask-legal-advice-skip", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.intent-router.enable-greeting-skip", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.intent-router.enable-irrelevant-skip", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.intent-router.enable-confirm", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.intent-router.enable-slot-auto-update", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.dynamic-plan.enabled", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.dynamic-plan.backfill.execute-enabled", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.rag.fusion-mode")).isEqualTo("weighted");
        assertThat(env.getProperty("app.ai.rag.retrieval-gate.enabled", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.rag.intent-aware.enabled", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.output-judge.shadow-enabled", Boolean.class)).isFalse();
        assertThat(env.getProperty("app.ai.output-judge.sampling-rate", Double.class)).isEqualTo(0.0d);
    }

    private MockEnvironment loadApplicationYamlWithoutExternalOverrides() throws Exception {
        MockEnvironment env = new MockEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (PropertySource<?> source : loader.load(
                "applicationConfig",
                new ClassPathResource("application.yml"))) {
            env.getPropertySources().addLast(source);
        }
        return env;
    }
}
