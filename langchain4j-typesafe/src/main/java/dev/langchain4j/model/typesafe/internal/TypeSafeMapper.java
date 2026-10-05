package dev.langchain4j.model.typesafe.internal;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.UnsupportedFeatureException;
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
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps {@link DecisionRequest}s to the System One request format and System One responses to
 * {@link DecisionResponse}s.
 */
@Internal
public final class TypeSafeMapper {

    private static final double PROBABILITY_TOLERANCE = 1e-6;

    private TypeSafeMapper() {}

    public static TypeSafeRequest toTypeSafeRequest(DecisionRequest request) {
        if (isNullOrBlank(request.modelName())) {
            throw new IllegalArgumentException("The model name must be set, either with "
                    + "TypeSafeDecisionModel.builder().modelName(...) or on the request with "
                    + "DecisionRequestParameters.builder().modelName(...)");
        }
        TypeSafeRequest typeSafeRequest = new TypeSafeRequest();
        typeSafeRequest.model = request.modelName();
        typeSafeRequest.state = request.input();
        typeSafeRequest.questions = new LinkedHashMap<>();
        request.questions().forEach((name, question) -> typeSafeRequest.questions.put(name, toQuestion(question)));
        return typeSafeRequest;
    }

    private static TypeSafeQuestion toQuestion(Question question) {
        TypeSafeQuestion result = new TypeSafeQuestion();
        result.instructions = question.text();
        if (question instanceof YesNoQuestion yesNo) {
            result.type = "noul";
            if (yesNo.yesWhen() != null || yesNo.noWhen() != null) {
                // some servers (e.g. OpenRouter) require both keys once criteria are sent
                Map<String, String> criteria = new LinkedHashMap<>();
                criteria.put("true", getOrDefault(yesNo.yesWhen(), ""));
                criteria.put("false", getOrDefault(yesNo.noWhen(), ""));
                result.criteria = criteria;
            }
        } else if (question instanceof ChoiceQuestion choice) {
            result.type = "choice";
            result.criteria = choice.options();
        } else if (question instanceof ScaleQuestion scale) {
            result.type = "score";
            result.criteria = scale.levels();
        } else {
            throw new UnsupportedFeatureException(
                    "TypeSafe does not support " + question.getClass().getName() + " questions");
        }
        return result;
    }

    public static DecisionResponse toDecisionResponse(TypeSafeResponse response, DecisionRequest request) {
        Map<String, TypeSafeAnswer> answers = getOrDefault(response.answers, Map.of());
        DecisionResponse.Builder builder = DecisionResponse.builder().modelName(response.model);
        if (response.usage != null) {
            builder.tokenUsage(new TokenUsage(response.usage.inputTokens, response.usage.outputTokens));
        }
        request.questions().forEach((name, question) -> {
            TypeSafeAnswer answer = answers.get(name);
            if (answer == null) {
                throw new InvalidDecisionResponseException("The response contains no answer to question '%s'"
                        .formatted(name));
            }
            builder.answer(name, toAnswer(name, answer, question));
        });
        return builder.build();
    }

    private static DecisionAnswer toAnswer(String name, TypeSafeAnswer answer, Question question) {
        if (question instanceof YesNoQuestion) {
            ensureType(name, answer, "noul");
            return YesNoAnswer.builder()
                    .probability(probability(name, "noul", required(name, "noul", answer.noul)))
                    .build();
        }
        if (question instanceof ChoiceQuestion) {
            ensureType(name, answer, "choice");
            String value = required(name, "choice", answer.choice);
            Map<String, Double> probabilities = new LinkedHashMap<>();
            if (answer.probabilities != null) {
                answer.probabilities.forEach((option, probability) ->
                        probabilities.put(option, probability(name, "probability of '" + option + "'", probability)));
            }
            return ChoiceAnswer.builder()
                    .value(value)
                    .probabilities(probabilities)
                    .confidence(answer.confidence == null ? null : probability(name, "confidence", answer.confidence))
                    .build();
        }
        ScaleQuestion scale = (ScaleQuestion) question;
        ensureType(name, answer, "score");
        int levels = scale.levels().size();
        List<Double> probabilities = new ArrayList<>();
        if (answer.probabilities != null && !answer.probabilities.isEmpty()) {
            answer.probabilities.keySet().forEach(level -> {
                if (!isLevelIndex(level, levels)) {
                    throw invalid(name, "has a probability for level '%s', but the levels are 0 to %s", level, levels - 1);
                }
            });
            for (int level = 0; level < levels; level++) {
                Double probability = answer.probabilities.getOrDefault(String.valueOf(level), 0.0);
                probabilities.add(probability(name, "probability of level " + level, probability));
            }
        }
        return ScaleAnswer.builder()
                .mean(level(name, required(name, "score", answer.score), levels))
                .probabilities(probabilities)
                .confidence(answer.confidence == null ? null : probability(name, "confidence", answer.confidence))
                .build();
    }

    private static void ensureType(String name, TypeSafeAnswer answer, String expectedType) {
        if (answer.type != null && !answer.type.equals(expectedType)) {
            throw invalid(name, "has type '%s' instead of '%s'", answer.type, expectedType);
        }
    }

    private static <T> T required(String name, String field, T value) {
        if (value == null) {
            throw invalid(name, "has no '%s'", field);
        }
        return value;
    }

    private static double probability(String name, String field, Double value) {
        if (value == null || value.isNaN() || value < -PROBABILITY_TOLERANCE || value > 1 + PROBABILITY_TOLERANCE) {
            throw invalid(name, "has an invalid %s: %s", field, value);
        }
        return Math.min(1.0, Math.max(0.0, value)); // absorbs rounding errors such as 1.0000000002
    }

    private static double level(String name, double value, int levels) {
        if (Double.isNaN(value) || value < -PROBABILITY_TOLERANCE || value > levels - 1 + PROBABILITY_TOLERANCE) {
            throw invalid(name, "has an invalid score: %s, but the levels are 0 to %s", value, levels - 1);
        }
        return Math.min(levels - 1, Math.max(0.0, value));
    }

    private static boolean isLevelIndex(String level, int levels) {
        try {
            int index = Integer.parseInt(level);
            return index >= 0 && index < levels && String.valueOf(index).equals(level);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static InvalidDecisionResponseException invalid(String name, String format, Object... args) {
        return new InvalidDecisionResponseException(
                "The answer to question '%s' %s".formatted(name, format.formatted(args)));
    }
}
