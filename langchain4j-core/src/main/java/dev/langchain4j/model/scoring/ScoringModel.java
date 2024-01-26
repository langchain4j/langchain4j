package dev.langchain4j.model.scoring;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;

import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureEq;
import static java.util.Collections.singletonList;

/**
 * Represents a model capable of scoring a text against a query.
 * <br>
 * Useful for identifying the most relevant texts when scoring multiple texts against the same query.
 * <br>
 * The scoring model can be employed for re-ranking purposes.
 */
public interface ScoringModel {

    /**
     * Scores a given text against a given query.
     *
     * @param text  The text to be scored.
     * @param query The query against which to score the text.
     * @return the score.
     */
    default Response<Double> score(String text, String query) {
        return score(TextSegment.from(text), query);
    }

    /**
     * Scores a given {@link TextSegment} against a given query.
     *
     * @param segment The {@link TextSegment} to be scored.
     * @param query   The query against which to score the segment.
     * @return the score.
     */
    default Response<Double> score(TextSegment segment, String query) {
        Response<List<Double>> response = scoreAll(singletonList(segment), query);
        ensureEq(response.content().size(), 1,
                "Expected a single score, but received %d", response.content().size());
        return Response.from(response.content().get(0), response.tokenUsage(), response.finishReason());
    }

    /**
     * Scores all provided {@link TextSegment}s against a given query.
     *
     * @param segments The list of {@link TextSegment}s to score.
     * @param query    The query against which to score the segments.
     * @return the list of scores. The order of scores corresponds to the order of {@link TextSegment}s.
     */
    Response<List<Double>> scoreAll(List<TextSegment> segments, String query);
}
