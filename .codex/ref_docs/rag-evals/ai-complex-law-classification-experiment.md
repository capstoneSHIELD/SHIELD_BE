# AI 법률 분야 분류 및 변호사 매칭 성능 실험 설계

기준 저장소: `capstoneSHIELD/SHIELD_BE` `develop` 브랜치  
작성일: 2026-06-15  
대상 파이프라인: Layer 1 `IntentClassificationService.route(...)` 기반 intent router + `LawyerMatchingService` 기반 변호사 매칭

## 0. 로컬 실험 디렉터리 구조

실험 파일은 아래 로컬 폴더를 기준으로 관리한다.

```text
C:\Users\mmkan\바탕 화면\EXPERIMENT\
  input\
    dataset-v1.jsonl
    classification-turns-v1.jsonl
    archetypes-v1.json
    splits-v1.json
    legal-ontology-slim.snapshot.json
    lawyers-v1.jsonl
    lawyer-corpus-generator-config.yaml
    matching-labels-v1.jsonl
  runner\
    README.md
    run_experiment.py
    config.example.yaml
    shield_experiment\
      __init__.py
      dataset.py
      client.py
      modes.py
      evaluator.py
      matching.py
      report.py
  output\
    .gitkeep
```

역할은 다음과 같이 분리한다.

| 폴더 | 역할 |
|---|---|
| `input` | 300개 case variant, turn 단위 분류 평가 row, archetype 원본, split, ontology snapshot, 변호사 더미 코퍼스, 매칭 정답 라벨 저장 |
| `runner` | 실험 실행 코드 저장. `run_experiment.py`가 `shield_experiment` 패키지를 import해서 실행 |
| `output` | 실행 결과, raw response, metric summary, failure report 저장 |

이 구조를 쓰면 분류 데이터셋, 변호사 코퍼스, 실행 코드, 결과물이 섞이지 않는다. 실험을 다시 돌릴 때는 `input/dataset-v1.jsonl`, `input/classification-turns-v1.jsonl`, `input/lawyers-v1.jsonl`, `input/matching-labels-v1.jsonl`, `input/splits-v1.json`, `runner/config.yaml`만 고정하고, `output/{run_id}` 아래에 매 실행 결과를 새로 저장한다.

권장 실행 방식은 Python runner가 로컬에서 실행 중인 SHIELD BE의 실험용 adapter를 호출하는 방식이다. 현재 운영 API에는 `POST /api/consultations/{id}/classify`가 없고, 상담 메시지 전송은 `POST /api/consultations/{id}/messages`, 분류 직접 수정은 `PATCH /api/consultations/{id}/classify`로 분리되어 있다. 이 운영 API들은 이번 실험에 필요한 raw classifier 응답, `matched_node_ids`, token, latency, fallback 여부를 안정적으로 노출하지 않으므로, `IntentClassificationService.route(...)`와 동일한 prompt/ontology/provider 경로를 사용하는 실험 전용 경로가 필요하다.

권장 adapter 선택지는 두 가지다.

| 방식 | 설명 | 추천도 |
|---|---|---|
| HTTP adapter | BE에 local/test profile 전용 `POST /internal/experiments/intent-route`를 두고 runner가 HTTP로 호출 | 높음 |
| Spring test runner | BE의 `@SpringBootTest`가 `input/dataset-v1.jsonl`을 읽고 `output`에 직접 저장 | 중간 |

이 문서에서는 사용자가 요청한 폴더 구조에 맞춰 `runner` 폴더의 Python orchestrator가 `client.py`, `dataset.py`, `evaluator.py`, `report.py`를 import해서 실행하는 방식을 기본으로 둔다.

### 0.1 실험용 HTTP adapter 계약

실험용 HTTP adapter는 local/test profile에서만 활성화한다. production profile에서는 bean 자체가 등록되지 않아야 한다.

권장 경로:

```text
POST /internal/experiments/intent-route
```

Request:

```json
{
  "provider": "cohere",
  "mode": "A_FULL",
  "domain": null,
  "messages": [
    {
      "role": "USER",
      "content": "전세보증금을 못 받고 있는데 집주인 재산에 가압류도 해야 할까요?"
    }
  ],
  "includeRaw": true
}
```

Response:

```json
{
  "provider": "cohere",
  "requestedProvider": "cohere",
  "mode": "A_FULL",
  "inputDomain": null,
  "responseId": "provider-response-id",
  "rawJson": "{\"schema_version\":\"2.0\"}",
  "parsed": {
    "schemaVersion": "2.0",
    "dialogueIntent": "ASK_LEGAL_ADVICE",
    "intentConfidence": 0.88,
    "caseType": {
      "l1": "임대차보호",
      "l2": "주택임대차보호",
      "l3": "보증금 반환 및 회수",
      "confidence": 0.84
    },
    "matchedNodeIds": ["law-007-01-05", "law-006-03-02"],
    "coreKeywords": ["전세보증금", "가압류"],
    "retrievalQueries": ["전세 보증금 반환 가압류"]
  },
  "tokensInput": 1200,
  "tokensOutput": 220,
  "latencyMs": 932,
  "parseSuccess": true,
  "schemaSuccess": true,
  "fallbackUsed": false,
  "errorType": null,
  "errorMessage": null
}
```

중요한 구현 원칙:

- adapter는 BE의 classifier prompt, ontology scoping, provider client, parser를 그대로 사용한다.
- 단순히 `IntentClassificationService.route(...)`만 호출하면 `AiCallResult`의 `responseId`, token, latency, raw JSON이 사라지므로, adapter는 raw classifier 호출 결과와 parser 결과를 함께 반환하는 실험 전용 application method를 둔다.
- 현재 production service는 `ai.classify.provider`를 환경변수 기반으로 주입받으므로, HTTP request의 `provider` 필드를 지원하려면 실험용 application method가 provider key를 명시적으로 받아 해당 `AiClassificationClient`를 선택해야 한다. 이를 만들지 않는다면 provider별로 BE 프로세스를 재기동하고 `requestedProvider`와 실제 설정값을 `run-meta.json`에 기록한다.
- provider key가 잘못되었거나 API key가 없거나 upstream 호출이 실패한 경우 fallback 결과를 정상 예측으로 저장하지 않는다.
- fallback 응답은 `fallbackUsed=true`와 `errorType`으로 분리하고, 정확도 지표 계산에서 제외하거나 별도 실패율로 집계한다.
- `rawJson`은 개인정보가 포함될 수 있으므로 local output에만 저장하고, 공유용 report에는 원문 사용자 발화를 기본적으로 싣지 않는다.

### 0.2 실험용 변호사 매칭 adapter 계약

현재 `LawyerMatchingService.findMatching(...)`는 `ClassificationResolver`가 산출한 `domains/subDomains/tags`와 `Brief.content`를 `LawyerEmbeddingTextBuilder`로 합친 뒤, `lawyer_embeddings.embedding <=> query_vector`의 cosine similarity 순서로 변호사를 반환한다. 반환 DTO의 `matchedKeywords`는 태그 교집합 표시용이고, 현재 점수식에는 별도 keyword/category boost가 들어가지 않는다.

따라서 "하이브리드 RAG가 아닌 일반 코사인 유사도만 사용하면 변호사 매칭 정확성이 얼마나 떨어지는가"를 측정하려면 실험에서 비교할 매칭 함수를 명시적으로 분리해야 한다.

권장 경로:

```text
POST /internal/experiments/lawyer-match
```

Request:

```json
{
  "caseId": "CLX-001-V01",
  "matchingMode": "PREDICTED_LABELS_COSINE_ONLY",
  "classificationMode": "C_HYBRID_RUNTIME",
  "topK": 10,
  "currentServiceCompatible": true,
  "query": {
    "briefContent": "전세보증금을 못 받고 있는데 집주인 재산에 가압류도 해야 할까요?",
    "predNodeIds": ["law-007-01-05", "law-006-03-02"],
    "goldNodeIds": ["law-007-01-05", "law-006-03-02"],
    "resolvedDomains": ["임대차보호"],
    "resolvedSubDomains": ["주택임대차보호"],
    "resolvedTags": ["보증금 반환 및 회수", "민사보전처분"],
    "queryText": "[전문 분야]\n임대차보호. 임대차보호. 임대차보호\n[세부 분야]\n주택임대차보호. 주택임대차보호\n[태그]\n보증금 반환 및 회수. 민사보전처분\n[사건 내용]\n전세보증금을 못 받고 있는데 집주인 재산에 가압류도 해야 할까요?"
  }
}
```

Response:

```json
{
  "caseId": "CLX-001-V01",
  "matchingMode": "PREDICTED_LABELS_COSINE_ONLY",
  "classificationMode": "C_HYBRID_RUNTIME",
  "topK": 10,
  "results": [
    {
      "rank": 1,
      "lawyerId": "L-007-01-05-003",
      "practiceNodeIds": ["law-007-01-05", "law-006-03-02"],
      "score": 0.842,
      "scoreComponents": {
        "cosine": 0.842,
        "fieldOverlap": null,
        "keywordOverlap": null,
        "hybridScore": null
      }
    }
  ],
  "latencyMs": 41,
  "errorType": null,
  "errorMessage": null
}
```

매칭 adapter 구현 원칙:

