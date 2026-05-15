package org.example.shield.consultation.application;

import lombok.RequiredArgsConstructor;
import org.example.shield.ai.application.OntologyService;
import org.example.shield.consultation.domain.Consultation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자 선택과 AI 분류를 온톨로지 canonical path 로 정규화하고 충돌 여부를 계산한다.
 */
@Service
@RequiredArgsConstructor
public class ClassificationResolver {

    private final OntologyService ontologyService;

    public ClassificationResolution resolve(Consultation consultation) {
        ClassificationCandidate user = canonicalize(
                consultation.getUserDomains(),
                consultation.getUserSubDomains(),
                consultation.getUserTags());
        ClassificationCandidate ai = canonicalizeStrict(
                consultation.getAiDomains(),
                consultation.getAiSubDomains(),
                consultation.getAiTags());

        boolean conflict = user.hasAny() && ai.hasAny() && !areCompatible(user, ai);
        ClassificationCandidate effective = null;
        if (!conflict) {
            effective = ai.hasAny() ? ai : (user.hasAny() ? user : null);
        }
        return new ClassificationResolution(conflict, nonEmptyOrNull(user), nonEmptyOrNull(ai), effective);
    }

    /**
     * 대화 수집/RAG 에 사용할 후보. 최종 확정 전이라도 AI 후보가 있으면 실제 사건 기준으로 대화를 이어간다.
     */
    public ClassificationCandidate candidateForCollection(Consultation consultation) {
        ClassificationCandidate ai = canonicalizeStrict(
                consultation.getAiDomains(),
                consultation.getAiSubDomains(),
                consultation.getAiTags());
        if (ai.hasAny()) return ai;
        return canonicalize(
                consultation.getUserDomains(),
                consultation.getUserSubDomains(),
                consultation.getUserTags());
    }

    public ClassificationCandidate canonicalize(List<String> domains, List<String> subDomains, List<String> tags) {
        return canonicalize(domains, subDomains, tags, true);
    }

    public ClassificationCandidate canonicalizeStrict(List<String> domains, List<String> subDomains, List<String> tags) {
        return canonicalize(domains, subDomains, tags, false);
    }

    private ClassificationCandidate canonicalize(List<String> domains, List<String> subDomains,
                                                 List<String> tags, boolean allowUnknownFallback) {
        List<String> cleanTags = clean(tags);
        ClassificationCandidate byTag = fromDeepestKnown(cleanTags, 3);
        if (byTag.hasAny()) return byTag;

        List<String> cleanSubs = clean(subDomains);
        ClassificationCandidate bySub = fromDeepestKnown(cleanSubs, 2);
        if (bySub.hasAny()) return bySub;

        List<String> cleanDomains = clean(domains);
        ClassificationCandidate byDomain = fromDeepestKnown(cleanDomains, 1);
        if (byDomain.hasAny()) return byDomain;

        if (!allowUnknownFallback) return ClassificationCandidate.empty();
        return new ClassificationCandidate(cleanDomains, cleanSubs, cleanTags);
    }

    private ClassificationCandidate fromDeepestKnown(List<String> names, int expectedDepth) {
        if (names.isEmpty()) return ClassificationCandidate.empty();

        for (String name : names) {
            List<String> path = ontologyService.pathOf(name);
            if (path.size() < expectedDepth) continue;

            String domain = path.get(0);
            String subDomain = path.size() >= 2 ? path.get(1) : null;
            List<String> tags = new ArrayList<>();
            List<String> subDomains = new ArrayList<>();
            List<String> domains = new ArrayList<>();

            domains.add(domain);
            if (subDomain != null) subDomains.add(subDomain);

            if (expectedDepth >= 3) {
                for (String tag : names) {
                    List<String> tagPath = ontologyService.pathOf(tag);
                    if (tagPath.size() >= 3
                            && domain.equals(tagPath.get(0))
                            && subDomain.equals(tagPath.get(1))) {
                        tags.add(tagPath.get(2));
                    }
                }
            } else if (expectedDepth == 2) {
                for (String sub : names) {
                    List<String> subPath = ontologyService.pathOf(sub);
                    if (subPath.size() >= 2 && domain.equals(subPath.get(0))) {
                        subDomains.add(subPath.get(1));
                    }
                }
            } else {
                for (String maybeDomain : names) {
                    List<String> domainPath = ontologyService.pathOf(maybeDomain);
                    if (domainPath.size() == 1) {
                        domains.add(domainPath.get(0));
                    }
                }
            }
            return new ClassificationCandidate(domains, subDomains, tags);
        }
        return ClassificationCandidate.empty();
    }

    private boolean areCompatible(ClassificationCandidate user, ClassificationCandidate ai) {
        if (!user.tags().isEmpty() && !ai.tags().isEmpty()) {
            return intersects(user.tags(), ai.tags());
        }
        if (!user.subDomains().isEmpty() && !ai.subDomains().isEmpty()) {
            return intersects(user.subDomains(), ai.subDomains());
        }
        if (!user.domains().isEmpty() && !ai.domains().isEmpty()) {
            return intersects(user.domains(), ai.domains());
        }
        return true;
    }

    private static ClassificationCandidate nonEmptyOrNull(ClassificationCandidate candidate) {
        return candidate != null && candidate.hasAny() ? candidate : null;
    }

    private static boolean intersects(List<String> left, List<String> right) {
        for (String value : left) {
            if (right.contains(value)) return true;
        }
        return false;
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
