package org.example.shield.ai.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsultationDynamicPlanRepository extends JpaRepository<ConsultationDynamicPlan, UUID> {

    Optional<ConsultationDynamicPlan> findFirstByConsultationIdOrderByPlanVersionDesc(UUID consultationId);
}
