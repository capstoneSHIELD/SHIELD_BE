# Frontend 코드리뷰 Session 1 문서

## 목적

이 문서는 pfm-FE frontend 코드리뷰 Session 1에서 파악한 전체 구조, 아키텍처 맵, 책임/의존성 분석, 주요 문제 후보를 이후 코드리뷰 및 리팩토링 세션에서 재사용하기 위해 정리한 기준 문서다.

`.codex/ref_docs`는 사용자 관리 참고자료 공간이므로, 이 문서는 프로젝트 명세가 아니라 리팩토링 세션을 위한 참조 자료로 취급한다.

## 분석 범위

- Next.js App Router 진입점: `app/**/page.tsx`, `app/layout.tsx`, `app/providers.tsx`
- 페이지 컨테이너: `components/pages/*`
- PFM simulation/admin 관련 컴포넌트: `components/simulation/*`, `components/pages/simulation2/*`
- API/service 계층: `lib/apiClient.ts`, `lib/auth.ts`, `lib/api/*`
- CMS/Supabase 직접 연동 페이지: `components/pages/HomePage.tsx`, `components/pages/NoticeBoardPage.tsx`, `components/pages/AdminPage2.tsx`, `components/pages/EditMemberPage.tsx`
- legacy AI/외부 연동: `api/chat.js`, `lib/api/legacyAiChat.ts`, `components/AIChatAssistant.tsx`, `components/pages/ContactPage.tsx`
- 타입/테스트/설정: `tsconfig.json`, `vitest.config.ts`, `scripts/check-pfm-api-boundaries.mjs`

## 문서 구성

| 파일 | 역할 |
|---|---|
| `README.md` | 세션 1 문서의 목적, 범위, 읽는 순서 |
| `frontend-structure.md` | frontend 디렉터리와 주요 모듈 분류 |
| `domain-map.md` | 주요 기능/도메인 단위와 책임, 의존 대상 |
| `dependency-flow.md` | Route/Page부터 API/backend까지의 실제 의존성 흐름 |
| `oop-architecture-review.md` | 책임 분리, 응집도, 결합도, 추상화 수준 평가 |
| `session1-findings.md` | 세션 1 주요 문제 후보와 리팩토링 방향 |
| `refactoring-brief.md` | 이후 리팩토링 세션에서 바로 사용할 작업 지침 |
| `next-session-prompt.md` | 다음 세션에 붙여넣어 사용할 프롬프트 |

## 리팩토링 세션 참고 순서

1. `README.md`
2. `refactoring-brief.md`
3. `session1-findings.md`
4. `dependency-flow.md`
5. `oop-architecture-review.md`
6. `frontend-structure.md`
7. `domain-map.md`
8. `next-session-prompt.md`

## 근거 표기 기준

- `파일:라인` 형식은 Session 1에서 실제 확인한 코드 근거다.
- `확인 필요`는 Session 1 범위에서 충분히 검증하지 못했거나 런타임/운영 설정 확인이 필요한 항목이다.
- 개선 방향은 리뷰 관점의 제안이며, 코드 수정은 이 문서 생성 작업에서 수행하지 않았다.
