package dev.langchain4j.model.decision.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.synchronizedList;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A {@link DecisionModel} for tests: it answers with fixed or computed answers and records the requests it receives.
 * It supports both {@link #decide(DecisionRequest)} and {@link #decideAsync(DecisionRequest)}.
 */
public class DecisionModelMock implements DecisionModel {

    private final Function<DecisionRequest, Map<String, ? extends DecisionAnswer>> answers;
    private final List<DecisionRequest> requests = synchronizedList(new ArrayList<>());

    public DecisionModelMock(Function<DecisionRequest, Map<String, ? extends DecisionAnswer>> answers) {
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
        return CompletableFuture.supplyAsync(() -> respond(request));
    }

    private DecisionResponse respond(DecisionRequest request) {
        return DecisionResponse.builder().answers(answers.apply(request)).build();
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
     * Answers every yes/no question of each request with the probability computed from the question.
     */
    public static DecisionModelMock thatAnswersYesNoQuestions(Function<YesNoQuestion, Double> probability) {
        ensureNotNull(probability, "probability");
        return new DecisionModelMock(request -> {
            Map<String, YesNoAnswer> answers = new LinkedHashMap<>();
            request.questions().forEach((name, question) ->
                    answers.put(name, YesNoAnswer.of(probability.apply((YesNoQuestion) question))));
            return answers;
        });
    }

    public static DecisionModelMock thatAlwaysThrowsException() {
        return thatAlwaysThrowsExceptionWithMessage("Something went wrong, but this is an expected exception");
    }

    public static DecisionModelMock thatAlwaysThrowsExceptionWithMessage(String message) {
        return new DecisionModelMock(request -> {
            throw new RuntimeException(message);
        });
    }
}
