# 사용자 진입 흐름

## 전역 Layout 흐름

```text
Route
  -> app/layout.tsx
    -> Providers
      -> Header
      -> main(children)
      -> Footer
      -> ScrollToTopButton
    -> Analytics
```

- 전역 layout은 `Providers`로 모든 children을 감싼다: `app/layout.tsx:87`.
- Header/Footer는 모든 route에 적용된다: `app/layout.tsx:88`, `app/layout.tsx:90`.
- React Query/Language/Toast provider는 client provider에서 구성된다: `app/providers.tsx:26`, `app/providers.tsx:27`, `app/providers.tsx:29`.

## PFM Simulation2 진입 흐름

```text
app/simulation2/page.tsx
  -> Suspense fallback
  -> Simulation2Inner
    -> useSearchParams().get('session')
    -> getAccessToken()
    -> getMe()
    -> redirect to /pfm_chat/login on missing/invalid token
    -> Simulation2Page(initialSessionId, nickname)
      -> chat/session/job/result/viz feature components
      -> PFM API helpers and WebSocket helpers
```

| 항목 | 실제 위치 | 근거 | 비고 |
|---|---|---|---|
| query param 처리 | `app/simulation2/page.tsx` | `app/simulation2/page.tsx:9`, `app/simulation2/page.tsx:10` | `session` query를 string으로 전달. 세부 검증은 container 쪽 확인 필요 |
| token 확인 | `app/simulation2/page.tsx` | `app/simulation2/page.tsx:18` | token 없으면 browser redirect |
| account 확인 | `app/simulation2/page.tsx` | `app/simulation2/page.tsx:23` | `getMe()` 실패 시 token clear |
| redirect | `app/simulation2/page.tsx` | `app/simulation2/page.tsx:20`, `app/simulation2/page.tsx:32` | `window.location.href` 직접 사용 |
| loading | `app/simulation2/page.tsx` | `app/simulation2/page.tsx:43`, `app/simulation2/page.tsx:52` | Suspense fallback과 inner loading 중복 |
| container props | `app/simulation2/page.tsx` | `app/simulation2/page.tsx:47` | `initialSessionId`, `nickname` 전달 |

## PFM Admin3 진입 흐름

```text
app/cmsl20043/page.tsx
  -> Suspense fallback
  -> AdminPage3
    -> useSearchParams()
    -> normalize tab/status/page/size/selected ids
    -> meQuery
    -> canUseAdmin gate
    -> tab별 useQuery/useMutation
    -> Error/Loading/Inactive/Forbidden/Success UI
```

| 항목 | 실제 위치 | 근거 | 비고 |
|---|---|---|---|
| route wrapper | `app/cmsl20043/page.tsx` | `app/cmsl20043/page.tsx:14`, `app/cmsl20043/page.tsx:15` | route page는 얇음 |
| query param 처리 | `components/pages/AdminPage3.tsx` | `components/pages/AdminPage3.tsx:489`, `components/pages/AdminPage3.tsx:498`, `components/pages/AdminPage3.tsx:499` | status/page/size/selected id를 container에서 직접 처리 |
| URL state 갱신 | `components/pages/AdminPage3.tsx` | `components/pages/AdminPage3.tsx:539`, `components/pages/AdminPage3.tsx:548` | `router.replace` 사용 |
| 권한 gate | `components/pages/AdminPage3.tsx` | `components/pages/AdminPage3.tsx:551`, `components/pages/AdminPage3.tsx:557` | `meQuery` 기반 |
| loading/error/denied | `components/pages/AdminPage3.tsx` | `components/pages/AdminPage3.tsx:1139`, `components/pages/AdminPage3.tsx:1147`, `components/pages/AdminPage3.tsx:1157`, `components/pages/AdminPage3.tsx:1166` | container 내부 early return |

## Legacy CMS Admin 진입 흐름

```text
app/cmsl2004/page.tsx
  -> client page
    -> supabase.auth.getSession()
    -> supabase.auth.onAuthStateChange()
    -> loading
    -> AdminPage or LegacyLoginPage
```

- `cmsl2004`와 `cmsl20042`는 거의 동일한 client auth gate 패턴이다: `app/cmsl2004/page.tsx:13`, `app/cmsl20042/page.tsx:13`.
- Supabase session을 route page가 직접 확인하고, session 유무로 Admin/Login page를 분기한다: `app/cmsl2004/page.tsx:30`, `app/cmsl20042/page.tsx:30`.
- subscription cleanup은 존재한다: `app/cmsl2004/page.tsx:23`, `app/cmsl20042/page.tsx:23`.

## Board List/Detail/Edit 진입 흐름

```text
app/board/news/page.tsx
  -> server Supabase session
  -> NoticeBoardPage(session)
    -> client Supabase auth/session 확인
    -> Supabase notices query/mutation

app/board/news/[id]/page.tsx
  -> params.id
  -> server Supabase session
  -> NoticeDetailPage(id, session)
    -> client Supabase detail query/delete

app/board/news/[id]/edit/page.tsx
  -> params.id
  -> EditNoticePage(id)
    -> client Supabase detail query/storage/update
```

| 항목 | 실제 위치 | 근거 | 비고 |
|---|---|---|---|
| list session 전달 | `app/board/news/page.tsx` | `app/board/news/page.tsx:7` | server session 전달 |
| detail params/session 전달 | `app/board/news/[id]/page.tsx` | `app/board/news/[id]/page.tsx:9`, `app/board/news/[id]/page.tsx:13` | `id` validation은 확인 필요 |
| edit params 전달 | `app/board/news/[id]/edit/page.tsx` | `app/board/news/[id]/edit/page.tsx:8`, `app/board/news/[id]/edit/page.tsx:9` | edit route guard 확인 필요 |
| container data fetching | `components/pages/NoticeDetailPage.tsx` | `components/pages/NoticeDetailPage.tsx:58`, `components/pages/NoticeDetailPage.tsx:62` | client Supabase 직접 조회 |
| container mutation | `components/pages/NoticeDetailPage.tsx` | `components/pages/NoticeDetailPage.tsx:69`, `components/pages/NoticeDetailPage.tsx:71` | delete 후 router push |
| edit mutation | `components/pages/EditNoticePage.tsx` | `components/pages/EditNoticePage.tsx:122`, `components/pages/EditNoticePage.tsx:133` | update 후 setTimeout으로 이동 |

## 상태/데이터 흐름 요약

- PFM simulation2: route page에서 인증 여부만 확인하고, 대부분의 application workflow는 `Simulation2Page` container가 수행한다.
- PFM admin3: route page는 얇지만, container가 URL state, 권한, query/mutation, loading/error/denied UI를 모두 수행한다.
- CMS board/detail/edit: route가 server session/params를 일부 전달하지만, container가 client Supabase session 재조회와 DB/storage 작업을 직접 수행한다.
- global loading/error boundary는 제한적이며, 대부분의 loading/error UI는 route 또는 container 로컬 구현이다.
