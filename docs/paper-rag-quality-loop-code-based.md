# 2.4 RAG 품질 루프

## 2.4.1 운영 검색 파이프라인

SHIELD의 운영 RAG 검색 레이어는 PostgreSQL 기반 법령·판례 저장소와 Cohere 임베딩을 결합한 3-way 하이브리드 검색 구조를 사용한다. 사용자 메시지가 들어오면 `RagPipelineService`는 최근 대화 이력과 사용자가 선택한 도메인을 `IntentClassificationService`에 전달하여 의도 요약, 온톨로지 노드, 핵심 키워드, 검색 질의를 산출한다. 현재 프롬프트 스키마는 단일 `retrieval_query`를 기본 출력으로 요구하지만, 파서는 하위 호환성을 위해 `retrieval_queries` 배열도 수용한다. 운영 경로에서는 이 중 첫 번째 검색 질의만 `vectorQuery`로 사용하며, 검색 질의가 비어 있으면 도메인 기반 fallback 질의를 생성한다.

검색 범위는 온톨로지 분류 결과와 법령 매핑으로 보강된다. `CategoryLawMappingService`는 분류된 노드 ID를 관련 `law_id` 목록으로 변환하고, `RagPipelineService`는 이를 법령 검색의 필터 입력으로 전달한다. 판례 포함 옵션이 활성화된 경우, 기본값 `rag.retrieval.include-cases=true`에 따라 `LegalRetrievalService.retrieveMixed()`가 호출되어 법령 코퍼스와 판례 코퍼스를 모두 검색한다. 법령은 `legal_chunks` 테이블에 조문 청크 단위로 저장되고, 판례는 `legal_cases` 테이블에 판례 1건당 1행으로 저장된다. 두 테이블 모두 `vector(1024)` 임베딩 컬럼과 전문검색용 `content_tsv`를 가진다.

운영 검색의 기본 점수는 벡터 유사도, PostgreSQL 전문검색 점수, 트라이그램 유사도를 결합한 가중합이다. 법령 검색의 경우 각 경로는 다음과 같이 계산된다. 벡터 경로는 Cohere `embed-v4.0`으로 생성한 1024차원 질의 임베딩과 `pgvector`의 cosine distance 연산자 `<=>`를 사용하고, `1 - distance`를 유사도로 사용한다. 키워드 경로는 핵심 키워드를 `to_tsquery('simple', ...)` 형식으로 변환한 뒤 `ts_rank(content_tsv, query, 1)`로 계산한다. 트라이그램 경로는 `pg_trgm`의 `similarity(content, query)`를 사용하여 한국어 공백 기반 전문검색의 한계를 보완한다. 최종 점수는 다음과 같다.

```text
score = w_vector * s_vector + w_keyword * s_keyword + w_trigram * s_trigram
```

기본 가중치는 `w_vector=0.5`, `w_keyword=0.3`, `w_trigram=0.2`이며, `RAG_W_VECTOR`, `RAG_W_KEYWORD`, `RAG_W_TRIGRAM` 환경변수로 조정할 수 있다. 각 검색 경로는 CTE로 분리되어 상위 후보를 수집하고, 후보 풀을 `UNION`한 뒤 가중합 점수 기준으로 정렬한다. 현재 운영 SQL에서 각 경로의 후보 수는 40개로 고정되어 있으며, 최종 반환 수는 호출부의 `topK`로 제한된다. `RagPipelineService`의 운영 호출은 현재 `topK=3`을 사용한다.

판례 검색도 동일한 3-way 구조를 따른다. 다만 판례의 트라이그램 비교 대상은 법령의 `content`가 아니라 판례 요지에 해당하는 `holding` 컬럼이다. `retrieveMixed()`는 법령과 판례를 각각 검색한 뒤 두 결과를 하나의 리스트로 합치고, 별도의 소스별 정규화 없이 raw score 내림차순으로 최종 상위 문서를 선택한다. 따라서 현재 구현은 "법령/판례 독립 검색 후 소스별 min-max 정규화" 구조가 아니라, 동일한 3-way 점수식으로 산출된 raw score 병합 구조에 가깝다.

