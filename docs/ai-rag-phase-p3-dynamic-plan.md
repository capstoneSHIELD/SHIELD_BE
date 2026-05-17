# AI/RAG Phase P3 Implementation: Dynamic Plan Proposer

상위 문서: `docs/ai-rag-upgrade-plan-v2.2.md`  
Phase: P3  
목표 기간: 4~6주  
코드 변경 범위: UUID 기반 dynamic plan schema, DynamicPlanProposer, BackendValidator, alias mapping loader

---

## 1. 목표와 비목표

### 목표

- 상담별 동적 슬롯 계획을 저장하는 정규화 테이블을 추가한다.
- LLM 제안 컴포넌트는 `DynamicPlanProposer`로 명명한다.
- LLM이 제안한 slot은 `BackendValidator`가 승인한 뒤에만 운영 질문 후보가 된다.
- 기존 checklist YAML은 유지하고 별도 alias mapping loader를 추가한다.
- P1.5 `slot_state`에서 P3 정규화 plan table로 이전 가능한 동기화 경로를 만든다.
- P3 활성화 후 `dynamic_plan_slot`을 source of truth로 승격하고 `slot_state`는 summary cache로 사용한다.
- plan 재생성 조건의 초기 정량 기준을 명시한다.

### 비목표

- 런타임 orchestrator agent를 만들지 않는다.
- LLM이 직접 상태 변경이나 질문 선택을 수행하지 않는다.
- RRF retrieval과 output LLM judge는 구현하지 않는다.
- 기존 checklist YAML을 object schema로 대규모 마이그레이션하지 않는다.

---

## 2. 현재 코드 기준 진입점

- `Consultation`: UUID 기반 상담 aggregate다.
- P1.5 `SlotLedger`: dynamic plan 전 단계의 임시 상태 저장 구조다.
- `ChecklistCoverageService`: static checklist coverage 안전망이다.
- checklist YAML 파일들: 현재 배열형 schema를 유지한다.
- `GuardrailFilter`: dynamic question 문장 검증에 재사용한다.
- `src/main/resources/db/migration`: Flyway migration 위치다.

먼저 읽을 테스트:

- `ChecklistYamlSchemaTest`
- `ChecklistCoverageServiceTest`
- `ConsultationTest`
- `GuardrailFilterTest`
- P1.5에서 추가된 `SlotLedgerTest`, `StaticQuestionSelectorTest`

---

## 3. 구현 순서

### Commit 1. UUID 기반 dynamic plan migration 추가

1. 다음 Flyway 번호를 사용해 migration을 추가한다.
2. `consultation_dynamic_plan`과 `dynamic_plan_slot` 모두 UUID PK를 사용한다.
3. FK `consultation_id`와 `plan_id`도 UUID다.
4. DB에서 UUID 자동 생성을 사용할지 애플리케이션에서 생성할지는 repo의 기존 UUID 생성 방식과 맞춘다.

Core schema:

```sql
CREATE TABLE consultation_dynamic_plan (
    id              UUID PRIMARY KEY,
    consultation_id UUID NOT NULL REFERENCES consultations(id),
    version         INT NOT NULL DEFAULT 1,
    case_type_l1    VARCHAR(50),
    case_type_l2    VARCHAR(50),
    case_type_l3    VARCHAR(50),
    plan_confidence DECIMAL(4,3),
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

CREATE TABLE dynamic_plan_slot (
    id                UUID PRIMARY KEY,
    plan_id           UUID NOT NULL REFERENCES consultation_dynamic_plan(id),
    slot_id           VARCHAR(100) NOT NULL,
    label             VARCHAR(200) NOT NULL,
    source            VARCHAR(30) NOT NULL,
    static_mapping_id VARCHAR(200),
    required          BOOLEAN NOT NULL DEFAULT FALSE,
    priority          INT NOT NULL,
    status            VARCHAR(30) NOT NULL,
    collected_value   TEXT,
    pending_value     TEXT,
    validation_hint   VARCHAR(50),
    question_text     TEXT,
    asked_at          TIMESTAMP,
    answered_at       TIMESTAMP,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);
```

