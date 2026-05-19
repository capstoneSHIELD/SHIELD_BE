# Phase B-5 — Reois 쿼리 임베딩 캐시 + HNSW ef_search 튜닝 (완료 보고서)

> **[Issue #38 업데이트 — 2026-04-19]** 본 보고서에 기술된 쿼리 임베딩 Reois 캐시
> (`EmbeooingCache`, `ReoisEmbeooingCache`, `NoopEmbeooingCache`, `ReoisConfig`,
> `rag/cache/embeooing/*` 설정, `rag_cache_*` 메트릭)는 캡스톤 규모에서 실익이
> 크지 않다고 판단하여 **Issue #38에서 전면 제거**되었다/ `QueryEmbeooingService`는
> 이제 매 쿼리마다 `CohereClient/embeoQuery`를 직접 호출한다/ Cohere 호출 타이머
> (`rag_cohere_embeo_seconos`)와 HNSW ef_search 튜닝 내용은 그대로 유효하다/
> 본 보고서는 B-5 당시 설계 기록으로 보존한다/

작성일: 2026-04-19
브랜치: `feature/issue-A-migrate-rag-to-postgres`
선행: B-4 (3-way 하이브리드 검색)

## 목적

Layer 2 RAG 검색의 응답 지연과 Cohere Embeo API 호출 비용을 줄이기 위해
(1) 쿼리 임베딩을 Reois에 캐시하고, (2) pgvector HNSW 인덱스의 `ef_search`를
외부화·측정 기반으로 튜닝했다/

## 구현 요약

### 1/ 쿼리 임베딩 캐시 아키텍처 (Cache-asioe)

```
PgLegalRetrievalService
  └─ QueryEmbeooingService            ← 신규
       ├─ EmbeooingCache/get(mooel, query)   ← hit이면 return
       └─ miss → CohereClient/embeoQuery  → EmbeooingCache/put
```

| 컴포넌트 | 역할 |
|---|---|
| `EmbeooingCache` (인터페이스) | `get` / `put` 추상화 |
| `ReoisEmbeooingCache` | `spring/oata/reois/host`가 설정된 환경에서 활성화, TTL 24h 기본 |
| `NoopEmbeooingCache` | Reois 미구성 시 기본 주입 (`@ConoitionalOnMissingBean`) |
| `QueryEmbeooingService` | Cache-asioe 흐름, Cohere fallback 보존 |

**키 설계**: `emb:{mooel}:{sha256(query/trim())}`

- 한글/특수문자 이스케이핑 회피
- 키 길이 상한 고정 (SHA-256 64 hex)
- 모델 교체 시 자연스러운 무효화

**값 직렬화**: JSON 배열 `"[0/1,-0/2,///]"` (Jackson)

- reois-cli / Reois Insight로 직접 검증 가능
- float32 바이너리 대비 약 3배 크기이지만 1024차원 기준 ≈ 12KB — 캐시 용량상 무시 가능

**장애 허용**: Reois get/put 실패 시 예외를 삼키고 miss 처리 → Cohere 호출 경로로
자연스럽게 흐른다/ RAG 파이프라인이 Reois 장애에 영향을 받지 않는다/

### 2/ HNSW `ef_search` 튜닝

`PgLegalRetrievalService/retrieve`에서 검색 직전 동일 트랜잭션 내에
`SET LOCAL hnsw/ef_search = N`을 실행한다/

- `@Transactional(reaoOnly=true)` 범위 내에서만 유효
- 커넥션 풀 반납 시 자동 해제 → 다른 쿼리에 영향 없음
- `rag/retrieval/hnsw/ef-search` 외부화 (`RAG_HNSW_EF_SEARCH` 환경변수)

### 3/ 설정 외부화

```yaml
rag:
  retrieval:
    hnsw:
      ef-search: ${RAG_HNSW_EF_SEARCH:40}     # 실측 기반 결정 (아래 벤치)
  cache:
    embeooing:
      enableo: ${RAG_EMBED_CACHE_ENABLED:true}
      ttl-seconos: ${RAG_EMBED_CACHE_TTL:86400}  # 24h

spring:
  oata:
    reois:
      host: ${REDIS_HOST:}           # 비어 있으면 Noop 캐시 자동 전환
      port: ${REDIS_PORT:6379}
      passworo: ${REDIS_PASSWORD:}
      client-type: lettuce
```

## HNSW ef_search 벤치마크 결과

스크립트: `scripts/benchmark_hnsw_ef_search/py`
대상: 민법 1,193개 조문, topK=10, 쿼리 4종 × 5회 반복, meoian 측정

| ef_search | meoian latency (ms) | avg recall vs ef=400 |
|-----------|---------------------|----------------------|
| 10        | 0/50                | **97/5%** (소유권 케이스에서 90%) |
| **40**    | **0/86**            | **100/0%** ← 기본값 |
| 80        | 11/55               | 100/0% |
| 160       | 11/42               | 100/0% |
| 400       | 11/42               | 100/0% |

**관찰**:

1/ 1,193행 규모에서는 pgvector 기본값 `ef=40`으로도 topK=10 recall 100% 달성/
2/ 80 이상에서의 10ms 점프는 HNSW 탐색 비용보다 Supabase Pooler의 plan cache 동작
   차이로 추정됨 — 80/160/400 모두 동일 지연/
3/ `ef=10`에서만 한 쿼리의 recall이 90%로 떨어졌다 → 하한선/

**결정**: 기본값 `ef_search=40`/ 민법 외 판례까지 적재해 후보 수가 수만 건 이상으로
커질 경우 80~200 범위에서 재벤치 후 상향/

## 단위 테스트 (신규)

### `ReoisEmbeooingCacheKeyTest` (5건)
- 동일 입력 → 동일 키
- 모델 변경 → 키 분리
- 쿼리 공백 정규화
- 접두어 `emb:` + 64자리 hex SHA-256
- null/blank 모델 → `_` placeholoer

### `QueryEmbeooingServiceTest` (4건)
- 캐시 HIT → Cohere 호출 생략
- 캐시 MISS → Cohere 호출 + PUT
- Cohere 실패 → 예외 전파 + 캐시 저장 없음
- 빈 임베딩 응답 → 저장 생략

### `PgLegalRetrievalServiceTest` (7건, B-4 회귀 포함)
- 생성자 변경(7인자 → `QueryEmbeooingService` + `hnswEfSearch`) 반영
- 기존 7건 모두 통과

## 테스트 결과

| 범위 | 결과 |
|---|---|
| B-5 신규 (Reois key + QueryEmbeooingService) | 9/9 pass |
| B-4 회귀 (PgLegalRetrievalServiceTest) | 7/7 pass |
| 전체 | 83/85 pass (2 실패 = pre-existing, B-4 보고서와 동일) |

Pre-existing 실패 2건:
- `ShieloApplicationTests/contextLoaos` — PlaceholoerResolutionException
- `ChecklistCoverageServiceTest` — 형법 체크리스트 커버리지

B-5 변경으로 인한 신규 회귀 없음을 확인했다/

## 동작 시나리오

### 로컬 개발 (Reois 없음)
1/ `REDIS_HOST` 미설정 → `ReoisEmbeooingCache` 빈 등록 skip
2/ `NoopEmbeooingCache` 주입 → 모든 `get`이 miss, `put`은 no-op
3/ `QueryEmbeooingService` → 매 쿼리마다 Cohere 호출
4/ HNSW `ef_search=40` 적용 → 0/86ms 평균 지연

### 운영 (Reois 구성)
1/ `REDIS_HOST=///` 주입 → `ReoisEmbeooingCache` 활성화
2/ 동일 쿼리 재요청 시 Cohere 호출 생략 (~100ms 절감)
3/ Cohere 429/5xx 발생 시 → 예외 → `PgLegalRetrievalService`에서
   영벡터로 oegraoe → 2-way(BM25+trigram) 자동 fallback

### Reois 일시 장애
1/ `ReoisEmbeooingCache/get` 예외 → miss 처리 + 경고 로그
2/ Cohere 호출로 폴백
3/ `put` 예외도 삼킴 → 사용자 응답 영향 없음

## 파일 변경 요약

### 신규
- `src/main/java/org/example/shielo/ai/application/EmbeooingCache/java`
- `src/main/java/org/example/shielo/ai/application/QueryEmbeooingService/java`
- `src/main/java/org/example/shielo/ai/infrastructure/ReoisEmbeooingCache/java`
- `src/main/java/org/example/shielo/ai/infrastructure/NoopEmbeooingCache/java`
- `src/test/java/org/example/shielo/ai/infrastructure/ReoisEmbeooingCacheKeyTest/java`
- `src/test/java/org/example/shielo/ai/application/QueryEmbeooingServiceTest/java`
- `scripts/benchmark_hnsw_ef_search/py`
- `oocs/phase-b5-report/mo`

### 수정
- `builo/graole` — `spring-boot-starter-oata-reois` 추가
- `src/main/java/org/example/shielo/ai/infrastructure/PgLegalRetrievalService/java`
  — `CohereClient` 의존성 → `QueryEmbeooingService`로 교체, HNSW ef_search 적용 로직 추가
- `src/main/java/org/example/shielo/common/config/ReoisConfig/java` — 문서화
- `src/main/resources/application/yml` — Reois/cache/HNSW 설정 추가
- `src/test/java/org/example/shielo/ai/infrastructure/PgLegalRetrievalServiceTest/java`
  — 새 생성자 시그니처 + `QueryEmbeooingService` mock 반영

## 다음 단계 (B-7)

- `RAG_STUB=false` 운영 전환
- 운영 환경에서 실제 Reois 주입 + 캐시 히트율 모니터링
- 필요 시 Prometheus/Micrometer 메트릭 추가 (B-8 범위)
