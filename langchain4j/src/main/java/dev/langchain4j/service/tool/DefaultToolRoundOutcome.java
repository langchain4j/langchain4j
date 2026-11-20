package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link ToolRound.Outcome} of a {@link ToolRound}, which also carries what the compensation of the round needs.
 *
 * @param calls    the calls as they were executed, in the order of the LLM, with their results
 * @param messages the messages for the rest of the round
 * @param error    the first failure of a call, in the order of the LLM, or {@code null}
 */
record DefaultToolRoundOutcome(List<ToolRoundCall> calls, List<ChatMessage> messages, Throwable error)
        implements ToolRound.Outcome {

    DefaultToolRoundOutcome {
        calls = List.copyOf(calls);
    }

    @Override
    public List<ToolExecutionRequest> requests() {
        return calls.stream().map(ToolRoundCall::request).toList();
    }

    @Override
    public Map<ToolExecutionRequest, ToolExecutionResult> results() {
        Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();
        for (ToolRoundCall call : calls) {
            results.putIfAbsent(call.request(), call.result());
        }
        return results;
    }

    @Override
    public Optional<Throwable> firstError() {
        return Optional.ofNullable(error);
    }

    DefaultToolRoundOutcome withMessages(List<ChatMessage> messages) {
        return new DefaultToolRoundOutcome(calls, messages, error);
    }
}
