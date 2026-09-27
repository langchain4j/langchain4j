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
 * A model that evaluates a state against a set of named, typed questions and returns one typed answer per
 * question, instead of generated text.
 * <p>
 * Typical uses are classification, routing, gating and grading: for example, deciding which team should handle a
 * support ticket, whether a message is spam, or how urgent an incident is. All questions of a request are answered
 * against the same state in a single call.
 * <p>
 * The supported question types are {@link dev.langchain4j.model.decision.request.YesNoQuestion} (a yes/no
 * judgment answered with a probability), {@link dev.langchain4j.model.decision.request.ChoiceQuestion} (one option
 * out of a named set) and {@link dev.langchain4j.model.decision.request.ScoreQuestion} (a position on an ordered
 * scale). Implementations may support additional question types.
 *
 * @since 1.21.0
 */
@Experimental
public interface DecisionModel {

    /**
     * Answers the questions of the given request.
     * <p>
     * This applies the model's {@link #defaultRequestParameters() default parameters}, notifies the
     * {@link #listeners() listeners} and dispatches to {@link #doDecide(DecisionRequest)}.
     *
     * @param request the state, the questions and the per-call parameters.
     * @return one answer per question, keyed by question name.
     */
    default DecisionResponse decide(DecisionRequest request) {
        DecisionRequest finalRequest = withDefaultParameters(request);
        List<DecisionModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        onRequest(finalRequest, provider(), attributes, listeners);
        try {
            DecisionResponse response = doDecide(finalRequest);
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
     * {@link #listeners() listeners} and dispatches to {@link #doDecideAsync(DecisionRequest)}.
     *
     * @param request the state, the questions and the per-call parameters.
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

        CompletableFuture<DecisionResponse> result = source.whenComplete((response, error) -> {
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
        return DecisionRequest.builder()
                .state(request.state())
                .questions(request.questions())
                .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                .build();
    }
}
