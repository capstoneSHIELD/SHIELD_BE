# Session 3 Component 계층 리뷰 문서

## 목적

이 디렉터리는 Session 3에서 확인한 frontend component 계층 코드리뷰 결과를 이후 리팩토링 세션에서 재사용하기 위한 기준 문서이다.

## 분석 범위

- `components/**` 전체 component 구조
- page/container에서 호출되는 feature component 흐름
- 공통 UI component와 feature 전용 component의 구분
- form, modal/dialog, table/list, viewer 계열 component의 책임 분리
- props/state/event handler 흐름과 렌더링 성능 후보

## Session 1/2 연결

- Session 1의 핵심 이슈인 `Simulation2Page` 대형 container 문제는 Session 3에서 하위 component 책임과 props 흐름 관점으로 연결했다.
- Session 2의 route/page/container 이슈인 `AdminPage3`, CMS board/edit 계열 문제는 Session 3에서 component 단위 분리 후보로 재정리했다.
- 이전 세션의 전체 구조를 그대로 복사하지 않고, component 리팩토링에 필요한 부분만 요약했다.

## 문서 구성

| 문서 | 역할 |
|---|---|
| `component-inventory.md` | 주요 component 목록과 분류 |
| `component-hierarchy.md` | page/container에서 하위 component로 이어지는 호출 구조 |
| `component-responsibility-review.md` | component 책임 분리 관점의 주요 리뷰 |
| `props-and-state-flow.md` | props, event handler, local state 흐름 |
| `rendering-performance-review.md` | key, re-render, 계산/목록 성능 후보 |
| `component-quality-review.md` | file size, 접근성, props type, loading/error 등 품질 이슈 |
| `session3-findings.md` | Session 3 종합 findings |
| `refactoring-brief.md` | 이후 리팩토링 작업 지침 |
| `next-session-prompt.md` | Session 4 시작용 프롬프트 |

## 리팩토링 세션 참고 순서

1. `session3-findings.md`
2. `refactoring-brief.md`
3. `component-hierarchy.md`
4. `props-and-state-flow.md`
5. `component-responsibility-review.md`
6. `rendering-performance-review.md`
7. `component-quality-review.md`
8. `component-inventory.md`

## 주의

- 이 문서는 코드리뷰 결과 문서이며 frontend 소스 코드를 변경하지 않았다.
- 실제 코드 근거가 부족한 항목은 `확인 필요`로 표시했다.
- `.codex/ref_docs`는 사용자 관리 참고자료 위치이며 프로젝트 공식 명세 위치가 아니다.
