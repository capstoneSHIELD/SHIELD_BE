# 리팩토링 브리프

## 우선순위

| 우선순위 | 리팩토링 대상 | 이유 | 예상 영향도 | 주의사항 |
|---|---|---|---|---|
| 1 | `components/pages/Simulation2Page.tsx` workflow side effect 분리 | 가장 큰 책임 집중 지점이며 job/viz/chat 흐름이 한 파일에 결합 | High | WebSocket cleanup, polling, refresh key, session/job/result ordering 회귀 위험. 테스트 선행 |
| 2 | CMS/Supabase data access service 또는 hook 도입 | UI component와 persistence 구현 결합이 여러 페이지에 반복 | High | RLS/권한/스토리지 path 정책 확인 필요. 한 번에 전체 이관하지 말고 도메인별 진행 |
| 3 | `components/pages/AdminPage3.tsx` tab/feature별 분리 | admin query/mutation/권한/UI가 대형 컨테이너에 집중 | Medium-High | query key와 invalidation, URL query param 호환성 보존 |
| 4 | `lib/api/admin.ts`와 shared API DTO 정리 | admin DTO와 generic resource DTO drift 위험 | Medium | backend contract 확인 후 alias/mapper를 먼저 명확화 |
| 5 | simulation workflow type 강화 | `Record<string, any>`와 loose API typing이 리팩토링 안전성을 낮춤 | Medium | strict 옵션 전체 변경은 영향 큼. 신규 분리 파일부터 타입 강화 |
| 6 | 외부 연동 adapter 표준화 | Gemini/EmailJS가 PFM API 흐름과 별도 정책 | Low-Medium | 현재 사용 여부와 운영 계약 확인 후 진행 |
| 7 | architecture boundary test 확대 | PFM boundary는 guard가 있으나 CMS/service boundary는 확인되지 않음 | Low | service 경계를 먼저 정한 뒤 검사 추가 |

## 먼저 건드리면 안 되는 민감한 영역

| 영역 | 이유 | 확인 근거 |
|---|---|---|
| `lib/apiClient.ts` token refresh/error normalization | 모든 PFM API 호출의 공통 기반 | `lib/apiClient.ts:265`, `lib/apiClient.ts:278`, `lib/apiClient.ts:304` |
| `lib/api/http.ts` WebSocket/binary/keepalive helper | job/viz/download/unload cleanup과 연결 | `lib/api/http.ts:56`, `lib/api/http.ts:90`, `lib/api/http.ts:103` |
| `Simulation2Page` WebSocket refs/lifecycle | 연결 중복/cleanup/상태 전파 회귀 위험 | `components/pages/Simulation2Page.tsx:611`, `components/pages/Simulation2Page.tsx:1674`, `components/pages/Simulation2Page.tsx:1949` |
| `AdminPage3` 권한/early return | admin 접근 제어 UX와 직접 연결 | `components/pages/AdminPage3.tsx:1157`, `components/pages/AdminPage3.tsx:1166` |
| Supabase delete/upload/update 흐름 | 운영 데이터 손실 및 권한 정책과 연결 | `components/pages/EditMemberPage.tsx:71`, `components/pages/EditMemberPage.tsx:74`, `components/pages/AdminPage2.tsx:66` |

## 안전하게 먼저 개선 가능한 영역

| 대상 | 이유 | 예상 작업 |
|---|---|---|
| `components/pages/simulation2/workflowMappers.ts` | 순수 함수 중심 | status/stage mapper 테스트 보강 |
| `components/pages/simulation2/jobMonitorSession.ts` | token helper로 범위가 작음 | lifecycle 테스트 추가 |
| component 내부 formatting/parsing helper | UI와 side effect에서 분리하기 쉬움 | 순수 util 추출 및 테스트 |
| 문서/아키텍처 guard | 런타임 영향이 낮음 | 현재 경계 기준을 문서화하고 boundary script 확대 검토 |

## 추가 조사가 필요한 영역

| 항목 | 이유 |
|---|---|
| Supabase RLS와 권한 모델 | UI 직접 호출 구조의 위험도 판단에 필요 |
| route 전체 목록과 인증/권한 정책 | route guard 일관성 판단에 필요 |
| legacy AI assistant 사용 여부 | 제거/격리/유지 전략 결정에 필요 |
| 테스트 커버리지와 주요 회귀 시나리오 | 대형 컨테이너 분리 전 안전망 설계에 필요 |
| backend PFM API contract | DTO 중복 정리와 타입 강화에 필요 |

## 다음 세션에서 이어서 확인할 항목

- route/page/layout/container 계층에서 인증/권한/loading/error 처리가 일관적인지 확인한다.
- `Simulation2Page`와 `AdminPage3`의 page-level 책임을 실제 함수/렌더링 단위로 더 내려가 리뷰한다.
- Supabase 직접 호출 페이지를 도메인별로 묶어 service 추출 후보를 정한다.
- 리팩토링은 테스트 추가 또는 pure helper 분리부터 시작한다.
