package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link ToolRound.Outcome} of a {@link ToolRound}, which also carries what the compensation of the round needs.
 *
 * @param requests     the calls as they were executed, in the order of the LLM
 * @param results      the result of each call, by the call as it was executed
 * @param messages     the messages for the rest of the round
 * @param error        the first failure of a call, in the order of the LLM, or {@code null}
 * @param compensation what the compensation of the round needs besides {@code results}
 */
record DefaultToolRoundOutcome(
        List<ToolExecutionRequest> requests,
        Map<ToolExecutionRequest, ToolExecutionResult> results,
        List<ChatMessage> messages,
        Throwable error,
        ToolRoundCompensation compensation)
        implements ToolRound.Outcome {

    @Override
    public Optional<Throwable> firstError() {
        return Optional.ofNullable(error);
    }

    DefaultToolRoundOutcome withMessages(List<ChatMessage> messages) {
        return new DefaultToolRoundOutcome(requests, results, messages, error, compensation);
    }
}
