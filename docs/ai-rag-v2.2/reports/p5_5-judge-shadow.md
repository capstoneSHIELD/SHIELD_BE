# Phase P5.5 Commit 3 — HyperCLOVA X Judge Shadow 보고서 (인프라 + 데이터 수집 가이드)

> **상태 (2026-05-26)**: 인프라 완비. 실측 verdict 분포 / 표본 검토 결과는 1~2주 운영 후 본 보고서 하단에 추가 기재.

## 1. 목표

P5.5 Commit 2에서 도입된 LLM judge (HyperCLOVA X HCX-005)가 production shadow mode로 동작할 때:

1. **GuardrailFilter regex로 잡히지 않는 의미론적 법적 단정**을 얼마나 추가로 감지하는지
2. **False positive rate** (정상 응답을 violation으로 잘못 판정한 비율)
3. **운영 비용·지연 영향**이 sampling rate 1% 수준에서 허용 범위인지

데이터 1~2주 축적 후 위 3개 질문에 답한다.

## 2. 활성화 절차 (1주 후 실행)

### 2.1 application.yml 환경변수

```
AI_OUTPUT_JUDGE_SHADOW_ENABLED=true
AI_OUTPUT_JUDGE_SAMPLING_RATE=0.01           # 1%
AI_OUTPUT_JUDGE_MAX_P95_LATENCY_INCREASE_MS=200
AI_OUTPUT_JUDGE_MAX_COST_RATIO=0.10
AI_OUTPUT_JUDGE_MAX_COST_PER_DAY_USD=5.0     # 본 plan은 메트릭만, 자동 OFF는 후속 phase
AI_OUTPUT_JUDGE_PROVIDER=hyperclova
HYPERCLOVA_API_KEY=<naver-clova-studio-key>
HYPERCLOVA_JUDGE_MODEL=HCX-005
```

### 2.2 활성화 후 24시간 가드 체크

```
# p95 judge latency
shield_ai_judge_latency_seconds{provider="hyperclova",status="success",quantile="0.95"} < 3

# judge 실패율 (5분 window)
sum(rate(shield_ai_judge_latency_seconds_count{status="failure"}[5m]))
  / sum(rate(shield_ai_judge_latency_seconds_count[5m])) < 0.05

# user-facing latency 회귀 (consultation_turn_latency)
diff(p95 before-vs-after) < 200ms
```

가드 한 개라도 위반 시 즉시 `AI_OUTPUT_JUDGE_SHADOW_ENABLED=false`로 redeploy.

## 3. Grafana 패널 정의 (Prometheus 쿼리)

본 phase에서는 Grafana dashboard JSON을 별도 commit으로 두지 않고 PromQL 스니펫만 기록한다.
P5.1 Commit 6의 `dashboards/grafana-p5-observability.json` 에 후속 PR에서 패널 추가 권장.

### 3.1 Judge verdict 분포

```promql
sum by (verdict) (
  rate(shield_ai_judge_outcome_total{provider="hyperclova"}[1h])
)
```

기대값: PASS ≫ SOFT_VIOLATION > HARD_VIOLATION. HARD가 빈번하면 ① 프롬프트 너무 엄격 ② 실제로 chat 톤이 단정적 — 표본 검토.

### 3.2 Judge confidence bucket

```promql
sum by (confidence) (
  rate(shield_ai_judge_outcome_total{provider="hyperclova",verdict!="fallback"}[1h])
)
```

`low` 비율이 높으면 모델이 판단을 못 하고 있음 → 프롬프트 명확화 후보.

### 3.3 Judge latency p50 / p95

```promql
histogram_quantile(0.50,
  sum by (le) (rate(shield_ai_judge_latency_seconds_bucket{provider="hyperclova",status="success"}[5m])))

histogram_quantile(0.95,
  sum by (le) (rate(shield_ai_judge_latency_seconds_bucket{provider="hyperclova",status="success"}[5m])))
```

기준: p95 ≤ 3s. 초과 시 sampling rate 강제 0으로 (현재는 manual yml + redeploy).

### 3.4 Judge 호출 실패율

```promql
sum(rate(shield_ai_judge_latency_seconds_count{provider="hyperclova",status="failure"}[5m]))
  /
sum(rate(shield_ai_judge_latency_seconds_count{provider="hyperclova"}[5m]))
```

기준: < 5%. 초과 시 fail-open으로 동작하지만 메트릭 신뢰도 저하 — 즉시 알림 후보.