- local/test profile에서만 활성화한다.
- production 변호사 DB를 사용하지 않고, `lawyers-v1.jsonl` 기반 synthetic lawyer corpus를 별도 test schema 또는 임시 테이블에 적재한다.
- `PREDICTED_LABELS_COSINE_ONLY`는 현재 운영 baseline이다. classifier의 `pred_node_ids`를 ontology label path로 변환한 뒤 `ClassificationResolver`가 만든 `domains/subDomains/tags`, `Brief.content`, `LawyerEmbeddingTextBuilder.build(...)`, `LawyerEmbeddingRepository.findTopBySimilarity(...)` 순서를 그대로 재현한다.
- `ORACLE_LABELS_COSINE_ONLY`는 같은 운영 cosine 경로를 쓰되 `pred_node_ids` 대신 `gold_node_ids`를 넣은 upper-bound다. 운영 성능으로 해석하지 않는다.
- `NO_LABEL_COSINE_ONLY`는 `domains/subDomains/tags`를 비우고 `Brief.content`만 `LawyerEmbeddingTextBuilder`에 넣는 하한선이다.
- `HYBRID_MATCH`는 실험용 점수식으로 정의한다. 기본 후보식은 `0.60 * cosine + 0.25 * fieldOverlap + 0.15 * keywordOverlap`이다.
- `fieldOverlap`은 case의 gold/pred node와 변호사 `practice_node_ids`의 계층 일치도로 계산한다. L3 일치 1.0, L2 일치 0.7, L1 일치 0.4를 사용한다.
- `keywordOverlap`은 case keywords와 변호사 tags의 Jaccard 또는 overlap coefficient 중 하나로 고정하고 `run-meta.json`에 기록한다.
- 지역, 경력, 수임 가능 여부는 이번 실험의 기본 점수식에 넣지 않는다. 넣는 경우 별도 ablation으로 분리한다.
- `HYBRID_MATCH` weight는 pilot/dev split에서만 조정할 수 있다. 최종 test split 실행 전에는 `run-meta.json`에 weight와 keyword overlap 방식을 고정한다.

## 1. 실험 목적

우리 시스템은 민사 법률 분야를 8개 L1 대분류로 나누고, 각 L1 아래에 L2 중분류와 L3 소분류를 둔다. LLM API는 크게 두 레이어로 구성되며, 첫 번째 레이어가 의도 분류와 법률 분야 분류를 담당한다. 이번 실험의 1차 목적은 매 대화 시점에서 Layer 1 AI가 법률 분야를 얼마나 정확히 분류하는지 측정하는 것이다.

2차 목적은 이 분류 결과가 변호사 매칭 품질에 미치는 영향을 측정하는 것이다. 현재 변호사 매칭 구현은 변호사 프로필 임베딩과 brief/query 임베딩의 cosine similarity를 중심으로 랭킹한다. 따라서 별도 실험용 hybrid matcher를 정의하고, 일반 cosine-only 매칭이 hybrid matcher 대비 `Recall@K`, `nDCG@K`, `MRR`에서 얼마나 떨어지는지 측정한다.

이번 실험은 아래 두 트랙으로 나눈다.

| track | 평가 대상 | 핵심 산출물 |
|---|---|---|
| T1_CLASSIFICATION | Layer 1 intent router의 법률 분야 분류 정확도 | L1/L2/L3 exact/partial, multilabel F1, turn별 drift |
| T2_MATCHING | 분류 결과와 매칭 방식이 변호사 추천 랭킹에 미치는 영향 | Recall@K, nDCG@K, MRR, cosine-only drop, classification-induced drop |

핵심 질문은 다음과 같다.

1. 사용자의 첫 발화와 이후 매 대화 턴에서 Layer 1 AI가 L1/L2/L3 법률 분야를 얼마나 정확하게 찾는가?
2. `domain`으로 ontology를 특정 L1에 scope했을 때 복합 분야가 잘리는가?
3. `full ontology`와 `scoped ontology`를 조합한 hybrid classification 방식이 운영 적용에 적합한가?
4. 현재 production config 기준 Cohere classifier와 OpenAI classifier 중 어떤 provider arm이 구조화 출력 안정성, 비용, latency, 복합 label recall에서 우위가 있는가?
5. gold label을 넣은 매칭과 predicted label을 넣은 매칭의 랭킹 차이가 얼마나 큰가?
6. 변호사 매칭에서 cosine-only 방식은 실험용 hybrid matcher 대비 어느 정도 성능이 떨어지는가?
7. 매칭 성능 하락의 원인이 분류 오류인지, cosine-only ranking 자체의 한계인지 분리해서 설명할 수 있는가?

주의: 4번은 운영 provider 교체 결론이 아니라 shadow evidence 수집이다. OpenAI arm은 strict JSON Schema, Cohere arm은 Cohere Chat v2 structured output 경로를 사용하므로, 결과는 "순수 모델 비교"가 아니라 "현재 BE 설정 묶음 비교"로 해석한다.

### 1.1 이번 실험이 산출해야 하는 벤치마크

이번 실험의 최종 산출물은 단순 리포트가 아니라 이후 모델, prompt, ontology, matching logic을 바꿀 때 재사용할 수 있는 benchmark suite다.

| benchmark | 기준 입력 | 비교 대상 | 대표 지표 | 의사결정 용도 |
|---|---|---|---|---|
| B1_LAYER1_TURN_CLASSIFICATION | `classification-turns-v1.jsonl`의 매 평가 turn | provider x classification mode | L1/L2 accuracy, L3 micro F1, complex recall, drift rate | Layer 1 분류 prompt/provider/ontology scope 개선 여부 판단 |
| B2_SCOPE_LOSS | 같은 case의 full/scoped/hybrid 결과 | `A_FULL`, `B_SCOPED_GOLD`, `B_SCOPED_RUNTIME`, `C_HYBRID_RUNTIME` | scoped ontology loss, under-classification rate | domain scoping이 복합 분야를 구조적으로 잘라내는지 판단 |
| B3_CURRENT_MATCHING_BASELINE | selected classifier arm의 predicted labels + brief content | `PREDICTED_LABELS_COSINE_ONLY` | nDCG@10, Recall@10, hard negative intrusion | 현재 운영 변호사 매칭 품질 기준선 |
| B4_MATCHING_ABLATION | oracle/predicted/no-label query variants | cosine-only vs experimental hybrid | cosine-only drop, classification-induced drop, hybrid recovery | 매칭 병목이 분류 오류인지 cosine ranking 한계인지 분리 |

대표 점수는 두 가지로 따로 낸다.

- `stress_score`: 이 문서의 300개 case 구성을 그대로 사용한다. 복합 사건 비중이 높으므로 복합분야 탐지력을 보는 스트레스 벤치마크다.
- `production_weighted_score`: `splits-v1.json` 또는 `run-meta.json`에 기록한 group weight를 적용한다. 실제 운영 분포를 아직 모르면 pilot 결과를 보고 weight를 확정하되, final test 실행 후에는 바꾸지 않는다.

## 2. 현재 BE 기준 분류 지점

현재 `develop` 브랜치에서 법률 분야 분류 실험에 가장 적합한 지점은 다음이다.

- `IntentClassificationService.route(List<Message> recentMessages, String domain)`
- prompt: `src/main/resources/ai/prompts/rag/intent-classifier.md`
- ontology: `src/main/resources/ontology/legal-ontology-slim.json`
- 출력 필드: `dialogueIntent`, `intentConfidence`, `caseType`, `matched_node_ids`, `core_keywords`, `retrievalQueries`

실험의 주 평가 대상은 `matched_node_ids`이다.

`caseType`은 단일 `l1/l2/l3` 구조이므로 복합분야의 보조 지표로만 사용한다. 복합분야 정답이 2개 이상일 때 `caseType` 하나만 맞았다고 성공으로 보면 안 된다.

변호사 매칭 실험의 현재 BE 기준 지점은 다음이다.

- `LawyerMatchingService.findMatching(UUID briefId, UUID userId, Pageable pageable)`
- query text builder: `LawyerEmbeddingTextBuilder.build(domains, subDomains, tags, brief.getContent())`
- vector search: `LawyerEmbeddingRepository.findTopBySimilarity(queryVec, limit, offset)`
- lawyer profile fields: `LawyerProfile.domains`, `subDomains`, `tags`, `bio`, `experienceYears`, `region`
- embedding table: `lawyer_embeddings` with `vector(1024)` and HNSW cosine index

중요한 해석:

- 현재 운영 매칭은 cosine similarity가 주 점수이며, 법률 분야/태그는 query embedding text에 반영된다.
- 현재 운영 매칭에는 RAG 법령 검색의 3-way hybrid score처럼 `vector + keyword + trigram`을 결합하는 변호사 매칭 점수식이 없다.
- 따라서 hybrid matcher는 실험용 비교군으로 별도 정의한다. 이 비교군을 운영 기능으로 간주하지 않는다.

## 3. 테스트셋 규모와 구성

전체 테스트셋은 약 300개로 구성한다.

구성 방식은 `30개 archetype x 10개 발화 변형 = 300개`이다. 모든 archetype은 gold label을 ontology node id로 가진다.

| 구분 | archetype 수 | 변형 수 | 총 케이스 | 목적 |
|---|---:|---:|---:|---|
| 단일 분야 | 6 | 10 | 60 | 기본 분류 정확도 확인 |
| 동일 L1 내 복합 | 9 | 10 | 90 | 같은 대분류 내부의 복합 쟁점 탐지 |
| 서로 다른 L1 복합 | 12 | 10 | 120 | 진짜 복합 법률 분야 탐지 |
| 애매하거나 노이즈가 큰 사건 | 3 | 10 | 30 | 과분류/저분류/보수적 분류 확인 |
| 합계 | 30 | 10 | 300 |  |

복합분야 비중은 210/300, 즉 70%로 둔다. 이번 실험 목적이 복합분야이기 때문이다.

주의: 300개는 `dataset-v1.jsonl`의 case variant 기준 규모다. 실제 Layer 1 분류 benchmark는 `classification-turns-v1.jsonl`을 기준으로 계산한다. `V03`, `V10`처럼 다중 턴 variant는 평가 가능한 사용자 turn마다 별도 row로 펼치므로 classification 평가 row는 300개보다 많아질 수 있다. report에는 반드시 `case_count`, `turn_eval_count`, `matching_case_count`를 분리해 기록한다.

