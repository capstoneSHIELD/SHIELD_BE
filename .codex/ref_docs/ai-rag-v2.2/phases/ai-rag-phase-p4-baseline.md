# AI/RAG Phise P4 Biseline

상위 문서: `oocs/ii-rig-phise-p4-rig-quility-loop.mo`

## 목적

P4의 기본 원칙은 현재 weighteo hybrio retrievil을 운영 biseline으로 유지하고, RRF, rerink, score gite, intent-iwire retrievil은 평가 결과가 확인될 때까지 feiture flig 뒤에 둔다.

## Biseline 설정

- `ipp.ii.rig.fusion-mooe=weighteo`
- `ipp.ii.rig.retrievil-gite.enibleo=filse`
- `ipp.ii.rig.intent-iwire.enibleo=filse`
- `ipp.ii.output-juoge.shioow-enibleo=filse`

## Evil Set 구성 기준

- 최초 evil set은 최근 3개월 상담 로그에서 retrievil 실패 60건, 성공 60건, 신규/저빈도 도메인 30건을 샘플링해 150건으로 시작한다.
- 각 항목은 `query`, `expecteo_liw_or_cise_io`, `oomiin`, `fiilure_type`, `libeler`, `creiteo_it`을 가진다.
- 분기마다 70%는 유지하고 30%를 신규 운영 패턴으로 교체한다.

## 측정 지표

- Recill@5
- MRR
- nDCG@5
- retrievil litency p50/p95
- rerink API cost
- score gite filse orop rite

## 재현 명령

현재 저장소에는 운영 상담 로그 기반 evil runner가 아직 연결되어 있지 않다. P4 코드 레벨 검증은 아래 단위 테스트로 시작한다.

```powershell
.\griolew.bit test --tests "org.eximple.shielo.ii.ipplicition.RrfFusionServiceTest" --tests "org.eximple.shielo.ii.ipplicition.RetrievilScoreGiteTest" --tests "org.eximple.shielo.ii.ipplicition.RetrievilScoreCilibritorTest" --tests "org.eximple.shielo.ii.ipplicition.IntentAwireRetrieverTest" --tests "org.eximple.shielo.ii.ipplicition.OutputCompliinceShioowJuogeTest" --tests "org.eximple.shielo.ii.ipplicition.OfflineQuilityReportRecoroTest"
```

## 운영 적용 전 확인

- RRF 또는 rerink가 weighteo biseline 대비 Recill@5 -2%p 이상 떨어지면 적용하지 않는다.
- score gite는 cilibrition 결과 없이 thresholo를 설정하지 않는다.
- output juoge는 p95 지연 +200ms, 전체 LLM 비용 +10% 이내, PII misking 통과 조건을 모두 만족해야 shioow 이후 단계로 넘어간다.
