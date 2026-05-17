# AI/RAG Phase P1 Implementation: Structured Output and Prompt Stabilization

상위 문서: `docs/ai-rag-upgrade-plan-v2.2.md`  
Phase: P1  
목표 기간: 1~2주  
코드 변경 범위: DB 변경 없음, 응답 schema/prompt/guardrail 안정화

---

## 1. 목표와 비목표

### 목표

- Cohere chat/brief 응답에 JSON Schema 기반 `response_format`을 적용한다.
- OpenAI intent classifier에 strict JSON Schema 응답 형식을 적용한다.
- 모든 LLM 응답 DTO와 JSON Schema에 `schema_version`을 추가한다.
- 현재 checklist coverage 요약을 Cohere system prompt 최상단으로 이동한다.
- 최근 AI 질문 3~5개를 repeat blacklist로 prompt에 주입한다.
- prompt block별 토큰 예산과 축소 정책을 정의한다.
- 기존 `GuardrailFilter`를 확장해 법적 판단 표현 차단 테스트를 강화한다.

### 비목표

- `slot_state` JSONB나 DB migration을 추가하지 않는다.
- 값 포함 Slot Status Block은 만들지 않는다. 해당 작업은 P1.5에서 수행한다.
- Cohere 조건부 skip, intent router 확장, dynamic plan table은 구현하지 않는다.
- YAML checklist schema는 변경하지 않는다.

---

## 2. 현재 코드 기준 진입점

- `CohereChatRequest`: Cohere API 요청 DTO이며 현재 `json_object` 응답 형식만 사용한다.
- `CohereService`: system prompt와 대화 이력, RAG context를 조합해 Cohere를 호출한다.
- `ChecklistCoverageService`: 대화 이력 기반 `[x]/[ ]` checklist coverage summary를 만든다.
- `OpenAiClassifyClient`: OpenAI classifier 요청을 만들며 현재 JSON mode만 사용한다.
- `GuardrailFilter`: AI 응답 내 법률 판단 위험 표현을 필터링한다.

먼저 읽을 테스트:

- `CohereChatRequestTest`
- `CohereServiceHistoryAppendTest`
- `CohereServiceTruncationTest`
- `ChecklistCoverageServiceTest`
- `IntentClassificationServiceTest`
- `GuardrailFilterTest`

---

## 3. 구현 순서

### Commit 1. Cohere response schema 추가

1. `CohereChatRequest`에 chat 응답용 JSON Schema builder를 추가한다.
2. `forChat()`은 `schema_version`, `nextQuestion`, `aiDomains`, `aiSubDomains`, `aiTags`, `allCompleted`를 포함하는 schema를 사용한다.
3. `forBrief()`은 `schema_version`과 현재 brief DTO가 기대하는 필드에 맞춘 schema를 사용한다.
4. 기존 `json_object` fallback 경로는 유지한다.
5. API 오류, schema 미지원 모델, 응답 파싱 실패는 기존 fallback parser와 로깅으로 처리한다.
6. parser는 알 수 없는 상위 필드를 무시하고, 현재 버전에서 optional로 정의한 필드 누락은 fallback parser로 보정한다.

Schema version policy:

| Version | 적용 범위 | 호환 정책 |
|---|---|---|
| `1.0` | P1 chat/brief/classifier 기본 응답 | required field 누락 시 fallback, unknown field 무시 |
| `2.0` | P2 intent/slot 확장 | `schema_version`으로 분기, P1 parser는 retrieval query만 복구 |
| `3.0` | P3 dynamic plan proposal | 별도 proposal parser에서만 처리 |

### Commit 2. OpenAI classifier strict schema 적용

1. `OpenAiClassifyClient`의 `response_format`을 `json_schema` + `strict: true` 형태로 바꾼다.
2. P1 schema는 기존 classifier DTO 범위만 포함한다.
3. 환경변수 기반 모델 설정은 유지한다.
4. 기본 모델명과 reasoning effort 값은 운영 계정에서 유효한 값인지 P1 배포 전 체크리스트에서 확인한다.
5. `.env`와 `application.yml`의 기본값이 공식 API Docs와 운영 계정에서 모두 유효해야 배포한다.

P1 classifier schema:

```json
{
  "schema_version": "1.0",
  "intent_summary": "string",
  "matched_node_ids": ["string"],
  "core_keywords": ["string"],
  "retrieval_query": "string"
}
```

### Commit 3. Checklist summary 최상단 주입

1. `ChecklistCoverageService.buildCollectedSummary()`는 기존 동작을 유지한다.
2. `CohereService`의 system prompt 조립 순서를 변경해 checklist coverage summary를 가장 앞에 둔다.
3. 아직 구조화 슬롯 값이 없으므로 `[x]/[ ]` summary만 사용한다.
4. 최근 AI 메시지에서 `nextQuestion`에 해당하는 문장 3~5개를 추출해 `DO NOT REPEAT EXACT QUESTIONS` 블록으로 넣는다.
5. prompt block은 아래 토큰 예산을 초과하면 우선순위 낮은 항목부터 줄인다.

Prompt block 형식:

```text
=== CURRENT CHECKLIST COVERAGE ===
[x] ...
[ ] ...

=== DO NOT REPEAT EXACT QUESTIONS ===
- ...

RULE: Do not ask an identical question again. Prefer the highest-priority unchecked item.
```

