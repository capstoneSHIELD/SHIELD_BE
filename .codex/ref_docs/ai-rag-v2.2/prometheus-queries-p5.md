# Phase P5 — Prometheus 쿼리 스니펫

> Grafana 대시보드 패널에서 그대로 사용 가능. `shield_*` 메트릭은 micrometer가 `.` → `_`로 변환한 이름.

## 1. Cohere 호출량 / 비용

### 1.1 분당 토큰 사용량 (operation × direction별)
```promql
sum by (operation, direction) (
  rate(shield_ai_cohere_tokens_total[1m])
)
```

### 1.2 1시간 누적 토큰 (모델별)
```promql
sum by (model) (
  increase(shield_ai_cohere_tokens_total[1h])
)
```

### 1.3 분당 추정 비용 (USD)
```promql
sum by (operation) (
  rate(shield_ai_cohere_cost_estimated_usd_sum[1m])
)
```

### 1.4 1일 누적 추정 비용 (모델별)
```promql
sum by (model) (
  increase(shield_ai_cohere_cost_estimated_usd_sum[1d])
)
```

## 2. Cohere 호출 지연

### 2.1 operation별 p50 / p95 latency (5분 window)
```promql
histogram_quantile(0.50,
  sum by (operation, le) (rate(shield_ai_cohere_latency_seconds_bucket[5m]))
)
```
```promql
histogram_quantile(0.95,
  sum by (operation, le) (rate(shield_ai_cohere_latency_seconds_bucket[5m]))
)
```

### 2.2 status별 호출 건수 (성공/실패/fallback)
```promql
sum by (status, operation) (
  rate(shield_ai_cohere_latency_seconds_count[1m])
)
```

## 3. Retrieval Gate (P5.1 Commit 5)

### 3.1 shadow mode pass/drop 비율
```promql
sum by (method, outcome) (
  rate(shield_rag_retrieval_gate_total{outcome=~"shadow_pass|shadow_drop"}[5m])
)
```

### 3.2 enforce mode drop 카운트
```promql
sum by (method) (
  increase(shield_rag_retrieval_gate_total{outcome="dropped"}[1h])
)
```

## 4. Output Compliance Shadow Judge (P5.2 Commit 4)

### 4.1 outcome 분포 (sampled / skipped / regex_violation)
```promql
sum by (outcome) (
  rate(shield_ai_output_judge_shadow_total[5m])
)
```

### 4.2 regex violation 분당 알람
```promql
rate(shield_ai_output_judge_shadow_total{outcome="regex_violation"}[1m]) > 0
```

## 5. 평가 / RAG 헬스

### 5.1 RAG pipeline fallback (RAG-less 응답으로 떨어진 비율)
```promql
rate(shield_rag_pipeline_fallback_total[5m])
```

### 5.2 Cohere embed degrade (영벡터 fallback 발생률)
```promql
sum by (reason) (
  rate(shield_rag_vector_degrade_total[5m])
)
```

## 6. 알람 룰 예시

```yaml
groups:
  - name: shield-ai-p5
    rules:
      - alert: CohereChatHighLatency
        expr: |
          histogram_quantile(0.95,
            sum by (le) (rate(shield_ai_cohere_latency_seconds_bucket{operation="chat"}[5m]))
          ) > 8
        for: 5m
        annotations:
          summary: "Chat p95 > 8s — RAG latency 회귀 가능"

      - alert: CohereCostSpike
        expr: |
          sum(increase(shield_ai_cohere_cost_estimated_usd_sum[1h]))
          /
          sum(increase(shield_ai_cohere_cost_estimated_usd_sum[1h] offset 1d))
          > 2
        for: 15m
        annotations:
          summary: "지난 1시간 추정 비용이 전일 동시간 대비 2배 이상"

      - alert: RagPipelineFallbackBurst
        expr: rate(shield_rag_pipeline_fallback_total[5m]) > 0.05
        for: 10m
        annotations:
          summary: "RAG-less fallback rate > 5% — Cohere/벡터 검색 장애 의심"
```

## 7. 검증 방법 (로컬)

```powershell
# /actuator/prometheus 노출 확인
curl http://localhost:8080/actuator/prometheus | Select-String "shield_ai_cohere"

# 평가셋 1회 실행 후 토큰 합산 검증
# sum(tokens, direction=output, operation=chat) ≈ sum(Message.tokensOutput) (±1%)
```
