package org.example.shield.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 법률 온톨로지 트리 조회 서비스 (Issue #48).
 *
 * <p>{@code legal-ontology-slim.json} 을 파싱해 부모 → 직계 자식 name 리스트를
 * 메모리에 보유한다. 앱 기동 시 1회만 구축하고 이후 읽기 전용.</p>
 *
 * <p>용도:
 * <ul>
 *   <li>AI 분류 결과(L2/L3) 구조 검증 — {@link #isChildOf(String, String)}
 *   <li>사용자 선택 L1 의 허용 자식 목록 조회 — {@link #childrenOf(String)}
 *   <li>노드 ID 로 한글명·부모ID·레벨 조회 — {@link #findNode(String)}
 *   <li>L3/L2 노드의 조상(부모/조부모) 한글명 조회 — {@link #ancestorNames(String)}
 * </ul>
 * </p>
 *
 * <p>기존 {@code slimOntologyJson} Bean(OntologyConfig) 을 재사용한다.</p>
 */
@Service
@Slf4j
public class OntologyService {

    private final String slimOntologyJson;
    private final ObjectMapper objectMapper;

    /** 부모 name → 직계 자식 name 리스트 (불변). */
    private Map<String, List<String>> childrenByParentName = Map.of();

    /** 노드 id → 노드 메타 (id, name, parentId, level). */
    private Map<String, NodeMeta> nodeMetaById = Map.of();

    /**
     * 온톨로지 노드 메타.
     *
     * @param id       노드 ID (예: "law-005-04-01")
     * @param name     한글명 (예: "명예훼손 및 모욕")
     * @param parentId 부모 노드 ID. 루트는 null
     * @param level    1=L1(대분류), 2=L2(중분류), 3=L3(소분류). 루트는 0
     */
    public record NodeMeta(String id, String name, String parentId, int level) {}

    public OntologyService(@Qualifier("slimOntologyJson") String slimOntologyJson,
                           ObjectMapper objectMapper) {
        this.slimOntologyJson = slimOntologyJson;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadOntology() {
        try {
            JsonNode root = objectMapper.readTree(slimOntologyJson);
            Map<String, List<String>> nameTree = new HashMap<>();
            Map<String, NodeMeta> idIndex = new HashMap<>();
            walk(root, null, nameTree, idIndex);
            this.childrenByParentName = Map.copyOf(nameTree);
            this.nodeMetaById = Map.copyOf(idIndex);
            log.info("온톨로지 로드 완료: 부모-자식 인덱스 {}건, ID 인덱스 {}건",
                    childrenByParentName.size(), nodeMetaById.size());
        } catch (Exception e) {
            throw new IllegalStateException("온톨로지 JSON 파싱 실패", e);
        }
    }

    private void walk(JsonNode node, String parentId,
                      Map<String, List<String>> nameTree,
                      Map<String, NodeMeta> idIndex) {
        String id = node.path("id").asText(null);
        String name = node.path("name").asText(null);

        if (id != null && name != null) {
            int level = computeLevel(id);
            idIndex.put(id, new NodeMeta(id, name, parentId, level));
        }

        if (!node.hasNonNull("c")) return;

        List<String> childNames = new ArrayList<>();
        for (JsonNode child : node.path("c")) {
            String childName = child.path("name").asText(null);
            if (childName != null) childNames.add(childName);
            walk(child, id, nameTree, idIndex);
        }
        if (name != null && !childNames.isEmpty()) {
            nameTree.put(name, List.copyOf(childNames));
        }
    }

    /**
     * "law-001" → 1, "law-001-02" → 2, "law-001-02-03" → 3. 루트("law-000")는 0.
     *
     * <p>주의: 첫 hyphen 은 prefix 구분자(law-) 이므로 dash 개수가 곧 레벨이 된다.
     * 즉 dash 1개("law-001") = L1, dash 2개("law-001-02") = L2 …</p>
     */
    private int computeLevel(String id) {
        if (id == null || "law-000".equals(id)) return 0;
        int dashes = 0;
        for (int i = 0; i < id.length(); i++) {
            if (id.charAt(i) == '-') dashes++;
        }
        return dashes;
    }

    /**
     * {@code childName} 이 {@code parentName} 의 직계 자식인지 검증.
     * 손자 이상 관계는 false.
     */
    public boolean isChildOf(String childName, String parentName) {
        if (childName == null || parentName == null) return false;
        List<String> children = childrenByParentName.get(parentName);
        return children != null && children.contains(childName);
    }

    /**
     * 부모 노드의 직계 자식 name 목록. 없으면 빈 리스트.
     */
    public List<String> childrenOf(String parentName) {
        return childrenByParentName.getOrDefault(parentName, List.of());
    }

    /**
     * 노드 ID 로 메타 조회. 없으면 null.
     */
    public NodeMeta findNode(String nodeId) {
        return nodeMetaById.get(nodeId);
    }

    /**
     * 주어진 노드의 [L1, L2, L3] 한글명 배열을 반환. 해당 레벨이 없으면 그 자리는 null.
     *
     * <p>예시:
     * <ul>
     *   <li>L3 노드 "law-005-04-01" → ["손해배상·불법행위", "인격권 침해", "명예훼손 및 모욕"]
     *   <li>L2 노드 "law-005-04"   → ["손해배상·불법행위", "인격권 침해", null]
     *   <li>L1 노드 "law-005"      → ["손해배상·불법행위", null, null]
     *   <li>알 수 없는 노드          → [null, null, null]
     * </ul>
     * </p>
     */
    public String[] ancestorNames(String nodeId) {
        String[] names = new String[3]; // [L1, L2, L3]
        NodeMeta meta = nodeMetaById.get(nodeId);
        while (meta != null) {
            int idx = meta.level() - 1;
            if (idx >= 0 && idx < 3) {
                names[idx] = meta.name();
            }
            meta = meta.parentId() != null ? nodeMetaById.get(meta.parentId()) : null;
        }
        return names;
    }
}
