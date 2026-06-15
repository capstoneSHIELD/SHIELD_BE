# W3 실행 기록 (2026-06-12)

> 실행 범위: `refactoring-execution-order.md`의 W3 API 계약 마무리 중 RF-TASK-010/013/014.
> admin DTO는 W1 결정에 따라 일반 DTO로 강제 병합하지 않고, shared status/DTO alias + admin 전용 확장 경계를 유지했다.

---

## 1. 작업 결과

| Task | 상태 | 결과 |
|---|---|---|
| RF-TASK-010 | 완료 | `lib/api/admin.ts`의 중복 status/job/result DTO 선언을 shared/API type alias로 정리했다. `AdminSimulationSummary`, account request, admin user 등 admin 전용 계약은 그대로 유지했다. |
| RF-TASK-013 | 완료(재검증 이월) | `Simulation2Page.tsx`의 inline PATCH body 조립을 `buildUpdateSimulationBody` 순수 mapper로 분리하고 단위 테스트를 추가했다. 기존 request body key/값/extra parameter spread 순서는 유지했다. 실제 backend/job 실행 + Playwright 플로우 검증은 현재 환경에서 수행할 수 없어 재검증 항목으로 남겼다. |
| RF-TASK-014 | 완료 | `lib/api/*`와 `lib/auth.ts`의 `apiRequest` call site가 응답 타입을 명시하도록 정리했다. `apiRequest<T = any>` 기본 generic은 변경하지 않았고, JSON parse 결과는 `unknown` boundary를 거쳐 `T`로 좁히도록 단정 위치를 축소했다. `labserverTrameClient.requestJson`의 `response.json() as Promise<T>`도 `unknown` payload 경계로 바꿨다. |

## 2. 계약 보존

- `/api/v1/admin/account-requests`, `/api/v1/admin/users`, `/api/v1/admin/simulations`의 request/response wire shape는 변경하지 않았다.
- `listAdminSimulations`의 `{ items: AdminSimulationSummary[] }` 응답 계약과 page metadata 미포함 특수 계약은 유지했다.
- `SyncOption`은 admin query key와 admin job helper에서 기존처럼 `sync` 필수 옵션으로 유지했다.
- 결과/작업/시각화 상세 helper는 일반 PFM API helper 계약을 alias로 재사용한다.
- `buildUpdateSimulationBody`는 기존 inline body와 동일하게 `{ parameters: { simulationType, alloySystem, composition, coolingRate, dimension, gridSize, ...extraParameters } }` 형태를 반환한다. 빈 `simulationType`/`alloySystem`을 `undefined`로 두는 기존 동작도 유지했다.
- `apiRequest<T = any>`의 기본 generic은 그대로 유지했다. 이번 작업은 call site 타입 명시와 JSON parse 단정 축소만 수행했다.

## 3. 검증

| 검증 | 결과 |
|---|---|
| `npx tsc --noEmit` | 성공 |
| `npm run test:run -- lib/api/admin.test.ts components/pages/adminPolling.test.ts` | 성공, 2 files / 16 tests passed |
| `npx eslint components/pages/simulation2/simulationParameterMappers.ts components/pages/simulation2/simulationParameterMappers.test.ts` | 성공 |
| `npx eslint lib/api/simulations.ts lib/api/jobs.ts lib/api/results.ts lib/api/visualizations.ts lib/api/chatSessions.ts lib/api/admin.ts lib/api/labserverTrameClient.ts` | 성공 |
| `npm run test:run -- components/pages/simulation2/simulationParameterMappers.test.ts components/pages/Simulation2Page.test.tsx lib/api/simulations.test.ts` | 성공, 3 files / 23 tests passed |
| `npm run test:run -- lib/apiClient.test.ts lib/api/simulations.test.ts lib/api/jobs.test.ts lib/api/results.test.ts lib/api/visualizations.test.ts lib/api/chatSessions.test.ts lib/api/admin.test.ts lib/api/labserverTrameClient.test.ts` | 성공, 8 files / 50 tests passed |
| `npm run test:run` | 성공, 27 files / 162 tests passed |
| `npm run test:coverage` | 성공, 27 files / 162 tests passed. 전체 coverage: statements 59.37%, branches 52.03%, functions 56.17%, lines 62.85% |
| `npm run test:boundaries` | 성공 |
| `npm run build` (dummy public Supabase env) | 성공. lint는 build 중 skip, Tailwind arbitrary class ambiguity warning 4건은 기존과 동일 |

## 4. 남은 게이트

- RF-TASK-011 workflow stage mapper 전환은 W2 실행 기록에서 후속 완료했다.
- RF-TASK-013은 대체 자동 검증까지 완료했다. 실제 parameter PATCH → job submit/update/restore Playwright 플로우는 backend/job 실행 환경과 브라우저 검증 도구가 확보되면 재검증한다.
- RF-TASK-014는 완료했다. 다음 W3 이후 작업은 Wave S4의 `lib/apiClient.ts` 민감 작업(RF-TASK-016~018)이며 token refresh/401 retry 회귀 검증이 필요하다.
