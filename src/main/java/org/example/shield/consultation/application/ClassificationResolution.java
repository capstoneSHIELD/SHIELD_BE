package org.example.shield.consultation.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 사용자 사전 선택과 AI 분류를 비교한 결과.
 *
 * @param conflict           두 후보가 온톨로지상 호환되지 않아 의뢰인 확인이 필요한지
 * @param userCandidate      사용자 선택에서 복원한 후보
 * @param aiCandidate        AI 응답에서 복원한 후보
 * @param effectiveCandidate 충돌이 없을 때 서버가 후속 처리에 사용할 후보
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClassificationResolution(
        boolean conflict,
        ClassificationCandidate userCandidate,
        ClassificationCandidate aiCandidate,
        ClassificationCandidate effectiveCandidate
) {
}
