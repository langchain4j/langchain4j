package dev.langchain4j.rag.content;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReRankedContentTest {
    
    @Test
    void test_create_from_text_segment_score() {

        // given
        TextSegment segment = TextSegment.from("text");

        // when
        ReRankedContent reRankedContent = ReRankedContent.from(segment, 0.2d);

        // then
        assertThat(reRankedContent.textSegment())
                .isSameAs(segment);
        assertThat(reRankedContent.score())
                .isEqualTo(0.2d);
    }

    @Test
    void test_equals_hashCode() {

        // given
        ReRankedContent content1 = new ReRankedContent(TextSegment.from("content"),0.1d);
        ReRankedContent content2 = new ReRankedContent(TextSegment.from("content2"),0.2d);
        ReRankedContent content3 = new ReRankedContent(TextSegment.from("content"),0.1d);

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
        ReRankedContent content = new ReRankedContent(TextSegment.from("content"),0.1d);

        // when
        String toString = content.toString();

        // then
        assertThat(toString)
                .isEqualTo("ReRankedContent{score=0.1}");
    }

    @Test
    void test_null_inputs() {
        assertThatThrownBy(() -> new ReRankedContent(null, 0.1d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("textSegment");

        ReRankedContent content = new ReRankedContent(TextSegment.from("text"), null);
        assertThat(content.score()).isNull();
    }

    @Test
    void test_negative_scores() {
        assertThatThrownBy(() -> new ReRankedContent(TextSegment.from("text"), -1.0d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }
}
