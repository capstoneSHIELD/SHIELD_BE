# 복합 법률 분야 분류 실험 결과 보고서

작성일: 2026-06-17 KST  
브랜치: `experiment/complex-law-classification-pipeline`  
실험 실행 디렉터리: `eval/complex-law-classification-experiment/output/2026-06-16_2208_experiment_complex-law-classification-pipeline_5eb9aa2c`

## 1. 실험 목적

이번 실험의 목적은 SHIELD의 AI 법률 분야 분류 파이프라인이 단일 법률 분야 상담뿐 아니라, 하나의 상담 안에 여러 법률 쟁점이 섞여 있는 복합 상담을 어느 정도 분류할 수 있는지 확인하는 것이다.

특히 단순히 "모델이 정답을 몇 개 맞혔는가"만 보려는 실험이 아니라, 다음 질문에 답하기 위한 실험이다.

1. 전체 법률 ontology를 한 번에 주고 분류하는 방식이 좋은가?
2. 먼저 L1 대분류 범위를 좁힌 뒤, 그 범위 안에서 세부 분류를 수행하는 방식이 좋은가?
3. L1 범위를 정확히 좁힐 수 있다면 성능 상한이 어느 정도 올라가는가?
4. 실제 런타임에서 L1을 추정해서 좁히는 방식도 의미 있는 개선을 만드는가?
5. 복합 분야 케이스에서 secondary issue를 얼마나 잘 놓치지 않는가?
6. 토큰 비용과 지연시간 관점에서 scoped ontology 방식이 실용적인가?

따라서 이번 실험의 핵심 가설은 다음과 같다.

> 전체 ontology를 모두 넣고 분류하는 것보다, 먼저 L1 layer에서 법률 분야 범위를 좁힌 다음 세부 node를 분류하는 방식이 더 의미 있을 것이다.

## 2. 실험 계획과 설계 기준

실험은 저장소에 구현된 실험 runner와 내부 backend adapter를 기준으로 설계했다.

참고한 구현 기준 문서는 다음과 같다.

- `docs/experiment-pipeline.md`
- `eval/complex-law-classification-experiment/runner/README.md`
- runner README에 명시된 class diagram 기반 설계 문서:
  - `ai-complex-law-classification-experiment.md`
  - `pipeline-class-diagram.md`

실험 runner는 class diagram 구조에 맞춰 다음 역할을 분리한다.

| 구성 요소 | 역할 |
|---|---|
| dataset loader | JSONL 테스트 케이스와 turn 단위 입력을 로드 |
| classification mode strategy | `A_FULL`, `B_SCOPED_GOLD`, `B_SCOPED_RUNTIME`, `C_HYBRID_RUNTIME` 실행 방식 분리 |
| backend experiment client | `/internal/experiments/intent-route` 호출 |
| ontology mapper | node id 유효성, L1/L2/L3 계층 비교, hierarchical partial score 계산 |
| result store | raw/parsed JSONL 결과 저장 |
| evaluator | Exact Match, Micro-F1, Precision, Recall, Primary Acc, Hierarchical Partial 등 계산 |
| report writer | markdown/csv 리포트 생성 |

backend 쪽에서는 운영 API가 아니라 실험 전용 내부 endpoint를 사용한다.

| Endpoint | 목적 |
|---|---|
| `POST /internal/experiments/intent-route/preflight` | provider 등록 여부와 실험 호출 가능 여부 확인 |
| `POST /internal/experiments/intent-route` | 실제 intent classification 경로 호출 |
| `POST /internal/experiments/lawyer-match/corpus` | synthetic 변호사 corpus 적재 |
| `POST /internal/experiments/lawyer-match/preflight` | matching 실험 전 corpus/weight 검증 |
| `POST /internal/experiments/lawyer-match` | synthetic corpus 기반 matching 후보 반환 |

