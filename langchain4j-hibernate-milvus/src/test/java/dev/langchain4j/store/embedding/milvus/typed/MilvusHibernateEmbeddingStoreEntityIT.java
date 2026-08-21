package dev.langchain4j.store.embedding.milvus.typed;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.milvus.MilvusDatabaseKind;
import dev.langchain4j.store.embedding.typed.GenericEmbeddingEntity;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.schema.Action;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

@Testcontainers
class MilvusHibernateEmbeddingStoreEntityIT extends EmbeddingStoreWithFilteringIT {

    static {
        // Register the driver class to allow resolving from the JDBC URL
        try {
            DriverManager.registerDriver(new org.hibernate.milvus.jdbc.internal.MilvusDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Container
    static MilvusContainer databaseContainer =
            new MilvusContainer("milvusdb/milvus:v2.6.22").withEnv("DEPLOY_MODE", "STANDALONE");

    final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    static SessionFactory sessionFactory;
    EmbeddingStore<TextSegment> embeddingStore;

    @BeforeAll
    static void setup() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(GenericEmbeddingEntity.class)
                .setJdbcUrl("jdbc:milvus://" + databaseContainer.getHost() + ":"
                        + databaseContainer.getMappedPort(19530) + "/default")
                .setCredentials("sa", "")
                .setSchemaExportAction(Action.CREATE_DROP)
                .buildSessionFactory();
    }

    @Override
    protected void ensureStoreIsReady() {
        embeddingStore = HibernateEmbeddingStore.builder(GenericEmbeddingEntity.class)
                .sessionFactory(sessionFactory)
                .databaseKind(MilvusDatabaseKind.INSTANCE)
                .build();
    }

    @AfterEach
    void clearData() {
        embeddingStore = null;
        if (sessionFactory != null) {
            sessionFactory.getSchemaManager().truncate();
        }
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @Test
    void test_escape_in() {
        TextSegment[] segments = new TextSegment[] {
            TextSegment.from("toEscape", Metadata.from(Map.of("text", "This must be escaped '"))),
            TextSegment.from("notEscape", Metadata.from(Map.of("text", "This does not require to be escaped")))
        };
        List<Embedding> embeddings = new ArrayList<>(segments.length);
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddings.add(embedding);
        }

        List<String> ids = embeddingStore().addAll(embeddings, Arrays.asList(segments));
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSameSizeAs(segments));

        // In filter escapes values as well
        Filter filterIN = metadataKey("text").isIn("This must be escaped '");
        EmbeddingSearchRequest inSearchRequest = EmbeddingSearchRequest.builder()
                .maxResults(1)
                .queryEmbedding(embeddings.get(0))
                .filter(filterIN)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(inSearchRequest);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(ids.get(0));

        // In filter escapes values as well
        Filter filterNotIN = metadataKey("text").isNotIn("This must be escaped '");
        EmbeddingSearchRequest notInSearchRequest = EmbeddingSearchRequest.builder()
                .maxResults(1)
                .queryEmbedding(embeddings.get(0))
                .filter(filterNotIN)
                .build();

        searchResult = embeddingStore().search(notInSearchRequest);
        match = searchResult.matches().get(0);
        // It must retrieve the second embedding
        assertThat(match.embeddingId()).isEqualTo(ids.get(1));
    }

    @Test
    @Disabled(
            "The test tries to assign an id explicitly, but the entity uses a generator that doesn't allow assignment")
    @Override
    protected void should_add_embedding_with_id() {
        super.should_add_embedding_with_id();
    }

    @Test
    @Disabled(
            "The test tries to assign an id explicitly, but the entity uses a generator that doesn't allow assignment")
    @Override
    protected void should_add_multiple_embeddings_with_ids_and_segments() {
        super.should_add_multiple_embeddings_with_ids_and_segments();
    }
}