### Commit 2. Entity/repository 추가

1. `ConsultationDynamicPlan` entity를 추가한다.
2. `DynamicPlanSlot` entity를 추가한다.
3. plan과 slot repository를 추가한다.
4. status/source enum은 P1.5 enum과 이름을 맞춘다.
5. P3 feature가 활성화되면 slot write는 정규화 table에 먼저 수행하고 `slot_state`는 같은 transaction에서 재생성하는 cache로만 갱신한다.
6. `slot_state`와 `dynamic_plan_slot`이 충돌하면 `dynamic_plan_slot`을 우선하고 mismatch metric을 남긴다.

### Commit 3. Alias mapping loader 추가

1. 기존 checklist YAML은 변경하지 않는다.
2. `src/main/resources/ai/checklists/aliases/*.yaml` 경로를 새로 사용한다.
3. alias loader는 static mapping id, label, keywords를 읽는다.
4. loader는 startup 시 Map으로 캐싱한다.
5. ontology와 alias는 모두 startup cache로 로드하며, P3에서는 hot reload를 지원하지 않는다. 변경은 resource 수정 + 배포로 반영한다.

Alias 예시:

```yaml
real-estate:
  lease_end_date:
    labels:
      - 계약 종료일
    keywords:
      - 계약 종료
      - 전세 만료
      - 임대차 종료
      - lease_expiry
```

Dynamic slot의 static 승격 절차:

1. P4 오프라인 평가 리포트에서 static 승격 후보를 생성한다.
2. 담당자가 후보 label, keyword, 기존 static 중복 여부를 검토한다.
3. `aliases/*.yaml`에 mapping을 추가한다.
4. 필요한 경우 checklist YAML에 static 항목을 별도 PR로 추가한다.
5. `ChecklistAliasIndexTest`를 갱신한다.
6. 운영 배포 후 batch job으로 기존 `dynamic_plan_slot.source`를 `static_checklist`로 전환하고 `static_mapping_id`를 채운다.
7. 전환 결과와 unmapped 잔여 slot 수를 리포트한다.

### Commit 4. DynamicPlanProposer 추가

1. Cohere 또는 OpenAI를 사용해 plan proposal JSON을 생성한다.
2. 컴포넌트 이름은 반드시 `DynamicPlanProposer`로 한다.
3. proposer는 저장하지 않고 proposal DTO만 반환한다.
4. proposal에는 `caseType`, `planConfidence`, `slots`, `nextSlotId`, `allCompleted`가 포함된다.

### Commit 5. BackendValidator 추가

검증 규칙:

1. caseType이 ontology 범위 안에 있어야 한다.
2. static slot은 checklist 또는 alias index에 존재해야 한다.
3. dynamic slot은 최소 하나의 static category 또는 alias keyword에 매핑 가능해야 한다.
4. question text는 `GuardrailFilter`를 통과해야 한다.
5. required/priority는 백엔드 정책으로 보정한다.

Ontology 데이터 소스:

- `OntologyService`가 사용하는 `slimOntologyJson` bean을 source of truth로 사용한다.
- ontology는 애플리케이션 시작 시 파싱해 memory cache로 보관한다.
- P3에서는 hot reload를 지원하지 않고, ontology 변경은 resource 수정과 애플리케이션 배포가 필요하다.
- BackendValidator는 하드코딩된 도메인 목록을 갖지 않고 `OntologyService.contains/pathOf/isChildOf` 계열 API만 사용한다.

검증 실패 slot은 저장하지 않고 rejection log에 사유를 남긴다.

### Commit 6. Incremental update 적용

Plan 재생성 조건:

- 첫 턴
- L2 이상 topic change
- CORRECT_INFO로 기존 slot 3개 이상 무효화
- 동일 slot에 대한 CORRECT_INFO가 한 상담 안에서 2회 이상 반복
- planConfidence < 0.65

