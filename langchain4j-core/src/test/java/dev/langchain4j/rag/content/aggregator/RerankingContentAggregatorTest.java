package dev.langchain4j.rag.content.aggregator;

import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RerankingContentAggregatorTest {

    @Test
    void should_rerank_when_single_query_and_single_contents() {

        // given
        Query query = Query.from("query");

        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");

        Map<Query, List<List<Content>>> queryToContents = singletonMap(
                query,
                singletonList(asList(content1, content2))
        );

        ScoringModel scoringModel = mock(ScoringModel.class);
        when(scoringModel.scoreAll(any(), any())).thenReturn(Response.from(asList(0.5, 0.7)));
        ContentAggregator aggregator = new RerankingContentAggregator(scoringModel);

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).containsExactly(content2, content1);
    }

    @Test
    void should_fuse_then_rerank_when_single_query_and_multiple_contents() {

        // given
        Query query = Query.from("query");

        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content");

        Content content3 = Content.from("content");
        Content content4 = Content.from("content 4");

        Map<Query, List<List<Content>>> queryToContents = singletonMap(
                query,
                asList(
                        asList(content1, content2),
                        asList(content3, content4)
                )
        );

        ScoringModel scoringModel = mock(ScoringModel.class);
        when(scoringModel.scoreAll(
                asList(
                        content2.textSegment(),
                        // content3 was fused with content2
                        content1.textSegment(),
                        content4.textSegment()
                ), query.text())).thenReturn(Response.from(
                asList(
                        0.5,
                        0.7,
                        0.9
                )));
        ContentAggregator aggregator = new RerankingContentAggregator(scoringModel);

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).containsExactly(content4, content1, content2);
    }

    @Test
    void should_fail_when_multiple_queries_with_default_query_selector() {

        // given
        Map<Query, List<List<Content>>> queryToContents = new HashMap<>();
        queryToContents.put(Query.from("query 1"), null);
        queryToContents.put(Query.from("query 2"), null);

        ScoringModel scoringModel = mock(ScoringModel.class);
        ContentAggregator aggregator = new RerankingContentAggregator(scoringModel);

        // when-then
        assertThatThrownBy(() -> aggregator.aggregate(queryToContents))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The 'queryToContents' contains 2 queries, making the ranking ambiguous. " +
                        "Because there are multiple queries, it is unclear which one should be used for reranking. " +
                        "Please provide a 'querySelector' in the constructor/builder.");
    }

    @Test
    void should_fuse_then_rerank_against_first_query_then_filter_by_minScore() {

        // given
        Function<Map<Query, List<List<Content>>>, Query> querySelector =
                (q) -> q.keySet().iterator().next(); // always selects first query

        double minScore = 0.4;

        Query query1 = Query.from("query 1");

        Content content1 = Content.from("content");
        Content content2 = Content.from("content 2");

        Content content3 = Content.from("content 3");
        Content content4 = Content.from("content");

        Query query2 = Query.from("query 2");

        Content content5 = Content.from("content 5");
        Content content6 = Content.from("content");

        Content content7 = Content.from("content");
        Content content8 = Content.from("content 8");

        // LinkedHashMap is used to ensure a predictable order in the test
        Map<Query, List<List<Content>>> queryToContents = new LinkedHashMap<>();
        queryToContents.put(query1, asList(
                asList(content1, content2),
                asList(content3, content4)

        ));
        queryToContents.put(query2, asList(
                asList(content5, content6),
                asList(content7, content8)
        ));

        ScoringModel scoringModel = mock(ScoringModel.class);
        when(scoringModel.scoreAll(
                asList(
                        content1.textSegment(),
                        content3.textSegment(),
                        content5.textSegment(),
                        content2.textSegment(),
                        content8.textSegment()
                ), query1.text())).thenReturn(Response.from(
                asList(
                        0.6,
                        0.2,
                        0.3,
                        0.4,
                        0.5
                )));

        ContentAggregator aggregator = new RerankingContentAggregator(scoringModel, querySelector, minScore);

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated)
                // content4, content6, content7 were fused with content1
                // content3 and content5 were filtered out by minScore
                .containsExactly(content1, content8, content2);
    }
}