이번 보고서는 이 중 법률 분야 분류 실험의 완료된 3개 모드만 공식 결과로 다룬다. `C_HYBRID_RUNTIME`은 서버 연결 실패로 완주하지 못했기 때문에 결과 해석에서 제외한다.

## 3. 비교한 분류 모드

이번 실험 계획의 핵심은 동일한 테스트 케이스를 여러 분류 전략으로 돌려 비교하는 것이다.

| 모드 | 의미 | 해석상 위치 |
|---|---|---|
| `A_FULL` | 전체 ontology를 모두 prompt에 제공하고 바로 분류 | baseline |
| `B_SCOPED_GOLD` | 정답 L1 범위를 미리 알고 있다고 가정하고 해당 범위만 제공 | oracle upper bound |
| `B_SCOPED_RUNTIME` | 실제 런타임에서 모델/라우터가 L1 범위를 먼저 추정한 뒤 scoped ontology로 분류 | production에 가까운 방식 |
| `C_HYBRID_RUNTIME` | runtime scoped 결과와 full 결과를 조합해 선택 | 이번 실행에서는 미완료 |

`B_SCOPED_GOLD`는 실제 서비스에서 그대로 쓸 수 있는 방식은 아니다. 정답 L1을 미리 알고 있다고 가정하기 때문이다. 하지만 매우 중요하다. 이 모드는 "L1 라우팅이 정확해지면 scoped ontology 구조가 어느 정도까지 좋아질 수 있는가"를 보여주는 상한선 역할을 한다.

`B_SCOPED_RUNTIME`은 실제 서비스 설계에 더 가깝다. 사용자의 상담 내용을 보고 먼저 L1 범위를 추정한 뒤, 그 범위 안에서 세부 node를 고르는 방식이다. 따라서 `B_SCOPED_RUNTIME`이 `A_FULL`보다 좋다면, 현재 파이프라인 방향이 실서비스 관점에서도 의미가 있다는 신호가 된다.

## 4. 테스트 케이스 설계

기존 40개 테스트 케이스만으로는 복합 분야 분류 실험의 안정적인 경향을 보기 어렵다고 판단해, 총 300개 테스트 케이스를 새로 구성했다.

| 그룹 | 케이스 수 | 턴 수 | 정답 label 수 | 목적 |
|---|---:|---:|---:|---|
| `single` | 100 | 각 10턴 | 1개 | 단일 법률 분야 상담 분류 성능 확인 |
| `complex2` | 100 | 각 10턴 | 2개 | 두 개 법률 쟁점이 섞인 상담 분류 성능 확인 |
| `complex3` | 100 | 각 10턴 | 3개 | 세 개 법률 쟁점이 섞인 어려운 상담 분류 성능 확인 |

총 규모는 다음과 같다.

| 항목 | 값 |
|---|---:|
| case-level 테스트 케이스 | 300개 |
| turn-level 평가 row | 3,000개 |
| 케이스당 턴 수 | 10턴 |
| split | `test` |

테스트 케이스는 case-level JSON과 runner용 turn-level JSONL로 나누어 저장했다.

| 파일 | 역할 |
|---|---|
| `src/test/testcases/single/testcases.json` | 단일 분야 100개 원본 케이스 |
| `src/test/testcases/complex2/testcases.json` | 복합 2분야 100개 원본 케이스 |
| `src/test/testcases/complex3/testcases.json` | 복합 3분야 100개 원본 케이스 |
| `src/test/testcases/quality-summary.json` | 전체 품질검사 요약 |
| `eval/complex-law-classification-experiment/input/generated-300-turn10/dataset-case-level.jsonl` | case-level runner input |
| `eval/complex-law-classification-experiment/input/generated-300-turn10/classification-turns.jsonl` | turn-level classification input |
| `eval/complex-law-classification-experiment/input/generated-300-turn10/config-openai-real.json` | 실제 OpenAI 호출용 runner config |

각 케이스는 다음 구조를 유지한다.

