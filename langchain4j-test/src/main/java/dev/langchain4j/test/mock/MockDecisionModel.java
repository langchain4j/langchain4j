package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * A {@link DecisionModel} for tests: it needs no network, answers with fixed or computed answers, and records the
 * requests it receives. It supports both {@link #decide(DecisionRequest)} and {@link #decideAsync(DecisionRequest)},
 * which completes on another thread. For example, a model that answers every yes/no question with yes:
 * <pre>{@code
 * MockDecisionModel model = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.9);
 * }</pre>
 * The model checks its answers like a real one: a request with a question that got no answer fails with an
 * {@code InvalidDecisionResponseException}.
 * A response reports the model name of the request.
 */
@Experimental
public class MockDecisionModel implements DecisionModel {

    private final Function<DecisionRequest, Map<String, ? extends DecisionAnswer>> answers;
    private final RecordedRequests<DecisionRequest> requests = new RecordedRequests<>("decision");
    private List<DecisionModelListener> listeners = List.of();

    MockDecisionModel(Function<DecisionRequest, Map<String, ? extends DecisionAnswer>> answers) {
        this.answers = ensureNotNull(answers, "answers");
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        requests.add(request);
        return respond(request);
    }

    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        requests.add(request);
        return CompletableFuture.supplyAsync(
                () -> respond(request), MockThreads.newThreadPerTask("MockDecisionModel"));
    }

    private DecisionResponse respond(DecisionRequest request) {
        return DecisionResponse.builder()
                .answers(answers.apply(request))
                .modelName(request.modelName())
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
     * Answers every request with the given answers, keyed by question name. A question without an answer makes the
     * request fail, see {@link MockDecisionModel}. {@link #thatAnswersQuestions(Function)} and
     * {@link #thatAnswersYesNoQuestions(ToDoubleFunction)} need no question names.
     */
    public static MockDecisionModel thatAnswers(Map<String, ? extends DecisionAnswer> answers) {
        Map<String, ? extends DecisionAnswer> copy =
                Collections.unmodifiableMap(new LinkedHashMap<>(ensureNotNull(answers, "answers")));
        return new MockDecisionModel(request -> copy);
    }

    /**
     * Answers with the answers computed from each request, keyed by question name.
     */
    public static MockDecisionModel thatAnswers(
            Function<? super DecisionRequest, ? extends Map<String, ? extends DecisionAnswer>> answers) {
        ensureNotNull(answers, "answers");
        return new MockDecisionModel(answers::apply);
    }

    /**
     * Answers every question of each request with the answer computed from the question.
     */
    public static MockDecisionModel thatAnswersQuestions(Function<Question, ? extends DecisionAnswer> answer) {
        ensureNotNull(answer, "answer");
        return new MockDecisionModel(request -> {
            Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
            request.questions().forEach((name, question) -> answers.put(name, answer.apply(question)));
            return answers;
        });
    }

    /**
     * Answers every yes/no question of each request with the probability computed from the question. A request with a
     * question that is not a yes/no question fails with an {@link IllegalStateException}.
     */
    public static MockDecisionModel thatAnswersYesNoQuestions(ToDoubleFunction<YesNoQuestion> probability) {
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
    public static MockDecisionModel thatFails(RuntimeException exception) {
        ensureNotNull(exception, "exception");
        return new MockDecisionModel(request -> {
            throw exception;
        });
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockDecisionModel withListeners(DecisionModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockDecisionModel withListeners(List<DecisionModelListener> listeners) {
        this.listeners = List.copyOf(listeners);
        return this;
    }

    @Override
    public List<DecisionModelListener> listeners() {
        return listeners;
    }
}
