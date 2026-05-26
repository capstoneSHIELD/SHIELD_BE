# Phase P5 — AI Pipeline Upgrade (Master Index)

## 메타
- 기간: 3주 (Sprint 1A → 1B → 2 → 3)
- 코드 범위: `org.example.shield.ai.*`, `org.example.shield.consultation.application.MessageService`
- 의존: Phase C-5 완료, P4 (rag-quality-loop) 기준선 유지
- 상위 plan: `C:\Users\to264\.claude\plans\ai-giggly-mccarthy.md`

## 1. 목표

본 phase의 목표는 **품질 개선이 아니라 측정 가능한 상태에서의 안전한 rollout**이다.
- Cohere 호출의 토큰/비용/지연 메트릭 Prometheus 노출
- 쿼리 임베딩 cache 재도입 (Caffeine L1)
- Intent-aware retrieval / Rerank / Context budget의 단계적 활성 (shadow 우선)
- 평가셋 v1.6으로 intent/low-evidence/mixed 커버리지 확보
- Provider interface 추상화로 Cohere lock-in 완화 (Phase 3 A/B 진입 비용↓)

## 2. 비목표

- LLM provider 교체 (Phase 3로 분리)
- Redis L2 cache (Caffeine L1만)
- Corpus 재구축 / 재색인
- RRF production enforcement (offline only)
- Context budget production enforcement (shadow only)
- 법률 답변 정책 변경

## 3. Sub-Phase 목록

| ID | 제목 | 문서 | 진행 | 산출물 |
|---|---|---|---|---|
| **P5.1** | Observability + 측정 인프라 | [ai-rag-phase-p5_1-observability-baseline.md](./ai-rag-phase-p5_1-observability-baseline.md) | ✅ 6/6 commits | mode enum + Converter, provider interface 4개, embed token plumbing, Cohere metric (token/cost/latency) + emitter 추출 + pricing yaml 외부화, Gate shadow mode, PR template + Grafana JSON + Prometheus snippets |
| **P5.2** | 평가 인프라 + Baseline | [ai-rag-phase-p5_2-evaluation-baseline.md](./ai-rag-phase-p5_2-evaluation-baseline.md) | ✅ 5/5 commits | RagEvalItem v1.6 (3개 신규 필드), Validator low_evidence 허용, eval-set.v1.6 50건, CitationCoverageEvaluator (reference mention coverage), PiiMasker 추출 + Sampler, baseline 인프라 보고서 |
| **P5.3** | 비용/성능 Quick Wins | [ai-rag-phase-p5_3-cost-routing.md](./ai-rag-phase-p5_3-cost-routing.md) | ✅ 5/5 commits | EmbeddingCache 인터페이스 + Noop + Caffeine, QueryEmbeddingService 캐시 통합, IntentAwareRetrievalPolicy shadow mode, **ASK_LEGAL_ADVICE skip 절대 금지 + GREETING-only enforce**, RagContextBuilder budget shadow |
| **P5.4** | 품질 실험 (RAG 검색 레이어) | [ai-rag-phase-p5_4-quality-experiments.md](./ai-rag-phase-p5_4-quality-experiments.md) | ✅ 6/6 commits | CohereRerankClient + Adapter, RerankingService shadow/sampled/enforce, Auto-OFF 회로 차단기, RRF path-specific repo + OfflineRrfRetrievalService (production guard), 4-mode 비교 보고서 |
| **P5.5** | **Cohere × HyperCLOVA X 하이브리드** (생성·판정 레이어) | [ai-rag-phase-p5_5-hyperclova-hybrid.md](./ai-rag-phase-p5_5-hyperclova-hybrid.md) | ⏸️ 미시작 (분석 문서만) | AiJudgeClient → OutputComplianceShadowJudge judge 본체 (HyperCLOVA) → Chat/Brief shadow 비교 → 최종 결정 |

