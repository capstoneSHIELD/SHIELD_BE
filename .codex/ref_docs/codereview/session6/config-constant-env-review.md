# Config / Constant / Env Review

| ID | 심각도 | 영역 | 파일 경로 | 라인 | 문제 | 영향 | 개선 방향 |
|---|---|---|---|---:|---|---|---|
| S6-ENV-001 | High | env | `lib/supabaseClient.ts` | 5-6 | Supabase public env를 non-null assertion으로 직접 읽는다. | 배포 누락 시 명확한 config error 없이 초기화 실패 가능성이 있다. | `getRequiredPublicEnv('NEXT_PUBLIC_SUPABASE_URL')` 같은 helper로 검증한다. |
| S6-ENV-002 | High | env | `components/pages/ContactPage.tsx` | 26-29 | EmailJS env를 UI component에서 직접 non-null assertion으로 사용한다. | env 누락이 사용자의 submit 시점 오류로 노출될 수 있다. | `lib/config/emailjs.ts` 같은 config boundary로 분리하고 fallback UI를 둔다. |
| S6-CONFIG-001 | Medium | config | `lib/apiClient.ts` | 217-231 | `NEXT_PUBLIC_PFM_API_URL`과 `NEXT_PUBLIC_PFM_LLM_URL` fallback을 사용하지만 required error는 `NEXT_PUBLIC_PFM_API_URL`만 안내한다. | 실제 사용 env와 안내 env가 어긋날 수 있다. | canonical env를 하나로 정하고 legacy fallback이면 주석/문서로 명시한다. |
| S6-CONFIG-002 | Medium | security | `next.config.ts` | 4-8 | `images.remotePatterns`가 모든 HTTPS host를 허용한다. | 이미지 출처 정책이 느슨하고 운영 환경별 허용 도메인 추적이 어렵다. | 실제 CMS/CDN/domain 목록으로 제한한다. |
| S6-CONFIG-003 | Low | config | `next.config.ts` | 14-19 | `experimental` 주석과 `CDN_IMG_PREFIX` env 설명에 인코딩 깨짐이 있다. | 설정 의도 파악이 어려워진다. | 주석을 UTF-8 한국어 또는 영어로 정리한다. |
| S6-CONST-001 | Medium | constant | `components/pages/Simulation2Page.tsx` | 545-547 | job polling interval, ws throttle, reconnect delay가 page 파일에 정의되어 있다. | workflow hook 분리 시 설정이 page와 함께 움직인다. | simulation workflow config constant로 이동한다. |
| S6-CONST-002 | Low | constant | `components/simulation/VisualizationControlBar.tsx`, `components/simulation/trame/TrameControlPanel.tsx`, `components/simulation/trame/CompositeDialog.tsx` | 29, 33, 37 | colormap 옵션이 여러 파일에 하드코딩되어 있다. | 옵션 변경 시 UI별 목록이 달라질 수 있다. | 공통 옵션이면 shared constant로, 도메인별이면 이름으로 구분한다. |
| S6-MAGIC-001 | Low | magic number | `components/simulation/SessionListCard.tsx`, `components/simulation/SimulationListCard.tsx` | 44, 28-30 | page size/fetch size가 component 내부 constant로 박혀 있다. | 목록 정책 변경 시 컴포넌트마다 찾아 수정해야 한다. | feature config 또는 props 기본값으로 분리한다. |
| S6-MAGIC-002 | Low | polling interval | `components/pages/adminPolling.ts` | 4 | admin active job refetch interval이 10초로 고정되어 있다. | 운영 정책 변경 시 재빌드 필요. 현재는 pure helper로 분리되어 있어 영향은 낮다. | 필요 시 config constant로 승격한다. |

## 확인 필요

- `NEXT_PUBLIC_LAB_SERVER_API_KEY`, `NEXT_PUBLIC_PFM_AUTH_TOKEN`은 테스트 파일에서 확인되지만 실제 runtime client에서 사용하는지는 추가 확인 필요하다.
- 이미지 remote pattern을 모든 host로 둔 것이 CMS 요구사항인지 확인 필요하다.
