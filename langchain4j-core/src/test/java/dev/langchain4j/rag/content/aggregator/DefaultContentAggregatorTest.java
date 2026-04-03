package dev.langchain4j.rag.content.aggregator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultContentAggregatorTest {

    ContentAggregator aggregator = new DefaultContentAggregator();

    @Test
    void should_return_same_contents_when_single_query_and_single_contents() {

        // given
        Query query = Query.from("query");
        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(asList(content1, content2)));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).containsExactly(content1, content2);
    }

    @Test
    void should_fuse_contents_when_single_query_and_multiple_contents() {

        // given
        Query query = Query.from("query");
        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");
        Content content3 = Content.from("content 3");
        Content content4 = Content.from("content 4");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, asList(asList(content1, content2), asList(content3, content4)));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).containsExactly(content1, content3, content2, content4);
    }

    @Test
    void should_fuse_contents_when_single_query_and_multiple_contents_with_repeating_content() {

        // given
        Query query = Query.from("query");
        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content");
        Content content3 = Content.from("content");
        Content content4 = Content.from("content 4");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, asList(asList(content1, content2), asList(content3, content4)));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated)
                // content3 was fused with content2
                .containsExactly(content2, content1, content4);
    }

    @Test
    void should_fuse_contents_when_multiple_queries_and_multiple_contents() {

        // given
        Query query1 = Query.from("query 1");
        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");
        Content content3 = Content.from("content 3");
        Content content4 = Content.from("content 4");

        Query query2 = Query.from("query 2");
        Content content5 = Content.from("content 5");
        Content content6 = Content.from("content 6");
        Content content7 = Content.from("content 7");
        Content content8 = Content.from("content 8");

        // LinkedHashMap is used to ensure a predictable order in the test
        Map<Query, Collection<List<Content>>> queryToContents = new LinkedHashMap<>();
        queryToContents.put(query1, asList(asList(content1, content2), asList(content3, content4)));
        queryToContents.put(query2, asList(asList(content5, content6), asList(content7, content8)));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated)
                .containsExactly(content1, content5, content3, content7, content2, content6, content4, content8);
    }

    @Test
    void should_fuse_contents_when_multiple_queries_and_multiple_contents_with_repeating_content_between_queries() {

        // given
        Query query1 = Query.from("query 1");
        Content content1 = Content.from("content");
        Content content2 = Content.from("content 2");
        Content content3 = Content.from("content 3");
        Content content4 = Content.from("content 4");

        Query query2 = Query.from("query 2");
        Content content5 = Content.from("content 5");
        Content content6 = Content.from("content 6");
        Content content7 = Content.from("content");
        Content content8 = Content.from("content 8");

        // LinkedHashMap is used to ensure a predictable order in the test
        Map<Query, Collection<List<Content>>> queryToContents = new LinkedHashMap<>();
        queryToContents.put(query1, asList(asList(content1, content2), asList(content3, content4)));
        queryToContents.put(query2, asList(asList(content5, content6), asList(content7, content8)));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated)
                // content7 was fused with content1
                .containsExactly(content1, content5, content3, content2, content6, content4, content8);
    }

    @ParameterizedTest
    @MethodSource
    void should_return_empty_list_when_there_is_no_content_to_rerank(
            Map<Query, Collection<List<Content>>> queryToContents) {
        // given
        ContentAggregator aggregator = new DefaultContentAggregator();

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).isEmpty();
    }

    private static Stream<Arguments> should_return_empty_list_when_there_is_no_content_to_rerank() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(emptyMap()))
                .add(Arguments.of(singletonMap(Query.from("query"), emptyList())))
                .add(Arguments.of(singletonMap(Query.from("query"), singletonList(emptyList()))))
                .add(Arguments.of(singletonMap(Query.from("query"), asList(emptyList(), emptyList()))))
                .build();
    }

    @Test
    void should_handle_null_input() {
        // given
        ContentAggregator aggregator = new DefaultContentAggregator();

        // when/then
        assertThatThrownBy(() -> aggregator.aggregate(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void should_handle_empty_content_lists_mixed_with_content() {
        // given
        Query query = Query.from("query");
        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");

        Map<Query, Collection<List<Content>>> queryToContents = singletonMap(
                query, asList(emptyList(), singletonList(content1), emptyList(), singletonList(content2), emptyList()));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).containsExactly(content1, content2);
    }

    @Test
    void should_handle_identical_content_across_different_queries() {
        // given
        Query query1 = Query.from("query 1");
        Query query2 = Query.from("query 2");
        Content sameContent = Content.from("same content");
        Content otherContent = Content.from("other content");

        Map<Query, Collection<List<Content>>> queryToContents = new LinkedHashMap<>();
        queryToContents.put(query1, singletonList(asList(sameContent, otherContent)));
        queryToContents.put(query2, singletonList(singletonList(sameContent)));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).containsExactly(sameContent, otherContent);
    }

    @Test
    void should_handle_multiple_identical_contents_in_single_list() {
        // given
        Query query = Query.from("query");
        Content content = Content.from("repeated content");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(asList(content, content, content)));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).containsExactly(content);
    }

    @Test
    void should_fuse_with_complex_interleaving() {
        // given
        Query query = Query.from("query");
        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");
        Content content3 = Content.from("content 3");

        Map<Query, Collection<List<Content>>> queryToContents = singletonMap(
                query,
                asList(
                        asList(content1, content2, content3),
                        singletonList(content1) // should fuse with first occurrence
                        ));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).containsExactly(content1, content2, content3);
    }
}
