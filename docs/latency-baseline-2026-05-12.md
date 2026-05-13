# sendMessage Latency Baseline — 2026-05-12

## 목적
sendMessage 파이프라인의 **단계별 p50/p95 baseline** 을 확보해 후속 최적화
(모델 교체 / 캐싱 / speculative 병렬 / #1 스킵) 우선순위를 결정한다.
관련 플랜: `~/.claude/plans/llm-foamy-sonnet.md`

## 준비된 신규 메트릭 (Phase 0)
- `shield.rag.classify` (timer, `outcome=success|failure`) — Layer 1 의도 분류 LLM 호출
- `shield.rag.pipeline.total` (timer, `outcome=success|empty|failure`) — RAG 파이프라인 전체
- 기존: `shield.chat.send_message`, `shield.chat.cohere.call`, `shield.rag.retrieve`, `shield.rag.cohere.embed`

## 실행 절차

### 1) 백엔드 기동 — 로컬 또는 스테이징
`.env` 의 `COHERE_API_KEY` / `DB_*` / `JWT_SECRET` 등이 설정되어 있어야 함.
```bash
./gradlew bootRun
# 또는 jar 빌드 후 java -jar
```
기동 후:
```bash
curl -s http://localhost:8080/actuator/prometheus | grep -E '^shield_(rag|chat)_' | head
# 트래픽 발사 전이라 아직 시리즈는 안 보일 수 있음 (Micrometer lazy registration).
```

### 2) (권장) percentile 노출 활성화 — application.yml 한 블록 추가
기본 Spring Boot Timer 는 prometheus 에 count/sum/max 만 노출하며 p50/p95 는 안 찍힘.
직접 추출하려면 `management.metrics` 아래에 추가:
```yaml
management:
  metrics:
    distribution:
      percentiles:
        shield.rag.classify: 0.5, 0.95
        shield.rag.pipeline.total: 0.5, 0.95
        shield.rag.retrieve: 0.5, 0.95
        shield.rag.cohere.embed: 0.5, 0.95
        shield.chat.cohere.call: 0.5, 0.95
        shield.chat.send_message: 0.5, 0.95
```
재기동 필요.
> Prometheus 서버 + Grafana 가 있는 환경이면 위 설정 없이도 `histogram_quantile(0.5, rate(...))` 로 추출 가능.

### 3) 부하 발사 (스크립트)
- JWT 토큰: 프론트엔드 로그인 후 DevTools → Network 헤더에서 추출, 또는 인증 엔드포인트 직접 호출.
- consultationId: **L1 분야가 선택된** 상담이어야 RAG 가 작동 (`MessageService:132` 의 `if (domainForRag != null)`).
- 한 상담당 사용자 턴 상한 10. 100건이 필요하면 10개 상담을 준비하거나 매 batch 사이 새 상담 생성.

```bash
# 단일 상담에 10건
python scripts/baseline_load.py \
  --base-url http://localhost:8080 \
  --token "Bearer eyJ..." \
  --consultation-id <uuid> \
  --count 10

# 10개 상담 × 10건 = 100건
python scripts/baseline_load.py \
  --base-url http://localhost:8080 \
  --token "Bearer eyJ..." \
  --consultation-ids id1,id2,...,id10 \
  --per-consultation 10
```

### 4) prometheus 수집
모든 요청 완료 후:
```bash
curl -s http://localhost:8080/actuator/prometheus \
  | grep -E '^shield_(rag|chat)_(send_message|cohere|classify|pipeline_total|retrieve)_seconds(_count|_sum|_max)?\{' \
  > /tmp/prom-snapshot-$(date +%F).txt
```

### 5) 결과 채우기 (아래 표)
- 평균 = `sum / count`
- p50/p95 는 (2)의 percentile 설정 활성 시 prometheus 출력의 `_seconds{quantile="0.5"}` / `_seconds{quantile="0.95"}` 라인에서 직접 읽기. 미활성 시 평균만 기록하고 추후 Grafana 로 보강.

---

## Baseline 결과 (채우기)

수집 환경: __________ (local / staging-? / prod-canary-?)
앱 버전 / 커밋: ________________
측정 시각 (KST): ________________
샘플 수: ____ 건 (성공 / 전체)

### 단계별 latency

| 메트릭 | 샘플 수 | 평균 (ms) | p50 (ms) | p95 (ms) | max (ms) |
|---|---:|---:|---:|---:|---:|
| `shield.chat.send_message{outcome="success"}` — 전체 |   |   |   |   |   |
| `shield.rag.pipeline.total{outcome="success"}` — RAG 단계 합 (신규) |   |   |   |   |   |
| `shield.rag.classify{outcome="success"}` — #1 LLM (신규) |   |   |   |   |   |
| `shield.rag.retrieve{outcome="success"}` — 검색 SQL |   |   |   |   |   |
| `shield.rag.cohere.embed{outcome="success"}` — 임베딩 |   |   |   |   |   |
| `shield.chat.cohere.call{outcome="success"}` — #2 LLM |   |   |   |   |   |

### outcome 분포 (이상치 진단용)

| 메트릭 | success | empty | failure | 비고 |
|---|---:|---:|---:|---|
| `shield.rag.pipeline.total` |   |   |   | empty 비율 높으면 Phase 1F (분류 스킵) 가치 큼 |
| `shield.rag.classify` |   | — |   | failure 잦으면 모델/타임아웃 점검 |
| `shield.rag.retrieve` |   |   |   | empty 비율 높으면 검색 품질 점검 |
| `shield.chat.cohere.call` |   | — |   | (blank 는 별도 `shield.chat.blank_response` 카운터) |

---

## 해석 — 다음 단계 결정 매트릭스

플랜 (Plan §Phase 0-4) 의 가이드:

| 관측 | 다음 단계 |
|---|---|
| `shield.rag.classify` p50 > 3s | **Phase 1A** — `cohere.classify.model` 을 `command-r-08-2024` 등 라이트 모델로 교체. eval/eval-set.v1.jsonl 로 회귀 검증 |
| `shield.chat.cohere.call` p50 ≫ `shield.rag.classify` p50 | speculative 병렬(Phase 3) 효과 작음 — Phase 1A 만 우선 |
| `shield.rag.pipeline.total{empty}` 비율 > 30% | **Phase 1F** — L1+L2+L3 확정 상담 턴에서 #1 스킵 |
| `shield.rag.retrieve` p50 > 1s | DB/인덱스(pgvector, BM25) 튜닝이 LLM 최적화보다 우선 |
| `send_message - (pipeline.total + cohere.call)` gap 큼 | DB tx / sanitize / appendHistory 등 비-LLM 구간 점검 |

## 결론 (채우기)

선택한 다음 Phase: ☐ 1A  ☐ 1E  ☐ 1F  ☐ 2 (캐싱)  ☐ 3 (speculative)  ☐ 측정 재실행

근거 (3줄 이내):
- ____________
- ____________
- ____________

---

## 부록 — prometheus 출력 형식 참고

기본 (percentile 비활성):
```
shield_rag_classify_seconds_count{outcome="success",application="shield",} 100.0
shield_rag_classify_seconds_sum{outcome="success",application="shield",}    234.5
shield_rag_classify_seconds_max{outcome="success",application="shield",}    6.123
```
평균 = sum/count = 234.5/100 = **2.345s**.

percentile 활성:
```
shield_rag_classify_seconds{outcome="success",application="shield",quantile="0.5",} 1.872
shield_rag_classify_seconds{outcome="success",application="shield",quantile="0.95",} 4.231
```
