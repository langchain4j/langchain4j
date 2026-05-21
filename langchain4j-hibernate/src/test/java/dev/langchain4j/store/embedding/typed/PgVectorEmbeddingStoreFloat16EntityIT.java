package dev.langchain4j.store.embedding.typed;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.util.List;
import java.util.UUID;
import org.assertj.core.data.Percentage;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.SourceType;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingStoreFloat16EntityIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    static EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    static SessionFactory sessionFactory;

    private HibernateEmbeddingStore<Float16Entity> embeddingStore;

    @Entity(name = "Float16Entity")
    public static class Float16Entity {
        @Id
        @GeneratedValue
        private UUID id;

        @EmbeddedText
        private String text;

        @dev.langchain4j.store.embedding.hibernate.Embedding
        @JdbcTypeCode(SqlTypes.VECTOR_FLOAT16)
        @Array(length = 384)
        private float[] embedding;

        @UnmappedMetadata
        private String metadata;
    }

    @Test
    protected void test_float16_works() {

        // given
        Embedding embedding = embeddingModel.embed("hello").content();
        String id = embeddingStore.add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .query("hello")
                .maxResults(10)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // then
        assertThat(id).isNotBlank();

        assertThat(searchResult.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertScore(match, 1);
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedded()).isNull();
    }

    protected List<EmbeddingMatch<TextSegment>> getAllEmbeddings() {
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("test").content())
                .query("test")
                .maxResults(1000)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(embeddingSearchRequest);

        return searchResult.matches();
    }

    protected Percentage percentage() {
        return withPercentage(1);
    }

    protected void assertScore(EmbeddingMatch<TextSegment> match, double expectedScore) {
        assertThat(match.score()).isCloseTo(expectedScore, percentage());
    }

    @BeforeAll
    static void setup() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(Float16Entity.class)
                .setJdbcUrl(pgVector.getJdbcUrl())
                .setCredentials("test", "test")
                .setSchemaExportAction(Action.CREATE_DROP)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE, SourceType.SCRIPT_THEN_METADATA)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE, "/setup.sql")
                .showSql(true, true, true)
                .buildSessionFactory();
    }

    @BeforeEach
    void beforeEach() {
        embeddingStore = HibernateEmbeddingStore.builder(Float16Entity.class)
                .sessionFactory(sessionFactory)
                .build();
    }

    @AfterEach
    void afterEach() {
        embeddingStore = null;
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}
