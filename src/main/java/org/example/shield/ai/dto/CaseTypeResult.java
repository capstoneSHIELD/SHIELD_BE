package org.example.shield.ai.dto;

public record CaseTypeResult(
        String l1,
        String l2,
        String l3,
        double confidence
) {
    public static CaseTypeResult empty() {
        return new CaseTypeResult(null, null, null, 0.0);
    }
}
