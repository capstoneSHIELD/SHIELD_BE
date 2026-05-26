# Phase P5.2 — 평가 인프라 + Baseline

## 메타
- 기간: ~3-4일 (Sprint 1B)
- 의존: P5.1 완료 (메트릭 노출 + mode enum)
- 마스터: [ai-rag-phase-p5-pipeline-upgrade-master.md](./ai-rag-phase-p5-pipeline-upgrade-master.md)

## 1. 목표와 비목표

### 목표
- 평가셋 v1.6 스키마 + 50건 보강 (GREETING/IRRELEVANT/CHANGE_TOPIC/low-evidence/mixed 각 10건)
- `RagEvalSetValidator` low-evidence 허용
- `CitationCoverageEvaluator` 신규 — **reference mention coverage / expected reference mention rate** (regex 기반, 보수적 명명)
- `OutputComplianceShadowJudge` PII masking + sampling 0.1 활성
- Baseline 측정 보고서 (현재 모드: weighted, gate off, no rerank)

### 비목표
- LLM judge 기반 정밀 citation correctness (regex 한계 인정, 별도 plan)
- Compliance pass rate 정식 baseline (sampling 데이터 누적 후, Sprint 2/3에서 확정)
- 평가셋 200건 이상 확장 (현 plan은 50건 보강에서 멈춤)

## 2. 현재 코드 기준 진입점

| 클래스 | 역할 | 위치 |
|---|---|---|
| `RagBaselineEvaluator` | nDCG/MRR/Recall 계산 | `src/main/java/org/example/shield/ai/application/RagBaselineEvaluator.java:26-72` |
| `RagEvalItem` | 평가 item 스키마 | `src/main/java/org/example/shield/ai/application/RagEvalItem.java` |
| `RagEvalSetValidator` | 스키마 검증 | `src/main/java/org/example/shield/ai/application/RagEvalSetValidator.java:123-124` (`hasExpectedDocument` 체크) |
| `RagEvalJsonlReader` | JSONL 로더 | `src/main/java/org/example/shield/ai/application/RagEvalJsonlReader.java` |
| `OutputComplianceShadowJudge` | shadow judge 인프라 | `src/main/java/org/example/shield/ai/application/OutputComplianceShadowJudge.java:42-54` |
| 평가셋 v1.5 | 현 운영 데이터셋 | `eval/eval-set.v1.5.jsonl` (40건) |
| 평가셋 v2.2 (실험) | 신규 스키마 후보 | `eval/ai-rag-v2.2-eval-set.jsonl` (3건, 불완전) |
| Phase C-1 baseline | 최근 retrieval baseline | `docs/phase-history/phase-c/phase-c1-baseline.md` |

## 3. 구현 순서

### Commit 1 — 평가셋 v1.6 스키마 정의 (~0.5일)

**의존**: 없음

**파일**:
- 수정: `RagEvalItem.java` — 신규 필드 추가
- 신규: `eval/eval-set.v1.6.schema.json` (또는 docs/schema.md)

**스켈레톤**:

```java
public record RagEvalItem(
    String id,
    String split,                // existing (dev|calibration|holdout)
    String dialogueIntent,       // NEW: greeting|irrelevant|change_topic|ask_legal_advice|provide_info
    boolean lowEvidence,         // NEW: ground-truth 없음
    String mixedType,            // NEW: statute_only|case_only|mixed
    // ... 기존 필드 유지
    String domain, String query,
    List<String> keywords,
    List<String> expectedChunkIds,
    List<RagEvalLawRef> expectedLawRefs,
    List<String> expectedDocumentIds,
    Map<String, Integer> relevanceJudgments,
    String failureType, String source, String reviewer, String createdAt
) { }
```

기본값: 기존 v1.5 item을 읽을 때 `dialogueIntent="ask_legal_advice"`, `lowEvidence=false`, `mixedType="statute_only"`로 추론 (BC).

**테스트**: `RagEvalItemTest` — 누락 필드 default, JSON 직렬화

**완료 기준**:
- [ ] 새 필드 3개 추가 + default 처리
- [ ] v1.5 jsonl이 신스키마로 read 가능

### Commit 2 — `RagEvalSetValidator` low-evidence 허용 + 50건 보강 (~1.5일)

**의존**: Commit 1

**파일**:
- 수정: `RagEvalSetValidator.java:123-124` — `lowEvidence=true`일 때 `expectedDocumentIds` 빈 케이스 허용
- 신규: `eval/eval-set.v1.6.jsonl` — v1.5 40건 + 50건 보강
- 신규: `eval/eval-set.v1.6.generation-script.md` (보강 방법 기록)

