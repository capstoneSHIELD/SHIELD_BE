package org.example.shield.brief.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record DeliveryStatusRequest(
        @NotBlank(message = "상태는 필수입니다")
        String status,
        String rejectionReason
) {}
