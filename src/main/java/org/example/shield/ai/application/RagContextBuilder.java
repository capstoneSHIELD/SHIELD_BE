package org.example.shield.ai.application;

import org.example.shield.ai.config.RagFeatureMode;
import org.example.shield.ai.dto.LegalChunk;
import org.example.shield.ai.dto.MixedRetrievalResult;
import org.example.shield.ai.dto.Precedent;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Layer 3: RAG 컨텍스트 빌더.
 * 검색된 법률 조문 청크 (및 C-5 이후 판례) 를 시스템 프롬프트에 삽입할 문자열로 포맷한다.
 *
 * <p>P5.3 Commit 5: token budget shadow 측정 추가. budget mode가 shadow일 때
 * 예산 초과 시 trim/drop 추정값을 메트릭으로만 기록하고 실제 prompt context는 baseline 유지.
 * enforce 모드는 본 plan 범위 밖 — 호출 시 {@link UnsupportedOperationException}.
 */
@Component
public class RagContextBuilder {

    /** 토큰 1개의 평균 문자 수 (한국어 보수적 추정). */
    private static final double CHARS_PER_TOKEN = 1.5;

    @Value("${app.ai.rag.context-budget.mode:off}")
    private String budgetModeRaw;

    @Value("${app.ai.rag.context-budget.tokens:2000}")
    private int tokenBudget;

    private final AiRagOperationalMetrics operationalMetrics;

    /** Test-friendly 생성자 — 메트릭 없이 동작. */
    public RagContextBuilder() {
        this(null);
    }

    @Autowired
    public RagContextBuilder(AiRagOperationalMetrics operationalMetrics) {
        this.operationalMetrics = operationalMetrics;
    }

    RagFeatureMode currentBudgetMode() {
        return RagFeatureMode.fromOrThrow(budgetModeRaw, "AI_RAG_CONTEXT_BUDGET_MODE");
    }

