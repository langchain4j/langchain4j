package dev.langchain4j.store.embedding.pgvector;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;

@Testcontainers
public class PgVectorEmbeddingStoreWithColumnsFilteringIT extends PgVectorEmbeddingStoreConfigIT {

    @BeforeAll
    static void beforeAll() {
        PgVectorEmbeddingStoreConfigIT.beforeAll();
        embeddingStore = DataSourcePgVectorEmbeddingStore.withDataSourceBuilder()
                .datasource(dataSource)
                .table(TABLE_NAME)
                .dimension(TABLE_DIMENSION)
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
}
