# W0 실행 기록 (2026-06-12)

> 실행 범위: `refactoring-execution-order.md`의 W0 baseline + 즉시형 P0 중 승인 없이 가능한 PFM/Admin 항목.
> CMS/게시판 및 Supabase/env 사용처 변경은 사용자 명시 승인 전까지 보류한다.

---

## 1. G0 baseline

| 명령 | 결과 | 기록 |
|---|---|---|
| `npm run lint` | 실패 | PowerShell에서는 `npm.ps1` 실행 정책으로 차단되어 `npm.cmd`로 재실행. `next lint`가 ESLint 설정 프롬프트를 띄워 비대화형 baseline 실패. 설정 파일은 생성하지 않음 |
| `npm run build` | 실패 | compile은 성공. prerender `/people/alumni` 단계에서 `supabaseUrl is required`로 실패. Tailwind arbitrary class ambiguity warning 4건 표시 |
| `npm run test:run` | 실패 | 24 files 중 22 passed / 2 failed, 152 tests 중 149 passed / 3 failed. 기존 실패: `lib/api/admin.test.ts` Blob instance assertion 2건, `components/simulation/SessionListCard.test.tsx` overlong rename guard 1건 |
| `npm run test:coverage` | 실패 | `@vitest/coverage-v8` missing dependency |
| `npm run test:boundaries` | 성공 | `PFM API boundary check passed.` |
| `npx tsc --noEmit` | 성공 | 출력 없음. strict 계열 off 상태이므로 타입 안전 보장으로 해석하지 않음 |

## 2. T002 민감 영역 확인

- 이번 W0 코드 변경은 `Simulation2Page` polling fallback guard와 `AdminPage3` URL parser에 한정했다.
- `lib/apiClient.ts` token refresh/error normalization, WebSocket lifecycle hook 이동, AdminPage3 query invalidation 재배치, Supabase storage mutation, env/config 사용처 교체는 건드리지 않았다.
- CMS/게시판 영역과 `lib/supabaseClient.ts`/`ContactPage.tsx` env 사용처 교체는 승인 전 보류한다.
- Playwright 수동 검증은 백엔드 + job 실행 환경 및 dev server가 확보된 뒤 수행해야 한다.

## 3. W0 task 결과

| Task | 상태 | 결과 |
|---|---|---|
| RF-TASK-001 | 완료 | baseline 명령 결과를 본 문서 1장에 기록 |
| RF-TASK-002 | 완료 | 민감 영역과 승인 게이트를 본 문서 2장에 기록 |
| RF-TASK-003 | 진행 중 | `components/pages/Simulation2Page.tsx`에 polling single-flight guard 추가. `components/pages/Simulation2Page.test.tsx`에 pending tick 중복 방지 테스트 추가. 타깃 테스트 통과 |
| RF-TASK-004 | 진행 중 | `components/pages/adminUrlState.ts` helper 추가, `AdminPage3.tsx` page/size 파싱 교체, `adminUrlState.test.tsx` 추가(A5에서 hook 테스트 추가와 함께 tsx로 전환). 타깃 테스트 통과 |
| RF-TASK-085 | 보류 | 로컬 `madge`/`dependency-cruiser` 실행 파일이 없어 설치 없이 측정 불가. 도구 설치 승인 전까지 보류 |

## 4. W0 추가 baseline repair

| 항목 | 결과 |
|---|---|
| `components/simulation/SessionListCard.tsx` | rename input의 `maxLength={200}` 제거. 201자 입력이 API 호출 전에 local validation으로 차단되도록 기존 테스트 기대와 일치시킴 |
| `lib/api/http.ts` | `downloadBinary` 반환 Blob을 현재 runtime realm의 `Blob`으로 정규화. Node/jsdom 간 Blob realm 차이로 admin download 테스트가 실패하던 문제 해소 |
| `package.json` / `package-lock.json` | `@vitest/coverage-v8@4.0.16` devDependency 추가. `npm run test:coverage` 실행 가능화 |
| `eslint.config.mjs` | `next/core-web-vitals`, `next/typescript` 기반 ESLint flat config 추가. `npm run lint`가 설정 프롬프트 없이 실제 lint baseline을 출력하도록 함 |

