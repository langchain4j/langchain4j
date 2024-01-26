package dev.langchain4j.rag.content.aggregator;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import lombok.Builder;

import java.util.*;
import java.util.function.Function;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toList;

/**
 * A {@link ContentAggregator} that performs re-ranking using a {@link ScoringModel}, such as Cohere.
 * <br>
 * The {@link ScoringModel} scores {@link Content}s against a (single) {@link Query}.
 * If multiple {@link Query}s are input to this aggregator
 * (for example, when using {@link ExpandingQueryTransformer}),
 * a {@link #querySelector} must be provided to select a {@link Query} for ranking all {@link Content}s.
 * Alternatively, a custom implementation can be created to score {@link Content}s against the {@link Query}s
 * that were used for their retrieval (instead of a single {@link Query}), and then re-rank based on those scores.
 * Although potentially more costly, this method may yield better results
 * when the {@link Query}s are significantly different.
 * <br>
 * <br>
 * Before the use of a {@link ScoringModel}, all {@link Content}s are fused in the same way
 * as by the {@link DefaultContentAggregator}. For detailed information, please refer to its Javadoc.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - {@link #minScore}: the minimum score for {@link Content}s to be returned.
 * {@link Content}s scoring below this threshold (as determined by the {@link ScoringModel})
 * are excluded from the results.
 *
 * @see DefaultContentAggregator
 */
public class ReRankingContentAggregator implements ContentAggregator {

    public static final Function<Map<Query, Collection<List<Content>>>, Query> DEFAULT_QUERY_SELECTOR =
            (queryToContents) -> {
                if (queryToContents.size() > 1) {
                    throw illegalArgument(
                            "The 'queryToContents' contains %s queries, making the re-ranking ambiguous. " +
                                    "Because there are multiple queries, it is unclear which one should be " +
                                    "used for re-ranking. Please provide a 'querySelector' in the constructor/builder.",
                            queryToContents.size()
                    );
                }
                return queryToContents.keySet().iterator().next();
            };

    private final ScoringModel scoringModel;
    private final Function<Map<Query, Collection<List<Content>>>, Query> querySelector;
    private final Double minScore;

    public ReRankingContentAggregator(ScoringModel scoringModel) {
        this(scoringModel, DEFAULT_QUERY_SELECTOR, null);
    }

    @Builder
    public ReRankingContentAggregator(ScoringModel scoringModel,
                                      Function<Map<Query, Collection<List<Content>>>, Query> querySelector,
                                      Double minScore) {
        this.scoringModel = ensureNotNull(scoringModel, "scoringModel");
        this.querySelector = getOrDefault(querySelector, DEFAULT_QUERY_SELECTOR);
        this.minScore = minScore;
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {

        // Select a query against which all contents will be re-ranked
        Query query = querySelector.apply(queryToContents);

        // For each query, fuse all contents retrieved from different sources using that query
        Map<Query, List<Content>> queryToFusedContents = fuse(queryToContents);

        // Fuse all contents retrieved using all queries
        List<Content> fusedContents = ReciprocalRankFuser.fuse(queryToFusedContents.values());

        // Re-rank all the fused contents against the query selected by the query selector
        List<TextSegment> reRankedAndFilteredSegments = reRankAndFilter(fusedContents, query);

        return reRankedAndFilteredSegments.stream()
                .map(Content::from)
                .collect(toList());
    }

    protected Map<Query, List<Content>> fuse(Map<Query, Collection<List<Content>>> queryToContents) {
        Map<Query, List<Content>> fused = new LinkedHashMap<>();
        for (Query query : queryToContents.keySet()) {
            Collection<List<Content>> contents = queryToContents.get(query);
            fused.put(query, ReciprocalRankFuser.fuse(contents));
        }
        return fused;
    }

    protected List<TextSegment> reRankAndFilter(List<Content> contents, Query query) {

        List<TextSegment> segments = contents.stream()
                .map(Content::textSegment)
                .collect(toList());

        List<Double> scores = scoringModel.scoreAll(segments, query.text()).content();

        Map<TextSegment, Double> segmentToScore = new HashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            segmentToScore.put(segments.get(i), scores.get(i));
        }

        return segmentToScore.entrySet().stream()
                .filter(entry -> minScore == null || entry.getValue() >= minScore)
                .sorted(Map.Entry.<TextSegment, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(toList());
    }
}
