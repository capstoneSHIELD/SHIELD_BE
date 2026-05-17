# AI/RAG Phase P2 Implementation: Intent Router and Conditional Cohere Skip

상위 문서: `docs/ai-rag-upgrade-plan-v2.2.md`  
Phase: P2  
목표 기간: 3~4주, shadow mode 2주 포함  
코드 변경 범위: OpenAI intent router 응답 확장, RAG 반환 계약 확장, `MessageService` 앞단 routing

---

## 1. 목표와 비목표

### 목표

- 기존 retrieval query 생성 호출을 확장해 `dialogueIntent`, `extractedSlots`, `caseType`, `retrievalQueries`를 함께 반환한다.
- `RagPipelineService` 반환값을 문자열에서 `RagPipelineResult` 객체로 확장한다.
- `MessageService`의 RAG/Cohere 호출 전 단계에 backend intent router를 추가한다.
- shadow mode로 intent 결과를 로깅한 뒤 검증된 intent부터 Cohere skip을 켠다.
- P1.5의 `slot_state` ledger에 high-confidence slot을 반영한다.
- confidence threshold를 도메인별로 튜닝할 수 있도록 shadow eval 분포를 수집한다.
- 혼합 발화, CONFIRM, fixed response template 정책을 명시한다.

### 비목표

- DynamicPlanProposer와 정규화 plan table은 구현하지 않는다.
- RRF retrieval, score gate, output LLM judge는 구현하지 않는다.
- 모든 intent를 한 번에 운영 활성화하지 않는다.

---

## 2. 현재 코드 기준 진입점

- `OpenAiClassifyClient`: OpenAI classifier API 호출을 담당한다.
- `IntentClassificationService`: classifier 결과를 domain/retrieval query로 정리한다.
- `IntentClassificationResult`: 현재 classifier 결과 DTO다.
- `RagPipelineService`: 현재 RAG context 문자열을 반환한다.
- `MessageService`: 실제 RAG와 Cohere 호출의 분기 지점이다.
- P1.5에서 추가된 `SlotLedger` 계열 클래스: slot update 대상이다.

먼저 읽을 테스트:

- `IntentClassificationServiceTest`
- `MessageServiceTest`
- `ClassificationResolverTest`
- `RagContextBuilderTest`
- `CohereServiceHistoryAppendTest`

---

## 3. 구현 순서

### Commit 1. IntentRouterResponse DTO 추가

1. 기존 `IntentClassificationResult`를 직접 깨지 말고 `IntentRouterResponse`를 새로 추가한다.
2. `IntentClassificationResult`는 backward compatibility용으로 유지한다.
3. `IntentRouterResponse`는 P2 schema 전체를 표현한다.

DTO shape:

```json
{
  "schema_version": "2.0",
  "dialogueIntent": "PROVIDE_INFO",
  "intentConfidence": 0.91,
  "extractedSlots": [],
  "caseType": {
    "l1": "부동산 거래",
    "l2": "부동산 임대차",
    "l3": "보증금 및 차임",
    "confidence": 0.87
  },
  "retrievalQueries": ["전세 보증금 반환 거절 임대차"],
  "correctedSlotIds": [],
  "topicChanged": false
}
```

### Commit 2. OpenAI structured schema 확장

1. `OpenAiClassifyClient`의 strict JSON Schema를 P2 DTO에 맞춘다.
2. `dialogueIntent` enum은 다음 8개로 제한한다.
   - `PROVIDE_INFO`
   - `CORRECT_INFO`
   - `CONFIRM`
   - `CHANGE_TOPIC`
   - `ASK_LEGAL_ADVICE`
   - `IRRELEVANT`
   - `GREETING`
   - `END_CONSULTATION`
3. slot confidence가 0.65 미만이면 `extractedSlots`에 포함하지 않도록 prompt에 명시한다.
4. 법적 판단 문장 생성 금지를 classifier prompt에 유지한다.
5. `schema_version`은 `2.0`으로 고정하고, P1 parser와 다른 parser path를 사용한다.

### Commit 3. RagPipelineResult 반환 계약 추가

1. `RagPipelineResult` record를 추가한다.
2. `RagPipelineService.execute()`는 `RagPipelineResult`를 반환하게 한다.
3. 기존 호출부가 많으면 `executeContextOnly()` compatibility method를 임시 제공한다.

Record:

```java
public record RagPipelineResult(
    IntentRouterResponse intent,
    String ragContext,
    List<RetrievalResult> retrievalResults
) {}
```

### Commit 4. BackendIntentRouter 추가

