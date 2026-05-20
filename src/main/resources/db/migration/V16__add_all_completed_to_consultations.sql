-- V16: consultations.all_completed 컬럼 추가 (Issue #100)
--
-- MessageService.evaluateAllCompletedGate 의 결과를 영구 저장하여
-- 페이지 재진입(GET /consultations/{id}) 시 의뢰서 생성 버튼 활성화 상태를
-- 복원할 수 있도록 한다. 한 번 true 가 되면 idempotent (다시 false 로 돌리지 않음).
ALTER TABLE consultations
ADD COLUMN IF NOT EXISTS all_completed BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN consultations.all_completed IS
    'AI 가 사실관계 수집을 완료했는지 여부 (영구 저장). MessageService 의 allCompleted 게이트 true 시점에 설정.';