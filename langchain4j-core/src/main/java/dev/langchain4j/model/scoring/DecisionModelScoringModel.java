package dev.langchain4j.model.scoring;

import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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
     * The default template of the question asked for each segment:
     * {@code "Does the document help answer the query?\nDocument: {{document}}"}.
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
        validate(questionTemplate);
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
            DecisionResponse response = decisionModel.decide(toRequest(batch, query, DecisionRequestParameters.EMPTY));
            scores.addAll(scores(response, batch.size()));
            tokenUsage = TokenUsage.sum(tokenUsage, response.tokenUsage());
        }
        return Response.from(scores, tokenUsage);
    }

    @Override
    public CompletableFuture<ScoringResponse> doScoreAsync(ScoringRequest request) {
        List<String> documents = ensureNotNull(request.documents(), "documents");
        String query = ensureNotBlank(request.query(), "query");

        CompletableFuture<ScoringResponse> result = new CompletableFuture<>();
        AtomicReference<CompletableFuture<?>> inFlight = new AtomicReference<>();
        result.whenComplete((response, error) -> {
            if (result.isCancelled() && inFlight.get() != null) {
                inFlight.get().cancel(true);
            }
        });
        DecisionRequestParameters parameters = DecisionRequestParameters.builder()
                .modelName(request.parameters().modelName())
                .build();
        scoreBatches(batches(documents).iterator(), query, parameters, List.of(), null, result, inFlight);
        return result;
    }

    private void scoreBatches(
            Iterator<List<String>> batches,
            String query,
            DecisionRequestParameters parameters,
            List<Double> scores,
            TokenUsage tokenUsage,
            CompletableFuture<ScoringResponse> result,
            AtomicReference<CompletableFuture<?>> inFlight) {
        if (result.isDone()) {
            return;
        }
        if (!batches.hasNext()) {
            result.complete(ScoringResponse.builder().scores(scores).tokenUsage(tokenUsage).build());
            return;
        }
        List<String> batch = batches.next();
        CompletableFuture<DecisionResponse> response;
        try {
            response = decisionModel.decideAsync(toRequest(batch, query, parameters));
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
            return;
        }
        inFlight.set(response);
        if (result.isCancelled()) {
            response.cancel(true);
            return;
        }
        response.whenComplete((decisionResponse, error) -> {
            if (error != null) {
                result.completeExceptionally(unwrapCompletionException(error));
                return;
            }
            List<Double> allScores = new ArrayList<>(scores);
            TokenUsage allTokenUsage;
            try {
                allScores.addAll(scores(decisionResponse, batch.size()));
                allTokenUsage = TokenUsage.sum(tokenUsage, decisionResponse.tokenUsage());
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
                return;
            }
            scoreBatches(batches, query, parameters, allScores, allTokenUsage, result, inFlight);
        });
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

    private DecisionRequest toRequest(List<String> batch, String query, DecisionRequestParameters parameters) {
        DecisionRequest.Builder request =
                DecisionRequest.builder().input(Map.of("query", query)).parameters(parameters);
        for (int i = 1; i <= batch.size(); i++) {
            request.question(
                    questionName(i),
                    YesNoQuestion.of(questionTemplate.apply(Map.of("document", batch.get(i - 1))).text()));
        }
        return request.build();
    }

    private static void validate(PromptTemplate template) {
        if (!template.template().contains("{{document}}")) {
            throw new IllegalArgumentException(
                    "The question template must contain {{document}}, but was: " + template.template());
        }
        try {
            template.apply(Map.of("document", "document"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "The question template can only use the {{document}} variable, but was: " + template.template(),
                    e);
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
         * Default value is {@link DecisionModelScoringModel#DEFAULT_QUESTION_TEMPLATE}.
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
