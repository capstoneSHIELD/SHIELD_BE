package org.example.shield.admin.application.event;

import java.util.UUID;

/**
 * 변호사 verification 이 {@code VERIFIED} 로 전환되는 트랜잭션이 커밋된 직후 발행되는 이벤트.
 *
 * <p>외부 AI API 호출(매칭 임베딩 업서트) 을 verification 트랜잭션 밖으로 분리하기 위한
 * 도메인 이벤트이다. {@link org.springframework.transaction.event.TransactionalEventListener}
 * 가 {@code AFTER_COMMIT} 에서 수신하여 후속 작업을 수행한다. (Gemini PR #90 리뷰 ④ 대응)</p>
 */
public record LawyerVerifiedEvent(UUID lawyerId) {
}
