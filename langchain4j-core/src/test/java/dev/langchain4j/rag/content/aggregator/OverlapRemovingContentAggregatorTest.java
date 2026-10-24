package dev.langchain4j.rag.content.aggregator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OverlapRemovingContentAggregatorTest {

    private static final Query QUERY = Query.from("query");

    @Test
    void should_fail_when_delegate_is_null() {
        assertThatThrownBy(() -> new OverlapRemovingContentAggregator(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delegate");
    }

    @Test
    void should_aggregate_with_delegate_then_strip_overlap() {

        // given
        Content content0 = content("paragraph A. paragraph B.", "doc.pdf", "0");
        Content content1 = content("paragraph B. paragraph C.", "doc.pdf", "1");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(QUERY, singletonList(asList(content0, content1)));

        ContentAggregator aggregator = new OverlapRemovingContentAggregator(new DefaultContentAggregator());

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(2);
        assertThat(aggregated.get(0).textSegment().text()).isEqualTo("paragraph A. paragraph B.");
        assertThat(aggregated.get(1).textSegment().text()).isEqualTo("paragraph C.");
    }

    @Test
    void should_preserve_aggregated_order_after_stripping() {

        // given: the higher-index segment ranks first after aggregation
        Content content1 = content("paragraph B. paragraph C.", "doc.pdf", "1");
        Content content0 = content("paragraph A. paragraph B.", "doc.pdf", "0");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(QUERY, singletonList(asList(content1, content0)));

        ContentAggregator aggregator = new OverlapRemovingContentAggregator(new DefaultContentAggregator());

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then: order preserved, the higher-index segment stripped in place
        assertThat(aggregated).hasSize(2);
        assertThat(aggregated.get(0).textSegment().text()).isEqualTo("paragraph C.");
        assertThat(aggregated.get(1).textSegment().text()).isEqualTo("paragraph A. paragraph B.");
    }

    @Test
    void should_not_strip_when_segments_are_not_consecutive() {

        // given: index 0 and index 2 are not adjacent
        Content content0 = content("paragraph A. paragraph B.", "doc.pdf", "0");
        Content content2 = content("paragraph B. paragraph C.", "doc.pdf", "2");

        // when
        List<Content> result = OverlapRemovingContentAggregator.removeOverlaps(asList(content0, content2));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("paragraph A. paragraph B.");
        assertThat(result.get(1).textSegment().text()).isEqualTo("paragraph B. paragraph C.");
    }

    @Test
    void should_not_strip_segments_from_different_documents() {

        // given: same index values but different source documents
        Content contentDoc1 = content("paragraph A. paragraph B.", "doc1.pdf", "0");
        Content contentDoc2 = content("paragraph B. paragraph C.", "doc2.pdf", "1");

        // when
        List<Content> result = OverlapRemovingContentAggregator.removeOverlaps(asList(contentDoc1, contentDoc2));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("paragraph A. paragraph B.");
        assertThat(result.get(1).textSegment().text()).isEqualTo("paragraph B. paragraph C.");
    }

    @Test
    void should_not_strip_segments_without_identifying_metadata() {

        // given: "index" is the only metadata key, so document identity cannot be established.
        // Two metadata-less documents would otherwise collapse into one group and their
        // colliding indexes could trigger cross-document stripping.
        Content content0 = Content.from(TextSegment.from("paragraph A. paragraph B.", Metadata.from("index", "0")));
        Content content1 = Content.from(TextSegment.from("paragraph B. paragraph C.", Metadata.from("index", "1")));

        // when
        List<Content> result = OverlapRemovingContentAggregator.removeOverlaps(asList(content0, content1));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("paragraph A. paragraph B.");
        assertThat(result.get(1).textSegment().text()).isEqualTo("paragraph B. paragraph C.");
    }

    @Test
    void should_not_strip_segments_with_invalid_index_metadata() {

        // given
        Content content0 = content("the quick brown fox jumps", "doc.pdf", "0");
        Content contentInvalid = content("brown fox jumps over the lazy dog", "doc.pdf", "one");

        // when
        List<Content> result = OverlapRemovingContentAggregator.removeOverlaps(asList(content0, contentInvalid));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("the quick brown fox jumps");
        assertThat(result.get(1).textSegment().text()).isEqualTo("brown fox jumps over the lazy dog");
    }

    @Test
    void should_not_strip_repeated_text_that_is_not_suffix_prefix_overlap() {

        // given: "paragraph B." repeats but is not a suffix of the first segment
        Content content0 = content("paragraph B. paragraph A.", "doc.pdf", "0");
        Content content1 = content("paragraph B. paragraph C.", "doc.pdf", "1");

        // when
        List<Content> result = OverlapRemovingContentAggregator.removeOverlaps(asList(content0, content1));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("paragraph B. paragraph A.");
        assertThat(result.get(1).textSegment().text()).isEqualTo("paragraph B. paragraph C.");
    }

    @Test
    void should_strip_chinese_overlap() {

        // given: the shared text is 12 characters, above the minimum overlap length
        Content content0 = content("今天我们学习向量数据库检索的基础知识", "doc.pdf", "0");
        Content content1 = content("向量数据库检索的基础知识帮助我们减少重复", "doc.pdf", "1");

        // when
        List<Content> result = OverlapRemovingContentAggregator.removeOverlaps(asList(content0, content1));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("今天我们学习向量数据库检索的基础知识");
        assertThat(result.get(1).textSegment().text()).isEqualTo("帮助我们减少重复");
    }

    @Test
    void should_not_produce_blank_segment_when_texts_are_identical() {

        // given: full overlap would strip the later segment down to blank text
        Content content0 = content("identical segment text", "doc.pdf", "0");
        Content content1 = content("identical segment text", "doc.pdf", "1");

        // when
        List<Content> result = OverlapRemovingContentAggregator.removeOverlaps(asList(content0, content1));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("identical segment text");
        assertThat(result.get(1).textSegment().text()).isEqualTo("identical segment text");
    }

    @Test
    void should_return_single_content_unchanged() {

        // given
        Content content = content("paragraph A.", "doc.pdf", "0");

        // when
        List<Content> result = OverlapRemovingContentAggregator.removeOverlaps(singletonList(content));

        // then
        assertThat(result).containsExactly(content);
    }

    @Test
    void stripOverlap_removes_suffix_prefix_overlap() {
        String result = OverlapRemovingContentAggregator.stripOverlap(
                "paragraph A. paragraph B.", "paragraph B. paragraph C.", 10);
        assertThat(result).isEqualTo("paragraph C.");
    }

    @Test
    void stripOverlap_leaves_unchanged_when_overlap_shorter_than_min_chars() {
        String result = OverlapRemovingContentAggregator.stripOverlap("hello world", "world end", 10);
        assertThat(result).isEqualTo("world end");
    }

    @Test
    void stripOverlap_leaves_unchanged_when_no_overlap() {
        String result = OverlapRemovingContentAggregator.stripOverlap("segment one", "segment two", 10);
        assertThat(result).isEqualTo("segment two");
    }

    @Test
    void stripOverlap_handles_empty_and_short_texts() {
        assertThat(OverlapRemovingContentAggregator.stripOverlap("", "hello", 10))
                .isEqualTo("hello");
        assertThat(OverlapRemovingContentAggregator.stripOverlap("hello", "", 10))
                .isEmpty();
        assertThat(OverlapRemovingContentAggregator.stripOverlap("a", "ab", 10)).isEqualTo("ab");
    }

    private static Content content(String text, String source, String index) {
        return Content.from(
                TextSegment.from(text, new Metadata().put("source", source).put("index", index)));
    }
}
