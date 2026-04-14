package org.example.shield.brief.application;

public record DeliveryStatusEvent(
        String clientEmail,
        String clientName,
        String lawyerName,
        String briefTitle,
        String status,
        String rejectionReason
) {}
