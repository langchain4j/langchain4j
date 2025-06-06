package dev.langchain4j.store.embedding.mariadb;

import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;

class MariaDbEmbeddingStoreWithColumnsFilteringIT extends MariaDbEmbeddingStoreConfigIT {

    @BeforeAll
    static void beforeAllTests() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Arrays.asList(
                        "key varchar(255)",
                        "`name` varchar(255)",
                        "`age` float",
                        "city text",
                        "country varchar(255)",
                        "string_empty varchar(255)",
                        "string_space varchar(255)",
                        "string_abc varchar(255)",
                        "uuid uuid",
                        "integer_min int",
                        "integer_minus_1 int",
                        "integer_0 int",
                        "integer_1 int",
                        "integer_max int",
                        "long_min bigint",
                        "long_minus_1 bigint",
                        "long_0 bigint",
                        "long_1 bigint",
                        "long_1746714878034235396 bigint",
                        "long_max bigint",
                        "float_min double",
                        "float_minus_1 double",
                        "float_0 double",
                        "float_1 double",
                        "float_123 double",
                        "float_max double",
                        "double_minus_1 double",
                        "double_0 double",
                        "double_1 double",
                        "double_123 double"))
                .indexes(Arrays.asList("key", "name", "age"))
                .build();
        configureStore(config);
    }
}
