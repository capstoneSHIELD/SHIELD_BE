# Phase P5.5 Commit 5 — Cohere × HyperCLOVA X 최종 결정 보고서 (템플릿)

> **상태 (2026-05-26)**: 인프라 완비, 결정은 [p5_5-judge-shadow.md](./p5_5-judge-shadow.md) 1~2주 데이터 + [eval_chat.py](../../../scripts/eval_chat.py) 오프라인 비교 결과 누적 후 작성.

## 1. 의사결정 입력

| 입력 | 출처 | 상태 |
|---|---|---|
| Judge shadow 1주 운영 데이터 | `p5_5-judge-shadow.md` §5 | ⏳ 운영 후 |
| Judge 표본 검토 30~50건 | `p5_5-judge-shadow.md` §4 | ⏳ 운영 후 |
| Chat shadow 오프라인 비교 50~100건 | `eval_chat.py` → `p5_5-chat-shadow.md` | ⏳ 실행 후 |
| LLM-as-judge 톤·정확성 점수 | Claude/GPT-4 후처리 | ⏳ 실행 후 |
| 비용 분석 (월 추정) | Grafana + pricing yaml | ⏳ 운영 후 |

## 2. 결정 트리 (Decision Tree)

### 2.1 Judge 결정

```
HyperCLOVA Judge가:
  ① regex 못잡는 case 의미 있게 잡고 (추가 감지율 5~20%)
  ② FALSE_POSITIVE rate < 10%
  ③ p95 latency ≤ 3s + 일일 비용 ≤ $5
─────────────────────────────────────────────────────
  ✅ 3개 모두 만족 → Judge production 활성화
                    (sampling 5% → 10% → enforce는 별도 phase)
  ⚠️ 1개 미달        → 프롬프트/모델 튜닝 후 재시도
  ❌ 2개 이상 미달  → Judge mode=off
```

### 2.2 Chat 결정

```
HyperCLOVA Chat이 (eval_chat.py 결과 기준):
  ① LLM-as-judge 톤 점수 ≥ Cohere + 0.05
  ② guardrail_hit_rate ≤ Cohere (안전성 미회귀)
  ③ p95 latency ≤ Cohere + 400ms
  ④ 비용 (월 추정) 허용 범위
─────────────────────────────────────────────────────
  ✅ 4개 모두 만족 → Chat sampled rollout (별도 phase)
  ⚠️ ①만 우위        → Brief만 전환 검토
                       (Brief는 단일 turn, 톤이 더 중요)
  ❌ ① 미달          → Chat = Cohere 유지
```

### 2.3 종합 결정 매트릭스

| Judge ↓ \ Chat → | Chat 우위 | Chat 무차이 | Chat 열위 |
|---|---|---|---|
| **Judge 활성** | 둘 다 적용 (별도 phase) | Judge만 활성 | Judge만 활성 |
| **Judge 보류** | Chat만 sampled | 둘 다 유지 | 둘 다 유지 |
| **Judge OFF** | Chat sampled (안전 가드 추가) | Cohere 유지 | Cohere 유지 |

## 3. 결정 (작성 시점)

```
[2026-06-XX 결정]
참여자: __
근거 문서: p5_5-judge-shadow.md (커밋 ___), p5_5-chat-shadow.md (커밋 ___)

Judge:    [ ] 활성 / [ ] 보류 / [ ] OFF
Chat:     [ ] sampled / [ ] Brief만 / [ ] 유지
Cohere lock-in 완화:  Provider interface 확정 ✅ (P5.1 Commit 2 완료)

근거 요약:
- ...
- ...

후속 phase:
- ...
```

## 4. 비목표 재확인 (변경 없음)

- ❌ RAG 백본 (embed/rerank/classify) provider 교체 — Cohere 유지
- ❌ DB 스키마 변경 — `vector(1024)` 그대로
- ❌ HyperCLOVA X로 100% 전환 — 모든 변경은 sampled까지만, enforce는 별도 phase

## 5. Rollback 시나리오

Judge 활성 후 회귀 발견 시:
1. `AI_OUTPUT_JUDGE_SHADOW_ENABLED=false` → 재배포
2. P5.5 commit 2 도입 코드는 그대로 유지 (yml flag만 변경)
3. Commit 5 결정 문서를 update — 어떤 사유로 OFF 되돌렸는지 기록

Chat 활성 후 회귀:
1. `AI_CHAT_PROVIDER=cohere` → 재배포
2. shadow compare는 그대로 유지 (sampling 0으로)

## 6. 미해결 질문 (해당 단계에서 답)

- Q1. HyperCLOVA pricing은 추정값 — Naver Cloud 정식 단가 확인 필요
- Q2. 표본 검토 검토자 풀 (변호사 1명 + 운영 1명 권장) — 절차 확정
- Q3. enforce 모드 도입 시 PR Blocking Rules에 `judge false-positive < 10%` 추가 여부
- Q4. HyperCLOVA Chat이 user-facing이 되면 prompt 다국어 정합성 (system prompt re-tuning) 필요 — 별도 spike
