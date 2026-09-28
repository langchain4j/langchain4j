package dev.langchain4j.model.scoring;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ScoringModel} backed by a {@link DecisionModel}: the score of a segment is the probability that the
 * answer to "Does the document help answer the query?" is "yes".
 * <p>
 * It can be used wherever a {@link ScoringModel} is expected, for example to re-rank retrieved content with a
 * {@link dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator}:
 * <pre>{@code
 * ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
 *         .scoringModel(new DecisionModelScoringModel(decisionModel))
 *         .minScore(0.5)
 *         .build();
 * }</pre>
 * Segments are scored in batches: all segments of a batch are sent in a single request, with one yes/no question per
 * segment.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelScoringModel implements ScoringModel {

    private static final String DEFAULT_QUESTION = "Does the document help answer the query?";
    private static final int DEFAULT_MAX_SEGMENTS_PER_REQUEST = 20;

    private final DecisionModel decisionModel;
    private final String question;
    private final int maxSegmentsPerRequest;

    public DecisionModelScoringModel(DecisionModel decisionModel) {
        this(builder().decisionModel(decisionModel));
    }

    protected DecisionModelScoringModel(Builder builder) {
        this.decisionModel = ensureNotNull(builder.decisionModel, "decisionModel");
        this.question = ensureNotBlank(getOrDefault(builder.question, DEFAULT_QUESTION), "question");
        this.maxSegmentsPerRequest = ensureGreaterThanZero(
                getOrDefault(builder.maxSegmentsPerRequest, DEFAULT_MAX_SEGMENTS_PER_REQUEST),
                "maxSegmentsPerRequest");
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        ensureNotNull(segments, "segments");
        ensureNotBlank(query, "query");

        List<Double> scores = new ArrayList<>(segments.size());
        TokenUsage tokenUsage = null;
        for (int start = 0; start < segments.size(); start += maxSegmentsPerRequest) {
            List<TextSegment> batch = segments.subList(start, Math.min(start + maxSegmentsPerRequest, segments.size()));
            DecisionResponse response = decisionModel.decide(toRequest(batch, query));
            for (int i = 1; i <= batch.size(); i++) {
                scores.add(response.yesNo(questionName(i)).probability());
            }
            tokenUsage = TokenUsage.sum(tokenUsage, response.tokenUsage());
        }
        return Response.from(scores, tokenUsage);
    }

    private DecisionRequest toRequest(List<TextSegment> batch, String query) {
        Map<String, String> documents = new LinkedHashMap<>();
        DecisionRequest.Builder request = DecisionRequest.builder();
        for (int i = 1; i <= batch.size(); i++) {
            documents.put(String.valueOf(i), batch.get(i - 1).text());
            request.question(questionName(i), YesNoQuestion.of("Document " + i + ": " + question));
        }
        return request.input(Map.of("query", query, "documents", documents)).build();
    }

    private static String questionName(int index) {
        return "document" + index;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DecisionModel decisionModel;
        private String question;
        private Integer maxSegmentsPerRequest;

        /**
         * Sets the decision model that scores the segments. Required.
         */
        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Sets the yes/no question asked for each segment. The score is the probability of "yes".
         * <p>
         * Default value is {@value DecisionModelScoringModel#DEFAULT_QUESTION}
         */
        public Builder question(String question) {
            this.question = question;
            return this;
        }

        /**
         * Sets the maximum number of segments sent in a single request to the decision model.
         * <p>
         * Default value is {@value DecisionModelScoringModel#DEFAULT_MAX_SEGMENTS_PER_REQUEST}.
         */
        public Builder maxSegmentsPerRequest(Integer maxSegmentsPerRequest) {
            this.maxSegmentsPerRequest = maxSegmentsPerRequest;
            return this;
        }

        public DecisionModelScoringModel build() {
            return new DecisionModelScoringModel(this);
        }
    }
}
