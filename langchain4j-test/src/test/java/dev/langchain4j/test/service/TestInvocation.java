package dev.langchain4j.test.service;

import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.ObservabilityListeners;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a captured {@link AiServiceInvocation} by firing the events an AI Service would fire, so that the assertions
 * can be tested on exact shapes of invocations.
 */
final class TestInvocation {

    private static final UserMessage USER_MESSAGE = UserMessage.from("Hello");

    private Object content;
    private List<ToolExecution> toolExecutions = List.of();
    private List<Content> sources;
    private FinishReason finishReason;
    private TokenUsage tokenUsage;
    private List<ChatResponse> intermediateResponses = List.of();
    private ChatResponse finalResponse;

    static TestInvocation builder() {
        return new TestInvocation();
    }

    TestInvocation content(Object content) {
        this.content = content;
        return this;
    }

    TestInvocation toolExecutions(List<ToolExecution> toolExecutions) {
        this.toolExecutions = toolExecutions;
        return this;
    }

    TestInvocation sources(List<Content> sources) {
        this.sources = sources;
        return this;
    }

    TestInvocation finishReason(FinishReason finishReason) {
        this.finishReason = finishReason;
        return this;
    }

    /**
     * Without explicit responses, reported by a single response.
     */
    TestInvocation tokenUsage(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
        return this;
    }

    TestInvocation intermediateResponses(List<ChatResponse> intermediateResponses) {
        this.intermediateResponses = intermediateResponses;
        return this;
    }

    TestInvocation finalResponse(ChatResponse finalResponse) {
        this.finalResponse = finalResponse;
        return this;
    }

    AiServiceInvocation build() {
        return capture(this::fireEvents).invocation();
    }

    private void fireEvents() {
        AiServiceListenerRegistrar registrar = AiServiceListenerRegistrar.newInstance();
        InvocationContext context = InvocationContext.builder()
                .invocationId(UUID.randomUUID())
                .interfaceName("com.example.Assistant")
                .methodName("chat")
                .timestampNow()
                .build();
        registrar.fireEvent(AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(USER_MESSAGE)
                .build());
        if (sources != null) {
            registrar.fireEvent(RetrievalAugmentedEvent.builder()
                    .invocationContext(context)
                    .chatMessage(USER_MESSAGE)
                    .contents(sources)
                    .build());
        }
        for (ToolExecution toolExecution : toolExecutions) {
            registrar.fireEvent(ToolExecutedEvent.builder()
                    .invocationContext(context)
                    .request(toolExecution.request())
                    .resultText(toolExecution.result())
                    .isError(toolExecution.hasFailed())
                    .build());
        }
        List<ChatModelListener> chatModelListeners = ObservabilityListeners.chat(List.of());
        for (ChatResponse response : responses()) {
            ChatRequest request = ChatRequest.builder().messages(USER_MESSAGE).build();
            // the chat model notifies its listeners, then the AI Service fires the event with the same response
            Map<Object, Object> attributes = new ConcurrentHashMap<>();
            ChatModelRequestContext requestContext = ChatModelRequestContext.builder()
                    .chatRequest(request)
                    .modelProvider(ModelProvider.OTHER)
                    .attributes(attributes)
                    .invocationId(context.invocationId())
                    .build();
            ChatModelResponseContext responseContext = ChatModelResponseContext.builder()
                    .chatResponse(response)
                    .chatRequest(request)
                    .modelProvider(ModelProvider.OTHER)
                    .attributes(attributes)
                    .invocationId(context.invocationId())
                    .build();
            chatModelListeners.forEach(listener -> listener.onRequest(requestContext));
            chatModelListeners.forEach(listener -> listener.onResponse(responseContext));
            registrar.fireEvent(AiServiceResponseReceivedEvent.builder()
                    .invocationContext(context)
                    .request(request)
                    .response(response)
                    .build());
        }
        registrar.fireEvent(AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result(content)
                .build());
    }

    private List<ChatResponse> responses() {
        List<ChatResponse> responses = new ArrayList<>(intermediateResponses);
        if (finalResponse != null) {
            responses.add(finalResponse);
        } else if (tokenUsage != null || finishReason != null) {
            responses.add(ChatResponse.builder()
                    .aiMessage(AiMessage.from(String.valueOf(content)))
                    .tokenUsage(tokenUsage)
                    .finishReason(finishReason)
                    .build());
        }
        return responses;
    }
}
