package org.example.shield.baseline;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.common.enums.UserRole;
import org.example.shield.consultation.application.MessageService;
import org.example.shield.consultation.domain.Consultation;
import org.example.shield.consultation.exception.ConsultationTurnLimitExceededException;
import org.example.shield.consultation.infrastructure.ConsultationRepository;
import org.example.shield.user.domain.User;
import org.example.shield.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 baseline 실측 통합 테스트.
 *
 * <p>실제 외부 인프라(Cohere API, PostgreSQL/pgvector, Redis)를 사용해 {@code sendMessage}
 * 파이프라인 N건을 발사하고 단계별 Micrometer 타이머 통계를 출력한다.
 * {@code /actuator/prometheus} 시리즈 노출 여부도 함께 검증한다.
 * {@code .env} 의 자격증명({@link org.example.shield.config.DotenvEnvironmentPostProcessor})을 그대로 사용한다.</p>
 *
 * <p><b>사이드이펙트 (반드시 확인):</b></p>
 * <ul>
 *   <li>실제 Cohere API 호출 — 분류(#1) + 본응답(#2) × 표본 수만큼 비용 발생</li>
 *   <li>실제 DB 에 USER / CHATBOT 메시지가 영구 저장됨 — <b>전용 테스트 상담을 사용</b></li>
 *   <li>한 상담당 사용자 턴 상한 10 — 그 이상 발사하려면 여러 consultationId 를 콤마로 전달</li>
 * </ul>
 *
 * <p><b>실행 방법 (PowerShell, S: 드라이브 + JDK 21 환경):</b></p>
 * <pre>{@code
 * $env:BASELINE_REAL = "true"
 * $env:BASELINE_CONSULTATION_IDS = "<uuid1>,<uuid2>,...,<uuid10>"  # L1 분야 선택된 상담
 * $env:BASELINE_SAMPLE_COUNT = "100"   # 미설정 시 10
 * $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
 * cd S:\
 * ./gradlew -g C:\GradleHome test --tests "org.example.shield.baseline.BaselineMetricsRealIT"
 * }</pre>
 *
 * <p><b>비활성화 (기본):</b> {@code BASELINE_REAL} 미설정 시 {@link EnabledIfEnvironmentVariable}
 * 가 클래스 단위로 SKIP 시키며 Spring 컨텍스트도 로딩되지 않는다. 일상 {@code ./gradlew test} 는 영향 없음.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "BASELINE_REAL", matches = "true")
@Slf4j
class BaselineMetricsRealIT {

    // Firebase service-account.json 이 로컬에 없으므로 mock (ShieldApplicationTests 와 동일 패턴)
    @MockitoBean private FirebaseApp firebaseApp;
    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private MessageService messageService;
    @Autowired private MeterRegistry registry;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsultationRepository consultationRepository;
    @LocalServerPort private int port;

    /** 테스트용 fixture 식별자 — 매 실행 시 reuse, 일반 사용자와 충돌 없도록 격리된 이메일/도메인 사용. */
    private static final String FIXTURE_USER_EMAIL = "baseline-load@shield.local";
    private static final List<String> FIXTURE_L1_DOMAIN = List.of("부동산 거래");

    @Value("${BASELINE_CONSULTATION_IDS:}")
    private String consultationIdsCsv;

    @Value("${BASELINE_SAMPLE_COUNT:10}")
    private int sampleCount;

    /** 다양성 확보를 위한 발화 풀 — 인덱스 % SAMPLES.length 로 라운드로빈. */
    private static final String[] SAMPLES = {
            "전세보증금을 못 받고 있어요 어떻게 해야 하나요",
            "임차인이 월세를 3개월째 안 내고 있어요",
            "이혼하려면 어떤 절차를 거쳐야 하나요",
            "상속 포기를 하려면 언제까지 신청해야 하나요",
            "교통사고가 났는데 합의금이 적정한지 모르겠어요",
            "회사에서 부당해고를 당했어요 어떻게 대응해야 하나요",
            "물건을 사고 환불을 못 받고 있어요",
            "층간소음 분쟁이 심해지고 있어요",
            "사기 피해를 당했는데 형사고소가 가능한가요",
            "유언장이 없는 상태에서 형제간 상속 분쟁이 있어요",
    };

