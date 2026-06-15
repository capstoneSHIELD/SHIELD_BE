# W1 의사결정 기록 (2026-06-12)

> 실행 범위: `refactoring-execution-order.md`의 W1 조건형 P0 + 확인 필요 일괄 해소.
> 코드 변경은 하지 않았다. CMS/게시판, Supabase storage/env 사용처 변경은 사용자 명시 승인 전까지 보류한다.

---

## 1. 결정 요약

| Task | 상태 | 결정 |
|---|---|---|
| RF-TASK-005 | 보류 | `EditNoticePage` attachment rollback은 게시판 앱 변경이다. 사용자 승인, Supabase storage path, 운영 데이터 백업/복구 경로, 테스트 데이터가 모두 확인될 때까지 구현하지 않는다. |
| RF-TASK-006 | 보류 | env helper는 helper 설계/인벤토리와 사용처 교체를 분리한다. `lib/supabaseClient.ts`, `components/pages/ContactPage.tsx` 교체는 CMS/게시판 승인 전까지 보류한다. |
| RF-TASK-007 | 완료 | admin DTO는 일반 PFM DTO와 일부 의도적으로 다른 계약으로 취급한다. W2/W3에서는 shared status alias와 mapper 경계를 먼저 세우고, `admin.ts`의 wholesale DTO 통합은 하지 않는다. |
| RF-TASK-015 | 부분 완료 | legacy chat은 현재 코드에서 사용 중이므로 보존 전제로 표준화한다. Supabase RLS/권한 정책은 로컬 근거가 확인되지 않아 CMS/게시판 쓰기·권한 변경은 계속 차단한다. |
| RF-TASK-024 | 완료 | `PFMSimulationPage`는 현재 App Router 직접 진입점이 확인되지 않는 legacy workbench로 보존/격리한다. 제거하지 않고, RF-TASK-029/080은 제품 접근 경로 결정 전까지 후속 보류한다. |

## 2. RF-TASK-007 admin DTO 계약

확인 근거:

- `.codex/ref_docs/backend_api.md`의 `/api/v1/admin/account-requests`, `/api/v1/admin/users`, `/api/v1/admin/simulations`.
- `lib/api/admin.ts`의 `AdminAccountRequest`, `AdminUser`, `AdminSimulationSummary`, `PaginatedResponse`.

결정:

- `account-requests`는 `items/page/size/total` 형태로 페이지 메타가 있다.
- `users`는 `{ items: AdminUser[] }` 형태다.
- `admin/simulations`는 query로 `page/size`를 받지만 응답에는 페이지 메타가 없다는 구현 규칙이 문서에 명시되어 있다.
- 따라서 admin DTO는 일반 simulation/result/job DTO와 완전 동일 계약으로 단정하지 않는다.
- RF-TASK-008/009는 shared status/type alias를 우선 도입하고, RF-TASK-010은 alias/mapper 경계 안에서 admin 전용 확장을 유지한다.

## 3. RF-TASK-015 RLS / legacy chat

확인 근거:

- `.codex/ref_docs/refactoring/risk-and-impact-map.md`, `verification-strategy.md`는 Supabase RLS/권한 정책을 "확인 필요"로 유지한다.
- 로컬 저장소에서는 Supabase dashboard policy나 migration 형태의 RLS 정책 원본이 확인되지 않았다.
- `components/AIChatAssistant.tsx`는 `askLegacySimulationAssistant`를 호출한다.
- `lib/api/legacyAiChat.ts`, `api/chat.js`, `lib/api/legacyAdapters.test.ts`가 존재한다.

결정:

- RLS/권한 정책은 미확인이다. C track, board edit route 권한 강화, storage delete/upload 순서 변경은 사용자 승인과 실제 정책 확인 전까지 진행하지 않는다.
- RLS 확인 전 과도한 UI 차단을 추가하지 않는다.
- legacy chat은 제거하지 않는다. RF-TASK-022는 유지 전제로 request schema, response parser, error mapping 표준화를 진행할 수 있다.
- legacy chat 표준화는 CMS/게시판 작업과 섞지 않고 독립 commit으로 진행한다.

## 4. RF-TASK-024 legacy PFMSimulationPage

확인 근거:

- `app/` 라우트 목록에서 `PFMSimulationPage`를 직접 import하는 page가 확인되지 않는다.
- `docs/architecture/directory.md`는 `components/pages/PFMSimulationPage.tsx`를 "현재 App Router 진입점에서는 사용하지 않지만" 남아 있는 legacy PFM workbench로 기록한다.
- `docs/architecture/component.md`, `docs/api/frontend-backend-api-map.md`는 legacy PFM backend 호출도 `lib/api/*` helper 경계를 지켜야 한다고 명시한다.

결정:

- `PFMSimulationPage`는 삭제하지 않고 legacy inactive 영역으로 격리한다.
- RF-TASK-029 polling guard와 RF-TASK-080 parser 분리는 유지/접근 경로가 제품 범위로 재확정될 때 수행한다.
- 제품 범위 재확정 전에는 legacy 화면에 새 기능이나 구조 변경을 넣지 않는다.

## 5. 다음 Wave 영향

- W2는 RF-TASK-007 완료 결과를 근거로 시작할 수 있다.
- RF-TASK-008/009는 admin DTO를 직접 병합하지 않고 shared status alias부터 도입한다.
- Track C는 사용자 승인, RLS/권한 정책, storage path, 백업/복구 경로가 확인될 때까지 보류한다.
- RF-TASK-022 legacy chat 표준화는 W1 legacy 보존 결정에 따라 Q2에서 진행 가능하지만, CMS/게시판 변경과 같은 commit에 섞지 않는다.