`classification-turns-v1.jsonl` 생성 규칙:

- single-turn variant는 `dataset-v1.jsonl`의 case 하나가 turn row 하나가 된다.
- multi-turn variant는 사용자 메시지가 추가될 때마다 그 시점까지의 최근 4턴 `messages`를 row로 만든다.
- `gold_node_ids`는 해당 turn에서 판단 가능한 정답만 둔다. 초기 발화가 모호하면 L1/L2 node를 gold로 둘 수 있고, 추가 정보가 들어온 뒤 L3 또는 복합 label로 확장한다.
- 최종 변호사 매칭 benchmark는 기본적으로 각 `conversation_id`의 마지막 평가 turn만 사용한다. 중간 turn 매칭은 별도 exploratory 지표로만 본다.
- 모든 row에는 `case_id`, `conversation_id`, `turn_index`, `is_final_turn`, `benchmark_split`을 둔다.

### 3.1 변호사 더미 코퍼스 규모

변호사 매칭 실험은 별도의 synthetic lawyer corpus를 사용한다. 운영 변호사 데이터나 실제 개인정보를 사용하지 않는다.

권장 규모는 아래 두 단계다.

| 단계 | 규모 | 구성 | 목적 |
|---|---:|---|---|
| pilot | 약 600명 | L3별 최소 5명 + cross-domain 80명 + distractor 80명 | runner와 metric 검증 |
| full | 약 1,200~1,600명 | L3별 10명 이상 + cross-domain 200명 + distractor 150명 | 매칭 성능 비교 본실험 |

코퍼스 구성 원칙:

- 8개 L1, 전체 L2/L3가 최소 1회 이상 변호사 전문 분야에 포함되어야 한다.
- 각 L3에는 "정확히 해당 L3 전문" 변호사와 "같은 L2의 인접 L3 전문" 변호사를 함께 둔다.
- 복합 사건 평가를 위해 서로 다른 L1을 함께 다루는 cross-domain 변호사를 별도 생성한다.
- cosine-only의 약점을 보기 위해 bio는 비슷하지만 practice node가 다른 hard negative 변호사를 넣는다.
- `seed`를 고정해 매번 같은 dummy lawyer id와 profile text가 생성되도록 한다.
- 이름, 변호사번호, 프로필 이미지는 모두 synthetic 값으로 만들고 실제 인물과 연결하지 않는다.

코퍼스 품질 통제:

- `lawyer-corpus-generator-config.yaml`에 L1/L2/L3별 생성 수, cross-domain 조합 수, hard negative 비율, bio template, noise level, seed를 기록한다.
- `splits-v1.json`에 `pilot`, `dev`, `test` split을 고정한다. pilot은 runner/adapter 검증, dev는 hybrid weight와 threshold 조정, test는 최종 benchmark 산출에만 사용한다.
- hard negative는 "bio/keyword는 비슷하지만 `practice_node_ids`가 다른 변호사"와 "같은 L1이지만 다른 L2/L3 변호사"를 분리해 생성한다.
- relevance label은 generator 규칙만으로 자동 확정하지 않는다. 최소한 dev/test에 포함되는 대표 case는 사람이 `grade`와 reason을 검수한다.
- `ORACLE_LABELS_HYBRID_MATCH`가 낮게 나오면 알고리즘 문제가 아니라 corpus coverage 또는 relevance label 문제일 수 있으므로, `corpus-coverage-report.md`를 먼저 확인한다.

## 4. 케이스 JSONL 스키마

`dataset-v1.jsonl`은 300개 case variant의 master 파일이다. `classification-turns-v1.jsonl`은 이 master 파일을 turn 단위로 펼친 Layer 1 분류 실행용 파일이다. 분류 metric은 `classification-turns-v1.jsonl` 기준으로 계산하고, 변호사 매칭 metric은 기본적으로 `is_final_turn=true`인 row만 사용한다.

분류 실행용 turn row는 아래 JSONL 스키마로 변환한다.

```json
{
  "id": "CLX-001-V01-T01",
  "case_id": "CLX-001-V01",
  "conversation_id": "CLX-001-V01",
  "turn_index": 1,
  "is_final_turn": true,
  "benchmark_split": "test",
  "group": "cross_l1",
  "archetype_id": "CLX-001",
  "variant": "V01_short_colloquial",
  "messages": [
    {
      "role": "USER",
      "content": "전세보증금을 못 받고 있는데 집주인 재산에 가압류도 해야 할까요?"
    }
  ],
  "gold_node_ids": ["law-007-01-05", "law-006-03-02"],
  "gold_primary_node_id": "law-007-01-05",
  "allowed_parent_node_ids": ["law-007-01", "law-006-03"],
  "expected_complex": true,
  "matching_label_set_id": "MLS-CLX-001",
  "gold_lawyer_ids": ["L-007-01-05-003", "L-X-007-006-014"],
  "notes": "주택임대차보호 보증금 반환 + 민사보전처분"
}
```

필드 의미:

- `id`: turn 단위 평가 row id. 같은 case라도 평가 turn이 여러 개면 `-T01`, `-T02`처럼 분리한다.
- `case_id`: 300개 case variant 기준 id.
- `gold_node_ids`: 정답 node id 배열. 멀티라벨 평가의 기준.
- `gold_primary_node_id`: 대표 분야. `caseType` 보조 평가에 사용.
- `allowed_parent_node_ids`: L3 대신 L2까지 맞춘 경우 부분 점수를 줄 수 있는 parent id.
- `expected_complex`: 정답 label이 2개 이상이면 true.
- `messages`: 현재 BE의 `contextWindowMessages=4`에 맞춰 최대 최근 4턴까지 구성.
- `conversation_id`: 같은 상담 흐름의 turn별 케이스를 묶는 id.
- `turn_index`: 해당 케이스가 conversation의 몇 번째 평가 시점인지 나타낸다.
- `is_final_turn`: 변호사 매칭 대표 지표에 포함할 최종 상담 상태인지 나타낸다.
- `benchmark_split`: `pilot`, `dev`, `test` 중 하나. final benchmark 대표값은 `test`에서만 산출한다.
- `matching_label_set_id`: `matching-labels-v1.jsonl`의 정답 라벨 세트 id.
- `gold_lawyer_ids`: 해당 사건에 대해 top-K에 들어와야 하는 핵심 변호사 id. 상세 relevance grade는 별도 matching label 파일을 우선한다.

### 4.1 변호사 더미 데이터 JSONL 스키마

`input/lawyers-v1.jsonl`은 아래 스키마를 따른다.

```json
{
  "lawyer_id": "L-007-01-05-003",
  "display_name": "테스트 변호사 007-003",
  "bar_number_fake": "TEST-2026-007003",
  "verification_status": "VERIFIED",
  "practice_node_ids": ["law-007-01-05"],
  "primary_node_id": "law-007-01-05",
  "secondary_node_ids": ["law-006-03-02"],
  "domains": ["임대차보호"],
  "sub_domains": ["주택임대차보호"],
  "tags": ["보증금 반환 및 회수", "가압류", "전세보증금"],
  "experience_years": 9,
  "case_count": 84,
  "region": "서울",
  "bio": "주택임대차 보증금 반환과 보전처분 사건을 주로 처리한 synthetic profile입니다.",
  "embedding_text": "[전문 분야]\n임대차보호. 임대차보호. 임대차보호\n[세부 분야]\n주택임대차보호. 주택임대차보호\n[태그]\n보증금 반환 및 회수. 가압류. 전세보증금\n[자기소개]\n..."
}
```

필드 의미:

- `practice_node_ids`: 평가용 canonical 전문 분야. 매칭 정답 라벨 계산의 기준이다.
- `domains/sub_domains/tags`: 현재 `LawyerProfile`과 `LawyerEmbeddingTextBuilder`에 맞춘 운영 유사 필드다.
- `embedding_text`: 변호사 임베딩 생성 입력. 실제 `LawyerEmbeddingTextBuilder` 출력과 동일한 형식으로 만든다.
- `verification_status`: 실험 adapter가 운영 쿼리와 같은 조건을 재현할 수 있도록 `VERIFIED`만 기본 후보로 사용한다.

### 4.2 매칭 정답 라벨 JSONL 스키마

`input/matching-labels-v1.jsonl`은 case와 lawyer의 관련도를 graded relevance로 둔다.

```json
{
  "label_set_id": "MLS-CLX-001",
  "case_id": "CLX-001-V01",
  "gold_node_ids": ["law-007-01-05", "law-006-03-02"],
  "relevance": [
    {
      "lawyer_id": "L-007-01-05-003",
      "grade": 3,
      "reason": "주택임대차 보증금 반환 L3 정확 일치"
    },
    {
      "lawyer_id": "L-X-007-006-014",
      "grade": 3,
      "reason": "임대차보호와 민사보전처분 cross-domain 일치"
    },
    {
      "lawyer_id": "L-007-01-04-002",
      "grade": 2,
      "reason": "같은 주택임대차 L2의 인접 L3"
    },
    {
      "lawyer_id": "L-001-01-02-011",
      "grade": 0,
      "reason": "부동산 매매계약 전문으로 사건과 무관"
    }
  ]
}
```

grade 기준:

```text
3: 사건 gold L3 또는 복합 gold set을 직접 다루는 핵심 적합 변호사
2: 같은 L2 또는 사건의 일부 쟁점만 강하게 맞는 변호사
1: 같은 L1이지만 L2/L3가 다른 약한 관련 변호사
0: 무관하거나 hard negative 변호사
```

