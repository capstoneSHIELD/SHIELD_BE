# Wrong-Selected Single-Bait Experiment Summary

## One-Line Takeaway

비록 세부 분류(L1/L2/L3 leaf exact match)까지 완전히 일치한 비율은 `0.4333`으로 높지 않았지만, 계층 근접도를 반영한 `hierarchical_partial_score`는 `0.7800`으로 나타났다. 즉, 모델이 함정 selected label에 완전히 끌려가기보다는 실제 정답 경로 근처까지는 비교적 안정적으로 도달했다는 점을 확인했다.

## Experiment Goal

- 사용자가 잘못 선택한 법률 분야(selected label) 1개가 주어졌을 때도 모델이 대화 내용 기준으로 올바른 법률 분류를 수행하는지 검증한다.
- 완전 정답 비율뿐 아니라, 정답 계층 경로 근처까지 도달하는 근사 정답 성능도 함께 평가한다.

## Test Setup

- benchmark: `wrong-selected`
- testcase variant: `single-bait`
- evaluation scope: `final-turn only`
- sample size: `30`
- provider/mode: `openai / A_FULL`
- runtime: production EC2 temporary instance on port `18080`
- run id: `aws-ec2-18080-single-bait-20260617-223136-7984b588-r1`

## Variable Definitions

### Experiment Variables

| Variable | Meaning |
|---|---|
| `benchmark` | 이번 실험이 속한 전체 평가 시나리오 이름이다. `wrong-selected`는 사용자가 잘못된 법률 분야를 선택한 상황을 가정한다. |
| `testcase variant` | benchmark 안의 세부 난이도 설정이다. `single-bait`는 잘못된 selected label을 1개만 넣는 완화형 함정 케이스다. |
| `evaluation scope` | 어떤 턴을 평가 대상으로 삼는지 뜻한다. `final-turn only`는 각 대화의 마지막 판단 시점만 채점한다. |
| `sample size` | 이번 실행에서 실제로 평가한 케이스 수다. 이번 run은 `30`개를 사용했다. |
| `provider` | 분류 응답을 생성한 LLM 제공자다. 이번 실험은 `openai`를 사용했다. |
| `mode` | 러너가 어떤 분류 전략으로 모델을 호출했는지 뜻한다. `A_FULL`은 전체 ontology 후보를 열어둔 기본 분류 모드다. |
| `runtime` | 실험이 실행된 환경이다. 이번에는 운영 EC2에 임시 인스턴스를 `18080` 포트로 띄워 실행했다. |
| `run_id` | 해당 실험 실행을 구분하는 고유 식별자다. 결과 bundle, 로그, 리포트가 모두 이 값 기준으로 저장된다. |
| `selected label` | 사용자가 UI에서 미리 선택했다고 가정한 법률 분야다. 이번 benchmark에서는 일부러 정답이 아닌 함정 label로 들어간다. |
| `bait label` | 모델을 잘못된 분류로 유도하기 위해 넣은 함정 selected label이다. `single-bait`에서는 1개만 사용한다. |
| `gold label` | 채점 기준이 되는 정답 법률 분류다. |
| `gold leaf` | 정답 경로에서 가장 구체적인 최종 node다. 보통 L3 세부 분류를 의미한다. |
| `L1 / L2 / L3` | ontology 분류 계층이다. L1은 대분류, L2는 중분류, L3는 세부 분류다. |
| `ancestor` | gold leaf의 상위 경로 node다. 예를 들어 gold leaf가 L3면 같은 경로의 L2, L1이 ancestor다. |

### Metric Variables

| Variable | Meaning |
|---|---|
| `exact_set_match` | 예측한 node 집합이 정답 집합과 완전히 동일한 비율이다. 발표에서는 “L1/L2/L3 세부 분류까지 정확히 맞춘 비율”로 설명할 수 있다. |
| `primary_accuracy` | 첫 번째 예측 또는 주 예측이 gold primary node와 일치한 비율이다. 단일 정답 문제에서는 strict 정확도와 비슷하게 해석된다. |
| `micro_precision` | 모델이 예측한 전체 label 중 실제 정답인 label의 비율이다. 불필요한 label을 많이 내면 낮아진다. |
| `micro_recall` | 전체 정답 label 중 모델이 맞춘 label의 비율이다. 맞춰야 할 label을 놓치면 낮아진다. |
| `micro_f1` | `micro_precision`과 `micro_recall`의 균형 점수다. 전체 분류 성능을 한 숫자로 보기 좋다. |
| `path_aware_accuracy` | 첫 예측이 gold leaf 자체이거나 같은 경로의 ancestor(L2/L1)인 비율이다. “정답 경로 안으로는 들어왔는가”를 보는 완화 지표다. |
| `hierarchical_partial_score` | 정답과 예측이 ontology 계층상 얼마나 가까운지를 반영한 평균 점수다. exact match가 아니어도 같은 경로의 상위 분류까지 맞추면 부분 점수를 받는다. 이 값은 “L2 정확도 비율” 그 자체는 아니고, 계층 기반 근사 정답 성능 점수로 해석하는 것이 맞다. |
| `valid_node_rate` | 모델이 반환한 node id가 ontology에 실제로 존재하는 유효 node인 비율이다. |
| `parse_success_rate` | 모델 원문 응답을 러너가 정상적으로 파싱한 비율이다. |
| `schema_success_rate` | 파싱된 응답이 실험용 JSON schema를 만족한 비율이다. |
| `fallback_rate` | 실험 중 대체 provider나 fallback 경로를 사용한 비율이다. `0.0`이면 원래 의도한 경로로만 실행된 것이다. |
| `over_classification_rate` | 정답보다 과도하게 많은 label을 예측한 비율이다. |
| `under_classification_rate` | 복합 정답 케이스에서 필요한 label보다 적게 예측한 비율이다. 이번 single-bait final-turn 실험에서는 해석 비중이 낮다. |
| `complex_recall` | 복합 정답 케이스에서 여러 정답 label을 얼마나 회수했는지 보는 재현율이다. 이번 실험은 단일 정답 중심이라 큰 의미는 없다. |
| `latency_avg_ms` | 한 건당 평균 응답 시간(ms)이다. |
| `tokens_input_avg` | 모델 호출 시 평균 입력 토큰 수다. |
| `tokens_output_avg` | 모델 호출 시 평균 출력 토큰 수다. |

