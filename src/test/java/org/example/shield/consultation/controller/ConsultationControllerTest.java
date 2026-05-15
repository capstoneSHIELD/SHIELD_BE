package org.example.shield.consultation.controller;

import org.example.shield.common.response.ApiResponse;
import org.example.shield.consultation.application.AnalysisService;
import org.example.shield.consultation.application.ClassificationCandidate;
import org.example.shield.consultation.application.ClassificationResolution;
import org.example.shield.consultation.application.ClassificationResolver;
import org.example.shield.consultation.application.ConsultationService;
import org.example.shield.consultation.application.MessageService;
import org.example.shield.consultation.domain.Consultation;
import org.example.shield.consultation.domain.ConsultationReader;
import org.example.shield.consultation.domain.ConsultationWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsultationControllerTest {

    @Mock private ConsultationService consultationService;
    @Mock private MessageService messageService;
    @Mock private AnalysisService analysisService;
    @Mock private ConsultationReader consultationReader;
    @Mock private ConsultationWriter consultationWriter;
    @Mock private ClassificationResolver classificationResolver;

    @InjectMocks
    private ConsultationController controller;

    @Test
    @DisplayName("analyze — 분류 충돌이 있으면 409와 확인 필요 메시지를 반환")
    void analyze_classificationConflict_returns409() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = Consultation.create(
                UUID.randomUUID(), List.of("부동산 거래"), List.of("부동산 임대차"), null);
        given(consultationReader.findById(consultationId)).willReturn(consultation);
        given(classificationResolver.resolve(consultation)).willReturn(new ClassificationResolution(
                true,
                new ClassificationCandidate(List.of("부동산 거래"), List.of("부동산 임대차"), List.of()),
                new ClassificationCandidate(List.of("손해배상·불법행위"), List.of("의료사고"), List.of("진료 과실 및 설명의무")),
                null));

        ResponseEntity<ApiResponse<Void>> response = controller.analyze(consultationId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("분류 확인이 필요합니다");
        verify(consultationWriter, never()).save(consultation);
        verify(analysisService, never()).analyzeAsync(consultationId);
    }
}
