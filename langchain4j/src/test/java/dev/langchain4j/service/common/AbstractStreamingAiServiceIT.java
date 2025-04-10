package dev.langchain4j.service.common;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * This test makes sure that all {@link StreamingChatLanguageModel} implementations behave consistently
 * when used with {@link AiServices}.
 */
@TestInstance(PER_CLASS)
public abstract class AbstractStreamingAiServiceIT {

    protected abstract List<StreamingChatLanguageModel> models();

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_answer_simple_question(StreamingChatLanguageModel model) throws Exception {

        // given
        model = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(model)
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();

        String userMessage = "What is the capital of Germany?";

        assistant.chat(userMessage)
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

        if (assertTokenUsage()) {
            TokenUsage tokenUsage = chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.totalTokenCount())
                    .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }

        verify(model).chat(
                eq(ChatRequest.builder().messages(UserMessage.from(userMessage)).build()),
                any(StreamingChatResponseHandler.class)
        );
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_without_arguments(StreamingChatLanguageModel model) throws Exception {

        // given
        class Tools {

            @Tool
            LocalDate currentDate() {
                return LocalDate.of(2019, 1, 7);
            }
        }

        Tools tools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(model)
                .tools(tools)
                .build();

        // when
        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();

        assistant.chat("What is the date today?")
                .onPartialResponse(ignored -> {})
                .onError(futureChatResponse::completeExceptionally)
                .onCompleteResponse(futureChatResponse::complete)
                .start();

        ChatResponse chatResponse = futureChatResponse.get(30, SECONDS);

        // then
        assertThat(chatResponse.aiMessage().text()).contains("2019");

        verify(tools).currentDate();
        verifyNoMoreInteractions(tools);
    }

    // TODO test threads
    // TODO all tests from sync, perhaps reuse the same test logic

    protected boolean assertTokenUsage() {
        return true;
    }

    protected boolean assertFinishReason() {
        return true;
    }
}
