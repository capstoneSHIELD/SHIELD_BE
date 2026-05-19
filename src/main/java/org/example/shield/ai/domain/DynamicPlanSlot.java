package org.example.shield.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.common.domain.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dynamic_plan_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DynamicPlanSlot extends BaseEntity {

    @Column(nullable = false)
    private UUID planId;

    @Column(nullable = false, length = 100)
    private String slotId;

    @Column(nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SlotSource source;

    @Column(length = 200)
    private String staticMappingId;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SlotStatus status;

    @Column(columnDefinition = "text")
    private String collectedValue;

    @Column(columnDefinition = "text")
    private String pendingValue;

    @Column(length = 50)
    private String validationHint;

    @Column(columnDefinition = "text")
    private String questionText;

    private LocalDateTime askedAt;

    private LocalDateTime answeredAt;

    private DynamicPlanSlot(
            UUID planId,
            String slotId,
            String label,
            SlotSource source,
            String staticMappingId,
            boolean required,
            int priority,
            SlotStatus status,
            String collectedValue,
            String pendingValue,
            String validationHint,
            String questionText,
            LocalDateTime askedAt,
            LocalDateTime answeredAt
    ) {
        this.planId = planId;
        this.slotId = slotId;
        this.label = label;
        this.source = source;
        this.staticMappingId = staticMappingId;
        this.required = required;
        this.priority = priority;
        this.status = status;
        this.collectedValue = collectedValue;
        this.pendingValue = pendingValue;
        this.validationHint = validationHint;
        this.questionText = questionText;
        this.askedAt = askedAt;
        this.answeredAt = answeredAt;
    }

    public static DynamicPlanSlot create(
            UUID planId,
            String slotId,
            String label,
            SlotSource source,
            String staticMappingId,
            boolean required,
            int priority,
            SlotStatus status,
            String collectedValue,
            String pendingValue,
            String validationHint,
            String questionText,
            LocalDateTime askedAt,
            LocalDateTime answeredAt
    ) {
        return new DynamicPlanSlot(
                planId,
                slotId,
                label,
                source,
                staticMappingId,
                required,
                priority,
                status,
                collectedValue,
                pendingValue,
                validationHint,
                questionText,
                askedAt,
                answeredAt);
    }
}