## 5. 발화 변형 규칙

각 archetype마다 아래 10개 변형을 만든다.

| variant | 설명 |
|---|---|
| V01_short_colloquial | 짧고 구어체인 첫 발화 |
| V02_detailed_fact | 사실관계가 비교적 자세한 첫 발화 |
| V03_two_turn_vague_to_detail | 1턴은 모호하게, 2턴에서 쟁점 추가 |
| V04_typo_slang | 오타, 띄어쓰기 오류, 구어체 포함 |
| V05_time_sensitive | 계약일, 퇴사일, 사고일 등 시점 포함 |
| V06_amount_numeric | 금액, 비율, 기간 등 숫자 포함 |
| V07_legal_terms | 가압류, 유류분, 부당해고 등 법률 용어 포함 |
| V08_emotional_noise | 감정 표현과 불필요한 배경 포함 |
| V09_ambiguous_request | 법률 분야를 일부러 흐리게 표현 |
| V10_topic_correction | 두 번째 턴에서 사용자가 분야를 정정하거나 추가 |

예를 들어 `CLX-001`은 다음처럼 10개로 확장한다.

```json
{"id":"CLX-001-V01","messages":[{"role":"USER","content":"전세보증금을 못 받고 있는데 집주인 재산에 가압류도 해야 할까요?"}],"gold_node_ids":["law-007-01-05","law-006-03-02"]}
{"id":"CLX-001-V02","messages":[{"role":"USER","content":"전세계약이 끝났는데 보증금 1억 8천을 못 돌려받았습니다. 집주인이 다른 빚도 많아서 집이나 통장에 가압류를 걸 수 있는지 알고 싶습니다."}],"gold_node_ids":["law-007-01-05","law-006-03-02"]}
{"id":"CLX-001-V03","messages":[{"role":"USER","content":"보증금을 못 받고 있어요."},{"role":"CHATBOT","content":"계약 종류와 현재 상황을 알려주세요."},{"role":"USER","content":"전세이고 계약은 끝났습니다. 집주인 재산을 묶어두는 가압류도 고민 중입니다."}],"gold_node_ids":["law-007-01-05","law-006-03-02"]}
```

다중 턴 variant는 최종 메시지만 평가하지 않는다. 같은 `conversation_id` 안에서 turn별 평가 row를 반드시 별도로 만들고 아래를 함께 측정한다. 한 row 안에 `gold_node_ids_by_turn`을 넣는 방식은 사용하지 않는다.

```text
turn 1: 사용자의 최초 정보만 보고 가능한 범위의 L1/L2/L3 분류
turn 2: 추가 정보가 들어온 뒤 분류가 정교해졌는지
turn 3~4: 사용자가 분야를 정정하거나 복합 쟁점을 추가했을 때 기존 label을 보존하면서 새 label을 추가하는지
```

예를 들어 `V03_two_turn_vague_to_detail`은 `CLX-001-V03-T01`에서 `law-007-01`까지만 정답으로 두고, `CLX-001-V03-T03`에서 `law-007-01-05`, `law-006-03-02`로 확장할 수 있다. 이 경우 최종 F1과 별도로 `turn_l1_accuracy`, `turn_l2_accuracy`, `classification_drift_rate`를 보고한다.

## 6. 300개 테스트셋 Archetype

아래 30개 archetype을 각각 10개 variant로 확장한다.

### 6.1 단일 분야 6개

| id | base user utterance | gold_node_ids | primary |
|---|---|---|---|
| SGL-001 | 전세계약이 끝났는데 보증금을 돌려받지 못했습니다. | `law-007-01-05` | `law-007-01-05` |
| SGL-002 | 회사가 두 달째 임금을 주지 않고 있습니다. | `law-004-02-01` | `law-004-02-01` |
| SGL-003 | 아버지가 돌아가신 뒤 형제들과 상속재산 분할이 안 되고 있습니다. | `law-003-02-01` | `law-003-02-01` |
| SGL-004 | 교통사고 과실비율 때문에 보험사와 다투고 있습니다. | `law-005-02-01` | `law-005-02-01` |
| SGL-005 | 물품을 납품했는데 거래처가 대금을 지급하지 않습니다. | `law-008-01-03` | `law-008-01-03` |
| SGL-006 | 개인회생을 신청할 수 있는지 알고 싶습니다. | `law-006-05-01` | `law-006-05-01` |

### 6.2 동일 L1 내 복합 9개

| id | base user utterance | gold_node_ids | primary |
|---|---|---|---|
| INL1-001 | 임대차 계약이 끝났는데 보증금도 못 받고 원상복구 비용까지 청구받았습니다. | `law-001-02-02`, `law-001-02-05` | `law-001-02-02` |
| INL1-002 | 매매계약을 해제하고 싶은데 이미 중도금 일부를 지급했습니다. | `law-001-01-02`, `law-001-01-03` | `law-001-01-03` |
| INL1-003 | 집을 샀는데 누수가 심하고 등기 이전도 늦어지고 있습니다. | `law-001-01-04`, `law-001-01-05` | `law-001-01-04` |
| INL1-004 | 이혼하면서 위자료와 재산분할, 양육비를 같이 정해야 합니다. | `law-002-02-01`, `law-002-03-02`, `law-002-04-02` | `law-002-03-02` |
| INL1-005 | 유언장이 있는데 형제들이 유류분 반환과 상속재산 분할을 같이 주장합니다. | `law-003-03-02`, `law-003-04-04`, `law-003-02-01` | `law-003-04-04` |
| INL1-006 | 해고도 억울하고 밀린 임금과 퇴직금도 못 받았습니다. | `law-004-04-04`, `law-004-02-01`, `law-004-02-04` | `law-004-04-04` |
| INL1-007 | 병원 치료 후 후유증이 남았고 설명도 제대로 못 들었습니다. | `law-005-03-01`, `law-005-03-02`, `law-005-03-03` | `law-005-03-01` |
| INL1-008 | 보증을 섰는데 채권자가 너무 과하게 추심하고 가압류까지 한다고 합니다. | `law-006-02-02`, `law-006-03-04`, `law-006-03-02` | `law-006-02-02` |
| INL1-009 | 프랜차이즈 계약을 해지하려는데 본사가 위약금과 영업비밀 침해를 주장합니다. | `law-008-03-01`, `law-008-01-04`, `law-008-05-03` | `law-008-01-04` |

### 6.3 서로 다른 L1 복합 12개

| id | base user utterance | gold_node_ids | primary |
|---|---|---|---|
| CLX-001 | 전세보증금을 못 받고 있는데 집주인 재산에 가압류도 해야 할까요? | `law-007-01-05`, `law-006-03-02` | `law-007-01-05` |
| CLX-002 | 월세 보증금을 못 받았고 임대인이 저를 명도소송으로 압박합니다. | `law-001-02-02`, `law-007-03-04` | `law-001-02-02` |
| CLX-003 | 회사에서 괴롭힘을 당하다 해고됐고 우울증 치료비도 청구하고 싶습니다. | `law-004-05-01`, `law-004-04-04`, `law-005-01-03` | `law-004-05-01` |
| CLX-004 | 교통사고 후 회사에 오래 못 나갔는데 해고 통보까지 받았습니다. | `law-005-02-03`, `law-004-04-02` | `law-005-02-03` |
| CLX-005 | 이혼하면서 배우자 명의 아파트 지분과 대출 채무를 어떻게 나눌지 다툽니다. | `law-002-03-01`, `law-002-03-03`, `law-001-04-01` | `law-002-03-01` |
| CLX-006 | 부모님 상속 부동산을 형제가 점유하고 있고 제 지분 등기도 안 해줍니다. | `law-003-02-01`, `law-001-04-01`, `law-001-04-03` | `law-003-02-01` |
| CLX-007 | 동업자가 회사 돈을 가져가고 주식 양도 계약도 지키지 않았습니다. | `law-008-04-01`, `law-008-01-02`, `law-005-01-02` | `law-008-04-01` |
| CLX-008 | 납품대금을 못 받아 지급명령을 하려는데 상대 회사가 폐업 직전입니다. | `law-008-01-03`, `law-006-03-01`, `law-006-03-02` | `law-008-01-03` |
| CLX-009 | 임대차 보증금을 못 받았는데 보증인이 책임을 져야 하는지도 궁금합니다. | `law-007-01-05`, `law-006-02-02` | `law-007-01-05` |
| CLX-010 | 상가 권리금을 못 받고 계약갱신도 거절당했습니다. | `law-007-02-02`, `law-007-02-03`, `law-005-01-02` | `law-007-02-03` |
| CLX-011 | 전 직장에서 영업비밀 침해라며 손해배상을 청구했고, 저는 임금도 못 받았습니다. | `law-008-05-03`, `law-004-02-01`, `law-005-01-02` | `law-008-05-03` |
| CLX-012 | 의료사고로 일을 못 하게 됐고 개인회생 중 변제계획도 조정해야 합니다. | `law-005-03-02`, `law-006-05-02` | `law-005-03-02` |

### 6.4 애매하거나 노이즈가 큰 사건 3개

| id | base user utterance | gold_node_ids | primary |
|---|---|---|---|
| AMB-001 | 돈 문제 때문에 너무 힘든데 상대방이 계속 연락을 피합니다. | `law-006-01` | `law-006-01` |
| AMB-002 | 계약이 잘못된 것 같은데 해지할 수 있는지 모르겠습니다. | `law-008-01` | `law-008-01` |
| AMB-003 | 집 문제랑 가족 문제가 같이 얽혀 있는데 어디부터 봐야 할지 모르겠습니다. | `law-001`, `law-002` | `law-001` |

