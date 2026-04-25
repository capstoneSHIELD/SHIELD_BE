package org.example.shield.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.IntentClassificationResult;
import org.example.shield.ai.dto.IntentClassificationResult.MatchedNode;
import org.example.shield.ai.dto.ResolvedDomain;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 사용자 선택 도메인과 AI Layer1 분류 결과를 교차 매칭해 활성 도메인을 결정.
 *
 * <p>도입 배경: 사용자는 상담 시작 시 L1/L2/L3 를 다중 선택할 수 있는데, 기존
 * {@code Consultation.getFirstSubDomain()} 은 항상 첫 항목만 사용해서 대화가
 * 다른 선택지로 흘러가도 체크리스트가 따라오지 못하던 한계가 있었다.</p>
 *
 * <p>이 서비스는 매 턴 AI 가 분류한 노드 중 사용자 선택과 교차하는 가장 정밀한
 * 도메인을 골라 체크리스트 anchor 로 쓸 수 있게 해준다.</p>
 *
 * <p>알고리즘:
 * <ol>
 *   <li>aiMatched 를 confidence 내림차순 정렬
 *   <li>각 매칭 노드의 [L1, L2, L3] 조상 한글명을 추출
 *   <li>조상 중 사용자가 선택한 도메인과 가장 깊은 일치를 시도
 *       (L3 일치 &gt; L2 일치 &gt; L1 일치)
 *   <li>전부 실패시 fallback: 사용자 첫 선택값
 * </ol>
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DomainResolver {

    private final OntologyService ontologyService;

    /**
     * @param classification AI Layer1 분류 결과 (null/empty 허용)
     * @param userL1         사용자 선택 L1 한글명 목록 (예: ["손해배상·불법행위"])
     * @param userL2         사용자 선택 L2 한글명 목록 (예: ["교통사고", "인격권 침해"])
     * @param userL3         사용자 선택 L3 한글명 목록 (예: [])
     * @return 활성 도메인. 모든 매칭 실패 시 fallback (userL1[0], userL2[0], userL3[0]).
     */
    public ResolvedDomain resolve(IntentClassificationResult classification,
                                  List<String> userL1,
                                  List<String> userL2,
                                  List<String> userL3) {
        log.debug("DomainResolver 시작: classification={}, userL1={}, userL2={}, userL3={}",
                classification != null ? classification.matchedNodes() : "null",
                userL1, userL2, userL3);

        if (classification != null && classification.matchedNodes() != null
                && !classification.matchedNodes().isEmpty()) {
            // confidence 내림차순으로 정렬해 가장 신뢰도 높은 매칭부터 시도
            List<MatchedNode> sorted = classification.matchedNodes().stream()
                    .sorted(Comparator.comparingDouble(MatchedNode::confidence).reversed())
                    .toList();

            for (MatchedNode node : sorted) {
                String[] ancestors = ontologyService.ancestorNames(node.id());
                String l1 = ancestors[0];
                String l2 = ancestors[1];
                String l3 = ancestors[2];
                log.debug("DomainResolver 시도: nodeId={}, ancestors=[L1={}, L2={}, L3={}]",
                        node.id(), l1, l2, l3);

                // L3 일치: 가장 정밀
                if (l3 != null && containsIgnoreCase(userL3, l3)) {
                    log.debug("DomainResolver: L3 일치 by node {} → ({}, {}, {})", node.id(), l1, l2, l3);
                    return new ResolvedDomain(l1, l2, l3);
                }
                // L2 일치
                if (l2 != null && containsIgnoreCase(userL2, l2)) {
                    log.debug("DomainResolver: L2 일치 by node {} → ({}, {}, null)", node.id(), l1, l2);
                    return new ResolvedDomain(l1, l2, null);
                }
                // L1 일치
                if (l1 != null && containsIgnoreCase(userL1, l1)) {
                    log.debug("DomainResolver: L1 일치 by node {} → ({}, null, null)", node.id(), l1);
                    return new ResolvedDomain(l1, null, null);
                }
            }
        }

        // 모든 매칭 실패 → 사용자 첫 선택으로 폴백
        ResolvedDomain fallback = new ResolvedDomain(
                firstOrNull(userL1),
                firstOrNull(userL2),
                firstOrNull(userL3));
        log.debug("DomainResolver: 매칭 실패 → 사용자 첫 선택 fallback ({}, {}, {})",
                fallback.l1(), fallback.l2(), fallback.l3());
        return fallback;
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null || value == null) return false;
        for (String item : list) {
            if (item != null && item.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private String firstOrNull(List<String> list) {
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }
}
