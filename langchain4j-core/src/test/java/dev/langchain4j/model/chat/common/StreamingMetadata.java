package dev.langchain4j.model.chat.common;

import java.util.List;
import java.util.Set;
import dev.langchain4j.agent.tool.CompleteToolExecutionRequest;
import dev.langchain4j.agent.tool.PartialToolExecutionRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

public record StreamingMetadata(String concatenatedPartialResponses,
                                int timesOnPartialResponseWasCalled,
                                List<PartialToolExecutionRequest> partialToolExecutionRequests,
                                List<CompleteToolExecutionRequest> completeToolExecutionRequests,
                                int timesOnCompleteResponseWasCalled,
                                Set<Thread> threads,
                                StreamingChatResponseHandler handler
) {
}
