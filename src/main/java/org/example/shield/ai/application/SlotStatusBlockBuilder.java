package org.example.shield.ai.application;

import org.example.shield.ai.dto.slot.SlotLedger;
import org.example.shield.ai.dto.slot.SlotStateItem;
import org.example.shield.ai.dto.slot.SlotStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SlotStatusBlockBuilder {

    private static final int COLLECTED_TOKEN_BUDGET = 120;
    private static final int PENDING_TOKEN_BUDGET = 80;
    private static final int MISSING_TOKEN_BUDGET = 120;
    private static final int ASKED_TOKEN_BUDGET = 80;
    private static final int APPROX_CHARS_PER_TOKEN = 4;

    public String build(SlotLedger ledger) {
        if (ledger == null || ledger.getSlots() == null || ledger.getSlots().isEmpty()) {
            return "";
        }

        List<String> sections = new ArrayList<>();
        sections.add(section(
                "=== COLLECTED INFORMATION (DO NOT ASK AGAIN) ===",
                collectedLines(ledger.getSlots()),
                COLLECTED_TOKEN_BUDGET));
        sections.add(section(
                "=== PENDING CONFIRMATION ===",
                pendingLines(ledger.getSlots()),
                PENDING_TOKEN_BUDGET));
        sections.add(section(
                "=== MISSING INFORMATION (TARGET ONLY THESE) ===",
                missingLines(ledger.getSlots()),
                MISSING_TOKEN_BUDGET));
        sections.add(section(
                "=== ALREADY ASKED QUESTIONS (DO NOT REPEAT) ===",
                askedQuestionLines(ledger.getSlots()),
                ASKED_TOKEN_BUDGET));

        List<String> nonEmpty = sections.stream()
                .filter(s -> s != null && !s.isBlank())
                .toList();
        if (nonEmpty.isEmpty()) {
            return "";
        }
        return String.join("\n\n", nonEmpty)
                + "\n\nRULE: Never ask again about collected items. "
                + "Confirm pending items before treating them as collected.";
    }

    private String collectedLines(List<SlotStateItem> slots) {
        List<SlotStateItem> collected = slots.stream()
                .filter(s -> !s.isOutOfScope())
                .filter(s -> s.getStatus() == SlotStatus.COLLECTED)
                .sorted(Comparator
                        .comparing(SlotStateItem::getAnsweredAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparingInt(SlotStateItem::getPriority))
                .toList();
        if (collected.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(collected.size(), 8);
        for (int i = 0; i < limit; i++) {
            SlotStateItem item = collected.get(i);
            sb.append("- ").append(slotRef(item))
                    .append(": ").append(item.displayCollectedValue())
                    .append('\n');
        }
        if (collected.size() > limit) {
            sb.append("- ... ").append(collected.size() - limit).append(" more collected items\n");
        }
        return sb.toString().trim();
    }

    private String pendingLines(List<SlotStateItem> slots) {
        StringBuilder sb = new StringBuilder();
        slots.stream()
                .filter(s -> !s.isOutOfScope())
                .filter(s -> s.getStatus() == SlotStatus.PENDING_CONFIRMATION)
                .sorted(Comparator.comparingInt(SlotStateItem::getPriority))
                .limit(5)
                .forEach(s -> sb.append("- ")
                        .append(slotRef(s))
                        .append(": ")
                        .append(s.getPendingValue() == null || s.getPendingValue().isBlank()
                                ? "pending value not recorded"
                                : s.getPendingValue())
                        .append('\n'));
        return sb.toString().trim();
    }

    private String missingLines(List<SlotStateItem> slots) {
        List<SlotStateItem> missing = slots.stream()
                .filter(s -> !s.isOutOfScope())
                .filter(s -> s.getStatus() == SlotStatus.MISSING)
                .sorted(Comparator
                        .comparing((SlotStateItem s) -> !s.isRequired())
                        .thenComparingInt(SlotStateItem::getPriority))
                .limit(8)
                .toList();
        if (missing.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (SlotStateItem item : missing) {
            sb.append("- ").append(slotRef(item))
                    .append(" (priority: ")
                    .append(item.getPriority())
                    .append(item.isRequired() ? ", required" : ", optional")
                    .append(")\n");
        }
        return sb.toString().trim();
    }

    private String askedQuestionLines(List<SlotStateItem> slots) {
        Set<String> questions = new LinkedHashSet<>();
        for (int i = slots.size() - 1; i >= 0 && questions.size() < 5; i--) {
            SlotStateItem slot = slots.get(i);
            if (slot.isOutOfScope()) {
                continue;
            }
            List<String> asked = slot.getAskedQuestions();
            if (asked == null || asked.isEmpty()) {
                continue;
            }
            for (int j = asked.size() - 1; j >= 0 && questions.size() < 5; j--) {
                String question = asked.get(j);
                if (question != null && !question.isBlank()) {
                    questions.add(question.trim().replaceAll("\\s+", " "));
                }
            }
        }
        if (questions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        questions.stream()
                .limit(5)
                .forEach(q -> sb.append("- ").append(q).append('\n'));
        return sb.toString().trim();
    }

    private String section(String title, String body, int tokenBudget) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return title + "\n" + truncate(body, tokenBudget);
    }

    private String slotRef(SlotStateItem item) {
        if (item == null) {
            return "(unknown slot)";
        }
        String label = item.getLabel() == null || item.getLabel().isBlank()
                ? "(unnamed slot)"
                : item.getLabel();
        String slotId = item.getSlotId();
        if (slotId == null || slotId.isBlank()) {
            return label;
        }
        return "[" + slotId + "] " + label;
    }

    private String truncate(String text, int tokenBudget) {
        int charBudget = Math.max(0, tokenBudget) * APPROX_CHARS_PER_TOKEN;
        if (charBudget == 0 || text.length() <= charBudget) {
            return text;
        }
        return text.substring(0, Math.max(0, charBudget - 3)).stripTrailing() + "...";
    }
}
