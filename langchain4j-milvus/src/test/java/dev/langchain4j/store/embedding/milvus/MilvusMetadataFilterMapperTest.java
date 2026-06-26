package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

class MilvusMetadataFilterMapperTest {

    @Test
    void should_escape_backslash_in_string_value() {
        Filter filter = metadataKey("key").isEqualTo("a\\b");

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"key\"] == \"a\\\\b\"");
    }

    @Test
    void should_escape_both_backslash_and_double_quote_in_string_value() {
        Filter filter = metadataKey("key").isEqualTo("a\\b\"c");

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"key\"] == \"a\\\\b\\\"c\"");
    }

    @Test
    void should_escape_backslash_in_collection_values() {
        Filter filter = metadataKey("key").isIn("a\\b");

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"key\"] in [\"a\\\\b\"]");
    }

    @Test
    void should_not_change_value_without_special_characters() {
        Filter filter = metadataKey("key").isEqualTo("foo");

        String expr = MilvusMetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"key\"] == \"foo\"");
    }
}
