package dev.langchain4j.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;

public class TestTokenStreamHandler {

    Set<Thread> allThreads = ConcurrentHashMap.newKeySet();

    Map<String, Set<Thread>> beforeToolExecutionThreads = new ConcurrentHashMap<>();
    Map<String, Set<Thread>> onToolExecutedThreads = new ConcurrentHashMap<>();

    void onPartialResponse(String partialResponse) {
        allThreads.add(Thread.currentThread());
    }

    void onPartialThinking(PartialThinking partialThinking) {
        allThreads.add(Thread.currentThread());
    }

    void onIntermediateResponse(ChatResponse intermediateResponse) {
        allThreads.add(Thread.currentThread());
    }

    void beforeToolExecution(BeforeToolExecution beforeToolExecution) {
        allThreads.add(Thread.currentThread());

        Set<Thread> threads = beforeToolExecutionThreads.computeIfAbsent(beforeToolExecution.request().name(),
                ignored -> ConcurrentHashMap.newKeySet());
        threads.add(Thread.currentThread());
    }

    void onToolExecuted(ToolExecution toolExecution) {
        allThreads.add(Thread.currentThread());

        Set<Thread> threads = onToolExecutedThreads.computeIfAbsent(toolExecution.request().name(),
                ignored -> ConcurrentHashMap.newKeySet());
        threads.add(Thread.currentThread());
    }

    void onError(Throwable error) {
        allThreads.add(Thread.currentThread());
    }

    void onCompleteResponse(ChatResponse completeResponse) {
        allThreads.add(Thread.currentThread());
    }
}
