package dev.langchain4j.store.embedding.milvus.v2;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

class MilvusV2MetadataFilterMapperTest {

    @Test
    void should_escape_backslash_in_string_value() {
        Filter filter = metadataKey("key").isEqualTo("a\\b");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"key\"] == \"a\\\\b\"");
    }

    @Test
    void should_escape_both_backslash_and_double_quote_in_string_value() {
        Filter filter = metadataKey("key").isEqualTo("a\\b\"c");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"key\"] == \"a\\\\b\\\"c\"");
    }

    @Test
    void should_escape_backslash_in_collection_values() {
        Filter filter = metadataKey("key").isIn("a\\b");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"key\"] in [\"a\\\\b\"]");
    }

    @Test
    void should_not_change_value_without_special_characters() {
        Filter filter = metadataKey("key").isEqualTo("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"key\"] == \"foo\"");
    }

    @Test
    void should_escape_double_quote_in_key() {
        // An unescaped key would break out of the metadata["..."] accessor and inject
        // arbitrary Milvus filter expression syntax (here: an "or" term the caller never wrote).
        Filter filter = metadataKey("tenant\"] != \"\" or metadata[\"x").isEqualTo("acme");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"tenant\\\"] != \\\"\\\" or metadata[\\\"x\"] == \"acme\"");
    }

    @Test
    void should_escape_backslash_in_key() {
        Filter filter = metadataKey("a\\b").isEqualTo("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"a\\\\b\"] == \"foo\"");
    }

    @Test
    void should_escape_key_in_collection_filter() {
        Filter filter = metadataKey("a\"b").isIn("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"a\\\"b\"] in [\"foo\"]");
    }

    @Test
    void should_escape_key_in_contains_filter() {
        Filter filter = metadataKey("a\"b").containsString("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"a\\\"b\"] LIKE \"%foo%\"");
    }

    @Test
    void should_not_change_key_without_special_characters() {
        // Metadata keys are not restricted to SQL-style identifiers, so dots, spaces and
        // non-ASCII characters must keep working exactly as before.
        Filter filter = metadataKey("user.email 1").isEqualTo("foo");

        String expr = MilvusV2MetadataFilterMapper.map(filter, "metadata");

        assertThat(expr).isEqualTo("metadata[\"user.email 1\"] == \"foo\"");
    }
}