## Key Metrics

| Metric | Value | Meaning |
|---|---:|---|
| `exact_set_match` | `0.4333` | L1/L2/L3 세부 정답까지 완전히 일치한 비율 |
| `primary_accuracy` | `0.4333` | 첫 예측 기준 주 정답 일치 비율 |
| `micro_f1` | `0.4839` | 전체 예측 집합 기준 정밀도/재현율 균형 |
| `path_aware_accuracy` | `0.5667` | 첫 예측이 gold leaf 또는 같은 경로의 상위 분류(L2/L1)인 비율 |
| `hierarchical_partial_score` | `0.7800` | 정답과의 계층적 근접도를 반영한 평균 점수 |
| `valid_node_rate` | `1.0000` | 잘못된 ontology node 없이 응답한 비율 |
| `parse_success_rate` | `1.0000` | 파싱 성공 비율 |
| `schema_success_rate` | `1.0000` | 응답 스키마 준수 비율 |

## Performance-Oriented Interpretation

### 1. Strict exact accuracy is limited, but not the whole story

- 완전 정답 비율은 `43.33%`로, 세부 분류 leaf까지 정확히 맞추는 성능은 아직 보완이 필요하다.
- 다만 이 수치만으로 모델이 전반적으로 실패했다고 보기는 어렵다.

### 2. Near-correct performance is meaningfully stronger

- `hierarchical_partial_score = 0.7800`은 모델이 정답과 완전히 다른 축으로 이탈하기보다, 같은 계층 경로 안에서 근접하게 맞춘 경우가 적지 않음을 보여준다.
- 발표용으로는 "세부 leaf exact match는 0.4333이지만, 계층 기반 근사 정답 성능은 0.7800 수준으로 확인되었다"라고 요약할 수 있다.

### 3. The model is not simply collapsing to the bait label

- `path_aware_accuracy = 0.5667`은 첫 예측 기준으로도 절반 이상에서 정답 leaf 또는 같은 경로의 상위 분류까지는 도달했음을 의미한다.
- 이는 wrong-selected single-bait 환경에서도 모델이 함정 label을 기계적으로 따라가지 않고, 실제 대화 문맥을 일부 반영하고 있음을 시사한다.

### 4. Output stability was strong

- `valid_node_rate`, `parse_success_rate`, `schema_success_rate`가 모두 `1.0`이었다.
- 즉, 이번 실험은 출력 포맷 불안정성 문제가 아니라 분류 정확도 자체를 해석할 수 있는 유효한 결과다.

## PPT Copy Draft

### Short Version

> Wrong-selected single-bait 실험에서 세부 분류까지 완전히 일치한 비율은 43.33%였지만, 계층 기반 근사 정답 성능은 0.7800으로 확인되었다. 이는 모델이 함정 selected label에 단순히 끌려가기보다는 정답 경로 근처까지는 비교적 안정적으로 도달하고 있음을 보여준다.

### Executive Version

> 본 실험은 사용자가 잘못 선택한 법률 분야 1개가 주어진 상황에서도 모델이 실제 대화 맥락을 기반으로 올바른 분류를 유지하는지 평가했다. strict exact match는 0.4333으로 제한적이었으나, hierarchical partial score는 0.7800으로 나타나 근사 정답 수준의 성능은 의미 있게 확보되었음을 확인했다.

## Suggested Messaging

- "완전 정답률은 아직 개선 여지가 있지만, 계층 기반 근사 정답 성능은 충분히 경쟁력 있는 수준이다."
- "모델이 함정 selected label에 완전히 끌려가지 않고, 실제 정답 경로 근처까지는 상당수 도달했다."
- "향후 개선 포인트는 근사 정답을 leaf exact match로 끌어올리는 후처리 및 prompt refinement다."

## Source Files

- metrics: `eval/complex-law-classification-experiment/output/aws-ec2-18080-single-bait-20260617-223136-7984b588-r1-bundle/bundle/output/reports/metrics-summary.md`
- turn progress: `eval/complex-law-classification-experiment/output/aws-ec2-18080-single-bait-20260617-223136-7984b588-r1-bundle/bundle/output/reports/classification-turn-progress.md`
- run meta: `eval/complex-law-classification-experiment/output/aws-ec2-18080-single-bait-20260617-223136-7984b588-r1-bundle/bundle/output/run-meta.json`
