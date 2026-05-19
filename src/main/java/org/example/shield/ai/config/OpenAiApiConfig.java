package org.example.shield.ai.config;

import io.netty.channel.ChannelOption;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * OpenAI API client configuration for lightweight intent classification.
 */
@Configuration
@Getter
public class OpenAiApiConfig {

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${openai.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${openai.timeout.read-classify:15000}")
    private int classifyReadTimeout;

    @Value("${openai.classify.model:gpt-5-nano}")
    private String classifyModel;

    @Value("${openai.classify.max-tokens:1024}")
    private int classifyMaxTokens;

    @Value("${openai.classify.reasoning-effort:minimal}")
    private String classifyReasoningEffort;

    @Value("${app.ai.openai.structured-output-enabled:true}")
    private boolean structuredOutputEnabled;

    @Bean
    public WebClient openAiWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}
