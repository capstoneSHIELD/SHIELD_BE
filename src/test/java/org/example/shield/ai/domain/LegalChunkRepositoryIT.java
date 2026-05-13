package org.example.shield.ai.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * legal_chunks 네이티브 trigram 쿼리 통합 테스트 — pg_trgm % 연산자 회귀 가드.
 *
 * <p>커밋 {@code 812bcf4} ([FIX] RAG trigram SQL 에러 — pg_trgm % 연산자 escape 제거 + CAST 추가)에서
 * {@code lc.content %% :vectorQuery} → {@code lc.content % CAST(:vectorQuery AS text)} 로 수정한
 * 변경이 다시 회귀하지 않도록 보호한다. 운영(api.shieldai.kr)에서 발생했던 정확한 에러
 * {@code operator does not exist: text %% unknown} 가 재현되면 본 테스트가 fail 한다.
 *
 * <p>운영 datasource 의 {@code stringtype=unspecified} 옵션도 함께 적용해 unknown 타입 바인딩 경로를
 * 동일하게 재현한다. 컨테이너 이미지는 {@code pgvector/pgvector:pg16} — pg_trgm 확장도 포함되어 있다.
 *
 * <p>실행 전제: 로컬/CI 에 Docker 데몬이 떠있어야 한다. 없으면 컨테이너 시작 단계에서 skip 처리되지
 * 않고 fail 하므로 Docker 미설치 환경에서는 {@code -PexcludeIT} 같은 별도 Gradle 옵션이 필요하다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LegalChunkRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        // 운영과 동일한 stringtype=unspecified 를 붙여 trigram % 의 unknown 타입 바인딩까지 재현
        r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?stringtype=unspecified");
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        r.add("spring.flyway.user", POSTGRES::getUsername);
        r.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired LegalChunkJpaRepository repository;
    @Autowired DataSource dataSource;

    @Test
    @DisplayName("searchHybrid — trigram `% CAST(:vectorQuery AS text)` 쿼리가 SQL 에러 없이 실행 (#812bcf4 회귀 가드)")
    void searchHybrid_trigramOperator_doesNotThrowOperatorDoesNotExist() {
        // given — 보고된 시나리오(보증금 반환)와 유사한 청크 1행 삽입.
        //         created_at/updated_at 은 V3 의 DEFAULT now() 가 채우므로 INSERT 컬럼에서 생략.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO legal_chunks (law_id, law_name, article_no, chunk_index,
                                          article_title, content)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "test-law-1", "테스트법", "1", (short) 0, "보증금 반환",
                "임대차계약 종료 후 임대인은 임차인에게 보증금을 반환해야 한다.");

        // when/then — 운영에서 500 을 유발한 정확한 쿼리 경로가 SQL 에러 없이 통과해야 한다.
        assertThatCode(() -> {
            List<LegalChunkJpaRepository.LegalChunkRow> rows = repository.searchHybrid(
                    "보증금 반환",        // vectorQuery — trigram `%` 연산자 입력
                    "보증금 | 반환",      // keywordQuery — to_tsquery OR 입력
                    0.5, 0.3, 0.2,
                    10
            );
            // BM25 또는 trigram 둘 중 하나는 hit 해야 한다 (점수 정렬은 본 테스트의 관심 아님).
            assertThat(rows).isNotEmpty();
        }).doesNotThrowAnyException();
    }
}
