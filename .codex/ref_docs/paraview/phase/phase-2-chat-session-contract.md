# Phase 2. Chat Session Contract

## 목표

Phase 2의 목표는 backend 명세의 채팅 세션 계약을 프론트에 완성도 있게 반영하는 것이다. 핵심은 `PATCH /api/v1/chat-sessions/{sessionId}` 세션 제목 변경 기능이며, 이 API가 연결된 시뮬레이션 제목까지 함께 바꾸는 부수효과를 UI 상태와 목록 갱신에 안전하게 반영해야 한다.

Phase 2는 새 대화/시뮬레이션 실행 흐름을 바꾸지 않는다. 채팅 세션 리소스의 create/list/detail/patch/delete/messages helper와 세션 목록 UI의 rename 동작을 명세에 맞게 닫는 단계다.

## 비판적 검토

기존 Phase 2 문서는 누락 API가 `PATCH /chat-sessions/{sessionId}`라는 점은 맞게 짚었지만, 실제 구현 계획으로는 부족했다.

- `PATCH`가 단순 세션명 수정이 아니라 연결된 시뮬레이션 제목까지 수정한다는 부수효과가 빠져 있었다.
- Phase 1에서 API helper 계층을 먼저 정리해야 하는 선행 조건이 명확하지 않았다.
- rename UI를 만들지 말지의 기준이 없었다.
- 현재 열린 세션을 rename했을 때 `Simulation2Page`의 `workflow.simulationTitle`, session list, simulation list refresh를 어떻게 동기화할지 빠져 있었다.
- 검색 중 rename, pagination, 실패 복구, 422 validation 표시 기준이 없었다.
- `updateChatSession` 테스트 범위가 path/body 정도로만 암시되어 있고 UI 테스트 케이스가 없었다.

## 선행 조건

Phase 1이 완료되어 있으면 다음을 그대로 사용한다.

- `lib/api/http.ts`의 `encodePathSegment`, `withQuery`
- `lib/api/chatSessions.ts`의 create/list/detail/delete/messages/send helper
- active UI의 직접 `apiRequest` 제거

Phase 1이 아직 완료되지 않은 상태에서 Phase 2를 먼저 구현한다면, 이 phase의 첫 작업으로 최소한 다음만 먼저 수행한다.

- `lib/api/chatSessions.ts`에 path encoding 적용
- `updateChatSession` helper 추가
- `lib/api/chatSessions.test.ts`에 patch 테스트 추가

## 명세 요약

대상 endpoint:

- `PATCH /api/v1/chat-sessions/{sessionId}`

계약:

- Auth: authenticated
- Path: `sessionId` required
- Body: `title` required, string, 1-200 chars
- Response: `ChatSessionDetail`
- Error: `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, validation error

중요한 서버 부수효과:

- 세션 제목이 바뀌면 `linkedSimulationId`로 연결된 시뮬레이션 제목도 같은 값으로 수정된다.
- `linkedSimulationId`가 없거나 연결된 시뮬레이션을 찾을 수 없으면 `chatSessionId` 기준 세션 하위 시뮬레이션 제목을 수정한다.
- 연결된 시뮬레이션이 없으면 세션 제목만 변경된다.

프론트 의미:

- rename 성공 응답은 세션 목록의 source of truth다.
- 현재 열린 세션이면 화면의 현재 시뮬레이션 제목도 함께 갱신해야 한다.
- 시뮬레이션 목록은 서버에서 변경된 title을 다시 읽도록 refresh해야 한다.

## 범위

포함:

- `updateChatSession` API helper 추가
- title body validation의 프론트 최소 방어
- `SessionListCard`에 rename UI 추가
- rename 성공 후 세션 목록 갱신
- 현재 열린 세션 rename 시 `Simulation2Page` workflow title 동기화
- 관련 테스트 추가

제외:

- 채팅 세션 자동 제목 생성 정책 변경
- 메시지 전송/LLM 응답 흐름 변경
- 세션 삭제 정책 변경
- simulation detail API를 별도로 호출해 rename 결과를 검증하는 로직
- optimistic update 기반 복잡한 cache layer 도입
- legacy 화면 rename 지원

## API Helper 계획

파일:

- `lib/api/chatSessions.ts`

추가 타입:

```ts
export interface CreateChatSessionBody {
  title: string;
}