    @Test
    void real_traffic_produces_per_stage_metrics_and_exposes_prometheus_series() {
        // ARRANGE — consultation 목록: 환경변수 명시 시 그것을, 미설정 시 fixture 자동 생성
        List<UUID> consultations = resolveConsultations(sampleCount);
        log.info("[baseline] consultations={}, sampleCount={}", consultations.size(), sampleCount);

        // ACT — sendMessage 라운드로빈 발사. 턴 상한 도달 시 skip.
        List<Long> wallClockMs = new ArrayList<>(sampleCount);
        int turnLimited = 0;
        int errored = 0;
        for (int i = 0; i < sampleCount; i++) {
            UUID cid = consultations.get(i % consultations.size());
            String msg = SAMPLES[i % SAMPLES.length];
            long t0 = System.nanoTime();
            try {
                messageService.sendMessage(cid, msg);
                long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
                wallClockMs.add(elapsedMs);
                log.info("[baseline] [{}/{}] OK  {}ms  cid={}", i + 1, sampleCount, elapsedMs, cid);
            } catch (ConsultationTurnLimitExceededException e) {
                turnLimited++;
                log.warn("[baseline] [{}/{}] TURN_LIMIT cid={}", i + 1, sampleCount, cid);
            } catch (RuntimeException e) {
                errored++;
                log.warn("[baseline] [{}/{}] ERROR  cid={}  msg={}", i + 1, sampleCount, cid, e.getMessage());
            }
        }
        int success = wallClockMs.size();
        log.info("[baseline] 완료: success={}/{} (turn_limit={}, error={})",
                success, sampleCount, turnLimited, errored);
        assertThat(success).as("성공 표본 0건 — turn limit / 인증 / 외부 API 점검").isPositive();

        // 클라이언트 사이드 wall-clock p50/p95 (참고용; 서버 사이드는 Timer 통계가 정확)
        Collections.sort(wallClockMs);
        long cliP50 = wallClockMs.get(success / 2);
        long cliP95 = wallClockMs.get(Math.min(success - 1, (int) Math.ceil(success * 0.95) - 1));
        long cliMin = wallClockMs.get(0);
        long cliMax = wallClockMs.get(success - 1);

        // ASSERT 1 — 단계별 서버 사이드 Timer 통계 + 마크다운 표 출력 (docs 에 복사 가능)
        StringBuilder report = new StringBuilder();
        report.append("\n\n=== Phase 0 baseline (real infra) — sample success=").append(success)
                .append("/").append(sampleCount).append(" ===\n");
        report.append(String.format("| %-44s | %5s | %12s | %12s |%n",
                "metric", "count", "mean (ms)", "max (ms)"));
        report.append("|----------------------------------------------|-------|--------------|--------------|\n");
        appendRow(report, "shield.chat.send_message",  "outcome", "success");
        appendRow(report, "shield.rag.pipeline.total", "outcome", "success");
        appendRow(report, "shield.rag.pipeline.total", "outcome", "empty");
        appendRow(report, "shield.rag.pipeline.total", "outcome", "failure");
        appendRow(report, "shield.rag.classify",       "outcome", "success");
        appendRow(report, "shield.rag.classify",       "outcome", "failure");
        appendRow(report, "shield.rag.retrieve",       "outcome", "success");
        appendRow(report, "shield.rag.retrieve",       "outcome", "empty");
        appendRow(report, "shield.rag.cohere.embed",   "outcome", "success");
        appendRow(report, "shield.chat.cohere.call",   "outcome", "success");
        report.append(String.format("%n클라이언트 wall-clock: min=%dms, p50=%dms, p95=%dms, max=%dms%n",
                cliMin, cliP50, cliP95, cliMax));
        String reportText = report.toString();

        // 보고서 출력 — 콘솔 (System.out 은 gradle testLogging.showStandardStreams 와 무관하게 build/reports/.../system-out 에 들어감)
        log.info(reportText);
        System.out.println(reportText);

        // 보고서 파일 저장 — 사용자가 docs/latency-baseline-*.md 에 그대로 복사 가능하도록
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"));
        Path outFile = Paths.get(System.getProperty("user.dir"), "build", "reports", "baseline-result-" + stamp + ".md");
        try {
            Files.createDirectories(outFile.getParent());
            Files.writeString(outFile, reportText, StandardCharsets.UTF_8);
            log.info("[baseline] 결과 파일 저장: {}", outFile.toAbsolutePath());
            System.out.println("[baseline] 결과 파일 저장: " + outFile.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[baseline] 결과 파일 저장 실패: {}", e.getMessage());
        }

        // ASSERT 2 — 신규 메트릭이 표본을 누적
        Timer classify = registry.find("shield.rag.classify").tag("outcome", "success").timer();
        Timer pipeline = anyOutcome("shield.rag.pipeline.total");
        assertThat(classify).as("shield.rag.classify timer 가 등록되지 않음").isNotNull();
        assertThat(classify.count()).as("classify timer 표본 0").isPositive();
        assertThat(pipeline).as("shield.rag.pipeline.total timer 가 등록되지 않음").isNotNull();
        assertThat(pipeline.count()).as("pipeline.total timer 표본 0").isPositive();

        // ASSERT 3 — /actuator/prometheus 에 시리즈 라인 노출 (Step 2 검증)
        String body = scrapePrometheus();
        assertThat(body).isNotNull();
        assertThat(body).as("신규 classify 시리즈 라인 누락").contains("shield_rag_classify_seconds_count");
        assertThat(body).as("신규 pipeline.total 시리즈 라인 누락").contains("shield_rag_pipeline_total_seconds_count");
        assertThat(body).as("기존 send_message 시리즈 라인 누락").contains("shield_chat_send_message_seconds_count");
        assertThat(body).as("기존 cohere.call 시리즈 라인 누락").contains("shield_chat_cohere_call_seconds_count");
    }

    /** /actuator/prometheus 를 JDK HttpClient 로 직접 호출. TestRestTemplate 자동 등록 의존 회피. */
    private String scrapePrometheus() {
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/actuator/prometheus"))
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            assertThat(resp.statusCode()).as("/actuator/prometheus 비-2xx").isBetween(200, 299);
            return resp.body();
        } catch (Exception e) {
            throw new RuntimeException("Failed to scrape /actuator/prometheus: " + e.getMessage(), e);
        }
    }

