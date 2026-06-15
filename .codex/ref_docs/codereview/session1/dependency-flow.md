# Frontend 의존성 흐름

## 전체 흐름 요약

```text
PFM 사용자 simulation
Route/Page
  -> Page Container
    -> Simulation Components
      -> PFM API helpers / WebSocket helpers
        -> apiClient/authFetch
          -> Backend API

PFM admin
Route/Page
  -> Admin Container
    -> TanStack Query query/mutation
      -> admin API helpers + shared resource helpers
        -> apiClient/authFetch
          -> Backend API

CMS public/admin
Route/Page
  -> Page Component
    -> Supabase client 직접 호출
      -> Supabase DB/storage

Legacy AI
Component
  -> legacy API helper
    -> Next API route
      -> Google Gemini
```

## 사용자 진입점

| 흐름 | 진입점 | 근거 | 설명 |
|---|---|---|---|
| PFM simulation2 | `app/simulation2/page.tsx` | `app/simulation2/page.tsx:1`, `app/simulation2/page.tsx:16`, `app/simulation2/page.tsx:47` | client page에서 token/account 확인 후 `Simulation2Page`를 렌더링 |
| PFM admin | `app/cmsl20043/page.tsx` | `app/cmsl20043/page.tsx:1`, `app/cmsl20043/page.tsx:14`, `app/cmsl20043/page.tsx:15` | `Suspense`로 `AdminPage3`를 감싼 얇은 route page |
| 전역 layout | `app/layout.tsx` | `app/layout.tsx:87`, `app/layout.tsx:88`, `app/layout.tsx:89`, `app/layout.tsx:90` | 모든 route에 provider/header/main/footer 적용 |
| 전역 providers | `app/providers.tsx` | `app/providers.tsx:12`, `app/providers.tsx:13`, `app/providers.tsx:26` | React Query, LanguageProvider, Toaster 제공 |

## PFM simulation2 흐름

```text
app/simulation2/page.tsx
  -> components/pages/Simulation2Page.tsx
    -> components/simulation/*
    -> components/pages/simulation2/*
    -> lib/api/chatSessions.ts
    -> lib/api/simulations.ts
    -> lib/api/jobs.ts
    -> lib/api/results.ts
    -> lib/api/visualizations.ts
    -> lib/api/http.ts
      -> lib/apiClient.ts
        -> Backend API
```

### 확인된 상태/호출 흐름

- route page에서 `clearTokens`, `getAccessToken`, `getMe`를 직접 import한다: `app/simulation2/page.tsx:5`.
- token이 없으면 `/pfm_chat/login`으로 redirect한다: `app/simulation2/page.tsx:18`, `app/simulation2/page.tsx:19`.
- `getMe()` 실패 시 token을 제거하고 login으로 redirect한다: `app/simulation2/page.tsx:23`, `app/simulation2/page.tsx:29`.
- `Simulation2Page`는 메시지, refresh key, loading/error, pending action, WebSocket refs, workflow state를 직접 보유한다: `components/pages/Simulation2Page.tsx:592`, `components/pages/Simulation2Page.tsx:593`, `components/pages/Simulation2Page.tsx:599`, `components/pages/Simulation2Page.tsx:611`, `components/pages/Simulation2Page.tsx:656`.
- job/result/viz 핵심 작업이 같은 컨테이너에 집중되어 있다: `components/pages/Simulation2Page.tsx:1444`, `components/pages/Simulation2Page.tsx:1490`, `components/pages/Simulation2Page.tsx:1674`, `components/pages/Simulation2Page.tsx:1949`, `components/pages/Simulation2Page.tsx:2238`, `components/pages/Simulation2Page.tsx:2437`.
- WebSocket 생성은 helper URL을 사용하지만 lifecycle orchestration은 page container 내부에 있다: `components/pages/Simulation2Page.tsx:1710`, `components/pages/Simulation2Page.tsx:1966`, `lib/api/http.ts:90`.

## PFM admin 흐름

```text
app/cmsl20043/page.tsx
  -> components/pages/AdminPage3.tsx
    -> TanStack Query
      -> lib/api/admin.ts
      -> lib/api/results.ts
      -> lib/api/visualizations.ts
        -> lib/apiClient.ts
          -> Backend API
```

### 확인된 상태/호출 흐름

