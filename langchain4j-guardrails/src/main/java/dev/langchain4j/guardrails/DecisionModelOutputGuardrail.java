package dev.langchain4j.guardrails;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.internal.DecisionModelInputUtils;
import dev.langchain4j.model.decision.DecisionModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * An {@link OutputGuardrail} that checks the responses of the model with a {@link DecisionModel}.
 * <p>
 * Each check is a yes/no question where "yes" means the response must be rejected. All checks are answered in a
 * single call. The decision model receives the conversation that led to the response, ending with the response:
 * the messages sent to the model to produce the response ({@link OutputGuardrailRequest#chatRequest()}), including
 * the system message, tool calls and tool results, so that checks can compare the response with the instructions of
 * the assistant and with the data its tools returned (see {@link Builder#maxMessages(Integer)},
 * {@link Builder#maxMessageLength(Integer)} and {@link Builder#messageFilter(Predicate)}). The user message comes with
 * the content retrieved for it (RAG), and after a reprompt, the reprompt and its tool calls are included. When the
 * request is not known, the messages are taken from the chat memory, or, without one, the user message is sent with
 * the response. Each question is preceded by a fixed English sentence saying that the response is the last assistant
 * message of the conversation. For example:
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
 * model fails, the exception is propagated, so the request fails. A check that the decision model refuses to answer
 * fails, so the response is rejected.
 *
 * @since 1.21.0
 */
@Experimental
public class DecisionModelOutputGuardrail implements OutputGuardrail {

    private static final int DEFAULT_MAX_MESSAGES = 50;
    private static final int DEFAULT_MAX_MESSAGE_LENGTH = 50000;
    // without it, decision models judge checks about "the response" against the whole conversation
    private static final String RESPONSE_IS_LAST_MESSAGE = "The response is the assistant's last message in the conversation. ";

    private final DecisionModelChecks checks;
    private final Predicate<? super ChatMessage> messageFilter;
    private final int maxMessages;
    private final int maxMessageLength;
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
        this.checks = new DecisionModelChecks(
                builder.decisionModel, builder.checks, builder.checkThresholds, builder.threshold, RESPONSE_IS_LAST_MESSAGE);
        this.reprompt = builder.reprompt == null ? null : ensureNotBlank(builder.reprompt, "reprompt");
        this.messageFilter = getOrDefault(builder.messageFilter, message -> true);
        this.maxMessages = ensureGreaterThanZero(getOrDefault(builder.maxMessages, DEFAULT_MAX_MESSAGES), "maxMessages");
        this.maxMessageLength = ensureGreaterThanZero(
                getOrDefault(builder.maxMessageLength, DEFAULT_MAX_MESSAGE_LENGTH), "maxMessageLength");
    }

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        AiMessage response = request.responseFromLLM().aiMessage();
        if (response.text() == null || response.text().isBlank()) {
            return success();
        }

        List<String> failedChecks = checks.failedChecks(conversation(request, response));
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

    /**
     * The messages sent to the model to produce the response, followed by the response (without its tool calls).
     */
    private List<ChatMessage> conversation(OutputGuardrailRequest request, AiMessage response) {
        List<ChatMessage> messages = request.chatRequest() != null
                ? new ArrayList<>(request.chatRequest().messages())
                : messagesFromChatMemory(request.requestParams());
        List<ChatMessage> conversation =
                new ArrayList<>(DecisionModelInputUtils.window(messages, messageFilter, maxMessages, maxMessageLength));
        conversation.add(AiMessage.from(response.text()));
        return conversation;
    }

    /**
     * For callers that do not provide the request sent to the model: the chat memory (or the user message, without
     * one), with the user message replaced by the one with the retrieved content, which the chat memory may not hold.
     */
    private static List<ChatMessage> messagesFromChatMemory(GuardrailRequestParams params) {
        List<ChatMessage> messages = new ArrayList<>();
        if (params.chatMemory() != null) {
            messages.addAll(params.chatMemory().messages());
        } else if (params.invocationContext() != null && params.invocationContext().userMessage() != null) {
            messages.add(params.invocationContext().userMessage());
        }
        if (params.augmentationResult() != null
                && params.augmentationResult().chatMessage() instanceof UserMessage augmented) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof UserMessage) {
                    messages.set(i, augmented);
                    break;
                }
            }
        }
        // the response and the responses rejected before it may be in the chat memory
        while (!messages.isEmpty()
                && messages.get(messages.size() - 1) instanceof AiMessage last
                && !last.hasToolExecutionRequests()) {
            messages.remove(messages.size() - 1);
        }
        return messages;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DecisionModel decisionModel;
        private Predicate<? super ChatMessage> messageFilter;
        private Integer maxMessages;
        private Integer maxMessageLength;
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

        /**
         * Sets the maximum number of messages of the conversation that the decision model receives in addition to the
         * response: the last messages, plus the last user message if it is older. An AI message with tool calls and
         * each tool result count as one message each. Tool results whose AI message does not fit are left out. With
         * parallel tool calls, set it higher than the number of tool calls in one round. With 1, system messages are
         * still sent; leave them out with {@link #messageFilter(Predicate)}. System messages are sent in addition and
         * do not count. Checks of the response against the data of the tools need the messages with that data, so keep
         * it large enough. For decision models with a small input limit, such as local models, set it lower (for
         * example, {@code maxMessages(4)} and {@code maxMessageLength(4000)} help keep a request to Ollama under its
         * 64 KiB limit; the system message and the response are always sent in full).
         * <p>
         * Default value is {@value DecisionModelOutputGuardrail#DEFAULT_MAX_MESSAGES}.
         * @since 1.23.0
         */
        public Builder maxMessages(Integer maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        /**
         * Sets the maximum length, in characters, of the text of each message the decision model receives, other than
         * system messages and the response, and of the arguments of each tool call. A longer text is shortened in the middle:
         * its beginning and its end are kept, with a note of about 90 characters saying how many characters were left
         * out. Long tool results and user messages with retrieved content are
         * the usual cases. A check that compares the response with data that was left out tends to reject the
         * response, even when it is correct, so raise it for checks against long tool results. As a rough guide,
         * 4 characters make one token in English text.
         * <p>
         * Default value is {@value DecisionModelOutputGuardrail#DEFAULT_MAX_MESSAGE_LENGTH}.
         * @since 1.23.0
         */
        public Builder maxMessageLength(Integer maxMessageLength) {
            this.maxMessageLength = maxMessageLength;
            return this;
        }

        /**
         * Sets which messages of the conversation the decision model receives, in addition to the response. For
         * example, {@code message -> !(message instanceof SystemMessage)} leaves out the system message, and
         * {@code message -> !(message instanceof ToolExecutionResultMessage)} leaves out tool results, which can be
         * large.
         * <p>
         * By default, all messages are sent: system messages, user messages, AI messages with their tool calls, and
         * tool results. The filter also applies to the last user message, but not to the response. Keep or leave out
         * an AI message with tool calls together with its tool results.
         * @since 1.23.0
         */
        public Builder messageFilter(Predicate<? super ChatMessage> messageFilter) {
            this.messageFilter = messageFilter;
            return this;
        }

        public DecisionModelOutputGuardrail build() {
            return new DecisionModelOutputGuardrail(this);
        }
    }
}