애매한 사건은 L3 강제 정답을 두지 않는다. 이 그룹에서는 L1/L2 partial match를 허용하고, 과도하게 많은 L3를 붙이는지를 관찰한다.

## 7. 분류 실행 실험 모드

각 300개 케이스를 아래 모드로 반복 실행한다.

| mode | 호출 방식 | 목적 |
|---|---|---|
| A_FULL | `route(messages, null)` | 전체 ontology에서 복합분야 탐지 |
| B_SCOPED_GOLD | `route(messages, goldL1)` | 올바른 L1으로 scope했을 때도 복합 분야가 잘리는지 확인 |
| B_SCOPED_RUNTIME | `route(messages, inferredL1)` | 운영에서 추론된 L1 scope가 만드는 실제 손실 확인 |
| C_HYBRID_RUNTIME | runtime scoped 실행 후 저신뢰/저라벨이면 full ontology 재실행 | 운영 적용 가능성이 높은 절충안 |

provider도 함께 나눈다.

| provider | 설정 |
|---|---|
| Cohere | `AI_CLASSIFY_PROVIDER=cohere` |
| OpenAI | `AI_CLASSIFY_PROVIDER=openai` |

최소 실행 조합:

```text
turn_eval_count x 4 modes x 2 providers
```

비용을 줄여야 하면 1차는 `A_FULL + Cohere`, `A_FULL + OpenAI`만 수행하고, 2차에서 `B_SCOPED_GOLD`, `B_SCOPED_RUNTIME`, `C_HYBRID_RUNTIME`를 수행한다.

`B_SCOPED_GOLD`는 oracle 실험이다. 운영 성능 지표로 사용하지 않고, "scope가 맞아도 다른 L1 label을 구조적으로 잃는가"를 확인하는 진단 지표로만 본다.

`B_SCOPED_RUNTIME`의 `inferredL1`은 아래 중 하나로 산출한다.

1. 실제 운영 상담 상태가 있는 경우 `ClassificationResolver.candidateForCollection(consultation).firstDomain()`
2. 독립 JSONL 실험에서는 `A_FULL` 결과의 `caseType.l1` 또는 첫 번째 valid `matched_node_ids`의 L1

두 방식은 서로 다른 가정을 가지므로 `run-meta.json`에 `runtime_scope_source`로 기록한다.

### 7.1 변호사 매칭 ablation 모드

분류 결과와 매칭 방식의 효과를 분리하기 위해 변호사 매칭은 아래 모드로 실행한다.

| matching mode | label 입력 | ranking 방식 | 목적 |
|---|---|---|---|
| PREDICTED_LABELS_COSINE_ONLY | classifier `pred_node_ids`를 운영 `domains/subDomains/tags`로 변환 | 현재 `LawyerEmbeddingTextBuilder` + `findTopBySimilarity` cosine ranking | 현재 운영 매칭 baseline의 end-to-end 성능 |
| ORACLE_LABELS_COSINE_ONLY | `gold_node_ids`를 운영 `domains/subDomains/tags`로 변환 | 같은 current-service cosine ranking | 분류 오류가 없을 때 current cosine ranking의 upper-bound |
| PREDICTED_LABELS_HYBRID_MATCH | classifier `pred_node_ids` | cosine + field overlap + keyword overlap | 운영 후보 hybrid matcher의 shadow 성능 |
| ORACLE_LABELS_HYBRID_MATCH | `gold_node_ids` | cosine + field overlap + keyword overlap | classification error가 없는 hybrid matcher upper-bound |
| NO_LABEL_COSINE_ONLY | label 미사용, 사용자 발화/brief content만 사용 | current-service query builder의 content-only cosine ranking | 법률 분야 분류를 아예 쓰지 않을 때의 하한선 |

실험 해석:

- `PREDICTED_LABELS_COSINE_ONLY`가 현재 운영 baseline이다. 이 값보다 낮은 mode는 운영 후보가 될 수 없다.
- `ORACLE_LABELS_COSINE_ONLY - PREDICTED_LABELS_COSINE_ONLY`는 Layer 1 분류 오류가 현재 cosine matching에 주는 손실이다.
- `ORACLE_LABELS_HYBRID_MATCH - ORACLE_LABELS_COSINE_ONLY`는 분류 오류가 없다고 가정했을 때 cosine-only ranking 자체의 손실이다.
- `ORACLE_LABELS_HYBRID_MATCH - PREDICTED_LABELS_HYBRID_MATCH`는 분류 오류가 hybrid matching에 주는 손실이다.
- `PREDICTED_LABELS_HYBRID_MATCH - PREDICTED_LABELS_COSINE_ONLY`는 운영 후보 hybrid matcher가 현재 운영 baseline 대비 개선하는 폭이다.
- `ORACLE_LABELS_*`는 병목 분해용 upper-bound다. 운영 성능이나 사용자 노출 품질로 해석하지 않는다.

최소 실행 조합:

```text
turn_eval_count x 4 classification modes x 2 providers
matching_case_count x 5 matching modes x selected classifier arm
```

매칭 실험 비용을 줄여야 하면 먼저 `C_HYBRID_RUNTIME + 선택 provider`의 predicted labels만 사용해 `PREDICTED_LABELS_COSINE_ONLY`, `PREDICTED_LABELS_HYBRID_MATCH`, `ORACLE_LABELS_COSINE_ONLY`, `ORACLE_LABELS_HYBRID_MATCH`를 수행한다. provider별 end-to-end 매칭 비교는 classification 성능 차이가 확인된 뒤 2차로 수행한다.

## 8. Hybrid 모드 규칙

운영 적용 후보인 `C_HYBRID_RUNTIME`는 다음처럼 설계한다.

1. 먼저 현재 상담의 domain이 있으면 scoped ontology로 실행한다.
2. 아래 조건 중 하나라도 만족하면 full ontology로 재실행한다.
   - `matched_node_ids`가 비어 있음
   - `intentConfidence < 0.70`
   - `caseType.confidence < 0.70`
   - `matched_node_ids.size() == 1`인데 입력에 cross-domain trigger가 있음
   - 반환 node가 L1 또는 L2에만 머물고 L3가 없음
3. full ontology 결과가 더 많은 valid node를 반환하고 `intentConfidence >= 0.60` 또는 `caseType.confidence >= 0.60`이면 full 결과를 채택한다.
4. 그렇지 않으면 scoped 결과를 유지한다.

현재 classifier compact output은 `matched_node_ids` 배열만 요구하므로 node별 confidence가 없다. 따라서 hybrid 채택 기준에서 "node confidence"라는 표현을 사용하지 않는다. node별 confidence를 비교하려면 실험용 schema에 `matched_nodes: [{id, confidence}]`를 별도로 추가한 뒤, 기존 운영 parser와의 차이를 report에 명시한다.

cross-domain trigger 예시:

```text
가압류, 가처분, 지급명령, 보증인, 손해배상, 위자료, 임금, 해고, 상속, 지분, 주식, 영업비밀, 회생, 파산
```

## 9. 결과 저장 포맷

각 실행 결과는 JSONL로 저장한다.

```json
{
  "case_id": "CLX-001-V01",
  "provider": "openai",
  "requested_provider": "openai",
  "mode": "A_FULL",
  "input_domain": null,
  "gold_node_ids": ["law-007-01-05", "law-006-03-02"],
  "pred_node_ids": ["law-007-01-05", "law-006-03-02"],
  "case_type": {
    "l1": "임대차보호",
    "l2": "주택임대차보호",
    "l3": "보증금 반환 및 회수",
    "confidence": 0.84
  },
  "dialogue_intent": "ASK_LEGAL_ADVICE",
  "intent_confidence": 0.88,
  "retrieval_queries": ["전세 보증금 반환 가압류"],
  "valid_node_rate": 1.0,
  "response_id": "provider-response-id",
  "latency_ms": 932,
  "tokens_in": 1200,
  "tokens_out": 220,
  "parse_success": true,
  "schema_success": true,
  "fallback_used": false,
  "raw_json_path": "raw/openai_A_FULL.jsonl:1",
  "error_type": null,
  "error_message": null
}
```

필드 해석:

- `provider`: 실제 호출된 provider. 요청 provider가 미등록되어 다른 provider로 대체되면 실제 provider를 기록한다.
- `requested_provider`: runner가 요청한 provider.
- `fallback_used`: `IntentClassificationService` fallback이나 adapter fallback이 사용되었는지 여부.
- `error_type`: `config_error`, `upstream_error`, `parse_failure`, `schema_failure`, `provider_fallback` 중 하나 또는 `null`.
- `raw_json_path`: raw JSON을 별도 파일에 저장한 경우 해당 위치. 공유용 report에서는 raw 본문 대신 경로만 남긴다.
- `latency_ms`, `tokens_in`, `tokens_out`: `IntentClassificationService.route(...)`만 호출하면 얻을 수 없으므로, 실험용 adapter가 raw provider call의 `AiCallResult`를 함께 반환해야 한다.

매칭 실행 결과는 별도 JSONL로 저장한다.

```json
{
  "case_id": "CLX-001-V01",
  "provider": "cohere",
  "classification_mode": "C_HYBRID_RUNTIME",
  "matching_mode": "PREDICTED_LABELS_COSINE_ONLY",
  "label_source": "predicted",
  "current_service_compatible": true,
  "input_node_ids": ["law-007-01-05", "law-006-03-02"],
  "gold_node_ids": ["law-007-01-05", "law-006-03-02"],
  "query_text_hash": "sha256:...",
  "top_k": 10,
  "ranked_lawyers": [
    {
      "rank": 1,
      "lawyer_id": "L-007-01-05-003",
      "practice_node_ids": ["law-007-01-05"],
      "relevance_grade": 3,
      "score": 0.842,
      "score_components": {
        "cosine": 0.842,
        "field_overlap": null,
        "keyword_overlap": null,
        "hybrid_score": null
      }
    }
  ],
  "recall_at_5": 0.5,
  "ndcg_at_10": 0.87,
  "mrr": 1.0,
  "latency_ms": 41,
  "error_type": null,
  "error_message": null
}
```

