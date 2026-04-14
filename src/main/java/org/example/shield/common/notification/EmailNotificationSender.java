package org.example.shield.common.notification;

/**
 * TODO [Issue #16] Gmail SMTP 이메일 발송 구현
 *
 * 1. 클래스 설정
 *    - @Component + @RequiredArgsConstructor
 *    - JavaMailSender 주입
 *
 * 2. onDeliveryStatusChanged(DeliveryStatusEvent event)
 *    - @Async("emailExecutor")
 *    - @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *    - 수락: "{lawyerName} 변호사가 귀하의 의뢰서를 수락하였습니다."
 *    - 거절: "{lawyerName} 변호사가 귀하의 의뢰서를 거절하였습니다. 사유: {reason}"
 *    - 발송 실패 시 log.error만 남기고 예외 전파하지 않음
 *
 * 3. 의존성 추가 필요
 *    - build.gradle: spring-boot-starter-mail
 *    - application.yml: spring.mail.host/port/username/password
 *    - .env: GMAIL_USERNAME, GMAIL_APP_PASSWORD
 *
 * 4. AsyncConfig 신규 생성 (common/config/)
 *    - @Configuration + @EnableAsync
 *    - emailExecutor Bean: corePoolSize=2, maxPoolSize=5, queueCapacity=50
 *
 * 5. DeliveryStatusEvent 신규 생성 (brief/application/)
 *    - record: clientEmail, clientName, lawyerName, briefTitle, status, reason
 */
public class EmailNotificationSender implements NotificationSender {

    @Override
    public void send(String to, String subject, String content) {
        // TODO: JavaMailSender를 이용한 이메일 발송 구현
    }
}