    private void appendRow(StringBuilder sb, String name, String tagKey, String tagValue) {
        Timer t = registry.find(name).tag(tagKey, tagValue).timer();
        String label = name + "{" + tagKey + "=" + tagValue + "}";
        if (t == null || t.count() == 0) {
            sb.append(String.format("| %-44s | %5s | %12s | %12s |%n", label, "-", "-", "-"));
            return;
        }
        double meanMs = t.totalTime(TimeUnit.MILLISECONDS) / (double) t.count();
        double maxMs  = t.max(TimeUnit.MILLISECONDS);
        sb.append(String.format("| %-44s | %5d | %12.1f | %12.1f |%n", label, t.count(), meanMs, maxMs));
    }

    /** outcome 태그 무관하게 같은 이름의 첫 Timer 를 반환 (등록 자체 확인용). */
    private Timer anyOutcome(String name) {
        return registry.find(name).timer();
    }

    /**
     * 사용할 consultation UUID 목록을 결정.
     * <ul>
     *   <li>{@code BASELINE_CONSULTATION_IDS} 환경변수가 지정되어 있으면 그것을 파싱하여 사용</li>
     *   <li>비어있으면 fixture 사용자(이메일 {@code baseline-load@shield.local})와 L1 분야가 설정된
     *       상담을 자동 생성. {@code sampleCount} 를 10턴 상한에 나눠 담을 만큼 생성.</li>
     * </ul>
     */
    private List<UUID> resolveConsultations(int desiredSampleCount) {
        if (consultationIdsCsv != null && !consultationIdsCsv.isBlank()) {
            List<UUID> ids = Arrays.stream(consultationIdsCsv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(UUID::fromString).toList();
            assertThat(ids).as("최소 한 개의 consultation UUID 가 필요").isNotEmpty();
            log.info("[baseline] BASELINE_CONSULTATION_IDS 사용 (n={})", ids.size());
            return ids;
        }

        User fixtureUser = userRepository.findByEmail(FIXTURE_USER_EMAIL).orElseGet(() -> {
            User created = userRepository.save(User.builder()
                    .email(FIXTURE_USER_EMAIL)
                    .name("Baseline Load Test")
                    .role(UserRole.USER)
                    .provider("TEST")
                    .build());
            log.info("[baseline] fixture 사용자 신규 생성: id={}, email={}",
                    created.getId(), created.getEmail());
            return created;
        });

        int turnLimit = 10;
        int needed = Math.max(1, (desiredSampleCount + turnLimit - 1) / turnLimit);
        List<UUID> ids = new ArrayList<>(needed);
        for (int i = 0; i < needed; i++) {
            Consultation c = consultationRepository.save(
                    Consultation.create(fixtureUser.getId(), FIXTURE_L1_DOMAIN, List.of(), List.of()));
            ids.add(c.getId());
            log.info("[baseline] fixture 상담 신규 생성: id={} (L1={})", c.getId(), FIXTURE_L1_DOMAIN);
        }
        return ids;
    }
}