운영 경로의 중요한 한계는 검색 질의 팬아웃이 아직 적용되지 않았다는 점이다. 분류 파서는 여러 `retrieval_queries`를 받을 수 있지만, `RagPipelineService`는 첫 번째 질의만 실제 검색에 사용한다. 따라서 하나의 사용자 발화 안에 복수의 법적 쟁점이 포함되는 경우, 후속 질의에 담긴 표현 다양성이 검색 단계에서 활용되지 않을 수 있다. 이 한계는 향후 다중 질의 검색 또는 후단 재정렬을 통해 개선할 수 있는 지점이다.

## 2.4.2 오프라인 검색 후보와 비교 기준

현재 코드베이스에서 운영 경로와 오프라인 평가 경로는 분리되어 있다. 운영 경로는 Java 서비스의 3-way 하이브리드 검색이며, 오프라인 평가는 `scripts/eval_rag.py`가 PostgreSQL과 Cohere API를 직접 호출하여 동일 계열의 SQL 점수식을 재현하는 방식으로 수행된다. 평가 스크립트는 `eval/eval-set.v1.jsonl` 또는 `eval/eval-set.v1.5.jsonl`을 입력으로 받아 Recall@1, Recall@3, Recall@5, Recall@10, MRR, nDCG@5를 계산하고, Markdown 및 JSON 리포트를 출력한다.

비교의 기준이 되는 후보 (a)는 현재 운영 방식과 가장 가까운 3-way 하이브리드 검색이다. 질의 임베딩과 BM25 계열 키워드 점수, 트라이그램 점수를 가중합하여 후보를 정렬한다. 오프라인 평가 스크립트도 기본값으로 `vector=0.5`, `keyword=0.3`, `trigram=0.2`를 사용하므로 운영 설정과 해석이 일치한다. 단, 평가 스크립트의 SQL은 운영 Java Repository의 CTE-split SQL을 완전히 동일하게 호출하는 것이 아니라, 같은 점수 구성 요소를 별도 SQL로 재현한다.

후보 (b)는 판례 포함 검색이다. 평가 스크립트에서 `--include-cases` 플래그를 사용하면 `legal_chunks`뿐 아니라 `legal_cases`도 검색에 포함한다. 이 경우 법령과 판례의 결과를 모두 모아 점수 기준으로 병합하고, `gold_articles`에는 법령 조문뿐 아니라 `{kind: "case", case_no: ...}` 형태의 정답 판례도 포함할 수 있다. 이는 운영의 `rag.retrieval.include-cases=true` 기본 설정과 대응된다.

후보 (c)는 후단 재정렬을 포함한 평가 경로다. `scripts/eval_rag.py`는 `--rerank --pool N` 옵션을 통해 하이브리드 검색 상위 `N`개 후보를 Cohere `rerank-v3.5`에 전달하고, 반환된 `relevance_score` 기준으로 top-10을 재정렬한다. 이 경로는 현재 Java 운영 코드에 통합되어 있지 않으며, 오프라인 품질 비교와 도입 가능성 평가를 위한 실험 경로로 보는 것이 정확하다. 운영 환경에 리랭커를 도입하려면 별도의 `CohereRerankClient`, timeout/fallback 처리, 비용·지연 메트릭, 실패 시 원래 하이브리드 순위로 복귀하는 정책이 추가로 필요하다.

RRF(Reciprocal Rank Fusion)는 현재 Java 운영 코드와 오프라인 스크립트에 구현되어 있지 않다. 기존 설계 문서에는 RRF가 점수 스케일 차이에 덜 민감한 후속 과제로 언급되어 있으나, 현재 구현은 순위 기반 결합이 아니라 가중합 기반 결합이다. 따라서 논문에서는 RRF를 운영 후보로 서술하기보다 향후 비교 가능한 대안으로 제한해 표현하는 것이 타당하다.

## 2.4.3 온톨로지 기반 검색 범위 보강

SHIELD는 별도의 L3 힌트 파일을 생성해 런타임에 주입하는 구조가 아니라, 온톨로지와 정적 매핑 파일을 통해 검색 범위를 보강한다. `IntentClassificationService`는 `legal-ontology-slim.json`을 포함한 프롬프트를 사용하여 사용자 발화에 가장 적합한 노드 ID를 산출한다. 이때 가능한 한 구체적인 L3 노드를 선택하도록 지시하지만, 불확실한 경우 L2 또는 L1 노드도 허용한다.

