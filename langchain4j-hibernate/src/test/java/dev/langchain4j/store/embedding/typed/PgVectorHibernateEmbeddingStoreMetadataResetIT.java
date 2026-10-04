package dev.langchain4j.store.embedding.typed;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.EmbeddingVector;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.MetadataAttribute;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Array;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.SourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests that {@code addAll} resets the right state array slots between entries when the entity has generated
 * identifiers. The array is allocated once and reused for every entity, so each iteration has to clear the slots the
 * previous iteration wrote, and it has to clear the entity's own slots rather than the ones declared by an embeddable.
 * <p>
 * The attribute names of {@link ProbeDocument} and {@link ProbeSource} are chosen so that Hibernate orders
 * {@code embedding} first in the entity state array and {@code name} first in the state array of the embeddable, which
 * is what makes an index mix-up between the two arrays observable.
 */
@Testcontainers
class PgVectorHibernateEmbeddingStoreMetadataResetIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    static SessionFactory sessionFactory;

    private HibernateEmbeddingStore<ProbeDocument> embeddingStore;

    @Entity(name = "ProbeDocument")
    public static class ProbeDocument {
        @Id
        @GeneratedValue
        Long id;

        @EmbeddingVector
        @Array(length = 3)
        float[] embedding;

        @MetadataAttribute
        String language;

        @UnmappedMetadata
        String metadata;

        @MetadataAttribute
        @Embedded
        ProbeSource source;

        @EmbeddedText
        String text;
    }

    @Embeddable
    public static class ProbeSource {
        @MetadataAttribute
        String name;

        String uri;
    }

    @Test
    void should_keep_metadata_of_an_entry_that_has_a_text_segment() {
        Embedding embedding = new Embedding(new float[] {1.0f, 2.0f, 3.0f});
        TextSegment segment = TextSegment.from("hello", Metadata.from(Map.of("language", "en")));

        List<String> ids = embeddingStore.addAll(List.of(embedding), List.of(segment));

        ProbeDocument stored = find(ids.get(0));
        assertThat(stored.embedding).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(stored.text).isEqualTo("hello");
        assertThat(stored.language).isEqualTo("en");
    }

    @Test
    void should_keep_the_embedding_vector_of_an_entry_without_a_text_segment() {
        Embedding embedding = new Embedding(new float[] {1.0f, 2.0f, 3.0f});

        List<String> ids = embeddingStore.addAll(List.of(embedding), null);

        assertThat(find(ids.get(0)).embedding)
                .as("addAll(List<Embedding>) must persist the embedding vector")
                .containsExactly(1.0f, 2.0f, 3.0f);
    }

    @Test
    void should_not_leak_metadata_of_the_previous_entry_into_an_entry_without_a_text_segment() {
        Embedding first = new Embedding(new float[] {1.0f, 2.0f, 3.0f});
        Embedding second = new Embedding(new float[] {4.0f, 5.0f, 6.0f});
        TextSegment segment = TextSegment.from("hello", Metadata.from(Map.of("language", "en")));

        List<String> ids = embeddingStore.addAll(Arrays.asList(first, second), Arrays.asList(segment, null));

        ProbeDocument stored = find(ids.get(1));
        assertThat(stored.language)
                .as("metadata of the previous entry must not leak into an entry without a TextSegment")
                .isNull();
        assertThat(stored.text).isNull();
        assertThat(stored.metadata).isNull();
        assertThat(stored.embedding).containsExactly(4.0f, 5.0f, 6.0f);
    }

    private static ProbeDocument find(String id) {
        return sessionFactory.fromStatelessTransaction(session -> session.get(ProbeDocument.class, Long.valueOf(id)));
    }

    @BeforeAll
    static void setup() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(ProbeDocument.class)
                .setJdbcUrl(pgVector.getJdbcUrl())
                .setCredentials("test", "test")
                .setSchemaExportAction(Action.CREATE_DROP)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE, SourceType.SCRIPT_THEN_METADATA)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE, "/setup.sql")
                .buildSessionFactory();
    }

    @BeforeEach
    void beforeEach() {
        embeddingStore = HibernateEmbeddingStore.builder(ProbeDocument.class)
                .sessionFactory(sessionFactory)
                .build();
        sessionFactory.inStatelessTransaction(session ->
                session.createMutationQuery("delete from ProbeDocument").executeUpdate());
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
