package dev.langchain4j.service;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TestTokenStreamHandler {

    Map<String, Set<Thread>> allThreadsByMethod = new ConcurrentHashMap<>();
    Map<String, Set<Thread>> beforeToolExecutionThreads = new ConcurrentHashMap<>();
    Map<String, Set<Thread>> onToolExecutedThreads = new ConcurrentHashMap<>();
    Map<String, Set<Thread>> onPartialToolCallThreads = new ConcurrentHashMap<>();

    void onPartialResponse(String ignored) {
        addThread(Thread.currentThread(), "onPartialResponse");
    }

    void onPartialThinking(PartialThinking ignored) {
        addThread(Thread.currentThread(), "onPartialThinking");
    }

    void onIntermediateResponse(ChatResponse ignored) {
        addThread(Thread.currentThread(), "onIntermediateResponse");
    }

    void beforeToolExecution(BeforeToolExecution beforeToolExecution) {
        addThread(Thread.currentThread(), "beforeToolExecution");

        Set<Thread> threads = beforeToolExecutionThreads.computeIfAbsent(
                beforeToolExecution.request().name(), ignored -> ConcurrentHashMap.newKeySet());
        threads.add(Thread.currentThread());
    }

    void onPartialToolCall(PartialToolCall partialToolCall) {
        addThread(Thread.currentThread(), "onPartialToolCall");

        Set<Thread> threads = onPartialToolCallThreads.computeIfAbsent(
                partialToolCall.name(), ignored -> ConcurrentHashMap.newKeySet());
        threads.add(Thread.currentThread());
    }

    void onToolExecuted(ToolExecution toolExecution) {
        addThread(Thread.currentThread(), "onToolExecuted");

        Set<Thread> threads = onToolExecutedThreads.computeIfAbsent(
                toolExecution.request().name(), ignored -> ConcurrentHashMap.newKeySet());
        threads.add(Thread.currentThread());
    }

    void onError(Throwable ignored) {
        addThread(Thread.currentThread(), "onError");
    }

    void onCompleteResponse(ChatResponse ignored) {
        addThread(Thread.currentThread(), "onCompleteResponse");
    }

    private void addThread(Thread thread, String methodName) {
        Set<Thread> threads = allThreadsByMethod.computeIfAbsent(methodName, ignored -> ConcurrentHashMap.newKeySet());
        threads.add(thread);
    }
}
