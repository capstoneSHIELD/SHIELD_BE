package org.example.shield.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.ai.dto.BriefParsedResponse;
import org.example.shield.ai.dto.ChatParsedResponse;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.CohereChatRequest;
import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.infrastructure.AiRagOperationalMetrics;
import org.example.shield.ai.infrastructure.CohereClient;
import org.example.shield.ai.infrastructure.CohereCostCalculator;
import org.example.shield.ai.infrastructure.GuardrailFilter;
import org.example.shield.ai.infrastructure.SanitizeService;
import org.example.shield.common.enums.MessageRole;
import org.example.shield.consultation.application.ClassificationCandidate;
import org.example.shield.consultation.application.ClassificationResolver;
import org.example.shield.consultation.domain.Consultation;
import org.example.shield.consultation.domain.Message;
import org.example.shield.consultation.domain.MessageReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AI 서비스 — AiClient를 활용한 AI 기능.
 *
 * ChatService / AnalysisService에서 호출.
 * - chat(): 대화 API (Phase 1) — 항상 full history 전송
 * - generateBrief(): 의뢰서 생성 API (Phase 2)
 * - callClassify(): RAG Layer 1 의도 분류
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CohereService {

    private static final int CHECKLIST_COVERAGE_TOKEN_BUDGET = 160;
    private static final int RECENT_QA_MEMORY_TOKEN_BUDGET = 180;
    private static final int CURRENT_TARGET_SLOT_TOKEN_BUDGET = 40;
    private static final int RAG_CONTEXT_TOKEN_BUDGET = 800;
    private static final int APPROX_CHARS_PER_TOKEN = 4;
    private static final int QA_MEMORY_MAX_ITEMS = 6;
    private static final int QA_ANSWER_MAX_CHARS = 160;

    private final AiClient aiClient;
    private final CohereApiConfig config;
    private final PromptService promptService;
    private final SanitizeService sanitizeService;
    private final GuardrailFilter guardrailFilter;
    private final MessageReader messageReader;
    private final CohereClient cohereClient;
    private final ChecklistCoverageService checklistCoverageService;
    private final ChecklistPromptBuilder checklistPromptBuilder;
    private final ClassificationResolver classificationResolver;
    private final SlotStatusBlockBuilder slotStatusBlockBuilder;
    private final StaticQuestionSelector staticQuestionSelector;
    private final OutputComplianceShadowJudge outputComplianceShadowJudge;
    private final AiRagOperationalMetrics operationalMetrics;
    private final CohereCostCalculator costCalculator;

    @Value("${app.ai.slot-ledger.enabled:true}")
    private boolean slotLedgerEnabled;

    /**
     * Phase 1 대화 — 사용자 메시지 처리 후 AI 응답 반환.
     * Cohere v2 Chat API는 무상태 모드이므로 항상 full history 전송.
     *
     * @param consultation      상담 엔티티
     * @param sanitizedUserText 사용자 입력 (sanitize 완료)
     * @param ragContext        RAG 컨텍스트 (빈 문자열이면 미삽입)
     * @param chatHistory       이미 조회된 대화 내역 (중복 DB 쿼리 방지)
     * @return AiCallResult<ChatParsedResponse>
     */
    public AiCallResult<ChatParsedResponse> chat(Consultation consultation, String sanitizedUserText,
                                                  String ragContext, List<Message> chatHistory) {
        List<CohereChatRequest.Message> messages = buildChatMessages(consultation, sanitizedUserText, ragContext, chatHistory);
        AiCallResult<ChatParsedResponse> result = aiClient.callChat(
                config.getChatModel(), messages);
        emitCohereMetrics(config.getChatModel(), "chat", result);

        // Layer 2 가드레일: 금칙어 필터
        ChatParsedResponse filtered = guardrailFilter.filterChatResponse(result.data());
        if (outputComplianceShadowJudge != null
                && filtered != null
                && filtered.getNextQuestion() != null) {
            outputComplianceShadowJudge.evaluate(filtered.getNextQuestion());
        }
        return new AiCallResult<>(
                result.responseId(),
                filtered,
                result.tokensInput(),
                result.tokensOutput(),
                result.latencyMs()
        );
    }

    /**
     * Phase 2 의뢰서 생성 — 전체 대화를 기반으로 구조화된 의뢰서 생성.
     *
     * @param consultation 상담 엔티티
     * @return AiCallResult<BriefParsedResponse>
     */
    public AiCallResult<BriefParsedResponse> generateBrief(Consultation consultation) {
        List<CohereChatRequest.Message> messages = buildBriefMessages(consultation);
        AiCallResult<BriefParsedResponse> result = aiClient.callBrief(
                config.getBriefModel(), messages);
        emitCohereMetrics(config.getBriefModel(), "brief", result);

        // Layer 2 가드레일: 의뢰서 금칙어 필터
        BriefParsedResponse filtered = guardrailFilter.filterBriefResponse(result.data());
        return new AiCallResult<>(
                result.responseId(),
                filtered,
                result.tokensInput(),
                result.tokensOutput(),
                result.latencyMs()
        );
    }

    /**
     * RAG Layer 1 — 의도 분류 전용 LLM 호출.
     * CohereClient.callRawJson()에 위임하여 raw JSON 문자열로 반환.
     *
     * @param messages 분류용 messages[] 배열 (system + user)
     * @return AiCallResult<String> — raw JSON 문자열
     */
    public AiCallResult<String> callClassify(List<CohereChatRequest.Message> messages) {
        CohereChatRequest request = CohereChatRequest.forClassify(
                config.getClassifyModel(),
                messages,
                config.getClassifyTemperature(),
                config.getClassifyMaxTokens(),
                config.isStructuredOutputEnabled());
        AiCallResult<String> result = cohereClient.callRawJson(
                request, Duration.ofMillis(config.getClassifyReadTimeout()));
        emitCohereMetrics(config.getClassifyModel(), "classify", result);
        return result;
    }

    /**
     * Cohere 호출 결과를 받아 token/cost/latency 메트릭을 best-effort로 emit.
     *
     * <p>P5.1 Commit 4 도입. metric 발행 중 예외가 발생해도 호출 결과에는 영향 없음
     * — 운영 관측은 사용자 응답을 절대 막지 않는다.
     */
    private void emitCohereMetrics(String model, String operation, AiCallResult<?> result) {
        if (operationalMetrics == null || result == null) {
            return;
        }
        try {
            operationalMetrics.recordCohereTokens(model, operation, "input",
                    result.tokensInput(), /*estimated=*/false);
            operationalMetrics.recordCohereTokens(model, operation, "output",
                    result.tokensOutput(), /*estimated=*/false);
            if (costCalculator != null) {
                double cost = costCalculator.estimate(model,
                        result.tokensInput(), result.tokensOutput());
                operationalMetrics.recordCohereEstimatedCost(model, operation, cost);
            }
            Integer latencyMsBoxed = result.latencyMs();
            if (latencyMsBoxed != null && latencyMsBoxed >= 0) {
                operationalMetrics.recordCohereLatency(model, operation,
                        Duration.ofMillis(latencyMsBoxed), "success");
            }
        } catch (Exception e) {
            log.warn("Cohere metric emit 실패 (operation={}, model={}): {}",
                    operation, model, Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /**
     * Phase 1 대화용 messages[] 배열 구성.
     * system + assistant/user 턴을 역할 배열로 구조화.
     * history truncation: 시스템 프롬프트 + 최대 N턴 (configurable).
     *
     * @param chatHistory 호출자가 이미 조회한 대화 내역 (중복 DB 쿼리 방지)
     */
    private List<CohereChatRequest.Message> buildChatMessages(
            Consultation consultation, String latestSanitizedUserText,
            String ragContext, List<Message> chatHistory) {

        List<CohereChatRequest.Message> msgs = new ArrayList<>();

        // 1. 시스템 프롬프트. P1 state hints must stay at the very top.
        List<String> topBlocks = new ArrayList<>();
        String systemPrompt = promptService.loadRouterChatPrompt();

        SlotLedger slotLedger = consultation.getSlotState();
        if (slotLedgerEnabled && slotStatusBlockBuilder != null && slotLedger != null) {
            String slotStatusBlock = slotStatusBlockBuilder.build(slotLedger);
            if (!slotStatusBlock.isEmpty()) {
                topBlocks.add(slotStatusBlock);
            }
        }

        // 분류 완료 시 체크리스트 YAML 동적 주입
        ClassificationCandidate collectionCandidate = classificationResolver.candidateForCollection(consultation);
        String domain = collectionCandidate.firstDomain();
        if (domain != null) {
            String collectedSummary = checklistCoverageService.buildCollectedSummary(
                    domain,
                    collectionCandidate.firstSubDomain(),
                    collectionCandidate.firstTag(),
                    chatHistory);
            if (!collectedSummary.isEmpty()) {
                topBlocks.add(budgetBlock(
                        "=== CURRENT CHECKLIST COVERAGE ===",
                        collectedSummary,
                        CHECKLIST_COVERAGE_TOKEN_BUDGET));
            }
        }

        String recentQaMemoryBlock = buildRecentQaMemoryBlock(chatHistory);
        if (!recentQaMemoryBlock.isEmpty()) {
            topBlocks.add(budgetBlock(
                    "=== RECENT Q/A MEMORY ===",
                    recentQaMemoryBlock,
                    RECENT_QA_MEMORY_TOKEN_BUDGET));
        }

        String currentTargetSlotBlock = buildCurrentTargetSlotBlock(slotLedger);
        if (!currentTargetSlotBlock.isEmpty()) {
            topBlocks.add(budgetBlock(
                    "=== CURRENT TARGET SLOT ===",
                    currentTargetSlotBlock,
                    CURRENT_TARGET_SLOT_TOKEN_BUDGET));
        }

        if (!topBlocks.isEmpty()) {
            topBlocks.add("""
                    RULE: 이미 답변을 받은 질문은 같은 문장이나 같은 의미로 다시 묻지 마세요.
                    RULE: COLLECTED INFORMATION 항목은 재질문하지 마세요.
                    RULE: PENDING CONFIRMATION은 새 질문처럼 다시 열지 말고 확인형으로만 처리하세요.
                    RULE: 답변이 불충분하면 기존 답변을 짧게 인정하고 누락된 하위 사실만 좁혀서 물어보세요.""");
            systemPrompt = String.join("\n\n", topBlocks) + "\n\n" + systemPrompt;
        }

        if (domain != null) {
            String checklist = checklistPromptBuilder == null
                    ? promptService.loadChecklist(domain)
                    : checklistPromptBuilder.build(
                            domain,
                            collectionCandidate.firstSubDomain(),
                            collectionCandidate.firstTag());
            if (checklist != null && !checklist.isBlank()) {
                systemPrompt = systemPrompt + "\n\n" + checklist;
            }
        }

        // 분류 컨텍스트: 사용자 사전 선택 + 허용 자식 목록 (Issue #48)
        String classificationContext = buildClassificationContext(consultation);
        if (!classificationContext.isEmpty()) {
            systemPrompt = systemPrompt + "\n\n" + classificationContext;
        }

        // RAG Layer 3: 법률 조문 컨텍스트 주입
        if (ragContext != null && !ragContext.isEmpty()) {
            systemPrompt = systemPrompt + "\n\n" + truncateForTokenBudget(
                    ragContext,
                    RAG_CONTEXT_TOKEN_BUDGET);
        }

        msgs.add(CohereChatRequest.Message.system(systemPrompt));

        // 2. 기존 대화 내역 (시간순) — 호출자가 전달한 리스트 사용
        appendHistory(msgs, chatHistory, "chat");

        // 3. 새 사용자 메시지 (이미 sanitize됨)
        if (!historyAlreadyContainsLatestUserMessage(chatHistory, latestSanitizedUserText)) {
            msgs.add(CohereChatRequest.Message.user(latestSanitizedUserText));
        }

        // 4. History truncation: system prompt + last N messages
        return truncateMessages(msgs, config.getMaxHistoryMessages());
    }

    /**
     * Phase 2 의뢰서용 messages[] 배열 구성.
     *
     * <p>체크리스트 L1 이 확정된 경우, 대화에서 수집되지 못한 슬롯 목록을
     * system 프롬프트에 힌트로 주입한다. 10턴 상한으로 조기 종료된 상담에서
     * LLM 이 대화 근거 기반 추론으로 누락 슬롯을 채울 수 있도록 돕는 용도.
     * 근거 없는 슬롯은 brief.md 규칙에 따라 의뢰서에서 제외한다.</p>
     */
    private List<CohereChatRequest.Message> buildBriefMessages(Consultation consultation) {
        List<CohereChatRequest.Message> msgs = new ArrayList<>();

        // 전체 대화 내역 — 미수집 슬롯 판정과 appendHistory 양쪽에서 공유 (중복 DB 쿼리 방지)
        List<Message> history = messageReader.findAllByConsultationId(consultation.getId());

        // 1. 의뢰서 전용 시스템 프롬프트 + (L1 확정 시) 미수집 슬롯 추론 가이드
        String systemPrompt = promptService.loadRouterBriefPrompt();
        ClassificationCandidate candidate = classificationResolver.resolve(consultation).effectiveCandidate();
        if (candidate == null || !candidate.hasAny()) {
            candidate = classificationResolver.candidateForCollection(consultation);
        }
        String l1 = candidate.firstDomain();
        if (l1 != null) {
            String missing = checklistCoverageService.buildMissingSlotsGuidance(
                    l1,
                    candidate.firstSubDomain(),
                    candidate.firstTag(),
                    history);
            if (!missing.isEmpty()) {
                systemPrompt = systemPrompt + "\n\n" + missing;
            }
        }
        String factDigest = buildConversationFactDigest(history);
        if (!factDigest.isEmpty()) {
            systemPrompt = systemPrompt + "\n\n" + factDigest;
        }
        msgs.add(CohereChatRequest.Message.system(systemPrompt));

        // 2. 전체 대화 내역
        appendHistory(msgs, history, "brief");

        return truncateMessages(msgs, config.getMaxHistoryMessages());
    }

    /**
     * DB 대화 내역을 Cohere messages 배열로 변환 — buildChatMessages /
     * buildBriefMessages 공통 로직.
     *
     * <p>방어적 skip 정책 (Issue #45):</p>
     * <ul>
     *   <li>USER/CHATBOT 무관하게 원본 content 가 null/blank 이면 skip → sanitize 호출 자체를 회피해 불필요한 PII 스캔 비용 제거</li>
     *   <li>USER 의 경우 sanitize 결과가 blank 로 수축하는 경우도 skip (예: 전체가 마스킹 대상)</li>
     *   <li>Cohere v2 Chat API 가 빈 content 를 400 으로 거부하므로 history 구성 시점에서 배제해야 한다</li>
     * </ul>
     *
     * <p>{@code context} 는 로그 구분용 (\"chat\" / \"brief\" / \"search\" 등) —
     * 한 곳에서 메트릭/알럿을 달기 쉽게 태깅한다.</p>
     */
    private void appendHistory(
            List<CohereChatRequest.Message> msgs, List<Message> history, String context) {
        for (Message msg : history) {
            String raw = msg.getContent();
            if (raw == null || raw.isBlank()) {
                log.warn("Skipping blank {} message in {} history: messageId={}",
                        msg.getRole(), context, msg.getId());
                continue;
            }
            if (msg.getRole() == MessageRole.USER) {
                // 저장 시점에 캐싱된 sanitizedContent 우선 사용 (Gemini PR #90 ⑤).
                // V13 마이그레이션 이전 legacy 행은 NULL 이므로 fallback 으로 호출 시점에 sanitize.
                String sanitized = msg.getSanitizedContent();
                if (sanitized == null) {
                    sanitized = sanitizeService.sanitizeUserText(raw);
                }
                if (sanitized == null || sanitized.isBlank()) {
                    log.warn("Skipping post-sanitize blank USER message in {} history: messageId={}",
                            context, msg.getId());
                    continue;
                }
                msgs.add(CohereChatRequest.Message.user(sanitized));
            } else if (msg.getRole() == MessageRole.CHATBOT) {
                msgs.add(CohereChatRequest.Message.assistant(raw));
            }
        }
    }

    private boolean historyAlreadyContainsLatestUserMessage(List<Message> history, String latestSanitizedUserText) {
        if (latestSanitizedUserText == null || latestSanitizedUserText.isBlank() || history == null || history.isEmpty()) {
            return false;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            Message msg = history.get(i);
            if (msg.getRole() != MessageRole.USER) {
                continue;
            }

            String raw = msg.getContent();
            if (raw == null || raw.isBlank()) {
                return false;
            }

            String sanitized = msg.getSanitizedContent();
            if (sanitized == null) {
                sanitized = sanitizeService.sanitizeUserText(raw);
            }
            return latestSanitizedUserText.equals(sanitized);
        }
        return false;
    }

    /**
     * 분류 컨텍스트 프롬프트 구성 (Issue #48).
     *
     * <p>사용자가 선택한 레벨은 "재분류 금지"로 명시하고, 비워둔
     * 레벨은 온톨로지 허용 자식 목록을 주입한다. LLM 이 환각 L2/L3 을
     * 반환하는 것을 사전 차단하고 분류 정확도를 올린다.</p>
     *
     * <p>userDomains 가 비어있으면 빈 문자열 반환 — L1 조차 아직 못정하면
     * 허용 자식 목록을 만들 기준이 없어 주입 skip.</p>
     */
    private String buildClassificationContext(Consultation c) {
        List<String> userL1 = c.getUserDomains();
        if (userL1 == null || userL1.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("## 사용자 사전 선택 분류\n");
        sb.append("- 대분류: ").append(String.join(", ", userL1)).append("\n");

        boolean hasL2 = isNonEmpty(c.getUserSubDomains());
        boolean hasL3 = isNonEmpty(c.getUserTags());

        if (hasL2) sb.append("- 중분류: ").append(String.join(", ", c.getUserSubDomains())).append("\n");
        if (hasL3) sb.append("- 소분류: ").append(String.join(", ", c.getUserTags())).append("\n");

        sb.append("\n## 분류 기준\n");
        sb.append("- 사용자 사전 선택은 참고값입니다. 대화에서 드러난 실제 사건 분야가 다르면 실제 사건 기준의 aiDomains/aiSubDomains/aiTags를 반환하세요.\n");
        sb.append("- 단, 사용자 선택과 실제 사건이 일치하면 같은 온톨로지 노드명을 반환하세요.\n");
        sb.append("- 사용자 선택과 중복되는 질문은 하지 마세요.");
        return sb.toString();
    }

    private String buildRecentQaMemoryBlock(List<Message> chatHistory) {
        if (chatHistory == null || chatHistory.isEmpty()) {
            return "";
        }

        List<QaMemoryItem> items = new ArrayList<>();
        List<String> seenQuestions = new ArrayList<>();
        for (int i = chatHistory.size() - 1; i >= 0 && items.size() < QA_MEMORY_MAX_ITEMS; i--) {
            Message msg = chatHistory.get(i);
            if (msg.getRole() != MessageRole.CHATBOT) {
                continue;
            }

            String question = normalizeForPrompt(msg.getContent());
            if (question.isBlank() || seenQuestions.contains(question)) {
                continue;
            }

            seenQuestions.add(question);
            List<String> answers = collectUserAnswersAfter(chatHistory, i);
            items.add(new QaMemoryItem(question, answers));
        }

        if (items.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (QaMemoryItem item : items) {
            boolean answered = !item.answers().isEmpty();
            sb.append("- [")
                    .append(answered ? "ANSWERED" : "UNANSWERED")
                    .append("] Q: ")
                    .append(item.question());
            if (answered) {
                sb.append(" / A: ")
                        .append(truncateForPrompt(String.join(" / ", item.answers()), QA_ANSWER_MAX_CHARS));
            }
            sb.append('\n');
        }
        sb.append("\nANSWERED 항목은 동일하거나 의미상 같은 질문으로 다시 묻지 마세요.");
        sb.append(" UNANSWERED 항목도 같은 문장을 반복하지 말고 더 좁은 후속 질문으로 이어가세요.");
        return sb.toString().trim();
    }

    private List<String> collectUserAnswersAfter(List<Message> chatHistory, int chatbotIndex) {
        List<String> answers = new ArrayList<>();
        for (int i = chatbotIndex + 1; i < chatHistory.size(); i++) {
            Message msg = chatHistory.get(i);
            if (msg.getRole() == MessageRole.CHATBOT) {
                break;
            }
            if (msg.getRole() != MessageRole.USER) {
                continue;
            }
            String answer = sanitizedUserContent(msg);
            if (!answer.isBlank()) {
                answers.add(answer);
            }
        }
        return answers;
    }

    private String buildCurrentTargetSlotBlock(SlotLedger slotLedger) {
        if (!slotLedgerEnabled
                || staticQuestionSelector == null
                || slotLedger == null
                || slotLedger.getSlots() == null
                || slotLedger.getSlots().isEmpty()) {
            return "";
        }

        SlotStateItem target = staticQuestionSelector.selectNext(slotLedger.getSlots());
        if (target == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- slotId: ").append(blankToFallback(target.getSlotId(), "(unknown)")).append('\n');
        sb.append("- label: ").append(blankToFallback(target.getLabel(), "(unnamed slot)")).append('\n');
        sb.append("- status: ").append(target.getStatus()).append('\n');
        sb.append("- priority: ").append(target.getPriority())
                .append(target.isRequired() ? " (required)" : " (optional)").append('\n');
        if (target.getPendingValue() != null && !target.getPendingValue().isBlank()) {
            sb.append("- pendingValue: ").append(normalizeForPrompt(target.getPendingValue())).append('\n');
        }
        sb.append("이번 턴은 이 slot을 우선 질문하고 collected item으로 되돌아가지 마세요.");
        return sb.toString();
    }

    private String sanitizedUserContent(Message msg) {
        String sanitized = msg.getSanitizedContent();
        if (sanitized == null) {
            String raw = msg.getContent();
            if (raw != null && !raw.isBlank()) {
                sanitized = sanitizeService.sanitizeUserText(raw);
            }
        }
        return normalizeForPrompt(sanitized);
    }

    private String normalizeForPrompt(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    private String blankToFallback(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    private record QaMemoryItem(String question, List<String> answers) {
    }

    private String budgetBlock(String title, String content, int tokenBudget) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return title + "\n" + truncateForTokenBudget(content.trim(), tokenBudget);
    }

    private String truncateForTokenBudget(String text, int tokenBudget) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int charBudget = Math.max(0, tokenBudget) * APPROX_CHARS_PER_TOKEN;
        if (charBudget == 0 || text.length() <= charBudget) {
            return text;
        }
        int cut = Math.max(0, charBudget - 3);
        return text.substring(0, cut).stripTrailing() + "...";
    }

    private static boolean isNonEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    /**
     * History truncation: 시스템 프롬프트(첫 메시지) + 최근 maxMessages개 유지.
     */
    private List<CohereChatRequest.Message> truncateMessages(List<CohereChatRequest.Message> messages, int maxMessages) {
        if (messages.size() <= maxMessages + 1) {
            return messages;
        }
        int dropped = messages.size() - maxMessages - 1;
        log.warn("History truncation: 전체 {}건 중 {}건 삭제, 최근 {}건 유지",
                messages.size() - 1, dropped, maxMessages);
        List<CohereChatRequest.Message> truncated = new ArrayList<>();
        truncated.add(messages.get(0)); // system prompt
        truncated.addAll(messages.subList(messages.size() - maxMessages, messages.size()));
        return truncated;
    }

    private String buildConversationFactDigest(List<Message> history) {
        if (history == null || history.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("## 대화에서 확인된 사용자 진술 (의뢰서 본문에 반영)\n");
        int count = 0;
        for (Message msg : history) {
            if (msg.getRole() != MessageRole.USER) continue;
            String text = msg.getSanitizedContent();
            if (text == null) {
                text = msg.getContent();
                if (text != null && !text.isBlank()) {
                    text = sanitizeService.sanitizeUserText(text);
                }
            }
            if (text == null || text.isBlank()) continue;
            sb.append("- ").append(truncateForPrompt(text.trim(), 280)).append('\n');
            count++;
            if (count >= 12) break;
        }
        if (count == 0) return "";
        sb.append("\n위 사용자 진술에서 확인된 시기, 대상, 행위 주체, 주요 경위, 상대방의 설명·고지·제안 여부, 손해 또는 요청 사항을 빠뜨리지 말고 사실관계 본문에 반영하세요.");
        return sb.toString();
    }

    private static String truncateForPrompt(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
