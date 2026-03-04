package dev.langchain4j.service.common;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.service.common.AbstractAiServiceWithToolsIT.checkMemoryWithImmediateTool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This test makes sure that all {@link StreamingChatModel} implementations behave consistently
 * when used with {@link AiServices}.
 */
@TestInstance(PER_CLASS)
public abstract class AbstractStreamingAiServiceIT {

    protected abstract List<StreamingChatModel> models();

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_answer_simple_question(StreamingChatModel model) throws Exception {

        // given
        model = spy(model);

        Assistant assistant = AiServices.create(Assistant.class, model);
        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();

        String userMessage = "What is the capital of Germany?";

        assistant
                .chat(userMessage)
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(chatResponse -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureChatResponse.complete(chatResponse);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        ChatResponse chatResponse = futureChatResponse.get(30, SECONDS);

        assertThat(answer).containsIgnoringCase("Berlin");
        assertThat(chatResponse.aiMessage().text()).isEqualTo(answer);

        ChatResponseMetadata chatResponseMetadata = chatResponse.metadata();
        if (assertChatResponseMetadataType()) {
            assertThat(chatResponseMetadata).isExactlyInstanceOf(chatResponseMetadataType(model));
        }

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponseMetadata.tokenUsage(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponseMetadata.finishReason()).isEqualTo(STOP);
        }

        verify(model)
                .chat(
                        eq(ChatRequest.builder()
                                .messages(UserMessage.from(userMessage))
                                .build()),
                        any(StreamingChatResponseHandler.class));
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_without_arguments(StreamingChatModel model) throws Exception {

        // given
        class Tools {

            @Tool
            LocalDate currentDate() {
                return LocalDate.of(2019, 1, 7);
            }
        }

        Tools tools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .build();

        // when
        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();

        assistant
                .chat("What is the date today?")
                .onPartialResponse(ignored -> {})
                .onError(futureChatResponse::completeExceptionally)
                .onCompleteResponse(futureChatResponse::complete)
                .start();

        futureChatResponse.get(30, SECONDS);

        // then
        verify(tools).currentDate();
        verifyNoMoreInteractions(tools);
    }

    // TODO test threads
    // TODO all tests from sync, perhaps reuse the same test logic

    protected boolean assertChatResponseMetadataType() {
        return true;
    }

    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return ChatResponseMetadata.class;
    }

    private void assertTokenUsage(TokenUsage tokenUsage, StreamingChatModel streamingChatModel) {
        assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(streamingChatModel));
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }

    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return TokenUsage.class;
    }

    protected boolean assertTokenUsage() {
        return true;
    }

    protected boolean assertFinishReason() {
        return true;
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_keep_memory_consistent_when_streaming_using_immediate_tool(StreamingChatModel model) {
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        var assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(new AbstractAiServiceWithToolsIT.ImmediateToolWithPrimitiveParameters())
                .chatMemory(chatMemory)
                .build();

        List<ToolExecution> toolExecutions = new ArrayList<>();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        // when
        assistant.chat("How much is 37 plus 87?")
                .onPartialResponse(ignored -> {})
                .onToolExecuted(toolExecutions::add)
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse chatResponse = future.join();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        assertThat(toolExecutions).hasSize(1);
        assertThat(toolExecutions.get(0).result()).isEqualTo("124");

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(3);
        checkMemoryWithImmediateTool(messages, "How much is 37 plus 87?", "124");

        List<ToolExecution> toolExecutions2 = new ArrayList<>();
        CompletableFuture<ChatResponse> future2 = new CompletableFuture<>();

        // when
        // Check that the memory is not corrupted and conversation can continue
        assistant.chat("Now add 47 to the previous result")
                .onPartialResponse(ignored -> {})
                .onToolExecuted(toolExecutions2::add)
                .onCompleteResponse(future2::complete)
                .onError(future2::completeExceptionally)
                .start();

        ChatResponse chatResponse2 = future2.join();

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.toolExecutionRequests()).hasSize(1);

        assertThat(toolExecutions2).hasSize(1);
        assertThat(toolExecutions2.get(0).result()).isEqualTo("171");

        messages = chatMemory.messages();
        assertThat(messages).hasSize(6);
        checkMemoryWithImmediateTool(messages.subList(3, 6), "Now add 47 to the previous result", "171");
    }
}
