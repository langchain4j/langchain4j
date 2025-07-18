package dev.langchain4j.model.openai.compatible;

import static dev.langchain4j.JsonTestUtils.jsonify;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * See <a href="https://api-docs.deepseek.com/guides/reasoning_model">DeepSeek API Docs</a> for more info.
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekReasoningIT { // TODO abstract? Move into AbstractBaseChatModelIT? name: OpenAiThinking...?

    SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

    ChatModel model = OpenAiChatModel.builder()
            .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
            .baseUrl("https://api.deepseek.com/v1")
            .apiKey(System.getenv("DEEPSEEK_API_KEY"))
            .modelName("deepseek-reasoner")
            .logRequests(true)
            .logResponses(true)
            .build();

//    OpenAiStreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
//            .baseUrl(System.getenv("DEEPSEEK_BASE_URL"))
//            .apiKey(System.getenv("DEEPSEEK_API_KEY"))
//            // .temperature(0.0)   unsupported by the model, will be ignored
//            .logRequests(true)
//            .logResponses(true)
//            .build();

    @Test
    void should_answer_with_reasoning_when_returnThinking_is_true() { // TODO name

        // given
        boolean returnThinking = true;

        ChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName("deepseek-reasoner")
                .returnThinking(returnThinking)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).containsIgnoringCase("Berlin"); // TODO will be stored. Add feature toggle?

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage, aiMessage, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).containsIgnoringCase("Paris");

        // should not send thinking back
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(aiMessage.text()))
                .doesNotContain(jsonify(aiMessage.thinking()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void should_answer_without_reasoning_when_returnThinking_is(Boolean returnThinking) { // TODO name

        // given
        ChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName("deepseek-reasoner")
                .returnThinking(returnThinking)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNull();
    }

//    @ParameterizedTest
//    @CsvSource({"deepseek-reasoner"})
//    void should_stream_answer_with_reasoning_content(String modelName) throws Exception {
//
//        // given
//        UserMessage userMessage = userMessage("what is the capital of China after 1949?");
//
//        ChatRequest request = ChatRequest.builder()
//                .parameters(ChatRequestParameters.builder().modelName(modelName).build())
//                .messages(userMessage)
//                .build();
//
//        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
//        CompletableFuture<String> futureReasoning = new CompletableFuture<>();
//        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
//
//        // when
//        streamingChatModel.chat(request, new StreamingChatResponseHandler() {
//
//            private final StringBuilder answerBuilder = new StringBuilder();
//            private final StringBuilder reasoningBuilder = new StringBuilder();
//
//            @Override
//            public void onPartialResponse(String partialResponse) {
//                answerBuilder.append(partialResponse);
//            }
//
//            @Override
//            public void onReasoningResponse(String reasoningContent) {
//                reasoningBuilder.append(reasoningContent);
//            }
//
//            @Override
//            public void onCompleteResponse(ChatResponse completeResponse) {
//                futureAnswer.complete(answerBuilder.toString());
//                futureReasoning.complete(reasoningBuilder.toString());
//                futureResponse.complete(completeResponse);
//            }
//
//            @Override
//            public void onError(Throwable error) {
//                futureAnswer.completeExceptionally(error);
//                futureReasoning.completeExceptionally(error);
//                futureResponse.completeExceptionally(error);
//            }
//        });
//
//        String answer = futureAnswer.get(30, SECONDS);
//        String reasoning = futureReasoning.get(30, SECONDS);
//        ChatResponse response = futureResponse.get(30, SECONDS);
//
//        // then
//
//        assertThat(answer).containsIgnoringCase("Beijing");
//        assertThat(reasoning).isNotBlank();
//
//        assertThat(response.finishReason()).isEqualTo(STOP);
//    }
}
