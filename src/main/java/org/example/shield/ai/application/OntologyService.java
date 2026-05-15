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

    /** 노드 name → 루트 L1 부터 해당 노드까지의 경로. 예: [손해배상·불법행위, 의료사고, 진료 과실 및 설명의무]. */
    private Map<String, List<String>> pathByName = Map.of();

    /** 노드 name → 직계 부모 name. L1 노드는 루트 "법률" 대신 null 로 취급한다. */
    private Map<String, String> parentByName = Map.of();

    public OntologyService(@Qualifier("slimOntologyJson") String slimOntologyJson,
                           ObjectMapper objectMapper) {
        this.slimOntologyJson = slimOntologyJson;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadOntology() {
        try {
            JsonNode root = objectMapper.readTree(slimOntologyJson);
            Map<String, List<String>> childrenMap = new HashMap<>();
            Map<String, List<String>> pathMap = new HashMap<>();
            Map<String, String> parentMap = new HashMap<>();
            walk(root, childrenMap, pathMap, parentMap, List.of());
            this.childrenByParentName = Map.copyOf(childrenMap);
            this.pathByName = Map.copyOf(pathMap);
            this.parentByName = Map.copyOf(parentMap);
            log.info("온톨로지 로드 완료: {}개 부모 노드에 자식 인덱싱", childrenByParentName.size());
        } catch (Exception e) {
            throw new IllegalStateException("온톨로지 JSON 파싱 실패", e);
        }
    }

    private void walk(JsonNode node,
                      Map<String, List<String>> childrenMap,
                      Map<String, List<String>> pathMap,
                      Map<String, String> parentMap,
                      List<String> parentPath) {
        String currentName = node.path("name").asText(null);
        boolean isRoot = currentName != null && "법률".equals(currentName);
        List<String> currentPath = parentPath;

        if (currentName != null && !isRoot) {
            currentPath = new ArrayList<>(parentPath);
            currentPath.add(currentName);
            pathMap.putIfAbsent(currentName, List.copyOf(currentPath));
            if (parentPath.size() >= 1 && parentPath.get(parentPath.size() - 1) != null) {
                parentMap.putIfAbsent(currentName, parentPath.get(parentPath.size() - 1));
            }
        }

        if (!node.hasNonNull("c")) return;
        List<String> childNames = new ArrayList<>();
        for (JsonNode child : node.path("c")) {
            String childName = child.path("name").asText(null);
            if (childName != null) childNames.add(childName);
            walk(child, childrenMap, pathMap, parentMap, currentPath);
        }
        if (currentName != null && !childNames.isEmpty()) {
            childrenMap.put(currentName, List.copyOf(childNames));
        }
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
     * 노드 이름의 온톨로지 경로를 반환한다.
     *
     * <p>예: {@code "진료 과실 및 설명의무"} →
     * {@code ["손해배상·불법행위", "의료사고", "진료 과실 및 설명의무"]}.</p>
     */
    public List<String> pathOf(String nodeName) {
        if (nodeName == null) return List.of();
        return pathByName.getOrDefault(nodeName, List.of());
    }

    /**
     * 노드 이름의 직계 부모를 반환한다. L1 또는 미등록 노드는 null.
     */
    public String parentOf(String nodeName) {
        if (nodeName == null) return null;
        return parentByName.get(nodeName);
    }

    public boolean contains(String nodeName) {
        return nodeName != null && pathByName.containsKey(nodeName);
    }
}
