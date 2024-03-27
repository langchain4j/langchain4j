package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;

@Testcontainers
public class PgVectorEmbeddingStoreWithColumnsFilteringIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void beforeEach() {
        embeddingStore = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table("test")
                .dimension(384)
                .dropTableFirst(true)
                .metadataType("COLUMNS")
                .metadataDefinition(
                        Arrays.asList("key text NULL", "name text NULL", "age float NULL", "city varchar null", "country varchar null",
                        "string_empty varchar null", "string_space varchar null", "string_abc varchar null",
                        "integer_min int null", "integer_minus_1 int null", "integer_0 int null", "integer_1 int null", "integer_max int null",
                        "long_min bigint null", "long_minus_1 bigint null", "long_0 bigint null", "long_1 bigint null", "long_max bigint null",
                        "float_min float null", "float_minus_1 float null", "float_0 float null", "float_1 float null", "float_123 float null", "float_max float null",
                        "double_minus_1 float8 null", "double_0 float8 null", "double_1 float8 null", "double_123 float8 null"
                        ))
                .metadataIndexes(Arrays.asList("key", "name", "age"))
                .build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