export type CreateChatSessionResponse = ChatSessionDetail;

export interface UpdateChatSessionBody {
  title: string;
}

export type UpdateChatSessionResponse = ChatSessionDetail;

export interface SendChatSessionMessageBody {
  content: string;
}

export interface SendChatSessionMessageResponse {
  userMessage: ChatMessage;
  assistantMessage: ChatMessage | null;
  simulationDraft: {
    simulationId: string;
    status: string;
    requiresUserConfirmation: unknown | null;
    backendAction: unknown | null;
    duplicateDecision: unknown | null;
    duplicateSignature: unknown | null;
    duplicateCandidateCount?: number | null;
    matchedSimulationIds?: string[] | null;
    existingExperiment?: unknown | null;
    draftUpdateAllowed: boolean | null;
    draftUpdateFailureReason: string | null;
  };
}
```

추가 함수:

```ts
export function updateChatSession(
  sessionId: string,
  body: UpdateChatSessionBody,
): Promise<UpdateChatSessionResponse> {
  return apiRequest(`/api/v1/chat-sessions/${encodePathSegment(sessionId)}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
}
```

검증:

- helper는 title을 임의 변환하지 않는다.
- UI가 trim한 title을 전달한다.
- path parameter는 반드시 `encodePathSegment`를 사용한다.
- 1-200자 제한은 UI에서 먼저 막되, 최종 판단은 backend 422 응답을 따른다.

## UI 계획

대상:

- `components/simulation/SessionListCard.tsx`
- `components/pages/Simulation2Page.tsx`

### `SessionListCard`

추가 props:

```ts
onRenamed?: (
  previous: ChatSessionSummary,
  updated: ChatSessionSummary,
) => void;
actionsDisabled?: boolean;
```

권장 상태:

```ts
const [editingSessionId, setEditingSessionId] = useState<string | null>(null);
const [editingTitle, setEditingTitle] = useState('');
const [renamingSessionId, setRenamingSessionId] = useState<string | null>(null);
```

UI 동작:

- 각 세션 row에 rename icon button을 추가한다.
- delete button과 같은 row action 영역에 배치한다.
- edit mode에서는 title text 대신 compact input을 보여준다.
- save/cancel은 icon button으로 제공한다.
- loading 중인 row는 select/delete/rename을 비활성화한다.
- 현재 검색어와 pagination은 유지한다.
- rename 성공 후 현재 page를 다시 로드한다.

입력 규칙:

- title은 `trim()` 후 전송한다.
- 빈 문자열은 API 호출 전에 막고 row-level error를 표시한다.
- 200자를 초과하면 API 호출 전에 막는다.
- 기존 title과 동일하면 API 호출하지 않고 edit mode만 종료한다.

업데이트 정책:

- optimistic update는 하지 않는다.
- 서버 응답을 받은 뒤 목록을 다시 로드한다.
- `onRenamed(previous, updated)`를 호출해 부모가 현재 workflow를 갱신할 수 있게 한다.

### `Simulation2Page`

추가 핸들러:

```ts
const handleRenamedSession = useCallback(
  (previous: ChatSessionSummary, updated: ChatSessionSummary) => {
    if (updated.sessionId !== workflow.chatSessionId) return;

    setWorkflow(prev => ({
      ...prev,
      simulationTitle:
        updated.linkedSimulationId && prev.simulationId === updated.linkedSimulationId
          ? updated.title
          : prev.simulationTitle,
    }));

    setSimulationListRefreshKey(k => k + 1);
    setSessionListRefreshKey(k => k + 1);
  },
  [workflow.chatSessionId],
);
```

주의:

- `linkedSimulationId`가 없지만 backend가 `chatSessionId` 기준으로 시뮬레이션 제목을 바꿀 수 있다. 이 경우 현재 workflow에 simulationId가 있으면 `simulationTitle`을 updated title로 맞추는 방식을 고려한다.
- rename 성공 후 URL은 바꾸지 않는다.
- messages는 다시 불러오지 않는다.
- 현재 세션이 아니면 workflow 상태를 건드리지 않는다.

## UX 세부 기준

- 버튼은 lucide icon을 사용한다.
- rename 버튼: `Pencil`
- save 버튼: `Check`
- cancel 버튼: `X`
- pending 표시: `Loader2`
- destructive delete와 rename action은 시각적으로 구분한다.
- row 안에 긴 설명 문구를 추가하지 않는다.
- title input은 row 높이를 크게 흔들지 않도록 `h-7`, `text-xs` 수준으로 유지한다.
- 모바일에서도 input, save, cancel, delete 버튼이 겹치지 않도록 row action 영역 폭을 고정한다.
- 검색 input과 rename input이 동시에 있어도 keyboard focus가 엉키지 않도록 edit mode 진입 시 rename input에 focus한다.

## 상태 전이

| 상태 | 진입 조건 | 허용 액션 | 종료 |
| --- | --- | --- | --- |
| idle row | 일반 목록 표시 | select, rename, delete | rename 클릭 |
| editing row | rename 클릭 | save, cancel | save 성공/실패, cancel |
| saving row | save 클릭 | 없음 | success 또는 error |
| error row | validation/API 실패 | edit 유지, cancel | 사용자가 수정 후 재시도 또는 cancel |

## Error 처리

| Error | UI 처리 |
| --- | --- |
| empty title | API 호출 전 row-level error |
| title > 200 | API 호출 전 row-level error |
| `401` | 기존 `ApiError` 메시지 표시. 전역 auth 흐름은 `apiClient`가 담당 |
| `403` | row-level error 또는 list error 표시 |
| `404` | row-level error 표시 후 목록 reload 후보 |
| `422` | backend message를 row-level error로 표시 |
| network/unknown | 기존 list error 스타일과 동일한 문구 사용 |

권장:

- rename 실패 시 edit mode를 유지한다.
- 실패한 title 입력값을 유지한다.
- list 전체 error로만 표시하지 말고 가능하면 해당 row 근처에 표시한다.

## 검색/페이지 정책

- 검색 중 rename 성공 시 현재 검색어와 page를 유지하고 reload한다.
- rename된 title이 현재 검색 조건에서 벗어나면 reload 후 해당 row가 사라질 수 있다. 이는 서버 검색 결과와 일치하므로 허용한다.
- 현재 page의 마지막 item이 검색 결과에서 사라져 빈 page가 되면 이전 page로 보정한다.
- `total`은 reload 응답 기준으로 갱신한다.

## 삭제와의 상호작용

- rename 중인 row의 delete는 비활성화한다.
- delete 확인 dialog가 열린 상태에서는 rename 진입을 허용하지 않는다.
- delete pending 중에는 rename save를 허용하지 않는다.
- rename과 delete가 동시에 같은 row에서 발생하지 않도록 `renamingSessionId`와 `deletingSessionId`를 함께 확인한다.

## 작업 순서

### Step 1. API helper

- `UpdateChatSessionBody`, `UpdateChatSessionResponse` 추가
- `updateChatSession` 추가
- path encoding 적용
- 기존 get/delete/messages path encoding도 함께 보강

검증:

- `lib/api/chatSessions.test.ts`

### Step 2. SessionListCard rename UI

- row action에 rename button 추가
- edit mode input/save/cancel 추가
- validation 추가
- pending/error state 추가
- 성공 시 current page reload
- `onRenamed` callback 추가

검증:

- `components/simulation/SessionListCard.test.tsx`

### Step 3. Simulation2Page 동기화

- `handleRenamedSession` 추가
- popover의 `SessionListCard`에 `onRenamed` 전달
- 현재 session rename 시 `workflow.simulationTitle` 갱신
- `simulationListRefreshKey`, `sessionListRefreshKey` 갱신

검증:

- `components/pages/Simulation2Page.test.tsx`

### Step 4. Phase 1 결과와 정합성 확인

- Phase 1에서 추가한 `createChatSession`, `sendChatSessionMessage` helper와 import 경로가 충돌하지 않는지 확인
- active UI에서 chat session 관련 직접 `/api/v1/chat-sessions` 조립이 남아 있지 않은지 확인

검증 명령 후보:

```text
rg -n "api/v1/chat-sessions|updateChatSession|deleteChatSession|getChatSession" lib components app
```

## 테스트 계획

### `lib/api/chatSessions.test.ts`

필수 케이스:

- `updateChatSession('session/1', { title: 'Renamed' })`가 encoded path로 `PATCH` 호출
- body가 `{"title":"Renamed"}`로 전달
- get/delete/messages도 slash 포함 id를 encoding
- list title query는 trim 후 query 생성

### `components/simulation/SessionListCard.test.tsx`

필수 케이스:

- rename icon 클릭 시 input/save/cancel이 표시된다.
- 빈 title save는 API를 호출하지 않고 error를 표시한다.
- 200자 초과 title save는 API를 호출하지 않는다.
- 동일 title save는 API를 호출하지 않고 edit mode를 종료한다.
- 성공 시 `updateChatSession` 호출 후 목록을 reload한다.
- 성공 시 `onRenamed(previous, updated)`가 호출된다.
- 실패 시 edit mode와 입력값을 유지한다.
- rename pending 중 select/delete가 비활성화된다.
- 검색어가 있는 상태에서 성공해도 동일 검색어로 reload한다.

### `components/pages/Simulation2Page.test.tsx`

필수 케이스:

- 현재 열린 세션 rename 성공 시 `workflow.simulationTitle`이 갱신된다.
- 다른 세션 rename 성공 시 현재 workflow가 바뀌지 않는다.
- rename 성공 후 session/simulation list refresh key가 증가한다.
- rename 후 URL query `session`은 유지된다.

## Acceptance Criteria

- `PATCH /api/v1/chat-sessions/{sessionId}` helper가 존재한다.
- chat session path parameter가 일관되게 encoding된다.
- `SessionListCard`에서 세션 title을 rename할 수 있다.
- title validation은 1-200자 계약을 따른다.
- rename 성공 후 세션 목록은 서버 응답 기준으로 갱신된다.
- 현재 열린 세션 rename 시 현재 화면의 simulation title과 simulation list refresh가 반영된다.
- rename 실패 시 사용자의 입력이 사라지지 않는다.
- delete, select, rename pending 상태가 서로 충돌하지 않는다.
- 테스트가 API helper, SessionListCard, Simulation2Page 주요 흐름을 커버한다.

## 허용 잔여 항목

Phase 2 완료 후에도 다음은 남아 있을 수 있다.

- 세션 title 자동 추천/재생성
- batch rename
- 관리자용 세션 rename
- legacy 화면 rename
- 서버가 업데이트한 simulation title을 별도 detail fetch로 검증하는 동작

## 문서 업데이트 기준

Phase 2에서 실제 코드 변경이 발생하면 다음 문서를 검토한다.

- `docs/architecture/flow.md`: 세션 rename이 사용자 관찰 가능 workflow로 추가될 경우 갱신
- `docs/architecture/component.md`: `SessionListCard` props 계약이 문서화되어 있다면 갱신
- `docs/architecture/state.md`: chat session state나 workflow title 동기화 규칙을 공식화할 경우 갱신

backend public API 자체를 바꾸는 작업은 아니므로 `docs/api/endpoints.md`와 `docs/api/specification.md`는 원칙적으로 수정하지 않는다. 다만 frontend-backend 매핑 문서를 `docs/api`에 공식화했다면 chat session row를 갱신한다.

## 리스크

- rename API가 연결 시뮬레이션 제목까지 바꾸므로, session list만 갱신하면 sidebar의 simulation list와 현재 workflow title이 stale 상태가 된다.
- optimistic update를 사용하면 검색 조건에서 벗어나는 rename이나 server-side title trim/validation과 충돌할 수 있다.
- row 안에 edit input과 action buttons가 추가되면 모바일 폭에서 텍스트/버튼 겹침이 생길 수 있다.
- delete와 rename이 같은 row에서 동시에 발생하면 서버 상태와 UI 상태가 엇갈릴 수 있다.
- Phase 1 helper 정리가 완료되지 않은 상태에서 UI부터 붙이면 직접 API 호출이 다시 늘어날 수 있다.

