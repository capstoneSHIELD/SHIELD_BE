-- USER 메시지 저장 시점에 PII 마스킹 결과를 함께 보관하여, 매 sendMessage 호출마다
-- history 의 모든 USER 메시지를 반복 sanitize 하던 비용을 제거한다 (Gemini PR #90 ⑤).
--
-- nullable 로 시작하여 기존 행은 fallback 으로 호출 시점에 sanitize 후 사용한다.
-- 신규 행은 ChatTransactionalBoundary.saveUserMessage 가 즉시 채운다.
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS sanitized_content TEXT NULL;

COMMENT ON COLUMN messages.sanitized_content IS
    'USER 메시지의 PII 마스킹 결과 캐시. NULL 이면 legacy 행 — 호출 시점에 sanitize 후 사용.';
