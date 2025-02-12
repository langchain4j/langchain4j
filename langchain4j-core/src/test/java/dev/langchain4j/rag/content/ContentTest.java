package dev.langchain4j.rag.content;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.langchain4j.rag.content.ContentMetadata.SCORE;
import static org.assertj.core.api.Assertions.assertThat;

class ContentTest {

    @Test
    void test_create_from_string() {

        // given
        String contentText = "content";

        // when
        Content content = Content.from(contentText);

        // then
        assertThat(content.textSegment().text()).isEqualTo(contentText);
    }

    @Test
    void test_create_from_text_segment() {

        // given
        TextSegment segment = TextSegment.from("text");

        // when
        Content content = Content.from(segment);

        // then
        assertThat(content.textSegment()).isSameAs(segment);
    }

    @Test
    void test_create_from_embedding_match() {

        // given
        TextSegment segment = TextSegment.from("text");
        Map<ContentMetadata, Object> metadata = Map.of(
                SCORE, 0.2d,
                ContentMetadata.EMBEDDING_ID, "test-eid"
        );

        // when
        Content content = Content.from(segment, metadata);

        // then
        assertThat(content.textSegment())
                .isSameAs(segment);
        assertThat(content.metadata())
                .isNotEmpty();
        assertThat(content.metadata())
                .containsExactlyEntriesOf(Map.of(SCORE, 0.2, ContentMetadata.EMBEDDING_ID, "test-eid"));

    }

    @Test
    void test_equals_hashCode() {

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
    void test_toString() {

        // given
        final var content = Content.from("content");

        // then
        assertThat(content)
                .hasToString("DefaultContent[textSegment=TextSegment { text = \"content\" metadata = {} }, metadata={}]");
    }
}
