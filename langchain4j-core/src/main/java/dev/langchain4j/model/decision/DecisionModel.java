package dev.langchain4j.model.decision;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.model.ModelProvider.OTHER;
import static dev.langchain4j.model.decision.DecisionModelListenerUtils.onError;
import static dev.langchain4j.model.decision.DecisionModelListenerUtils.onRequest;
import static dev.langchain4j.model.decision.DecisionModelListenerUtils.onResponse;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A model that evaluates an input against a set of named, typed questions and returns one typed answer per
 * question, instead of generated text.
 * <p>
 * Typical uses are classification, routing, gating and grading: for example, deciding which team should handle a
 * support ticket, whether a message is spam, or how urgent an incident is. All questions of a request are answered
 * against the same input in a single call.
 * <p>
 * The supported question types are {@link dev.langchain4j.model.decision.request.YesNoQuestion} (a yes/no
 * judgment answered with a probability), {@link dev.langchain4j.model.decision.request.ChoiceQuestion} (one option
 * out of a named set) and {@link dev.langchain4j.model.decision.request.ScaleQuestion} (a position on an ordered
 * scale). Implementations may support additional question types.
 * <p>
 * Probabilities in the answers must be derived from the model's output distribution, since thresholds and
 * escalation logic in applications rely on them. An implementation that cannot provide them for choice or scale
 * answers leaves {@code probabilities()} empty instead of inventing values.
 *
 * @since 1.21.0
 */
@Experimental
public interface DecisionModel {

    /**
     * Answers the questions of the given request.
     * <p>
     * This applies the model's {@link #defaultRequestParameters() default parameters}, notifies the
     * {@link #listeners() listeners} and dispatches to {@link #doDecide(DecisionRequest)}. The response is checked
     * against the request: a missing answer, an answer of the wrong type, a choice of an option that was not offered
     * or a scale answer outside the levels throws an
     * {@link dev.langchain4j.exception.InvalidDecisionResponseException}.
     *
     * @param request the input, the questions and the per-call parameters.
     * @return one answer per question, keyed by question name.
     */
    default DecisionResponse decide(DecisionRequest request) {
        DecisionRequest finalRequest = withDefaultParameters(request);
        List<DecisionModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        onRequest(finalRequest, provider(), attributes, listeners);
        try {
            DecisionResponse response = DecisionResponseValidator.validate(finalRequest, doDecide(finalRequest));
            onResponse(response, finalRequest, provider(), attributes, listeners);
            return response;
        } catch (Exception error) {
            onError(error, finalRequest, provider(), attributes, listeners);
            throw error;
        }
    }

    /**
     * The provider hook behind {@link #decide(DecisionRequest)}.
     *
     * @param request the request, with the default parameters already applied.
     * @return one answer per question, keyed by question name.
     */
    DecisionResponse doDecide(DecisionRequest request);

    /**
     * Non-blocking counterpart of {@link #decide(DecisionRequest)}.
     * <p>
     * This applies the model's {@link #defaultRequestParameters() default parameters}, notifies the
     * {@link #listeners() listeners} and dispatches to {@link #doDecideAsync(DecisionRequest)}. The response is
     * checked against the request, as in {@link #decide(DecisionRequest)}.
     *
     * @param request the input, the questions and the per-call parameters.
     * @return a {@link CompletableFuture} of the answers, keyed by question name.
     */
    default CompletableFuture<DecisionResponse> decideAsync(DecisionRequest request) {
        DecisionRequest finalRequest = withDefaultParameters(request);
        List<DecisionModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        onRequest(finalRequest, provider(), attributes, listeners);

        CompletableFuture<DecisionResponse> source;
        try {
            source = doDecideAsync(finalRequest);
        } catch (Exception error) {
            onError(error, finalRequest, provider(), attributes, listeners);
            return CompletableFuture.failedFuture(error);
        }

        CompletableFuture<DecisionResponse> validated =
                source.thenApply(response -> DecisionResponseValidator.validate(finalRequest, response));
        CompletableFuture<DecisionResponse> result = validated.whenComplete((response, error) -> {
            if (error != null) {
                Throwable cause = unwrapCompletionException(error);
                if (!(cause instanceof CancellationException)) {
                    onError(cause, finalRequest, provider(), attributes, listeners);
                }
            } else {
                onResponse(response, finalRequest, provider(), attributes, listeners);
            }
        });

        propagateCancellation(result, source);
        return result;
    }

    /**
     * The provider hook behind {@link #decideAsync(DecisionRequest)}. The default returns a failed future: a model
     * that is not genuinely asynchronous does not pretend to be.
     *
     * @param request the request, with the default parameters already applied.
     * @return a {@link CompletableFuture} of the answers, keyed by question name.
     */
    default CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        return AsyncNotSupported.failedFuture(getClass(), "doDecideAsync");
    }

    /**
     * The model's default per-call parameters, applied to every request and overridden by any parameters set on
     * the request itself. The default is {@link DecisionRequestParameters#EMPTY}.
     */
    default DecisionRequestParameters defaultRequestParameters() {
        return DecisionRequestParameters.EMPTY;
    }

    /**
     * The listeners notified of every request, response and error of this model.
     */
    default List<DecisionModelListener> listeners() {
        return List.of();
    }

    /**
     * The provider of this model.
     */
    default ModelProvider provider() {
        return OTHER;
    }

    /**
     * The name of the model used when a request does not specify one, or {@code null} if there is none.
     */
    default String modelName() {
        return defaultRequestParameters().modelName();
    }

    private DecisionRequest withDefaultParameters(DecisionRequest request) {
        return request.toBuilder()
                .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                .build();
    }
}
