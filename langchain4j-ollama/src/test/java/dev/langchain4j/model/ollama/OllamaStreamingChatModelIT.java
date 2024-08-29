package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaStreamingChatModelIT extends AbstractOllamaLanguageModelInfrastructure {

    StreamingChatLanguageModel model = OllamaStreamingChatModel.builder()
            .baseUrl(ollama.getEndpoint())
            .modelName(TINY_DOLPHIN_MODEL)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_answer() {

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();
        String answer = response.content().text();

        // then
        assertThat(answer).contains("Berlin");

        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isEqualTo(answer);
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_respect_numPredict() {

        // given
        int numPredict = 1; // max output tokens

        StreamingChatLanguageModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .numPredict(numPredict)
                .temperature(0.0)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();
        String answer = response.content().text();

        // then
        assertThat(answer).doesNotContain("Berlin");
        assertThat(response.content().text()).isEqualTo(answer);

        assertThat(response.tokenUsage().outputTokenCount()).isBetween(numPredict, numPredict + 2); // bug in Ollama
    }

    @Test
    void should_respect_system_message() {

        // given
        SystemMessage systemMessage = SystemMessage.from("Translate messages from user into German");
        UserMessage userMessage = UserMessage.from("I love you");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(asList(systemMessage, userMessage), handler);
        Response<AiMessage> response = handler.get();
        String answer = response.content().text();

        // then
        assertThat(answer).containsIgnoringCase("liebe");
        assertThat(response.content().text()).isEqualTo(answer);
    }

    @Test
    void should_respond_to_few_shot() {

        // given
        List<ChatMessage> messages = asList(
                UserMessage.from("1 + 1 ="),
                AiMessage.from(">>> 2"),

                UserMessage.from("2 + 2 ="),
                AiMessage.from(">>> 4"),

                UserMessage.from("4 + 4 =")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(messages, handler);
        Response<AiMessage> response = handler.get();
        String answer = response.content().text();

        // then
        assertThat(answer).startsWith(">>> 8");
        assertThat(response.content().text()).isEqualTo(answer);
    }

    @Test
    void should_generate_valid_json() {

        // given
        StreamingChatLanguageModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .format("json")
                .temperature(0.0)
                .build();

        String userMessage = "Return JSON with two fields: name and age of John Doe, 42 years old.";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();
        String answer = response.content().text();

        // then
        assertThat(answer).isEqualToIgnoringWhitespace("{\"name\": \"John Doe\", \"age\": 42}");
        assertThat(response.content().text()).isEqualTo(answer);
    }

    @Test
    void should_propagate_failure_to_handler_onError() throws Exception {

        // given
        String wrongModelName = "banana";

        StreamingChatLanguageModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(wrongModelName)
                .build();

        CompletableFuture<Throwable> future = new CompletableFuture<>();

        // when
        model.generate("does not matter", new StreamingResponseHandler<AiMessage>() {

            @Override
            public void onNext(String token) {
                future.completeExceptionally(new Exception("onNext should never be called"));
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                future.completeExceptionally(new Exception("onComplete should never be called"));
            }

            @Override
            public void onError(Throwable error) {
                future.complete(error);
            }
        });

        // then
        assertThat(future.get())
                .isExactlyInstanceOf(NullPointerException.class);
    }
}