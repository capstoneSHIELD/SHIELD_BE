package org.example.shield.consultation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.shield.ai.application.OntologyService;
import org.example.shield.consultation.domain.Consultation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationResolverTest {

    private ClassificationResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        String json;
        try (InputStream in = new ClassPathResource("ontology/legal-ontology-slim.json").getInputStream()) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        OntologyService ontologyService = new OntologyService(json, new ObjectMapper());
        ontologyService.loadOntology();
        resolver = new ClassificationResolver(ontologyService);
    }

    @Test
    @DisplayName("AI L3 만 있어도 L2/L1 부모를 채운다")
    void canonicalizeStrict_l3Only_fillsParents() {
        ClassificationCandidate candidate = resolver.canonicalizeStrict(
                null, null, List.of("진료 과실 및 설명의무"));

        assertThat(candidate.domains()).containsExactly("손해배상·불법행위");
        assertThat(candidate.subDomains()).containsExactly("의료사고");
        assertThat(candidate.tags()).containsExactly("진료 과실 및 설명의무");
    }

    @Test
    @DisplayName("사용자 다중 L1 안에 AI L1 이 포함되면 충돌이 아니다")
    void resolve_userMultiDomainContainsAiDomain_noConflict() {
        Consultation consultation = Consultation.create(
                UUID.randomUUID(),
                List.of("부동산 거래", "이혼·위자료·재산분할", "손해배상·불법행위"),
                null,
                null);
        consultation.updateAiClassification(
                List.of("손해배상·불법행위"),
                List.of("의료사고"),
                List.of("진료 과실 및 설명의무"));

        ClassificationResolution resolution = resolver.resolve(consultation);

        assertThat(resolution.conflict()).isFalse();
        assertThat(resolution.effectiveCandidate().domains()).containsExactly("손해배상·불법행위");
    }

    @Test
    @DisplayName("사용자 선택과 AI 분류가 다른 온톨로지 가지면 충돌")
    void resolve_differentBranches_conflict() {
        Consultation consultation = Consultation.create(
                UUID.randomUUID(),
                List.of("부동산 거래"),
                List.of("부동산 임대차"),
                null);
        consultation.updateAiClassification(
                List.of("손해배상·불법행위"),
                List.of("의료사고"),
                List.of("진료 과실 및 설명의무"));

        ClassificationResolution resolution = resolver.resolve(consultation);

        assertThat(resolution.conflict()).isTrue();
        assertThat(resolution.effectiveCandidate()).isNull();
        assertThat(resolution.userCandidate().domains()).containsExactly("부동산 거래");
        assertThat(resolution.aiCandidate().domains()).containsExactly("손해배상·불법행위");
    }
}
