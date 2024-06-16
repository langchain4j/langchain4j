package dev.langchain4j.data.segment;

import dev.langchain4j.data.document.Metadata;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class TextSegmentTest implements WithAssertions {
    @Test
    public void test_blank() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TextSegment.from(" "))
                .withMessageContaining("text cannot be null or blank");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TextSegment.from(null))
                .withMessageContaining("text cannot be null or blank");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TextSegment.from("text", null))
                .withMessageContaining("metadata cannot be null");
    }

    @Test
    public void test_equals_hashCode() {
        TextSegment ts1 = TextSegment.from("text");
        TextSegment ts2 = TextSegment.from("text");

        Metadata m1 = new Metadata();
        m1.put("abc", "123");
        Metadata m2 = new Metadata();
        m2.put("abc", "123");

        Metadata m3 = new Metadata();
        m3.put("abc", "xyz");

        TextSegment ts3 = TextSegment.from("text", m1);
        TextSegment ts4 = TextSegment.from("text", m1);
        TextSegment ts5 = TextSegment.from("text", m2);

        assertThat(ts1)
                .isEqualTo(ts1)
                .hasSameHashCodeAs(ts1)
                .isEqualTo(ts2)
                .hasSameHashCodeAs(ts2)
                .isNotEqualTo(ts3)
                .doesNotHaveSameHashCodeAs(ts3);

        assertThat(ts3)
                .isEqualTo(ts3)
                .hasSameHashCodeAs(ts3)
                .isEqualTo(ts4)
                .hasSameHashCodeAs(ts4)
                .isEqualTo(ts5)
                .hasSameHashCodeAs(ts5);
    }

    @Test
    public void test_accessors() {
        Metadata metadata = new Metadata();
        metadata.put("abc", "123");
        TextSegment ts = TextSegment.from("text", metadata);

        assertThat(ts.text()).isEqualTo("text");
        assertThat(ts.metadata()).isEqualTo(metadata);
        assertThat(ts.metadata("abc")).isEqualTo("123");

        assertThat(ts)
                .hasToString("TextSegment { text = \"text\" metadata = {abc=123} }");
    }

    @Test
    public void test_builders() {
        assertThat(new TextSegment("abc", new Metadata()))
                .isEqualTo(TextSegment.from("abc"))
                .isEqualTo(TextSegment.textSegment("abc"))
                .isEqualTo(TextSegment.from("abc", new Metadata()))
                .isEqualTo(TextSegment.textSegment("abc", new Metadata()));

        Metadata metadata = new Metadata();
        metadata.put("abc", "123");

        assertThat(new TextSegment("abc", metadata))
                .isEqualTo(TextSegment.from("abc", metadata))
                .isEqualTo(TextSegment.textSegment("abc", metadata));
    }

}