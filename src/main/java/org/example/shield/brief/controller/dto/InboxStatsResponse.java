package org.example.shield.brief.controller.dto;

public record InboxStatsResponse(
        long all,
        long newCount,
        long reviewing,
        long confirmed,
        long rejected,
        long responded
) {}
