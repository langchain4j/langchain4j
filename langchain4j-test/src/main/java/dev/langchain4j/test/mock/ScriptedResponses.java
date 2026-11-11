package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The steps of a mock, taken in order: one per request. A request after the last step fails, unless the script ends
 * with {@code thenRepeats()}: then the last step answers every further request (the script does not start over).
 * Tool calls without an id get the next free id of the mock ({@code tool_call_1}, {@code tool_call_2}, ...) when the
 * mock answers with them. An answer that is not a whole given response reports the model name of the request and the
 * default token usage of the mock.
 */
final class ScriptedResponses {

    private static final int MAX_MESSAGE_LENGTH = 200;

    /**
     * One step: a response, a response computed from the request, a failure, or (when streaming) a response that fails
     * after its tokens were streamed.
     *
     * @param response  the response, {@code null} when the step only fails or computes its response
     * @param tokens    the tokens to stream
     * @param failure   the failure, {@code null} when the step succeeds
     * @param computing the function that computes the answer, {@code null} unless the step computes it
     * @param given     whether the response was given whole by the test, so that it is used exactly as given
     */
    record Step(
            ChatResponse response,
            List<String> tokens,
            RuntimeException failure,
            Function<ChatRequest, AiMessage> computing,
            boolean given) {

        AiMessage aiMessage() {
            return response == null ? null : response.aiMessage();
        }

        List<String> thinkingTokens() {
            return response == null ? List.of() : splitIntoTokens(response.aiMessage().thinking());
        }

        /**
         * A step that answers with the given message and reports the finish reason a provider reports:
         * {@link FinishReason#TOOL_EXECUTION} for an answer with tool calls, otherwise {@link FinishReason#STOP}.
         */
        static Step responding(AiMessage aiMessage) {
            ensureNotNull(aiMessage, "aiMessage");
            return responding(
                    ChatResponse.builder()
                            .aiMessage(aiMessage)
                            .finishReason(
                                    aiMessage.hasToolExecutionRequests()
                                            ? FinishReason.TOOL_EXECUTION
                                            : FinishReason.STOP)
                            .build(),
                    false);
        }

        /**
         * A step that answers with exactly the given response.
         */
        static Step responding(ChatResponse response) {
            return responding(response, true);
        }

        private static Step responding(ChatResponse response, boolean given) {
            ensureNotNull(response, "chatResponse");
            ensureDifferentToolCallIds(response.aiMessage());
            return new Step(response, splitIntoTokens(response.aiMessage().text()), null, null, given);
        }

        static Step callingTool(String toolName, String jsonArguments) {
            return responding(AiMessage.from(MockChatModel.toolCall(toolName, jsonArguments)));
        }

        static Step streaming(String... tokens) {
            ensureNotNull(tokens, "tokens");
            for (String token : tokens) {
                ensureNotNull(token, "token");
                if (token.isEmpty()) {
                    throw new IllegalArgumentException("A token cannot be empty, because LangChain4j never passes an"
                            + " empty token to a handler. To stream an answer without text, give no tokens.");
                }
            }
            Step step = responding(AiMessage.builder()
                    .text(tokens.length == 0 ? null : String.join("", tokens))
                    .build());
            return new Step(step.response(), List.of(tokens), null, null, false);
        }

        static Step computing(Function<ChatRequest, AiMessage> answers) {
            return new Step(null, List.of(), null, ensureNotNull(answers, "answers"), false);
        }

        static Step failing(RuntimeException failure) {
            return new Step(null, List.of(), ensureNotNull(failure, "failure"), null, false);
        }

        ChatResponse responseOrThrow() {
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        private boolean callsTools() {
            return failure == null && computing == null && response.aiMessage().hasToolExecutionRequests();
        }

        private boolean streamsSomethingFirst() {
            return failure == null && computing == null && !(tokens.isEmpty() && thinkingTokens().isEmpty());
        }

        private Step withResponse(ChatResponse response) {
            return new Step(response, tokens, failure, null, given);
        }
    }

    private final String mockName;
    private final List<Step> steps;
    private final Set<String> usedToolCallIds = new HashSet<>();
    private int nextIndex;
    private boolean repeatsLastStep;
    private int lastToolCallId;
    private volatile TokenUsage defaultTokenUsage;

    ScriptedResponses(String mockName, Collection<Step> initialSteps) {
        this.mockName = mockName;
        this.steps = new ArrayList<>();
        initialSteps.forEach(this::add);
    }

    synchronized void add(Step step) {
        ensureNotRepeating();
        recordToolCallIds(step);
        steps.add(step);
    }

    synchronized void failLastAfterTokens(RuntimeException failure) {
        ensureNotNull(failure, "failure");
        ensureNotRepeating();
        Step step = lastStep();
        if (nextIndex == steps.size() || !step.streamsSomethingFirst()) {
            throw new IllegalStateException("andFails(...) must follow a step that streams text or thinking, so that"
                    + " the failure comes after its tokens: the last step fails, computes its answer, has no text or"
                    + " thinking to stream, or was already taken");
        }
        steps.set(steps.size() - 1, new Step(step.response(), step.tokens(), failure, null, step.given()));
    }

    synchronized void repeatLastStep() {
        ensureNotRepeating();
        if (lastStep().callsTools()) {
            String factory = mockName.contains("Streaming") ? "thatStreams" : "thatResponds";
            throw new IllegalStateException("thenRepeats() cannot repeat a step that calls a tool: an AI Service would"
                    + " call the tool again and again. Script the answer that follows the tool results first. To test a"
                    + " model that keeps calling a tool, for example the limit set with maxToolCallingRoundTrips,"
                    + " compute the answer: "
                    + factory + "(request -> AiMessage.from(MockChatModel.toolCall(...))).thenRepeats()");
        }
        repeatsLastStep = true;
    }

    /**
     * Sets the token usage of every answer that is not a whole given response.
     */
    void defaultTokenUsage(TokenUsage tokenUsage) {
        this.defaultTokenUsage = tokenUsage;
    }

    private void ensureNotRepeating() {
        if (repeatsLastStep) {
            throw new IllegalStateException("thenRepeats() must be the last call of the script: the last"
                    + " step already answers every further request");
        }
    }

    private Step lastStep() {
        return steps.get(steps.size() - 1);
    }

    /**
     * Records the request and takes the step that answers it, in one go, so that concurrent requests get consistent
     * request numbers. A request after the last step gets a step that fails with an {@link IllegalStateException}.
     * The mock answers with the step returned by {@link #answer(Step, ChatRequest)}.
     */
    synchronized Step take(RecordedRequests<ChatRequest> requests, ChatRequest request) {
        int requestNumber = requests.add(request);
        if (nextIndex < steps.size()) {
            return steps.get(nextIndex++);
        }
        if (repeatsLastStep) {
            return lastStep();
        }
        return Step.failing(new IllegalStateException(runOutMessage(request, requestNumber)));
    }

    /**
     * The step as the mock answers with it: a computed answer is computed (a function that throws, or an answer whose
     * tool calls share an id, gives a failing step), tool calls without an id get the next free ids of the mock, and an
     * answer that is not a whole given response reports the model name of the request and the default token usage.
     * The function is not called while holding the lock, because it may wait for other requests.
     */
    Step answer(Step step, ChatRequest request) {
        if (step.failure() != null) {
            return step;
        }
        if (step.computing() != null) {
            try {
                step = Step.responding(step.computing().apply(request));
            } catch (RuntimeException e) {
                return Step.failing(e);
            }
        }
        step = withToolCallIds(step);
        if (step.given()) {
            return step;
        }
        return step.withResponse(ChatResponse.builder()
                .aiMessage(step.aiMessage())
                .finishReason(step.response().finishReason())
                .modelName(request.modelName())
                .tokenUsage(defaultTokenUsage)
                .build());
    }

    private synchronized Step withToolCallIds(Step step) {
        recordToolCallIds(step);
        List<ToolExecutionRequest> toolCalls = step.aiMessage().toolExecutionRequests();
        if (toolCalls.stream().allMatch(toolCall -> toolCall.id() != null)) {
            return step;
        }
        List<ToolExecutionRequest> withIds = new ArrayList<>();
        for (ToolExecutionRequest toolCall : toolCalls) {
            withIds.add(
                    toolCall.id() != null
                            ? toolCall
                            : toolCall.toBuilder().id(nextFreeToolCallId()).build());
        }
        AiMessage aiMessage =
                step.aiMessage().toBuilder().toolExecutionRequests(withIds).build();
        return step.withResponse(step.response().toBuilder().aiMessage(aiMessage).build());
    }

    private void recordToolCallIds(Step step) {
        if (step.response() != null) {
            step.aiMessage().toolExecutionRequests().stream()
                    .map(ToolExecutionRequest::id)
                    .filter(Objects::nonNull)
                    .forEach(usedToolCallIds::add);
        }
    }

    private String nextFreeToolCallId() {
        String id;
        do {
            id = "tool_call_" + ++lastToolCallId;
        } while (!usedToolCallIds.add(id));
        return id;
    }

    private static void ensureDifferentToolCallIds(AiMessage aiMessage) {
        Set<String> seen = new HashSet<>();
        for (ToolExecutionRequest toolCall : aiMessage.toolExecutionRequests()) {
            if (toolCall.id() != null && !seen.add(toolCall.id())) {
                throw new IllegalArgumentException("The answer contains two tool calls with the id \"" + toolCall.id()
                        + "\", but a provider never gives two tool calls of one answer the same id. Give the calls"
                        + " different ids, or no ids: the mock then numbers them.");
            }
        }
    }

    private String runOutMessage(ChatRequest request, int requestNumber) {
        StringBuilder message = new StringBuilder()
                .append(mockName)
                .append(" received request ")
                .append(requestNumber)
                .append(", but its script has only ")
                .append(steps.size())
                .append(steps.size() == 1 ? " step. " : " steps. ");
        if (lastStep().callsTools()) {
            message.append("The last step called a tool, so script the answer that follows the tool results. ");
        }
        return message.append("Script one more step, or end the script with thenRepeats() to answer every further"
                        + " request with its last step. If the mock is shared between tests, for example as a bean,"
                        + " create a new one for each test. The last message of request ")
                .append(requestNumber)
                .append(": ")
                .append(describe(request.messages().get(request.messages().size() - 1)))
                .toString();
    }

    private static String describe(ChatMessage message) {
        String text;
        if (message instanceof UserMessage userMessage) {
            text = userMessage.hasSingleText()
                    ? userMessage.singleText()
                    : userMessage.contents().toString();
        } else if (message instanceof ToolExecutionResultMessage toolResult) {
            text = "result of " + toolResult.toolName() + ": " + toolResult.text();
        } else if (message instanceof SystemMessage systemMessage) {
            text = systemMessage.text();
        } else if (message instanceof AiMessage aiMessage) {
            text = aiMessage.text();
        } else {
            text = message.toString();
        }
        String described = message.type() + " \"" + text + "\"";
        return described.length() > MAX_MESSAGE_LENGTH
                ? described.substring(0, MAX_MESSAGE_LENGTH) + "...\""
                : described;
    }

    /**
     * Splits the text into tokens the way the streaming mock streams it: one character (code point) per token.
     */
    static List<String> splitIntoTokens(String text) {
        if (isNullOrEmpty(text)) {
            return List.of();
        }
        return text.codePoints().mapToObj(Character::toString).toList();
    }
}
