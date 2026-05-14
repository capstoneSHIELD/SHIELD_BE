package org.example.shield.admin.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.lawyer.application.LawyerEmbeddingService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link LawyerVerifiedEvent} 를 verification 트랜잭션 커밋 직후 처리.
 *
 * <p>임베딩 업서트는 Cohere 외부 API 호출(분류·embed) 을 동반하여 수 초~수 십 초가 걸릴 수 있다.
 * 트랜잭션 안에서 호출하면 그 시간만큼 HikariCP 커넥션을 점유해 풀 고갈 위험이 있으므로
 * {@link TransactionPhase#AFTER_COMMIT} 으로 분리한다. 임베딩 실패는 verification 자체에
 * 영향을 주지 않으며, 로그로 추적해 별도 재처리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LawyerVerifiedEventListener {

    private final LawyerEmbeddingService lawyerEmbeddingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLawyerVerified(LawyerVerifiedEvent event) {
        try {
            lawyerEmbeddingService.upsertEmbedding(event.lawyerId());
        } catch (Exception ex) {
            log.warn("변호사 임베딩 생성 실패 (verification 은 이미 커밋됨) lawyerId={} error={}",
                    event.lawyerId(), ex.getMessage());
        }
    }
}
