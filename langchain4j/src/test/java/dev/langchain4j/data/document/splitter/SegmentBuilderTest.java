package dev.langchain4j.data.document.splitter;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;


class SegmentBuilderTest implements WithAssertions {
    @Test
    void shouldAppendText() {
        SegmentBuilder builder = new SegmentBuilder(10, String::length, " ");
        builder.append("Hello");
        builder.append("world");
        builder.append(" ");
        assertThat(builder.toString()).isEqualTo("Hello world");
    }

    @Test
    public void test_by_words() {
        SegmentBuilder builder = new SegmentBuilder(10,
                text -> text.split(" ").length,
                " ; ");

        builder.append("one fish");

        assertThat(builder.getSize()).isEqualTo(2);
        assertThat(builder.hasSpaceFor("two fish")).isTrue();

        builder.prepend("two fish");
        assertThat(builder.getSize()).isEqualTo(5);

        builder.append("rabbit rabbit rabbit");
        assertThat(builder.getSize()).isEqualTo(9);

        assertThat(builder.hasSpaceFor("two more")).isFalse();

        assertThat(builder.toString()).isEqualTo("two fish ; one fish ; rabbit rabbit rabbit");
    }

    @Test
    public void test_reset() {
        SegmentBuilder builder = new SegmentBuilder(10,
                text -> text.split(" ").length,
                " ; ");

        builder.append("one fish");

        assertThat(builder.isNotEmpty()).isTrue();
        assertThat(builder.getSize()).isEqualTo(2);
        assertThat(builder.toString()).isEqualTo("one fish");

        builder.reset();

        assertThat(builder.isNotEmpty()).isFalse();
        assertThat(builder.getSize()).isEqualTo(0);
        assertThat(builder.toString()).isEqualTo("");
    }

    @Test
    public void test_append_prepend() {
        {
            SegmentBuilder builder = new SegmentBuilder(10, String::length, " ");
            builder.append("Hello");
            builder.append("world");
            assertThat(builder.toString()).isEqualTo("Hello world");
        }
        {
            SegmentBuilder builder = new SegmentBuilder(10, String::length, " ");
            builder.prepend("world");
            builder.prepend("Hello");
            assertThat(builder.toString()).isEqualTo("Hello world");
        }
    }
}