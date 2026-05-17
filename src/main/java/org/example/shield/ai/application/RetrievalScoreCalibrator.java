package org.example.shield.ai.application;

import org.example.shield.ai.dto.RetrievalScoreCalibrationResult;
import org.example.shield.ai.dto.RetrievalScoreMethod;
import org.example.shield.ai.dto.RetrievalScoreObservation;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class RetrievalScoreCalibrator {

    public RetrievalScoreCalibrationResult calibrate(
            List<RetrievalScoreObservation> observations,
            RetrievalScoreMethod method,
            double maxFalseDropRate
    ) {
        RetrievalScoreMethod safeMethod = method == null ? RetrievalScoreMethod.WEIGHTED : method;
        List<RetrievalScoreObservation> scoped = (observations == null ? List.<RetrievalScoreObservation>of() : observations)
                .stream()
                .filter(observation -> observation != null && observation.method() == safeMethod)
                .sorted(Comparator.comparingDouble(RetrievalScoreObservation::score))
                .toList();

        int relevantTotal = (int) scoped.stream().filter(RetrievalScoreObservation::relevant).count();
        if (scoped.isEmpty() || relevantTotal == 0) {
            return new RetrievalScoreCalibrationResult(safeMethod, 0.0d, 0.0d, scoped.size(), relevantTotal, 0);
        }

        double allowedFalseDropRate = Math.max(0.0d, Math.min(1.0d, maxFalseDropRate));
        double selectedThreshold = 0.0d;
        int selectedFalseDropped = 0;
        double selectedFalseDropRate = 0.0d;

        List<Double> candidates = scoped.stream()
                .map(RetrievalScoreObservation::score)
                .distinct()
                .toList();

        for (double threshold : candidates) {
            int falseDropped = (int) scoped.stream()
                    .filter(RetrievalScoreObservation::relevant)
                    .filter(observation -> observation.score() < threshold)
                    .count();
            double falseDropRate = falseDropped / (double) relevantTotal;
            if (falseDropRate <= allowedFalseDropRate) {
                selectedThreshold = threshold;
                selectedFalseDropped = falseDropped;
                selectedFalseDropRate = falseDropRate;
            }
        }

        return new RetrievalScoreCalibrationResult(
                safeMethod,
                selectedThreshold,
                selectedFalseDropRate,
                scoped.size(),
                relevantTotal,
                selectedFalseDropped
        );
    }
}