분류된 노드는 `CategoryLawMappingService`에서 두 가지 방식으로 활용된다. 첫째, `resolveLawIds()`는 노드 ID를 관련 법령 ID 목록으로 변환한다. L3 노드에 대한 직접 매핑이 없는 경우 상위 노드로 fallback하여 관련 법령을 찾는다. 이 결과는 법령 검색 SQL의 `law_id IN (:lawIds)` 조건으로 전달되므로, 현재 법령 검색에서는 하드 필터로 작동한다. 둘째, `resolveCategoryIds()`는 노드 ID를 `category_ids` 토큰으로 변환할 수 있지만, 현재 `RagPipelineService`는 이 메서드를 호출하지 않고 `classification.matchedNodeIds()`를 그대로 검색 서비스의 `categoryIds` 인자로 전달한다. 따라서 운영 경로에서 카테고리 필터가 실제 DB의 `category_ids` 토큰과 얼마나 일치하는지는 노드 ID와 저장 토큰 체계의 일관성에 좌우된다.

법령과 판례 테이블의 SQL은 모두 `category_ids && :categoryIds` 배열 겹침 조건을 지원한다. 이 조건은 후보 CTE 내부에 적용되며, 입력 배열이 비어 있으면 필터가 비활성화된다. 다만 현재 구현의 법령 검색은 `law_id` 필터도 함께 사용하므로, 검색 범위 보강은 "힌트 기반 query expansion"보다는 "온톨로지 분류 기반 법령 범위 제한과 카테고리 후보 제한"에 가깝다. recall 손실을 줄이려면 향후 `resolveCategoryIds()`를 운영 경로에 명시적으로 연결하거나, 카테고리는 soft boost로 사용하고 `law_id` 필터는 확신도에 따라 완화하는 방식이 필요하다.

## 2.4.4 장애 허용과 검색 품질 계측

현재 코드베이스에는 검색 점수 임계값으로 문서를 제거하는 `RetrievalScoreGate` 또는 임계값을 학습하는 `RetrievalScoreCalibrator`가 구현되어 있지 않다. 운영 품질 관리는 점수 게이트보다는 검색 경로의 장애 허용, 지연 측정, 오프라인 평가 리포트를 중심으로 구성되어 있다.

장애 허용 측면에서 중요한 설계는 임베딩 실패 시의 degraded retrieval이다. `PgLegalRetrievalService`는 질의 임베딩 생성이 실패하거나 빈 응답이 반환되면 1024차원 영벡터를 사용한다. 이 경우 벡터 경로의 기여는 사실상 약해지고, BM25 계열 키워드 검색과 트라이그램 검색 중심의 2-way 검색으로 자연스럽게 축소된다. 이 동작은 Cohere 임베딩 API 장애가 전체 RAG 파이프라인 실패로 이어지는 것을 줄이기 위한 안전장치다.

관측 지표는 `RagMetrics`가 Micrometer로 기록한다. 주요 지표는 Cohere 임베딩 호출 지연(`shield.rag.cohere.embed`), 검색 지연(`shield.rag.retrieve`), 벡터 경로 degrade 카운터(`shield.rag.vector.degrade`), 의도 분류 지연(`shield.rag.classify`), 전체 RAG 파이프라인 지연(`shield.rag.pipeline.total`)이다. 각 지표는 `success`, `failure`, `empty` 등의 outcome 태그를 사용해 성공, 실패, 결과 없음 상태를 구분한다. 이 지표들은 운영에서 RAG가 병목인지, 검색 결과가 비어 있는지, 임베딩 장애가 발생했는지를 분리해 관측하는 데 사용된다.

검색 품질 자체는 오프라인 평가셋으로 측정한다. `eval` 디렉터리의 JSONL 스키마는 질의 ID, 도메인, 자연어 질의, 카테고리 ID, BM25 키워드, 정답 조문 또는 정답 판례를 포함한다. 평가 스크립트는 각 질의별 상위 결과와 hit 여부를 기록하고, 도메인별 Recall@5와 MRR을 포함한 요약 리포트를 생성한다. 따라서 현재의 품질 루프는 실시간 점수 게이트보다 "운영 메트릭 + 오프라인 회귀 평가"의 결합으로 설명하는 편이 정확하다.

