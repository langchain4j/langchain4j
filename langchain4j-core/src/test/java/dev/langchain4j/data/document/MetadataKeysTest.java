package dev.langchain4j.data.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Test to verify that MetadataKeys class works correctly.
 */
class MetadataKeysTest {

    @Test
    void should_have_index_constant() {
        assertThat(MetadataKeys.INDEX).isEqualTo("index");
    }

    @Test
    void should_work_with_metadata() {
        Metadata metadata = Metadata.from(MetadataKeys.INDEX, "0");
        assertThat(metadata.getString(MetadataKeys.INDEX)).isEqualTo("0");
    }
}
