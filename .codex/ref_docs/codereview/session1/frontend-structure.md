# Frontend 구조 요약

## 주요 디렉터리/모듈 분류

| 구분 | 경로 | 역할 | 관련 기능 | 비고 |
|---|---|---|---|---|
| route/page | `app/**/page.tsx` | Next.js App Router 사용자 진입점 | public CMS, PFM simulation, admin, login | 실제 route 목록은 추가 확인 필요 |
| route/page | `app/simulation2/page.tsx` | PFM simulation2 진입 및 클라이언트 인증 게이트 | simulation2 | `use client`, 토큰 확인, `getMe()` 호출, redirect 처리 확인: `app/simulation2/page.tsx:1`, `app/simulation2/page.tsx:16` |
| route/page | `app/cmsl20043/page.tsx` | PFM admin page 진입점 | admin | `Suspense`로 `AdminPage3` 렌더링: `app/cmsl20043/page.tsx:1`, `app/cmsl20043/page.tsx:14` |
| layout | `app/layout.tsx` | 전역 HTML/layout, Header/Footer, providers 배치 | 전체 앱 | `Providers`, `Header`, `Footer`, `Analytics` 배치: `app/layout.tsx:87` |
| layout | `app/providers.tsx` | React Query, LanguageProvider, Toaster 구성 | 전체 앱 | QueryClient 기본 옵션 설정: `app/providers.tsx:12`, `app/providers.tsx:13` |
| component | `components/pages/*` | page-level container 및 페이지 단위 UI | CMS, PFM, admin, login | 일부 파일이 API 호출/상태/비즈니스 흐름까지 포함 |
| component | `components/pages/Simulation2Page.tsx` | simulation2 핵심 컨테이너 | PFM simulation workflow | 3,632라인 대형 컨테이너, 다수 상태/API/WS 흐름 포함: `components/pages/Simulation2Page.tsx:589` |
| component | `components/pages/AdminPage3.tsx` | PFM admin 콘솔 컨테이너 | admin dashboard | 3,076라인 대형 컨테이너, query/mutation/권한/UI 포함: `components/pages/AdminPage3.tsx:483` |
| component | `components/simulation/*` | simulation 관련 UI 카드/패널/도구 | job/result/session/viz | 일부 컴포넌트가 직접 API 호출 수행: `components/simulation/JobResultListCard.tsx:98` |
| component | `components/ui/*` | UI primitive/shadcn 계열 컴포넌트 | 공통 UI | coverage 제외 설정 확인: `vitest.config.ts:16` |
| component | `components/common/*` | 공통 컴포넌트 | 공통 UI | 세부 역할 추가 확인 필요 |
| feature/domain | `components/pages/simulation2/*` | simulation2 workflow 타입/매퍼/세션 헬퍼 | PFM simulation workflow | `workflowTypes`, `workflowMappers`, `jobMonitorSession` 확인 |
| hook | `hooks/*` | 공통 hook 위치 | UI/상태 보조 | 구체 hook 목록과 사용처는 추가 확인 필요 |
| store/state | 전용 `store` 디렉터리 확인되지 않음 | 전역 상태 저장소 | 확인 필요 | Session 1 기준으로 local state와 TanStack Query 중심 |
| api/service | `lib/apiClient.ts` | 인증 포함 공통 API request, refresh/error normalization | PFM API | `authFetch`, `apiRequest<T = any>` 확인: `lib/apiClient.ts:265`, `lib/apiClient.ts:380` |
| api/service | `lib/api/*` | PFM backend 리소스별 API helper | simulation, job, result, visualization, admin | helper 계층은 비교적 명확하나 admin에 중복 DTO 존재 |
| api/service | `lib/auth.ts` | token/account 인증 helper | auth | route guard와 연동, 세부 검토는 Session 2 대상 |
| api/service | `lib/supabaseClient.ts` | Supabase browser client | CMS | CMS 페이지에서 직접 사용 |
| api/service | `utils/supabase/server.ts` | Supabase server helper | CMS/server | 세부 사용처 추가 확인 필요 |
| api/service | `api/chat.js` | legacy Gemini API route | legacy AI assistant | API key 기반 server route: `api/chat.js:63`, `api/chat.js:80` |
| util | `lib/utils.ts` | 공통 utility | UI/common | 세부 사용처 추가 확인 필요 |
| util | `lib/api/http.ts` | query/path/binary/WebSocket/keepalive helper | PFM API | WebSocket URL helper 확인: `lib/api/http.ts:90` |
| util | `scripts/check-pfm-api-boundaries.mjs` | PFM API boundary 정적 검사 | architecture guard | simulation page boundary 검사: `scripts/check-pfm-api-boundaries.mjs:6` |
| type | `types.ts` | 공통 타입 위치 | 확인 필요 | 세부 타입 범위 추가 확인 필요 |
| type | `lib/api/*.ts` | 리소스 DTO/API response 타입 | PFM API | 도메인별 타입과 helper가 같은 파일에 공존 |
| type | `components/pages/simulation2/workflowTypes.ts` | workflow view/domain 상태 타입 | simulation2 | `Record<string, any>` 포함: `components/pages/simulation2/workflowTypes.ts:72` |
| constant/config | `package.json` | script/dependency/package metadata | 전체 앱 | package name은 `cmsl-nextjs`: `package.json:2`; 프로젝트명 pfm-FE와 명칭 불일치 여부 확인 필요 |
| constant/config | `tsconfig.json` | TypeScript compiler 설정 | 전체 앱 | strict 비활성화: `tsconfig.json:10`, `tsconfig.json:29` |
| constant/config | `next.config.ts` | Next.js 설정 | 전체 앱 | 세부 설정 추가 확인 필요 |
| style | `app/globals.css` | 전역 CSS | 전체 앱 | 세부 스타일 검토는 Session 1 범위 밖 |
| style | `tailwind.config.ts` | Tailwind 설정 | 전체 앱 | 세부 스타일 검토는 Session 1 범위 밖 |
| test | `*.test.ts`, `*.test.tsx` | 단위/컴포넌트 테스트 | simulation2 등 | include 패턴 확인: `vitest.config.ts:11` |
| test | `scripts/check-pfm-api-boundaries.mjs` | 아키텍처 경계 회귀 테스트 | PFM API boundary | `test:boundaries` 연동 여부는 `package.json` 추가 확인 필요 |

## 구조상 관찰

- PFM API 계층은 `lib/apiClient.ts`와 `lib/api/*`로 분리되어 있어 route/page에서 raw endpoint를 직접 다루지 않도록 하는 의도가 확인된다.
- PFM simulation2는 `components/pages/simulation2/*`에 일부 workflow helper를 분리했지만, 핵심 orchestration은 여전히 `Simulation2Page.tsx`에 집중되어 있다.
- CMS/Supabase 흐름은 service/hook 계층을 통하지 않고 페이지 컴포넌트에서 직접 DB/storage 작업을 수행하는 파일들이 확인된다.
- 전용 store 디렉터리는 Session 1에서 명확히 확인되지 않았으며, 상태 관리는 local state와 TanStack Query가 중심인 것으로 보인다. 확인 필요.
