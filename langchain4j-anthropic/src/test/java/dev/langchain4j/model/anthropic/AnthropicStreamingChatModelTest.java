package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class AnthropicStreamingChatModelTest {

    StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @BeforeEach
    void beforeEach() throws InterruptedException {
        Thread.sleep(10_000L); // to avoid hitting rate limits
    }

    @Test
    void should_stream_answer() throws Exception {
        String userMessage = "What is the capital of Germany?";
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();
        model.generate(userMessage, streamHandler(futureResponse));

        Response<AiMessage> response = futureResponse.get(30, SECONDS);
        assertThat(response.content().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(14);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_respect_numPredict() throws Exception {
        int numPredict = 1; // max output tokens
        StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .maxTokens(numPredict)
                .build();
        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();
        model.generate(singletonList(userMessage), streamHandler(futureResponse));
        Response<AiMessage> response = futureResponse.get(30, SECONDS);
        assertThat(response.content().text()).doesNotContain("Berlin");
        assertThat(response.tokenUsage().outputTokenCount()).isBetween(numPredict, numPredict);
    }

    @Test
    void should_respect_system_message() throws Exception {
        SystemMessage systemMessage = SystemMessage.from("You are a professional translator into German language");
        UserMessage userMessage = UserMessage.from("Translate: I love you");
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();
        model.generate(asList(systemMessage, userMessage), streamHandler(futureResponse));
        Response<AiMessage> response = futureResponse.get(30, SECONDS);
        assertThat(response.content().text()).containsIgnoringCase("liebe");
    }

    @SneakyThrows
    @ParameterizedTest
    @EnumSource(AnthropicChatModelName.class)
    void should_support_all_string_model_names(AnthropicChatModelName modelName) {

        StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName.toString())
                .maxTokens(3)
                .build();

        UserMessage userMessage = userMessage("Hi");
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();
        model.generate(singletonList(userMessage), streamHandler(futureResponse));
        Response<AiMessage> response = futureResponse.get(30, SECONDS);
        assertThat(response.content().text()).isNotBlank();
    }

    @NotNull
    private static StreamingResponseHandler<AiMessage> streamHandler(CompletableFuture<Response<AiMessage>> futureResponse) {
        return new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                System.out.println("onComplete: '" + response + "'");
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        };
    }

}