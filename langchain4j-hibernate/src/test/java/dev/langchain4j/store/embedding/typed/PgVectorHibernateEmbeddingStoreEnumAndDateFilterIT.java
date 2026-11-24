package dev.langchain4j.store.embedding.typed;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.typed.ArticleEntity.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.SourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Enum and date/time attributes can be filtered by the value itself, or by its text form,
// which the store converts to the attribute type.
@Testcontainers
class PgVectorHibernateEmbeddingStoreEnumAndDateFilterIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    static final float[] VECTOR = {1, 0, 0};

    static SessionFactory sessionFactory;

    private HibernateEmbeddingStore<ArticleEntity> embeddingStore;

    @Test
    void should_filter_enum_attribute_by_name() {
        assertThat(ids(metadataKey("status").isEqualTo(Status.PUBLISHED.name())))
                .containsExactly("1", "2");
        assertThat(ids(metadataKey("status").isNotEqualTo(Status.PUBLISHED.name())))
                .containsExactly("3", "4");
        assertThat(ids(metadataKey("status").isIn(Status.DRAFT.name(), Status.ARCHIVED.name())))
                .containsExactly("3", "4");
        assertThat(ids(metadataKey("status").isNotIn(Status.DRAFT.name(), Status.ARCHIVED.name())))
                .containsExactly("1", "2");
    }

    @Test
    void should_filter_local_date_attribute_by_iso_text() {
        assertThat(ids(metadataKey("publishedOn").isGreaterThan("2024-01-01"))).containsExactly("2", "3");
        assertThat(ids(metadataKey("publishedOn").isLessThanOrEqualTo(LocalDate.of(2023, 6, 1).toString())))
                .containsExactly("1", "4");
        assertThat(ids(metadataKey("publishedOn").isEqualTo("2024-05-20"))).containsExactly("3");
    }

    @Test
    void should_filter_instant_attribute_by_iso_text() {
        assertThat(ids(metadataKey("updatedAt")
                        .isGreaterThanOrEqualTo(
                                Instant.parse("2024-03-15T10:00:00Z").toString())))
                .containsExactly("2", "3");
        assertThat(ids(metadataKey("updatedAt").isLessThan("2023-01-01T00:00:00Z")))
                .containsExactly("4");
    }

    @Test
    void should_combine_text_form_filters() {
        Filter filter = metadataKey("status")
                .isEqualTo(Status.PUBLISHED.name())
                .and(metadataKey("publishedOn").isGreaterThan("2024-01-01"));

        assertThat(ids(filter)).containsExactly("2");
    }

    @Test
    void should_filter_enum_attribute_by_value() {
        assertThat(ids(new IsEqualTo("status", Status.PUBLISHED))).containsExactly("1", "2");
        assertThat(ids(metadataKey("status").isIn(List.of(Status.DRAFT, Status.ARCHIVED))))
                .containsExactly("3", "4");
    }

    @Test
    void should_filter_date_and_time_attributes_by_value() {
        assertThat(ids(new IsGreaterThan("publishedOn", LocalDate.of(2024, 1, 1)))).containsExactly("2", "3");
        assertThat(ids(new IsLessThan("updatedAt", Instant.parse("2023-01-01T00:00:00Z")))).containsExactly("4");
    }

    private List<String> ids(Filter filter) {
        return embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(VECTOR))
                        .filter(filter)
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .matches()
                .stream()
                .map(EmbeddingMatch::embeddingId)
                .sorted()
                .toList();
    }

    @BeforeAll
    static void setup() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(ArticleEntity.class)
                .setJdbcUrl(pgVector.getJdbcUrl())
                .setCredentials("test", "test")
                .setSchemaExportAction(Action.CREATE_DROP)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE, SourceType.SCRIPT_THEN_METADATA)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE, "/setup.sql")
                .buildSessionFactory();
        sessionFactory.inStatelessTransaction(session -> session.insertMultiple(List.of(
                article(1L, Status.PUBLISHED, "2023-06-01", "2023-06-01T10:00:00Z"),
                article(2L, Status.PUBLISHED, "2024-03-15", "2024-03-15T10:00:00Z"),
                article(3L, Status.DRAFT, "2024-05-20", "2024-05-20T10:00:00Z"),
                article(4L, Status.ARCHIVED, "2022-01-10", "2022-01-10T10:00:00Z"))));
    }

    private static ArticleEntity article(Long id, Status status, String publishedOn, String updatedAt) {
        ArticleEntity article = new ArticleEntity();
        article.setId(id);
        article.setStatus(status);
        article.setPublishedOn(LocalDate.parse(publishedOn));
        article.setUpdatedAt(Instant.parse(updatedAt));
        article.setEmbedding(VECTOR);
        return article;
    }

    @BeforeEach
    void beforeEach() {
        embeddingStore = HibernateEmbeddingStore.builder(ArticleEntity.class)
                .sessionFactory(sessionFactory)
                .build();
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}
