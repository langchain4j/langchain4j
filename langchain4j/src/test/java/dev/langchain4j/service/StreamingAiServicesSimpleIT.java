package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

/**
 * This test makes sure that all {@link StreamingChatLanguageModel} implementations behave consistently
 * when used with {@link AiServices}.
 */
@TestInstance(PER_CLASS)
public abstract class StreamingAiServicesSimpleIT {

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
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        String userMessage = "What is the capital of Germany?";

        assistant.chat(userMessage)
                .onNext(answerBuilder::append)
                .onComplete(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        Response<AiMessage> response = futureResponse.get(30, SECONDS);

        assertThat(answer).containsIgnoringCase("Berlin");
        assertThat(response.content().text()).isEqualTo(answer);

        if (assertTokenUsage()) {
            TokenUsage tokenUsage = response.tokenUsage();
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.totalTokenCount())
                    .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        }

        if (assertFinishReason()) {
            assertThat(response.finishReason()).isEqualTo(STOP);
        }

        verify(model).chat(
                eq(ChatRequest.builder().messages(UserMessage.from(userMessage)).build()),
                any(StreamingChatResponseHandler.class)
        );
    }

    // TODO test threads

    protected boolean assertTokenUsage() {
        return true;
    }

    protected boolean assertFinishReason() {
        return true;
    }

    // TODO test tool handling in AI Services across models (separate test)

    // TODO test token usage is summed for tools?
}
