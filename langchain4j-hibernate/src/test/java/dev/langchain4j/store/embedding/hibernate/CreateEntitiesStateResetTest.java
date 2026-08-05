package dev.langchain4j.store.embedding.hibernate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the state array {@code HibernateEmbeddingStore.createEntities} builds for entities with generated
 * identifiers. The array is allocated once and reused for every entity, so each iteration has to reset the slots the
 * previous iteration wrote. These tests run without a live database: the {@link SessionFactory} is bootstrapped with an
 * explicit dialect and no JDBC metadata access, and {@code createEntities} is invoked through reflection.
 */
class CreateEntitiesStateResetTest {

    private static SessionFactory sessionFactory;
    private static HibernateEmbeddingStore<ProbeDocument> store;

    @BeforeAll
    static void bootSessionFactory() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(ProbeDocument.class)
                .addAnnotatedClass(ProbeSource.class)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .buildSessionFactory();
        store = HibernateEmbeddingStore.builder(ProbeDocument.class)
                .sessionFactory(sessionFactory)
                .databaseKind(DatabaseKind.POSTGRESQL)
                .build();
    }

    @AfterAll
    static void closeSessionFactory() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ProbeDocument> createEntities(List<Embedding> embeddings, List<TextSegment> embedded)
            throws Exception {
        Method createEntities =
                HibernateEmbeddingStore.class.getDeclaredMethod("createEntities", List.class, List.class);
        createEntities.setAccessible(true);
        return (List<ProbeDocument>) createEntities.invoke(store, embeddings, embedded);
    }

    @Test
    void keeps_metadata_of_an_entry_that_has_a_text_segment() throws Exception {
        Embedding embedding = new Embedding(new float[] {1.0f, 2.0f, 3.0f});
        TextSegment segment = TextSegment.from("hello", Metadata.from(Map.of("language", "en")));

        List<ProbeDocument> entities = createEntities(List.of(embedding), List.of(segment));

        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).embedding).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(entities.get(0).text).isEqualTo("hello");
        assertThat(entities.get(0).language).isEqualTo("en");
    }

    @Test
    void keeps_the_embedding_vector_of_an_entry_without_a_text_segment() throws Exception {
        Embedding embedding = new Embedding(new float[] {1.0f, 2.0f, 3.0f});

        List<ProbeDocument> entities = createEntities(List.of(embedding), null);

        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).embedding)
                .as("addAll(List<Embedding>) must persist the embedding vector")
                .containsExactly(1.0f, 2.0f, 3.0f);
    }

    @Test
    void does_not_leak_metadata_of_the_previous_entry_into_an_entry_without_a_text_segment() throws Exception {
        Embedding first = new Embedding(new float[] {1.0f, 2.0f, 3.0f});
        Embedding second = new Embedding(new float[] {4.0f, 5.0f, 6.0f});
        TextSegment segment = TextSegment.from("hello", Metadata.from(Map.of("language", "en")));

        List<ProbeDocument> entities = createEntities(Arrays.asList(first, second), Arrays.asList(segment, null));

        assertThat(entities).hasSize(2);
        assertThat(entities.get(1).language)
                .as("metadata of the previous entry must not leak into an entry without a TextSegment")
                .isNull();
        assertThat(entities.get(1).text).isNull();
        assertThat(entities.get(1).metadata).isNull();
        assertThat(entities.get(1).embedding).containsExactly(4.0f, 5.0f, 6.0f);
    }

    /**
     * The attribute names are chosen so that Hibernate orders {@code embedding} first in the entity state array and
     * {@code name} first in the state array of the embeddable, which is what makes the index mix-up observable.
     */
    @Entity(name = "ProbeDocument")
    static class ProbeDocument {
        @Id
        @GeneratedValue
        Long id;

        @EmbeddingVector
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
    static class ProbeSource {
        @MetadataAttribute
        String name;

        String uri;
    }
}
