package dev.langchain4j.agentic.decision;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Factories of {@link MockDecisionModel}s answering the questions of the agents of {@link DecisionAgents}.
 */
final class DecisionMocks {

    private DecisionMocks() {}

    /** Answers every choice question with the given option and probability, the rest of the probability shared. */
    static MockDecisionModel choosing(String option, double probability) {
        return MockDecisionModel.thatAnswersQuestions(question -> {
            if (question instanceof ChoiceQuestion choice) {
                return choiceAnswer(choice, option, probability);
            }
            throw new IllegalArgumentException("Unexpected question: " + question);
        });
    }

    static ChoiceAnswer choiceAnswer(ChoiceQuestion question, String option, double probability) {
        List<String> options = List.copyOf(question.options().keySet());
        Map<String, Double> probabilities = new LinkedHashMap<>();
        double rest = options.size() > 1 ? (1 - probability) / (options.size() - 1) : 0;
        options.forEach(name -> probabilities.put(name, name.equals(option) ? probability : rest));
        return ChoiceAnswer.builder()
                .value(option)
                .probabilities(probabilities)
                .options(options)
                .build();
    }

    /** Answers every yes/no question with the given probability of "yes". */
    static MockDecisionModel answeringYes(double probability) {
        return MockDecisionModel.thatAnswersYesNoQuestions(question -> probability);
    }

    /** Answers every scale question with the given level, with the given probability. */
    static MockDecisionModel scaling(String level, double probability) {
        return MockDecisionModel.thatAnswersQuestions(question -> {
            if (question instanceof ScaleQuestion scale) {
                return scaleAnswer(scale, level, probability);
            }
            throw new IllegalArgumentException("Unexpected question: " + question);
        });
    }

    static ScaleAnswer scaleAnswer(ScaleQuestion question, String level, double probability) {
        List<String> levels = question.levels();
        double rest = levels.size() > 1 ? (1 - probability) / (levels.size() - 1) : 0;
        List<Double> probabilities = levels.stream()
                .map(name -> name.equals(level) ? probability : rest)
                .toList();
        // the probability-weighted mean of the level indexes, from 0 to n - 1
        double mean = 0;
        for (int i = 0; i < levels.size(); i++) {
            mean += probabilities.get(i) * i;
        }
        return ScaleAnswer.builder().mean(mean).probabilities(probabilities).build();
    }

    /** Answers a triage: the team as a choice, the urgency as a yes/no and the sentiment as a choice. */
    static MockDecisionModel triaging(String team, double urgency, String sentiment) {
        return MockDecisionModel.thatAnswersQuestions(question -> {
            if (question instanceof YesNoQuestion) {
                return YesNoAnswer.from(urgency);
            }
            ChoiceQuestion choice = (ChoiceQuestion) question;
            String option = choice.options().containsKey(team) ? team : sentiment;
            return choiceAnswer(choice, option, 0.9);
        });
    }

    static DecisionAnswer yes(double probability) {
        return YesNoAnswer.from(probability);
    }

    /** The given model, reporting the given token usage in every response. */
    @SuppressWarnings("unchecked")
    static MockDecisionModel withTokenUsage(MockDecisionModel model, TokenUsage tokenUsage) {
        MockDecisionModel reporting = spy(model);
        doAnswer(invocation -> withTokenUsage((DecisionResponse) invocation.callRealMethod(), tokenUsage))
                .when(reporting)
                .doDecide(any());
        doAnswer(invocation -> ((CompletableFuture<DecisionResponse>) invocation.callRealMethod())
                        .thenApply(response -> withTokenUsage(response, tokenUsage)))
                .when(reporting)
                .doDecideAsync(any());
        return reporting;
    }

    private static DecisionResponse withTokenUsage(DecisionResponse response, TokenUsage tokenUsage) {
        return DecisionResponse.builder()
                .answers(response.answers())
                .tokenUsage(tokenUsage)
                .build();
    }
}
