# Component Quality Review

| ID | 심각도 | 영역 | 파일 경로 | 라인 | 문제 | 영향 | 개선 방향 |
|---|---|---|---|---:|---|---|---|
| S3-QUAL-001 | High | file size | `components/pages/Simulation2Page.tsx` | 589 | 파일 길이가 3347 lines이고 helper, state, API orchestration, JSX가 한 파일에 집중되어 있다. | 변경 충돌과 회귀 위험이 가장 크다. | workflow/hook/presenter 단위로 세로 분리 |
| S3-QUAL-002 | High | file size | `components/pages/AdminPage3.tsx` | 483 | 파일 길이가 2942 lines이고 admin tab, query/mutation, table, dialog가 한 파일에 집중되어 있다. | admin 확장 시 유지보수성과 테스트 용이성이 크게 떨어진다. | tab container와 dialog/table component로 분리 |
| S3-QUAL-003 | Medium | props type | `components/ResearchPageTemplate.tsx` | 53 | `getContent(data: any, field: string)`가 CMS content access를 `any`로 처리한다. | CMS schema 변경/오타를 컴파일 타임에 잡기 어렵다. | CMS content view model과 localized accessor 타입 도입 |
| S3-QUAL-004 | Medium | accessibility | `components/MemberDetailModal.tsx` | 18 | custom overlay/modal을 직접 구현하고 `role="dialog"`, `aria-modal`, focus trap, escape close가 확인되지 않는다. | keyboard/screen reader 사용성 문제가 생길 수 있다. | Radix `Dialog` 기반으로 전환하거나 접근성 속성/포커스 관리 추가 |
| S3-QUAL-005 | Medium | loading/error/empty | `components/pages/EditNoticePage.tsx` | 46 | `noticeId`가 falsy이면 `fetchNotice`가 return하지만 `loading`을 false로 내리지 않는다. `Number(id)`가 `NaN`인 경우도 포함된다. | 잘못된 route param에서 editor가 무한 loading 될 수 있다. | id parser와 invalid state를 명시하고 error/not-found UI로 전환 |
| S3-QUAL-006 | Medium | loading/error/empty | `components/pages/HomePage.tsx` | 36 | `fetchPageData`에 `try/catch/finally`가 없고 `setLoading(false)`가 정상 경로 `components/pages/HomePage.tsx:66`에만 있다. | Supabase 호출 throw 시 loading이 유지될 수 있고 error UI가 없다. | `useHomeContent` hook에서 error/loading/empty를 표준화 |
| S3-QUAL-007 | Medium | duplication | `components/pages/AdminPage.tsx` | 43 | `sanitizeForStorage`가 여러 edit/admin component에 중복된다. 같은 패턴은 `components/pages/EditNoticePage.tsx:14`, `components/pages/EditGalleryPage.tsx:13`에도 있다. | 파일명 정책 변경 시 중복 수정 누락 가능. | `lib/storage/filename` 또는 CMS storage helper로 이동 |
| S3-QUAL-008 | Medium | styling | `components/pages/ResearchPageTemplate.tsx` | 65 | CMS text를 `dangerouslySetInnerHTML`로 렌더링한다. 실제 sanitize 정책은 component에서 확인되지 않는다. | CMS content 입력 경로가 안전하지 않으면 XSS 위험. | sanitize 위치와 trusted content 정책 확인 후 sanitizer/service 경계로 이동 |
| S3-QUAL-009 | Medium | modal/dialog | `components/pages/AdminPage3.tsx` | 2906 | review/user/cancel/close dialog가 대형 admin component 내부에 inline으로 정의되어 있다. | dialog 상태/validation 변경이 admin 전체 component에 결합된다. | `ReviewRequestDialog`, `UpdateUserDialog`, `CancelJobDialog`, `CloseVisualizationDialog`로 분리 |
| S3-QUAL-010 | Low | table/list | `components/pages/NewsPage.tsx` | 29 | presentation list component가 search setter, pagination, edit/delete/pin action까지 props로 직접 받는다. | list UI 재사용성이 낮고 action policy가 props contract에 노출된다. | row action model 또는 action slot으로 축소 |
| S3-QUAL-011 | Low | viewer | `components/simulation/ResultExplorerPanel.tsx` | 197 | blob download helper가 component 파일 내부에 있다. 유사 helper가 `Simulation2Page`, `AdminPage3`에도 존재한다. | download 처리 정책/테스트가 중복된다. | 공통 download helper로 이동 |
| S3-QUAL-012 | Suggestion | common component | `components/common/ApiErrorNotice.tsx` | 19 | normalized error presenter는 UI와 error normalization 경계가 비교적 명확하다. | 유지할 만한 좋은 분리 기준이다. | 다른 page-local error UI도 `ApiErrorNotice` 패턴으로 통일 |

## 확인 필요

- HTML content가 모두 관리자 trusted input인지, sanitizer가 저장 시점에 적용되는지 확인 필요.
- legacy admin/editor component가 현재 운영 범위인지 확인 필요.
