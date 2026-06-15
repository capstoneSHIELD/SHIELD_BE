# AI 복합 법률 분야 분류 실험 설계

기준 저장소: `capstoneSHIELD/SHIELD_BE` `develop` 브랜치  
작성일: 2026-06-15  
대상 파이프라인: `IntentClassificationService.route(...)` 기반 intent router

## 0. 로컬 실험 디렉터리 구조

실험 파일은 아래 로컬 폴더를 기준으로 관리한다.

```text
C:\Users\mmkan\바탕 화면\EXPERIMENT\
  input\
    dataset-v1.jsonl
    archetypes-v1.json
    legal-ontology-slim.snapshot.json
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
      report.py
  output\
    .gitkeep
```

역할은 다음과 같이 분리한다.

| 폴더 | 역할 |
|---|---|
| `input` | 300개 테스트셋 JSONL, archetype 원본, 실험 시점의 ontology snapshot 저장 |
| `runner` | 실험 실행 코드 저장. `run_experiment.py`가 `shield_experiment` 패키지를 import해서 실행 |
| `output` | 실행 결과, raw response, metric summary, failure report 저장 |

이 구조를 쓰면 데이터셋과 실행 코드, 결과물이 섞이지 않는다. 실험을 다시 돌릴 때는 `input/dataset-v1.jsonl`과 `runner/config.yaml`만 고정하고, `output/{run_id}` 아래에 매 실행 결과를 새로 저장한다.

권장 실행 방식은 Python runner가 로컬에서 실행 중인 SHIELD BE의 실험용 adapter를 호출하는 방식이다. 현재 운영 API의 `POST /api/consultations/{id}/classify`는 `primaryField`만 반환하므로, 이번 실험에 필요한 `matched_node_ids`를 얻으려면 `IntentClassificationService.route(...)` 결과를 노출하는 실험용 경로가 필요하다.

권장 adapter 선택지는 두 가지다.

| 방식 | 설명 | 추천도 |
|---|---|---|
| HTTP adapter | BE에 local/test profile 전용 `POST /internal/experiments/intent-route`를 두고 runner가 HTTP로 호출 | 높음 |
| Spring test runner | BE의 `@SpringBootTest`가 `input/dataset-v1.jsonl`을 읽고 `output`에 직접 저장 | 중간 |

이 문서에서는 사용자가 요청한 폴더 구조에 맞춰 `runner` 폴더의 Python orchestrator가 `client.py`, `dataset.py`, `evaluator.py`, `report.py`를 import해서 실행하는 방식을 기본으로 둔다.

## 1. 실험 목적

현재 BE의 AI 분류 흐름은 단일 `primaryField`만으로 복합 법률 분야를 평가하기 어렵다. 따라서 이번 실험은 `primaryField` 5분류가 아니라, intent router가 반환하는 `matched_node_ids`를 기준으로 멀티라벨 법률 분야 분류 성능을 측정한다.

핵심 질문은 다음과 같다.

1. 사용자의 첫 발화 또는 최근 대화 4턴만으로 복합 법률 분야를 얼마나 잘 찾는가?
2. `domain`으로 ontology를 특정 L1에 scope했을 때 복합 분야가 잘리는가?
3. `full ontology`와 `scoped ontology`를 조합한 hybrid 방식이 운영 적용에 적합한가?
4. Cohere classifier와 OpenAI classifier 중 어떤 provider가 구조화 출력 안정성과 복합 label recall이 좋은가?

## 2. 현재 BE 기준 분류 지점

현재 `develop` 브랜치에서 법률 분야 분류 실험에 가장 적합한 지점은 다음이다.

- `IntentClassificationService.route(List<Message> recentMessages, String domain)`
- prompt: `src/main/resources/ai/prompts/rag/intent-classifier.md`
- ontology: `src/main/resources/ontology/legal-ontology-slim.json`
- 출력 필드: `dialogueIntent`, `intentConfidence`, `caseType`, `matched_node_ids`, `core_keywords`, `retrievalQueries`