매칭 결과 저장 원칙:

- `input_node_ids`는 matching mode에 실제 투입된 node id다.
- `label_source`는 `oracle`, `predicted`, `none` 중 하나로 둔다.
- `current_service_compatible=true`인 행은 현재 운영 `LawyerEmbeddingTextBuilder`와 cosine repository 정렬을 재현한 결과다.
- `query_text_hash`는 같은 case/mode가 같은 embedding input으로 실행되었는지 검증하기 위한 값이다. 공유용 report에는 원문 query text 대신 hash만 싣는다.
- `relevance_grade`는 `matching-labels-v1.jsonl` 기준으로 채운다. 라벨 파일에 없는 후보는 기본 `0`으로 처리한다.
- `score_components`는 mode별로 null일 수 있다. cosine-only에서는 `field_overlap`, `keyword_overlap`, `hybrid_score`가 null이다.
- 공유용 report에서는 synthetic lawyer id와 grade만 사용하고, profile 전문은 기본적으로 싣지 않는다.

## 10. 평가 지표

분류 주요 지표는 멀티라벨 기준으로 계산한다.

| metric | 설명 |
|---|---|
| parse_success_rate | JSON parse 성공률 |
| schema_success_rate | 필수 필드 존재 및 타입 정상 비율 |
| valid_node_rate | 반환 node id가 ontology에 실제 존재하는 비율 |
| exact_set_match | 예측 label set이 gold label set과 완전히 같은 비율 |
| micro_precision | 전체 label 기준 precision |
| micro_recall | 전체 label 기준 recall |
| micro_f1 | 전체 label 기준 F1 |
| complex_recall | 복합 케이스에서 gold label을 얼마나 회수했는지 |
| under_classification_rate | 복합 gold인데 예측 label이 1개 이하인 비율 |
| over_classification_rate | gold보다 2개 이상 많은 label을 붙인 비율 |
| primary_accuracy | `gold_primary_node_id`가 예측 label 첫 번째 또는 `caseType`과 맞는 비율 |
| hierarchical_partial_score | L3가 틀려도 같은 L2/L1이면 부분 점수 |
| latency_p50/p95 | 응답 지연 시간 |
| token_avg | 평균 token 사용량 |
| fallback_rate | fallback 응답이 발생한 비율 |
| provider_fallback_rate | 요청 provider와 실제 provider가 다른 비율 |
| config_error_count | API key, model, provider 설정 문제로 실행 불가능한 케이스 수 |
| turn_l1_accuracy | 각 conversation turn에서 L1이 맞은 비율 |
| turn_l2_accuracy | 각 conversation turn에서 L2가 맞은 비율 |
| turn_l3_micro_f1 | 각 conversation turn의 L3 multilabel F1 |
| classification_drift_rate | 이전 turn에서 맞던 주요 node가 다음 turn에서 사라진 비율 |
| stress_micro_f1 | 이 문서의 stress case 분포를 그대로 적용한 micro F1 |
| production_weighted_micro_f1 | `run-meta.json`의 group weight를 적용한 운영 분포 가중 F1 |

hierarchical partial score는 다음처럼 계산한다.

```text
정확한 L3 일치: 1.0
같은 L2 일치: 0.7
같은 L1 일치: 0.4
불일치: 0.0
```

집계 규칙:

- `fallback_used=true`, `error_type=config_error`, `error_type=upstream_error`인 행은 precision/recall/F1에서 제외하고 실패율로 별도 집계한다.
- `parse_success=false` 또는 `schema_success=false`인 행은 정확도 지표에서 제외하지 않고, 예측 label empty로 처리한 결과와 parse/schema 실패율을 함께 보고한다.
- `AMB-*` 그룹은 전체 `micro_f1`의 기본 집계에서 제외한다. 대신 `ambiguous_hierarchical_score`, `ambiguous_over_classification_rate`, `ambiguous_l1_recall`로 별도 보고한다.
- 전체 대표 지표는 `single`, `intra_l1`, `cross_l1` 그룹 기준으로 산출하고, `ambiguous`는 안정성/과분류 관찰 지표로 해석한다.
- 최종 benchmark 대표값은 `benchmark_split=test`에서만 산출한다. pilot/dev 결과는 threshold 조정과 오류 분석에만 사용한다.
- stress 대표값과 production-weighted 대표값을 함께 보고한다. production group weight는 final test 실행 전에 `run-meta.json`에 고정한다.

매칭 주요 지표는 ranked retrieval 기준으로 계산한다.

| metric | 설명 |
|---|---|
| hit_at_1 | rank 1 변호사의 relevance grade가 2 이상인 비율 |
| recall_at_3/5/10 | grade 2 이상 relevant lawyer 중 top-K에 포함된 비율 |
| ndcg_at_5/10 | graded relevance를 반영한 ranking quality |
| mrr | 첫 relevant lawyer가 나타나는 rank의 역수 평균 |
| exact_specialist_recall_at_10 | grade 3 specialist가 top-10에 포함된 비율 |
| cross_domain_coverage_at_10 | 복합 사건에서 각 gold L1/L2를 커버하는 변호사가 top-10에 모두 있는 비율 |
| cosine_only_drop_ndcg_at_10 | `ORACLE_LABELS_HYBRID_MATCH - ORACLE_LABELS_COSINE_ONLY`의 nDCG@10 차이 |
| classification_induced_drop_ndcg_at_10 | `ORACLE_LABELS_COSINE_ONLY - PREDICTED_LABELS_COSINE_ONLY`의 nDCG@10 차이 |
| hybrid_recovery_ndcg_at_10 | `PREDICTED_LABELS_HYBRID_MATCH - PREDICTED_LABELS_COSINE_ONLY`의 nDCG@10 차이 |
| no_label_drop_ndcg_at_10 | `ORACLE_LABELS_COSINE_ONLY - NO_LABEL_COSINE_ONLY`의 nDCG@10 차이 |
| hard_negative_intrusion_rate | grade 0 hard negative가 top-5에 들어온 비율 |
| stress_ndcg_at_10 | stress case 분포를 그대로 적용한 nDCG@10 |
| production_weighted_ndcg_at_10 | group weight를 적용한 운영 분포 가중 nDCG@10 |

매칭 집계 규칙:

- `relevance_grade >= 2`를 relevant로 본다.
- `relevance_grade = 3`은 exact specialist로 별도 집계한다.
- `AMB-*`는 매칭 대표 지표에서 제외하고 exploratory report에만 포함한다.
- 변호사 dummy corpus의 class imbalance가 결과를 왜곡하지 않도록 L1별, group별 macro 평균을 함께 보고한다.
- cosine-only drop은 oracle label 기준과 predicted label 기준을 모두 계산한다. oracle 기준은 매칭 알고리즘 차이, predicted 기준은 실제 운영 end-to-end 차이를 의미한다.
- final matching 대표값은 `is_final_turn=true`이고 `benchmark_split=test`인 case만 사용한다. 중간 turn matching은 exploratory report로 분리한다.
- `PREDICTED_LABELS_COSINE_ONLY`는 current-service baseline이므로 모든 개선폭은 이 모드를 기준으로 계산한다.

## 11. 성공 기준

아래 기준은 pilot 전 임시 기준이다. pilot split으로 runner, corpus, label 품질을 검증한 뒤 dev split에서 threshold와 hybrid weight를 한 번만 조정하고, test split 실행 전 `benchmark-v1` 기준으로 고정한다.

1차 성공 기준:

- `parse_success_rate >= 0.98`
- `valid_node_rate >= 0.99`
- `benchmark_split=test`의 `single + intra_l1 + cross_l1` 기준 `stress_micro_f1 >= 0.75`
- `benchmark_split=test`의 `production_weighted_micro_f1 >= 0.75`
- 복합 케이스 `complex_recall >= 0.70`
- `under_classification_rate <= 0.25`
- `fallback_rate <= 0.02`
- `provider_fallback_rate = 0`
- `config_error_count = 0`

매칭 성공 기준:

- `ORACLE_LABELS_HYBRID_MATCH` 기준 `ndcg_at_10 >= 0.85`
- current-service baseline인 `PREDICTED_LABELS_COSINE_ONLY`의 `ndcg_at_10`을 반드시 먼저 기록할 것
- `PREDICTED_LABELS_HYBRID_MATCH` 기준 `stress_ndcg_at_10 >= 0.75`
- `PREDICTED_LABELS_HYBRID_MATCH` 기준 `production_weighted_ndcg_at_10 >= 0.75`
- `PREDICTED_LABELS_HYBRID_MATCH` 기준 `recall_at_10 >= 0.80`
- `PREDICTED_LABELS_COSINE_ONLY` 대비 `PREDICTED_LABELS_HYBRID_MATCH`의 `ndcg_at_10` 개선폭이 0.05 이상일 것
- `classification_induced_drop_ndcg_at_10 <= 0.10`
- 복합 사건 `cross_domain_coverage_at_10 >= 0.70`
- `hard_negative_intrusion_rate <= 0.15`

운영 후보 기준:

