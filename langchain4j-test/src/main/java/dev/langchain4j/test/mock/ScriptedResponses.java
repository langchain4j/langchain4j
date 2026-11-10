package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;

/**
 * The steps of a mock, taken in order: one per request. Once all were taken, the last one is repeated (like Mockito's
 * {@code thenReturn(a, b)}), unless it is a response with tool calls: repeating it would make an AI Service call the
 * tools again and again, so the mock fails instead.
 */
final class ScriptedResponses {

    /**
     * One step: a response, a failure, or (when streaming) a response that fails after its tokens were streamed.
     *
     * @param aiMessage the response, {@code null} when the step only fails
     * @param tokens    the tokens to stream
     * @param failure   the failure, {@code null} when the step succeeds
     */
    record Step(AiMessage aiMessage, List<String> tokens, RuntimeException failure) {

        List<String> thinkingTokens() {
            return aiMessage == null ? List.of() : StreamingChatModelMock.splitIntoTokens(aiMessage.thinking());
        }

        static Step responding(AiMessage aiMessage) {
            ensureNotNull(aiMessage, "aiMessage");
            List<ToolExecutionRequest> toolCalls = aiMessage.toolExecutionRequests();
            if (new HashSet<>(toolCalls).size() != toolCalls.size()) {
                throw new IllegalArgumentException("The answer contains the same tool call twice, but AI Services"
                        + " keep the result of each tool call by its request. Give the calls different ids with"
                        + " ToolExecutionRequest.builder().");
            }
            return new Step(aiMessage, StreamingChatModelMock.toTokens(aiMessage), null);
        }

        static Step callingTool(String toolName, String jsonArguments, int stepNumber) {
            return responding(AiMessage.from(
                    ChatModelMock.toolCall("call_" + toolName + "_" + stepNumber, toolName, jsonArguments)));
        }

        static Step streaming(List<String> tokens) {
            ensureNotNull(tokens, "tokens");
            tokens.forEach(token -> ensureNotNull(token, "token"));
            return new Step(AiMessage.from(String.join("", tokens)), List.copyOf(tokens), null);
        }

        static Step failing(RuntimeException failure) {
            return new Step(null, List.of(), ensureNotNull(failure, "failure"));
        }

        AiMessage responseOrThrow() {
            if (failure != null) {
                throw failure;
            }
            return aiMessage;
        }

        private boolean hasNoTokensToStreamFirst() {
            return aiMessage == null || failure != null || aiMessage.hasToolExecutionRequests();
        }
    }

    private final String mockName;
    private final Deque<Step> remaining;
    private int stepCount;
    private Step last;

    ScriptedResponses(String mockName, Collection<Step> steps) {
        this.mockName = mockName;
        this.remaining = new ArrayDeque<>(steps);
        this.stepCount = steps.size();
    }

    synchronized void add(Step step) {
        remaining.addLast(step);
        stepCount++;
    }

    synchronized void addToolCall(String toolName, String jsonArguments) {
        add(Step.callingTool(toolName, jsonArguments, stepCount + 1));
    }

    synchronized void failLastAfterTokens(RuntimeException failure) {
        ensureNotNull(failure, "failure");
        Step step = remaining.peekLast();
        if (step == null || step.hasNoTokensToStreamFirst()) {
            throw new IllegalStateException("thenFailsWith(...) must follow a step that streams text: a step that"
                    + " fails or calls a tool has no tokens that could be streamed before the failure");
        }
        remaining.removeLast();
        remaining.addLast(new Step(step.aiMessage(), step.tokens(), failure));
    }

    synchronized Step next() {
        Step next = remaining.poll();
        if (next != null) {
            last = next;
            return next;
        }
        if (last.failure() == null && last.aiMessage().hasToolExecutionRequests()) {
            throw new IllegalStateException(mockName
                    + " has no more responses: the last scripted response (with tool calls) would be repeated, which"
                    + " would loop forever. Script the response that follows the tool results.");
        }
        return last;
    }
}