실험의 주 평가 대상은 `matched_node_ids`이다.

`caseType`은 단일 `l1/l2/l3` 구조이므로 복합분야의 보조 지표로만 사용한다. 복합분야 정답이 2개 이상일 때 `caseType` 하나만 맞았다고 성공으로 보면 안 된다.

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

## 4. 케이스 JSONL 스키마

실제 실행용 데이터는 아래 JSONL 스키마로 변환한다.

```json
{
  "id": "CLX-001-V01",
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
  "notes": "주택임대차보호 보증금 반환 + 민사보전처분"
}
```

필드 의미:

- `gold_node_ids`: 정답 node id 배열. 멀티라벨 평가의 기준.
- `gold_primary_node_id`: 대표 분야. `caseType` 보조 평가에 사용.
- `allowed_parent_node_ids`: L3 대신 L2까지 맞춘 경우 부분 점수를 줄 수 있는 parent id.
- `expected_complex`: 정답 label이 2개 이상이면 true.
- `messages`: 현재 BE의 `contextWindowMessages=4`에 맞춰 최대 최근 4턴까지 구성.

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

## 7. 실행 실험 모드

각 300개 케이스를 아래 모드로 반복 실행한다.

| mode | 호출 방식 | 목적 |
|---|---|---|
| A_FULL | `route(messages, null)` | 전체 ontology에서 복합분야 탐지 |
| B_SCOPED | `route(messages, inferredOrGoldL1)` | 현재 domain scoping 방식의 손실 확인 |
| C_HYBRID | scoped 실행 후 저신뢰/저라벨이면 full ontology 재실행 | 운영 적용 가능성이 높은 절충안 |

provider도 함께 나눈다.

| provider | 설정 |
|---|---|
| Cohere | `AI_CLASSIFY_PROVIDER=cohere` |
| OpenAI | `AI_CLASSIFY_PROVIDER=openai` |

최소 실행 조합:

```text
300 cases x 3 modes x 2 providers = 1,800 classifier calls
```

비용을 줄여야 하면 1차는 `A_FULL + Cohere`, `A_FULL + OpenAI`만 수행하고, 2차에서 `B_SCOPED`, `C_HYBRID`를 수행한다.

## 8. Hybrid 모드 규칙

운영 적용 후보인 `C_HYBRID`는 다음처럼 설계한다.

1. 먼저 현재 상담의 domain이 있으면 scoped ontology로 실행한다.
2. 아래 조건 중 하나라도 만족하면 full ontology로 재실행한다.
   - `matched_node_ids`가 비어 있음
   - `intentConfidence < 0.70`
   - `caseType.confidence < 0.70`
   - `matched_node_ids.size() == 1`인데 입력에 cross-domain trigger가 있음
   - 반환 node가 L1 또는 L2에만 머물고 L3가 없음