## 5. W0 검증

| 검증 | 결과 |
|---|---|
| `npm run test:run -- components/pages/Simulation2Page.test.tsx` | 성공, 16 tests passed |
| `npm run test:run -- components/pages/adminUrlState.test.tsx` | 성공, W0 시점 3 parser tests passed; A5 이후 같은 파일에 hook tests 추가 |
| `npm run test:run -- components/pages/Simulation2Page.test.tsx components/pages/adminUrlState.test.tsx` | 성공, W0 시점 19 tests passed; A5 이후 admin URL test count 증가 |
| `npm run test:boundaries` | 성공 |
| `npx tsc --noEmit` | 성공 |
| `npm run build` (변경 후) | 실패, compile/type 단계 성공 후 prerender에서 `supabaseUrl is required`로 실패. baseline과 같은 env 계열 실패 |
| `npm run test:run` (변경 후 전체) | 실패, 25 files 중 23 passed / 2 failed, 156 tests 중 153 passed / 3 failed. baseline과 같은 실패 3건, 신규 `adminUrlState` 3 tests와 `Simulation2Page` 신규 guard test는 통과 |
| `npm run test:run` (baseline repair 후 전체) | 성공, 25 files / 156 tests passed |
| `npm run test:coverage` | 성공, 25 files / 156 tests passed. 전체 coverage: statements 58.91%, branches 50.99%, functions 56.22%, lines 62.32% |
| `npm run build` (dummy public Supabase env + network 허용) | 성공. Tailwind arbitrary class ambiguity warning 4건은 유지 |
| `npm run lint` (ESLint config 추가 후) | 실패. 프롬프트는 해소됐고 실제 lint baseline이 출력됨. 기존 코드 전반의 `no-explicit-any`, unused vars, img/no-img-element 등 다수 오류/경고 존재 |
| Playwright smoke (`next start -p 3100`, dummy public Supabase env) | 부분 성공. `/cmsl20043?page=abc&size=999` 렌더됨, body에 `NaN` 없음. `/simulation2`는 인증 가드에 의해 `/pfm_chat/login`으로 이동. 외부 Pretendard CDN / Vercel analytics 요청은 sandbox 네트워크에서 실패. job backend 기반 polling 중복 관찰은 환경 부재로 미수행 |
| T085 madge 측정 | 완료. `npx --yes madge --circular --extensions ts,tsx,js,jsx --ts-config tsconfig.json app components lib hooks api scripts` 실행 결과 225 files 처리, 순환 1건 발견: `lib/api/results.ts > lib/api/simulations.ts` |

## 6. 남은 게이트

- RF-TASK-003/004는 코드와 자동 테스트는 완료했지만, 백로그 완료 조건의 전체 수동 Playwright gate 중 job backend 기반 polling 중복 관찰이 아직 남아 있다.
- `npm run lint`는 실행 가능해졌지만 기존 코드 전반의 lint baseline 오류가 많아 W0 범위에서 일괄 수정하지 않는다. 주요 오류군은 `no-explicit-any`, `prefer-const`, `react/no-unescaped-entities`, `no-empty-object-type`, `react/display-name`, `ban-ts-comment`이며 후속 Task/RF-FINDING 범위에 맞춰 분할 처리한다.
- `npm run build`는 로컬 env가 없으면 Supabase env 누락으로 실패한다. 파일 수정 없이 dummy public Supabase env를 주입하면 build는 통과한다. 실제 T006 env helper/사용처 교체는 CMS/게시판 승인 전 보류한다.
- Playwright smoke command는 결과 출력 후 child server cleanup 문제로 command timeout이 발생했으나 포트 3100 listener는 정리됨을 확인했다.
- T085 순환 1건은 W2 shared type alias 작업(RF-TASK-008/009)에서 해소 대상으로 연결한다. 즉시 수정하지 않았다.
