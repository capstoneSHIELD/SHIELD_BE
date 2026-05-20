package org.example.shield.consultation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.common.domain.BaseEntity;
import org.example.shield.common.enums.ConsultationStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "consultations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consultation extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "consultation_status")
    private ConsultationStatus status;

    // ── 사용자 선택 (상담 생성 시) ──

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> userDomains;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> userSubDomains;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> userTags;

    // ── AI 분류 (대화 중 LLM이 판단) ──

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> aiDomains;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> aiSubDomains;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> aiTags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "slot_state", columnDefinition = "jsonb")
    private SlotLedger slotState;

    // ── 공통 필드 ──

    @Column(columnDefinition = "text")
    private String lastMessage;

    private LocalDateTime lastMessageAt;

    /**
     * LLM 응답 completion ID (감사 로깅용).
     * Cohere v2 Chat API는 무상태 모델이므로 Stateful 연결 용도 없음 — 항상 full history 전송.
     * 과거 LLM 제공자의 previous_response_id 호환을 위해 필드는 보존 (DB 스키마 호환성).
     */
    @Column(columnDefinition = "text")
    private String lastResponseId;

    /**
     * AI 가 사실관계 수집을 완료했는지 여부 (Issue #100, V16 마이그레이션).
     * MessageService.evaluateAllCompletedGate 가 true 를 반환한 시점에 영구 저장되며,
     * 한 번 true 가 되면 다시 false 로 돌리지 않는다 (idempotent).
     * FE 가 GET /consultations/{id} 응답으로 의뢰서 생성 버튼 노출 여부를 복원하는 데 사용한다.
     */
    @Column(name = "all_completed", nullable = false)
    private boolean allCompleted = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private Consultation(UUID userId, List<String> domains, List<String> subDomains, List<String> tags) {
        this.userId = userId;
        this.userDomains = domains;
        this.userSubDomains = subDomains;
        this.userTags = tags;
        this.status = ConsultationStatus.COLLECTING;
    }

    public static Consultation create(UUID userId, List<String> domains,
                                      List<String> subDomains, List<String> tags) {
        return new Consultation(userId, domains, subDomains, tags);
    }

    /**
     * 사용자가 직접 분류를 수정.
     */
    public void updateUserClassification(List<String> domains, List<String> subDomains,
                                         List<String> tags) {
        this.userDomains = domains;
        this.userSubDomains = subDomains;
        this.userTags = tags;
        clearAiClassification();
    }

    /**
     * LLM 분류 결과를 반영한다.
     * 사용자 선택과 AI 판단이 충돌할 수 있으므로 AI 결과는 별도 ai* 필드에 항상 보존한다.
     * 최종 사용 분류는 서비스 계층의 ClassificationResolver 가 계산한다.
     *
     * @return 실제로 하나라도 업데이트되었으면 true
     */
    public boolean updateAiClassification(List<String> domains, List<String> subDomains,
                                          List<String> tags) {
        boolean anyUpdated = false;
        if (domains != null) {
            this.aiDomains = domains;
            anyUpdated = true;
        }
        if (subDomains != null) {
            this.aiSubDomains = subDomains;
            anyUpdated = true;
        }
        if (tags != null) {
            this.aiTags = tags;
            anyUpdated = true;
        }
        return anyUpdated;
    }

    public void clearAiClassification() {
        this.aiDomains = null;
        this.aiSubDomains = null;
        this.aiTags = null;
    }

    public void updateLastResponseId(String responseId) {
        this.lastResponseId = responseId;
    }

    public void updateStatus(ConsultationStatus status) {
        this.status = status;
    }

    public void updateLastMessage(String content, LocalDateTime timestamp) {
        this.lastMessage = content;
        this.lastMessageAt = timestamp;
    }

    public void updateSlotState(SlotLedger slotState) {
        this.slotState = slotState;
    }

    /**
     * AI 사실관계 수집 완료 시 호출. Idempotent — 한 번 true 면 다시 호출돼도 변화 없음.
     * MessageService.evaluateAllCompletedGate 결과가 true 일 때 호출된다 (Issue #100).
     */
    public void markAllCompleted() {
        if (!this.allCompleted) {
            this.allCompleted = true;
        }
    }

    /**
     * 도메인 정보 추출: userDomains 우선, aiDomains 폴백.
     * 온톨로지 L1 한글 이름을 담는다 (예: "부동산 거래").
     */
    public String getFirstDomain() {
        if (isNonEmpty(userDomains)) return userDomains.get(0);
        if (isNonEmpty(aiDomains)) return aiDomains.get(0);
        return null;
    }

    /**
     * 서브도메인(L2) 추출: userSubDomains 우선, aiSubDomains 폴백.
     * 온톨로지 L2 한글 이름을 담는다 (예: "부동산 매매").
     */
    public String getFirstSubDomain() {
        if (isNonEmpty(userSubDomains)) return userSubDomains.get(0);
        if (isNonEmpty(aiSubDomains)) return aiSubDomains.get(0);
        return null;
    }

    /**
     * 태그(L3) 추출: userTags 우선, aiTags 폴백.
     * 온톨로지 L3 한글 이름을 담는다 (예: "매매 계약 불이행").
     */
    public String getFirstTag() {
        if (isNonEmpty(userTags)) return userTags.get(0);
        if (isNonEmpty(aiTags)) return aiTags.get(0);
        return null;
    }

    /**
     * 매칭용 전체 대분류 리스트: userDomains 우선, 없으면 aiDomains.
     */
    public List<String> getEffectiveDomains() {
        if (isNonEmpty(userDomains)) return userDomains;
        if (isNonEmpty(aiDomains)) return aiDomains;
        return List.of();
    }

    /**
     * 매칭용 전체 중분류 리스트: userSubDomains 우선, 없으면 aiSubDomains.
     */
    public List<String> getEffectiveSubDomains() {
        if (isNonEmpty(userSubDomains)) return userSubDomains;
        if (isNonEmpty(aiSubDomains)) return aiSubDomains;
        return List.of();
    }

    /**
     * 매칭용 전체 소분류(태그) 리스트: userTags 우선, 없으면 aiTags.
     */
    public List<String> getEffectiveTags() {
        if (isNonEmpty(userTags)) return userTags;
        if (isNonEmpty(aiTags)) return aiTags;
        return List.of();
    }

    private static boolean isNonEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }
}
