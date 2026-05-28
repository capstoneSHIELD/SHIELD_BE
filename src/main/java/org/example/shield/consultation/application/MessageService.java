package org.example.shield.consultation.application;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.application.ChecklistCoverageService;
import org.example.shield.ai.application.CohereService;
import org.example.shield.ai.application.BackendIntentRouter;
import org.example.shield.ai.application.IntentClassificationService;
import org.example.shield.ai.application.IntentRouteDecision;
import org.example.shield.ai.application.RagPipelineService;
import org.example.shield.ai.application.ChecklistScopeResolver;
import org.example.shield.ai.application.SlotLedgerService;
import org.example.shield.ai.config.CohereApiConfig;
import org.example.shield.ai.dto.AiCallResult;
import org.example.shield.ai.dto.ChatParsedResponse;
import org.example.shield.ai.dto.checklist.ChecklistScope;
import org.example.shield.ai.dto.IntentRouterResponse;
import org.example.shield.ai.dto.RagPipelineResult;
import org.example.shield.ai.infrastructure.SanitizeService;
import org.example.shield.common.enums.MessageRole;
import org.example.shield.common.exception.ChatAiException;
import org.example.shield.common.response.PageResponse;
import org.example.shield.consultation.controller.dto.MessageResponse;
import org.example.shield.consultation.controller.dto.SendMessageResponse;
import org.example.shield.consultation.domain.Consultation;
import org.example.shield.consultation.domain.ConsultationReader;
import org.example.shield.consultation.domain.Message;
import org.example.shield.consultation.domain.MessageReader;
import org.example.shield.consultation.exception.ConsultationTurnLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 상담 메시지 처리 조율자.
 *
 * <p>이 서비스는 <b>non-transactional</b> 이다 ({@code readOnly=true} 도 부여하지
 * 않는다). Cohere Chat v2 호출은 외부 HTTP 요청이라 DB 트랜잭션 안에서 수행
 * 되면 커넥션을 수초~수십초 점유할 수 있으므로, DB 작업은
 * {@link ChatTransactionalBoundary} 의 짧은 트랜잭션으로 분리했다.</p>
 *
 * <p>실행 흐름:</p>
 * <ol>
 *   <li>사용자 입력 sanitize (순수 로직, 예외 시 PII 안내 메시지 저장 후 early return)</li>
 *   <li>USER 메시지 저장 — {@link ChatTransactionalBoundary#saveUserMessage} (독립 tx)</li>
 *   <li>대화 내역 조회 (tx 밖의 read-only)</li>
 *   <li>RAG + Cohere chat() 호출 — <b>트랜잭션 밖</b></li>
 *   <li>blank 응답 차단 — {@link ChatAiException} (Issue #45)</li>
 *   <li>AI 분류 결과 온톨로지 필터링 (순수 로직)</li>
 *   <li>AI 응답 최종 반영 — {@link ChatTransactionalBoundary#finalizeAiResponse} (독립 tx)</li>
 *   <li>allCompleted 커버리지 게이트 (외부 검사, tx 불필요)</li>
 * </ol>
 *
 * <p>USER 메시지 저장이 독립 트랜잭션이기 때문에 이후 Cohere 실패·blank 응답·
 * 네트워크 에러 등 어떤 예외가 발생해도 사용자 입력은 절대 유실되지 않는다.
 * PR-A 의 {@code noRollbackFor} 접근보다 한 단계 더 강한 격리다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageReader messageReader;
    private final ConsultationReader consultationReader;
    private final CohereService cohereService;
    private final CohereApiConfig cohereApiConfig;
    private final SanitizeService sanitizeService;
    private final ChecklistCoverageService checklistCoverageService;
    private final ChecklistScopeResolver checklistScopeResolver;
    private final RagPipelineService ragPipelineService;
    private final ChatTransactionalBoundary chatTxBoundary;
    private final ChatMetrics chatMetrics;
    private final ClassificationResolver classificationResolver;
    private final SlotLedgerService slotLedgerService;
    private final IntentClassificationService intentClassificationService;
    private final BackendIntentRouter backendIntentRouter;

    /**
     * 사용자 메시지 상한. 도달(=) 시 현재 턴을 마지막으로 처리하고 {@code effectiveAllCompleted=true}
     * 를 강제 반환하여 FE 가 의뢰서 생성(/analyze)으로 전환하게 한다. 초과(>) 는 방어선 —
     * {@link ConsultationTurnLimitExceededException} 으로 400 반환.
     */
    @Value("${shield.consultation.max-user-turns:10}")
    private int maxUserTurns;

    @Value("${app.ai.cohere.corrected-slots.enabled:false}")
    private boolean correctedSlotsEnabled;

    @Value("${app.ai.cohere.corrected-slots.confidence-threshold:0.85}")
    private double correctedSlotsConfidenceThreshold;

    /**
     * 사용자 메시지 처리 및 AI 응답 생성.
     *
     * <p>{@code noRollbackFor = ChatAiException.class} (Issue #45 후속):
     * AI 응답이 blank 로 내려와 {@link ChatAiException} 이 발생하더라도
     * 사용자가 이미 저장한 USER 메시지와 감사 로깅용
     * {@code lastResponseId} 는 커밋되어야 한다. 그렇지 않으면 AI 실패
     * 시마다 사용자 입력이 유실되어 재현 가능한 데이터 손실이 발생한다.</p>
     *
     * <p>이 메서드 자체는 트랜잭션을 열지 않는다 (클래스 javadoc 참조).
     * DB 작업은 모두 {@link ChatTransactionalBoundary} 의 짧은 독립 트랜잭션으로 위임된다.
     * 외부 호출(RAG/Cohere) 중 발생한 SQL 오류가 외부 트랜잭션을 rollback-only 로
     * 만들어 후속 commit 단계에서 {@code UnexpectedRollbackException} 으로 500 응답을
     * 유발하던 회귀를 방지하기 위함이다.</p>
     */
    public SendMessageResponse sendMessage(UUID consultationId, String content) {
        long pipelineStart = System.nanoTime();
        try {
            Consultation consultation = consultationReader.findById(consultationId);

            // 0-a. 턴 상한 방어선 — 이미 상한에 도달한 상담이면 새 USER 메시지 저장 전에 차단.
            // 정상 FE 는 이전 턴의 allCompleted=true 에서 /analyze 로 전환하므로 이 분기에 도달하지 않음.
            long existingUserTurns = messageReader.countByConsultationIdAndRole(consultationId, MessageRole.USER);
            if (existingUserTurns >= maxUserTurns) {
                chatMetrics.recordSendMessage(pipelineStart, "turn_limit");
                throw new ConsultationTurnLimitExceededException(consultationId, maxUserTurns);
            }

            // 0-b. 사용자 입력 sanitization (P0-III)
            String sanitizedText;
            try {
                sanitizedText = sanitizeService.sanitizeUserText(content);
            } catch (SanitizeService.PiiDetectedException e) {
                Message savedPii = chatTxBoundary.savePiiAiMessage(consultationId, e.getMessage());
                chatMetrics.recordSendMessage(pipelineStart, "pii");
                // PII 거부 시 USER 메시지는 저장되지 않으므로 진행률 증가 없음
                SendMessageResponse.Progress piiProgress =
                        SendMessageResponse.Progress.of((int) existingUserTurns, maxUserTurns);
                return SendMessageResponse.from(savedPii, false, piiProgress, null);
            }

            // 1. USER 메시지 저장 (독립 트랜잭션 — 후속 실패와 무관하게 보존)
            //    sanitizedText 도 함께 캐싱하여 LLM history 구성 시 반복 sanitize 회피 (Gemini PR #90 ⑤).
            chatTxBoundary.saveUserMessage(consultationId, content, sanitizedText);

            // 대화 내역 1회 조회 — RAG와 chat() 양쪽에서 공유 (중복 DB 쿼리 방지)
            List<Message> chatHistory = messageReader.findAllByConsultationId(consultationId);

            // 방금 저장된 USER 메시지 포함 턴 수가 상한에 도달했는지.
            // 도달 시 Cohere 호출은 정상 수행하되(분류/슬롯 업데이트), 응답의 allCompleted 를 강제로 true 로 내려
            // FE 가 /analyze 로 전환하게 한다.
            boolean turnLimitReached = existingUserTurns + 1 >= maxUserTurns;

            // 2. [RAG] 도메인 정보가 있을 때만 실행 — 트랜잭션 밖
            String ragContext = "";
            ClassificationCandidate collectionCandidate =
                    classificationResolver.candidateForCollection(consultation);
            slotLedgerService.ensureInitialized(consultation, collectionCandidate, chatHistory);
            SendMessageResponse.Checklist promptChecklist = buildPromptChecklist(collectionCandidate);
            String domainForRag = collectionCandidate.firstDomain();
            IntentRouterResponse intentResult = intentClassificationService.route(chatHistory, domainForRag);
            IntentRouteDecision routeDecision = backendIntentRouter.route(
                    consultation, intentResult, chatHistory, sanitizedText);
            if (routeDecision.skipCohere()) {
                AiFinalizePayload fixedPayload = new AiFinalizePayload(
                        "intent-router:" + routeDecision.reason(),
                        routeDecision.responseText(),
                        "backend-intent-router",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
                Message savedAi = chatTxBoundary.finalizeAiResponse(
                        consultationId, fixedPayload, consultation.getSlotState());
                chatMetrics.recordSendMessage(pipelineStart, "success");
                SendMessageResponse.Progress progress =
                        SendMessageResponse.Progress.of((int) existingUserTurns + 1, maxUserTurns);
                return SendMessageResponse.from(
                        savedAi,
                        false,
                        classificationResolver.resolve(consultation),
                        progress,
                        promptChecklist);
            }
            if (domainForRag != null) {
                RagPipelineResult ragResult = ragPipelineService.executeDetailed(
                        chatHistory, domainForRag, consultationId, intentResult);
                ragContext = ragResult.ragContext();
                if (ragContext == null || ragContext.isEmpty()) {
                    // RAG 파이프라인은 실패해도 빈 컨텍스트로 응답하므로(graceful degrade),
                    // 어떤 의뢰가 RAG-less 경로로 빠졌는지 추적 가능하도록 한 줄 남긴다.
                    // 상세 실패 원인은 RagPipelineService 의 warn 로그에 stage/스택과 함께 기록됨.
                    log.info("Proceeding without RAG context: consultationId={}, domain={}, retrieved={}",
                            consultationId, domainForRag,
                            ragResult.retrievalResults() == null ? 0 : ragResult.retrievalResults().size());
                }
            }

            // 3. Cohere Chat v2 호출 — 트랜잭션 밖 + Micrometer 타이밍
            AiCallResult<ChatParsedResponse> result = callCohereMeasured(
                    consultation, sanitizedText, ragContext, chatHistory);
            ChatParsedResponse parsed = result.data();

            // 4. AI 응답 blank 차단 (Issue #45)
            String nextQuestion = parsed.getNextQuestion();
            if (nextQuestion == null || nextQuestion.isBlank()) {
                log.error("AI chat response is blank: consultationId={}, responseId={}, tokensOut={}",
                        consultationId, result.responseId(), result.tokensOutput());
                // 감사 로깅 목적 lastResponseId 는 별도 트랜잭션으로 커밋 (Issue #45)
                chatTxBoundary.persistBlankResponseId(consultationId, result.responseId());
                consultation.updateLastResponseId(result.responseId());
                chatMetrics.incrementBlankResponse();
                chatMetrics.recordSendMessage(pipelineStart, "blank");
                throw new ChatAiException();
            }

            // 4-b. 턴 상한 도달 시: LLM 이 추가 질문을 생성했더라도 완료 안내 멘트로 강제 치환.
            // allCompleted=true 와 nextQuestion="추가 질문..." 이 공존해 UX 가 혼란스러워지는 문제 방어.
            if (turnLimitReached) {
                nextQuestion = "필요한 정보를 충분히 수집했습니다. "
                        + "'의뢰서 생성' 버튼을 눌러 의뢰서를 만들어 주세요.";
                log.info("턴 상한 도달 — nextQuestion 을 완료 안내로 치환: consultationId={}",
                        consultationId);
            }

            // 5. AI 분류 결과 온톨로지 정규화 (L3만 와도 L2/L1 부모 복원)
            ClassificationCandidate aiCandidate = classificationResolver.canonicalizeStrict(
                    parsed.getAiDomains(),
                    parsed.getAiSubDomains(),
                    parsed.getAiTags());
            boolean hasAnyAi = aiCandidate.hasAny();
            applyCorrectedSlotsIfEnabled(consultation, parsed);
            slotLedgerService.appendAskedQuestion(consultation, nextQuestion);

            // 6. AI 응답 최종 반영 (독립 트랜잭션)
            AiFinalizePayload payload = new AiFinalizePayload(
                    result.responseId(),
                    nextQuestion,
                    cohereApiConfig.getChatModel(),
                    result.tokensInput(),
                    result.tokensOutput(),
                    result.latencyMs(),
                    hasAnyAi ? aiCandidate.domains() : null,
                    hasAnyAi ? aiCandidate.subDomains() : null,
                    hasAnyAi ? aiCandidate.tags() : null
            );
            Message savedAi = chatTxBoundary.finalizeAiResponse(
                    consultationId, payload, consultation.getSlotState());

            // DB에 반영된 AI 분류 결과를 로컬 객체에도 동기화하여 후속 커버리지 계산에 사용
            if (payload.hasAnyClassification()) {
                consultation.updateAiClassification(payload.aiDomains(), payload.aiSubDomains(), payload.aiTags());
            }

            // 7. allCompleted 게이트 — 턴 상한 도달 시 커버리지 무시하고 true,
            //    아니면 기존 AND gate (P0-II, Issue #40 3레벨 커버리지) — 모두 트랜잭션 밖
            boolean effectiveAllCompleted = evaluateAllCompletedGate(
                    consultationId, consultation, parsed, turnLimitReached);

            // 8. allCompleted=true 시 영구 저장 — 페이지 재진입 복원용 (Issue #100, idempotent)
            if (effectiveAllCompleted) {
                chatTxBoundary.markConsultationAllCompleted(consultationId);
            }

            chatMetrics.recordSendMessage(pipelineStart, turnLimitReached ? "turn_limit_reached" : "success");
            // 방금 저장된 USER 메시지를 포함한 누적 턴 수로 진행률 계산
            SendMessageResponse.Progress progress =
                    SendMessageResponse.Progress.of((int) existingUserTurns + 1, maxUserTurns);
            return SendMessageResponse.from(
                    savedAi,
                    effectiveAllCompleted,
                    classificationResolver.resolve(consultation),
                    progress,
                    promptChecklist);
        } catch (ChatAiException | ConsultationTurnLimitExceededException e) {
            throw e; // already metered
        } catch (RuntimeException e) {
            chatMetrics.recordSendMessage(pipelineStart, "error");
            throw e;
        }
    }

    /**
     * Cohere chat() 호출을 Micrometer timer 로 감싼다.
     * outcome 태그: success / blank / failure.
     */
    private AiCallResult<ChatParsedResponse> callCohereMeasured(
            Consultation consultation, String sanitizedText, String ragContext, List<Message> chatHistory) {
        Timer.Sample sample = chatMetrics.startCohereCall();
        try {
            AiCallResult<ChatParsedResponse> result = cohereService.chat(
                    consultation, sanitizedText, ragContext, chatHistory);
            String nq = result.data() == null ? null : result.data().getNextQuestion();
            if (nq == null || nq.isBlank()) {
                chatMetrics.stopCohereCallBlank(sample);
            } else {
                chatMetrics.stopCohereCallSuccess(sample);
            }
            return result;
        } catch (RuntimeException e) {
            chatMetrics.stopCohereCallFailure(sample);
            throw e;
        }
    }

    private void applyCorrectedSlotsIfEnabled(Consultation consultation, ChatParsedResponse parsed) {
        if (!correctedSlotsEnabled) {
            return;
        }
        boolean changed = slotLedgerService.applyCorrectedSlots(
                consultation,
                parsed,
                correctedSlotsConfidenceThreshold);
        if (changed) {
            log.info("Cohere correctedSlots staged as pending confirmation: consultationId={}",
                    consultation.getId());
        }
    }

    private SendMessageResponse.Checklist buildPromptChecklist(ClassificationCandidate candidate) {
        if (candidate == null || candidate.firstDomain() == null) {
            return SendMessageResponse.Checklist.empty();
        }
        ChecklistScope scope = checklistScopeResolver.resolve(
                candidate.firstDomain(),
                candidate.firstSubDomain(),
                candidate.firstTag());
        return SendMessageResponse.Checklist.from(scope);
    }

    /**
     * allCompleted 게이트.
     *
     * <ol>
     *   <li>{@code turnLimitReached=true} → 커버리지·LLM 신호 모두 무시하고 true 반환.
     *       사용자 메시지 상한에 도달했으므로 상담을 강제 종료하고 /analyze 로 전환한다.</li>
     *   <li>그 외 → 기존 AND gate: LLM 이 allCompleted 를 외친 경우에 한해
     *       {@link ChecklistCoverageService} 커버리지가 임계치 이상인지 검증.</li>
     * </ol>
     */
    private boolean evaluateAllCompletedGate(UUID consultationId, Consultation consultation,
                                              ChatParsedResponse parsed, boolean turnLimitReached) {
        if (turnLimitReached) {
            log.info("Turn limit reached ({}): forcing effectiveAllCompleted=true. consultationId={}",
                    maxUserTurns, consultationId);
            return true;
        }
        if (!parsed.isAllCompleted()) return false;

        ClassificationCandidate candidate = classificationResolver.candidateForCollection(consultation);
        String l1 = candidate.firstDomain();
        String l2 = candidate.firstSubDomain();
        String l3 = candidate.firstTag();

        double coverageRatio = checklistCoverageService.compute(consultationId, l1, l2, l3);
        boolean effective = checklistCoverageService.isEffectivelyCompleted(true, coverageRatio);

        if (!effective) {
            log.warn("LLM reported allCompleted=true but coverage={} < {}: consultationId={}, L1={}, L2={}, L3={}",
                    coverageRatio, checklistCoverageService.getThreshold(), consultationId, l1, l2, l3);
        }
        return effective;
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> getMessages(UUID consultationId, Pageable pageable) {
        consultationReader.findById(consultationId);

        Page<Message> messages = messageReader.findAllByConsultationId(consultationId, pageable);
        Page<MessageResponse> responsePage = messages.map(MessageResponse::from);

        return PageResponse.from(responsePage);
    }

}
