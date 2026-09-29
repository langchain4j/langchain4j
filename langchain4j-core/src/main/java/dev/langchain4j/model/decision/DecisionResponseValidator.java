package dev.langchain4j.model.decision;

import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Checks that a response matches its request, whatever the {@link DecisionModel} implementation: every question has
 * an answer of the matching type, a choice answer chooses one of the offered options, and a scale answer stays within
 * the levels. Answers to question types other than the built-in ones are not checked.
 * <p>
 * Choice answers are returned with the names of the offered options, so that
 * {@link ChoiceAnswer#probabilityOf(String)} can reject a misspelled option.
 */
final class DecisionResponseValidator {

    private DecisionResponseValidator() {}

    static DecisionResponse validate(DecisionRequest request, DecisionResponse response) {
        if (response == null) {
            throw new InvalidDecisionResponseException("The decision model returned no response");
        }
        Map<String, DecisionAnswer> answers = new LinkedHashMap<>(response.answers());
        request.questions().forEach((name, question) -> {
            validate(name, question, answers.get(name));
            if (question instanceof ChoiceQuestion choice && answers.get(name) instanceof ChoiceAnswer answer) {
                answers.put(name, withOptions(answer, choice));
            }
        });
        return DecisionResponse.builder()
                .answers(answers)
                .metadata(response.metadata())
                .build();
    }

    private static ChoiceAnswer withOptions(ChoiceAnswer answer, ChoiceQuestion question) {
        return ChoiceAnswer.builder()
                .value(answer.value())
                .probabilities(answer.probabilities())
                .confidence(answer.confidence())
                .options(question.options().keySet())
                .build();
    }

    private static void validate(String name, Question question, DecisionAnswer answer) {
        if (answer == null) {
            throw new InvalidDecisionResponseException(
                    "The response contains no answer to question '%s'".formatted(name));
        }
        if (question instanceof YesNoQuestion) {
            ensureType(name, answer, YesNoAnswer.class);
        } else if (question instanceof ChoiceQuestion choice) {
            validate(name, choice, ensureType(name, answer, ChoiceAnswer.class));
        } else if (question instanceof ScaleQuestion scale) {
            validate(name, scale, ensureType(name, answer, ScaleAnswer.class));
        }
    }

    private static void validate(String name, ChoiceQuestion question, ChoiceAnswer answer) {
        if (!question.options().containsKey(answer.value())) {
            throw invalid(name, "chose '%s', which is not one of the options %s",
                    answer.value(), question.options().keySet());
        }
        answer.probabilities().keySet().forEach(option -> {
            if (!question.options().containsKey(option)) {
                throw invalid(name, "has a probability for '%s', which is not one of the options %s",
                        option, question.options().keySet());
            }
        });
    }

    private static void validate(String name, ScaleQuestion question, ScaleAnswer answer) {
        int levels = question.levels().size();
        if (answer.mean() < 0 || answer.mean() > levels - 1) {
            throw invalid(name, "has a mean of %s, but the levels are 0 to %s", answer.mean(), levels - 1);
        }
        if (!answer.probabilities().isEmpty() && answer.probabilities().size() != levels) {
            throw invalid(name, "has %s level probabilities, but the question has %s levels",
                    answer.probabilities().size(), levels);
        }
    }

    private static <T extends DecisionAnswer> T ensureType(String name, DecisionAnswer answer, Class<T> type) {
        if (!type.isInstance(answer)) {
            throw invalid(name, "is a %s, but a %s is expected",
                    answer.getClass().getSimpleName(), type.getSimpleName());
        }
        return type.cast(answer);
    }

    private static InvalidDecisionResponseException invalid(String name, String format, Object... args) {
        return new InvalidDecisionResponseException(
                "The answer to question '%s' %s".formatted(name, format.formatted(args)));
    }
}
