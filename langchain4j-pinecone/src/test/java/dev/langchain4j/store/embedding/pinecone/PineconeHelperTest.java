package dev.langchain4j.store.embedding.pinecone;

import static dev.langchain4j.store.embedding.pinecone.PineconeHelper.metadataToStruct;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

class PineconeHelperTest {

    private static final String DEFAULT_METADATA_TEXT_KEY = "text_segment";

    @Test
    void metadataToStruct_should_preserve_text_and_other_metadata_when_metadata_uses_the_text_key() {
        Metadata metadata =
                new Metadata().put(DEFAULT_METADATA_TEXT_KEY, "another value").put("source", "manual");
        TextSegment textSegment = TextSegment.from("the real document", metadata);

        Struct struct = metadataToStruct(textSegment, DEFAULT_METADATA_TEXT_KEY);

        assertThat(struct.getFieldsMap().get(DEFAULT_METADATA_TEXT_KEY).getStringValue())
                .isEqualTo("the real document");
        assertThat(struct.getFieldsMap().get("source").getStringValue()).isEqualTo("manual");
    }

    @Test
    void metadataToStruct_should_preserve_text_when_metadata_uses_a_custom_text_key() {
        TextSegment textSegment = TextSegment.from("the real document", Metadata.from("content", "another value"));

        Struct struct = metadataToStruct(textSegment, "content");

        assertThat(struct.getFieldsMap().get("content").getStringValue()).isEqualTo("the real document");
    }

    @Test
    void metadataToStruct_should_store_text_and_metadata_when_there_is_no_collision() {
        TextSegment textSegment = TextSegment.from("the real document", Metadata.from("source", "manual"));

        Struct struct = metadataToStruct(textSegment, DEFAULT_METADATA_TEXT_KEY);

        assertThat(struct.getFieldsMap().get(DEFAULT_METADATA_TEXT_KEY).getStringValue())
                .isEqualTo("the real document");
        assertThat(struct.getFieldsMap().get("source").getStringValue()).isEqualTo("manual");
    }

    @Test
    void metadataToStruct_should_store_text_when_metadata_is_empty() {
        TextSegment textSegment = TextSegment.from("the real document");

        Struct struct = metadataToStruct(textSegment, DEFAULT_METADATA_TEXT_KEY);

        assertThat(struct.getFieldsMap()).containsOnlyKeys(DEFAULT_METADATA_TEXT_KEY);
        assertThat(struct.getFieldsMap().get(DEFAULT_METADATA_TEXT_KEY).getStringValue())
                .isEqualTo("the real document");
    }
}