| 필드 | 의미 |
|---|---|
| `caseId` 또는 `case_id` | 테스트 케이스 식별자 |
| `group` | `single`, `complex2`, `complex3` |
| `expectedComplex` | 복합 분야 여부 |
| `goldPrimaryNodeId` | 주된 법률 분야 node id |
| `goldLabels` 또는 `gold_node_ids` | 정답 법률 node 목록 |
| `turns` | 10턴 상담 흐름 |
| `turnIndex` | 턴 번호 |
| `userInput` | 해당 턴의 사용자 입력 |
| `observableGoldNodeIds` | 해당 턴까지 드러난 정답 node |
| `difficulty` | `normal` 또는 `complex` |
| `memo` | 케이스 설명 |

복합 케이스는 처음부터 모든 쟁점이 드러나는 방식이 아니라, 상담이 진행되면서 secondary issue가 추가되는 흐름으로 만들었다. 예를 들어 처음에는 보증금 반환 문제로 시작하지만, 이후 집주인 재산에 대한 가압류 문제가 추가되는 식이다. 이 설계는 실제 상담에서 사용자가 처음부터 모든 법률 쟁점을 정확히 말하지 않는 상황을 반영하기 위한 것이다.

## 5. 테스트 케이스 품질검사

테스트 케이스는 실행 전에 품질검사를 통과한 것만 input으로 사용했다.

검사 항목은 다음과 같다.

| 검사 항목 | 목적 |
|---|---|
| JSON parse 가능 여부 | 파일 구조가 깨지지 않았는지 확인 |
| 필수 필드 존재 여부 | `caseId`, `group`, `goldLabels`, `turns` 등 확인 |
| 그룹별 label 수 | `single=1`, `complex2=2`, `complex3=3` 유지 여부 확인 |
| 턴 수 | 모든 케이스가 정확히 10턴인지 확인 |
| 중복 case id | 같은 케이스가 중복 생성되지 않았는지 확인 |
| 중복 user input | 같은 문장이 반복되어 benchmark가 왜곡되지 않는지 확인 |
| invalid node id | ontology에 없는 node가 정답으로 들어가지 않았는지 확인 |
| replacement character | `�` 같은 깨진 문자 포함 여부 확인 |
| 비정상 question mark run | 인코딩 깨짐으로 의심되는 `???` 패턴 확인 |

품질검사 결과는 다음과 같다.

| 항목 | 결과 |
|---|---:|
| 전체 통과 여부 | `true` |
| 전체 케이스 수 | 300 |
| 전체 턴 수 | 3,000 |
| 중복 case id | 0 |
| 중복 user input | 0 |
| invalid error | 0 |
| replacement character | 0 |
| 비정상 question mark run | 0 |

콘솔 출력에서는 Windows PowerShell 인코딩 문제로 한글이 깨져 보이는 경우가 있었지만, UTF-8로 읽은 원본 파일의 첫 글자 코드포인트가 정상 한글인 것을 확인했다. 따라서 테스트 케이스 원본의 한글 자체가 깨진 것은 아니다.

## 6. 실험 진행 방식

실험은 `config-openai-real.json`을 기준으로 real provider 호출 방식으로 진행했다.

주요 환경은 다음과 같다.

| 항목 | 값 |
|---|---|
| provider | `openai` |
| classification model | `gpt-4o-mini` |
| structured output | disabled |
| backend base URL | `http://localhost:8080` |
| 평가 단위 | turn-level 3,000 rows |
| ontology snapshot | `src/main/resources/ontology/legal-ontology-slim.json` |

실행된 흐름은 다음과 같다.

