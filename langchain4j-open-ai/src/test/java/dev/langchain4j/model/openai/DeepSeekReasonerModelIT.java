package dev.langchain4j.model.openai;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekReasonerModelIT {

    OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("DEEPSEEK_BASE_URL"))
            .apiKey(System.getenv("DEEPSEEK_API_KEY"))
            // .temperature(0.0)   unsupported by the model, will be ignored
            .logRequests(true)
            .logResponses(true)
            .build();

    OpenAiStreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
            .baseUrl(System.getenv("DEEPSEEK_BASE_URL"))
            .apiKey(System.getenv("DEEPSEEK_API_KEY"))
            // .temperature(0.0)   unsupported by the model, will be ignored
            .logRequests(true)
            .logResponses(true)
            .build();

    /**
     * Refer to the model's documentation: <a href="https://api-docs.deepseek.com/guides/reasoning_model">DeepSeek Api Doc</a>
     */
    @ParameterizedTest
    @CsvSource({"deepseek-reasoner"})
    void should_answer_with_reasoning_content(String modelName) {

        // given
        UserMessage userMessage = userMessage("what is the capital of China after 1949?");

        ChatRequest request = ChatRequest.builder()
                .parameters(ChatRequestParameters.builder().modelName(modelName).build())
                .messages(userMessage)
                .build();

        // when
        ChatResponse response = chatModel.chat(request);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Beijing");
        assertThat(response.aiMessage().reasoningContent()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({"deepseek-reasoner"})
    void should_stream_answer_with_reasoning_content(String modelName) throws Exception {

        // given
        UserMessage userMessage = userMessage("what is the capital of China after 1949?");

        ChatRequest request = ChatRequest.builder()
                .parameters(ChatRequestParameters.builder().modelName(modelName).build())
                .messages(userMessage)
                .build();

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<String> futureReasoning = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        streamingChatModel.chat(request, new StreamingChatResponseHandler() {

            private final StringBuilder answerBuilder = new StringBuilder();
            private final StringBuilder reasoningBuilder = new StringBuilder();

            @Override
            public void onPartialResponse(String partialResponse) {
                answerBuilder.append(partialResponse);
            }

            @Override
            public void onReasoningResponse(String reasoningContent) {
                reasoningBuilder.append(reasoningContent);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureAnswer.complete(answerBuilder.toString());
                futureReasoning.complete(reasoningBuilder.toString());
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureAnswer.completeExceptionally(error);
                futureReasoning.completeExceptionally(error);
                futureResponse.completeExceptionally(error);
            }
        });

        String answer = futureAnswer.get(30, SECONDS);
        String reasoning = futureReasoning.get(30, SECONDS);
        ChatResponse response = futureResponse.get(30, SECONDS);

        // then

        assertThat(answer).containsIgnoringCase("Beijing");
        assertThat(reasoning).isNotBlank();

        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}
