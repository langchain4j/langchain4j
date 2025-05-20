package dev.langchain4j.rag.content.aggregator;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Aggregates all {@link Content}s retrieved from all {@link ContentRetriever}s using all {@link Query}s.
 * <br>
 * The goal is to ensure that only the most relevant and non-redundant {@link Content}s are presented to the LLM.
 * <br>
 * Some effective approaches include:
 * <pre>
 * - Re-ranking (see {@link ReRankingContentAggregator})
 * - Reciprocal Rank Fusion (see {@link ReciprocalRankFuser}, utilized in both {@link DefaultContentAggregator} and {@link ReRankingContentAggregator})
 * </pre>
 *
 * @see DefaultContentAggregator
 * @see ReRankingContentAggregator
 */
public interface ContentAggregator {

    /**
     * Aggregates all {@link Content}s retrieved by all {@link ContentRetriever}s using all {@link Query}s.
     * The {@link Content}s, both on input and output, are sorted by relevance,
     * with the most relevant {@link Content}s appearing at the beginning of {@code List<Content>}.
     *
     * @param queryToContents A map from a {@link Query} to all {@code List<Content>} retrieved with that {@link Query}.
     *                        Given that each {@link Query} can be routed to multiple {@link ContentRetriever}s, the
     *                        value of this map is a {@code Collection<List<Content>>}
     *                        rather than a simple {@code List<Content>}.
     * @return A list of aggregated {@link Content}s.
     */
    List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents);
}