1. `BackendIntentRouter`를 추가하고 `MessageService`에서 Cohere 호출 전에 사용한다.
2. `shadowMode=true`에서는 route 결과를 로그만 남기고 기존 경로를 그대로 탄다.
3. `ASK_LEGAL_ADVICE`, `GREETING`, `IRRELEVANT`, `CONFIRM` 순서로 feature flag를 분리한다.
4. fixed response template은 `MessageService` 안에 두지 말고 별도 template/helper로 분리한다.
5. fixed response template은 `src/main/resources/ai/templates/intent-router-fixed-responses.yaml`에서 관리한다.
6. P2에서는 template 변경에 코드 배포가 필요하다. 법무 검토가 필요한 문구는 PR review checklist에 "legal-approved" 항목을 추가한다.

Fixed template keys:

```yaml
greeting:
  ko: "안녕하세요. 상담에 필요한 사실관계를 차근차근 정리해보겠습니다."
irrelevant:
  ko: "법률 상담과 관련된 사실관계를 알려주시면 필요한 정보를 정리해드리겠습니다."
ask_legal_advice:
  ko: "승소 가능성이나 법적 결론은 단정할 수 없습니다. 대신 판단에 필요한 사실관계를 정리해드리겠습니다."
confirm_affirmative:
  ko: "확인했습니다. 다음으로 필요한 사실관계를 이어서 확인하겠습니다."
confirm_negative:
  ko: "알겠습니다. 올바른 내용을 다시 알려주세요."
```

### Commit 5. Slot update 적용

1. `PROVIDE_INFO`와 `CORRECT_INFO`에서 `extractedSlots`를 P1.5 `slot_state`에 반영한다.
2. confidence >= 0.85 and `needsConfirmation=false`이면 collected로 저장한다.
3. 0.65 <= confidence < 0.85 또는 `needsConfirmation=true`이면 pending confirmation으로 저장한다.
4. `CORRECT_INFO`는 이전 값을 audit log로 남기고 새 값을 pending 또는 collected로 반영한다.
5. `ASK_LEGAL_ADVICE`는 응답 우선순위가 가장 높지만, 같은 발화에 high-confidence slot이 있으면 slot은 confidence gate에 따라 수집한다.

혼합 발화 정책:

| 발화 유형 | 예시 | 처리 |
|---|---|---|
| 순수 법적 판단 요청 | `제가 이길 수 있나요?` | slot update 없음, legal advice fixed response |
| 정보 제공 + 법적 판단 요청 | `보증금은 3천만 원이고요, 이 경우 제가 이길 수 있나요?` | `deposit_amount`는 수집 또는 pending, 응답은 legal advice fixed response |
| 정보 제공 + 절차 질문 | `보증금은 3천만 원인데 다음에 뭘 준비해야 하나요?` | `PROVIDE_INFO`로 처리하고 Cohere 호출 가능 |

CONFIRM skip 정책:

| 조건 | 처리 |
|---|---|
| pending slot 1개 이상, `dialogueIntent=CONFIRM`, `intentConfidence >= 0.85`, corrected/extracted slot 없음, 명확한 긍정 | pending을 collected로 확정하고 Cohere skip |
| pending slot 1개 이상, 명확한 부정, 새 값 없음 | pending을 missing으로 되돌리고 정정 요청 template 반환 |
| `아니요, 5000만원이에요`처럼 새 값 포함 | `CORRECT_INFO` 가능성이 있으므로 Cohere 또는 correction path 호출 |
| `맞는 것 같긴 한데 확실하지 않아요`처럼 모호함 | Cohere 호출 |

Confidence threshold policy:

| Threshold | 초기값 | 의미 | 조정 방식 |
|---|---:|---|---|
| auto collect | 0.85 | 바로 collected 반영 | shadow eval 후 도메인별 precision-recall curve로 조정 |
| pending lower bound | 0.65 | confirmation 필요 | shadow eval 후 도메인별 recall/오염률로 조정 |

초기값은 운영 기본값이 아니라 시작 기본값이다. shadow 2주 종료 후 intent별·도메인별 confidence 분포를 리뷰하고, `app.ai.intent-router.thresholds.<domain>` config로 조정한다.

### Commit 6. 단계 활성화

활성화 순서:

1. shadow logging only
2. `ASK_LEGAL_ADVICE` skip
3. `GREETING` skip
4. `IRRELEVANT` skip
5. `CONFIRM` pending 처리
6. `PROVIDE_INFO` slot auto-update

---

## 4. 인터페이스/API 변경

