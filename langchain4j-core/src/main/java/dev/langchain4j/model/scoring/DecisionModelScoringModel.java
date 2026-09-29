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
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
 * All segments are scored in a single request: the input is the query, and each segment is part of its own yes/no
 * question (see {@link Builder#questionTemplate(PromptTemplate)}), so the score of a segment depends only on the query and the segment, not on the other segments of the
 * request. If the segments together exceed the input size accepted by the decision model, set
 * {@link Builder#maxSegmentsPerRequest(Integer)}.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelScoringModel implements ScoringModel {

    /**
     * The default template of the question asked for each segment.
     */
    public static final PromptTemplate DEFAULT_QUESTION_TEMPLATE =
            PromptTemplate.from("Does the document help answer the query?\nDocument: {{document}}");

    private final DecisionModel decisionModel;
    private final PromptTemplate questionTemplate;
    private final int maxSegmentsPerRequest;

    public DecisionModelScoringModel(DecisionModel decisionModel) {
        this(builder().decisionModel(decisionModel));
    }

    protected DecisionModelScoringModel(Builder builder) {
        this.decisionModel = ensureNotNull(builder.decisionModel, "decisionModel");
        this.questionTemplate = getOrDefault(builder.questionTemplate, DEFAULT_QUESTION_TEMPLATE);
        ensureContains(questionTemplate, "document");
        this.maxSegmentsPerRequest = builder.maxSegmentsPerRequest == null
                ? Integer.MAX_VALUE
                : ensureGreaterThanZero(builder.maxSegmentsPerRequest, "maxSegmentsPerRequest");
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        ensureNotNull(segments, "segments");
        ensureNotBlank(query, "query");

        List<String> texts = segments.stream().map(TextSegment::text).toList();
        List<Double> scores = new ArrayList<>(texts.size());
        TokenUsage tokenUsage = null;
        for (List<String> batch : batches(texts)) {
            DecisionResponse response = decisionModel.decide(toRequest(batch, query));
            scores.addAll(scores(response, batch.size()));
            tokenUsage = TokenUsage.sum(tokenUsage, response.tokenUsage());
        }
        return Response.from(scores, tokenUsage);
    }

    @Override
    public CompletableFuture<ScoringResponse> doScoreAsync(ScoringRequest request) {
        List<String> documents = ensureNotNull(request.documents(), "documents");
        String query = ensureNotBlank(request.query(), "query");

        CompletableFuture<ScoringResponse> result = CompletableFuture.completedFuture(
                ScoringResponse.builder().scores(List.of()).build());
        for (List<String> batch : batches(documents)) {
            result = result.thenCompose(previous -> decisionModel
                    .decideAsync(toRequest(batch, query))
                    .thenApply(response -> {
                        List<Double> scores = new ArrayList<>(previous.scores());
                        scores.addAll(scores(response, batch.size()));
                        return ScoringResponse.builder()
                                .scores(scores)
                                .tokenUsage(TokenUsage.sum(previous.tokenUsage(), response.tokenUsage()))
                                .build();
                    }));
        }
        return result;
    }

    private List<List<String>> batches(List<String> documents) {
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < documents.size(); ) {
            List<String> batch =
                    documents.subList(start, start + Math.min(maxSegmentsPerRequest, documents.size() - start));
            batches.add(batch);
            start += batch.size();
        }
        return batches;
    }

    private static List<Double> scores(DecisionResponse response, int size) {
        List<Double> scores = new ArrayList<>(size);
        for (int i = 1; i <= size; i++) {
            scores.add(response.yesNo(questionName(i)).probability());
        }
        return scores;
    }

    private DecisionRequest toRequest(List<String> batch, String query) {
        DecisionRequest.Builder request = DecisionRequest.builder().input(Map.of("query", query));
        for (int i = 1; i <= batch.size(); i++) {
            request.question(
                    questionName(i),
                    YesNoQuestion.of(questionTemplate.apply(Map.of("document", batch.get(i - 1))).text()));
        }
        return request.build();
    }

    static void ensureContains(PromptTemplate template, String variable) {
        if (!template.template().contains("{{" + variable + "}}")) {
            throw new IllegalArgumentException(
                    "The question template must contain {{%s}}, but was: %s".formatted(variable, template.template()));
        }
    }

    private static String questionName(int index) {
        return "document" + index;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DecisionModel decisionModel;
        private PromptTemplate questionTemplate;
        private Integer maxSegmentsPerRequest;

        /**
         * Sets the decision model that scores the segments. Required.
         */
        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Sets the template of the yes/no question asked for each segment, which must contain the
         * {@code {{document}}} variable (the text of the segment). The score is the probability of "yes".
         * <p>
         * Default value is {@link DecisionModelScoringModel#DEFAULT_QUESTION_TEMPLATE}:
         * "Does the document help answer the query?
Document: {{document}}".
         */
        public Builder questionTemplate(PromptTemplate questionTemplate) {
            this.questionTemplate = questionTemplate;
            return this;
        }

        /**
         * Sets the maximum number of segments sent in a single request to the decision model, for when the segments
         * together exceed the input size accepted by the decision model.
         * <p>
         * By default, all segments are sent in a single request.
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
