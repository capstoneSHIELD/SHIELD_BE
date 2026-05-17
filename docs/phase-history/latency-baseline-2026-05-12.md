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

## Baseline 결과

수집 환경: **로컬 (학교 네트워크, KMU SSL inspection 통과)**
앱 버전 / 커밋: `rag-정상화` HEAD = `8400a5e Merge fix/hnsw-cte-refactor`
측정 시각 (KST): 2026-05-13 18:30 / 18:34 (smoke 2회 평균치 채택은 18:34 측정)
샘플 수: 2 / 2 (성공 / 전체)
classify.model = `command-a-03-2025` (운영 default)
chat.model     = `command-a-03-2025` (운영 default)
classify timeout = 30000ms (`COHERE_TIMEOUT_READ_CLASSIFY` env override, 운영 default 는 15000ms)

> **주의**: 학교 네트워크의 SSL inspection 프록시(KMU CA 재서명) 가 추가 지연을 부과하여
> **운영 latency 의 상한선** 으로 해석해야 함. 운영 환경에서는 이보다 빠르거나 같을 가능성.
> Phase 1A 비교 측정(command-r-08-2024) 은 Gradle daemon corruption 으로 학교 환경에선 재현 실패 —
> staging/CI 측정으로 이관.

### 단계별 latency (smoke 2건, 학교 환경)

표본이 작아 (n=2) 평균/max 만 보고. p50/p95 는 의미 없는 수치라 생략.

| 메트릭 | 샘플 수 | outcome | 평균 (ms) | max (ms) | 비고 |
| --- | ---: | --- | ---: | ---: | --- |
| `shield.chat.send_message` | 2 | success | **47047** | 56002 | wall-clock 전체 |
| `shield.rag.pipeline.total` | 2 | success | 30660 | 30773 | classify+retrieve+build |
| `shield.rag.classify` | 2 | **failure** | **30051** | 30087 | **30s timeout 100%** — 학교 환경 특수 (운영은 다를 가능성) |
| `shield.rag.retrieve` | 2 | success | 587 | 661 | pgvector + BM25 정상 |
| `shield.rag.cohere.embed` | 2 | success | 334 | 361 | Cohere embed 정상 |
| `shield.chat.cohere.call` | 2 | success | **16243** | 25338 | **#2 본응답 LLM — 가장 큰 정상 비중** |

### outcome 분포 (smoke 2건 기준)

| 메트릭 | success | empty | failure | 비고 |
| --- | ---: | ---: | ---: | --- |
| `shield.rag.pipeline.total` | 2 | 0 | 0 | classify 가 fallback 으로 RuntimeException 안 던지므로 pipeline 은 success |
| `shield.rag.classify` | 0 | — | **2** | **failure 100% — 학교 환경 timeout. 운영에서 재측정 필요** |
| `shield.rag.retrieve` | 2 | 0 | — | 정상 |
| `shield.chat.cohere.call` | 2 | — | 0 | 정상 |

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

## 결론

선택한 다음 Phase: ☑ **1A** (`cohere.classify.model` 라이트 모델 교체) — staging 카나리 측정 → 운영 default 결정
보조 선택: ☑ **재측정 (staging)** — 학교 환경 baseline 은 운영 추정용으로만 활용

근거 (학교 환경 baseline 기준):

- **classify 가 timeout 100%** (`outcome=failure` 2/2) — 학교 SSL inspection + 큰 prompt (ontology JSON 7555자) 결합으로 30s 안에 안 들어옴. 라이트 모델(`command-r-08-2024`) 은 응답이 빨라 timeout 회피 기대. 운영 환경에서도 classify timeout 발생 빈도가 메트릭에 잡히면 즉시 적용 가치.
- **`chat.cohere.call` 16–25s 가 안정적 정상 측정** — Phase 1A(classify 교체) 와 직접 무관하지만 다음 큰 병목. Phase 3 (speculative 병렬) 또는 chat 모델 자체 라이트화 검토 필요. classify p50 « chat p50 가 staging 에서도 재현되면 Phase 3 효과 큼.
- **retrieve 0.6s, embed 0.3s 정상** — DB/벡터 경로는 충분히 빠름. Phase 4 (임베딩-only 검색) 동기 없음.

## Phase 1A 실행 절차

1. **staging 환경에 `COHERE_CLASSIFY_MODEL=command-r-08-2024` 환경변수 set** — application.yml 무변경, CohereApiConfig 의 relaxed binding 으로 자동 override.
2. staging 의 BaselineMetricsRealIT 또는 운영 트래픽 일부 (카나리) 로 측정 — 같은 신규 메트릭 (`shield.rag.classify`) 가 운영 환경에서도 노출됨.
3. **회귀 검증**: `scripts/eval_rag.py` 로 retrieval 정확도(Recall@5, nDCG) 비교. classify 결과가 retrieval 입력(`vectorQuery`, `bm25Keywords`)에 영향을 주므로 간접 측정 가능.
4. classify p50 가 운영 default 보다 의미 있게 빠르고 retrieval 정확도 회귀가 없으면 → 운영 default 변경 PR.

## 보류된 후속 단계 (Phase 0 범위 밖)

- **Phase 3 (speculative 병렬)**: chat 16–25s 가 본 응답의 가장 큰 비중. 학교 환경 wall-clock 47s 중 `pipeline.total` 30s + `chat` 16s 가 직렬. 운영에서도 비슷한 ratio 면 `chat` 을 RAG 와 병렬 시작하여 max(RAG, chat) 로 줄이는 게 큰 효과.
- **chat 모델도 라이트화 검토**: `cohere.model.chat` 도 placeholder (`CohereApiConfig.java:30`). `COHERE_MODEL_CHAT` 환경변수 override 로 시도 가능. classify 와 동일 패턴.

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
