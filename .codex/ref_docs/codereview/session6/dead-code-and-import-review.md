# Dead Code / Import / Export Review

정적 검색만으로 실제 dead code를 확정하지 않았다. 이 문서는 사용되지 않는 코드가 숨어 있을 가능성을 높이는 설정과 import/export 구조의 확인 결과를 정리한다.

| ID | 심각도 | 파일 경로 | 라인 | 대상 | 문제 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S6-DEAD-001 | Medium | `tsconfig.json` | 30-31 | `noUnusedParameters`, `noUnusedLocals` | unused parameter/local 검출이 꺼져 있다. | 미사용 type/util/constant가 누적되어도 타입체크에서 드러나지 않는다. | 신규 리팩토링 영역부터 unused check를 켜거나 lint rule로 보완한다. |
| S6-DEAD-002 | Medium | `tsconfig.json` | 8 | `allowJs` | JS 파일이 함께 허용되어 `api/chat.js` 같은 파일의 dead code/type issue 검출이 약하다. | TS 기반 import/export 정리가 일관되지 않을 수 있다. | API route를 TS로 전환하거나 JS 파일에 별도 lint/typecheck 정책을 둔다. |
| S6-EXPORT-001 | Low | `lib/api/admin.ts` | 31 | `export { getFilenameFromContentDisposition } from './http'` | admin API 파일이 http helper를 재-export한다. | admin API boundary가 helper export까지 포함해 역할이 넓어진다. | 호출부가 `lib/api/http`에서 직접 import하도록 정리할지 검토한다. |
| S6-IMPORT-001 | Suggestion | `components/pages/Simulation2Page.tsx` | 4-102 | imports | UI, API, auth, error, labserver, workflow helper를 한 page에서 모두 import한다. | import 목록 자체가 책임 집중의 신호다. | workflow/service hook 분리 후 page import를 container/component 중심으로 축소한다. |
| S6-IMPORT-002 | Suggestion | `components/pages/AdminPage3.tsx` | 21-117 | imports | UI primitives, hooks, error util, admin API, polling helper가 한 파일에 집중된다. | admin tab 분리와 테스트가 어려워진다. | tab별 container/hook으로 import surface를 줄인다. |
| S6-BARREL-001 | Suggestion | 전체 | 확인 필요 | barrel export | `index.ts` 기반 barrel 과다 사용은 검색 범위에서 두드러지지 않았다. | 현재는 큰 문제 근거 없음. | 추가 분석 도구로 cycle/barrel 여부를 확인한다. |
| S6-CYCLE-001 | Suggestion | 전체 | 확인 필요 | circular import | 정적 `rg`만으로 순환 의존성을 확정하지 않았다. | 순환 import가 있으면 런타임 초기화 순서 문제가 생길 수 있다. | madge 또는 dependency-cruiser 같은 도구로 별도 검증한다. |

## 확인 필요

- 실제 unused export/type 목록은 `tsc --noUnusedLocals`, ESLint, dependency analyzer 실행이 필요하다.
- 현재 문서는 소스 수정 없이 정적 검색 근거만 기록한다.
