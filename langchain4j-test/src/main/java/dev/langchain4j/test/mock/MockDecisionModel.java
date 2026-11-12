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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * A {@link DecisionModel} for tests: it needs no network, answers the way the test tells it to, and records the
 * requests it receives.
 * <p>
 * Script the answers step by step, one step per request. For example, a model that answers every yes/no question with
 * yes:
 * <pre>{@code
 * MockDecisionModel model = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.9).thenRepeats();
 * }</pre>
 * Every {@code that...} method creates a mock with its first step, and every {@code then...} method adds a step. Each
 * step answers exactly one request. A request after the last step fails with an {@link IllegalStateException} that
 * shows the questions of the request, so a test notices when the code asks the model more often than the script
 * expects, unless the code under test handles the error itself. {@code decide(...)} throws it, and
 * {@code decideAsync(...)} returns a future that fails with it. To answer every further request with the last step,
 * end the script with {@link #thenRepeats()}.
 * <p>
 * A script can give different answers to the same question, for example to a loop that asks whether its task is done
 * and stops at the third answer:
 * <pre>{@code
 * MockDecisionModel model = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.1)
 *         .thenAnswersYesNoQuestions(question -> 0.1)
 *         .thenAnswersYesNoQuestions(question -> 0.9);
 * }</pre>
 * It can also let the first request fail and the next one succeed, for example to test your own retry logic:
 * <pre>{@code
 * MockDecisionModel model = MockDecisionModel.thatFails(new RateLimitException("Too many requests"))
 *         .thenAnswersYesNoQuestions(question -> 0.9);
 * }</pre>
 * A step can compute its answers from the request with {@link #thatAnswers(Function)},
 * {@link #thatAnswersQuestions(Function)} or {@link #thatAnswersYesNoQuestions(ToDoubleFunction)}, for example to
 * answer concurrent requests by their questions: {@code thatAnswersQuestions(question -> ...).thenRepeats()}. If the
 * function throws, the request fails with that exception, unchanged.
 * <p>
 * The model checks its answers like a real one: a request with a question that got no answer fails with an
 * {@code InvalidDecisionResponseException}. A response reports the model name of the request.
 * {@code decideAsync(...)} completes on another thread, where a step that computes its answers also computes them.
 * <p>
 * A mock remembers which steps were taken and which requests it received, so create a new one for each test. A mock
 * shared between tests, for example as a bean, fails with a clear message as soon as its script runs out, unless the
 * script ends with {@link #thenRepeats()}. The steps are taken in the order the requests arrive. A mock keeps every
 * request it receives, so it is not meant for load tests or other long runs.
 */
@Experimental
public class MockDecisionModel implements DecisionModel {

    private static final String MOCK_NAME = "MockDecisionModel";
    private static final int MAX_TEXT_LENGTH = 200;

    /**
     * One step of the script: the answers to one request, keyed by question name.
     */
    private interface Step {

        Map<String, ? extends DecisionAnswer> answer(DecisionRequest request);
    }

    private final List<Step> steps = new ArrayList<>();
    private final RecordedRequests<DecisionRequest> requests = new RecordedRequests<>("decision");
    private int nextIndex;
    private boolean repeatsLastStep;
    private List<DecisionModelListener> listeners = List.of();

    private MockDecisionModel(Step firstStep) {
        steps.add(firstStep);
    }

    /**
     * Adds a step: the next request is answered with the given answers, keyed by question name.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockDecisionModel thenAnswers(Map<String, ? extends DecisionAnswer> answers) {
        return add(fixed(answers));
    }

    /**
     * Adds a step: the next request is answered with the answers computed from it, keyed by question name.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockDecisionModel thenAnswers(
            Function<? super DecisionRequest, ? extends Map<String, ? extends DecisionAnswer>> answers) {
        return add(computed(answers));
    }

    /**
     * Adds a step: every question of the next request is answered with the answer computed from the question.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockDecisionModel thenAnswersQuestions(Function<Question, ? extends DecisionAnswer> answer) {
        return add(perQuestion(answer));
    }

    /**
     * Adds a step: every yes/no question of the next request is answered with the probability computed from the
     * question, see {@link #thatAnswersYesNoQuestions(ToDoubleFunction)}.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockDecisionModel thenAnswersYesNoQuestions(ToDoubleFunction<YesNoQuestion> probability) {
        return add(yesNo(probability));
    }

    /**
     * Adds a step: the next request fails with exactly the given exception.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockDecisionModel thenFails(RuntimeException exception) {
        return add(failing(exception));
    }

    /**
     * Answers every further request with the last step of the script. Only the last step repeats; the script does not
     * start over. A last step that computes its answers computes them again for each request. Without it, a request
     * after the last step fails. It must be the last call of the script.
     *
     * @throws IllegalStateException if the script already ends with {@code thenRepeats()}
     */
    public synchronized MockDecisionModel thenRepeats() {
        ensureNotRepeating();
        repeatsLastStep = true;
        return this;
    }

    private synchronized MockDecisionModel add(Step step) {
        ensureNotRepeating();
        steps.add(step);
        return this;
    }

    private void ensureNotRepeating() {
        if (repeatsLastStep) {
            throw new IllegalStateException("thenRepeats() must be the last call of the script: the last"
                    + " step already answers every further request");
        }
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        return respond(take(request), request);
    }

    /**
     * Answers like {@link #doDecide(DecisionRequest)}, with the next step of the script, and completes the future on
     * another thread, where a step that computes its answers also computes them.
     */
    @Override
    public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
        Step step = take(request);
        return CompletableFuture.supplyAsync(() -> respond(step, request), MockThreads.newThreadPerTask(MOCK_NAME));
    }

    /**
     * Records the request and takes the step that answers it, in one go, so that concurrent requests get consistent
     * request numbers. A request after the last step gets a step that fails with an {@link IllegalStateException}.
     */
    private synchronized Step take(DecisionRequest request) {
        int requestNumber = requests.add(request);
        if (nextIndex < steps.size()) {
            return steps.get(nextIndex++);
        }
        if (repeatsLastStep) {
            return steps.get(steps.size() - 1);
        }
        return failing(new IllegalStateException(runOutMessage(request, requestNumber)));
    }

    private static DecisionResponse respond(Step step, DecisionRequest request) {
        return DecisionResponse.builder()
                .answers(step.answer(request))
                .modelName(request.modelName())
                .build();
    }

    private String runOutMessage(DecisionRequest request, int requestNumber) {
        StringBuilder message = new StringBuilder()
                .append(MOCK_NAME)
                .append(" received request ")
                .append(requestNumber)
                .append(", but its script has only ")
                .append(steps.size())
                .append(steps.size() == 1 ? " step. " : " steps. ")
                .append("Script one more step, or end the script with thenRepeats() to answer every further request"
                        + " with its last step. If the mock is shared between tests, for example as a bean, create a"
                        + " new one for each test. The questions of request ")
                .append(requestNumber)
                .append(":");
        request.questions().forEach((name, question) -> message.append(" ")
                .append(name)
                .append(" \"")
                .append(shortened(question.text()))
                .append("\""));
        return message.toString();
    }

    private static String shortened(String text) {
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) + "..." : text;
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
     * The last request received so far.
     *
     * @throws IllegalStateException if the mock received no request
     */
    public DecisionRequest lastRequest() {
        return requests.last();
    }

    /**
     * A model that answers the first request with the given answers, keyed by question name. A question without an
     * answer makes the request fail, see {@link MockDecisionModel}. {@link #thatAnswersQuestions(Function)} and
     * {@link #thatAnswersYesNoQuestions(ToDoubleFunction)} need no question names. Add the next steps with
     * {@code then...} methods, or answer every request with these answers: {@code thatAnswers(answers).thenRepeats()}.
     */
    public static MockDecisionModel thatAnswers(Map<String, ? extends DecisionAnswer> answers) {
        return new MockDecisionModel(fixed(answers));
    }

    /**
     * A model that answers the first request with the answers computed from it, keyed by question name. Add the next
     * steps with {@code then...} methods, or compute the answers to every request with {@code thenRepeats()}.
     */
    public static MockDecisionModel thatAnswers(
            Function<? super DecisionRequest, ? extends Map<String, ? extends DecisionAnswer>> answers) {
        return new MockDecisionModel(computed(answers));
    }

    /**
     * A model that answers every question of the first request with the answer computed from the question. Add the
     * next steps with {@code then...} methods, or answer the questions of every request with {@code thenRepeats()}.
     */
    public static MockDecisionModel thatAnswersQuestions(Function<Question, ? extends DecisionAnswer> answer) {
        return new MockDecisionModel(perQuestion(answer));
    }

    /**
     * A model that answers every yes/no question of the first request with the probability computed from the
     * question. A request with a question that is not a yes/no question fails with an {@link IllegalStateException}.
     * Add the next steps with {@code then...} methods, or answer the questions of every request with
     * {@code thenRepeats()}.
     */
    public static MockDecisionModel thatAnswersYesNoQuestions(ToDoubleFunction<YesNoQuestion> probability) {
        return new MockDecisionModel(yesNo(probability));
    }

    /**
     * A model whose first request fails with exactly the given exception. To let every request fail, add
     * {@link #thenRepeats()}. To let later requests succeed, for example to test your own retry logic, add steps with
     * {@code then...} methods.
     */
    public static MockDecisionModel thatFails(RuntimeException exception) {
        return new MockDecisionModel(failing(exception));
    }

    private static Step fixed(Map<String, ? extends DecisionAnswer> answers) {
        Map<String, ? extends DecisionAnswer> copy =
                Collections.unmodifiableMap(new LinkedHashMap<>(ensureNotNull(answers, "answers")));
        return request -> copy;
    }

    private static Step computed(
            Function<? super DecisionRequest, ? extends Map<String, ? extends DecisionAnswer>> answers) {
        ensureNotNull(answers, "answers");
        return answers::apply;
    }

    private static Step perQuestion(Function<Question, ? extends DecisionAnswer> answer) {
        ensureNotNull(answer, "answer");
        return request -> {
            Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
            request.questions().forEach((name, question) -> answers.put(name, answer.apply(question)));
            return answers;
        };
    }

    private static Step yesNo(ToDoubleFunction<YesNoQuestion> probability) {
        ensureNotNull(probability, "probability");
        return perQuestion(question -> {
            if (!(question instanceof YesNoQuestion yesNoQuestion)) {
                throw new IllegalStateException("Expected a yes/no question, but got " + question);
            }
            return YesNoAnswer.from(probability.applyAsDouble(yesNoQuestion));
        });
    }

    private static Step failing(RuntimeException exception) {
        ensureNotNull(exception, "exception");
        return request -> {
            throw exception;
        };
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
