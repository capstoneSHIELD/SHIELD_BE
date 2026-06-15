# AI 법률 분야 분류 및 변호사 매칭 실험 파이프라인 클래스 다이어그램

기준 문서: [ai-complex-law-classification-experiment.md](../ai-complex-law-classification-experiment.md)

이 다이어그램은 `runner`가 담당하는 orchestration과 BE local/test adapter가 담당하는 실제 classifier/matching 호출 경계를 분리해서 표현한다. `IntentRouteAdapter`와 `LawyerMatchAdapter`는 SHIELD BE 안의 local/test profile 전용 adapter이고, 나머지는 `EXPERIMENT/runner`의 Python 모듈이다.

```mermaid
classDiagram
direction LR

class RunExperiment {
  +main(args)
  +loadConfig()
  +createRunContext()
  +runPreflight()
  +runClassification()
  +runMatching()
  +writeReports()
}

class ExperimentConfig {
  +Path datasetPath
  +Path classificationTurnsPath
  +Path ontologySnapshotPath
  +Path lawyerCorpusPath
  +Path matchingLabelsPath
  +List providers
  +List classificationModes
  +List matchingModes
  +Map productionGroupWeights
  +Map hybridMatchWeights
}

class RunContext {
  +String runId
  +String repo
  +String branch
  +String commitSha
  +String runtimeScopeSource
  +Path outputDir
}

class DatasetBuilder {
  +loadArchetypes()
  +expandCaseVariants()
  +buildClassificationTurns()
  +writeDatasetJsonl()
}

class Archetype {
  +String id
  +String group
  +List goldNodeIds
  +String primaryNodeId
}

class CaseVariant {
  +String caseId
  +String archetypeId
  +String variant
  +List messages
  +Boolean expectedComplex
}

class ClassificationTurn {
  +String id
  +String caseId
  +String conversationId
  +int turnIndex
  +Boolean isFinalTurn
  +String benchmarkSplit
  +List messages
  +List goldNodeIds
}

class OntologySnapshot {
  +Set nodeIds
  +nodePath(nodeId)
  +parentOf(nodeId)
  +validate(nodeIds)
}

class LawyerCorpusLoader {
  +loadLawyers()
  +loadMatchingLabels()
  +validateCoverage()
  +validateHardNegatives()
}

class LawyerFixture {
  +String lawyerId
  +List practiceNodeIds
  +String primaryNodeId
  +List secondaryNodeIds
  +List domains
  +List subDomains
  +List tags
  +String embeddingText
}

class MatchingLabelSet {
  +String labelSetId
  +String caseId
  +List relevanceGrades
  +gradeOf(lawyerId)
}

class ExperimentClient {
  +preflightProviders()
  +intentRoute(request)
  +lawyerMatch(request)
}

class IntentRouteAdapter {
  <<BEAdapter>>
  +preflight()
  +route(messages, domain, provider)
  +returnRawAndParsed()
}

class LawyerMatchAdapter {
  <<BEAdapter>>
  +preflight()
  +loadSyntheticCorpus()
  +match(query, matchingMode, topK)
}

class ClassificationModeRunner {
  +runAFull(turn)
  +runBScopedGold(turn)
  +runBScopedRuntime(turn)
  +runCHybridRuntime(turn)
}

class HybridClassificationPolicy {
  +inferRuntimeScope(turn, fullResult)
  +shouldRerunFull(scopedResult, turn)
  +choose(scopedResult, fullResult)
}

class MatchingRunner {
  +runCurrentServiceCosine(case)
  +runOracleCosine(case)
  +runPredictedHybrid(case)
  +runOracleHybrid(case)
  +runNoLabelCosine(case)
}

class CurrentServiceQueryBuilder {
  +nodeIdsToDomains(nodeIds)
  +nodeIdsToSubDomains(nodeIds)
  +nodeIdsToTags(nodeIds)
  +buildLikeLawyerEmbeddingText()
  +hashQueryText()
}

class HybridMatchScorer {
  +fieldOverlap(caseNodes, lawyerNodes)
  +keywordOverlap(caseKeywords, lawyerTags)
  +score(cosine, fieldOverlap, keywordOverlap)
}

class ResultStore {
  +writeRawResult()
  +writeParsedResult()
  +writeMatchingResult()
  +writeRunMeta()
}

class Evaluator {
  +classificationMetrics()
  +matchingMetrics()
  +scopeLossMetrics()
  +corpusCoverageMetrics()
}

class ReportWriter {
  +writeMetricsSummary()
  +writeMatchingMetricsSummary()
  +writeBenchmarkValidityCheck()
  +writeCurrentServiceBaseline()
  +writeCorpusCoverageReport()
  +writeFailureCases()
}

RunExperiment --> ExperimentConfig : reads
RunExperiment --> RunContext : creates
RunExperiment --> DatasetBuilder : builds input rows
RunExperiment --> LawyerCorpusLoader : loads fixtures
RunExperiment --> ClassificationModeRunner : executes T1/B1/B2
RunExperiment --> MatchingRunner : executes T2/B3/B4
RunExperiment --> Evaluator : calculates metrics
RunExperiment --> ReportWriter : writes reports
RunExperiment --> ResultStore : persists outputs

DatasetBuilder --> Archetype : reads
DatasetBuilder --> CaseVariant : expands
DatasetBuilder --> ClassificationTurn : emits
DatasetBuilder --> OntologySnapshot : validates labels

LawyerCorpusLoader --> LawyerFixture : loads
LawyerCorpusLoader --> MatchingLabelSet : loads
LawyerCorpusLoader --> OntologySnapshot : validates practice nodes

ClassificationModeRunner --> ClassificationTurn : consumes
ClassificationModeRunner --> ExperimentClient : calls route
ClassificationModeRunner --> HybridClassificationPolicy : applies hybrid rules
ClassificationModeRunner --> ResultStore : writes parsed/raw rows

HybridClassificationPolicy --> OntologySnapshot : checks hierarchy

MatchingRunner --> ClassificationTurn : consumes final turns
MatchingRunner --> LawyerFixture : ranks
MatchingRunner --> MatchingLabelSet : attaches relevance
MatchingRunner --> CurrentServiceQueryBuilder : reproduces baseline query
MatchingRunner --> HybridMatchScorer : scores hybrid modes
MatchingRunner --> ExperimentClient : calls match adapter
MatchingRunner --> ResultStore : writes matching rows

ExperimentClient --> IntentRouteAdapter : HTTP /internal/experiments/intent-route
ExperimentClient --> LawyerMatchAdapter : HTTP /internal/experiments/lawyer-match

Evaluator --> OntologySnapshot : validates node predictions
Evaluator --> MatchingLabelSet : computes ranking metrics
Evaluator --> ResultStore : reads outputs

ReportWriter --> Evaluator : consumes metric tables
ReportWriter --> ResultStore : reads run artifacts
```

## 해석 기준

- `RunExperiment`는 orchestration만 담당한다. classifier prompt, ontology scoping, provider client, parser, 운영 cosine query 생성은 직접 재구현하지 않는다.
- `ClassificationModeRunner`는 Layer 1 분류 benchmark인 `B1_LAYER1_TURN_CLASSIFICATION`, `B2_SCOPE_LOSS`를 실행한다.
- `MatchingRunner`는 final turn만 기본 입력으로 사용해 `B3_CURRENT_MATCHING_BASELINE`, `B4_MATCHING_ABLATION`을 실행한다.
- `CurrentServiceQueryBuilder`는 `PREDICTED_LABELS_COSINE_ONLY`가 현재 `LawyerMatchingService`와 같은 query text를 쓰는지 검증하기 위한 runner-side mirror다. 실제 운영 경로 검증은 `LawyerMatchAdapter` preflight에서 한 번 더 수행한다.
- `HybridMatchScorer`는 운영 코드가 아니라 실험 비교군이다. final test 실행 전 `ExperimentConfig.hybridMatchWeights`가 고정되어야 한다.
