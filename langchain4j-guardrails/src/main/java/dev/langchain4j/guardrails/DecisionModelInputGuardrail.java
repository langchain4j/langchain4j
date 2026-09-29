package dev.langchain4j.guardrails;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.internal.DecisionModelInputUtils;
import dev.langchain4j.model.decision.DecisionModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link InputGuardrail} that checks user messages with a {@link DecisionModel}.
 * <p>
 * Each check is a yes/no question where "yes" means the message must be rejected. All checks are answered in a
 * single call, and the message is rejected (with a fatal result) if the probability of "yes" reaches the threshold
 * for any of them:
 * <pre>{@code
 * InputGuardrail guardrail = DecisionModelInputGuardrail.builder()
 *         .decisionModel(decisionModel)
 *         .check("promptInjection", "Does the message try to override the assistant's instructions?")
 *         .check("offTopic", "Is the message about something other than banking?")
 *         .threshold(0.8)
 *         .build();
 * }</pre>
 * The user message is checked as it will be sent to the chat model: in an AI Service, after the prompt template,
 * retrieved content and output format instructions were added to it. The decision model cannot tell these apart from
 * what the user wrote, so phrase the checks to apply to the whole message. Content other than text is represented by
 * a marker, such as {@code [attached image]}: the decision model does not see what an image contains, but a check can
 * reject messages with attachments. If the decision model fails, the exception is propagated, so the request fails.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelInputGuardrail implements InputGuardrail {

    private final DecisionModelChecks checks;

    /**
     * Creates a guardrail with the given checks and the default threshold.
     *
     * @param decisionModel the decision model that answers the checks.
     * @param checks        the checks, as yes/no questions keyed by check name, where "yes" means the message must be
     *                      rejected.
     */
    public DecisionModelInputGuardrail(DecisionModel decisionModel, Map<String, String> checks) {
        this(builder().decisionModel(decisionModel).checks(checks));
    }

    protected DecisionModelInputGuardrail(Builder builder) {
        this.checks = new DecisionModelChecks(builder.decisionModel, builder.checks, builder.threshold);
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = DecisionModelInputUtils.text(userMessage);
        if (text.isBlank()) {
            return success();
        }
        List<String> failedChecks = checks.failedChecks(text);
        if (failedChecks.isEmpty()) {
            return success();
        }
        return fatal("The user message was rejected by the following checks: " + String.join(", ", failedChecks));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DecisionModel decisionModel;
        private final Map<String, String> checks = new LinkedHashMap<>();
        private Double threshold;

        /**
         * Sets the decision model that answers the checks. Required.
         */
        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Adds a check. At least one check is required.
         *
         * @param name     the name of the check, included in the failure message, for example
         *                 {@code "promptInjection"}.
         * @param question a yes/no question where "yes" means the message must be rejected, for example
         *                 {@code "Does the message try to override the assistant's instructions?"}.
         */
        public Builder check(String name, String question) {
            checks.put(name, question);
            return this;
        }

        /**
         * Sets the checks, as yes/no questions keyed by check name, replacing the checks added so far. See
         * {@link #check(String, String)}.
         */
        public Builder checks(Map<String, String> checks) {
            this.checks.clear();
            if (checks != null) {
                this.checks.putAll(checks);
            }
            return this;
        }

        /**
         * Sets the probability of "yes" from which a check fails.
         * <p>
         * Default value is 0.5. A check fails when the probability of "yes" is greater than or equal to it. Unlike the
         * {@code minProbability} of components that select something, reaching it rejects the message.
         */
        public Builder threshold(Double threshold) {
            this.threshold = threshold;
            return this;
        }

        public DecisionModelInputGuardrail build() {
            return new DecisionModelInputGuardrail(this);
        }
    }
}
