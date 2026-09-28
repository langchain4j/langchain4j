package dev.langchain4j.guardrails;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The yes/no checks of a decision model guardrail, all answered in a single call. A check fails when the probability
 * of "yes" reaches the threshold.
 */
final class DecisionModelChecks {

    private static final double DEFAULT_THRESHOLD = 0.5;

    private final DecisionModel decisionModel;
    private final Map<String, YesNoQuestion> questions;
    private final double threshold;

    DecisionModelChecks(DecisionModel decisionModel, Map<String, String> questions, Double threshold) {
        this.decisionModel = ensureNotNull(decisionModel, "decisionModel");
        Map<String, YesNoQuestion> yesNoQuestions = new LinkedHashMap<>();
        ensureNotEmpty(questions, "questions").forEach((name, text) -> yesNoQuestions.put(name, YesNoQuestion.of(text)));
        this.questions = yesNoQuestions;
        this.threshold = ensureBetween(getOrDefault(threshold, DEFAULT_THRESHOLD), 0, 1, "threshold");
    }

    /**
     * Returns the names of the failed checks, with the probability of "yes", for example
     * {@code promptInjection (0.97)}.
     */
    List<String> failedChecks(Object input) {
        DecisionResponse response = decisionModel.decide(
                DecisionRequest.builder().input(input).questions(questions).build());
        return questions.keySet().stream()
                .filter(name -> response.yesNo(name).isYes(threshold))
                .map(name -> "%s (%.2f)".formatted(name, response.yesNo(name).probability()))
                .toList();
    }

    static String text(UserMessage userMessage) {
        return userMessage.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(joining("\n"));
    }
}
