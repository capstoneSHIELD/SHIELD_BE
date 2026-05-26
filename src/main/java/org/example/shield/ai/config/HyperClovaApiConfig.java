package org.example.shield.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * HyperCLOVA X (Naver Clova Studio) API config (Phase P5.5 Commit 1).
 *
 * <p>현재 SHIELD에서 사용 목적:
 * <ul>
 *   <li>Output compliance shadow judge (한국 법조 의미론적 단정 감지) — P5.5 Commit 2부터</li>
 *   <li>(향후) Chat/Brief 생성 shadow 비교 — P5.5 Commit 4</li>
 * </ul>
 *
 * <p>API key는 {@code HYPERCLOVA_API_KEY} 환경변수 (없으면 빈 문자열 — bean은 생성되되 호출 시 실패).
 * Bean 자체는 항상 등록하여 다른 컴포넌트의 {@code @ConditionalOnBean} 단순화.
 *
 * <p>Pricing은 {@link CoherePricingProperties}와 별도로 코드 내 상수 또는
 * application.yml 외부화 (P5.5 후속).
 */
@Configuration
@Getter
public class HyperClovaApiConfig {

    @Value("${hyperclova.api-key:${HYPERCLOVA_API_KEY:}}")
    private String apiKey;

    @Value("${hyperclova.base-url:https://clovastudio.stream.ntruss.com}")
    private String baseUrl;

    @Value("${hyperclova.model.chat:HCX-005}")
    private String chatModel;

    @Value("${hyperclova.model.judge:HCX-005}")
    private String judgeModel;

    @Value("${hyperclova.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${hyperclova.timeout.read:30000}")
    private int readTimeout;

    @Value("${hyperclova.timeout.read-judge:3000}")
    private int judgeReadTimeout;

    @Bean
    public WebClient hyperClovaWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024));

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        return builder.build();
    }

    /**
     * API key 설정 여부 — 빈은 항상 등록되지만 키 없으면 호출 시 실패.
     * 호출자가 사전 가드용으로 사용.
     */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
