package org.example.shield.ai.application;

import org.example.shield.ai.config.RagFeatureMode;
import org.example.shield.ai.dto.DialogueIntent;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.dto.RetrievalStrategyDecision;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 의도 분류 결과를 기반으로 retrieval 전략(skipRag, topK)을 결정하는 정책.
 *
 * <p>P5.3 Commit 3: 기존 boolean {@code enabled}를 {@link RagFeatureMode} 분기로 확장.
 * <ul>
 *   <li>{@link RagFeatureMode#OFF} — 항상 baseline (현재 기본값)</li>
 *   <li>{@link RagFeatureMode#SHADOW} — 결정은 계산해 메트릭으로만 기록, 라우팅은 baseline 유지</li>
 *   <li>{@link RagFeatureMode#ENFORCE} — 결정을 실제 라우팅에 적용</li>
 *   <li>{@link RagFeatureMode#SAMPLED} — 본 정책에 의미 없음, OFF와 동일 처리</li>
 * </ul>
 *
 * <p><b>중요</b>: 본 commit에서는 기존 ASK_LEGAL_ADVICE skip 로직을 유지한다.
 * P5.3 Commit 4에서 "ASK_LEGAL_ADVICE skip 절대 금지" 정책을 적용하면서 동시에
 * GREETING-only enforce로 제한할 예정.
 */
@Component
public class IntentAwareRetrievalPolicy {

    /** Legacy boolean flag (BC). true면 ENFORCE로 마이그레이션. */
    @Value("${app.ai.rag.intent-aware.enabled:false}")
    private boolean legacyEnabled;

    /** P5.3 Commit 3 — mode 분기. legacy true면 무시되고 ENFORCE 강제. */
    @Value("${app.ai.rag.intent-aware.mode:off}")
    private String modeRaw;

    @Value("${app.ai.intent-router.thresholds.default.auto-collect:0.85}")
    private double highConfidenceThreshold;

    @Value("${app.ai.intent-router.thresholds.default.pending-lower-bound:0.65}")
    private double mediumConfidenceThreshold;

    /**
     * P5.3 Commit 4 — GREETING skip 별도 활성화 + 별도 confidence threshold.
     * 기본값 false로 시작 — Shadow 데이터로 정밀도 확인 후 켤 것.
     */
    @Value("${app.ai.intent-router.enable-greeting-skip:false}")
    private boolean enableGreetingSkip;

    /**
     * GREETING skip 적용 시 요구되는 최소 confidence.
     * 일반 high-confidence threshold(0.85)보다 더 엄격하게 (false-skip 위험 회피).
     */
    @Value("${app.ai.intent-router.greeting-min-confidence:0.90}")
    private double greetingMinConfidence;

    private final AiRagOperationalMetrics operationalMetrics;

    /** Test-friendly 생성자 — 메트릭 미주입. */
    public IntentAwareRetrievalPolicy() {
        this(null);
    }

    @Autowired
    public IntentAwareRetrievalPolicy(AiRagOperationalMetrics operationalMetrics) {
        this.operationalMetrics = operationalMetrics;
    }

    RagFeatureMode currentMode() {
        if (legacyEnabled) {
            return RagFeatureMode.ENFORCE;
        }
        return RagFeatureMode.fromOrThrow(modeRaw, "AI_RAG_INTENT_AWARE_MODE");
    }

    public RetrievalStrategyDecision decide(IntentRouterResponse intent, int defaultTopK) {
        int safeDefaultTopK = Math.max(1, defaultTopK);
        RagFeatureMode mode = currentMode();
        String modeTag = mode.name().toLowerCase(Locale.ROOT);

        if (mode == RagFeatureMode.OFF || mode == RagFeatureMode.SAMPLED) {
            recordDecision(modeTag, intent, "baseline_disabled", confidenceBucket(intent));
            return RetrievalStrategyDecision.baseline(safeDefaultTopK, "disabled");
        }
        if (intent == null) {
            recordDecision(modeTag, null, "baseline_missing_intent", "unknown");
            return RetrievalStrategyDecision.baseline(safeDefaultTopK, "missing_intent");
        }

        // 결정 계산 (shadow와 enforce 둘 다 동일하게 진행)
        RetrievalStrategyDecision computed = computeDecision(intent, safeDefaultTopK);
        recordDecision(modeTag, intent, computed.reason(), confidenceBucket(intent));

        // SHADOW: 결정은 메트릭만 기록, 실제 라우팅은 baseline
        if (mode == RagFeatureMode.SHADOW) {
            return RetrievalStrategyDecision.baseline(safeDefaultTopK, "shadow_" + computed.reason());
        }

        // ENFORCE
        return computed;
    }

    private RetrievalStrategyDecision computeDecision(IntentRouterResponse intent, int safeDefaultTopK) {
        double confidence = intent.intentConfidence();
        DialogueIntent dialogueIntent = intent.dialogueIntent();

        if (dialogueIntent == null) {
            return RetrievalStrategyDecision.baseline(safeDefaultTopK, "null_intent_baseline");
        }

        // P5.3 Commit 4 — CRITICAL: ASK_LEGAL_ADVICE는 절대 RAG skip 금지.
        // 법률 조언 요청은 항상 법령/판례 근거 필요. 의도 분류가 잘못된 경우에도
        // skip되지 않도록 가장 먼저 가드.
        if (dialogueIntent == DialogueIntent.ASK_LEGAL_ADVICE) {
            return new RetrievalStrategyDecision(false, true, safeDefaultTopK,
                    "ask_legal_advice_force_rag");
        }

        if (confidence < mediumConfidenceThreshold) {
            return RetrievalStrategyDecision.baseline(safeDefaultTopK, "low_confidence_baseline");
        }
        if (confidence < highConfidenceThreshold) {
            return RetrievalStrategyDecision.baseline(safeDefaultTopK, "medium_confidence_conservative");
        }

        // P5.3 Commit 4 — GREETING-only skip:
        //   1) enableGreetingSkip flag로 명시적 활성
        //   2) GREETING 자체 더 엄격한 confidence threshold (기본 0.90)
        //   3) IRRELEVANT는 별도 분리 (현재는 baseline, 향후 별도 enable flag)
        return switch (dialogueIntent) {
            case GREETING -> {
                if (enableGreetingSkip && confidence >= greetingMinConfidence) {
                    yield new RetrievalStrategyDecision(true, true, safeDefaultTopK,
                            "greeting_skip");
                }
                yield RetrievalStrategyDecision.baseline(safeDefaultTopK, "greeting_baseline");
            }
            case IRRELEVANT ->
                    // 현재 baseline 유지. 별도 enable flag로 활성화될 예정.
                    RetrievalStrategyDecision.baseline(safeDefaultTopK, "irrelevant_baseline");
            case CHANGE_TOPIC ->
                    new RetrievalStrategyDecision(false, true, Math.max(safeDefaultTopK, 10),
                            "high_confidence_broad_search");
            // ASK_LEGAL_ADVICE는 위에서 이미 처리됨 — 도달 불가
            case ASK_LEGAL_ADVICE,
                 PROVIDE_INFO, CORRECT_INFO, CONFIRM, END_CONSULTATION ->
                    new RetrievalStrategyDecision(false, true, safeDefaultTopK,
                            "high_confidence_default_search");
        };
    }

    private void recordDecision(String modeTag, IntentRouterResponse intent, String decision, String confidenceBucket) {
        if (operationalMetrics == null) {
            return;
        }
        String intentTag = intent == null || intent.dialogueIntent() == null
                ? "UNKNOWN" : intent.dialogueIntent().name();
        try {
            operationalMetrics.recordIntentRouting(modeTag, intentTag, decision, confidenceBucket);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private String confidenceBucket(IntentRouterResponse intent) {
        if (intent == null) {
            return "unknown";
        }
        double c = intent.intentConfidence();
        if (c >= highConfidenceThreshold) return "high";
        if (c >= mediumConfidenceThreshold) return "medium";
        return "low";
    }
}
