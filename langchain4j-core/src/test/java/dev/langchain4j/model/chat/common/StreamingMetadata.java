package dev.langchain4j.model.chat.common;

import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Set;

public record StreamingMetadata(
        String concatenatedPartialResponses,
        int timesOnPartialResponseWasCalled,
        List<PartialToolCall> partialToolCalls,
        List<CompleteToolCall> completeToolCalls,
        int timesOnCompleteResponseWasCalled,
        Set<Thread> threads,
        StreamingChatResponseHandler handler) {}
