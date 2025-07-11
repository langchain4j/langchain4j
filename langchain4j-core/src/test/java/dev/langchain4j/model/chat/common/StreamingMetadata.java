package dev.langchain4j.model.chat.common;

import java.util.List;
import java.util.Set;
import dev.langchain4j.agent.tool.CompleteToolCall;
import dev.langchain4j.agent.tool.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

public record StreamingMetadata(String concatenatedPartialResponses,
                                int timesOnPartialResponseWasCalled,
                                List<PartialToolCall> partialToolCalls,
                                List<CompleteToolCall> completeToolCalls,
                                int timesOnCompleteResponseWasCalled,
                                Set<Thread> threads,
                                StreamingChatResponseHandler handler
) {
}
