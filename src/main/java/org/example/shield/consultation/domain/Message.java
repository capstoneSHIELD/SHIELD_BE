package org.example.shield.consultation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.shield.common.enums.MessageRole;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID consultationId;

    @Column(nullable = false, insertable = false, updatable = false)
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "message_role")
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /**
     * USER 메시지의 PII 마스킹 결과 캐시 (Gemini PR #90 ⑤).
     * 저장 시점에 채워 두면 LLM history 구성마다 sanitize 를 반복하지 않아도 된다.
     * NULL 이면 V13 마이그레이션 이전 legacy 행 — 호출 시점에 fallback 으로 sanitize 한다.
     */
    @Column(name = "sanitized_content", columnDefinition = "text")
    private String sanitizedContent;

    private String model;

    private Integer tokensInput;

    private Integer tokensOutput;

    private Integer latencyMs;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Message(UUID consultationId, MessageRole role, String content, String sanitizedContent,
                    String model, Integer tokensInput, Integer tokensOutput,
                    Integer latencyMs) {
        this.consultationId = consultationId;
        this.role = role;
        this.content = content;
        this.sanitizedContent = sanitizedContent;
        this.model = model;
        this.tokensInput = tokensInput;
        this.tokensOutput = tokensOutput;
        this.latencyMs = latencyMs;
    }

    public static Message createUserMessage(UUID consultationId, String content, String sanitizedContent) {
        return Message.builder()
                .consultationId(consultationId)
                .role(MessageRole.USER)
                .content(content)
                .sanitizedContent(sanitizedContent)
                .build();
    }

    /**
     * Sanitize 결과 캐시 없이 USER 메시지를 생성한다. {@link #createUserMessage(UUID, String, String)}
     * 가 사용되기 전 코드 및 테스트 호환성용. 신규 production 경로는 sanitizedContent 를 함께 전달해야
     * LLM history 구성 시 반복 sanitize 를 회피할 수 있다.
     */
    public static Message createUserMessage(UUID consultationId, String content) {
        return createUserMessage(consultationId, content, null);
    }

    public static Message createAiMessage(UUID consultationId, String content,
                                          String model, Integer tokensInput,
                                          Integer tokensOutput, Integer latencyMs) {
        return Message.builder()
                .consultationId(consultationId)
                .role(MessageRole.CHATBOT)
                .content(content)
                .model(model)
                .tokensInput(tokensInput)
                .tokensOutput(tokensOutput)
                .latencyMs(latencyMs)
                .build();
    }
}
