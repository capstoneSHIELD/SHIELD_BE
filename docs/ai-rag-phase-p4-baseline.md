# AI/RAG Phase P4 Baseline

상위 문서: `docs/ai-rag-phase-p4-rag-quality-loop.md`

## 목적

P4의 기본 원칙은 현재 weighted hybrid retrieval을 운영 baseline으로 유지하고, RRF, rerank, score gate, intent-aware retrieval은 평가 결과가 확인될 때까지 feature flag 뒤에 둔다.

## Baseline 설정

- `app.ai.rag.fusion-mode=weighted`
- `app.ai.rag.retrieval-gate.enabled=false`
- `app.ai.rag.intent-aware.enabled=false`
- `app.ai.output-judge.shadow-enabled=false`

## Eval Set 구성 기준

- 최초 eval set은 최근 3개월 상담 로그에서 retrieval 실패 60건, 성공 60건, 신규/저빈도 도메인 30건을 샘플링해 150건으로 시작한다.
- 각 항목은 `query`, `expected_law_or_case_id`, `domain`, `failure_type`, `labeler`, `created_at`을 가진다.
- 분기마다 70%는 유지하고 30%를 신규 운영 패턴으로 교체한다.

## 측정 지표

- Recall@5
- MRR
- nDCG@5
- retrieval latency p50/p95
- rerank API cost
- score gate false drop rate

## 재현 명령

현재 저장소에는 운영 상담 로그 기반 eval runner가 아직 연결되어 있지 않다. P4 코드 레벨 검증은 아래 단위 테스트로 시작한다.

```powershell
.\gradlew.bat test --tests "org.example.shield.ai.application.RrfFusionServiceTest" --tests "org.example.shield.ai.application.RetrievalScoreGateTest" --tests "org.example.shield.ai.application.RetrievalScoreCalibratorTest" --tests "org.example.shield.ai.application.IntentAwareRetrieverTest" --tests "org.example.shield.ai.application.OutputComplianceShadowJudgeTest" --tests "org.example.shield.ai.application.OfflineQualityReportRecordTest"
```

## 운영 적용 전 확인

- RRF 또는 rerank가 weighted baseline 대비 Recall@5 -2%p 이상 떨어지면 적용하지 않는다.
- score gate는 calibration 결과 없이 threshold를 설정하지 않는다.
- output judge는 p95 지연 +200ms, 전체 LLM 비용 +10% 이내, PII masking 통과 조건을 모두 만족해야 shadow 이후 단계로 넘어간다.
