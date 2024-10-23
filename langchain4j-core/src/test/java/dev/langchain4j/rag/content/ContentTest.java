package dev.langchain4j.rag.content;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTest {

    @Test
    void test_create_from_string() {

        // given
        final var contentText = "content";

        // when
        final var content = Content.from(contentText);

        // then
        assertThat(content.textSegment().text()).isEqualTo(contentText);
    }

    @Test
    void test_create_from_text_segment() {

        // given
        final var segment = TextSegment.from("text");

        // when
        final var content = Content.from(segment);

        // then
        assertThat(content.textSegment()).isSameAs(segment);
    }

    @Test
    void test_equals_hashCode() {

        // given
        final var content1 = Content.from("content");
        final var content2 = Content.from("content 2");
        final var content3 = Content.from("content");

        // then
        assertThat(content1)
                .isNotEqualTo(content2)
                .doesNotHaveSameHashCodeAs(content2);

        assertThat(content1)
                .isEqualTo(content3)
                .hasSameHashCodeAs(content3);
    }

    @Test
    void test_toString() {
        // given
        final var content = Content.from("content");

        // then
        assertThat(content)
                .hasToString("Content { textSegment = TextSegment { text = \"content\" metadata = {} } }");
    }
}