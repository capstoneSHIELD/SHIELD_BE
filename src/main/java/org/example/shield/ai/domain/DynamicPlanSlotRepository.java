package org.example.shield.ai.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DynamicPlanSlotRepository extends JpaRepository<DynamicPlanSlot, UUID> {

    List<DynamicPlanSlot> findAllByPlanIdOrderByPriorityAsc(UUID planId);

    void deleteAllByPlanId(UUID planId);
}
