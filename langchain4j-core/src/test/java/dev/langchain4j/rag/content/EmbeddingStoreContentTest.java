package dev.langchain4j.rag.content;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingStoreContentTest {

    @Test
    void test_create_from_text_segment_score_embeddingId() {

        // given
        TextSegment segment = TextSegment.from("text");

        // when
        EmbeddingStoreContent embeddingStoreContent = EmbeddingStoreContent.from(segment, 0.2d,"test-eid");

        // then
        assertThat(embeddingStoreContent.textSegment())
                .isSameAs(segment);
        assertThat(embeddingStoreContent.score())
                .isEqualTo(0.2d);
        assertThat(embeddingStoreContent.embeddingId())
                .isEqualTo("test-eid");

    }

    @Test
    void test_equals_hashCode() {

        // given
        EmbeddingStoreContent content1 = new EmbeddingStoreContent(TextSegment.from("content"),0.1d,"test-eid");
        EmbeddingStoreContent content2 = new EmbeddingStoreContent(TextSegment.from("content2"),0.2d,"test-eid2");
        EmbeddingStoreContent content3 = new EmbeddingStoreContent(TextSegment.from("content"),0.1d,"test-eid");

        // then
        assertThat(content1)
                .isNotEqualTo(content2)
                .doesNotHaveSameHashCodeAs(content2)
                .isEqualTo(content3)
                .hasSameHashCodeAs(content3);
    }

    @Test
    void test_toString() {

        // given
        EmbeddingStoreContent content = new EmbeddingStoreContent(TextSegment.from("content"),0.1d,"test-eid");

        // when
        String toString = content.toString();

        // then
        assertThat(toString)
                .isEqualTo("EmbeddingStoreContent{score=0.1, embeddingId='test-eid'}");
    }

    @Test
    void test_null_inputs() {
        assertThatThrownBy(() -> new EmbeddingStoreContent(null, 0.1d, "test-eid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("textSegment");

        EmbeddingStoreContent content = new EmbeddingStoreContent(TextSegment.from("text"), null, null);
        assertThat(content.score()).isNull();
        assertThat(content.embeddingId()).isNull();
    }

    @Test
    void test_negative_scores() {
        assertThatThrownBy(() -> new EmbeddingStoreContent(TextSegment.from("text"), -1.0d, "test-eid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }

}