- `C_HYBRID_RUNTIME`가 `A_FULL` 대비 F1이 크게 떨어지지 않을 것
- `C_HYBRID_RUNTIME`가 `B_SCOPED_RUNTIME` 대비 복합 recall을 유의미하게 개선할 것
- `B_SCOPED_GOLD`와 `B_SCOPED_RUNTIME`의 차이로 scope 자체의 손실과 runtime inference 손실을 분리해 설명할 수 있을 것
- Cohere와 OpenAI 비교는 운영 provider 전환 결정이 아니라 production config shadow evidence로 해석할 것
- 변호사 매칭 운영 후보는 `PREDICTED_LABELS_HYBRID_MATCH`가 `PREDICTED_LABELS_COSINE_ONLY`보다 ranking metric에서 일관되게 높을 때만 검토할 것
- cosine-only와 hybrid 차이가 작다면 매칭 알고리즘보다 Layer 1 분류 정확도 또는 변호사 프로필/임베딩 텍스트 품질을 먼저 개선할 것

## 12. 실행 파이프라인

```text
1. SHIELD_BE develop 브랜치 checkout
2. 실행 commit SHA 기록
3. SHIELD_BE의 legal-ontology-slim.json을 EXPERIMENT/input에 snapshot으로 복사
4. EXPERIMENT/input/archetypes-v1.json 작성
5. EXPERIMENT/input/splits-v1.json에 pilot/dev/test split과 production group weight 고정
6. EXPERIMENT/input/lawyer-corpus-generator-config.yaml 작성
7. EXPERIMENT/input/lawyers-v1.jsonl과 matching-labels-v1.jsonl 작성 또는 generator로 생성
8. runner가 archetype x variant를 EXPERIMENT/input/dataset-v1.jsonl 300개로 확장
9. runner가 multi-turn case를 EXPERIMENT/input/classification-turns-v1.jsonl로 펼침
10. 로컬 BE 실행 또는 실험용 adapter 실행
11. 변호사 dummy corpus를 local/test DB 또는 adapter 전용 in-memory store에 적재
12. EXPERIMENT/runner/run_experiment.py 실행
13. runner가 dataset.py, client.py, modes.py, matching.py, evaluator.py, report.py를 import
14. provider/mode별 실험용 intent adapter 결과 수집
15. selected classifier arm의 predicted labels와 oracle labels로 matching adapter 결과 수집
16. raw response와 parsed/matching response를 EXPERIMENT/output/{run_id}에 저장
17. ontology id validation, classification metric, matching metric, corpus coverage 계산
18. provider/mode/group/matching-mode별 리포트 생성
```

runner 실행 예시:

```text
C:\Users\mmkan\바탕 화면\EXPERIMENT\
  runner\
    run_experiment.py
```

```powershell
cd "C:\Users\mmkan\바탕 화면\EXPERIMENT\runner"
python .\run_experiment.py --config .\config.yaml
```

권장 output 구조:

```text
C:\Users\mmkan\바탕 화면\EXPERIMENT\output\
  2026-06-15_2330_develop_382b2c18\
    run-meta.json
    raw\
      openai_A_FULL.jsonl
      openai_B_SCOPED_GOLD.jsonl
      openai_B_SCOPED_RUNTIME.jsonl
      openai_C_HYBRID_RUNTIME.jsonl
      cohere_A_FULL.jsonl
      cohere_B_SCOPED_GOLD.jsonl
      cohere_B_SCOPED_RUNTIME.jsonl
      cohere_C_HYBRID_RUNTIME.jsonl
    parsed\
      openai_A_FULL.parsed.jsonl
      openai_B_SCOPED_GOLD.parsed.jsonl
      openai_B_SCOPED_RUNTIME.parsed.jsonl
      openai_C_HYBRID_RUNTIME.parsed.jsonl
      cohere_A_FULL.parsed.jsonl
      cohere_B_SCOPED_GOLD.parsed.jsonl
      cohere_B_SCOPED_RUNTIME.parsed.jsonl
      cohere_C_HYBRID_RUNTIME.parsed.jsonl
    matching\
      oracle_labels_cosine_only.jsonl
      predicted_labels_cosine_only.jsonl
      oracle_labels_hybrid_match.jsonl
      predicted_labels_hybrid_match.jsonl
      no_label_cosine_only.jsonl
    reports\
      metrics-summary.md
      matching-metrics-summary.md
      benchmark-validity-check.md
      current-service-baseline.md
      corpus-coverage-report.md
      confusion-by-l1.csv
      failure-cases.md
      scoped-ontology-loss.md
      cosine-vs-hybrid-matching.md
      classification-to-matching-loss.md
```

`run-meta.json`에는 최소한 아래 값을 기록한다.

```json
{
  "run_id": "2026-06-15_2330_develop_382b2c18",
  "repo": "capstoneSHIELD/SHIELD_BE",
  "branch": "develop",
  "commit_sha": "382b2c18...",
  "dataset_path": "../input/dataset-v1.jsonl",
  "classification_turns_path": "../input/classification-turns-v1.jsonl",
  "splits_path": "../input/splits-v1.json",
  "ontology_snapshot_path": "../input/legal-ontology-slim.snapshot.json",
  "lawyer_corpus_path": "../input/lawyers-v1.jsonl",
  "lawyer_corpus_generator_config_path": "../input/lawyer-corpus-generator-config.yaml",
  "matching_labels_path": "../input/matching-labels-v1.jsonl",
  "lawyer_corpus_seed": 20260615,
  "providers": ["openai", "cohere"],
  "modes": ["A_FULL", "B_SCOPED_GOLD", "B_SCOPED_RUNTIME", "C_HYBRID_RUNTIME"],
  "matching_modes": [
    "ORACLE_LABELS_COSINE_ONLY",
    "PREDICTED_LABELS_COSINE_ONLY",
    "ORACLE_LABELS_HYBRID_MATCH",
    "PREDICTED_LABELS_HYBRID_MATCH",
    "NO_LABEL_COSINE_ONLY"
  ],
  "runtime_scope_source": "A_FULL_caseType_l1",
  "production_group_weights": {
    "single": 0.40,
    "intra_l1": 0.25,
    "cross_l1": 0.25,
    "ambiguous": 0.10
  },
  "hybrid_match_weights": {
    "cosine": 0.60,
    "fieldOverlap": 0.25,
    "keywordOverlap": 0.15
  },
  "keyword_overlap_metric": "overlap_coefficient"
}
```

## 12.1 관련 설계 산출물

- 클래스 다이어그램: [pipeline-class-diagram.md](./ai-complex-law-classification-experiment-diagrams/pipeline-class-diagram.md)

## 12.2 runner 코드 책임 분리

`runner` 폴더의 코드는 아래처럼 작게 나눈다.

| 파일 | 책임 |
|---|---|
| `run_experiment.py` | CLI entrypoint. config 로드, run_id 생성, 전체 실행 순서 제어 |
| `shield_experiment/dataset.py` | `input/archetypes-v1.json`을 `dataset-v1.jsonl`로 확장하고, multi-turn case를 `classification-turns-v1.jsonl`로 펼치거나 기존 JSONL 로드 |
| `shield_experiment/client.py` | 로컬 BE 실험용 adapter 호출. provider/mode/domain/message를 request로 변환하고 preflight/config error를 분리 |
| `shield_experiment/modes.py` | `A_FULL`, `B_SCOPED_GOLD`, `B_SCOPED_RUNTIME`, `C_HYBRID_RUNTIME` 실행 규칙 구현 |
| `shield_experiment/matching.py` | lawyer corpus 로드/검증, current-service cosine baseline 재현 검증, matching adapter 호출, matching mode별 결과 정규화 |
| `shield_experiment/evaluator.py` | valid node, precision/recall/F1, hierarchical partial score 계산 |
| `shield_experiment/report.py` | `metrics-summary.md`, `matching-metrics-summary.md`, `benchmark-validity-check.md`, `current-service-baseline.md`, `corpus-coverage-report.md`, `failure-cases.md`, `confusion-by-l1.csv` 생성 |

runner는 BE의 분류 로직을 재구현하지 않는다. 반드시 BE의 실험용 adapter가 반환한 raw provider call 결과와 `IntentClassificationService` parser 결과를 받아 평가만 한다. 이렇게 해야 실험 결과가 현재 AI 파이프라인과 어긋나지 않는다.

runner는 BE의 운영 변호사 매칭 로직도 임의로 재구현하지 않는다. 다만 현재 운영에는 hybrid matcher가 없으므로, `HYBRID_MATCH`는 실험용 adapter 또는 runner의 명시적 scoring module로 분리하고 report에서 "운영 구현이 아닌 실험 비교군"이라고 표기한다.

runner 시작 전 preflight:

1. `/internal/experiments/intent-route/preflight` 또는 동등한 adapter 호출로 provider별 사용 가능 여부를 확인한다.
2. `AI_CLASSIFY_PROVIDER=openai` 실험은 `OPENAI_API_KEY`, `OPENAI_CLASSIFY_MODEL`, `OPENAI_CLASSIFY_REASONING_EFFORT`가 실제 호출 가능한 경우에만 실행한다.
3. provider가 미등록되거나 API key가 없거나 model 오류가 나면 해당 provider arm 전체를 `config_error`로 기록하고 정확도 지표에서 제외한다.
4. requested provider와 actual provider가 다르면 `provider_fallback`으로 기록하고 해당 행을 운영 성능 비교에서 제외한다.
5. `classification-turns-v1.jsonl` preflight에서 모든 row의 `case_id`, `conversation_id`, `turn_index`, `is_final_turn`, `benchmark_split`, `gold_node_ids`를 검증한다.
6. 변호사 corpus preflight에서 `practice_node_ids`가 ontology snapshot에 존재하는지, 각 L1/L2/L3 coverage가 충분한지, relevance label의 lawyer id가 corpus에 존재하는지 검증한다.
7. matching adapter preflight에서 `PREDICTED_LABELS_COSINE_ONLY`가 현재 `LawyerMatchingService`의 query text 생성과 `LawyerEmbeddingRepository` 정렬 기준을 재현하는지 확인한다.
8. matching adapter preflight에서 `HYBRID_MATCH`가 run-meta의 weight를 그대로 쓰는지, dev/test 실행 중 weight가 바뀌지 않았는지 확인한다.