### 3.5 일일 추정 비용 (HyperCLOVA)

본 plan에서는 application.yml `hyperclova.pricing.HCX-005`를 호출자가 곱해서 메트릭으로 emit하는 단계는 미구현.
임시로 `shield.ai.judge.outcome` 카운트 × `평균 토큰 추정값` × `단가` 식으로 dashboard 변수에서 계산.

향후 P5.5 Commit 5 결과에 따라 자동 cost 메트릭 (`shield.ai.judge.cost.estimated.usd`) 도입 검토.

### 3.6 GuardrailFilter regex 대비 추가 감지율

```promql
# judge가 violation 판정한 응답 중, 결정적 regex로 동시에 잡힌 비율의 inverse
# (= regex가 못 잡고 judge만 잡은 케이스)
sum(rate(shield_ai_judge_outcome_total{verdict=~"SOFT_VIOLATION|HARD_VIOLATION"}[1d]))
  /
sum(rate(shield_ai_output_judge_shadow_total{outcome="sampled"}[1d]))
```

기대 목표: 5~20% (regex가 못 잡는 의미론적 단정을 의미 있게 추가 감지).
0%에 가까우면 judge 도입 가치 없음 — Commit 5에서 OFF 결정.
50%+ 면 톤 회귀 의심 (chat 모델 자체 문제) 또는 judge 과도하게 엄격.

## 4. 표본 검토 절차 (1주 누적 후)

### 4.1 자동 추출 (DB or 로그)

`OutputComplianceResult.judgeResult` 가 non-null이고 `verdict` ∈ {SOFT_VIOLATION, HARD_VIOLATION} 인 케이스를 30~50건 수동 검토 대상으로 선별.

원본 응답은 **저장되지 않음** (PII 정책). `maskedText` + `hashedConversationId` + `reason` + `categories` 만으로 검토.

### 4.2 검토 시트 (수기 작성)

| hashedConvId | verdict | confidence | categories | maskedText (요약) | regex_violation 동시 hit? | 검토자 판정 |
|---|---|---|---|---|---|---|
| `a1b2c3d4` | HARD_VIOLATION | 0.91 | legal_conclusion | "...대항력이 [인정]됩니다..." | NO | TRUE_POSITIVE |
| `e5f6g7h8` | SOFT_VIOLATION | 0.55 | tendency_or_likelihood | "...일반적으로 [받아들여] 집니다..." | NO | FALSE_POSITIVE |

### 4.3 집계 지표

- **TRUE_POSITIVE rate**: judge가 violation으로 판정 + 검토자가 동의 — 클수록 좋음 (이상적 >70%)
- **FALSE_POSITIVE rate**: judge가 violation으로 판정 + 검토자가 부정 — 작을수록 좋음 (목표 <10%)
- **regex 동시 hit율**: regex로도 잡힌 비율 — 낮을수록 judge의 부가가치 큼

## 5. 실측 결과 (1~2주 후 작성)

```
[2026-06-XX update] 운영 기간: 2026-XX-XX ~ 2026-XX-XX (1주)
표본: N개 sampled responses

verdict 분포:
- PASS: __%
- SOFT_VIOLATION: __%
- HARD_VIOLATION: __%

표본 검토 (30~50건):
- TRUE_POSITIVE rate: __%
- FALSE_POSITIVE rate: __%
- regex 동시 hit율: __%

운영 영향:
- p95 latency 증가: __ms (기준 200ms 이내)
- 일일 비용 (추정): $__ (한도 $5)
- judge 호출 실패율: __% (기준 5%)

결론: __
```

## 6. PR Blocking Rules와의 정합

본 phase에서 도입되는 judge metric은 **shadow only**이므로 PR Blocking Rules에 직접 가드되지 않는다.
대신 Commit 5에서 enforce 결정이 나면 별도 phase에서:

- `legal advice false-skip rate > 1% → block`은 기존 그대로 (intent router 정밀도)
- 신설 예상: `judge false-positive rate > 10% → block enforce`

## 7. Rollback

런타임 flag 변경 불가. 비상 시:
1. `AI_OUTPUT_JUDGE_SHADOW_ENABLED=false`
2. CI/CD 재배포 (~10분)

회로 차단기 (일일 비용 / 실패율 자동 OFF)는 후속 phase에서 도입 — 현재는 수동 모니터링.
