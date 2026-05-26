package org.example.shield.ai.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Spring {@code String → RagFeatureMode} 컨버터 (P5.1 Commit 1 보완).
 *
 * <p>본 컨버터가 등록되면 {@code @Value("${...:off}") RagFeatureMode mode}나
 * {@code @ConfigurationProperties}에서 자동 변환된다. invalid 값은 startup
 * fail-fast로 동작 ({@link RagFeatureMode#fromOrThrow}이 throw).
 *
 * <p>{@code @ConfigurationPropertiesBinding}을 붙이면 Spring Boot가 binding 시점에
 * 이 컨버터를 사용한다.
 */
@Component
@ConfigurationPropertiesBinding
public class RagFeatureModeConverter implements Converter<String, RagFeatureMode> {

    @Override
    public RagFeatureMode convert(String source) {
        // flagName은 컨버터 단계에서 알 수 없으므로 generic 메시지.
        // 호출자가 더 친절한 메시지를 원하면 RagFeatureMode.fromOrThrow를 직접 사용.
        return RagFeatureMode.fromOrThrow(source, "<rag feature flag>");
    }
}
