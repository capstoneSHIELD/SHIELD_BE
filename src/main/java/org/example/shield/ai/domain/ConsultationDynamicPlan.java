package org.example.shield.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.shield.common.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "consultation_dynamic_plan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultationDynamicPlan extends BaseEntity {

    @Column(nullable = false)
    private UUID consultationId;

    @Column(name = "version", nullable = false)
    private int planVersion;

    @Column(length = 50)
    private String caseTypeL1;

    @Column(length = 50)
    private String caseTypeL2;

    @Column(length = 50)
    private String caseTypeL3;

    @Column(precision = 4, scale = 3)
    private BigDecimal planConfidence;

    private ConsultationDynamicPlan(
            UUID consultationId,
            int planVersion,
            String caseTypeL1,
            String caseTypeL2,
            String caseTypeL3,
            BigDecimal planConfidence
    ) {
        this.consultationId = consultationId;
        this.planVersion = planVersion;
        this.caseTypeL1 = caseTypeL1;
        this.caseTypeL2 = caseTypeL2;
        this.caseTypeL3 = caseTypeL3;
        this.planConfidence = planConfidence;
    }

    public static ConsultationDynamicPlan create(
            UUID consultationId,
            int planVersion,
            String caseTypeL1,
            String caseTypeL2,
            String caseTypeL3,
            BigDecimal planConfidence
    ) {
        return new ConsultationDynamicPlan(
                consultationId,
                planVersion,
                caseTypeL1,
                caseTypeL2,
                caseTypeL3,
                planConfidence);
    }
}