- route page는 `Suspense` wrapper 역할만 수행한다: `app/cmsl20043/page.tsx:14`.
- `AdminPage3`가 query params를 읽고 tab/filter/selection을 관리한다: `components/pages/AdminPage3.tsx:489`, `components/pages/AdminPage3.tsx:539`.
- `AdminPage3` 내부에서 current account, health, account requests, users, simulations, jobs, events, results, visualization query를 직접 정의한다: `components/pages/AdminPage3.tsx:551`, `components/pages/AdminPage3.tsx:559`, `components/pages/AdminPage3.tsx:573`, `components/pages/AdminPage3.tsx:579`, `components/pages/AdminPage3.tsx:585`, `components/pages/AdminPage3.tsx:597`, `components/pages/AdminPage3.tsx:617`, `components/pages/AdminPage3.tsx:624`, `components/pages/AdminPage3.tsx:642`.
- 같은 컨테이너에서 sync/cancel/load/download/create/update/close/screenshot mutation도 직접 정의한다: `components/pages/AdminPage3.tsx:666`, `components/pages/AdminPage3.tsx:697`, `components/pages/AdminPage3.tsx:729`, `components/pages/AdminPage3.tsx:760`, `components/pages/AdminPage3.tsx:782`, `components/pages/AdminPage3.tsx:806`, `components/pages/AdminPage3.tsx:830`, `components/pages/AdminPage3.tsx:849`.
- 권한/상태별 early return도 같은 파일에 있다: `components/pages/AdminPage3.tsx:1139`, `components/pages/AdminPage3.tsx:1147`, `components/pages/AdminPage3.tsx:1157`, `components/pages/AdminPage3.tsx:1166`.

## CMS/Supabase 흐름

```text
app/*/page.tsx
  -> components/pages/*
    -> lib/supabaseClient.ts 직접 사용
      -> Supabase DB/storage
```

### 확인된 직접 의존

- `HomePage`가 Supabase client와 page/publication/project/notice/gallery 조회를 직접 수행한다: `components/pages/HomePage.tsx:11`, `components/pages/HomePage.tsx:40`, `components/pages/HomePage.tsx:51`, `components/pages/HomePage.tsx:52`, `components/pages/HomePage.tsx:58`, `components/pages/HomePage.tsx:59`.
- `NoticeBoardPage`가 auth session과 Supabase query/mutation을 직접 수행한다: `components/pages/NoticeBoardPage.tsx:38`, `components/pages/NoticeBoardPage.tsx:58`, `components/pages/NoticeBoardPage.tsx:99`, `components/pages/NoticeBoardPage.tsx:105`.
- `AdminPage2`가 members/alumni/pages/popups 조회와 삭제를 직접 수행한다: `components/pages/AdminPage2.tsx:31`, `components/pages/AdminPage2.tsx:38`, `components/pages/AdminPage2.tsx:45`, `components/pages/AdminPage2.tsx:52`, `components/pages/AdminPage2.tsx:66`.
- `EditMemberPage`가 fetch/upload/public URL/update/insert를 직접 수행한다: `components/pages/EditMemberPage.tsx:31`, `components/pages/EditMemberPage.tsx:71`, `components/pages/EditMemberPage.tsx:74`, `components/pages/EditMemberPage.tsx:76`, `components/pages/EditMemberPage.tsx:87`, `components/pages/EditMemberPage.tsx:91`.

## API 호출 흐름

| 영역 | 호출 방식 | 근거 | 평가 |
|---|---|---|---|
| PFM API 공통 | `apiRequest` -> `authFetch` -> token/refresh/error normalization | `lib/apiClient.ts:236`, `lib/apiClient.ts:265`, `lib/apiClient.ts:278`, `lib/apiClient.ts:304`, `lib/apiClient.ts:380` | 계층화 의도 명확. 단 `apiRequest<T = any>`로 타입 안전성 약화 |
| PFM resource API | `lib/api/{simulations,jobs,results,visualizations}.ts` | `lib/api/simulations.ts:173`, `lib/api/jobs.ts:79`, `lib/api/results.ts:104`, `lib/api/visualizations.ts:85` | 리소스별 helper 분리 양호 |
| PFM admin API | `lib/api/admin.ts` | `lib/api/admin.ts:343`, `lib/api/admin.ts:385`, `lib/api/admin.ts:429`, `lib/api/admin.ts:459` | admin API와 shared resource wrapper/type이 섞여 중복/드리프트 위험 |
| CMS Supabase | page component에서 Supabase 직접 호출 | `components/pages/HomePage.tsx:11`, `components/pages/NoticeBoardPage.tsx:58` | UI와 persistence 결합도 높음 |
| Legacy Gemini | `AIChatAssistant` -> `legacyAiChat` -> `/api/chat` -> Gemini | `components/AIChatAssistant.tsx:26`, `lib/api/legacyAiChat.ts:11`, `api/chat.js:80` | PFM API 흐름과 별도. 유지 여부 확인 필요 |

## 의존성 방향 문제 후보

- `Simulation2Page.tsx`는 page container가 UI, workflow orchestration, API 호출, WebSocket lifecycle, parsing/normalization helper까지 포함한다.
- CMS page components는 service/hook 경계 없이 Supabase persistence 구현에 직접 의존한다.
- `AdminPage3.tsx`는 route/page는 얇지만 container 하나에 권한, query, mutation, URL state, UI 렌더링이 집중되어 있다.
- `lib/api/admin.ts`는 admin DTO와 일반 리소스 DTO가 일부 중복되어 타입 변경 시 양쪽 drift가 발생할 수 있다.
- 전용 store/state 계층은 Session 1 기준 명확히 확인되지 않았다. 이 구조가 의도인지 확인 필요.
