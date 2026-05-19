# SHIELD AI/RAG v2.2 Rollout Checklist

작성일: 2026-05-17

## 목적

P1부터 P4까지 구현된 기능을 운영에 순차 적용하기 위한 최종 체크리스트다. 기본 원칙은 모든 위험 기능을 기본 비활성 상태로 두고, shadow/eval 결과를 확인한 뒤 작은 단위로 켜는 것이다.

## 현재 기본값

| 기능 | 기본값 | 이유 |
|---|---|---|
| Slot ledger | `app.ai.slot-ledger.enabled=true` | 반복 질문 완화의 기반이며 DB 저장 구조가 명시적이다. |
| Cohere structured output | `app.ai.cohere.structured-output-enabled=true` | 파싱 안정화 목적. fallback parser는 유지한다. |
| OpenAI structured output | `app.ai.openai.structured-output-enabled=true` | classifier schema 안정화 목적. 운영 모델명 검증은 별도 체크. |
| Intent router shadow | `app.ai.intent-router.shadow-mode=true` | skip/auto-update는 운영 검증 전 log-only. |
| Intent skip flags | `false` | 오분류 시 Cohere 생략 위험이 있어 단계적 활성화. |
| Slot auto-update | `false` | slot 오염 방지를 위해 shadow eval 후 활성화. |
| Dynamic plan | `false` | P3 정규화 테이블은 준비하되 운영 write는 검증 후 활성화. |
| RAG fusion mode | `weighted` | 현행 weighted hybrid retrieval을 baseline으로 유지. |
| Retrieval gate | `false` | calibration 결과 없이 문서를 drop하지 않는다. |
| Intent-aware retrieval | `false` | P2 intent confidence 안정화 후 활성화. |
| Output judge shadow | `false` | 비용, 지연, PII masking 기준 확인 후 shadow만 활성화. |

## 활성화 순서

1. P1 structured output과 prompt block을 운영 로그에서 확인한다.
2. P1.5 slot ledger가 반복 질문률을 낮추는지 확인한다.
3. P2 intent router는 2주 shadow logging 후 `ASK_LEGAL_ADVICE` skip부터 켠다.
4. `GREETING`, `IRRELEVANT`, `CONFIRM` skip을 순서대로 켠다.
5. slot auto-update는 precision 95% 이상일 때만 켠다.
6. P3 dynamic plan은 validator false positive와 plan 재생성률을 확인한 뒤 일부 도메인부터 켠다.
7. P4 RRF/rerank는 eval에서 weighted baseline 대비 Recall@5 -2%p 이상 하락하지 않을 때만 적용 후보로 둔다.
8. retrieval gate는 method별 calibrated threshold가 정해진 뒤 켠다.
9. output judge는 p95 지연 +200ms 이내, 전체 LLM 비용 +10% 이내, PII masking 통과 후 shadow만 켠다.

## 배포 전 필수 확인

- 운영 OpenAI 계정에서 `OPENAI_CLASSIFY_MODEL`과 `OPENAI_CLASSIFY_REASONING_EFFORT`가 실제 사용 가능한 값인지 공식 API 기준으로 재확인한다.
- Flyway migration `V14__add_slot_state_to_consultations.sql`, `V15__create_dynamic_plan_tables.sql` 적용 순서를 확인한다.
- `app.ai.dynamic-plan.enabled=false`, `app.ai.rag.retrieval-gate.enabled=false`, `app.ai.output-judge.shadow-enabled=false` 기본값을 유지한다.
- 법무 검토가 필요한 fixed response template 문구를 배포 전에 승인받는다.
- 운영 dashboard에서 JSON parse failure, Cohere skip false positive, slot auto-update precision, retrieval false drop rate를 볼 수 있어야 한다.

## 롤백 기준

| 기능 | 롤백 기준 | 조치 |
|---|---|---|
| Structured output | JSON parse failure > 1% 또는 AI API 4xx/5xx > 5%가 10분 지속 | structured output flag off |
| Guardrail | false positive rate > 2% | pattern 완화 또는 이전 profile 복구 |
| Slot ledger | slot 오염률 > 1% 또는 p95 latency +300ms 초과 | `app.ai.slot-ledger.enabled=false` |
| Intent skip | skip false positive rate > 0.5% | 해당 intent flag off, shadow mode 복귀 |
| Slot auto-update | precision < 95% | `enable-slot-auto-update=false` |
| Dynamic plan | validator false positive > 5% 또는 plan 재생성률 > 30% | `app.ai.dynamic-plan.enabled=false` |
| Retrieval gate | false drop rate > 2% 또는 Recall@5 baseline 대비 -2%p | gate off, `fusion-mode=weighted` |
| Output judge | p95 latency +200ms 초과 또는 비용 +10% 초과 | `app.ai.output-judge.shadow-enabled=false` |

## 검증 명령

```powershell
.\gradlew.bat test
```

## YAML Scope Regression 추가 확인

새 체크리스트 YAML 구조는 `L1 > L2 > L3` 계층과 optional `ai/checklists/nodes/<node-id>.yaml` override를 지원한다. staging 배포 전 아래 시나리오를 확인한다.

| 시나리오 | 확인 내용 |
|---|---|
| L1-only 상담 | prompt, coverage, slot ledger가 L1 공통 항목만 사용 |
| L2 확정 상담 | L1 공통 + 해당 L2 focus만 사용하고 형제 L2는 제외 |
| L3 확정 상담 | L1 공통 + 해당 L2 focus + 해당 L3 items만 사용 |
| L1 → L3 narrowing | 기존 collected/pending/asked 상태 보존, 새 L3 slot 추가, 이전 missing slot은 out-of-scope 처리 |
| node override | prompt와 coverage가 같은 override item set을 사용 |
| correctedSlots | stable slot id 또는 legacy `static_001` fallback으로만 correction 처리 |

관련 phase 문서: `docs/ai-rag-v2.2/phases/ai-rag-phase-p1_6-yaml-scope-hardening.md`

마지막 통합 검증: 2026-05-17, `BUILD SUCCESSFUL`.
