package org.example.shield.ai.application;

import org.example.shield.ai.dto.IntentShadowEvalSummary;
import org.example.shield.ai.dto.LegalAdviceLabelRecord;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IntentShadowEvalAggregator {

    public IntentShadowEvalSummary summarize(List<LegalAdviceLabelRecord> labels) {
        if (labels == null || labels.isEmpty()) {
            return IntentShadowEvalSummary.empty();
        }
        int total = 0;
        int correct = 0;
        int leaks = 0;
        int skipFalsePositive = 0;
        for (LegalAdviceLabelRecord label : labels) {
            if (label == null) {
                continue;
            }
            total++;
            if (label.expectedIntent() != null && label.expectedIntent() == label.actualIntent()) {
                correct++;
            }
            if (label.highRiskLeak()) {
                leaks++;
            }
            if (label.skipFalsePositive()) {
                skipFalsePositive++;
            }
        }
        if (total == 0) {
            return IntentShadowEvalSummary.empty();
        }
        return new IntentShadowEvalSummary(
                total,
                total,
                correct,
                leaks,
                skipFalsePositive,
                correct / (double) total,
                skipFalsePositive / (double) total
        );
    }
}
