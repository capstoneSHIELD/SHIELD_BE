# Type Duplication Review

| ID | 심각도 | 중복 대상 | 파일 경로들 | 문제 | 영향 | 통합 방향 |
|---|---|---|---|---|---|---|
| S6-DUPTYPE-001 | High | `SimulationStatus` | `lib/api/simulations.ts:16`, `lib/api/admin.ts:36`, `components/pages/simulation2/workflowTypes.ts:4` | 같은 simulation 상태 union이 API와 workflow에 중복 정의되어 있다. | 상태값 추가/삭제 시 일부 계층만 갱신될 수 있다. | API contract status를 단일 export하고 workflow stage는 mapper로 파생한다. |
| S6-DUPTYPE-002 | High | `JobStatus` | `lib/api/jobs.ts:4`, `lib/api/admin.ts:37` | job 상태 union이 일반 API와 admin API에 중복 정의되어 있다. | admin/job 화면의 상태 처리 drift 위험이 있다. | shared job DTO/status module 또는 `lib/api/jobs` 재사용을 검토한다. |
| S6-ENUM-001 | Medium | `VisualizationStatus` | `lib/api/visualizations.ts:11`, `components/pages/simulation2/workflowTypes.ts:5`, `lib/api/admin.ts:40` | visualization 상태 union이 API/admin/workflow에 흩어져 있다. | visualization control/status UI가 불일치할 수 있다. | visualization API status를 기준으로 UI 상태를 파생한다. |
| S6-DUPTYPE-003 | Medium | `Composition`, `SimulationCompositionDto`, `SimulationWarning` | `lib/api/simulations.ts:24-42`, `lib/api/admin.ts:149-167`, `components/pages/simulation2/workflowTypes.ts:29-36` | simulation 응답 부속 타입이 일반 API, admin API, workflow에 유사하게 존재한다. | backend contract 변경 시 mapper와 UI 일부가 누락될 수 있다. | API DTO는 한 곳으로 모으고 workflow warning은 view model로 명명한다. |
| S6-DUPTYPE-004 | Medium | `JobSummary`, `JobDetail`, `JobEvent` | `lib/api/jobs.ts:16-35`, `lib/api/admin.ts:198-217`, `components/pages/simulation2/workflowTypes.ts:22-27` | job 응답 타입이 admin과 일반 API에서 유사하게 반복된다. | 필드 추가/삭제 시 일부 화면만 대응할 위험이 있다. | 공통 DTO + admin 확장 DTO 구조를 검토한다. |
| S6-DUPTYPE-005 | Medium | `ResultSummary`, `ResultDetail`, `ResultFieldsResponse` | `lib/api/results.ts:14-51`, `lib/api/admin.ts:224-261` | result 응답 타입이 일반 API와 admin API에 반복된다. | result explorer와 admin result detail의 계약 drift 가능성이 있다. | shared result DTO를 import하고 admin 전용 필드만 확장한다. |
| S6-PROPS-001 | Low | CMS content item shape | `components/pages/EditHomePageForm.tsx:181`, `components/pages/introduction/Section2_CoreCapabilites.tsx:8`, `components/pages/introduction/Section3_ResearchAreas.tsx:10` | CMS content item이 `any`로 반복 처리된다. | public page와 edit form의 content 구조가 어긋날 수 있다. | CMS content DTO와 section별 view model을 정의한다. |

## 확인 필요

- admin API가 일반 API와 의도적으로 다른 응답 계약을 갖는지 백엔드 계약 확인이 필요하다.
- CMS content는 pageKey별 자유 schema를 의도했을 수 있으므로, 완전한 공통화 전에 CMS 데이터 구조 확인이 필요하다.