1. 300개 case-level 테스트 케이스를 3,000개 turn-level classification input으로 변환했다.
2. backend의 `/internal/experiments/intent-route` adapter를 통해 실제 분류 경로를 호출했다.
3. 각 모드별 raw 결과와 parsed 결과를 JSONL로 저장했다.
4. parsed 결과의 `pred_node_ids`와 gold node id를 비교해 지표를 계산했다.
5. `A_FULL`, `B_SCOPED_GOLD`, `B_SCOPED_RUNTIME`은 3,000 row 모두 완료했다.
6. `C_HYBRID_RUNTIME`은 중간에 backend server 연결이 끊겨 완주하지 못했다.

완료된 파일은 다음과 같다.

| 모드 | parsed 결과 |
|---|---|
| `A_FULL` | `parsed/openai_A_FULL.parsed.jsonl` |
| `B_SCOPED_GOLD` | `parsed/openai_B_SCOPED_GOLD.parsed.jsonl` |
| `B_SCOPED_RUNTIME` | `parsed/openai_B_SCOPED_RUNTIME.parsed.jsonl` |

`C_HYBRID_RUNTIME`은 `localhost:8080` 연결 실패가 기록된 chunk 파일이 있어, 이번 보고서의 공식 성능 비교에서는 제외했다.

## 7. 주요 결과

### 7.1 전체 지표

| 모드 | Exact Match | Micro-F1 | Precision | Recall | Primary Acc | Hierarchical Partial | Complex Recall | 평균 입력 토큰 | 평균 지연 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `A_FULL` | 6.33% | 37.61% | 29.43% | 52.09% | 6.87% | 76.97% | 36.03% | 4,274 | 4,465ms |
| `B_SCOPED_GOLD` | 10.63% | 47.34% | 38.06% | 62.60% | 13.67% | 84.07% | 50.40% | 1,118 | 4,080ms |
| `B_SCOPED_RUNTIME` | 8.90% | 42.16% | 33.78% | 56.06% | 10.17% | 76.28% | 46.50% | 1,110 | 4,134ms |

파싱과 schema 안정성은 완료된 3개 모드 모두 좋았다.

| 모드 | row 수 | eligible row | parse success | schema success | fallback |
|---|---:|---:|---:|---:|---:|
| `A_FULL` | 3,000 | 3,000 | 100% | 100% | 0% |
| `B_SCOPED_GOLD` | 3,000 | 3,000 | 100% | 100% | 0% |
| `B_SCOPED_RUNTIME` | 3,000 | 3,000 | 100% | 100% | 0% |

### 7.2 그룹별 결과

| 모드 | 그룹 | Exact Match | Micro-F1 | Primary Acc | Hierarchical Partial | Complex Recall | 평균 입력 토큰 |
|---|---|---:|---:|---:|---:|---:|---:|
| `A_FULL` | `single` | 11.80% | 45.02% | 11.80% | 87.15% | - | 4,270 |
| `A_FULL` | `complex2` | 4.00% | 36.76% | 4.70% | 73.44% | 40.58% | 4,273 |
| `A_FULL` | `complex3` | 3.20% | 33.09% | 4.10% | 70.31% | 33.00% | 4,278 |
| `B_SCOPED_GOLD` | `single` | 18.90% | 50.69% | 18.90% | 94.36% | - | 1,105 |
| `B_SCOPED_GOLD` | `complex2` | 6.90% | 46.80% | 10.90% | 81.12% | 54.58% | 1,118 |
| `B_SCOPED_GOLD` | `complex3` | 6.10% | 45.53% | 11.20% | 76.75% | 47.61% | 1,131 |
| `B_SCOPED_RUNTIME` | `single` | 15.80% | 43.42% | 15.80% | 82.93% | - | 1,097 |
| `B_SCOPED_RUNTIME` | `complex2` | 5.70% | 42.50% | 7.70% | 74.98% | 50.42% | 1,110 |
| `B_SCOPED_RUNTIME` | `complex3` | 5.20% | 41.02% | 7.00% | 70.94% | 43.89% | 1,123 |