**구현 노트 (2026-05-26 refine)**: P5.1/P5.2 모두 명세 부합. 일부 구현은 명세보다 보수적으로 조정:
- P5.1-C3: Breaking 시그니처 변경 → Non-breaking 신규 메서드 추가 (ingest 서비스 blast radius 회피)
- P5.1-C4: pricing 외부화 + CohereMetricEmitter 추출 (중복 제거)
- P5.2-C2: jsonl 50건 (v1.5 schema 비호환으로 변환 보류)
- P5.2-C4: PiiMasker NAME/ADDRESS 제외 (regex false positive)
- P5.2-C5: 실측 baseline 수치는 production DB 의존 — 인프라만 완성

## 4. Commit 분해 요약 (22개)

| Phase | Commit 수 | 작업 요약 |
|---|---|---|
| **P5.1 Observability** (Sprint 1A, ~3-4일) | 6 | RagFeatureMode enum / Provider interface / Embed plumbing / Cohere metric / Gate shadow / PR Template+Grafana |
| **P5.2 Evaluation** (Sprint 1B, ~3-4일) | 5 | Eval schema v1.6 / Validator + 50건 보강 / CitationCoverageEvaluator / PII masking judge / Baseline 측정 |
| **P5.3 Cost+Routing** (Sprint 2, ~1주) | 5 | EmbeddingCache 뼈대 / CaffeineEmbeddingCache / Intent-aware shadow / GREETING enforce / Context budget shadow |
| **P5.4 Quality Experiments** (Sprint 3, ~1주) | 6 | CohereRerankClient / RerankingService shadow / Sampled 30% / RRF path repos / RRF 분기 / 4-mode offline report |

## 5. PR Blocking Rules (전체 공통)

```text
nDCG@5 drop > 0.02 -> block
Recall@5 drop > 1%p -> block
answer compliance pass rate drop > 3%p -> block (baseline 확보 후 활성)
reference mention coverage drop > 5%p -> block (baseline 확보 후 활성)
legal advice false-skip rate > 1% -> block
p95 RAG latency > 8s -> block or keep in shadow
rerank fallback rate > 5% -> force rerank mode=off (자동)
```

## 6. Master 정책 요약

이전 v3 plan의 결정사항은 모두 유지:
- **3주 목표는 production 전환이 아님**: 측정 가능 상태 + 일부 rollout + 근거 기반 판단
- **Cohere 유지, lock-in만 완화**: Provider interface (`AiChatClient`, `AiEmbeddingClient`, `AiRerankClient`, `AiClassificationClient`)
- **(P5.5 신설) Cohere × HyperCLOVA X 하이브리드**: RAG 백본은 Cohere 유지, 생성·판정 레이어만 HyperCLOVA X 후보 평가 (역할 분리). Judge 우선 → Chat/Brief는 데이터 축적 후 결정.
- **Mode-based flag (`off|shadow|sampled|enforce`)**: enum + startup fail-fast
- **Conversation-id deterministic sampling**
- **Context budget enforcement는 본 plan에서 제외** (shadow까지만)
- **GREETING-only skip enforce**, IRRELEVANT는 보수적 보류
- **RRF production enforcement는 본 plan에서 제외** (offline only)
- **ASK_LEGAL_ADVICE skip 금지**
- **Reference mention coverage** 보수적 명명
- **Shadow judge PII masking**

## 7. Rollback 방침

런타임 flag 변경 불가 (Spring Cloud Config 없음, `/refresh` 미노출). 비상 시:
1. application.yml flag 값 변경 (mode → off)
2. CI/CD 재배포
3. 컨테이너 재시작

ETA ~10분. 회로 차단기 자동화로 보완.

## 8. Open Questions (해결 완료)

| ID | 질문 | 결론 |
|---|---|---|
| Q1 | Runtime flag 변경 가능? | ❌ 불가능, redeploy 필요 |
| Q2 | Admin/config 롤백 메커니즘? | ❌ 없음, 회로 차단기로 보완 |
| Q3 | Citation/compliance baseline? | ⚠️ 메트릭 부재, P5.2에 흡수 |
| Q4 | 평가셋 intent/low-evidence/mixed? | ❌ 누락, P5.2에 흡수 |
| Q5 | Cohere embed 토큰 정확값? | ✅ 가능, plumbing 미완료 → P5.1 |
| Q6 | Rerank 예산/sampling? | 적극적 (shadow 30%, sampled 30%) |