3. full ontology 결과가 더 많은 valid node를 반환하고 confidence가 0.60 이상이면 full 결과를 채택한다.
4. 그렇지 않으면 scoped 결과를 유지한다.

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
  "latency_ms": 932,
  "tokens_in": 1200,
  "tokens_out": 220,
  "parse_success": true,
  "error": null
}
```

## 10. 평가 지표

주요 지표는 멀티라벨 기준으로 계산한다.

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

hierarchical partial score는 다음처럼 계산한다.

```text
정확한 L3 일치: 1.0
같은 L2 일치: 0.7
같은 L1 일치: 0.4
불일치: 0.0
```

## 11. 성공 기준

1차 성공 기준:

- `parse_success_rate >= 0.98`
- `valid_node_rate >= 0.99`
- 전체 `micro_f1 >= 0.75`
- 복합 케이스 `complex_recall >= 0.70`
- `under_classification_rate <= 0.25`

운영 후보 기준:

- `C_HYBRID`가 `A_FULL` 대비 F1이 크게 떨어지지 않을 것
- `C_HYBRID`가 `B_SCOPED` 대비 복합 recall을 유의미하게 개선할 것
- OpenAI와 Cohere 중 한 provider가 parse 안정성 또는 비용 측면에서 확실한 우위가 있을 것

## 12. 실행 파이프라인

```text
1. SHIELD_BE develop 브랜치 checkout
2. 실행 commit SHA 기록
3. SHIELD_BE의 legal-ontology-slim.json을 EXPERIMENT/input에 snapshot으로 복사
4. EXPERIMENT/input/archetypes-v1.json 작성
5. runner가 archetype x variant를 EXPERIMENT/input/dataset-v1.jsonl 300개로 확장
6. 로컬 BE 실행 또는 실험용 adapter 실행
7. EXPERIMENT/runner/run_experiment.py 실행
8. runner가 dataset.py, client.py, modes.py, evaluator.py, report.py를 import
9. provider/mode별 route 결과 수집
10. raw response와 parsed response를 EXPERIMENT/output/{run_id}에 저장
11. ontology id validation과 metric 계산
12. provider/mode/group별 리포트 생성
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
      openai_B_SCOPED.jsonl
      openai_C_HYBRID.jsonl
      cohere_A_FULL.jsonl
      cohere_B_SCOPED.jsonl
      cohere_C_HYBRID.jsonl
    parsed\
      openai_A_FULL.parsed.jsonl
      openai_B_SCOPED.parsed.jsonl
      openai_C_HYBRID.parsed.jsonl
      cohere_A_FULL.parsed.jsonl
      cohere_B_SCOPED.parsed.jsonl
      cohere_C_HYBRID.parsed.jsonl
    reports\
      metrics-summary.md
      confusion-by-l1.csv
      failure-cases.md
      scoped-ontology-loss.md
```

`run-meta.json`에는 최소한 아래 값을 기록한다.

```json
{
  "run_id": "2026-06-15_2330_develop_382b2c18",
  "repo": "capstoneSHIELD/SHIELD_BE",
  "branch": "develop",
  "commit_sha": "382b2c18...",
  "dataset_path": "../input/dataset-v1.jsonl",
  "ontology_snapshot_path": "../input/legal-ontology-slim.snapshot.json",
  "providers": ["openai", "cohere"],
  "modes": ["A_FULL", "B_SCOPED", "C_HYBRID"]
}
```

## 12.1 runner 코드 책임 분리

`runner` 폴더의 코드는 아래처럼 작게 나눈다.

| 파일 | 책임 |
|---|---|
| `run_experiment.py` | CLI entrypoint. config 로드, run_id 생성, 전체 실행 순서 제어 |
| `shield_experiment/dataset.py` | `input/archetypes-v1.json`을 `dataset-v1.jsonl`로 확장하거나 기존 JSONL 로드 |
| `shield_experiment/client.py` | 로컬 BE 실험용 adapter 호출. provider/mode/domain/message를 request로 변환 |
| `shield_experiment/modes.py` | `A_FULL`, `B_SCOPED`, `C_HYBRID` 실행 규칙 구현 |
| `shield_experiment/evaluator.py` | valid node, precision/recall/F1, hierarchical partial score 계산 |
| `shield_experiment/report.py` | `metrics-summary.md`, `failure-cases.md`, `confusion-by-l1.csv` 생성 |

runner는 BE의 분류 로직을 재구현하지 않는다. 반드시 BE의 `IntentClassificationService.route(...)` 결과를 받아 평가만 한다. 이렇게 해야 실험 결과가 현재 AI 파이프라인과 어긋나지 않는다.

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
2. `B_SCOPED`가 단일 분야에서는 좋지만 복합 recall이 낮으면 scoped는 상담 수집용으로만 쓰고, 복합 감지는 full 재분류를 추가한다.
3. `C_HYBRID`가 비용과 성능 균형이 좋으면 운영 후보로 채택한다.
4. `matched_node_ids`가 의뢰서/매칭에 필요하면 `brief` 또는 `consultation`에 JSONB 저장을 추가한다.

권장 방향은 `C_HYBRID`이다. 현재 BE의 domain 기반 흐름을 유지하면서도 복합분야 손실을 줄일 수 있기 때문이다.
