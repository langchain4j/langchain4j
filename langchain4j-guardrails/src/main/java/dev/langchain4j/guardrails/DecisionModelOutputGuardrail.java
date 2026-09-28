package dev.langchain4j.guardrails;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.decision.DecisionModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link OutputGuardrail} that checks the responses of the model with a {@link DecisionModel}.
 * <p>
 * Each check is a yes/no question where "yes" means the response must be rejected. All checks are answered in a
 * single call. The decision model receives the response and, when a chat memory is available, the last user message:
 * <pre>{@code
 * OutputGuardrail guardrail = DecisionModelOutputGuardrail.builder()
 *         .decisionModel(decisionModel)
 *         .check("personalData", "Does the response reveal personal data, such as contact details or account numbers?")
 *         .check("unanswered", "Does the response fail to address the user message?")
 *         .reprompt("Answer the user message without revealing personal data.")
 *         .build();
 * }</pre>
 * A rejected response fails with a fatal result or, if {@link Builder#reprompt(String)} is set, the model is asked
 * again with that instruction. Responses without text (for example, only tool calls) are not checked.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelOutputGuardrail implements OutputGuardrail {

    private final DecisionModelChecks checks;
    private final String reprompt;

    protected DecisionModelOutputGuardrail(Builder builder) {
        this.checks = new DecisionModelChecks(builder.decisionModel, builder.checks, builder.threshold);
        this.reprompt = builder.reprompt;
    }

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        String response = request.responseFromLLM().aiMessage().text();
        if (response == null || response.isBlank()) {
            return success();
        }

        Map<String, Object> input = new LinkedHashMap<>();
        String userMessage = lastUserMessage(request.requestParams().chatMemory());
        if (userMessage != null) {
            input.put("userMessage", userMessage);
        }
        input.put("response", response);

        List<String> failedChecks = checks.failedChecks(input);
        if (failedChecks.isEmpty()) {
            return success();
        }
        String message = "The response was rejected by the following checks: " + String.join(", ", failedChecks);
        return reprompt == null ? fatal(message) : reprompt(message, reprompt);
    }

    private static String lastUserMessage(ChatMemory chatMemory) {
        if (chatMemory == null) {
            return null;
        }
        List<ChatMessage> messages = chatMemory.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                String text = DecisionModelChecks.text(userMessage);
                return text.isBlank() ? null : text;
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
         * Adds a check. At least one check is required.
         *
         * @param name     the name of the check, included in the failure message, for example
         *                 {@code "personalData"}.
         * @param question a yes/no question where "yes" means the response must be rejected, for example
         *                 {@code "Does the response reveal personal data?"}.
         */
        public Builder check(String name, String question) {
            checks.put(ensureNotBlank(name, "name"), ensureNotBlank(question, "question"));
            return this;
        }

        /**
         * Sets the probability of "yes" from which a check fails.
         * <p>
         * Default value is 0.5.
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