복합도가 올라갈수록 Exact Match와 Primary Acc는 떨어진다. 이는 자연스러운 결과다. `complex3`는 하나의 턴에서 세 개의 법률 node를 모두 맞혀야 하므로, 하나만 틀려도 Exact Match가 실패한다.

## 8. 지표의 의미

각 지표는 다음 의미를 가진다.

| 지표 | 의미 |
|---|---|
| Exact Match | 예측 node set이 정답 node set과 완전히 같은 비율 |
| Micro Precision | 전체 예측 node 중 정답 node 비율 |
| Micro Recall | 전체 정답 node 중 모델이 찾아낸 node 비율 |
| Micro-F1 | Precision과 Recall의 조화평균 |
| Primary Accuracy | 주된 법률 분야 node를 정확히 맞힌 비율 |
| Hierarchical Partial | 완전 정답이 아니어도 같은 L2/L1이면 부분 점수를 주는 계층 기반 점수 |
| Complex Recall | 복합 케이스의 여러 정답 node 중 모델이 찾아낸 비율 |
| Under-classification | 복합 케이스인데 1개 이하만 예측한 비율 |
| Over-classification | 정답보다 2개 이상 많은 node를 예측한 비율 |
| Valid Node Rate | 예측 node id가 ontology에 실제 존재하는 비율 |
| 평균 입력 토큰 | 한 turn을 분류하는 데 prompt로 들어간 평균 token 수 |
| 평균 지연 | backend 호출부터 응답까지 평균 시간 |

이번 실험에서 가장 엄격한 지표는 Exact Match다. 예를 들어 정답이 `law-004-02-01` 하나인데 모델이 `law-004`, `law-004-02`, `law-004-02-01`을 함께 반환하면, leaf node는 맞혔더라도 extra node가 있으므로 Exact Match는 실패한다. Precision도 extra node 때문에 낮아진다.

반면 Hierarchical Partial은 조금 더 현실적인 보조 지표다. 완전히 같은 L3 node를 맞히면 1.0, 같은 L2 안에 있으면 0.7, 같은 L1 안에 있으면 0.4를 준다. 따라서 Exact Match가 낮아도 Hierarchical Partial이 높다면, 모델이 완전히 틀린 분야를 고르는 것이 아니라 법률적으로 가까운 영역까지는 접근하고 있다는 뜻이다.

## 9. 결과 해석

### 9.1 `B_SCOPED_GOLD`가 가장 좋은 성능을 보였다

완료된 3개 모드 중 가장 좋은 결과는 `B_SCOPED_GOLD`다.

`A_FULL` 대비 개선폭은 다음과 같다.

| 지표 | `A_FULL` | `B_SCOPED_GOLD` | 변화 |
|---|---:|---:|---:|
| Exact Match | 6.33% | 10.63% | +4.30%p |
| Micro-F1 | 37.61% | 47.34% | +9.73%p |
| Precision | 29.43% | 38.06% | +8.63%p |
| Recall | 52.09% | 62.60% | +10.51%p |
| Primary Acc | 6.87% | 13.67% | +6.80%p |
| Hierarchical Partial | 76.97% | 84.07% | +7.10%p |
| Complex Recall | 36.03% | 50.40% | +14.37%p |
| 평균 입력 토큰 | 4,274 | 1,118 | 약 73.8% 감소 |

이 결과는 매우 중요하다. 정답 L1 범위를 알고 있다는 oracle 조건에서는, 전체 ontology를 모두 주는 것보다 scoped ontology가 모든 주요 품질 지표에서 좋아졌다. 즉 "먼저 L1 layer에서 범위를 좁힌 다음 분류한다"는 설계 자체는 실험적으로 의미가 있다.

### 9.2 `B_SCOPED_RUNTIME`은 전체 평균과 복합 케이스에서 개선되었다

실제 서비스에 더 가까운 `B_SCOPED_RUNTIME`도 전체 평균 기준으로는 `A_FULL`보다 여러 지표에서 좋아졌다.

