package org.example.shield.consultation.controller.dto;

import jakarta.validation.constraints.NotNull;
import org.example.shield.common.enums.DomainType;

public record ClassifyRequest(
        @NotNull(message = "분류 값은 필수입니다")
        DomainType primaryField
) {}
