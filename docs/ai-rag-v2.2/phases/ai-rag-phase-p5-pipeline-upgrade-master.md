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

| ID | 제목 | 문서 | 산출물 |
|---|---|---|---|
| P5.1 | Observability + 측정 인프라 | [ai-rag-phase-p5_1-observability-baseline.md](./ai-rag-phase-p5_1-observability-baseline.md) | 메트릭 노출, mode enum, gate shadow, provider interface |
| P5.2 | 평가 인프라 + Baseline | [ai-rag-phase-p5_2-evaluation-baseline.md](./ai-rag-phase-p5_2-evaluation-baseline.md) | 평가셋 v1.6, citation metric, PII masking, baseline 보고서 |
| P5.3 | 비용/성능 Quick Wins | [ai-rag-phase-p5_3-cost-routing.md](./ai-rag-phase-p5_3-cost-routing.md) | Caffeine cache, GREETING skip, context budget shadow |
| P5.4 | 품질 실험 | [ai-rag-phase-p5_4-quality-experiments.md](./ai-rag-phase-p5_4-quality-experiments.md) | Rerank shadow/sampled, RRF offline 비교 |

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