| 지표 | `A_FULL` | `B_SCOPED_RUNTIME` | 변화 |
|---|---:|---:|---:|
| Exact Match | 6.33% | 8.90% | +2.57%p |
| Micro-F1 | 37.61% | 42.16% | +4.55%p |
| Precision | 29.43% | 33.78% | +4.35%p |
| Recall | 52.09% | 56.06% | +3.97%p |
| Primary Acc | 6.87% | 10.17% | +3.30%p |
| Complex Recall | 36.03% | 46.50% | +10.47%p |
| 평균 입력 토큰 | 4,274 | 1,110 | 약 74.0% 감소 |

이 결과는 production 관점에서 의미가 있다. 정답 L1을 모르는 실제 runtime 상황에서도, L1을 먼저 추정하고 범위를 좁히는 방식이 전체 ontology 방식보다 더 나은 Micro-F1과 더 낮은 token cost를 만들었다.

다만 `B_SCOPED_RUNTIME`이 `A_FULL`보다 모든 지표에서 우월한 것은 아니다. 단일 분야와 일부 계층 유사도 지표에서는 `A_FULL`이 더 높게 나왔다.

| 비교 항목 | `A_FULL` | `B_SCOPED_RUNTIME` | 더 높은 모드 |
|---|---:|---:|---|
| 전체 Hierarchical Partial | 76.97% | 76.28% | `A_FULL` |
| 단일 Micro-F1 | 45.02% | 43.42% | `A_FULL` |
| 단일 Hierarchical Partial | 87.15% | 82.93% | `A_FULL` |
| 복합2 Micro-F1 | 36.76% | 42.50% | `B_SCOPED_RUNTIME` |
| 복합3 Micro-F1 | 33.09% | 41.02% | `B_SCOPED_RUNTIME` |

따라서 더 정확한 해석은 "`B_SCOPED_RUNTIME`이 무조건 `A_FULL`보다 좋다"가 아니다. `B_SCOPED_RUNTIME`은 전체 평균, 복합2, 복합3, token 효율에서는 의미 있는 개선을 보였지만, 단일 분야와 계층적 근접도 일부에서는 `A_FULL`의 장점이 남아 있다.

이는 runtime L1 추정이 틀렸을 때 scoped ontology가 오히려 정답과 먼 범위로 분류를 제한할 수 있음을 보여준다. 따라서 현재 병목은 "scoped 분류 방식" 자체보다 "L1 router의 정확도"와 "잘못 좁혔을 때 full ontology로 되돌아가는 fallback 정책"에 있다.

### 9.3 점수는 아직 낮다

가장 좋은 `B_SCOPED_GOLD`도 Exact Match 10.63%, Micro-F1 47.34%, Primary Acc 13.67% 수준이다. 절대값만 보면 아직 낮다. 이 결과를 "현재 AI 분류기가 충분히 정확하다"로 해석하면 안 된다.

점수가 낮은 주요 이유는 다음과 같다.

1. 평가가 leaf node 단위로 매우 엄격하다. L1이나 L2를 맞혀도 최종 L3 leaf를 정확히 맞히지 못하면 Exact Match는 실패한다.
2. 모델이 ancestor node와 leaf node를 함께 반환하는 경향이 있다. 예를 들어 `law-004`, `law-004-02`, `law-004-02-01`을 모두 반환하면 leaf는 맞혔지만 extra false positive 때문에 Precision과 Exact Match가 낮아진다.
3. 복합 케이스는 정답 node가 2개 또는 3개다. 하나만 놓쳐도 Exact Match가 깨지며, secondary issue 누락이 Recall을 낮춘다.
4. `B_SCOPED_RUNTIME`은 L1 router가 먼저 맞아야 한다. 초기 L1이 잘못 잡히면 이후 세부 분류가 아무리 좋아도 정답 후보가 prompt에 없을 수 있다.
5. synthetic test case는 법률 node 조합을 고르게 만들었기 때문에 쉬운 빈출 케이스에만 성능이 높게 나오는 효과가 적다.
6. 출력 설명 텍스트 일부에서 한글 깨짐이 관찰되었다. 평가는 node id 기반이라 이번 지표 계산은 가능하지만, prompt/응답 인코딩 경로는 별도로 점검할 필요가 있다.

