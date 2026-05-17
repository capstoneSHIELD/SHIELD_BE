package org.example.shield.ai.application;

import org.example.shield.ai.dto.CaseTypeResult;
import org.example.shield.ai.dto.DynamicPlanProposal;
import org.example.shield.ai.dto.DynamicPlanSlotProposal;
import org.example.shield.ai.dto.ValidatedDynamicPlan;
import org.example.shield.ai.dto.slot.SlotSource;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.example.shield.ai.infrastructure.GuardrailFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackendValidatorTest {

    private OntologyService ontologyService;
    private ChecklistAliasIndex aliasIndex;
    private BackendValidator validator;

    @BeforeEach
    void setUp() {
        ontologyService = mock(OntologyService.class);
        aliasIndex = new ChecklistAliasIndex();
        aliasIndex.load();
        validator = new BackendValidator(ontologyService, aliasIndex, new GuardrailFilter());

        when(ontologyService.contains("부동산 거래")).thenReturn(true);
        when(ontologyService.isChildOf("부동산 임대차", "부동산 거래")).thenReturn(true);
        when(ontologyService.isChildOf("보증금 및 차임", "부동산 임대차")).thenReturn(true);
    }

    @Test
    @DisplayName("valid static and mapped dynamic slots are accepted")
    void validate_acceptsValidSlots() {
        DynamicPlanProposal proposal = new DynamicPlanProposal(
                caseType(),
                0.87,
                List.of(
                        slot("lease_end_date", SlotSource.STATIC_CHECKLIST,
                                "real-estate.lease_end_date", "계약은 언제 종료되었나요?"),
                        slot("landlord_response", SlotSource.DYNAMIC,
                                null, "임대인은 반환 거절 사유를 설명했나요?")),
                "lease_end_date",
                false);

        ValidatedDynamicPlan result = validator.validate(proposal);

        assertThat(result.slots()).hasSize(2);
        assertThat(result.rejectionReasons()).isEmpty();
    }

    @Test
    @DisplayName("out-of-domain caseType rejects all plan trust")
    void validate_rejectsOutOfDomain() {
        DynamicPlanProposal proposal = new DynamicPlanProposal(
                new CaseTypeResult("형사", null, null, 0.7),
                0.7,
                List.of(slot("lease_end_date", SlotSource.STATIC_CHECKLIST,
                        "real-estate.lease_end_date", "계약은 언제 종료되었나요?")),
                "lease_end_date",
                false);

        ValidatedDynamicPlan result = validator.validate(proposal);

        assertThat(result.rejectionReasons()).anyMatch(reason -> reason.contains("caseType"));
    }

    @Test
    @DisplayName("unmapped dynamic slot is rejected")
    void validate_rejectsUnmappedDynamic() {
        DynamicPlanProposal proposal = new DynamicPlanProposal(
                caseType(),
                0.87,
                List.of(slot("random", SlotSource.DYNAMIC, null, "아무 질문인가요?")),
                "random",
                false);

        ValidatedDynamicPlan result = validator.validate(proposal);

        assertThat(result.slots()).isEmpty();
        assertThat(result.rejectionReasons()).anyMatch(reason -> reason.contains("unmappable"));
    }

    @Test
    @DisplayName("legal judgment question is rejected")
    void validate_rejectsLegalJudgment() {
        DynamicPlanProposal proposal = new DynamicPlanProposal(
                caseType(),
                0.87,
                List.of(slot("lease_end_date", SlotSource.STATIC_CHECKLIST,
                        "real-estate.lease_end_date", "이 사안은 승소 가능합니다.")),
                "lease_end_date",
                false);

        ValidatedDynamicPlan result = validator.validate(proposal);

        assertThat(result.slots()).isEmpty();
        assertThat(result.rejectionReasons()).anyMatch(reason -> reason.contains("legal judgment"));
    }

    private CaseTypeResult caseType() {
        return new CaseTypeResult("부동산 거래", "부동산 임대차", "보증금 및 차임", 0.87);
    }

    private DynamicPlanSlotProposal slot(
            String id,
            SlotSource source,
            String staticMappingId,
            String question
    ) {
        return new DynamicPlanSlotProposal(
                id,
                labelFor(id),
                source,
                staticMappingId,
                true,
                1,
                SlotStatus.MISSING,
                question,
                "text",
                null);
    }

    private String labelFor(String id) {
        if ("landlord_response".equals(id)) {
            return "임대인 반환 거절 사유";
        }
        if ("lease_end_date".equals(id)) {
            return "계약 종료일";
        }
        return "무관한 정보";
    }
}