초기 기준값은 운영 기본값이 아니라 시작 기본값이다. 재생성률, validator rejection rate, 상담 완료율을 주 1회 리뷰하고 config PR로 조정한다.

그 외에는 existing plan의 slot status만 갱신한다.

---

## 4. 인터페이스/API 변경

- DB:
  - `consultation_dynamic_plan`
  - `dynamic_plan_slot`
- Entity/repository:
  - `ConsultationDynamicPlan`
  - `DynamicPlanSlot`
  - 관련 repository
- Internal service:
  - `DynamicPlanProposer`
  - `BackendValidator`
  - `ChecklistAliasIndex`
- Resource:
  - `src/main/resources/ai/checklists/aliases/*.yaml`
- External API:
  - 상담 API 응답 shape는 변경하지 않는다.

---

## 5. 테스트 계획

### Unit tests

- 신규 `ChecklistAliasIndexTest`
  - alias YAML을 로드하고 keyword mapping을 확인한다.
  - dynamic slot의 static 승격 후 alias mapping이 기존 dynamic label을 resolve하는지 확인한다.
- 신규 `DynamicPlanProposerSchemaTest`
  - proposal DTO schema와 required fields를 검증한다.
- 신규 `BackendValidatorTest`
  - out-of-domain slot을 reject한다.
  - unmapped dynamic slot을 reject한다.
  - legal judgment question을 reject한다.
  - valid static/dynamic slot을 accept한다.
- 신규 `DynamicPlanIncrementalUpdateTest`
  - regeneration 조건과 status-only update 조건을 검증한다.
  - planConfidence < 0.65, invalidated slot >= 3, 동일 slot correction 2회 조건을 검증한다.

### Integration tests

- migration 적용 후 plan/slot 저장과 조회가 가능해야 한다.
- P3 활성화 후 `dynamic_plan_slot`이 source of truth이고 `slot_state`는 summary cache로 재생성되어야 한다.
- 의도적으로 cache mismatch를 만들면 정규화 table 값이 우선되어야 한다.
- 기존 checklist YAML schema test는 계속 통과해야 한다.

---

## 6. 완료 기준

- [ ] dynamic plan FK 타입은 모두 UUID다.
- [ ] `DynamicPlanAgent`라는 이름을 사용하지 않는다.
- [ ] P3 활성화 후 `dynamic_plan_slot`이 source of truth이고 `slot_state`는 cache로만 쓰인다.
- [ ] LLM proposal은 validator 승인 전 저장되지 않는다.
- [ ] alias mapping은 기존 checklist YAML schema를 깨지 않는다.
- [ ] dynamic slot의 static 승격 체크리스트가 문서와 테스트에 반영된다.
- [ ] ontology source는 `OntologyService`/`slimOntologyJson`이고 변경에는 배포가 필요하다는 점이 명시된다.
- [ ] BackendValidator rejection 사유가 로그에 남는다.
- [ ] plan regeneration은 `planConfidence < 0.65`, invalidated slot >= 3, 동일 slot correction 2회 기준으로만 발생한다.
- [ ] 상담 API 외부 응답 shape는 변경되지 않는다.

---

## 7. Rollback / Feature Flag

- `app.ai.dynamic-plan.enabled=false`이면 P1.5 slot ledger 기반 흐름으로 동작한다.
- 신규 테이블은 기존 상담 흐름에서 참조하지 않으면 무해하다.
- alias loader 실패 시 dynamic plan feature를 자동 비활성화하고 서버는 계속 기동한다.
- validator false positive가 높으면 dynamic slot만 비활성화하고 static slot proposal은 유지할 수 있게 한다.
- 즉시 rollback 기준은 validator false positive > 5%, plan 재생성률 > 30%, cache mismatch rate > 0.5%, 또는 plan proposal API 오류율 > 5%가 10분 지속되는 경우다.
