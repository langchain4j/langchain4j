package dev.langchain4j.rag.content.aggregator;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultContentAggregatorTest {

    ContentAggregator aggregator = new DefaultContentAggregator();

    @Test
    void should_return_same_contents_when_single_query_and_single_contents() {

        // given
        Query query = Query.from("query");
        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");

        Map<Query, Collection<List<Content>>> queryToContents = singletonMap(
                query,
                singletonList(asList(content1, content2))
        );

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

        Map<Query, Collection<List<Content>>> queryToContents = singletonMap(
                query,
                asList(
                        asList(content1, content2),
                        asList(content3, content4)
                )
        );

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

        Map<Query, Collection<List<Content>>> queryToContents = singletonMap(
                query,
                asList(
                        asList(content1, content2),
                        asList(content3, content4)
                )
        );

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
        queryToContents.put(
                query1,
                asList(
                        asList(content1, content2),
                        asList(content3, content4)
                )
        );
        queryToContents.put(
                query2,
                asList(
                        asList(content5, content6),
                        asList(content7, content8)
                )
        );

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
        queryToContents.put(
                query1,
                asList(
                        asList(content1, content2),
                        asList(content3, content4)
                )
        );
        queryToContents.put(
                query2,
                asList(
                        asList(content5, content6),
                        asList(content7, content8)
                )
        );

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
            Map<Query, Collection<List<Content>>> queryToContents
    ) {
        // given
        ContentAggregator aggregator = new DefaultContentAggregator();

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).isEmpty();
    }

    private static Stream<Arguments> should_return_empty_list_when_there_is_no_content_to_rerank() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(
                        emptyMap()
                ))
                .add(Arguments.of(
                        singletonMap(Query.from("query"), emptyList())
                ))
                .add(Arguments.of(
                        singletonMap(Query.from("query"), singletonList(emptyList()))
                ))
                .add(Arguments.of(
                        singletonMap(Query.from("query"), asList(emptyList(), emptyList()))
                ))
                .build();
    }
}