## 2.4.5 출력 가드레일과 개인정보 처리

응답 컴플라이언스는 LLM 기반 shadow judge가 아니라 결정론적 필터와 입력 sanitize 경로를 중심으로 구현되어 있다. `SanitizeService`는 사용자 입력에서 주민등록번호, 계좌번호, 카드번호 등 PII 패턴을 탐지하면 입력을 거부하고 안내 메시지를 반환한다. 대화 이력에 포함되는 사용자 메시지는 가능한 경우 `sanitizedContent`를 우선 사용하여 LLM 호출 시 개인정보 노출 위험을 줄인다.

출력 단계에서는 `GuardrailFilter`가 채팅 응답과 의뢰서 응답을 필터링한다. 이 필터는 법률 해석, 판례 인용, 승패 예측, 변호사 추천, 법적 조언으로 해석될 수 있는 표현을 정규식 기반으로 탐지하고, 채팅 응답의 `nextQuestion` 또는 의뢰서의 `strategy`, `keyIssues` 일부를 안전 문구로 대체하거나 제거한다. 따라서 현재 운영 차단은 LLM judge가 아니라 deterministic guardrail에 의해 수행된다.

LLM 기반 `OutputComplianceShadowJudge`는 현재 코드베이스에 구현되어 있지 않다. 향후 도입한다면 운영 차단 경로가 아니라 shadow evaluation으로 시작하는 것이 바람직하다. 이 경우 PII 마스킹 이후 외부 judge에 전달하고, `shadow-enabled`, judge latency, judge cost, false positive/false negative 표본 검토 지표를 별도로 관리해야 한다. 하지만 현재 논문에서 이를 실제 운영 컴포넌트처럼 서술하는 것은 코드와 맞지 않는다.

## 2.4.6 오프라인 품질 리포트

오프라인 품질 리포트는 Java `OfflineQualityReportJob`이 아니라 Python 스크립트 `scripts/eval_rag.py`가 생성한다. 입력은 `eval/eval-set.v1.jsonl` 또는 `eval/eval-set.v1.5.jsonl`이며, 출력은 사용자가 지정한 Markdown 파일과 동일 경로의 JSON 파일이다. JSON 출력은 `meta`, `summary`, `cases` 구조를 가지며, 각 case는 질의 ID, 도메인, 질의문, 정답 목록, BM25 tsquery, 검색 결과 rows, metrics를 포함한다.

평가 지표는 검색 recall과 순위 품질을 중심으로 구성된다. Recall@1, Recall@3, Recall@5, Recall@10은 정답 조문 또는 판례가 상위 K개 결과에 포함되는지를 측정한다. MRR은 첫 번째 정답 문서의 역순위 평균을 계산하고, nDCG@5는 상위 5개 결과 내 정답의 순위 품질을 반영한다. `eval-set.v1.5`는 법령 질의뿐 아니라 판례 질의도 포함하며, 판례 정답은 `kind: "case"`와 `case_no`로 식별된다.

리포트는 전체 지표뿐 아니라 도메인별 Recall@5와 MRR도 제공한다. 또한 각 질의에 대해 상위 5개 결과의 조문 또는 판례, 제목, hybrid score, vector score, BM25 score, trigram score, hit 여부를 Markdown 표로 출력한다. rerank 모드에서는 rerank score가 추가되어 하이브리드 순위와 후단 재정렬 순위를 비교할 수 있다.

현재 리포트가 제공하지 않는 분석도 명확히 구분할 필요가 있다. L1/L2/L3 계층별 성능, 실패 유형 분류, query type별 성능, statuteRecall@5·caseRecall@5·mixedRecall@5 같은 세분 지표는 논문에서 향후 확장 가능한 평가 축으로 제안할 수 있지만, 현재 스크립트의 기본 출력에는 포함되어 있지 않다. 따라서 현재 구현 기준의 품질 루프는 "JSONL gold set 기반 정량 평가, Markdown/JSON 리포트 산출, 운영 메트릭과 비교하여 회귀를 탐지하는 구조"로 정리하는 것이 가장 정확하다.