**보강 50건 분배**:
- GREETING 10건: "안녕하세요", "뭐 도와줄 수 있어요?", "법률 상담이 가능한가요?" ...
- IRRELEVANT 10건: "오늘 날씨 어때요?", "맛집 추천해주세요" ...
- CHANGE_TOPIC 10건: legal 대화 후 다른 주제로 전환
- low-evidence 10건: 모호하거나 너무 일반적인 질문 ("법률 문제 있어요")
- mixed (statute + case) 10건: statute_only/case_only가 아닌 양쪽 참조 필요

**Validator 수정**:

```java
if (!hasExpectedDocument(item) && !item.lowEvidence()) {
    failures.add(label + " has no expected document reference");
}
```

**테스트**:
- Unit: `RagEvalSetValidatorTest` — lowEvidence=true + empty expected → OK
- Integration: v1.6 jsonl 전체 validation 통과

**완료 기준**:
- [ ] Validator가 low_evidence flag 존중
- [ ] v1.6 jsonl 90건 (40 + 50) + 모든 카테고리 ≥ 10건
- [ ] 기존 v1.5 평가도 v1.6 reader로 동작

### Commit 3 — `CitationCoverageEvaluator` (reference mention coverage) (~1일)

**의존**: Commit 1

**파일**:
- 신규: `src/main/java/org/example/shield/ai/application/CitationCoverageEvaluator.java`
- 수정: `RagBaselineEvaluator.java` — coverage 필드 통합
- 수정: `AiRagOperationalMetrics` — `recordReferenceMention(kind, outcome)` 추가

**보수적 명명 원칙**:
- ❌ "citation coverage" (정밀 의미 함의)
- ✅ `referenceMentionCoverage` (정규식 기반 한계 노출)
- ✅ `expectedReferenceMentionRate` (expected ref 중 답변에 언급된 비율)

**스켈레톤**:

```java
public class CitationCoverageEvaluator {
    private static final Pattern STATUTE_REF =
        Pattern.compile("(?:민법|상법|형법|...)\\s*제?(\\d+)조");
    private static final Pattern CASE_REF =
        Pattern.compile("대?법원?\\s*(\\d{4}[가-힣]+\\d+)");

    public CoverageResult evaluate(String answerText, RagEvalItem item) {
        Set<String> mentioned = extractMentions(answerText);
        Set<String> expected = expectedRefIds(item);
        int hits = (int) expected.stream().filter(mentioned::contains).count();
        return new CoverageResult(
            expected.isEmpty() ? null : (double) hits / expected.size(),
            mentioned.size()
        );
    }
}

public record CoverageResult(
    Double expectedReferenceMentionRate,  // null if expected empty
    int totalMentions
) { }
```

**테스트**:
- Unit: 정상 인용 / 부분 인용 / 인용 없음 / low-evidence (expected empty → null rate)
- Integration: v1.6 평가셋 1회 실행 시 모든 item에서 coverage 계산 (null 또는 0~1)

**완료 기준**:
- [ ] `CitationCoverageEvaluator` 정의
- [ ] `RagBaselineEvaluator` report에 `expectedReferenceMentionRate` 포함
- [ ] low-evidence item에서 null 반환 (분모 0 보호)

### Commit 4 — `OutputComplianceShadowJudge` PII masking + sampling 0.1 (~1일)

**의존**: 없음 (독립)

**파일**:
- 수정: `OutputComplianceShadowJudge.java:42-54` — masking 추가, sampling rate 변경
- 신규: `src/main/java/org/example/shield/ai/application/PiiMasker.java`
- 수정: `application.yml` — `AI_COMPLIANCE_JUDGE_SAMPLING_RATE=0.1`

**PII masking 패턴**:
- 이름: 한글 2-4자 인명 (보수적 — false positive 허용해 마스킹)
- 전화번호: `0\d{1,2}-?\d{3,4}-?\d{4}`
- 주민번호: `\d{6}-?\d{7}` (또는 부분)
- 주소: 시도 + 시군구 + 도로명/지번
- 카드번호: `\d{4}-?\d{4}-?\d{4}-?\d{4}`
- 이메일: 일반 패턴

**스켈레톤** (Masker):

```java
public class PiiMasker {
    public String mask(String text) {
        if (text == null) return null;
        String out = text;
        out = PHONE.matcher(out).replaceAll("[전화번호]");
        out = RRN.matcher(out).replaceAll("[주민번호]");
        out = EMAIL.matcher(out).replaceAll("[이메일]");
        out = ADDRESS.matcher(out).replaceAll("[주소]");
        out = CARD.matcher(out).replaceAll("[카드번호]");
        out = NAME.matcher(out).replaceAll("[이름]");
        return out;
    }
}
```