    /**
     * LegalChunk 목록을 프롬프트 컨텍스트 문자열로 변환 (법령 전용, 기존 호환).
     */
    public String build(List<LegalChunk> chunks, String intentSummary) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendLawsSection(sb, chunks, intentSummary, /*includeCasesHeader*/ false);
        String result = sb.toString();
        recordBudgetShadow(result, chunks.size(), 0);
        return result;
    }

    /**
     * 법령 + 판례 병합 결과를 프롬프트 컨텍스트 문자열로 변환 (C-5, Issue #42).
     */
    public String build(MixedRetrievalResult mixed, String intentSummary) {
        if (mixed == null || mixed.isEmpty()) {
            return "";
        }

        List<LegalChunk> laws = mixed.laws();
        List<Precedent> cases = mixed.cases();

        StringBuilder sb = new StringBuilder();
        boolean hasCases = cases != null && !cases.isEmpty();

        if (laws != null && !laws.isEmpty()) {
            appendLawsSection(sb, laws, intentSummary, hasCases);
        }

        if (hasCases) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            appendCasesSection(sb, cases);
        }

        String result = sb.toString();
        recordBudgetShadow(result,
                laws == null ? 0 : laws.size(),
                cases == null ? 0 : cases.size());
        return result;
    }

    /**
     * P5.3 Commit 5 — token budget shadow 측정 (overload, 명세 §5).
     *
     * <ul>
     *   <li>{@code budgetMode=off} → 기본 {@link #build(MixedRetrievalResult, String)}와 동일</li>
     *   <li>{@code budgetMode=shadow} → 추정 토큰 / would-drop 카운트를 메트릭에 기록, 결과는 baseline 동일</li>
     *   <li>{@code budgetMode=enforce} → {@link UnsupportedOperationException} (본 plan 범위 밖)</li>
     * </ul>
     *
     * @param mixed         법령·판례 병합 검색 결과
     * @param intentSummary 의도 분류 요약
     * @param budgetTokens  명시적 token 예산 (기본 yaml 값을 override). 0 이하면 기본 사용.
     */
    public String build(MixedRetrievalResult mixed, String intentSummary, int budgetTokens) {
        RagFeatureMode mode = currentBudgetMode();
        if (mode == RagFeatureMode.ENFORCE) {
            // citation metadata 보존, statute/case minimum slot 등 enforce 로직은
            // 별도 plan에서 구현. 본 plan에서는 명시적 미구현.
            throw new UnsupportedOperationException(
                    "Context budget ENFORCE mode is out of scope for P5.3. " +
                    "Use mode=off or mode=shadow.");
        }

        // shadow + off 모두 결과는 동일 (baseline build).
        String result = build(mixed, intentSummary);

        if (mode == RagFeatureMode.SHADOW) {
            int effectiveBudget = budgetTokens > 0 ? budgetTokens : tokenBudget;
            recordBudgetShadowDetailed(mixed, result, effectiveBudget);
        }
        return result;
    }

    /**
     * 기본 build 경로에서의 shadow 측정 (token 추정만 기록).
     */
    private void recordBudgetShadow(String result, int statuteCount, int caseCount) {
        if (operationalMetrics == null) {
            return;
        }
        RagFeatureMode mode;
        try {
            mode = currentBudgetMode();
        } catch (Exception ignored) {
            return;
        }
        if (mode != RagFeatureMode.SHADOW) {
            return;
        }
        try {
            long estimated = estimateTokens(result);
            operationalMetrics.recordContextBudget("total", "estimated", estimated);
            operationalMetrics.recordContextBudget("statute", "kept", statuteCount);
            operationalMetrics.recordContextBudget("case", "kept", caseCount);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    /**
     * budget-aware overload에서의 shadow 측정 (would-drop / would-trim 추정).
     */
    private void recordBudgetShadowDetailed(MixedRetrievalResult mixed, String result, int budget) {
        if (operationalMetrics == null) {
            return;
        }
        try {
            long estimated = estimateTokens(result);
            operationalMetrics.recordContextBudget("total", "estimated", estimated);

            if (estimated <= budget) {
                operationalMetrics.recordContextBudget("total", "kept", 1);
                return;
            }

            // 예산 초과 — would-drop / would-trim 추정
            int wouldDropStatute = 0;
            int wouldDropCase = 0;
            List<LegalChunk> laws = mixed.laws();
            List<Precedent> cases = mixed.cases();

            // 간단 휴리스틱: 마지막 chunk부터 drop 시뮬레이션, 예산 들어맞을 때까지
            long remaining = estimated - budget;
            if (cases != null) {
                for (int i = cases.size() - 1; i >= 0 && remaining > 0; i--) {
                    long chunkTokens = estimateTokens(cases.get(i).toString());
                    remaining -= chunkTokens;
                    wouldDropCase++;
                }
            }
            if (remaining > 0 && laws != null) {
                for (int i = laws.size() - 1; i >= 0 && remaining > 0; i--) {
                    long chunkTokens = estimateTokens(laws.get(i).content());
                    remaining -= chunkTokens;
                    wouldDropStatute++;
                }
            }

            operationalMetrics.recordContextBudget("statute", "dropped", wouldDropStatute);
            operationalMetrics.recordContextBudget("case", "dropped", wouldDropCase);
            operationalMetrics.recordContextBudget("total", "trimmed", estimated - budget);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    /**
     * 한국어 평균 문자/토큰 비율로 추정. Cohere tokenizer 호출은 비용·지연이 크므로
     * 추정값 사용. 일반적으로 한국어는 1자 ≈ 0.7~1.5 token, 보수적으로 1.5자/token.
     */
    static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (long) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    private void appendLawsSection(StringBuilder sb,
                                   List<LegalChunk> chunks,
                                   String intentSummary,
                                   boolean casesFollow) {
        sb.append("## 참고 법령 (출처는 반드시 응답에 인용할 것)\n\n");
        sb.append("분류: ").append(intentSummary).append("\n\n");
        sb.append("다음은 본 사건과 관련된 현행 법령 조문입니다. 응답 시 이 정보를 우선 참고하고,\n");
        sb.append("인용 시 반드시 법령명과 조항 번호를 함께 표시하세요.\n");
        if (casesFollow) {
            sb.append("아래 '참고 판례' 섹션은 법리 해석 근거로만 활용하고, 조문 내용과 명확히 구분해 인용하세요.\n");
        }
        sb.append("법령에 없는 내용은 추측하지 말고 \"관련 법령에서 확인할 수 없습니다\"라고 답하세요.\n");

        for (int i = 0; i < chunks.size(); i++) {
            LegalChunk chunk = chunks.get(i);
            sb.append("\n---\n");
            sb.append("[").append(i + 1).append("] ");
            sb.append(chunk.lawName()).append(" ").append(chunk.articleNo());
            sb.append(" (").append(chunk.articleTitle()).append(")\n");
            sb.append("시행일: ").append(chunk.effectiveDate());
            if (chunk.sourceUrl() != null && !chunk.sourceUrl().isEmpty()) {
                sb.append(" / 출처: ").append(chunk.sourceUrl());
            }
            sb.append("\n\n");
            sb.append(chunk.content()).append("\n");
        }
    }

    private void appendCasesSection(StringBuilder sb, List<Precedent> cases) {
        sb.append("## 참고 판례 (법리 해석 근거, 인용 시 사건번호·선고일 함께 표시)\n\n");
        sb.append("다음은 본 사건과 유사한 쟁점의 판례입니다. 법령 조문과 구분해 인용하고,\n");
        sb.append("판례의 사실관계가 본 사건과 다를 경우 그 차이를 명시하세요.\n");

        for (int i = 0; i < cases.size(); i++) {
            Precedent p = cases.get(i);
            sb.append("\n---\n");
            sb.append("[").append(i + 1).append("] ");
            sb.append("[").append(nz(p.court())).append(" ").append(nz(p.caseNo()));
            if (p.decisionDate() != null && !p.decisionDate().isEmpty()) {
                sb.append(" · ").append(p.decisionDate());
            }
            sb.append("]");
            if (p.caseName() != null && !p.caseName().isEmpty()) {
                sb.append(" ").append(p.caseName());
            }
            sb.append("\n");
            if (p.caseType() != null && !p.caseType().isEmpty()) {
                sb.append("유형: ").append(p.caseType());
            }
            if (p.sourceUrl() != null && !p.sourceUrl().isEmpty()) {
                if (p.caseType() != null && !p.caseType().isEmpty()) {
                    sb.append(" / ");
                }
                sb.append("출처: ").append(p.sourceUrl());
            }
            sb.append("\n\n");
            if (p.headnote() != null && !p.headnote().isEmpty()) {
                sb.append("판시사항: ").append(p.headnote()).append("\n");
            }
            if (p.holding() != null && !p.holding().isEmpty()) {
                sb.append("판결요지: ").append(p.holding()).append("\n");
            }
        }
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
