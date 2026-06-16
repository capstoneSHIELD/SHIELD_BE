package org.example.shield.experiment.controller;

import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.CorpusLoadRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.CorpusLoadResponse;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.MatchRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.MatchResponse;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.PreflightRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.PreflightResponse;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 변호사 매칭 benchmark runner 전용 내부 adapter.
 *
 * <p>운영 공개 API가 아니며 {@code local/test} profile에서만 등록된다.
 * 운영 변호사 DB를 사용하지 않고 runner가 업로드한 synthetic corpus만 조회한다.</p>
 */
@RestController
@Profile({"local", "test"})
@RequestMapping("/internal/experiments/lawyer-match")
public class ExperimentLawyerMatchController {

    private final ExperimentLawyerMatchService lawyerMatchService;

    public ExperimentLawyerMatchController(ExperimentLawyerMatchService lawyerMatchService) {
        this.lawyerMatchService = lawyerMatchService;
    }

    @PostMapping("/corpus")
    public CorpusLoadResponse loadCorpus(@RequestBody(required = false) CorpusLoadRequest request) {
        return lawyerMatchService.loadCorpus(request);
    }

    @PostMapping("/preflight")
    public PreflightResponse preflight(@RequestBody(required = false) PreflightRequest request) {
        return lawyerMatchService.preflight(request);
    }

    @PostMapping
    public MatchResponse match(@RequestBody MatchRequest request) {
        return lawyerMatchService.match(request);
    }
}
