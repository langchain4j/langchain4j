package dev.langchain4j.guardrails;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The yes/no checks of a decision model guardrail, all answered in a single call. A check fails when the probability
 * of "yes" reaches its threshold: the threshold of the check, if set, otherwise the threshold of the guardrail.
 */
final class DecisionModelChecks {

    private static final Logger log = LoggerFactory.getLogger(DecisionModelChecks.class);

    private static final double DEFAULT_THRESHOLD = 0.5;

    private final DecisionModel decisionModel;
    private final Map<String, YesNoQuestion> questions;
    private final Map<String, Double> thresholds;

    DecisionModelChecks(
            DecisionModel decisionModel,
            Map<String, String> questions,
            Map<String, Double> checkThresholds,
            Double threshold) {
        this.decisionModel = ensureNotNull(decisionModel, "decisionModel");
        Map<String, YesNoQuestion> yesNoQuestions = new LinkedHashMap<>();
        ensureNotEmpty(questions, "checks")
                .forEach((name, text) -> yesNoQuestions.put(
                        ensureNotBlank(name, "check name"),
                        YesNoQuestion.of(ensureNotBlank(text, "question of check '%s'".formatted(name)))));
        this.questions = yesNoQuestions;
        double defaultThreshold = ensureBetween(getOrDefault(threshold, DEFAULT_THRESHOLD), 0, 1, "threshold");
        Map<String, Double> thresholds = new LinkedHashMap<>();
        yesNoQuestions.keySet().forEach(name -> thresholds.put(
                name,
                checkThresholds.containsKey(name)
                        ? ensureBetween(checkThresholds.get(name), 0, 1, "threshold of check '%s'".formatted(name))
                        : defaultThreshold));
        this.thresholds = thresholds;
    }

    /**
     * Returns the names of the failed checks. The probabilities are only logged (at DEBUG level), so that they do not
     * reach the users through the failure message, where they would show how close a rejected input came to passing.
     */
    List<String> failedChecks(String input) {
        return failedChecks(DecisionRequest.builder().input(input));
    }

    List<String> failedChecks(Map<String, ?> input) {
        return failedChecks(DecisionRequest.builder().input(input));
    }

    private List<String> failedChecks(DecisionRequest.Builder request) {
        DecisionResponse response = decisionModel.decide(request.questions(questions).build());
        List<String> failedChecks = questions.keySet().stream()
                .filter(name -> response.yesNo(name).isYes(thresholds.get(name)))
                .toList();
        if (log.isDebugEnabled()) {
            failedChecks.forEach(name -> log.debug(
                    "Check '{}' failed with a probability of {}", name, response.yesNo(name).probability()));
        }
        return failedChecks;
    }
}