## 13. 실패 케이스 분석 기준

실패 케이스는 아래 유형으로 태깅한다.

| tag | 의미 |
|---|---|
| missed_secondary_area | 대표 분야만 맞고 부수 분야를 놓침 |
| over_broad_l1 | L3 대신 L1만 반환 |
| wrong_sibling_l3 | 같은 L2 안에서 잘못된 L3 선택 |
| wrong_l1 | 완전히 다른 대분류 선택 |
| scoped_ontology_loss | scoped ontology 때문에 다른 분야를 반환할 수 없었음 |
| invalid_node_id | ontology에 없는 node id 반환 |
| legal_advice_leak | 분류 외 법률 판단 표현 생성 |
| parse_failure | JSON parse 실패 |
| schema_failure | 필수 필드 누락 또는 타입 불일치 |
| provider_fallback | 요청 provider와 실제 provider가 다름 |
| config_error | API key, model, provider 설정 문제로 해당 arm 실행 불가 |
| upstream_error | provider API 호출 실패 |
| matching_missed_exact_specialist | grade 3 변호사가 top-K에 없음 |
| matching_hard_negative_top_rank | grade 0 hard negative가 top-3에 진입 |
| matching_cross_domain_gap | 복합 사건에서 일부 gold L1/L2 전문 변호사가 top-K에 없음 |
| classification_to_matching_loss | oracle label 매칭은 성공했지만 predicted label 매칭은 실패 |
| cosine_only_ranking_loss | oracle label cosine-only가 oracle label hybrid보다 낮은 랭킹 품질 |
| lawyer_corpus_coverage_gap | 해당 gold node를 다루는 synthetic lawyer가 부족함 |
| current_service_baseline_mismatch | `PREDICTED_LABELS_COSINE_ONLY`가 운영 `LawyerMatchingService` query/ranking 경로를 재현하지 못함 |
| benchmark_split_leakage | pilot/dev/test split이 섞였거나 test 결과를 보고 threshold/weight를 조정함 |
| hybrid_weight_drift | final test 실행 중 hybrid weight 또는 keyword overlap 방식이 바뀜 |

특히 `scoped_ontology_loss`는 이번 실험의 핵심 관찰 대상이다. 복합 사건인데 `domain`이 한 L1로 좁혀져 있으면 현재 구조상 다른 L1 label을 반환할 수 없다.

## 14. 샘플 상세 케이스 10개

```json
{"id":"CLX-001-V01","group":"cross_l1","messages":[{"role":"USER","content":"전세보증금을 못 받고 있는데 집주인 재산에 가압류도 해야 할까요?"}],"gold_node_ids":["law-007-01-05","law-006-03-02"],"gold_primary_node_id":"law-007-01-05","expected_complex":true}
{"id":"CLX-003-V02","group":"cross_l1","messages":[{"role":"USER","content":"회사에서 계속 괴롭힘을 당하다가 결국 해고됐습니다. 정신과 치료도 받고 있어서 치료비나 위자료도 청구하고 싶습니다."}],"gold_node_ids":["law-004-05-01","law-004-04-04","law-005-01-03"],"gold_primary_node_id":"law-004-05-01","expected_complex":true}
{"id":"INL1-006-V06","group":"intra_l1","messages":[{"role":"USER","content":"해고 통보를 받은 지 20일 됐고, 3개월치 임금과 퇴직금 700만원도 아직 못 받았습니다."}],"gold_node_ids":["law-004-04-04","law-004-02-01","law-004-02-04"],"gold_primary_node_id":"law-004-04-04","expected_complex":true}
{"id":"CLX-008-V07","group":"cross_l1","messages":[{"role":"USER","content":"납품대금 미지급 때문에 지급명령을 생각 중인데 상대 회사가 폐업 직전이라 가압류가 필요한지도 궁금합니다."}],"gold_node_ids":["law-008-01-03","law-006-03-01","law-006-03-02"],"gold_primary_node_id":"law-008-01-03","expected_complex":true}
{"id":"SGL-002-V04","group":"single","messages":[{"role":"USER","content":"월급 두달째 안줘요 퇴사했는데도 계속 미루네요"}],"gold_node_ids":["law-004-02-01"],"gold_primary_node_id":"law-004-02-01","expected_complex":false}
{"id":"INL1-004-V10","group":"intra_l1","messages":[{"role":"USER","content":"이혼 상담이 필요합니다."},{"role":"CHATBOT","content":"어떤 부분이 가장 걱정되시나요?"},{"role":"USER","content":"처음엔 위자료만 생각했는데 재산분할이랑 아이 양육비도 같이 봐야 할 것 같습니다."}],"gold_node_ids":["law-002-02-01","law-002-03-02","law-002-04-02"],"gold_primary_node_id":"law-002-03-02","expected_complex":true}
{"id":"CLX-006-V03","group":"cross_l1","messages":[{"role":"USER","content":"상속받은 집 문제입니다."},{"role":"CHATBOT","content":"누가 점유하고 있고 어떤 분쟁이 있나요?"},{"role":"USER","content":"형이 계속 살고 있고 제 지분 등기나 인도를 거부합니다."}],"gold_node_ids":["law-003-02-01","law-001-04-01","law-001-04-03"],"gold_primary_node_id":"law-003-02-01","expected_complex":true}
{"id":"CLX-011-V08","group":"cross_l1","messages":[{"role":"USER","content":"전 회사가 너무 화가 납니다. 영업비밀 빼갔다고 손해배상을 하라는데, 정작 저는 마지막 월급도 못 받았습니다."}],"gold_node_ids":["law-008-05-03","law-004-02-01","law-005-01-02"],"gold_primary_node_id":"law-008-05-03","expected_complex":true}
{"id":"AMB-003-V09","group":"ambiguous","messages":[{"role":"USER","content":"집이랑 가족 사이 돈 문제가 같이 얽혀 있는데 정확히 어떤 문제인지 모르겠습니다."}],"gold_node_ids":["law-001","law-002"],"gold_primary_node_id":"law-001","expected_complex":true}
{"id":"SGL-006-V05","group":"single","messages":[{"role":"USER","content":"카드빚과 대출이 8천만원 정도 있고 6개월째 연체 중인데 개인회생 신청 요건이 되는지 궁금합니다."}],"gold_node_ids":["law-006-05-01"],"gold_primary_node_id":"law-006-05-01","expected_complex":false}
```

## 15. 실험 후 의사결정

실험 결과에 따라 다음 중 하나를 선택한다.

1. `A_FULL`이 압도적으로 좋으면 intent router는 full ontology를 기본으로 사용한다.
2. `B_SCOPED_GOLD`가 좋고 `B_SCOPED_RUNTIME`이 낮으면 scope 방식보다 runtime L1 추론 품질을 먼저 개선한다.
3. `B_SCOPED_GOLD`도 복합 recall이 낮으면 scoped는 상담 수집용으로만 쓰고, 복합 감지는 full 재분류를 추가한다.
4. `C_HYBRID_RUNTIME`가 비용과 성능 균형이 좋으면 운영 후보로 채택한다.
5. Cohere와 OpenAI 중 한 provider가 parse 안정성 또는 비용에서 우위여도, 이것만으로 운영 classify provider를 교체하지 않는다. 기존 AI/RAG phase 문서의 "RAG backbone provider 교체는 별도 phase 결정" 원칙에 따라 shadow evidence로만 기록한다.
6. provider 전환을 실제 후보로 올리려면 별도 설계 문서에서 비용, latency, rate limit, 개인정보, 장애 fallback, feature flag, rollback 기준을 다시 정의한다.
7. current-service baseline인 `PREDICTED_LABELS_COSINE_ONLY`가 낮고 `ORACLE_LABELS_COSINE_ONLY`가 높으면 매칭 알고리즘보다 Layer 1 분류 정확도를 먼저 개선한다.
8. `ORACLE_LABELS_COSINE_ONLY`도 낮고 `ORACLE_LABELS_HYBRID_MATCH`가 높으면 cosine-only ranking이 병목이므로 변호사 매칭 hybrid score 도입을 검토한다.
9. `ORACLE_LABELS_HYBRID_MATCH`도 낮으면 더미 변호사 corpus, relevance label, 변호사 profile embedding text 설계를 먼저 재검토한다.
10. `NO_LABEL_COSINE_ONLY`가 current-service predicted label 방식과 큰 차이가 없으면 현재 분류 정보가 변호사 매칭 쿼리에 충분히 반영되지 않는 것이므로 `LawyerEmbeddingTextBuilder` 또는 matching query schema를 개선한다.
11. `matched_node_ids`가 의뢰서/매칭에 필요하면 `brief` 또는 `consultation`에 JSONB 저장을 추가한다.
12. hybrid matcher를 운영 후보로 올리려면 별도 설계 문서에서 score weight, explainability, cache, pagination, region/availability filter, rollback 기준을 정의한다.

권장 방향은 `C_HYBRID_RUNTIME`이다. 현재 BE의 domain 기반 흐름을 유지하면서도 복합분야 손실을 줄일 수 있기 때문이다.

변호사 매칭의 권장 1차 방향은 `PREDICTED_LABELS_COSINE_ONLY`를 current-service baseline으로 두고, `PREDICTED_LABELS_HYBRID_MATCH`를 실험 후보로 비교하는 것이다. 이때 `ORACLE_LABELS_*` 결과는 운영 성능이 아니라 병목 분해용 upper-bound로만 해석한다.
