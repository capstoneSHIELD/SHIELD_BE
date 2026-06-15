# ParaView API Alignment Phase Plan

이 문서는 `.codex/ref_docs/backend_api.md` 명세에 맞춰 pfm-FE 프론트엔드 구현을 정렬하기 위한 phase 계획이다.

주의: 일반 저장소 규칙상 `.codex/ref_docs`는 사용자 관리 참고자료 공간이며 생성 프로젝트 명세의 공식 위치는 `docs/`이다. 다만 이번 작업에서는 사용자가 이 경로를 명시했으므로 현재 요청 우선순위에 따라 이 위치에 작성한다.

## 기준 원칙

- UI, Route, Page 컴포넌트에는 API URL 조립과 비즈니스 판단을 추가하지 않는다.
- API 호출은 `lib/api/*` 계층으로 모으고, 공통 인증/refresh/error 처리는 `lib/apiClient.ts`를 사용한다.
- DTO, domain state, view model을 섞지 않는다.
- backend API 명세에 없는 Lab Server 직접 연동은 별도 adapter로 격리한다.
- 명세가 불명확한 외부 연동은 TODO와 경계만 두고 임의 구현하지 않는다.

## Phase 목록

1. `phase-0-contract-map.md`: backend API와 현재 프론트 구현 매핑 문서화
2. `phase-1-api-client-layer.md`: API client 계층 정리
3. `phase-2-chat-session-contract.md`: 채팅 세션 API 누락분 보강
4. `phase-3-job-monitoring.md`: job 상태 조회, sync 정책, monitor WS 정합화
5. `phase-4-result-explorer.md`: 결과 상세, 필드, 파일, 다운로드 기능 보강
6. `phase-5-visualization-contract.md`: 시각화 생성/제어/스크린샷/WS 명세 반영
7. `phase-6-error-experience.md`: 명세 기반 오류 표시 개선
8. `phase-7-tests-docs.md`: 테스트와 공식 docs 갱신

## 추천 실행 순서

1. Phase 0으로 기준표를 먼저 확정한다.
2. Phase 1에서 직접 fetch와 API URL 조립을 API 계층으로 이동한다.
3. Phase 3과 Phase 5를 먼저 처리해 시뮬레이션 실행/시각화 핵심 흐름을 안정화한다.
4. Phase 4로 결과 탐색 기능을 사용자 화면에 확장한다.
5. Phase 2, Phase 6, Phase 7로 누락 API, 오류 UX, 테스트/문서를 마무리한다.