따라서 이번 실험의 의미는 높은 최종 정확도 달성이 아니라, 어떤 구조가 개선 가능성이 큰지를 확인했다는 데 있다.

## 10. 우리 AI 파이프라인의 의미 있는 성과

이번 실험에서 확인한 의미 있는 성과는 크게 세 가지다.

### 10.1 L1-first scoped ontology 전략이 검증되었다

가장 중요한 성과는 전체 ontology를 처음부터 모두 넣는 방식보다, L1 범위를 먼저 좁히는 방식이 더 유망하다는 점이다.

`B_SCOPED_GOLD`는 `A_FULL`보다 Micro-F1이 9.73%p 높고, token은 약 73.8% 적게 사용했다. 정확도와 비용이 동시에 좋아졌다.

`B_SCOPED_RUNTIME`도 전체 평균 기준으로는 `A_FULL`보다 Micro-F1이 4.55%p 높고, token은 약 74.0% 적게 사용했다. 즉 oracle이 아닌 실제 runtime 조건에서도 복합 분야와 비용 측면에서는 같은 방향의 개선이 나타났다.

다만 이 결과는 scoped runtime 방식이 모든 상황에서 full ontology 방식보다 우월하다는 뜻은 아니다. 단일 분야와 전체 Hierarchical Partial에서는 `A_FULL`이 더 높았기 때문에, 현재 결론은 "복합 분야 분류와 token 효율 측면에서 L1-first scoped 방식이 유망하다"로 보는 것이 정확하다.

### 10.2 개선해야 할 병목이 구체화되었다

실험 전에는 단순히 "AI 분류 정확도가 낮다"라고만 말할 수 있었다. 하지만 이번 실험 후에는 병목이 더 구체적으로 보인다.

| 병목 | 근거 | 개선 방향 |
|---|---|---|
| L1 router 정확도 | `B_SCOPED_GOLD`가 `B_SCOPED_RUNTIME`보다 높음 | L1 후보 top-k, confidence threshold, fallback 정책 개선 |
| extra ancestor node 반환 | Precision과 Exact Match가 낮고 over-classification이 높음 | leaf-only normalization, ancestor 제거 post-processing |
| secondary issue 누락 | complex recall이 아직 낮음 | 복합 쟁점 탐지 prompt, multi-label 최소 개수 정책 |
| 인코딩 리스크 | raw 설명 텍스트 일부 한글 깨짐 | ontology/prompt/response UTF-8 경로 점검 |

즉 다음 개발 작업의 우선순위가 생겼다.

1. L1 router를 더 정확하게 만든다.
2. 모델이 반환한 node 중 ancestor node를 정리하고 leaf node 중심으로 normalize한다.
3. 복합 케이스에서 secondary issue를 강제로 점검하는 후처리 또는 self-check 단계를 둔다.
4. C hybrid 모드를 안정적인 서버 환경에서 재실행한다.

### 10.3 반복 가능한 실험 기반이 만들어졌다

이번 작업으로 300개 case, 3,000개 turn 단위의 benchmark dataset과 runner input이 만들어졌다. 또한 raw/parsed 결과가 모드별 JSONL로 남아 있어, 다음 실험에서 동일 조건 비교가 가능하다.

이 점도 성과다. 이제는 "느낌상 좋아졌다"가 아니라, 같은 테스트셋에서 지표를 비교할 수 있다.

## 11. 남은 작업과 주의점

### 11.1 `C_HYBRID_RUNTIME`은 아직 결론을 내리면 안 된다

