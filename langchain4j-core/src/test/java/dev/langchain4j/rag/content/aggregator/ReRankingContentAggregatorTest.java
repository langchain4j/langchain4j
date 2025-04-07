package dev.langchain4j.rag.content.aggregator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReRankingContentAggregatorTest {

    @ParameterizedTest
    @MethodSource
    void should_rerank_when_single_query_and_single_contents(
            Function<ScoringModel, ContentAggregator> contentAggregatorProvider) {

        // given
        Query query = Query.from("query");

        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(asList(content1, content2)));

        ScoringModel scoringModel = mock(ScoringModel.class);
        when(scoringModel.scoreAll(any(), any())).thenReturn(Response.from(asList(0.5, 0.7)));
        ContentAggregator aggregator = contentAggregatorProvider.apply(scoringModel);

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(2);
        assertReRankedContentOrder(aggregated, content2, content1);
        assertReRankedContentScore(aggregated, 0.7, 0.5);
    }

    static Stream<Arguments> should_rerank_when_single_query_and_single_contents() {
        return Stream.<Arguments>builder()
                .add(Arguments.of((Function<ScoringModel, ContentAggregator>) ReRankingContentAggregator::new))
                .add(Arguments.of((Function<ScoringModel, ContentAggregator>)
                        (scoringModel) -> ReRankingContentAggregator.builder()
                                .scoringModel(scoringModel)
                                .build()))
                .build();
    }

    @Test
    void should_fuse_then_rerank_when_single_query_and_multiple_contents() {

        // given
        Query query = Query.from("query");

        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content");

        Content content3 = Content.from("content");
        Content content4 = Content.from("content 4");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, asList(asList(content1, content2), asList(content3, content4)));

        ScoringModel scoringModel = mock(ScoringModel.class);
        when(scoringModel.scoreAll(
                        asList(
                                content2.textSegment(),
                                // content3 was fused with content2
                                content1.textSegment(),
                                content4.textSegment()),
                        query.text()))
                .thenReturn(Response.from(asList(0.5, 0.7, 0.9)));
        ContentAggregator aggregator = new ReRankingContentAggregator(scoringModel);

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(3);
        assertReRankedContentOrder(aggregated, content4, content1, content2);
        assertReRankedContentScore(aggregated, 0.9, 0.7, 0.5);
    }

    @Test
    void should_fail_when_multiple_queries_with_default_query_selector() {

        // given
        Map<Query, Collection<List<Content>>> queryToContents = new HashMap<>();
        queryToContents.put(Query.from("query 1"), null);
        queryToContents.put(Query.from("query 2"), null);

        ScoringModel scoringModel = mock(ScoringModel.class);
        ContentAggregator aggregator = new ReRankingContentAggregator(scoringModel);

        // when-then
        assertThatThrownBy(() -> aggregator.aggregate(queryToContents))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The 'queryToContents' contains 2 queries, making the re-ranking ambiguous. "
                        + "Because there are multiple queries, it is unclear which one should be used for re-ranking. "
                        + "Please provide a 'querySelector' in the constructor/builder.");
    }

    @Test
    void should_fuse_then_rerank_against_first_query_then_filter_by_minScore() {

        // given
        Function<Map<Query, Collection<List<Content>>>, Query> querySelector =
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
        Map<Query, Collection<List<Content>>> queryToContents = new LinkedHashMap<>();
        queryToContents.put(query1, asList(asList(content1, content2), asList(content3, content4)));

        queryToContents.put(query2, asList(asList(content5, content6), asList(content7, content8)));

        ScoringModel scoringModel = mock(ScoringModel.class);
        when(scoringModel.scoreAll(
                        asList(
                                content1.textSegment(),
                                content3.textSegment(),
                                content5.textSegment(),
                                content2.textSegment(),
                                content8.textSegment()),
                        query1.text()))
                .thenReturn(Response.from(asList(0.6, 0.2, 0.3, 0.4, 0.5)));

        ContentAggregator aggregator = new ReRankingContentAggregator(scoringModel, querySelector, minScore);

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        // content4, content6, content7 were fused with content1
        // content3 and content5 were filtered out by minScore
        assertThat(aggregated).hasSize(3);
        assertReRankedContentOrder(aggregated, content1, content8, content2);
        assertReRankedContentScore(aggregated, 0.6, 0.5, 0.4);
    }

    @Test
    void should_got_max_results() {

        // given
        Function<Map<Query, Collection<List<Content>>>, Query> querySelector =
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
        Map<Query, Collection<List<Content>>> queryToContents = new LinkedHashMap<>();
        queryToContents.put(query1, asList(asList(content1, content2), asList(content3, content4)));

        queryToContents.put(query2, asList(asList(content5, content6), asList(content7, content8)));

        ScoringModel scoringModel = mock(ScoringModel.class);
        when(scoringModel.scoreAll(
                        asList(
                                content1.textSegment(),
                                content3.textSegment(),
                                content5.textSegment(),
                                content2.textSegment(),
                                content8.textSegment()),
                        query1.text()))
                .thenReturn(Response.from(asList(0.6, 0.2, 0.3, 0.4, 0.5)));

        ContentAggregator aggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .querySelector(querySelector)
                .minScore(minScore)
                .maxResults(2)
                .build();

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        // content4, content6, content7 were fused with content1
        // content3 and content5 were filtered out by minScore
        // count2 filtered by maxResults
        assertThat(aggregated).hasSize(2);
        assertReRankedContentOrder(aggregated, content1, content8);
        assertReRankedContentScore(aggregated, 0.6, 0.5);
    }

    @ParameterizedTest
    @MethodSource
    void should_return_empty_list_when_there_is_no_content_to_rerank(
            Map<Query, Collection<List<Content>>> queryToContents) {
        // given
        ScoringModel scoringModel = mock(ScoringModel.class);
        ContentAggregator aggregator = new ReRankingContentAggregator(scoringModel);

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).isEmpty();
        verifyNoInteractions(scoringModel);
    }

    private static Stream<Arguments> should_return_empty_list_when_there_is_no_content_to_rerank() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(emptyMap()))
                .add(Arguments.of(singletonMap(Query.from("query"), emptyList())))
                .add(Arguments.of(singletonMap(Query.from("query"), singletonList(emptyList()))))
                .add(Arguments.of(singletonMap(Query.from("query"), asList(emptyList(), emptyList()))))
                .build();
    }

    private void assertReRankedContentOrder(List<Content> actual, Content... expectedContents) {
        List<TextSegment> expectedTextSegments =
                Arrays.stream(expectedContents).map(Content::textSegment).toList();

        List<TextSegment> actualTextSegments =
                actual.stream().map(Content::textSegment).toList();

        assertThat(actualTextSegments).containsExactlyElementsOf(expectedTextSegments);
    }

    private void assertReRankedContentScore(List<Content> actual, double... expectedScores) {
        for (int i = 0; i < actual.size(); i++) {
            assertThat((actual.get(i)).metadata()).containsEntry(ContentMetadata.RERANKED_SCORE, expectedScores[i]);
        }
    }
}
