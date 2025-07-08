package dev.langchain4j.model.chat.common;

import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Set;

public record StreamingMetadata(String concatenatedPartialResponses,
                                int timesOnPartialResponseWasCalled,
                                List<IndexAndToolRequest> partialToolExecutionRequests,
                                List<IndexAndToolRequest> completeToolExecutionRequests,
                                int timesOnCompleteResponseWasCalled,
                                Set<Thread> threads,
                                StreamingChatResponseHandler handler
) {
}
