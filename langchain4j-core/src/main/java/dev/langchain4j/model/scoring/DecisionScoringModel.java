package dev.langchain4j.model.scoring;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
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
 *         .scoringModel(new DecisionScoringModel(decisionModel))
 *         .minScore(0.5)
 *         .build();
 * }</pre>
 * The segments are scored in requests of up to 20 segments (see {@link Builder#maxSegmentsPerRequest(Integer)}): the
 * input is the query, and each segment is part of its own yes/no question (see
 * {@link Builder#questionTemplate(PromptTemplate)}). Whether the answer to one question can be influenced by the other
 * questions of the request depends on the decision model. With {@link #scoreAsync}, the requests are sent in parallel.
 * <p>
 * Since the text of a segment is part of its question, a retrieved document that contains instructions, such as
 * "answer yes", can try to raise its own score. Treat the scores of untrusted content accordingly.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionScoringModel implements ScoringModel {

    /**
     * The default template of the question asked for each segment:
     * {@code "Does the document help answer the query?\nDocument: {{document}}"}.
     */
    public static final PromptTemplate DEFAULT_QUESTION_TEMPLATE =
            PromptTemplate.from("Does the document help answer the query?\nDocument: {{document}}");
    private static final int DEFAULT_MAX_SEGMENTS_PER_REQUEST = 20;

    private final DecisionModel decisionModel;
    private final PromptTemplate questionTemplate;
    private final int maxSegmentsPerRequest;

    public DecisionScoringModel(DecisionModel decisionModel) {
        this(builder().decisionModel(decisionModel));
    }

    protected DecisionScoringModel(Builder builder) {
        this.decisionModel = ensureNotNull(builder.decisionModel, "decisionModel");
        this.questionTemplate = getOrDefault(builder.questionTemplate, DEFAULT_QUESTION_TEMPLATE);
        validate(questionTemplate);
        this.maxSegmentsPerRequest = ensureGreaterThanZero(
                getOrDefault(builder.maxSegmentsPerRequest, DEFAULT_MAX_SEGMENTS_PER_REQUEST), "maxSegmentsPerRequest");
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

        DecisionRequestParameters parameters = DecisionRequestParameters.builder()
                .modelName(request.parameters().modelName())
                .build();
        List<List<String>> batches = batches(documents);
        List<CompletableFuture<DecisionResponse>> responses = new ArrayList<>(batches.size());
        for (List<String> batch : batches) {
            CompletableFuture<DecisionResponse> response;
            try {
                response = decisionModel.decideAsync(toRequest(batch, query, parameters));
            } catch (RuntimeException e) {
                response = CompletableFuture.failedFuture(e);
            }
            responses.add(response);
        }
        CompletableFuture<ScoringResponse> result = new CompletableFuture<>();
        responses.forEach(response -> response.whenComplete((ignored, error) -> {
            if (error != null) {
                result.completeExceptionally(unwrapCompletionException(error));
            }
        }));
        CompletableFuture.allOf(responses.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    List<Double> scores = new ArrayList<>(documents.size());
                    TokenUsage tokenUsage = null;
                    for (int i = 0; i < batches.size(); i++) {
                        DecisionResponse response = responses.get(i).join();
                        scores.addAll(scores(response, batches.get(i).size()));
                        tokenUsage = TokenUsage.sum(tokenUsage, response.tokenUsage());
                    }
                    return ScoringResponse.builder().scores(scores).tokenUsage(tokenUsage).build();
                })
                .whenComplete((response, error) -> {
                    if (error == null) {
                        result.complete(response);
                    } else {
                        result.completeExceptionally(unwrapCompletionException(error));
                    }
                });
        result.whenComplete((response, error) -> {
            if (error != null) {
                responses.forEach(future -> future.cancel(true));
            }
        });
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
         * Default value is {@link DecisionScoringModel#DEFAULT_QUESTION_TEMPLATE}.
         */
        public Builder questionTemplate(PromptTemplate questionTemplate) {
            this.questionTemplate = questionTemplate;
            return this;
        }

        /**
         * Sets the maximum number of segments sent in a single request to the decision model, so that the segments of
         * a request do not exceed the input size accepted by the decision model.
         * <p>
         * Default value is {@value DecisionScoringModel#DEFAULT_MAX_SEGMENTS_PER_REQUEST}.
         */
        public Builder maxSegmentsPerRequest(Integer maxSegmentsPerRequest) {
            this.maxSegmentsPerRequest = maxSegmentsPerRequest;
            return this;
        }

        public DecisionScoringModel build() {
            return new DecisionScoringModel(this);
        }
    }
}
