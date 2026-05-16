package org.example.shield.consultation.application;

import java.util.List;
import java.util.Objects;

/**
 * 온톨로지 기준으로 정규화한 L1/L2/L3 분류 후보.
 */
public record ClassificationCandidate(
        List<String> domains,
        List<String> subDomains,
        List<String> tags
) {
    public ClassificationCandidate {
        domains = normalize(domains);
        subDomains = normalize(subDomains);
        tags = normalize(tags);
    }

    public static ClassificationCandidate empty() {
        return new ClassificationCandidate(List.of(), List.of(), List.of());
    }

    public boolean hasAny() {
        return !domains.isEmpty() || !subDomains.isEmpty() || !tags.isEmpty();
    }

    public String firstDomain() {
        return domains.isEmpty() ? null : domains.get(0);
    }

    public String firstSubDomain() {
        return subDomains.isEmpty() ? null : subDomains.get(0);
    }

    public String firstTag() {
        return tags.isEmpty() ? null : tags.get(0);
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
