package dev.langchain4j.model.decision.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.synchronizedList;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.mock.MockDecisionModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * A {@link DecisionModel} for tests: it answers with fixed or computed answers and records the requests it receives.
 * It supports both {@link #decide(DecisionRequest)} and {@link #decideAsync(DecisionRequest)} (the future is already
 * completed when returned), unless created with {@link #withoutAsyncSupport()}.
 *
 * @deprecated use {@link MockDecisionModel} (package {@code dev.langchain4j.test.mock}) from the
 * {@code langchain4j-test} module. Unlike this class, it answers one request per scripted step and fails when the
 * script runs out; end the script with {@link MockDecisionModel#thenRepeats()} to answer every further request with its
 * last step. {@code requests()} and {@code request()} have the same names. Instead of {@code withTokenUsage(...)},
 * give a step the whole response: {@link MockDecisionModel#thatResponds(DecisionResponse)}; for answers computed from
 * the request, a fixed token usage has no replacement. {@code withoutAsyncSupport()} and {@code withAsyncResponse(...)}
 * have no replacement (with Mockito, stub the method on a spy), and asynchronous calls complete on another thread. A
 * response reports the model name of the request. Another difference:
 * {@link MockDecisionModel#thatAnswersYesNoQuestions(ToDoubleFunction)} takes a {@code ToDoubleFunction}, so a lambda
 * compiles unchanged, but a variable of type {@code Function<YesNoQuestion, Double>} does not. This class will be
 * removed in 1.26.0 at the earliest.
 */
@Experimental
@Deprecated(since = "1.24.0", forRemoval = true)
public class DecisionModelMock implements DecisionModel {

    private final Function<DecisionRequest, Map<String, ? extends DecisionAnswer>> answers;
    private final List<DecisionRequest> requests = synchronizedList(new ArrayList<>());
    private TokenUsage tokenUsage;
    private boolean asyncSupported = true;
    private Function<DecisionRequest, CompletableFuture<DecisionResponse>> asyncResponse;

    /**
     * @deprecated use {@link MockDecisionModel#thatAnswers(Function)} followed by
     * {@link MockDecisionModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public DecisionModelMock(Function<DecisionRequest, Map<String, ? extends DecisionAnswer>> answers) {
        this.answers = ensureNotNull(answers, "answers");
    }

    /**
     * Reports the given token usage in every response.
     *
     * @deprecated give a step the whole response, with its token usage:
     * {@link MockDecisionModel#thatResponds(DecisionResponse)}, for example
     * {@code thatResponds(DecisionResponse.builder().answers(answers).tokenUsage(tokenUsage).build())}, followed by
     * {@link MockDecisionModel#thenRepeats()}; for answers computed from the request, a fixed token usage has no
     * replacement. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public DecisionModelMock withTokenUsage(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
        return this;
    }

    /**
     * Makes {@link #decideAsync(DecisionRequest)} fail with an
     * {@link dev.langchain4j.exception.AsyncNotSupportedException}, like a decision model without asynchronous
     * support.
     *
     * @deprecated has no replacement: {@link MockDecisionModel} always answers asynchronous calls; with Mockito, stub
     * {@code doDecideAsync(...)} on a spy of it, for example to return a future that fails with an
     * {@code AsyncNotSupportedException}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public DecisionModelMock withoutAsyncSupport() {
        this.asyncSupported = false;
        return this;
    }

    /**
     * Makes {@link #decideAsync(DecisionRequest)} return the future given by the function instead of answering, for
     * example a future that is never completed, to test cancellation.
     *
     * @deprecated has no replacement: {@link MockDecisionModel} completes asynchronous calls on another thread with the
     * steps of its script; with Mockito, stub {@code doDecideAsync(...)} on a spy of it, for example
     * {@code doReturn(new CompletableFuture<>()).when(model).doDecideAsync(any())} to test cancellation. Removed
     * together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public DecisionModelMock withAsyncResponse(Function<DecisionRequest, CompletableFuture<DecisionResponse>> asyncResponse) {
        this.asyncResponse = asyncResponse;
        return this;
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        requests.add(request);
        return respond(request);
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        if (!asyncSupported) {
            return AsyncNotSupported.failedFuture(getClass(), "doDecideAsync");
        }
        requests.add(request);
        if (asyncResponse != null) {
            return asyncResponse.apply(request);
        }
        try {
            return CompletableFuture.completedFuture(respond(request));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private DecisionResponse respond(DecisionRequest request) {
        return DecisionResponse.builder()
                .answers(answers.apply(request))
                .tokenUsage(tokenUsage)
                .build();
    }

    public List<DecisionRequest> requests() {
        return new ArrayList<>(requests);
    }

    /**
     * The only request received so far.
     *
     * @throws IllegalStateException if the mock received no request or several requests.
     */
    public DecisionRequest request() {
        List<DecisionRequest> requests = requests();
        if (requests.size() != 1) {
            throw new IllegalStateException("Expected exactly 1 request, but received " + requests.size());
        }
        return requests.get(0);
    }

    /**
     * Always answers with the given answers, keyed by question name.
     *
     * @deprecated use {@link MockDecisionModel#thatAnswers(Map)} followed by {@link MockDecisionModel#thenRepeats()}.
     * Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static DecisionModelMock thatAlwaysAnswers(Map<String, ? extends DecisionAnswer> answers) {
        ensureNotNull(answers, "answers");
        return new DecisionModelMock(request -> answers);
    }

    /**
     * Answers with the answers computed from each request, keyed by question name.
     *
     * @deprecated use {@link MockDecisionModel#thatAnswers(Function)} followed by
     * {@link MockDecisionModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static DecisionModelMock thatAnswers(
            Function<DecisionRequest, Map<String, ? extends DecisionAnswer>> answers) {
        return new DecisionModelMock(answers);
    }

    /**
     * Answers every question of each request with the answer computed from the question.
     *
     * @deprecated use {@link MockDecisionModel#thatAnswersQuestions(Function)} followed by
     * {@link MockDecisionModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static DecisionModelMock thatAnswersQuestions(Function<Question, ? extends DecisionAnswer> answer) {
        ensureNotNull(answer, "answer");
        return new DecisionModelMock(request -> {
            Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
            request.questions().forEach((name, question) -> answers.put(name, answer.apply(question)));
            return answers;
        });
    }

    /**
     * Answers every yes/no question of each request with the probability computed from the question.
     *
     * @throws IllegalStateException when a request contains a question that is not a yes/no question.
     *
     * @deprecated use {@link MockDecisionModel#thatAnswersYesNoQuestions(ToDoubleFunction)} followed by
     * {@link MockDecisionModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static DecisionModelMock thatAnswersYesNoQuestions(Function<YesNoQuestion, Double> probability) {
        ensureNotNull(probability, "probability");
        return thatAnswersQuestions(question -> {
            if (!(question instanceof YesNoQuestion yesNoQuestion)) {
                throw new IllegalStateException("Expected a yes/no question, but got " + question);
            }
            return YesNoAnswer.from(probability.apply(yesNoQuestion));
        });
    }

    /**
     * @deprecated use {@link MockDecisionModel#thatFails(RuntimeException)} followed by
     * {@link MockDecisionModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static DecisionModelMock thatAlwaysThrowsException() {
        return thatAlwaysThrowsExceptionWithMessage("Something went wrong, but this is an expected exception");
    }

    /**
     * @deprecated use {@link MockDecisionModel#thatFails(RuntimeException)} followed by
     * {@link MockDecisionModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static DecisionModelMock thatAlwaysThrowsExceptionWithMessage(String message) {
        return new DecisionModelMock(request -> {
            throw new RuntimeException(message);
        });
    }
}
