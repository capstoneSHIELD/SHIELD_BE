package org.example.shield.ai.application;

import org.example.shield.ai.dto.RetrievalScoreCalibrationResult;
import org.example.shield.ai.dto.RetrievalScoreMethod;
import org.example.shield.ai.dto.RetrievalScoreObservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalScoreCalibratorTest {

    private final RetrievalScoreCalibrator calibrator = new RetrievalScoreCalibrator();

    @Test
    @DisplayName("calibrator chooses the highest threshold within false drop budget")
    void calibrate_selectsThresholdWithinBudget() {
        RetrievalScoreCalibrationResult result = calibrator.calibrate(List.of(
                new RetrievalScoreObservation("q1", RetrievalScoreMethod.WEIGHTED, 0.10, false),
                new RetrievalScoreObservation("q2", RetrievalScoreMethod.WEIGHTED, 0.20, false),
                new RetrievalScoreObservation("q3", RetrievalScoreMethod.WEIGHTED, 0.30, true),
                new RetrievalScoreObservation("q4", RetrievalScoreMethod.WEIGHTED, 0.50, true),
                new RetrievalScoreObservation("q5", RetrievalScoreMethod.RRF, 0.01, true)
        ), RetrievalScoreMethod.WEIGHTED, 0.0);

        assertThat(result.method()).isEqualTo(RetrievalScoreMethod.WEIGHTED);
        assertThat(result.threshold()).isEqualTo(0.30);
        assertThat(result.falseDropRate()).isEqualTo(0.0);
        assertThat(result.totalObservations()).isEqualTo(4);
        assertThat(result.relevantObservations()).isEqualTo(2);
    }
}