- DTO:
  - `IntentRouterResponse`
  - `ExtractedSlot`
  - `CaseTypeResult`
  - `RagPipelineResult`
- Config:
  - `app.ai.intent-router.shadow-mode`
  - `app.ai.intent-router.enable-ask-legal-advice-skip`
  - `app.ai.intent-router.enable-greeting-skip`
  - `app.ai.intent-router.enable-irrelevant-skip`
  - `app.ai.intent-router.enable-confirm`
  - `app.ai.intent-router.enable-slot-auto-update`
  - `app.ai.intent-router.thresholds.default.auto-collect=0.85`
  - `app.ai.intent-router.thresholds.default.pending-lower-bound=0.65`
  - `app.ai.intent-router.thresholds.<domain>.auto-collect`
  - `app.ai.intent-router.thresholds.<domain>.pending-lower-bound`
- Resources:
  - `src/main/resources/ai/templates/intent-router-fixed-responses.yaml`
- DB:
  - 신규 migration 없음
  - P1.5의 `slot_state` JSONB를 사용한다.
- External API:
  - 상담 API 응답 shape는 변경하지 않는다.

---

## 5. 테스트 계획

### Unit tests

- `IntentClassificationServiceTest`
  - 8-class intent schema를 파싱한다.
  - 기존 retrieval query fallback이 유지된다.
- 신규 `BackendIntentRouterTest`
  - feature flag별 skip 여부를 확인한다.
  - shadow mode에서는 항상 기존 경로를 유지한다.
  - 혼합 발화에서 slot은 저장하고 응답은 legal advice template을 반환하는지 확인한다.
  - CONFIRM 명확/부정/모호 케이스의 skip 여부를 확인한다.
- 신규 `SlotUpdateFromIntentTest`
  - confidence gate별 collected/pending/ignored 상태를 검증한다.
  - 순수 `ASK_LEGAL_ADVICE`는 slot update를 하지 않고, 혼합 발화는 high-confidence slot만 저장하는지 확인한다.

### Integration tests

- `MessageServiceTest`
  - shadow mode에서 Cohere 호출이 기존과 동일하게 발생한다.
  - `ASK_LEGAL_ADVICE` flag 활성화 시 Cohere 호출 없이 fixed response가 저장된다.
  - `GREETING` flag 활성화 시 fixed response와 다음 질문 연결이 동작한다.

### Shadow evaluation

- 300~500개 실제 발화를 샘플링한다.
- intent, slot, caseType, legal advice risk를 수동 라벨링한다.
- 2주간 운영 경로는 유지하고 intent 결과만 저장한다.
- intent accuracy, slot precision/recall, Cohere skip false positive rate를 측정한다.
- intent별·도메인별 confidence 분포와 precision-recall curve를 산출한다.
- 2주 종료 후 threshold review를 진행하고, 변경값은 config PR로 남긴다.

---

## 6. 완료 기준

- [ ] `IntentRouterResponse`가 strict schema로 파싱된다.
- [ ] `RagPipelineService`가 `RagPipelineResult`를 반환한다.
- [ ] intent routing 분기는 `MessageService`의 RAG/Cohere 호출 전 단계에 있다.
- [ ] shadow mode가 기본값이다.
- [ ] feature flag 없이 운영 Cohere skip은 발생하지 않는다.
- [ ] `ASK_LEGAL_ADVICE` skip부터 단계적으로 활성화할 수 있다.
- [ ] 혼합 발화는 high-confidence slot을 보존하되 응답은 legal advice template으로 차단한다.
- [ ] CONFIRM skip은 명확한 긍정/부정 조건에서만 수행한다.
- [ ] fixed response template은 YAML resource로 관리되고 법무 검토 checklist를 가진다.
- [ ] confidence threshold는 shadow eval 종료 후 도메인별 precision-recall curve로 리뷰된다.
- [ ] slot auto-update는 confidence gate를 통과한 값만 반영한다.

---

## 7. Rollback / Feature Flag

- 기본 rollback은 `app.ai.intent-router.shadow-mode=true`로 전환하는 것이다.
- 각 skip intent는 개별 flag로 비활성화할 수 있어야 한다.
- `RagPipelineResult` 도입으로 문제가 생기면 compatibility method를 통해 기존 string-only 흐름으로 되돌린다.
- slot auto-update는 마지막에 켜며, 문제가 있으면 `enable-slot-auto-update=false`로 끈다.
- 즉시 rollback 기준은 Cohere skip false positive rate > 0.5%, slot auto-update precision < 95%, 또는 intent router API 오류율 > 5%가 10분 지속되는 경우다.
