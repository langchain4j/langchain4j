package dev.langchain4j.rag.content;

import static dev.langchain4j.rag.content.ContentMetadata.SCORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.segment.TextSegment;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentTest {

    @Test
    void create_from_string() {

        // given
        String contentText = "content";

        // when
        Content content = Content.from(contentText);

        // then
        assertThat(content.textSegment().text()).isEqualTo(contentText);
    }

    @Test
    void create_from_text_segment() {

        // given
        TextSegment segment = TextSegment.from("text");

        // when
        Content content = Content.from(segment);

        // then
        assertThat(content.textSegment()).isSameAs(segment);
    }

    @Test
    void create_from_embedding_match() {

        // given
        TextSegment segment = TextSegment.from("text");
        Map<ContentMetadata, Object> metadata = Map.of(SCORE, 0.2d, ContentMetadata.EMBEDDING_ID, "test-eid");

        // when
        Content content = Content.from(segment, metadata);

        // then
        assertThat(content.textSegment()).isSameAs(segment);
        assertThat(content.metadata()).isNotEmpty();
        assertThat(content.metadata())
                .containsExactlyEntriesOf(Map.of(SCORE, 0.2, ContentMetadata.EMBEDDING_ID, "test-eid"));
    }

    @Test
    void equals_hash_code() {

        // given
        Content content1 = Content.from(TextSegment.from("content"), Map.of(SCORE, 1.0));
        Content content2 = Content.from("content 2");
        Content content3 = Content.from("content");

        // then
        assertThat(content1)
                .isNotEqualTo(content2)
                .doesNotHaveSameHashCodeAs(content2)
                .isEqualTo(content3) // Content.metadata() is not taken into account
                .hasSameHashCodeAs(content3); // Content.metadata() is not taken into account
    }

    @Test
    void to_string() {

        // given
        final var content = Content.from("content");

        // then
        assertThat(content)
                .hasToString(
                        "DefaultContent { textSegment = TextSegment { text = \"content\" metadata = {} }, metadata = {} }");
    }

    @Test
    void create_from_text_segment_with_null_metadata() {
        // given
        TextSegment segment = TextSegment.from("text");

        // when
        Content content = Content.from(segment, null);

        // then
        assertThat(content.textSegment()).isSameAs(segment);
        assertThat(content.metadata()).isEmpty();
    }

    @Test
    void create_from_text_segment_with_empty_metadata() {
        // given
        TextSegment segment = TextSegment.from("text");
        Map<ContentMetadata, Object> emptyMetadata = Collections.emptyMap();

        // when
        Content content = Content.from(segment, emptyMetadata);

        // then
        assertThat(content.textSegment()).isSameAs(segment);
        assertThat(content.metadata()).isEmpty();
    }

    @Test
    void metadata_returned_is_defensive_copy() {
        // given
        TextSegment segment = TextSegment.from("text");
        Map<ContentMetadata, Object> metadata = Map.of(SCORE, 0.5);
        Content content = Content.from(segment, metadata);

        // when/then - attempting to modify returned metadata should fail
        assertThatThrownBy(() -> content.metadata().put(ContentMetadata.EMBEDDING_ID, "test"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_handle_null_string_input() {
        // when/then
        assertThatThrownBy(() -> Content.from((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text cannot be null");
    }

    @Test
    void should_handle_null_text_segment_input() {
        // when/then
        assertThatThrownBy(() -> Content.from((TextSegment) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("textSegment cannot be null");
    }

    @Test
    void should_handle_special_characters() {
        // given
        String specialText = "!@#$%^&*()_+-=[]{}|;:'\",.<>?/~`";

        // when
        Content content = Content.from(specialText);

        // then
        assertThat(content.textSegment().text()).isEqualTo(specialText);
    }

    @Test
    void should_handle_multiline_text() {
        // given
        String multilineText = "Line 1\nLine 2\nLine 3\n\nLine 5";

        // when
        Content content = Content.from(multilineText);

        // then
        assertThat(content.textSegment().text()).isEqualTo(multilineText);
    }

    @Test
    void should_handle_metadata_with_null_values() {
        // given
        TextSegment segment = TextSegment.from("text");
        Map<ContentMetadata, Object> metadata = new HashMap<>();
        metadata.put(SCORE, 0.1);
        metadata.put(ContentMetadata.EMBEDDING_ID, null);

        // when
        Content content = Content.from(segment, metadata);

        // then
        assertThat(content.metadata()).hasSize(2);
        assertThat(content.metadata().get(SCORE)).isEqualTo(0.1);
        assertThat(content.metadata().get(ContentMetadata.EMBEDDING_ID)).isNull();
    }

    @Test
    void should_handle_different_score_types() {
        // given
        TextSegment segment = TextSegment.from("text");

        // when
        Content contentWithDouble = Content.from(segment, Map.of(SCORE, 0.1));
        Content contentWithFloat = Content.from(segment, Map.of(SCORE, 0.1f));
        Content contentWithInteger = Content.from(segment, Map.of(SCORE, 1));

        // then
        assertThat(contentWithDouble.metadata().get(SCORE)).isEqualTo(0.1);
        assertThat(contentWithFloat.metadata().get(SCORE)).isEqualTo(0.1f);
        assertThat(contentWithInteger.metadata().get(SCORE)).isEqualTo(1);
    }

    @Test
    void should_ignore_metadata_in_equality_with_different_metadata() {
        // given
        TextSegment segment = TextSegment.from("test");
        Content content1 = Content.from(segment, Map.of(SCORE, 0.1));
        Content content2 = Content.from(segment, Map.of(SCORE, 0.1));
        Content content3 = Content.from(segment, Map.of(ContentMetadata.EMBEDDING_ID, "test"));

        // then
        assertThat(content1).isEqualTo(content2);
        assertThat(content1).isEqualTo(content3);
        assertThat(content2).isEqualTo(content3);
    }

    @Test
    void should_differentiate_based_on_text_content() {
        // given
        Content content1 = Content.from("text1");
        Content content2 = Content.from("text2");

        // then
        assertThat(content1).isNotEqualTo(content2);
        assertThat(content1.hashCode()).isNotEqualTo(content2.hashCode());
    }

    @Test
    void toString_should_handle_different_content_types() {
        // given
        Content simpleContent = Content.from("simple");
        Content contentWithMetadata = Content.from(TextSegment.from("with metadata"), Map.of(SCORE, 0.1));

        // then
        assertThat(simpleContent.toString()).contains("simple");
        assertThat(contentWithMetadata.toString()).contains("with metadata");
        assertThat(contentWithMetadata.toString()).contains("0.1");
    }

    @Test
    void toString_should_handle_special_characters() {
        // given
        Content content = Content.from("text with \"quotes\" and \n newlines");

        // then
        String toString = content.toString();
        assertThat(toString).contains("text with");
        assertThat(toString).contains("quotes");
    }

    @Test
    void should_handle_concurrent_access_to_metadata() throws Exception {
        // given
        TextSegment segment = TextSegment.from("text");
        Map<ContentMetadata, Object> metadata = Map.of(SCORE, 0.1);
        Content content = Content.from(segment, metadata);

        // when - concurrent access to metadata
        assertThatThrownBy(() -> {
                    content.metadata().put(ContentMetadata.EMBEDDING_ID, "test");
                })
                .isInstanceOf(UnsupportedOperationException.class);

        // then - original metadata should be unchanged
        assertThat(content.metadata()).hasSize(1);
        assertThat(content.metadata().get(SCORE)).isEqualTo(0.1);
    }
}
