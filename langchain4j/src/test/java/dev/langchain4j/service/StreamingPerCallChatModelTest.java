package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.service.tool.ToolServiceContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Validates that downstream framework integrators can supply an explicit per-call
 * {@link StreamingChatModel} to {@link AiServiceStreamingResponseHandler}, mirroring the
 * sync overload of {@link ToolService#executeInferenceAndToolsLoop} that takes an explicit
 * {@code ChatModel}.
 *
 * <p>This test lives in the {@code dev.langchain4j.service} package so it can reach the
 * package-private {@code AiServiceStreamingResponseHandler}.
 */
class StreamingPerCallChatModelTest {

    interface Service {
        // Placeholder service interface; the handler is exercised directly.
    }

    static class Echo {
        @Tool
        String echo(String arg) {
            return "echo:" + arg;
        }
    }

    @Test
    void per_call_streaming_chat_model_is_used_for_follow_up_request() throws Exception {
        // Default model would respond "from-default-model"; per-call model responds "from-per-call".
        // The handler should use the per-call model on its recursive follow-up call.

        StreamingChatModelMock defaultModel =
                StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("from-default-model"));
        StreamingChatModelMock perCallModel =
                StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("from-per-call-model"));

        ToolService toolService = new ToolService();
        toolService.tools(java.util.Collections.singletonList(new Echo()));

        AiServiceContext context = AiServiceContext.create(Service.class);
        context.streamingChatModel = defaultModel;
        context.toolService = toolService;

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        context.initChatMemories(chatMemory);
        dev.langchain4j.data.message.UserMessage userMessage = dev.langchain4j.data.message.UserMessage.from("go");

        InvocationContext invocationContext = InvocationContext.builder()
                .interfaceName(Service.class.getName())
                .methodName("chat")
                .userMessage(userMessage)
                .chatMemoryId("default")
                .timestampNow()
                .build();

        ToolServiceContext toolServiceContext =
                toolService.createContext(invocationContext, userMessage, new java.util.ArrayList<>());

        // Fabricate the FIRST chat response: a tool call. The handler will run the tool, then
        // call the LLM again to get the final answer — that's the call that should hit the
        // per-call model.
        ChatRequest initialRequest = ChatRequest.builder()
                .messages(java.util.List.of(userMessage))
                .parameters(ChatRequestParameters.builder().build())
                .build();
        ChatExecutor chatExecutor = ChatExecutor.builder(defaultModel)
                .chatRequest(initialRequest)
                .invocationContext(invocationContext)
                .eventListenerRegistrar(context.eventListenerRegistrar)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        AiServiceStreamingResponseHandler handler = new AiServiceStreamingResponseHandler(
                initialRequest,
                chatExecutor,
                context,
                invocationContext,
                ignored -> {},
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                future::complete,
                future::completeExceptionally,
                chatMemory,
                new TokenUsage(),
                toolServiceContext,
                10,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler(),
                null,
                null,
                null,
                perCallModel,
                null,
                initialRequest.parameters(),
                null);

        // Synthesize the first onCompleteResponse with a tool call — same as if the initial
        // streamed response from the default model had carried it.
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("c1")
                .name("echo")
                .arguments("{\"arg0\":\"hi\"}")
                .build();
        ChatResponse firstResponse =
                ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build();
        handler.onCompleteResponse(firstResponse);

        ChatResponse finalResponse = future.get(10, TimeUnit.SECONDS);
        assertThat(finalResponse.aiMessage().text()).isEqualTo("from-per-call-model");

        // Confirm the per-call model received the follow-up request and the default model was
        // not invoked (we never invoked the initial request through it in this test).
        assertThat(perCallModel.requests()).hasSize(1);
        assertThat(defaultModel.requests()).isEmpty();
    }
}
