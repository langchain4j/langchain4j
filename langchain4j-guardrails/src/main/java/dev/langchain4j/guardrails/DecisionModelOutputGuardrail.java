package dev.langchain4j.guardrails;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.internal.DecisionModelInputUtils;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.decision.DecisionModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link OutputGuardrail} that checks the responses of the model with a {@link DecisionModel}.
 * <p>
 * Each check is a yes/no question where "yes" means the response must be rejected. All checks are answered in a
 * single call. The decision model receives the response and the last user message:
 * <pre>{@code
 * OutputGuardrail guardrail = DecisionModelOutputGuardrail.builder()
 *         .decisionModel(decisionModel)
 *         .check("personalData", "Does the response reveal personal data, such as contact details or account numbers?")
 *         .check("unanswered", "Does the response fail to address the user message?")
 *         .reprompt("Answer the user message without revealing personal data.")
 *         .build();
 * }</pre>
 * A rejected response fails with a fatal result or, if {@link Builder#reprompt(String)} is set, the model is asked
 * again with that instruction. Responses without text (for example, only tool calls) are not checked. If the decision
 * model fails, the exception is propagated, so the request fails.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelOutputGuardrail implements OutputGuardrail {

    private final DecisionModelChecks checks;
    private final String reprompt;

    /**
     * Creates a guardrail with the given checks and the default threshold.
     *
     * @param decisionModel the decision model that answers the checks.
     * @param checks        the checks, as yes/no questions keyed by check name, where "yes" means the response must be
     *                      rejected.
     */
    public DecisionModelOutputGuardrail(DecisionModel decisionModel, Map<String, String> checks) {
        this(builder().decisionModel(decisionModel).checks(checks));
    }

    protected DecisionModelOutputGuardrail(Builder builder) {
        this.checks = new DecisionModelChecks(builder.decisionModel, builder.checks, builder.checkThresholds, builder.threshold);
        this.reprompt = builder.reprompt == null ? null : ensureNotBlank(builder.reprompt, "reprompt");
    }

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        String response = request.responseFromLLM().aiMessage().text();
        if (response == null || response.isBlank()) {
            return success();
        }

        Map<String, Object> input = new LinkedHashMap<>();
        String userMessage = userMessage(request.requestParams());
        if (userMessage != null) {
            input.put("userMessage", userMessage);
        }
        input.put("response", response);

        List<String> failedChecks = checks.failedChecks(input);
        if (failedChecks.isEmpty()) {
            return success();
        }
        String message = failureMessage(failedChecks);
        return reprompt == null ? fatal(message) : reprompt(message, reprompt);
    }

    /**
     * The message of the failure when checks fail. Override it, for example, to hide which checks failed from users
     * who can see the message.
     *
     * @param failedChecks the names of the failed checks.
     */
    protected String failureMessage(List<String> failedChecks) {
        return "The response was rejected by the following checks: " + String.join(", ", failedChecks);
    }

    private static String userMessage(GuardrailRequestParams params) {
        UserMessage userMessage = lastUserMessage(params.chatMemory());
        if (userMessage == null && params.invocationContext() != null) {
            userMessage = params.invocationContext().userMessage();
        }
        if (userMessage == null) {
            return null;
        }
        String text = DecisionModelInputUtils.text(userMessage);
        return text.isBlank() ? null : text;
    }

    private static UserMessage lastUserMessage(ChatMemory chatMemory) {
        if (chatMemory == null) {
            return null;
        }
        List<ChatMessage> messages = chatMemory.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage;
            }
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DecisionModel decisionModel;
        private final Map<String, String> checks = new LinkedHashMap<>();
        private final Map<String, Double> checkThresholds = new LinkedHashMap<>();
        private Double threshold;
        private String reprompt;

        /**
         * Sets the decision model that answers the checks. Required.
         */
        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Adds a check. At least one check is required, and check names must be unique.
         *
         * @param name     the name of the check, included in the failure message, for example
         *                 {@code "personalData"}.
         * @param question a yes/no question where "yes" means the response must be rejected, for example
         *                 {@code "Does the response reveal personal data?"}.
         */
        public Builder check(String name, String question) {
            return check(name, question, null);
        }

        /**
         * Adds a check with its own threshold, which overrides the threshold of the guardrail (see
         * {@link #threshold(Double)}) for this check: for example, a low threshold for a check that must rarely
         * miss, and a high one for a check that should only reject clear cases. All checks are still answered in a
         * single call.
         *
         * @param name      the name of the check, see {@link #check(String, String)}.
         * @param question  a yes/no question where "yes" means the response must be rejected.
         * @param threshold the probability of "yes", from 0 to 1, from which this check fails, or {@code null} to use the
         *                  threshold of the guardrail.
         */
        public Builder check(String name, String question, Double threshold) {
            if (checks.containsKey(name)) {
                throw new IllegalArgumentException("There is more than one check named '%s'".formatted(name));
            }
            checks.put(name, question);
            if (threshold != null) {
                checkThresholds.put(name, threshold);
            }
            return this;
        }

        /**
         * Sets the checks, as yes/no questions keyed by check name, replacing the checks added so far. See
         * {@link #check(String, String)}.
         */
        public Builder checks(Map<String, String> checks) {
            this.checks.clear();
            this.checkThresholds.clear();
            if (checks != null) {
                this.checks.putAll(checks);
            }
            return this;
        }

        /**
         * Sets the probability of "yes" from which a check fails, for the checks without their own threshold (see
         * {@link #check(String, String, Double)}).
         * <p>
         * Default value is 0.5. A check fails when the probability of "yes" is greater than or equal to it. Unlike the
         * {@code minProbability} of components that select something, reaching it rejects the response.
         */
        public Builder threshold(Double threshold) {
            this.threshold = threshold;
            return this;
        }

        /**
         * Sets the instruction sent to the model when a response is rejected, to ask it for a new response. Optional:
         * by default, a rejected response fails with a fatal result.
         */
        public Builder reprompt(String reprompt) {
            this.reprompt = reprompt;
            return this;
        }

        public DecisionModelOutputGuardrail build() {
            return new DecisionModelOutputGuardrail(this);
        }
    }
}
