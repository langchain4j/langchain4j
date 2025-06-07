package dev.langchain4j.service.common;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

        Assistant assistant =
                AiServices.builder(Assistant.class).streamingChatModel(model).build();

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

        ChatResponse chatResponse = futureChatResponse.get(30, SECONDS);

        // then
        assertThat(chatResponse.aiMessage().text()).contains("2019");

        verify(tools).currentDate();
        verifyNoMoreInteractions(tools);

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
}