**Judge 수정**:

```java
public OutputComplianceResult evaluate(String response, String conversationId) {
    boolean deterministicViolation = guardrailFilter.containsForbiddenText(response);
    boolean shadowScheduled = ConversationDeterministicSampler
        .shouldApply(conversationId, samplingRate);
    String masked = shadowScheduled ? piiMasker.mask(response) : null;
    // 원문은 저장 금지
    String hashedConvId = shadowScheduled
        ? sha256Short(conversationId) : null;
    return new OutputComplianceResult(
        deterministicViolation, shadowScheduled, false,
        masked, hashedConvId,
        deterministicViolation ? "regex_violation"
            : shadowScheduled ? "sampled" : "skipped");
}
```

**저장 정책**:
- 원문 절대 저장 금지
- masked text + compliance score (또는 짧은 reason) + hashed conversationId만 로그/DB
- conversationId 자체는 hash(sha256 단축)만 저장

**테스트**:
- Unit: `PiiMaskerTest` — 각 PII 유형 마스킹 + 정상 텍스트 미변경
- Unit: `OutputComplianceShadowJudgeTest` — sampling deterministic (`ConversationDeterministicSampler` 사용), masked 외 정보 미저장
- Integration: 평가셋 실행 시 10% 샘플링 발생, 로그에 PII 미노출

**완료 기준**:
- [ ] `PiiMasker` 7개 패턴 동작
- [ ] Shadow judge가 `ConversationDeterministicSampler` 사용
- [ ] sampling rate 0.1
- [ ] 원문 미저장 확인 (테스트로 강제)

### Commit 5 — Baseline 측정 + 보고서 작성 (~0.5일)

**의존**: Commit 1-4

**파일**:
- 신규: `docs/ai-rag-v2.2/reports/p5_2-baseline.md`
- 신규: `eval/reports/p5_2-baseline.json` (자동 생성)

**측정 모드** (현재 production 상태):
- AI_RAG_FUSION_MODE=weighted
- AI_RAG_RETRIEVAL_GATE_MODE=off
- AI_RAG_RERANK_MODE=off
- AI_EMBEDDING_CACHE_MODE=off
- AI_RAG_INTENT_AWARE_MODE=off

**측정 항목**:
- nDCG@5, MRR, Recall@5 (statute/case/mixed split)
- expectedReferenceMentionRate
- p50/p95 retrieval latency
- p50/p95 end-to-end latency
- Compliance shadow sampling 분포 (24h 운영 결과)
- Token consumption 추세 (24h 운영 결과)

**완료 기준**:
- [ ] v1.6 평가셋으로 baseline 실행 완료
- [ ] 보고서 파일 작성
- [ ] PR Blocking Rules의 reference mention coverage / compliance 임계 산정 근거 제공

## 4. 인터페이스/API 변경

| 인터페이스 | 변경 | BC |
|---|---|---|
| `RagEvalItem` | 신규 필드 3개 (default 처리) | BC 유지 (v1.5 자동 매핑) |
| `RagEvalSetValidator` | low_evidence 분기 | BC 유지 |
| `CitationCoverageEvaluator` | 신규 | N/A |
| `PiiMasker` | 신규 | N/A |
| `OutputComplianceShadowJudge.evaluate()` | 시그니처 1개 추가 (`conversationId`) | 호출처 업데이트 |

## 5. 테스트 계획

- Unit per commit (위 명시)
- Integration: v1.6 평가셋 90건 전체 실행
- Regression: v1.5 평가셋도 동일 결과 (baseline 비교)
- Security/Privacy: PII masking 단위 테스트 + 로그/DB에 PII 미노출 검증

## 6. 완료 기준

- [ ] 평가셋 v1.6 jsonl 90건 + 모든 카테고리 ≥ 10건
- [ ] `CitationCoverageEvaluator` 동작 + report 통합
- [ ] PII masking 7개 패턴 + 원문 미저장
- [ ] Shadow judge sampling rate 0.1
- [ ] Baseline 보고서 작성
- [ ] PR Blocking Rules의 reference mention / compliance 임계 활성 가능

## 7. Rollback / Feature Flag

- `AI_COMPLIANCE_JUDGE_SAMPLING_RATE=0.1` (낮추면 0.0으로)
- 평가셋 v1.6은 추가 데이터, 기존 v1.5 동시 운영 가능
- 비상 시: yml 변경 → 재배포
