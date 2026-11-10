package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.Internal;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * A {@link DecisionModel} for tests: it needs no network, answers with fixed or computed answers, and records the
 * requests it receives. It supports both {@link #decide(DecisionRequest)} and {@link #decideAsync(DecisionRequest)} (the
 * future is already completed when returned).
 */
@Experimental
public class DecisionModelMock implements DecisionModel {

    private final Function<DecisionRequest, Map<String, ? extends DecisionAnswer>> answers;
    private final RecordedRequests<DecisionRequest> requests = new RecordedRequests<>("decision");
    private TokenUsage tokenUsage;
    private String modelName;
    private ModelProvider provider = ModelProvider.OTHER;
    private boolean asyncSupported = true;
    private Function<DecisionRequest, CompletableFuture<DecisionResponse>> asyncResponse;
    private List<DecisionModelListener> listeners = List.of();

    private DecisionModelMock(Function<DecisionRequest, Map<String, ? extends DecisionAnswer>> answers) {
        this.answers = ensureNotNull(answers, "answers");
    }

    /**
     * Reports the given token usage in every response.
     */
    public DecisionModelMock withTokenUsage(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
        return this;
    }

    /**
     * Reports the given model name in every response.
     */
    public DecisionModelMock withModelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    /**
     * Returns the given provider from {@link #provider()}.
     */
    public DecisionModelMock withProvider(ModelProvider provider) {
        this.provider = ensureNotNull(provider, "provider");
        return this;
    }

    @Override
    public ModelProvider provider() {
        return provider;
    }

    /**
     * Makes {@link #decideAsync(DecisionRequest)} fail with an
     * {@link AsyncNotSupportedException}, like a decision model without asynchronous
     * support.
     */
    @Internal
    public DecisionModelMock withoutAsyncSupport() {
        this.asyncSupported = false;
        return this;
    }

    /**
     * Makes {@link #decideAsync(DecisionRequest)} return the future given by the function instead of answering, for
     * example a future that is never completed, to test cancellation.
     */
    @Internal
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
                .modelName(modelName)
                .tokenUsage(tokenUsage)
                .build();
    }

    /**
     * A copy of the requests received so far, in the order they arrived.
     */
    public List<DecisionRequest> requests() {
        return requests.all();
    }

    /**
     * The only request received so far.
     *
     * @throws IllegalStateException if the mock received no request or several requests.
     */
    public DecisionRequest request() {
        return requests.only();
    }

    /**
     * Always answers with the given answers, keyed by question name.
     */
    public static DecisionModelMock thatAlwaysAnswers(Map<String, ? extends DecisionAnswer> answers) {
        ensureNotNull(answers, "answers");
        return new DecisionModelMock(request -> answers);
    }

    /**
     * Answers with the answers computed from each request, keyed by question name.
     */
    public static DecisionModelMock thatAnswers(
            Function<DecisionRequest, Map<String, ? extends DecisionAnswer>> answers) {
        return new DecisionModelMock(answers);
    }

    /**
     * Answers every question of each request with the answer computed from the question.
     */
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
     */
    public static DecisionModelMock thatAnswersYesNoQuestions(ToDoubleFunction<YesNoQuestion> probability) {
        ensureNotNull(probability, "probability");
        return thatAnswersQuestions(question -> {
            if (!(question instanceof YesNoQuestion yesNoQuestion)) {
                throw new IllegalStateException("Expected a yes/no question, but got " + question);
            }
            return YesNoAnswer.from(probability.applyAsDouble(yesNoQuestion));
        });
    }

    /**
     * A model whose requests all fail with the given exception.
     */
    public static DecisionModelMock thatAlwaysThrows(RuntimeException exception) {
        ensureNotNull(exception, "exception");
        return new DecisionModelMock(request -> {
            throw exception;
        });
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public DecisionModelMock withListeners(DecisionModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    @Override
    public List<DecisionModelListener> listeners() {
        return listeners;
    }
}
