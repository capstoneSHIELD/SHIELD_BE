# Rendering Performance Review

| ID | 심각도 | 파일 경로 | 라인 | component | 성능 이슈 | 영향 | 개선 방향 |
|---|---|---|---:|---|---|---|---|
| S3-PERF-001 | Medium | `components/pages/Simulation2Page.tsx` | 3355 | `Simulation2Page` | chat messages 렌더링에서 `messages.map((msg, index) => ...)`와 `key={index}`가 사용된다. system/user branch도 `components/pages/Simulation2Page.tsx:3360`, `3368`에서 index key를 사용한다. | 메시지 삽입/삭제/복원 시 React reconciliation이 불안정해질 수 있고, message row state가 생기면 잘못 재사용될 수 있다. | `ChatMessage`에 stable id를 부여하거나 timestamp+role+sequence 기반 key를 사용 |
| S3-PERF-002 | Medium | `components/pages/Simulation2Page.tsx` | 3419 | `Simulation2Page` | job event log에서 `workflow.events.slice(-5).map((evt, i) => ...)`와 `key={i}`를 사용한다. | event 순서 변경/중간 삽입 시 row 재사용이 불안정할 수 있다. | event id가 있으면 사용하고, 없으면 backend event timestamp/type/message 조합 key 검토 |
| S3-PERF-003 | Medium | `components/pages/AdminPage3.tsx` | 483 | `AdminPage3` | 2942 line 대형 component 내부에 query state, table rendering, dialog rendering, inline handlers가 집중되어 있다. | 특정 tab/dialog/form state 변경이 대형 JSX tree와 함께 평가되어 성능 튜닝 지점이 불명확하다. | tab별 component를 분리하고 heavy table/detail 영역을 독립 memoization 단위로 만들기 |
| S3-PERF-004 | Low | `components/simulation/SimulationListCard.tsx` | 65 | `SimulationListCard` | `listSimulations({ size: FETCH_SIZE })`로 최대 100개를 받아 `slice`로 client pagination한다. | 데이터가 더 늘거나 refresh가 잦아지면 네트워크/렌더 비용이 증가한다. 현재 명세상 최대 100개라 즉시 위험은 제한적이다. | server pagination 또는 query param 기반 pagination 전환 여부 확인 |
| S3-RENDER-001 | Low | `components/ImageCarousel.tsx` | 32 | `ImageCarousel` | `items.map((item, index) => <CarouselItem key={index}>` 형태의 index key 사용. | media 순서 변경/삽입 시 slide state가 잘못 재사용될 수 있다. | `item.url` 또는 url+type 기반 stable key 사용 |
| S3-RENDER-002 | Low | `components/ResearchPageTemplate.tsx` | 142 | `ResearchPageTemplate` | `research_sections?.map((section, index) => <ScrollAnimation key={index}>` 형태의 index key 사용. | CMS section reorder 시 animation/component state 재사용이 불안정할 수 있다. | section id가 없으면 heading+index 임시 key, 장기적으로 CMS section id 도입 |
| S3-RENDER-003 | Suggestion | `components/ResearchHighlightsSlider.tsx` | 80 | `ResearchHighlightsSlider` | `slideVariants`, `contentVariants` object가 매 render마다 새로 생성된다. | 현재 규모에서는 심각도 낮지만 motion subtree가 커지면 memoization이 어려워진다. | static variants를 component 밖으로 이동하거나 `useMemo` 검토 |
| S3-RENDER-004 | Suggestion | `components/pages/NewsPage.tsx` | 65 | `NewsPage` | `renderPageNumbers`와 pagination click handler가 매 render마다 재생성된다. | 현재 page number가 작아 즉시 영향은 낮다. list가 커지면 pagination rendering 최적화 여지가 있다. | 공통 pagination presenter로 추출하고 handler를 상위에서 안정화 |

## 성능 관찰

- `VisualizationControlBar`는 props 기반 intent UI라 비교적 가볍고, API 호출을 직접 하지 않는다.
- `ResultExplorerPanel`은 field/file API 결과를 최대 100개 단위로 다루는 구조라 대량 파일 결과가 늘어날 경우 별도 list virtualization 여부를 확인해야 한다.
- `components/reactbits/*`와 3D/viewer 계열은 시각적 비용이 클 수 있으나 이번 Session 3에서는 product workflow component를 우선했다. 별도 performance audit 확인 필요.
