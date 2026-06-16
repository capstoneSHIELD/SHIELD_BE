package org.example.shield.experiment.controller;

import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.CorpusLoadRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.CorpusLoadResponse;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.MatchRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.MatchResponse;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.PreflightRequest;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchDtos.PreflightResponse;
import org.example.shield.experiment.lawyermatch.ExperimentLawyerMatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 변호사 매칭 benchmark runner 전용 내부 adapter.
 *
 * <p>운영 공개 API가 아니며 {@code local/test} profile에서만 등록된다.
 * 운영 변호사 DB를 사용하지 않고 runner가 업로드한 synthetic corpus만 조회한다.</p>
 */
@RestController
@ConditionalOnProperty(prefix = "shield.experiment.adapter", name = "enabled", havingValue = "true")
@RequestMapping("/internal/experiments/lawyer-match")
public class ExperimentLawyerMatchController {

    private final ExperimentLawyerMatchService lawyerMatchService;
    private final ExperimentAdapterAccessGuard accessGuard;

    public ExperimentLawyerMatchController(
            ExperimentLawyerMatchService lawyerMatchService,
            ExperimentAdapterAccessGuard accessGuard
    ) {
        this.lawyerMatchService = lawyerMatchService;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/corpus")
    public CorpusLoadResponse loadCorpus(
            @RequestHeader(name = ExperimentAdapterAccessGuard.HEADER_NAME, required = false) String accessToken,
            @RequestBody(required = false) CorpusLoadRequest request
    ) {
        accessGuard.verify(accessToken);
        return lawyerMatchService.loadCorpus(request);
    }

    @PostMapping("/preflight")
    public PreflightResponse preflight(
            @RequestHeader(name = ExperimentAdapterAccessGuard.HEADER_NAME, required = false) String accessToken,
            @RequestBody(required = false) PreflightRequest request
    ) {
        accessGuard.verify(accessToken);
        return lawyerMatchService.preflight(request);
    }

    @PostMapping
    public MatchResponse match(
            @RequestHeader(name = ExperimentAdapterAccessGuard.HEADER_NAME, required = false) String accessToken,
            @RequestBody MatchRequest request
    ) {
        accessGuard.verify(accessToken);
        return lawyerMatchService.match(request);
    }
}
