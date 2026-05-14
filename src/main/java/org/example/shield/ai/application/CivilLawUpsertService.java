package org.example.shield.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.ai.domain.LegalChunkEntity;
import org.example.shield.ai.domain.LegalChunkJpaRepository;
import org.example.shield.ai.dto.CivilLawSeed;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * 민법 인제스트 upsert 트랜잭션 경계.
 *
 * <p>Spring AOP {@code @Transactional}은 같은 클래스 내 self-invocation 시 프록시가
 * 적용되지 않아 트랜잭션이 열리지 않는다. 이를 회피하기 위해 {@link CivilLawIngestService}
 * 와 분리된 빈으로 만들어 의존성 주입으로 호출한다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CivilLawUpsertService {

    private final LegalChunkJpaRepository chunkRepository;

    /**
     * 배치 단위 upsert.
     *
     * @param batch       원본 조문 리스트
     * @param embeddings  조문별 임베딩 벡터 (batch와 동일 순서)
     * @param embedModel  임베딩 모델 ID ("embed-v4.0")
     * @param entityMaker Article → LegalChunkEntity 변환 함수 (카테고리 매핑 등은 호출자 책임)
     * @return upsert 처리된 청크 수
     */
    @Transactional
    public int upsertBatch(List<CivilLawSeed.Article> batch,
                           List<float[]> embeddings,
                           String embedModel,
                           EntityMaker entityMaker) {
        int count = 0;
        Short chunkIndex = (short) 0;
        for (int i = 0; i < batch.size(); i++) {
            CivilLawSeed.Article a = batch.get(i);
            float[] vec = embeddings.get(i);

            Optional<LegalChunkEntity> existing = chunkRepository.findActiveByNaturalKey(
                    a.lawId(), a.articleNo(), chunkIndex);
            if (existing.isPresent()) {
                // 시드 본문·메타데이터까지 동기화 — 임베딩만 갱신하면 content 변경분이
                // DB 에 반영되지 않는다 (Gemini PR #90 ②). entityMaker 가 categoryIds /
                // lodUri / legislationTerms 등 카테고리 매핑까지 처리하므로 fresh entity
                // 의 값을 그대로 사용해 일관성을 유지한다.
                LegalChunkEntity fresh = entityMaker.apply(a, vec);
                LegalChunkEntity e = existing.get();
                e.updateContent(
                        fresh.getLawName(),
                        fresh.getArticleTitle(),
                        fresh.getContent(),
                        fresh.getEffectiveDate(),
                        fresh.getAbolitionDate(),
                        fresh.getSourceUrl(),
                        fresh.getCategoryIds(),
                        fresh.getLodUri(),
                        fresh.getLegislationTerms());
                e.updateEmbedding(vec, embedModel);
                count++;
            } else {
                LegalChunkEntity e = entityMaker.apply(a, vec);
                chunkRepository.save(e);
                count++;
            }
        }
        return count;
    }

    /**
     * Article + 임베딩 벡터 → LegalChunkEntity 변환 함수 인터페이스.
     * chunkIndex/embeddingModel 등 공통 값은 호출자가 closure에 캡처한다.
     */
    @FunctionalInterface
    public interface EntityMaker extends BiFunction<CivilLawSeed.Article, float[], LegalChunkEntity> {
    }
}
