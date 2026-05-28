package org.example.shield.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.dto.checklist.ChecklistScope;
import org.example.shield.ai.dto.checklist.ChecklistScopeItem;
import org.example.shield.ai.dto.slot.SlotValueType;
import org.example.shield.common.enums.MessageRole;
import org.example.shield.consultation.domain.Message;
import org.example.shield.consultation.domain.MessageReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChecklistCoverageService {

    private final MessageReader messageReader;
    private final ChecklistScopeResolver checklistScopeResolver;

    @Value("${shield.consultation.completion.coverage-threshold:0.5}")
    private double coverageThreshold;

    private static final Pattern TIME_EXPRESSION_PATTERN = Pattern.compile(
            "(\\d+\\s*(\\uB144|\\uC6D4|\\uC77C|\\uAC1C\\uC6D4|\\uC8FC|\\uC2DC\\uAC04)"
                    + "|\\uC791\\uB144|\\uC62C\\uD574|\\uCD5C\\uADFC"
                    + "|\\d{4}\\s*[-./\\uB144]\\s*\\d{1,2})"
    );

    public double compute(UUID consultationId, String l1Name) {
        return compute(consultationId, l1Name, null, null);
    }

    public double compute(UUID consultationId, String l1Name, String l2Name, String l3Name) {
        if (l1Name == null || l1Name.isBlank()) {
            return 0.0;
        }

        ChecklistScope scope = checklistScopeResolver.resolve(l1Name, l2Name, l3Name);
        if (scope == null || !scope.hasItems()) {
            return 0.0;
        }

        String haystack = loadUserHaystack(consultationId);
        if (haystack.isBlank()) {
            return 0.0;
        }

        int matched = 0;
        for (ChecklistScopeItem item : scope.items()) {
            if (matchesItem(item.label(), haystack)) {
                matched++;
            }
        }

        double ratio = (double) matched / scope.items().size();
        log.debug("Checklist coverage: L1={}, L2={}, L3={}, matched={}/{}, ratio={}",
                l1Name, l2Name, l3Name, matched, scope.items().size(), ratio);
        return ratio;
    }

    public boolean isEffectivelyCompleted(boolean llmAllCompleted, double coverageRatio) {
        return llmAllCompleted && coverageRatio >= coverageThreshold;
    }

    public double getThreshold() {
        return coverageThreshold;
    }

    public List<CoverageItem> buildCoverageItems(
            String l1Name, String l2Name, String l3Name, List<Message> chatHistory) {
        if (l1Name == null || l1Name.isBlank()) {
            return List.of();
        }
        ChecklistScope scope = checklistScopeResolver.resolve(l1Name, l2Name, l3Name);
        if (scope == null || !scope.hasItems()) {
            return List.of();
        }

        String haystack = buildHaystackFromHistory(chatHistory);
        List<CoverageItem> coverage = new ArrayList<>();
        for (ChecklistScopeItem item : scope.items()) {
            coverage.add(new CoverageItem(
                    item.slotId(),
                    item.label(),
                    item.required(),
                    item.valueType(),
                    item.sourcePath(),
                    item.nodeId(),
                    matchesItem(item.label(), haystack)));
        }
        return coverage;
    }

    public record CoverageItem(
            String slotId,
            String label,
            boolean required,
            SlotValueType valueType,
            String sourcePath,
            String nodeId,
            boolean collected
    ) {
        public CoverageItem(String label, boolean collected) {
            this(null, label, true, SlotValueType.TEXT, null, null, collected);
        }
    }

    public String buildCollectedSummary(
            String l1Name, String l2Name, String l3Name, List<Message> chatHistory) {
        if (l1Name == null || l1Name.isBlank()) {
            return "";
        }
        ChecklistScope scope = checklistScopeResolver.resolve(l1Name, l2Name, l3Name);
        if (scope == null || !scope.hasItems()) {
            return "";
        }

        String haystack = buildHaystackFromHistory(chatHistory);
        StringBuilder sb = new StringBuilder("## 이미 수집된 항목 (재질문 금지)\n");
        int matched = 0;
        int firstUncheckedIdx = -1;
        for (int i = 0; i < scope.items().size(); i++) {
            String label = scope.items().get(i).label();
            boolean hit = matchesItem(label, haystack);
            sb.append(hit ? "- [x] " : "- [ ] ").append(label).append('\n');
            if (hit) {
                matched++;
            } else if (firstUncheckedIdx < 0) {
                firstUncheckedIdx = i;
            }
        }
        sb.append('\n');
        sb.append("위 `[x]` 항목은 이미 답변됐습니다. 같은 정보를 다시 묻지 마세요. ");
        if (firstUncheckedIdx >= 0) {
            sb.append("`[ ]` 항목 중 가장 중요한 것 하나만 다음 질문으로 던지세요.");
        } else {
            sb.append("모든 항목이 수집됐으니 마무리 단계로 전환하세요.");
        }
        log.debug("Collected summary: matched={}/{}, L1={}, L2={}, L3={}",
                matched, scope.items().size(), l1Name, l2Name, l3Name);
        return sb.toString();
    }

    public String buildMissingSlotsGuidance(
            String l1Name, String l2Name, String l3Name, List<Message> chatHistory) {
        if (l1Name == null || l1Name.isBlank()) {
            return "";
        }
        ChecklistScope scope = checklistScopeResolver.resolve(l1Name, l2Name, l3Name);
        if (scope == null || !scope.hasItems()) {
            return "";
        }

        String haystack = buildHaystackFromHistory(chatHistory);
        List<String> missing = new ArrayList<>();
        for (ChecklistScopeItem item : scope.items()) {
            if (!matchesItem(item.label(), haystack)) {
                missing.add(item.label());
            }
        }
        if (missing.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("## 미수집 슬롯 (대화 내용으로 추론 필요)\n");
        for (String item : missing) {
            sb.append("- ").append(item).append('\n');
        }
        sb.append('\n');
        sb.append("위 항목은 체크리스트 필수 슬롯이지만 대화에서 명시적으로 답변되지 않았습니다. ");
        sb.append("대화 문맥에 근거가 있다면 합리적으로 추론해 채우고, 근거가 불충분하면 의뢰서 본문과 keyIssues에서 제외하세요. ");
        sb.append("근거 없이 새 사실을 만들어내지 마세요.");
        return sb.toString();
    }

    private boolean matchesItem(String item, String haystack) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        Set<String> tokens = ChecklistTokenizer.tokensOf(item);
        if (ChecklistTokenizer.anyTokenMatches(tokens, haystack)) {
            return true;
        }
        return isTimeSlot(item) && TIME_EXPRESSION_PATTERN.matcher(haystack).find();
    }

    private boolean isTimeSlot(String item) {
        if (item == null || item.isBlank()) {
            return false;
        }
        String normalized = ChecklistTokenizer.normalizeForMatch(item);
        return normalized.contains("\uC2DC\uAE30")
                || normalized.contains("\uC77C\uC2DC")
                || normalized.contains("\uC2DC\uC810")
                || normalized.contains("\uACBD\uACFC")
                || normalized.contains("\uAE30\uAC04")
                || normalized.contains("\uC885\uB8CC")
                || normalized.contains("\uB9CC\uB8CC");
    }

    private String loadUserHaystack(UUID consultationId) {
        List<Message> messages = messageReader.findAllByConsultationId(consultationId);
        return buildHaystackFromHistory(messages);
    }

    private String buildHaystackFromHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            if (message.getRole() == MessageRole.USER && message.getContent() != null) {
                sb.append(' ').append(message.getContent());
            }
        }
        return ChecklistTokenizer.normalizeForMatch(sb.toString());
    }
}