이번 실행에서 `C_HYBRID_RUNTIME`은 서버 연결 실패로 완주하지 못했다. 따라서 현재 산출물 안의 hybrid 관련 summary는 공식 결과로 해석하면 안 된다.

다른 컴퓨터나 안정적인 서버 환경에서 다음 조건으로 재실행해야 한다.

1. backend server를 `localhost:8080`에서 계속 유지한다.
2. `SHIELD_EXPERIMENT_RESUME_DIR`로 이번 run directory를 지정한다.
3. `SHIELD_EXPERIMENT_RESUME_MODES=C_HYBRID_RUNTIME`으로 미완료 모드만 재실행한다.
4. 실행 후 `openai_C_HYBRID_RUNTIME.parsed.jsonl`이 3,000 row를 모두 채웠는지 확인한다.
5. 그 다음 matching 실험까지 다시 생성한다.

### 11.2 matching 결과도 아직 최종 결론이 아니다

이번 run config에는 cosine-only와 hybrid matching 비교도 포함되어 있다. 하지만 selected classification mode가 `C_HYBRID_RUNTIME`이었고, 해당 분류 결과가 완주하지 못했기 때문에 matching 결과도 최종 결론으로 쓰기 어렵다.

분류 `C_HYBRID_RUNTIME`을 먼저 완주한 뒤, 다음 matching mode들을 다시 비교해야 한다.

| matching mode | 의미 |
|---|---|
| `PREDICTED_LABELS_COSINE_ONLY` | 모델 예측 label 기반 cosine-only matching |
| `ORACLE_LABELS_COSINE_ONLY` | 정답 label 기반 cosine-only matching |
| `PREDICTED_LABELS_HYBRID_MATCH` | 모델 예측 label 기반 hybrid matching |
| `ORACLE_LABELS_HYBRID_MATCH` | 정답 label 기반 hybrid matching |
| `NO_LABEL_COSINE_ONLY` | label 없이 본문 유사도만 사용하는 baseline |

## 12. 결론

이번 실험의 결론은 다음과 같다.

1. 현재 AI 분류기의 절대 성능은 아직 낮다. 가장 좋은 완료 모드인 `B_SCOPED_GOLD`도 Exact Match 10.63%, Micro-F1 47.34% 수준이다.
2. 하지만 L1-first scoped ontology 설계는 의미 있는 성과를 보였다.
3. `B_SCOPED_GOLD`는 `A_FULL` 대비 Micro-F1을 9.73%p 올리면서 입력 token을 약 73.8% 줄였다.
4. 실제 runtime에 가까운 `B_SCOPED_RUNTIME`도 전체 평균 Micro-F1을 4.55%p 올리면서 입력 token을 약 74.0% 줄였다.
5. 다만 단일 분야 Micro-F1과 전체 Hierarchical Partial에서는 `A_FULL`이 더 높아, runtime scoped 방식이 무조건 우월하다고 볼 수는 없다.
6. 따라서 이번 실험의 정확한 결론은 "복합 분야 분류와 token 효율에서는 L1으로 먼저 좁히는 방식이 유망하지만, 단일 분야와 계층적 근접도까지 안정적으로 이기려면 L1 router와 fallback 정책 개선이 필요하다"이다.
7. 다음 개선의 핵심은 L1 router 정확도, leaf-only normalization, 복합 쟁점 secondary issue 탐지, C hybrid 재실행이다.

한 문장으로 정리하면 다음과 같다.

> 이번 실험은 현재 분류 정확도가 아직 충분하지 않다는 점을 보여주면서도, 복합 분야 분류와 token 효율 측면에서는 "L1으로 먼저 좁히고 세부 분류하는 구조"가 유망하다는 것을 확인했다. 다만 단일 분야와 계층적 근접도에서는 `A_FULL`의 장점도 남아 있어, L1 router와 fallback 정책 개선이 다음 핵심 과제다.
