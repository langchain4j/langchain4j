package dev.langchain4j.model.decision;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.concurrent.CompletableFuture;

/**
 * A model that evaluates a state against a set of named, typed questions and returns one typed answer per
 * question, instead of generated text.
 * <p>
 * Typical uses are classification, routing, gating and grading: for example, deciding which team should handle a
 * support ticket, whether a message is spam, or how urgent an incident is. All questions of a request are answered
 * against the same state in a single call.
 * <p>
 * The supported question types are {@link dev.langchain4j.model.decision.request.NoulQuestion} (a yes/no
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
     * This applies the model's {@link #defaultRequestParameters() default parameters} and dispatches to
     * {@link #doDecide(DecisionRequest)}.
     *
     * @param request the state, the questions and the per-call parameters.
     * @return one answer per question, keyed by question name.
     */
    default DecisionResponse decide(DecisionRequest request) {
        return doDecide(withDefaultParameters(request));
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
     * This applies the model's {@link #defaultRequestParameters() default parameters} and dispatches to
     * {@link #doDecideAsync(DecisionRequest)}.
     *
     * @param request the state, the questions and the per-call parameters.
     * @return a {@link CompletableFuture} of the answers, keyed by question name.
     */
    default CompletableFuture<DecisionResponse> decideAsync(DecisionRequest request) {
        return doDecideAsync(withDefaultParameters(request));
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

    private DecisionRequest withDefaultParameters(DecisionRequest request) {
        return DecisionRequest.builder()
                .state(request.state())
                .questions(request.questions())
                .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                .build();
    }
}
