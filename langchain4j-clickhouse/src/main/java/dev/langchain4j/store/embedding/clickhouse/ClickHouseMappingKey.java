package dev.langchain4j.store.embedding.clickhouse;

import java.util.Arrays;
import java.util.List;

class ClickHouseMappingKey {

    static final String DISTANCE_COLUMN_NAME = "dist";

    static final String ID_MAPPING_KEY = "id";

    static final String TEXT_MAPPING_KEY = "text";

    static final String EMBEDDING_MAPPING_KEY = "embedding";

    static final List<String> REQUIRED_COLUMN_MAP_KEYS = Arrays.asList(ID_MAPPING_KEY, TEXT_MAPPING_KEY, EMBEDDING_MAPPING_KEY);
}