Prompt budget:

| Block | P1 예산 | 초과 시 축소 정책 |
|---|---:|---|
| CURRENT CHECKLIST COVERAGE | 160 tokens | unchecked required 우선, checked 항목은 최대 5개 |
| DO NOT REPEAT EXACT QUESTIONS | 80 tokens | 최근 3개 우선, 최대 5개 |
| Base router instructions | 500 tokens | 금지/출력 형식 규칙은 보존 |
| RAG context | 800 tokens | retrieval score 순으로 truncate |

P1 A/B check:

- A안: CURRENT CHECKLIST COVERAGE만 최상단 주입
- B안: CURRENT CHECKLIST COVERAGE + DO NOT REPEAT EXACT QUESTIONS 동시 주입
- 50~100개 회귀 대화에서 반복 질문률, JSON parse failure, 평균 응답 길이를 비교하고 B안이 반복 질문률을 낮추면서 parse failure를 증가시키지 않을 때 기본값으로 둔다.

### Commit 4. Guardrail 확장

1. `GuardrailFilter`의 법적 판단 패턴을 더 좁고 구체적으로 정리한다.
2. `가능합니다`, `할 수 있습니다` 같은 일반 절차 안내 표현만으로는 차단하지 않는다.
3. 승소/패소, 위법/적법, 배상 가능성, 고소 가능성, 결과 예측 표현은 차단한다.
4. `nextQuestion`과 brief 응답 모두 같은 guardrail 정책을 통과하게 한다.

---

## 4. 인터페이스/API 변경

- DB migration: 없음
- 외부 API endpoint: 변경 없음
- 내부 DTO:
  - `CohereChatRequest.responseFormat` 구조가 `json_object` 단독에서 schema 포함 map으로 확장된다.
  - `OpenAiClassifyClient` 요청 body의 `response_format`이 `json_schema` strict 형태로 변경된다.
- Config:
  - 모델명 기본값은 즉시 변경하지 않는다.
  - `gpt-5-nano`, `minimal` 값은 운영 alias 여부를 확인해야 한다는 comment를 남긴다.

---

## 5. 테스트 계획

### Unit tests

- `CohereChatRequestTest`
  - `forChat()` response_format에 schema가 포함되는지 확인한다.
  - required field 목록이 `ChatParsedResponse`와 일치하는지 확인한다.
- `IntentClassificationServiceTest` 또는 신규 `OpenAiClassifyClientTest`
  - OpenAI request body가 `json_schema`와 `strict: true`를 포함하는지 확인한다.
- `CohereServiceHistoryAppendTest`
  - checklist coverage block이 system prompt 첫 부분에 들어가는지 확인한다.
  - 최근 질문 blacklist가 포함되는지 확인한다.
- `GuardrailFilterTest`
  - 법적 판단 표현은 차단한다.
  - 일반 절차 안내 표현은 차단하지 않는다.

### Regression tests

- 기존 chat/brief JSON parse 테스트가 통과해야 한다.
- checklist coverage 계산 결과 자체는 P1 전후로 동일해야 한다.
- Cohere schema 미지원 또는 parse 실패 시 기존 fallback 동작이 유지되어야 한다.

### Manual evaluation

- guardrail 평가는 최근 또는 합성 응답 200건을 수동 라벨링해 측정한다.
- 정상 절차 안내 문장에 대한 false positive rate는 2% 미만이어야 한다.
- 승소/패소, 위법/적법, 손해배상 가능성 등 high-risk 표현 50건은 모두 차단되어야 한다.

---

## 6. 완료 기준

- [ ] P1에서 DB migration 파일이 추가되지 않는다.
- [ ] Cohere chat/brief request에 JSON Schema response format이 포함된다.
- [ ] OpenAI classifier request가 strict JSON Schema를 사용한다.
- [ ] 모든 LLM 응답 DTO에 `schema_version`이 포함된다.
- [ ] 운영 OpenAI 계정에서 모델명과 `reasoning_effort` 파라미터 유효성을 확인하고 `.env` 또는 config에 반영한다.
- [ ] checklist coverage summary가 Cohere system prompt 최상단에 위치한다.
- [ ] 최근 AI 질문 3~5개가 repeat blacklist로 주입된다.
- [ ] prompt block별 토큰 예산과 truncate 정책이 구현된다.
- [ ] guardrail false positive 테스트와 legal judgment 차단 테스트가 추가된다.
- [ ] guardrail false positive rate가 수동 라벨링 200건 기준 2% 미만이다.
- [ ] 기존 상담 API 응답 형식은 바뀌지 않는다.

---

## 7. Rollback / Feature Flag

- Cohere schema 적용은 `app.ai.cohere.structured-output-enabled` config로 끌 수 있게 한다.
- OpenAI strict schema 적용은 `app.ai.openai.structured-output-enabled` config로 끌 수 있게 한다.
- prompt 최상단 checklist block은 config 없이 유지한다. 문제가 생기면 해당 commit만 revert한다.
- guardrail 확장은 false positive가 커질 경우 패턴 변경 commit만 revert한다.
- 즉시 rollback 기준은 JSON parse failure > 1%, Cohere/OpenAI 4xx/5xx > 5%가 10분 지속, 또는 p95 latency가 P1 이전 대비 300ms 이상 증가하는 경우다.
